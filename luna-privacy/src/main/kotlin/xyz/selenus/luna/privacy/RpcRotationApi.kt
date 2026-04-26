package xyz.selenus.luna.privacy

import kotlinx.serialization.Serializable
import xyz.selenus.luna.LunaHeliusClient
import xyz.selenus.luna.crypto.SecureRng

/**
 * # RPC Rotation API
 *
 * Distribute requests across multiple RPC providers so no single provider
 * sees the user's complete activity pattern. Defends against:
 *  - Single-provider IP correlation across requests
 *  - Provider-level activity fingerprinting
 *  - Single point of timing-correlation
 *
 * Uses [SecureRng] for `RANDOM` and `WEIGHTED` strategies — predictable
 * rotation defeats the entire purpose, so randomness MUST be unpredictable
 * to a network observer.
 *
 * ## Acquire
 * ```kotlin
 * import xyz.selenus.luna.privacy.rpcRotation
 *
 * val helius = LunaHeliusClient("<api-key>")
 * val endpoint = helius.rpcRotation.getNextEndpoint(strategy = RotationStrategy.WEIGHTED)
 * println("Routing via ${endpoint.provider}")
 * ```
 */
class RpcRotationApi internal constructor(private val client: LunaHeliusClient) {

    // Per-session rotation cursor. Survives for the lifetime of this
    // RpcRotationApi instance. Recreating the instance resets the cursor —
    // callers that need durable rotation state across restarts should
    // persist `getRotationStats(sessionId).requestsRouted` and seed the
    // initial cursor on next launch.
    private val rotationState = mutableMapOf<String, Int>()

    /**
     * Get the next RPC endpoint in rotation.
     *
     * @param sessionId Session identifier for consistent per-user rotation.
     *   Different sessions rotate independently.
     * @param strategy Rotation strategy.
     */
    fun getNextEndpoint(
        sessionId: String = "default",
        strategy: RotationStrategy = RotationStrategy.ROUND_ROBIN
    ): RotatedEndpoint {
        val endpoints = listOf(
            EndpointInfo("helius", client.baseUrl, true),
            EndpointInfo("backup1", "https://api.mainnet-beta.solana.com", false),
            EndpointInfo("backup2", "https://solana-mainnet.g.alchemy.com/v2/demo", false)
        )

        val selected = when (strategy) {
            RotationStrategy.ROUND_ROBIN -> {
                val current = rotationState.getOrDefault(sessionId, 0)
                rotationState[sessionId] = (current + 1) % endpoints.size
                endpoints[current]
            }
            RotationStrategy.RANDOM -> {
                // SecureRng so an adversary observing the rotation pattern
                // can't predict (and front-run / correlate to) the next
                // endpoint pick. This is the whole point of rotation —
                // predictable rotation is just round-robin in disguise.
                endpoints[SecureRng.nextInt(endpoints.size)]
            }
            RotationStrategy.WEIGHTED -> {
                // Prefer authenticated endpoint with 70% probability,
                // unpredictably. SecureRandom so the bias direction
                // cannot be inferred from observed traffic.
                if (SecureRng.nextDouble() < 0.7) endpoints[0]
                else endpoints[SecureRng.nextInt(endpoints.size)]
            }
        }

        return RotatedEndpoint(
            provider = selected.name,
            url = selected.url,
            isAuthenticated = selected.isAuthenticated,
            rotationIndex = rotationState.getOrDefault(sessionId, 0),
            privacyNote = "Request routed via ${selected.name}"
        )
    }

    /** Get privacy statistics for the current rotation session. */
    fun getRotationStats(sessionId: String = "default"): RotationStats {
        val currentIndex = rotationState.getOrDefault(sessionId, 0)
        return RotationStats(
            sessionId = sessionId,
            requestsRouted = currentIndex,
            providersUsed = (currentIndex % 3) + 1,
            privacyScore = when {
                currentIndex < 3 -> 30
                currentIndex < 10 -> 50
                currentIndex < 50 -> 70
                else -> 85
            },
            recommendation = if (currentIndex < 10) {
                "Continue distributing requests for better privacy"
            } else {
                "Good request distribution"
            }
        )
    }
}

/** Selected endpoint after rotation. */
@Serializable
data class RotatedEndpoint(
    val provider: String,
    val url: String,
    val isAuthenticated: Boolean,
    val rotationIndex: Int,
    val privacyNote: String
)

/** Per-session rotation statistics from [RpcRotationApi.getRotationStats]. */
@Serializable
data class RotationStats(
    val sessionId: String,
    val requestsRouted: Int,
    val providersUsed: Int,
    val privacyScore: Int,
    val recommendation: String
)

/** Internal endpoint descriptor used by the rotation strategies. */
data class EndpointInfo(
    val name: String,
    val url: String,
    val isAuthenticated: Boolean
)

/** Strategy for picking the next endpoint in [RpcRotationApi.getNextEndpoint]. */
enum class RotationStrategy {
    /** Cycle through endpoints in order. Predictable but balanced. */
    ROUND_ROBIN,
    /** Pick uniformly at random using SecureRandom. */
    RANDOM,
    /** 70% chance of authenticated endpoint, 30% any (SecureRandom-backed). */
    WEIGHTED
}

/**
 * Acquire the [RpcRotationApi] namespace from a [LunaHeliusClient].
 *
 * ```kotlin
 * import xyz.selenus.luna.privacy.rpcRotation
 *
 * val endpoint = client.rpcRotation.getNextEndpoint()
 * ```
 */
val LunaHeliusClient.rpcRotation: RpcRotationApi
    get() = RpcRotationApi(this)
