# Selenus Solana SDKs

<div align="center">
  <h2>🌙 Luna SDK • 🌈 Iris SDK</h2>
  <p><strong>The definitive Kotlin-first SDKs for Solana</strong></p>
  <p>
    <a href="#lunasdk-for-helius">Luna (Helius)</a> •
    <a href="#irissdk-for-quicknode">Iris (QuickNode)</a> •
    <a href="https://selenus.xyz">Website</a>
  </p>
</div>

---

## Overview

This repository contains **two** world-class Kotlin SDKs for Solana:

| SDK | Provider | Focus | Maven |
|-----|----------|-------|-------|
| 🌙 **Luna SDK** | [Helius](https://helius.dev) | DeFi, Analytics, Enhanced APIs | `xyz.selenus:luna-sdk:5.2.0` |
| 🌈 **Iris SDK** | [QuickNode](https://quicknode.com) | Full Infrastructure, JITO, Streaming | `xyz.selenus:iris-sdk:1.0.0` |

Both SDKs are:
- **Kotlin-first** with coroutine support and Flow-based streaming
- **Type-safe** with comprehensive data classes
- **Privacy-focused** with exclusive privacy innovations
- **Production-ready** with comprehensive test coverage

---

# LunaSDK for Helius

**LunaSDK** is a modern, Kotlin-first client for the [Helius](https://www.helius.dev/) platform.  It exposes
Helius' Solana APIs, including Digital Asset Standard (DAS), enhanced RPC methods, staking,
transactions, priority fee estimation, enhanced transactions, webhooks, WebSockets and ZK-Compression,
as idiomatic, suspendable Kotlin functions.  The goal is to provide Android and JVM developers with a clean,
coroutine-friendly interface that hides JSON-RPC boilerplate and makes interacting with Helius simple.

**v5.2.0** introduces **Privacy-First Helius-Exclusive APIs** inspired by Zcash, Aztec Network, 
Monero, and Tornado Cash patterns - implemented EXCLUSIVELY using Helius infrastructure:
- **Stealth Address API** - One-time receiving addresses breaking on-chain links
- **Privacy Pool API** - Anonymity set analysis via Helius ZK Compression
- **Transaction Graph Privacy** - Detect leaks, wallet linkage, privacy-preserving paths
- **Shielded Pattern API** - Shielded vs transparent balance analysis
- **Privacy Score Engine** - Comprehensive privacy scoring with improvement roadmaps

For more information, visit [selenus.xyz](https://selenus.xyz).

## Highlights

- **Privacy-First Innovation (v5.2.0)**: Stealth addresses, privacy pools, transaction graph analysis,
  shielded patterns, and enterprise-grade privacy scoring - all using Helius exclusively.
- **Helius-Exclusive Infrastructure**: Ultra-low latency Sender API, LaserStream gRPC with 9 global
  regions, extended ZK Compression with 20+ methods, enhanced WebSocket Flow subscriptions.
- **Web2-Inspired Innovation**: Analytics dashboards with funnel analysis and cohort metrics,
  real-time notification system, and mobile-first optimization with battery-aware polling.
- **2026 Kotlin Architecture**: Built on Kotlin Coroutines 1.10.2 with Flow-based reactive streams,
  StateFlow for UI binding, and channelFlow for WebSocket subscriptions.
- **Privacy Innovation**: ZK Privacy API, Confidential Transactions, and Full Privacy Audits - 
  features no other SDK offers.
- **Modern design**: built around Kotlin coroutines, data classes and sealed types to leverage
  contemporary language features.
- **Strong typing**: request and response objects are represented as data classes where the Helius API
  schemas are stable, while dynamic fields fall back to `JsonElement` for maximum flexibility.
- **Easy configuration**: a single `LunaHeliusClient` takes an API key and cluster, then exposes
  namespaced APIs via properties (`das`, `rpc`, `staking`, `tx`, `priority`, `enhanced`, `webhooks`,
  `ws`, `zk`, `sender`, `solana`, `niche`, `reactive`, `zkPrivacy`, `confidential`, `subscriptions`,
  `stealthAddress`, `privacyPool`, `graphPrivacy`, `shieldedPattern`, `privacyScore`).
- **No dependencies on web3.js**: calls are made directly via HTTP using OkHttp and `kotlinx.serialization`.
- **Minimal Dependencies**: To keep the SDK lightweight, cryptographic operations (like webhook signature verification) require an external library (e.g., Bouncy Castle or TweetNacl).
- **Extensible**: additional endpoints can be added by creating new methods in the appropriate
  namespace.

## Usage example

```kotlin
import com.selenus.luna.LunaHeliusClient
import com.selenus.luna.Cluster
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

fun main() = runBlocking {
    val apiKey = "YOUR_HELIUS_API_KEY"
    val helius = LunaHeliusClient(apiKey, Cluster.MAINNET)

    // v5.2.0: Stealth Address Generation (Monero/Zcash-inspired)
    val stealthSet = helius.stealthAddress.generateStealthReceiveSet("recipient_pubkey", count = 5)
    println("Use one-time path: ${stealthSet.recommendedPath.derivationPath}")
    
    // v5.2.0: Privacy Pool Analysis (Tornado Cash-inspired)
    val anonymity = helius.privacyPool.getAnonymitySetSize("address")
    println("Anonymity set: ${anonymity.result?.estimatedAnonymitySet}")
    
    // v5.2.0: Transaction Graph Privacy (detect leaks)
    val leaks = helius.graphPrivacy.analyzePrivacyLeaks("wallet", depth = 2)
    println("Risk score: ${leaks.result?.overallRiskScore}/100")
    leaks.result?.leaksDetected?.forEach { leak ->
        println("  ${leak.severity}: ${leak.type}")
    }
    
    // v5.2.0: Shielded Balance Analysis (Zcash-inspired)
    val shielded = helius.shieldedPattern.analyzeShieldedRatio("owner")
    println("Shielded: ${shielded.result?.shieldedRatio?.times(100)}%")
    
    // v5.2.0: Comprehensive Privacy Score
    val score = helius.privacyScore.calculateComprehensiveScore("address")
    println("Privacy Grade: ${score.result?.privacyGrade} (${score.result?.overallScore}/100)")
    
    // v5.2.0: Privacy Improvement Roadmap
    val roadmap = helius.privacyScore.generatePrivacyRoadmap("address", targetScore = 90)
    roadmap.result?.milestones?.forEach { m ->
        println("Milestone ${m.milestone}: ${m.title} (+${m.scoreImpact} pts)")
    }

    // v5.1.0: Ultra-low latency transaction submission via Helius Sender
    helius.sender.sendAndTrack("base64Transaction")
        .collect { status ->
            println("Phase: ${status.phase}, Status: ${status.confirmationStatus}")
        }
    
    // v5.1.0: Find optimal Sender region for backend apps
    val optimalRegion = helius.sender.findOptimalRegion()
    println("Use: ${optimalRegion.optimalEndpoint} (${optimalRegion.latencyMs}ms)")

    // v5.1.0: LaserStream gRPC configuration
    val regions = helius.laserStream.getAvailableRegions()
    println("Available regions: ${regions.map { it.name }}")
    
    val txSubscription = helius.laserStream.buildTransactionSubscription(
        accountInclude = listOf("wallet-address"),
        includeFailed = false
    )

    // v5.1.0: Web2-inspired wallet health score
    val healthScore = helius.analytics.calculateWalletHealthScore("wallet-address")
    println("Health: ${healthScore.result?.healthLevel} (${healthScore.result?.overallScore}/100)")

    // v5.1.0: Real-time notifications
    val alert = helius.notifications.createBalanceAlert(
        walletAddress = "wallet-address",
        thresholdLamports = 1_000_000_000L, // 1 SOL
        direction = "below"
    )
    helius.notifications.monitorAlerts(listOf(alert))
        .collect { notification ->
            println("Alert: ${notification.message}")
        }

    // v5.1.0: Mobile-optimized compact summary
    val summary = helius.mobileOptimization.getCompactWalletSummary("wallet-address")
    println("${summary.balanceSol} SOL, ${summary.tokenCount} tokens")

    // v5.0.0: Flow-based reactive streaming
    helius.reactive.balanceChanges("wallet-address")
        .onEach { balance -> println("Balance: ${balance / 1_000_000_000.0} SOL") }
        .launchIn(this)

    // v5.0.0: ZK Privacy Audit
    val privacyAudit = helius.zkPrivacy.fullPrivacyAudit("wallet-address")
    println("Privacy Score: ${privacyAudit.result?.overallScore}/100")

    // Fetch a single asset by its ID
    val asset = helius.das.getAsset("F9Lw3ki3hJ7PF9HQXsBzoY8GyE6sPoEZZdXJBsTTD2rk")
    println(asset?.result)

    // List all assets owned by a wallet
    val owned = helius.das.getAssetsByOwner(
        ownerAddress = "86xCnPeV69n6t3DnyGvkKobf9FdN2H9oiVDdaMpo2MMY",
        page = 1,
        limit = 50
    )
    println("Total assets: ${owned?.result?.total}")

    // Estimate a priority fee
    val fee = helius.priority.getPriorityFeeEstimate(priorityLevel = "High")
    println(fee?.result)
}
```

Refer to the [LunaHeliusClient source](src/main/kotlin/com/selenus/luna/LunaHeliusClient.kt) for the
complete list of available methods and their usage.  See the [Helius docs](https://www.helius.dev/docs/api-reference)
for explanations of each endpoint and its parameters.

## Example App

A complete Android sample application is available in the `sample-app` directory. It demonstrates how to configure the client, make DAS requests, and handle responses in an Android environment. See the [Sample App README](sample-app/README.md) for setup instructions.

## Installation

### Local Development
LunaSDK is distributed as a Gradle module.  You can include it in your project by
publishing the module locally or copying the `luna-sdk` folder into your Android
or JVM build.  The library depends only on `okhttp` and `kotlinx.serialization`.

```kotlin
// settings.gradle.kts
include(":luna-sdk")

// build.gradle.kts for your app module
dependencies {
    implementation(project(":luna-sdk"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
```

### Maven Central
To use the published library:

```kotlin
dependencies {
    implementation("xyz.selenus:luna-sdk:5.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
```

## API overview

The `LunaHeliusClient` exposes several namespaces that mirror the structure of the
official Helius SDK and documentation.  Each namespace groups related methods and
returns deserialized JSON responses.  For detailed parameter definitions and
response schemas, consult the Helius API reference.  Below is a summary of the
available features and their corresponding methods.

### Digital Asset Standard (DAS)

The DAS API provides rich access to tokens, NFTs, compressed NFTs and other
digital assets on Solana.  These endpoints operate on the `helius` namespace in
the Node.js SDK and are mapped to `LunaHeliusClient.das` here.

| Method | Description |
|-------|-------------|
| `getAsset(id)` | Fetch a single asset by its ID. Returns on-chain/off-chain metadata, ownership and compression state. |
| `getAssetBatch(ids)` | Retrieve up to 1000 assets in a single request. |
| `getAssetProof(id)` | Return a Merkle proof for a compressed asset. |
| `getAssetProofBatch(ids)` | Get proofs for multiple compressed NFTs. |
| `getAssetsByAuthority(authorityAddress, page?, limit?)` | List assets with a specific update authority. |
| `getAssetsByCreator(creatorAddress, page?, limit?)` | List assets created by an address. |
| `getAssetsByGroup(groupKey, groupValue, page?, limit?)` | Fetch assets by group key/value, e.g. an NFT collection. |
| `getAssetsByOwner(ownerAddress, page?, limit?, sortBy?)` | List all assets (NFTs, tokens, cNFTs) owned by a wallet. |
| `getNftEditions(masterAssetId)` | Get editions derived from a master NFT. |
| `getTokenAccounts(mint?, owner?)` | Retrieve token accounts for a mint or owner. |
| `searchAssets(filters)` | Search the asset index by arbitrary fields. |
| `getSignaturesForAsset(assetId, page?, limit?, before?, after?)` | Fetch signatures of all transactions involving a compressed NFT. |

### Standard Solana RPC Methods

The SDK includes a comprehensive implementation of the standard Solana JSON-RPC API via `LunaHeliusClient.solana`.
This allows you to perform standard chain operations without needing a separate library.

| Method | Description |
|-------|-------------|
| `getBalance(pubkey)` | Get the SOL balance of an account. |
| `getAccountInfo(pubkey)` | Get all account information. |
| `getLatestBlockhash()` | Fetch the latest blockhash for transaction signing. |
| `sendTransaction(...)` | (See Transaction helpers below) |
| ... and 40+ more | Includes `getBlock`, `getSlot`, `getTokenAccountsByOwner`, `requestAirdrop`, etc. |

### Enhanced RPC methods (RPC V2)

Helius provides enhanced versions of standard Solana RPC calls that support
pagination, incremental updates and convenience helpers.  These methods live on
`LunaHeliusClient.rpc`.

| Method | Description |
|-------|-------------|
| `getProgramAccountsV2(programId, encoding?, limit?, paginationKey?, changedSinceSlot?)` | Enhanced `getProgramAccounts` with cursor-based pagination and `changedSinceSlot` filtering. |
| `getAllProgramAccounts(programId, encoding?)` | Auto-paginate through all program accounts. |
| `getTokenAccountsByOwnerV2(owner, mint?, limit?, paginationKey?, changedSinceSlot?)` | Paginate through SPL token accounts by owner with incremental updates. |
| `getAllTokenAccountsByOwner(owner, mint?)` | Retrieve all token accounts owned by an address. |
| `getTransactionsForAddress(address, options)` | Query recent transactions for an address with advanced filtering and sorting. |

#### getTransactionsForAddress Tutorial

The `getTransactionsForAddress` method provides powerful transaction history queries with advanced filtering, flexible sorting, and efficient pagination. It is a Helius-exclusive feature.

**Key Features:**
*   **Flexible sorting**: Chronological (asc) or reverse (desc).
*   **Advanced filtering**: Filter by time ranges, slots, signatures, and status.
*   **Full transaction data**: Get complete details in one call.
*   **Simple pagination**: Uses `slot:position` tokens.

**Example: Fetch successful transactions in a date range**

```kotlin
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

// ... inside your coroutine scope
val startTime = 1735689600L // Jan 1, 2025
val endTime = 1738368000L   // Jan 31, 2025

val filters = buildJsonObject {
    putJsonObject("blockTime") {
        put("gte", startTime)
        put("lte", endTime)
    }
    put("status", "succeeded")
}

val response = helius.rpc.getTransactionsForAddress(
    address = "YOUR_ADDRESS_HERE",
    transactionDetails = "full",
    sortOrder = "asc",
    limit = 100,
    filters = filters
)

println("Found ${response.result?.jsonArray?.size} transactions")
```

**Example: Pagination**

```kotlin
var paginationToken: String? = null
do {
    val response = helius.rpc.getTransactionsForAddress(
        address = "YOUR_ADDRESS_HERE",
        limit = 100,
        paginationToken = paginationToken
    )
    
    // Process results...
    // val txs = response.result?.jsonObject?.get("data")?.jsonArray
    
    // Get next token
    // paginationToken = response.result?.jsonObject?.get("paginationToken")?.jsonPrimitive?.content
    // Note: You'll need to parse the specific response structure based on your needs
} while (paginationToken != null)
```

### Staking helpers

The staking API generates transactions or instructions for staking and withdrawing
SOL to the Helius validator.  These methods are under `LunaHeliusClient.staking`.

| Method | Description |
|-------|-------------|
| `createStakeTransaction(wallet, amountLamports, validatorVoteAddress)` | Build a transaction that creates and delegates a new stake account. |
| `createUnstakeTransaction(stakeAccount)` | Deactivate a stake account. |
| `createWithdrawTransaction(stakeAccount, amountLamports)` | Withdraw lamports from a stake account after the cooldown period. |
| `getStakeInstructions(wallet, amountLamports, validatorVoteAddress)` | Return only the instructions for creating/delegating stake. |
| `getUnstakeInstruction(stakeAccount)` | Get the instruction to deactivate a stake. |
| `getWithdrawInstruction(stakeAccount, amountLamports)` | Get the instruction to withdraw lamports. |
| `getWithdrawableAmount(stakeAccount, includeRentExempt?)` | Determine how many lamports can be withdrawn. |
| `getHeliusStakeAccounts(wallet)` | List all stake accounts delegated to the Helius validator for a wallet. |

### Transaction helpers

Helius exposes several convenience methods for working with transactions.  They
are grouped under `LunaHeliusClient.tx`.

| Method | Description |
|-------|-------------|
| `getComputeUnits(transaction)` | Estimate the compute units consumed by a transaction. |
| `broadcastTransaction(serializedTransaction)` | Broadcast a fully signed transaction and poll for confirmation. |
| `sendTransaction(transaction, encoding?, rebateAddress?)` | Wrapper around `sendTransaction` RPC call with optional encoding and rebate address for backrun rebates. |
| `pollTransactionConfirmation(signature)` | Poll until a transaction is confirmed. |
| `getSmartTransactionPlan(transaction)` | Get optimal Compute Units and Priority Fee for building a Smart Transaction. |
| `sendSmartTransaction(signedTransaction)` | Send a transaction with Helius-recommended polling and rebroadcasting logic. |
| `sendTransactionWithSender(transaction, region?, swqosOnly?)` | Ultra-low-latency transaction submission using Helius Sender. |
| `getSenderTipFloor()` | Get the current Jito tip floor (75th percentile). |

### Priority Fee API

Available via `LunaHeliusClient.priority`, this endpoint estimates an optimal
priority fee given a percentile target.

| Method | Description |
|-------|-------------|
| `getPriorityFeeEstimate(priorityLevel)` | Estimate the fee per compute unit for priority levels like "low", "normal", "fast" or "instant". |

### Enhanced Transactions API

These methods transform raw Solana transactions into human-readable data.  They
are accessible via `LunaHeliusClient.enhanced`.

| Method | Description |
|-------|-------------|
| `getTransactions(signatures)` | Decode one or more transaction signatures into enhanced, readable transactions. |
| `getTransactionsByAddress(address, page?, limit?)` | Retrieve enhanced transactions for a wallet or program. |

### Sender API

The Sender API allows you to send transactions with high reliability and fetch Jito tip floors.
Accessible via `LunaHeliusClient.sender`.

| Method | Description |
|-------|-------------|
| `getSenderTipFloor()` | Fetches the 75th percentile tip floor from Jito. |
| `sendTransaction(transaction, region?, swqosOnly?)` | Sends a transaction via the Helius Sender API. |

### Webhooks API

Helius' webhooks let you subscribe to on-chain events and receive HTTP callbacks
when they occur.  These methods live on `LunaHeliusClient.webhooks`.

| Method | Description |
|-------|-------------|
| `createWebhook(webhookUrl, accountAddresses, transactionTypes, webhookType?, authHeader?, version?)` | Create a new webhook subscription. |
| `getWebhookById(id)` | Retrieve a webhook by its ID. |
| `getAllWebhooks()` | List all webhooks tied to your API key. |
| `updateWebhook(id, updates)` | Update fields of an existing webhook. |
| `deleteWebhook(id)` | Delete a webhook subscription. |

### WebSocket API

Helius supports the full suite of Solana WebSocket methods such as
`accountSubscribe`, `logsSubscribe`, `programSubscribe`, `signatureSubscribe`
and others.  The `LunaSDK` provides a `ws` namespace with helper methods to
connect and generate subscription messages.

```kotlin
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.Response

// Define a listener to handle events
val listener = object : WebSocketListener() {
    override fun onOpen(webSocket: WebSocket, response: Response) {
        println("Connected!")
        // Subscribe to account updates
        val subscribeMsg = helius.ws.accountSubscribe("86xCnPeV69n6t3DnyGvkKobf9FdN2H9oiVDdaMpo2MMY")
        webSocket.send(subscribeMsg)
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        println("Received: $text")
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        println("Closing: $code / $reason")
        webSocket.close(1000, null)
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        println("Error: ${t.message}")
    }
}

// Connect
val ws = helius.ws.connect(listener)

// Keep the process alive to receive messages (for example purposes)
// Thread.sleep(10000)
// ws.close(1000, "Goodbye")
```

| Method | Description |
|-------|-------------|
| `connect(listener)` | Open a WebSocket connection using the provided OkHttp listener. |
| `accountSubscribe(pubkey, ...)` | Generate JSON for account subscription. |
| `logsSubscribe(filter, ...)` | Generate JSON for logs subscription. |
| `programSubscribe(programId, ...)` | Generate JSON for program subscription. |
| `signatureSubscribe(signature, ...)` | Generate JSON for signature subscription. |
| `slotSubscribe()` | Generate JSON for slot subscription. |
| `transactionSubscribe(filters, options)` | Generate JSON for enhanced transaction subscription. |

### Compressed NFT Event Listening

The SDK supports all major methods for listening to Compressed NFT (cNFT) events: Standard WebSockets, Enhanced WebSockets, and Webhooks.

**1. Standard WebSockets (Bubblegum Program)**

Subscribe to the Bubblegum program to catch all cNFT events.

```kotlin
val BUBBLEGUM_PROGRAM_ID = "BGUMAp9Gq7iTEuizy4pqaxsTyUCBK68MDfK752saRPUY"
val msg = helius.ws.programSubscribe(
    programId = BUBBLEGUM_PROGRAM_ID,
    commitment = "confirmed",
    encoding = "jsonParsed"
)
webSocket.send(msg)
```

**2. Enhanced WebSockets (Transaction Subscribe)**

Use `transactionSubscribe` for advanced filtering (e.g., specific Merkle trees).

```kotlin
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.add

val filters = buildJsonObject {
    put("vote", false)
    put("failed", false)
    putJsonArray("accountInclude") {
        add("YOUR_MERKLE_TREE_ADDRESS")
    }
}

val options = buildJsonObject {
    put("commitment", "confirmed")
    put("encoding", "jsonParsed")
    put("transactionDetails", "full")
}

val msg = helius.ws.transactionSubscribe(filters, options)
webSocket.send(msg)
```

**3. Webhooks**

Create a webhook to receive HTTP callbacks for cNFT events.

```kotlin
val response = helius.webhooks.createWebhook(
    webhookUrl = "https://myapp.com/cnft-webhook",
    accountAddresses = listOf("YOUR_MERKLE_TREE_ADDRESS"),
    transactionTypes = listOf("ANY"),
    webhookType = "enhanced",
    authHeader = "Bearer your-auth-token"
)
println("Webhook ID: ${response.result?.jsonObject?.get("webhookID")}")
```

### ZK Compression API

ZK Compression endpoints index, verify and manage compressed accounts on
Solana.  These calls are grouped under `LunaHeliusClient.zk`.

| Method | Description |
|-------|-------------|
| `getCompressedAccount(hashOrAddress)` | Retrieve a compressed account. |
| `getCompressedAccountProof(hashOrAddress)` | Fetch a Merkle proof for a compressed account. |
| `getCompressedAccountsByOwner(owner)` | List all compressed accounts owned by an address. |
| `getCompressedBalance(hashOrAddress)` | Get the balance of a compressed account. |
| `getCompressedBalanceByOwner(owner)` | Get the total balance of compressed accounts for an owner. |
| `getCompressedMintTokenHolders(mint)` | List holders of a compressed mint. |
| `getCompressedTokenAccountBalance(tokenAccount)` | Get the balance of a compressed token account. |
| `getCompressedTokenAccountsByDelegate(delegate)` | List compressed token accounts delegated to an address. |
| `getCompressedTokenAccountsByOwner(owner)` | List compressed token accounts owned by an address. |
| `getCompressedTokenBalancesByOwner(owner)` | Retrieve token balances for compressed accounts owned by an address. |
| `getCompressedTokenBalancesByOwnerV2(owner)` | Same as above but solves a naming issue. |
| `getCompressionSignaturesForAccount(hash)` | Return signatures of transactions that opened or closed a compressed account. |
| `getCompressionSignaturesForAddress(address)` | Return signatures of transactions that opened or closed compressed accounts for an address. |
| `getCompressionSignaturesForOwner(owner)` | Signatures of transactions that modified an owner's compressed accounts. |
| `getCompressionSignaturesForTokenOwner(owner)` | Signatures of transactions modifying an owner's compressed token accounts. |
| `getIndexerHealth()` | Check if the compression indexer is healthy. |
| `getIndexerSlot()` | Get the slot of the last block indexed. |
| `getLatestCompressionSignatures(limit?)` | Return signatures of the latest compression transactions. |
| `getLatestNonVotingSignatures(limit?)` | Return signatures of the latest non-vote transactions. |
| `getMultipleCompressedAccountProofs(hashesOrAddresses)` | Fetch proofs for multiple compressed accounts. |
| `getMultipleCompressedAccounts(hashesOrAddresses)` | Retrieve multiple compressed accounts. |
| `getMultipleNewAddressProofs(newAddresses)` | Prove that a set of new addresses are unused. |
| `getMultipleNewAddressProofsV2(newAddresses)` | Same as above (V2). |
| `getTransactionWithCompressionInfo(signature)` | Retrieve a transaction and parse compression info. |
| `getValidityProof(args)` | Return a ZK proof to verify compressed accounts and new address creation. |

### Niche & Composite API

The `niche` namespace provides high-level, composite methods that combine multiple RPC calls into single operations. These are designed for specific use cases like gaming, dashboards, and deep analysis.

| Method | Description |
|-------|-------------|
| `getWalletPortfolio(address)` | Returns a complete snapshot of a wallet, including SOL balance and all DAS assets. |
| `getTokenDeepDive(mint)` | Fetches metadata, supply, and largest accounts for a token in one call. |
| `verifyGameAccess(address, ...)` | Verifies if a user meets specific criteria (balance + asset ownership) to access a feature. |
| `getAllAssetsByOwner(address, maxPages)` | Recursively fetches **all** assets for a wallet, handling pagination automatically. |
| `getAllAssetsByGroup(groupKey, groupValue, maxPages)` | Recursively fetches **all** assets for a group (e.g. Collection), handling pagination automatically. |
| `getTPS()` | Calculates the current network Transactions Per Second (TPS). |

### Solana Name Service (SNS)

Access via `client.sns`.

| Method | Description |
|-------|-------------|
| `getDomains(owner)` | Returns all `.sol` domains owned by a wallet. |
| `getFavoriteDomain(owner)` | Returns the primary/favorite domain for a wallet. |

### Memo API

Access via `client.memo`.

| Method | Description |
|-------|-------------|
| `getMemosForTransaction(signature)` | Extracts SPL Memo instructions from a transaction. |

### Mobile & Android Utilities

Access via `client.mobile`.

| Method | Description |
|-------|-------------|
| `generatePaymentLink(recipient, amount...)` | Generates `solana:` deep links for QR codes or intents. |
| `parsePaymentLink(uri)` | Parses a `solana:` URI into a map of parameters. |
| `isValidAddress(address)` | Validates if a string is a valid Solana address format. |
| `getAssetLite(assetId)` | Returns a lightweight asset object (ID, Name, Image) optimized for mobile lists. |

### LaserStream gRPC (Data Streaming)

Helius offers a high-performance gRPC streaming service called **LaserStream** for
real-time Solana data.  The LaserStream API includes methods to subscribe to
accounts, transactions, blocks and slot updates.

The `LunaSDK` provides a `laser` namespace with configuration helpers:

```kotlin
val endpoint = helius.laser.getDefaultEndpoint()
val token = helius.laser.getAuthToken()
```

You can use these with any standard gRPC client (e.g. `grpc-kotlin` or `yellowstone-grpc`) to connect to LaserStream.
See the [Helius LaserStream Documentation](https://docs.helius.dev/laserstream) for details.

---

# IrisSDK for QuickNode

**IrisSDK** is the definitive Kotlin SDK for [QuickNode](https://quicknode.com), offering comprehensive access to ALL QuickNode Solana methods plus marketplace add-ons. Named after the Greek goddess of the rainbow and swift messenger, Iris delivers lightning-fast access to QuickNode's infrastructure.

## Iris Highlights

- **Complete QuickNode Coverage**: Every Solana JSON-RPC method plus QuickNode-exclusive enhancements
- **Marketplace Add-On Integration**: Native support for JITO, Jupiter Metis, Pump.fun, Priority Fees, and more
- **Yellowstone gRPC Streaming**: Real-time data streaming via QuickNode's Yellowstone Geyser plugin
- **Transaction Fastlane**: Sub-slot transaction propagation for time-critical operations
- **Privacy Innovations**: Exclusive privacy features including stealth addresses, JITO mixing, and graph analysis
- **Smart Transaction Optimization**: Intelligent fee estimation and routing for optimal execution

## Iris Installation

```kotlin
// build.gradle.kts
dependencies {
    implementation("xyz.selenus:iris-sdk:1.0.0")
}
```

```xml
<!-- Maven pom.xml -->
<dependency>
    <groupId>xyz.selenus</groupId>
    <artifactId>iris-sdk</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Iris Quick Start

```kotlin
import com.selenus.iris.IrisQuickNodeClient
import com.selenus.iris.SolanaNetwork
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    // Initialize with your QuickNode endpoint
    val iris = IrisQuickNodeClient(
        endpoint = "https://your-endpoint.solana-mainnet.quiknode.pro/YOUR-API-KEY/",
        network = SolanaNetwork.MAINNET_BETA
    )
    
    // Standard RPC
    val balance = iris.rpc.getBalance("YourWalletAddress...")
    println("Balance: ${balance.value} lamports")
    
    // DAS API (Digital Asset Standard)
    val nfts = iris.das.getAssetsByOwner("YourWalletAddress...")
    println("NFTs owned: ${nfts.items.size}")
    
    // Jupiter Swap via Metis
    val quote = iris.metis.getQuote(
        inputMint = "So11111111111111111111111111111111111111112", // SOL
        outputMint = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v", // USDC
        amount = 1_000_000_000 // 1 SOL
    )
    println("Best route: ${quote.routePlan?.size} hops, output: ${quote.outAmount}")
    
    // JITO Bundle (requires Lil' JIT add-on)
    val tipFloor = iris.jito.getTipFloor()
    println("Current JITO tip floor: ${tipFloor.landedTips25thPercentile} lamports")
    
    // Priority Fee Estimation
    val fees = iris.priority.estimatePriorityFees()
    println("Recommended fee: ${fees.perComputeUnit.medium} microlamports/CU")
    
    // Privacy Score
    val privacyScore = iris.privacy.analyzeWallet("WalletAddress...")
    println("Privacy score: ${privacyScore.overallScore}/100")
}
```

## Iris Namespaces Reference

### Standard RPC (`iris.rpc`)

| Method | Description |
|--------|-------------|
| `getAccountInfo(pubkey)` | Returns account data for a public key |
| `getBalance(pubkey)` | Returns SOL balance in lamports |
| `getBlock(slot)` | Returns block information for a slot |
| `getBlockHeight()` | Returns current block height |
| `getLatestBlockhash()` | Returns latest blockhash for transactions |
| `getSlot()` | Returns current slot |
| `getTransaction(signature)` | Returns transaction details |
| `sendTransaction(signedTx)` | Submits a signed transaction |
| `simulateTransaction(tx)` | Simulates a transaction |
| `getTokenAccountsByOwner(owner)` | Returns token accounts for a wallet |
| `getMultipleAccounts(pubkeys)` | Returns multiple accounts in one call |
| `getSignaturesForAddress(address)` | Returns transaction signatures |
| `getProgramAccounts(program)` | Returns all accounts owned by a program |

### Digital Asset Standard (`iris.das`)

| Method | Description |
|--------|-------------|
| `getAsset(assetId)` | Returns metadata for a single asset |
| `getAssets(assetIds)` | Batch fetch multiple assets |
| `getAssetProof(assetId)` | Returns Merkle proof for compressed NFT |
| `getAssetsByOwner(owner)` | Returns all assets owned by a wallet |
| `getAssetsByCreator(creator)` | Returns assets by creator address |
| `getAssetsByCollection(collection)` | Returns assets in a collection |
| `searchAssets(query)` | Advanced asset search with filters |
| `getTokenAccounts(owner)` | Returns fungible token accounts |
| `getNftEditions(masterEdition)` | Returns all editions of an NFT |

### Jupiter Metis Swap (`iris.metis`)

| Method | Description |
|--------|-------------|
| `getQuote(input, output, amount)` | Get best swap route and price |
| `getQuoteBySymbol(inputSymbol, outputSymbol, amount)` | Quote using token symbols |
| `getSwapTransaction(quote, userPubkey)` | Build swap transaction from quote |
| `getSwapInstructions(quote, userPubkey)` | Get raw swap instructions |
| `getPrice(mintAddresses)` | Get current token prices |
| `getNewPools(limit)` | Discover newly created liquidity pools |
| `createLimitOrder(...)` | Create a limit order |
| `cancelLimitOrder(order, userPubkey)` | Cancel an existing limit order |
| `getOpenLimitOrders(wallet)` | List open limit orders |

### JITO Bundles (`iris.jito`)

Requires **Lil' JIT** add-on ($89/month)

| Method | Description |
|--------|-------------|
| `getTipFloor()` | Returns current minimum tip amounts |
| `getTipAccounts()` | Returns JITO tip account addresses |
| `getOptimalTip(priority)` | Calculate optimal tip for priority level |
| `sendBundle(transactions)` | Submit transaction bundle |
| `simulateBundle(bundle)` | Simulate bundle execution |
| `sendTransaction(signedTx, tip)` | Send single transaction with tip |
| `getBundleStatuses(bundleIds)` | Check bundle confirmation status |
| `getInflightBundleStatuses(bundleIds)` | Check in-flight bundle status |
| `getRegions()` | List available JITO regions |

### Yellowstone Streaming (`iris.yellowstone`)

Requires **Yellowstone Geyser** add-on ($499/month)

| Method | Description |
|--------|-------------|
| `subscribeToAccount(pubkey)` | Stream real-time account updates |
| `subscribeToAccounts(pubkeys)` | Stream updates for multiple accounts |
| `subscribeToTransactions(filter)` | Stream matching transactions |
| `subscribeToSlots()` | Stream slot notifications |
| `subscribeToProgramAccounts(program)` | Stream program account changes |
| `getHistoricalAccountUpdates(pubkey, slots)` | Fetch historical updates |

### Priority Fees (`iris.priority`)

| Method | Description |
|--------|-------------|
| `estimatePriorityFees(account?)` | Get recommended priority fees |

### Pump.fun Trading (`iris.pumpfun`)

| Method | Description |
|--------|-------------|
| `getQuote(type, mint, amount)` | Get pump.fun bonding curve quote |
| `getSwapTransaction(quote, wallet)` | Build pump.fun swap transaction |

### Transaction Fastlane (`iris.fastlane`)

| Method | Description |
|--------|-------------|
| `getTipAccounts()` | Get fastlane tip accounts |
| `sendFastTransaction(signedTx, tipLamports)` | Send with sub-slot propagation |

### Privacy Innovations (`iris.privacy`)

**EXCLUSIVE** features not available in any other SDK:

| Method | Description |
|--------|-------------|
| `analyzeWallet(address)` | Comprehensive privacy scoring (0-100) |
| `generateStealthAddress(recipientPubkey)` | Create one-time receiving address |
| `sendMixedTransaction(...)` | JITO-based transaction mixing |
| `createPrivacyRoutePlan(from, to, amount)` | Multi-hop privacy-preserving routing |
| `analyzeTransactionGraph(address, depth)` | Analyze wallet connections and clusters |

### Smart Optimization (`iris.smart`)

| Method | Description |
|--------|-------------|
| `createOptimizationPlan(transactions)` | Intelligent transaction optimization |
| `estimateOptimalFee(accountKeys)` | Context-aware fee estimation |

### WebSocket Subscriptions (`iris.ws`)

| Method | Description |
|--------|-------------|
| `subscribeAccount(pubkey)` | Subscribe to account changes |
| `subscribeSlot()` | Subscribe to slot updates |
| `subscribeLogs(mention?)` | Subscribe to transaction logs |
| `subscribeProgram(programId)` | Subscribe to program changes |
| `subscribeSignature(signature)` | Subscribe to transaction confirmation |

## QuickNode Add-Ons

IrisSDK integrates with these QuickNode Marketplace add-ons:

| Add-On | Price | Namespace | Description |
|--------|-------|-----------|-------------|
| **Metis - Jupiter V6 Swap** | FREE | `metis` | Best-in-class DEX aggregation |
| **Priority Fee API** | FREE | `priority` | Dynamic fee estimation |
| **Pump.fun API** | FREE | `pumpfun` | Bonding curve trading |
| **Transaction Fastlane** | FREE (Beta) | `fastlane` | Sub-slot transaction propagation |
| **Metaplex DAS API** | FREE | `das` | NFT and token metadata |
| **Lil' JIT - JITO Bundles** | $89/mo | `jito` | MEV protection and tips |
| **Yellowstone Geyser** | $499/mo | `yellowstone` | Real-time gRPC streaming |

## Privacy Features Deep Dive

IrisSDK includes innovative privacy features that leverage QuickNode's infrastructure:

### Privacy Scoring

```kotlin
val score = iris.privacy.analyzeWallet("WalletAddress...")
println("Overall: ${score.overallScore}/100")
println("Breakdown:")
println("  - Transaction Patterns: ${score.factors.transactionPatternScore}")
println("  - Timing Analysis: ${score.factors.timingScore}")  
println("  - Counterparty Diversity: ${score.factors.counterpartyDiversityScore}")
println("  - Amount Patterns: ${score.factors.amountPatternScore}")
println("  - Address Reuse: ${score.factors.addressReuseScore}")
```

### Stealth Addresses

```kotlin
// Generate one-time address for receiving
val stealth = iris.privacy.generateStealthAddress("RecipientPubkey...")
println("Send to: ${stealth.stealthAddress}")
println("Scan key: ${stealth.scanKey}")
// Recipient uses scanKey to detect and claim funds
```

### Transaction Mixing via JITO

```kotlin
// Send with mixing through JITO bundles
val result = iris.privacy.sendMixedTransaction(
    signedTransaction = mySignedTx,
    mixingLevel = 3,
    tipLamports = 10000
)
println("Bundle: ${result.bundleId}")
```

### Multi-Hop Routing

```kotlin
// Create privacy-preserving transfer route
val plan = iris.privacy.createPrivacyRoutePlan(
    fromAddress = "Source...",
    toAddress = "Destination...",
    amount = 1_000_000_000L
)
println("Hops: ${plan.hops.size}")
println("Estimated privacy gain: ${plan.estimatedPrivacyGain}%")
```

For complete documentation, see the [Iris SDK Guide](docs/IrisSDK_Guide.md).

---

## 🤝 Contributing

Contributions are welcome! If you notice missing endpoints or inaccurate
documentation, feel free to open a pull request.

---

## 👤 Author

- **@moonmanquark** on X (Twitter): [https://x.com/moonmanquark](https://x.com/moonmanquark)

For support, please reach out to **@moonmanquark** on X.

---

## 💜 Support Development

If you find this SDK useful, please consider donating to support development:

**Solana Address:** `solanadevdao.sol` or `F42ZovBoRJZU4av5MiESVwJWnEx8ZQVFkc1RM29zMxNT`

---

<p align="center">
  <b>Built with 💜 by @moonmanquark & Selenus</b>
</p>
