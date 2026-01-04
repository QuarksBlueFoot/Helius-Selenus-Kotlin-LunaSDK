package com.selenus.luna

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

class LiveIntegrationTest {

    // Using the API key from the sample app
    private val apiKey = "REDACTED_API_KEY"
    private val client = LunaHeliusClient(apiKey, Cluster.MAINNET)

    @Test
    fun testGetSlot() = runBlocking {
        println("Testing getSlot...")
        val response = client.rpcCall("getSlot", kotlinx.serialization.json.JsonArray(emptyList()))
        println("Slot: ${response.result}")
        assertNotNull(response.result, "Slot should not be null")
    }

    @Test
    fun testGetAsset() = runBlocking {
        println("Testing getAsset...")
        // Asset ID from FeatureRegistry
        val assetId = "F9Lw3ki3hJ7PF9HQXsBzoY8GyE6sPoEZZdXJBsTTD2rk"
        val response = client.das.getAsset(assetId)
        println("Asset: ${response.result}")
        assertNotNull(response.result, "Asset result should not be null")
    }

    @Test
    fun testGetAssetsByOwner() = runBlocking {
        println("Testing getAssetsByOwner...")
        // Owner from FeatureRegistry
        val owner = "86xCnPeV69n6t3DnyGvkKobf9FdN2H9oiVDdaMpo2MMY"
        val response = client.das.getAssetsByOwner(owner, page = 1, limit = 5)
        println("Assets: ${response.result}")
        assertNotNull(response.result, "Assets result should not be null")
    }

    @Test
    fun testGetTPS() = runBlocking {
        println("Testing getTPS...")
        val response = client.niche.getTPS()
        println("TPS: ${response.result}")
        assertNotNull(response.result, "TPS should not be null")
        assertTrue(response.result!! > 0, "TPS should be positive")
    }

    @Test
    fun testMobilePaymentLink() {
        println("Testing generatePaymentLink...")
        val link = client.mobile.generatePaymentLink(
            recipient = "86xCnPeV69n6t3DnyGvkKobf9FdN2H9oiVDdaMpo2MMY",
            amount = 1.5,
            label = "Test Order",
            message = "Thanks for the fish"
        )
        println("Link: $link")
        assertTrue(link.startsWith("solana:"), "Link should start with solana:")
        assertTrue(link.contains("amount=1.5"), "Link should contain amount")
    }
}
