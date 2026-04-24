package xyz.selenus.luna

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.BeforeEach

/**
 * Tests for Phase 1 Privacy Innovations
 * 
 * These tests verify the new privacy features added in v5.3.0:
 * - Confidential Token-2022 API
 * - Private Broadcast API  
 * - Fingerprint Obfuscation API
 * - RPC Rotation API
 */
class Phase1PrivacyInnovationsTest {

    private lateinit var client: LunaHeliusClient

    @BeforeEach
    fun setUp() {
        // Use a test API key or mock
        client = LunaHeliusClient("test-api-key", Cluster.DEVNET)
    }

    // ========================================================================
    // CONFIDENTIAL TOKEN-2022 API TESTS
    // ========================================================================

    @Test
    @DisplayName("Confidential deposit plan should include correct instructions")
    fun testConfidentialDepositPlan() {
        val plan = client.confidentialToken.prepareConfidentialDeposit(
            tokenAccount = "TokenAccount123",
            amount = 1_000_000_000L
        )
        
        assertEquals("TokenAccount123", plan.tokenAccount)
        assertEquals(1_000_000_000L, plan.amount)
        assertTrue(plan.instructions.contains("DepositConfidentialBalance"))
        assertTrue(plan.privacyNotes.isNotEmpty())
        assertTrue(plan.encryptedAmountPlaceholder.startsWith("[ENCRYPTED:"))
    }

    @Test
    @DisplayName("Confidential transfer plan should include proofs")
    fun testConfidentialTransferPlan() {
        val plan = client.confidentialToken.prepareConfidentialTransfer(
            from = "Sender123",
            to = "Receiver456",
            amount = 500_000_000L
        )
        
        assertEquals("Sender123", plan.from)
        assertEquals("Receiver456", plan.to)
        assertEquals(500_000_000L, plan.amount)
        assertEquals("MAXIMUM", plan.privacyLevel)
        
        assertTrue(plan.rangeProofPlaceholder.contains("RANGE_PROOF"))
        assertTrue(plan.equalityProofPlaceholder.contains("EQUALITY_PROOF"))
        assertTrue(plan.instructions.contains("ConfidentialTransfer"))
    }

    @Test
    @DisplayName("Apply pending should require decryption")
    fun testApplyPending() {
        val plan = client.confidentialToken.prepareApplyPending("TokenAccount123")
        
        assertEquals("TokenAccount123", plan.tokenAccount)
        assertTrue(plan.decryptionRequired)
        assertTrue(plan.instructions.contains("ApplyPendingConfidentialBalance"))
    }

    @Test
    @DisplayName("Confidential withdraw should warn about privacy impact")
    fun testConfidentialWithdraw() {
        val plan = client.confidentialToken.prepareConfidentialWithdraw(
            tokenAccount = "TokenAccount123",
            amount = 100_000_000L
        )
        
        assertEquals("HIGH", plan.privacyImpact)
        assertTrue(plan.privacyNotes.any { it.contains("PUBLIC") || it.contains("WARNING") })
    }

    // ========================================================================
    // FINGERPRINT OBFUSCATION API TESTS
    // ========================================================================

    @Test
    @DisplayName("Small transactions should have lower uniqueness score")
    fun testFingerprintSmallTransaction() {
        // Simulate small SOL transfer (around 550 chars)
        val smallTx = "A".repeat(550)
        val analysis = client.fingerprint.analyzeFingerprint(smallTx)
        
        assertEquals("MEDIUM", analysis.sizeCategory)
        assertTrue(analysis.uniquenessScore <= 50)
        assertEquals("LOW", analysis.privacyRisk)
    }

    @Test
    @DisplayName("Large transactions should have higher uniqueness score")
    fun testFingerprintLargeTransaction() {
        // Simulate large custom transaction
        val largeTx = "B".repeat(2500)
        val analysis = client.fingerprint.analyzeFingerprint(largeTx)
        
        assertEquals("LARGE", analysis.sizeCategory)
        assertTrue(analysis.uniquenessScore >= 60)
        assertEquals("HIGH", analysis.privacyRisk)
    }

    @Test
    @DisplayName("Padding suggestion should calculate correct bytes")
    fun testPaddingSuggestion() {
        val suggestion = client.fingerprint.suggestPadding(
            currentSize = 600,
            targetPattern = LunaHeliusClient.TransactionPattern.DEX_SWAP
        )
        
        assertEquals(600, suggestion.currentSize)
        assertEquals(1100, suggestion.targetSize) // DEX_SWAP target
        assertEquals(500, suggestion.paddingBytes)
        assertEquals("MEMO_DATA", suggestion.paddingMethod)
        assertNotNull(suggestion.suggestedMemo)
    }

    @Test
    @DisplayName("No padding needed when already at target size")
    fun testPaddingNotNeeded() {
        val suggestion = client.fingerprint.suggestPadding(
            currentSize = 1200,
            targetPattern = LunaHeliusClient.TransactionPattern.DEX_SWAP
        )
        
        assertEquals(0, suggestion.paddingBytes)
        assertEquals("NONE", suggestion.paddingMethod)
        assertNull(suggestion.suggestedMemo)
    }

    @Test
    @DisplayName("Regular timing patterns should have high risk")
    fun testTimingFingerprintRegular() {
        // Regular 10-second intervals
        val regularTimes = listOf(1000L, 11000L, 21000L, 31000L, 41000L)
        val analysis = client.fingerprint.analyzeTimingFingerprint(regularTimes)
        
        assertTrue(analysis.isRegular)
        assertEquals("REGULAR_INTERVAL", analysis.patternDetected)
        assertEquals("HIGH", analysis.privacyRisk)
    }

    @Test
    @DisplayName("Random timing patterns should have low risk")
    fun testTimingFingerprintRandom() {
        // Random intervals
        val randomTimes = listOf(1000L, 5000L, 7500L, 25000L, 50000L)
        val analysis = client.fingerprint.analyzeTimingFingerprint(randomTimes)
        
        assertFalse(analysis.isRegular)
        assertEquals("RANDOM", analysis.patternDetected)
        assertEquals("LOW", analysis.privacyRisk)
    }

    @Test
    @DisplayName("Insufficient timing data should return unknown risk")
    fun testTimingFingerprintInsufficient() {
        val analysis = client.fingerprint.analyzeTimingFingerprint(listOf(1000L))
        
        assertEquals("INSUFFICIENT_DATA", analysis.patternDetected)
        assertEquals("UNKNOWN", analysis.privacyRisk)
    }

    // ========================================================================
    // RPC ROTATION API TESTS
    // ========================================================================

    @Test
    @DisplayName("Round robin rotation should cycle through endpoints")
    fun testRoundRobinRotation() {
        val endpoint1 = client.rpcRotation.getNextEndpoint("test-session", LunaHeliusClient.RotationStrategy.ROUND_ROBIN)
        val endpoint2 = client.rpcRotation.getNextEndpoint("test-session", LunaHeliusClient.RotationStrategy.ROUND_ROBIN)
        val endpoint3 = client.rpcRotation.getNextEndpoint("test-session", LunaHeliusClient.RotationStrategy.ROUND_ROBIN)
        
        assertEquals(0, endpoint1.rotationIndex)
        assertEquals(1, endpoint2.rotationIndex)
        assertEquals(2, endpoint3.rotationIndex)
    }

    @Test
    @DisplayName("Different sessions should have independent rotation")
    fun testSessionIndependence() {
        val session1Endpoint = client.rpcRotation.getNextEndpoint("session-1", LunaHeliusClient.RotationStrategy.ROUND_ROBIN)
        val session2Endpoint = client.rpcRotation.getNextEndpoint("session-2", LunaHeliusClient.RotationStrategy.ROUND_ROBIN)
        
        // Both should start at index 0
        assertEquals(0, session1Endpoint.rotationIndex)
        assertEquals(0, session2Endpoint.rotationIndex)
    }

    @Test
    @DisplayName("Rotation stats should track requests")
    fun testRotationStats() {
        val sessionId = "stats-test-${System.currentTimeMillis()}"
        
        // Make some requests
        repeat(5) {
            client.rpcRotation.getNextEndpoint(sessionId, LunaHeliusClient.RotationStrategy.ROUND_ROBIN)
        }
        
        val stats = client.rpcRotation.getRotationStats(sessionId)
        
        assertEquals(sessionId, stats.sessionId)
        assertEquals(5, stats.requestsRouted)
        assertTrue(stats.privacyScore > 0)
    }

    @Test
    @DisplayName("Random rotation should return valid endpoints")
    fun testRandomRotation() {
        val endpoint = client.rpcRotation.getNextEndpoint("random-test", LunaHeliusClient.RotationStrategy.RANDOM)
        
        assertNotNull(endpoint.provider)
        assertNotNull(endpoint.url)
        assertTrue(endpoint.privacyNote.isNotEmpty())
    }

    // ========================================================================
    // PRIVATE BROADCAST API TESTS
    // ========================================================================

    @Test
    @DisplayName("Get optimal regions should return diverse list")
    fun testOptimalRegions() = runBlocking {
        val regions = client.privateBroadcast.getOptimalRegions(3)
        
        assertEquals(3, regions.size)
        // Should include geographically diverse regions
        assertTrue(regions.any { it.name.contains("US") || it.name.contains("EAST") })
    }

    // ========================================================================
    // INTEGRATION TESTS (Require Network)
    // ========================================================================

    // These tests would require actual network access and valid API keys
    // Uncomment and configure for integration testing

    /*
    @Test
    @DisplayName("Multi-region broadcast should attempt all regions")
    fun testMultiRegionBroadcast() = runBlocking {
        val result = client.privateBroadcast.multiRegionBroadcast(
            transaction = "TestTransaction123",
            obfuscateOrder = true
        )
        
        assertTrue(result.regionsAttempted >= 3)
        assertTrue(result.privacyNotes.isNotEmpty())
    }

    @Test
    @DisplayName("Confidential support check should work")
    fun testConfidentialSupportCheck() = runBlocking {
        // USDC on mainnet (would need valid mint address)
        val result = client.confidentialToken.checkConfidentialSupport("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v")
        
        assertNotNull(result.result)
    }
    */
}
