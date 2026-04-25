package xyz.selenus.luna.nlp

import java.math.BigDecimal
import java.util.regex.Pattern

/**
 * Natural Language Transaction Builder for Luna SDK (Helius)
 * 
 * Build Solana transactions by typing in plain English:
 * - "send 1 SOL to alice.sol"
 * - "swap 100 USDC for SOL"
 * - "get my NFTs"
 * - "check balance of moonmanquark.sol"
 */
class NaturalLanguageBuilder private constructor(
    private val resolver: EntityResolver,
    private val config: NlpConfig
) {
    
    companion object {
        /**
         * Create a new NLP builder with default configuration
         */
        fun create(resolver: EntityResolver): NaturalLanguageBuilder {
            return NaturalLanguageBuilder(resolver, NlpConfig())
        }
        
        /**
         * Create a new NLP builder with custom configuration
         */
        fun create(resolver: EntityResolver, config: NlpConfig.() -> Unit): NaturalLanguageBuilder {
            return NaturalLanguageBuilder(resolver, NlpConfig().apply(config))
        }
        
        // Regex patterns - NOTE: These are used INSIDE other capturing groups, so don't add capturing parens here
        private const val AMOUNT_PATTERN = """\d+(?:\.\d+)?(?:[kmb])?|\d{1,3}(?:,\d{3})*(?:\.\d+)?"""
        private const val TOKEN_PATTERN = """[a-zA-Z]{2,15}"""
        private const val DOMAIN_PATTERN = """[a-zA-Z0-9\-]+\.(?:sol|skr)"""
        private const val BASE58_PATTERN = """[1-9A-HJ-NP-Za-km-z]{32,44}"""
        private const val ADDRESS_PATTERN = """[a-zA-Z0-9\-]+\.(?:sol|skr)|[1-9A-HJ-NP-Za-km-z]{32,44}|@[a-zA-Z0-9_]+"""
    }
    
    // Pattern matchers for different intents
    private val patterns = buildPatterns()
    
    /**
     * Parse natural language input into a transaction intent
     */
    suspend fun parse(input: String): ParseResult {
        val normalizedInput = normalizeInput(input)
        
        // Try each pattern category
        for ((intentType, patternList) in patterns) {
            for (pattern in patternList) {
                val match = pattern.pattern.matcher(normalizedInput)
                if (match.find()) {
                    return try {
                        pattern.extract(match, normalizedInput, resolver)
                    } catch (e: Exception) {
                        continue
                    }
                }
            }
        }
        
        // No pattern matched - return suggestions
        return ParseResult.Unknown(
            input = input,
            suggestions = generateSuggestions(normalizedInput)
        )
    }
    
    /**
     * Parse with context from previous interactions
     */
    suspend fun parseWithContext(input: String, context: NlpContext): ParseResult {
        // If input references "it" or "that", try to use context
        val expandedInput = expandContextReferences(input, context)
        return parse(expandedInput)
    }
    
    private fun normalizeInput(input: String): String {
        return input
            .trim()
            .lowercase()
            .replace(Regex("\\s+"), " ")
            .replace("'", "'")
            .replace(""", "\"")
            .replace(""", "\"")
    }
    
    private fun expandContextReferences(input: String, context: NlpContext): String {
        var expanded = input
        
        // Replace "it" with last mentioned entity
        if (context.lastMentionedAddress != null) {
            expanded = expanded.replace(Regex("\\b(it|that address|that wallet)\\b"), context.lastMentionedAddress)
        }
        
        // Replace "them" with last mentioned token
        if (context.lastMentionedToken != null) {
            expanded = expanded.replace(Regex("\\b(it|that token|those tokens)\\b"), context.lastMentionedToken)
        }
        
        return expanded
    }
    
    private fun buildPatterns(): Map<IntentType, List<NlpPattern>> {
        return mapOf(
            // === TRANSFER PATTERNS ===
            IntentType.TRANSFER_SOL to listOf(
                NlpPattern(
                    Pattern.compile("""(?:send|transfer|pay)\s+($AMOUNT_PATTERN)\s*(?:sol|◎)\s+(?:to\s+)?($ADDRESS_PATTERN)""", Pattern.CASE_INSENSITIVE),
                    ::extractTransferSol
                ),
                NlpPattern(
                    Pattern.compile("""(?:send|transfer|pay)\s+($ADDRESS_PATTERN)\s+($AMOUNT_PATTERN)\s*(?:sol|◎)""", Pattern.CASE_INSENSITIVE),
                    ::extractTransferSolReverse
                )
            ),
            
            IntentType.TRANSFER_TOKEN to listOf(
                NlpPattern(
                    Pattern.compile("""(?:send|transfer|pay)\s+($AMOUNT_PATTERN)\s+($TOKEN_PATTERN)\s+(?:to\s+)?($ADDRESS_PATTERN)""", Pattern.CASE_INSENSITIVE),
                    ::extractTransferToken
                )
            ),
            
            // === SWAP PATTERNS ===
            IntentType.SWAP to listOf(
                NlpPattern(
                    Pattern.compile("""(?:swap|exchange|convert|trade)\s+($AMOUNT_PATTERN)\s+($TOKEN_PATTERN)\s+(?:for|to|into)\s+($TOKEN_PATTERN)""", Pattern.CASE_INSENSITIVE),
                    ::extractSwap
                ),
                NlpPattern(
                    Pattern.compile("""(?:buy|get)\s+($AMOUNT_PATTERN)\s+($TOKEN_PATTERN)\s+(?:with|using)\s+($TOKEN_PATTERN)""", Pattern.CASE_INSENSITIVE),
                    ::extractSwapBuy
                ),
                NlpPattern(
                    Pattern.compile("""(?:sell)\s+($AMOUNT_PATTERN)\s+($TOKEN_PATTERN)\s+(?:for)\s+($TOKEN_PATTERN)""", Pattern.CASE_INSENSITIVE),
                    ::extractSwap
                )
            ),
            
            // === STAKING PATTERNS ===
            IntentType.STAKE to listOf(
                NlpPattern(
                    Pattern.compile("""(?:stake)\s+($AMOUNT_PATTERN)\s*(?:sol)?(?:\s+(?:with|to)\s+(.+))?""", Pattern.CASE_INSENSITIVE),
                    ::extractStake
                )
            ),
            
            IntentType.UNSTAKE to listOf(
                NlpPattern(
                    Pattern.compile("""(?:unstake|withdraw)\s+($AMOUNT_PATTERN)\s*(?:sol)?""", Pattern.CASE_INSENSITIVE),
                    ::extractUnstake
                )
            ),
            
            IntentType.CLAIM_REWARDS to listOf(
                NlpPattern(
                    Pattern.compile("""(?:claim|collect|harvest)\s+(?:my\s+)?(?:staking\s+)?rewards?""", Pattern.CASE_INSENSITIVE),
                    ::extractClaimRewards
                )
            ),
            
            // === BALANCE PATTERNS ===
            IntentType.GET_BALANCE to listOf(
                NlpPattern(
                    Pattern.compile("""(?:check|get|show|what(?:'s| is)?)\s+(?:my\s+)?(?:sol\s+)?balance(?:\s+(?:of|for)\s+($ADDRESS_PATTERN))?""", Pattern.CASE_INSENSITIVE),
                    ::extractGetBalance
                ),
                NlpPattern(
                    Pattern.compile("""(?:how much)\s+(?:sol|◎)\s+(?:do i have|in)\s*($ADDRESS_PATTERN)?""", Pattern.CASE_INSENSITIVE),
                    ::extractGetBalance
                )
            ),
            
            IntentType.GET_TOKEN_BALANCE to listOf(
                NlpPattern(
                    Pattern.compile("""(?:check|get|show|what(?:'s| is)?)\s+(?:my\s+)?($TOKEN_PATTERN)\s+balance(?:\s+(?:of|for)\s+($ADDRESS_PATTERN))?""", Pattern.CASE_INSENSITIVE),
                    ::extractGetTokenBalance
                )
            ),
            
            // === ASSET PATTERNS ===
            IntentType.GET_ASSETS to listOf(
                NlpPattern(
                    Pattern.compile("""(?:get|show|list|what(?:'s| are)?)\s+(?:my\s+)?(?:nfts?|assets?|tokens?)(?:\s+(?:of|for|in)\s+($ADDRESS_PATTERN))?""", Pattern.CASE_INSENSITIVE),
                    ::extractGetAssets
                ),
                NlpPattern(
                    Pattern.compile("""(?:what do i (?:have|own))(?:\s+in\s+($ADDRESS_PATTERN))?""", Pattern.CASE_INSENSITIVE),
                    ::extractGetAssets
                )
            ),
            
            // === TRANSACTION HISTORY PATTERNS ===
            IntentType.GET_TRANSACTION_HISTORY to listOf(
                NlpPattern(
                    Pattern.compile("""(?:get|show|list)\s+(?:my\s+)?(?:transaction|tx)\s*(?:history|log)?(?:\s+(?:of|for)\s+($ADDRESS_PATTERN))?(?:\s+(?:limit|last)\s+(\d+))?""", Pattern.CASE_INSENSITIVE),
                    ::extractGetTransactionHistory
                ),
                NlpPattern(
                    Pattern.compile("""(?:what(?:'s| are)?)\s+(?:my\s+)?(?:recent\s+)?transactions?(?:\s+(?:of|for)\s+($ADDRESS_PATTERN))?""", Pattern.CASE_INSENSITIVE),
                    ::extractGetTransactionHistory
                )
            ),
            
            // === DOMAIN PATTERNS ===
            IntentType.RESOLVE_DOMAIN to listOf(
                NlpPattern(
                    Pattern.compile("""(?:resolve|lookup|find)\s+(?:domain\s+)?($DOMAIN_PATTERN)""", Pattern.CASE_INSENSITIVE),
                    ::extractResolveDomain
                ),
                NlpPattern(
                    Pattern.compile("""(?:what(?:'s| is)?)\s+(?:the\s+)?(?:address\s+(?:of|for)\s+)?($DOMAIN_PATTERN)""", Pattern.CASE_INSENSITIVE),
                    ::extractResolveDomain
                )
            ),
            
            IntentType.REVERSE_LOOKUP to listOf(
                NlpPattern(
                    Pattern.compile("""(?:what(?:'s| is)?)\s+(?:the\s+)?domain\s+(?:of|for)\s+($BASE58_PATTERN)""", Pattern.CASE_INSENSITIVE),
                    ::extractReverseLookup
                ),
                NlpPattern(
                    Pattern.compile("""(?:reverse\s+)?lookup\s+($BASE58_PATTERN)""", Pattern.CASE_INSENSITIVE),
                    ::extractReverseLookup
                )
            ),
            
            // === PRIVACY PATTERNS ===
            IntentType.ANALYZE_PRIVACY to listOf(
                NlpPattern(
                    Pattern.compile("""(?:analyze|check|score)\s+(?:my\s+)?privacy(?:\s+(?:of|for)\s+($ADDRESS_PATTERN))?""", Pattern.CASE_INSENSITIVE),
                    ::extractAnalyzePrivacy
                ),
                NlpPattern(
                    Pattern.compile("""(?:how private)\s+(?:is|am)\s+(?:i|my wallet|($ADDRESS_PATTERN))""", Pattern.CASE_INSENSITIVE),
                    ::extractAnalyzePrivacy
                )
            ),
            
            // === NFT PATTERNS ===
            IntentType.NFT_TRANSFER to listOf(
                NlpPattern(
                    Pattern.compile("""(?:send|transfer)\s+(?:my\s+)?nft\s+($BASE58_PATTERN)\s+(?:to\s+)?($ADDRESS_PATTERN)""", Pattern.CASE_INSENSITIVE),
                    ::extractNftTransfer
                )
            ),
            
            IntentType.NFT_LIST to listOf(
                NlpPattern(
                    Pattern.compile("""(?:list|sell)\s+(?:my\s+)?nft\s+($BASE58_PATTERN)\s+(?:for|at)\s+($AMOUNT_PATTERN)\s*(?:sol)?""", Pattern.CASE_INSENSITIVE),
                    ::extractNftList
                )
            ),
            
            // === WEBHOOK PATTERNS ===
            IntentType.LIST_WEBHOOKS to listOf(
                NlpPattern(
                    Pattern.compile("""(?:list|show|get)\s+(?:my\s+)?webhooks?""", Pattern.CASE_INSENSITIVE),
                    ::extractListWebhooks
                )
            )
        )
    }
    
    // === EXTRACTION FUNCTIONS ===
    
    private suspend fun extractTransferSol(
        match: java.util.regex.Matcher, 
        input: String, 
        resolver: EntityResolver
    ): ParseResult {
        val amount = parseAmount(match.group(1))
        val recipient = match.group(2)
        val resolved = resolver.resolveAddress(recipient)
        
        return if (resolved != null) {
            ParseResult.Success(
                intent = TransactionIntent.TransferSol(amount, recipient, resolved),
                confidence = 0.95,
                rawInput = input
            )
        } else {
            ParseResult.NeedsInfo(
                intentType = IntentType.TRANSFER_SOL,
                missing = listOf("recipient address"),
                partial = mapOf("amount" to amount.toString()),
                suggestion = "Could not resolve '$recipient'. Is it a valid .sol/.skr domain or wallet address?"
            )
        }
    }
    
    private suspend fun extractTransferSolReverse(
        match: java.util.regex.Matcher,
        input: String,
        resolver: EntityResolver
    ): ParseResult {
        val recipient = match.group(1)
        val amount = parseAmount(match.group(2))
        val resolved = resolver.resolveAddress(recipient)
        
        return if (resolved != null) {
            ParseResult.Success(
                intent = TransactionIntent.TransferSol(amount, recipient, resolved),
                confidence = 0.90,
                rawInput = input
            )
        } else {
            ParseResult.NeedsInfo(
                intentType = IntentType.TRANSFER_SOL,
                missing = listOf("recipient address"),
                partial = mapOf("amount" to amount.toString()),
                suggestion = "Could not resolve '$recipient'. Is it a valid .sol/.skr domain or wallet address?"
            )
        }
    }
    
    private suspend fun extractTransferToken(
        match: java.util.regex.Matcher,
        input: String,
        resolver: EntityResolver
    ): ParseResult {
        val amount = parseAmount(match.group(1))
        val token = match.group(2).uppercase()
        val recipient = match.group(3)
        
        val tokenInfo = resolver.resolveToken(token)
        val resolved = resolver.resolveAddress(recipient)
        
        return when {
            tokenInfo == null -> ParseResult.NeedsInfo(
                intentType = IntentType.TRANSFER_TOKEN,
                missing = listOf("valid token"),
                partial = mapOf("amount" to amount.toString(), "recipient" to recipient),
                suggestion = "Token '$token' not recognized. Try using the mint address or a common symbol like USDC, BONK, etc."
            )
            resolved == null -> ParseResult.NeedsInfo(
                intentType = IntentType.TRANSFER_TOKEN,
                missing = listOf("recipient address"),
                partial = mapOf("amount" to amount.toString(), "token" to token),
                suggestion = "Could not resolve '$recipient'. Is it a valid .sol/.skr domain or wallet address?"
            )
            else -> ParseResult.Success(
                intent = TransactionIntent.TransferToken(amount, token, tokenInfo.mint, recipient, resolved),
                confidence = 0.95,
                rawInput = input
            )
        }
    }
    
    private suspend fun extractSwap(
        match: java.util.regex.Matcher,
        input: String,
        resolver: EntityResolver
    ): ParseResult {
        val amount = parseAmount(match.group(1))
        val inputToken = match.group(2).uppercase()
        val outputToken = match.group(3).uppercase()
        
        val inputInfo = resolver.resolveToken(inputToken)
        val outputInfo = resolver.resolveToken(outputToken)
        
        return when {
            inputInfo == null -> ParseResult.NeedsInfo(
                intentType = IntentType.SWAP,
                missing = listOf("input token"),
                partial = mapOf("amount" to amount.toString(), "outputToken" to outputToken),
                suggestion = "Token '$inputToken' not recognized."
            )
            outputInfo == null -> ParseResult.NeedsInfo(
                intentType = IntentType.SWAP,
                missing = listOf("output token"),
                partial = mapOf("amount" to amount.toString(), "inputToken" to inputToken),
                suggestion = "Token '$outputToken' not recognized."
            )
            else -> ParseResult.Success(
                intent = TransactionIntent.Swap(amount, inputToken, inputInfo.mint, outputToken, outputInfo.mint, config.defaultSlippageBps),
                confidence = 0.95,
                rawInput = input
            )
        }
    }
    
    private suspend fun extractSwapBuy(
        match: java.util.regex.Matcher,
        input: String,
        resolver: EntityResolver
    ): ParseResult {
        val amount = parseAmount(match.group(1))
        val outputToken = match.group(2).uppercase()
        val inputToken = match.group(3).uppercase()
        
        val inputInfo = resolver.resolveToken(inputToken)
        val outputInfo = resolver.resolveToken(outputToken)
        
        return when {
            inputInfo == null || outputInfo == null -> ParseResult.NeedsInfo(
                intentType = IntentType.SWAP_EXACT_OUT,
                missing = listOf("token"),
                partial = mapOf("amount" to amount.toString()),
                suggestion = "Token not recognized."
            )
            else -> ParseResult.Success(
                intent = TransactionIntent.SwapExactOut(amount, outputToken, outputInfo.mint, inputToken, inputInfo.mint, config.defaultSlippageBps),
                confidence = 0.90,
                rawInput = input
            )
        }
    }
    
    private suspend fun extractStake(
        match: java.util.regex.Matcher,
        input: String,
        resolver: EntityResolver
    ): ParseResult {
        val amount = parseAmount(match.group(1))
        val validator = match.group(2)?.trim()
        
        return ParseResult.Success(
            intent = TransactionIntent.Stake(amount, validator),
            confidence = 0.95,
            rawInput = input
        )
    }
    
    private suspend fun extractUnstake(
        match: java.util.regex.Matcher,
        input: String,
        resolver: EntityResolver
    ): ParseResult {
        val amount = parseAmount(match.group(1))
        
        return ParseResult.Success(
            intent = TransactionIntent.Unstake(amount),
            confidence = 0.95,
            rawInput = input
        )
    }
    
    private suspend fun extractClaimRewards(
        match: java.util.regex.Matcher,
        input: String,
        resolver: EntityResolver
    ): ParseResult {
        return ParseResult.Success(
            intent = TransactionIntent.ClaimRewards,
            confidence = 0.95,
            rawInput = input
        )
    }
    
    private suspend fun extractGetBalance(
        match: java.util.regex.Matcher,
        input: String,
        resolver: EntityResolver
    ): ParseResult {
        val address = match.group(1) ?: config.defaultWallet ?: return ParseResult.NeedsInfo(
            intentType = IntentType.GET_BALANCE,
            missing = listOf("wallet address"),
            partial = emptyMap(),
            suggestion = "Which wallet do you want to check? Provide an address or .sol/.skr domain."
        )
        
        val resolved = resolver.resolveAddress(address)
        
        return if (resolved != null) {
            ParseResult.Success(
                intent = TransactionIntent.GetBalance(address, resolved),
                confidence = 0.95,
                rawInput = input
            )
        } else {
            ParseResult.NeedsInfo(
                intentType = IntentType.GET_BALANCE,
                missing = listOf("valid address"),
                partial = mapOf("address" to address),
                suggestion = "Could not resolve '$address'."
            )
        }
    }
    
    private suspend fun extractGetTokenBalance(
        match: java.util.regex.Matcher,
        input: String,
        resolver: EntityResolver
    ): ParseResult {
        val token = match.group(1).uppercase()
        val address = match.group(2) ?: config.defaultWallet ?: return ParseResult.NeedsInfo(
            intentType = IntentType.GET_TOKEN_BALANCE,
            missing = listOf("wallet address"),
            partial = mapOf("token" to token),
            suggestion = "Which wallet do you want to check?"
        )
        
        val resolved = resolver.resolveAddress(address)
        
        return if (resolved != null) {
            ParseResult.Success(
                intent = TransactionIntent.GetTokenBalance(address, token, resolved),
                confidence = 0.95,
                rawInput = input
            )
        } else {
            ParseResult.NeedsInfo(
                intentType = IntentType.GET_TOKEN_BALANCE,
                missing = listOf("valid address"),
                partial = mapOf("token" to token, "address" to address),
                suggestion = "Could not resolve '$address'."
            )
        }
    }
    
    private suspend fun extractGetAssets(
        match: java.util.regex.Matcher,
        input: String,
        resolver: EntityResolver
    ): ParseResult {
        val address = match.group(1) ?: config.defaultWallet ?: return ParseResult.NeedsInfo(
            intentType = IntentType.GET_ASSETS,
            missing = listOf("wallet address"),
            partial = emptyMap(),
            suggestion = "Which wallet do you want to check?"
        )
        
        val resolved = resolver.resolveAddress(address)
        
        return if (resolved != null) {
            ParseResult.Success(
                intent = TransactionIntent.GetAssets(address, resolved),
                confidence = 0.95,
                rawInput = input
            )
        } else {
            ParseResult.NeedsInfo(
                intentType = IntentType.GET_ASSETS,
                missing = listOf("valid address"),
                partial = mapOf("address" to address),
                suggestion = "Could not resolve '$address'."
            )
        }
    }
    
    private suspend fun extractGetTransactionHistory(
        match: java.util.regex.Matcher,
        input: String,
        resolver: EntityResolver
    ): ParseResult {
        val address = match.group(1) ?: config.defaultWallet ?: return ParseResult.NeedsInfo(
            intentType = IntentType.GET_TRANSACTION_HISTORY,
            missing = listOf("wallet address"),
            partial = emptyMap(),
            suggestion = "Which wallet's transaction history do you want?"
        )
        
        val limit = match.group(2)?.toIntOrNull() ?: 10
        val resolved = resolver.resolveAddress(address)
        
        return if (resolved != null) {
            ParseResult.Success(
                intent = TransactionIntent.GetTransactionHistory(address, resolved, limit),
                confidence = 0.95,
                rawInput = input
            )
        } else {
            ParseResult.NeedsInfo(
                intentType = IntentType.GET_TRANSACTION_HISTORY,
                missing = listOf("valid address"),
                partial = mapOf("address" to address),
                suggestion = "Could not resolve '$address'."
            )
        }
    }
    
    private suspend fun extractResolveDomain(
        match: java.util.regex.Matcher,
        input: String,
        resolver: EntityResolver
    ): ParseResult {
        val domain = match.group(1)
        
        return ParseResult.Success(
            intent = TransactionIntent.ResolveDomain(domain),
            confidence = 0.95,
            rawInput = input
        )
    }
    
    private suspend fun extractReverseLookup(
        match: java.util.regex.Matcher,
        input: String,
        resolver: EntityResolver
    ): ParseResult {
        val address = match.group(1)
        
        return ParseResult.Success(
            intent = TransactionIntent.ReverseLookup(address),
            confidence = 0.95,
            rawInput = input
        )
    }
    
    private suspend fun extractAnalyzePrivacy(
        match: java.util.regex.Matcher,
        input: String,
        resolver: EntityResolver
    ): ParseResult {
        val address = match.group(1) ?: config.defaultWallet ?: return ParseResult.NeedsInfo(
            intentType = IntentType.ANALYZE_PRIVACY,
            missing = listOf("wallet address"),
            partial = emptyMap(),
            suggestion = "Which wallet's privacy do you want to analyze?"
        )
        
        val resolved = resolver.resolveAddress(address)
        
        return if (resolved != null) {
            ParseResult.Success(
                intent = TransactionIntent.AnalyzePrivacy(address, resolved),
                confidence = 0.95,
                rawInput = input
            )
        } else {
            ParseResult.NeedsInfo(
                intentType = IntentType.ANALYZE_PRIVACY,
                missing = listOf("valid address"),
                partial = mapOf("address" to address),
                suggestion = "Could not resolve '$address'."
            )
        }
    }
    
    private suspend fun extractNftTransfer(
        match: java.util.regex.Matcher,
        input: String,
        resolver: EntityResolver
    ): ParseResult {
        val nftAddress = match.group(1)
        val recipient = match.group(2)
        val resolved = resolver.resolveAddress(recipient)
        
        return if (resolved != null) {
            ParseResult.Success(
                intent = TransactionIntent.NftTransfer(nftAddress, recipient, resolved),
                confidence = 0.95,
                rawInput = input
            )
        } else {
            ParseResult.NeedsInfo(
                intentType = IntentType.NFT_TRANSFER,
                missing = listOf("recipient address"),
                partial = mapOf("nft" to nftAddress),
                suggestion = "Could not resolve '$recipient'."
            )
        }
    }
    
    private suspend fun extractNftList(
        match: java.util.regex.Matcher,
        input: String,
        resolver: EntityResolver
    ): ParseResult {
        val nftAddress = match.group(1)
        val price = parseAmount(match.group(2))
        
        return ParseResult.Success(
            intent = TransactionIntent.NftList(nftAddress, price),
            confidence = 0.95,
            rawInput = input
        )
    }
    
    private suspend fun extractListWebhooks(
        match: java.util.regex.Matcher,
        input: String,
        resolver: EntityResolver
    ): ParseResult {
        return ParseResult.Success(
            intent = TransactionIntent.ListWebhooks,
            confidence = 0.95,
            rawInput = input
        )
    }
    
    // === HELPER FUNCTIONS ===
    
    private fun parseAmount(raw: String): BigDecimal {
        val cleaned = raw.lowercase()
            .replace(",", "")
            .replace("_", "")
            .trim()
        
        return when {
            cleaned.endsWith("k") -> BigDecimal(cleaned.dropLast(1)) * BigDecimal(1_000)
            cleaned.endsWith("m") -> BigDecimal(cleaned.dropLast(1)) * BigDecimal(1_000_000)
            cleaned.endsWith("b") -> BigDecimal(cleaned.dropLast(1)) * BigDecimal(1_000_000_000)
            else -> BigDecimal(cleaned)
        }
    }
    
    private fun generateSuggestions(input: String): List<CommandSuggestion> {
        return listOf(
            CommandSuggestion(
                template = "send {amount} SOL to {address}",
                description = "Transfer SOL to another wallet",
                examples = listOf("send 1 SOL to alice.sol", "send 0.5 SOL to moonmanquark.skr")
            ),
            CommandSuggestion(
                template = "swap {amount} {token} for {token}",
                description = "Swap tokens using Jupiter",
                examples = listOf("swap 100 USDC for SOL", "swap 1 SOL for BONK")
            ),
            CommandSuggestion(
                template = "check balance of {address}",
                description = "Get SOL balance",
                examples = listOf("check balance of alice.sol", "check my balance")
            ),
            CommandSuggestion(
                template = "get my NFTs",
                description = "List all NFTs in your wallet",
                examples = listOf("get my NFTs", "show my assets")
            ),
            CommandSuggestion(
                template = "stake {amount} SOL",
                description = "Stake SOL for rewards",
                examples = listOf("stake 10 SOL", "stake 5 SOL with Marinade")
            ),
            CommandSuggestion(
                template = "resolve {domain}",
                description = "Lookup domain address",
                examples = listOf("resolve alice.sol", "what is moonmanquark.skr")
            )
        )
    }
}

/**
 * Pattern with extraction function
 */
internal class NlpPattern(
    val pattern: Pattern,
    val extract: suspend (java.util.regex.Matcher, String, EntityResolver) -> ParseResult
)

/**
 * NLP Configuration
 */
data class NlpConfig(
    var defaultSlippageBps: Int = 50,
    var defaultWallet: String? = null,
    var language: Language = Language.ENGLISH
)

/**
 * Context for multi-turn conversations
 */
data class NlpContext(
    val lastMentionedAddress: String? = null,
    val lastMentionedToken: String? = null,
    val lastIntent: TransactionIntent? = null,
    val conversationHistory: List<String> = emptyList()
)

/**
 * Supported languages
 */
enum class Language {
    ENGLISH,
    SPANISH,
    PORTUGUESE,
    FRENCH,
    GERMAN,
    CHINESE,
    JAPANESE,
    KOREAN
}
