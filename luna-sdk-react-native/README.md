# @selenus/luna-sdk

> The most comprehensive Solana SDK for React Native - Complete Helius API coverage with Privacy-First features

[![npm version](https://badge.fury.io/js/@selenus%2Fluna-sdk.svg)](https://www.npmjs.com/package/@selenus/luna-sdk)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.0-blue.svg)](https://www.typescriptlang.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

## Features

- **Complete Helius API Coverage** - All 100+ Helius endpoints
- **Standard Solana RPC** - Full JSON-RPC 2.0 support
- **Digital Asset Standard (DAS)** - NFT and token metadata APIs
- **Privacy-First APIs** - Stealth addresses, privacy pools, transaction graph privacy
- **ZK Compression** - Light Protocol integration
- **WebSocket Subscriptions** - Real-time blockchain events
- **Smart Transactions** - Automatic retry, priority fees, Jito bundles
- **Type-Safe** - Full TypeScript support with comprehensive types

## Installation

```bash
npm install @selenus/luna-sdk
# or
yarn add @selenus/luna-sdk
# or
pnpm add @selenus/luna-sdk
```

## Quick Start

```typescript
import { LunaHeliusClient } from '@selenus/luna-sdk';

// Initialize the client
const client = new LunaHeliusClient('your-helius-api-key');

// Get account balance
const balance = await client.solana.getBalance('So11111111111111111111111111111111111111112');

// Get NFTs for a wallet
const nfts = await client.das.getAssetsByOwner({
  ownerAddress: 'wallet-address',
  page: 1,
  limit: 100,
});

// Parse a transaction
const parsed = await client.enhancedTransactions.parseTransaction('signature');
```

## API Reference

### Core APIs

#### SolanaApi
Complete Solana JSON-RPC implementation:
```typescript
client.solana.getAccountInfo(address)
client.solana.getBalance(address)
client.solana.getBlock(slot)
client.solana.getBlockHeight()
client.solana.getBlockTime(slot)
client.solana.getLatestBlockhash()
client.solana.getMinimumBalanceForRentExemption(dataSize)
client.solana.getSignaturesForAddress(address, options)
client.solana.getSlot()
client.solana.getTransaction(signature)
client.solana.sendTransaction(signedTx)
// ... 50+ more methods
```

#### DAS (Digital Asset Standard)
```typescript
client.das.getAsset(assetId)
client.das.getAssetsByOwner(params)
client.das.getAssetsByCreator(params)
client.das.getAssetsByGroup(params)
client.das.searchAssets(params)
client.das.getAssetProof(assetId)
client.das.getAssetsBatch(assetIds)
```

#### Enhanced Transactions
```typescript
client.enhancedTransactions.parseTransaction(signature)
client.enhancedTransactions.parseTransactions(signatures)
client.enhancedTransactions.parseTransactionHistory(address)
```

### Privacy APIs (v5.2.0+)

#### Stealth Addresses
```typescript
client.stealthAddress.generateStealthAddress(recipientViewKey)
client.stealthAddress.scanForPayments({ viewKey, startSlot })
```

#### Privacy Pools
```typescript
client.privacyPool.getAvailablePools()
client.privacyPool.calculateOptimalDeposit(amount)
```

#### Transaction Graph Privacy
```typescript
client.transactionGraphPrivacy.analyzeGraphPrivacy(address)
client.transactionGraphPrivacy.findConnectedAddresses(address, depth)
```

#### Privacy Score
```typescript
client.privacyScore.calculatePrivacyScore(address)
client.privacyScore.getPrivacyRecommendations(address)
```

### Additional APIs

#### ZK Compression
```typescript
client.zkCompression.getCompressedAccount(address)
client.zkCompression.getCompressedAccountsByOwner(owner)
client.zkCompression.getCompressedTokenBalances(owner)
client.zkCompression.getValidityProof(hashes)
```

#### Webhooks
```typescript
client.webhook.createWebhook(config)
client.webhook.getWebhooks()
client.webhook.editWebhook(webhookId, config)
client.webhook.deleteWebhook(webhookId)
```

#### Staking
```typescript
client.staking.getStakeAccounts(address)
client.staking.createStakeTransaction(params)
client.staking.createUnstakeTransaction(params)
```

#### Jupiter
```typescript
client.jupiter.getQuote(params)
client.jupiter.swap(params)
```

#### Jito
```typescript
client.jito.sendBundle(transactions)
client.jito.getBundleStatuses(bundleIds)
```

### WebSocket Subscriptions

```typescript
// Connect to WebSocket
await client.webSocket.connect();

// Subscribe to account changes
client.webSocket.accountSubscribe(accountId, (update) => {
  console.log('Account updated:', update);
});

// Subscribe to program logs
client.webSocket.logsSubscribe(programId, (logs) => {
  console.log('Program logs:', logs);
});

// Subscribe to slot updates
client.webSocket.slotSubscribe((slot) => {
  console.log('New slot:', slot);
});

// Disconnect
client.webSocket.disconnect();
```

## Utilities

### Base58 Encoding
```typescript
import { base58 } from '@selenus/luna-sdk';

const encoded = base58.encode(bytes);
const decoded = base58.decode(encoded);
```

### Lamport Conversions
```typescript
import { lamports } from '@selenus/luna-sdk';

const sol = lamports.lamportsToSol(1_000_000_000); // 1 SOL
const amount = lamports.solToLamports(1.5); // 1,500,000,000
const formatted = lamports.formatSolWithSymbol(1_000_000_000); // "◎ 1.0000"
```

### Retry with Backoff
```typescript
import { retry, retryRpc } from '@selenus/luna-sdk';

const result = await retryRpc(() => client.solana.getBalance(address), {
  maxRetries: 5,
  initialDelay: 1000,
});
```

## Configuration

### Custom Endpoints
```typescript
const client = new LunaHeliusClient('api-key', {
  rpcUrl: 'https://custom-rpc.example.com',
  cluster: 'mainnet-beta',
});
```

### Network Selection
```typescript
// Mainnet (default)
const mainnet = new LunaHeliusClient('api-key');

// Devnet
const devnet = new LunaHeliusClient('api-key', { cluster: 'devnet' });
```

## Error Handling

```typescript
try {
  const result = await client.das.getAsset(assetId);
  if (result.error) {
    console.error('RPC Error:', result.error.message);
  } else {
    console.log('Asset:', result.result);
  }
} catch (error) {
  console.error('Network error:', error);
}
```

## React Native Considerations

This SDK is designed for React Native and includes:
- No Node.js-specific dependencies
- Compatible with React Native's JavaScript engine
- Works with Hermes and JSC
- Supports Expo and bare React Native projects

## Peer Dependencies

- `@solana/web3.js` >= 1.95.0

## TypeScript Support

Full TypeScript support with comprehensive type definitions:
```typescript
import type { 
  Asset, 
  Transaction, 
  RpcResponse, 
  WebhookConfig,
  PrivacyScore 
} from '@selenus/luna-sdk';
```

## License

MIT © Selenus Labs

## Links

- [Documentation](https://docs.selenus.io/luna-sdk)
- [GitHub](https://github.com/selenus-labs/luna-sdk)
- [Helius API Docs](https://docs.helius.dev)
