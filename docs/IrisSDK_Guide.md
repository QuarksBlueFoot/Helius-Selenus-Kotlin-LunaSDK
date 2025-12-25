# IrisSDK - The Definitive QuickNode Solana SDK

<div align="center">
  <h1>🌈 Iris SDK</h1>
  <p><em>Named after Iris, Greek goddess of the rainbow and swift messenger of the gods</em></p>
  <p><strong>The most comprehensive Kotlin SDK for QuickNode Solana infrastructure</strong></p>
</div>

---

## Features

### ✨ Complete QuickNode Coverage

IrisSDK is the **definitive** QuickNode Solana SDK, providing first-class support for:

| Feature | Description |
|---------|-------------|
| **Core Solana RPC** | All standard JSON-RPC methods with type-safe responses |
| **Metis Jupiter Swap API** | DEX aggregation, quotes, swaps, limit orders |
| **Lil' JIT JITO Bundles** | MEV protection, atomic execution, tip optimization |
| **Priority Fee API** | Real-time fee estimation for transaction prioritization |
| **Pump.fun API** | Bonding curve trading for new token launches |
| **Transaction Fastlane** | Sub-slot latency with 50%+ zero-slot execution |
| **DAS API** | NFT metadata, compressed assets, token accounts |
| **Yellowstone gRPC** | Real-time streaming with historical replay |

### 🔒 Privacy Innovations (Iris Exclusive)

Revolutionary privacy features unique to Iris SDK:

- **Privacy Scoring**: Analyze wallet transaction patterns and generate privacy scores
- **Stealth Addresses**: One-time addresses for private fund reception
- **JITO Bundle Mixing**: Privacy-enhanced transactions via atomic bundles
- **Multi-Hop Routing**: Route transactions through intermediaries with delays
- **Transaction Graph Analysis**: Identify address clusters and linked wallets

---

## Installation

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("xyz.selenus:iris-sdk:1.0.0")
}
```

### Gradle (Groovy)

```groovy
dependencies {
    implementation 'xyz.selenus:iris-sdk:1.0.0'
}
```

---

## Quick Start

```kotlin
import com.selenus.iris.*

// Initialize the client
val iris = IrisQuickNodeClient(
    endpoint = "https://your-endpoint.solana-mainnet.quiknode.pro/your-token/",
    network = SolanaNetwork.MAINNET_BETA
)

// Get SOL balance
val balance = iris.getBalanceSol("wallet-address")
println("Balance: $balance SOL")

// Get a Jupiter swap quote
val quote = iris.metis.getQuote(
    inputMint = MetisNamespace.WSOL_MINT,
    outputMint = MetisNamespace.USDC_MINT,
    amount = 1_000_000_000 // 1 SOL
)
println("You'll receive: ${quote.outAmount} USDC")

// Send via JITO for MEV protection
val signature = iris.jito.sendTransaction(signedTransaction)

// Analyze wallet privacy
val privacyScore = iris.privacy.analyzeWallet("wallet-address")
println("Privacy Score: ${privacyScore.overallScore}/100")
```

---

## Namespaces

### `iris.rpc` - Core Solana RPC

All standard Solana JSON-RPC methods with type-safe responses.

```kotlin
// Account operations
val accountInfo = iris.rpc.getAccountInfo(pubkey)
val balance = iris.rpc.getBalance(pubkey)
val tokenAccounts = iris.rpc.getTokenAccountsByOwner(owner, filter)

// Transaction operations
val signature = iris.rpc.sendTransaction(signedTx)
val tx = iris.rpc.getTransaction(signature)
val simulation = iris.rpc.simulateTransaction(tx)

// Block operations
val block = iris.rpc.getBlock(slot)
val blockHeight = iris.rpc.getBlockHeight()
val blockhash = iris.rpc.getLatestBlockhash()
```

### `iris.das` - Digital Asset Standard API

Comprehensive NFT and token metadata.

```kotlin
// Get single asset
val nft = iris.das.getAsset("asset-id")

// Get all assets owned by a wallet
val assets = iris.das.getAssetsByOwner(
    ownerAddress = "wallet-address",
    showFungible = true
)

// Get collection NFTs
val collection = iris.das.getAssetsByCollection("collection-address")

// Search with filters
val results = iris.das.searchAssets(
    owner = "wallet-address",
    compressed = true,
    burnt = false
)

// Get Merkle proof for compressed NFT
val proof = iris.das.getAssetProof("asset-id")
```

### `iris.metis` - Jupiter Swap API

DEX aggregation with elite latency.

```kotlin
// Get a quote
val quote = iris.metis.getQuote(
    inputMint = "So11111111111111111111111111111111111111112",
    outputMint = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v",
    amount = 1_000_000_000,
    slippageBps = 50
)

// Build swap transaction
val swapTx = iris.metis.getSwapTransaction(
    quote = quote,
    userPublicKey = "your-wallet"
)

// Get token prices
val prices = iris.metis.getPrice(listOf("token-mint-1", "token-mint-2"))
val solPrice = iris.metis.getSolPriceUSD()

// Discover new pools
val newPools = iris.metis.getNewPools(limit = 50)

// Limit orders
val order = iris.metis.createLimitOrder(
    inputMint = "SOL",
    outputMint = "USDC",
    inAmount = 1_000_000_000,
    outAmount = 100_000_000,
    maker = "your-wallet"
)
```

### `iris.jito` - JITO Bundles

MEV protection and atomic execution.

```kotlin
// Get tip information
val tipFloor = iris.jito.getTipFloor()
val tipAccounts = iris.jito.getTipAccounts()
val optimalTip = iris.jito.getOptimalTip(PriorityLevel.MEDIUM)

// Send a bundle
val bundleId = iris.jito.sendBundle(listOf(tx1, tx2, tx3))

// Simulate before sending
val simulation = iris.jito.simulateBundle(listOf(tx1, tx2))

// Track status
val status = iris.jito.getBundleStatus(bundleId)
```

### `iris.priority` - Priority Fees

Optimal fee estimation.

```kotlin
// Get priority fee for current network conditions
val fee = iris.priority.estimatePriorityFees(
    accounts = listOf("program-id"),
    level = PriorityLevel.HIGH
)

// Get detailed breakdown
val detailed = iris.priority.getDetailedFeeEstimate()
```

### `iris.pumpfun` - Pump.fun Trading

Trade bonding curve tokens.

```kotlin
// Get quote
val quote = iris.pumpfun.getQuote(
    inputMint = "SOL-mint",
    outputMint = "pump-token-mint",
    amount = 100_000_000
)

// Build swap
val swapTx = iris.pumpfun.getSwapTransaction(quote, "your-wallet")
```

### `iris.fastlane` - Transaction Fastlane

Sub-slot transaction propagation.

```kotlin
// Send via fastlane
val signature = iris.fastlane.sendTransaction(signedTx)

// Get tip account for inclusion
val tipAccount = iris.fastlane.getRandomTipAccount()

// Check requirements
val minTip = iris.fastlane.minimumTipLamports // 0.001 SOL
val minPriorityFee = iris.fastlane.recommendedComputeUnitPrice
```

### `iris.yellowstone` - Real-time Streaming

Yellowstone Geyser gRPC for real-time data.

```kotlin
// Stream account updates
iris.yellowstone.subscribeToAccount("wallet-address").collect { update ->
    println("Balance: ${update.lamports}")
}

// Stream multiple accounts
iris.yellowstone.subscribeToAccounts(listOf("addr1", "addr2")).collect { update ->
    println("${update.pubkey}: ${update.lamports}")
}

// Stream slot updates
iris.yellowstone.subscribeToSlots().collect { slot ->
    println("New slot: ${slot.slot}")
}

// Stream program accounts
iris.yellowstone.subscribeToProgramAccounts("program-id").collect { update ->
    println("Account ${update.pubkey} changed")
}
```

### `iris.privacy` - Privacy Innovations 🔒

**Iris Exclusive** - Revolutionary privacy features.

```kotlin
// Analyze wallet privacy
val score = iris.privacy.analyzeWallet("wallet-address")
println("Privacy Score: ${score.overallScore}/100")
println("Recommendations: ${score.recommendations}")

// Generate stealth address
val stealth = iris.privacy.generateStealthAddress(viewingKey)
println("Send to: ${stealth.stealthAddress}")

// Create privacy routing plan
val plan = iris.privacy.createPrivacyRoutePlan(
    fromAddress = "source",
    toAddress = "destination",
    amountLamports = 1_000_000_000,
    hopCount = 3,
    minDelaySeconds = 60,
    maxDelaySeconds = 300
)
println("Privacy gain: ${plan.privacyGain}%")

// Analyze transaction graph
val graph = iris.privacy.analyzeTransactionGraph(
    address = "wallet-address",
    depth = 2
)
println("Found ${graph.clusters.size} address clusters")
```

---

## Smart Transaction Building

```kotlin
// Get optimization plan
val plan = iris.smart.getOptimizationPlan(
    accounts = listOf("program-id"),
    priorityLevel = PriorityLevel.HIGH,
    useJito = true
)

println("Priority fee: ${plan.priorityFeeMicroLamports} microLamports")
println("JITO tip: ${plan.jitoTipSol} SOL")
println("Total cost: ${plan.totalEstimatedCostSol} SOL")
```

---

## Error Handling

```kotlin
try {
    val result = iris.rpc.getAccountInfo(pubkey)
} catch (e: IrisRpcException) {
    println("RPC Error ${e.code}: ${e.message}")
} catch (e: IrisNetworkException) {
    println("Network error: ${e.message}")
} catch (e: IrisValidationException) {
    println("Validation error: ${e.message}")
}
```

---

## Configuration

### Custom HTTP Client

```kotlin
val customClient = OkHttpClient.Builder()
    .connectTimeout(Duration.ofSeconds(60))
    .addInterceptor(loggingInterceptor)
    .build()

val iris = IrisQuickNodeClient(
    endpoint = "https://...",
    httpClient = customClient
)
```

### Custom JSON Configuration

```kotlin
val customJson = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
}

val iris = IrisQuickNodeClient(
    endpoint = "https://...",
    json = customJson
)
```

---

## QuickNode Add-on Requirements

| Feature | Add-on Required | Pricing |
|---------|-----------------|---------|
| Core RPC | None | Included |
| Priority Fee API | Solana Priority Fee | Free |
| DAS API | Metaplex DAS API | Free |
| Metis Jupiter | Metis Jupiter Swap API | Free (10 RPS) |
| JITO Bundles | Lil' JIT | $89/month |
| Pump.fun | Pump Fun API | Free |
| Transaction Fastlane | Solana Transaction Fastlane | Free (Beta) |
| Yellowstone gRPC | Yellowstone Geyser | $499/month |

---

## Comparison: Iris vs Luna

| Feature | Iris (QuickNode) | Luna (Helius) |
|---------|------------------|---------------|
| **Provider** | QuickNode | Helius |
| **Focus** | Full Infrastructure | DeFi & Analytics |
| **JITO Bundles** | ✅ Lil' JIT | ❌ |
| **Yellowstone gRPC** | ✅ Real-time streaming | ❌ |
| **Transaction Fastlane** | ✅ Sub-slot execution | ❌ |
| **Pump.fun API** | ✅ Native support | ❌ |
| **Jupiter Integration** | ✅ Metis | ✅ (direct) |
| **DAS API** | ✅ Metaplex | ✅ Helius Enhanced |
| **Enhanced Transactions** | ❌ | ✅ Parse & Enrich |
| **Webhooks** | ❌ (coming) | ✅ |
| **Privacy Features** | ✅ Exclusive | ❌ |

---

## License

Apache License 2.0

---

## About

**Iris SDK** is developed by [Bluefoot Labs](https://www.bluefootlabs.com) as part of the Selenus SDK family.

- 🌙 **Luna SDK** - Helius Solana SDK
- 🌈 **Iris SDK** - QuickNode Solana SDK

> *"Built for developers who demand the best. Iris bridges the gap between your code and Solana with the speed of the gods."*
