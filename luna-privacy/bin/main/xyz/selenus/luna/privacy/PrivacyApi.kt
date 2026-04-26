// Phase 2 monolith-split status (v5.7.3 — extraction in progress, in batches):
//
// EXTRACTED to this module so far:
//   ✅ PrivateBroadcastApi      (file: PrivateBroadcastApi.kt)
//   ✅ RpcRotationApi           (file: RpcRotationApi.kt)
//   ✅ FingerprintObfuscationApi (file: FingerprintObfuscationApi.kt)
//
// STILL IN MONOLITH (luna-core/LunaHeliusClient.kt):
//   PrivacyApi, ZkPrivacyApi, ConfidentialTransactionApi, StealthAddressApi,
//   PrivacyPoolApi, TransactionGraphPrivacyApi, ShieldedPatternApi,
//   PrivacyScoreEngineApi, ConfidentialTokenApi, PrivacyCombinatorApi,
//   AdvancedStealthApi, PrivateTransactionsApi, UniversalPrivacyApi.
//
// The remaining classes form a cross-call graph (PrivacyScoreEngineApi calls
// 5 other privacy classes, PrivacyCombinatorApi calls 8+ others, etc.) so
// the next batch should extract them as a coherent group. The 2 historical
// Gradle cycles (ZkCompressionExtended via PrivacyPoolApi/ShieldedPatternApi,
// and TransactionIntelligenceApi via TransactionGraphPrivacyApi) are
// resolved as of v5.7.2 — see BUILD_STATUS.md for the resolution.
package xyz.selenus.luna.privacy
