package xyz.selenus.luna.nlp.context

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import xyz.selenus.luna.nlp.IntentType
import xyz.selenus.luna.nlp.ParseResult
import xyz.selenus.luna.nlp.TransactionIntent
import java.util.concurrent.ConcurrentHashMap

/**
 * Conversational Context Memory - Multi-turn NLP interaction
 * 
 * Maintains conversation state across multiple interactions, enabling:
 * - Pronoun resolution ("send 1 SOL to alice.sol" → "send them 2 more")
 * - Intent continuation ("swap 100 USDC for SOL" → "make it 200")
 * - History recall ("repeat last transaction")
 * - Preference learning (frequent recipients, tokens, amounts)
 * - Undo/redo support
 * 
 * Follows Android 2026 architecture patterns with StateFlow
 */
class ConversationMemory private constructor(
    private val config: MemoryConfig,
    private val storage: MemoryStorage?
) {
    
    private val _context = MutableStateFlow(ConversationContext())
    val context: StateFlow<ConversationContext> = _context.asStateFlow()
    
    private val _history = MutableStateFlow<List<ConversationTurn>>(emptyList())
    val history: StateFlow<List<ConversationTurn>> = _history.asStateFlow()
    
    private val preferences = UserPreferences()
    
    companion object {
        fun create(config: MemoryConfig = MemoryConfig()): ConversationMemory {
            return ConversationMemory(config, null)
        }
        
        fun create(config: MemoryConfig, storage: MemoryStorage): ConversationMemory {
            return ConversationMemory(config, storage).also {
                it.loadFromStorage()
            }
        }
    }
    
    /**
     * Record a successful parse and update context
     */
    fun recordInteraction(input: String, result: ParseResult.Success) {
        val turn = ConversationTurn(
            input = input,
            result = result,
            timestamp = System.currentTimeMillis()
        )
        
        // Add to history
        _history.value = (_history.value + turn).takeLast(config.maxHistorySize)
        
        // Update context based on intent
        updateContext(result.intent)
        
        // Update preferences
        updatePreferences(result.intent)
        
        // Persist if storage available
        storage?.save(this)
    }
    
    /**
     * Expand input with context (pronoun resolution, etc.)
     */
    fun expandInput(input: String): String {
        var expanded = input
        val ctx = _context.value
        
        // Pronoun resolution for addresses
        ctx.lastMentionedAddress?.let { address ->
            expanded = expanded.replace(Regex("\\b(them|that address|that wallet|there)\\b", RegexOption.IGNORE_CASE), address)
        }
        
        // Pronoun resolution for tokens
        ctx.lastMentionedToken?.let { token ->
            expanded = expanded.replace(Regex("\\b(it|that token|that coin)\\b", RegexOption.IGNORE_CASE), token)
        }
        
        // Amount modification ("make it X", "change to X")
        val amountMod = Regex("""(?:make it|change (?:it )?to)\s+(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
        amountMod.find(expanded)?.let { match ->
            ctx.lastIntent?.let { lastIntent ->
                val newAmount = match.groupValues[1]
                expanded = reconstructWithNewAmount(lastIntent, newAmount)
            }
        }
        
        // "again" or "repeat" → use last command
        if (expanded.matches(Regex("(?:do it )?again|repeat(?: (?:last|that))?|same", RegexOption.IGNORE_CASE))) {
            _history.value.lastOrNull()?.let { turn ->
                expanded = turn.input
            }
        }
        
        // "more" / "another" → increment last transaction
        if (expanded.matches(Regex("(?:send )?(?:one )?more|another", RegexOption.IGNORE_CASE))) {
            ctx.lastIntent?.let { intent ->
                when (intent) {
                    is TransactionIntent.TransferSol -> {
                        expanded = "send ${intent.amount} SOL to ${intent.recipientResolved ?: intent.recipient}"
                    }
                    is TransactionIntent.TransferToken -> {
                        expanded = "send ${intent.amount} ${intent.token} to ${intent.recipientResolved ?: intent.recipient}"
                    }
                    else -> {}
                }
            }
        }
        
        // "undo" → generate reverse transaction
        if (expanded.matches(Regex("undo(?: last)?|reverse", RegexOption.IGNORE_CASE))) {
            _history.value.lastOrNull()?.result?.intent?.let { intent ->
                expanded = generateReverseIntent(intent) ?: expanded
            }
        }
        
        return expanded
    }
    
    /**
     * Get smart suggestions based on context
     */
    fun getSuggestions(): List<ContextualSuggestion> {
        val suggestions = mutableListOf<ContextualSuggestion>()
        val ctx = _context.value
        
        // Suggest based on last intent
        ctx.lastIntent?.let { intent ->
            suggestions.add(
                ContextualSuggestion(
                    text = "Do it again",
                    description = "Repeat: ${intent.summary()}",
                    type = SuggestionType.REPEAT
                )
            )
            
            // Suggest modification
            when (intent) {
                is TransactionIntent.TransferSol -> {
                    suggestions.add(
                        ContextualSuggestion(
                            text = "Send double",
                            description = "Send ${intent.amount * java.math.BigDecimal(2)} SOL to ${intent.recipientResolved ?: intent.recipient}",
                            type = SuggestionType.MODIFY
                        )
                    )
                }
                is TransactionIntent.Swap -> {
                    suggestions.add(
                        ContextualSuggestion(
                            text = "Swap back",
                            description = "Swap ${intent.outputToken} back to ${intent.inputToken}",
                            type = SuggestionType.REVERSE
                        )
                    )
                }
                else -> {}
            }
        }
        
        // Suggest frequent recipients
        preferences.frequentRecipients.toList().take(3).forEach { (address, count) ->
            suggestions.add(
                ContextualSuggestion(
                    text = "Send to $address",
                    description = "Frequently used recipient ($count times)",
                    type = SuggestionType.FREQUENT
                )
            )
        }
        
        // Suggest frequently used amounts
        preferences.frequentAmounts.toList().take(2).forEach { (amount, count) ->
            suggestions.add(
                ContextualSuggestion(
                    text = "Send $amount SOL",
                    description = "Frequently used amount ($count times)",
                    type = SuggestionType.FREQUENT
                )
            )
        }
        
        return suggestions.take(5)
    }
    
    /**
     * Get autocomplete suggestions for partial input
     */
    fun getAutocompletions(partial: String): List<String> {
        val completions = mutableListOf<String>()
        val lower = partial.lowercase()
        
        // Complete addresses
        if (lower.contains("to ") && !lower.contains(".sol") && !lower.contains(".skr")) {
            preferences.frequentRecipients.keys.take(3).forEach { address ->
                completions.add(partial.replace(Regex("to\\s*$", RegexOption.IGNORE_CASE), "to $address"))
            }
        }
        
        // Complete token names
        if (lower.matches(Regex(".*\\d+\\s+\\w{1,3}$"))) {
            listOf("SOL", "USDC", "USDT", "BONK", "WIF", "JTO").forEach { token ->
                if (token.lowercase().startsWith(lower.takeLastWhile { it.isLetter() })) {
                    completions.add(partial.dropLastWhile { it.isLetter() } + token)
                }
            }
        }
        
        // Complete command prefixes
        val commands = listOf(
            "send", "transfer", "swap", "exchange", "stake", "unstake",
            "check balance", "get assets", "resolve"
        )
        commands.filter { it.startsWith(lower) }.forEach { cmd ->
            completions.add(cmd)
        }
        
        return completions.distinct().take(5)
    }
    
    /**
     * Clear conversation context
     */
    fun clear() {
        _context.value = ConversationContext()
        _history.value = emptyList()
    }
    
    /**
     * Clear history but keep preferences
     */
    fun clearHistory() {
        _history.value = emptyList()
        _context.value = _context.value.copy(lastIntent = null)
    }
    
    private fun updateContext(intent: TransactionIntent) {
        val current = _context.value
        
        val newContext = when (intent) {
            is TransactionIntent.TransferSol -> current.copy(
                lastMentionedAddress = intent.recipientResolved ?: intent.recipient,
                lastMentionedAmount = intent.amount.toString(),
                lastIntent = intent,
                lastIntentType = IntentType.TRANSFER_SOL
            )
            is TransactionIntent.TransferToken -> current.copy(
                lastMentionedAddress = intent.recipientResolved ?: intent.recipient,
                lastMentionedToken = intent.token,
                lastMentionedAmount = intent.amount.toString(),
                lastIntent = intent,
                lastIntentType = IntentType.TRANSFER_TOKEN
            )
            is TransactionIntent.Swap -> current.copy(
                lastMentionedToken = intent.outputToken,
                lastMentionedAmount = intent.inputAmount.toString(),
                lastIntent = intent,
                lastIntentType = IntentType.SWAP
            )
            is TransactionIntent.GetBalance -> current.copy(
                lastMentionedAddress = intent.addressResolved ?: intent.address,
                lastIntent = intent,
                lastIntentType = IntentType.GET_BALANCE
            )
            else -> current.copy(lastIntent = intent)
        }
        
        _context.value = newContext
    }
    
    private fun updatePreferences(intent: TransactionIntent) {
        when (intent) {
            is TransactionIntent.TransferSol -> {
                preferences.incrementRecipient(intent.recipientResolved ?: intent.recipient)
                preferences.incrementAmount(intent.amount.toString())
            }
            is TransactionIntent.TransferToken -> {
                preferences.incrementRecipient(intent.recipientResolved ?: intent.recipient)
                preferences.incrementToken(intent.token)
            }
            is TransactionIntent.Swap -> {
                preferences.incrementToken(intent.inputToken)
                preferences.incrementToken(intent.outputToken)
            }
            else -> {}
        }
    }
    
    private fun reconstructWithNewAmount(intent: TransactionIntent, newAmount: String): String {
        return when (intent) {
            is TransactionIntent.TransferSol -> "send $newAmount SOL to ${intent.recipientResolved ?: intent.recipient}"
            is TransactionIntent.TransferToken -> "send $newAmount ${intent.token} to ${intent.recipientResolved ?: intent.recipient}"
            is TransactionIntent.Swap -> "swap $newAmount ${intent.inputToken} for ${intent.outputToken}"
            else -> "send $newAmount SOL"
        }
    }
    
    private fun generateReverseIntent(intent: TransactionIntent): String? {
        return when (intent) {
            is TransactionIntent.Swap -> {
                "swap ${intent.outputToken} back to ${intent.inputToken}"
            }
            else -> null // Can't reverse transfers
        }
    }
    
    private fun loadFromStorage() {
        storage?.load()?.let { saved ->
            _history.value = saved.history
            // Preferences would also be loaded
        }
    }
}

/**
 * Current conversation context
 */
@Serializable
data class ConversationContext(
    val lastMentionedAddress: String? = null,
    val lastMentionedToken: String? = null,
    val lastMentionedAmount: String? = null,
    val lastMentionedNft: String? = null,
    @kotlinx.serialization.Transient
    val lastIntent: TransactionIntent? = null,
    val lastIntentType: IntentType? = null,
    val sessionStartTime: Long = System.currentTimeMillis()
)

/**
 * Single conversation turn
 */
@Serializable
data class ConversationTurn(
    val input: String,
    @kotlinx.serialization.Transient
    val result: ParseResult.Success? = null,
    val timestamp: Long
)

/**
 * Memory configuration
 */
data class MemoryConfig(
    val maxHistorySize: Int = 50,
    val persistPreferences: Boolean = true,
    val enableAutoComplete: Boolean = true,
    val enableSmartSuggestions: Boolean = true
)

/**
 * User preferences learned over time
 */
class UserPreferences {
    val frequentRecipients = ConcurrentHashMap<String, Int>()
    val frequentTokens = ConcurrentHashMap<String, Int>()
    val frequentAmounts = ConcurrentHashMap<String, Int>()
    
    fun incrementRecipient(address: String) {
        frequentRecipients.compute(address) { _, v -> (v ?: 0) + 1 }
    }
    
    fun incrementToken(token: String) {
        frequentTokens.compute(token) { _, v -> (v ?: 0) + 1 }
    }
    
    fun incrementAmount(amount: String) {
        frequentAmounts.compute(amount) { _, v -> (v ?: 0) + 1 }
    }
}

/**
 * Contextual suggestion
 */
data class ContextualSuggestion(
    val text: String,
    val description: String,
    val type: SuggestionType,
    val confidence: Double = 0.8
)

enum class SuggestionType {
    REPEAT,      // Repeat last action
    MODIFY,      // Modify last action
    REVERSE,     // Reverse/undo last action
    FREQUENT,    // Frequently used
    TRENDING,    // Based on network activity
    CONTEXTUAL   // Based on current context
}

/**
 * Storage interface for persistence
 */
interface MemoryStorage {
    fun save(memory: ConversationMemory)
    fun load(): SavedMemory?
    fun clear()
}

/**
 * Saved memory state
 */
@Serializable
data class SavedMemory(
    val history: List<ConversationTurn>,
    val frequentRecipients: Map<String, Int>,
    val frequentTokens: Map<String, Int>,
    val frequentAmounts: Map<String, Int>
)

/**
 * In-memory storage implementation (for testing)
 */
class InMemoryStorage : MemoryStorage {
    private var saved: SavedMemory? = null
    
    override fun save(memory: ConversationMemory) {
        saved = SavedMemory(
            history = memory.history.value,
            frequentRecipients = emptyMap(), // Would extract from memory
            frequentTokens = emptyMap(),
            frequentAmounts = emptyMap()
        )
    }
    
    override fun load(): SavedMemory? = saved
    
    override fun clear() {
        saved = null
    }
}
