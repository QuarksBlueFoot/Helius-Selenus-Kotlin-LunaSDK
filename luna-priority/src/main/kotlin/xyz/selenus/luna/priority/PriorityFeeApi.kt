package xyz.selenus.luna.priority

import kotlinx.coroutines.delay
import kotlinx.serialization.json.*
import xyz.selenus.luna.LunaHeliusClient
import xyz.selenus.luna.RpcError
import xyz.selenus.luna.RpcResponse

/**
 * Priority fee estimation API.  Use this to estimate network fees for a transaction
 * given a desired priority level.
 */
class PriorityFeeApi internal constructor(private val client: LunaHeliusClient) {
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
        lookbackSlots: Int? = null,
        recommended: Boolean? = null
    ): RpcResponse<JsonElement> {
        val options = buildJsonObject {
            priorityLevel?.let { put("priorityLevel", it) }
            includeAllPriorityFeeLevels?.let { put("includeAllPriorityFeeLevels", it) }
            lookbackSlots?.let { put("lookbackSlots", it) }
            recommended?.let { put("recommended", it) }
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
        return client.rpcCall("getPriorityFeeEstimate", params)
    }
}

/**
 * Access the PriorityFeeApi namespace from a [LunaHeliusClient].
 *
 * Import this extension to enable the `client.priority` style:
 * ```
 * import xyz.selenus.luna.priority.priority
 * client.priority.<method>()
 * ```
 */
val LunaHeliusClient.priority: PriorityFeeApi
    get() = PriorityFeeApi(this)
