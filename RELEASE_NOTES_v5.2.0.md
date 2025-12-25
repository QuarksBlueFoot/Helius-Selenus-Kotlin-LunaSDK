# v5.2.0 - Privacy-First APIs & React Native SDK

## 🚀 Major Release

This is a major feature release introducing Privacy-First APIs and a complete React Native SDK.

### ✨ New Features

#### Privacy-First APIs (v5.2.0)
- **Stealth Address API** - Generate and scan stealth addresses for private transactions
- **Privacy Pool API** - Optimal deposit strategies with mixing pools
- **Transaction Graph Privacy API** - Analyze transaction patterns for privacy leaks
- **Shielded Pattern API** - Detect shielded transaction patterns
- **Privacy Score Engine API** - Calculate comprehensive privacy scores with recommendations

#### React Native SDK
- Complete TypeScript port of all Helius APIs
- Full type definitions (400+ types)
- Base58 encoding/decoding utilities
- Lamport conversion utilities
- Retry with exponential backoff
- NPM package: `@selenus/luna-sdk`

### 📦 API Coverage

| API | Methods |
|-----|---------|
| Solana RPC | 50+ methods |
| DAS (Digital Asset Standard) | 12+ methods |
| Enhanced Transactions | Full parsing |
| Webhooks | Create, edit, delete |
| ZK Compression | 20+ methods |
| NFT Minting | Compressed NFTs |
| Priority Fees | Estimation |
| Staking | Stake/unstake |
| Smart Transactions | Auto-retry, fees |
| Jupiter | Quotes, swaps |
| Jito | Bundle support |
| WebSocket | Real-time subscriptions |
| **Privacy APIs** | 5 new APIs |

### 🔧 Technical Updates
- Kotlin 2.1.0
- Java 17 target
- kotlinx-serialization 1.7.3
- kotlinx-coroutines 1.10.2
- OkHttp 4.12.0

### 📥 Installation

**Gradle (Kotlin DSL)**
```kotlin
dependencies {
    implementation("xyz.selenus:luna-sdk:5.2.0")
}
```

**React Native**
```bash
npm install @selenus/luna-sdk
```

### 🔗 Links
- [Documentation](docs/LunaSDK_Guide.md)
- [API Reference](https://docs.selenus.xyz)
- [React Native README](luna-sdk-react-native/README.md)

### 📋 Full Changelog
https://github.com/QuarksBlueFoot/Helius-Selenus-Kotlin-LunaSDK/compare/v1.0.1...v5.2.0
