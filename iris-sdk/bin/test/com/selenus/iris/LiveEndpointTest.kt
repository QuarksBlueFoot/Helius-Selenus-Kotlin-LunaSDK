package com.selenus.iris

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Live endpoint tests - run against your QuickNode endpoint.
 */
class LiveEndpointTest {
    
    private val endpoint = "https://twilight-capable-log.solana-mainnet.quiknode.pro/90788d8c2f1776de628db8e5ea00faff5d4207d5/"
    
    private val client = IrisQuickNodeClient(
        endpoint = endpoint,
        network = SolanaNetwork.MAINNET_BETA
    )
    
    // Known mainnet addresses for testing
    private val solanaFoundation = "CuieVDEDtLo7FypA9SbLM9saXFdb1dsshEkyErMqkRQq"
    private val usdcMint = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v"
    
    @Test
    fun `test getSlot returns valid slot`() = runBlocking {
        val slot = client.rpc.getSlot()
        println("✅ Current slot: $slot")
        assertTrue(slot > 200_000_000, "Slot should be > 200M on mainnet")
    }
    
    @Test
    fun `test getBlockHeight returns valid height`() = runBlocking {
        val height = client.rpc.getBlockHeight()
        println("✅ Block height: $height")
        assertTrue(height > 200_000_000, "Block height should be > 200M on mainnet")
    }
    
    @Test
    fun `test getLatestBlockhash returns valid blockhash`() = runBlocking {
        val result = client.rpc.getLatestBlockhash()
        println("✅ Latest blockhash result: $result")
        assertNotNull(result)
    }
    
    @Test
    fun `test getBalance returns SOL balance`() = runBlocking {
        val balance = client.rpc.getBalance(solanaFoundation)
        println("✅ Solana Foundation balance: $balance lamports")
        println("   Balance in SOL: ${balance / 1_000_000_000.0} SOL")
        assertTrue(balance >= 0, "Balance should be non-negative")
    }
    
    @Test
    fun `test getBalanceSol convenience method`() = runBlocking {
        val solBalance = client.getBalanceSol(solanaFoundation)
        println("✅ Solana Foundation balance: $solBalance SOL")
        assertTrue(solBalance >= 0.0, "SOL balance should be non-negative")
    }
    
    @Test
    fun `test getGenesisHash returns mainnet genesis`() = runBlocking {
        val genesis = client.rpc.getGenesisHash()
        println("✅ Genesis hash: $genesis")
        assertEquals(
            "5eykt4UsFv8P8NJdTREpY1vzqKqZKvdpKuc147dw2N9d",
            genesis,
            "Should be mainnet genesis hash"
        )
    }
    
    @Test
    fun `test getEpochInfo returns epoch details`() = runBlocking {
        val epochInfo = client.rpc.getEpochInfo()
        println("✅ Epoch info: $epochInfo")
        assertNotNull(epochInfo)
    }
    
    @Test
    fun `test getVersion returns node version`() = runBlocking {
        val version = client.rpc.getVersion()
        println("✅ Solana version: $version")
        assertNotNull(version)
    }
    
    @Test
    fun `test getHealth returns ok`() = runBlocking {
        val health = client.rpc.getHealth()
        println("✅ Node health: $health")
        assertEquals("ok", health, "Node should be healthy")
    }
    
    @Test
    fun `test getTokenSupply for USDC`() = runBlocking {
        val supply = client.rpc.getTokenSupply(usdcMint)
        println("✅ USDC supply: ${supply.uiAmountString}")
        println("   Decimals: ${supply.decimals}")
        assertEquals(6, supply.decimals, "USDC has 6 decimals")
        assertTrue(supply.uiAmount ?: 0.0 > 1_000_000_000, "USDC supply should be > 1B")
    }
    
    @Test
    fun `test priority fee estimation`() = runBlocking {
        val fees = client.priority.estimatePriorityFees()
        println("✅ Priority fees: $fees")
        assertNotNull(fees)
    }
    
    @Test
    fun `test DAS getAssetsByOwner`() = runBlocking {
        val assets = client.das.getAssetsByOwner(solanaFoundation)
        println("✅ Solana Foundation assets: ${assets.total} total")
        println("   Returned items: ${assets.items.size}")
        assets.items.take(3).forEach { asset ->
            println("   - ${asset.id}: ${asset.content?.metadata?.name ?: "unnamed"}")
        }
    }
    
    @Test
    fun `test Metis Jupiter quote SOL to USDC`() = runBlocking {
        try {
            val quote = client.metis.getQuote(
                inputMint = MetisNamespace.WSOL_MINT,
                outputMint = MetisNamespace.USDC_MINT,
                amount = 1_000_000_000 // 1 SOL
            )
            println("✅ Jupiter quote for 1 SOL -> USDC:")
            println("   Input: ${quote.inAmount} lamports")
            println("   Output: ${quote.outAmount} USDC (raw)")
            println("   Output in USDC: ${quote.outAmount.toLong() / 1_000_000.0}")
            println("   Price impact: ${quote.priceImpactPct}%")
            println("   Route hops: ${quote.routePlan?.size ?: 0}")
            
            val outputUsdc = quote.outAmount.toLong() / 1_000_000.0
            assertTrue(outputUsdc > 50, "1 SOL should be worth > $50 USDC")
        } catch (e: Exception) {
            println("⚠️ Metis not available (Jupiter add-on may not be enabled): ${e.message}")
        }
    }
    
    @Test
    fun `test JITO tip floor`() = runBlocking {
        try {
            val tipFloors = client.jito.getTipFloor()
            println("✅ JITO Tip Floor information:")
            tipFloors.forEach { tip ->
                println("   Time: ${tip.time}")
                println("   25th percentile: ${tip.landedTips25thPercentile}")
                println("   50th percentile: ${tip.landedTips50thPercentile}")
                println("   75th percentile: ${tip.landedTips75thPercentile}")
            }
        } catch (e: Exception) {
            println("⚠️ JITO not available (Lil' JIT add-on may not be enabled): ${e.message}")
        }
    }
    
    @Test
    fun `test privacy wallet analysis`() = runBlocking {
        val score = client.privacy.analyzeWallet(solanaFoundation)
        println("✅ Privacy analysis for Solana Foundation wallet:")
        println("   Overall score: ${score.overallScore}/100")
        println("   Factors: ${score.factors}")
        println("   Recommendations: ${score.recommendations.take(3)}")
        
        assertTrue(score.overallScore in 0..100, "Score should be 0-100")
    }
    
    @Test
    fun `test multiple accounts lookup`() = runBlocking {
        val addresses = listOf(
            solanaFoundation,
            "7cVfgArCheMR6Cs4t6vz5rfnqd56vZq4ndaBrY5xkxXy" // Another known address
        )
        val accounts = client.rpc.getMultipleAccounts(addresses)
        println("✅ Multiple accounts lookup:")
        accounts.forEachIndexed { i, account ->
            if (account != null) {
                println("   ${addresses[i]}: ${account.lamports} lamports, owner: ${account.owner}")
            } else {
                println("   ${addresses[i]}: not found")
            }
        }
    }
}
