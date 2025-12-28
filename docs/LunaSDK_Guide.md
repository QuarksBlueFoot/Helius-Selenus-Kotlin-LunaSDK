# LunaSDK Comprehensive Guide

This guide provides a detailed overview of the **LunaSDK** for Helius, covering all available features and methods.

## Table of Contents

1. [Installation](#installation)
2. [Configuration](#configuration)
3. [Android Integration](#android-integration)
4. [Digital Asset Standard (DAS)](#digital-asset-standard-das)
5. [Standard Solana RPC](#standard-solana-rpc)
6. [Enhanced RPC](#enhanced-rpc)
7. [Enhanced Transactions](#enhanced-transactions)
8. [Staking](#staking)
9. [Transactions & Sender API](#transactions--sender-api)
10. [Priority Fees](#priority-fees)
11. [Webhooks](#webhooks)
12. [WebSockets](#websockets)
13. [ZK Compression](#zk-compression)
14. [LaserStream](#laserstream)
15. [Example App](#example-app)

---

## Installation

### Option 1: Local Module (Recommended for development)

Add the following to your module's `build.gradle.kts`:

```kotlin
dependencies {
    implementation(project(":luna-sdk"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
```

### Option 2: Maven Central

If you are using the published version:

```kotlin
dependencies {
    implementation("xyz.selenus:luna-sdk:1.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
```

## Configuration

Initialize the client with your Helius API key and desired cluster.

```kotlin
import com.selenus.luna.LunaHeliusClient
import com.selenus.luna.Cluster

val apiKey = "YOUR_API_KEY"
val client = LunaHeliusClient(apiKey, Cluster.MAINNET)
```

---

## Android Integration

Integrating LunaSDK into an Android application requires a few standard setup steps to ensure network access and proper concurrency management.

### 1. Permissions

Add the Internet permission to your `AndroidManifest.xml` file. This is required for all network requests.

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.yourapp">

    <uses-permission android:name="android.permission.INTERNET" />

    <application ...>
        ...
    </application>
</manifest>
```

### 2. Coroutines & Scopes

LunaSDK is built with Kotlin Coroutines. Network calls are `suspend` functions and must be called from a coroutine scope.

**In a ViewModel:**
Use `viewModelScope` to launch coroutines. This ensures requests are cancelled if the ViewModel is cleared.

```kotlin
class MyViewModel : ViewModel() {
    private val client = LunaHeliusClient("YOUR_API_KEY")

    fun fetchAssets() {
        viewModelScope.launch {
            try {
                val response = client.das.getAssetsByOwner("Wallet_Address")
                // Update UI with response.result
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
}
```

**In an Activity/Fragment:**
Use `lifecycleScope` (requires `androidx.lifecycle:lifecycle-runtime-ktx`).

```kotlin
lifecycleScope.launch {
    val balance = client.solana.getBalance("Wallet_Address")
}
```

### 3. Dependency Injection (Recommended)

Avoid creating a new `LunaHeliusClient` for every request. Create a single instance (Singleton) and inject it where needed.

**Using Hilt/Dagger:**

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideHeliusClient(): LunaHeliusClient {
        return LunaHeliusClient(
            apiKey = BuildConfig.HELIUS_API_KEY, // Store key in local.properties/BuildConfig
            cluster = Cluster.MAINNET
        )
    }
}
```

### 4. ProGuard / R8

LunaSDK uses `kotlinx.serialization`. If you are using R8 (enabled by default in release builds), it generally handles serialization rules automatically. However, if you encounter runtime crashes related to missing serializers, ensure you have the standard Kotlin serialization rules.

The SDK itself does not use reflection for anything other than serialization, so aggressive shrinking should be safe as long as your data classes are preserved if you use them in other reflective ways.

### 5. Network Security

Helius APIs use HTTPS. Android's default network security configuration allows HTTPS, so no special `network_security_config.xml` is needed unless you are testing against a local mock server using HTTP.

---

## Digital Asset Standard (DAS)

Access via `client.das`.

### Methods

*   **`getAsset(assetId, ...)`**: Fetch a single asset.
*   **`getAssetBatch(assetIds, ...)`**: Fetch multiple assets (up to 1000).
*   **`getAssetsByOwner(ownerAddress, ...)`**: List assets owned by a wallet.
*   **`getAssetsByAuthority(authorityAddress, ...)`**: List assets by update authority.
*   **`getAssetsByCreator(creatorAddress, ...)`**: List assets by creator.
*   **`getAssetsByGroup(groupKey, groupValue, ...)`**: List assets in a collection/group.
*   **`searchAssets(filters)`**: Search assets using arbitrary filters.
*   **`getAssetProof(assetId)`**: Get Merkle proof for a compressed asset.
*   **`getAssetProofBatch(assetIds)`**: Get multiple Merkle proofs.
*   **`getNftEditions(masterAssetId, ...)`**: Get editions of a master NFT.
*   **`getTokenAccounts(mint?, owner?, ...)`**: Get token accounts.
*   **`getSignaturesForAsset(assetId, ...)`**: Get transaction history for an asset.

### Example

```kotlin
val asset = client.das.getAsset("Asset_ID").result
val myAssets = client.das.getAssetsByOwner("Wallet_Address", page = 1, limit = 50).result
```

---

## Standard Solana RPC

Access via `client.solana`. Supports standard Solana JSON-RPC methods.

### Methods

*   **`getAccountInfo(pubkey, ...)`**: Returns all information associated with the account of provided Pubkey.
*   **`getBalance(pubkey, ...)`**: Returns the balance of the account of provided Pubkey.
*   **`getBlock(slot, ...)`**: Returns identity and transaction information about a confirmed block in the ledger.
*   **`getBlockHeight(...)`**: Returns the current block height of the node.
*   **`getBlockProduction(...)`**: Returns recent block production information from the current or previous epoch.
*   **`getBlockCommitment(slot)`**: Returns commitment for particular block.
*   **`getBlocks(startSlot, endSlot, ...)`**: Returns a list of confirmed blocks between two slots.
*   **`getBlocksWithLimit(startSlot, limit, ...)`**: Returns a list of confirmed blocks starting at the given slot.
*   **`getBlockTime(slot)`**: Returns the estimated production time of a block.
*   **`getClusterNodes()`**: Returns information about all the nodes participating in the cluster.
*   **`getEpochInfo(...)`**: Returns information about the current epoch.
*   **`getEpochSchedule()`**: Returns the epoch schedule information from this cluster's genesis config.
*   **`getFeeForMessage(message, ...)`**: Returns the fee the network will charge for a particular Message.
*   **`getFirstAvailableBlock()`**: Returns the slot of the lowest confirmed block that has not been purged from the ledger.
*   **`getGenesisHash()`**: Returns the genesis hash.
*   **`getHealth()`**: Returns the current health of the node.
*   **`getHighestSnapshotSlot()`**: Returns the highest slot information that the node has snapshots for.
*   **`getIdentity()`**: Returns the identity pubkey for the current node.
*   **`getInflationGovernor(...)`**: Returns the current inflation governor.
*   **`getInflationRate()`**: Returns the specific inflation values for the current epoch.
*   **`getInflationReward(addresses, ...)`**: Returns the inflation / staking reward for a list of addresses.
*   **`getLargestAccounts(...)`**: Returns the 20 largest accounts, by lamport balance.
*   **`getLatestBlockhash(...)`**: Returns the latest blockhash.
*   **`getLeaderSchedule(...)`**: Returns the leader schedule for an epoch.
*   **`getMaxRetransmitSlot()`**: Returns the max slot seen from retransmit stage.
*   **`getMaxShredInsertSlot()`**: Returns the max slot seen from after shred insert.
*   **`getMinimumBalanceForRentExemption(dataLength, ...)`**: Returns minimum balance required to make account rent exempt.
*   **`getMultipleAccounts(pubkeys, ...)`**: Returns the account information for a list of Pubkeys.
*   **`getProgramAccounts(programId, ...)`**: Returns all accounts owned by the provided program Pubkey.
*   **`getRecentPerformanceSamples(...)`**: Returns a list of recent performance samples.
*   **`getRecentPrioritizationFees(...)`**: Returns a list of prioritization fees from recent blocks.
*   **`getSignaturesForAddress(address, ...)`**: Returns signatures for confirmed transactions that include the given address.
*   **`getSignatureStatuses(signatures, ...)`**: Returns the statuses of a list of signatures.
*   **`getSlot(...)`**: Returns the slot that has reached the given or default commitment level.
*   **`getSlotLeader(...)`**: Returns the current slot leader.
*   **`getSlotLeaders(...)`**: Returns the slot leaders for a given slot range.
*   **`getStakeMinimumDelegation(...)`**: Returns the stake minimum delegation.
*   **`getSupply(...)`**: Returns information about the current supply.
*   **`getTokenAccountBalance(pubkey, ...)`**: Returns the token balance of an SPL Token account.
*   **`getTokenAccountsByDelegate(delegate, ...)`**: Returns all SPL Token accounts by approved delegate.
*   **`getTokenAccountsByOwner(owner, ...)`**: Returns all SPL Token accounts by token owner.
*   **`getTokenLargestAccounts(mint, ...)`**: Returns the 20 largest accounts of a particular SPL Token type.
*   **`getTokenSupply(mint, ...)`**: Returns the total supply of an SPL Token type.
*   **`getTransaction(signature, ...)`**: Returns transaction details for a confirmed transaction.
*   **`getTransactionCount(...)`**: Returns the current Transaction count from the ledger.
*   **`getVersion()`**: Returns the current solana-core version running on the node.
*   **`getVoteAccounts(...)`**: Returns the account info and associated stake for all the voting accounts in the current bank.
*   **`isBlockhashValid(blockhash, ...)`**: Returns whether a blockhash is still valid.
*   **`minimumLedgerSlot()`**: Returns the lowest slot that the node has information about in its ledger.
*   **`requestAirdrop(pubkey, lamports, ...)`**: Requests an airdrop of lamports to a Pubkey.

### Example

```kotlin
val balance = client.solana.getBalance("Wallet_Address").result
val info = client.solana.getAccountInfo("Wallet_Address").result
```

---

## Enhanced RPC

Access via `client.rpc`.

### Methods

*   **`getTransactionsForAddress(address, options)`**: Advanced transaction history with filtering.
*   **`getProgramAccountsV2(programId, ...)`**: Pagination support for program accounts.
*   **`getAllProgramAccounts(programId)`**: Auto-paginated fetch of all accounts.
*   **`getTokenAccountsByOwnerV2(owner, ...)`**: Pagination support for token accounts.
*   **`getAllTokenAccountsByOwner(owner)`**: Auto-paginated fetch of all token accounts.

### Example

```kotlin
val txs = client.rpc.getTransactionsForAddress(
    address = "Wallet_Address",
    limit = 10
).result
```

---

## Enhanced Transactions

Access via `client.enhanced`.

### Methods

*   **`getTransactions(signatures)`**: Fetch human-readable parsed transactions.
*   **`getTransactionsByAddress(address, ...)`**: Fetch parsed transactions for an address.

### Example

```kotlin
val parsedTxs = client.enhanced.getTransactions(
    signatures = listOf("Signature1", "Signature2")
).result
```

---

## Staking

Access via `client.staking`.

### Methods

*   **`createStakeTransaction(wallet, amount, validator)`**: Create a new stake account.
*   **`createUnstakeTransaction(stakeAccount)`**: Deactivate stake.
*   **`createWithdrawTransaction(stakeAccount, amount)`**: Withdraw SOL.
*   **`getStakeInstructions(wallet, amount, validator)`**: Get raw instructions for staking.
*   **`getUnstakeInstruction(stakeAccount)`**: Get raw instruction to deactivate stake.
*   **`getWithdrawInstruction(stakeAccount, amount)`**: Get raw instruction to withdraw.
*   **`getWithdrawableAmount(stakeAccount, includeRentExempt)`**: Check withdrawable balance.
*   **`getHeliusStakeAccounts(wallet)`**: List Helius-delegated stake accounts.

---

## Transactions & Sender API

Access via `client.tx` and `client.sender`.

### Transaction Helpers (`client.tx`)

*   **`sendTransaction(txBase64)`**: Standard broadcast.
*   **`getComputeUnits(txBase64)`**: Simulate compute unit usage.
*   **`pollTransactionConfirmation(signature)`**: Wait for confirmation.
*   **`getSmartTransactionPlan(txBase64)`**: Get optimal CU limit and priority fee for a Smart Transaction.
*   **`sendSmartTransaction(signedTxBase64)`**: Send a transaction with Helius-recommended polling and rebroadcasting logic.

### Smart Transactions

"Smart Transactions" are a Helius feature that optimizes transaction delivery by calculating the perfect compute budget and priority fee, then routing the transaction through staked connections.

Since LunaSDK is a lightweight client without a heavy transaction builder dependency, you implement Smart Transactions in two steps:

1.  **Plan**: Use `getSmartTransactionPlan` to get the recommended parameters.
2.  **Build & Sign**: Use your preferred Solana library (e.g., `solana4k`, `metaplex-android`) to add the Compute Budget instructions and sign.
3.  **Send**: Use `sendSmartTransaction` to handle the submission and confirmation.

**Example Workflow:**

```kotlin
// 1. Create your initial transaction (using your preferred library)
val initialTx = mySolanaLib.createTransaction(instructions)

// 2. Get the plan
val plan = client.tx.getSmartTransactionPlan(initialTx.toBase64()).result!!

// 3. Rebuild transaction with optimization
val optimizedTx = mySolanaLib.createTransaction(
    instructions + listOf(
        ComputeBudget.setComputeUnitLimit(plan.computeUnits),
        ComputeBudget.setComputeUnitPrice(plan.priorityFeeEstimate)
    )
)

// 4. Sign and Send
val signedTx = optimizedTx.sign(myKeypair).toBase64()
val signature = client.tx.sendSmartTransaction(signedTx).result
```

### Sender API (`client.sender`)

High-performance transaction submission via Jito.

*   **`getSenderTipFloor()`**: Get the 75th percentile Jito tip floor.
*   **`sendTransaction(txBase64, region, swqosOnly)`**: Send via Helius Sender.

### Example

```kotlin
// Get Tip
val tip = client.sender.getSenderTipFloor().result

// Send Transaction
val sig = client.sender.sendTransaction(
    transaction = "base64_tx_string",
    region = LunaHeliusClient.SenderRegion.US_EAST
).result
```

---

## Priority Fees

Access via `client.priority`.

### Methods

*   **`getPriorityFeeEstimate(priorityLevel, ...)`**: Estimate optimal priority fees.

### Example

```kotlin
val fee = client.priority.getPriorityFeeEstimate(
    priorityLevel = "High",
    lookbackSlots = 100
).result
```

---

## Webhooks

Access via `client.webhooks`.

### Methods

*   **`createWebhook(url, addresses, types, ...)`**: Register a new webhook.
*   **`getAllWebhooks()`**: List all webhooks.
*   **`getWebhookById(id)`**: Get details.
*   **`updateWebhook(id, updates)`**: Modify a webhook.
*   **`deleteWebhook(id)`**: Remove a webhook.

---

## WebSockets

Access via `client.ws`.

### Methods

*   **`connect(listener)`**: Open connection.
*   **`accountSubscribe(pubkey)`**: Monitor account changes.
*   **`logsSubscribe(filter)`**: Monitor logs.
*   **`programSubscribe(programId)`**: Monitor program activity.
*   **`transactionSubscribe(filters, options)`**: Enhanced transaction monitoring.

### Example

```kotlin
val listener = object : WebSocketListener() {
    override fun onMessage(webSocket: WebSocket, text: String) {
        println("Event: $text")
    }
}
val ws = client.ws.connect(listener)
ws.send(client.ws.accountSubscribe("Account_Address"))
```

---

## ZK Compression

Access via `client.zk`.

### Methods

*   **`getCompressedAccount(hashOrAddress)`**: Retrieve a compressed account.
*   **`getCompressedAccountProof(hashOrAddress)`**: Get Merkle proof for an account.
*   **`getCompressedBalance(hashOrAddress)`**: Get balance of a compressed account.
*   **`getCompressedTokenAccountsByOwner(owner)`**: List compressed token accounts.
*   **`getCompressionSignaturesForAccount(hash)`**: Get history for a compressed account.
*   **`getValidityProof(args)`**: Get validity proof for transaction verification.
*   **`getCompressionSignaturesForAddress(address)`**: Get history for an address.
*   **`getCompressedAccountsByOwner(owner)`**: List all compressed accounts for an owner.
*   **`getCompressedBalanceByOwner(owner)`**: Get total compressed balance for an owner.
*   **`getCompressedMintTokenHolders(mint)`**: List holders of a compressed mint.
*   **`getCompressedTokenAccountBalance(tokenAccount)`**: Get balance of a specific token account.
*   **`getCompressedTokenAccountsByDelegate(delegate)`**: List accounts by delegate.
*   **`getCompressedTokenBalancesByOwner(owner)`**: Get token balances for an owner.
*   **`getCompressionSignaturesForOwner(owner)`**: Get history for an owner's accounts.
*   **`getCompressionSignaturesForTokenOwner(owner)`**: Get history for an owner's token accounts.
*   **`getIndexerHealth()`**: Check compression indexer status.
*   **`getIndexerSlot()`**: Get current indexed slot.
*   **`getLatestCompressionSignatures(limit)`**: Get recent compression transactions.
*   **`getMultipleCompressedAccountProofs(hashes)`**: Batch fetch proofs.
*   **`getMultipleCompressedAccounts(hashes)`**: Batch fetch accounts.
*   **`getMultipleNewAddressProofs(addresses)`**: Proofs for creating new addresses.
*   **`getTransactionWithCompressionInfo(signature)`**: Get transaction with compression details.

---

## LaserStream

Access via `client.laser`.

Provides configuration for connecting to Helius LaserStream gRPC service.

### Methods

*   **`getDefaultEndpoint()`**: Get the recommended gRPC endpoint.
*   **`getAuthToken()`**: Get the auth token (API Key).

### Usage

Use these values to configure your gRPC client (e.g., `yellowstone-grpc`).

```kotlin
val endpoint = client.laser.getDefaultEndpoint()
val token = client.laser.getAuthToken()
// Pass 'token' as 'x-token' metadata header
```

---

## Niche & Composite API

Access via `client.niche`.

This namespace provides high-level, composite methods that combine multiple RPC calls into single operations. These are designed for specific use cases like gaming, dashboards, and deep analysis.

### Methods

*   **`getWalletPortfolio(address)`**: Returns a complete snapshot of a wallet, including SOL balance and all DAS assets.
*   **`getTokenDeepDive(mint)`**: Fetches metadata, supply, and largest accounts for a token in one call.
*   **`verifyGameAccess(address, minSol, collection, mint)`**: Verifies if a user meets specific criteria (balance + asset ownership) to access a feature.
*   **`getAllAssetsByOwner(address, maxPages)`**: Recursively fetches **all** assets for a wallet, handling pagination automatically.
*   **`getAllAssetsByGroup(groupKey, groupValue, maxPages)`**: Recursively fetches **all** assets for a group (e.g. Collection), handling pagination automatically.
*   **`getTPS()`**: Calculates the current network Transactions Per Second (TPS).

### Example

```kotlin
// 1. Get full portfolio
val portfolio = client.niche.getWalletPortfolio("Wallet_Address").result
println("SOL: ${portfolio?.solBalance}")
println("Assets: ${portfolio?.assets}")

// 2. Verify Game Access
val access = client.niche.verifyGameAccess(
    address = "User_Wallet",
    minSolBalance = 0.01,
    requiredCollectionAddress = "Collection_Address"
).result

if (access?.hasAccess == true) {
    println("Welcome to the game!")
} else {
    println("Access Denied: ${access?.reason}")
}

// 3. Get ALL assets (auto-pagination)
val allAssets = client.niche.getAllAssetsByOwner("Wallet_Address").result
println("Total Assets: ${allAssets?.size}")

// 4. Get TPS
val tps = client.niche.getTPS().result
println("Current TPS: $tps")
```

---

## Solana Name Service (SNS)

Access via `client.sns`.

Helper methods for interacting with `.sol` domains.

### Methods

*   **`getDomains(owner)`**: Returns a list of all `.sol` domains owned by the wallet.
*   **`getFavoriteDomain(owner)`**: Returns the primary domain name for the wallet (currently returns the first found).

### Example

```kotlin
val domains = client.sns.getDomains("Wallet_Address").result
domains?.forEach { println("Domain: $it") }
```

---

## Memo API

Access via `client.memo`.

Helper methods for extracting memos from transactions.

### Methods

*   **`getMemosForTransaction(signature)`**: Fetches a transaction and extracts any SPL Memo instructions.

### Example

```kotlin
val memos = client.memo.getMemosForTransaction("Signature_String").result
memos?.forEach { println("Memo: $it") }
```

---

## Mobile & Android Utilities

Access via `client.mobile`.

Features designed to simplify Solana mobile development.

### Methods

*   **`generatePaymentLink(recipient, amount, label, message, memo)`**: Generates a standard `solana:` URI for deep linking or QR codes.
*   **`parsePaymentLink(uri)`**: Parses a `solana:` URI into a map of parameters.
*   **`isValidAddress(address)`**: Validates if a string is a valid Solana address format (regex check).
*   **`getAssetLite(assetId)`**: Fetches a lightweight version of an asset (ID, Name, Image URL only) to save bandwidth and parsing time in list views.

### Example

```kotlin
// Generate a payment link
val link = client.mobile.generatePaymentLink(
    recipient = "Wallet_Address",
    amount = 0.1,
    label = "Coffee",
    message = "Thanks for the coffee!"
)
println("Deep Link: $link")

// Parse a link
val params = client.mobile.parsePaymentLink(link)
println("Recipient: ${params["recipient"]}")

// Validate Address
if (client.mobile.isValidAddress("Wallet_Address")) {
    println("Valid Address")
}

// Get lite asset for RecyclerView
val liteAsset = client.mobile.getAssetLite("Asset_ID").result
println("Image URL: ${liteAsset?.get("image")}")
```

---

## Webhook Verification

To verify webhook signatures from Helius (Ed25519), you must use a cryptographic library as this SDK does not include one to keep dependencies light.

**Steps:**
1.  Extract the `signature` header from the incoming POST request.
2.  Get the raw request body as a string/bytes.
3.  Use a library like Bouncy Castle or TweetNacl to verify the signature against the body using the Helius Public Key.

---

## Example App

The SDK includes a comprehensive example application that demonstrates how to use all the features described in this guide.

### Location

The example code is located in:
`luna-sdk/src/test/kotlin/com/selenus/luna/ExampleTest.kt`

### Running the Examples

You can run the examples using Gradle. The test file contains a `main` function that executes a series of examples.

1.  Open `luna-sdk/src/test/kotlin/com/selenus/luna/ExampleTest.kt`.
2.  Replace `YOUR_API_KEY` with your actual Helius API key.
3.  Run the `main` function from your IDE, or use the following Gradle command:

```bash
./gradlew :luna-sdk:test --tests "com.selenus.luna.ExampleTest"
```

Note: Ensure you have a valid API key and, for some examples, a funded wallet if you intend to execute transactions.

