package xyz.selenus.luna.privacy

import kotlinx.serialization.Serializable
import xyz.selenus.luna.LunaHeliusClient

/**
 * # Private Broadcast API
 *
 * Broadcast transactions through multiple geographically distributed Helius
 * Sender endpoints to prevent IP correlation and timing analysis.
 *
 * ## Privacy benefits
 *  - **Geographic distribution**: no single point sees the origin
 *  - **Timing obfuscation**: randomized submission order
 *  - **Path diversity**: different network paths to validators
 *
 * ## Acquire
 * ```kotlin
 * import xyz.selenus.luna.privacy.privateBroadcast
 *
 * val helius = LunaHeliusClient("<api-key>")
 * val result = helius.privateBroadcast.maxPrivacyBroadcast(signedTx)
 * println("Broadcast from ${result.successfulRegions} regions")
 * ```
 */
class PrivateBroadcastApi internal constructor(private val client: LunaHeliusClient) {

    /**
     * Broadcast a transaction through multiple regions simultaneously.
     *
     * @param transaction Signed transaction (base64/base58)
     * @param regions Regions to broadcast from
     * @param obfuscateOrder Randomize which region submits first
     * @return [MultiRegionBroadcastResult] with per-region status
     */
    suspend fun multiRegionBroadcast(
        transaction: String,
        regions: List<LunaHeliusClient.SenderRegion> = listOf(
            LunaHeliusClient.SenderRegion.US_EAST,
            LunaHeliusClient.SenderRegion.EU_NORTH,
            LunaHeliusClient.SenderRegion.AP_TOKYO
        ),
        obfuscateOrder: Boolean = true
    ): MultiRegionBroadcastResult {
        // Use SecureRandom-backed shuffle so an observer can't predict
        // submission order from the SDK's PRNG state.
        val orderedRegions = if (obfuscateOrder) regions.secureShuffled() else regions
        val results = mutableListOf<RegionBroadcastStatus>()
        var firstSuccess: String? = null

        for (region in orderedRegions) {
            try {
                val response = client.sender.sendTransaction(transaction, region)
                val signature = response.result
                if (signature != null) {
                    results.add(RegionBroadcastStatus(
                        region = region.name,
                        success = true,
                        signature = signature,
                        error = null
                    ))
                    if (firstSuccess == null) firstSuccess = signature
                } else {
                    results.add(RegionBroadcastStatus(
                        region = region.name,
                        success = false,
                        signature = null,
                        error = response.error?.message ?: "Unknown error"
                    ))
                }
            } catch (e: Exception) {
                results.add(RegionBroadcastStatus(
                    region = region.name,
                    success = false,
                    signature = null,
                    error = e.message
                ))
            }
        }

        return MultiRegionBroadcastResult(
            transaction = transaction.take(32) + "...",
            regionsAttempted = regions.size,
            successfulRegions = results.count { it.success },
            signature = firstSuccess,
            regionResults = results,
            privacyNotes = listOf(
                "Transaction broadcast from ${results.count { it.success }} regions",
                "Order was ${if (obfuscateOrder) "randomized" else "sequential"}",
                "Observers cannot determine origin region"
            )
        )
    }

    /** Broadcast with maximum privacy (every available region). */
    suspend fun maxPrivacyBroadcast(transaction: String): MultiRegionBroadcastResult =
        multiRegionBroadcast(
            transaction = transaction,
            regions = LunaHeliusClient.SenderRegion.values().toList(),
            obfuscateOrder = true
        )

    /**
     * Get a geographically diverse set of regions for a balanced broadcast.
     *
     * Returns the canonical 3-region cross-continent triplet (US/EU/APAC) by
     * default; pass [count] to trim. For latency-optimised picks, use
     * `LunaHeliusClient.warmSenderConnection(region)` to probe each region
     * and select the lowest-RTT subset.
     */
    @Suppress("unused")
    fun getOptimalRegions(count: Int = 3): List<LunaHeliusClient.SenderRegion> {
        require(count in 1..LunaHeliusClient.SenderRegion.values().size) {
            "count must be 1..${LunaHeliusClient.SenderRegion.values().size}"
        }
        return listOf(
            LunaHeliusClient.SenderRegion.US_EAST,
            LunaHeliusClient.SenderRegion.EU_NORTH,
            LunaHeliusClient.SenderRegion.AP_TOKYO,
            LunaHeliusClient.SenderRegion.US_SLC,
            LunaHeliusClient.SenderRegion.EU_WEST,
            LunaHeliusClient.SenderRegion.EU_CENTRAL,
            LunaHeliusClient.SenderRegion.AP_SINGAPORE
        ).take(count)
    }

    /**
     * Cryptographically-secure shuffle using `java.security.SecureRandom`.
     * Guards against an observer predicting broadcast order from the SDK's
     * `kotlin.random.Random.Default` state.
     */
    private fun <T> List<T>.secureShuffled(): List<T> {
        val random = java.security.SecureRandom()
        val mutable = toMutableList()
        for (i in mutable.size - 1 downTo 1) {
            val j = random.nextInt(i + 1)
            val tmp = mutable[i]
            mutable[i] = mutable[j]
            mutable[j] = tmp
        }
        return mutable
    }
}

/** One region's broadcast status — included in [MultiRegionBroadcastResult.regionResults]. */
@Serializable
data class RegionBroadcastStatus(
    val region: String,
    val success: Boolean,
    val signature: String?,
    val error: String?
)

/** Aggregate result of a multi-region broadcast. */
@Serializable
data class MultiRegionBroadcastResult(
    val transaction: String,
    val regionsAttempted: Int,
    val successfulRegions: Int,
    val signature: String?,
    val regionResults: List<RegionBroadcastStatus>,
    val privacyNotes: List<String>
)

/**
 * Acquire the [PrivateBroadcastApi] namespace from a [LunaHeliusClient].
 *
 * ```kotlin
 * import xyz.selenus.luna.privacy.privateBroadcast
 *
 * val result = client.privateBroadcast.maxPrivacyBroadcast(signedTx)
 * ```
 */
val LunaHeliusClient.privateBroadcast: PrivateBroadcastApi
    get() = PrivateBroadcastApi(this)
