# Luna SDK

<div align="center">
  <p><strong>Kotlin SDK for Helius Solana APIs</strong></p>
  <p>
    <a href="#installation">Installation</a> •
    <a href="#quick-start">Quick Start</a> •
    <a href="#api-reference">API Reference</a> •
    <a href="#privacy-apis">Privacy APIs</a> •
    <a href="docs/LunaSDK_Guide.md">Documentation</a>
  </p>
  
  ![License](https://img.shields.io/badge/license-MIT-blue)
  ![Kotlin](https://img.shields.io/badge/kotlin-2.3.0-purple)
  ![Version](https://img.shields.io/badge/version-5.5.0-green)
</div>

---

## Overview

Luna SDK is a Kotlin client library for [Helius](https://helius.dev) Solana APIs. It provides type-safe access to:

- **Digital Asset Standard (DAS)** - NFT/token metadata, ownership, and compressed NFT operations
- **Enhanced RPC** - Paginated account queries with incremental update support
- **ZK Compression** - Full indexer access for compressed accounts and proofs
- **Webhooks & WebSockets** - Real-time event subscriptions
- **Priority Fees** - Dynamic fee estimation for optimal transaction landing
- **Transaction APIs** - Enhanced transaction parsing and smart transaction building
- **Privacy APIs** - Stealth addresses, anonymity analysis, and transaction graph privacy

## Features

| Feature | Namespace | Description |
|---------|-----------|-------------|
| Digital Assets | `helius.das` | NFT/token queries, compressed NFT proofs, asset search |
| Standard RPC | `helius.solana` | Full Solana JSON-RPC implementation |
| Enhanced RPC | `helius.rpc` | Paginated queries with `changedSinceSlot` filtering |
| Staking | `helius.staking` | Stake account creation, delegation, withdrawal |
| Transactions | `helius.tx` | Compute unit estimation, smart transactions |
| Priority Fees | `helius.priority` | Dynamic fee estimation by priority level |
| Enhanced Txs | `helius.enhanced` | Human-readable transaction parsing |
| Webhooks | `helius.webhooks` | HTTP callback subscriptions for on-chain events |
| WebSockets | `helius.ws` | Real-time account, log, and transaction subscriptions |
| ZK Compression | `helius.zk` | Compressed account queries and validity proofs |
| Sender API | `helius.sender` | Low-latency transaction submission |
| Privacy | `helius.privacy` | Stealth addresses, anonymity sets, graph analysis |

## Requirements

- Kotlin 1.9+ / JDK 11+
- kotlinx-coroutines-core 1.7+
- kotlinx-serialization-json 1.6+
- OkHttp 4.12+

## Installation

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("xyz.selenus:luna-sdk:5.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
```

### Maven

```xml
<dependency>
    <groupId>xyz.selenus</groupId>
    <artifactId>luna-sdk</artifactId>
    <version>5.3.0</version>
</dependency>
```

### Local Development

```kotlin
// settings.gradle.kts
include(":luna-sdk")

// build.gradle.kts
dependencies {
    implementation(project(":luna-sdk"))
}
```

## Quick Start

```kotlin
import com.selenus.luna.LunaHeliusClient
import com.selenus.luna.Cluster
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val helius = LunaHeliusClient("YOUR_API_KEY", Cluster.MAINNET)
    
    // Fetch assets owned by a wallet
    val assets = helius.das.getAssetsByOwner(
        ownerAddress = "86xCnPeV69n6t3DnyGvkKobf9FdN2H9oiVDdaMpo2MMY",
        page = 1,
        limit = 50
    )
    println("Assets: ${assets?.result?.total}")
    
    // Estimate priority fee
    val fee = helius.priority.getPriorityFeeEstimate(priorityLevel = "High")
    println("Fee: ${fee?.result}")
    
    // Get SOL balance
    val balance = helius.solana.getBalance("wallet-address")
    println("Balance: $balance lamports")
}
```

---

## API Reference

### Digital Asset Standard (`helius.das`)

Query NFTs, tokens, and compressed assets using the Metaplex Digital Asset Standard.

| Method | Description |
|--------|-------------|
| `getAsset(id)` | Fetch asset metadata by ID |
| `getAssetBatch(ids)` | Batch fetch up to 1000 assets |
| `getAssetProof(id)` | Get Merkle proof for compressed NFT |
| `getAssetProofBatch(ids)` | Batch fetch proofs |
| `getAssetsByOwner(owner, page?, limit?)` | List assets owned by wallet |
| `getAssetsByCreator(creator, page?, limit?)` | List assets by creator |
| `getAssetsByAuthority(authority, page?, limit?)` | List assets by update authority |
| `getAssetsByGroup(key, value, page?, limit?)` | List assets by collection |
| `searchAssets(filters)` | Advanced asset search |
| `getNftEditions(masterAssetId)` | Get editions of a master NFT |
| `getTokenAccounts(mint?, owner?)` | Query token accounts |
| `getSignaturesForAsset(assetId)` | Get transaction history for asset |

### Standard RPC (`helius.solana`)

Full implementation of Solana JSON-RPC methods.

| Method | Description |
|--------|-------------|
| `getBalance(pubkey)` | Get SOL balance in lamports |
| `getAccountInfo(pubkey)` | Get account data and metadata |
| `getLatestBlockhash()` | Get blockhash for transaction signing |
| `getBlock(slot)` | Get block data |
| `getSlot()` | Get current slot |
| `getTokenAccountsByOwner(owner)` | List token accounts |
| `requestAirdrop(pubkey, lamports)` | Request devnet/testnet airdrop |
| `sendTransaction(...)` | Submit signed transaction |
| `simulateTransaction(...)` | Simulate transaction execution |

See [Solana RPC Documentation](https://docs.solana.com/api/http) for the full method list.

### Enhanced RPC (`helius.rpc`)

Helius-enhanced RPC methods with pagination and incremental updates.

| Method | Description |
|--------|-------------|
| `getProgramAccountsV2(programId, ...)` | Paginated program accounts with cursor |
| `getAllProgramAccounts(programId)` | Auto-paginate all program accounts |
| `getTokenAccountsByOwnerV2(owner, ...)` | Paginated token accounts |
| `getAllTokenAccountsByOwner(owner)` | Auto-paginate all token accounts |
| `getTransactionsForAddress(address, options)` | Transaction history with filtering |

### Staking (`helius.staking`)

Build staking transactions for the Helius validator.

| Method | Description |
|--------|-------------|
| `createStakeTransaction(wallet, amount, validator)` | Create stake delegation transaction |
| `createUnstakeTransaction(stakeAccount)` | Deactivate stake account |
| `createWithdrawTransaction(stakeAccount, amount)` | Withdraw from stake account |
| `getHeliusStakeAccounts(wallet)` | List stake accounts delegated to Helius |
| `getWithdrawableAmount(stakeAccount)` | Get withdrawable balance |

### Transactions (`helius.tx`)

Transaction building and submission utilities.

| Method | Description |
|--------|-------------|
| `getComputeUnits(transaction)` | Estimate compute units |
| `sendTransaction(transaction)` | Submit signed transaction |
| `sendSmartTransaction(transaction)` | Submit with optimal fees and retry logic |
| `pollTransactionConfirmation(signature)` | Wait for confirmation |
| `getSmartTransactionPlan(transaction)` | Get optimal CU and priority fee |

### Priority Fees (`helius.priority`)

| Method | Description |
|--------|-------------|
| `getPriorityFeeEstimate(priorityLevel)` | Get fee estimate for Low/Medium/High/VeryHigh |

### Enhanced Transactions (`helius.enhanced`)

Parse raw transactions into human-readable format.

| Method | Description |
|--------|-------------|
| `getTransactions(signatures)` | Parse transaction signatures |
| `getTransactionsByAddress(address)` | Get parsed transactions for wallet |

### Sender API (`helius.sender`)

Low-latency transaction submission.

| Method | Description |
|--------|-------------|
| `sendTransaction(transaction, region?)` | Submit via Helius Sender |
| `getSenderTipFloor()` | Get current Jito tip floor |
| `findOptimalRegion()` | Find lowest latency region |

### Webhooks (`helius.webhooks`)

Subscribe to on-chain events via HTTP callbacks.

| Method | Description |
|--------|-------------|
| `createWebhook(url, addresses, types)` | Create webhook subscription |
| `getWebhookById(id)` | Get webhook details |
| `getAllWebhooks()` | List all webhooks |
| `updateWebhook(id, updates)` | Update webhook configuration |
| `deleteWebhook(id)` | Delete webhook |

### WebSockets (`helius.ws`)

Real-time subscriptions via WebSocket.

| Method | Description |
|--------|-------------|
| `connect(listener)` | Open WebSocket connection |
| `accountSubscribe(pubkey)` | Subscribe to account changes |
| `logsSubscribe(filter)` | Subscribe to transaction logs |
| `programSubscribe(programId)` | Subscribe to program account changes |
| `signatureSubscribe(signature)` | Subscribe to transaction confirmation |
| `slotSubscribe()` | Subscribe to slot updates |
| `transactionSubscribe(filters, options)` | Enhanced transaction subscription |

### ZK Compression (`helius.zk`)

Query and verify compressed accounts.

| Method | Description |
|--------|-------------|
| `getCompressedAccount(hash)` | Get compressed account data |
| `getCompressedAccountProof(hash)` | Get Merkle proof |
| `getCompressedAccountsByOwner(owner)` | List compressed accounts |
| `getCompressedBalance(hash)` | Get compressed account balance |
| `getCompressedBalanceByOwner(owner)` | Get total compressed balance |
| `getCompressedTokenAccountsByOwner(owner)` | List compressed token accounts |
| `getValidityProof(args)` | Get ZK validity proof |
| `getIndexerHealth()` | Check indexer status |
| `getIndexerSlot()` | Get last indexed slot |

---

## Privacy APIs

Luna SDK includes privacy-focused APIs for wallet analysis and privacy-preserving patterns.

### Stealth Addresses (`helius.stealthAddress`)

Generate one-time receiving addresses to break on-chain transaction links.

```kotlin
val stealthSet = helius.stealthAddress.generateStealthReceiveSet(
    recipientPubkey = "recipient-address",
    count = 5
)
println("Derivation path: ${stealthSet.recommendedPath.derivationPath}")
```

| Method | Description |
|--------|-------------|
| `generateStealthReceiveSet(recipient, count)` | Generate one-time addresses |
| `scanForStealthPayments(scanKey, range)` | Detect incoming stealth payments |

### Privacy Pool Analysis (`helius.privacyPool`)

Analyze anonymity sets using ZK Compression data.

```kotlin
val anonymity = helius.privacyPool.getAnonymitySetSize("address")
println("Anonymity set: ${anonymity.result?.estimatedAnonymitySet}")
```

| Method | Description |
|--------|-------------|
| `getAnonymitySetSize(address)` | Estimate anonymity set size |
| `findOptimalMixingPool(amount)` | Find pool with best anonymity |

### Transaction Graph Analysis (`helius.graphPrivacy`)

Detect privacy leaks and wallet clustering.

```kotlin
val analysis = helius.graphPrivacy.analyzePrivacyLeaks("wallet", depth = 2)
println("Risk score: ${analysis.result?.overallRiskScore}/100")
analysis.result?.leaksDetected?.forEach { leak ->
    println("${leak.severity}: ${leak.type}")
}
```

| Method | Description |
|--------|-------------|
| `analyzePrivacyLeaks(address, depth)` | Detect privacy leaks |
| `findLinkedWallets(address, depth)` | Find related wallets |
| `getPrivacyPreservingPath(from, to)` | Find privacy-optimal route |

### Shielded Pattern Analysis (`helius.shieldedPattern`)

Analyze shielded vs transparent balance ratios.

```kotlin
val shielded = helius.shieldedPattern.analyzeShieldedRatio("owner")
println("Shielded ratio: ${shielded.result?.shieldedRatio?.times(100)}%")
```

### Privacy Score (`helius.privacyScore`)

Comprehensive privacy scoring with improvement recommendations.

```kotlin
val score = helius.privacyScore.calculateComprehensiveScore("address")
println("Grade: ${score.result?.privacyGrade} (${score.result?.overallScore}/100)")

val roadmap = helius.privacyScore.generatePrivacyRoadmap("address", targetScore = 90)
roadmap.result?.milestones?.forEach { milestone ->
    println("${milestone.title}: +${milestone.scoreImpact} points")
}
```

| Method | Description |
|--------|-------------|
| `calculateComprehensiveScore(address)` | Get overall privacy score |
| `generatePrivacyRoadmap(address, target)` | Get improvement steps |
| `getFactorBreakdown(address)` | Get detailed factor analysis |

### NEW: Confidential Token-2022 (`helius.confidentialToken`)

First Kotlin SDK with Token-2022 Confidential Balance support.

```kotlin
// Check if token supports confidential transfers
val support = helius.confidentialToken.checkConfidentialSupport("mint-address")

// Prepare a confidential transfer (amounts hidden on-chain)
val transferPlan = helius.confidentialToken.prepareConfidentialTransfer(
    from = "sender-account",
    to = "receiver-account",
    amount = 1_000_000_000L
)
println("Privacy level: ${transferPlan.privacyLevel}")  // "MAXIMUM"
```

| Method | Description |
|--------|-------------|
| `checkConfidentialSupport(mint)` | Check if mint supports confidential transfers |
| `prepareConfidentialAccount(mint, owner)` | Prepare confidential account creation |
| `prepareConfidentialDeposit(account, amount)` | Prepare deposit to encrypted balance |
| `prepareConfidentialTransfer(from, to, amount)` | Prepare confidential transfer with ZK proofs |
| `prepareApplyPending(account)` | Prepare to apply pending encrypted balance |
| `prepareConfidentialWithdraw(account, amount)` | Prepare withdrawal to public balance |
| `analyzeConfidentialPrivacy(account)` | Analyze confidential privacy posture |

### NEW: Private Broadcast (`helius.privateBroadcast`)

Multi-region transaction broadcast for IP correlation resistance.

```kotlin
// Broadcast through multiple regions simultaneously
val result = helius.privateBroadcast.multiRegionBroadcast(
    transaction = signedTx,
    obfuscateOrder = true  // Randomize which region submits first
)
println("Broadcast from ${result.successfulRegions} regions")
```

| Method | Description |
|--------|-------------|
| `multiRegionBroadcast(tx, regions, obfuscate)` | Broadcast via multiple Helius Sender regions |
| `maxPrivacyBroadcast(tx)` | Broadcast via all available regions |
| `getOptimalRegions(count)` | Get geographically diverse regions |

### NEW: Fingerprint Obfuscation (`helius.fingerprint`)

Make transactions blend in with network traffic.

```kotlin
// Analyze how unique a transaction looks
val analysis = helius.fingerprint.analyzeFingerprint(signedTx)
println("Uniqueness: ${analysis.uniquenessScore}/100")
println("Looks like: ${analysis.looksLike}")  // "DEX_SWAP", "SOL_TRANSFER", etc.

// Get padding suggestion to match common patterns
val padding = helius.fingerprint.suggestPadding(
    currentSize = 600,
    targetPattern = TransactionPattern.DEX_SWAP
)
```

| Method | Description |
|--------|-------------|
| `analyzeFingerprint(tx)` | Analyze transaction uniqueness |
| `suggestPadding(size, pattern)` | Get padding to match common patterns |
| `analyzeTimingFingerprint(times)` | Detect predictable timing patterns |

### NEW: RPC Rotation (`helius.rpcRotation`)

Distribute requests across providers to prevent activity correlation.

```kotlin
// Get next endpoint in rotation
val endpoint = helius.rpcRotation.getNextEndpoint(
    sessionId = "my-session",
    strategy = RotationStrategy.ROUND_ROBIN
)

// Check rotation statistics
val stats = helius.rpcRotation.getRotationStats("my-session")
println("Privacy score: ${stats.privacyScore}/100")
```

| Method | Description |
|--------|-------------|
| `getNextEndpoint(session, strategy)` | Get next provider in rotation |
| `getRotationStats(session)` | Get rotation privacy statistics |

### NEW: Privacy Combinator (`helius.privacyCombinator`)

State-of-the-art privacy operations that combine multiple Helius APIs in innovative ways.

#### Ghost Transactions

Execute transactions that blend into network noise.

```kotlin
// Execute ghost transaction with temporal obfuscation
val ghostResult = helius.privacyCombinator.executeGhostTransaction(
    signedTransaction = signedTx,
    ghostConfig = GhostConfig(
        useTemporalObfuscation = true,
        broadcastStrategy = GhostBroadcastStrategy.DUAL_REGION,
        staggerBroadcasts = true,
        minDelayMs = 100,
        maxDelayMs = 2000
    )
)
println("Ghost score: ${ghostResult.ghostScore}/100")
println("Regions used: ${ghostResult.regionsUsed.size}")
```

#### Shadow Profile Analysis

Analyze how visible/traceable a wallet is.

```kotlin
val profile = helius.privacyCombinator.analyzeShadowProfile("wallet-address")
println("Shadow score: ${profile.shadowScore}/100")
println("Level: ${profile.shadowLevel}")  // GHOST, SHADOW, VISIBLE, EXPOSED, TRANSPARENT
profile.factors.forEach { println("${it.type}: ${it.description}") }
profile.recommendations.forEach { println("Recommendation: $it") }
```

#### Privacy-Optimized Swaps

Jupiter swaps with ghost transaction execution.

```kotlin
val swap = helius.privacyCombinator.executePrivacySwap(
    inputMint = SOL_MINT,
    outputMint = USDC_MINT,
    amount = 1_000_000_000L,  // 1 SOL
    userPublicKey = wallet,
    signCallback = { tx -> signTransaction(tx) },
    privacyConfig = PrivacySwapConfig(
        preSwapDelayMs = 500,
        useTemporalObfuscation = true,
        broadcastStrategy = GhostBroadcastStrategy.DUAL_REGION
    )
)
println("Swap signature: ${swap.signature}")
println("Privacy score: ${swap.privacyScore}/100")
```

#### Surveillance Detection

Detect if a wallet is being monitored.

```kotlin
val surveillance = helius.privacyCombinator.detectSurveillance("wallet-address")
println("Threat score: ${surveillance.threatScore}/100")
println("Level: ${surveillance.level}")  // NONE, LOW, MEDIUM, HIGH, CRITICAL
surveillance.threats.forEach { threat ->
    println("${threat.severity}: ${threat.type} - ${threat.description}")
}
```

#### Decoy Generation

Generate realistic decoy activity to confuse analysis.

```kotlin
val decoyPlan = helius.privacyCombinator.generateDecoyPlan(
    walletAddress = "wallet",
    decoyConfig = DecoyConfig(
        decoyCount = 10,
        patterns = listOf(DecoyPattern.SOL_MICRO_TRANSFER, DecoyPattern.SWAP_DUST)
    )
)
println("Generated ${decoyPlan.decoyCount} decoys")
println("Noise score: ${decoyPlan.noiseScore}/100")
```

#### Stealth Asset Queries

Query assets without revealing query patterns.

```kotlin
val assets = helius.privacyCombinator.stealthAssetQuery(
    targetAddress = "wallet",
    stealthConfig = StealthQueryConfig(
        useDecoyQueries = true,
        decoyCount = 5,
        useTemporalSpread = true
    )
)
println("Stealth score: ${assets.stealthScore}/100")
println("Query position: ${assets.queryPosition}/${assets.totalQueries}")
```

#### Transaction History Leak Analysis

Find privacy leaks in transaction history.

```kotlin
val leaks = helius.privacyCombinator.analyzeHistoryLeaks("wallet", depth = 50)
println("Overall risk: ${leaks.overallRisk}")
println("Linked addresses: ${leaks.linkedAddressCount}")
leaks.leaks.forEach { leak ->
    println("${leak.severity}: ${leak.type} - ${leak.mitigation}")
}
```

#### Stealth Balance Aggregation

Aggregate balances across multiple wallets privately.

```kotlin
val aggregation = helius.privacyCombinator.stealthAggregateBalances(
    wallets = listOf("wallet1", "wallet2", "wallet3"),
    includeTokens = true
)
println("Total: ${aggregation.totalSol} SOL")
println("Decoys used: ${aggregation.decoysUsed}")
println("Stealth score: ${aggregation.stealthScore}/100")
```

| Method | Description |
|--------|-------------|
| `executeGhostTransaction(tx, config)` | Transaction with temporal + geographic obfuscation |
| `analyzeShadowProfile(address)` | Analyze wallet visibility/traceability |
| `executePrivacySwap(input, output, ...)` | Jupiter swap via ghost transaction |
| `detectSurveillance(address)` | Detect tracking/monitoring |
| `generateDecoyPlan(address, config)` | Generate decoy transaction plan |
| `stealthAssetQuery(address, config)` | Query assets without fingerprinting |
| `analyzeHistoryLeaks(address, depth)` | Find privacy leaks in history |
| `stealthAggregateBalances(wallets)` | Aggregate balances privately |

---

## Utility APIs

### Niche & Composite (`helius.niche`)

High-level methods combining multiple API calls.

| Method | Description |
|--------|-------------|
| `getWalletPortfolio(address)` | Complete wallet snapshot |
| `getTokenDeepDive(mint)` | Token metadata, supply, holders |
| `verifyGameAccess(address, ...)` | Check balance + asset ownership |
| `getAllAssetsByOwner(address, maxPages)` | Auto-paginate all assets |
| `getTPS()` | Calculate network TPS |

### Solana Name Service (`helius.sns`)

| Method | Description |
|--------|-------------|
| `getDomains(owner)` | Get .sol domains for wallet |
| `getFavoriteDomain(owner)` | Get primary domain |

### Mobile Utilities (`helius.mobile`)

| Method | Description |
|--------|-------------|
| `generatePaymentLink(recipient, amount)` | Generate `solana:` deep link |
| `parsePaymentLink(uri)` | Parse `solana:` URI |
| `isValidAddress(address)` | Validate Solana address format |
| `getAssetLite(assetId)` | Lightweight asset for mobile lists |

---

## Natural Language Processing

The SDK includes NLP capabilities for parsing natural language transaction commands.

```kotlin
val nlp = NaturalLanguageBuilder(entityResolver)

val result = nlp.parse("send 5 SOL to alice.sol")
when (result) {
    is NlpResult.Success -> println("Intent: ${result.intent}")
    is NlpResult.NeedsInfo -> println("Missing: ${result.missingFields}")
    is NlpResult.Ambiguous -> println("Options: ${result.options}")
}
```

Features:
- Chain expression parsing (`send 5 SOL to alice.sol then swap remaining to USDC`)
- Conversation memory for contextual follow-ups
- Phonetic matching for typo tolerance (`bonk` → `bonk`, `jup` → `jupiter`)
- Domain resolution (`.sol`, `.skr`, social handles)

---

## Sample App

An Android sample application is available in `sample-app/`. See [sample-app/README.md](sample-app/README.md) for setup instructions.

---

## Error Handling

API methods return nullable response wrappers. Check for errors:

```kotlin
val response = helius.das.getAsset("asset-id")
if (response?.error != null) {
    println("Error: ${response.error.message}")
} else {
    println("Asset: ${response?.result}")
}
```

---

## Contributing

Contributions are welcome. Please open an issue or pull request for bug fixes, new endpoints, or documentation improvements.

---

## License

MIT License - see [LICENSE](LICENSE) for details.

---

## Links

- [Helius Documentation](https://docs.helius.dev)
- [Luna SDK Guide](docs/LunaSDK_Guide.md)
- [Sample App](sample-app/README.md)
