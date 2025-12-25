# LunaSDK Feature Guide for Website Team

**Version:** 1.0.1
**Date:** December 28, 2025
**Target Audience:** Android Developers, Kotlin Backend Engineers, Solana Builders

---

## 1. Core Value Proposition
**"The Modern, Kotlin-First Helius Client"**
LunaSDK is designed specifically for the Kotlin ecosystem (Android & JVM). Unlike generic Java wrappers, it leverages modern language features like **Coroutines** and **Data Classes** to make Solana development feel native and intuitive.

*   **Type-Safe**: Strong typing for requests and responses.
*   **Non-Blocking**: Built entirely on Kotlin Coroutines (`suspend` functions).
*   **Zero-Config**: Works out of the box with just an API Key.
*   **Modular**: Organized into intuitive namespaces (e.g., `client.das`, `client.niche`).

---

## 2. Feature Breakdown (By Namespace)

The SDK is organized into "Namespaces" accessible from the main `LunaHeliusClient`.

### 🎮 Niche API (`client.niche`) **[NEW]**
*High-level composite methods designed for specific use cases like Gaming and Dashboards.*
*   **`getWalletPortfolio(address)`**: Returns a complete snapshot of a user's wallet (SOL balance + All Assets/NFTs) in one call. Perfect for "My Profile" screens.
*   **`getTokenDeepDive(mint)`**: Fetches everything about a token: Metadata, Supply, and Largest Holders. Ideal for "Token Details" pages.
*   **`verifyGameAccess(address, ...)`**: One-line check to see if a user can play a game. Verifies they have enough SOL for gas AND own a specific NFT/Collection.

### 🆔 SNS API (`client.sns`) **[NEW]**
*Native support for Solana Name Service (.sol domains).*
*   **`getDomainName(address)`**: Reverse lookup (Wallet Address -> "user.sol").
*   **`getWalletAddress(domain)`**: Forward lookup ("user.sol" -> Wallet Address).
*   **`getDomainRecord(domain, record)`**: Fetch specific records like IPFS hashes, Twitter handles, or email addresses attached to a domain.

### 📱 Mobile API (`client.mobile`) **[NEW]**
*Utilities optimized for Android/Mobile development.*
*   **`parseSolanaPayLink(url)`**: Parses `solana:` payment links (QR codes) into usable objects.
*   **`createSolanaPayLink(...)`**: Generates standard Solana Pay URLs for payments.
*   **`getAssetLite(assetId)`**: Fetches a lightweight version of an asset (just ID, Name, Image) to save bandwidth on mobile lists.

### ⚡ Smart Transactions (`client.tx`) **[UPDATED]**
*Intelligent transaction management.*
*   **`createSmartTransaction(...)`**: Automatically optimizes a transaction with the perfect **Priority Fee** and **Compute Unit** limits to ensure it lands on-chain quickly and cheaply.
*   **`sendSmartTransaction(...)`**: Sends, monitors, and automatically retries transactions until confirmation.

### 🖼️ DAS API (`client.das`)
*Digital Asset Standard - The ultimate way to query NFTs and Tokens.*
*   **`getAsset(id)`**: Get full details for any token/NFT.
*   **`getAssetsByOwner(address)`**: List everything a user owns.
*   **`getAssetsByGroup(...)`**: Fetch all NFTs in a collection.
*   **`searchAssets(...)`**: Powerful search queries for assets.

### 🚀 Enhanced API (`client.enhanced`)
*Human-readable transaction history.*
*   **`getTransactions(signatures)`**: Turns raw on-chain data into human-readable stories (e.g., "User sent 5 SOL to Exchange").
*   **`getTransactionsForAddress(address)`**: Get a user's history with parsed descriptions.

### 📝 Memo API (`client.memo`) **[NEW]**
*   **`getMemosForTransaction(signature)`**: Extracts text memos from transactions. Useful for payment references or on-chain messaging.

### 💰 Staking API (`client.staking`)
*   **`getStakingRewards(...)`**: View rewards earned by staking accounts.
*   **`createStakeTransaction(...)`**: Helpers to delegate SOL to validators.

---

## 3. Technical Highlights for the "Developers" Section

*   **Dependency**: `implementation("xyz.selenus:luna-sdk:1.0.1")`
*   **Platform**: Android 5.0+ (API 21+), Java 11+
*   **Architecture**:
    *   Uses `OkHttp 4` for networking.
    *   Uses `kotlinx.serialization` for JSON parsing (no Gson/Jackson overhead).
    *   Fully Coroutine-based (no callbacks, no RxJava required).

## 4. Example Code Snippet for Website

```kotlin
// 1. Initialize
val client = LunaHeliusClient("YOUR_API_KEY")

// 2. Get a User's Portfolio (One-liner!)
val portfolio = client.niche.getWalletPortfolio("user.sol")
println("SOL Balance: ${portfolio.solBalance}")
println("NFT Count: ${portfolio.assets.total}")

// 3. Check if they can play your game
val access = client.niche.verifyGameAccess(
    address = "user.sol",
    requiredCollectionAddress = "MyGameCollectionAddress"
)

if (access.hasAccess) {
    startGame()
} else {
    showError(access.reason)
}
```
