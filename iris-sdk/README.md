# 🌈 Iris SDK

**The Definitive QuickNode Solana SDK for Kotlin/Android**

Named after Iris, the Greek goddess of the rainbow and swift messenger of the gods - representing speed, communication, and the bridge between developers and Solana.

[![Maven Central](https://img.shields.io/badge/Maven%20Central-xyz.selenus%3Airis--sdk-blue)](https://central.sonatype.com/artifact/xyz.selenus/iris-sdk)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0+-purple.svg)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

---

## ✨ Features

### 🔮 Complete QuickNode Marketplace Integration

| Add-on | Namespace | Features |
|--------|-----------|----------|
| **Core Solana RPC** | `rpc` | All standard Solana JSON-RPC methods |
| **Metis Jupiter Swap API** | `metis` | DEX aggregation, quotes, swaps, limit orders, new pools |
| **Lil' JIT - JITO Bundles** | `jito` | MEV protection, bundle submission, tip optimization |
| **Priority Fee API** | `priority` | Real-time fee estimation for transaction prioritization |
| **Pump.fun API** | `pumpfun` | Bonding curve trading, quotes, swaps |
| **Metaplex DAS API** | `das` | NFT metadata, compressed assets, token accounts |
| **Transaction Fastlane** | `fastlane` | Sub-slot transaction propagation |
| **Yellowstone gRPC** | `yellowstone` | Real-time account streaming, transaction updates |
| **WebSocket** | `ws` | Standard Solana WebSocket subscriptions |

### 🎭 Exclusive Features

| Feature | Namespace | Description |
|---------|-----------|-------------|
| **Privacy Analysis** | `privacy` | Wallet privacy scoring, stealth addresses, transaction mixing |
| **Smart Transactions** | `smart` | Automatic priority fee optimization |
| **SNS Domains** | `sns` | Solana Name Service (.sol) domain resolution |

---

## 🚀 Quick Start

### Installation

```kotlin
// build.gradle.kts
dependencies {
    implementation("xyz.selenus:iris-sdk:1.0.0")
}
```

### Basic Usage

```kotlin
import com.selenus.iris.*

// Create client with your QuickNode endpoint
val iris = IrisQuickNodeClient(
    endpoint = "https://your-endpoint.solana-mainnet.quiknode.pro/your-token/",
    network = SolanaNetwork.MAINNET_BETA
)

// Get SOL balance
val balance = iris.getBalanceSol("CuieVDEDtLo7FypA9SbLM9saXFdb1dsshEkyErMqkRQq")
println("Balance: $balance SOL")
```

---

## 📖 Namespace Examples

### 🔮 Core RPC

```kotlin
// Get slot, block height, epoch info
val slot = iris.rpc.getSlot()
val height = iris.rpc.getBlockHeight()
val epochInfo = iris.rpc.getEpochInfo()

// Get account info
val accountInfo = iris.rpc.getAccountInfo("address")

// Get token balances
val tokenAccounts = iris.rpc.getTokenAccountsByOwner("address")

// Send transaction
val signature = iris.rpc.sendTransaction(signedTx)
```

### 🌊 Metis Jupiter Swap API

```kotlin
// Get swap quote (1 SOL → USDC)
val quote = iris.metis.getQuote(
    inputMint = MetisNamespace.WSOL_MINT,
    outputMint = MetisNamespace.USDC_MINT,
    amount = 1_000_000_000 // 1 SOL in lamports
)
println("1 SOL = ${quote.outAmount.toLong() / 1_000_000.0} USDC")

// Get swap transaction
val swapResult = iris.metis.getSwapTransaction(
    quote = quote,
    userPublicKey = "your-wallet-address",
    computeUnitPriceMicroLamports = 50000 // priority fee
)

// Get new liquidity pools
val newPools = iris.metis.getNewPools(limit = 10)
```

### 🔥 JITO Bundles

```kotlin
// Send a JITO bundle with MEV protection
val bundleId = iris.jito.sendBundle(
    transactions = listOf(signedTx1, signedTx2),
    tip = 5000 // lamports
)

// Check bundle status
val status = iris.jito.getBundleStatuses(listOf(bundleId))

// Get tip floor (minimum tips)
val tipFloors = iris.jito.getTipFloor()
```

### 💹 Priority Fee API

```kotlin
// Estimate priority fees for a transaction
val priorityFee = iris.priority.estimatePriorityFees(
    accounts = listOf("account1", "account2"),
    level = PriorityLevel.HIGH
)
println("Recommended fee: $priorityFee micro-lamports/CU")

// Get detailed fee breakdown
val feeDetails = iris.priority.getDetailedFeeEstimate()
```

### 🎪 Pump.fun Trading

```kotlin
// Get quote for a pump.fun token
val pumpQuote = iris.pumpfun.getQuote(
    type = PumpFunType.BUY,
    mint = "token-mint-address",
    amount = 100_000_000 // 0.1 SOL
)

// Get swap transaction
val swapResult = iris.pumpfun.getSwapTransaction(
    quote = pumpQuote,
    userPublicKey = "your-wallet"
)
```

### 🖼️ DAS (NFT/Token Metadata)

```kotlin
// Get NFTs owned by an address
val assets = iris.das.getAssetsByOwner("wallet-address")
assets.items.forEach { asset ->
    println("${asset.id}: ${asset.content?.metadata?.name}")
}

// Get single asset by ID
val asset = iris.das.getAsset("asset-id")

// Search assets
val searchResults = iris.das.searchAssets(
    ownerAddress = "wallet-address",
    burnt = false
)
```

### 🚀 Transaction Fastlane

```kotlin
// Get a random tip account
val tipAccount = iris.fastlane.getRandomTipAccount()

// Minimum tip required
val minTip = iris.fastlane.minimumTipLamports // 0.001 SOL

// Recommended compute unit price
val cuPrice = iris.fastlane.recommendedComputeUnitPrice // 5M micro-lamports
```

### 🌊 Yellowstone gRPC Streaming

```kotlin
// Stream account updates
iris.yellowstone.subscribeToAccount("wallet-address").collect { update ->
    println("Account updated: ${update.lamports} lamports")
}

// Stream slot updates
iris.yellowstone.subscribeToSlots().collect { slot ->
    println("New slot: $slot")
}
```

### 🎭 Privacy Analysis (Iris Exclusive)

```kotlin
// Analyze wallet privacy
val score = iris.privacy.analyzeWallet("wallet-address")
println("Privacy score: ${score.overallScore}/100")
println("Recommendations: ${score.recommendations}")

// Generate stealth address
val stealth = iris.privacy.generateStealthAddress("recipient-pubkey")
```

### 🏷️ Solana Name Service

```kotlin
// Resolve .sol domain
val address = iris.sns.resolveDomain("moonmanquark.sol")

// Get domains owned by wallet
val domains = iris.sns.getDomains("wallet-address")

// Get favorite/primary domain
val favorite = iris.sns.getFavoriteDomain("wallet-address")
```

---

## ⚙️ Configuration

### Custom Metis Endpoint

Metis (Jupiter) uses a separate endpoint from the RPC. You can configure it:

```kotlin
val iris = IrisQuickNodeClient(
    endpoint = "https://your-rpc.solana-mainnet.quiknode.pro/token/",
    // Private QuickNode Metis endpoint (faster, higher limits)
    metisEndpoint = "https://jupiter-swap-api.quiknode.pro/YOUR_KEY"
    // Or use public endpoint (rate-limited)
    // metisEndpoint = "https://public.jupiterapi.com"
)
```

### Custom HTTP Client

```kotlin
val customHttpClient = OkHttpClient.Builder()
    .connectTimeout(Duration.ofSeconds(60))
    .readTimeout(Duration.ofSeconds(120))
    .addInterceptor(loggingInterceptor)
    .build()

val iris = IrisQuickNodeClient(
    endpoint = "...",
    httpClient = customHttpClient
)
```

---

## 📊 Test Results

All features tested against live QuickNode mainnet endpoint:

```
✅ 16/16 tests passing
✅ Core RPC methods
✅ DAS API (NFT metadata)
✅ Priority Fee API
✅ Jupiter/Metis quotes & swaps
✅ JITO tip floor
✅ Privacy analysis
✅ Multi-account lookups
```

---

## � World-First Innovations

Iris SDK includes groundbreaking features that combine multiple QuickNode add-ons in novel ways - features that have never been implemented before on Solana.

### ⚡ Combined Add-On Innovations

Access via `iris.innovations`:

| Innovation | Description | Components Used |
|------------|-------------|-----------------|
| **Atomic Sniper** | Real-time new token detection with instant MEV-protected execution | Yellowstone + Pump.fun/Jupiter + JITO |
| **Guaranteed Swap Cascade** | Multi-strategy landing with automatic fallback | Fastlane → JITO → Standard |
| **Atomic Portfolio Rebalancer** | Single-transaction multi-token rebalancing | DAS + Metis + JITO Bundles |
| **Cross-DEX Arbitrage Scanner** | Real-time price discrepancy detection | Metis multi-route comparison |

```kotlin
// Example: Guaranteed Swap with strategy cascade
val result = iris.innovations.guaranteedSwap(
    inputMint = MetisNamespace.WSOL_MINT,
    outputMint = MetisNamespace.USDC_MINT,
    amount = 1_000_000_000,
    userPublicKey = "your-wallet",
    config = GuaranteedSwapConfig(
        maxRetries = 5,
        slippageBps = 100
    )
)
// Automatically tries: Fastlane → JITO → Standard with fee escalation
```

### 🔐 Privacy Innovations

Access via `iris.privacyAdvanced`:

| Innovation | Description | World-First Aspect |
|------------|-------------|-------------------|
| **JITO-Shielded Transactions** | Bundle transactions for complete privacy until landing | Using JITO for privacy, not just MEV |
| **Stealth Address Protocol** | Generate one-time receiving addresses | Application-layer stealth on Solana |
| **Temporal Obfuscation Engine** | Randomize transaction timing with statistical models | Human-like delay distributions |
| **Split-Send Privacy** | Break transfers into random-sized chunks | Pattern-resistant amount splitting |
| **DEX-Route Obfuscation** | Route value through swaps to break transaction graph | DEX routing as privacy layer |
| **Decoy Transaction Generator** | Create plausible decoy transactions | Shuffle real with decoy txs |
| **Comprehensive Privacy Scoring** | Multi-factor wallet privacy analysis | SDK-integrated privacy metrics |

```kotlin
// Example: Stealth Address generation
val metaAddress = iris.privacyAdvanced.createMetaAddress(spendingKey, viewingKey)
val stealth = iris.privacyAdvanced.generateStealthAddress(metaAddress)
// Each payment uses a unique address

// Example: Temporal obfuscation
val schedule = iris.privacyAdvanced.createTemporalSchedule(
    transactions = txList,
    config = TemporalConfig(distribution = DelayDistribution.HUMAN_LIKE)
)

// Example: Split-send privacy
val plan = iris.privacyAdvanced.createSplitSendPlan(
    totalAmount = 1_000_000_000, // 1 SOL
    recipient = "recipient-address",
    config = SplitSendConfig(strategy = SplitStrategy.NOISE_INJECTED)
)

// Example: Privacy analysis
val report = iris.privacyAdvanced.analyzeWalletPrivacy("wallet-address")
println("Privacy Score: ${report.overallScore}/100")
```

### 🎯 Why These Are World-Firsts

1. **No existing Solana SDK** combines multiple QuickNode add-ons atomically
2. **Existing privacy solutions** (Elusiv, Light Protocol, Arcium) are **on-chain programs**
3. **Our approach is application-layer** - requires NO smart contracts
4. **Works with existing Solana infrastructure** - no new programs needed

---

## �🌐 Supported Networks

| Network | Status |
|---------|--------|
| Mainnet-Beta | ✅ Full support + Archive |
| Devnet | ✅ Supported |
| Testnet | ✅ Supported |

---

## 📦 Dependencies

- **OkHttp 4.12.0** - HTTP client
- **kotlinx-serialization 1.7.3** - JSON serialization
- **kotlinx-coroutines 1.10.2** - Async/await support
- **JUnit 5** - Testing (test scope)

---

## 🤝 Contributing

Contributions welcome! Please read our contributing guidelines.

---

## 📄 License

MIT License - see [LICENSE](../LICENSE) for details.

---

## 🔗 Links

- [QuickNode Docs](https://www.quicknode.com/docs/solana)
- [Jupiter API](https://station.jup.ag/docs)
- [Solana Docs](https://solana.com/docs)
- [Selenus](https://selenus.xyz)

---

## 👤 Author

- **@moonmanquark** on X (Twitter): [https://x.com/moonmanquark](https://x.com/moonmanquark)

For support, please reach out to **@moonmanquark** on X.

---

## 💜 Support Development

If Iris SDK helps your project, consider supporting development:

**Solana Address:** `solanadevdao.sol` or `F42ZovBoRJZU4av5MiESVwJWnEx8ZQVFkc1RM29zMxNT`

---

<p align="center">
  <b>Built with 💜 by @moonmanquark & Selenus</b>
</p>
