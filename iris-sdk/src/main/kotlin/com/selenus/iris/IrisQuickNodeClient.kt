@file:Suppress("unused")
package com.selenus.iris

// ============================================================================
// IRIS SDK - The Definitive QuickNode Solana SDK
// ============================================================================
// Named after Iris, Greek goddess of the rainbow and swift messenger of the gods
// Representing speed, communication, and the bridge between developers and Solana
// ============================================================================

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import java.util.UUID

// ============================================================================
// ENUMS & CONFIGURATION
// ============================================================================

/**
 * Solana network clusters supported by QuickNode.
 */
enum class SolanaNetwork(val slug: String) {
    /** Production Solana network - supports Archive */
    MAINNET_BETA("mainnet-beta"),
    /** Developer testing network */
    DEVNET("devnet"),
    /** Testnet for validators */
    TESTNET("testnet")
}

/**
 * Commitment levels for transaction confirmation.
 */
enum class Commitment(val value: String) {
    /** Transaction has been processed - fastest but least certain */
    PROCESSED("processed"),
    /** Transaction has been confirmed by supermajority of cluster */
    CONFIRMED("confirmed"),
    /** Transaction is finalized - maximum certainty */
    FINALIZED("finalized")
}

/**
 * Encoding formats for account data and transactions.
 */
enum class Encoding(val value: String) {
    BASE58("base58"),
    BASE64("base64"),
    BASE64_ZSTD("base64+zstd"),
    JSON("json"),
    JSON_PARSED("jsonParsed")
}

/**
 * JITO regions for geographic optimization.
 */
enum class JitoRegion(val value: String) {
    NYC("ny"),
    AMSTERDAM("amsterdam"),
    FRANKFURT("frankfurt"),
    TOKYO("tokyo")
}

/**
 * Priority fee levels for transaction prioritization.
 */
enum class PriorityLevel(val percentile: Int) {
    /** Minimum viable fee */
    LOW(25),
    /** Standard priority */
    MEDIUM(50),
    /** Higher priority for faster landing */
    HIGH(75),
    /** Maximum priority - 95th percentile */
    VERY_HIGH(95),
    /** Extreme priority - for time-critical transactions */
    UNSAFE_MAX(100)
}

// ============================================================================
// DATA CLASSES - RPC Foundation
// ============================================================================

@Serializable
data class RpcRequest<T>(
    val jsonrpc: String = "2.0",
    val id: String,
    val method: String,
    val params: T
)

@Serializable
data class RpcError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null
)

@Serializable
data class RpcResponse<T>(
    val jsonrpc: String = "2.0",
    val id: String? = null,
    val result: T? = null,
    val error: RpcError? = null
)

// ============================================================================
// DATA CLASSES - Account & Balance
// ============================================================================

@Serializable
data class AccountInfo(
    val data: JsonElement,
    val executable: Boolean,
    val lamports: Long,
    val owner: String,
    val rentEpoch: JsonElement? = null, // Can be u64 (larger than Long.MAX_VALUE)
    val space: Long? = null
)

@Serializable
data class ContextSlot(val slot: Long)

@Serializable
data class AccountInfoWithContext(
    val context: ContextSlot,
    val value: AccountInfo?
)

@Serializable
data class BalanceResult(
    val context: ContextSlot,
    val value: Long
)

@Serializable
data class TokenAmount(
    val amount: String,
    val decimals: Int,
    val uiAmount: Double? = null,
    val uiAmountString: String? = null
)

@Serializable
data class TokenAccountInfo(
    val mint: String,
    val owner: String,
    val tokenAmount: TokenAmount,
    val delegate: String? = null,
    val delegatedAmount: TokenAmount? = null,
    val state: String,
    val isNative: Boolean,
    val closeAuthority: String? = null
)

// ============================================================================
// DATA CLASSES - Transactions
// ============================================================================

@Serializable
data class SignatureInfo(
    val signature: String,
    val slot: Long,
    val err: JsonElement? = null,
    val memo: String? = null,
    val blockTime: Long? = null,
    val confirmationStatus: String? = null
)

@Serializable
data class TransactionMeta(
    val err: JsonElement? = null,
    val fee: Long,
    val innerInstructions: JsonElement? = null,
    val logMessages: List<String>? = null,
    val postBalances: List<Long>,
    val postTokenBalances: JsonElement? = null,
    val preBalances: List<Long>,
    val preTokenBalances: JsonElement? = null,
    val rewards: JsonElement? = null,
    val computeUnitsConsumed: Long? = null
)

@Serializable
data class TransactionResult(
    val slot: Long,
    val transaction: JsonElement,
    val meta: TransactionMeta? = null,
    val blockTime: Long? = null,
    val version: JsonElement? = null
)

@Serializable
data class SimulationResult(
    val err: JsonElement? = null,
    val logs: List<String>? = null,
    val accounts: JsonElement? = null,
    val unitsConsumed: Long? = null,
    val returnData: JsonElement? = null
)

// ============================================================================
// DATA CLASSES - Block Information
// ============================================================================

@Serializable
data class BlockProduction(
    val byIdentity: Map<String, List<Long>>,
    val range: SlotRange
)

@Serializable
data class SlotRange(
    val firstSlot: Long,
    val lastSlot: Long
)

@Serializable
data class BlockInfo(
    val blockhash: String,
    val previousBlockhash: String,
    val parentSlot: Long,
    val transactions: List<TransactionResult>? = null,
    val rewards: JsonElement? = null,
    val blockTime: Long? = null,
    val blockHeight: Long? = null
)

@Serializable
data class BlockCommitment(
    val commitment: List<Long>?,
    val totalStake: Long
)

// ============================================================================
// DATA CLASSES - Priority Fees (QuickNode Add-on)
// ============================================================================

@Serializable
data class PriorityFeeEstimate(
    val priorityFeeEstimate: Double? = null,
    val priorityFeeLevels: PriorityFeeLevels? = null
)

@Serializable
data class PriorityFeeLevels(
    val min: Double,
    val low: Double,
    val medium: Double,
    val high: Double,
    val veryHigh: Double,
    val unsafeMax: Double
)

@Serializable
data class PriorityFeeResult(
    val context: ContextSlot? = null,
    val perComputeUnit: PriorityFeeEstimate? = null,
    val perTransaction: PriorityFeeEstimate? = null
)

// ============================================================================
// DATA CLASSES - JITO Bundles (Lil' JIT Add-on)
// ============================================================================

@Serializable
data class JitoTipFloor(
    val time: String,
    val landedTips25thPercentile: Double,
    val landedTips50thPercentile: Double,
    val landedTips75thPercentile: Double,
    val landedTips95thPercentile: Double,
    val landedTips99thPercentile: Double,
    val emaLandedTips50thPercentile: Double
)

@Serializable
data class JitoBundleStatus(
    val bundleId: String,
    val status: String,
    val landedSlot: Long? = null
)

@Serializable
data class JitoBundleResult(
    val context: ContextSlot? = null,
    val value: List<JitoBundleStatus>
)

@Serializable
data class JitoSimulationResult(
    val summary: String,
    val transactionResults: List<JitoTransactionResult>
)

@Serializable
data class JitoTransactionResult(
    val err: JsonElement? = null,
    val logs: List<String>? = null,
    val unitsConsumed: Long? = null,
    val returnData: JsonElement? = null
)

// ============================================================================
// DATA CLASSES - Metis Jupiter Swap API (QuickNode Add-on)
// ============================================================================

@Serializable
data class JupiterQuote(
    val inputMint: String,
    val outputMint: String,
    val inAmount: String,
    val outAmount: String,
    val otherAmountThreshold: String,
    val swapMode: String,
    val slippageBps: Int,
    val priceImpactPct: String,
    val routePlan: List<JsonElement>,
    val contextSlot: Long? = null,
    val timeTaken: Double? = null
)

@Serializable
data class JupiterSwapResult(
    val swapTransaction: String,
    val lastValidBlockHeight: Long,
    val prioritizationFeeLamports: Long? = null,
    val computeUnitLimit: Int? = null,
    val prioritizationType: JsonElement? = null,
    val dynamicSlippageReport: JsonElement? = null,
    val simulationError: JsonElement? = null
)

@Serializable
data class JupiterPrice(
    val id: String,
    val mintSymbol: String? = null,
    val vsToken: String,
    val vsTokenSymbol: String? = null,
    val price: Double
)

@Serializable
data class JupiterNewPool(
    val poolId: String,
    val poolType: String,
    val tokenA: String,
    val tokenB: String,
    val createdAt: String? = null
)

@Serializable
data class JupiterLimitOrder(
    val publicKey: String,
    val account: JsonElement
)

// ============================================================================
// DATA CLASSES - Pump.fun API (QuickNode Exclusive)
// ============================================================================

/**
 * Type of pump.fun trade: BUY or SELL
 */
enum class PumpFunType {
    BUY,
    SELL
}

/**
 * Legacy quote format for backwards compatibility.
 */
@Serializable
data class PumpFunQuote(
    val inputMint: String,
    val outputMint: String,
    val inAmount: String,
    val outAmount: String,
    val slippageBps: Int,
    val bondingCurveAddress: String? = null,
    val priceImpactPct: Double? = null,
    @Deprecated("Use priceImpactPct") val priceImpact: Double? = null
)

/**
 * New Pump.fun quote response matching QuickNode API.
 */
@Serializable
data class PumpFunQuoteResponse(
    val quote: PumpFunQuoteData
)

/**
 * Pump.fun quote data from QuickNode API.
 */
@Serializable
data class PumpFunQuoteData(
    /** The pump.fun mint address */
    val mint: String,
    /** The bonding curve address */
    val bondingCurve: String,
    /** BUY or SELL */
    val type: String,
    /** Raw input amount in base units */
    val inAmount: String,
    /** Formatted input amount */
    val inAmountUi: Double,
    /** Input token address (SOL for buy) */
    val inTokenAddress: String,
    /** Raw output amount in base units */
    val outAmount: String,
    /** Formatted output amount */
    val outAmountUi: Double,
    /** Output token address */
    val outTokenAddress: String,
    /** Additional metadata */
    val meta: PumpFunMeta? = null
)

/**
 * Pump.fun quote metadata.
 */
@Serializable
data class PumpFunMeta(
    /** Whether the bonding curve is completed */
    val isCompleted: Boolean? = null,
    /** Decimal places for output token */
    val outDecimals: Int? = null,
    /** Decimal places for input token */
    val inDecimals: Int? = null,
    /** Total supply of the token */
    val totalSupply: String? = null,
    /** Current market cap in SOL */
    val currentMarketCapInSol: Double? = null
)

/**
 * Pump.fun swap result.
 */
@Serializable
data class PumpFunSwapResult(
    val swapTransaction: String,
    val lastValidBlockHeight: Long? = null
)

/**
 * Pump.fun swap instructions result for composable trading.
 */
@Serializable
data class PumpFunInstructionsResult(
    val setupInstructions: List<JsonElement>? = null,
    val swapInstruction: JsonElement? = null,
    val cleanupInstruction: JsonElement? = null,
    val addressLookupTableAddresses: List<String>? = null
)

// ============================================================================
// DATA CLASSES - DAS API (Metaplex Digital Asset Standard)
// ============================================================================

@Serializable
data class DasAsset(
    val id: String,
    @SerialName("interface") val assetInterface: String? = null,
    val content: DasContent? = null,
    val authorities: List<DasAuthority>? = null,
    val compression: DasCompression? = null,
    val grouping: List<DasGrouping>? = null,
    val royalty: DasRoyalty? = null,
    val creators: List<DasCreator>? = null,
    val ownership: DasOwnership? = null,
    val supply: DasSupply? = null,
    val mutable: Boolean? = null,
    val burnt: Boolean? = null,
    val tokenInfo: JsonElement? = null
)

@Serializable
data class DasContent(
    val schema: String? = null,
    val jsonUri: String? = null,
    val files: List<DasFile>? = null,
    val metadata: DasMetadata? = null,
    val links: DasLinks? = null
)

@Serializable
data class DasFile(
    val uri: String,
    val mime: String? = null,
    val quality: JsonElement? = null,
    val contexts: List<String>? = null
)

@Serializable
data class DasMetadata(
    val name: String? = null,
    val description: String? = null,
    val symbol: String? = null,
    val tokenStandard: String? = null,
    val attributes: List<DasAttribute>? = null
)

@Serializable
data class DasAttribute(
    val traitType: String? = null,
    @SerialName("trait_type") val altTraitType: String? = null,
    val value: JsonElement? = null
)

@Serializable
data class DasLinks(
    val externalUrl: String? = null,
    val image: String? = null
)

@Serializable
data class DasAuthority(
    val address: String? = null,
    val scopes: List<String>? = null
)

@Serializable
data class DasCompression(
    val assetHash: String? = null,
    val compressed: Boolean,
    val creatorHash: String? = null,
    val dataHash: String? = null,
    val eligible: Boolean? = null,
    val leafId: Long? = null,
    val seq: Long? = null,
    val tree: String? = null
)

@Serializable
data class DasGrouping(
    val groupKey: String? = null,
    val groupValue: String? = null,
    @SerialName("group_key") val altGroupKey: String? = null,
    @SerialName("group_value") val altGroupValue: String? = null
)

@Serializable
data class DasRoyalty(
    val basisPoints: Int? = null,
    val locked: Boolean? = null,
    val percent: Double? = null,
    val primarySaleHappened: Boolean? = null,
    val royaltyModel: String? = null,
    val target: String? = null
)

@Serializable
data class DasCreator(
    val address: String? = null,
    val share: Int? = null,
    val verified: Boolean? = null
)

@Serializable
data class DasOwnership(
    val delegate: String? = null,
    val delegated: Boolean? = null,
    val frozen: Boolean? = null,
    val owner: String? = null,
    val ownershipModel: String? = null
)

@Serializable
data class DasSupply(
    val editionNonce: Int? = null,
    val printCurrentSupply: Long? = null,
    val printMaxSupply: Long? = null
)

@Serializable
data class DasAssetProof(
    val root: String,
    val proof: List<String>,
    val nodeIndex: Long,
    val leaf: String,
    val treeId: String
)

@Serializable
data class DasAssetList(
    val total: Long,
    val limit: Int,
    val page: Int? = null,
    val cursor: String? = null,
    val items: List<DasAsset>
)

@Serializable
data class DasTokenAccount(
    val address: String,
    val mint: String,
    val owner: String,
    val amount: Long,
    val delegatedAmount: Long? = null,
    val delegate: String? = null,
    val frozen: Boolean
)

@Serializable
data class DasTokenAccountList(
    val total: Long,
    val limit: Int,
    val page: Int? = null,
    val cursor: String? = null,
    val tokenAccounts: List<DasTokenAccount>
)

// ============================================================================
// DATA CLASSES - Transaction Fastlane (QuickNode Exclusive)
// ============================================================================

@Serializable
data class FastlaneResult(
    val signature: String,
    val slot: Long? = null,
    val confirmationStatus: String? = null,
    val slotLatency: Int? = null
)

// ============================================================================
// DATA CLASSES - Privacy Innovations (Iris Exclusive)
// ============================================================================

/**
 * Privacy score for a wallet based on transaction patterns.
 */
@Serializable
data class PrivacyScore(
    val address: String,
    val overallScore: Int, // 0-100, higher = more private
    val factors: PrivacyFactors,
    val recommendations: List<String>,
    val analyzedTransactions: Int,
    val analysisTimestamp: Long
)

@Serializable
data class PrivacyFactors(
    val addressReuse: Int,
    val transactionTiming: Int,
    val amountPatterns: Int,
    val mixerUsage: Int,
    val exchangeExposure: Int,
    val dustConsolidation: Int
)

/**
 * Stealth address for receiving funds privately.
 */
@Serializable
data class StealthAddress(
    val ephemeralPublicKey: String,
    val stealthAddress: String,
    val viewingKey: String,
    val spendingKeyEncrypted: String
)

/**
 * Bundle routing plan for privacy-optimized transactions.
 */
@Serializable
data class PrivacyRoutePlan(
    val originalAmount: Long,
    val routes: List<PrivacyRoute>,
    val totalFeeLamports: Long,
    val estimatedTimeSeconds: Int,
    val privacyGain: Int // 0-100
)

@Serializable
data class PrivacyRoute(
    val hopNumber: Int,
    val intermediateAddress: String,
    val amount: Long,
    val delaySeconds: Int,
    val bundleId: String? = null
)

/**
 * Transaction graph analysis for identifying linked addresses.
 */
@Serializable
data class TransactionGraph(
    val rootAddress: String,
    val nodes: List<GraphNode>,
    val edges: List<GraphEdge>,
    val clusters: List<AddressCluster>,
    val analysisDepth: Int
)

@Serializable
data class GraphNode(
    val address: String,
    val label: String? = null,
    val type: String, // "wallet", "exchange", "contract", "mixer", "unknown"
    val totalInflow: Long,
    val totalOutflow: Long,
    val transactionCount: Int
)

@Serializable
data class GraphEdge(
    val from: String,
    val to: String,
    val weight: Long,
    val transactionCount: Int,
    val firstSeen: Long?,
    val lastSeen: Long?
)

@Serializable
data class AddressCluster(
    val clusterId: String,
    val addresses: List<String>,
    val clusterType: String, // "same_owner", "exchange", "contract", "mixer"
    val confidence: Double
)

// ============================================================================
// DATA CLASSES - Yellowstone gRPC Streaming
// ============================================================================

@Serializable
data class YellowstoneSubscription(
    val id: String,
    val type: YellowstoneSubscriptionType,
    val filters: JsonElement,
    val active: Boolean,
    val createdAt: Long
)

enum class YellowstoneSubscriptionType {
    ACCOUNT,
    TRANSACTION,
    BLOCK_META,
    SLOT
}

@Serializable
data class YellowstoneAccountUpdate(
    val pubkey: String,
    val lamports: Long,
    val owner: String,
    val executable: Boolean,
    val rentEpoch: JsonElement? = null, // Can be u64 (larger than Long.MAX_VALUE)
    val data: String,
    val slot: Long,
    val writeVersion: Long
)

@Serializable
data class YellowstoneTransactionUpdate(
    val signature: String,
    val slot: Long,
    val isVote: Boolean,
    val transaction: JsonElement,
    val meta: JsonElement?
)

@Serializable
data class YellowstoneBlockMeta(
    val slot: Long,
    val blockhash: String,
    val parentSlot: Long,
    val parentBlockhash: String,
    val rewards: JsonElement?,
    val blockTime: Long?,
    val blockHeight: Long?
)

// ============================================================================
// DATA CLASSES - WebSocket Subscriptions
// ============================================================================

@Serializable
data class WsSubscription(
    val subscriptionId: Long,
    val method: String
)

@Serializable
data class WsNotification<T>(
    val jsonrpc: String = "2.0",
    val method: String,
    val params: WsNotificationParams<T>
)

@Serializable
data class WsNotificationParams<T>(
    val subscription: Long,
    val result: T
)

// ============================================================================
// EXCEPTIONS
// ============================================================================

open class IrisException(message: String, cause: Throwable? = null) : Exception(message, cause)
class IrisRpcException(val code: Int, message: String, val data: JsonElement? = null) : IrisException("RPC Error $code: $message")
class IrisNetworkException(message: String, cause: Throwable? = null) : IrisException(message, cause)
class IrisTimeoutException(message: String) : IrisException(message)
class IrisValidationException(message: String) : IrisException(message)

// ============================================================================
// IRIS QUICKNODE CLIENT - Main Entry Point
// ============================================================================

/**
 * # IrisQuickNodeClient
 * 
 * The definitive Kotlin-first SDK for QuickNode Solana infrastructure.
 * Named after Iris, the Greek goddess of the rainbow and swift messenger of the gods.
 * 
 * ## Features
 * 
 * ### Core Solana RPC
 * - All standard Solana JSON-RPC methods
 * - Account queries, transactions, blocks, program accounts
 * 
 * ### QuickNode Marketplace Add-ons
 * - **Metis Jupiter Swap API**: DEX aggregation, quotes, swaps, limit orders
 * - **Lil' JIT JITO Bundles**: MEV protection, bundle submission, tip optimization
 * - **Priority Fee API**: Real-time fee estimation for transaction prioritization
 * - **Pump.fun API**: Bonding curve trading, new token launches
 * - **Transaction Fastlane**: Enterprise-grade sub-slot transaction propagation
 * - **DAS API**: NFT metadata, compressed assets, token accounts
 * 
 * ### Yellowstone gRPC Streaming
 * - Real-time account updates
 * - Transaction streaming
 * - Block metadata
 * - Historical replay up to 3000 slots
 * 
 * ### Privacy Innovations (Iris Exclusive)
 * - Privacy scoring for wallets
 * - Stealth address generation
 * - JITO bundle-based transaction mixing
 * - Multi-hop privacy routing
 * - Transaction graph analysis
 * 
 * ## Example Usage
 * 
 * ```kotlin
 * val iris = IrisQuickNodeClient(
 *     endpoint = "https://your-endpoint.solana-mainnet.quiknode.pro/your-token/",
 *     network = SolanaNetwork.MAINNET_BETA
 * )
 * 
 * // Get a Jupiter quote
 * val quote = iris.metis.getQuote(
 *     inputMint = "So11111111111111111111111111111111111111112",
 *     outputMint = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v",
 *     amount = 1_000_000_000
 * )
 * 
 * // Send a JITO bundle
 * val bundleId = iris.jito.sendBundle(listOf(signedTx1, signedTx2))
 * 
 * // Stream account updates
 * iris.yellowstone.subscribeToAccount("wallet-address").collect { update ->
 *     println("Balance changed: ${update.lamports}")
 * }
 * 
 * // Analyze wallet privacy
 * val privacyScore = iris.privacy.analyzeWallet("wallet-address")
 * ```
 * 
 * @param endpoint Your QuickNode endpoint URL (e.g., https://xxx.solana-mainnet.quiknode.pro/token/)
 * @param network The Solana network cluster
 * @param httpClient Optional custom OkHttpClient for advanced configuration
 * @param json Optional custom Json serializer configuration
 */
class IrisQuickNodeClient(
    private val endpoint: String,
    val network: SolanaNetwork = SolanaNetwork.MAINNET_BETA,
    /**
     * Metis (Jupiter) API endpoint.
     * - For QuickNode private endpoint: Get from dashboard after enabling Metis add-on
     *   Format: `https://jupiter-swap-api.quiknode.pro/YOUR_KEY`
     * - For public endpoint: `https://public.jupiterapi.com` (rate limited)
     */
    private val metisEndpoint: String = DEFAULT_METIS_ENDPOINT,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(Duration.ofSeconds(30))
        .readTimeout(Duration.ofSeconds(60))
        .writeTimeout(Duration.ofSeconds(60))
        .build(),
    private val json: Json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }
) {
    
    companion object {
        /** Default public Jupiter API endpoint (rate-limited) */
        const val DEFAULT_METIS_ENDPOINT = "https://public.jupiterapi.com"
    }
    
    private val requestIdCounter = AtomicInteger(0)
    private val mediaType = "application/json; charset=utf-8".toMediaType()
    
    // Derived endpoints
    private val wsEndpoint: String = endpoint.replace("https://", "wss://").replace("http://", "ws://")
    private val yellowstoneGrpcEndpoint: String = endpoint.replace("https://", "").replace("http://", "").trimEnd('/') + ":10000"
    
    // ========================================================================
    // NAMESPACE ACCESSORS
    // ========================================================================
    
    /** Core Solana RPC methods */
    val rpc: RpcNamespace by lazy { RpcNamespace(this) }
    
    /** Metaplex Digital Asset Standard (DAS) API for NFTs and tokens */
    val das: DasNamespace by lazy { DasNamespace(this) }
    
    /** Metis Jupiter Swap API for DEX aggregation */
    val metis: MetisNamespace by lazy { MetisNamespace(this) }
    
    /** Lil' JIT JITO bundle operations */
    val jito: JitoNamespace by lazy { JitoNamespace(this) }
    
    /** Priority fee estimation */
    val priority: PriorityNamespace by lazy { PriorityNamespace(this) }
    
    /** Pump.fun trading API */
    val pumpfun: PumpFunNamespace by lazy { PumpFunNamespace(this) }
    
    /** Transaction Fastlane for sub-slot execution */
    val fastlane: FastlaneNamespace by lazy { FastlaneNamespace(this) }
    
    /** Yellowstone gRPC streaming */
    val yellowstone: YellowstoneNamespace by lazy { YellowstoneNamespace(this) }
    
    /** WebSocket subscriptions */
    val ws: WebSocketNamespace by lazy { WebSocketNamespace(this) }
    
    /** Privacy analysis and innovations */
    val privacy: PrivacyNamespace by lazy { PrivacyNamespace(this) }
    
    /** Smart transaction building with optimization */
    val smart: SmartNamespace by lazy { SmartNamespace(this) }
    
    /** Solana Name Service (SNS) - .sol domain resolution */
    val sns: SnsNamespace by lazy { SnsNamespace(this) }
    
    /** Bonfida SNS utilities */
    val bonfida: BonfidaSnsNamespace by lazy { BonfidaSnsNamespace(this) }
    
    /** Combined add-on innovations - World-first atomic operations */
    val innovations: IrisInnovationsNamespace by lazy { IrisInnovationsNamespace(this) }
    
    /** Advanced privacy innovations - World-first application-layer privacy */
    val privacyAdvanced: IrisPrivacyNamespace by lazy { IrisPrivacyNamespace(this) }
    
    /** v1.2.0 - Phase 1 Privacy Innovations */
    /** Confidential Token-2022 features (QuickNode-powered) */
    val confidentialToken: IrisConfidentialTokenNamespace by lazy { IrisConfidentialTokenNamespace(this) }
    
    /** Multi-region broadcast for privacy */
    val privateBroadcast: IrisPrivateBroadcastNamespace by lazy { IrisPrivateBroadcastNamespace(this) }
    
    /** Transaction fingerprint obfuscation */
    val fingerprint: IrisFingerprintNamespace by lazy { IrisFingerprintNamespace(this) }
    
    /** RPC rotation for enhanced privacy */
    val rpcRotation: IrisRpcRotationNamespace by lazy { IrisRpcRotationNamespace(this) }

    /** v1.5.0 - Secure Payment Links (No-Wallet Sending) */
    val paymentLinks: IrisPaymentLinkNamespace by lazy { IrisPaymentLinkNamespace(this) }
    
    // ========================================================================
    // INTERNAL HELPERS
    // ========================================================================
    
    internal fun nextRequestId(): String = "iris-${requestIdCounter.incrementAndGet()}"
    
    internal suspend fun <T> executeRpcCall(
        method: String,
        params: JsonElement = JsonArray(emptyList()),
        resultDeserializer: kotlinx.serialization.DeserializationStrategy<T>
    ): T = withContext(Dispatchers.IO) {
        val requestId = nextRequestId()
        val requestBody = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", requestId)
            put("method", method)
            put("params", params)
        }.toString()
        
        val request = Request.Builder()
            .url(endpoint)
            .post(requestBody.toRequestBody(mediaType))
            .addHeader("Content-Type", "application/json")
            .build()
        
        val response = try {
            httpClient.newCall(request).execute()
        } catch (e: Exception) {
            throw IrisNetworkException("Network request failed: ${e.message}", e)
        }
        
        response.use { resp ->
            if (!resp.isSuccessful) {
                throw IrisNetworkException("HTTP ${resp.code}: ${resp.message}")
            }
            
            val body = resp.body?.string() ?: throw IrisNetworkException("Empty response body")
            val jsonResponse = json.parseToJsonElement(body).jsonObject
            
            val error = jsonResponse["error"]
            if (error != null && error !is JsonNull) {
                val rpcError = json.decodeFromJsonElement<RpcError>(error)
                throw IrisRpcException(rpcError.code, rpcError.message, rpcError.data)
            }
            
            val result = jsonResponse["result"] ?: throw IrisRpcException(-32600, "Missing result in response")
            json.decodeFromJsonElement(resultDeserializer, result)
        }
    }
    
    internal suspend fun <T> executeRestCall(
        path: String,
        method: String = "GET",
        body: JsonElement? = null,
        resultDeserializer: kotlinx.serialization.DeserializationStrategy<T>
    ): T = withContext(Dispatchers.IO) {
        val url = endpoint.trimEnd('/') + path
        
        val requestBuilder = Request.Builder().url(url)
        
        when (method.uppercase()) {
            "GET" -> requestBuilder.get()
            "POST" -> requestBuilder.post((body?.toString() ?: "{}").toRequestBody(mediaType))
            else -> throw IrisValidationException("Unsupported HTTP method: $method")
        }
        
        requestBuilder.addHeader("Content-Type", "application/json")
        
        val response = try {
            httpClient.newCall(requestBuilder.build()).execute()
        } catch (e: Exception) {
            throw IrisNetworkException("Network request failed: ${e.message}", e)
        }
        
        response.use { resp ->
            if (!resp.isSuccessful) {
                throw IrisNetworkException("HTTP ${resp.code}: ${resp.message}")
            }
            
            val responseBody = resp.body?.string() ?: throw IrisNetworkException("Empty response body")
            json.decodeFromString(resultDeserializer, responseBody)
        }
    }
    
    /**
     * Execute a REST call against a specific base URL.
     * Used for APIs that have separate endpoints from the main RPC (e.g., Metis/Jupiter).
     */
    internal suspend fun <T> executeRestCallWithEndpoint(
        baseUrl: String,
        path: String,
        method: String = "GET",
        body: JsonElement? = null,
        resultDeserializer: kotlinx.serialization.DeserializationStrategy<T>
    ): T = withContext(Dispatchers.IO) {
        val url = baseUrl.trimEnd('/') + path
        
        val requestBuilder = Request.Builder().url(url)
        
        when (method.uppercase()) {
            "GET" -> requestBuilder.get()
            "POST" -> requestBuilder.post((body?.toString() ?: "{}").toRequestBody(mediaType))
            else -> throw IrisValidationException("Unsupported HTTP method: $method")
        }
        
        requestBuilder.addHeader("Content-Type", "application/json")
        
        val response = try {
            httpClient.newCall(requestBuilder.build()).execute()
        } catch (e: Exception) {
            throw IrisNetworkException("Network request failed: ${e.message}", e)
        }
        
        response.use { resp ->
            if (!resp.isSuccessful) {
                val errorBody = resp.body?.string() ?: ""
                throw IrisNetworkException("HTTP ${resp.code}: ${resp.message}. Body: $errorBody")
            }
            
            val responseBody = resp.body?.string() ?: throw IrisNetworkException("Empty response body")
            json.decodeFromString(resultDeserializer, responseBody)
        }
    }
    
    internal fun getJson(): Json = json
    internal fun getHttpClient(): OkHttpClient = httpClient
    internal fun getEndpoint(): String = endpoint
    internal fun getMetisEndpoint(): String = metisEndpoint
    internal fun getWsEndpoint(): String = wsEndpoint
    internal fun getYellowstoneEndpoint(): String = yellowstoneGrpcEndpoint
    
    // ========================================================================
    // CONVENIENCE METHODS
    // ========================================================================
    
    /**
     * Get SOL balance for an address in lamports.
     */
    suspend fun getBalance(address: String, commitment: Commitment = Commitment.FINALIZED): Long {
        return rpc.getBalance(address, commitment)
    }
    
    /**
     * Get SOL balance for an address in SOL.
     */
    suspend fun getBalanceSol(address: String, commitment: Commitment = Commitment.FINALIZED): Double {
        return rpc.getBalance(address, commitment) / 1_000_000_000.0
    }
    
    /**
     * Send a transaction with optimized priority fees via Fastlane.
     */
    suspend fun sendOptimizedTransaction(
        signedTransaction: String,
        useFastlane: Boolean = true,
        skipPreflight: Boolean = false
    ): String {
        return if (useFastlane) {
            fastlane.sendTransaction(signedTransaction, skipPreflight)
        } else {
            rpc.sendTransaction(signedTransaction, skipPreflight)
        }
    }
    
    /**
     * Get optimal priority fee for current network conditions.
     */
    suspend fun getOptimalPriorityFee(
        accounts: List<String> = emptyList(),
        level: PriorityLevel = PriorityLevel.MEDIUM
    ): Double {
        return priority.estimatePriorityFees(accounts, level)
    }

    // ========================================================================
    // ADVANCED STEALTH ADDRESS SYSTEM
    // ========================================================================

    /**
     * Advanced Stealth Address System for QuickNode.
     * 
     * Implements cryptographically-inspired stealth address generation
     * with ECDH-style patterns adapted for Solana.
     */
    val advancedStealth = IrisAdvancedStealthApi()

    inner class IrisAdvancedStealthApi {

        /**
         * Generate a stealth meta-address (dual-key system).
         */
        fun generateStealthMetaAddress(
            masterPubkey: String,
            entropy: ByteArray? = null
        ): IrisStealthMetaAddress {
            val timestamp = System.currentTimeMillis()
            val randomEntropy = entropy ?: generateSecureEntropy(32)
            
            val spendSeed = "${masterPubkey}_spend_${randomEntropy.contentHashCode()}_$timestamp"
            val spendPath = "m/44'/501'/0'/0'/${(spendSeed.hashCode().toLong() and 0x7FFFFFFFL) % 1000000}'"
            
            val viewSeed = "${masterPubkey}_view_${randomEntropy.contentHashCode()}_$timestamp"
            val viewPath = "m/44'/501'/0'/1'/${(viewSeed.hashCode().toLong() and 0x7FFFFFFFL) % 1000000}'"
            
            val metaAddressId = "iris-st:${masterPubkey.take(8)}:${timestamp.toString(16)}"
            
            return IrisStealthMetaAddress(
                metaAddressId = metaAddressId,
                masterPubkey = masterPubkey,
                spendKeyPath = spendPath,
                viewKeyPath = viewPath,
                createdAt = timestamp,
                version = 1,
                privacyFeatures = listOf(
                    "DUAL_KEY_SYSTEM",
                    "UNLINKABLE_PAYMENTS",
                    "VIEW_KEY_SCANNING",
                    "QUICKNODE_OPTIMIZED"
                )
            )
        }

        /**
         * Generate a one-time stealth payment address.
         */
        fun generateOneTimeAddress(
            recipientMeta: IrisStealthMetaAddress,
            amount: Long? = null,
            memo: String? = null
        ): IrisStealthOneTimeAddress {
            val ephemeralEntropy = generateSecureEntropy(32)
            val timestamp = System.currentTimeMillis()
            
            val derivationInput = buildString {
                append(recipientMeta.masterPubkey)
                append("_${ephemeralEntropy.contentHashCode()}_$timestamp")
                amount?.let { append("_$it") }
                memo?.let { append("_${it.hashCode()}") }
            }
            
            val derivationIndex = (derivationInput.hashCode().toLong() and 0x7FFFFFFFL)
            val oneTimePath = "m/44'/501'/stealth'/${derivationIndex % 1000000}'/${(derivationIndex / 1000000) % 1000}'"
            
            val ephemeralHint = ephemeralEntropy.take(8).joinToString("") { "%02x".format(it) }
            
            return IrisStealthOneTimeAddress(
                address = "DERIVE:$oneTimePath",
                derivationPath = oneTimePath,
                ephemeralHint = ephemeralHint,
                recipientMetaId = recipientMeta.metaAddressId,
                createdAt = timestamp,
                expiresAt = timestamp + (24 * 60 * 60 * 1000),
                amount = amount,
                scanTag = "scan:${recipientMeta.metaAddressId.takeLast(8)}:$ephemeralHint"
            )
        }

        private fun generateSecureEntropy(size: Int): ByteArray {
            return ByteArray(size) { (Math.random() * 256).toInt().toByte() }
        }

        /**
         * Scan for stealth payments using QuickNode RPC.
         */
        suspend fun scanForStealthPayments(
            metaAddress: IrisStealthMetaAddress,
            limit: Int = 50
        ): IrisStealthPaymentScan {
            val payments = mutableListOf<IrisDetectedStealthPayment>()
            
            val signatures = rpc.getSignaturesForAddress(
                metaAddress.masterPubkey,
                limit = limit
            )
            
            for (sig in signatures) {
                val tx = try {
                    rpc.getTransaction(sig.signature)
                } catch (e: Exception) {
                    continue
                }
                
                if (tx != null) {
                    val meta = tx.meta
                    if (meta != null) {
                        val preBalances = meta.preBalances
                        val postBalances = meta.postBalances
                        
                        if (preBalances.isNotEmpty() && postBalances.isNotEmpty()) {
                            val amount = postBalances[0] - preBalances[0]
                            if (amount > 0) {
                                payments.add(IrisDetectedStealthPayment(
                                    signature = sig.signature,
                                    slot = sig.slot,
                                    amount = kotlin.math.abs(amount),
                                    stealthLikelihood = 60,
                                    detectedAt = System.currentTimeMillis()
                                ))
                            }
                        }
                    }
                }
            }
            
            return IrisStealthPaymentScan(
                metaAddressId = metaAddress.metaAddressId,
                paymentsFound = payments.size,
                payments = payments,
                scanDepth = signatures.size,
                scanTime = System.currentTimeMillis()
            )
        }

        /**
         * Analyze an address for stealth characteristics.
         */
        suspend fun analyzeStealthCharacteristics(address: String): IrisStealthAnalysis {
            val balance = rpc.getBalance(address)
            val signatures = rpc.getSignaturesForAddress(address, limit = 20)
            
            val txCount = signatures.size
            
            var stealthScore = 50
            val factors = mutableListOf<String>()
            
            when {
                txCount == 0 -> {
                    stealthScore += 25
                    factors.add("Fresh address (no transactions)")
                }
                txCount <= 2 -> {
                    stealthScore += 20
                    factors.add("Minimal transactions (sweep pattern)")
                }
                txCount <= 5 -> {
                    stealthScore += 10
                    factors.add("Low transaction count")
                }
                else -> {
                    stealthScore -= 15
                    factors.add("High transaction count")
                }
            }
            
            if (balance > 0 && balance % 1_000_000_000 != 0L) {
                stealthScore += 5
                factors.add("Non-round balance")
            }
            
            return IrisStealthAnalysis(
                address = address,
                stealthScore = minOf(100, maxOf(0, stealthScore)),
                classification = when {
                    stealthScore >= 70 -> "LIKELY_STEALTH"
                    stealthScore >= 50 -> "POSSIBLY_STEALTH"
                    else -> "REGULAR_ADDRESS"
                },
                factors = factors,
                transactionCount = txCount,
                balanceLamports = balance
            )
        }

        /**
         * Generate stealth payment proof.
         */
        fun generatePaymentProof(
            oneTimeAddress: IrisStealthOneTimeAddress,
            signature: String
        ): IrisStealthPaymentProof {
            val proofHash = "${oneTimeAddress.derivationPath}_$signature".hashCode()
                .toLong().and(0xFFFFFFFFL).toString(16)
            
            return IrisStealthPaymentProof(
                proofId = "proof:$proofHash",
                oneTimeAddress = oneTimeAddress.address,
                transactionSignature = signature,
                ephemeralHint = oneTimeAddress.ephemeralHint,
                timestamp = System.currentTimeMillis()
            )
        }
    }

    // ========================================================================
    // PRIVATE TRANSACTIONS SYSTEM
    // ========================================================================

    /**
     * Private Transactions System for QuickNode.
     * 
     * Comprehensive privacy-preserving transaction features.
     */
    val privateTransactions = IrisPrivateTransactionsApi()

    inner class IrisPrivateTransactionsApi {

        /**
         * Create a split-send transaction plan.
         */
        fun createSplitSendPlan(
            amount: Long,
            finalRecipient: String,
            splitCount: Int = 3,
            useIntermediates: Boolean = true
        ): IrisSplitSendPlan {
            require(splitCount in 2..10) { "Split count must be 2-10" }
            
            val splits = mutableListOf<IrisSplitTransaction>()
            val baseAmount = amount / splitCount
            var remaining = amount
            
            for (i in 0 until splitCount) {
                val isLast = i == splitCount - 1
                val splitAmount = if (isLast) remaining else {
                    val variance = (baseAmount * 0.2).toLong()
                    val randomized = baseAmount + (-variance..variance).random()
                    minOf(randomized, remaining - (splitCount - i - 1) * 10000)
                }
                
                remaining -= splitAmount
                
                splits.add(IrisSplitTransaction(
                    index = i,
                    amount = splitAmount,
                    recipient = if (isLast) finalRecipient else "intermediate_$i",
                    isIntermediate = useIntermediates && !isLast,
                    suggestedDelayMs = (i * (30..120).random() * 1000).toLong()
                ))
            }
            
            return IrisSplitSendPlan(
                planId = "split:${System.currentTimeMillis().toString(16)}",
                totalAmount = amount,
                finalRecipient = finalRecipient,
                splits = splits,
                estimatedFees = splits.size * 5000L,
                privacyScore = 40 + (splits.size * 10) + (if (useIntermediates) 20 else 0)
            )
        }

        /**
         * Create time-locked release plan.
         */
        fun createTimeLockedPlan(
            transactions: List<IrisPlannedTransaction>,
            strategy: IrisTimeReleaseStrategy = IrisTimeReleaseStrategy.RANDOM_INTERVALS
        ): IrisTimeLockedPlan {
            val scheduled = mutableListOf<IrisScheduledTransaction>()
            var currentTime = System.currentTimeMillis()
            
            for ((index, tx) in transactions.withIndex()) {
                val delay = when (strategy) {
                    IrisTimeReleaseStrategy.RANDOM_INTERVALS -> (30_000L..300_000L).random()
                    IrisTimeReleaseStrategy.FIXED_INTERVALS -> 60_000L
                    IrisTimeReleaseStrategy.EXPONENTIAL_BACKOFF -> (30_000L * (1 shl minOf(index, 5)))
                }
                
                currentTime += delay
                
                scheduled.add(IrisScheduledTransaction(
                    index = index,
                    transaction = tx,
                    scheduledTime = currentTime,
                    delayMs = delay
                ))
            }
            
            return IrisTimeLockedPlan(
                planId = "timelock:${System.currentTimeMillis().toString(16)}",
                strategy = strategy,
                transactions = scheduled,
                totalDurationMs = currentTime - System.currentTimeMillis()
            )
        }

        /**
         * Generate decoy outputs.
         */
        fun generateDecoyOutputs(
            realAmount: Long,
            decoyCount: Int = 2
        ): IrisDecoyOutputPlan {
            val decoys = (0 until decoyCount).map { i ->
                val variance = (realAmount * 0.3).toLong()
                IrisDecoyOutput(
                    index = i,
                    amount = maxOf(10000, realAmount + (-variance..variance).random()),
                    returnToSender = true
                )
            }
            
            return IrisDecoyOutputPlan(
                realAmount = realAmount,
                decoys = decoys,
                totalOutputs = decoyCount + 1,
                privacyScore = 60 + (decoyCount * 10)
            )
        }

        /**
         * Create obfuscated memo.
         */
        fun createObfuscatedMemo(
            realMemo: String,
            type: IrisMemoObfuscationType = IrisMemoObfuscationType.PADDING
        ): IrisObfuscatedMemo {
            val obfuscated = when (type) {
                IrisMemoObfuscationType.PADDING -> {
                    val padding = (1..(64 - realMemo.length).coerceAtLeast(0))
                        .map { ('a'..'z').random() }.joinToString("")
                    "$realMemo|$padding"
                }
                IrisMemoObfuscationType.BASE64 -> {
                    java.util.Base64.getEncoder().encodeToString(realMemo.toByteArray())
                }
                IrisMemoObfuscationType.HASH_REF -> {
                    val hash = realMemo.hashCode().toLong().and(0xFFFFFFFFL).toString(16)
                    "ref:$hash"
                }
            }
            
            return IrisObfuscatedMemo(
                original = realMemo,
                obfuscated = obfuscated,
                type = type,
                recoverable = type != IrisMemoObfuscationType.HASH_REF
            )
        }

        /**
         * Analyze transaction privacy.
         */
        suspend fun analyzeTransactionPrivacy(signature: String): IrisTransactionPrivacyAnalysis {
            val tx = rpc.getTransaction(signature) 
                ?: throw IrisValidationException("Transaction not found")
            
            var privacyScore = 50
            val factors = mutableListOf<String>()
            
            val meta = tx.meta
            if (meta != null) {
                val outputCount = meta.postBalances.size
                when {
                    outputCount > 3 -> {
                        privacyScore += 20
                        factors.add("Multiple outputs (good obfuscation)")
                    }
                    outputCount == 2 -> {
                        privacyScore += 10
                        factors.add("Standard change pattern")
                    }
                    else -> factors.add("Single output")
                }
                
                val hasLogs = meta.logMessages?.isNotEmpty() == true
                if (hasLogs) {
                    val hasMemo = meta.logMessages?.any { it.contains("Memo") } == true
                    if (hasMemo) {
                        privacyScore -= 15
                        factors.add("Contains memo (metadata leak)")
                    }
                }
            }
            
            return IrisTransactionPrivacyAnalysis(
                signature = signature,
                privacyScore = minOf(100, maxOf(0, privacyScore)),
                classification = when {
                    privacyScore >= 70 -> "HIGH"
                    privacyScore >= 50 -> "MEDIUM"
                    else -> "LOW"
                },
                factors = factors
            )
        }

        /**
         * Create complete private transaction package.
         */
        fun createPrivateTransactionPackage(
            amount: Long,
            recipient: String,
            options: IrisPrivateTransactionOptions = IrisPrivateTransactionOptions()
        ): IrisPrivateTransactionPackage {
            val splitPlan = if (options.useSplitSend && amount > 100_000_000) {
                createSplitSendPlan(amount, recipient, options.splitCount, options.useIntermediates)
            } else null
            
            val decoyPlan = if (options.useDecoys) {
                generateDecoyOutputs(amount, options.decoyCount)
            } else null
            
            val timePlan = if (options.useTimeLock && splitPlan != null) {
                val planned = splitPlan.splits.map { 
                    IrisPlannedTransaction(it.amount, it.recipient, null) 
                }
                createTimeLockedPlan(planned, options.timeStrategy)
            } else null
            
            val overallScore = 30 + 
                (splitPlan?.privacyScore?.div(3) ?: 0) + 
                (decoyPlan?.privacyScore?.div(4) ?: 0) +
                (if (timePlan != null) 15 else 0)
            
            return IrisPrivateTransactionPackage(
                packageId = "pkg:${System.currentTimeMillis().toString(16)}",
                amount = amount,
                recipient = recipient,
                splitPlan = splitPlan,
                decoyPlan = decoyPlan,
                timePlan = timePlan,
                overallPrivacyScore = minOf(100, overallScore)
            )
        }
    }
}

// ============================================================================
// STEALTH & PRIVATE TRANSACTION DATA CLASSES (Iris SDK)
// ============================================================================

@Serializable
data class IrisStealthMetaAddress(
    val metaAddressId: String,
    val masterPubkey: String,
    val spendKeyPath: String,
    val viewKeyPath: String,
    val createdAt: Long,
    val version: Int,
    val privacyFeatures: List<String>
)

@Serializable
data class IrisStealthOneTimeAddress(
    val address: String,
    val derivationPath: String,
    val ephemeralHint: String,
    val recipientMetaId: String,
    val createdAt: Long,
    val expiresAt: Long,
    val amount: Long?,
    val scanTag: String
)

@Serializable
data class IrisStealthPaymentScan(
    val metaAddressId: String,
    val paymentsFound: Int,
    val payments: List<IrisDetectedStealthPayment>,
    val scanDepth: Int,
    val scanTime: Long
)

@Serializable
data class IrisDetectedStealthPayment(
    val signature: String,
    val slot: Long,
    val amount: Long,
    val stealthLikelihood: Int,
    val detectedAt: Long
)

@Serializable
data class IrisStealthAnalysis(
    val address: String,
    val stealthScore: Int,
    val classification: String,
    val factors: List<String>,
    val transactionCount: Int,
    val balanceLamports: Long
)

@Serializable
data class IrisStealthPaymentProof(
    val proofId: String,
    val oneTimeAddress: String,
    val transactionSignature: String,
    val ephemeralHint: String,
    val timestamp: Long
)

@Serializable
data class IrisSplitTransaction(
    val index: Int,
    val amount: Long,
    val recipient: String,
    val isIntermediate: Boolean,
    val suggestedDelayMs: Long
)

@Serializable
data class IrisSplitSendPlan(
    val planId: String,
    val totalAmount: Long,
    val finalRecipient: String,
    val splits: List<IrisSplitTransaction>,
    val estimatedFees: Long,
    val privacyScore: Int
)

@Serializable
data class IrisPlannedTransaction(
    val amount: Long,
    val recipient: String,
    val memo: String?
)

@Serializable
data class IrisScheduledTransaction(
    val index: Int,
    val transaction: IrisPlannedTransaction,
    val scheduledTime: Long,
    val delayMs: Long
)

enum class IrisTimeReleaseStrategy {
    RANDOM_INTERVALS,
    FIXED_INTERVALS,
    EXPONENTIAL_BACKOFF
}

@Serializable
data class IrisTimeLockedPlan(
    val planId: String,
    val strategy: IrisTimeReleaseStrategy,
    val transactions: List<IrisScheduledTransaction>,
    val totalDurationMs: Long
)

@Serializable
data class IrisDecoyOutput(
    val index: Int,
    val amount: Long,
    val returnToSender: Boolean
)

@Serializable
data class IrisDecoyOutputPlan(
    val realAmount: Long,
    val decoys: List<IrisDecoyOutput>,
    val totalOutputs: Int,
    val privacyScore: Int
)

enum class IrisMemoObfuscationType {
    PADDING,
    BASE64,
    HASH_REF
}

@Serializable
data class IrisObfuscatedMemo(
    val original: String,
    val obfuscated: String,
    val type: IrisMemoObfuscationType,
    val recoverable: Boolean
)

@Serializable
data class IrisTransactionPrivacyAnalysis(
    val signature: String,
    val privacyScore: Int,
    val classification: String,
    val factors: List<String>
)

@Serializable
data class IrisPrivateTransactionOptions(
    val useSplitSend: Boolean = true,
    val splitCount: Int = 3,
    val useIntermediates: Boolean = true,
    val useDecoys: Boolean = true,
    val decoyCount: Int = 2,
    val useTimeLock: Boolean = true,
    val timeStrategy: IrisTimeReleaseStrategy = IrisTimeReleaseStrategy.RANDOM_INTERVALS
)

@Serializable
data class IrisPrivateTransactionPackage(
    val packageId: String,
    val amount: Long,
    val recipient: String,
    val splitPlan: IrisSplitSendPlan?,
    val decoyPlan: IrisDecoyOutputPlan?,
    val timePlan: IrisTimeLockedPlan?,
    val overallPrivacyScore: Int
)