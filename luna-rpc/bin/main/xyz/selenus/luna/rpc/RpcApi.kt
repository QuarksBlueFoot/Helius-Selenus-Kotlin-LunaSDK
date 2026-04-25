package xyz.selenus.luna.rpc

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import xyz.selenus.luna.LunaHeliusClient
import xyz.selenus.luna.RpcResponse

/**
 * Enhanced Solana JSON-RPC helpers exposed through Helius.
 *
 * This namespace wraps the "v2" and auto-paginating variants of standard
 * Solana RPC methods (`getProgramAccountsV2`, `getAllProgramAccounts`,
 * `getTokenAccountsByOwnerV2`, etc.) and the Helius `getTransactionsForAddress`
 * endpoint.
 *
 * Acquire via [LunaHeliusClient.rpc] (the extension property below) or by
 * constructing directly:
 *
 * ```
 * val client = LunaHeliusClient("<api-key>")
 * val accounts = client.rpc.getAllProgramAccounts(programId = "...")
 * ```
 */
class RpcApi internal constructor(private val client: LunaHeliusClient) {

    /**
     * Enhanced version of `getProgramAccounts` that supports pagination and
     * incremental updates.
     *
     * @param programId The public key of the program whose accounts should be listed.
     * @param encoding Optional data encoding (e.g. "base64", "base64+zstd").
     * @param limit Optional page size.
     * @param paginationKey Optional pagination key from a previous response.
     * @param changedSinceSlot Optional slot filter; only accounts changed after
     *   this slot are returned.
     */
    suspend fun getProgramAccountsV2(
        programId: String,
        encoding: String? = null,
        limit: Int? = null,
        paginationKey: String? = null,
        changedSinceSlot: Long? = null
    ): RpcResponse<JsonElement> {
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
        return client.rpcCall("getProgramAccountsV2", params)
    }

    /**
     * Auto-paginate through all program accounts for a given program.
     *
     * Use with caution on large programs — this exhausts pagination and can
     * return very large responses.
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
        return client.rpcCall("getAllProgramAccounts", params)
    }

    /**
     * Enhanced version of `getTokenAccountsByOwner` with pagination and
     * incremental-update support.
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
        return client.rpcCall("getTokenAccountsByOwnerV2", params)
    }

    /**
     * Auto-paginate through all token accounts owned by [owner], optionally
     * filtered by [mint].
     */
    suspend fun getAllTokenAccountsByOwner(
        owner: String,
        mint: String? = null
    ): RpcResponse<JsonElement> {
        val options = buildJsonObject {
            mint?.let { put("mint", it) }
        }
        val params = buildJsonArray {
            add(JsonPrimitive(owner))
            add(options)
        }
        return client.rpcCall("getAllTokenAccountsByOwner", params)
    }

    /**
     * Retrieve transaction history for [address] using a free-form options map.
     *
     * Prefer the strongly-typed overload below for new code.
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
        return client.rpcCall("getTransactionsForAddress", params)
    }

    /**
     * Retrieve transaction history for [address] with strongly-typed filters.
     *
     * @param address The address to query.
     * @param transactionDetails Level of detail: `"signatures"` or `"full"`.
     * @param sortOrder `"asc"` (oldest first) or `"desc"` (newest first).
     * @param limit Max transactions to return (1000 for signatures, 100 for full).
     * @param paginationToken Token for fetching the next page.
     * @param commitment Commitment level (e.g. `"finalized"`).
     * @param filters Advanced filtering options (slot, blockTime, signature, status).
     * @param encoding Encoding for transaction data (only for `"full"` details).
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
        return client.rpcCall("getTransactionsForAddress", params)
    }
}

/**
 * Access the enhanced Helius RPC namespace.
 *
 * The returned [RpcApi] is a lightweight view over the client — it holds only
 * a reference to [this] and carries no mutable state of its own, so it is safe
 * to rebuild on every access. A client-scoped cache is intentionally avoided
 * to keep [LunaHeliusClient] free of per-feature-module coupling.
 *
 * Import this extension to enable the `client.rpc` style:
 *
 * ```
 * import xyz.selenus.luna.rpc.rpc
 *
 * client.rpc.getAllProgramAccounts(programId)
 * ```
 */
val LunaHeliusClient.rpc: RpcApi
    get() = RpcApi(this)
