package com.selenus.iris

import kotlinx.serialization.json.*

/**
 * # Priority Namespace
 * 
 * QuickNode's Priority Fee API for optimal transaction prioritization.
 * Get real-time fee estimates based on current network conditions.
 * 
 * ## Features
 * - Network-wide fee estimates
 * - Account-specific fee estimates
 * - Multiple percentile levels
 * - Per compute unit and per transaction estimates
 */
class PriorityNamespace internal constructor(private val client: IrisQuickNodeClient) {
    
    private val json = client.getJson()
    
    /**
     * Estimate priority fees based on recent network activity.
     * 
     * @param accounts Optional list of accounts for targeted estimation
     * @param level Priority level for fee recommendation
     * @param lastNBlocks Number of recent blocks to analyze
     * @return Recommended priority fee in micro-lamports per compute unit
     */
    suspend fun estimatePriorityFees(
        accounts: List<String> = emptyList(),
        level: PriorityLevel = PriorityLevel.MEDIUM,
        lastNBlocks: Int = 100
    ): Double {
        val params = buildJsonObject {
            put("last_n_blocks", lastNBlocks)
            put("api_version", 2)
            if (accounts.isNotEmpty()) {
                put("account", accounts.first())
            }
        }
        
        val result = client.executeRpcCall(
            method = "qn_estimatePriorityFees",
            params = params,
            resultDeserializer = JsonElement.serializer()
        )
        
        // Parse the result based on priority level
        val perComputeUnit = result.jsonObject["per_compute_unit"]?.jsonObject
        val levels = perComputeUnit?.get("percentiles")?.jsonObject
        
        return when (level) {
            PriorityLevel.LOW -> levels?.get("25")?.jsonPrimitive?.double ?: 1000.0
            PriorityLevel.MEDIUM -> levels?.get("50")?.jsonPrimitive?.double ?: 5000.0
            PriorityLevel.HIGH -> levels?.get("75")?.jsonPrimitive?.double ?: 10000.0
            PriorityLevel.VERY_HIGH -> levels?.get("95")?.jsonPrimitive?.double ?: 50000.0
            PriorityLevel.UNSAFE_MAX -> levels?.get("100")?.jsonPrimitive?.double ?: 100000.0
        }
    }
    
    /**
     * Get detailed priority fee breakdown.
     * Returns estimates at multiple percentile levels.
     */
    suspend fun getDetailedFeeEstimate(
        accounts: List<String> = emptyList(),
        lastNBlocks: Int = 100
    ): PriorityFeeResult {
        val params = buildJsonObject {
            put("last_n_blocks", lastNBlocks)
            put("api_version", 2)
            if (accounts.isNotEmpty()) {
                put("account", accounts.first())
            }
        }
        
        val result = client.executeRpcCall(
            method = "qn_estimatePriorityFees",
            params = params,
            resultDeserializer = JsonElement.serializer()
        )
        
        return json.decodeFromJsonElement(PriorityFeeResult.serializer(), result)
    }
    
    /**
     * Get recent prioritization fees from the ledger.
     * Lower-level method that returns raw fee data.
     */
    suspend fun getRecentPrioritizationFees(
        accounts: List<String> = emptyList()
    ): JsonElement {
        return client.rpc.getRecentPrioritizationFees(accounts)
    }
}

/**
 * # Pump.fun Namespace
 * 
 * QuickNode's exclusive Pump.fun API for bonding curve trading.
 * Trade new tokens at the fastest speeds before they hit DEXs.
 * 
 * ## Features
 * - Get quotes for pump.fun tokens (BUY or SELL)
 * - Build swap transactions
 * - Custom slippage and platform fees
 * - Bonding curve pricing
 * - Metadata about token market cap and supply
 * 
 * ## Endpoints
 * - `/pump-fun/quote` - Get best-route quotes
 * - `/pump-fun/swap` - Build fully-signed swap payloads
 * - `/pump-fun/swap-instructions` - Get individual instructions for custom tx assembly
 * 
 * @see <a href="https://www.quicknode.com/docs/solana/pump-fun-quote">Pump.fun API Docs</a>
 */
class PumpFunNamespace internal constructor(private val client: IrisQuickNodeClient) {
    
    private val json = client.getJson()
    
    /**
     * Get a quote for buying or selling a pump.fun token.
     * 
     * @param type BUY or SELL
     * @param mint The pump.fun mint address of the token to quote
     * @param amount The amount in smallest units. For BUY: SOL amount, for SELL: token amount (6 decimals)
     * @param commitment Transaction commitment level (default: confirmed)
     * @return Quote with bonding curve details and pricing
     * 
     * ## Example
     * ```kotlin
     * // Buy tokens with 0.1 SOL
     * val buyQuote = client.pumpfun.getQuote(
     *     type = PumpFunType.BUY,
     *     mint = "2q7jMwWYFxUdxBqWbi8ohztyG1agjQMrasUXwqGCpump",
     *     amount = 100_000_000 // 0.1 SOL
     * )
     * 
     * // Sell 100 tokens
     * val sellQuote = client.pumpfun.getQuote(
     *     type = PumpFunType.SELL,
     *     mint = "2q7jMwWYFxUdxBqWbi8ohztyG1agjQMrasUXwqGCpump",
     *     amount = 100_000_000 // 100 tokens (6 decimals)
     * )
     * ```
     */
    suspend fun getQuote(
        type: PumpFunType,
        mint: String,
        amount: Long,
        commitment: Commitment = Commitment.CONFIRMED
    ): PumpFunQuoteResponse {
        val queryParams = buildString {
            append("?type=${type.name}")
            append("&mint=$mint")
            append("&amount=$amount")
            append("&commitment=${commitment.value}")
        }
        
        return client.executeRestCall(
            path = "/pump-fun/quote$queryParams",
            method = "GET",
            resultDeserializer = PumpFunQuoteResponse.serializer()
        )
    }
    
    /**
     * Legacy quote method for backwards compatibility.
     * Use the new getQuote(type, mint, amount) signature instead.
     */
    @Deprecated("Use getQuote(type, mint, amount) instead", ReplaceWith("getQuote(PumpFunType.BUY, outputMint, amount)"))
    suspend fun getQuote(
        inputMint: String,
        outputMint: String,
        amount: Long,
        slippageBps: Int = 100
    ): PumpFunQuote {
        // Determine if buy or sell based on SOL mint
        val isBuy = inputMint == "So11111111111111111111111111111111111111112"
        val type = if (isBuy) PumpFunType.BUY else PumpFunType.SELL
        val mint = if (isBuy) outputMint else inputMint
        
        val response = getQuote(type, mint, amount)
        
        // Convert to legacy format
        return PumpFunQuote(
            inputMint = response.quote.inTokenAddress,
            outputMint = response.quote.outTokenAddress,
            inAmount = response.quote.inAmount,
            outAmount = response.quote.outAmount,
            slippageBps = slippageBps,
            priceImpactPct = 0.0
        )
    }
    
    /**
     * Build a swap transaction for pump.fun.
     * 
     * @param quote The quote response from getQuote
     * @param userPublicKey The user's wallet address
     * @param priorityFee Optional priority fee in lamports
     * @param slippageBps Slippage tolerance in basis points (default: 250 = 2.5%)
     * @return Swap result containing the serialized transaction
     */
    suspend fun getSwapTransaction(
        quote: PumpFunQuoteResponse,
        userPublicKey: String,
        priorityFee: Long? = null,
        slippageBps: Int = 250
    ): PumpFunSwapResult {
        val body = buildJsonObject {
            put("quote", json.encodeToJsonElement(PumpFunQuoteData.serializer(), quote.quote))
            put("userPublicKey", userPublicKey)
            put("slippageBps", slippageBps)
            if (priorityFee != null) {
                put("priorityFee", priorityFee)
            }
        }
        
        return client.executeRestCall(
            path = "/pump-fun/swap",
            method = "POST",
            body = body,
            resultDeserializer = PumpFunSwapResult.serializer()
        )
    }
    
    /**
     * Legacy getSwapTransaction for backwards compatibility.
     */
    @Deprecated("Use getSwapTransaction(quote: PumpFunQuoteResponse, ...) instead")
    suspend fun getSwapTransaction(
        quote: PumpFunQuote,
        userPublicKey: String
    ): PumpFunSwapResult {
        val body = buildJsonObject {
            put("quoteResponse", json.encodeToJsonElement(PumpFunQuote.serializer(), quote))
            put("userPublicKey", userPublicKey)
        }
        
        return client.executeRestCall(
            path = "/pump-fun/swap",
            method = "POST",
            body = body,
            resultDeserializer = PumpFunSwapResult.serializer()
        )
    }
    
    /**
     * Get swap instructions for custom transaction building.
     * 
     * @param quote The quote response from getQuote
     * @param userPublicKey The user's wallet address
     * @param priorityFee Optional priority fee in lamports
     * @param slippageBps Slippage tolerance in basis points
     * @return Instructions object for composable trading
     */
    suspend fun getSwapInstructions(
        quote: PumpFunQuoteResponse,
        userPublicKey: String,
        priorityFee: Long? = null,
        slippageBps: Int = 250
    ): PumpFunInstructionsResult {
        val body = buildJsonObject {
            put("quote", json.encodeToJsonElement(PumpFunQuoteData.serializer(), quote.quote))
            put("userPublicKey", userPublicKey)
            put("slippageBps", slippageBps)
            if (priorityFee != null) {
                put("priorityFee", priorityFee)
            }
        }
        
        return client.executeRestCall(
            path = "/pump-fun/swap-instructions",
            method = "POST",
            body = body,
            resultDeserializer = PumpFunInstructionsResult.serializer()
        )
    }
    
    /**
     * Legacy getSwapInstructions for backwards compatibility.
     */
    @Deprecated("Use getSwapInstructions(quote: PumpFunQuoteResponse, ...) instead")
    suspend fun getSwapInstructions(
        quote: PumpFunQuote,
        userPublicKey: String
    ): JsonElement {
        val body = buildJsonObject {
            put("quoteResponse", json.encodeToJsonElement(PumpFunQuote.serializer(), quote))
            put("userPublicKey", userPublicKey)
        }
        
        return client.executeRestCall(
            path = "/pump-fun/swap-instructions",
            method = "POST",
            body = body,
            resultDeserializer = JsonElement.serializer()
        )
    }
}

/**
 * # Fastlane Namespace
 * 
 * QuickNode's Transaction Fastlane for enterprise-grade transaction propagation.
 * Achieves sub-slot latencies through optimized validator networks.
 * 
 * ## Performance Metrics
 * - 50%+ zero-slot execution
 * - 97.5% sub-second settlement
 * - P99 latency: 2.0 slots maximum
 * - 99.99% uptime
 * 
 * ## Requirements
 * - Minimum 5,000,000 microLamports compute unit price
 * - Minimum 0.001 SOL tip to fastlane tip accounts
 */
class FastlaneNamespace internal constructor(private val client: IrisQuickNodeClient) {
    
    /**
     * Tip accounts for Transaction Fastlane.
     * Include a tip transfer to one of these in your transaction.
     */
    val tipAccounts = listOf(
        "CHJPZWYoHMkTFtDq75Jmy6FLFcHD5kJhGziBgiNSfmLE",
        "JfxpfrmghH7MWRPB8XMVxQfEhKWVp24ZpvPAHCEQHMoo",
        "mkaneVz99A4gdUfnPaNYedAcBzfnVMRe82rEMWFgBDNK",
        "reUbZqYJHQFN8199b9zGCpkKqBCPyDjpCgzhx42mWt6Q",
        "Xn167LgSxKM6rtHjEjMztFF6u5Vnb1nA6Vvu622GKaRX",
        "ieiGnrpGdBWpCRoW4a3m7zjSUaUxrDbnhpphkF9XUxbN",
        "cqGQAfzjT2E9LDcHtw5LWQzaeqoQuCkXXPhvUAkoLzkJ",
        "uthPh9ZGR"
    )
    
    /**
     * Send a transaction via Fastlane for optimal execution.
     * Transaction should include priority fee and tip.
     * 
     * @param signedTransaction Base64 encoded signed transaction
     * @param skipPreflight Skip preflight simulation
     * @return Transaction signature
     */
    suspend fun sendTransaction(
        signedTransaction: String,
        skipPreflight: Boolean = false
    ): String {
        return client.rpc.sendTransaction(
            signedTransaction = signedTransaction,
            skipPreflight = skipPreflight,
            preflightCommitment = Commitment.CONFIRMED
        )
    }
    
    /**
     * Get a random tip account for round-robin distribution.
     */
    fun getRandomTipAccount(): String {
        return tipAccounts.random()
    }
    
    /**
     * Minimum tip required in lamports (0.001 SOL).
     */
    val minimumTipLamports: Long = 1_000_000
    
    /**
     * Minimum compute unit price for optimal fastlane performance.
     */
    val recommendedComputeUnitPrice: Long = 5_000_000
}

/**
 * # WebSocket Namespace
 * 
 * Standard Solana WebSocket subscriptions.
 * For high-performance streaming, use the Yellowstone namespace.
 */
class WebSocketNamespace internal constructor(private val client: IrisQuickNodeClient) {
    
    private val yellowstone = YellowstoneNamespace(client)
    
    /**
     * Subscribe to account changes.
     */
    fun accountSubscribe(
        pubkey: String,
        commitment: Commitment = Commitment.CONFIRMED
    ) = yellowstone.subscribeToAccount(pubkey, commitment)
    
    /**
     * Subscribe to logs mentioning specific accounts.
     */
    fun logsSubscribe(
        mentions: List<String>,
        commitment: Commitment = Commitment.CONFIRMED
    ) = yellowstone.subscribeToTransactions(mentions, commitment)
    
    /**
     * Subscribe to slot changes.
     */
    fun slotSubscribe() = yellowstone.subscribeToSlots()
    
    /**
     * Subscribe to program account changes.
     */
    fun programSubscribe(
        programId: String,
        commitment: Commitment = Commitment.CONFIRMED
    ) = yellowstone.subscribeToProgramAccounts(programId, commitment)
}

/**
 * # Smart Namespace
 * 
 * Intelligent transaction building with automatic optimization.
 * Combines priority fees, compute budget, and JITO tips.
 */
class SmartNamespace internal constructor(private val client: IrisQuickNodeClient) {
    
    /**
     * Build an optimized transaction with appropriate fees.
     * 
     * @param priorityLevel Desired priority level
     * @param useJito Use JITO for MEV protection
     * @param useFastlane Use Fastlane for sub-slot execution
     */
    suspend fun getOptimizationPlan(
        accounts: List<String> = emptyList(),
        priorityLevel: PriorityLevel = PriorityLevel.MEDIUM,
        useJito: Boolean = false,
        useFastlane: Boolean = true
    ): TransactionOptimizationPlan {
        val priorityFee = client.priority.estimatePriorityFees(accounts, priorityLevel)
        
        val jitoTip = if (useJito) {
            client.jito.getOptimalTip(priorityLevel)
        } else 0.0
        
        val fastlaneTip = if (useFastlane && !useJito) {
            0.001 // Minimum fastlane tip
        } else 0.0
        
        return TransactionOptimizationPlan(
            priorityFeeMicroLamports = priorityFee.toLong(),
            recommendedComputeUnits = 200_000,
            jitoTipSol = jitoTip,
            fastlaneTipSol = fastlaneTip,
            totalEstimatedCostSol = (priorityFee * 200_000 / 1_000_000_000_000.0) + jitoTip + fastlaneTip,
            useJito = useJito,
            useFastlane = useFastlane && !useJito
        )
    }
}

/**
 * Transaction optimization plan with fee recommendations.
 */
data class TransactionOptimizationPlan(
    val priorityFeeMicroLamports: Long,
    val recommendedComputeUnits: Int,
    val jitoTipSol: Double,
    val fastlaneTipSol: Double,
    val totalEstimatedCostSol: Double,
    val useJito: Boolean,
    val useFastlane: Boolean
)
