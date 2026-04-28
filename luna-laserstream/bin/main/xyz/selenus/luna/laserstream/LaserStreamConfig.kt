package xyz.selenus.luna.laserstream

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Knobs for a LaserStream subscription. Defaults are tuned for production
 * workloads (5s reconnect cap, 100ms initial backoff, 1.6× growth factor).
 *
 * @property region Helius regional endpoint to connect to.
 * @property authToken Helius API key, used as the gRPC bearer token.
 * @property reconnect Backoff/jitter policy for transport drops. Defaults
 *   match what the official Helius LaserStream example uses.
 * @property replayFromSlot If non-null, request historical replay from this
 *   slot (LaserStream's signature feature — vanilla Yellowstone gRPC drops
 *   anything that arrived before subscription).
 * @property keepaliveTimeMs HTTP/2 PING interval. 30s is conservative; drop
 *   to 10s if you're seeing intermediate proxies kill idle streams.
 */
data class LaserStreamConfig(
    val region: LaserStreamRegion,
    val authToken: String,
    val reconnect: ReconnectPolicy = ReconnectPolicy.default(),
    val replayFromSlot: Long? = null,
    val keepaliveTimeMs: Long = 30_000L
) {
    init {
        require(authToken.isNotBlank()) { "LaserStream authToken (Helius API key) must not be blank" }
        require(keepaliveTimeMs > 0) { "keepaliveTimeMs must be positive" }
        replayFromSlot?.let {
            require(it >= 0) { "replayFromSlot must be ≥ 0 (got $it)" }
        }
    }
}

/**
 * Exponential-backoff-with-jitter policy. Used by both gRPC LaserStream and
 * Enhanced WebSocket transports.
 */
data class ReconnectPolicy(
    /** First sleep duration after a disconnect. */
    val initialDelay: Duration = 100.milliseconds,
    /** Hard ceiling on the delay. */
    val maxDelay: Duration = 5.seconds,
    /** Multiplier applied after each failure (1.0 = no growth). */
    val backoffFactor: Double = 1.6,
    /** Random jitter ± fraction of the current delay. 0.0 disables jitter. */
    val jitterFraction: Double = 0.25,
    /** Stop reconnecting after this many consecutive failures. `null` = infinite. */
    val maxRetries: Int? = null
) {
    init {
        require(backoffFactor >= 1.0) { "backoffFactor must be ≥ 1.0" }
        require(jitterFraction in 0.0..1.0) { "jitterFraction must be in [0.0, 1.0]" }
        require(initialDelay > Duration.ZERO) { "initialDelay must be positive" }
        require(maxDelay >= initialDelay) { "maxDelay must be ≥ initialDelay" }
        maxRetries?.let { require(it >= 0) { "maxRetries must be ≥ 0" } }
    }

    /**
     * Compute the delay before the [attempt]-th retry (0-indexed: attempt 0
     * is the *first* retry, sleeps [initialDelay]).
     *
     * Uses [SECURE_RANDOM] for jitter so backoff timing is not predictable
     * from process state — defends against an adversary trying to time
     * reconnect bursts.
     */
    internal fun delayForAttempt(attempt: Int): Duration {
        val grown = initialDelay.inWholeMilliseconds.toDouble() *
            Math.pow(backoffFactor, attempt.toDouble())
        val capped = grown.coerceAtMost(maxDelay.inWholeMilliseconds.toDouble())
        // Symmetric jitter: ±jitterFraction * capped
        val jitter = if (jitterFraction == 0.0) 0.0 else {
            (SECURE_RANDOM.nextDouble() * 2.0 - 1.0) * jitterFraction * capped
        }
        return (capped + jitter).coerceAtLeast(0.0).toLong().milliseconds
    }

    companion object {
        /**
         * Process-wide [java.security.SecureRandom] used for backoff jitter.
         * Single shared instance because constructing SecureRandom is expensive
         * (it seeds from `/dev/urandom` or the platform CSPRNG) and contention
         * on the lazy seed lock is the documented cost — not the `nextDouble`
         * call itself.
         */
        private val SECURE_RANDOM = java.security.SecureRandom()

        /**
         * Production-tuned defaults: 100ms → 5s with 1.6× growth and ±25%
         * jitter. Matches the Helius LaserStream Rust client's defaults.
         */
        fun default() = ReconnectPolicy()

        /** Aggressive policy for low-tolerance dashboards: 50ms → 1s. */
        fun aggressive() = ReconnectPolicy(
            initialDelay = 50.milliseconds,
            maxDelay = 1.seconds,
            backoffFactor = 1.4
        )

        /** Conservative policy for back-end indexers: 500ms → 30s. */
        fun conservative() = ReconnectPolicy(
            initialDelay = 500.milliseconds,
            maxDelay = 30.seconds,
            backoffFactor = 2.0
        )
    }
}
