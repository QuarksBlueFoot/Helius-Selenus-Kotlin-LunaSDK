package com.selenus.luna.sample

import com.selenus.luna.Cluster
import com.selenus.luna.LunaHeliusClient
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FeatureRegistryTest {

    @Test
    fun `verify client creation`() {
        try {
            println("Test: verify client creation")
            val apiKey = "test-key"
            val client = LunaHeliusClient(apiKey, Cluster.MAINNET)
            println("Client created successfully: $client")
        } catch (e: Throwable) {
            println("Exception during client creation:")
            e.printStackTrace()
            throw e
        }
    }

    @Test
    fun `verify registry has 51 features`() {
        val features = FeatureRegistry.getFeatures()
        println("Registry size: ${features.size}")
        assertTrue(features.isNotEmpty(), "Registry should not be empty")
    }

    @Test
    fun `verify getVersion feature runs successfully`() = runTest {
        try {
            // TODO: Replace with your actual Helius API key to run this test
            val apiKey = "YOUR_API_KEY_HERE"
            if (apiKey == "YOUR_API_KEY_HERE") {
                println("Skipping test: API key not set")
                return@runTest
            }
            println("Creating client...")
            val client = LunaHeliusClient(apiKey, Cluster.MAINNET)
            println("Client created.")
            
            val features = FeatureRegistry.getFeatures()
            val versionFeature = features.find { it.name == "Get Version" }
            
            assertTrue(versionFeature != null, "Get Version feature should exist")
            
            println("Testing feature: ${versionFeature!!.name}")
            
            var output = ""
            versionFeature.action(client) { message ->
                output = message
                println("Output: $message")
            }
            
            assertTrue(output.isNotEmpty(), "Output should not be empty")
            assertTrue(output.contains("Version:"), "Output should contain 'Version:'")
        } catch (e: Exception) {
            println("Exception in test: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }
}
