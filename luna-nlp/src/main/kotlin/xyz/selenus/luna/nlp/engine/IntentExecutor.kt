package xyz.selenus.luna.nlp.engine

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import xyz.selenus.luna.nlp.ParseResult
import xyz.selenus.luna.nlp.TransactionIntent
import java.math.BigDecimal

/**
 * Intent Execution Engine - Solana Mobile Standard compliant
 * 
 * Executes parsed NLP intents through a secure, auditable pipeline
 * with support for hardware wallet signing via Seed Vault protocol.
 * 
 * Architecture follows Android 2026 coroutine patterns:
 * - StateFlow for UI state management
 * - Flow for streaming execution updates
 * - Structured concurrency with proper cancellation
 */
class IntentExecutor private constructor(
    private val config: ExecutorConfig,
    private val signer: TransactionSigner,
    private val broadcaster: TransactionBroadcaster
) {
    
    private val _executionState = MutableStateFlow<ExecutionState>(ExecutionState.Idle)
    val executionState: StateFlow<ExecutionState> = _executionState.asStateFlow()
    
    private val _transactionHistory = MutableStateFlow<List<ExecutionRecord>>(emptyList())
    val transactionHistory: StateFlow<List<ExecutionRecord>> = _transactionHistory.asStateFlow()
    
    companion object {
        fun create(
            config: ExecutorConfig = ExecutorConfig(),
            signer: TransactionSigner,
            broadcaster: TransactionBroadcaster
        ): IntentExecutor {
            return IntentExecutor(config, signer, broadcaster)
        }
    }
    
    /**
     * Execute a parsed intent with full lifecycle management
     * Returns a Flow of execution updates for reactive UI binding
     */
    fun execute(parseResult: ParseResult.Success): Flow<ExecutionUpdate> = flow {
        require(parseResult.confidence >= config.minimumConfidence) {
            "Intent confidence ${parseResult.confidence} below threshold ${config.minimumConfidence}"
        }
        
        _executionState.value = ExecutionState.Preparing(parseResult.intent)
        emit(ExecutionUpdate.Started(parseResult.intent))
        
        try {
            // Step 1: Preview and validate
            val preview = generatePreview(parseResult.intent)
            emit(ExecutionUpdate.Preview(preview))
            _executionState.value = ExecutionState.AwaitingConfirmation(preview)
            
            // Step 2: Build transaction
            val transaction = buildTransaction(parseResult.intent)
            emit(ExecutionUpdate.TransactionBuilt(transaction))
            _executionState.value = ExecutionState.AwaitingSignature(transaction)
            
            // Step 3: Sign via Seed Vault / hardware wallet
            val signedTx = signer.sign(transaction)
            emit(ExecutionUpdate.Signed(signedTx))
            _executionState.value = ExecutionState.Broadcasting(signedTx)
            
            // Step 4: Broadcast with retry logic
            val signature = broadcaster.broadcast(signedTx, config.broadcastConfig)
            emit(ExecutionUpdate.Broadcasted(signature))
            
            // Step 5: Confirm on-chain
            val confirmation = awaitConfirmation(signature)
            emit(ExecutionUpdate.Confirmed(confirmation))
            
            // Record execution
            val record = ExecutionRecord(
                intent = parseResult.intent,
                signature = signature,
                confirmation = confirmation,
                timestamp = System.currentTimeMillis()
            )
            _transactionHistory.value = _transactionHistory.value + record
            _executionState.value = ExecutionState.Completed(record)
            
            emit(ExecutionUpdate.Completed(record))
            
        } catch (e: ExecutionException) {
            _executionState.value = ExecutionState.Failed(e)
            emit(ExecutionUpdate.Failed(e))
            throw e
        } finally {
            // Reset to idle after delay for UI feedback
            kotlinx.coroutines.delay(config.resetDelayMs)
            _executionState.value = ExecutionState.Idle
        }
    }
    
    /**
     * Execute multiple intents as an atomic batch
     * Uses Jito bundles when available for MEV protection
     */
    fun executeBatch(intents: List<ParseResult.Success>): Flow<BatchExecutionUpdate> = flow {
        emit(BatchExecutionUpdate.Started(intents.size))
        
        val transactions = intents.mapIndexed { index, result ->
            emit(BatchExecutionUpdate.BuildingTransaction(index, result.intent))
            buildTransaction(result.intent)
        }
        
        emit(BatchExecutionUpdate.SigningBatch(transactions.size))
        val signedBatch = signer.signBatch(transactions)
        
        emit(BatchExecutionUpdate.Broadcasting)
        val signatures = broadcaster.broadcastBatch(signedBatch)
        
        emit(BatchExecutionUpdate.Completed(signatures))
    }
    
    /**
     * Generate human-readable preview before execution
     */
    private suspend fun generatePreview(intent: TransactionIntent): TransactionPreview {
        return TransactionPreview(
            intent = intent,
            summary = intent.summary(),
            estimatedFee = estimateFee(intent),
            estimatedTime = estimateTime(intent),
            warnings = detectWarnings(intent),
            riskLevel = assessRisk(intent)
        )
    }
    
    private suspend fun buildTransaction(intent: TransactionIntent): UnsignedTransaction {
        // Build transaction based on intent type
        return when (intent) {
            is TransactionIntent.TransferSol -> buildSolTransfer(intent)
            is TransactionIntent.TransferToken -> buildTokenTransfer(intent)
            is TransactionIntent.Swap -> buildSwap(intent)
            is TransactionIntent.Stake -> buildStake(intent)
            else -> throw ExecutionException.UnsupportedIntent(intent)
        }
    }
    
    private suspend fun buildSolTransfer(intent: TransactionIntent.TransferSol): UnsignedTransaction {
        return UnsignedTransaction(
            type = TransactionType.TRANSFER,
            instructions = listOf(
                Instruction.Transfer(
                    from = config.walletAddress,
                    to = intent.recipientResolved ?: intent.recipient,
                    lamports = intent.amount.multiply(BigDecimal(1_000_000_000)).toLong()
                )
            ),
            recentBlockhash = null, // Fetched at sign time
            feePayer = config.walletAddress
        )
    }
    
    private suspend fun buildTokenTransfer(intent: TransactionIntent.TransferToken): UnsignedTransaction {
        return UnsignedTransaction(
            type = TransactionType.TOKEN_TRANSFER,
            instructions = listOf(
                Instruction.TokenTransfer(
                    mint = intent.tokenMint ?: "",
                    from = config.walletAddress,
                    to = intent.recipientResolved ?: intent.recipient,
                    amount = intent.amount
                )
            ),
            recentBlockhash = null,
            feePayer = config.walletAddress
        )
    }
    
    private suspend fun buildSwap(intent: TransactionIntent.Swap): UnsignedTransaction {
        // Use Jupiter for optimal routing
        return UnsignedTransaction(
            type = TransactionType.SWAP,
            instructions = emptyList(), // Jupiter builds these
            recentBlockhash = null,
            feePayer = config.walletAddress,
            metadata = mapOf(
                "inputMint" to (intent.inputMint ?: ""),
                "outputMint" to (intent.outputMint ?: ""),
                "amount" to intent.inputAmount.toString(),
                "slippage" to intent.slippageBps.toString()
            )
        )
    }
    
    private suspend fun buildStake(intent: TransactionIntent.Stake): UnsignedTransaction {
        return UnsignedTransaction(
            type = TransactionType.STAKE,
            instructions = emptyList(),
            recentBlockhash = null,
            feePayer = config.walletAddress,
            metadata = mapOf(
                "amount" to intent.amount.toString(),
                "validator" to (intent.validator ?: "auto")
            )
        )
    }
    
    private suspend fun estimateFee(intent: TransactionIntent): BigDecimal {
        // Base fee + priority fee estimation
        val baseFee = BigDecimal("0.000005") // 5000 lamports
        val priorityMultiplier = when (intent) {
            is TransactionIntent.Swap -> BigDecimal("1.5")
            is TransactionIntent.Stake -> BigDecimal("1.2")
            else -> BigDecimal.ONE
        }
        return baseFee.multiply(priorityMultiplier)
    }
    
    private suspend fun estimateTime(intent: TransactionIntent): Long {
        // Estimated seconds to confirmation
        return when (intent) {
            is TransactionIntent.Swap -> 15L
            is TransactionIntent.Stake -> 30L
            else -> 5L
        }
    }
    
    private suspend fun detectWarnings(intent: TransactionIntent): List<Warning> {
        val warnings = mutableListOf<Warning>()
        
        when (intent) {
            is TransactionIntent.TransferSol -> {
                if (intent.amount > BigDecimal("10")) {
                    warnings.add(Warning.LargeTransfer(intent.amount))
                }
            }
            is TransactionIntent.Swap -> {
                if (intent.slippageBps > 100) {
                    warnings.add(Warning.HighSlippage(intent.slippageBps))
                }
            }
            else -> {}
        }
        
        return warnings
    }
    
    private fun assessRisk(intent: TransactionIntent): RiskLevel {
        return when (intent) {
            is TransactionIntent.TransferSol -> {
                when {
                    intent.amount > BigDecimal("100") -> RiskLevel.HIGH
                    intent.amount > BigDecimal("10") -> RiskLevel.MEDIUM
                    else -> RiskLevel.LOW
                }
            }
            is TransactionIntent.Swap -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }
    }
    
    private suspend fun awaitConfirmation(signature: String): Confirmation {
        // Poll for confirmation with exponential backoff
        var attempts = 0
        val maxAttempts = 30
        var delayMs = 500L
        
        while (attempts < maxAttempts) {
            val status = broadcaster.getStatus(signature)
            if (status.confirmed) {
                return Confirmation(
                    signature = signature,
                    slot = status.slot,
                    confirmations = status.confirmations,
                    finalized = status.finalized
                )
            }
            kotlinx.coroutines.delay(delayMs)
            delayMs = minOf(delayMs * 2, 5000L)
            attempts++
        }
        
        throw ExecutionException.ConfirmationTimeout(signature)
    }
}

/**
 * Execution state for reactive UI binding
 */
sealed class ExecutionState {
    data object Idle : ExecutionState()
    data class Preparing(val intent: TransactionIntent) : ExecutionState()
    data class AwaitingConfirmation(val preview: TransactionPreview) : ExecutionState()
    data class AwaitingSignature(val transaction: UnsignedTransaction) : ExecutionState()
    data class Broadcasting(val signedTx: SignedTransaction) : ExecutionState()
    data class Completed(val record: ExecutionRecord) : ExecutionState()
    data class Failed(val error: ExecutionException) : ExecutionState()
}

/**
 * Streaming execution updates
 */
sealed class ExecutionUpdate {
    data class Started(val intent: TransactionIntent) : ExecutionUpdate()
    data class Preview(val preview: TransactionPreview) : ExecutionUpdate()
    data class TransactionBuilt(val transaction: UnsignedTransaction) : ExecutionUpdate()
    data class Signed(val signedTx: SignedTransaction) : ExecutionUpdate()
    data class Broadcasted(val signature: String) : ExecutionUpdate()
    data class Confirmed(val confirmation: Confirmation) : ExecutionUpdate()
    data class Completed(val record: ExecutionRecord) : ExecutionUpdate()
    data class Failed(val error: ExecutionException) : ExecutionUpdate()
}

/**
 * Batch execution updates
 */
sealed class BatchExecutionUpdate {
    data class Started(val count: Int) : BatchExecutionUpdate()
    data class BuildingTransaction(val index: Int, val intent: TransactionIntent) : BatchExecutionUpdate()
    data class SigningBatch(val count: Int) : BatchExecutionUpdate()
    data object Broadcasting : BatchExecutionUpdate()
    data class Completed(val signatures: List<String>) : BatchExecutionUpdate()
}

/**
 * Transaction preview for user confirmation
 */
data class TransactionPreview(
    val intent: TransactionIntent,
    val summary: String,
    val estimatedFee: BigDecimal,
    val estimatedTime: Long,
    val warnings: List<Warning>,
    val riskLevel: RiskLevel
) {
    fun toHumanReadable(): String = buildString {
        appendLine("📝 Transaction Preview")
        appendLine("━━━━━━━━━━━━━━━━━━━━━━")
        appendLine(summary)
        appendLine()
        appendLine("⚡ Estimated Fee: $estimatedFee SOL")
        appendLine("⏱️ Estimated Time: ${estimatedTime}s")
        if (warnings.isNotEmpty()) {
            appendLine()
            appendLine("⚠️ Warnings:")
            warnings.forEach { appendLine("  • ${it.message}") }
        }
        appendLine()
        appendLine("🔒 Risk Level: $riskLevel")
    }
}

/**
 * Execution configuration
 */
data class ExecutorConfig(
    val walletAddress: String = "",
    val minimumConfidence: Double = 0.80,
    val resetDelayMs: Long = 2000L,
    val broadcastConfig: BroadcastConfig = BroadcastConfig()
)

data class BroadcastConfig(
    val maxRetries: Int = 3,
    val retryDelayMs: Long = 1000L,
    val useJito: Boolean = false,
    val jitoTipLamports: Long = 10000L
)

/**
 * Execution record for history
 */
data class ExecutionRecord(
    val intent: TransactionIntent,
    val signature: String,
    val confirmation: Confirmation,
    val timestamp: Long
)

data class Confirmation(
    val signature: String,
    val slot: Long,
    val confirmations: Int,
    val finalized: Boolean
)

/**
 * Transaction types
 */
enum class TransactionType {
    TRANSFER, TOKEN_TRANSFER, SWAP, STAKE, UNSTAKE, NFT, CUSTOM
}

/**
 * Risk levels for UI display
 */
enum class RiskLevel {
    LOW, MEDIUM, HIGH, CRITICAL
}

/**
 * Warning types
 */
sealed class Warning(open val message: String) {
    data class LargeTransfer(val amount: BigDecimal) : Warning("Large transfer: $amount SOL")
    data class HighSlippage(val bps: Int) : Warning("High slippage: ${bps / 100.0}%")
    data class UnverifiedRecipient(val address: String) : Warning("Unverified recipient: $address")
    data class NewToken(val symbol: String) : Warning("New/unverified token: $symbol")
}

/**
 * Execution exceptions
 */
sealed class ExecutionException(override val message: String) : Exception(message) {
    data class UnsupportedIntent(val intent: TransactionIntent) : ExecutionException("Unsupported intent: ${intent::class.simpleName}")
    data class SigningFailed(val reason: String) : ExecutionException("Signing failed: $reason")
    data class BroadcastFailed(val reason: String) : ExecutionException("Broadcast failed: $reason")
    data class ConfirmationTimeout(val signature: String) : ExecutionException("Confirmation timeout: $signature")
    data class InsufficientFunds(val required: BigDecimal, val available: BigDecimal) : ExecutionException("Insufficient funds: need $required, have $available")
}

/**
 * Transaction data classes
 */
data class UnsignedTransaction(
    val type: TransactionType,
    val instructions: List<Instruction>,
    val recentBlockhash: String?,
    val feePayer: String,
    val metadata: Map<String, String> = emptyMap()
)

data class SignedTransaction(
    val unsigned: UnsignedTransaction,
    val signatures: List<String>,
    val serialized: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as SignedTransaction
        return signatures == other.signatures
    }
    
    override fun hashCode(): Int = signatures.hashCode()
}

sealed class Instruction {
    data class Transfer(val from: String, val to: String, val lamports: Long) : Instruction()
    data class TokenTransfer(val mint: String, val from: String, val to: String, val amount: BigDecimal) : Instruction()
    data class CreateAccount(val payer: String, val newAccount: String, val lamports: Long, val space: Long, val owner: String) : Instruction()
}

/**
 * Transaction signer interface - implements Seed Vault protocol
 */
interface TransactionSigner {
    suspend fun sign(transaction: UnsignedTransaction): SignedTransaction
    suspend fun signBatch(transactions: List<UnsignedTransaction>): List<SignedTransaction>
    fun isHardwareWallet(): Boolean
}

/**
 * Transaction broadcaster interface
 */
interface TransactionBroadcaster {
    suspend fun broadcast(transaction: SignedTransaction, config: BroadcastConfig): String
    suspend fun broadcastBatch(transactions: List<SignedTransaction>): List<String>
    suspend fun getStatus(signature: String): TransactionStatus
}

data class TransactionStatus(
    val signature: String,
    val confirmed: Boolean,
    val slot: Long,
    val confirmations: Int,
    val finalized: Boolean,
    val error: String? = null
)
