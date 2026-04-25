package xyz.selenus.luna

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StandardRpcTest {

    private val apiKey = System.getenv("HELIUS_API_KEY") ?: "REDACTED_API_KEY"
    private val client = LunaHeliusClient(apiKey, Cluster.MAINNET)
    private val testAddress = "86xCnPeV69n6t3DnyGvkKobf9FdN2H9oiVDdaMpo2MMY"

    private fun skipIfNoKey(): Boolean {
        if (apiKey == "REDACTED_API_KEY") {
            println("Skipping test: No API Key")
            return true
        }
        return false
    }

    @Test
    fun testGetAccountInfo() = runBlocking {
        if (skipIfNoKey()) return@runBlocking
        println("Testing getAccountInfo...")
        val response = client.solana.getAccountInfo(testAddress)
        println("Account Info: ${response.result}")
        assertNotNull(response.result, "Account Info should not be null")
    }

    @Test
    fun testGetBalance() = runBlocking {
        if (skipIfNoKey()) return@runBlocking
        println("Testing getBalance...")
        val response = client.solana.getBalance(testAddress)
        println("Balance: ${response.result}")
        assertNotNull(response.result, "Balance should not be null")
    }

    @Test
    fun testGetBlockHeight() = runBlocking {
        if (skipIfNoKey()) return@runBlocking
        println("Testing getBlockHeight...")
        val response = client.solana.getBlockHeight()
        println("Block Height: ${response.result}")
        assertNotNull(response.result, "Block Height should not be null")
    }

    @Test
    fun testGetLatestBlockhash() = runBlocking {
        if (skipIfNoKey()) return@runBlocking
        println("Testing getLatestBlockhash...")
        val response = client.solana.getLatestBlockhash()
        println("Latest Blockhash: ${response.result}")
        assertNotNull(response.result, "Latest Blockhash should not be null")
    }

    @Test
    fun testGetEpochInfo() = runBlocking {
        if (skipIfNoKey()) return@runBlocking
        println("Testing getEpochInfo...")
        val response = client.solana.getEpochInfo()
        println("Epoch Info: ${response.result}")
        assertNotNull(response.result, "Epoch Info should not be null")
    }

    @Test
    fun testGetHealth() = runBlocking {
        if (skipIfNoKey()) return@runBlocking
        println("Testing getHealth...")
        val response = client.solana.getHealth()
        println("Health: ${response.result}")
        assertNotNull(response.result, "Health should not be null")
        // Usually returns "ok" string wrapped in JSON
        val healthStr = response.result.toString()
        assertTrue(healthStr.contains("ok", ignoreCase = true), "Health should be 'ok'")
    }

    @Test
    fun testGetGenesisHash() = runBlocking {
        if (skipIfNoKey()) return@runBlocking
        println("Testing getGenesisHash...")
        val response = client.solana.getGenesisHash()
        println("Genesis Hash: ${response.result}")
        assertNotNull(response.result, "Genesis Hash should not be null")
    }
}
