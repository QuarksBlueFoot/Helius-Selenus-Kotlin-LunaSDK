package xyz.selenus.luna.enhanced

import kotlinx.coroutines.delay
import kotlinx.serialization.json.*
import xyz.selenus.luna.LunaHeliusClient
import xyz.selenus.luna.RpcError
import xyz.selenus.luna.RpcResponse

/**
 * Enhanced Transactions API.  Converts raw transaction data into human readable
 * form and fetches transactions by address.
 */
class EnhancedApi internal constructor(private val client: LunaHeliusClient) {
    /**
     * Convert one or more transaction signatures into enhanced, human readable
     * transaction descriptions.
     * @param signatures List of transaction signatures to decode.
     */
    suspend fun getTransactions(signatures: List<String>): RpcResponse<JsonElement> {
        val params = buildJsonObject {
            put("transactions", JsonArray(signatures.map { JsonPrimitive(it) }))
        }
        // Use REST call for enhanced transactions parsing
        val result = client.restCall("transactions", method = "POST", body = params)
        return RpcResponse(result = result)
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
        val queryParams = mutableMapOf<String, String>()
        page?.let { queryParams["page"] = it.toString() }
        limit?.let { queryParams["limit"] = it.toString() }
        before?.let { queryParams["before"] = it }
        until?.let { queryParams["until"] = it }

        // Use REST call for enhanced transaction history
        val result = client.restCall("addresses/$address/transactions", queryParams = queryParams)
        return RpcResponse(result = result)
    }
}

/**
 * Access the EnhancedApi namespace from a [LunaHeliusClient].
 *
 * Import this extension to enable the `client.enhanced` style:
 * ```
 * import xyz.selenus.luna.enhanced.enhanced
 * client.enhanced.<method>()
 * ```
 */
val LunaHeliusClient.enhanced: EnhancedApi
    get() = EnhancedApi(this)
