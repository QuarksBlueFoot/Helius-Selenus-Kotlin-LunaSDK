package xyz.selenus.luna.nlp.suggestions

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import xyz.selenus.luna.nlp.IntentType
import xyz.selenus.luna.nlp.TransactionIntent
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap

/**
 * Smart Suggestion Engine - Proactive AI-like suggestions without AI
 * 
 * Analyzes:
 * - User behavior patterns
 * - Transaction history
 * - Network trends
 * - Time-based patterns
 * - Portfolio state
 * 
 * Provides intelligent suggestions following Solana Foundation UX guidelines
 */
class SmartSuggestionEngine private constructor(
    private val config: SuggestionConfig,
    private val portfolioProvider: PortfolioProvider?,
    private val trendProvider: TrendProvider?
) {
    
    private val _suggestions = MutableStateFlow<List<SmartSuggestion>>(emptyList())
    val suggestions: StateFlow<List<SmartSuggestion>> = _suggestions.asStateFlow()
    
    // Behavior tracking
    private val intentFrequency = ConcurrentHashMap<IntentType, Int>()
    private val recipientHistory = ConcurrentHashMap<String, RecipientStats>()
    private val tokenUsage = ConcurrentHashMap<String, TokenUsageStats>()
    private val timePatterns = TimePatternAnalyzer()
    
    companion object {
        fun create(config: SuggestionConfig = SuggestionConfig()): SmartSuggestionEngine {
            return SmartSuggestionEngine(config, null, null)
        }
        
        fun create(
            config: SuggestionConfig,
            portfolioProvider: PortfolioProvider,
            trendProvider: TrendProvider
        ): SmartSuggestionEngine {
            return SmartSuggestionEngine(config, portfolioProvider, trendProvider)
        }
    }
    
    /**
     * Record an executed intent for learning
     */
    fun recordIntent(intent: TransactionIntent, timestamp: Long = System.currentTimeMillis()) {
        // Track intent type frequency
        when (intent) {
            is TransactionIntent.TransferSol -> {
                intentFrequency.compute(IntentType.TRANSFER_SOL) { _, v -> (v ?: 0) + 1 }
                trackRecipient(intent.recipientResolved ?: intent.recipient, intent.amount, "SOL", timestamp)
            }
            is TransactionIntent.TransferToken -> {
                intentFrequency.compute(IntentType.TRANSFER_TOKEN) { _, v -> (v ?: 0) + 1 }
                trackRecipient(intent.recipientResolved ?: intent.recipient, intent.amount, intent.token, timestamp)
                trackToken(intent.token, intent.amount, timestamp)
            }
            is TransactionIntent.Swap -> {
                intentFrequency.compute(IntentType.SWAP) { _, v -> (v ?: 0) + 1 }
                trackToken(intent.inputToken, intent.inputAmount, timestamp)
                trackToken(intent.outputToken, BigDecimal.ZERO, timestamp)
            }
            is TransactionIntent.Stake -> {
                intentFrequency.compute(IntentType.STAKE) { _, v -> (v ?: 0) + 1 }
            }
            else -> {}
        }
        
        // Track time patterns
        timePatterns.record(intent, timestamp)
        
        // Refresh suggestions
        refreshSuggestions()
    }
    
    /**
     * Get current smart suggestions
     */
    suspend fun getSuggestions(): List<SmartSuggestion> {
        refreshSuggestions()
        return _suggestions.value
    }
    
    /**
     * Get suggestions as a stream
     */
    fun observeSuggestions(): Flow<List<SmartSuggestion>> = flow {
        while (true) {
            refreshSuggestions()
            emit(_suggestions.value)
            kotlinx.coroutines.delay(config.refreshIntervalMs)
        }
    }
    
    private fun refreshSuggestions() {
        val suggestions = mutableListOf<SmartSuggestion>()
        
        // Pattern-based suggestions
        suggestions.addAll(generatePatternSuggestions())
        
        // Time-based suggestions
        suggestions.addAll(generateTimeSuggestions())
        
        // Portfolio-based suggestions
        portfolioProvider?.let {
            suggestions.addAll(generatePortfolioSuggestions(it))
        }
        
        // Trend-based suggestions
        trendProvider?.let {
            suggestions.addAll(generateTrendSuggestions(it))
        }
        
        // Gas/fee optimization suggestions
        suggestions.addAll(generateOptimizationSuggestions())
        
        // Sort by relevance and take top N
        _suggestions.value = suggestions
            .sortedByDescending { it.relevanceScore }
            .take(config.maxSuggestions)
    }
    
    private fun generatePatternSuggestions(): List<SmartSuggestion> {
        val suggestions = mutableListOf<SmartSuggestion>()
        
        // Frequent recipients
        recipientHistory.entries
            .sortedByDescending { it.value.transactionCount }
            .take(3)
            .forEach { (address, stats) ->
                suggestions.add(
                    SmartSuggestion(
                        type = SuggestionType.FREQUENT_RECIPIENT,
                        title = "Send to ${stats.label ?: address.take(8)}...",
                        description = "You've sent to this address ${stats.transactionCount} times",
                        command = "send ${stats.averageAmount} ${stats.primaryToken} to $address",
                        relevanceScore = 0.7 + (stats.transactionCount * 0.05).coerceAtMost(0.3),
                        metadata = mapOf(
                            "address" to address,
                            "count" to stats.transactionCount.toString()
                        )
                    )
                )
            }
        
        // Frequent tokens
        tokenUsage.entries
            .sortedByDescending { it.value.usageCount }
            .take(3)
            .filter { it.key != "SOL" }
            .forEach { (token, stats) ->
                suggestions.add(
                    SmartSuggestion(
                        type = SuggestionType.FREQUENT_TOKEN,
                        title = "Trade $token",
                        description = "Frequently used token (${stats.usageCount} transactions)",
                        command = "swap SOL for $token",
                        relevanceScore = 0.6 + (stats.usageCount * 0.03).coerceAtMost(0.2)
                    )
                )
            }
        
        return suggestions
    }
    
    private fun generateTimeSuggestions(): List<SmartSuggestion> {
        val suggestions = mutableListOf<SmartSuggestion>()
        
        // Check for recurring patterns
        timePatterns.getRecurringPatterns().forEach { pattern ->
            if (pattern.shouldTrigger()) {
                suggestions.add(
                    SmartSuggestion(
                        type = SuggestionType.RECURRING_PATTERN,
                        title = "Time for your ${pattern.description}?",
                        description = "You usually do this around this time",
                        command = pattern.suggestedCommand,
                        relevanceScore = 0.85,
                        metadata = mapOf("pattern" to pattern.id)
                    )
                )
            }
        }
        
        // Day-of-week patterns
        val dayOfWeek = java.time.LocalDate.now().dayOfWeek
        timePatterns.getDayPatterns(dayOfWeek).forEach { pattern ->
            suggestions.add(
                SmartSuggestion(
                    type = SuggestionType.TIME_BASED,
                    title = pattern.title,
                    description = "Common action on ${dayOfWeek.name.lowercase().capitalize()}s",
                    command = pattern.suggestedCommand,
                    relevanceScore = 0.7
                )
            )
        }
        
        return suggestions
    }
    
    private fun generatePortfolioSuggestions(provider: PortfolioProvider): List<SmartSuggestion> {
        val suggestions = mutableListOf<SmartSuggestion>()
        val portfolio = provider.getPortfolio()
        
        // Rebalancing suggestions
        portfolio.imbalances.forEach { imbalance ->
            suggestions.add(
                SmartSuggestion(
                    type = SuggestionType.REBALANCE,
                    title = "Rebalance ${imbalance.token}",
                    description = "${imbalance.currentPercent}% → ${imbalance.targetPercent}% target",
                    command = if (imbalance.currentPercent > imbalance.targetPercent) {
                        "swap ${imbalance.excessAmount} ${imbalance.token} for ${imbalance.swapTo}"
                    } else {
                        "swap ${imbalance.neededAmount} ${imbalance.swapFrom} for ${imbalance.token}"
                    },
                    relevanceScore = 0.8,
                    urgency = if (imbalance.deviation > 20) Urgency.HIGH else Urgency.MEDIUM
                )
            )
        }
        
        // Low balance warnings
        if (portfolio.solBalance < BigDecimal("0.1")) {
            suggestions.add(
                SmartSuggestion(
                    type = SuggestionType.LOW_BALANCE,
                    title = "Low SOL balance",
                    description = "You have ${portfolio.solBalance} SOL - may not cover fees",
                    command = "check balance",
                    relevanceScore = 0.95,
                    urgency = Urgency.HIGH
                )
            )
        }
        
        // Staking opportunities
        if (portfolio.solBalance > BigDecimal("10") && portfolio.stakedSol == BigDecimal.ZERO) {
            suggestions.add(
                SmartSuggestion(
                    type = SuggestionType.STAKING_OPPORTUNITY,
                    title = "Stake your SOL",
                    description = "Earn ~7% APY on ${portfolio.solBalance} SOL",
                    command = "stake ${portfolio.solBalance.multiply(BigDecimal("0.9"))} SOL",
                    relevanceScore = 0.75,
                    urgency = Urgency.LOW
                )
            )
        }
        
        // Unclaimed rewards
        portfolio.unclaimedRewards.forEach { reward ->
            suggestions.add(
                SmartSuggestion(
                    type = SuggestionType.UNCLAIMED_REWARDS,
                    title = "Claim ${reward.amount} ${reward.token}",
                    description = "Unclaimed rewards from ${reward.source}",
                    command = "claim rewards",
                    relevanceScore = 0.85,
                    urgency = Urgency.MEDIUM
                )
            )
        }
        
        return suggestions
    }
    
    private fun generateTrendSuggestions(provider: TrendProvider): List<SmartSuggestion> {
        val suggestions = mutableListOf<SmartSuggestion>()
        val trends = provider.getCurrentTrends()
        
        // Token price movements
        trends.priceMovements.filter { it.changePercent.abs() > BigDecimal("5") }.forEach { movement ->
            val isUp = movement.changePercent > BigDecimal.ZERO
            suggestions.add(
                SmartSuggestion(
                    type = if (isUp) SuggestionType.PRICE_UP else SuggestionType.PRICE_DOWN,
                    title = "${movement.token} ${if (isUp) "↑" else "↓"} ${movement.changePercent.abs()}%",
                    description = "24h price ${if (isUp) "increase" else "decrease"}",
                    command = if (isUp) "swap SOL for ${movement.token}" else "swap ${movement.token} for USDC",
                    relevanceScore = 0.6 + (movement.changePercent.abs().toDouble() * 0.01).coerceAtMost(0.2)
                )
            )
        }
        
        // Trending tokens
        trends.trendingTokens.take(3).forEach { token ->
            suggestions.add(
                SmartSuggestion(
                    type = SuggestionType.TRENDING,
                    title = "${token.symbol} is trending",
                    description = "${token.volumeIncrease}% volume increase",
                    command = "check ${token.symbol} price",
                    relevanceScore = 0.5
                )
            )
        }
        
        // Gas price suggestions
        if (trends.currentPriorityFee < trends.averagePriorityFee * 0.5) {
            suggestions.add(
                SmartSuggestion(
                    type = SuggestionType.LOW_FEES,
                    title = "Low network fees",
                    description = "Good time for transactions",
                    command = "", // No specific command
                    relevanceScore = 0.4,
                    urgency = Urgency.LOW
                )
            )
        }
        
        return suggestions
    }
    
    private fun generateOptimizationSuggestions(): List<SmartSuggestion> {
        val suggestions = mutableListOf<SmartSuggestion>()
        
        // Check if user has many small token balances (dust)
        val dustTokens = tokenUsage.filter { it.value.lastBalance < BigDecimal("1") }
        if (dustTokens.size >= 3) {
            suggestions.add(
                SmartSuggestion(
                    type = SuggestionType.DUST_CONSOLIDATION,
                    title = "Consolidate dust",
                    description = "You have ${dustTokens.size} small token balances",
                    command = "consolidate tokens",
                    relevanceScore = 0.5,
                    urgency = Urgency.LOW
                )
            )
        }
        
        return suggestions
    }
    
    private fun trackRecipient(address: String, amount: BigDecimal, token: String, timestamp: Long) {
        recipientHistory.compute(address) { _, existing ->
            if (existing == null) {
                RecipientStats(
                    address = address,
                    label = null,
                    transactionCount = 1,
                    totalSent = amount,
                    averageAmount = amount,
                    primaryToken = token,
                    lastTransactionTime = timestamp
                )
            } else {
                existing.copy(
                    transactionCount = existing.transactionCount + 1,
                    totalSent = existing.totalSent + amount,
                    averageAmount = (existing.totalSent + amount) / BigDecimal(existing.transactionCount + 1),
                    lastTransactionTime = timestamp
                )
            }
        }
    }
    
    private fun trackToken(token: String, amount: BigDecimal, timestamp: Long) {
        tokenUsage.compute(token) { _, existing ->
            if (existing == null) {
                TokenUsageStats(
                    token = token,
                    usageCount = 1,
                    totalVolume = amount,
                    lastUsedTime = timestamp,
                    lastBalance = BigDecimal.ZERO
                )
            } else {
                existing.copy(
                    usageCount = existing.usageCount + 1,
                    totalVolume = existing.totalVolume + amount,
                    lastUsedTime = timestamp
                )
            }
        }
    }
}

/**
 * Smart suggestion
 */
data class SmartSuggestion(
    val type: SuggestionType,
    val title: String,
    val description: String,
    val command: String,
    val relevanceScore: Double,
    val urgency: Urgency = Urgency.NORMAL,
    val metadata: Map<String, String> = emptyMap()
) {
    val id: String = "${type.name}_${title.hashCode()}"
}

/**
 * Suggestion types
 */
enum class SuggestionType {
    FREQUENT_RECIPIENT,
    FREQUENT_TOKEN,
    RECURRING_PATTERN,
    TIME_BASED,
    REBALANCE,
    LOW_BALANCE,
    STAKING_OPPORTUNITY,
    UNCLAIMED_REWARDS,
    PRICE_UP,
    PRICE_DOWN,
    TRENDING,
    LOW_FEES,
    DUST_CONSOLIDATION,
    SECURITY_ALERT
}

/**
 * Urgency level
 */
enum class Urgency {
    LOW, NORMAL, MEDIUM, HIGH, CRITICAL
}

/**
 * Suggestion configuration
 */
data class SuggestionConfig(
    val maxSuggestions: Int = 5,
    val refreshIntervalMs: Long = 30000,
    val enableTrending: Boolean = true,
    val enablePortfolio: Boolean = true,
    val enableTimePatterns: Boolean = true
)

/**
 * Recipient statistics
 */
data class RecipientStats(
    val address: String,
    val label: String?,
    val transactionCount: Int,
    val totalSent: BigDecimal,
    val averageAmount: BigDecimal,
    val primaryToken: String,
    val lastTransactionTime: Long
)

/**
 * Token usage statistics
 */
data class TokenUsageStats(
    val token: String,
    val usageCount: Int,
    val totalVolume: BigDecimal,
    val lastUsedTime: Long,
    val lastBalance: BigDecimal
)

/**
 * Time pattern analyzer
 */
class TimePatternAnalyzer {
    private val hourlyPatterns = ConcurrentHashMap<Int, MutableList<IntentType>>()
    private val dailyPatterns = ConcurrentHashMap<java.time.DayOfWeek, MutableList<IntentType>>()
    
    fun record(intent: TransactionIntent, timestamp: Long) {
        val dateTime = java.time.Instant.ofEpochMilli(timestamp)
            .atZone(java.time.ZoneId.systemDefault())
        
        val hour = dateTime.hour
        val day = dateTime.dayOfWeek
        
        hourlyPatterns.computeIfAbsent(hour) { mutableListOf() }
            .add(getIntentType(intent))
        
        dailyPatterns.computeIfAbsent(day) { mutableListOf() }
            .add(getIntentType(intent))
    }
    
    fun getRecurringPatterns(): List<RecurringPattern> {
        val patterns = mutableListOf<RecurringPattern>()
        
        // Find hourly patterns
        hourlyPatterns.forEach { (hour, intents) ->
            val mostCommon = intents.groupingBy { it }.eachCount().maxByOrNull { it.value }
            if (mostCommon != null && mostCommon.value >= 3) {
                patterns.add(
                    RecurringPattern(
                        id = "hourly_${hour}_${mostCommon.key}",
                        description = "${mostCommon.key.name.lowercase()} at $hour:00",
                        triggerHour = hour,
                        intentType = mostCommon.key,
                        suggestedCommand = getDefaultCommand(mostCommon.key)
                    )
                )
            }
        }
        
        return patterns
    }
    
    fun getDayPatterns(day: java.time.DayOfWeek): List<DayPattern> {
        val patterns = mutableListOf<DayPattern>()
        
        dailyPatterns[day]?.let { intents ->
            val mostCommon = intents.groupingBy { it }.eachCount().maxByOrNull { it.value }
            if (mostCommon != null && mostCommon.value >= 2) {
                patterns.add(
                    DayPattern(
                        day = day,
                        intentType = mostCommon.key,
                        title = "${mostCommon.key.name.lowercase().capitalize()} on ${day.name.lowercase().capitalize()}s",
                        suggestedCommand = getDefaultCommand(mostCommon.key)
                    )
                )
            }
        }
        
        return patterns
    }
    
    private fun getIntentType(intent: TransactionIntent): IntentType {
        return when (intent) {
            is TransactionIntent.TransferSol -> IntentType.TRANSFER_SOL
            is TransactionIntent.TransferToken -> IntentType.TRANSFER_TOKEN
            is TransactionIntent.Swap -> IntentType.SWAP
            is TransactionIntent.Stake -> IntentType.STAKE
            else -> IntentType.GET_BALANCE
        }
    }
    
    private fun getDefaultCommand(type: IntentType): String {
        return when (type) {
            IntentType.TRANSFER_SOL -> "send SOL"
            IntentType.TRANSFER_TOKEN -> "send tokens"
            IntentType.SWAP -> "swap tokens"
            IntentType.STAKE -> "stake SOL"
            else -> "check balance"
        }
    }
}

/**
 * Recurring pattern
 */
data class RecurringPattern(
    val id: String,
    val description: String,
    val triggerHour: Int,
    val intentType: IntentType,
    val suggestedCommand: String
) {
    fun shouldTrigger(): Boolean {
        val currentHour = java.time.LocalTime.now().hour
        return currentHour == triggerHour
    }
}

/**
 * Day pattern
 */
data class DayPattern(
    val day: java.time.DayOfWeek,
    val intentType: IntentType,
    val title: String,
    val suggestedCommand: String
)

/**
 * Portfolio provider interface
 */
interface PortfolioProvider {
    fun getPortfolio(): Portfolio
}

/**
 * Portfolio state
 */
data class Portfolio(
    val solBalance: BigDecimal,
    val stakedSol: BigDecimal,
    val tokenBalances: Map<String, BigDecimal>,
    val nftCount: Int,
    val imbalances: List<PortfolioImbalance>,
    val unclaimedRewards: List<UnclaimedReward>
)

data class PortfolioImbalance(
    val token: String,
    val currentPercent: Double,
    val targetPercent: Double,
    val deviation: Double,
    val excessAmount: BigDecimal,
    val neededAmount: BigDecimal,
    val swapTo: String,
    val swapFrom: String
)

data class UnclaimedReward(
    val token: String,
    val amount: BigDecimal,
    val source: String
)

/**
 * Trend provider interface
 */
interface TrendProvider {
    fun getCurrentTrends(): Trends
}

/**
 * Market trends
 */
data class Trends(
    val priceMovements: List<PriceMovement>,
    val trendingTokens: List<TrendingToken>,
    val currentPriorityFee: Long,
    val averagePriorityFee: Long
)

data class PriceMovement(
    val token: String,
    val currentPrice: BigDecimal,
    val changePercent: BigDecimal
)

data class TrendingToken(
    val symbol: String,
    val volumeIncrease: Int,
    val socialMentions: Int
)
