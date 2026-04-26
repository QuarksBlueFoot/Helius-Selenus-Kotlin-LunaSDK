# Luna SDK:

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
  ![Version](https://img.shields.io/badge/version-5.7.0-green)
</div>

---

## Overview

Luna SDK is a Kotlin client library for [Helius](https://helius.dev) Solana APIs. It provides type-safe access to:

- **Wallet API (Beta)** *(NEW v5.7)* — Wallet identity (SNS/exchange enrichment), human-readable balances, parsed history, transfers, and funding lineage
- **LaserStream + Enhanced WebSockets** *(NEW v5.7)* — Region-affinity endpoint selection, Flow-based subscriptions, exponential-backoff reconnect, BYO gRPC transport
- **Keys + Address utilities** *(NEW v5.7)* — Pure-JVM Ed25519 keypairs, base58 codec, on-curve validation (PDA vs wallet), no Bouncy Castle dependency
- **Solana Pay** *(NEW v5.7)* — Type-safe Transfer Request + Transaction Request URI builder/parser. SPL token amounts, references, label/message escaping
- **Webhook signature verification** *(NEW v5.7)* — Real Ed25519 verify against Helius webhook deliveries (uses JDK 17 native EdDSA)
- **Sender innovations** *(NEW v5.7)* — `warmSenderConnection()` pre-warms TLS, `determineTipLamports()` auto-clamps to Helius's 75th-percentile floor
- **Digital Asset Standard (DAS)** — NFT/token metadata, ownership, and compressed NFT operations
- **Enhanced RPC** — Paginated account queries with incremental update support
- **ZK Compression** — Full indexer access for compressed accounts and proofs
- **Webhooks & WebSockets** — Real-time event subscriptions
- **Priority Fees** — Dynamic fee estimation for optimal transaction landing
- **Transaction APIs** — Enhanced transaction parsing and smart transaction building
- **Privacy APIs** — Stealth addresses, anonymity analysis, and transaction graph privacy
- **Universal Privacy** — Comprehensive privacy toolkit with RPC rotation, timing obfuscation, MEV protection

## Features

| Feature | Namespace | Description |
|---------|-----------|-------------|
| **Wallet API (Beta)** | `helius.wallet` | **NEW v5.7** — Identity, balances, history, transfers, funding lineage |
| **LaserStream** | `helius.laserStream` | **NEW v5.7** — Geo-affinity endpoint pick, Flow subscriptions, BYO gRPC transport |
| **Keys** | `xyz.selenus.luna.keys` | **NEW v5.7** — `SolanaKeypair.generate/makeKeypairs`, `Base58`, `SolanaAddress.parse/parseStrict`, on-curve check |
| **Solana Pay** | `xyz.selenus.luna.solanapay` | **NEW v5.7** — `TransferRequest`/`TransactionRequest` builder + parser |
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
| Universal Privacy | `helius.universalPrivacy` | RPC rotation, timing obfuscation, MEV swaps |

## Requirements

- Kotlin 2.3+ / JDK 17+
- kotlinx-coroutines-core 1.10+
- kotlinx-serialization-json 1.10+
- OkHttp 5.3+

## Installation

### Gradle (Kotlin DSL) — single umbrella

```kotlin
dependencies {
    // All modules transitively
    implementation("xyz.selenus.luna:luna-sdk:5.7.0")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:5.3.2")
}
```

### Gradle — pick only the modules you use

```kotlin
dependencies {
    implementation("xyz.selenus.luna:luna-core:5.7.0")          // required base
    implementation("xyz.selenus.luna:luna-wallet:5.7.0")        // Wallet API (Beta)
    implementation("xyz.selenus.luna:luna-laserstream:5.7.0")   // LaserStream + Enhanced WS
    implementation("xyz.selenus.luna:luna-das:5.3.0")           // optional
    implementation("xyz.selenus.luna:luna-jupiter:5.3.0")       // optional
    // ... other feature modules as needed
}
```

### Maven

```xml
<dependency>
    <groupId>xyz.selenus.luna</groupId>
    <artifactId>luna-sdk</artifactId>
    <version>5.7.0</version>
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
import xyz.selenus.luna.LunaHeliusClient
import xyz.selenus.luna.Cluster
import xyz.selenus.luna.das.das
import xyz.selenus.luna.priority.priority
import xyz.selenus.luna.wallet.wallet                 // NEW v5.7
import xyz.selenus.luna.laserstream.laserStream       // NEW v5.7
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.collect

fun main() = runBlocking {
    val helius = LunaHeliusClient("YOUR_API_KEY", Cluster.MAINNET)

    // ── Classic Helius RPC ─────────────────────────────────────────
    val assets = helius.das.getAssetsByOwner(
        ownerAddress = "86xCnPeV69n6t3DnyGvkKobf9FdN2H9oiVDdaMpo2MMY",
        page = 1,
        limit = 50
    )
    println("Assets: ${assets?.result?.total}")

    val fee = helius.priority.getPriorityFeeEstimate(priorityLevel = "High")
    println("Priority fee: ${fee?.result}")

    // ── NEW v5.7: Wallet API (Beta) ────────────────────────────────
    // Human-readable balances with USD values; no manual decimal math.
    val portfolio = helius.wallet.getBalances(
        wallet = "86xCnPeV69n6t3DnyGvkKobf9FdN2H9oiVDdaMpo2MMY",
        showNative = true
    )
    println("Portfolio worth: $${portfolio.totalUsdValue}")
    portfolio.balances.take(5).forEach { t ->
        println("  ${t.symbol}: ${t.balance} ($${t.usdValue ?: "n/a"})")
    }

    // Wallet identity — is this address a known exchange / protocol?
    val whoIsThis = helius.wallet.tryGetIdentity("HXsKP7wrBWaQ8T2Vtjry3Nj3oUgwYcqq9vrHDM12G664")
    println("Identity: ${whoIsThis?.name ?: "unknown wallet"}")

    // Stream every transfer in/out for an address — automatic pagination
    helius.wallet.getAllTransfersFlow(wallet = "86xCnPeV...")
        .take(3)  // first 3 pages then stop
        .collect { page ->
            println("Got ${page.data.size} transfers (more=${page.pagination.hasMore})")
        }

    // ── NEW v5.7: LaserStream geo-affinity ─────────────────────────
    // Probe every Helius region in parallel, pick the lowest RTT.
    val region = helius.laserStream.bestRegion()
    println("Closest LaserStream region: ${region.city}")

    // Open Atlas Enhanced WebSocket subscription (auto reconnect with backoff)
    val subscriptions = listOf(
        helius.laserStream.transactionSubscribePayload(
            accountInclude = listOf("So11111111111111111111111111111111111111112"),
            commitment = "confirmed"
        )
    )
    helius.laserStream.enhancedWebSocketSubscriptions(subscriptions)
        .take(5)  // first 5 frames then disconnect
        .collect { frame -> println("WS frame: ${frame.take(100)}...") }
}
```

---

## API Reference

### Wallet API — `helius.wallet` *(NEW v5.7, Beta)*

High-level REST wrapper around `https://api.helius.xyz/v1/wallet/...`. Returns
already-decoded values (balances divided by `decimals`, USD values pre-computed,
identity metadata enriched). All methods are `suspend` and the streaming
helpers return cold `Flow`s.

| Method | Description |
|--------|-------------|
| `getIdentity(wallet)` / `tryGetIdentity(wallet)` | Identity metadata (exchange, protocol, etc.) for a known address |
| `getBatchIdentity(addresses)` | Batch lookup, server cap = 100 addresses |
| `getBatchIdentityChunked(addresses)` | Auto-chunks any-size lists into 100-address windows |
| `getBalances(wallet, page, limit, ...)` | One page of token + NFT balances with USD values |
| `getAllBalancesFlow(wallet, ...)` | Cold `Flow` streaming every page of balances (USD-sorted) |
| `getHistory(wallet, limit, before, type, tokenAccounts)` | One page of parsed transaction history |
| `getAllHistoryFlow(wallet, ...)` | Cold `Flow` streaming every page (newest → oldest) |
| `getTransfers(wallet, limit, cursor)` | One page of token transfer activity |
| `getAllTransfersFlow(wallet, limit)` | Cold `Flow` streaming every transfer |
| `getFundedBy(wallet)` / `tryGetFundedBy(wallet)` | Discover the wallet's first funder |

```kotlin
// Top 10 holdings by USD value across all pages, with no manual pagination
val top10 = helius.wallet.getAllBalancesFlow("86xCnPe...")
    .flatMapConcat { it.balances.asFlow() }
    .take(10)
    .toList()
```

### Keys — `xyz.selenus.luna.keys` *(NEW v5.7)*

Pure-JVM Solana key utilities. No Bouncy Castle — uses JDK 17 native Ed25519.

| API | Description |
|--------|-------------|
| `SolanaKeypair.generate()` | Fresh Ed25519 keypair (matches `solana-keygen new`) |
| `SolanaKeypair.makeKeypairs(n)` | Bulk generation (mirrors Helius Rust SDK's `make_keypairs`) |
| `SolanaKeypair.fromSecretSeed(32bytes)` | Derive matching pubkey via real Ed25519 scalar mult (RFC 8032 §5.1.5) |
| `SolanaKeypair.fromSolanaKeystoreBytes(64bytes)` | Parse the standard `[seed \|\| pubkey]` Solana keystore |
| `kp.sign(message)` / `kp.verify(message, sig)` | Native Ed25519 sign + verify |
| `Base58.encode/decode/isValid` | Solana base58 codec, no third-party deps |
| `SolanaAddress.parse("...")` | Syntactic check (32 bytes, base58) |
| `SolanaAddress.parseStrict("...")` | On-curve check — distinguishes wallets from PDAs |
| `isValidSolanaAddress(s)` | Free function matching Helius Rust SDK's `is_valid_solana_address` |
| `Slip10.derivePhantomAccount(seed, n)` | BIP-39/SLIP-0010 derivation along `m/44'/501'/n'/0'` (Phantom-compatible) |
| `Slip10.derivePath(seed, "m/44'/501'/0'/0'")` | General SLIP-0010 path derivation |
| `X25519.ecdh(myScalar, theirPub)` | RFC 7748 X25519 ECDH using JDK XDH (constant-time) |
| `X25519.ed25519PublicKeyToX25519(edPub)` | Birational map: Ed25519 pubkey → X25519 pubkey |
| `X25519.ed25519SeedToX25519Scalar(seed)` | Birational map: Ed25519 seed → X25519 scalar |

```kotlin
import xyz.selenus.luna.keys.SolanaKeypair
import xyz.selenus.luna.keys.Slip10
import xyz.selenus.luna.keys.X25519
import xyz.selenus.luna.keys.isValidSolanaAddress

// Fresh keypair from CSPRNG
val kp = SolanaKeypair.generate()
println("Address: ${kp.publicKeyBase58}")
val sig = kp.sign("hello".toByteArray())
require(kp.verify("hello".toByteArray(), sig))

require(isValidSolanaAddress("HXsKP7wrBWaQ8T2Vtjry3Nj3oUgwYcqq9vrHDM12G664"))

// Mnemonic-import: BIP-39 seed → Phantom-compatible Solana account
// (use any BIP-39 library to convert mnemonic → seed bytes)
val seed: ByteArray = bip39SeedFromMnemonic("twelve word phrase ...")
val account0 = Slip10.derivePhantomAccount(seed, index = 0)
val account1 = Slip10.derivePhantomAccount(seed, index = 1)

// X25519 ECDH between two Solana wallets — for encrypted memos / DMs
val alice = SolanaKeypair.generate()
val bob = SolanaKeypair.generate()
val (aliceX, _) = X25519.ed25519KeypairToX25519(alice.secretKeyBytes, alice.publicKeyBytes)
val bobXPub = X25519.ed25519PublicKeyToX25519(bob.publicKeyBytes)
val sharedSecret = X25519.ecdh(aliceX, bobXPub) // 32 bytes — feed to a KDF
```

### Solana Pay — `xyz.selenus.luna.solanapay` *(NEW v5.7)*

Spec-compliant Solana Pay URI builder + parser. Both Transfer Requests
(client builds the tx) and Transaction Requests (server signs the tx) are
first-class. Lossless decimal/raw conversion for SPL token amounts.

```kotlin
import xyz.selenus.luna.keys.SolanaAddress
import xyz.selenus.luna.solanapay.TransferRequest

val recipient = SolanaAddress.parse("HXsKP7wrBWaQ8T2Vtjry3Nj3oUgwYcqq9vrHDM12G664")!!
val usdc = SolanaAddress.parse("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v")!!

// 12.50 USDC payment with merchant label + on-chain memo
val payment = TransferRequest(
    recipient = recipient,
    amount = java.math.BigDecimal("12.50"),
    splToken = usdc,
    label = "Bluefoot Booby Mint",
    message = "Save the Galapagos",
    memo = "order-1234"
)
println(payment.toUri())
// → solana:HXsKP7wrBWaQ8T2Vtjry3Nj3oUgwYcqq9vrHDM12G664?amount=12.5&spl-token=EPjFWdd...&label=Bluefoot%20Booby%20Mint&...

// Lossless raw → decimal conversion (avoids floating-point drift)
val raw = TransferRequest.splTokenAmount(
    recipient = recipient, rawAmount = 12_500_000L, decimals = 6, splToken = usdc
)
require(raw.rawAmount(6) == 12_500_000L)  // round-trips exactly
```

### LaserStream — `helius.laserStream` *(NEW v5.7)*

Geo-affinity endpoint selection, Flow-based subscriptions over Helius Atlas
Enhanced WebSocket, exponential-backoff reconnect, and a BYO gRPC transport
contract for Yellowstone-compatible streams.

| Method | Description |
|--------|-------------|
| `bestRegion(timeout)` | Probes every endpoint in parallel, returns lowest-RTT region |
| `probeRegions(timeout)` | Full latency table for diagnostics |
| `config(region, reconnect, replayFromSlot, keepaliveTimeMs)` | Build a `LaserStreamConfig` |
| `enhancedWebSocketUrl()` | Atlas WSS URL for the client's cluster |
| `enhancedWebSocketSubscriptions(subscriptions, reconnect, driver)` | Cold `Flow<String>` of WS frames with auto-reconnect |
| `transactionSubscribePayload(...)` | Build a JSON-RPC `transactionSubscribe` envelope |
| `accountSubscribePayload(account, ...)` | Build a JSON-RPC `accountSubscribe` envelope |
| `grpcSubscribe(cfg, request, transport)` | Cold `Flow<LaserStreamUpdate>` over user-supplied gRPC transport |

The gRPC path is intentionally BYO: implement
[`LaserStreamGrpcTransport`](luna-laserstream/src/main/kotlin/xyz/selenus/luna/laserstream/LaserStreamTransport.kt)
with the official Helius LaserStream Rust client (via JNI) or a
protobuf-generated Kotlin stub. The framework handles backoff/retry so
your transport only implements a single subscription attempt.

```kotlin
// Pick lowest-latency region, build config, open WS subscription
val region = helius.laserStream.bestRegion()
val payload = helius.laserStream.transactionSubscribePayload(
    accountInclude = listOf(USDC_MINT),
    commitment = "confirmed"
)
helius.laserStream.enhancedWebSocketSubscriptions(listOf(payload))
    .collect { frame -> handle(frame) }
```

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

### NEW v5.6: Universal Privacy API (`helius.universalPrivacy`)

The most comprehensive privacy toolkit for Solana, combining cutting-edge privacy techniques in one unified API.

#### RPC Rotation for Query Privacy

Distribute queries across providers so no single provider sees your full activity pattern.

```kotlin
// Configure multiple RPC providers
val providers = listOf(
    helius.universalPrivacy.RpcProviderConfig("helius", heliusUrl, weight = 3),
    helius.universalPrivacy.RpcProviderConfig("backup", backupUrl, weight = 1)
)

// Execute with rotation
val result = helius.universalPrivacy.rotatedQuery(providers) { endpoint ->
    // Your query using the selected endpoint
    fetchBalance(endpoint, address)
}
println("Used provider: ${result.providerUsed}")
```

#### Address Cycling with HD Derivation

Generate fresh addresses for every transaction to prevent address reuse tracking.

```kotlin
// Get a fresh address path for receiving
val receivePath = helius.universalPrivacy.getCyclingAddressPath(
    purpose = AddressPurpose.RECEIVE
)
println("Use derivation path: ${receivePath.derivationPath}")

// Analyze address reuse patterns
val reuseAnalysis = helius.universalPrivacy.analyzeAddressReuse("wallet")
println("Reuse score: ${reuseAnalysis.result?.reuseScore}/100")
println("Risk level: ${reuseAnalysis.result?.riskLevel}")
```

#### Optimal Denomination Splitting

Split amounts into common denominations for larger anonymity sets.

```kotlin
val plan = helius.universalPrivacy.getOptimalDenominations(2_500_000_000L) // 2.5 SOL
println("Split into ${plan.totalTransactions} transactions")
plan.splits.forEach { split ->
    println("${split.count}x ${split.amount / 1_000_000_000.0} SOL (anonymity set: ${split.estimatedAnonymitySet})")
}
println("Average anonymity set: ${plan.averageAnonymitySet}")
```

#### Timing Obfuscation

Break temporal correlation by randomizing transaction timing.

```kotlin
// Generate timing schedule
val timingPlan = helius.universalPrivacy.generateTimingPlan(
    transactionCount = 5,
    totalWindowMs = 3600_000, // 1 hour
    strategy = TimingStrategy.RANDOM_WITHIN_WINDOW
)
timingPlan.schedule.forEach { scheduled ->
    println("TX ${scheduled.transactionIndex} at ${Date(scheduled.scheduledTimeMs)}")
}

// Execute with random delay
val result = helius.universalPrivacy.executeWithTimingObfuscation(
    minDelayMs = 5000,
    maxDelayMs = 30000
) {
    sendTransaction(tx)
}
println("Actual delay: ${result.actualDelayMs}ms")
```

#### MEV-Protected Swaps

Execute Jupiter swaps with full MEV protection via Helius Sender.

```kotlin
val swapResult = helius.universalPrivacy.executeMevProtectedSwap(
    inputMint = SOL_MINT,
    outputMint = USDC_MINT,
    amount = 1_000_000_000L,
    userPublicKey = wallet,
    signCallback = { tx -> signTransaction(tx) },
    slippageBps = 100
)
if (swapResult.result?.success == true) {
    println("Signature: ${swapResult.result.signature}")
    println("MEV protection: ${swapResult.result.mevProtectionActive}")
}
```

#### ZK Compression Privacy Assessment

Assess privacy benefits of using ZK Compression.

```kotlin
val assessment = helius.universalPrivacy.assessZkCompressionBenefits("wallet")
println("Current privacy score: ${assessment.result?.currentPrivacyScore}")
println("Potential with ZK: ${assessment.result?.potentialPrivacyScore}")
println("Privacy gain possible: +${assessment.result?.privacyGainPossible}")
```

#### Comprehensive Privacy Health Score

Get a full privacy health report with actionable recommendations.

```kotlin
val health = helius.universalPrivacy.generatePrivacyHealthScore("wallet")
println("Overall: ${health.result?.overallScore}/100 (Grade: ${health.result?.grade})")
health.result?.dimensions?.forEach { dim ->
    println("  ${dim.dimension}: ${dim.score}/100")
}
println("Top recommendations:")
health.result?.topRecommendations?.forEach { println("  - $it") }
```

| Method | Description |
|--------|-------------|
| `rotatedQuery(providers, query)` | Execute query with RPC rotation |
| `getCyclingAddressPath(purpose, index)` | Get HD derivation path for fresh address |
| `analyzeAddressReuse(address)` | Analyze address reuse patterns |
| `getOptimalDenominations(amount)` | Get optimal split denominations |
| `generateTimingPlan(count, window, strategy)` | Generate timing schedule |
| `executeWithTimingObfuscation(min, max, action)` | Execute with random delay |
| `createEncryptedMemo(plaintext, hint)` | Create encrypted memo payload |
| `generateDecoyMemoSet(memo, count)` | Generate decoy memos |
| `assessZkCompressionBenefits(address)` | Assess ZK compression privacy benefits |
| `executeMevProtectedSwap(...)` | Execute MEV-protected Jupiter swap |
| `generatePrivacyHealthScore(address)` | Get comprehensive privacy report |

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
