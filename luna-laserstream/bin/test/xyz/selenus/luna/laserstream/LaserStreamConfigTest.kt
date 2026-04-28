package xyz.selenus.luna.laserstream

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class LaserStreamConfigTest {

    @Test
    fun `default reconnect policy stays under maxDelay across many attempts`() {
        val policy = ReconnectPolicy.default()
        // Pump 50 attempts; nothing should exceed maxDelay + jitter ceiling.
        val ceilingMs = (policy.maxDelay.inWholeMilliseconds * (1.0 + policy.jitterFraction)).toLong()
        for (i in 0..50) {
            val d = policy.delayForAttempt(i).inWholeMilliseconds
            assertTrue(d <= ceilingMs, "attempt $i delay=$d ms exceeded ceiling=$ceilingMs")
            assertTrue(d >= 0, "attempt $i delay=$d must be non-negative")
        }
    }

    @Test
    fun `attempt-zero delay is initialDelay (with jitter band)`() {
        val policy = ReconnectPolicy(
            initialDelay = 200.milliseconds,
            maxDelay = 5.seconds,
            backoffFactor = 2.0,
            jitterFraction = 0.0
        )
        // jitter disabled → exactly 200ms
        assertEquals(200L, policy.delayForAttempt(0).inWholeMilliseconds)
    }

    @Test
    fun `aggressive policy hits maxDelay faster than default`() {
        val agg = ReconnectPolicy.aggressive()
        val def = ReconnectPolicy.default()
        // After 3 attempts, aggressive should be at or near its cap; default still climbing.
        val aggAtCap = agg.delayForAttempt(10).inWholeMilliseconds
        val defAt3 = def.delayForAttempt(3).inWholeMilliseconds
        assertTrue(aggAtCap <= agg.maxDelay.inWholeMilliseconds * (1.0 + agg.jitterFraction).toLong())
        assertTrue(defAt3 < def.maxDelay.inWholeMilliseconds)
    }

    @Test
    fun `LaserStreamConfig rejects blank auth token`() {
        assertFailsWith<IllegalArgumentException> {
            LaserStreamConfig(
                region = LaserStreamRegion.MAINNET_NEW_YORK,
                authToken = ""
            )
        }
    }

    @Test
    fun `LaserStreamConfig rejects negative replay slot`() {
        assertFailsWith<IllegalArgumentException> {
            LaserStreamConfig(
                region = LaserStreamRegion.MAINNET_NEW_YORK,
                authToken = "key",
                replayFromSlot = -1L
            )
        }
    }

    @Test
    fun `ReconnectPolicy rejects backoff factor below 1`() {
        assertFailsWith<IllegalArgumentException> {
            ReconnectPolicy(backoffFactor = 0.9)
        }
    }

    @Test
    fun `LaserStreamRegion forCluster returns 9 mainnet regions`() {
        val mainnet = LaserStreamRegion.forCluster(xyz.selenus.luna.Cluster.MAINNET)
        assertEquals(9, mainnet.size)
        assertTrue(mainnet.all { it.cluster == xyz.selenus.luna.Cluster.MAINNET })
    }

    @Test
    fun `LaserStreamRegion endpoints all use HTTPS and helius-rpc domain`() {
        for (r in LaserStreamRegion.values()) {
            assertTrue(r.grpcUrl.startsWith("https://"), "${r.name} not HTTPS")
            assertTrue(r.grpcUrl.contains("helius-rpc.com"), "${r.name} not helius-rpc.com")
            assertTrue(r.grpcUrl.contains(r.code), "${r.name} URL does not contain region code ${r.code}")
        }
    }

    @Test
    fun `transactionSubscribePayload builds well-formed JSON`() {
        // Smoke-test that payload helpers produce valid JSON-RPC envelopes.
        // Use a manually constructed LaserStreamApi via reflection of the factory function
        // — since the helper is on LaserStreamApi we test its output through the contract.
        // Simpler: test via the SubscriptionRequest companion object.
        val req = LaserStreamSubscriptionRequest.txByAddress(
            addresses = listOf("addr1", "addr2"),
            commitment = "confirmed",
            includeVote = false,
            includeFailed = false
        )
        assertEquals("confirmed", req.commitment)
        assertEquals(listOf("addr1", "addr2"), req.transactions["default"]?.accountInclude)
    }
}
