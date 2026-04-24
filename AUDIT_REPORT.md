# Helius-Selenus Kotlin LunaSDK — Audit Report

**Scope:** all `luna-*` modules, `iris-sdk`, `luna-sdk-react-native`, `sample-app`, root config.
**Date:** 2026-04-23
**Verdict:** NOT SHIPPABLE. Multiple CRITICAL blockers before a Maven Central or npm publish.

---

## 0. Severity Legend

- **BLOCKER** — will not compile, ships fake cryptography, or publishes empty artifacts.
- **CRITICAL** — will compile but returns fabricated values from methods whose contracts imply real work.
- **HIGH** — correctness/safety defects (insecure RNG, truncated addresses, missing error paths).
- **MEDIUM** — structural debt, duplication, oversized files.
- **LOW** — cosmetic (smart quotes, nbsp, stray em-dash in one README).

---

## 1. BLOCKERS

### 1.1 Nine Gradle modules publish no source code
`settings.gradle.kts` includes these 12 Luna modules, but 9 of them contain **zero** `.kt` source files under `src/`:

| Module | kt files in src |
|---|---|
| `luna-rpc` | 0 |
| `luna-das` | 0 |
| `luna-webhooks` | 0 |
| `luna-priority` | 0 |
| `luna-enhanced-tx` | 0 |
| `luna-analytics` | 0 |
| `luna-innovations` | 0 |
| `luna-privacy` | 0 |
| `luna-jupiter` | 0 |

Each one has a `build.gradle.kts` that declares a `MavenPublication` (e.g. `luna-rpc/build.gradle.kts:35-69`) promising artifacts like "LunaSDK RPC — Standard and Enhanced Solana RPC methods". Publishing these will produce empty jars and make the SDK look fraudulent. Root cause: all real code lives inside the single `luna-core/src/main/kotlin/xyz/selenus/luna/LunaHeliusClient.kt` monolith (see §3.1). Either split that file into the modules, or delete the modules from `settings.gradle.kts`.

Additionally, the top-level `helius-tooling-sdk/` folder is entirely empty (no files at all).

### 1.2 Source file encoded as UTF-16 LE with BOM
`luna-core/src/main/kotlin/xyz/selenus/luna/LunaBurnerManager.kt` starts with byte sequence `FF FE` (UTF-16 LE BOM) and every character is followed by a null byte. `kotlinc` defaults to UTF-8 and will reject or mis-parse this. Must be re-saved as UTF-8 (no BOM). Every other source file in the repo is UTF-8 — this one is an outlier.

### 1.3 Mock cryptography shipped as real privacy features
These are labeled "mock"/"simulated" by the author but are exposed through public API surfaces called `encrypt`, `decrypt`, `deriveSharedSecret`, `stealthAddress` etc. If a user trusts them with funds or PII the consequence is total loss of confidentiality.

| File | Line(s) | Problem |
|---|---|---|
| `iris-sdk/.../IrisWhisperNamespace.kt` | 83-97 | `deriveSimulatedSecret` (Mock ECDH — concatenates first 4 chars of each key), `encryptAes` (just reverses bytes), `decryptAes` (base64 decode + reverse). Comment admits `// Replace with BouncyCastle/Signal Lib in Prod`. |
| `iris-sdk/.../IrisPrivacy.kt` | 855-873 | `derivePublicKey`, `computeSharedSecret`, `addPoints`, `scalarMultiply` — fake ed25519 using SHA-256 over concatenated strings. |
| `iris-sdk/.../PrivacyNamespace.kt` | 226-256 | `generateRandomPublicKey`, `deriveSharedSecret`, `deriveStealthAddress` — strings + hashCode. |
| `iris-sdk/.../IrisPaymentLinks.kt` | 135-138 | `mockSignature = "sig_claim_${System.currentTimeMillis()}"` returned as a real on-chain signature. |
| `luna-core/.../LunaBurnerManager.kt` | 251, 287 | `sig_sweep_…` and `sig_close_…` — same fake-signature pattern for a wallet-sweeping feature that implies on-chain movement of funds. |
| `luna-core/.../LunaHeliusClient.kt` | 11343, 11382-11385 | `encryptedAmountPlaceholder`, `senderCiphertextPlaceholder`, `receiverCiphertextPlaceholder`, `rangeProofPlaceholder`, `equalityProofPlaceholder` — SPL Confidential Transfer fields filled with hashCode strings. |
| `luna-core/.../LunaHeliusClient.kt` | 13130 | `address = "DERIVE:$oneTimePath"` returned as a stealth address (not a valid Solana pubkey). |
| `luna-core/.../LunaHeliusClient.kt` | 11778, 13148 | `java.lang.Math.random()` used for endpoint routing and key-material generation. Use `java.security.SecureRandom`. |
| `iris-sdk/.../IrisPhase1Privacy.kt` | 92, 488 | `encryptedPlaceholder = "[ENCRYPTED:${amount.hashCode()}]"` exposed via public data class. |
| `luna-sdk-react-native/src/api/privacy/PrivacyApis.ts` | 43 | `deriveSharedSecret` placeholder — "would use crypto library". |

### 1.4 LLM training-data citation artifacts leaked into production KDoc
`luna-core/src/main/kotlin/xyz/selenus/luna/LunaHeliusClient.kt` contains **40** citation markers of the form `【<trainer-id>†L<n>-L<n>】` (Unicode `U+3010`/`U+3011`/`U+2020`). These are model-training-data references that should never appear in source.

Lines: 840, 849, 1072, 1106, 1126, 1154, 1237, 1304, 1324, 1333, 1349, 1365, 1377, 1624, 1671, 2038, 2049, 2058, 2066, 2075, 2084, 2093, 2102, 2111, 2120, 2129, 2138, 2147, 2156, 2165, 2174, 2182, 2190, 2201, 2212, 2223, 2234, 2245, 2256, 2265. Two trainer IDs: `128353577680464` and `800459967483568`.

### 1.5 Truncated Solana address
`iris-sdk/.../AddOnNamespaces.kt:333` — the Fastlane tip-account list ends with `"uthPh9ZGR"`, a 9-character string. Solana base58 pubkeys are 32-44 characters; any code selecting this as a tip account will fail. Remove or replace with a valid tip account.

---

## 2. CRITICAL — Fabricated return values

### 2.1 Hardcoded privacy scores
`iris-sdk/.../PrivacyNamespace.kt`:
- 106: `val addressReuseScore = 70 // Placeholder`
- 114: `val amountScore = 65 // Placeholder`
- 122: `val exchangeScore = 60 // Placeholder`
- 126: `val dustScore = 75 // Placeholder`
- 139: `if (signatures.size < 2) return 80`

`iris-sdk/.../IrisPrivacy.kt:815` — `return 70 // Placeholder` inside `analyzeExchangeExposure()`. Public method whose return value is independent of its input.

### 2.2 Functions that return `true` regardless of input
`luna-nlp/.../chain/IntentChainParser.kt`:
- 487-490: `PriceCondition.evaluate()` always returns `true`.
- 498-501: `BalanceCondition.evaluate()` always returns `true`.

These are used inside conditional-execution intents, so every trade-trigger chain will "fire" unconditionally.

### 2.3 Methods advertised as implementations that return empty / placeholder
- `luna-core/.../LunaHeliusClient.kt:1739-1752` — `verifySignatureHelp` is a docstring masquerading as a function.
- `luna-core/.../LunaHeliusClient.kt:11588-11594` — `getOptimalRegions(count)` ignores `count` and returns a hardcoded 3-element list regardless of real latency.
- `luna-core/.../LunaHeliusClient.kt:11617` — "simplified analysis" block that doesn't decode the transaction it claims to analyze.
- `luna-core/.../LunaHeliusClient.kt:3117` — unknown URI scheme silently returns `emptyMap()` instead of throwing.
- `iris-sdk/.../IrisPrivacy.kt:132` — `generateDecoyTransactions` returns `emptyList()`.
- `iris-sdk/.../YellowstoneNamespace.kt:414` — `getHistoricalAccountUpdates` returns `emptyList() // Placeholder` (needs gRPC client).
- `iris-sdk/.../SnsNamespace.kt:194, 209, 214, 221` — PDA derivation and data parsers all return empty/null stubs.
- `iris-sdk/.../JitoNamespace.kt:242` — `validateBundleHasTip` only checks `isNotEmpty`; does not parse instructions.
- `iris-sdk/.../PrivacyNamespace.kt:289` — `sendMixedTransaction` hardcodes `bundled = false`; either implement mixing or rename.
- `iris-sdk/.../IrisInnovations.kt:532-550` — `snipePool()` returns `SnipeResult(signature = null)` but the surrounding message wording implies execution succeeded.
- `luna-sdk-react-native/src/api/CoreApis.ts:69` — `buildOptimizedTransaction()` — "would require local transaction building".
- `luna-sdk-react-native/src/api/privacy/PrivacyApis.ts:130` — `findConnectedAddresses()` returns `[]`.
- `luna-sdk-react-native/src/api/StakingApi.ts:1-34` — file opens with `// Stub APIs for remaining modules` and has no error handling on RPC calls.
- `luna-nlp/.../voice/VoiceInput.kt:350-362` — `DefaultSpeechRecognizer` emits an error; no platform bridge implementation.

### 2.4 Tests that only validate placeholder formats
`luna-sdk/src/test/kotlin/com/selenus/luna/Phase1PrivacyInnovationsTest.kt`:
- 25: hardcoded `"test-api-key"`.
- 44: `assertTrue(plan.encryptedAmountPlaceholder.startsWith("[ENCRYPTED:"))`
- 61-62: asserts on `rangeProofPlaceholder` and `equalityProofPlaceholder` string markers.

These tests pass precisely because the crypto is fake. Green CI currently guarantees nothing about privacy correctness.

---

## 3. MEDIUM — Structure

### 3.1 14,353-line God file
`luna-core/src/main/kotlin/xyz/selenus/luna/LunaHeliusClient.kt`:
- 47 inner API classes, 151 `@Serializable` data classes in a single source file.
- Eight overlapping privacy-related API classes: `PrivacyApi` (line 3486), `ZkPrivacyApi` (7271), `PrivacyPoolApi` (10121), `PrivacyScoreEngineApi` (10764), `ShieldedPatternApi` (10569), `AdvancedStealthApi` (13043), `PrivateTransactionsApi` (13380), `UniversalPrivacyApi` (14124). These should live in the empty `luna-privacy` module, one class per file.
- Other inner classes (`RpcApi`, `DasApi`, `WebhookApi`, `PriorityFeeApi`, `EnhancedTransactionsApi`, `ZkCompressionApi`, `JupiterApi`, `AnalyticsApi`, etc.) map 1:1 to the empty Gradle modules listed in §1.1 — this is almost certainly where the source was meant to go.

### 3.2 1,782-line IrisQuickNodeClient
Recommended split:
- `IrisQuickNodeClientCore.kt` — client + RPC execution.
- `IrisStealthAddressModule.kt` — stealth helpers.
- `IrisPrivacyAnalysisModule.kt` — scoring helpers.
- `IrisDataModels.kt` — all `@Serializable` data classes.

### 3.3 Redundant single-line re-export files
`luna-sdk-react-native/src/api/privacy/`:
- `StealthAddressApi.ts`, `PrivacyPoolApi.ts`, `ShieldedPatternApi.ts`, `TransactionGraphPrivacyApi.ts`, `PrivacyScoreEngineApi.ts` each contain one `export { X } from './PrivacyApis';`. Either actually split the classes into those files or delete the stubs and export from `PrivacyApis.ts` directly.

### 3.4 Unused/broken wiring (HIGH within medium)
- `iris-sdk/.../IrisPaymentLinks.kt:48` — `vaultPublicKey` derived from raw Java keypair bytes, not encoded as Solana base58.
- `iris-sdk/.../IrisPaymentLinks.kt:77` — return type mismatch: returns `BalanceResult.result.value` where signature declares `Long`.
- `luna-sdk-react-native/src/api/CoreApis.ts:78` — `JupiterApi.baseUrl = 'https://quote-api.jup.ag/v6'` hardcoded; should come from a client config.

---

## 4. LOW — Cosmetic

### 4.1 Smart quotes / non-breaking space in KDoc
`luna-core/.../LunaHeliusClient.kt`:
- 34, 986, 1155, 1235, 2156, 2165 — right single quotes `'` (U+2019) instead of ASCII `'`.
- 810 — NBSP (U+00A0) between `1` and `000` in "up to 1 000".

These aren't compile errors but trip copy-paste and grep.

### 4.2 Em-dashes
Only a single em-dash anywhere in source/docs: `luna-nlp/README.md` (one occurrence). No em-dashes in any `.kt`, `.ts`, or `.java` file. (This was explicitly asked about — essentially clean.)

### 4.3 TODO/FIXME
Only two:
- `sample-app/src/main/java/com/selenus/luna/sample/MainActivity.kt:21` — "Replace with your actual Helius API key" (intentional).
- `sample-app/src/test/java/com/selenus/luna/sample/FeatureRegistryTest.kt:36` — same.

### 4.4 Sample-app dummy transaction
`sample-app/.../FeatureRegistry.kt:160-181` — a 64-zero-byte base64 string is passed to `getComputeUnits`. Label it `DEMO_ZERO_TX` with a comment, or guard behind a feature flag so sample-app users don't file bugs about the expected poll failure.

---

## 5. Remediation priority

1. **Before any publish:** fix §1.1 (9 empty modules), §1.2 (UTF-16 file), §1.3 (mock crypto), §1.4 (40 citation markers), §1.5 (truncated address).
2. **Replace fabricated returns** (§2) with real RPC calls or delete the methods. Most placeholder returns have real Helius/QuickNode endpoints that can be wired through existing `executeRpc(...)` helpers.
3. **Restructure** (§3.1) by moving the inner classes out of `LunaHeliusClient.kt` into the currently-empty modules. This alone fixes both §1.1 and §3.1 simultaneously.
4. **Replace the placeholder-format tests** (§2.4) with contract tests that actually invoke crypto and assert mathematical properties (e.g. `decrypt(encrypt(m)) == m`, range proof verification passes).
5. **Fix cosmetic** (§4) in the same commit as §1 since they touch the same file.

### Suggested libraries for the crypto rewrite
- Ed25519 / Curve25519: **Bouncy Castle** (`org.bouncycastle:bcprov-jdk18on`) or **tweetnacl-java**.
- ElGamal for SPL Confidential Transfers: port from the official `solana-zk-token-sdk` Rust crate, or call out over JNI to `solana-sdk`.
- AES-GCM: `javax.crypto.Cipher` with `AES/GCM/NoPadding`.
- `SecureRandom` everywhere (`new SecureRandom()` / `SecureRandom.getInstanceStrong()`), never `Math.random()`.
- For React Native: `@noble/ed25519`, `@noble/curves`, `tweetnacl` — all audited and tree-shakeable.

---

## 6. File/line index (quick reference)

```
CRITICAL
  luna-core/.../LunaHeliusClient.kt:34,810,986,1155,1235,1739,2156,2165
  luna-core/.../LunaHeliusClient.kt:11313,11343,11372-11376,11382-11385,11588-11594,11617,11778,13130,13148
  luna-core/.../LunaHeliusClient.kt  [40 citation markers, see §1.4]
  luna-core/.../LunaBurnerManager.kt   [whole file: UTF-16 LE BOM; lines 221,251,287 fake values]
  iris-sdk/.../IrisWhisperNamespace.kt:83-97
  iris-sdk/.../IrisPrivacy.kt:132,815,855-873
  iris-sdk/.../PrivacyNamespace.kt:106,114,122,126,139,226-256,289
  iris-sdk/.../IrisPaymentLinks.kt:48,77,135,138
  iris-sdk/.../IrisPhase1Privacy.kt:92,488
  iris-sdk/.../AddOnNamespaces.kt:333
  iris-sdk/.../YellowstoneNamespace.kt:414
  iris-sdk/.../JitoNamespace.kt:242
  iris-sdk/.../SnsNamespace.kt:194,209,214,221
  iris-sdk/.../IrisInnovations.kt:532-550
  luna-nlp/.../chain/IntentChainParser.kt:487-501
  luna-nlp/.../voice/VoiceInput.kt:350-362
  luna-sdk-react-native/src/api/privacy/PrivacyApis.ts:43,130
  luna-sdk-react-native/src/api/CoreApis.ts:69,78
  luna-sdk-react-native/src/api/StakingApi.ts:1-34
  luna-sdk/src/test/kotlin/com/selenus/luna/Phase1PrivacyInnovationsTest.kt:25,44,61-62

STRUCTURE
  luna-core/.../LunaHeliusClient.kt            [14,353 lines, 47 inner classes]
  iris-sdk/.../IrisQuickNodeClient.kt          [1,782 lines]
  luna-sdk-react-native/src/api/privacy/*.ts   [redundant re-export stubs]
  luna-rpc, luna-das, luna-webhooks, luna-priority, luna-enhanced-tx,
    luna-analytics, luna-innovations, luna-privacy, luna-jupiter   [empty published modules]
  helius-tooling-sdk/                          [empty directory, referenced nowhere in settings.gradle.kts]

COSMETIC
  luna-core/.../LunaHeliusClient.kt:34,810,986,1155,1235,2156,2165
  luna-nlp/README.md                           [1 em-dash]
  sample-app/.../MainActivity.kt:21
  sample-app/.../FeatureRegistryTest.kt:36
  sample-app/.../FeatureRegistry.kt:160-181
```

---

**Bottom line:** the "em-dashes and TODOs" part of the request is nearly clean — there is essentially one em-dash in a README and two intentional `TODO`s for user-supplied API keys. The real issues are (a) nine empty modules that are publishable as fraudulent artifacts, (b) a UTF-16 source file that won't compile on a strict toolchain, (c) fake cryptography presented as privacy features, and (d) 40 LLM citation markers still inside KDoc comments. Fix those four classes of issue first; everything else is structural cleanup that falls out naturally once the 14k-line monolith is split into the currently-empty modules.
