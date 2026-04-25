package com.selenus.iris

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.*

/**
 * # RPC Namespace
 * 
 * Complete implementation of Solana JSON-RPC methods as supported by QuickNode.
 * Provides all standard Solana RPC operations with type-safe Kotlin interfaces.
 * 
 * ## Categories
 * - Account queries
 * - Balance operations  
 * - Transaction operations
 * - Block operations
 * - Slot operations
 * - Epoch operations
 * - Validator operations
 * - Program operations
 */
class RpcNamespace internal constructor(private val client: IrisQuickNodeClient) {
    
    private val json = client.getJson()
    
    // ========================================================================
    // ACCOUNT METHODS
    // ========================================================================
    
    /**
     * Returns all information associated with the account of the provided pubkey.
     */
    suspend fun getAccountInfo(
        pubkey: String,
        commitment: Commitment = Commitment.FINALIZED,
        encoding: Encoding = Encoding.BASE64
    ): AccountInfo? {
        val params = buildJsonArray {
            add(pubkey)
            addJsonObject {
                put("commitment", commitment.value)
                put("encoding", encoding.value)
            }
        }
        
        val result = client.executeRpcCall(
            method = "getAccountInfo",
            params = params,
            resultDeserializer = AccountInfoWithContext.serializer()
        )
        return result.value
    }
    
    /**
     * Returns the balance of the account of provided pubkey.
     */
    suspend fun getBalance(
        pubkey: String,
        commitment: Commitment = Commitment.FINALIZED
    ): Long {
        val params = buildJsonArray {
            add(pubkey)
            addJsonObject {
                put("commitment", commitment.value)
            }
        }
        
        val result = client.executeRpcCall(
            method = "getBalance",
            params = params,
            resultDeserializer = BalanceResult.serializer()
        )
        return result.value
    }
    
    /**
     * Returns all accounts owned by the provided program pubkey.
     */
    suspend fun getProgramAccounts(
        programId: String,
        commitment: Commitment = Commitment.FINALIZED,
        encoding: Encoding = Encoding.BASE64,
        filters: List<JsonElement>? = null,
        withContext: Boolean = false
    ): JsonElement {
        val params = buildJsonArray {
            add(programId)
            addJsonObject {
                put("commitment", commitment.value)
                put("encoding", encoding.value)
                put("withContext", withContext)
                filters?.let { 
                    put("filters", JsonArray(it))
                }
            }
        }
        
        return client.executeRpcCall(
            method = "getProgramAccounts",
            params = params,
            resultDeserializer = JsonElement.serializer()
        )
    }
    
    /**
     * Returns identity and transaction information about a confirmed block.
     */
    suspend fun getMultipleAccounts(
        pubkeys: List<String>,
        commitment: Commitment = Commitment.FINALIZED,
        encoding: Encoding = Encoding.BASE64
    ): List<AccountInfo?> {
        val params = buildJsonArray {
            add(JsonArray(pubkeys.map { JsonPrimitive(it) }))
            addJsonObject {
                put("commitment", commitment.value)
                put("encoding", encoding.value)
            }
        }
        
        val result = client.executeRpcCall(
            method = "getMultipleAccounts",
            params = params,
            resultDeserializer = JsonElement.serializer()
        )
        
        val values = result.jsonObject["value"]?.jsonArray ?: return emptyList()
        return values.map { elem ->
            if (elem is JsonNull) null
            else json.decodeFromJsonElement(AccountInfo.serializer(), elem)
        }
    }
    
    /**
     * Returns the lamport balance of a token account.
     */
    suspend fun getTokenAccountBalance(
        tokenAccount: String,
        commitment: Commitment = Commitment.FINALIZED
    ): TokenAmount {
        val params = buildJsonArray {
            add(tokenAccount)
            addJsonObject {
                put("commitment", commitment.value)
            }
        }
        
        val result = client.executeRpcCall(
            method = "getTokenAccountBalance",
            params = params,
            resultDeserializer = JsonElement.serializer()
        )
        
        val value = result.jsonObject["value"] ?: throw IrisRpcException(-32600, "Missing value in response")
        return json.decodeFromJsonElement(TokenAmount.serializer(), value)
    }
    
    /**
     * Returns all SPL Token accounts by token owner.
     */
    suspend fun getTokenAccountsByOwner(
        owner: String,
        filter: TokenAccountFilter,
        commitment: Commitment = Commitment.FINALIZED,
        encoding: Encoding = Encoding.JSON_PARSED
    ): JsonElement {
        val params = buildJsonArray {
            add(owner)
            addJsonObject {
                when (filter) {
                    is TokenAccountFilter.ByMint -> put("mint", filter.mint)
                    is TokenAccountFilter.ByProgram -> put("programId", filter.programId)
                }
            }
            addJsonObject {
                put("commitment", commitment.value)
                put("encoding", encoding.value)
            }
        }
        
        return client.executeRpcCall(
            method = "getTokenAccountsByOwner",
            params = params,
            resultDeserializer = JsonElement.serializer()
        )
    }
    
    /**
     * Returns the total supply of an SPL Token type.
     */
    suspend fun getTokenSupply(
        mint: String,
        commitment: Commitment = Commitment.FINALIZED
    ): TokenAmount {
        val params = buildJsonArray {
            add(mint)
            addJsonObject {
                put("commitment", commitment.value)
            }
        }
        
        val result = client.executeRpcCall(
            method = "getTokenSupply",
            params = params,
            resultDeserializer = JsonElement.serializer()
        )
        
        val value = result.jsonObject["value"] ?: throw IrisRpcException(-32600, "Missing value in response")
        return json.decodeFromJsonElement(TokenAmount.serializer(), value)
    }
    
    /**
     * Returns the 20 largest accounts of a particular SPL Token type.
     */
    suspend fun getTokenLargestAccounts(
        mint: String,
        commitment: Commitment = Commitment.FINALIZED
    ): JsonElement {
        val params = buildJsonArray {
            add(mint)
            addJsonObject {
                put("commitment", commitment.value)
            }
        }
        
        return client.executeRpcCall(
            method = "getTokenLargestAccounts",
            params = params,
            resultDeserializer = JsonElement.serializer()
        )
    }
    
    // ========================================================================
    // TRANSACTION METHODS
    // ========================================================================
    
    /**
     * Submits a signed transaction to the cluster for processing.
     */
    suspend fun sendTransaction(
        signedTransaction: String,
        skipPreflight: Boolean = false,
        preflightCommitment: Commitment = Commitment.FINALIZED,
        maxRetries: Int? = null,
        minContextSlot: Long? = null
    ): String {
        val params = buildJsonArray {
            add(signedTransaction)
            addJsonObject {
                put("encoding", "base64")
                put("skipPreflight", skipPreflight)
                put("preflightCommitment", preflightCommitment.value)
                maxRetries?.let { put("maxRetries", it) }
                minContextSlot?.let { put("minContextSlot", it) }
            }
        }
        
        return client.executeRpcCall(
            method = "sendTransaction",
            params = params,
            resultDeserializer = String.serializer()
        )
    }
    
    /**
     * Returns transaction details for a confirmed transaction signature.
     */
    suspend fun getTransaction(
        signature: String,
        commitment: Commitment = Commitment.FINALIZED,
        encoding: Encoding = Encoding.JSON_PARSED,
        maxSupportedTransactionVersion: Int? = 0
    ): TransactionResult? {
        val params = buildJsonArray {
            add(signature)
            addJsonObject {
                put("commitment", commitment.value)
                put("encoding", encoding.value)
                maxSupportedTransactionVersion?.let { put("maxSupportedTransactionVersion", it) }
            }
        }
        
        val result = client.executeRpcCall(
            method = "getTransaction",
            params = params,
            resultDeserializer = JsonElement.serializer()
        )
        
        return if (result is JsonNull) null
        else json.decodeFromJsonElement(TransactionResult.serializer(), result)
    }
    
    /**
     * Returns signatures for confirmed transactions that include the given address.
     */
    suspend fun getSignaturesForAddress(
        address: String,
        limit: Int = 1000,
        before: String? = null,
        until: String? = null,
        commitment: Commitment = Commitment.FINALIZED
    ): List<SignatureInfo> {
        val params = buildJsonArray {
            add(address)
            addJsonObject {
                put("limit", limit)
                put("commitment", commitment.value)
                before?.let { put("before", it) }
                until?.let { put("until", it) }
            }
        }
        
        return client.executeRpcCall(
            method = "getSignaturesForAddress",
            params = params,
            resultDeserializer = kotlinx.serialization.builtins.ListSerializer(SignatureInfo.serializer())
        )
    }
    
    /**
     * Returns the statuses of a list of signatures.
     */
    suspend fun getSignatureStatuses(
        signatures: List<String>,
        searchTransactionHistory: Boolean = false
    ): JsonElement {
        val params = buildJsonArray {
            add(JsonArray(signatures.map { JsonPrimitive(it) }))
            addJsonObject {
                put("searchTransactionHistory", searchTransactionHistory)
            }
        }
        
        return client.executeRpcCall(
            method = "getSignatureStatuses",
            params = params,
            resultDeserializer = JsonElement.serializer()
        )
    }
    
    /**
     * Simulate sending a transaction.
     */
    suspend fun simulateTransaction(
        transaction: String,
        commitment: Commitment = Commitment.FINALIZED,
        sigVerify: Boolean = false,
        replaceRecentBlockhash: Boolean = false,
        accounts: List<String>? = null
    ): SimulationResult {
        val params = buildJsonArray {
            add(transaction)
            addJsonObject {
                put("commitment", commitment.value)
                put("encoding", "base64")
                put("sigVerify", sigVerify)
                put("replaceRecentBlockhash", replaceRecentBlockhash)
                accounts?.let {
                    putJsonObject("accounts") {
                        put("encoding", "base64")
                        put("addresses", JsonArray(it.map { addr -> JsonPrimitive(addr) }))
                    }
                }
            }
        }
        
        val result = client.executeRpcCall(
            method = "simulateTransaction",
            params = params,
            resultDeserializer = JsonElement.serializer()
        )
        
        val value = result.jsonObject["value"] ?: result
        return json.decodeFromJsonElement(SimulationResult.serializer(), value)
    }
    
    // ========================================================================
    // BLOCK METHODS
    // ========================================================================
    
    /**
     * Returns identity and transaction information about a confirmed block.
     */
    suspend fun getBlock(
        slot: Long,
        commitment: Commitment = Commitment.FINALIZED,
        encoding: Encoding = Encoding.JSON_PARSED,
        transactionDetails: String = "full",
        rewards: Boolean = true,
        maxSupportedTransactionVersion: Int? = 0
    ): BlockInfo? {
        val params = buildJsonArray {
            add(slot)
            addJsonObject {
                put("commitment", commitment.value)
                put("encoding", encoding.value)
                put("transactionDetails", transactionDetails)
                put("rewards", rewards)
                maxSupportedTransactionVersion?.let { put("maxSupportedTransactionVersion", it) }
            }
        }
        
        val result = client.executeRpcCall(
            method = "getBlock",
            params = params,
            resultDeserializer = JsonElement.serializer()
        )
        
        return if (result is JsonNull) null
        else json.decodeFromJsonElement(BlockInfo.serializer(), result)
    }
    
    /**
     * Returns recent block production information.
     */
    suspend fun getBlockProduction(
        commitment: Commitment = Commitment.FINALIZED,
        identity: String? = null,
        range: SlotRange? = null
    ): BlockProduction {
        val params = buildJsonArray {
            addJsonObject {
                put("commitment", commitment.value)
                identity?.let { put("identity", it) }
                range?.let {
                    putJsonObject("range") {
                        put("firstSlot", it.firstSlot)
                        put("lastSlot", it.lastSlot)
                    }
                }
            }
        }
        
        val result = client.executeRpcCall(
            method = "getBlockProduction",
            params = params,
            resultDeserializer = JsonElement.serializer()
        )
        
        val value = result.jsonObject["value"] ?: throw IrisRpcException(-32600, "Missing value")
        return json.decodeFromJsonElement(BlockProduction.serializer(), value)
    }
    
    /**
     * Returns commitment for particular block.
     */
    suspend fun getBlockCommitment(slot: Long): BlockCommitment {
        val params = buildJsonArray { add(slot) }
        
        return client.executeRpcCall(
            method = "getBlockCommitment",
            params = params,
            resultDeserializer = BlockCommitment.serializer()
        )
    }
    
    /**
     * Returns the estimated production time of a block.
     */
    suspend fun getBlockTime(slot: Long): Long? {
        val params = buildJsonArray { add(slot) }
        
        val result = client.executeRpcCall(
            method = "getBlockTime",
            params = params,
            resultDeserializer = JsonElement.serializer()
        )
        
        return if (result is JsonNull) null else result.jsonPrimitive.long
    }
    
    /**
     * Returns a list of confirmed blocks.
     */
    suspend fun getBlocks(
        startSlot: Long,
        endSlot: Long? = null,
        commitment: Commitment = Commitment.FINALIZED
    ): List<Long> {
        val params = buildJsonArray {
            add(startSlot)
            endSlot?.let { add(it) }
            addJsonObject {
                put("commitment", commitment.value)
            }
        }
        
        return client.executeRpcCall(
            method = "getBlocks",
            params = params,
            resultDeserializer = kotlinx.serialization.builtins.ListSerializer(Long.serializer())
        )
    }
    
    /**
     * Returns the current block height of the node.
     */
    suspend fun getBlockHeight(commitment: Commitment = Commitment.FINALIZED): Long {
        val params = buildJsonArray {
            addJsonObject {
                put("commitment", commitment.value)
            }
        }
        
        return client.executeRpcCall(
            method = "getBlockHeight",
            params = params,
            resultDeserializer = Long.serializer()
        )
    }
    
    // ========================================================================
    // SLOT & EPOCH METHODS
    // ========================================================================
    
    /**
     * Returns the current slot the node is processing.
     */
    suspend fun getSlot(commitment: Commitment = Commitment.FINALIZED): Long {
        val params = buildJsonArray {
            addJsonObject {
                put("commitment", commitment.value)
            }
        }
        
        return client.executeRpcCall(
            method = "getSlot",
            params = params,
            resultDeserializer = Long.serializer()
        )
    }
    
    /**
     * Returns the slot leader for a slot.
     */
    suspend fun getSlotLeader(commitment: Commitment = Commitment.FINALIZED): String {
        val params = buildJsonArray {
            addJsonObject {
                put("commitment", commitment.value)
            }
        }
        
        return client.executeRpcCall(
            method = "getSlotLeader",
            params = params,
            resultDeserializer = String.serializer()
        )
    }
    
    /**
     * Returns the slot leaders for a slot range.
     */
    suspend fun getSlotLeaders(startSlot: Long, limit: Long): List<String> {
        val params = buildJsonArray {
            add(startSlot)
            add(limit)
        }
        
        return client.executeRpcCall(
            method = "getSlotLeaders",
            params = params,
            resultDeserializer = kotlinx.serialization.builtins.ListSerializer(String.serializer())
        )
    }
    
    /**
     * Returns information about the current epoch.
     */
    suspend fun getEpochInfo(commitment: Commitment = Commitment.FINALIZED): JsonElement {
        val params = buildJsonArray {
            addJsonObject {
                put("commitment", commitment.value)
            }
        }
        
        return client.executeRpcCall(
            method = "getEpochInfo",
            params = params,
            resultDeserializer = JsonElement.serializer()
        )
    }
    
    /**
     * Returns epoch schedule information.
     */
    suspend fun getEpochSchedule(): JsonElement {
        return client.executeRpcCall(
            method = "getEpochSchedule",
            params = JsonArray(emptyList()),
            resultDeserializer = JsonElement.serializer()
        )
    }
    
    /**
     * Returns the first available slot.
     */
    suspend fun getFirstAvailableBlock(): Long {
        return client.executeRpcCall(
            method = "getFirstAvailableBlock",
            params = JsonArray(emptyList()),
            resultDeserializer = Long.serializer()
        )
    }
    
    // ========================================================================
    // CLUSTER & NETWORK METHODS
    // ========================================================================
    
    /**
     * Returns a recent block hash from the ledger.
     */
    suspend fun getLatestBlockhash(commitment: Commitment = Commitment.FINALIZED): JsonElement {
        val params = buildJsonArray {
            addJsonObject {
                put("commitment", commitment.value)
            }
        }
        
        return client.executeRpcCall(
            method = "getLatestBlockhash",
            params = params,
            resultDeserializer = JsonElement.serializer()
        )
    }
    
    /**
     * Returns information about all the nodes in the cluster.
     */
    suspend fun getClusterNodes(): JsonElement {
        return client.executeRpcCall(
            method = "getClusterNodes",
            params = JsonArray(emptyList()),
            resultDeserializer = JsonElement.serializer()
        )
    }
    
    /**
     * Returns the genesis hash.
     */
    suspend fun getGenesisHash(): String {
        return client.executeRpcCall(
            method = "getGenesisHash",
            params = JsonArray(emptyList()),
            resultDeserializer = String.serializer()
        )
    }
    
    /**
     * Returns the identity pubkey for the current node.
     */
    suspend fun getIdentity(): JsonElement {
        return client.executeRpcCall(
            method = "getIdentity",
            params = JsonArray(emptyList()),
            resultDeserializer = JsonElement.serializer()
        )
    }
    
    /**
     * Returns the current health of the node.
     */
    suspend fun getHealth(): String {
        return client.executeRpcCall(
            method = "getHealth",
            params = JsonArray(emptyList()),
            resultDeserializer = String.serializer()
        )
    }
    
    /**
     * Returns the current Solana version.
     */
    suspend fun getVersion(): JsonElement {
        return client.executeRpcCall(
            method = "getVersion",
            params = JsonArray(emptyList()),
            resultDeserializer = JsonElement.serializer()
        )
    }
    
    /**
     * Returns the lowest slot that the node has information about.
     */
    suspend fun minimumLedgerSlot(): Long {
        return client.executeRpcCall(
            method = "minimumLedgerSlot",
            params = JsonArray(emptyList()),
            resultDeserializer = Long.serializer()
        )
    }
    
    // ========================================================================
    // FEE & INFLATION METHODS
    // ========================================================================
    
    /**
     * Returns the fee the network will charge for a particular message.
     */
    suspend fun getFeeForMessage(
        message: String,
        commitment: Commitment = Commitment.FINALIZED
    ): Long? {
        val params = buildJsonArray {
            add(message)
            addJsonObject {
                put("commitment", commitment.value)
            }
        }
        
        val result = client.executeRpcCall(
            method = "getFeeForMessage",
            params = params,
            resultDeserializer = JsonElement.serializer()
        )
        
        val value = result.jsonObject["value"]
        return if (value is JsonNull) null else value?.jsonPrimitive?.long
    }
    
    /**
     * Returns the current inflation governor.
     */
    suspend fun getInflationGovernor(commitment: Commitment = Commitment.FINALIZED): JsonElement {
        val params = buildJsonArray {
            addJsonObject {
                put("commitment", commitment.value)
            }
        }
        
        return client.executeRpcCall(
            method = "getInflationGovernor",
            params = params,
            resultDeserializer = JsonElement.serializer()
        )
    }
    
    /**
     * Returns the current inflation rate.
     */
    suspend fun getInflationRate(): JsonElement {
        return client.executeRpcCall(
            method = "getInflationRate",
            params = JsonArray(emptyList()),
            resultDeserializer = JsonElement.serializer()
        )
    }
    
    /**
     * Returns the inflation rewards for a list of addresses.
     */
    suspend fun getInflationReward(
        addresses: List<String>,
        epoch: Long? = null,
        commitment: Commitment = Commitment.FINALIZED
    ): JsonElement {
        val params = buildJsonArray {
            add(JsonArray(addresses.map { JsonPrimitive(it) }))
            addJsonObject {
                put("commitment", commitment.value)
                epoch?.let { put("epoch", it) }
            }
        }
        
        return client.executeRpcCall(
            method = "getInflationReward",
            params = params,
            resultDeserializer = JsonElement.serializer()
        )
    }
    
    // ========================================================================
    // STAKE & VOTE METHODS
    // ========================================================================
    
    /**
     * Returns stake accounts delegated to a specific validator.
     */
    suspend fun getStakeMinimumDelegation(commitment: Commitment = Commitment.FINALIZED): Long {
        val params = buildJsonArray {
            addJsonObject {
                put("commitment", commitment.value)
            }
        }
        
        val result = client.executeRpcCall(
            method = "getStakeMinimumDelegation",
            params = params,
            resultDeserializer = JsonElement.serializer()
        )
        
        return result.jsonObject["value"]?.jsonPrimitive?.long ?: 0
    }
    
    /**
     * Returns the current vote accounts.
     */
    suspend fun getVoteAccounts(commitment: Commitment = Commitment.FINALIZED): JsonElement {
        val params = buildJsonArray {
            addJsonObject {
                put("commitment", commitment.value)
            }
        }
        
        return client.executeRpcCall(
            method = "getVoteAccounts",
            params = params,
            resultDeserializer = JsonElement.serializer()
        )
    }
    
    // ========================================================================
    // MISC METHODS
    // ========================================================================
    
    /**
     * Returns the highest slot that has been processed by the node.
     */
    suspend fun getHighestSnapshotSlot(): JsonElement {
        return client.executeRpcCall(
            method = "getHighestSnapshotSlot",
            params = JsonArray(emptyList()),
            resultDeserializer = JsonElement.serializer()
        )
    }
    
    /**
     * Returns the minimum balance required to make account rent exempt.
     */
    suspend fun getMinimumBalanceForRentExemption(dataSize: Long): Long {
        val params = buildJsonArray { add(dataSize) }
        
        return client.executeRpcCall(
            method = "getMinimumBalanceForRentExemption",
            params = params,
            resultDeserializer = Long.serializer()
        )
    }
    
    /**
     * Returns recent performance samples.
     */
    suspend fun getRecentPerformanceSamples(limit: Int = 720): JsonElement {
        val params = buildJsonArray { add(limit) }
        
        return client.executeRpcCall(
            method = "getRecentPerformanceSamples",
            params = params,
            resultDeserializer = JsonElement.serializer()
        )
    }
    
    /**
     * Returns a list of recent prioritization fees.
     */
    suspend fun getRecentPrioritizationFees(accounts: List<String> = emptyList()): JsonElement {
        val params = buildJsonArray {
            if (accounts.isNotEmpty()) {
                add(JsonArray(accounts.map { JsonPrimitive(it) }))
            }
        }
        
        return client.executeRpcCall(
            method = "getRecentPrioritizationFees",
            params = params,
            resultDeserializer = JsonElement.serializer()
        )
    }
    
    /**
     * Requests an airdrop of lamports to a pubkey (devnet/testnet only).
     */
    suspend fun requestAirdrop(
        pubkey: String,
        lamports: Long,
        commitment: Commitment = Commitment.FINALIZED
    ): String {
        val params = buildJsonArray {
            add(pubkey)
            add(lamports)
            addJsonObject {
                put("commitment", commitment.value)
            }
        }
        
        return client.executeRpcCall(
            method = "requestAirdrop",
            params = params,
            resultDeserializer = String.serializer()
        )
    }
    
    /**
     * Returns whether a blockhash is still valid.
     */
    suspend fun isBlockhashValid(
        blockhash: String,
        commitment: Commitment = Commitment.FINALIZED
    ): Boolean {
        val params = buildJsonArray {
            add(blockhash)
            addJsonObject {
                put("commitment", commitment.value)
            }
        }
        
        val result = client.executeRpcCall(
            method = "isBlockhashValid",
            params = params,
            resultDeserializer = JsonElement.serializer()
        )
        
        return result.jsonObject["value"]?.jsonPrimitive?.boolean ?: false
    }
}

/**
 * Token account filter for querying by mint or program.
 */
sealed class TokenAccountFilter {
    data class ByMint(val mint: String) : TokenAccountFilter()
    data class ByProgram(val programId: String) : TokenAccountFilter()
}
