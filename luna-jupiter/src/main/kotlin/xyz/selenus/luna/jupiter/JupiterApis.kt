package xyz.selenus.luna.jupiter

import kotlinx.coroutines.delay
import kotlinx.serialization.json.*
import xyz.selenus.luna.LunaHeliusClient
import xyz.selenus.luna.RpcError
import xyz.selenus.luna.RpcResponse
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType

// ============================================================================
// JUPITER DEX AGGREGATOR API (Industry-Leading DeFi Integration)
// ============================================================================

/**
 * Jupiter API integration for DEX aggregation and token swaps.
 * Provides access to Jupiter's routing engine for optimal swap execution.
 * 
 * This is a Luna SDK innovation - no other Kotlin SDK provides native Jupiter integration.
 */
class JupiterApi internal constructor(private val client: LunaHeliusClient) {
    private val jupiterBaseUrl = "https://lite-api.jup.ag"

    /**
     * Get a quote for swapping tokens via Jupiter's aggregator.
     * Returns the best route and expected output amount.
     *
     * @param inputMint The mint address of the input token.
     * @param outputMint The mint address of the output token.
     * @param amount The amount to swap (in smallest unit, e.g., lamports).
     * @param slippageBps Slippage tolerance in basis points (e.g., 50 = 0.5%).
     * @param onlyDirectRoutes Whether to only use direct routes (no intermediate hops).
     * @param asLegacyTransaction Whether to return a legacy transaction (vs versioned).
     */
    suspend fun getQuote(
        inputMint: String,
        outputMint: String,
        amount: Long,
        slippageBps: Int = 50,
        onlyDirectRoutes: Boolean = false,
        asLegacyTransaction: Boolean = false
    ): RpcResponse<JsonElement> {
        val urlBuilder = "$jupiterBaseUrl/swap/v1/quote".toHttpUrl().newBuilder()
            .addQueryParameter("inputMint", inputMint)
            .addQueryParameter("outputMint", outputMint)
            .addQueryParameter("amount", amount.toString())
            .addQueryParameter("slippageBps", slippageBps.toString())
        
        if (onlyDirectRoutes) urlBuilder.addQueryParameter("onlyDirectRoutes", "true")
        if (asLegacyTransaction) urlBuilder.addQueryParameter("asLegacyTransaction", "true")

        val request = Request.Builder()
            .url(urlBuilder.build())
            .get()
            .build()

        return try {
            client.httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (!response.isSuccessful || body == null) {
                    RpcResponse(error = RpcError(response.code, "Jupiter quote failed: ${response.message}"))
                } else {
                    RpcResponse(result = client.json.parseToJsonElement(body))
                }
            }
        } catch (e: Exception) {
            RpcResponse(error = RpcError(500, "Jupiter quote error: ${e.message}"))
        }
    }

    /**
     * Get a swap transaction from Jupiter based on a quote.
     * Returns a serialized transaction ready for signing.
     *
     * @param quoteResponse The quote response from getQuote.
     * @param userPublicKey The user's wallet public key.
     * @param wrapUnwrapSol Whether to auto wrap/unwrap SOL.
     * @param dynamicComputeUnitLimit Whether to use dynamic compute units.
     * @param priorityLevel Priority level: "none", "low", "medium", "high", "veryHigh".
     */
    suspend fun getSwapTransaction(
        quoteResponse: JsonElement,
        userPublicKey: String,
        wrapUnwrapSol: Boolean = true,
        dynamicComputeUnitLimit: Boolean = true,
        priorityLevel: String = "high"
    ): RpcResponse<JsonElement> {
        val requestBody = buildJsonObject {
            put("quoteResponse", quoteResponse)
            put("userPublicKey", userPublicKey)
            put("wrapAndUnwrapSol", wrapUnwrapSol)
            put("dynamicComputeUnitLimit", dynamicComputeUnitLimit)
            putJsonObject("prioritizationFeeLamports") {
                putJsonObject("priorityLevelWithMaxLamports") {
                    put("maxLamports", 1000000)
                    put("priorityLevel", priorityLevel)
                }
            }
        }

        val request = Request.Builder()
            .url("$jupiterBaseUrl/swap/v1/swap")
            .post(client.json.encodeToString(JsonElement.serializer(), requestBody).toRequestBody("application/json".toMediaType()))
            .header("Content-Type", "application/json")
            .build()

        return try {
            client.httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (!response.isSuccessful || body == null) {
                    RpcResponse(error = RpcError(response.code, "Jupiter swap failed: ${response.message}"))
                } else {
                    RpcResponse(result = client.json.parseToJsonElement(body))
                }
            }
        } catch (e: Exception) {
            RpcResponse(error = RpcError(500, "Jupiter swap error: ${e.message}"))
        }
    }

    /**
     * Convenience method: Get quote and execute swap via Sender for ultra-low latency.
     * This combines Jupiter routing with Helius Sender for optimal execution.
     *
     * @param inputMint Input token client.mint.
     * @param outputMint Output token client.mint.
     * @param amount Amount in smallest units.
     * @param userPublicKey User's public key.
     * @param signedTransactionCallback Callback to sign the transaction (returns base64 signed tx).
     * @param slippageBps Slippage in basis points.
     * @param region Sender region to use.
     */
    suspend fun swapViaSender(
        inputMint: String,
        outputMint: String,
        amount: Long,
        userPublicKey: String,
        signedTransactionCallback: suspend (unsignedTxBase64: String) -> String,
        slippageBps: Int = 50,
        region: LunaHeliusClient.SenderRegion = LunaHeliusClient.SenderRegion.DEFAULT
    ): RpcResponse<LunaHeliusClient.JupiterSwapResult> {
        // 1. Get quote
        val quoteResponse = getQuote(inputMint, outputMint, amount, slippageBps)
        if (quoteResponse.error != null) {
            return RpcResponse(result = LunaHeliusClient.JupiterSwapResult(null, false, "Quote failed: ${quoteResponse.error.message}"))
        }

        // 2. Get swap transaction
        val swapResponse = getSwapTransaction(
            quoteResponse.result!!,
            userPublicKey,
            dynamicComputeUnitLimit = true,
            priorityLevel = "veryHigh"
        )
        if (swapResponse.error != null) {
            return RpcResponse(result = LunaHeliusClient.JupiterSwapResult(null, false, "Swap tx failed: ${swapResponse.error.message}"))
        }

        val swapTx = swapResponse.result?.jsonObject?.get("swapTransaction")?.jsonPrimitive?.content
        if (swapTx == null) {
            return RpcResponse(result = LunaHeliusClient.JupiterSwapResult(null, false, "No swap transaction returned"))
        }

        // 3. Sign transaction (via callback - user provides signing logic)
        val signedTx = try {
            signedTransactionCallback(swapTx)
        } catch (e: Exception) {
            return RpcResponse(result = LunaHeliusClient.JupiterSwapResult(null, false, "Signing failed: ${e.message}"))
        }

        // 4. Send via Sender
        val sendResult = client.sender.sendTransaction(signedTx, region)
        if (sendResult.error != null) {
            return RpcResponse(result = LunaHeliusClient.JupiterSwapResult(null, false, "Send failed: ${sendResult.error.message}"))
        }

        return RpcResponse(result = LunaHeliusClient.JupiterSwapResult(sendResult.result, true, null))
    }

    /**
     * Get all available tokens from Jupiter.
     */
    suspend fun getTokenList(): RpcResponse<JsonElement> {
        val request = Request.Builder()
            .url("https://token.jup.ag/all")
            .get()
            .build()

        return try {
            client.httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (!response.isSuccessful || body == null) {
                    RpcResponse(error = RpcError(response.code, "Token list failed"))
                } else {
                    RpcResponse(result = client.json.parseToJsonElement(body))
                }
            }
        } catch (e: Exception) {
            RpcResponse(error = RpcError(500, "Token list error: ${e.message}"))
        }
    }

    /**
     * Get current price for a token in USD.
     * Uses Jupiter's price API.
     *
     * @param mintAddresses List of token mint addresses.
     */
    suspend fun getPrices(mintAddresses: List<String>): RpcResponse<JsonElement> {
        val ids = mintAddresses.joinToString(",")
        val request = Request.Builder()
            .url("https://api.jup.ag/price/v2?ids=$ids")
            .get()
            .build()

        return try {
            client.httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (!response.isSuccessful || body == null) {
                    RpcResponse(error = RpcError(response.code, "Price API failed"))
                } else {
                    RpcResponse(result = client.json.parseToJsonElement(body))
                }
            }
        } catch (e: Exception) {
            RpcResponse(error = RpcError(500, "Price API error: ${e.message}"))
        }
    }
}


// ============================================================================
// JUPITER TRIGGER API (v4.0.0 - Limit Orders)
// ============================================================================

/**
 * Jupiter Trigger API for limit orders.
 * 
 * Allows users to set target prices for token swaps that execute automatically
 * when market conditions are met.
 *
 * Luna SDK Innovation: First Kotlin SDK with Jupiter Trigger integration.
 */
class JupiterTriggerApi internal constructor(private val client: LunaHeliusClient) {
    private val triggerBaseUrl = "https://api.jup.ag/trigger/v1"

    /**
     * Create a limit order.
     *
     * @param inputMint Token to sell.
     * @param outputMint Token to buy.
     * @param inputAmount Amount to sell in smallest units.
     * @param targetPrice Target price to execute at.
     * @param userPublicKey User's wallet address.
     * @param expireInSeconds Optional expiration time.
     */
    suspend fun createLimitOrder(
        inputMint: String,
        outputMint: String,
        inputAmount: Long,
        targetPrice: Double,
        userPublicKey: String,
        expireInSeconds: Long? = null
    ): RpcResponse<JsonElement> {
        val body = buildJsonObject {
            put("inputMint", inputMint)
            put("outputMint", outputMint)
            put("inputAmount", inputAmount.toString())
            put("targetPrice", targetPrice)
            put("maker", userPublicKey)
            expireInSeconds?.let { put("expireAt", System.currentTimeMillis() / 1000 + it) }
        }

        val request = Request.Builder()
            .url("$triggerBaseUrl/create")
            .post(client.json.encodeToString(JsonElement.serializer(), body).toRequestBody("application/json".toMediaType()))
            .header("Content-Type", "application/json")
            .build()

        return try {
            client.httpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                if (!response.isSuccessful || responseBody == null) {
                    RpcResponse(error = RpcError(response.code, "Create order failed: ${response.message}"))
                } else {
                    RpcResponse(result = client.json.parseToJsonElement(responseBody))
                }
            }
        } catch (e: Exception) {
            RpcResponse(error = RpcError(500, "Trigger API error: ${e.message}"))
        }
    }

    /**
     * Get all open limit orders for a user.
     *
     * @param userPublicKey User's wallet address.
     */
    suspend fun getOpenOrders(userPublicKey: String): RpcResponse<List<LimitOrder>> {
        val request = Request.Builder()
            .url("$triggerBaseUrl/orders?maker=$userPublicKey")
            .get()
            .build()

        return try {
            client.httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (!response.isSuccessful || body == null) {
                    RpcResponse(error = RpcError(response.code, "Get orders failed"))
                } else {
                    val ordersArray = client.json.parseToJsonElement(body).jsonArray
                    val orders = ordersArray.map { orderJson ->
                        val obj = orderJson.jsonObject
                        LimitOrder(
                            orderId = obj["orderId"]?.jsonPrimitive?.content,
                            inputMint = obj["inputMint"]?.jsonPrimitive?.content ?: "",
                            outputMint = obj["outputMint"]?.jsonPrimitive?.content ?: "",
                            inputAmount = obj["inputAmount"]?.jsonPrimitive?.longOrNull ?: 0L,
                            minOutputAmount = obj["minOutputAmount"]?.jsonPrimitive?.longOrNull ?: 0L,
                            targetPrice = obj["targetPrice"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                            expireAt = obj["expireAt"]?.jsonPrimitive?.longOrNull,
                            status = obj["status"]?.jsonPrimitive?.content ?: "UNKNOWN"
                        )
                    }
                    RpcResponse(result = orders)
                }
            }
        } catch (e: Exception) {
            RpcResponse(error = RpcError(500, "Get orders error: ${e.message}"))
        }
    }

    /**
     * Cancel a limit order.
     *
     * @param orderId The order ID to cancel.
     * @param userPublicKey User's wallet address.
     */
    suspend fun cancelOrder(orderId: String, userPublicKey: String): RpcResponse<JsonElement> {
        val body = buildJsonObject {
            put("orderId", orderId)
            put("maker", userPublicKey)
        }

        val request = Request.Builder()
            .url("$triggerBaseUrl/cancel")
            .post(client.json.encodeToString(JsonElement.serializer(), body).toRequestBody("application/json".toMediaType()))
            .header("Content-Type", "application/json")
            .build()

        return try {
            client.httpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                if (!response.isSuccessful || responseBody == null) {
                    RpcResponse(error = RpcError(response.code, "Cancel failed"))
                } else {
                    RpcResponse(result = client.json.parseToJsonElement(responseBody))
                }
            }
        } catch (e: Exception) {
            RpcResponse(error = RpcError(500, "Cancel error: ${e.message}"))
        }
    }

    /**
     * Get order history (filled and cancelled).
     *
     * @param userPublicKey User's wallet address.
     */
    suspend fun getOrderHistory(userPublicKey: String): RpcResponse<JsonElement> {
        val request = Request.Builder()
            .url("$triggerBaseUrl/history?maker=$userPublicKey")
            .get()
            .build()

        return try {
            client.httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (!response.isSuccessful || body == null) {
                    RpcResponse(error = RpcError(response.code, "Get history failed"))
                } else {
                    RpcResponse(result = client.json.parseToJsonElement(body))
                }
            }
        } catch (e: Exception) {
            RpcResponse(error = RpcError(500, "History error: ${e.message}"))
        }
    }
}


// ============================================================================
// JUPITER RECURRING API (v4.0.0 - Dollar Cost Averaging)
// ============================================================================

/**
 * Jupiter Recurring API for Dollar Cost Averaging (DCA).
 * 
 * Enables automated recurring token purchases at set intervals.
 * Essential for long-term investment strategies.
 *
 * Luna SDK Innovation: First Kotlin SDK with Jupiter DCA integration.
 */
class JupiterRecurringApi internal constructor(private val client: LunaHeliusClient) {
    private val recurringBaseUrl = "https://api.jup.ag/recurring/v1"

    /**
     * Create a DCA (recurring) order.
     *
     * @param inputMint Token to spend each cycle.
     * @param outputMint Token to accumulate.
     * @param amountPerCycle Amount to spend each cycle.
     * @param cycleFrequencySeconds Time between purchases.
     * @param totalCycles Total number of purchases (0 for unlimited).
     * @param userPublicKey User's wallet address.
     */
    suspend fun createDcaOrder(
        inputMint: String,
        outputMint: String,
        amountPerCycle: Long,
        cycleFrequencySeconds: Long,
        totalCycles: Int,
        userPublicKey: String
    ): RpcResponse<JsonElement> {
        val body = buildJsonObject {
            put("inputMint", inputMint)
            put("outputMint", outputMint)
            put("amountPerCycle", amountPerCycle.toString())
            put("cycleFrequency", cycleFrequencySeconds)
            put("numberOfCycles", totalCycles)
            put("user", userPublicKey)
        }

        val request = Request.Builder()
            .url("$recurringBaseUrl/create")
            .post(client.json.encodeToString(JsonElement.serializer(), body).toRequestBody("application/json".toMediaType()))
            .header("Content-Type", "application/json")
            .build()

        return try {
            client.httpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                if (!response.isSuccessful || responseBody == null) {
                    RpcResponse(error = RpcError(response.code, "Create DCA failed"))
                } else {
                    RpcResponse(result = client.json.parseToJsonElement(responseBody))
                }
            }
        } catch (e: Exception) {
            RpcResponse(error = RpcError(500, "DCA error: ${e.message}"))
        }
    }

    /**
     * Get all active DCA orders for a user.
     *
     * @param userPublicKey User's wallet address.
     */
    suspend fun getActiveOrders(userPublicKey: String): RpcResponse<List<DcaOrder>> {
        val request = Request.Builder()
            .url("$recurringBaseUrl/orders?user=$userPublicKey")
            .get()
            .build()

        return try {
            client.httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (!response.isSuccessful || body == null) {
                    RpcResponse(error = RpcError(response.code, "Get DCA orders failed"))
                } else {
                    val ordersArray = client.json.parseToJsonElement(body).jsonArray
                    val orders = ordersArray.map { obj ->
                        val o = obj.jsonObject
                        DcaOrder(
                            orderId = o["orderId"]?.jsonPrimitive?.content,
                            inputMint = o["inputMint"]?.jsonPrimitive?.content ?: "",
                            outputMint = o["outputMint"]?.jsonPrimitive?.content ?: "",
                            amountPerCycle = o["amountPerCycle"]?.jsonPrimitive?.longOrNull ?: 0L,
                            cycleFrequencySeconds = o["cycleFrequency"]?.jsonPrimitive?.longOrNull ?: 0L,
                            totalCycles = o["numberOfCycles"]?.jsonPrimitive?.intOrNull ?: 0,
                            remainingCycles = o["remainingCycles"]?.jsonPrimitive?.intOrNull ?: 0,
                            status = o["status"]?.jsonPrimitive?.content ?: "UNKNOWN"
                        )
                    }
                    RpcResponse(result = orders)
                }
            }
        } catch (e: Exception) {
            RpcResponse(error = RpcError(500, "Get DCA error: ${e.message}"))
        }
    }

    /**
     * Cancel a DCA order.
     *
     * @param orderId Order to cancel.
     * @param userPublicKey User's wallet address.
     */
    suspend fun cancelDca(orderId: String, userPublicKey: String): RpcResponse<JsonElement> {
        val body = buildJsonObject {
            put("orderId", orderId)
            put("user", userPublicKey)
        }

        val request = Request.Builder()
            .url("$recurringBaseUrl/cancel")
            .post(client.json.encodeToString(JsonElement.serializer(), body).toRequestBody("application/json".toMediaType()))
            .header("Content-Type", "application/json")
            .build()

        return try {
            client.httpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                if (!response.isSuccessful || responseBody == null) {
                    RpcResponse(error = RpcError(response.code, "Cancel DCA failed"))
                } else {
                    RpcResponse(result = client.json.parseToJsonElement(responseBody))
                }
            }
        } catch (e: Exception) {
            RpcResponse(error = RpcError(500, "Cancel DCA error: ${e.message}"))
        }
    }

    /**
     * Convenience: Create daily DCA for a token.
     *
     * @param inputMint Token to spend (e.g., USDC).
     * @param outputMint Token to accumulate (e.g., SOL).
     * @param dailyAmount Amount per day in smallest units.
     * @param days Number of days.
     * @param userPublicKey User's wallet.
     */
    suspend fun createDailyDca(
        inputMint: String,
        outputMint: String,
        dailyAmount: Long,
        days: Int,
        userPublicKey: String
    ): RpcResponse<JsonElement> {
        return createDcaOrder(
            inputMint = inputMint,
            outputMint = outputMint,
            amountPerCycle = dailyAmount,
            cycleFrequencySeconds = 86400, // 24 hours
            totalCycles = days,
            userPublicKey = userPublicKey
        )
    }

    /**
     * Convenience: Create weekly DCA for a token.
     */
    suspend fun createWeeklyDca(
        inputMint: String,
        outputMint: String,
        weeklyAmount: Long,
        weeks: Int,
        userPublicKey: String
    ): RpcResponse<JsonElement> {
        return createDcaOrder(
            inputMint = inputMint,
            outputMint = outputMint,
            amountPerCycle = weeklyAmount,
            cycleFrequencySeconds = 604800, // 7 days
            totalCycles = weeks,
            userPublicKey = userPublicKey
        )
    }
}

/**
 * Access the JupiterApi namespace from a [LunaHeliusClient].
 *
 * Import this extension to enable the `client.jupiter` style:
 * ```
 * import xyz.selenus.luna.jupiter.jupiter
 * client.jupiter.<method>()
 * ```
 */
val LunaHeliusClient.jupiter: JupiterApi
    get() = JupiterApi(this)

/**
 * Access the JupiterTriggerApi namespace from a [LunaHeliusClient].
 *
 * Import this extension to enable the `client.jupiterTrigger` style:
 * ```
 * import xyz.selenus.luna.jupiter.jupiterTrigger
 * client.jupiterTrigger.<method>()
 * ```
 */
val LunaHeliusClient.jupiterTrigger: JupiterTriggerApi
    get() = JupiterTriggerApi(this)

/**
 * Access the JupiterRecurringApi namespace from a [LunaHeliusClient].
 *
 * Import this extension to enable the `client.jupiterRecurring` style:
 * ```
 * import xyz.selenus.luna.jupiter.jupiterRecurring
 * client.jupiterRecurring.<method>()
 * ```
 */
val LunaHeliusClient.jupiterRecurring: JupiterRecurringApi
    get() = JupiterRecurringApi(this)
