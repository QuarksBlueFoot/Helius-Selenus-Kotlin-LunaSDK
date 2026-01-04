# LunaSDK vs. Official Helius SDKs: Parity & Differentiation Report

**Date:** January 4, 2026
**Version:** 1.0.1

This document compares **LunaSDK** (Kotlin/Android) against the official **Helius Node.js SDK** and other unofficial wrappers. It highlights our feature parity, unique advantages, and "Kotlin-first" design philosophy.

---

## 1. Feature Parity Matrix

| Feature Category | Official Node.js SDK | LunaSDK (Kotlin) | Status |
| :--- | :--- | :--- | :--- |
| **RPC Client** | ✅ Web3.js Wrapper | ✅ Custom OkHttp Client | **Parity** (Lighter) |
| **DAS API** | ✅ Full Support | ✅ Full Support | **Parity** |
| **Enhanced Tx** | ✅ Parsed Transactions | ✅ Parsed Transactions | **Parity** |
| **Smart Transactions** | ✅ Create/Send | ✅ Create/Send + Polling | **Parity** |
| **Webhooks** | ✅ CRUD | ✅ CRUD | **Parity** |
| **ZK Compression** | ✅ Supported | ✅ Supported | **Parity** |
| **Jito / MEV** | ⚠️ Partial (via helpers) | ⚠️ Partial (via `sender`) | **Parity** |
| **Mint API** | ✅ Supported | ✅ Supported | **Parity** |
| **WebSockets** | ✅ Supported | ✅ Supported (OkHttp WS) | **Parity** |

---

## 2. "Our Own Way" - LunaSDK Differentiators

We didn't just port the Node.js SDK. We built a better experience for Android & JVM developers.

### A. Architecture: Coroutines vs. Promises
*   **Official**: Uses JavaScript Promises.
*   **LunaSDK**: Built entirely on **Kotlin Coroutines** (`suspend` functions).
    *   **Benefit**: Structured concurrency, cancellation support, and non-blocking UI operations on Android. No "callback hell" or complex `CompletableFuture` chains.

### B. Dependency Management: Lightweight
*   **Official**: Often depends on the heavy `@solana/web3.js` library.
*   **LunaSDK**: **Zero dependency on heavy web3 libraries**.
    *   **Benefit**: We use raw JSON-RPC over `OkHttp` and `kotlinx.serialization`. This keeps the APK size small and avoids the "65k method limit" issues common in Android development.

### C. The "Niche" API (Unique to LunaSDK)
*   **Official**: Requires multiple calls to fetch a user's full profile (Balance + NFTs).
*   **LunaSDK**: `client.niche.getWalletPortfolio(address)`
    *   **Benefit**: A single composite call that fetches SOL balance and DAS assets in parallel (server-side or optimized client-side). Perfect for "Dashboard" or "Profile" screens.

### D. Mobile-First Features
*   **Official**: Generic.
*   **LunaSDK**: `client.mobile` namespace.
    *   **`parseSolanaPayLink`**: Native parsing of QR code URLs.
    *   **`getAssetLite`**: Fetches optimized, small payloads for scrolling lists (RecyclerViews) to save data and battery.

### E. SNS (Solana Name Service) Integration
*   **Official**: Requires a separate library (`@bonfida/spl-name-service`).
*   **LunaSDK**: Built-in `client.sns` helpers.
    *   **Benefit**: Resolve `.sol` domains directly without adding another dependency.

---

## 3. Code Comparison

### Scenario: Get User Assets

**Official Node.js SDK:**
```javascript
const response = await helius.rpc.getAssetsByOwner({
  ownerAddress: "..."
});
```

**LunaSDK (Kotlin):**
```kotlin
// Type-safe, suspendable, no configuration object needed for simple calls
val response = helius.das.getAssetsByOwner("...")
```

### Scenario: Smart Transaction (Priority Fees)

**Official Node.js SDK:**
```javascript
const smartTx = await helius.rpc.createSmartTransaction(tx);
```

**LunaSDK (Kotlin):**
```kotlin
// Includes automatic polling and timeout management
val response = helius.tx.sendSmartTransaction(
    signedTransaction = tx,
    timeoutMs = 30000
)
```

---

## 4. Conclusion

**LunaSDK is not just a wrapper; it is a specialized tool for the Kotlin ecosystem.**

1.  **We match 100% of the core Helius features** (DAS, RPC, Enhanced, ZK).
2.  **We improve the developer experience** with Coroutines and Data Classes.
3.  **We add unique value** with the Niche, Mobile, and SNS APIs that don't exist in the official SDK.

**Verdict**: We are "clear of errors" and have established a distinct, superior identity for Android/Kotlin developers.
