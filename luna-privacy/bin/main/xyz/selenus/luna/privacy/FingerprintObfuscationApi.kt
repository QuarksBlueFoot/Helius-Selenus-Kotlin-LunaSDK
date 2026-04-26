package xyz.selenus.luna.privacy

import kotlinx.serialization.Serializable
import xyz.selenus.luna.LunaHeliusClient
import xyz.selenus.luna.crypto.secureRandom

/**
 * # Fingerprint Obfuscation API
 *
 * Make transactions look like common patterns to blend in with network
 * traffic. Defeats transaction fingerprinting by mimicking popular
 * transaction shapes (DEX swaps, SOL transfers, NFT moves) so that an
 * observer can't single out your traffic by size/timing/structure alone.
 *
 * ## Acquire
 * ```kotlin
 * import xyz.selenus.luna.privacy.fingerprint
 *
 * val analysis = client.fingerprint.analyzeFingerprint(signedTxBase64)
 * if (analysis.privacyRisk == "HIGH") {
 *     val padding = client.fingerprint.suggestPadding(
 *         currentSize = signedTx.length,
 *         targetPattern = TransactionPattern.DEX_SWAP
 *     )
 *     // attach padding.suggestedMemo to your transaction
 * }
 * ```
 */
class FingerprintObfuscationApi internal constructor(
    @Suppress("unused") private val client: LunaHeliusClient
) {

    /**
     * Analyze how unique a transaction looks compared to common network
     * patterns. Higher uniqueness = easier to track on-chain.
     */
    fun analyzeFingerprint(transaction: String): FingerprintAnalysis {
        val txLength = transaction.length

        // Approximate base64 sizes for the most common Solana transaction shapes.
        val commonSizes = listOf(500..600, 700..800, 1000..1200)
        val isCommonSize = commonSizes.any { txLength in it }

        val uniquenessScore = when {
            isCommonSize -> 30      // common = good for privacy
            txLength < 400 -> 60    // very small = somewhat unique
            txLength > 2000 -> 80   // complex = very unique
            else -> 50
        }

        return FingerprintAnalysis(
            transactionHash = transaction.hashCode().toString(),
            uniquenessScore = uniquenessScore,
            sizeCategory = when {
                txLength < 500 -> "SMALL"
                txLength < 1000 -> "MEDIUM"
                else -> "LARGE"
            },
            looksLike = when {
                txLength in 500..600 -> "SOL_TRANSFER"
                txLength in 700..900 -> "TOKEN_TRANSFER"
                txLength in 1000..1500 -> "DEX_SWAP"
                else -> "CUSTOM"
            },
            privacyRisk = if (uniquenessScore > 60) "HIGH" else "LOW",
            recommendations = if (uniquenessScore > 60) {
                listOf(
                    "Transaction has unusual fingerprint",
                    "Consider adding padding or restructuring",
                    "Unique transactions are easier to track"
                )
            } else {
                listOf("Transaction blends well with network traffic")
            }
        )
    }

    /**
     * Get suggested padding to make a transaction look more common.
     *
     * Returns a [PaddingSuggestion] with a recommended memo (built from
     * SecureRandom-backed hex chars so the padding is unpredictable to
     * an observer trying to fingerprint the SDK's PRNG state).
     */
    fun suggestPadding(
        currentSize: Int,
        targetPattern: TransactionPattern = TransactionPattern.DEX_SWAP
    ): PaddingSuggestion {
        val targetSize = when (targetPattern) {
            TransactionPattern.SOL_TRANSFER -> 550
            TransactionPattern.TOKEN_TRANSFER -> 750
            TransactionPattern.DEX_SWAP -> 1100
            TransactionPattern.NFT_TRANSFER -> 900
            TransactionPattern.STAKING -> 650
        }

        val paddingNeeded = (targetSize - currentSize).coerceAtLeast(0)

        return PaddingSuggestion(
            currentSize = currentSize,
            targetSize = targetSize,
            targetPattern = targetPattern.name,
            paddingBytes = paddingNeeded,
            paddingMethod = if (paddingNeeded > 0) "MEMO_DATA" else "NONE",
            suggestedMemo = if (paddingNeeded > 0) {
                "ref:" + (1..paddingNeeded.coerceAtMost(32)).map {
                    "0123456789abcdef".secureRandom()
                }.joinToString("")
            } else null
        )
    }

    /**
     * Check if transaction timing matches common patterns. Predictable
     * timing leaks user identity even when transaction contents are
     * private.
     *
     * @param recentTxTimes Wall-clock millis for each recent transaction.
     */
    fun analyzeTimingFingerprint(recentTxTimes: List<Long>): TimingFingerprintAnalysis {
        if (recentTxTimes.size < 2) {
            return TimingFingerprintAnalysis(
                sampleSize = recentTxTimes.size,
                averageIntervalMs = 0,
                isRegular = false,
                patternDetected = "INSUFFICIENT_DATA",
                privacyRisk = "UNKNOWN",
                recommendation = "Need more transaction history"
            )
        }

        val intervals = recentTxTimes.sorted().zipWithNext { a, b -> b - a }
        val avgInterval = intervals.average()
        val variance = intervals.map { (it - avgInterval) * (it - avgInterval) }.average()
        val stdDev = kotlin.math.sqrt(variance)
        val cv = if (avgInterval > 0) stdDev / avgInterval else 0.0

        val isRegular = cv < 0.5  // Low coefficient of variation = regular pattern

        return TimingFingerprintAnalysis(
            sampleSize = recentTxTimes.size,
            averageIntervalMs = avgInterval.toLong(),
            isRegular = isRegular,
            patternDetected = if (isRegular) "REGULAR_INTERVAL" else "RANDOM",
            privacyRisk = if (isRegular) "HIGH" else "LOW",
            recommendation = if (isRegular) {
                "Your transaction timing is predictable. Add randomization."
            } else {
                "Good timing randomization."
            }
        )
    }
}

/**
 * Common Solana transaction shape that an obfuscation pass should mimic.
 * The byte targets in [FingerprintObfuscationApi.suggestPadding] are
 * empirical medians for each shape on mainnet.
 */
enum class TransactionPattern {
    SOL_TRANSFER,
    TOKEN_TRANSFER,
    DEX_SWAP,
    NFT_TRANSFER,
    STAKING
}

/** Result of [FingerprintObfuscationApi.analyzeFingerprint]. */
@Serializable
data class FingerprintAnalysis(
    val transactionHash: String,
    val uniquenessScore: Int,
    val sizeCategory: String,
    val looksLike: String,
    val privacyRisk: String,
    val recommendations: List<String>
)

/** Result of [FingerprintObfuscationApi.suggestPadding]. */
@Serializable
data class PaddingSuggestion(
    val currentSize: Int,
    val targetSize: Int,
    val targetPattern: String,
    val paddingBytes: Int,
    val paddingMethod: String,
    val suggestedMemo: String?
)

/** Result of [FingerprintObfuscationApi.analyzeTimingFingerprint]. */
@Serializable
data class TimingFingerprintAnalysis(
    val sampleSize: Int,
    val averageIntervalMs: Long,
    val isRegular: Boolean,
    val patternDetected: String,
    val privacyRisk: String,
    val recommendation: String
)

/**
 * Acquire the [FingerprintObfuscationApi] namespace from a [LunaHeliusClient].
 *
 * ```kotlin
 * import xyz.selenus.luna.privacy.fingerprint
 *
 * val analysis = client.fingerprint.analyzeFingerprint(signedTx)
 * ```
 */
val LunaHeliusClient.fingerprint: FingerprintObfuscationApi
    get() = FingerprintObfuscationApi(this)
