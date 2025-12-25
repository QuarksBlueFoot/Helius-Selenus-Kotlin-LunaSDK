package com.selenus.luna

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import kotlin.test.assertNotNull

/**
 * Working examples of how to use the LunaSDK.
 * 
 * To run these tests, set the HELIUS_API_KEY environment variable.
 * Example: export HELIUS_API_KEY="your-api-key"
 */
class ExampleTest {

    // Retrieve API key from environment variable, or use a placeholder
    private val apiKey = System.getenv("HELIUS_API_KEY") ?: "YOUR_API_KEY_HERE"
    private val client = LunaHeliusClient(apiKey)

    @Test
    @EnabledIfEnvironmentVariable(named = "HELIUS_API_KEY", matches = ".+")
    fun `Example - Get Asset (DAS)`() {
        runBlocking {
            println("--- Starting Get Asset Example ---")
            // Example Asset ID (Mad Lads #8420)
            val assetId = "F9Lw3ki3hJ7PF9HQXsBzoY8GyE6sPoEZZdXJBsTTD2rk"
            
            try {
                val response = client.das.getAsset(assetId)
                println("Raw Response: $response")
                
                if (response.result != null) {
                    val name = response.result?.jsonObject?.get("content")?.jsonObject?.get("metadata")?.jsonObject?.get("name")?.jsonPrimitive?.content
                    println("Asset Name: $name")
                    assertNotNull(name)
                } else {
                    println("Error: ${response.error}")
                }
            } catch (e: Exception) {
                println("Exception occurred: ${e.message}")
            }
            println("--- End Get Asset Example ---")
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "HELIUS_API_KEY", matches = ".+")
    fun `Example - Get Priority Fee Estimate`() {
        runBlocking {
            println("--- Starting Priority Fee Estimate Example ---")
            try {
                val response = client.priority.getPriorityFeeEstimate(
                    priorityLevel = "High",
                    lookbackSlots = 100
                )
                println("Raw Response: $response")
                
                if (response.result != null) {
                    val estimate = response.result?.jsonObject?.get("priorityFeeEstimate")?.jsonPrimitive?.doubleOrNull
                    println("Priority Fee Estimate (High): $estimate micro-lamports")
                    assertNotNull(estimate)
                } else {
                    println("Error: ${response.error}")
                }
            } catch (e: Exception) {
                println("Exception occurred: ${e.message}")
            }
            println("--- End Priority Fee Estimate Example ---")
        }
    }

    @Test
    fun `Example - Get Sender Tip Floor`() {
        // This test does not require an API Key as it hits Jito directly
        runBlocking {
            println("--- Starting Sender Tip Floor Example ---")
            try {
                val response = client.sender.getSenderTipFloor()
                println("Raw Response: $response")
                
                if (response.result != null) {
                    println("75th Percentile Tip Floor: ${response.result}")
                    assertNotNull(response.result)
                } else {
                    println("Error: ${response.error}")
                }
            } catch (e: Exception) {
                println("Exception occurred: ${e.message}")
            }
            println("--- End Sender Tip Floor Example ---")
        }
    }
}
