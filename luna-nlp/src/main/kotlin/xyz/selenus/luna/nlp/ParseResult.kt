package xyz.selenus.luna.nlp

import kotlinx.serialization.Serializable
import java.math.BigDecimal

/**
 * Result of parsing natural language input
 */
sealed class ParseResult {
    /**
     * Successfully parsed into a transaction intent
     */
    data class Success(
        val intent: TransactionIntent,
        val confidence: Double,
        val rawInput: String
    ) : ParseResult() {
        fun summary(): String = intent.summary()
    }
    
    /**
     * Multiple interpretations possible - user should clarify
     */
    data class Ambiguous(
        val primary: TransactionIntent,
        val alternatives: List<TransactionIntent>,
        val confidence: Double
    ) : ParseResult()
    
    /**
     * Partial understanding - need more information
     */
    data class NeedsInfo(
        val intentType: IntentType,
        val missing: List<String>,
        val partial: Map<String, String>,
        val suggestion: String
    ) : ParseResult()
    
    /**
     * Could not understand the input
     */
    data class Unknown(
        val input: String,
        val suggestions: List<CommandSuggestion>
    ) : ParseResult()
}

/**
 * Types of transaction intents
 */
enum class IntentType {
    // Transfers
    TRANSFER_SOL,
    TRANSFER_TOKEN,
    
    // Swaps
    SWAP,
    SWAP_EXACT_OUT,
    
    // Staking
    STAKE,
    UNSTAKE,
    CLAIM_REWARDS,
    
    // NFT Operations
    NFT_TRANSFER,
    NFT_LIST,
    NFT_BUY,
    NFT_BURN,
    
    // Token Operations
    TOKEN_CREATE,
    TOKEN_MINT,
    TOKEN_BURN,
    
    // DAS Queries
    GET_ASSETS,
    GET_ASSET_PROOF,
    SEARCH_ASSETS,
    
    // Enhanced Transactions
    PARSE_TRANSACTION,
    GET_TRANSACTION_HISTORY,
    
    // Priority Fees
    GET_PRIORITY_FEE,
    
    // Webhooks
    CREATE_WEBHOOK,
    DELETE_WEBHOOK,
    LIST_WEBHOOKS,
    
    // Balance & Info
    GET_BALANCE,
    GET_TOKEN_BALANCE,
    GET_ACCOUNT_INFO,
    
    // Domain Resolution
    RESOLVE_DOMAIN,
    REVERSE_LOOKUP,
    
    // Privacy
    ANALYZE_PRIVACY,
    GENERATE_STEALTH_ADDRESS,
    
    // Custom
    CUSTOM
}

/**
 * Base class for all transaction intents
 */
sealed class TransactionIntent {
    abstract val type: IntentType
    abstract fun summary(): String
    abstract fun details(): Map<String, String>
    
    // === TRANSFER INTENTS ===
    
    data class TransferSol(
        val amount: BigDecimal,
        val recipient: String,
        val recipientResolved: String? = null
    ) : TransactionIntent() {
        override val type = IntentType.TRANSFER_SOL
        override fun summary() = "Transfer $amount SOL to ${recipientResolved ?: recipient}"
        override fun details() = mapOf(
            "Amount" to "$amount SOL",
            "Recipient" to recipient,
            "Resolved" to (recipientResolved ?: "pending")
        )
    }
    
    data class TransferToken(
        val amount: BigDecimal,
        val token: String,
        val tokenMint: String? = null,
        val recipient: String,
        val recipientResolved: String? = null
    ) : TransactionIntent() {
        override val type = IntentType.TRANSFER_TOKEN
        override fun summary() = "Transfer $amount $token to ${recipientResolved ?: recipient}"
        override fun details() = mapOf(
            "Amount" to "$amount $token",
            "Token Mint" to (tokenMint ?: "pending"),
            "Recipient" to recipient,
            "Resolved" to (recipientResolved ?: "pending")
        )
    }
    
    // === SWAP INTENTS ===
    
    data class Swap(
        val inputAmount: BigDecimal,
        val inputToken: String,
        val inputMint: String? = null,
        val outputToken: String,
        val outputMint: String? = null,
        val slippageBps: Int = 50
    ) : TransactionIntent() {
        override val type = IntentType.SWAP
        override fun summary() = "Swap $inputAmount $inputToken for $outputToken"
        override fun details() = mapOf(
            "Input" to "$inputAmount $inputToken",
            "Output" to outputToken,
            "Slippage" to "${slippageBps / 100.0}%"
        )
    }
    
    data class SwapExactOut(
        val outputAmount: BigDecimal,
        val outputToken: String,
        val outputMint: String? = null,
        val inputToken: String,
        val inputMint: String? = null,
        val slippageBps: Int = 50
    ) : TransactionIntent() {
        override val type = IntentType.SWAP_EXACT_OUT
        override fun summary() = "Buy $outputAmount $outputToken with $inputToken"
        override fun details() = mapOf(
            "Output" to "$outputAmount $outputToken",
            "Input Token" to inputToken,
            "Slippage" to "${slippageBps / 100.0}%"
        )
    }
    
    // === STAKING INTENTS ===
    
    data class Stake(
        val amount: BigDecimal,
        val validator: String? = null
    ) : TransactionIntent() {
        override val type = IntentType.STAKE
        override fun summary() = "Stake $amount SOL" + (validator?.let { " with $it" } ?: "")
        override fun details() = mapOf(
            "Amount" to "$amount SOL",
            "Validator" to (validator ?: "auto-select")
        )
    }
    
    data class Unstake(
        val amount: BigDecimal
    ) : TransactionIntent() {
        override val type = IntentType.UNSTAKE
        override fun summary() = "Unstake $amount SOL"
        override fun details() = mapOf("Amount" to "$amount SOL")
    }
    
    data object ClaimRewards : TransactionIntent() {
        override val type = IntentType.CLAIM_REWARDS
        override fun summary() = "Claim staking rewards"
        override fun details() = emptyMap<String, String>()
    }
    
    // === NFT INTENTS ===
    
    data class NftTransfer(
        val nftAddress: String,
        val recipient: String,
        val recipientResolved: String? = null
    ) : TransactionIntent() {
        override val type = IntentType.NFT_TRANSFER
        override fun summary() = "Transfer NFT to ${recipientResolved ?: recipient}"
        override fun details() = mapOf(
            "NFT" to nftAddress,
            "Recipient" to recipient
        )
    }
    
    data class NftList(
        val nftAddress: String,
        val price: BigDecimal,
        val marketplace: String = "MagicEden"
    ) : TransactionIntent() {
        override val type = IntentType.NFT_LIST
        override fun summary() = "List NFT for $price SOL on $marketplace"
        override fun details() = mapOf(
            "NFT" to nftAddress,
            "Price" to "$price SOL",
            "Marketplace" to marketplace
        )
    }
    
    data class NftBuy(
        val nftAddress: String,
        val maxPrice: BigDecimal? = null
    ) : TransactionIntent() {
        override val type = IntentType.NFT_BUY
        override fun summary() = "Buy NFT" + (maxPrice?.let { " for up to $it SOL" } ?: "")
        override fun details() = mapOf(
            "NFT" to nftAddress,
            "Max Price" to (maxPrice?.let { "$it SOL" } ?: "market price")
        )
    }
    
    // === QUERY INTENTS ===
    
    data class GetAssets(
        val owner: String,
        val ownerResolved: String? = null
    ) : TransactionIntent() {
        override val type = IntentType.GET_ASSETS
        override fun summary() = "Get assets for ${ownerResolved ?: owner}"
        override fun details() = mapOf("Owner" to owner)
    }
    
    data class GetBalance(
        val address: String,
        val addressResolved: String? = null
    ) : TransactionIntent() {
        override val type = IntentType.GET_BALANCE
        override fun summary() = "Get SOL balance for ${addressResolved ?: address}"
        override fun details() = mapOf("Address" to address)
    }
    
    data class GetTokenBalance(
        val address: String,
        val token: String,
        val addressResolved: String? = null
    ) : TransactionIntent() {
        override val type = IntentType.GET_TOKEN_BALANCE
        override fun summary() = "Get $token balance for ${addressResolved ?: address}"
        override fun details() = mapOf("Address" to address, "Token" to token)
    }
    
    data class GetTransactionHistory(
        val address: String,
        val addressResolved: String? = null,
        val limit: Int = 10
    ) : TransactionIntent() {
        override val type = IntentType.GET_TRANSACTION_HISTORY
        override fun summary() = "Get transaction history for ${addressResolved ?: address}"
        override fun details() = mapOf("Address" to address, "Limit" to limit.toString())
    }
    
    // === DOMAIN INTENTS ===
    
    data class ResolveDomain(
        val domain: String
    ) : TransactionIntent() {
        override val type = IntentType.RESOLVE_DOMAIN
        override fun summary() = "Resolve domain $domain"
        override fun details() = mapOf("Domain" to domain)
    }
    
    data class ReverseLookup(
        val address: String
    ) : TransactionIntent() {
        override val type = IntentType.REVERSE_LOOKUP
        override fun summary() = "Lookup domain for $address"
        override fun details() = mapOf("Address" to address)
    }
    
    // === PRIVACY INTENTS ===
    
    data class AnalyzePrivacy(
        val address: String,
        val addressResolved: String? = null
    ) : TransactionIntent() {
        override val type = IntentType.ANALYZE_PRIVACY
        override fun summary() = "Analyze privacy for ${addressResolved ?: address}"
        override fun details() = mapOf("Address" to address)
    }
    
    data class GenerateStealthAddress(
        val recipientMetaAddress: String
    ) : TransactionIntent() {
        override val type = IntentType.GENERATE_STEALTH_ADDRESS
        override fun summary() = "Generate stealth address for $recipientMetaAddress"
        override fun details() = mapOf("Meta Address" to recipientMetaAddress)
    }
    
    // === WEBHOOK INTENTS ===
    
    data class CreateWebhook(
        val webhookUrl: String,
        val addresses: List<String>,
        val transactionTypes: List<String> = listOf("ANY")
    ) : TransactionIntent() {
        override val type = IntentType.CREATE_WEBHOOK
        override fun summary() = "Create webhook for ${addresses.size} addresses"
        override fun details() = mapOf(
            "URL" to webhookUrl,
            "Addresses" to addresses.joinToString(", "),
            "Types" to transactionTypes.joinToString(", ")
        )
    }
    
    data object ListWebhooks : TransactionIntent() {
        override val type = IntentType.LIST_WEBHOOKS
        override fun summary() = "List all webhooks"
        override fun details() = emptyMap<String, String>()
    }
    
    data class DeleteWebhook(
        val webhookId: String
    ) : TransactionIntent() {
        override val type = IntentType.DELETE_WEBHOOK
        override fun summary() = "Delete webhook $webhookId"
        override fun details() = mapOf("Webhook ID" to webhookId)
    }
}

/**
 * Command suggestion for unknown inputs
 */
@Serializable
data class CommandSuggestion(
    val template: String,
    val description: String,
    val examples: List<String>
)

/**
 * Extracted entity from natural language
 */
@Serializable
data class ExtractedEntity(
    val type: EntityType,
    val value: String,
    val raw: String,
    val startIndex: Int,
    val endIndex: Int
)

/**
 * Types of entities that can be extracted
 */
enum class EntityType {
    AMOUNT,
    TOKEN,
    ADDRESS,
    DOMAIN,
    NUMBER,
    STRING,
    VALIDATOR,
    MARKETPLACE,
    URL
}
