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

    @Test
    @EnabledIfEnvironmentVariable(named = "HELIUS_API_KEY", matches = ".+")
    fun `Example - Standard RPC (Get Balance)`() {
        runBlocking {
            println("--- Starting Get Balance Example ---")
            val wallet = "86xCnPeV69n6t3DnyGvkKobf9FdN2H9oiVDdaMpo2MMY" // Helius official wallet
            try {
                val response = client.solana.getBalance(wallet)
                println("Balance for $wallet: ${response.result}")
            } catch (e: Exception) {
                println("Exception: ${e.message}")
            }
            println("--- End Get Balance Example ---")
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "HELIUS_API_KEY", matches = ".+")
    fun `Example - Enhanced RPC (Get Transactions)`() {
        runBlocking {
            println("--- Starting Enhanced Transactions Example ---")
            val wallet = "86xCnPeV69n6t3DnyGvkKobf9FdN2H9oiVDdaMpo2MMY"
            try {
                val response = client.rpc.getTransactionsForAddress(
                    address = wallet,
                    limit = 5
                )
                println("Found ${response.result?.jsonArray?.size} transactions")
            } catch (e: Exception) {
                println("Exception: ${e.message}")
            }
            println("--- End Enhanced Transactions Example ---")
        }
    }

    @Test
    fun `Example - LaserStream Config`() {
        println("--- Starting LaserStream Config Example ---")
        val endpoint = client.laser.getDefaultEndpoint()
        val token = client.laser.getAuthToken()
        println("LaserStream Endpoint: $endpoint")
        println("LaserStream Token: ${token.take(5)}...")
        println("--- End LaserStream Config Example ---")
    }
}

/**
 * Main entry point for running examples directly.
 */
fun main() = runBlocking {
    println("Running LunaSDK Examples...")
    val test = ExampleTest()
    
    // Manually invoke examples (ignoring JUnit annotations for main execution)
    // Note: Ensure HELIUS_API_KEY is set or hardcoded in ExampleTest
    
    test.`Example - Get Asset (DAS)`()
    test.`Example - Get Priority Fee Estimate`()
    test.`Example - Get Sender Tip Floor`()
    test.`Example - Standard RPC (Get Balance)`()
    test.`Example - Enhanced RPC (Get Transactions)`()
    test.`Example - LaserStream Config`()
    
    println("All examples finished.")
}
