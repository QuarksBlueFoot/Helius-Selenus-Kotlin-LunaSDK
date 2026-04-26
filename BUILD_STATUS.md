# Build Status Report

**Date**: 2026-04-24
**Version**: 5.7.0 — Wallet API + LaserStream
**Status**: Module split in progress (see "Phase 2 status" below)

---

## What's in 5.7.0

- **`:luna-wallet`** *(NEW)* — Helius Wallet API (Beta) wrapper. Identity,
  human-readable balances, parsed history, transfers, funding lineage. Cold
  `Flow` helpers for automatic pagination. Auto-chunked batch identity for
  large address lists.
- **`:luna-laserstream`** *(NEW)* — Geo-affinity endpoint selection (parallel
  HTTP HEAD probe across 9 mainnet regions), Flow-based Atlas Enhanced
  WebSocket subscriptions with exponential-backoff reconnect, and a clean
  BYO-transport interface for Yellowstone-compatible gRPC streams (so the
  SDK stays Kotlin-only — no protobuf code-gen pipeline forced on consumers).
- **`:luna-keys`** *(NEW)* — Pure-JVM Solana key utilities. `SolanaKeypair`
  (generate / makeKeypairs / fromSecretSeed / fromSolanaKeystoreBytes / sign /
  verify) using JDK 17 native Ed25519 + a real RFC 8032 §5.1.5 seed-derivation
  implementation. `Base58` codec. `SolanaAddress` with on-curve validation
  that distinguishes wallets from PDAs (a strict superset of Helius Rust
  SDK's `is_valid_solana_address`). `Slip10` for BIP-39/SLIP-0010 hierarchical
  deterministic wallet derivation along Phantom-compatible
  `m/44'/501'/n'/0'` paths (validated against SLIP-0010 official Ed25519
  test vectors). `X25519` for RFC 7748 ECDH using JDK XDH (constant-time)
  plus Ed25519↔Curve25519 birational conversion so two Solana wallet
  holders can do ECDH without an extra key exchange. `StealthAddress` —
  Monero-style dual-key stealth address protocol (DKSAP) adapted for
  Ed25519/Solana, with `MetaAddress`/`derive`/`scan` API and 9 contract
  tests proving the recovered spending scalar can sign for the stealth
  address (`spendingScalar·G == stealthAddress`). No Bouncy Castle dep.
- **`:luna-solana-pay`** *(NEW)* — Type-safe Solana Pay spec implementation.
  `TransferRequest` (URI built client-side) + `TransactionRequest` (server
  signs). Lossless `BigDecimal`-based amount math, multi-reference support
  (the in-monolith MobileApi.generatePaymentLink lacks all of these),
  RFC 3986-correct percent-encoding (`%20` for spaces, not `+`).
- **Webhook upgrades**: `toggleWebhook`, `appendAddressesToWebhook`,
  `removeAddressesFromWebhook` — closes parity gap with Helius Rust SDK.
  `verifyWebhookSignature` is now a real Ed25519 verify (was a stub
  returning a help string in 5.6).
- **Sender innovations**: `warmSenderConnection(region)` pre-warms the TLS
  session before a latency-critical send (mirrors Helius Rust SDK's
  `warm_sender_connection`); `determineTipLamports(min)` clamps tips to
  the 75th-percentile floor with a hard minimum (`determine_tip_lamports`).
- **`LunaHeliusClientBuilder` + `LunaHeliusClientFactory`** — fluent
  builders for the client. Custom timeouts, user-agent header, custom
  interceptors, BYO `OkHttpClient`. Factory shares connection pool across
  multiple cluster instances. Mirrors `HeliusBuilder` / `HeliusFactory`.
- **`apiKey` and `baseUrl` promoted to `public val`** on `LunaHeliusClient`.
  Required by feature modules that build their own URLs (`luna-wallet`,
  `luna-laserstream`, plus `RpcRotationApi` and `WebSocketEnhancedApi` once
  Phase 2 lands).

## Module list (`settings.gradle.kts`)

| Module | Status | Notes |
|--------|--------|-------|
| `:luna-core` | shipped | Monolith, 13K lines, in-progress split |
| `:luna-rpc` | shipped | Enhanced V2 RPC methods |
| `:luna-das` | shipped | Digital Asset Standard |
| `:luna-webhooks` | shipped | Webhook CRUD |
| `:luna-priority` | shipped | Priority fee estimation |
| `:luna-enhanced-tx` | shipped | Parsed transaction REST |
| `:luna-jupiter` | shipped | Jupiter swap, trigger, recurring |
| `:luna-analytics` | shipped | Cross-cutting analytics & dashboards |
| `:luna-privacy` | **3 of 16 classes extracted (v5.7.3)** | PrivateBroadcastApi, RpcRotationApi, FingerprintObfuscationApi shipped; 13 still in monolith |
| `:luna-innovations` | **declared, source not extracted** | See Phase 2 status |
| `:luna-wallet` | **NEW v5.7** | Helius Wallet API (Beta) |
| `:luna-laserstream` | **NEW v5.7** | LaserStream + Atlas WS |
| `:luna-keys` | **NEW v5.7** | Ed25519 keypairs, base58, on-curve check |
| `:luna-solana-pay` | **NEW v5.7** | Solana Pay URI builder + parser |
| `:luna-nlp` | shipped | NLP transaction command parsing |
| `:luna-sdk` | shipped | Umbrella package (api transitively depends on every module) |
| `:iris-sdk` | shipped | QuickNode Solana SDK (separate product) |

## Phase 2 status — monolith split

7 of ~16 feature modules have been extracted from `LunaHeliusClient.kt`.
Privacy + innovations (~28 inner classes, ~6,000 lines combined) are still
in-monolith. Investigation in 2026-04-24's session revealed two structural
constraints that change the plan:

1. **Atomic extraction required.** The 28 classes form a cyclic call graph
   (e.g. `WalletCorrelationApi → privacy.analyzeAddressLinkage`,
   `StrategyEngineApi → jupiter.getQuote`). A partial extraction breaks the
   monolith because in-monolith inner classes still reference the removed
   `privacy.xxx` field. The next session must read all 28 classes, write 28
   new module files, then delete from the monolith in a single atomic edit.
2. **Migration shim is permanent.** The `_DasMigrationShim` /
   `_RpcMigrationShim` / etc. block was originally documented as "delete when
   the rest is extracted." That's wrong — 29 references inside *stay-in-core*
   classes (`NicheApi`, `MintApi`, `BatchOperationsApi`, `WebSocketApi`,
   `TransactionApi`, `NotificationSystemApi`, `MobileOptimizationApi`) would
   need to be rewritten to `client.das.xxx` etc., which would require
   `:luna-core` to depend on `:luna-das` / `:luna-rpc` / etc. — a Gradle
   cycle. The shim must stay until an SPI-interface decoupling is designed
   for these stay-in-core classes.

## Phase 3 status — crypto correctness

**Started 2026-04-25**. First two items shipped this session:

✅ **`SecureRng` utility** in `luna-core/xyz.selenus.luna.crypto/SecureRng.kt`.
   Process-wide shared `SecureRandom` + ergonomic `.secureRandom()` extensions
   matching the `kotlin.random.Random` surface (IntRange, LongRange, List,
   CharSequence, Array). Two critical sites in `LunaHeliusClient.kt` migrated:
   (1) `generateSecureEntropy` — was using `Math.random()` to generate
   stealth-address ephemeral hints, completely broken privacy guarantee since
   the function name lied about being secure; (2) `RpcRotationApi.WEIGHTED` —
   predictable endpoint rotation defeats the purpose of rotation. 14 tests
   in `SecureRngTest`. Remaining ~30 `.random()` sites inside privacy /
   innovations classes (decoy gen, timing jitter, fingerprint padding) are
   deferred to atomic privacy/innovations extraction (#3, #4) — captured in
   the extraction plan.

✅ **Real Ed25519 seed derivation** in `luna-keys/Ed25519Derive.kt`.
   ~150 LOC of careful BigInteger field arithmetic over `GF(2^255 - 19)`,
   extended-projective Edwards point ops, MSB-first double-and-add. Validated
   against RFC 8032 §7.1 vectors 1-5 and 10-iteration round-trip against the
   JDK's Ed25519 generator. Cost: ~3-5ms per derivation. Variable-time at the
   field-op level (BigInteger ops aren't constant-time) — documented as fine
   for personal-device wallet keygen, not safe for HSM/co-resident threat
   models. `SolanaKeypair.fromSecretSeed(32bytes)` now works.

✅ **iris-sdk Whisper rewrite** — `IrisWhisperNamespace` v1 (mock crypto:
   string-reverse "AES" + 4-char-concat "ECDH") replaced with real
   AES-GCM-256 + HKDF-style key derivation + PBKDF2 passphrase derivation.
   Wire format: `whisper:v2:base64url(nonce||ciphertext||tag)`. v1 payloads
   now throw `IrisWhisperVersionException` so they can't be silently
   accepted. 18 contract tests covering round-trip, GCM tag mismatch,
   tampered ciphertext, truncation, v1 rejection, key length validation,
   unicode. Used JDK 17 native AES-GCM / PBKDF2-HMAC / HMAC-SHA256 — no
   Bouncy Castle dep added.

✅ **iris-sdk PrivacyNamespace.generateStealthAddress fully implemented**
   using the new `xyz.selenus.luna.keys.StealthAddress` toolkit (DKSAP over
   Ed25519/X25519). Companion `scanStealthAddress` recipient-side method
   added. The fail-loud `IrisStealthAddressNotImplementedError` removed.
   Same scheme implemented in TypeScript for `luna-sdk-react-native` using
   `@noble/curves` + `@noble/hashes` — interoperable bit-for-bit with the
   JVM side (same domain separator `luna-stealth-v1`).

✅ **In-place monolith hardening (no extraction needed)** — fixed shim-vs-
   response bug at LunaHeliusClient.kt:11623 (`_enhanced.result?.jsonArray`
   was referencing the migration shim, not the response — silently returned
   no transactions for years). Swept every `.random()` call in the monolith
   to `.secureRandom()` (using `xyz.selenus.luna.crypto.SecureRng`); zero
   `.random()` or `Math.random()` calls remain. Migration shim KDoc updated
   to "PERMANENT — backs stay-in-core classes" so future readers don't try
   to delete it.

Still open:

- Replace hashCode-based ZK placeholders (`encryptedAmountPlaceholder`,
  `rangeProofPlaceholder`, `equalityProofPlaceholder`) in SPL Confidential
  Transfer planning with real ElGamal + range proofs.
- Replace the placeholder `startsWith("[ENCRYPTED:")` assertions in
  `Phase1PrivacyInnovationsTest` with contract tests
  (`decrypt(encrypt(m)) == m`; `verifyProof(p) == true`).
- `IrisPaymentLinks.kt`: `mockSignature = "sig_claim_${...}"` — needs to
  return the actual on-chain signature once the claim path is wired.
- React Native `StealthAddressApi` (#11) and Iris `generateStealthAddress`
  (#19) share a common need for Ed25519→X25519 conversion.

## Build configuration

- **Kotlin**: 2.3.0
- **Gradle**: 8.9 (foojay toolchain resolver)
- **Android Gradle Plugin**: 8.7.3
- **Java target**: 17 (foojay-resolved)
- **Android min SDK**: 24
- **Android target SDK**: 34

### Dependencies (current)

- OkHttp: 5.3.2
- kotlinx-serialization-json: 1.10.0
- kotlinx-coroutines-core: 1.10.2
- gRPC Kotlin: 1.5.0 / gRPC Java: 1.78.0 / protobuf-kotlin: 4.32.0
  (declared in `:luna-core` for future LaserStream protobuf work; not yet
  consumed — `:luna-laserstream` ships a BYO transport interface instead)

## Known sandbox limitations

- This repo cannot be compiled inside the assistant's sandbox (Java 11 only;
  the project requires 17 via foojay + network access). Run `./gradlew build`
  locally to verify.

---

**Cycle resolution (v5.7.2 ✅)**: Both Gradle cycles that blocked extraction
are now eliminated. (1) PrivacyPoolApi + ShieldedPatternApi switched from
`zkCompressionExtended.getCompressedTokenAccountsByOwner` to stay-in-core
`zk.getCompressedTokenAccountsByOwner` (identical RPC call). (2) Built a
TransactionIntelligenceApi mirror in `:luna-analytics` with extension
property `client.txIntelligence`; the in-monolith inner class is now a
slim mirror (only the 2 methods that the in-monolith TransactionGraphPrivacyApi
caller uses) until that class itself is extracted. Net luna-core code
reduction: ~210 lines.

**Next session priorities** (in order):
1. Atomic extraction of privacy + innovations in 3 batches:
   - **Batch 1 (leaves)**: ConfidentialTokenApi, FingerprintObfuscationApi,
     RpcRotationApi, PrivateBroadcastApi, PrivateTransactionsApi,
     AdvancedStealthApi, ReactiveStreamApi, ReactiveSubscriptionApi,
     WebSocketEnhancedApi, LaserStreamAdvancedApi, SenderAdvancedApi,
     ZkCompressionExtendedApi, TimeTravelApi, ConfidentialTransactionApi.
   - **Batch 2 (single cross-call)**: WalletCorrelationApi, ZkPrivacyApi,
     TokenLaunchApi.
   - **Batch 3 (heavy aggregators)**: PrivacyApi, PrivacyScoreEngineApi,
     PrivacyCombinatorApi (~1000 LOC), UniversalPrivacyApi, FundingTrackerApi,
     StrategyEngineApi, NetworkIntelligenceApi, TransactionGraphPrivacyApi
     (when this lands, delete the in-monolith TransactionIntelligenceApi mirror).
2. Sign-with-raw-scalar primitive in luna-keys for stealth-address spending.
