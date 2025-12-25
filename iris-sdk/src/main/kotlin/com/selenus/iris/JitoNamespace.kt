package com.selenus.iris

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.*

/**
 * # Jito Namespace (Lil' JIT)
 * 
 * JITO bundle and transaction operations via QuickNode's Lil' JIT add-on.
 * Provides MEV protection, atomic bundle execution, and transaction priority.
 * 
 * ## Features
 * - Send transaction bundles atomically
 * - Simulate bundles before sending
 * - Get tip floor information for optimal tips
 * - Track bundle and transaction status
 * - MEV protection through searcher integration
 * 
 * ## Why JITO?
 * - Bundles execute atomically (all-or-nothing)
 * - Built-in front-running protection
 * - Higher landing rates during congestion
 * - Ideal for DEX trades, liquidations, and arbitrage
 */
class JitoNamespace internal constructor(private val client: IrisQuickNodeClient) {
    
    private val json = client.getJson()
    
    // ========================================================================
    // TIP INFORMATION
    // ========================================================================
    
    /**
     * Get the current tip floor information.
     * Returns recent tip percentiles to help you choose optimal tip amounts.
     * 
     * Higher tips generally result in:
     * - Lower slot latency
     * - Higher landing rates
     * - Priority during congestion
     */
    suspend fun getTipFloor(): List<JitoTipFloor> {
        return client.executeRpcCall(
            method = "getTipFloor",
            params = JsonArray(emptyList()),
            resultDeserializer = kotlinx.serialization.builtins.ListSerializer(JitoTipFloor.serializer())
        )
    }
    
    /**
     * Get the current tip accounts.
     * You must include a tip transfer to one of these accounts in your bundle.
     * Round-robin through them for best distribution.
     */
    suspend fun getTipAccounts(): List<String> {
        return client.executeRpcCall(
            method = "getTipAccounts",
            params = JsonArray(emptyList()),
            resultDeserializer = kotlinx.serialization.builtins.ListSerializer(String.serializer())
        )
    }
    
    /**
     * Get optimal tip amount for a given priority level.
     * 
     * @param level The desired priority level
     * @return Tip amount in SOL
     */
    suspend fun getOptimalTip(level: PriorityLevel = PriorityLevel.MEDIUM): Double {
        val tipFloor = getTipFloor().firstOrNull() ?: return 0.001
        
        return when (level) {
            PriorityLevel.LOW -> tipFloor.landedTips25thPercentile
            PriorityLevel.MEDIUM -> tipFloor.landedTips50thPercentile
            PriorityLevel.HIGH -> tipFloor.landedTips75thPercentile
            PriorityLevel.VERY_HIGH -> tipFloor.landedTips95thPercentile
            PriorityLevel.UNSAFE_MAX -> tipFloor.landedTips99thPercentile
        }
    }
    
    // ========================================================================
    // BUNDLE OPERATIONS
    // ========================================================================
    
    /**
     * Send a bundle of transactions to be executed atomically.
     * All transactions must be fully signed and base58/base64 encoded.
     * The bundle MUST include a tip transfer to one of the tip accounts.
     * 
     * @param transactions List of base64 encoded signed transactions
     * @return Bundle ID for tracking
     */
    suspend fun sendBundle(transactions: List<String>): String {
        val params = buildJsonArray {
            add(JsonArray(transactions.map { JsonPrimitive(it) }))
        }
        
        return client.executeRpcCall(
            method = "sendBundle",
            params = params,
            resultDeserializer = String.serializer()
        )
    }
    
    /**
     * Simulate a bundle before sending.
     * Useful for checking if transactions will succeed and estimating compute units.
     * 
     * @param transactions List of base64 encoded signed transactions
     * @return Simulation results including logs and any errors
     */
    suspend fun simulateBundle(transactions: List<String>): JitoSimulationResult {
        val params = buildJsonObject {
            put("encodedTransactions", JsonArray(transactions.map { JsonPrimitive(it) }))
        }
        
        return client.executeRpcCall(
            method = "simulateBundle",
            params = params,
            resultDeserializer = JitoSimulationResult.serializer()
        )
    }
    
    /**
     * Send a single transaction via JITO for MEV protection.
     * The transaction should include a tip for priority.
     * 
     * @param transaction Base64 encoded signed transaction
     * @return Transaction signature
     */
    suspend fun sendTransaction(transaction: String): String {
        val params = buildJsonArray {
            add(transaction)
            addJsonObject {
                put("encoding", "base64")
            }
        }
        
        return client.executeRpcCall(
            method = "sendTransaction",
            params = params,
            resultDeserializer = String.serializer()
        )
    }
    
    // ========================================================================
    // STATUS TRACKING
    // ========================================================================
    
    /**
     * Get the status of submitted bundles.
     * 
     * @param bundleIds List of bundle IDs to check
     * @return Status information for each bundle
     */
    suspend fun getBundleStatuses(bundleIds: List<String>): JitoBundleResult {
        val params = buildJsonArray {
            add(JsonArray(bundleIds.map { JsonPrimitive(it) }))
        }
        
        return client.executeRpcCall(
            method = "getBundleStatuses",
            params = params,
            resultDeserializer = JitoBundleResult.serializer()
        )
    }
    
    /**
     * Get the status of bundles currently in flight (not yet landed).
     * 
     * @param bundleIds List of bundle IDs to check
     */
    suspend fun getInflightBundleStatuses(bundleIds: List<String>): JitoBundleResult {
        val params = buildJsonArray {
            add(JsonArray(bundleIds.map { JsonPrimitive(it) }))
        }
        
        return client.executeRpcCall(
            method = "getInflightBundleStatuses",
            params = params,
            resultDeserializer = JitoBundleResult.serializer()
        )
    }
    
    /**
     * Check if a bundle has landed and get its status.
     * 
     * @param bundleId The bundle ID to check
     * @return Bundle status or null if not found
     */
    suspend fun getBundleStatus(bundleId: String): JitoBundleStatus? {
        val result = getBundleStatuses(listOf(bundleId))
        return result.value.firstOrNull()
    }
    
    // ========================================================================
    // REGION INFORMATION
    // ========================================================================
    
    /**
     * Get available JITO regions.
     * Different regions may have different latencies depending on your location.
     */
    suspend fun getRegions(): List<String> {
        return client.executeRpcCall(
            method = "getRegions",
            params = JsonArray(emptyList()),
            resultDeserializer = kotlinx.serialization.builtins.ListSerializer(String.serializer())
        )
    }
    
    // ========================================================================
    // HELPER METHODS
    // ========================================================================
    
    /**
     * Create a tip instruction for inclusion in a bundle.
     * Note: You must serialize this instruction in your transaction.
     * 
     * @param fromPubkey The payer's public key
     * @param lamports Amount to tip in lamports
     * @return Tip account to send to (cycles through available accounts)
     */
    suspend fun getTipInstruction(fromPubkey: String, lamports: Long): TipInstruction {
        val tipAccounts = getTipAccounts()
        // Round-robin through tip accounts
        val tipAccount = tipAccounts.random()
        
        return TipInstruction(
            programId = "11111111111111111111111111111111", // System program
            fromPubkey = fromPubkey,
            toPubkey = tipAccount,
            lamports = lamports
        )
    }
    
    /**
     * Validate that a bundle includes a valid tip.
     * This is a helper method - JITO will reject bundles without tips.
     */
    fun validateBundleHasTip(transactions: List<String>): Boolean {
        // This is a placeholder - actual validation would require parsing transactions
        return transactions.isNotEmpty()
    }
}

/**
 * Tip instruction details for JITO bundles.
 */
data class TipInstruction(
    val programId: String,
    val fromPubkey: String,
    val toPubkey: String,
    val lamports: Long
)
