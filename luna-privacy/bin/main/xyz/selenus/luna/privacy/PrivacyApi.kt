// Phase 2 Phase 2 monolith-split placeholder.
//
// This module's source files (PrivacyApi, ZkPrivacyApi, ConfidentialTransactionApi,
// StealthAddressApi, PrivacyPoolApi, TransactionGraphPrivacyApi, ShieldedPatternApi,
// PrivacyScoreEngineApi, ConfidentialTokenApi, PrivateBroadcastApi,
// FingerprintObfuscationApi, RpcRotationApi, PrivacyCombinatorApi,
// AdvancedStealthApi, PrivateTransactionsApi, UniversalPrivacyApi) have NOT
// yet been extracted from luna-core/LunaHeliusClient.kt.
//
// The extraction must be done as one atomic transaction across both
// :luna-privacy and :luna-innovations because of cyclic call graphs:
// - WalletCorrelationApi (innovations) calls client.privacy.analyzeAddressLinkage
// - ZkPrivacyApi (privacy) calls client.privacy.analyzeWalletPrivacy
// - StrategyEngineApi (innovations) calls client.jupiter.getQuote
// - many more
//
// A partial extraction (e.g. PrivacyApi only) leaves the monolith with broken
// references because in-monolith inner classes still reference the removed
// `privacy.xxx` field. See README and Phase 2 plan for the full extraction
// schedule.
package xyz.selenus.luna.privacy
