package xyz.selenus.luna

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AdvancedRpcTest {

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
    fun testGetIndexerHealth() = runBlocking {
        if (skipIfNoKey()) return@runBlocking
        println("Testing zk.getIndexerHealth...")
        val response = client.zk.getIndexerHealth()
        println("Indexer Health: ${response.result}")
        assertNotNull(response.result, "Indexer Health should not be null")
    }

    @Test
    fun testGetPriorityFeeEstimate() = runBlocking {
        if (skipIfNoKey()) return@runBlocking
        println("Testing priority.getPriorityFeeEstimate...")
        // Using the test address as a dummy account key for estimation
        val response = client.priority.getPriorityFeeEstimate(
            accountKeys = listOf(testAddress),
            priorityLevel = "High"
        )
        println("Priority Fee Estimate: ${response.result}")
        assertNotNull(response.result, "Priority Fee Estimate should not be null")
    }

    @Test
    fun testGetEnhancedTransactions() = runBlocking {
        if (skipIfNoKey()) return@runBlocking
        println("Testing enhanced.getTransactionsByAddress...")
        try {
            val response = client.enhanced.getTransactionsByAddress(
                address = testAddress,
                limit = 1
            )
            println("Enhanced Transactions: ${response.result}")
            assertNotNull(response.result, "Enhanced Transactions should not be null")
        } catch (e: Exception) {
            println("FAILED with error: ${e.message}")
            throw e
        }
    }

    @Test
    fun testGetCompressedAccountsByOwner() = runBlocking {
        if (skipIfNoKey()) return@runBlocking
        println("Testing zk.getCompressedAccountsByOwner...")
        // Even if the address has no compressed accounts, it should return a valid empty list or result
        val response = client.zk.getCompressedAccountsByOwner(testAddress)
        println("Compressed Accounts by Owner: ${response.result}")
        assertNotNull(response.result, "Compressed Accounts response should not be null")
    }

    @Test
    fun testSolanaMiscRpc() = runBlocking {
        if (skipIfNoKey()) return@runBlocking
        println("Testing solana.getSlot...")
        val slotResp = client.solana.getSlot()
        assertNotNull(slotResp.result, "Slot should not be null")
        println("Slot: ${slotResp.result}")

        println("Testing solana.getSupply...")
        val supplyResp = client.solana.getSupply()
        assertNotNull(supplyResp.result, "Supply should not be null")
        println("Supply: ${supplyResp.result}")

        println("Testing solana.getClusterNodes...")
        val nodesResp = client.solana.getClusterNodes()
        assertNotNull(nodesResp.result, "Cluster Nodes should not be null")
        // No full print, too large
    }
}
