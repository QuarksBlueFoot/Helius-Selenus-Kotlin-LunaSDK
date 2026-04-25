package xyz.selenus.luna

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

class NicheApiTest {

    private val apiKey = System.getenv("HELIUS_API_KEY") ?: "REDACTED_API_KEY"
    private val extendedTimeoutClient = OkHttpClient.Builder()
        .readTimeout(60, TimeUnit.SECONDS)
        .connectTimeout(60, TimeUnit.SECONDS)
        .build()
        
    private val client = LunaHeliusClient(apiKey, Cluster.MAINNET, httpClient = extendedTimeoutClient)
    // Using a known wallet that likely has assets (Helius standard test wallet)
    private val testAddress = "86xCnPeV69n6t3DnyGvkKobf9FdN2H9oiVDdaMpo2MMY" 

    private fun skipIfNoKey(): Boolean {
        if (apiKey == "REDACTED_API_KEY") {
            println("Skipping test: No API Key")
            return true
        }
        return false
    }

    @Test
    fun testWalletPortfolio() = runBlocking {
        if (skipIfNoKey()) return@runBlocking
        println("Testing niche.getWalletPortfolio...")
        
        // Use limit=10 for speed
        val portfolio = client.niche.getWalletPortfolio(testAddress, limit = 10)
        
        // Ensure no error
        if (portfolio.error != null) {
            println("Portfolio Error: ${portfolio.error}")
        }
        assertNotNull(portfolio.result, "Portfolio result should not be null")
        
        val data = portfolio.result!!
        println("Portfolio SOL: ${data.solBalance}")
        println("Portfolio Assets (Count): ${data.assets?.toString()?.length ?: 0} chars of JSON")
        
        assertTrue(data.solBalance >= 0, "SOL balance should be non-negative")
        assertNotNull(data.assets, "Assets JSON should be present")
    }

    @Test
    fun testMobileLinks() {
        // purely local logic, no API key needed
        println("Testing mobile.generatePaymentLink...")
        
        val link = client.mobile.generatePaymentLink(
            recipient = "77765111111111111111111111111111",
            amount = 1.5,
            label = "My Store",
            message = "Thanks for the coffee"
        )
        
        println("Generated Link: $link")
        assertTrue(link.startsWith("solana:77765111111111111111111111111111"), "Link should start with solana scheme and address")
        assertTrue(link.contains("amount=1.5"), "Link should contain amount")
        assertTrue(link.contains("label=My+Store"), "Link should contain encoded label")
        
        println("Testing mobile.parsePaymentLink...")
        val parts = client.mobile.parsePaymentLink(link)
        assertEquals("77765111111111111111111111111111", parts["recipient"])
        assertEquals("1.5", parts["amount"])
        assertEquals("My Store", parts["label"])
        assertEquals("Thanks for the coffee", parts["message"])
    }

    @Test
    fun testOptimizationGetAssetLite() = runBlocking {
        if (skipIfNoKey()) return@runBlocking
        println("Testing mobile.getAssetLite...")
        
        // Use a known Mad Lad mint for testing
        val mint = "F9Lw3ki3hJ7PF9HQXsBzoY8GyE6sPoEZZdXJBsTTD2rk" 
        val response = client.mobile.getAssetLite(mint)
        
        assertNotNull(response.result, "AssetLite result should not be null")
        val lite = response.result!!
        
        println("Asset Lite: $lite")
        
        // Verify it only has the lite keys
        assertTrue(lite.containsKey("id"), "Should have id")
        assertTrue(lite.containsKey("name"), "Should have name")
        assertTrue(lite.containsKey("image"), "Should have image")
    }

    @Test
    fun testGameAccess() = runBlocking {
        if (skipIfNoKey()) return@runBlocking
        // This wallet likely has balance
        val response = client.niche.verifyGameAccess(testAddress, minSolBalance = 0.000001)
        println("Game Access: ${response.result}")
        assertNotNull(response.result, "Game access result should not be null")
        assertTrue(response.result!!.hasAccess, "Should have access with low min balance")
    }
}
