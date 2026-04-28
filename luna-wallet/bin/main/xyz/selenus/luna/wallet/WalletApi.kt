package xyz.selenus.luna.wallet

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import xyz.selenus.luna.LunaHeliusClient
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * # Helius Wallet API (Beta)
 *
 * High-level wrapper around `https://api.helius.xyz/v1/wallet/...`. The Wallet
 * API is a separate REST surface from Helius's JSON-RPC: it returns
 * already-decoded, human-readable values (balances divided by `decimals`, USD
 * values pre-computed, identity metadata enriched) so callers don't need to do
 * their own decoding.
 *
 * **Beta caveat:** Helius reserves the right to change response shapes. This
 * wrapper isolates that risk in [WalletTypes] — a future spec change should
 * only touch the `@Serializable` data classes there, not the call sites.
 *
 * ## Acquire
 *
 * ```kotlin
 * import xyz.selenus.luna.wallet.wallet
 *
 * val helius = LunaHeliusClient("<api-key>")
 * val balances = helius.wallet.getBalances("86xCnPeV...")
 * println("Portfolio: $${balances.totalUsdValue}")
 * ```
 *
 * ## Innovation hooks
 *
 * Two helpers go beyond what the upstream Helius SDKs ship:
 *  - [getAllBalancesFlow] streams across pagination so callers get the first
 *    page immediately and incrementally receive the rest, without forcing them
 *    to manage cursors.
 *  - [getBatchIdentityChunked] auto-splits a list larger than the 100-address
 *    POST limit into multiple parallel calls and concatenates results — useful
 *    for wallet-cluster analysis at scale (e.g. when scoring a Boobies NFT
 *    holder distribution).
 */
class WalletApi internal constructor(private val client: LunaHeliusClient) {

    private val httpClient: OkHttpClient get() = client.httpClient
    private val json: Json get() = client.json
    private val apiKey: String get() = client.apiKey

    // ── Single-shot endpoints ───────────────────────────────────────────

    /**
     * Look up identity metadata for a single known wallet (exchange,
     * protocol, market maker, etc).
     *
     * @throws WalletApiException with `status == 404` if the address has no
     *   indexed identity. Use [tryGetIdentity] for a `null`-returning variant.
     */
    suspend fun getIdentity(wallet: String): WalletIdentity {
        val url = walletPath(wallet, "identity").build()
        val raw = doGet(url)
        return json.decodeFromString(WalletIdentity.serializer(), raw)
    }

    /** Like [getIdentity] but returns `null` instead of throwing on 404. */
    suspend fun tryGetIdentity(wallet: String): WalletIdentity? = try {
        getIdentity(wallet)
    } catch (e: WalletApiException) {
        if (e.status == 404) null else throw e
    }

    /**
     * Batch identity lookup for up to 100 addresses in a single POST.
     *
     * For larger batches use [getBatchIdentityChunked], which transparently
     * splits the request into 100-address chunks and runs them in parallel.
     */
    suspend fun getBatchIdentity(addresses: List<String>): List<WalletIdentity> {
        require(addresses.isNotEmpty()) { "addresses must not be empty" }
        require(addresses.size <= MAX_BATCH_IDENTITY) {
            "Wallet API caps batch-identity at $MAX_BATCH_IDENTITY addresses; got ${addresses.size}. " +
                "Use getBatchIdentityChunked() for larger lists."
        }

        val url = "$BASE_URL/batch-identity".toHttpUrl().newBuilder()
            .addQueryParameter("api-key", apiKey)
            .build()

        val payload = buildJsonObject {
            put("addresses", JsonArray(addresses.map { JsonPrimitive(it) }))
        }
        val body = json.encodeToString(JsonElement.serializer(), payload)
            .toRequestBody(JSON_MEDIA_TYPE)

        val raw = doPost(url, body)
        return json.decodeFromString(ListSerializer(WalletIdentity.serializer()), raw)
    }

    /**
     * Convenience over [getBatchIdentity]: chunks an arbitrarily large list
     * into 100-address windows and concatenates results.
     *
     * Address order is preserved relative to [addresses]. Duplicate addresses
     * are deduplicated before the request and re-attached on the way back, so
     * callers don't pay credits for repeats.
     */
    suspend fun getBatchIdentityChunked(addresses: List<String>): List<WalletIdentity> {
        if (addresses.isEmpty()) return emptyList()
        val unique = LinkedHashSet(addresses).toList()
        val results = ArrayList<WalletIdentity>(unique.size)
        unique.chunked(MAX_BATCH_IDENTITY).forEach { chunk ->
            results += getBatchIdentity(chunk)
        }
        return results
    }

    /**
     * Get the first page of token + (optionally) NFT balances for [wallet].
     *
     * Native SOL is included by default — pass `showNative = false` to omit it.
     * To paginate manually, increment [page]; for streaming consumption use
     * [getAllBalancesFlow].
     */
    suspend fun getBalances(
        wallet: String,
        page: Int? = null,
        limit: Int? = null,
        showZeroBalance: Boolean? = null,
        showNative: Boolean? = null,
        showNfts: Boolean? = null
    ): WalletBalancesResponse {
        val url = walletPath(wallet, "balances")
            .addOptionalParam("page", page)
            .addOptionalParam("limit", limit)
            .addOptionalParam("showZeroBalance", showZeroBalance)
            .addOptionalParam("showNative", showNative)
            .addOptionalParam("showNfts", showNfts)
            .build()
        val raw = doGet(url)
        return json.decodeFromString(WalletBalancesResponse.serializer(), raw)
    }

    /**
     * Stream every page of [wallet]'s balances as a cold [Flow]. Each emission
     * is the [WalletBalancesResponse] for one page; the flow completes when
     * `pagination.hasMore` flips to false.
     *
     * Token positions, by contract, are sorted USD-descending across the
     * stream so consumers can short-circuit (e.g. `.take(50)` to get the top
     * 50 holdings without exhausting pagination).
     *
     * NFTs are only requested on the **first** page when [showNfts] is true,
     * matching the upstream API contract. Subsequent pages will not include
     * an NFT list.
     */
    fun getAllBalancesFlow(
        wallet: String,
        limit: Int = 100,
        showZeroBalance: Boolean = false,
        showNative: Boolean = true,
        showNfts: Boolean = false
    ): Flow<WalletBalancesResponse> = flow {
        var page = 1
        while (true) {
            val response = getBalances(
                wallet = wallet,
                page = page,
                limit = limit,
                showZeroBalance = showZeroBalance,
                showNative = showNative,
                // NFTs are first-page-only per spec; suppress on subsequent pages.
                showNfts = showNfts && page == 1
            )
            emit(response)
            if (!response.pagination.hasMore || response.balances.isEmpty()) return@flow
            page += 1
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Fetch one page of parsed transaction history.
     *
     * @param tokenAccounts How to treat ATA-only transactions. Default
     *   ([WalletAtaFilter.BALANCE_CHANGED]) is Helius's recommendation —
     *   it filters spam without dropping real balance changes.
     * @param type Optional Helius-defined event filter. Use the [WalletTxType]
     *   sealed hierarchy for type-safety, or pass a raw string if Helius adds
     *   a type the SDK doesn't yet enumerate.
     */
    suspend fun getHistory(
        wallet: String,
        limit: Int? = null,
        before: String? = null,
        after: String? = null,
        type: WalletTxType? = null,
        tokenAccounts: WalletAtaFilter? = null
    ): WalletHistoryResponse {
        val url = walletPath(wallet, "history")
            .addOptionalParam("limit", limit)
            .addOptionalParam("before", before)
            .addOptionalParam("after", after)
            .addOptionalParam("type", type?.raw)
            .addOptionalParam("tokenAccounts", tokenAccounts?.serialName())
            .build()
        val raw = doGet(url)
        return json.decodeFromString(WalletHistoryResponse.serializer(), raw)
    }

    /**
     * Stream every page of history (newest → oldest) as a cold [Flow].
     *
     * The flow completes when `pagination.hasMore` is false. Useful for
     * indexing a wallet's full lifetime; combine with `.take(N)` to bound
     * the number of pages fetched.
     */
    fun getAllHistoryFlow(
        wallet: String,
        limit: Int = 100,
        type: WalletTxType? = null,
        tokenAccounts: WalletAtaFilter? = WalletAtaFilter.BALANCE_CHANGED
    ): Flow<WalletHistoryResponse> = flow {
        var cursor: String? = null
        while (true) {
            val response = getHistory(
                wallet = wallet,
                limit = limit,
                before = cursor,
                type = type,
                tokenAccounts = tokenAccounts
            )
            emit(response)
            val next = response.pagination.nextCursor
            if (!response.pagination.hasMore || next.isNullOrEmpty()) return@flow
            cursor = next
        }
    }.flowOn(Dispatchers.IO)

    /** One page of token transfers in/out of [wallet]. */
    suspend fun getTransfers(
        wallet: String,
        limit: Int? = null,
        cursor: String? = null
    ): WalletTransfersResponse {
        val url = walletPath(wallet, "transfers")
            .addOptionalParam("limit", limit)
            .addOptionalParam("cursor", cursor)
            .build()
        val raw = doGet(url)
        return json.decodeFromString(WalletTransfersResponse.serializer(), raw)
    }

    /** Stream every transfer (newest → oldest) as a cold [Flow]. */
    fun getAllTransfersFlow(
        wallet: String,
        limit: Int = 100
    ): Flow<WalletTransfersResponse> = flow {
        var cursor: String? = null
        while (true) {
            val response = getTransfers(wallet = wallet, limit = limit, cursor = cursor)
            emit(response)
            val next = response.pagination.nextCursor
            if (!response.pagination.hasMore || next.isNullOrEmpty()) return@flow
            cursor = next
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Get the original funding source of [wallet] — typically the address that
     * sent the first incoming SOL transfer. Helius enriches the funder with an
     * identity (e.g. `"Coinbase 2"`) when known.
     *
     * @throws WalletApiException with `status == 404` if no funding tx exists
     *   (e.g. a brand-new keypair). Use [tryGetFundedBy] for a safe variant.
     */
    suspend fun getFundedBy(wallet: String): WalletFundingSource {
        val url = walletPath(wallet, "funded-by").build()
        val raw = doGet(url)
        return json.decodeFromString(WalletFundingSource.serializer(), raw)
    }

    /** Like [getFundedBy] but returns `null` on 404 instead of throwing. */
    suspend fun tryGetFundedBy(wallet: String): WalletFundingSource? = try {
        getFundedBy(wallet)
    } catch (e: WalletApiException) {
        if (e.status == 404) null else throw e
    }

    // ── Internal HTTP plumbing ─────────────────────────────────────────

    private fun walletPath(wallet: String, segment: String) =
        "$BASE_URL/$wallet/$segment".toHttpUrl().newBuilder()
            .addQueryParameter("api-key", apiKey)

    private suspend fun doGet(url: okhttp3.HttpUrl): String {
        val request = Request.Builder().url(url).get().build()
        return execute(request)
    }

    private suspend fun doPost(url: okhttp3.HttpUrl, body: okhttp3.RequestBody): String {
        val request = Request.Builder().url(url).post(body)
            .header("Content-Type", "application/json")
            .build()
        return execute(request)
    }

    private suspend fun execute(request: Request): String =
        suspendCancellableCoroutine { cont ->
            val call = httpClient.newCall(request)
            cont.invokeOnCancellation { runCatching { call.cancel() } }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    cont.resumeWithException(WalletApiException(-1, e.message ?: "I/O failure", e))
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { res ->
                        val body = res.body?.string().orEmpty()
                        if (!res.isSuccessful) {
                            cont.resumeWithException(
                                WalletApiException(
                                    status = res.code,
                                    message = "Helius Wallet API ${res.code}: ${body.ifEmpty { res.message }}"
                                )
                            )
                        } else {
                            cont.resume(body)
                        }
                    }
                }
            })
        }

    companion object {
        /** Helius Wallet API root. */
        const val BASE_URL = "https://api.helius.xyz/v1/wallet"

        /** Server-enforced cap for `POST /batch-identity`. */
        const val MAX_BATCH_IDENTITY = 100

        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}

/**
 * Raised on any non-2xx response from the Wallet API. [status] is the HTTP
 * status code, or `-1` if the failure was an I/O error before a response
 * arrived. [message] carries the response body when available.
 */
class WalletApiException(
    val status: Int,
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

/**
 * Acquire the Wallet API namespace from a [LunaHeliusClient].
 *
 * The returned [WalletApi] is a lightweight view over the client — no mutable
 * state — so it is safe (and cheap) to rebuild on every property access.
 *
 * ```kotlin
 * import xyz.selenus.luna.wallet.wallet
 *
 * val balances = client.wallet.getBalances("86xCnPe...")
 * ```
 */
val LunaHeliusClient.wallet: WalletApi
    get() = WalletApi(this)

// ── Internal serialization helpers (file-private so they don't pollute API) ──

private fun WalletAtaFilter.serialName(): String = when (this) {
    WalletAtaFilter.NONE -> "none"
    WalletAtaFilter.BALANCE_CHANGED -> "balanceChanged"
    WalletAtaFilter.ALL -> "all"
}

private fun okhttp3.HttpUrl.Builder.addOptionalParam(name: String, value: Any?): okhttp3.HttpUrl.Builder {
    if (value != null) addQueryParameter(name, value.toString())
    return this
}

