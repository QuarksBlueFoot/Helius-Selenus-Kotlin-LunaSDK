# LunaSDK for Helius

**LunaSDK** is a modern, Kotlin-first client for the [Helius](https://www.helius.dev/) platform.  It exposes
Helius' Solana APIs, including Digital Asset Standard (DAS), enhanced RPC methods, staking,
transactions, priority fee estimation, enhanced transactions, webhooks, WebSockets and ZK-Compression,
as idiomatic, suspendable Kotlin functions.  The goal is to provide Android and JVM developers with a clean,
coroutine-friendly interface that hides JSON-RPC boilerplate and makes interacting with Helius simple.

For more information, visit [selenus.bluefootlabs.com](https://selenus.bluefootlabs.com).

## Highlights

- **Modern design**: built around Kotlin coroutines, data classes and sealed types to leverage
  contemporary language features.
- **Strong typing**: request and response objects are represented as data classes where the Helius API
  schemas are stable, while dynamic fields fall back to `JsonElement` for maximum flexibility.
- **Easy configuration**: a single `LunaHeliusClient` takes an API key and cluster, then exposes
  namespaced APIs via properties (`das`, `rpc`, `staking`, `tx`, `priority`, `enhanced`, `webhooks`,
  `ws`, `zk`).  Each namespace groups related methods to mirror the structure of the official Helius
  Node.js SDK described in the Helius documentation.
- **No dependencies on web3.js**: calls are made directly via HTTP using OkHttp and `kotlinx.serialization`.
- **Extensible**: additional endpoints can be added by creating new methods in the appropriate
  namespace.

## Usage example

```kotlin
import com.selenus.luna.LunaHeliusClient
import com.selenus.luna.Cluster
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val apiKey = "YOUR_HELIUS_API_KEY"
    val helius = LunaHeliusClient(apiKey, Cluster.MAINNET)

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

    // Get Jito Tip Floor (Sender API)
    val tip = helius.sender.getSenderTipFloor()
    println("Tip Floor: ${tip.result}")

    // Standard Solana RPC call (e.g. get balance)
    val balance = helius.solana.getBalance("86xCnPeV69n6t3DnyGvkKobf9FdN2H9oiVDdaMpo2MMY")
    println("Balance: ${balance?.result}")

    // Create and send a smart transaction (example only, replace with real serialized tx)
    // val txResponse = helius.tx.sendTransaction("base64EncodedTransaction")
    // println(txResponse?.result)
}
```

Refer to the [LunaHeliusClient source](src/main/kotlin/com/selenus/luna/LunaHeliusClient.kt) for the
complete list of available methods and their usage.  See the [Helius docs](https://www.helius.dev/docs/api-reference)
for explanations of each endpoint and its parameters.

## Example App

A complete Android sample application is available in the `sample-app` directory. It demonstrates how to configure the client, make DAS requests, and handle responses in an Android environment. See the [Sample App README](sample-app/README.md) for setup instructions.

## Installation

LunaSDK is distributed as a Gradle module.  You can include it in your project by
publishing the module locally or copying the `luna-sdk` folder into your Android
or JVM build.  The library depends only on `okhttp` and `kotlinx.serialization`.

```
// settings.gradle.kts
include(":luna-sdk")

// build.gradle.kts for your app module
dependencies {
    implementation(project(":luna-sdk"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
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

## Contributing

Contributions are welcome!  If you notice missing endpoints or inaccurate
documentation, feel free to open a pull request.

## Author

- **@moonmanquark** on X (Twitter): [https://x.com/moonmanquark](https://x.com/moonmanquark)

For support, please reach out to **@moonmanquark** on X.

## Donations

If you find this SDK useful, please consider donating to support development:

- **Solana Address**: `solanadevdao.sol` or `F42ZovBoRJZU4av5MiESVwJWnEx8ZQVFkc1RM29zMxNT`
