package com.selenus.iris

import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*

/**
 * # Metis Namespace
 * 
 * Jupiter Swap API powered by QuickNode's Metis infrastructure.
 * The fastest, most reliable DEX aggregation for Solana.
 * 
 * ## Features
 * - Get swap quotes with optimal routes
 * - Execute swaps with customizable slippage
 * - Real-time token prices
 * - New pool detection
 * - Limit orders
 * - WebSocket streaming for live quotes
 * 
 * ## Why Metis?
 * - Elite latency with dedicated infrastructure
 * - Rolling restarts ensure fresh pool/market data
 * - Higher rate limits than public Jupiter API
 * - Integrated priority fee support
 * 
 * ## Configuration
 * Metis uses a separate API endpoint from the standard RPC. Options:
 * 1. **Private Endpoint** (recommended): Get from QuickNode dashboard after enabling Metis add-on
 *    Format: `https://jupiter-swap-api.quiknode.pro/YOUR_KEY`
 * 2. **Public Endpoint**: `https://public.jupiterapi.com` (rate limited)
 * 
 * Set the endpoint using `IrisQuickNodeClient.Builder().metisEndpoint(url)`
 */
class MetisNamespace internal constructor(private val client: IrisQuickNodeClient) {
    
    private val json = client.getJson()
    
    // Metis uses a separate endpoint - not the RPC endpoint
    private val metisEndpoint: String
        get() = client.getMetisEndpoint()
    
    // ========================================================================
    // QUOTE METHODS
    // ========================================================================
    
    /**
     * Get the best swap quote for a token pair.
     * 
     * @param inputMint The input token mint address
     * @param outputMint The output token mint address
     * @param amount The input amount in smallest units (lamports for SOL)
     * @param slippageBps Maximum slippage in basis points (100 = 1%)
     * @param swapMode "ExactIn" or "ExactOut"
     * @param onlyDirectRoutes Only return direct routes (no multi-hop)
     * @param asLegacyTransaction Return legacy transaction format
     * @param maxAccounts Max accounts for transaction (affects route selection)
     */
    suspend fun getQuote(
        inputMint: String,
        outputMint: String,
        amount: Long,
        slippageBps: Int = 50,
        swapMode: SwapMode = SwapMode.EXACT_IN,
        onlyDirectRoutes: Boolean = false,
        asLegacyTransaction: Boolean = false,
        maxAccounts: Int? = null
    ): JupiterQuote {
        val queryParams = buildString {
            append("?inputMint=$inputMint")
            append("&outputMint=$outputMint")
            append("&amount=$amount")
            append("&slippageBps=$slippageBps")
            append("&swapMode=${swapMode.value}")
            append("&onlyDirectRoutes=$onlyDirectRoutes")
            append("&asLegacyTransaction=$asLegacyTransaction")
            maxAccounts?.let { append("&maxAccounts=$it") }
        }
        
        return client.executeRestCallWithEndpoint(
            baseUrl = metisEndpoint,
            path = "/quote$queryParams",
            method = "GET",
            resultDeserializer = JupiterQuote.serializer()
        )
    }
    
    /**
     * Get quote using popular token symbols.
     * Convenience wrapper that resolves common symbols.
     */
    suspend fun getQuoteBySymbol(
        inputSymbol: String,
        outputSymbol: String,
        amount: Long,
        slippageBps: Int = 50
    ): JupiterQuote {
        val inputMint = resolveTokenSymbol(inputSymbol)
        val outputMint = resolveTokenSymbol(outputSymbol)
        
        return getQuote(
            inputMint = inputMint,
            outputMint = outputMint,
            amount = amount,
            slippageBps = slippageBps
        )
    }
    
    // ========================================================================
    // SWAP METHODS
    // ========================================================================
    
    /**
     * Build a swap transaction from a quote.
     * 
     * @param quote The quote obtained from getQuote
     * @param userPublicKey The user's wallet address
     * @param wrapUnwrapSOL Auto wrap/unwrap SOL
     * @param useSharedAccounts Use Jupiter's shared intermediate accounts
     * @param feeAccount Platform fee account (for platform fees)
     * @param computeUnitPriceMicroLamports Priority fee in micro-lamports
     * @param dynamicComputeUnitLimit Automatically set compute budget
     * @param skipUserAccountsRpcCalls Skip account fetching (faster but may fail)
     */
    suspend fun getSwapTransaction(
        quote: JupiterQuote,
        userPublicKey: String,
        wrapUnwrapSOL: Boolean = true,
        useSharedAccounts: Boolean = true,
        feeAccount: String? = null,
        computeUnitPriceMicroLamports: Long? = null,
        dynamicComputeUnitLimit: Boolean = true,
        skipUserAccountsRpcCalls: Boolean = false
    ): JupiterSwapResult {
        val body = buildJsonObject {
            put("quoteResponse", json.encodeToJsonElement(JupiterQuote.serializer(), quote))
            put("userPublicKey", userPublicKey)
            put("wrapAndUnwrapSol", wrapUnwrapSOL)
            put("useSharedAccounts", useSharedAccounts)
            put("dynamicComputeUnitLimit", dynamicComputeUnitLimit)
            put("skipUserAccountsRpcCalls", skipUserAccountsRpcCalls)
            feeAccount?.let { put("feeAccount", it) }
            computeUnitPriceMicroLamports?.let { put("computeUnitPriceMicroLamports", it) }
        }
        
        return client.executeRestCallWithEndpoint(
            baseUrl = metisEndpoint,
            path = "/swap",
            method = "POST",
            body = body,
            resultDeserializer = JupiterSwapResult.serializer()
        )
    }
    
    /**
     * Get swap instructions for custom transaction building.
     * Use this when you need to combine Jupiter swap with other instructions.
     * 
     * @param quote The quote obtained from getQuote
     * @param userPublicKey The user's wallet address
     */
    suspend fun getSwapInstructions(
        quote: JupiterQuote,
        userPublicKey: String,
        wrapUnwrapSOL: Boolean = true
    ): JsonElement {
        val body = buildJsonObject {
            put("quoteResponse", json.encodeToJsonElement(JupiterQuote.serializer(), quote))
            put("userPublicKey", userPublicKey)
            put("wrapAndUnwrapSol", wrapUnwrapSOL)
        }
        
        return client.executeRestCallWithEndpoint(
            baseUrl = metisEndpoint,
            path = "/swap-instructions",
            method = "POST",
            body = body,
            resultDeserializer = JsonElement.serializer()
        )
    }
    
    // ========================================================================
    // PRICE METHODS
    // ========================================================================
    
    /**
     * Get real-time token prices.
     * 
     * @param ids List of token mint addresses
     * @param vsToken Quote token (default: USDC)
     */
    suspend fun getPrice(
        ids: List<String>,
        vsToken: String = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v" // USDC
    ): Map<String, JupiterPrice> {
        val idsParam = ids.joinToString(",")
        
        val result = client.executeRestCallWithEndpoint(
            baseUrl = metisEndpoint,
            path = "/price?ids=$idsParam&vsToken=$vsToken",
            method = "GET",
            resultDeserializer = JsonElement.serializer()
        )
        
        val data = result.jsonObject["data"]?.jsonObject ?: return emptyMap()
        return data.mapValues { (_, value) ->
            json.decodeFromJsonElement(JupiterPrice.serializer(), value)
        }
    }
    
    /**
     * Get the price of a single token in USD.
     */
    suspend fun getTokenPriceUSD(mint: String): Double? {
        val prices = getPrice(listOf(mint))
        return prices[mint]?.price
    }
    
    /**
     * Get SOL price in USD.
     */
    suspend fun getSolPriceUSD(): Double? {
        return getTokenPriceUSD(WSOL_MINT)
    }
    
    // ========================================================================
    // NEW POOLS / DISCOVERY
    // ========================================================================
    
    /**
     * Get recently created liquidity pools.
     * Essential for new token detection and sniping.
     * 
     * @param limit Maximum number of pools to return
     */
    suspend fun getNewPools(limit: Int = 50): List<JupiterNewPool> {
        val result = client.executeRestCallWithEndpoint(
            baseUrl = metisEndpoint,
            path = "/new-pools?limit=$limit",
            method = "GET",
            resultDeserializer = JsonElement.serializer()
        )
        
        val pools = result.jsonObject["pools"]?.jsonArray ?: return emptyList()
        return pools.map { json.decodeFromJsonElement(JupiterNewPool.serializer(), it) }
    }
    
    // ========================================================================
    // LIMIT ORDERS
    // ========================================================================
    
    /**
     * Create a limit order.
     * 
     * @param inputMint Token to sell
     * @param outputMint Token to buy
     * @param inAmount Amount to sell in smallest units
     * @param outAmount Minimum amount to receive
     * @param maker The order creator's address
     * @param expireAt Optional expiration timestamp
     */
    suspend fun createLimitOrder(
        inputMint: String,
        outputMint: String,
        inAmount: Long,
        outAmount: Long,
        maker: String,
        expireAt: Long? = null
    ): JsonElement {
        val body = buildJsonObject {
            put("inputMint", inputMint)
            put("outputMint", outputMint)
            put("inAmount", inAmount.toString())
            put("outAmount", outAmount.toString())
            put("maker", maker)
            expireAt?.let { put("expireAt", it) }
        }
        
        return client.executeRestCallWithEndpoint(
            baseUrl = metisEndpoint,
            path = "/limit-orders/create",
            method = "POST",
            body = body,
            resultDeserializer = JsonElement.serializer()
        )
    }
    
    /**
     * Cancel a limit order.
     * 
     * @param maker The order creator's address
     * @param orderPublicKey The order's public key
     */
    suspend fun cancelLimitOrder(
        maker: String,
        orderPublicKey: String
    ): JsonElement {
        val body = buildJsonObject {
            put("maker", maker)
            put("orders", JsonArray(listOf(JsonPrimitive(orderPublicKey))))
        }
        
        return client.executeRestCallWithEndpoint(
            baseUrl = metisEndpoint,
            path = "/limit-orders/cancel",
            method = "POST",
            body = body,
            resultDeserializer = JsonElement.serializer()
        )
    }
    
    /**
     * Get open limit orders for an address.
     * 
     * @param wallet The wallet address
     */
    suspend fun getOpenLimitOrders(wallet: String): List<JupiterLimitOrder> {
        val result = client.executeRestCallWithEndpoint(
            baseUrl = metisEndpoint,
            path = "/limit-orders/open?wallet=$wallet",
            method = "GET",
            resultDeserializer = JsonElement.serializer()
        )
        
        val orders = result.jsonArray
        return orders.map { json.decodeFromJsonElement(JupiterLimitOrder.serializer(), it) }
    }
    
    /**
     * Get limit order history for an address.
     * 
     * @param wallet The wallet address
     */
    suspend fun getLimitOrderHistory(wallet: String): JsonElement {
        return client.executeRestCallWithEndpoint(
            baseUrl = metisEndpoint,
            path = "/limit-orders/history?wallet=$wallet",
            method = "GET",
            resultDeserializer = JsonElement.serializer()
        )
    }
    
    // ========================================================================
    // TOKEN LISTS
    // ========================================================================
    
    /**
     * Get verified token list from Jupiter.
     */
    suspend fun getVerifiedTokens(): JsonElement {
        return client.executeRestCallWithEndpoint(
            baseUrl = metisEndpoint,
            path = "/tokens?tags=verified",
            method = "GET",
            resultDeserializer = JsonElement.serializer()
        )
    }
    
    /**
     * Get all tradeable tokens.
     */
    suspend fun getAllTokens(): JsonElement {
        return client.executeRestCallWithEndpoint(
            baseUrl = metisEndpoint,
            path = "/tokens",
            method = "GET",
            resultDeserializer = JsonElement.serializer()
        )
    }
    
    // ========================================================================
    // HELPER METHODS
    // ========================================================================
    
    /**
     * Resolve common token symbols to mint addresses.
     */
    private fun resolveTokenSymbol(symbol: String): String {
        return when (symbol.uppercase()) {
            "SOL", "WSOL" -> WSOL_MINT
            "USDC" -> USDC_MINT
            "USDT" -> USDT_MINT
            "BONK" -> BONK_MINT
            "JUP" -> JUP_MINT
            "RAY" -> RAY_MINT
            "ORCA" -> ORCA_MINT
            else -> symbol // Assume it's already a mint address
        }
    }
    
    companion object {
        // Common token mints
        const val WSOL_MINT = "So11111111111111111111111111111111111111112"
        const val USDC_MINT = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v"
        const val USDT_MINT = "Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB"
        const val BONK_MINT = "DezXAZ8z7PnrnRJjz3wXBoRgixCa6xjnB7YaB1pPB263"
        const val JUP_MINT = "JUPyiwrYJFskUPiHa7hkeR8VUtAeFoSYbKedZNsDvCN"
        const val RAY_MINT = "4k3Dyjzvzp8eMZWUXbBCjEvwSkkk59S5iCNLY3QrkX6R"
        const val ORCA_MINT = "orcaEKTdK7LKz57vaAYr9QeNsVEPfiu6QeMU1kektZE"
    }
}

/**
 * Swap mode for Jupiter quotes.
 */
enum class SwapMode(val value: String) {
    /** Specify exact input amount */
    EXACT_IN("ExactIn"),
    /** Specify exact output amount */
    EXACT_OUT("ExactOut")
}
