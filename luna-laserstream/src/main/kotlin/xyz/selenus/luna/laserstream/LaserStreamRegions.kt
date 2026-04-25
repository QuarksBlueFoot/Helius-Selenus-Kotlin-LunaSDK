package xyz.selenus.luna.laserstream

import xyz.selenus.luna.Cluster

/**
 * # LaserStream regional endpoint registry
 *
 * Helius runs LaserStream gRPC across nine mainnet regions (and one devnet
 * region). Picking the geographically closest endpoint to your application
 * server is the single biggest latency win available for streaming workloads
 * — typical RTT savings range from 80–200ms vs. always defaulting to
 * us-east-1.
 *
 * Use [LaserStreamRegion.bestForLatency] to probe all endpoints in parallel
 * and pick the lowest median RTT, or pick a fixed [LaserStreamRegion] if you
 * already know your deployment region.
 */
enum class LaserStreamRegion(
    /** IATA-style airport code identifier used in the hostname. */
    val code: String,
    /** Cluster (mainnet/devnet) the endpoint serves. */
    val cluster: Cluster,
    /** gRPC base URL. */
    val grpcUrl: String,
    /** Human-readable city for diagnostics / logging. */
    val city: String
) {
    MAINNET_NEW_YORK("ewr", Cluster.MAINNET, "https://laserstream-mainnet-ewr.helius-rpc.com", "New York"),
    MAINNET_PITTSBURGH("pitt", Cluster.MAINNET, "https://laserstream-mainnet-pitt.helius-rpc.com", "Pittsburgh"),
    MAINNET_SALT_LAKE("slc", Cluster.MAINNET, "https://laserstream-mainnet-slc.helius-rpc.com", "Salt Lake City"),
    MAINNET_LOS_ANGELES("lax", Cluster.MAINNET, "https://laserstream-mainnet-lax.helius-rpc.com", "Los Angeles"),
    MAINNET_LONDON("lon", Cluster.MAINNET, "https://laserstream-mainnet-lon.helius-rpc.com", "London"),
    MAINNET_AMSTERDAM("ams", Cluster.MAINNET, "https://laserstream-mainnet-ams.helius-rpc.com", "Amsterdam"),
    MAINNET_FRANKFURT("fra", Cluster.MAINNET, "https://laserstream-mainnet-fra.helius-rpc.com", "Frankfurt"),
    MAINNET_TOKYO("tyo", Cluster.MAINNET, "https://laserstream-mainnet-tyo.helius-rpc.com", "Tokyo"),
    MAINNET_SINGAPORE("sgp", Cluster.MAINNET, "https://laserstream-mainnet-sgp.helius-rpc.com", "Singapore"),
    DEVNET_NEW_YORK("ewr", Cluster.DEVNET, "https://laserstream-devnet-ewr.helius-rpc.com", "New York (devnet)");

    /** All endpoints for [cluster]. */
    companion object {
        /** Returns every region that serves the given [cluster]. */
        fun forCluster(cluster: Cluster): List<LaserStreamRegion> = when (cluster) {
            Cluster.MAINNET -> values().filter { it.cluster == Cluster.MAINNET }
            Cluster.DEVNET -> listOf(DEVNET_NEW_YORK)
            // Helius does not expose testnet LaserStream; fall back to devnet for SDK ergonomics.
            Cluster.TESTNET -> listOf(DEVNET_NEW_YORK)
        }

        /**
         * Default fallback when geo-probe is not desired. Returns the
         * us-east region for mainnet/devnet — same default the official
         * Helius examples use.
         */
        fun defaultFor(cluster: Cluster): LaserStreamRegion = when (cluster) {
            Cluster.MAINNET -> MAINNET_NEW_YORK
            Cluster.DEVNET -> DEVNET_NEW_YORK
            Cluster.TESTNET -> DEVNET_NEW_YORK
        }
    }
}

/**
 * Result of a single latency probe. [rttMs] is wall-clock millis for an HTTP
 * HEAD round-trip — a useful proxy for gRPC RTT since both share the same
 * TCP/TLS path. [error] is non-null when the probe failed (DNS, timeout,
 * TLS, etc.); in that case [rttMs] is `Long.MAX_VALUE` so the region sorts
 * to the bottom of any "lowest RTT first" list.
 */
data class LaserStreamLatencyResult(
    val region: LaserStreamRegion,
    val rttMs: Long,
    val error: String? = null
) {
    /** Convenience for filters and ordering. */
    val ok: Boolean get() = error == null
}
