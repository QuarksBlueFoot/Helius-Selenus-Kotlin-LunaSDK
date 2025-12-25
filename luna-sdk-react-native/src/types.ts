/**
 * LunaSDK Types - React Native
 * 
 * Core type definitions matching the Kotlin SDK
 */

// ========== Core Types ==========

export interface RpcResponse<T> {
  result: T | null;
  error: RpcError | null;
}

export interface RpcError {
  code: number;
  message: string;
  data: any;
}

export interface RpcRequest<T> {
  jsonrpc: string;
  id: string;
  method: string;
  params: T;
}

// ========== Account Types ==========

export interface AccountInfo {
  lamports: number;
  owner: string;
  data: string | [string, string];
  executable: boolean;
  rentEpoch: number;
  space: number;
}

export interface TokenBalance {
  mint: string;
  owner: string;
  amount: string;
  decimals: number;
  uiAmount: number;
  uiAmountString: string;
}

// ========== Transaction Types ==========

export interface TransactionSignature {
  signature: string;
  slot: number;
  err: any | null;
  memo: string | null;
  blockTime: number | null;
  confirmationStatus: 'processed' | 'confirmed' | 'finalized';
}

export interface EnhancedTransaction {
  signature: string;
  type: string;
  source: string;
  fee: number;
  feePayer: string;
  slot: number;
  timestamp: number;
  nativeTransfers: NativeTransfer[];
  tokenTransfers: TokenTransfer[];
  accountData: AccountData[];
  instructions: ParsedInstruction[];
  events: TransactionEvent;
}

export interface NativeTransfer {
  fromUserAccount: string;
  toUserAccount: string;
  amount: number;
}

export interface TokenTransfer {
  fromUserAccount: string;
  toUserAccount: string;
  fromTokenAccount: string;
  toTokenAccount: string;
  tokenAmount: number;
  mint: string;
  tokenStandard: string;
}

export interface AccountData {
  account: string;
  nativeBalanceChange: number;
  tokenBalanceChanges: TokenBalanceChange[];
}

export interface TokenBalanceChange {
  userAccount: string;
  tokenAccount: string;
  mint: string;
  rawTokenAmount: RawTokenAmount;
}

export interface RawTokenAmount {
  tokenAmount: string;
  decimals: number;
}

export interface ParsedInstruction {
  programId: string;
  data: string;
  accounts: string[];
  innerInstructions: ParsedInstruction[];
}

export interface TransactionEvent {
  nft?: NftEvent;
  swap?: SwapEvent;
  compressed?: CompressedNftEvent[];
}

export interface NftEvent {
  description: string;
  type: string;
  source: string;
  amount: number;
  fee: number;
  feePayer: string;
  signature: string;
  slot: number;
  timestamp: number;
  saleType: string;
  buyer: string;
  seller: string;
  nfts: NftDetails[];
}

export interface NftDetails {
  mint: string;
  tokenStandard: string;
}

export interface SwapEvent {
  nativeInput: NativeSwapInput | null;
  nativeOutput: NativeSwapOutput | null;
  tokenInputs: TokenSwapInput[];
  tokenOutputs: TokenSwapOutput[];
  tokenFees: TokenFee[];
  nativeFees: NativeFee[];
  innerSwaps: InnerSwap[];
}

export interface NativeSwapInput {
  account: string;
  amount: string;
}

export interface NativeSwapOutput {
  account: string;
  amount: string;
}

export interface TokenSwapInput {
  userAccount: string;
  tokenAccount: string;
  mint: string;
  rawTokenAmount: RawTokenAmount;
}

export interface TokenSwapOutput {
  userAccount: string;
  tokenAccount: string;
  mint: string;
  rawTokenAmount: RawTokenAmount;
}

export interface TokenFee {
  userAccount: string;
  tokenAccount: string;
  mint: string;
  rawTokenAmount: RawTokenAmount;
}

export interface NativeFee {
  account: string;
  amount: string;
}

export interface InnerSwap {
  tokenInputs: TokenSwapInput[];
  tokenOutputs: TokenSwapOutput[];
  tokenFees: TokenFee[];
  nativeFees: NativeFee[];
  programInfo: ProgramInfo;
}

export interface ProgramInfo {
  source: string;
  account: string;
  programName: string;
  instructionName: string;
}

export interface CompressedNftEvent {
  type: string;
  treeId: string;
  assetId: string;
  leafIndex: number;
  instructionIndex: number;
  innerInstructionIndex: number | null;
  newLeafOwner: string;
  oldLeafOwner: string | null;
}

// ========== DAS Types ==========

export interface Asset {
  interface: string;
  id: string;
  content: AssetContent;
  authorities: Authority[];
  compression: Compression;
  grouping: Grouping[];
  royalty: Royalty;
  creators: Creator[];
  ownership: Ownership;
  supply: Supply | null;
  mutable: boolean;
  burnt: boolean;
  token_info?: TokenInfo;
  inscription?: Inscription;
}

export interface AssetContent {
  $schema: string;
  json_uri: string;
  files: ContentFile[];
  metadata: Metadata;
  links: Links;
}

export interface ContentFile {
  uri: string;
  cdn_uri?: string;
  mime: string;
}

export interface Metadata {
  name: string;
  symbol: string;
  description?: string;
  attributes?: Attribute[];
}

export interface Attribute {
  trait_type: string;
  value: string | number;
}

export interface Links {
  image?: string;
  external_url?: string;
  animation_url?: string;
}

export interface Authority {
  address: string;
  scopes: string[];
}

export interface Compression {
  eligible: boolean;
  compressed: boolean;
  data_hash: string;
  creator_hash: string;
  asset_hash: string;
  tree: string;
  seq: number;
  leaf_id: number;
}

export interface Grouping {
  group_key: string;
  group_value: string;
}

export interface Royalty {
  royalty_model: string;
  target: string | null;
  percent: number;
  basis_points: number;
  primary_sale_happened: boolean;
  locked: boolean;
}

export interface Creator {
  address: string;
  share: number;
  verified: boolean;
}

export interface Ownership {
  frozen: boolean;
  delegated: boolean;
  delegate: string | null;
  ownership_model: string;
  owner: string;
}

export interface Supply {
  print_max_supply: number;
  print_current_supply: number;
  edition_nonce: number | null;
}

export interface TokenInfo {
  balance: number;
  supply: number;
  decimals: number;
  token_program: string;
  mint_authority?: string;
  freeze_authority?: string;
}

export interface Inscription {
  order: number;
  size: number;
  contentType: string;
  encoding: string;
  validationHash: string;
  inscriptionDataAccount: string;
}

export interface AssetProof {
  root: string;
  proof: string[];
  node_index: number;
  leaf: string;
  tree_id: string;
}

export interface AssetList {
  total: number;
  limit: number;
  page?: number;
  cursor?: string;
  items: Asset[];
}

// ========== Webhook Types ==========

export interface Webhook {
  webhookID: string;
  wallet: string;
  webhookURL: string;
  transactionTypes: string[];
  accountAddresses: string[];
  webhookType: 'enhanced' | 'enhancedDevnet' | 'raw' | 'rawDevnet' | 'discord';
  authHeader?: string;
  txnStatus?: 'all' | 'success' | 'failed';
  encoding?: 'base58' | 'base64';
}

export interface WebhookResponse {
  webhookID: string;
}

// ========== Priority Fee Types ==========

export interface PriorityFeeEstimate {
  min: number;
  low: number;
  medium: number;
  high: number;
  veryHigh: number;
  unsafeMax: number;
}

export interface PriorityFeeResponse {
  priorityFeeLevels: PriorityFeeEstimate;
}

// ========== ZK Compression Types ==========

export interface CompressedAccount {
  hash: string;
  address: string;
  data: CompressedAccountData;
  owner: string;
  lamports: number;
  tree: string;
  leafIndex: number;
  seq: number;
  slotCreated: number;
}

export interface CompressedAccountData {
  discriminator: number[];
  data: number[];
  dataHash: string;
}

export interface CompressedAccountProof {
  hash: string;
  root: string;
  proof: string[];
  leafIndex: number;
  tree: string;
  rootSeq: number;
}

export interface ValidityProof {
  compressedProof: {
    a: number[];
    b: number[];
    c: number[];
  };
  roots: string[];
  rootIndices: number[];
  leafIndices: number[];
  leaves: string[];
  merkleTrees: string[];
  nullifierQueues: string[];
}

// ========== Staking Types ==========

export interface StakeAccount {
  pubkey: string;
  lamports: number;
  voter: string;
  state: 'activating' | 'active' | 'deactivating' | 'inactive';
  activationEpoch: number;
  deactivationEpoch: number;
}

// ========== Jupiter Types ==========

export interface JupiterQuote {
  inputMint: string;
  inAmount: string;
  outputMint: string;
  outAmount: string;
  otherAmountThreshold: string;
  swapMode: string;
  slippageBps: number;
  platformFee: PlatformFee | null;
  priceImpactPct: string;
  routePlan: RoutePlanStep[];
  contextSlot: number;
  timeTaken: number;
}

export interface PlatformFee {
  amount: string;
  feeBps: number;
}

export interface RoutePlanStep {
  swapInfo: SwapInfo;
  percent: number;
}

export interface SwapInfo {
  ammKey: string;
  label: string;
  inputMint: string;
  outputMint: string;
  inAmount: string;
  outAmount: string;
  feeAmount: string;
  feeMint: string;
}

export interface JupiterSwapResponse {
  swapTransaction: string;
  lastValidBlockHeight: number;
  prioritizationFeeLamports: number;
}

// ========== Jito Types ==========

export interface BundleStatus {
  bundleId: string;
  transactions: string[];
  slot: number;
  confirmationStatus: 'processed' | 'confirmed' | 'finalized';
}

export interface TipAccount {
  address: string;
  balance: number;
}

// ========== Privacy Types (v5.2.0) ==========

export interface StealthAddress {
  ephemeralPubkey: string;
  stealthAddress: string;
  viewTag: string;
}

export interface PrivacyPoolInfo {
  poolAddress: string;
  denomination: number;
  totalDeposits: number;
  anonymitySetSize: number;
  lastActivity: number;
}

export interface PrivacyScore {
  address: string;
  overallScore: number;
  addressReuseScore: number;
  mixingScore: number;
  timingScore: number;
  amountPrivacyScore: number;
  recommendations: string[];
}

export interface GraphPrivacyAnalysis {
  address: string;
  clusteringRisk: number;
  linkedAddresses: string[];
  commonInputs: number;
  changeAddressPatterns: number;
  privacyLevel: 'LOW' | 'MEDIUM' | 'HIGH' | 'EXCELLENT';
}

export interface ShieldedTransactionPattern {
  isShielded: boolean;
  shieldingMethod: string;
  confidenceLevel: number;
  estimatedPrivacyGain: number;
}
