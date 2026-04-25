package xyz.selenus.luna.analytics

import kotlinx.coroutines.delay
import kotlinx.serialization.json.*
import xyz.selenus.luna.LunaHeliusClient
import xyz.selenus.luna.RpcError
import xyz.selenus.luna.RpcResponse
import xyz.selenus.luna.das.das  // extension for client.das (luna-das)
import xyz.selenus.luna.rpc.rpc  // extension for client.rpc (luna-rpc)

// ============================================================================
// ANALYTICS API (Advanced Wallet & Token Intelligence)
// ============================================================================

/**
 * Advanced analytics API for wallet intelligence, risk scoring, and portfolio analysis.
 * Provides insights that go beyond basic RPC data.
 */
class AnalyticsApi internal constructor(private val client: LunaHeliusClient) {
    
    /**
     * Compute a risk score for a wallet address.
     * Analyzes transaction patterns, interactions, and on-chain behavior.
     *
     * @param address The wallet address to analyze.
     */
    suspend fun getWalletRiskScore(address: String): RpcResponse<LunaHeliusClient.WalletRiskScore> {
        val factors = mutableListOf<String>()
        var riskScore = 0

        // 1. Check wallet age via first transaction
        val signatures = client.solana.getSignaturesForAddress(address, limit = 1000)
        val sigArray = signatures.result?.jsonArray

        val txCount = sigArray?.size ?: 0
        val firstSeen = sigArray?.lastOrNull()?.jsonObject?.get("blockTime")?.jsonPrimitive?.longOrNull

        // New wallets with high activity = higher risk
        val currentTime = System.currentTimeMillis() / 1000
        val ageSeconds = if (firstSeen != null) currentTime - firstSeen else 0L
        val ageDays = ageSeconds / 86400

        if (ageDays < 7 && txCount > 100) {
            riskScore += 30
            factors.add("New wallet with unusually high activity")
        } else if (ageDays < 30) {
            riskScore += 10
            factors.add("Relatively new wallet (< 30 days)")
        }

        // 2. Check for known high-risk patterns
        val balance = client.solana.getBalance(address)
        val lamports = balance.result?.let {
            if (it is JsonPrimitive) it.longOrNull
            else if (it is JsonObject) it["value"]?.jsonPrimitive?.longOrNull
            else null
        } ?: 0L

        if (lamports == 0L && txCount > 50) {
            riskScore += 20
            factors.add("Empty wallet with significant transaction history")
        }

        // 3. Analyze asset holdings
        val assets = client.das.getAssetsByOwner(address, limit = 100)
        val items = assets.result?.jsonObject?.get("items")?.jsonArray

        val interactedProtocols = mutableListOf<String>()
        items?.forEach { item ->
            val grouping = item.jsonObject["grouping"]?.jsonArray
            grouping?.forEach { group ->
                val value = group.jsonObject["group_value"]?.jsonPrimitive?.content
                if (value != null) interactedProtocols.add(value)
            }
        }

        val riskLevel = when {
            riskScore >= 50 -> "HIGH"
            riskScore >= 30 -> "MEDIUM"
            else -> "LOW"
        }

        return RpcResponse(result = LunaHeliusClient.WalletRiskScore(
            address = address,
            riskScore = minOf(100, riskScore),
            riskLevel = riskLevel,
            factors = factors.ifEmpty { listOf("No significant risk factors detected") },
            firstSeen = firstSeen,
            transactionCount = txCount,
            interactedProtocols = interactedProtocols.distinct().take(20)
        ))
    }

    /**
     * Analyze the health and safety of a token.
     *
     * @param mint The token mint address.
     */
    suspend fun getTokenHealthScore(mint: String): RpcResponse<LunaHeliusClient.TokenHealthScore> {
        var healthScore = 100
        val factors = mutableListOf<String>()

        // 1. Get token supply info
        val supplyResponse = client.solana.getTokenSupply(mint)
        val supply = supplyResponse.result?.jsonObject?.get("value")?.jsonObject

        // 2. Get largest holders
        val holdersResponse = client.solana.getTokenLargestAccounts(mint)
        val holders = holdersResponse.result?.jsonObject?.get("value")?.jsonArray

        // Calculate holder concentration
        var topHolderConcentration = 0.0
        if (holders != null && holders.size > 0) {
            val totalSupply = supply?.get("amount")?.jsonPrimitive?.content?.toLongOrNull() ?: 1L
            val topHolderAmount = holders[0].jsonObject["amount"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
            topHolderConcentration = if (totalSupply > 0) (topHolderAmount.toDouble() / totalSupply) * 100 else 0.0
            
            if (topHolderConcentration > 50) {
                healthScore -= 40
                factors.add("Top holder controls >50% of supply")
            } else if (topHolderConcentration > 20) {
                healthScore -= 20
                factors.add("Top holder controls >20% of supply")
            }
        }

        // 3. Check if metadata exists (DAS)
        val assetResponse = client.das.getAsset(mint)
        if (assetResponse.error != null) {
            healthScore -= 15
            factors.add("No metadata found for token")
        }

        val rugPullRisk = when {
            topHolderConcentration > 50 -> "HIGH"
            topHolderConcentration > 20 -> "MEDIUM"
            else -> "LOW"
        }

        val liquidityDepth = when {
            (holders?.size ?: 0) > 1000 -> "HIGH"
            (holders?.size ?: 0) > 100 -> "MEDIUM"
            else -> "LOW"
        }

        return RpcResponse(result = LunaHeliusClient.TokenHealthScore(
            mint = mint,
            healthScore = maxOf(0, healthScore),
            liquidityDepth = liquidityDepth,
            holderConcentration = topHolderConcentration,
            rugPullRisk = rugPullRisk,
            socialSentiment = null // Would need external API
        ))
    }

    /**
     * Get comprehensive portfolio analytics for a wallet.
     *
     * @param address The wallet address.
     */
    suspend fun getPortfolioAnalytics(address: String): RpcResponse<LunaHeliusClient.PortfolioAnalytics> {
        // Fetch portfolio data
        val portfolioResponse = client.niche.getWalletPortfolio(address, limit = 1000)
        if (portfolioResponse.error != null) return RpcResponse(error = portfolioResponse.error)

        val portfolio = portfolioResponse.result!!
        val items = portfolio.assets?.jsonObject?.get("items")?.jsonArray

        var tokenCount = 0
        var nftCount = 0
        val protocolSet = mutableSetOf<String>()

        items?.forEach { item ->
            val itemObj = item.jsonObject
            val isFungible = itemObj["interface"]?.jsonPrimitive?.content == "FungibleToken" ||
                             itemObj["interface"]?.jsonPrimitive?.content == "FungibleAsset"
            
            if (isFungible) tokenCount++ else nftCount++

            // Track protocols
            val grouping = itemObj["grouping"]?.jsonArray
            grouping?.forEach { group ->
                val value = group.jsonObject["group_value"]?.jsonPrimitive?.content
                if (value != null) protocolSet.add(value)
            }
        }

        val totalAssets = tokenCount + nftCount
        val diversificationScore = when {
            totalAssets > 50 && tokenCount > 10 && nftCount > 10 -> 90
            totalAssets > 20 -> 70
            totalAssets > 5 -> 50
            else -> 30
        }

        val riskProfile = when {
            nftCount > tokenCount * 2 -> "SPECULATIVE"
            tokenCount > 10 && portfolio.solBalance > 10 -> "BALANCED"
            portfolio.solBalance > 100 -> "CONSERVATIVE"
            else -> "MODERATE"
        }

        return RpcResponse(result = LunaHeliusClient.PortfolioAnalytics(
            totalValueUsd = null, // Would need price data
            solBalance = portfolio.solBalance,
            tokenCount = tokenCount,
            nftCount = nftCount,
            defiPositions = protocolSet.size,
            riskProfile = riskProfile,
            diversificationScore = diversificationScore
        ))
    }

    /**
     * Get network health metrics.
     */
    suspend fun getNetworkHealth(): RpcResponse<JsonElement> {
        val tpsResponse = client.niche.getTPS()
        val epochInfo = client.solana.getEpochInfo()
        val health = client.solana.getHealth()
        val version = client.solana.getVersion()

        return RpcResponse(result = buildJsonObject {
            put("tps", tpsResponse.result ?: 0.0)
            put("epochInfo", epochInfo.result ?: JsonNull)
            put("health", health.result?.jsonPrimitive?.content ?: "unknown")
            put("version", version.result ?: JsonNull)
            put("timestamp", System.currentTimeMillis())
        })
    }
}


// ============================================================================
// WEB2-INSPIRED INNOVATION APIs (v5.1.0 - Never-Before-Seen on Solana)
// ============================================================================

/**
 * Analytics Dashboard API - Web2-inspired real-time client.analytics.
 *
 * INDUSTRY FIRST: Brings web2 analytics patterns to Solana.
 * Features like session tracking, funnel analysis, and cohort metrics
 * that have never been implemented on Solana before.
 */
class AnalyticsDashboardApi internal constructor(private val client: LunaHeliusClient) {

    /**
     * Track wallet session with analytics events.
     * Web2 pattern: Session-based user tracking.
     *
     * @param walletAddress The wallet to track.
     */
    suspend fun startWalletSession(walletAddress: String): LunaHeliusClient.WalletSession {
        val sessionId = java.util.UUID.randomUUID().toString()
        
        // Get initial wallet state from Helius
        val balance = client.solana.getBalance(walletAddress)
        val tokens = client.das.getTokenAccounts(owner = walletAddress)
        val recentTx = client.rpc.getTransactionsForAddress(walletAddress, limit = 5)
        
        val initialBalance = balance.result?.let {
            if (it is JsonPrimitive) it.longOrNull
            else if (it is JsonObject) it["value"]?.jsonPrimitive?.longOrNull
            else null
        } ?: 0L
        
        val tokenCount = tokens.result?.jsonObject
            ?.get("items")?.jsonArray?.size ?: 0
        
        return LunaHeliusClient.WalletSession(
            sessionId = sessionId,
            walletAddress = walletAddress,
            startedAt = System.currentTimeMillis(),
            initialBalance = initialBalance,
            tokenCount = tokenCount,
            events = mutableListOf()
        )
    }

    /**
     * Analyze wallet transaction funnel.
     * Web2 pattern: Conversion funnel analysis.
     *
     * @param walletAddress The wallet to analyze.
     * @param funnelSteps Transaction types to track as funnel steps.
     */
    suspend fun analyzeTransactionFunnel(
        walletAddress: String,
        funnelSteps: List<String> = listOf("TRANSFER", "SWAP", "NFT_MINT", "STAKE")
    ): LunaHeliusClient.TransactionFunnel {
        val history = client.rpc.getTransactionsForAddress(walletAddress, limit = 100)
        val transactions = client.history.result?.jsonObject?.get("data")?.jsonArray
        
        val stepCounts = mutableMapOf<String, Int>()
        funnelSteps.forEach { stepCounts[it] = 0 }
        
        transactions?.forEach { tx ->
            val type = client.tx.jsonObject["type"]?.jsonPrimitive?.content
            if (type != null && stepCounts.containsKey(type)) {
                stepCounts[type] = stepCounts[type]!! + 1
            }
        }
        
        val steps = funnelSteps.mapIndexed { index, step ->
            LunaHeliusClient.FunnelStep(
                stepNumber = index + 1,
                stepName = step,
                count = stepCounts[step] ?: 0,
                conversionRate = if (index == 0) 1.0 
                    else (stepCounts[step] ?: 0).toDouble() / 
                        (stepCounts[funnelSteps[0]] ?: 1).coerceAtLeast(1)
            )
        }
        
        return LunaHeliusClient.TransactionFunnel(
            walletAddress = walletAddress,
            totalTransactions = transactions?.size ?: 0,
            steps = steps,
            overallConversion = steps.lastOrNull()?.conversionRate ?: 0.0,
            analyzedAt = System.currentTimeMillis()
        )
    }

    /**
     * Generate cohort analysis for wallet activity.
     * Web2 pattern: Cohort-based retention analysis.
     *
     * @param walletAddress The wallet to analyze.
     * @param periodDays Period size in days for cohort analysis.
     */
    suspend fun generateCohortAnalysis(
        walletAddress: String,
        periodDays: Int = 7
    ): LunaHeliusClient.CohortAnalysis {
        val history = client.rpc.getTransactionsForAddress(walletAddress, limit = 200)
        val transactions = client.history.result?.jsonObject?.get("data")?.jsonArray
        
        val now = System.currentTimeMillis()
        val periodMs = periodDays * 24 * 60 * 60 * 1000L
        
        val cohorts = mutableMapOf<Int, LunaHeliusClient.CohortPeriod>()
        
        transactions?.forEach { tx ->
            val timestamp = client.tx.jsonObject["timestamp"]?.jsonPrimitive?.longOrNull
                ?: return@forEach
            
            val periodIndex = ((now - timestamp * 1000) / periodMs).toInt()
            val existing = cohorts.getOrPut(periodIndex) {
                LunaHeliusClient.CohortPeriod(
                    periodIndex = periodIndex,
                    startDate = now - ((periodIndex + 1) * periodMs),
                    endDate = now - (periodIndex * periodMs),
                    transactionCount = 0,
                    uniquePrograms = mutableSetOf<String>()
                )
            }
            cohorts[periodIndex] = existing.copy(
                transactionCount = existing.transactionCount + 1
            )
        }
        
        val retentionRates = if (cohorts.isNotEmpty()) {
            val firstPeriodCount = cohorts[0]?.transactionCount ?: 1
            cohorts.map { (index, period) ->
                index to (period.transactionCount.toDouble() / firstPeriodCount)
            }.toMap()
        } else emptyMap()
        
        return LunaHeliusClient.CohortAnalysis(
            walletAddress = walletAddress,
            periodDays = periodDays,
            cohorts = cohorts.values.toList(),
            retentionRates = retentionRates,
            averageRetention = retentionRates.values.average().takeIf { !it.isNaN() } ?: 0.0,
            analyzedAt = System.currentTimeMillis()
        )
    }

    /**
     * Calculate wallet health score.
     * Web2 pattern: User health/engagement scoring.
     *
     * @param walletAddress The wallet to score.
     */
    suspend fun calculateWalletHealthScore(walletAddress: String): LunaHeliusClient.WalletHealthScore {
        // Gather metrics from Helius
        val balance = client.solana.getBalance(walletAddress)
        val tokens = client.das.getTokenAccounts(owner = walletAddress)
        val recentTx = client.rpc.getTransactionsForAddress(walletAddress, limit = 50)
        val nfts = client.das.getAssetsByOwner(walletAddress)
        
        // Calculate individual scores
        val balanceLamports = balance.result?.let {
            if (it is JsonPrimitive) it.longOrNull
            else if (it is JsonObject) it["value"]?.jsonPrimitive?.longOrNull
            else null
        } ?: 0L
        
        val balanceScore = when {
            balanceLamports >= 100_000_000_000 -> 100 // 100+ SOL
            balanceLamports >= 10_000_000_000 -> 80   // 10+ SOL
            balanceLamports >= 1_000_000_000 -> 60    // 1+ SOL
            balanceLamports >= 100_000_000 -> 40      // 0.1+ SOL
            else -> 20
        }
        
        val txCount = recentTx.result?.jsonObject?.get("data")?.jsonArray?.size ?: 0
        val activityScore = when {
            txCount >= 40 -> 100
            txCount >= 20 -> 80
            txCount >= 10 -> 60
            txCount >= 5 -> 40
            else -> 20
        }
        
        val tokenCount = tokens.result?.jsonObject?.get("items")?.jsonArray?.size ?: 0
        val diversificationScore = when {
            tokenCount >= 20 -> 100
            tokenCount >= 10 -> 80
            tokenCount >= 5 -> 60
            tokenCount >= 2 -> 40
            else -> 20
        }
        
        val nftCount = nfts.result?.jsonObject?.get("items")?.jsonArray?.size ?: 0
        val nftScore = when {
            nftCount >= 50 -> 100
            nftCount >= 20 -> 80
            nftCount >= 10 -> 60
            nftCount >= 5 -> 40
            else -> if (nftCount > 0) 30 else 0
        }
        
        val overallScore = (balanceScore + activityScore + diversificationScore + nftScore) / 4
        
        return LunaHeliusClient.WalletHealthScore(
            walletAddress = walletAddress,
            overallScore = overallScore,
            balanceScore = balanceScore,
            activityScore = activityScore,
            diversificationScore = diversificationScore,
            nftScore = nftScore,
            healthLevel = when {
                overallScore >= 80 -> "EXCELLENT"
                overallScore >= 60 -> "GOOD"
                overallScore >= 40 -> "MODERATE"
                else -> "LOW"
            },
            recommendations = buildRecommendations(
                balanceScore, activityScore, diversificationScore, nftScore
            ),
            calculatedAt = System.currentTimeMillis()
        )
    }

    private fun buildRecommendations(
        balanceScore: Int,
        activityScore: Int,
        diversificationScore: Int,
        nftScore: Int
    ): List<String> {
        val recommendations = mutableListOf<String>()
        if (balanceScore < 60) recommendations.add("Consider adding more SOL to your wallet")
        if (activityScore < 40) recommendations.add("Increase on-chain activity for better engagement")
        if (diversificationScore < 40) recommendations.add("Diversify your token holdings")
        if (nftScore < 20) recommendations.add("Explore NFT collections to increase portfolio diversity")
        return recommendations
    }
}

/**
 * Access the AnalyticsApi namespace from a [LunaHeliusClient].
 *
 * Import this extension to enable the `client.analytics` style:
 * ```
 * import xyz.selenus.luna.analytics.analytics
 * client.analytics.<method>()
 * ```
 */
val LunaHeliusClient.analytics: AnalyticsApi
    get() = AnalyticsApi(this)

/**
 * Access the AnalyticsDashboardApi namespace from a [LunaHeliusClient].
 *
 * Import this extension to enable the `client.analyticsDashboard` style:
 * ```
 * import xyz.selenus.luna.analytics.analyticsDashboard
 * client.analyticsDashboard.<method>()
 * ```
 */
val LunaHeliusClient.analyticsDashboard: AnalyticsDashboardApi
    get() = AnalyticsDashboardApi(this)
