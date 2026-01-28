package xyz.selenus.luna.nlp.chain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import xyz.selenus.luna.nlp.EntityResolver
import xyz.selenus.luna.nlp.IntentType
import xyz.selenus.luna.nlp.NaturalLanguageBuilder
import xyz.selenus.luna.nlp.ParseResult
import xyz.selenus.luna.nlp.TransactionIntent
import java.math.BigDecimal
import java.util.regex.Pattern

/**
 * Intent Chain Parser - Parse complex multi-step commands
 * 
 * Supports:
 * - Sequential chaining: "send 1 SOL to alice.sol and then swap 10 USDC for BONK"
 * - Conditional chaining: "if SOL > $100 then swap all to USDC"
 * - Batch operations: "send 0.1 SOL to alice.sol, bob.sol, and carol.sol"
 * - Scheduled intents: "send 1 SOL to alice.sol tomorrow at 3pm"
 * - Recurring intents: "send 0.5 SOL to alice.sol every week"
 * 
 * Follows Solana Mobile Standard for transaction batching
 */
class IntentChainParser(
    private val nlpBuilder: NaturalLanguageBuilder,
    private val resolver: EntityResolver
) {
    
    companion object {
        // Chain connectors
        private val AND_THEN_PATTERN = Pattern.compile(
            """(.+?)\s+(?:and\s+then|then|and|,\s*then)\s+(.+)""",
            Pattern.CASE_INSENSITIVE
        )
        
        // Conditional patterns
        private val IF_THEN_PATTERN = Pattern.compile(
            """if\s+(.+?)\s+then\s+(.+?)(?:\s+else\s+(.+))?""",
            Pattern.CASE_INSENSITIVE
        )
        
        // Batch pattern (comma-separated addresses)
        private val BATCH_ADDRESSES_PATTERN = Pattern.compile(
            """(?:send|transfer)\s+(\d+(?:\.\d+)?)\s*(sol|◎)?\s+to\s+(.+)""",
            Pattern.CASE_INSENSITIVE
        )
        
        // Schedule patterns
        private val SCHEDULE_PATTERN = Pattern.compile(
            """(.+?)\s+(?:at|on|in)\s+(tomorrow|next\s+\w+|\d{1,2}:\d{2}(?:\s*[ap]m)?|in\s+\d+\s+(?:minutes?|hours?|days?))""",
            Pattern.CASE_INSENSITIVE
        )
        
        // Recurring pattern
        private val RECURRING_PATTERN = Pattern.compile(
            """(.+?)\s+every\s+(day|week|month|hour|\d+\s+(?:minutes?|hours?|days?))""",
            Pattern.CASE_INSENSITIVE
        )
        
        // Percentage pattern for partial amounts
        private val PERCENTAGE_PATTERN = Pattern.compile(
            """(\d+(?:\.\d+)?)\s*%\s*(?:of\s+)?(?:my\s+)?(\w+)""",
            Pattern.CASE_INSENSITIVE
        )
    }
    
    /**
     * Parse input that may contain chains, conditions, or batches
     */
    suspend fun parse(input: String): ChainParseResult {
        val normalized = input.trim().lowercase()
        
        // Check for recurring first
        val recurringMatch = RECURRING_PATTERN.matcher(normalized)
        if (recurringMatch.find()) {
            return parseRecurring(recurringMatch.group(1), recurringMatch.group(2))
        }
        
        // Check for scheduled
        val scheduleMatch = SCHEDULE_PATTERN.matcher(normalized)
        if (scheduleMatch.find()) {
            return parseScheduled(scheduleMatch.group(1), scheduleMatch.group(2))
        }
        
        // Check for conditional
        val conditionalMatch = IF_THEN_PATTERN.matcher(normalized)
        if (conditionalMatch.find()) {
            return parseConditional(
                conditionalMatch.group(1),
                conditionalMatch.group(2),
                conditionalMatch.group(3)
            )
        }
        
        // Check for batch addresses
        val batchMatch = BATCH_ADDRESSES_PATTERN.matcher(normalized)
        if (batchMatch.find()) {
            val addresses = batchMatch.group(3)
            if (addresses.contains(",") || addresses.contains(" and ")) {
                return parseBatch(input)
            }
        }
        
        // Check for chain (and then / then)
        val chainMatch = AND_THEN_PATTERN.matcher(normalized)
        if (chainMatch.find()) {
            return parseChain(chainMatch.group(1), chainMatch.group(2))
        }
        
        // Single intent
        val result = nlpBuilder.parse(input)
        return when (result) {
            is ParseResult.Success -> ChainParseResult.Single(result)
            is ParseResult.NeedsInfo -> ChainParseResult.NeedsInfo(result)
            is ParseResult.Unknown -> ChainParseResult.Unknown(result)
            is ParseResult.Ambiguous -> ChainParseResult.Ambiguous(result)
        }
    }
    
    /**
     * Parse a chain of commands
     */
    private suspend fun parseChain(first: String, rest: String): ChainParseResult {
        val intents = mutableListOf<ParseResult.Success>()
        
        // Parse first part
        when (val firstResult = nlpBuilder.parse(first)) {
            is ParseResult.Success -> intents.add(firstResult)
            else -> return ChainParseResult.ChainError(
                step = 0,
                error = "Could not parse first command: $first",
                parsed = emptyList()
            )
        }
        
        // Recursively parse rest (may contain more chains)
        val restResult = parse(rest)
        when (restResult) {
            is ChainParseResult.Single -> intents.add(restResult.result)
            is ChainParseResult.Chain -> intents.addAll(restResult.intents)
            is ChainParseResult.ChainError -> return ChainParseResult.ChainError(
                step = restResult.step + 1,
                error = restResult.error,
                parsed = intents
            )
            else -> return ChainParseResult.ChainError(
                step = 1,
                error = "Could not parse subsequent command: $rest",
                parsed = intents
            )
        }
        
        return ChainParseResult.Chain(
            intents = intents,
            executionMode = ChainExecutionMode.SEQUENTIAL
        )
    }
    
    /**
     * Parse batch operations (multiple recipients)
     */
    private suspend fun parseBatch(input: String): ChainParseResult {
        val batchMatch = BATCH_ADDRESSES_PATTERN.matcher(input.lowercase())
        if (!batchMatch.find()) {
            return ChainParseResult.Unknown(ParseResult.Unknown(input, emptyList()))
        }
        
        val amount = BigDecimal(batchMatch.group(1))
        val addressesRaw = batchMatch.group(3)
        
        // Split by commas and "and"
        val addresses = addressesRaw
            .replace(Regex("\\s+and\\s+"), ",")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        
        if (addresses.size < 2) {
            return ChainParseResult.Unknown(ParseResult.Unknown(input, emptyList()))
        }
        
        val intents = mutableListOf<ParseResult.Success>()
        
        for (address in addresses) {
            val resolved = resolver.resolveAddress(address)
            if (resolved != null) {
                intents.add(
                    ParseResult.Success(
                        intent = TransactionIntent.TransferSol(amount, address, resolved),
                        confidence = 0.95,
                        rawInput = "send $amount SOL to $address"
                    )
                )
            } else {
                return ChainParseResult.BatchError(
                    failedAddress = address,
                    reason = "Could not resolve address: $address",
                    successfulIntents = intents
                )
            }
        }
        
        return ChainParseResult.Batch(
            intents = intents,
            totalAmount = amount.multiply(BigDecimal(addresses.size)),
            recipientCount = addresses.size
        )
    }
    
    /**
     * Parse conditional commands (if/then/else)
     */
    private suspend fun parseConditional(
        condition: String,
        thenAction: String,
        elseAction: String?
    ): ChainParseResult {
        // Parse the condition
        val parsedCondition = parseCondition(condition)
            ?: return ChainParseResult.ConditionError("Could not parse condition: $condition")
        
        // Parse then action
        val thenResult = nlpBuilder.parse(thenAction)
        val thenIntent = when (thenResult) {
            is ParseResult.Success -> thenResult
            else -> return ChainParseResult.ConditionError("Could not parse 'then' action: $thenAction")
        }
        
        // Parse else action if present
        val elseIntent = elseAction?.let {
            when (val elseResult = nlpBuilder.parse(it)) {
                is ParseResult.Success -> elseResult
                else -> null
            }
        }
        
        return ChainParseResult.Conditional(
            condition = parsedCondition,
            thenIntent = thenIntent,
            elseIntent = elseIntent
        )
    }
    
    /**
     * Parse condition expressions
     */
    private fun parseCondition(condition: String): Condition? {
        // Price conditions: "SOL > $100", "BTC price is above 50000"
        val pricePattern = Pattern.compile(
            """(\w+)\s*(?:price\s+)?(?:is\s+)?([><]=?|==|equals?|above|below)\s*\$?(\d+(?:\.\d+)?)""",
            Pattern.CASE_INSENSITIVE
        )
        
        val priceMatch = pricePattern.matcher(condition)
        if (priceMatch.find()) {
            val token = priceMatch.group(1).uppercase()
            val operator = when (priceMatch.group(2).lowercase()) {
                ">", "above" -> ComparisonOperator.GREATER_THAN
                ">=", "at least" -> ComparisonOperator.GREATER_THAN_OR_EQUAL
                "<", "below" -> ComparisonOperator.LESS_THAN
                "<=", "at most" -> ComparisonOperator.LESS_THAN_OR_EQUAL
                "==", "equals", "equal" -> ComparisonOperator.EQUAL
                else -> ComparisonOperator.GREATER_THAN
            }
            val value = BigDecimal(priceMatch.group(3))
            
            return Condition.PriceCondition(token, operator, value)
        }
        
        // Balance conditions: "my SOL balance > 10"
        val balancePattern = Pattern.compile(
            """(?:my\s+)?(\w+)\s+balance\s*([><]=?|==)\s*(\d+(?:\.\d+)?)""",
            Pattern.CASE_INSENSITIVE
        )
        
        val balanceMatch = balancePattern.matcher(condition)
        if (balanceMatch.find()) {
            val token = balanceMatch.group(1).uppercase()
            val operator = parseOperator(balanceMatch.group(2))
            val value = BigDecimal(balanceMatch.group(3))
            
            return Condition.BalanceCondition(token, operator, value)
        }
        
        return null
    }
    
    private fun parseOperator(op: String): ComparisonOperator {
        return when (op) {
            ">" -> ComparisonOperator.GREATER_THAN
            ">=" -> ComparisonOperator.GREATER_THAN_OR_EQUAL
            "<" -> ComparisonOperator.LESS_THAN
            "<=" -> ComparisonOperator.LESS_THAN_OR_EQUAL
            "==" -> ComparisonOperator.EQUAL
            else -> ComparisonOperator.EQUAL
        }
    }
    
    /**
     * Parse scheduled commands
     */
    private suspend fun parseScheduled(action: String, schedule: String): ChainParseResult {
        val actionResult = nlpBuilder.parse(action)
        val intent = when (actionResult) {
            is ParseResult.Success -> actionResult
            else -> return ChainParseResult.ScheduleError("Could not parse action: $action")
        }
        
        val scheduledTime = parseScheduleTime(schedule)
            ?: return ChainParseResult.ScheduleError("Could not parse schedule: $schedule")
        
        return ChainParseResult.Scheduled(
            intent = intent,
            scheduledTime = scheduledTime,
            timezone = java.util.TimeZone.getDefault().id
        )
    }
    
    /**
     * Parse recurring commands
     */
    private suspend fun parseRecurring(action: String, frequency: String): ChainParseResult {
        val actionResult = nlpBuilder.parse(action)
        val intent = when (actionResult) {
            is ParseResult.Success -> actionResult
            else -> return ChainParseResult.RecurringError("Could not parse action: $action")
        }
        
        val interval = parseRecurringInterval(frequency)
            ?: return ChainParseResult.RecurringError("Could not parse frequency: $frequency")
        
        return ChainParseResult.Recurring(
            intent = intent,
            interval = interval,
            nextExecution = System.currentTimeMillis() + interval.toMillis()
        )
    }
    
    private fun parseScheduleTime(schedule: String): Long? {
        val now = System.currentTimeMillis()
        val oneDay = 24 * 60 * 60 * 1000L
        
        return when {
            schedule.contains("tomorrow") -> now + oneDay
            schedule.contains("next week") -> now + (7 * oneDay)
            schedule.contains("next month") -> now + (30 * oneDay)
            schedule.startsWith("in ") -> {
                val match = Pattern.compile("""in\s+(\d+)\s+(minutes?|hours?|days?)""")
                    .matcher(schedule)
                if (match.find()) {
                    val amount = match.group(1).toLong()
                    val unit = match.group(2)
                    now + when {
                        unit.startsWith("minute") -> amount * 60 * 1000
                        unit.startsWith("hour") -> amount * 60 * 60 * 1000
                        unit.startsWith("day") -> amount * oneDay
                        else -> 0
                    }
                } else null
            }
            else -> null
        }
    }
    
    private fun parseRecurringInterval(frequency: String): RecurringInterval? {
        return when {
            frequency == "day" -> RecurringInterval.DAILY
            frequency == "week" -> RecurringInterval.WEEKLY
            frequency == "month" -> RecurringInterval.MONTHLY
            frequency == "hour" -> RecurringInterval.HOURLY
            else -> {
                val match = Pattern.compile("""(\d+)\s+(minutes?|hours?|days?)""")
                    .matcher(frequency)
                if (match.find()) {
                    val amount = match.group(1).toLong()
                    val unit = match.group(2)
                    when {
                        unit.startsWith("minute") -> RecurringInterval.Custom(amount * 60 * 1000)
                        unit.startsWith("hour") -> RecurringInterval.Custom(amount * 60 * 60 * 1000)
                        unit.startsWith("day") -> RecurringInterval.Custom(amount * 24 * 60 * 60 * 1000)
                        else -> null
                    }
                } else null
            }
        }
    }
}

/**
 * Result of parsing a potentially complex command
 */
sealed class ChainParseResult {
    /** Single intent parsed */
    data class Single(val result: ParseResult.Success) : ChainParseResult()
    
    /** Chain of sequential intents */
    data class Chain(
        val intents: List<ParseResult.Success>,
        val executionMode: ChainExecutionMode
    ) : ChainParseResult()
    
    /** Batch operation (same action, multiple recipients) */
    data class Batch(
        val intents: List<ParseResult.Success>,
        val totalAmount: BigDecimal,
        val recipientCount: Int
    ) : ChainParseResult()
    
    /** Conditional execution */
    data class Conditional(
        val condition: Condition,
        val thenIntent: ParseResult.Success,
        val elseIntent: ParseResult.Success?
    ) : ChainParseResult()
    
    /** Scheduled for future execution */
    data class Scheduled(
        val intent: ParseResult.Success,
        val scheduledTime: Long,
        val timezone: String
    ) : ChainParseResult()
    
    /** Recurring execution */
    data class Recurring(
        val intent: ParseResult.Success,
        val interval: RecurringInterval,
        val nextExecution: Long
    ) : ChainParseResult()
    
    /** Error parsing chain */
    data class ChainError(
        val step: Int,
        val error: String,
        val parsed: List<ParseResult.Success>
    ) : ChainParseResult()
    
    /** Error parsing batch */
    data class BatchError(
        val failedAddress: String,
        val reason: String,
        val successfulIntents: List<ParseResult.Success>
    ) : ChainParseResult()
    
    /** Error parsing condition */
    data class ConditionError(val reason: String) : ChainParseResult()
    
    /** Error parsing schedule */
    data class ScheduleError(val reason: String) : ChainParseResult()
    
    /** Error parsing recurring */
    data class RecurringError(val reason: String) : ChainParseResult()
    
    /** Needs more info */
    data class NeedsInfo(val result: ParseResult.NeedsInfo) : ChainParseResult()
    
    /** Ambiguous */
    data class Ambiguous(val result: ParseResult.Ambiguous) : ChainParseResult()
    
    /** Unknown */
    data class Unknown(val result: ParseResult.Unknown) : ChainParseResult()
}

/**
 * How to execute a chain
 */
enum class ChainExecutionMode {
    /** Execute one after another, fail if any fails */
    SEQUENTIAL,
    /** Execute all in parallel (atomic via bundle) */
    PARALLEL,
    /** Execute all, continue even if some fail */
    BEST_EFFORT
}

/**
 * Condition for conditional execution
 */
sealed class Condition {
    abstract suspend fun evaluate(): Boolean
    
    data class PriceCondition(
        val token: String,
        val operator: ComparisonOperator,
        val targetPrice: BigDecimal
    ) : Condition() {
        override suspend fun evaluate(): Boolean {
            // Would fetch current price and compare
            return true // Placeholder
        }
    }
    
    data class BalanceCondition(
        val token: String,
        val operator: ComparisonOperator,
        val targetBalance: BigDecimal
    ) : Condition() {
        override suspend fun evaluate(): Boolean {
            // Would fetch current balance and compare
            return true // Placeholder
        }
    }
    
    data class TimeCondition(
        val operator: ComparisonOperator,
        val targetTime: Long
    ) : Condition() {
        override suspend fun evaluate(): Boolean {
            return when (operator) {
                ComparisonOperator.GREATER_THAN -> System.currentTimeMillis() > targetTime
                ComparisonOperator.LESS_THAN -> System.currentTimeMillis() < targetTime
                else -> false
            }
        }
    }
}

enum class ComparisonOperator {
    GREATER_THAN,
    GREATER_THAN_OR_EQUAL,
    LESS_THAN,
    LESS_THAN_OR_EQUAL,
    EQUAL,
    NOT_EQUAL
}

/**
 * Recurring interval
 */
sealed class RecurringInterval {
    abstract fun toMillis(): Long
    
    data object HOURLY : RecurringInterval() {
        override fun toMillis() = 60 * 60 * 1000L
    }
    
    data object DAILY : RecurringInterval() {
        override fun toMillis() = 24 * 60 * 60 * 1000L
    }
    
    data object WEEKLY : RecurringInterval() {
        override fun toMillis() = 7 * 24 * 60 * 60 * 1000L
    }
    
    data object MONTHLY : RecurringInterval() {
        override fun toMillis() = 30 * 24 * 60 * 60 * 1000L
    }
    
    data class Custom(val millis: Long) : RecurringInterval() {
        override fun toMillis() = millis
    }
}
