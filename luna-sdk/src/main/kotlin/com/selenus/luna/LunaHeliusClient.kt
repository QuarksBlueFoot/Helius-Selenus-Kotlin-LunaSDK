package com.selenus.luna

import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable

import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * Enumeration of Solana clusters supported by Helius.  Mainnet is the default.
 */
enum class Cluster {
    /** Primary Solana network */
    MAINNET,
    /** Developer network for testing */
    DEVNET,
    /** Testnet network */
    TESTNET
}

/**
 * Generic JSON‑RPC request wrapper.  The [params] property is a JSON tree and can vary per
 * method.  See the Helius documentation for details on each method’s expected parameters.
 */
@Serializable
data class RpcRequest<T>(
    val jsonrpc: String = "2.0",
    val id: String,
    val method: String,
    val params: T
)

/**
 * Generic JSON‑RPC error returned by Helius when a request fails.
 */
@Serializable
data class RpcError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null
)

/**
 * Generic JSON‑RPC response wrapper.  When [error] is non‑null, [result] will be null.
 * When [error] is null, [result] contains the method‑specific payload.
 */
@Serializable
data class RpcResponse<T>(
    val jsonrpc: String = "2.0",
    val id: String? = null,
    val result: T? = null,
    val error: RpcError? = null
)

/**
 * Main entry point for interacting with the Helius API from Kotlin.  Pass your API key
 * and optionally a cluster to the constructor.  The client exposes namespaced APIs via
 * properties like [das], [rpc], [staking], [tx], [priority], [enhanced], [webhooks],
 * [ws], and [zk].  These namespaces group related RPC methods and hide the JSON‑RPC
 * plumbing from callers.
 *
 * Example:
 * ```kotlin
 * val helius = LunaHeliusClient("myKey")
 * val asset = helius.das.getAsset("assetId")
 * ```
 */
class LunaHeliusClient(
    private val apiKey: String,
    val cluster: Cluster = Cluster.MAINNET,
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
) {
    /**
     * Base URL for the selected cluster.  Note that the API key is appended as a query
     * parameter on each call.
     */
    private val baseUrl: String
        get() = when (cluster) {
            Cluster.MAINNET -> "https://mainnet.helius-rpc.com"
            Cluster.DEVNET -> "https://devnet.helius-rpc.com"
            Cluster.TESTNET -> "https://testnet.helius-rpc.com"
        }

    /**
     * Executes a JSON‑RPC call against Helius.  The [method] string is the name of the RPC
     * method (e.g. `getAsset`), and [params] is a `JsonElement` representing the method
     * arguments.  A [RpcResponse] containing a generic [JsonElement] is returned.  If
     * the HTTP layer returns an error or if Helius indicates an error in the response,
     * an exception will be thrown.
     *
     * @param method The RPC method name.
     * @param params The parameters for the RPC call.
     * @param queryParams Optional query parameters to append to the URL.
     */
    @Throws(Exception::class)
    suspend fun rpcCall(
        method: String,
        params: JsonElement,
        queryParams: Map<String, String> = emptyMap()
    ): RpcResponse<JsonElement> {
        // Construct the JSON‑RPC payload.  Use a fixed id of "1"; callers may set
        // their own id by wrapping this call if correlation is needed.
        val requestPayload = RpcRequest(
            id = "1",
            method = method,
            params = params
        )
        val requestBodyString = json.encodeToString(
            RpcRequest.serializer(JsonElement.serializer()),
            requestPayload
        )
        val mediaType = "application/json".toMediaType()
        val body = requestBodyString.toRequestBody(mediaType)

        val urlBuilder = baseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("api-key", apiKey)
        
        queryParams.forEach { (key, value) ->
            urlBuilder.addQueryParameter(key, value)
        }
        
        val url = urlBuilder.build()

        val request = Request.Builder()
            .url(url)
            .post(body)
            .header("Content-Type", "application/json")
            .build()
        httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string()
            if (!response.isSuccessful || responseBody == null) {
                throw Exception("Helius RPC call failed with HTTP ${response.code}")
            }
            val rpcResponse = json.decodeFromString(
                RpcResponse.serializer(JsonElement.serializer()),
                responseBody
            )
            // If Helius returned an error, throw it as an exception to fail fast.
            rpcResponse.error?.let { err ->
                throw Exception("Helius RPC error ${err.code}: ${err.message}")
            }
            return rpcResponse
        }
    }

    /**
     * Fetches the 75th percentile tip floor from Jito.
     * Returns null if the fetch fails.
     */
    private suspend fun fetchTipFloor(): Double? {
        val request = Request.Builder()
            .url("https://bundles.jito.wtf/api/v1/bundles/tip_floor")
            .header("Cache-Control", "no-store")
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val jsonElement = json.parseToJsonElement(body)
                // Expecting array, get first element, then landed_tips_75th_percentile
                jsonElement.jsonArray.getOrNull(0)
                    ?.jsonObject?.get("landed_tips_75th_percentile")
                    ?.jsonPrimitive?.doubleOrNull
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Sends a transaction via the Helius Sender API.
     */
    private suspend fun sendViaSender(
        transaction: String,
        region: SenderRegion,
        swqosOnly: Boolean
    ): String {
        val baseUrl = region.url
        val url = "$baseUrl/fast" + if (swqosOnly) "?swqos_only=true" else ""

        val payload = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", System.currentTimeMillis().toString())
            put("method", "sendTransaction")
            putJsonArray("params") {
                add(transaction)
                addJsonObject {
                    put("encoding", "base64")
                    put("skipPreflight", true)
                    put("maxRetries", 0)
                }
            }
        }

        val requestBody = json.encodeToString(JsonObject.serializer(), payload)
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        return httpClient.newCall(request).execute().use { response ->
            val bodyString = response.body?.string()
            if (!response.isSuccessful) {
                throw Exception("Sender HTTP ${response.code}: ${bodyString?.take(200)}")
            }

            if (bodyString == null) throw Exception("Empty response from Sender")

            // Parse response
            try {
                val element = json.parseToJsonElement(bodyString)
                if (element is JsonPrimitive && element.isString) {
                    return@use element.content
                } else if (element is JsonObject) {
                    if (element.containsKey("error")) {
                        throw Exception("Sender error: ${element["error"]}")
                    }
                    if (element.containsKey("result")) {
                        return@use element["result"]!!.jsonPrimitive.content
                    }
                }
            } catch (e: Exception) {
                // Fallthrough
            }
            throw Exception("Unexpected Sender response: ${bodyString.take(200)}")
        }
    }


    /** Provides access to the Digital Asset Standard (DAS) API methods. */
    val das: DasApi = DasApi()
    /** Provides access to enhanced Solana RPC methods, such as getProgramAccountsV2. */
    val rpc: RpcApi = RpcApi()
    /** Provides access to staking helper methods. */
    val staking: StakingApi = StakingApi()
    /** Provides access to transaction helper methods. */
    val tx: TransactionApi = TransactionApi()
    /** Provides access to the priority fee estimation API. */
    val priority: PriorityFeeApi = PriorityFeeApi()
    /** Provides access to the enhanced transactions API. */
    val enhanced: EnhancedApi = EnhancedApi()
    /** Provides access to webhooks API for creating and managing webhooks. */
    val webhooks: WebhookApi = WebhookApi()
    /** Provides access to WebSocket subscriptions and message generation. */
    val ws: WebSocketApi = WebSocketApi()
    /** Provides access to ZK Compression helper methods. */
    val zk: ZkCompressionApi = ZkCompressionApi()
    /** Provides access to the Helius Sender API. */
    val sender: SenderApi = SenderApi()
    /** Provides access to LaserStream configuration and endpoints. */
    val laser: LaserStreamApi = LaserStreamApi()
    /** Provides access to standard Solana RPC methods (e.g. getBalance, getAccountInfo). */
    val solana: SolanaApi = SolanaApi()

    /**
     * Sorting options accepted by certain DAS endpoints.  See `getAssetsByOwner` for usage.
     * `sortBy` corresponds to the field to sort on (e.g. "created", "updated").
     * `sortDirection` should be either "asc" or "desc".
     */
    data class SortBy(val sortBy: String, val sortDirection: String)

    /**
     * Regions supported by the Helius Sender API.
     */
    enum class SenderRegion(val url: String) {
        DEFAULT("https://sender.helius-rpc.com"),
        US_SLC("http://slc-sender.helius-rpc.com"),
        US_EAST("http://ewr-sender.helius-rpc.com"),
        EU_WEST("http://lon-sender.helius-rpc.com"),
        EU_CENTRAL("http://fra-sender.helius-rpc.com"),
        EU_NORTH("http://ams-sender.helius-rpc.com"),
        AP_SINGAPORE("http://sg-sender.helius-rpc.com"),
        AP_TOKYO("http://tyo-sender.helius-rpc.com")
    }

    companion object {
        val SENDER_TIP_ACCOUNTS = listOf(
            "4ACfpUFoaSD9bfPdeu6DBt89gB6ENTeHBXCAi87NhDEE",
            "D2L6yPZ2FmmmTKPgzaMKdhu6EWZcTpLy1Vhx8uvZe7NZ",
            "9bnz4RShgq1hAnLnZbP8kbgBg1kEmcJBYQq3gQbmnSta",
            "5VY91ws6B2hMmBFRsXkoAAdsPHBJwRfBht4DXox3xkwn",
            "2nyhqdwKcJZR2vcqCyrYsaPVdAnFoJjiksCXJ7hfEYgD",
            "2q5pghRs6arqVjRvT5gfgWfWcHWmw1ZuCzphgd5KfWGJ",
            "wyvPkWjVZz1M8fHQnMMCDTQDbkManefNNhweYk5WkcF",
            "3KCKozbAaF75qEU33jtzozcJ29yJuaLJTy2jFdzUY8bT",
            "4vieeGHPYPG2MmyPRcYjdiDmmhN3ww7hsFNap8pVN3Ey",
            "4TQLFNWK8AovT1gFvda5jfw2oJeRMKEmw7aH6MGBJ3or"
        )
    }


    /** Digital Asset Standard (DAS) API namespace. */
    inner class DasApi {
        /**
         * Fetch a single asset by its unique identifier.  Returns a JSON tree containing
         * on‑chain and off‑chain metadata, ownership details and compression state for any
         * Solana digital asset.
         *
         * @param assetId The mint address or asset ID of the NFT, token or cNFT.
         * @param showFungible Whether to show fungible tokens.
         * @param showUnverifiedCollections Whether to show unverified collections.
         * @param showCollectionMetadata Whether to show collection metadata.
         * @param showInscription Whether to show inscription data.
         */
        suspend fun getAsset(
            assetId: String,
            showFungible: Boolean? = null,
            showUnverifiedCollections: Boolean? = null,
            showCollectionMetadata: Boolean? = null,
            showInscription: Boolean? = null
        ): RpcResponse<JsonElement> {
            val options = buildJsonObject {
                showFungible?.let { put("showFungible", it) }
                showUnverifiedCollections?.let { put("showUnverifiedCollections", it) }
                showCollectionMetadata?.let { put("showCollectionMetadata", it) }
                showInscription?.let { put("showInscription", it) }
            }
            val params = buildJsonObject {
                put("id", assetId)
                if (options.isNotEmpty()) put("options", options)
            }
            return rpcCall("getAsset", params)
        }

        /**
         * Retrieve a list of digital assets owned by a wallet with optional pagination and
         * sorting.  This is the easiest way to fetch all NFTs and fungible
         * tokens held by a user.
         *
         * @param ownerAddress Wallet address whose assets should be listed.
         * @param page Optional page number (1‑indexed).  When omitted the first page is returned.
         * @param limit Optional page size.  When omitted the default server limit is used.
         * @param sortBy Optional sort specification.
         * @param before Optional cursor for pagination (before this asset ID).
         * @param after Optional cursor for pagination (after this asset ID).
         * @param showFungible Whether to show fungible tokens.
         * @param showUnverifiedCollections Whether to show unverified collections.
         * @param showCollectionMetadata Whether to show collection metadata.
         * @param showInscription Whether to show inscription data.
         */
        suspend fun getAssetsByOwner(
            ownerAddress: String,
            page: Int? = null,
            limit: Int? = null,
            sortBy: SortBy? = null,
            before: String? = null,
            after: String? = null,
            showFungible: Boolean? = null,
            showUnverifiedCollections: Boolean? = null,
            showCollectionMetadata: Boolean? = null,
            showInscription: Boolean? = null
        ): RpcResponse<JsonElement> {
            val options = buildJsonObject {
                showFungible?.let { put("showFungible", it) }
                showUnverifiedCollections?.let { put("showUnverifiedCollections", it) }
                showCollectionMetadata?.let { put("showCollectionMetadata", it) }
                showInscription?.let { put("showInscription", it) }
            }
            val params = buildJsonObject {
                put("ownerAddress", ownerAddress)
                page?.let { put("page", it) }
                limit?.let { put("limit", it) }
                before?.let { put("before", it) }
                after?.let { put("after", it) }
                sortBy?.let { sort ->
                    putJsonObject("sortBy") {
                        put("sortBy", sort.sortBy)
                        put("sortDirection", sort.sortDirection)
                    }
                }
                if (options.isNotEmpty()) put("options", options)
            }
            return rpcCall("getAssetsByOwner", params)
        }

        /**
         * Search for assets by arbitrary fields.  Accepts a JSON object of search filters
         * as documented in the Helius searchAssets endpoint.  Passing an empty map
         * returns all assets.  See the official docs for supported search keys.
         */
        suspend fun searchAssets(filters: Map<String, String>): RpcResponse<JsonElement> {
            val params = buildJsonObject {
                for ((k, v) in filters) put(k, v)
            }
            return rpcCall("searchAssets", params)
        }

        /**
         * Fetch multiple assets by their IDs (up to 1 000).  Use this method when you
         * need to fetch many assets in a single request.
         * @param assetIds A list of asset identifiers.
         * @param showFungible Whether to show fungible tokens.
         * @param showUnverifiedCollections Whether to show unverified collections.
         * @param showCollectionMetadata Whether to show collection metadata.
         * @param showInscription Whether to show inscription data.
         */
        suspend fun getAssetBatch(
            assetIds: List<String>,
            showFungible: Boolean? = null,
            showUnverifiedCollections: Boolean? = null,
            showCollectionMetadata: Boolean? = null,
            showInscription: Boolean? = null
        ): RpcResponse<JsonElement> {
            val options = buildJsonObject {
                showFungible?.let { put("showFungible", it) }
                showUnverifiedCollections?.let { put("showUnverifiedCollections", it) }
                showCollectionMetadata?.let { put("showCollectionMetadata", it) }
                showInscription?.let { put("showInscription", it) }
            }
            val params = buildJsonObject {
                put("ids", JsonArray(assetIds.map { JsonPrimitive(it) }))
                if (options.isNotEmpty()) put("options", options)
            }
            return rpcCall("getAssetBatch", params)
        }


        /**
         * Retrieve a Merkle proof for a compressed NFT by its ID【128353577680464†L142-L147】.
         * @param assetId The identifier of the compressed asset.
         */
        suspend fun getAssetProof(assetId: String): RpcResponse<JsonElement> {
            val params = buildJsonObject { put("id", assetId) }
            return rpcCall("getAssetProof", params)
        }

        /**
         * Fetch Merkle proofs for multiple compressed NFTs【128353577680464†L146-L148】.
         * @param assetIds The list of compressed asset IDs.
         */
        suspend fun getAssetProofBatch(assetIds: List<String>): RpcResponse<JsonElement> {
            val params = buildJsonObject {
                put("ids", JsonArray(assetIds.map { JsonPrimitive(it) }))
            }
            return rpcCall("getAssetProofBatch", params)
        }

        /**
         * Get a list of assets with a specific authority.
         * @param authorityAddress The authority address.
         * @param page Optional page number.
         * @param limit Optional page size.
         * @param before Optional cursor for pagination.
         * @param after Optional cursor for pagination.
         * @param showFungible Whether to show fungible tokens.
         * @param showUnverifiedCollections Whether to show unverified collections.
         * @param showCollectionMetadata Whether to show collection metadata.
         * @param showInscription Whether to show inscription data.
         */
        suspend fun getAssetsByAuthority(
            authorityAddress: String,
            page: Int? = null,
            limit: Int? = null,
            before: String? = null,
            after: String? = null,
            showFungible: Boolean? = null,
            showUnverifiedCollections: Boolean? = null,
            showCollectionMetadata: Boolean? = null,
            showInscription: Boolean? = null
        ): RpcResponse<JsonElement> {
            val options = buildJsonObject {
                showFungible?.let { put("showFungible", it) }
                showUnverifiedCollections?.let { put("showUnverifiedCollections", it) }
                showCollectionMetadata?.let { put("showCollectionMetadata", it) }
                showInscription?.let { put("showInscription", it) }
            }
            val params = buildJsonObject {
                put("authorityAddress", authorityAddress)
                page?.let { put("page", it) }
                limit?.let { put("limit", it) }
                before?.let { put("before", it) }
                after?.let { put("after", it) }
                if (options.isNotEmpty()) put("options", options)
            }
            return rpcCall("getAssetsByAuthority", params)
        }

        /**
         * Retrieve a list of assets created by the given creator address.
         * @param creatorAddress The address of the asset creator.
         * @param page Optional page number.
         * @param limit Optional page size.
         * @param before Optional cursor for pagination.
         * @param after Optional cursor for pagination.
         * @param showFungible Whether to show fungible tokens.
         * @param showUnverifiedCollections Whether to show unverified collections.
         * @param showCollectionMetadata Whether to show collection metadata.
         * @param showInscription Whether to show inscription data.
         */
        suspend fun getAssetsByCreator(
            creatorAddress: String,
            page: Int? = null,
            limit: Int? = null,
            before: String? = null,
            after: String? = null,
            showFungible: Boolean? = null,
            showUnverifiedCollections: Boolean? = null,
            showCollectionMetadata: Boolean? = null,
            showInscription: Boolean? = null
        ): RpcResponse<JsonElement> {
            val options = buildJsonObject {
                showFungible?.let { put("showFungible", it) }
                showUnverifiedCollections?.let { put("showUnverifiedCollections", it) }
                showCollectionMetadata?.let { put("showCollectionMetadata", it) }
                showInscription?.let { put("showInscription", it) }
            }
            val params = buildJsonObject {
                put("creatorAddress", creatorAddress)
                page?.let { put("page", it) }
                limit?.let { put("limit", it) }
                before?.let { put("before", it) }
                after?.let { put("after", it) }
                if (options.isNotEmpty()) put("options", options)
            }
            return rpcCall("getAssetsByCreator", params)
        }

        /**
         * Return assets that belong to a specific group key and value.
         * Useful for fetching mints for NFT collections.
         * @param groupKey The group key (e.g. "collection").
         * @param groupValue The value for the group key.
         * @param page Optional page number.
         * @param limit Optional page size.
         * @param before Optional cursor for pagination.
         * @param after Optional cursor for pagination.
         * @param showFungible Whether to show fungible tokens.
         * @param showUnverifiedCollections Whether to show unverified collections.
         * @param showCollectionMetadata Whether to show collection metadata.
         * @param showInscription Whether to show inscription data.
         */
        suspend fun getAssetsByGroup(
            groupKey: String,
            groupValue: String,
            page: Int? = null,
            limit: Int? = null,
            before: String? = null,
            after: String? = null,
            showFungible: Boolean? = null,
            showUnverifiedCollections: Boolean? = null,
            showCollectionMetadata: Boolean? = null,
            showInscription: Boolean? = null
        ): RpcResponse<JsonElement> {
            val options = buildJsonObject {
                showFungible?.let { put("showFungible", it) }
                showUnverifiedCollections?.let { put("showUnverifiedCollections", it) }
                showCollectionMetadata?.let { put("showCollectionMetadata", it) }
                showInscription?.let { put("showInscription", it) }
            }
            val params = buildJsonObject {
                put("groupKey", groupKey)
                put("groupValue", groupValue)
                page?.let { put("page", it) }
                limit?.let { put("limit", it) }
                before?.let { put("before", it) }
                after?.let { put("after", it) }
                if (options.isNotEmpty()) put("options", options)
            }
            return rpcCall("getAssetsByGroup", params)
        }


        /**
         * Get edition NFTs for a given master NFT.
         * @param masterAssetId The master NFT’s asset ID.
         * @param page Optional page number.
         * @param limit Optional page size.
         */
        suspend fun getNftEditions(
            masterAssetId: String,
            page: Int? = null,
            limit: Int? = null
        ): RpcResponse<JsonElement> {
            val params = buildJsonObject {
                put("id", masterAssetId)
                page?.let { put("page", it) }
                limit?.let { put("limit", it) }
            }
            return rpcCall("getNftEditions", params)
        }

        /**
         * Return token accounts by mint or by owner.
         * Provide either a `mint` to fetch all accounts for a token, or an `owner`
         * address to fetch all token accounts owned by that address.
         * @param mint Optional token mint address.
         * @param owner Optional owner address.
         * @param page Optional page number.
         * @param limit Optional page size.
         * @param before Optional cursor for pagination.
         * @param after Optional cursor for pagination.
         * @param showZeroBalance Whether to show accounts with zero balance.
         */
        suspend fun getTokenAccounts(
            mint: String? = null,
            owner: String? = null,
            page: Int? = null,
            limit: Int? = null,
            before: String? = null,
            after: String? = null,
            showZeroBalance: Boolean? = null
        ): RpcResponse<JsonElement> {
            val options = buildJsonObject {
                showZeroBalance?.let { put("showZeroBalance", it) }
            }
            val params = buildJsonObject {
                mint?.let { put("mint", it) }
                owner?.let { put("owner", it) }
                page?.let { put("page", it) }
                limit?.let { put("limit", it) }
                before?.let { put("before", it) }
                after?.let { put("after", it) }
                if (options.isNotEmpty()) put("options", options)
            }
            return rpcCall("getTokenAccounts", params)
        }


        /**
         * Retrieve transaction signatures involving a specific asset (NFT or token)
         * with chronological order.
         *
         * @param assetId The asset identifier.
         * @param page The page number (1-indexed).
         * @param limit The maximum number of signatures to return.
         * @param before The cursor for paginating backwards.
         * @param after The cursor for paginating forwards.
         */
        suspend fun getSignaturesForAsset(
            assetId: String,
            page: Int? = null,
            limit: Int? = null,
            before: String? = null,
            after: String? = null
        ): RpcResponse<JsonElement> {
            val params = buildJsonObject {
                put("id", assetId)
                page?.let { put("page", it) }
                limit?.let { put("limit", it) }
                before?.let { put("before", it) }
                after?.let { put("after", it) }
            }
            return rpcCall("getSignaturesForAsset", params)
        }
    }

    /** Enhanced RPC methods namespace. */
    inner class RpcApi {
        /**
         * Enhanced version of getProgramAccounts that supports pagination and incremental
         * updates【128353577680464†L171-L187】.  Returns account data and a pagination key when more
         * results are available.
         *
         * @param programId The public key of the program whose accounts should be listed.
         * @param encoding Optional data encoding (e.g. "base64", "base64+zstd").
         * @param limit Optional page size.
         * @param paginationKey Optional pagination key from a previous response.
         * @param changedSinceSlot Optional slot to return accounts changed after this slot.
         */
        suspend fun getProgramAccountsV2(
            programId: String,
            encoding: String? = null,
            limit: Int? = null,
            paginationKey: String? = null,
            changedSinceSlot: Long? = null
        ): RpcResponse<JsonElement> {
            // Build the options object.  The JSON‑RPC method expects an array with
            // [programId, options] rather than a named object.
            val options = buildJsonObject {
                encoding?.let { put("encoding", it) }
                limit?.let { put("limit", it) }
                paginationKey?.let { put("paginationKey", it) }
                changedSinceSlot?.let { put("changedSinceSlot", it) }
            }
            val params = buildJsonArray {
                add(JsonPrimitive(programId))
                add(options)
            }
            return rpcCall("getProgramAccountsV2", params)
        }

        /**
         * Auto‑paginate through all program accounts for a given program.  Use
         * this with caution on large programs because it can produce a large
         * response【128353577680464†L180-L186】.
         * @param programId The program ID.
         * @param encoding Optional encoding.
         */
        suspend fun getAllProgramAccounts(
            programId: String,
            encoding: String? = null
        ): RpcResponse<JsonElement> {
            val options = buildJsonObject {
                encoding?.let { put("encoding", it) }
            }
            val params = buildJsonArray {
                add(JsonPrimitive(programId))
                add(options)
            }
            return rpcCall("getAllProgramAccounts", params)
        }

        /**
         * Enhanced version of getTokenAccountsByOwner with pagination and
         * incremental update support【128353577680464†L182-L184】.
         * @param owner The owner address to query.
         * @param mint Optional mint address to filter by.
         * @param limit Optional page size.
         * @param paginationKey Optional pagination key from a previous response.
         * @param changedSinceSlot Optional slot for incremental updates.
         */
        suspend fun getTokenAccountsByOwnerV2(
            owner: String,
            mint: String? = null,
            limit: Int? = null,
            paginationKey: String? = null,
            changedSinceSlot: Long? = null
        ): RpcResponse<JsonElement> {
            val options = buildJsonObject {
                mint?.let { put("mint", it) }
                limit?.let { put("limit", it) }
                paginationKey?.let { put("paginationKey", it) }
                changedSinceSlot?.let { put("changedSinceSlot", it) }
            }
            val params = buildJsonArray {
                add(JsonPrimitive(owner))
                add(options)
            }
            return rpcCall("getTokenAccountsByOwnerV2", params)
        }

        /**
         * Auto‑paginate through all token accounts owned by a given address【128353577680464†L185-L186】.
         * @param owner The owner’s public key.
         * @param mint Optional mint filter.
         */
        suspend fun getAllTokenAccountsByOwner(owner: String, mint: String? = null): RpcResponse<JsonElement> {
            val options = buildJsonObject {
                mint?.let { put("mint", it) }
            }
            val params = buildJsonArray {
                add(JsonPrimitive(owner))
                add(options)
            }
            return rpcCall("getAllTokenAccountsByOwner", params)
        }

        /**
         * Retrieve transaction history for a given address with advanced filtering
         * and sorting.
         *
         * @param address The address to query.
         * @param options A map of additional options for filtering, sorting and pagination.
         */
        suspend fun getTransactionsForAddress(
            address: String,
            options: Map<String, JsonElement> = emptyMap()
        ): RpcResponse<JsonElement> {
            val optionsObj = JsonObject(options)
            val params = buildJsonArray {
                add(JsonPrimitive(address))
                add(optionsObj)
            }
            return rpcCall("getTransactionsForAddress", params)
        }

        /**
         * Retrieve transaction history for a given address with advanced filtering
         * and sorting (Strongly Typed Overload).
         *
         * @param address The address to query.
         * @param transactionDetails Level of detail: "signatures" or "full".
         * @param sortOrder "asc" (oldest first) or "desc" (newest first).
         * @param limit Max transactions to return (1000 for signatures, 100 for full).
         * @param paginationToken Token for fetching the next page.
         * @param commitment Commitment level (e.g. "finalized").
         * @param filters Advanced filtering options (slot, blockTime, signature, status).
         * @param encoding Encoding for transaction data (only for "full" details).
         * @param maxSupportedTransactionVersion Max transaction version to return.
         * @param minContextSlot Minimum slot for request evaluation.
         */
        suspend fun getTransactionsForAddress(
            address: String,
            transactionDetails: String? = null,
            sortOrder: String? = null,
            limit: Int? = null,
            paginationToken: String? = null,
            commitment: String? = null,
            filters: JsonObject? = null,
            encoding: String? = null,
            maxSupportedTransactionVersion: Int? = null,
            minContextSlot: Long? = null
        ): RpcResponse<JsonElement> {
            val options = buildJsonObject {
                transactionDetails?.let { put("transactionDetails", it) }
                sortOrder?.let { put("sortOrder", it) }
                limit?.let { put("limit", it) }
                paginationToken?.let { put("paginationToken", it) }
                commitment?.let { put("commitment", it) }
                filters?.let { put("filters", it) }
                encoding?.let { put("encoding", it) }
                maxSupportedTransactionVersion?.let { put("maxSupportedTransactionVersion", it) }
                minContextSlot?.let { put("minContextSlot", it) }
            }
            val params = buildJsonArray {
                add(JsonPrimitive(address))
                add(options)
            }
            return rpcCall("getTransactionsForAddress", params)
        }
    }

    /**
     * Staking helper methods.  These methods wrap Helius’ staking endpoints which
     * produce transactions or instructions for delegating, undelegating and
     * withdrawing lamports from Solana stake accounts【128353577680464†L194-L206】.
     */
    inner class StakingApi {
        /**
         * Create a transaction to open and delegate a new stake account to the Helius
         * validator.  The resulting transaction must be signed and broadcast by the caller.
         *
         * @param wallet Wallet address funding the stake account.
         * @param amountLamports Amount of lamports to delegate.
         * @param validatorVoteAddress Vote account of the validator to delegate to.
         */
        suspend fun createStakeTransaction(
            wallet: String,
            amountLamports: Long,
            validatorVoteAddress: String
        ): RpcResponse<JsonElement> {
            val params = buildJsonObject {
                put("wallet", wallet)
                put("amountLamports", amountLamports)
                put("validatorVoteAddress", validatorVoteAddress)
            }
            return rpcCall("createStakeTransaction", params)
        }

        /** Generate a transaction to deactivate an existing stake account. */
        suspend fun createUnstakeTransaction(
            stakeAccount: String
        ): RpcResponse<JsonElement> {
            val params = buildJsonObject {
                put("stakeAccount", stakeAccount)
            }
            return rpcCall("createUnstakeTransaction", params)
        }

        /** Generate a transaction to withdraw lamports from a stake account. */
        suspend fun createWithdrawTransaction(
            stakeAccount: String,
            amountLamports: Long
        ): RpcResponse<JsonElement> {
            val params = buildJsonObject {
                put("stakeAccount", stakeAccount)
                put("amountLamports", amountLamports)
            }
            return rpcCall("createWithdrawTransaction", params)
        }

        /**
         * Return the instructions for creating and delegating a stake account without
         * constructing the full transaction【128353577680464†L200-L203】.
         * Useful when combining instructions in a custom transaction.
         * @param wallet Funding wallet address.
         * @param amountLamports Amount of lamports to delegate.
         * @param validatorVoteAddress Vote account of the validator.
         */
        suspend fun getStakeInstructions(
            wallet: String,
            amountLamports: Long,
            validatorVoteAddress: String
        ): RpcResponse<JsonElement> {
            val params = buildJsonObject {
                put("wallet", wallet)
                put("amountLamports", amountLamports)
                put("validatorVoteAddress", validatorVoteAddress)
            }
            return rpcCall("getStakeInstructions", params)
        }

        /**
         * Return the instruction to deactivate a stake account【128353577680464†L202-L203】.
         * @param stakeAccount The stake account to deactivate.
         */
        suspend fun getUnstakeInstruction(stakeAccount: String): RpcResponse<JsonElement> {
            val params = buildJsonObject { put("stakeAccount", stakeAccount) }
            return rpcCall("getUnstakeInstruction", params)
        }

        /**
         * Return the instruction to withdraw lamports from a stake account【128353577680464†L203-L205】.
         * @param stakeAccount The stake account to withdraw from.
         * @param amountLamports The amount of lamports to withdraw.
         */
        suspend fun getWithdrawInstruction(
            stakeAccount: String,
            amountLamports: Long
        ): RpcResponse<JsonElement> {
            val params = buildJsonObject {
                put("stakeAccount", stakeAccount)
                put("amountLamports", amountLamports)
            }
            return rpcCall("getWithdrawInstruction", params)
        }

        /**
         * Determine how many lamports are withdrawable from a stake account【128353577680464†L206-L207】.
         * @param stakeAccount The stake account.
         * @param includeRentExempt Whether to include the rent‑exempt reserve.
         */
        suspend fun getWithdrawableAmount(
            stakeAccount: String,
            includeRentExempt: Boolean? = null
        ): RpcResponse<JsonElement> {
            val params = buildJsonObject {
                put("stakeAccount", stakeAccount)
                includeRentExempt?.let { put("includeRentExempt", it) }
            }
            return rpcCall("getWithdrawableAmount", params)
        }

        /**
         * Return all stake accounts delegated to the Helius validator for a given wallet【128353577680464†L208-L209】.
         * @param wallet The wallet address.
         */
        suspend fun getHeliusStakeAccounts(wallet: String): RpcResponse<JsonElement> {
            val params = buildJsonObject { put("wallet", wallet) }
            return rpcCall("getHeliusStakeAccounts", params)
        }
    }

    /**
     * Transaction helper methods.  These simplify sending and managing Solana transactions
     * through Helius.  All methods return a raw JSON tree; see the official docs for
     * expected response fields【128353577680464†L214-L233】.
     */
    inner class TransactionApi {
        /** Fetch the estimated compute units a transaction will consume. */
        suspend fun getComputeUnits(transaction: String): RpcResponse<JsonElement> {
            val params = buildJsonObject { put("transaction", transaction) }
            return rpcCall("getComputeUnits", params)
        }

        /**
         * Broadcast a fully signed transaction and poll for confirmation.  The
         * transaction must be base64 encoded and signed client‑side.
         */
        suspend fun broadcastTransaction(serializedTransaction: String): RpcResponse<JsonElement> {
            val params = buildJsonObject { put("transaction", serializedTransaction) }
            return rpcCall("broadcastTransaction", params)
        }

        /**
         * Send a serialized transaction to the Solana network with optional encoding.
         * Defaults to base64 encoding.
         *
         * @param transaction The serialized transaction.
         * @param encoding The encoding of the transaction string (default: "base64").
         * @param rebateAddress Optional SOL address to receive backrun rebates (mainnet only).
         */
        suspend fun sendTransaction(
            transaction: String,
            encoding: String = "base64",
            rebateAddress: String? = null
        ): RpcResponse<JsonElement> {
            val params = buildJsonObject {
                put("transaction", transaction)
                put("encoding", encoding)
            }
            val queryParams = if (rebateAddress != null) {
                mapOf("rebate-address" to rebateAddress)
            } else {
                emptyMap()
            }
            return rpcCall("sendTransaction", params, queryParams)
        }

        /**
         * Poll a transaction until it has been confirmed.
         * @param signature The transaction signature to poll.
         * @param timeoutMs Max time to wait in milliseconds (default 60000).
         * @param intervalMs Polling interval in milliseconds (default 2000).
         */
        suspend fun pollTransactionConfirmation(
            signature: String,
            timeoutMs: Long = 60000,
            intervalMs: Long = 2000
        ): RpcResponse<JsonElement> {
            val start = System.currentTimeMillis()
            while (System.currentTimeMillis() - start < timeoutMs) {
                try {
                    val response = solana.getSignatureStatuses(listOf(signature))
                    val statuses = response.result?.jsonArray
                    val status = statuses?.getOrNull(0)?.jsonObject
                    
                    if (status != null && status["confirmationStatus"] != JsonNull) {
                        val confirmationStatus = status["confirmationStatus"]?.jsonPrimitive?.content
                        if (confirmationStatus == "confirmed" || confirmationStatus == "finalized") {
                            return response
                        }
                    }
                } catch (e: Exception) {
                    // Ignore errors and retry
                }
                delay(intervalMs)
            }
            throw Exception("Transaction confirmation timed out for signature: $signature")
        }

        /**
         * Create a smart transaction given a configuration.
         * Note: This method is currently not supported by the Helius RPC and requires client-side implementation.
         */
        suspend fun createSmartTransaction(config: JsonObject): RpcResponse<JsonElement> {
            return rpcCall("createSmartTransaction", config)
        }

        /**
         * Build and send an optimized smart transaction in a single call.
         * Note: This method is currently not supported by the Helius RPC and requires client-side implementation.
         */
        suspend fun sendSmartTransaction(config: JsonObject): RpcResponse<JsonElement> {
            return rpcCall("sendSmartTransaction", config)
        }

        /**
         * Submit a transaction using the ultra‑low latency Helius Sender service.
         * This method routes the transaction to validators and Jito infrastructure.
         * 
         * Note: The transaction must be fully signed. If you wish to include a Jito tip,
         * you must add the tip instruction (transfer to one of SENDER_TIP_ACCOUNTS)
         * before signing.
         *
         * @param transaction The base64 encoded transaction.
         * @param region The Sender region to use (default: DEFAULT).
         * @param swqosOnly Whether to use SWQOS-only routing (default: false).
         */
        suspend fun sendTransactionWithSender(
            transaction: String,
            region: SenderRegion = SenderRegion.DEFAULT,
            swqosOnly: Boolean = false
        ): RpcResponse<JsonElement> {
            val signature = sendViaSender(transaction, region, swqosOnly)
            return pollTransactionConfirmation(signature)
        }
        
        /**
         * Get the current Jito tip floor (75th percentile).
         */
        suspend fun getSenderTipFloor(): RpcResponse<JsonElement> {
            val floor = fetchTipFloor()
            return RpcResponse(
                result = if (floor != null) JsonPrimitive(floor) else JsonNull,
                id = "1" // Default ID
            )
        }
    }

    /**
     * Priority fee estimation API.  Use this to estimate network fees for a transaction
     * given a desired priority level.
     */
    inner class PriorityFeeApi {
        /**
         * Estimate the fee per compute unit needed to achieve a certain priority level.
         * @param transaction The base58 or base64 encoded transaction string.
         * @param accountKeys A list of account keys involved in the transaction (alternative to passing transaction).
         * @param priorityLevel One of "Min", "Low", "Medium", "High", "VeryHigh", "UnsafeMax", or "Default".
         * @param includeAllPriorityFeeLevels If true, returns estimates for all priority levels.
         * @param lookbackSlots Number of slots to look back for estimation.
         */
        suspend fun getPriorityFeeEstimate(
            transaction: String? = null,
            accountKeys: List<String>? = null,
            priorityLevel: String? = null,
            includeAllPriorityFeeLevels: Boolean? = null,
            lookbackSlots: Int? = null
        ): RpcResponse<JsonElement> {
            val options = buildJsonObject {
                priorityLevel?.let { put("priorityLevel", it) }
                includeAllPriorityFeeLevels?.let { put("includeAllPriorityFeeLevels", it) }
                lookbackSlots?.let { put("lookbackSlots", it) }
            }

            val paramsObj = buildJsonObject {
                transaction?.let { put("transaction", it) }
                accountKeys?.let { keys ->
                    put("accountKeys", JsonArray(keys.map { JsonPrimitive(it) }))
                }
                if (options.isNotEmpty()) {
                    put("options", options)
                }
            }
            
            // The RPC expects an array containing the parameters object
            val params = buildJsonArray { add(paramsObj) }
            return rpcCall("getPriorityFeeEstimate", params)
        }
    }


    /**
     * Enhanced Transactions API.  Converts raw transaction data into human readable
     * form and fetches transactions by address【128353577680464†L245-L256】.
     */
    inner class EnhancedApi {
        /**
         * Convert one or more transaction signatures into enhanced, human readable
         * transaction descriptions.
         * @param signatures List of transaction signatures to decode.
         */
        suspend fun getTransactions(signatures: List<String>): RpcResponse<JsonElement> {
            val params = buildJsonObject {
                put("signatures", JsonArray(signatures.map { JsonPrimitive(it) }))
            }
            return rpcCall("getTransactions", params)
        }

        /**
         * Retrieve enhanced transactions for a given address with optional pagination.
         * @param address The wallet or program address to fetch transactions for.
         * @param page Optional page number.
         * @param limit Optional page size.
         * @param before Optional signature to fetch transactions before (for pagination).
         * @param until Optional signature to fetch transactions until (for pagination).
         */
        suspend fun getTransactionsByAddress(
            address: String,
            page: Int? = null,
            limit: Int? = null,
            before: String? = null,
            until: String? = null
        ): RpcResponse<JsonElement> {
            val params = buildJsonObject {
                put("address", address)
                page?.let { put("page", it) }
                limit?.let { put("limit", it) }
                before?.let { put("before", it) }
                until?.let { put("until", it) }
            }
            return rpcCall("getTransactionsByAddress", params)
        }
    }

    /**
     * Webhooks API.  Enables developers to subscribe to on‑chain events such as sales,
     * listings, swaps or account changes and receive HTTP callbacks when those events
     * occur【128353577680464†L260-L276】.
     */
    inner class WebhookApi {
        /**
         * Create a new webhook subscription.  The returned object contains the ID
         * required to modify or delete the webhook later.
         *
         * @param webhookUrl The URL that Helius will call when the event fires.
         * @param accountAddresses Solana addresses to monitor; events referencing these
         *                         addresses will trigger the webhook.
         * @param transactionTypes Types of transactions to listen for (e.g. "all",
         *                         "token-transfer", "swap").
         * @param webhookType Type of webhook, defaults to "account".
         * @param authHeader Optional authorization header Helius should include when
         *                   invoking your webhook.  Useful for securing the endpoint.
         * @param version Webhook version number; default is 1.
         */
        suspend fun createWebhook(
            webhookUrl: String,
            accountAddresses: List<String>,
            transactionTypes: List<String>,
            webhookType: String = "account",
            authHeader: String? = null,
            version: Int = 1
        ): RpcResponse<JsonElement> {
            val body = buildJsonObject {
                put("webhookUrl", webhookUrl)
                put("address", JsonArray(accountAddresses.map { JsonPrimitive(it) }))
                put("type", webhookType)
                put("transactionTypes", JsonArray(transactionTypes.map { JsonPrimitive(it) }))
                authHeader?.let { put("authorizationHeader", it) }
                put("version", version)
            }
            return rpcCall("createWebhook", body)
        }

        /** Retrieve all webhooks associated with your API key. */
        suspend fun getAllWebhooks(): RpcResponse<JsonElement> {
            // The getAllWebhooks method takes an empty object as parameters.
            return rpcCall("getAllWebhooks", JsonObject(emptyMap()))
        }

        /** Fetch a single webhook by its ID. */
        suspend fun getWebhookById(webhookId: String): RpcResponse<JsonElement> {
            val params = buildJsonObject { put("webhookID", webhookId) }
            return rpcCall("getWebhookByID", params)
        }

        /** Update a webhook by its ID.  Only fields present in [updates] will be changed. */
        suspend fun updateWebhook(
            webhookId: String,
            updates: Map<String, JsonElement>
        ): RpcResponse<JsonElement> {
            val params = buildJsonObject {
                put("webhookID", webhookId)
                put("updates", JsonObject(updates))
            }
            return rpcCall("updateWebhook", params)
        }

        /** Delete a webhook subscription permanently. */
        suspend fun deleteWebhook(webhookId: String): RpcResponse<JsonElement> {
            val params = buildJsonObject { put("webhookID", webhookId) }
            return rpcCall("deleteWebhook", params)
        }
    }

    /**
     * WebSocket API.  Provides methods to connect to the Helius WebSocket endpoint
     * and helper methods to generate subscription messages.
     */
    inner class WebSocketApi {
        private val wssUrl: String
            get() = when (cluster) {
                Cluster.MAINNET -> "wss://mainnet.helius-rpc.com/?api-key=$apiKey"
                Cluster.DEVNET -> "wss://devnet.helius-rpc.com/?api-key=$apiKey"
                Cluster.TESTNET -> "wss://testnet.helius-rpc.com/?api-key=$apiKey"
            }

        /**
         * Opens a WebSocket connection to Helius.
         * @param listener A standard OkHttp WebSocketListener to receive events.
         * @return The WebSocket instance, which can be used to send messages and close the connection.
         */
        fun connect(listener: WebSocketListener): WebSocket {
            val request = Request.Builder().url(wssUrl).build()
            return httpClient.newWebSocket(request, listener)
        }

        /**
         * Generates the JSON message to subscribe to account updates.
         * @param pubkey The account address to monitor.
         * @param commitment Optional commitment level.
         * @param encoding Optional encoding (base58, base64, etc).
         */
        fun accountSubscribe(pubkey: String, commitment: String? = null, encoding: String? = null): String {
            val config = buildJsonObject {
                commitment?.let { put("commitment", it) }
                encoding?.let { put("encoding", it) }
            }
            val params = buildJsonArray {
                add(JsonPrimitive(pubkey))
                if (config.isNotEmpty()) add(config)
            }
            return buildRequest("accountSubscribe", params)
        }

        fun accountUnsubscribe(subscriptionId: Long): String {
            return buildRequest("accountUnsubscribe", buildJsonArray { add(JsonPrimitive(subscriptionId)) })
        }

        /**
         * Subscribe to logs for all transactions or all with votes.
         * @param filter "all" or "allWithVotes"
         */
        fun logsSubscribe(filter: String, commitment: String? = null): String {
            val config = buildJsonObject {
                commitment?.let { put("commitment", it) }
            }
            val params = buildJsonArray {
                add(JsonPrimitive(filter))
                if (config.isNotEmpty()) add(config)
            }
            return buildRequest("logsSubscribe", params)
        }

        /**
         * Subscribe to logs mentioning specific addresses.
         * @param mentions List of addresses to filter by.
         */
        fun logsSubscribe(mentions: List<String>, commitment: String? = null): String {
            val filter = buildJsonObject {
                put("mentions", JsonArray(mentions.map { JsonPrimitive(it) }))
            }
            val config = buildJsonObject {
                commitment?.let { put("commitment", it) }
            }
            val params = buildJsonArray {
                add(filter)
                if (config.isNotEmpty()) add(config)
            }
            return buildRequest("logsSubscribe", params)
        }

        fun logsUnsubscribe(subscriptionId: Long): String {
            return buildRequest("logsUnsubscribe", buildJsonArray { add(JsonPrimitive(subscriptionId)) })
        }

        fun programSubscribe(programId: String, commitment: String? = null, encoding: String? = null, filters: JsonArray? = null): String {
            val config = buildJsonObject {
                commitment?.let { put("commitment", it) }
                encoding?.let { put("encoding", it) }
                filters?.let { put("filters", it) }
            }
            val params = buildJsonArray {
                add(JsonPrimitive(programId))
                if (config.isNotEmpty()) add(config)
            }
            return buildRequest("programSubscribe", params)
        }

        fun programUnsubscribe(subscriptionId: Long): String {
            return buildRequest("programUnsubscribe", buildJsonArray { add(JsonPrimitive(subscriptionId)) })
        }

        fun signatureSubscribe(signature: String, commitment: String? = null): String {
            val config = buildJsonObject {
                commitment?.let { put("commitment", it) }
            }
            val params = buildJsonArray {
                add(JsonPrimitive(signature))
                if (config.isNotEmpty()) add(config)
            }
            return buildRequest("signatureSubscribe", params)
        }

        fun signatureUnsubscribe(subscriptionId: Long): String {
            return buildRequest("signatureUnsubscribe", buildJsonArray { add(JsonPrimitive(subscriptionId)) })
        }

        fun slotSubscribe(): String {
            return buildRequest("slotSubscribe", JsonArray(emptyList()))
        }

        fun slotUnsubscribe(subscriptionId: Long): String {
            return buildRequest("slotUnsubscribe", buildJsonArray { add(JsonPrimitive(subscriptionId)) })
        }

        /**
         * Enhanced WebSocket subscription for transactions with advanced filtering.
         * @param filters Filter criteria (vote, failed, accountInclude, etc.)
         * @param options Configuration options (commitment, encoding, transactionDetails, etc.)
         */
        fun transactionSubscribe(filters: JsonObject, options: JsonObject): String {
            val params = buildJsonArray {
                add(filters)
                add(options)
            }
            return buildRequest("transactionSubscribe", params)
        }

        fun transactionUnsubscribe(subscriptionId: Long): String {
            return buildRequest("transactionUnsubscribe", buildJsonArray { add(JsonPrimitive(subscriptionId)) })
        }

        private fun buildRequest(method: String, params: JsonArray): String {
            val request = JsonObject(mapOf(
                "jsonrpc" to JsonPrimitive("2.0"),
                "id" to JsonPrimitive(System.currentTimeMillis().toString()),
                "method" to JsonPrimitive(method),
                "params" to params
            ))
            return request.toString()
        }
    }

    /**
     * ZK Compression helper methods.  These wrap Helius endpoints that index and
     * validate compressed accounts【128353577680464†L303-L346】.
     */
    inner class ZkCompressionApi {
        /** Retrieve a compressed account by its hash or address. */
        suspend fun getCompressedAccount(hashOrAddress: String): RpcResponse<JsonElement> {
            val params = buildJsonObject { put("hashOrAddress", hashOrAddress) }
            return rpcCall("getCompressedAccount", params)
        }

        /**
         * Return signatures of transactions that created or closed a compressed account
         * with the given hash【128353577680464†L348-L352】.
         */
        suspend fun getCompressionSignaturesForAccount(hash: String): RpcResponse<JsonElement> {
            val params = buildJsonObject { put("hash", hash) }
            return rpcCall("getCompressionSignaturesForAccount", params)
        }

        /**
         * Return signatures of transactions that created or closed compressed accounts
         * owned by the given address【128353577680464†L352-L357】.
         */
        suspend fun getCompressionSignaturesForAddress(address: String): RpcResponse<JsonElement> {
            val params = buildJsonObject { put("address", address) }
            return rpcCall("getCompressionSignaturesForAddress", params)
        }

        /**
         * Get a Merkle proof for a compressed account by its hash or address【800459967483568†L590-L594】.
         * @param hashOrAddress The compressed account hash or address.
         */
        suspend fun getCompressedAccountProof(hashOrAddress: String): RpcResponse<JsonElement> {
            val params = buildJsonObject { put("hashOrAddress", hashOrAddress) }
            return rpcCall("getCompressedAccountProof", params)
        }

        /**
         * Return all compressed accounts owned by a specific address【800459967483568†L592-L596】.
         * @param owner The owner address.
         */
        suspend fun getCompressedAccountsByOwner(owner: String): RpcResponse<JsonElement> {
            val params = buildJsonObject { put("owner", owner) }
            return rpcCall("getCompressedAccountsByOwner", params)
        }

        /**
         * Retrieve the balance for a compressed account【800459967483568†L594-L597】.
         * @param hashOrAddress Hash or address of the compressed account.
         */
        suspend fun getCompressedBalance(hashOrAddress: String): RpcResponse<JsonElement> {
            val params = buildJsonObject { put("hashOrAddress", hashOrAddress) }
            return rpcCall("getCompressedBalance", params)
        }

        /**
         * Retrieve the combined balance for all compressed accounts owned by an address【800459967483568†L596-L598】.
         * @param owner Owner address.
         */
        suspend fun getCompressedBalanceByOwner(owner: String): RpcResponse<JsonElement> {
            val params = buildJsonObject { put("owner", owner) }
            return rpcCall("getCompressedBalanceByOwner", params)
        }

        /**
         * Return the balances for holders of a compressed mint in descending order【800459967483568†L598-L600】.
         * @param mint The compressed mint address.
         */
        suspend fun getCompressedMintTokenHolders(mint: String): RpcResponse<JsonElement> {
            val params = buildJsonObject { put("mint", mint) }
            return rpcCall("getCompressedMintTokenHolders", params)
        }

        /**
         * Return the token balance for a compressed token account【800459967483568†L600-L603】.
         * @param tokenAccount The compressed token account address.
         */
        suspend fun getCompressedTokenAccountBalance(tokenAccount: String): RpcResponse<JsonElement> {
            val params = buildJsonObject { put("tokenAccount", tokenAccount) }
            return rpcCall("getCompressedTokenAccountBalance", params)
        }

        /**
         * Return compressed token accounts delegated to a specific delegate【800459967483568†L602-L604】.
         * @param delegate The delegate address.
         */
        suspend fun getCompressedTokenAccountsByDelegate(delegate: String): RpcResponse<JsonElement> {
            val params = buildJsonObject { put("delegate", delegate) }
            return rpcCall("getCompressedTokenAccountsByDelegate", params)
        }

        /**
         * Return compressed token accounts owned by a specific owner【800459967483568†L604-L607】.
         * @param owner The owner address.
         */
        suspend fun getCompressedTokenAccountsByOwner(owner: String): RpcResponse<JsonElement> {
            val params = buildJsonObject { put("owner", owner) }
            return rpcCall("getCompressedTokenAccountsByOwner", params)
        }

        /**
         * Retrieve token balances for compressed accounts owned by an address【800459967483568†L606-L609】.
         * @param owner The owner address.
         */
        suspend fun getCompressedTokenBalancesByOwner(owner: String): RpcResponse<JsonElement> {
            val params = buildJsonObject { put("owner", owner) }
            return rpcCall("getCompressedTokenBalancesByOwner", params)
        }

        /**
         * Retrieve token balances for compressed accounts owned by an address (V2)【800459967483568†L609-L611】.
         * @param owner The owner address.
         */
        suspend fun getCompressedTokenBalancesByOwnerV2(owner: String): RpcResponse<JsonElement> {
            val params = buildJsonObject { put("owner", owner) }
            return rpcCall("getCompressedTokenBalancesByOwnerV2", params)
        }

        /**
         * Return signatures of transactions that modified an owner’s compressed accounts【800459967483568†L617-L619】.
         * @param owner The owner address.
         */
        suspend fun getCompressionSignaturesForOwner(owner: String): RpcResponse<JsonElement> {
            val params = buildJsonObject { put("owner", owner) }
            return rpcCall("getCompressionSignaturesForOwner", params)
        }

        /**
         * Return signatures of transactions that modified an owner’s compressed token accounts【800459967483568†L620-L622】.
         * @param owner The token owner address.
         */
        suspend fun getCompressionSignaturesForTokenOwner(owner: String): RpcResponse<JsonElement> {
            val params = buildJsonObject { put("owner", owner) }
            return rpcCall("getCompressionSignaturesForTokenOwner", params)
        }

        /**
         * Check indexer health; returns ok if indexer is within a few blocks of the head【800459967483568†L623-L625】.
         */
        suspend fun getIndexerHealth(): RpcResponse<JsonElement> {
            val params = JsonObject(emptyMap())
            return rpcCall("getIndexerHealth", params)
        }

        /**
         * Retrieve the slot of the last block indexed by the compression indexer【800459967483568†L625-L626】.
         */
        suspend fun getIndexerSlot(): RpcResponse<JsonElement> {
            val params = JsonObject(emptyMap())
            return rpcCall("getIndexerSlot", params)
        }

        /**
         * Return the signatures of the latest compression program transactions【800459967483568†L627-L629】.
         * @param limit Optional limit on number of signatures to return (defaults to server limit).
         */
        suspend fun getLatestCompressionSignatures(limit: Int? = null): RpcResponse<JsonElement> {
            val params = buildJsonObject {
                limit?.let { put("limit", it) }
            }
            return rpcCall("getLatestCompressionSignatures", params)
        }

        /**
         * Return the signatures of the latest non‑voting transactions【800459967483568†L629-L630】.
         * @param limit Optional limit on number of signatures to return.
         */
        suspend fun getLatestNonVotingSignatures(limit: Int? = null): RpcResponse<JsonElement> {
            val params = buildJsonObject {
                limit?.let { put("limit", it) }
            }
            return rpcCall("getLatestNonVotingSignatures", params)
        }

        /**
         * Return proofs for multiple compressed accounts【800459967483568†L631-L633】.
         * @param hashesOrAddresses A list of compressed account hashes or addresses.
         */
        suspend fun getMultipleCompressedAccountProofs(hashesOrAddresses: List<String>): RpcResponse<JsonElement> {
            val params = buildJsonObject {
                put("hashesOrAddresses", JsonArray(hashesOrAddresses.map { JsonPrimitive(it) }))
            }
            return rpcCall("getMultipleCompressedAccountProofs", params)
        }

        /**
         * Retrieve multiple compressed accounts by their hashes or addresses【800459967483568†L633-L634】.
         * @param hashesOrAddresses A list of hashes or addresses.
         */
        suspend fun getMultipleCompressedAccounts(hashesOrAddresses: List<String>): RpcResponse<JsonElement> {
            val params = buildJsonObject {
                put("hashesOrAddresses", JsonArray(hashesOrAddresses.map { JsonPrimitive(it) }))
            }
            return rpcCall("getMultipleCompressedAccounts", params)
        }

        /**
         * Fetch proofs that the provided new addresses are unused and can be created【800459967483568†L635-L637】.
         * @param newAddresses List of new compressed addresses to prove.
         */
        suspend fun getMultipleNewAddressProofs(newAddresses: List<String>): RpcResponse<JsonElement> {
            val params = buildJsonObject {
                put("newAddresses", JsonArray(newAddresses.map { JsonPrimitive(it) }))
            }
            return rpcCall("getMultipleNewAddressProofs", params)
        }

        /**
         * Fetch proofs (V2) that the provided new addresses are unused and can be created【800459967483568†L637-L639】.
         * @param newAddresses List of new compressed addresses.
         */
        suspend fun getMultipleNewAddressProofsV2(newAddresses: List<String>): RpcResponse<JsonElement> {
            val params = buildJsonObject {
                put("newAddresses", JsonArray(newAddresses.map { JsonPrimitive(it) }))
            }
            return rpcCall("getMultipleNewAddressProofsV2", params)
        }

        /**
         * Retrieve a transaction and parse compression info associated with it【800459967483568†L639-L641】.
         * @param signature The transaction signature.
         */
        suspend fun getTransactionWithCompressionInfo(signature: String): RpcResponse<JsonElement> {
            val params = buildJsonObject { put("signature", signature) }
            return rpcCall("getTransactionWithCompressionInfo", params)
        }

        /**
         * Return a ZK validity proof used to verify compressed accounts and new address creation【800459967483568†L641-L644】.
         * @param args An object containing accounts and new addresses arrays as documented in the Helius API.
         */
        suspend fun getValidityProof(args: JsonObject): RpcResponse<JsonElement> {
            return rpcCall("getValidityProof", args)
        }
    }

    /**
     * LaserStream API configuration helper.
     *
     * LaserStream is a high-performance gRPC streaming service.  This SDK does not include
     * a full gRPC client to avoid heavy dependencies, but this class provides the necessary
     * configuration constants and helper methods to connect using a standard gRPC client.
     *
     * See [Helius LaserStream Documentation](https://docs.helius.dev/laserstream) for details.
     */
    inner class LaserStreamApi {
        // Mainnet Endpoints
        val ENDPOINT_MAINNET_EWR = "https://laserstream-mainnet-ewr.helius-rpc.com"
        val ENDPOINT_MAINNET_PITT = "https://laserstream-mainnet-pitt.helius-rpc.com"
        val ENDPOINT_MAINNET_SLC = "https://laserstream-mainnet-slc.helius-rpc.com"
        val ENDPOINT_MAINNET_LAX = "https://laserstream-mainnet-lax.helius-rpc.com"
        val ENDPOINT_MAINNET_LON = "https://laserstream-mainnet-lon.helius-rpc.com"
        val ENDPOINT_MAINNET_AMS = "https://laserstream-mainnet-ams.helius-rpc.com"
        val ENDPOINT_MAINNET_FRA = "https://laserstream-mainnet-fra.helius-rpc.com"
        val ENDPOINT_MAINNET_TYO = "https://laserstream-mainnet-tyo.helius-rpc.com"
        val ENDPOINT_MAINNET_SGP = "https://laserstream-mainnet-sgp.helius-rpc.com"

        // Devnet Endpoint
        val ENDPOINT_DEVNET_EWR = "https://laserstream-devnet-ewr.helius-rpc.com"

        /**
         * Returns the recommended endpoint for the current cluster.
         * Note: LaserStream is region-specific, so you may want to choose a specific endpoint
         * closer to your application server instead of this default.
         */
        fun getDefaultEndpoint(): String {
            return when (cluster) {
                Cluster.MAINNET -> ENDPOINT_MAINNET_EWR
                Cluster.DEVNET -> ENDPOINT_DEVNET_EWR
                Cluster.TESTNET -> ENDPOINT_DEVNET_EWR // Fallback
            }
        }

        /**
         * Returns the authentication token to use with LaserStream gRPC connection.
         * This is simply your Helius API key.
         */
        fun getAuthToken(): String {
            return apiKey
        }
    }

    /**
     * Standard Solana RPC methods.
     * These methods mirror the standard Solana JSON-RPC API.
     */
    inner class SolanaApi {
        suspend fun getAccountInfo(pubkey: String, commitment: String? = null, encoding: String? = null): RpcResponse<JsonElement> {
            val config = buildJsonObject {
                commitment?.let { put("commitment", it) }
                encoding?.let { put("encoding", it) }
            }
            val params = buildJsonArray {
                add(JsonPrimitive(pubkey))
                if (config.isNotEmpty()) add(config)
            }
            return rpcCall("getAccountInfo", params)
        }

        suspend fun getBalance(pubkey: String, commitment: String? = null): RpcResponse<JsonElement> {
            val config = buildJsonObject {
                commitment?.let { put("commitment", it) }
            }
            val params = buildJsonArray {
                add(JsonPrimitive(pubkey))
                if (config.isNotEmpty()) add(config)
            }
            return rpcCall("getBalance", params)
        }

        suspend fun getBlock(slot: Long, commitment: String? = null, encoding: String? = null, transactionDetails: String? = null, rewards: Boolean? = null): RpcResponse<JsonElement> {
            val config = buildJsonObject {
                commitment?.let { put("commitment", it) }
                encoding?.let { put("encoding", it) }
                transactionDetails?.let { put("transactionDetails", it) }
                rewards?.let { put("rewards", it) }
            }
            val params = buildJsonArray {
                add(JsonPrimitive(slot))
                if (config.isNotEmpty()) add(config)
            }
            return rpcCall("getBlock", params)
        }

        suspend fun getBlockHeight(commitment: String? = null): RpcResponse<JsonElement> {
            val config = buildJsonObject {
                commitment?.let { put("commitment", it) }
            }
            val params = if (config.isNotEmpty()) buildJsonArray { add(config) } else JsonArray(emptyList())
            return rpcCall("getBlockHeight", params)
        }

        suspend fun getBlockProduction(commitment: String? = null, range: JsonObject? = null, identity: String? = null): RpcResponse<JsonElement> {
            val config = buildJsonObject {
                commitment?.let { put("commitment", it) }
                range?.let { put("range", it) }
                identity?.let { put("identity", it) }
            }
            val params = if (config.isNotEmpty()) buildJsonArray { add(config) } else JsonArray(emptyList())
            return rpcCall("getBlockProduction", params)
        }

        suspend fun getBlockCommitment(slot: Long): RpcResponse<JsonElement> {
            val params = buildJsonArray { add(JsonPrimitive(slot)) }
            return rpcCall("getBlockCommitment", params)
        }

        suspend fun getBlocks(startSlot: Long, endSlot: Long? = null, commitment: String? = null): RpcResponse<JsonElement> {
            val params = buildJsonArray {
                add(JsonPrimitive(startSlot))
                endSlot?.let { add(JsonPrimitive(it)) }
                commitment?.let { add(buildJsonObject { put("commitment", it) }) }
            }
            return rpcCall("getBlocks", params)
        }

        suspend fun getBlocksWithLimit(startSlot: Long, limit: Int, commitment: String? = null): RpcResponse<JsonElement> {
            val params = buildJsonArray {
                add(JsonPrimitive(startSlot))
                add(JsonPrimitive(limit))
                commitment?.let { add(buildJsonObject { put("commitment", it) }) }
            }
            return rpcCall("getBlocksWithLimit", params)
        }

        suspend fun getBlockTime(slot: Long): RpcResponse<JsonElement> {
            val params = buildJsonArray { add(JsonPrimitive(slot)) }
            return rpcCall("getBlockTime", params)
        }

        suspend fun getClusterNodes(): RpcResponse<JsonElement> {
            return rpcCall("getClusterNodes", JsonArray(emptyList()))
        }

        suspend fun getEpochInfo(commitment: String? = null): RpcResponse<JsonElement> {
            val config = buildJsonObject {
                commitment?.let { put("commitment", it) }
            }
            val params = if (config.isNotEmpty()) buildJsonArray { add(config) } else JsonArray(emptyList())
            return rpcCall("getEpochInfo", params)
        }

        suspend fun getEpochSchedule(): RpcResponse<JsonElement> {
            return rpcCall("getEpochSchedule", JsonArray(emptyList()))
        }

        suspend fun getFeeForMessage(message: String, commitment: String? = null): RpcResponse<JsonElement> {
            val config = buildJsonObject {
                commitment?.let { put("commitment", it) }
            }
            val params = buildJsonArray {
                add(JsonPrimitive(message))
                if (config.isNotEmpty()) add(config)
            }
            return rpcCall("getFeeForMessage", params)
        }

        suspend fun getFirstAvailableBlock(): RpcResponse<JsonElement> {
            return rpcCall("getFirstAvailableBlock", JsonArray(emptyList()))
        }

        suspend fun getGenesisHash(): RpcResponse<JsonElement> {
            return rpcCall("getGenesisHash", JsonArray(emptyList()))
        }

        suspend fun getHealth(): RpcResponse<JsonElement> {
            return rpcCall("getHealth", JsonArray(emptyList()))
        }

        suspend fun getHighestSnapshotSlot(): RpcResponse<JsonElement> {
            return rpcCall("getHighestSnapshotSlot", JsonArray(emptyList()))
        }

        suspend fun getIdentity(): RpcResponse<JsonElement> {
            return rpcCall("getIdentity", JsonArray(emptyList()))
        }

        suspend fun getInflationGovernor(commitment: String? = null): RpcResponse<JsonElement> {
            val config = buildJsonObject {
                commitment?.let { put("commitment", it) }
            }
            val params = if (config.isNotEmpty()) buildJsonArray { add(config) } else JsonArray(emptyList())
            return rpcCall("getInflationGovernor", params)
        }

        suspend fun getInflationRate(): RpcResponse<JsonElement> {
            return rpcCall("getInflationRate", JsonArray(emptyList()))
        }

        suspend fun getInflationReward(addresses: List<String>, commitment: String? = null, epoch: Long? = null): RpcResponse<JsonElement> {
            val config = buildJsonObject {
                commitment?.let { put("commitment", it) }
                epoch?.let { put("epoch", it) }
            }
            val params = buildJsonArray {
                add(JsonArray(addresses.map { JsonPrimitive(it) }))
                if (config.isNotEmpty()) add(config)
            }
            return rpcCall("getInflationReward", params)
        }

        suspend fun getLargestAccounts(filter: String? = null, commitment: String? = null): RpcResponse<JsonElement> {
            val config = buildJsonObject {
                filter?.let { put("filter", it) }
                commitment?.let { put("commitment", it) }
            }
            val params = if (config.isNotEmpty()) buildJsonArray { add(config) } else JsonArray(emptyList())
            return rpcCall("getLargestAccounts", params)
        }

        suspend fun getLatestBlockhash(commitment: String? = null): RpcResponse<JsonElement> {
            val config = buildJsonObject {
                commitment?.let { put("commitment", it) }
            }
            val params = if (config.isNotEmpty()) buildJsonArray { add(config) } else JsonArray(emptyList())
            return rpcCall("getLatestBlockhash", params)
        }

        suspend fun getLeaderSchedule(slot: Long? = null, commitment: String? = null, identity: String? = null): RpcResponse<JsonElement> {
            val config = buildJsonObject {
                commitment?.let { put("commitment", it) }
                identity?.let { put("identity", it) }
            }
            val params = buildJsonArray {
                slot?.let { add(JsonPrimitive(it)) } ?: add(JsonNull)
                if (config.isNotEmpty()) add(config)
            }
            return rpcCall("getLeaderSchedule", params)
        }

        suspend fun getMaxRetransmitSlot(): RpcResponse<JsonElement> {
            return rpcCall("getMaxRetransmitSlot", JsonArray(emptyList()))
        }

        suspend fun getMaxShredInsertSlot(): RpcResponse<JsonElement> {
            return rpcCall("getMaxShredInsertSlot", JsonArray(emptyList()))
        }

        suspend fun getMinimumBalanceForRentExemption(dataLength: Long, commitment: String? = null): RpcResponse<JsonElement> {
            val config = buildJsonObject {
                commitment?.let { put("commitment", it) }
            }
            val params = buildJsonArray {
                add(JsonPrimitive(dataLength))
                if (config.isNotEmpty()) add(config)
            }
            return rpcCall("getMinimumBalanceForRentExemption", params)
        }

        suspend fun getMultipleAccounts(pubkeys: List<String>, commitment: String? = null, encoding: String? = null): RpcResponse<JsonElement> {
            val config = buildJsonObject {
                commitment?.let { put("commitment", it) }
                encoding?.let { put("encoding", it) }
            }
            val params = buildJsonArray {
                add(JsonArray(pubkeys.map { JsonPrimitive(it) }))
                if (config.isNotEmpty()) add(config)
            }
            return rpcCall("getMultipleAccounts", params)
        }

        suspend fun getProgramAccounts(programId: String, commitment: String? = null, encoding: String? = null, filters: JsonArray? = null): RpcResponse<JsonElement> {
            val config = buildJsonObject {
                commitment?.let { put("commitment", it) }
                encoding?.let { put("encoding", it) }
                filters?.let { put("filters", it) }
            }
            val params = buildJsonArray {
                add(JsonPrimitive(programId))
                if (config.isNotEmpty()) add(config)
            }
            return rpcCall("getProgramAccounts", params)
        }

        suspend fun getRecentPerformanceSamples(limit: Int? = null): RpcResponse<JsonElement> {
            val params = if (limit != null) buildJsonArray { add(JsonPrimitive(limit)) } else JsonArray(emptyList())
            return rpcCall("getRecentPerformanceSamples", params)
        }

        suspend fun getRecentPrioritizationFees(addresses: List<String>? = null): RpcResponse<JsonElement> {
            val params = if (addresses != null) buildJsonArray { add(JsonArray(addresses.map { JsonPrimitive(it) })) } else JsonArray(emptyList())
            return rpcCall("getRecentPrioritizationFees", params)
        }

        suspend fun getSignaturesForAddress(address: String, limit: Int? = null, before: String? = null, until: String? = null, commitment: String? = null): RpcResponse<JsonElement> {
            val config = buildJsonObject {
                limit?.let { put("limit", it) }
                before?.let { put("before", it) }
                until?.let { put("until", it) }
                commitment?.let { put("commitment", it) }
            }
            val params = buildJsonArray {
                add(JsonPrimitive(address))
                if (config.isNotEmpty()) add(config)
            }
            return rpcCall("getSignaturesForAddress", params)
        }

        suspend fun getSignatureStatuses(signatures: List<String>, searchTransactionHistory: Boolean? = null): RpcResponse<JsonElement> {
            val config = buildJsonObject {
                searchTransactionHistory?.let { put("searchTransactionHistory", it) }
            }
            val params = buildJsonArray {
                add(JsonArray(signatures.map { JsonPrimitive(it) }))
                if (config.isNotEmpty()) add(config)
            }
            return rpcCall("getSignatureStatuses", params)
        }

        suspend fun getSlot(commitment: String? = null): RpcResponse<JsonElement> {
            val config = buildJsonObject {
                commitment?.let { put("commitment", it) }
            }
            val params = if (config.isNotEmpty()) buildJsonArray { add(config) } else JsonArray(emptyList())
            return rpcCall("getSlot", params)
        }

        suspend fun getSlotLeader(commitment: String? = null): RpcResponse<JsonElement> {
            val config = buildJsonObject {
                commitment?.let { put("commitment", it) }
            }
            val params = if (config.isNotEmpty()) buildJsonArray { add(config) } else JsonArray(emptyList())
            return rpcCall("getSlotLeader", params)
        }

        suspend fun getSlotLeaders(startSlot: Long, limit: Int): RpcResponse<JsonElement> {
            val params = buildJsonArray {
                add(JsonPrimitive(startSlot))
                add(JsonPrimitive(limit))
            }
            return rpcCall("getSlotLeaders", params)
        }

        suspend fun getStakeMinimumDelegation(commitment: String? = null): RpcResponse<JsonElement> {
            val config = buildJsonObject {
                commitment?.let { put("commitment", it) }
            }
            val params = if (config.isNotEmpty()) buildJsonArray { add(config) } else JsonArray(emptyList())
            return rpcCall("getStakeMinimumDelegation", params)
        }

        suspend fun getSupply(commitment: String? = null, excludeNonCirculatingAccountsList: Boolean? = null): RpcResponse<JsonElement> {
            val config = buildJsonObject {
                commitment?.let { put("commitment", it) }
                excludeNonCirculatingAccountsList?.let { put("excludeNonCirculatingAccountsList", it) }
            }
            val params = if (config.isNotEmpty()) buildJsonArray { add(config) } else JsonArray(emptyList())
            return rpcCall("getSupply", params)
        }

        suspend fun getTokenAccountBalance(pubkey: String, commitment: String? = null): RpcResponse<JsonElement> {
            val config = buildJsonObject {
                commitment?.let { put("commitment", it) }
            }
            val params = buildJsonArray {
                add(JsonPrimitive(pubkey))
                if (config.isNotEmpty()) add(config)
            }
            return rpcCall("getTokenAccountBalance", params)
        }

        suspend fun getTokenAccountsByDelegate(delegate: String, mint: String? = null, programId: String? = null, commitment: String? = null, encoding: String? = null): RpcResponse<JsonElement> {
            val filter = buildJsonObject {
                mint?.let { put("mint", it) }
                programId?.let { put("programId", it) }
            }
            val config = buildJsonObject {
                commitment?.let { put("commitment", it) }
                encoding?.let { put("encoding", it) }
            }
            val params = buildJsonArray {
                add(JsonPrimitive(delegate))
                add(filter)
                if (config.isNotEmpty()) add(config)
            }
            return rpcCall("getTokenAccountsByDelegate", params)
        }

        suspend fun getTokenAccountsByOwner(owner: String, mint: String? = null, programId: String? = null, commitment: String? = null, encoding: String? = null): RpcResponse<JsonElement> {
            val filter = buildJsonObject {
                mint?.let { put("mint", it) }
                programId?.let { put("programId", it) }
            }
            val config = buildJsonObject {
                commitment?.let { put("commitment", it) }
                encoding?.let { put("encoding", it) }
            }
            val params = buildJsonArray {
                add(JsonPrimitive(owner))
                add(filter)
                if (config.isNotEmpty()) add(config)
            }
            return rpcCall("getTokenAccountsByOwner", params)
        }

        suspend fun getTokenLargestAccounts(mint: String, commitment: String? = null): RpcResponse<JsonElement> {
            val config = buildJsonObject {
                commitment?.let { put("commitment", it) }
            }
            val params = buildJsonArray {
                add(JsonPrimitive(mint))
                if (config.isNotEmpty()) add(config)
            }
            return rpcCall("getTokenLargestAccounts", params)
        }

        suspend fun getTokenSupply(mint: String, commitment: String? = null): RpcResponse<JsonElement> {
            val config = buildJsonObject {
                commitment?.let { put("commitment", it) }
            }
            val params = buildJsonArray {
                add(JsonPrimitive(mint))
                if (config.isNotEmpty()) add(config)
            }
            return rpcCall("getTokenSupply", params)
        }

        suspend fun getTransaction(signature: String, commitment: String? = null, encoding: String? = null, maxSupportedTransactionVersion: Int? = null): RpcResponse<JsonElement> {
            val config = buildJsonObject {
                commitment?.let { put("commitment", it) }
                encoding?.let { put("encoding", it) }
                maxSupportedTransactionVersion?.let { put("maxSupportedTransactionVersion", it) }
            }
            val params = buildJsonArray {
                add(JsonPrimitive(signature))
                if (config.isNotEmpty()) add(config)
            }
            return rpcCall("getTransaction", params)
        }

        suspend fun getTransactionCount(commitment: String? = null): RpcResponse<JsonElement> {
            val config = buildJsonObject {
                commitment?.let { put("commitment", it) }
            }
            val params = if (config.isNotEmpty()) buildJsonArray { add(config) } else JsonArray(emptyList())
            return rpcCall("getTransactionCount", params)
        }

        suspend fun getVersion(): RpcResponse<JsonElement> {
            return rpcCall("getVersion", JsonArray(emptyList()))
        }

        suspend fun getVoteAccounts(commitment: String? = null, votePubkey: String? = null, keepUnstakedDelinquents: Boolean? = null): RpcResponse<JsonElement> {
            val config = buildJsonObject {
                commitment?.let { put("commitment", it) }
                votePubkey?.let { put("votePubkey", it) }
                keepUnstakedDelinquents?.let { put("keepUnstakedDelinquents", it) }
            }
            val params = if (config.isNotEmpty()) buildJsonArray { add(config) } else JsonArray(emptyList())
            return rpcCall("getVoteAccounts", params)
        }

        suspend fun isBlockhashValid(blockhash: String, commitment: String? = null): RpcResponse<JsonElement> {
            val config = buildJsonObject {
                commitment?.let { put("commitment", it) }
            }
            val params = buildJsonArray {
                add(JsonPrimitive(blockhash))
                if (config.isNotEmpty()) add(config)
            }
            return rpcCall("isBlockhashValid", params)
        }

        suspend fun minimumLedgerSlot(): RpcResponse<JsonElement> {
            return rpcCall("minimumLedgerSlot", JsonArray(emptyList()))
        }

        suspend fun requestAirdrop(pubkey: String, lamports: Long, commitment: String? = null): RpcResponse<JsonElement> {
            val config = buildJsonObject {
                commitment?.let { put("commitment", it) }
            }
            val params = buildJsonArray {
                add(JsonPrimitive(pubkey))
                add(JsonPrimitive(lamports))
                if (config.isNotEmpty()) add(config)
            }
            return rpcCall("requestAirdrop", params)
        }
    }

    inner class SenderApi {
        /**
         * Fetches the 75th percentile tip floor from Jito.
         */
        suspend fun getSenderTipFloor(): RpcResponse<Double> {
            val tip = fetchTipFloor()
            return if (tip != null) {
                RpcResponse(result = tip)
            } else {
                RpcResponse(error = RpcError(500, "Failed to fetch tip floor"))
            }
        }

        /**
         * Sends a transaction via the Helius Sender API.
         */
        suspend fun sendTransaction(transaction: String, region: SenderRegion = SenderRegion.DEFAULT, swqosOnly: Boolean = false): RpcResponse<String> {
            return try {
                val sig = sendViaSender(transaction, region, swqosOnly)
                RpcResponse(result = sig)
            } catch (e: Exception) {
                RpcResponse(error = RpcError(500, e.message ?: "Unknown error"))
            }
        }
    }
}