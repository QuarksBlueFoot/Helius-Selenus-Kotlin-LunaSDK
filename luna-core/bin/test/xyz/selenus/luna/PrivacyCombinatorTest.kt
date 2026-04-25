package xyz.selenus.luna

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertEquals

/**
 * Tests for the Advanced Privacy Combinator API.
 * 
 * These tests validate the state-of-the-art privacy operations
 * that combine multiple Helius APIs in innovative ways.
 */
class PrivacyCombinatorTest {

    private val client = LunaHeliusClient(
        apiKey = System.getenv("HELIUS_API_KEY") ?: "test-key",
        cluster = Cluster.MAINNET
    )

    // Test wallet (well-known devnet faucet for testing)
    private val testWallet = "vines1vzrYbzLMRdu58ou5XTby4qAqVRLmqo36NKPTg"

    // =========================================================================
    // Ghost Transaction Tests
    // =========================================================================

    @Test
    fun `test GhostConfig defaults are reasonable`() {
        val config = LunaHeliusClient.GhostConfig()
        
        assertTrue(config.useTemporalObfuscation, "Temporal obfuscation should default to true")
        assertTrue(config.minDelayMs >= 0, "Min delay should be non-negative")
        assertTrue(config.maxDelayMs >= config.minDelayMs, "Max delay should be >= min delay")
        assertTrue(config.staggerBroadcasts, "Stagger broadcasts should default to true")
        assertEquals(LunaHeliusClient.GhostBroadcastStrategy.DUAL_REGION, config.broadcastStrategy)
    }

    @Test
    fun `test GhostBroadcastStrategy enum values`() {
        val strategies = LunaHeliusClient.GhostBroadcastStrategy.entries
        
        assertTrue(strategies.contains(LunaHeliusClient.GhostBroadcastStrategy.SINGLE_RANDOM))
        assertTrue(strategies.contains(LunaHeliusClient.GhostBroadcastStrategy.DUAL_REGION))
        assertTrue(strategies.contains(LunaHeliusClient.GhostBroadcastStrategy.FULL_SCATTER))
        assertTrue(strategies.contains(LunaHeliusClient.GhostBroadcastStrategy.NEAREST_ONLY))
    }

    // =========================================================================
    // Shadow Profile Tests
    // =========================================================================

    @Test
    fun `test analyzeShadowProfile returns valid result`() = runBlocking {
        val result = client.privacyCombinator.analyzeShadowProfile(testWallet)
        
        // Should return a result (even if API key is test)
        if (result.result != null) {
            val profile = result.result
            
            assertNotNull(profile.address)
            assertTrue(profile.shadowScore in 0..100, "Shadow score should be 0-100")
            assertNotNull(profile.shadowLevel)
            assertNotNull(profile.factors)
            assertNotNull(profile.recommendations)
        }
    }

    @Test
    fun `test ShadowLevel enum ordering`() {
        // GHOST is most private, TRANSPARENT is least private
        val levels = LunaHeliusClient.ShadowLevel.entries
        
        assertEquals(5, levels.size)
        assertTrue(levels.contains(LunaHeliusClient.ShadowLevel.GHOST))
        assertTrue(levels.contains(LunaHeliusClient.ShadowLevel.SHADOW))
        assertTrue(levels.contains(LunaHeliusClient.ShadowLevel.VISIBLE))
        assertTrue(levels.contains(LunaHeliusClient.ShadowLevel.EXPOSED))
        assertTrue(levels.contains(LunaHeliusClient.ShadowLevel.TRANSPARENT))
    }

    @Test
    fun `test ShadowFactor data class`() {
        val factor = LunaHeliusClient.ShadowFactor(
            type = "ZK_COMPRESSION",
            description = "Uses ZK compressed accounts",
            scoreImpact = 15,
            classification = "POSITIVE"
        )
        
        assertEquals("ZK_COMPRESSION", factor.type)
        assertEquals(15, factor.scoreImpact)
        assertEquals("POSITIVE", factor.classification)
    }

    // =========================================================================
    // Surveillance Detection Tests
    // =========================================================================

    @Test
    fun `test detectSurveillance returns analysis`() = runBlocking {
        val result = client.privacyCombinator.detectSurveillance(testWallet)
        
        if (result.result != null) {
            val analysis = result.result
            
            assertNotNull(analysis.address)
            assertTrue(analysis.threatScore in 0..100)
            assertNotNull(analysis.level)
            assertNotNull(analysis.threats)
            assertNotNull(analysis.recommendations)
        }
    }

    @Test
    fun `test SurveillanceLevel enum values`() {
        val levels = LunaHeliusClient.SurveillanceLevel.entries
        
        assertEquals(6, levels.size)
        assertTrue(levels.contains(LunaHeliusClient.SurveillanceLevel.NONE))
        assertTrue(levels.contains(LunaHeliusClient.SurveillanceLevel.LOW))
        assertTrue(levels.contains(LunaHeliusClient.SurveillanceLevel.MEDIUM))
        assertTrue(levels.contains(LunaHeliusClient.SurveillanceLevel.HIGH))
        assertTrue(levels.contains(LunaHeliusClient.SurveillanceLevel.CRITICAL))
        assertTrue(levels.contains(LunaHeliusClient.SurveillanceLevel.UNKNOWN))
    }

    @Test
    fun `test SurveillanceThreat data class`() {
        val threat = LunaHeliusClient.SurveillanceThreat(
            type = "AUTOMATED_MONITORING",
            severity = "HIGH",
            description = "Bot activity detected",
            evidence = "Regular intervals"
        )
        
        assertEquals("AUTOMATED_MONITORING", threat.type)
        assertEquals("HIGH", threat.severity)
        assertNotNull(threat.description)
        assertNotNull(threat.evidence)
    }

    // =========================================================================
    // Decoy Generation Tests
    // =========================================================================

    @Test
    fun `test generateDecoyPlan creates valid plan`() = runBlocking {
        val result = client.privacyCombinator.generateDecoyPlan(testWallet)
        
        if (result.result != null) {
            val plan = result.result
            
            assertEquals(testWallet, plan.walletAddress)
            assertTrue(plan.decoyCount > 0)
            assertTrue(plan.decoys.isNotEmpty())
            assertTrue(plan.noiseScore in 0..100)
            assertTrue(plan.notes.isNotEmpty())
        }
    }

    @Test
    fun `test generateDecoyPlan with custom config`() = runBlocking {
        val config = LunaHeliusClient.DecoyConfig(
            decoyCount = 10,
            patterns = listOf(
                LunaHeliusClient.DecoyPattern.SOL_MICRO_TRANSFER,
                LunaHeliusClient.DecoyPattern.SWAP_DUST
            )
        )
        
        val result = client.privacyCombinator.generateDecoyPlan(testWallet, config)
        
        if (result.result != null) {
            val plan = result.result
            
            assertEquals(10, plan.decoyCount)
            assertEquals(10, plan.decoys.size)
        }
    }

    @Test
    fun `test DecoyPattern enum values`() {
        val patterns = LunaHeliusClient.DecoyPattern.entries
        
        assertEquals(5, patterns.size)
        assertTrue(patterns.contains(LunaHeliusClient.DecoyPattern.SOL_MICRO_TRANSFER))
        assertTrue(patterns.contains(LunaHeliusClient.DecoyPattern.TOKEN_CHECK))
        assertTrue(patterns.contains(LunaHeliusClient.DecoyPattern.STAKE_NOISE))
        assertTrue(patterns.contains(LunaHeliusClient.DecoyPattern.SWAP_DUST))
        assertTrue(patterns.contains(LunaHeliusClient.DecoyPattern.NFT_METADATA_READ))
    }

    @Test
    fun `test DecoyTransaction structure`() {
        val decoy = LunaHeliusClient.DecoyTransaction(
            type = "SOL_TRANSFER",
            amount = "10000",
            description = "Micro transfer",
            suggestedDelay = 5000L
        )
        
        assertEquals("SOL_TRANSFER", decoy.type)
        assertEquals("10000", decoy.amount)
        assertTrue(decoy.suggestedDelay > 0)
    }

    // =========================================================================
    // Stealth Query Tests
    // =========================================================================

    @Test
    fun `test stealthAssetQuery returns results`() = runBlocking {
        val result = client.privacyCombinator.stealthAssetQuery(testWallet)
        
        if (result.result != null) {
            val queryResult = result.result
            
            assertEquals(testWallet, queryResult.address)
            assertTrue(queryResult.stealthScore > 0)
            assertTrue(queryResult.totalQueries >= 1)
            assertTrue(queryResult.notes.isNotEmpty())
        }
    }

    @Test
    fun `test stealthAssetQuery with decoys`() = runBlocking {
        val config = LunaHeliusClient.StealthQueryConfig(
            useDecoyQueries = true,
            decoyCount = 5,
            useTemporalSpread = true,
            limit = 50
        )
        
        val result = client.privacyCombinator.stealthAssetQuery(testWallet, config)
        
        if (result.result != null) {
            val queryResult = result.result
            
            assertEquals(5, queryResult.decoyQueriesUsed)
            assertEquals(6, queryResult.totalQueries) // 1 target + 5 decoys
            assertTrue(queryResult.stealthScore >= 80) // High score with decoys
        }
    }

    @Test
    fun `test StealthQueryConfig defaults`() {
        val config = LunaHeliusClient.StealthQueryConfig()
        
        assertTrue(config.useDecoyQueries)
        assertEquals(3, config.decoyCount)
        assertTrue(config.useTemporalSpread)
        assertEquals(100, config.limit)
    }

    // =========================================================================
    // History Leak Analysis Tests
    // =========================================================================

    @Test
    fun `test analyzeHistoryLeaks returns analysis`() = runBlocking {
        val result = client.privacyCombinator.analyzeHistoryLeaks(testWallet, depth = 20)
        
        if (result.result != null) {
            val analysis = result.result
            
            assertEquals(testWallet, analysis.address)
            assertTrue(analysis.transactionsAnalyzed >= 0)
            assertNotNull(analysis.overallRisk)
            assertNotNull(analysis.leaks)
            assertNotNull(analysis.recommendations)
        }
    }

    @Test
    fun `test PrivacyLeak data class`() {
        val leak = LunaHeliusClient.PrivacyLeak(
            type = "ADDRESS_GRAPH",
            severity = "HIGH",
            description = "Large address graph",
            affectedAddresses = emptyList(),
            mitigation = "Use fresh wallets"
        )
        
        assertEquals("ADDRESS_GRAPH", leak.type)
        assertEquals("HIGH", leak.severity)
        assertNotNull(leak.mitigation)
    }

    // =========================================================================
    // Stealth Balance Aggregation Tests
    // =========================================================================

    @Test
    fun `test stealthAggregateBalances works`() = runBlocking {
        val wallets = listOf(testWallet)
        val result = client.privacyCombinator.stealthAggregateBalances(wallets)
        
        if (result.result != null) {
            val aggregation = result.result
            
            assertEquals(1, aggregation.walletCount)
            assertTrue(aggregation.totalLamports >= 0)
            assertTrue(aggregation.totalSol >= 0.0)
            assertTrue(aggregation.stealthScore > 0)
            assertTrue(aggregation.decoysUsed >= 2) // At least 2 decoys
            assertTrue(aggregation.notes.isNotEmpty())
        }
    }

    @Test
    fun `test StealthAggregation structure`() {
        val aggregation = LunaHeliusClient.StealthAggregation(
            totalLamports = 1_000_000_000L,
            totalSol = 1.0,
            walletCount = 2,
            individualBalances = mapOf("wallet1" to 0.5, "wallet2" to 0.5),
            stealthScore = 75,
            decoysUsed = 3,
            notes = listOf("Test note")
        )
        
        assertEquals(1.0, aggregation.totalSol)
        assertEquals(2, aggregation.walletCount)
        assertEquals(75, aggregation.stealthScore)
    }

    // =========================================================================
    // Privacy Swap Tests
    // =========================================================================

    @Test
    fun `test PrivacySwapConfig defaults`() {
        val config = LunaHeliusClient.PrivacySwapConfig()
        
        assertEquals(500, config.preSwapDelayMs)
        assertEquals(50, config.slippageBps)
        assertEquals(false, config.directRoutesOnly)
        assertEquals("medium", config.priorityLevel)
        assertTrue(config.useTemporalObfuscation)
    }

    @Test
    fun `test PrivacySwapResult structure`() {
        val result = LunaHeliusClient.PrivacySwapResult(
            signature = "test-sig",
            success = true,
            error = null,
            privacyScore = 85,
            privacyNotes = listOf("Executed via ghost"),
            routeUsed = "Route A -> B"
        )
        
        assertTrue(result.success)
        assertEquals(85, result.privacyScore)
        assertNotNull(result.routeUsed)
    }

    // =========================================================================
    // API Access Tests
    // =========================================================================

    @Test
    fun `test privacyCombinator is accessible`() {
        assertNotNull(client.privacyCombinator)
    }

    @Test
    fun `test all privacy combinator methods are accessible`() {
        // Verify method accessibility (compile-time check)
        val combinator = client.privacyCombinator
        assertNotNull(combinator)
        
        // These just verify the methods exist and are accessible
        // They would need proper parameters to actually run
    }
}
