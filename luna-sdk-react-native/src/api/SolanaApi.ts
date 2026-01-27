/**
 * SolanaApi - Standard Solana RPC Methods
 * 
 * Complete coverage of all Solana JSON-RPC methods
 */

import type { LunaHeliusClient } from '../LunaHeliusClient';
import type { RpcResponse, AccountInfo, TransactionSignature } from '../types';

export class SolanaApi {
  constructor(private readonly client: LunaHeliusClient) {}

  /** Get account info for a public key */
  async getAccountInfo(
    pubkey: string,
    options?: {
      commitment?: 'processed' | 'confirmed' | 'finalized';
      encoding?: 'base58' | 'base64' | 'base64+zstd' | 'jsonParsed';
      dataSlice?: { offset: number; length: number };
    }
  ): Promise<RpcResponse<{ value: AccountInfo | null }>> {
    const params: any[] = [pubkey];
    if (options) {
      params.push({
        commitment: options.commitment,
        encoding: options.encoding ?? 'base64',
        dataSlice: options.dataSlice,
      });
    }
    return this.client.rpcCall('getAccountInfo', params);
  }

  /** Get balance in lamports */
  async getBalance(
    pubkey: string,
    commitment?: 'processed' | 'confirmed' | 'finalized'
  ): Promise<RpcResponse<{ value: number }>> {
    const params: any[] = [pubkey];
    if (commitment) {
      params.push({ commitment });
    }
    return this.client.rpcCall('getBalance', params);
  }

  /** Get recent blockhash */
  async getLatestBlockhash(
    commitment?: 'processed' | 'confirmed' | 'finalized'
  ): Promise<RpcResponse<{ value: { blockhash: string; lastValidBlockHeight: number } }>> {
    const params = commitment ? [{ commitment }] : [];
    return this.client.rpcCall('getLatestBlockhash', params);
  }

  /** Get block information */
  async getBlock(
    slot: number,
    options?: {
      encoding?: 'json' | 'jsonParsed' | 'base58' | 'base64';
      transactionDetails?: 'full' | 'accounts' | 'signatures' | 'none';
      rewards?: boolean;
      commitment?: 'confirmed' | 'finalized';
      maxSupportedTransactionVersion?: number;
    }
  ): Promise<RpcResponse<any>> {
    const params: any[] = [slot];
    if (options) {
      params.push(options);
    }
    return this.client.rpcCall('getBlock', params);
  }

  /** Get block height */
  async getBlockHeight(
    commitment?: 'processed' | 'confirmed' | 'finalized'
  ): Promise<RpcResponse<number>> {
    const params = commitment ? [{ commitment }] : [];
    return this.client.rpcCall('getBlockHeight', params);
  }

  /** Get block time */
  async getBlockTime(slot: number): Promise<RpcResponse<number | null>> {
    return this.client.rpcCall('getBlockTime', [slot]);
  }

  /** Get cluster nodes */
  async getClusterNodes(): Promise<RpcResponse<any[]>> {
    return this.client.rpcCall('getClusterNodes', []);
  }

  /** Get epoch info */
  async getEpochInfo(
    commitment?: 'processed' | 'confirmed' | 'finalized'
  ): Promise<RpcResponse<any>> {
    const params = commitment ? [{ commitment }] : [];
    return this.client.rpcCall('getEpochInfo', params);
  }

  /** Get epoch schedule */
  async getEpochSchedule(): Promise<RpcResponse<any>> {
    return this.client.rpcCall('getEpochSchedule', []);
  }

  /** Get fee for message */
  async getFeeForMessage(
    message: string,
    commitment?: 'processed' | 'confirmed' | 'finalized'
  ): Promise<RpcResponse<{ value: number | null }>> {
    const params: any[] = [message];
    if (commitment) {
      params.push({ commitment });
    }
    return this.client.rpcCall('getFeeForMessage', params);
  }

  /** Get genesis hash */
  async getGenesisHash(): Promise<RpcResponse<string>> {
    return this.client.rpcCall('getGenesisHash', []);
  }

  /** Get health status */
  async getHealth(): Promise<RpcResponse<string>> {
    return this.client.rpcCall('getHealth', []);
  }

  /** Get highest snapshot slot */
  async getHighestSnapshotSlot(): Promise<RpcResponse<{ full: number; incremental?: number }>> {
    return this.client.rpcCall('getHighestSnapshotSlot', []);
  }

  /** Get identity pubkey */
  async getIdentity(): Promise<RpcResponse<{ identity: string }>> {
    return this.client.rpcCall('getIdentity', []);
  }

  /** Get inflation governor */
  async getInflationGovernor(
    commitment?: 'processed' | 'confirmed' | 'finalized'
  ): Promise<RpcResponse<any>> {
    const params = commitment ? [{ commitment }] : [];
    return this.client.rpcCall('getInflationGovernor', params);
  }

  /** Get inflation rate */
  async getInflationRate(): Promise<RpcResponse<any>> {
    return this.client.rpcCall('getInflationRate', []);
  }

  /** Get inflation reward */
  async getInflationReward(
    addresses: string[],
    options?: {
      commitment?: 'processed' | 'confirmed' | 'finalized';
      epoch?: number;
      minContextSlot?: number;
    }
  ): Promise<RpcResponse<any[]>> {
    const params: any[] = [addresses];
    if (options) {
      params.push(options);
    }
    return this.client.rpcCall('getInflationReward', params);
  }

  /** Get largest accounts */
  async getLargestAccounts(
    options?: {
      commitment?: 'processed' | 'confirmed' | 'finalized';
      filter?: 'circulating' | 'nonCirculating';
    }
  ): Promise<RpcResponse<{ value: any[] }>> {
    const params = options ? [options] : [];
    return this.client.rpcCall('getLargestAccounts', params);
  }

  /** Get leader schedule */
  async getLeaderSchedule(
    slot?: number,
    options?: {
      commitment?: 'processed' | 'confirmed' | 'finalized';
      identity?: string;
    }
  ): Promise<RpcResponse<any>> {
    const params: any[] = slot !== undefined ? [slot] : [null];
    if (options) {
      params.push(options);
    }
    return this.client.rpcCall('getLeaderSchedule', params);
  }

  /** Get max retransmit slot */
  async getMaxRetransmitSlot(): Promise<RpcResponse<number>> {
    return this.client.rpcCall('getMaxRetransmitSlot', []);
  }

  /** Get max shred insert slot */
  async getMaxShredInsertSlot(): Promise<RpcResponse<number>> {
    return this.client.rpcCall('getMaxShredInsertSlot', []);
  }

  /** Get minimum balance for rent exemption */
  async getMinimumBalanceForRentExemption(
    dataLength: number,
    commitment?: 'processed' | 'confirmed' | 'finalized'
  ): Promise<RpcResponse<number>> {
    const params: any[] = [dataLength];
    if (commitment) {
      params.push({ commitment });
    }
    return this.client.rpcCall('getMinimumBalanceForRentExemption', params);
  }

  /** Get multiple accounts */
  async getMultipleAccounts(
    pubkeys: string[],
    options?: {
      commitment?: 'processed' | 'confirmed' | 'finalized';
      encoding?: 'base58' | 'base64' | 'base64+zstd' | 'jsonParsed';
      dataSlice?: { offset: number; length: number };
    }
  ): Promise<RpcResponse<{ value: (AccountInfo | null)[] }>> {
    const params: any[] = [pubkeys];
    if (options) {
      params.push(options);
    }
    return this.client.rpcCall('getMultipleAccounts', params);
  }

  /** Get program accounts */
  async getProgramAccounts(
    programId: string,
    options?: {
      commitment?: 'processed' | 'confirmed' | 'finalized';
      encoding?: 'base58' | 'base64' | 'base64+zstd' | 'jsonParsed';
      dataSlice?: { offset: number; length: number };
      filters?: any[];
      withContext?: boolean;
    }
  ): Promise<RpcResponse<any>> {
    const params: any[] = [programId];
    if (options) {
      params.push(options);
    }
    return this.client.rpcCall('getProgramAccounts', params);
  }

  /** Get recent performance samples */
  async getRecentPerformanceSamples(limit?: number): Promise<RpcResponse<any[]>> {
    const params = limit !== undefined ? [limit] : [];
    return this.client.rpcCall('getRecentPerformanceSamples', params);
  }

  /** Get recent prioritization fees */
  async getRecentPrioritizationFees(
    accounts?: string[]
  ): Promise<RpcResponse<any[]>> {
    const params = accounts ? [accounts] : [];
    return this.client.rpcCall('getRecentPrioritizationFees', params);
  }

  /** Get signatures for address */
  async getSignaturesForAddress(
    address: string,
    options?: {
      limit?: number;
      before?: string;
      until?: string;
      commitment?: 'confirmed' | 'finalized';
      minContextSlot?: number;
    }
  ): Promise<RpcResponse<TransactionSignature[]>> {
    const params: any[] = [address];
    if (options) {
      params.push(options);
    }
    return this.client.rpcCall('getSignaturesForAddress', params);
  }

  /** Get signature statuses */
  async getSignatureStatuses(
    signatures: string[],
    options?: { searchTransactionHistory?: boolean }
  ): Promise<RpcResponse<{ value: any[] }>> {
    const params: any[] = [signatures];
    if (options) {
      params.push(options);
    }
    return this.client.rpcCall('getSignatureStatuses', params);
  }

  /** Get slot */
  async getSlot(
    commitment?: 'processed' | 'confirmed' | 'finalized'
  ): Promise<RpcResponse<number>> {
    const params = commitment ? [{ commitment }] : [];
    return this.client.rpcCall('getSlot', params);
  }

  /** Get slot leader */
  async getSlotLeader(
    commitment?: 'processed' | 'confirmed' | 'finalized'
  ): Promise<RpcResponse<string>> {
    const params = commitment ? [{ commitment }] : [];
    return this.client.rpcCall('getSlotLeader', params);
  }

  /** Get slot leaders */
  async getSlotLeaders(
    startSlot: number,
    limit: number
  ): Promise<RpcResponse<string[]>> {
    return this.client.rpcCall('getSlotLeaders', [startSlot, limit]);
  }

  /** Get stake activation */
  async getStakeActivation(
    pubkey: string,
    options?: {
      commitment?: 'processed' | 'confirmed' | 'finalized';
      epoch?: number;
      minContextSlot?: number;
    }
  ): Promise<RpcResponse<any>> {
    const params: any[] = [pubkey];
    if (options) {
      params.push(options);
    }
    return this.client.rpcCall('getStakeActivation', params);
  }

  /** Get stake minimum delegation */
  async getStakeMinimumDelegation(
    commitment?: 'processed' | 'confirmed' | 'finalized'
  ): Promise<RpcResponse<{ value: number }>> {
    const params = commitment ? [{ commitment }] : [];
    return this.client.rpcCall('getStakeMinimumDelegation', params);
  }

  /** Get supply */
  async getSupply(
    options?: {
      commitment?: 'processed' | 'confirmed' | 'finalized';
      excludeNonCirculatingAccountsList?: boolean;
    }
  ): Promise<RpcResponse<{ value: any }>> {
    const params = options ? [options] : [];
    return this.client.rpcCall('getSupply', params);
  }

  /** Get token account balance */
  async getTokenAccountBalance(
    pubkey: string,
    commitment?: 'processed' | 'confirmed' | 'finalized'
  ): Promise<RpcResponse<{ value: any }>> {
    const params: any[] = [pubkey];
    if (commitment) {
      params.push({ commitment });
    }
    return this.client.rpcCall('getTokenAccountBalance', params);
  }

  /** Get token accounts by delegate */
  async getTokenAccountsByDelegate(
    delegate: string,
    filter: { mint?: string; programId?: string },
    options?: {
      commitment?: 'processed' | 'confirmed' | 'finalized';
      encoding?: 'base64' | 'jsonParsed';
      dataSlice?: { offset: number; length: number };
    }
  ): Promise<RpcResponse<{ value: any[] }>> {
    const params: any[] = [delegate, filter];
    if (options) {
      params.push(options);
    }
    return this.client.rpcCall('getTokenAccountsByDelegate', params);
  }

  /** Get token accounts by owner */
  async getTokenAccountsByOwner(
    owner: string,
    filter: { mint?: string; programId?: string },
    options?: {
      commitment?: 'processed' | 'confirmed' | 'finalized';
      encoding?: 'base64' | 'jsonParsed';
      dataSlice?: { offset: number; length: number };
    }
  ): Promise<RpcResponse<{ value: any[] }>> {
    const params: any[] = [owner, filter];
    if (options) {
      params.push(options);
    }
    return this.client.rpcCall('getTokenAccountsByOwner', params);
  }

  /** Get token largest accounts */
  async getTokenLargestAccounts(
    mint: string,
    commitment?: 'processed' | 'confirmed' | 'finalized'
  ): Promise<RpcResponse<{ value: any[] }>> {
    const params: any[] = [mint];
    if (commitment) {
      params.push({ commitment });
    }
    return this.client.rpcCall('getTokenLargestAccounts', params);
  }

  /** Get token supply */
  async getTokenSupply(
    mint: string,
    commitment?: 'processed' | 'confirmed' | 'finalized'
  ): Promise<RpcResponse<{ value: any }>> {
    const params: any[] = [mint];
    if (commitment) {
      params.push({ commitment });
    }
    return this.client.rpcCall('getTokenSupply', params);
  }

  /** Get transaction */
  async getTransaction(
    signature: string,
    options?: {
      commitment?: 'confirmed' | 'finalized';
      encoding?: 'json' | 'jsonParsed' | 'base58' | 'base64';
      maxSupportedTransactionVersion?: number;
    }
  ): Promise<RpcResponse<any>> {
    const params: any[] = [signature];
    if (options) {
      params.push(options);
    }
    return this.client.rpcCall('getTransaction', params);
  }

  /** Get transaction count */
  async getTransactionCount(
    commitment?: 'processed' | 'confirmed' | 'finalized'
  ): Promise<RpcResponse<number>> {
    const params = commitment ? [{ commitment }] : [];
    return this.client.rpcCall('getTransactionCount', params);
  }

  /** Get version */
  async getVersion(): Promise<RpcResponse<{ 'solana-core': string; 'feature-set': number }>> {
    return this.client.rpcCall('getVersion', []);
  }

  /** Get vote accounts */
  async getVoteAccounts(
    options?: {
      commitment?: 'processed' | 'confirmed' | 'finalized';
      votePubkey?: string;
      keepUnstakedDelinquents?: boolean;
      delinquentSlotDistance?: number;
    }
  ): Promise<RpcResponse<any>> {
    const params = options ? [options] : [];
    return this.client.rpcCall('getVoteAccounts', params);
  }

  /** Check if blockhash is valid */
  async isBlockhashValid(
    blockhash: string,
    options?: {
      commitment?: 'processed' | 'confirmed' | 'finalized';
      minContextSlot?: number;
    }
  ): Promise<RpcResponse<{ value: boolean }>> {
    const params: any[] = [blockhash];
    if (options) {
      params.push(options);
    }
    return this.client.rpcCall('isBlockhashValid', params);
  }

  /** Minimum ledger slot */
  async minimumLedgerSlot(): Promise<RpcResponse<number>> {
    return this.client.rpcCall('minimumLedgerSlot', []);
  }

  /** Request airdrop (devnet/testnet only) */
  async requestAirdrop(
    pubkey: string,
    lamports: number,
    commitment?: 'processed' | 'confirmed' | 'finalized'
  ): Promise<RpcResponse<string>> {
    const params: any[] = [pubkey, lamports];
    if (commitment) {
      params.push({ commitment });
    }
    return this.client.rpcCall('requestAirdrop', params);
  }

  /** Send transaction */
  async sendTransaction(
    signedTransaction: string,
    options?: {
      encoding?: 'base58' | 'base64';
      skipPreflight?: boolean;
      preflightCommitment?: 'processed' | 'confirmed' | 'finalized';
      maxRetries?: number;
      minContextSlot?: number;
    }
  ): Promise<RpcResponse<string>> {
    const params: any[] = [signedTransaction];
    if (options) {
      params.push(options);
    }
    return this.client.rpcCall('sendTransaction', params);
  }

  /** Simulate transaction */
  async simulateTransaction(
    transaction: string,
    options?: {
      commitment?: 'processed' | 'confirmed' | 'finalized';
      encoding?: 'base58' | 'base64';
      sigVerify?: boolean;
      replaceRecentBlockhash?: boolean;
      accounts?: {
        encoding?: 'base64' | 'base64+zstd' | 'jsonParsed';
        addresses: string[];
      };
      minContextSlot?: number;
    }
  ): Promise<RpcResponse<any>> {
    const params: any[] = [transaction];
    if (options) {
      params.push(options);
    }
    return this.client.rpcCall('simulateTransaction', params);
  }
}
