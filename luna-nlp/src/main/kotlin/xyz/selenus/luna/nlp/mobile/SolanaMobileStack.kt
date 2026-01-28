package xyz.selenus.luna.nlp.mobile

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow

/**
 * Solana Mobile Stack Integration
 * 
 * Implements Solana Mobile Standard protocols:
 * - Mobile Wallet Adapter (MWA) for wallet connections
 * - Seed Vault for secure key storage
 * - Deep linking for dApp discovery
 * - Transaction signing with biometric confirmation
 * 
 * Designed for Android 2026 architecture with:
 * - StateFlow for reactive UI binding
 * - Structured concurrency for async operations
 * - Proper lifecycle management
 */

/**
 * Mobile Wallet Adapter - Connect to Solana wallets
 */
interface MobileWalletAdapter {
    val connectionState: StateFlow<WalletConnectionState>
    val connectedWallet: StateFlow<ConnectedWallet?>
    
    suspend fun connect(config: ConnectionConfig = ConnectionConfig()): WalletConnectionResult
    suspend fun disconnect()
    suspend fun signTransaction(transaction: ByteArray): SignResult
    suspend fun signAllTransactions(transactions: List<ByteArray>): List<SignResult>
    suspend fun signMessage(message: ByteArray): SignResult
}

/**
 * Wallet connection state
 */
sealed class WalletConnectionState {
    data object Disconnected : WalletConnectionState()
    data object Connecting : WalletConnectionState()
    data class Connected(val wallet: ConnectedWallet) : WalletConnectionState()
    data class Error(val message: String) : WalletConnectionState()
}

/**
 * Seed Vault Integration - Hardware-backed key storage
 */
interface SeedVaultProvider {
    val isAvailable: Boolean
    val authenticationLevel: AuthenticationLevel
    
    suspend fun requestAuthorization(purpose: AuthorizationPurpose): AuthorizationResult
    suspend fun signWithBiometrics(transaction: ByteArray): BiometricSignResult
    suspend fun getPublicKey(): ByteArray?
}

/**
 * NLP Mobile Bridge - Connects NLP intents to Mobile Stack
 */
class NlpMobileBridge(
    private val walletAdapter: MobileWalletAdapter,
    private val seedVault: SeedVaultProvider?
) {
    
    private val _state = MutableStateFlow<BridgeState>(BridgeState.Disconnected)
    val state: StateFlow<BridgeState> = _state.asStateFlow()
    
    /**
     * Initialize connection to mobile wallet
     */
    suspend fun initialize(): InitializationResult {
        _state.value = BridgeState.Connecting
        
        return try {
            val result = walletAdapter.connect()
            when (result) {
                is WalletConnectionResult.Success -> {
                    _state.value = BridgeState.Connected(result.wallet)
                    
                    // Check Seed Vault availability
                    val seedVaultStatus = seedVault?.let {
                        if (it.isAvailable) {
                            SeedVaultStatus.Available(it.authenticationLevel)
                        } else {
                            SeedVaultStatus.NotAvailable
                        }
                    } ?: SeedVaultStatus.NotSupported
                    
                    InitializationResult.Success(result.wallet, seedVaultStatus)
                }
                is WalletConnectionResult.Cancelled -> {
                    _state.value = BridgeState.Disconnected
                    InitializationResult.Cancelled
                }
                is WalletConnectionResult.Error -> {
                    _state.value = BridgeState.Error(result.error)
                    InitializationResult.Error(result.error)
                }
            }
        } catch (e: Exception) {
            _state.value = BridgeState.Error(e.message ?: "Unknown error")
            InitializationResult.Error(e.message ?: "Unknown error")
        }
    }
    
    /**
     * Sign transaction with appropriate method (Seed Vault or MWA)
     */
    suspend fun signTransaction(transaction: ByteArray): MobileSignResult {
        val currentState = _state.value
        if (currentState !is BridgeState.Connected) {
            return MobileSignResult.NotConnected
        }
        
        // Prefer Seed Vault if available
        return if (seedVault?.isAvailable == true) {
            signWithSeedVault(transaction)
        } else {
            signWithMwa(transaction)
        }
    }
    
    private suspend fun signWithSeedVault(transaction: ByteArray): MobileSignResult {
        val seedVault = this.seedVault ?: return MobileSignResult.Error("Seed Vault not available")
        
        // Request biometric authorization
        val authResult = seedVault.requestAuthorization(
            AuthorizationPurpose.SignTransaction("Sign transaction")
        )
        
        return when (authResult) {
            is AuthorizationResult.Authorized -> {
                val signResult = seedVault.signWithBiometrics(transaction)
                when (signResult) {
                    is BiometricSignResult.Success -> MobileSignResult.Success(
                        signature = signResult.signature,
                        method = SigningMethod.SEED_VAULT
                    )
                    is BiometricSignResult.Cancelled -> MobileSignResult.Cancelled
                    is BiometricSignResult.Failed -> MobileSignResult.Error(signResult.reason)
                }
            }
            is AuthorizationResult.Denied -> MobileSignResult.Denied
            is AuthorizationResult.Error -> MobileSignResult.Error(authResult.reason)
        }
    }
    
    private suspend fun signWithMwa(transaction: ByteArray): MobileSignResult {
        return when (val result = walletAdapter.signTransaction(transaction)) {
            is SignResult.Success -> MobileSignResult.Success(
                signature = result.signature,
                method = SigningMethod.MWA
            )
            is SignResult.Rejected -> MobileSignResult.Denied
            is SignResult.Error -> MobileSignResult.Error(result.reason)
        }
    }
    
    /**
     * Sign multiple transactions (for batches/chains)
     */
    suspend fun signAllTransactions(transactions: List<ByteArray>): MobileBatchSignResult {
        val currentState = _state.value
        if (currentState !is BridgeState.Connected) {
            return MobileBatchSignResult.NotConnected
        }
        
        val results = walletAdapter.signAllTransactions(transactions)
        val successes = results.filterIsInstance<SignResult.Success>()
        
        return if (successes.size == transactions.size) {
            MobileBatchSignResult.AllSigned(successes.map { it.signature })
        } else {
            val errors = results.filterIsInstance<SignResult.Error>()
            MobileBatchSignResult.PartialSuccess(
                signed = successes.map { it.signature },
                failed = errors.size,
                errors = errors.map { it.reason }
            )
        }
    }
    
    /**
     * Disconnect from wallet
     */
    suspend fun disconnect() {
        walletAdapter.disconnect()
        _state.value = BridgeState.Disconnected
    }
}

/**
 * Bridge state
 */
sealed class BridgeState {
    data object Disconnected : BridgeState()
    data object Connecting : BridgeState()
    data class Connected(val wallet: ConnectedWallet) : BridgeState()
    data class Error(val message: String) : BridgeState()
}

/**
 * Initialization result
 */
sealed class InitializationResult {
    data class Success(
        val wallet: ConnectedWallet,
        val seedVaultStatus: SeedVaultStatus
    ) : InitializationResult()
    data object Cancelled : InitializationResult()
    data class Error(val reason: String) : InitializationResult()
}

/**
 * Seed Vault status
 */
sealed class SeedVaultStatus {
    data class Available(val authLevel: AuthenticationLevel) : SeedVaultStatus()
    data object NotAvailable : SeedVaultStatus()
    data object NotSupported : SeedVaultStatus()
}

/**
 * Mobile signing result
 */
sealed class MobileSignResult {
    data class Success(
        val signature: ByteArray,
        val method: SigningMethod
    ) : MobileSignResult() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as Success
            return signature.contentEquals(other.signature) && method == other.method
        }
        override fun hashCode(): Int = signature.contentHashCode()
    }
    data object NotConnected : MobileSignResult()
    data object Cancelled : MobileSignResult()
    data object Denied : MobileSignResult()
    data class Error(val reason: String) : MobileSignResult()
}

/**
 * Batch signing result
 */
sealed class MobileBatchSignResult {
    data class AllSigned(val signatures: List<ByteArray>) : MobileBatchSignResult()
    data class PartialSuccess(
        val signed: List<ByteArray>,
        val failed: Int,
        val errors: List<String>
    ) : MobileBatchSignResult()
    data object NotConnected : MobileBatchSignResult()
    data class Error(val reason: String) : MobileBatchSignResult()
}

/**
 * Signing method used
 */
enum class SigningMethod {
    SEED_VAULT,  // Hardware-backed
    MWA,         // Mobile Wallet Adapter
    LOCAL        // Software wallet (less secure)
}

/**
 * Connection configuration
 */
data class ConnectionConfig(
    val cluster: Cluster = Cluster.MAINNET,
    val appIdentity: AppIdentity = AppIdentity.default(),
    val preferredWallet: String? = null
)

/**
 * App identity for wallet connections
 */
data class AppIdentity(
    val name: String,
    val uri: String,
    val icon: String? = null
) {
    companion object {
        fun default() = AppIdentity(
            name = "Luna SDK",
            uri = "https://selenus.xyz",
            icon = null
        )
    }
}

/**
 * Cluster selection
 */
enum class Cluster {
    MAINNET,
    DEVNET,
    TESTNET,
    LOCALNET
}

/**
 * Connected wallet info
 */
data class ConnectedWallet(
    val publicKey: String,
    val label: String?,
    val iconUri: String?,
    val walletName: String,
    val supportsSignAndSend: Boolean
)

/**
 * Wallet connection result
 */
sealed class WalletConnectionResult {
    data class Success(val wallet: ConnectedWallet) : WalletConnectionResult()
    data object Cancelled : WalletConnectionResult()
    data class Error(val error: String) : WalletConnectionResult()
}

/**
 * Sign result
 */
sealed class SignResult {
    data class Success(val signature: ByteArray) : SignResult() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            return signature.contentEquals((other as Success).signature)
        }
        override fun hashCode(): Int = signature.contentHashCode()
    }
    data object Rejected : SignResult()
    data class Error(val reason: String) : SignResult()
}

/**
 * Authentication level for Seed Vault
 */
enum class AuthenticationLevel {
    NONE,           // No authentication required
    BIOMETRIC,      // Fingerprint/Face
    PIN,            // PIN code
    BIOMETRIC_STRONG // Strong biometric (e.g., iris)
}

/**
 * Authorization purpose
 */
sealed class AuthorizationPurpose {
    data class SignTransaction(val description: String) : AuthorizationPurpose()
    data class SignMessage(val message: String) : AuthorizationPurpose()
    data object ExportPublicKey : AuthorizationPurpose()
}

/**
 * Authorization result
 */
sealed class AuthorizationResult {
    data object Authorized : AuthorizationResult()
    data object Denied : AuthorizationResult()
    data class Error(val reason: String) : AuthorizationResult()
}

/**
 * Biometric sign result
 */
sealed class BiometricSignResult {
    data class Success(val signature: ByteArray) : BiometricSignResult() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            return signature.contentEquals((other as Success).signature)
        }
        override fun hashCode(): Int = signature.contentHashCode()
    }
    data object Cancelled : BiometricSignResult()
    data class Failed(val reason: String) : BiometricSignResult()
}

/**
 * Deep Link Handler for dApp discovery
 */
class DeepLinkHandler {
    
    /**
     * Parse Solana Pay URLs into NLP commands
     */
    fun parseSolanaPay(url: String): SolanaPayIntent? {
        if (!url.startsWith("solana:")) return null
        
        val uri = url.removePrefix("solana:")
        
        // Transfer format: solana:<recipient>?amount=<amount>&spl-token=<mint>
        val parts = uri.split("?")
        val recipient = parts[0]
        
        val params = if (parts.size > 1) {
            parts[1].split("&").associate { param ->
                val kv = param.split("=")
                kv[0] to (kv.getOrNull(1) ?: "")
            }
        } else {
            emptyMap()
        }
        
        val amount = params["amount"]?.toBigDecimalOrNull()
        val splToken = params["spl-token"]
        val label = params["label"]
        val message = params["message"]
        val memo = params["memo"]
        
        return SolanaPayIntent(
            recipient = recipient,
            amount = amount,
            splToken = splToken,
            label = label,
            message = message,
            memo = memo
        )
    }
    
    /**
     * Convert Solana Pay intent to NLP command
     */
    fun solanaPayToNlp(intent: SolanaPayIntent): String {
        val amount = intent.amount ?: return "check address ${intent.recipient}"
        
        return if (intent.splToken != null) {
            "send $amount ${intent.splToken} to ${intent.recipient}"
        } else {
            "send $amount SOL to ${intent.recipient}"
        }
    }
    
    /**
     * Generate Solana Pay URL from NLP intent
     */
    fun nlpToSolanaPay(recipient: String, amount: java.math.BigDecimal, splToken: String? = null): String {
        val base = "solana:$recipient"
        val params = mutableListOf("amount=$amount")
        splToken?.let { params.add("spl-token=$it") }
        return "$base?${params.joinToString("&")}"
    }
}

/**
 * Solana Pay intent
 */
data class SolanaPayIntent(
    val recipient: String,
    val amount: java.math.BigDecimal?,
    val splToken: String?,
    val label: String?,
    val message: String?,
    val memo: String?
)

/**
 * QR Code Scanner integration
 */
interface QrCodeScanner {
    /**
     * Scan QR code and return content
     */
    suspend fun scan(): QrScanResult
}

sealed class QrScanResult {
    data class Success(val content: String, val format: QrFormat) : QrScanResult()
    data object Cancelled : QrScanResult()
    data class Error(val reason: String) : QrScanResult()
}

enum class QrFormat {
    SOLANA_PAY,     // solana: URL
    ADDRESS,        // Raw Solana address
    TRANSACTION,    // Serialized transaction
    UNKNOWN
}

/**
 * Haptic feedback for mobile interactions
 */
interface HapticFeedback {
    fun success()
    fun error()
    fun warning()
    fun light()
    fun medium()
    fun heavy()
}

/**
 * Accessibility features
 */
interface AccessibilityProvider {
    fun announce(message: String)
    fun describe(element: String, description: String)
    val isScreenReaderEnabled: Boolean
    val isReducedMotionEnabled: Boolean
}
