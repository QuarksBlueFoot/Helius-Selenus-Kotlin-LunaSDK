# Iris SDK

<div align="center">
  <p><strong>Kotlin SDK for QuickNode Solana APIs</strong></p>
  <p>
    <a href="#installation">Installation</a> •
    <a href="#quick-start">Quick Start</a> •
    <a href="#api-reference">API Reference</a> •
    <a href="#privacy-apis">Privacy APIs</a>
  </p>
  
  ![License](https://img.shields.io/badge/license-MIT-blue)
  ![Kotlin](https://img.shields.io/badge/kotlin-2.3.0-purple)
  ![Version](https://img.shields.io/badge/version-1.1.0-green)
</div>

---

## Overview

Iris SDK is a Kotlin client library for [QuickNode](https://quicknode.com) Solana APIs. It provides type-safe access to:

- **Standard RPC** - Full Solana JSON-RPC implementation
- **Digital Asset Standard (DAS)** - NFT/token metadata via Metaplex DAS API
- **Jupiter Metis** - DEX aggregation, swap quotes, and limit orders
- **JITO Bundles** - MEV protection and bundle submission
- **Priority Fees** - Dynamic fee estimation
- **Pump.fun** - Bonding curve trading
- **Yellowstone gRPC** - Real-time account and transaction streaming
- **Privacy APIs** - Wallet analysis, stealth addresses, transaction privacy

## Features

| Feature | Namespace | QuickNode Add-on |
|---------|-----------|------------------|
| Standard RPC | `iris.rpc` | Core (included) |
| Digital Assets | `iris.das` | Metaplex DAS API (free) |
| Jupiter Swap | `iris.metis` | Metis Jupiter V6 (free) |
| JITO Bundles | `iris.jito` | Lil' JIT ($89/mo) |
| Priority Fees | `iris.priority` | Priority Fee API (free) |
| Pump.fun | `iris.pumpfun` | Pump.fun API (free) |
| Fastlane | `iris.fastlane` | Transaction Fastlane (free) |
| Streaming | `iris.yellowstone` | Yellowstone gRPC ($499/mo) |
| Privacy | `iris.privacy` | SDK feature |
| SNS Domains | `iris.sns` | SDK feature |

## Requirements

- Kotlin 1.9+ / JDK 11+
- kotlinx-coroutines-core 1.7+
- kotlinx-serialization-json 1.6+
- OkHttp 4.12+

## Installation

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("xyz.selenus:iris-sdk:1.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
```

### Maven

```xml
<dependency>
    <groupId>xyz.selenus</groupId>
    <artifactId>iris-sdk</artifactId>
    <version>1.1.0</version>
</dependency>
```

## Quick Start

```kotlin
import com.selenus.iris.IrisQuickNodeClient
import com.selenus.iris.SolanaNetwork

val iris = IrisQuickNodeClient(
    endpoint = "https://your-endpoint.solana-mainnet.quiknode.pro/token/",
    network = SolanaNetwork.MAINNET_BETA
)

// Get SOL balance
val balance = iris.getBalanceSol("wallet-address")
println("Balance: $balance SOL")

// Get assets owned by wallet
val assets = iris.das.getAssetsByOwner("wallet-address")
println("Assets: ${assets.items.size}")
```

---

## API Reference

### Standard RPC (`iris.rpc`)

Full Solana JSON-RPC implementation.

| Method | Description |
|--------|-------------|
| `getBalance(pubkey)` | Get SOL balance in lamports |
| `getAccountInfo(pubkey)` | Get account data |
| `getSlot()` | Get current slot |
| `getBlockHeight()` | Get current block height |
| `getEpochInfo()` | Get epoch information |
| `getLatestBlockhash()` | Get blockhash for signing |
| `getTokenAccountsByOwner(owner)` | List token accounts |
| `sendTransaction(signedTx)` | Submit signed transaction |
| `simulateTransaction(tx)` | Simulate transaction |
| `getSignaturesForAddress(address)` | Get transaction history |

### Digital Asset Standard (`iris.das`)

Query NFTs and token metadata via Metaplex DAS API.

| Method | Description |
|--------|-------------|
| `getAsset(id)` | Get asset metadata by ID |
| `getAssets(ids)` | Batch fetch multiple assets |
| `getAssetProof(id)` | Get Merkle proof for compressed NFT |
| `getAssetsByOwner(owner)` | List assets owned by wallet |
| `getAssetsByCreator(creator)` | List assets by creator |
| `getAssetsByCollection(collection)` | List assets in collection |
| `searchAssets(query)` | Advanced asset search |
| `getTokenAccounts(owner)` | Get fungible token accounts |

### Jupiter Metis (`iris.metis`)

DEX aggregation and swap execution.

| Method | Description |
|--------|-------------|
| `getQuote(inputMint, outputMint, amount)` | Get best swap route |
| `getQuoteBySymbol(input, output, amount)` | Quote using token symbols |
| `getSwapTransaction(quote, userPubkey)` | Build swap transaction |
| `getSwapInstructions(quote, userPubkey)` | Get raw swap instructions |
| `getPrice(mints)` | Get token prices |
| `getNewPools(limit)` | Discover new liquidity pools |
| `createLimitOrder(...)` | Create limit order |
| `cancelLimitOrder(order, userPubkey)` | Cancel limit order |
| `getOpenLimitOrders(wallet)` | List open limit orders |

```kotlin
// Example: Get swap quote
val quote = iris.metis.getQuote(
    inputMint = MetisNamespace.WSOL_MINT,
    outputMint = MetisNamespace.USDC_MINT,
    amount = 1_000_000_000  // 1 SOL
)
println("Output: ${quote.outAmount}")
```

### JITO Bundles (`iris.jito`)

MEV protection and atomic bundle submission. Requires Lil' JIT add-on.

| Method | Description |
|--------|-------------|
| `getTipFloor()` | Get minimum tip amounts |
| `getTipAccounts()` | Get JITO tip account addresses |
| `getOptimalTip(priority)` | Calculate optimal tip |
| `sendBundle(transactions)` | Submit transaction bundle |
| `simulateBundle(bundle)` | Simulate bundle execution |
| `sendTransaction(signedTx, tip)` | Send with tip |
| `getBundleStatuses(bundleIds)` | Check bundle status |
| `getRegions()` | List available regions |

```kotlin
// Example: Send JITO bundle
val bundleId = iris.jito.sendBundle(
    transactions = listOf(signedTx1, signedTx2),
    tip = 5000
)
```

### Priority Fees (`iris.priority`)

Dynamic fee estimation.

| Method | Description |
|--------|-------------|
| `estimatePriorityFees(accounts?)` | Get recommended fees |
| `getDetailedFeeEstimate()` | Get fee breakdown by percentile |

### Pump.fun (`iris.pumpfun`)

Bonding curve token trading.

| Method | Description |
|--------|-------------|
| `getQuote(type, mint, amount)` | Get bonding curve quote |
| `getSwapTransaction(quote, wallet)` | Build swap transaction |

### Transaction Fastlane (`iris.fastlane`)

Sub-slot transaction propagation.

| Method | Description |
|--------|-------------|
| `getTipAccounts()` | Get fastlane tip accounts |
| `getRandomTipAccount()` | Get random tip account |
| `sendFastTransaction(signedTx, tip)` | Send with sub-slot propagation |

### Yellowstone Streaming (`iris.yellowstone`)

Real-time gRPC streaming. Requires Yellowstone Geyser add-on.

| Method | Description |
|--------|-------------|
| `subscribeToAccount(pubkey)` | Stream account updates |
| `subscribeToAccounts(pubkeys)` | Stream multiple accounts |
| `subscribeToTransactions(filter)` | Stream transactions |
| `subscribeToSlots()` | Stream slot updates |
| `subscribeToProgramAccounts(program)` | Stream program accounts |

```kotlin
// Example: Stream account updates
iris.yellowstone.subscribeToAccount("wallet-address").collect { update ->
    println("Balance: ${update.lamports}")
}
```

### Solana Name Service (`iris.sns`)

Domain resolution for .sol names.

| Method | Description |
|--------|-------------|
| `resolveDomain(domain)` | Resolve .sol domain to address |
| `getDomains(owner)` | Get domains owned by wallet |
| `getFavoriteDomain(owner)` | Get primary domain |

---

## Privacy APIs

Iris SDK includes privacy-focused APIs for wallet analysis and privacy-preserving transactions.

### Privacy Analysis (`iris.privacy`)

Analyze wallet privacy and generate stealth addresses.

```kotlin
val score = iris.privacy.analyzeWallet("wallet-address")
println("Privacy score: ${score.overallScore}/100")
println("Recommendations: ${score.recommendations}")
```

| Method | Description |
|--------|-------------|
| `analyzeWallet(address)` | Get privacy score (0-100) |
| `generateStealthAddress(recipient)` | Create one-time receiving address |
| `analyzeTransactionGraph(address, depth)` | Analyze wallet connections |

### Advanced Privacy (`iris.privacyAdvanced`)

Privacy-preserving transaction patterns.

| Method | Description |
|--------|-------------|
| `createMetaAddress(spendKey, viewKey)` | Create stealth meta-address |
| `generateStealthAddress(metaAddress)` | Generate one-time address |
| `createTemporalSchedule(txs, config)` | Schedule with timing obfuscation |
| `createSplitSendPlan(amount, recipient)` | Split transfer into chunks |
| `analyzeWalletPrivacy(address)` | Comprehensive privacy report |

```kotlin
// Example: Stealth address generation
val metaAddress = iris.privacyAdvanced.createMetaAddress(spendingKey, viewingKey)
val stealth = iris.privacyAdvanced.generateStealthAddress(metaAddress)

// Example: Split-send for privacy
val plan = iris.privacyAdvanced.createSplitSendPlan(
    totalAmount = 1_000_000_000,  // 1 SOL
    recipient = "recipient-address",
    config = SplitSendConfig(strategy = SplitStrategy.NOISE_INJECTED)
)
```

---

## Configuration

### Custom Metis Endpoint

```kotlin
val iris = IrisQuickNodeClient(
    endpoint = "https://your-rpc.solana-mainnet.quiknode.pro/token/",
    metisEndpoint = "https://jupiter-swap-api.quiknode.pro/YOUR_KEY"
)
```

### Custom HTTP Client

```kotlin
val customClient = OkHttpClient.Builder()
    .connectTimeout(Duration.ofSeconds(60))
    .readTimeout(Duration.ofSeconds(120))
    .build()

val iris = IrisQuickNodeClient(
    endpoint = "...",
    httpClient = customClient
)
```

---

## Supported Networks

| Network | Status |
|---------|--------|
| Mainnet-Beta | Supported |
| Devnet | Supported |
| Testnet | Supported |

---

## Error Handling

API methods return typed responses. Check for errors:

```kotlin
val response = iris.das.getAsset("asset-id")
if (response.error != null) {
    println("Error: ${response.error}")
} else {
    println("Asset: ${response.result}")
}
```

---

## Contributing

Contributions welcome. Please open an issue or pull request.

---

## License

MIT License - see [LICENSE](../LICENSE) for details.

---

## Links

- [QuickNode Documentation](https://www.quicknode.com/docs/solana)
- [Jupiter API](https://station.jup.ag/docs)
- [Solana Documentation](https://solana.com/docs)
