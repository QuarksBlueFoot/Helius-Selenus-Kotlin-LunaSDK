package com.selenus.luna

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertNotNull

class DevnetPreviewTest {

    // Attempts to read from environment variable, falls back to placeholder
    private val apiKey = System.getenv("HELIUS_API_KEY") ?: "REDACTED_API_KEY"
    private val client = LunaHeliusClient(apiKey, Cluster.DEVNET)
    
    // Publicly known Devnet wallet for testing
    // Seed: 2jNmruSprMRuBSuyT9LzWQ9Ar853WDyhYppmMZPtZ665
    // Note: Since we lack a crypto library in this SDK, we cannot derive the public key dynamically 
    // or sign transactions in this test suite yet. 
    // Using a known implementation or placeholder for address-based tests would be required.

    @Test
    fun testGetDevnetSlot() = runBlocking {
        if (apiKey == "REDACTED_API_KEY") {
            println("Skipping testGetDevnetSlot: No API Key")
            return@runBlocking
        }
        println("Testing Devnet getSlot...")
        val response = client.rpcCall("getSlot", kotlinx.serialization.json.JsonArray(emptyList()))
        println("Devnet Slot: ${response.result}")
        assertNotNull(response.result, "Slot should not be null")
    }

    @Test
    fun testGetDevnetTPS() = runBlocking {
         if (apiKey == "REDACTED_API_KEY") {
            println("Skipping testGetDevnetTPS: No API Key")
            return@runBlocking
        }
        println("Testing Devnet getTPS...")
        val response = client.niche.getTPS()
        println("Devnet TPS: ${response.result}")
        assertNotNull(response.result, "TPS should not be null")
    }
}
