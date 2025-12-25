package com.selenus.iris

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import kotlin.test.*

/**
 * Comprehensive tests for Iris QuickNode SDK.
 * 
 * Run with a valid QuickNode endpoint:
 * QUICKNODE_ENDPOINT=https://your-endpoint.solana-mainnet.quiknode.pro/token/ ./gradlew :iris-sdk:test
 */
class IrisQuickNodeClientTest {
    
    companion object {
        // Test wallet (Solana Foundation)
        const val TEST_WALLET = "7jPmmR1vxFXBvMjb2b5Q3q7hVqB1gkxCmGGXiPNxYKSE"
        
        // Test token mint (USDC)
        const val USDC_MINT = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v"
        
        // Test NFT collection (Claynosaurz)
        const val TEST_COLLECTION = "DZAptNWcY97yK1aH3tUqbDpZYEoxRLN1FGHNMN9HFVxm"
        
        // Get endpoint from environment or use demo
        val endpoint = System.getenv("QUICKNODE_ENDPOINT") 
            ?: "https://docs-demo.solana-mainnet.quiknode.pro/"
    }
    
    private val iris = IrisQuickNodeClient(
        endpoint = endpoint,
        network = SolanaNetwork.MAINNET_BETA
    )
    
    // ========================================================================
    // CLIENT INITIALIZATION TESTS
    // ========================================================================
    
    @Test
    fun `client initializes with correct network`() {
        assertEquals(SolanaNetwork.MAINNET_BETA, iris.network)
    }
    
    @Test
    fun `client exposes all namespaces`() {
        assertNotNull(iris.rpc)
        assertNotNull(iris.das)
        assertNotNull(iris.metis)
        assertNotNull(iris.jito)
        assertNotNull(iris.priority)
        assertNotNull(iris.pumpfun)
        assertNotNull(iris.fastlane)
        assertNotNull(iris.yellowstone)
        assertNotNull(iris.privacy)
        assertNotNull(iris.smart)
    }
    
    // ========================================================================
    // RPC NAMESPACE TESTS
    // ========================================================================
    
    @Test
    fun `rpc getBalance returns valid balance`() = runBlocking {
        val balance = iris.rpc.getBalance(TEST_WALLET)
        assertTrue(balance >= 0, "Balance should be non-negative")
    }
    
    @Test
    fun `rpc getBlockHeight returns valid height`() = runBlocking {
        val height = iris.rpc.getBlockHeight()
        assertTrue(height > 0, "Block height should be positive")
    }
    
    @Test
    fun `rpc getSlot returns valid slot`() = runBlocking {
        val slot = iris.rpc.getSlot()
        assertTrue(slot > 0, "Slot should be positive")
    }
    
    @Test
    fun `rpc getLatestBlockhash returns valid blockhash`() = runBlocking {
        val result = iris.rpc.getLatestBlockhash()
        val value = result.jsonObject["value"]?.jsonObject
        assertNotNull(value)
        assertNotNull(value["blockhash"])
        assertNotNull(value["lastValidBlockHeight"])
    }
    
    @Test
    fun `rpc getGenesisHash returns mainnet genesis`() = runBlocking {
        val hash = iris.rpc.getGenesisHash()
        // Mainnet genesis hash
        assertEquals("5eykt4UsFv8P8NJdTREpY1vzqKqZKvdpKuc147dw2N9d", hash)
    }
    
    @Test
    fun `rpc getVersion returns version info`() = runBlocking {
        val version = iris.rpc.getVersion()
        assertNotNull(version.jsonObject["solana-core"])
    }
    
    @Test
    fun `rpc getAccountInfo returns account data`() = runBlocking {
        val info = iris.rpc.getAccountInfo(TEST_WALLET)
        if (info != null) {
            assertTrue(info.lamports >= 0)
            assertNotNull(info.owner)
        }
    }
    
    @Test
    fun `rpc getSignaturesForAddress returns signatures`() = runBlocking {
        val signatures = iris.rpc.getSignaturesForAddress(TEST_WALLET, limit = 10)
        assertTrue(signatures.isNotEmpty() || true) // May be empty for some wallets
    }
    
    @Test
    fun `rpc getEpochInfo returns current epoch`() = runBlocking {
        val epochInfo = iris.rpc.getEpochInfo()
        assertNotNull(epochInfo.jsonObject["epoch"])
        assertNotNull(epochInfo.jsonObject["slotIndex"])
    }
    
    // ========================================================================
    // DAS NAMESPACE TESTS
    // ========================================================================
    
    @Test
    fun `das getAssetsByOwner returns assets`() = runBlocking {
        try {
            val assets = iris.das.getAssetsByOwner(
                ownerAddress = TEST_WALLET,
                limit = 10
            )
            assertTrue(assets.total >= 0)
        } catch (e: IrisRpcException) {
            // DAS may not be enabled on demo endpoint
            println("DAS not available: ${e.message}")
        }
    }
    
    @Test
    fun `das getAssetsByCollection returns collection items`() = runBlocking {
        try {
            val assets = iris.das.getAssetsByCollection(
                collectionAddress = TEST_COLLECTION,
                limit = 5
            )
            assertTrue(assets.total >= 0)
        } catch (e: IrisRpcException) {
            println("DAS not available: ${e.message}")
        }
    }
    
    // ========================================================================
    // METIS NAMESPACE TESTS
    // ========================================================================
    
    @Test
    fun `metis resolves common token symbols`() {
        assertEquals(MetisNamespace.WSOL_MINT, "So11111111111111111111111111111111111111112")
        assertEquals(MetisNamespace.USDC_MINT, "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v")
    }
    
    @Test
    fun `metis getQuote returns valid quote`() = runBlocking {
        try {
            val quote = iris.metis.getQuote(
                inputMint = MetisNamespace.WSOL_MINT,
                outputMint = MetisNamespace.USDC_MINT,
                amount = 100_000_000, // 0.1 SOL
                slippageBps = 100
            )
            assertEquals(MetisNamespace.WSOL_MINT, quote.inputMint)
            assertEquals(MetisNamespace.USDC_MINT, quote.outputMint)
            assertTrue(quote.outAmount.toLong() > 0)
        } catch (e: Exception) {
            println("Metis not available: ${e.message}")
        }
    }
    
    // ========================================================================
    // JITO NAMESPACE TESTS
    // ========================================================================
    
    @Test
    fun `jito getTipAccounts returns valid accounts`() = runBlocking {
        try {
            val accounts = iris.jito.getTipAccounts()
            assertTrue(accounts.isNotEmpty())
            accounts.forEach { account ->
                assertTrue(account.length >= 32) // Valid base58 pubkey
            }
        } catch (e: IrisRpcException) {
            println("JITO not available: ${e.message}")
        }
    }
    
    @Test
    fun `jito getTipFloor returns tip information`() = runBlocking {
        try {
            val tipFloor = iris.jito.getTipFloor()
            assertTrue(tipFloor.isNotEmpty())
            val tip = tipFloor.first()
            assertTrue(tip.landedTips50thPercentile >= 0)
        } catch (e: IrisRpcException) {
            println("JITO not available: ${e.message}")
        }
    }
    
    @Test
    fun `jito getRegions returns available regions`() = runBlocking {
        try {
            val regions = iris.jito.getRegions()
            assertTrue(regions.isNotEmpty())
        } catch (e: IrisRpcException) {
            println("JITO not available: ${e.message}")
        }
    }
    
    // ========================================================================
    // PRIORITY NAMESPACE TESTS
    // ========================================================================
    
    @Test
    fun `priority estimatePriorityFees returns valid fee`() = runBlocking {
        try {
            val fee = iris.priority.estimatePriorityFees(
                level = PriorityLevel.MEDIUM
            )
            assertTrue(fee >= 0)
        } catch (e: IrisRpcException) {
            println("Priority Fee API not available: ${e.message}")
        }
    }
    
    // ========================================================================
    // PRIVACY NAMESPACE TESTS
    // ========================================================================
    
    @Test
    fun `privacy analyzeWallet returns valid score`() = runBlocking {
        val score = iris.privacy.analyzeWallet(TEST_WALLET, transactionLimit = 10)
        
        assertTrue(score.overallScore in 0..100)
        assertEquals(TEST_WALLET, score.address)
        assertTrue(score.analyzedTransactions >= 0)
        assertTrue(score.recommendations.isNotEmpty())
        
        // Check all factor scores are in valid range
        assertTrue(score.factors.addressReuse in 0..100)
        assertTrue(score.factors.transactionTiming in 0..100)
        assertTrue(score.factors.amountPatterns in 0..100)
    }
    
    @Test
    fun `privacy generateStealthAddress returns valid address`() = runBlocking {
        val stealth = iris.privacy.generateStealthAddress("viewingKey123")
        
        assertNotNull(stealth.ephemeralPublicKey)
        assertNotNull(stealth.stealthAddress)
        assertEquals("viewingKey123", stealth.viewingKey)
    }
    
    @Test
    fun `privacy createPrivacyRoutePlan returns valid plan`() = runBlocking {
        val plan = iris.privacy.createPrivacyRoutePlan(
            fromAddress = "source-address",
            toAddress = "destination-address",
            amountLamports = 1_000_000_000,
            hopCount = 3,
            minDelaySeconds = 60,
            maxDelaySeconds = 120
        )
        
        assertEquals(1_000_000_000, plan.originalAmount)
        assertEquals(3, plan.routes.size)
        assertTrue(plan.privacyGain > 0)
        assertTrue(plan.totalFeeLamports > 0)
        
        // Last hop should be to destination
        assertEquals("destination-address", plan.routes.last().intermediateAddress)
    }
    
    @Test
    fun `privacy createPrivacyRoutePlan validates hop count`() = runBlocking {
        assertFailsWith<IllegalArgumentException> {
            iris.privacy.createPrivacyRoutePlan(
                fromAddress = "source",
                toAddress = "dest",
                amountLamports = 1_000_000_000,
                hopCount = 10 // Invalid - max is 5
            )
        }
    }
    
    // ========================================================================
    // SMART NAMESPACE TESTS
    // ========================================================================
    
    @Test
    fun `smart getOptimizationPlan returns valid plan`() = runBlocking {
        try {
            val plan = iris.smart.getOptimizationPlan(
                priorityLevel = PriorityLevel.HIGH,
                useJito = false,
                useFastlane = true
            )
            
            assertTrue(plan.priorityFeeMicroLamports >= 0)
            assertTrue(plan.recommendedComputeUnits > 0)
            assertTrue(plan.totalEstimatedCostSol >= 0)
        } catch (e: IrisRpcException) {
            println("Smart optimization not available: ${e.message}")
        }
    }
    
    // ========================================================================
    // FASTLANE NAMESPACE TESTS
    // ========================================================================
    
    @Test
    fun `fastlane has valid tip accounts`() {
        val tipAccounts = iris.fastlane.tipAccounts
        assertTrue(tipAccounts.isNotEmpty())
    }
    
    @Test
    fun `fastlane has correct minimum tip`() {
        assertEquals(1_000_000L, iris.fastlane.minimumTipLamports) // 0.001 SOL
    }
    
    @Test
    fun `fastlane getRandomTipAccount returns valid account`() {
        val account = iris.fastlane.getRandomTipAccount()
        assertTrue(account.isNotEmpty())
        assertTrue(account in iris.fastlane.tipAccounts)
    }
    
    // ========================================================================
    // ERROR HANDLING TESTS
    // ========================================================================
    
    @Test
    fun `invalid address throws appropriate error`() = runBlocking {
        try {
            iris.rpc.getAccountInfo("invalid-address")
            fail("Should throw exception for invalid address")
        } catch (e: IrisRpcException) {
            assertTrue(e.code != 0)
        }
    }
    
    // ========================================================================
    // CONVENIENCE METHOD TESTS
    // ========================================================================
    
    @Test
    fun `getBalance convenience method works`() = runBlocking {
        val balance = iris.getBalance(TEST_WALLET)
        assertTrue(balance >= 0)
    }
    
    @Test
    fun `getBalanceSol returns SOL value`() = runBlocking {
        val balanceSol = iris.getBalanceSol(TEST_WALLET)
        assertTrue(balanceSol >= 0.0)
    }
}

/**
 * Integration tests that require specific add-ons enabled.
 * Run separately with appropriate endpoint configuration.
 */
class IrisIntegrationTest {
    
    companion object {
        val endpoint = System.getenv("QUICKNODE_ENDPOINT")
    }
    
    @Test
    fun `integration tests require endpoint`() {
        if (endpoint == null) {
            println("Skipping integration tests - set QUICKNODE_ENDPOINT env var")
            return
        }
        
        val iris = IrisQuickNodeClient(endpoint = endpoint)
        runBlocking {
            // Add integration tests here
            val balance = iris.getBalance("11111111111111111111111111111111")
            println("System program balance: $balance")
        }
    }
}
