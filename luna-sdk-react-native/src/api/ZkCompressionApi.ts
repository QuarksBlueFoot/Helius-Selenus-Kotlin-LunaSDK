/**
 * ZkCompressionApi - ZK Compression API
 * 
 * Compressed accounts and tokens for 98% cost reduction
 */

import type { LunaHeliusClient } from '../LunaHeliusClient';
import type { RpcResponse, CompressedAccount, CompressedAccountProof, ValidityProof } from '../types';

export class ZkCompressionApi {
  constructor(private readonly client: LunaHeliusClient) {}

  /** Get compressed account by hash */
  async getCompressedAccount(hash: string): Promise<RpcResponse<CompressedAccount>> {
    return this.client.rpcCall('getCompressedAccount', { hash });
  }

  /** Get compressed accounts by owner */
  async getCompressedAccountsByOwner(owner: string): Promise<RpcResponse<{ items: CompressedAccount[] }>> {
    return this.client.rpcCall('getCompressedAccountsByOwner', { owner });
  }

  /** Get compressed account balance */
  async getCompressedBalance(hashOrAddress: string): Promise<RpcResponse<{ value: number }>> {
    return this.client.rpcCall('getCompressedBalance', { hashOrAddress });
  }

  /** Get combined compressed balance by owner */
  async getCompressedBalanceByOwner(owner: string): Promise<RpcResponse<{ value: number }>> {
    return this.client.rpcCall('getCompressedBalanceByOwner', { owner });
  }

  /** Get compressed account proof */
  async getCompressedAccountProof(hashOrAddress: string): Promise<RpcResponse<CompressedAccountProof>> {
    return this.client.rpcCall('getCompressedAccountProof', { hashOrAddress });
  }

  /** Get multiple compressed account proofs */
  async getMultipleCompressedAccountProofs(
    hashes: string[]
  ): Promise<RpcResponse<CompressedAccountProof[]>> {
    return this.client.rpcCall('getMultipleCompressedAccountProofs', { hashes });
  }

  /** Get validity proof for transaction */
  async getValidityProof(params: {
    hashes?: string[];
    newAddresses?: string[];
    newAddressesWithTrees?: Array<{ address: string; tree: string }>;
  }): Promise<RpcResponse<ValidityProof>> {
    return this.client.rpcCall('getValidityProof', params);
  }

  /** Get compression signatures for account */
  async getCompressionSignaturesForAccount(hash: string): Promise<RpcResponse<any>> {
    return this.client.rpcCall('getCompressionSignaturesForAccount', { hash });
  }

  /** Get compression signatures for address */
  async getCompressionSignaturesForAddress(address: string): Promise<RpcResponse<any>> {
    return this.client.rpcCall('getCompressionSignaturesForAddress', { address });
  }

  /** Get compression signatures for owner */
  async getCompressionSignaturesForOwner(owner: string): Promise<RpcResponse<any>> {
    return this.client.rpcCall('getCompressionSignaturesForOwner', { owner });
  }

  /** Get compressed token accounts by owner */
  async getCompressedTokenAccountsByOwner(owner: string): Promise<RpcResponse<any>> {
    return this.client.rpcCall('getCompressedTokenAccountsByOwner', { owner });
  }

  /** Get compressed token accounts by delegate */
  async getCompressedTokenAccountsByDelegate(delegate: string): Promise<RpcResponse<any>> {
    return this.client.rpcCall('getCompressedTokenAccountsByDelegate', { delegate });
  }

  /** Get compressed token balances by owner */
  async getCompressedTokenBalancesByOwner(owner: string): Promise<RpcResponse<any>> {
    return this.client.rpcCall('getCompressedTokenBalancesByOwner', { owner });
  }

  /** Get compressed mint token holders */
  async getCompressedMintTokenHolders(mint: string): Promise<RpcResponse<any>> {
    return this.client.rpcCall('getCompressedMintTokenHolders', { mint });
  }

  /** Get multiple compressed accounts */
  async getMultipleCompressedAccounts(hashes: string[]): Promise<RpcResponse<CompressedAccount[]>> {
    return this.client.rpcCall('getMultipleCompressedAccounts', { hashes });
  }

  /** Get new address proof */
  async getMultipleNewAddressProofs(addresses: string[]): Promise<RpcResponse<any>> {
    return this.client.rpcCall('getMultipleNewAddressProofs', { addresses });
  }

  /** Get indexer health status */
  async getIndexerHealth(): Promise<RpcResponse<string>> {
    return this.client.rpcCall('getIndexerHealth', {});
  }

  /** Get indexer slot */
  async getIndexerSlot(): Promise<RpcResponse<number>> {
    return this.client.rpcCall('getIndexerSlot', {});
  }

  /** Get latest compression signatures */
  async getLatestCompressionSignatures(limit?: number): Promise<RpcResponse<any>> {
    return this.client.rpcCall('getLatestCompressionSignatures', { limit: limit ?? 100 });
  }

  /** Get latest non-voting signatures */
  async getLatestNonVotingSignatures(limit?: number): Promise<RpcResponse<any>> {
    return this.client.rpcCall('getLatestNonVotingSignatures', { limit: limit ?? 100 });
  }

  /** Get transaction with compression info */
  async getTransactionWithCompressionInfo(signature: string): Promise<RpcResponse<any>> {
    return this.client.rpcCall('getTransactionWithCompressionInfo', { signature });
  }
}
