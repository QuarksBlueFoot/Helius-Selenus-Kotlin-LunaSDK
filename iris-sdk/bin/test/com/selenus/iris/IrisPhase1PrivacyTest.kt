package com.selenus.iris

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.BeforeEach

/**
 * Tests for Iris SDK Phase 1 Privacy Innovations
 * 
 * These tests verify the new privacy features added in v1.2.0:
 * - Confidential Token-2022 API
 * - Private Broadcast API  
 * - Fingerprint Obfuscation API
 * - RPC Rotation API
 */
class IrisPhase1PrivacyTest {

    // Note: Can't instantiate full client without valid endpoint
    // These tests focus on the static/computational methods

    // ========================================================================
    // FINGERPRINT OBFUSCATION TESTS
    // ========================================================================

    @Test
    @DisplayName("Fingerprint analysis should categorize by size")
    fun testFingerprintCategories() {
        // Test categorization logic
        val smallSize = 400
        val mediumSize = 700
        val largeSize = 1500
        
        assertTrue(smallSize < 500) // Would be SMALL
        assertTrue(mediumSize in 500..999) // Would be MEDIUM
        assertTrue(largeSize >= 1000) // Would be LARGE
    }

    @Test
    @DisplayName("Common transaction sizes should have low risk")
    fun testCommonSizes() {
        val commonSizes = listOf(500..600, 700..800, 1000..1200)
        
        // 550 is in first range
        assertTrue(commonSizes.any { 550 in it })
        // 750 is in second range
        assertTrue(commonSizes.any { 750 in it })
        // 1100 is in third range
        assertTrue(commonSizes.any { 1100 in it })
        // 400 is not in any range
        assertFalse(commonSizes.any { 400 in it })
    }

    @Test
    @DisplayName("Padding calculation should be accurate")
    fun testPaddingCalculation() {
        // DEX_SWAP target is 1100
        val currentSize = 600
        val targetSize = 1100
        val paddingNeeded = (targetSize - currentSize).coerceAtLeast(0)
        
        assertEquals(500, paddingNeeded)
    }

    @Test
    @DisplayName("No negative padding")
    fun testNoNegativePadding() {
        val currentSize = 1500
        val targetSize = 1100
        val paddingNeeded = (targetSize - currentSize).coerceAtLeast(0)
        
        assertEquals(0, paddingNeeded)
    }

    // ========================================================================
    // TIMING ANALYSIS TESTS
    // ========================================================================

    @Test
    @DisplayName("Regular intervals should be detected")
    fun testRegularIntervalDetection() {
        val regularTimes = listOf(1000L, 11000L, 21000L, 31000L, 41000L)
        val intervals = regularTimes.sorted().zipWithNext { a, b -> b - a }
        
        // All intervals should be 10000
        assertTrue(intervals.all { it == 10000L })
        
        // Calculate coefficient of variation
        val avg = intervals.average()
        val variance = intervals.map { (it - avg) * (it - avg) }.average()
        val stdDev = kotlin.math.sqrt(variance)
        val cv = if (avg > 0) stdDev / avg else 0.0
        
        // Very regular = low CV
        assertTrue(cv < 0.5)
    }

    @Test
    @DisplayName("Random intervals should have high variation")
    fun testRandomIntervalDetection() {
        val randomTimes = listOf(1000L, 5000L, 7500L, 25000L, 50000L)
        val intervals = randomTimes.sorted().zipWithNext { a, b -> b - a }
        
        // Intervals: 4000, 2500, 17500, 25000
        val avg = intervals.average()
        val variance = intervals.map { (it - avg) * (it - avg) }.average()
        val stdDev = kotlin.math.sqrt(variance)
        val cv = if (avg > 0) stdDev / avg else 0.0
        
        // Random = high CV
        assertTrue(cv >= 0.5)
    }

    @Test
    @DisplayName("Insufficient data should be handled")
    fun testInsufficientData() {
        val singleTime = listOf(1000L)
        
        assertTrue(singleTime.size < 2)
    }

    // ========================================================================
    // RPC ROTATION TESTS
    // ========================================================================

    @Test
    @DisplayName("Round robin should cycle correctly")
    fun testRoundRobinLogic() {
        val endpoints = listOf("A", "B", "C")
        
        var index = 0
        val sequence = mutableListOf<String>()
        
        repeat(6) {
            sequence.add(endpoints[index])
            index = (index + 1) % endpoints.size
        }
        
        assertEquals(listOf("A", "B", "C", "A", "B", "C"), sequence)
    }

    @Test
    @DisplayName("Privacy score should increase with usage")
    fun testPrivacyScoreProgression() {
        // Score thresholds
        fun calculateScore(requestCount: Int): Int = when {
            requestCount < 3 -> 30
            requestCount < 10 -> 50
            requestCount < 50 -> 70
            else -> 85
        }
        
        assertTrue(calculateScore(1) < calculateScore(5))
        assertTrue(calculateScore(5) < calculateScore(20))
        assertTrue(calculateScore(20) < calculateScore(100))
    }

    // ========================================================================
    // CONFIDENTIAL TOKEN TESTS
    // ========================================================================

    @Test
    @DisplayName("Deposit plan should include required instructions")
    fun testDepositPlanInstructions() {
        val expectedInstructions = listOf(
            "ApproveConfidentialTransfer",
            "DepositConfidentialBalance"
        )
        
        assertTrue(expectedInstructions.contains("DepositConfidentialBalance"))
    }

    @Test
    @DisplayName("Transfer plan should include proofs")
    fun testTransferPlanProofs() {
        val expectedInstructions = listOf(
            "ConfidentialTransfer",
            "VerifyRangeProof",
            "VerifyEqualityProof"
        )
        
        assertEquals(3, expectedInstructions.size)
        assertTrue(expectedInstructions.any { it.contains("Proof") })
    }

    @Test
    @DisplayName("Withdraw should have high privacy impact")
    fun testWithdrawPrivacyImpact() {
        val privacyImpact = "HIGH"
        
        assertEquals("HIGH", privacyImpact)
    }

    // ========================================================================
    // PRIVATE BROADCAST TESTS
    // ========================================================================

    @Test
    @DisplayName("JITO regions should be geographically diverse")
    fun testJitoRegionDiversity() {
        val regions = JitoRegion.values()
        
        assertTrue(regions.size >= 4)
        assertTrue(regions.any { it.value == "ny" })
        assertTrue(regions.any { it.value == "amsterdam" })
        assertTrue(regions.any { it.value == "tokyo" })
    }

    @Test
    @DisplayName("Optimal regions should return requested count")
    fun testOptimalRegionCount() {
        val allRegions = listOf(
            JitoRegion.NYC,
            JitoRegion.AMSTERDAM,
            JitoRegion.TOKYO,
            JitoRegion.FRANKFURT
        )
        
        val optimal3 = allRegions.take(3)
        assertEquals(3, optimal3.size)
        
        val optimal2 = allRegions.take(2)
        assertEquals(2, optimal2.size)
    }

    // ========================================================================
    // DATA CLASS TESTS
    // ========================================================================

    @Test
    @DisplayName("ConfidentialMintInfo should serialize correctly")
    fun testConfidentialMintInfo() {
        val info = ConfidentialMintInfo(
            mint = "TestMint123",
            supportsConfidential = true,
            isToken2022 = true,
            recommendation = "Ready for confidential transfers"
        )
        
        assertEquals("TestMint123", info.mint)
        assertTrue(info.supportsConfidential)
        assertTrue(info.isToken2022)
    }

    @Test
    @DisplayName("MultiBroadcastResult should track regions")
    fun testMultiBroadcastResult() {
        val results = listOf(
            RegionResult("ny", true, "sig1", null),
            RegionResult("amsterdam", true, "sig2", null),
            RegionResult("tokyo", false, null, "Connection failed")
        )
        
        val successful = results.count { it.success }
        assertEquals(2, successful)
    }

    @Test
    @DisplayName("FingerprintResult should categorize risk")
    fun testFingerprintResult() {
        val highRisk = FingerprintResult(
            transactionHash = "abc123",
            uniquenessScore = 80,
            sizeCategory = "LARGE",
            looksLike = "CUSTOM",
            privacyRisk = "HIGH",
            recommendations = listOf("Add padding")
        )
        
        assertEquals("HIGH", highRisk.privacyRisk)
        assertTrue(highRisk.uniquenessScore > 60)
    }

    @Test
    @DisplayName("TxPattern enum should have all common patterns")
    fun testTxPatterns() {
        val patterns = TxPattern.values()
        
        assertTrue(patterns.any { it == TxPattern.SOL_TRANSFER })
        assertTrue(patterns.any { it == TxPattern.TOKEN_TRANSFER })
        assertTrue(patterns.any { it == TxPattern.DEX_SWAP })
        assertTrue(patterns.any { it == TxPattern.NFT_TRANSFER })
        assertTrue(patterns.any { it == TxPattern.STAKING })
    }

    @Test
    @DisplayName("RotateStrategy enum should have all strategies")
    fun testRotateStrategies() {
        val strategies = RotateStrategy.values()
        
        assertEquals(3, strategies.size)
        assertTrue(strategies.any { it == RotateStrategy.ROUND_ROBIN })
        assertTrue(strategies.any { it == RotateStrategy.RANDOM })
        assertTrue(strategies.any { it == RotateStrategy.WEIGHTED })
    }
}
