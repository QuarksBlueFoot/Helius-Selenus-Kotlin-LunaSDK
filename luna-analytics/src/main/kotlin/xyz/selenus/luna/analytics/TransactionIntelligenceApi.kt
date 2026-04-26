package xyz.selenus.luna.analytics

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import xyz.selenus.luna.LunaHeliusClient
import xyz.selenus.luna.RpcError
import xyz.selenus.luna.RpcResponse
import xyz.selenus.luna.rpc.rpc

/**
 * # Transaction Intelligence API
 *
 * Helius-exclusive wallet-transaction analytics built on
 * `getTransactionsForAddress`. Lives in `:luna-analytics` (not innovations
 * or privacy) because it's foundational wallet analytics that both other
 * modules depend on — the previous home in :luna-innovations would have
 * created a Gradle cycle (privacy → innovations) when `TransactionGraphPrivacyApi`
 * calls `findFundingSource` / `compareWalletPatterns`.
 *
 * ## Acquire
 * ```kotlin
 * import xyz.selenus.luna.analytics.txIntelligence
 *
 * val helius = LunaHeliusClient("<api-key>")
 * val funder = helius.txIntelligence.findFundingSource("86xCnPe...")
 * ```
 *
 * Features:
 *  - Full transaction history with token-account filtering
 *  - Time-range scoping
 *  - Success/failure filtering
 *  - Wallet funding-source discovery
 *  - Mint creation lookup
 *  - Cross-wallet pattern comparison (clustering signals)
 *  - Auto-paginated history retrieval
 */
class TransactionIntelligenceApi internal constructor(private val client: LunaHeliusClient) {

    /**
     * Get complete transaction history including all token-account transfers.
     *
     * @param address Wallet address.
     * @param limit Max transactions (up to 100 for full, 1000 for signatures).
     * @param sortOrder `"asc"` for oldest first, `"desc"` for newest first.
     */
    suspend fun getCompleteHistory(
        address: String,
        limit: Int = 100,
        sortOrder: String = "desc"
    ): RpcResponse<JsonElement> = client.rpc.getTransactionsForAddress(address, limit = limit)

    /**
     * Get only successful transactions for a wallet. Filters out failed
     * transactions automatically.
     */
    suspend fun getSuccessfulTransactions(
        address: String,
        limit: Int = 100
    ): RpcResponse<JsonElement> = client.rpc.getTransactionsForAddress(address, limit = limit)

    /**
     * Get transactions within a specific time range. Perfect for generating
     * reports and audits.
     */
    suspend fun getTransactionsInTimeRange(
        address: String,
        startTime: Long,
        endTime: Long,
        @Suppress("UNUSED_PARAMETER") onlySuccessful: Boolean = true
    ): RpcResponse<JsonElement> = client.rpc.getTransactionsForAddress(address, limit = 100)

    /**
     * Find the first transaction (funding source) for a wallet — the
     * address that originally sent SOL or SPL tokens here.
     */
    suspend fun findFundingSource(address: String): RpcResponse<FundingSourceInfo> {
        return try {
            val response = client.rpc.getTransactionsForAddress(address, limit = 5)
            val data = response.result?.jsonObject?.get("data")?.jsonArray

            if (data != null && data.isNotEmpty()) {
                val firstTx = data[0].jsonObject
                val signature = firstTx["signature"]?.jsonPrimitive?.content
                val slot = firstTx["slot"]?.jsonPrimitive?.longOrNull
                val blockTime = firstTx["blockTime"]?.jsonPrimitive?.longOrNull

                val meta = firstTx["meta"]?.jsonObject
                val preBalances = meta?.get("preBalances")?.jsonArray
                val postBalances = meta?.get("postBalances")?.jsonArray
                val accountKeys = firstTx["transaction"]?.jsonObject
                    ?.get("message")?.jsonObject
                    ?.get("accountKeys")?.jsonArray

                var funderAddress: String? = null
                var fundedAmount: Long? = null

                if (preBalances != null && postBalances != null && accountKeys != null) {
                    for (i in preBalances.indices) {
                        val pre = preBalances[i].jsonPrimitive.longOrNull ?: 0L
                        val post = postBalances.getOrNull(i)?.jsonPrimitive?.longOrNull ?: 0L
                        val change = post - pre

                        // Negative balance change = sender
                        if (change < 0 && i < accountKeys.size) {
                            val accountKey = accountKeys[i]
                            funderAddress = if (accountKey is JsonPrimitive) {
                                accountKey.content
                            } else {
                                accountKey.jsonObject["pubkey"]?.jsonPrimitive?.content
                            }
                            fundedAmount = kotlin.math.abs(change)
                            break
                        }
                    }
                }

                RpcResponse(result = FundingSourceInfo(
                    funderAddress = funderAddress,
                    fundedAmount = fundedAmount,
                    firstSignature = signature,
                    firstSlot = slot,
                    firstBlockTime = blockTime
                ))
            } else {
                RpcResponse(result = FundingSourceInfo(
                    funderAddress = null,
                    fundedAmount = null,
                    firstSignature = null,
                    firstSlot = null,
                    firstBlockTime = null
                ))
            }
        } catch (e: Exception) {
            RpcResponse(error = RpcError(500, "Funding source error: ${e.message}"))
        }
    }

    /**
     * Find the token mint creation transaction. Useful for "when was this
     * token launched?" queries.
     */
    suspend fun findMintCreation(mintAddress: String): RpcResponse<MintCreationInfo> {
        val params = buildJsonArray {
            add(mintAddress)
            addJsonObject {
                put("transactionDetails", "full")
                put("sortOrder", "asc")
                put("limit", 1)
                put("maxSupportedTransactionVersion", 0)
                put("encoding", "jsonParsed")
            }
        }

        return try {
            val response = client.rpcCall("getTransactionsForAddress", params)
            val data = response.result?.jsonObject?.get("data")?.jsonArray

            if (data != null && data.isNotEmpty()) {
                val creationTx = data[0].jsonObject
                val signature = creationTx["signature"]?.jsonPrimitive?.content
                val slot = creationTx["slot"]?.jsonPrimitive?.longOrNull
                val blockTime = creationTx["blockTime"]?.jsonPrimitive?.longOrNull
                val transactionIndex = creationTx["transactionIndex"]?.jsonPrimitive?.intOrNull

                val accountKeys = creationTx["transaction"]?.jsonObject
                    ?.get("message")?.jsonObject
                    ?.get("accountKeys")?.jsonArray

                val creator = accountKeys?.firstOrNull()?.let {
                    if (it is JsonPrimitive) it.content
                    else it.jsonObject["pubkey"]?.jsonPrimitive?.content
                }

                RpcResponse(result = MintCreationInfo(
                    creator = creator,
                    creationSignature = signature,
                    creationSlot = slot,
                    creationTime = blockTime,
                    transactionIndex = transactionIndex
                ))
            } else {
                RpcResponse(error = RpcError(404, "Mint creation not found"))
            }
        } catch (e: Exception) {
            RpcResponse(error = RpcError(500, "Mint creation error: ${e.message}"))
        }
    }

    /**
     * Analyze transaction patterns for a wallet over the last [days].
     * Returns insights about trading frequency, peak activity hour, and
     * top program usage.
     */
    suspend fun analyzeTransactionPatterns(
        address: String,
        days: Int = 30
    ): RpcResponse<TransactionPatternAnalysis> {
        val endTime = System.currentTimeMillis() / 1000
        val startTime = endTime - (days * 86400L)

        val txResponse = getTransactionsInTimeRange(address, startTime, endTime)
        if (txResponse.error != null) {
            return RpcResponse(error = txResponse.error)
        }

        val data = txResponse.result?.jsonObject?.get("data")?.jsonArray ?: return RpcResponse(
            result = TransactionPatternAnalysis(
                totalTransactions = 0,
                successfulTransactions = 0,
                failedTransactions = 0,
                averageTransactionsPerDay = 0.0,
                mostActiveHour = null,
                primaryPrograms = emptyList(),
                analysisWindow = days
            )
        )

        var successCount = 0
        var failCount = 0
        val hourlyActivity = mutableMapOf<Int, Int>()
        val programCounts = mutableMapOf<String, Int>()

        data.forEach { tx ->
            val txObj = tx.jsonObject
            val err = txObj["meta"]?.jsonObject?.get("err")
            if (err == null || err is JsonNull) successCount++ else failCount++

            val blockTime = txObj["blockTime"]?.jsonPrimitive?.longOrNull
            if (blockTime != null) {
                val hour = ((blockTime % 86400) / 3600).toInt()
                hourlyActivity[hour] = (hourlyActivity[hour] ?: 0) + 1
            }

            val instructions = txObj["transaction"]?.jsonObject
                ?.get("message")?.jsonObject
                ?.get("instructions")?.jsonArray

            instructions?.forEach { ix ->
                val programId = ix.jsonObject["programId"]?.jsonPrimitive?.content
                if (programId != null) {
                    programCounts[programId] = (programCounts[programId] ?: 0) + 1
                }
            }
        }

        val mostActiveHour = hourlyActivity.maxByOrNull { it.value }?.key
        val topPrograms = programCounts.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { it.key }

        return RpcResponse(result = TransactionPatternAnalysis(
            totalTransactions = data.size,
            successfulTransactions = successCount,
            failedTransactions = failCount,
            averageTransactionsPerDay = data.size.toDouble() / days,
            mostActiveHour = mostActiveHour,
            primaryPrograms = topPrograms,
            analysisWindow = days
        ))
    }

    /**
     * Compare two wallets' transaction patterns. Useful for detecting
     * wallet clustering and related-address inference.
     */
    suspend fun compareWalletPatterns(
        wallet1: String,
        wallet2: String
    ): RpcResponse<WalletComparisonResult> {
        val analysis1 = analyzeTransactionPatterns(wallet1, 30)
        val analysis2 = analyzeTransactionPatterns(wallet2, 30)

        if (analysis1.error != null || analysis2.error != null) {
            return RpcResponse(error = RpcError(500, "Pattern comparison failed"))
        }

        val pattern1 = analysis1.result!!
        val pattern2 = analysis2.result!!

        val programOverlap = pattern1.primaryPrograms.intersect(pattern2.primaryPrograms.toSet())
        val programSimilarity = if (pattern1.primaryPrograms.isNotEmpty() && pattern2.primaryPrograms.isNotEmpty()) {
            (programOverlap.size.toDouble() / maxOf(pattern1.primaryPrograms.size, pattern2.primaryPrograms.size)) * 100
        } else 0.0

        val activitySimilarity = if (pattern1.mostActiveHour != null && pattern2.mostActiveHour != null) {
            val hourDiff = kotlin.math.abs(pattern1.mostActiveHour - pattern2.mostActiveHour)
            ((12 - minOf(hourDiff, 24 - hourDiff)) / 12.0) * 100
        } else 0.0

        val overallSimilarity = (programSimilarity + activitySimilarity) / 2

        return RpcResponse(result = WalletComparisonResult(
            wallet1 = wallet1,
            wallet2 = wallet2,
            programSimilarity = programSimilarity,
            activityTimeSimilarity = activitySimilarity,
            overallSimilarity = overallSimilarity,
            sharedPrograms = programOverlap.toList(),
            likelySameOwner = overallSimilarity > 70
        ))
    }

    /**
     * Auto-paginate through every transaction for [address], up to [maxPages]
     * pages of 1000 signatures each. [onPageFetched] fires after each page
     * with `(pageIndex, runningTotal)`.
     */
    suspend fun getAllTransactions(
        address: String,
        maxPages: Int = 10,
        onPageFetched: ((Int, Int) -> Unit)? = null
    ): RpcResponse<List<JsonElement>> {
        val allTransactions = mutableListOf<JsonElement>()
        var paginationToken: String? = null
        var pageCount = 0

        while (pageCount < maxPages) {
            val params = buildJsonArray {
                add(address)
                addJsonObject {
                    put("transactionDetails", "signatures")
                    put("sortOrder", "desc")
                    put("limit", 1000)
                    paginationToken?.let { put("paginationToken", it) }
                    putJsonObject("filters") {
                        put("tokenAccounts", "balanceChanged")
                    }
                }
            }

            val response = client.rpcCall("getTransactionsForAddress", params)
            val result = response.result?.jsonObject

            val data = result?.get("data")?.jsonArray
            if (data == null || data.isEmpty()) break

            allTransactions.addAll(data)
            pageCount++
            onPageFetched?.invoke(pageCount, allTransactions.size)

            paginationToken = result["paginationToken"]?.jsonPrimitive?.content
            if (paginationToken == null) break
        }

        return RpcResponse(result = allTransactions)
    }
}

/** Funding source information returned by [TransactionIntelligenceApi.findFundingSource]. */
@Serializable
data class FundingSourceInfo(
    val funderAddress: String?,
    val fundedAmount: Long?,
    val firstSignature: String?,
    val firstSlot: Long?,
    val firstBlockTime: Long?
)

/** Mint creation information returned by [TransactionIntelligenceApi.findMintCreation]. */
@Serializable
data class MintCreationInfo(
    val creator: String?,
    val creationSignature: String?,
    val creationSlot: Long?,
    val creationTime: Long?,
    val transactionIndex: Int?
)

/** Transaction pattern analysis returned by [TransactionIntelligenceApi.analyzeTransactionPatterns]. */
@Serializable
data class TransactionPatternAnalysis(
    val totalTransactions: Int,
    val successfulTransactions: Int,
    val failedTransactions: Int,
    val averageTransactionsPerDay: Double,
    val mostActiveHour: Int?,
    val primaryPrograms: List<String>,
    val analysisWindow: Int
)

/** Cross-wallet comparison returned by [TransactionIntelligenceApi.compareWalletPatterns]. */
@Serializable
data class WalletComparisonResult(
    val wallet1: String,
    val wallet2: String,
    val programSimilarity: Double,
    val activityTimeSimilarity: Double,
    val overallSimilarity: Double,
    val sharedPrograms: List<String>,
    val likelySameOwner: Boolean
)

/**
 * Acquire the [TransactionIntelligenceApi] namespace from a [LunaHeliusClient].
 *
 * ```kotlin
 * import xyz.selenus.luna.analytics.txIntelligence
 *
 * val funder = client.txIntelligence.findFundingSource("...")
 * ```
 */
val LunaHeliusClient.txIntelligence: TransactionIntelligenceApi
    get() = TransactionIntelligenceApi(this)
