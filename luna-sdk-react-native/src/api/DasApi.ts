/**
 * DasApi - Digital Asset Standard API
 * 
 * Complete coverage of Helius DAS API for NFTs and tokens
 */

import type { LunaHeliusClient } from '../LunaHeliusClient';
import type { RpcResponse, Asset, AssetProof, AssetList } from '../types';

export interface GetAssetParams {
  id: string;
  displayOptions?: {
    showFungible?: boolean;
    showInscription?: boolean;
    showCollectionMetadata?: boolean;
    showUnverifiedCollections?: boolean;
  };
}

export interface GetAssetsByOwnerParams {
  ownerAddress: string;
  page?: number;
  limit?: number;
  cursor?: string;
  before?: string;
  after?: string;
  sortBy?: { sortBy: 'created' | 'updated' | 'recentAction' | 'none'; sortDirection: 'asc' | 'desc' };
  displayOptions?: {
    showFungible?: boolean;
    showInscription?: boolean;
    showCollectionMetadata?: boolean;
    showUnverifiedCollections?: boolean;
    showNativeBalance?: boolean;
    showGrandTotal?: boolean;
  };
}

export interface SearchAssetsParams {
  page?: number;
  limit?: number;
  cursor?: string;
  before?: string;
  after?: string;
  creatorAddress?: string;
  creatorVerified?: boolean;
  ownerAddress?: string;
  delegate?: string;
  frozen?: boolean;
  supply?: number;
  supplyMint?: string;
  compressed?: boolean;
  compressible?: boolean;
  royaltyTargetType?: 'creators' | 'fanout' | 'single';
  royaltyTarget?: string;
  royaltyAmount?: number;
  burnt?: boolean;
  sortBy?: { sortBy: 'created' | 'updated' | 'recentAction' | 'none'; sortDirection: 'asc' | 'desc' };
  interface?: string;
  jsonUri?: string;
  grouping?: [string, string];
  tokenType?: 'fungible' | 'nonFungible' | 'regularNft' | 'compressedNft' | 'all';
  displayOptions?: {
    showFungible?: boolean;
    showInscription?: boolean;
    showCollectionMetadata?: boolean;
    showUnverifiedCollections?: boolean;
    showNativeBalance?: boolean;
    showGrandTotal?: boolean;
  };
}

export class DasApi {
  constructor(private readonly client: LunaHeliusClient) {}

  /** Get a single asset by ID */
  async getAsset(params: GetAssetParams): Promise<RpcResponse<Asset>> {
    return this.client.rpcCall('getAsset', params);
  }

  /** Get multiple assets by IDs */
  async getAssets(ids: string[]): Promise<RpcResponse<Asset[]>> {
    return this.client.rpcCall('getAssets', { ids });
  }

  /** Get asset proof for compressed NFT */
  async getAssetProof(id: string): Promise<RpcResponse<AssetProof>> {
    return this.client.rpcCall('getAssetProof', { id });
  }

  /** Get asset proofs for multiple compressed NFTs */
  async getAssetProofs(ids: string[]): Promise<RpcResponse<{ [key: string]: AssetProof }>> {
    return this.client.rpcCall('getAssetProofs', { ids });
  }

  /** Get assets by authority */
  async getAssetsByAuthority(params: {
    authorityAddress: string;
    page?: number;
    limit?: number;
    cursor?: string;
    before?: string;
    after?: string;
    sortBy?: { sortBy: string; sortDirection: 'asc' | 'desc' };
    displayOptions?: any;
  }): Promise<RpcResponse<AssetList>> {
    return this.client.rpcCall('getAssetsByAuthority', params);
  }

  /** Get assets by creator */
  async getAssetsByCreator(params: {
    creatorAddress: string;
    onlyVerified?: boolean;
    page?: number;
    limit?: number;
    cursor?: string;
    before?: string;
    after?: string;
    sortBy?: { sortBy: string; sortDirection: 'asc' | 'desc' };
    displayOptions?: any;
  }): Promise<RpcResponse<AssetList>> {
    return this.client.rpcCall('getAssetsByCreator', params);
  }

  /** Get assets by group (collection) */
  async getAssetsByGroup(params: {
    groupKey: string;
    groupValue: string;
    page?: number;
    limit?: number;
    cursor?: string;
    before?: string;
    after?: string;
    sortBy?: { sortBy: string; sortDirection: 'asc' | 'desc' };
    displayOptions?: any;
  }): Promise<RpcResponse<AssetList>> {
    return this.client.rpcCall('getAssetsByGroup', params);
  }

  /** Get assets by owner */
  async getAssetsByOwner(params: GetAssetsByOwnerParams): Promise<RpcResponse<AssetList>> {
    return this.client.rpcCall('getAssetsByOwner', params);
  }

  /** Get signatures for an asset */
  async getAssetSignatures(params: {
    id: string;
    page?: number;
    limit?: number;
    cursor?: string;
    before?: string;
    after?: string;
    sortDirection?: 'asc' | 'desc';
  }): Promise<RpcResponse<any>> {
    return this.client.rpcCall('getAssetSignatures', params);
  }

  /** Get token accounts */
  async getTokenAccounts(params: {
    owner?: string;
    mint?: string;
    page?: number;
    limit?: number;
    cursor?: string;
    before?: string;
    after?: string;
    displayOptions?: any;
  }): Promise<RpcResponse<any>> {
    return this.client.rpcCall('getTokenAccounts', params);
  }

  /** Get NFT editions */
  async getNftEditions(params: {
    mint: string;
    page?: number;
    limit?: number;
  }): Promise<RpcResponse<any>> {
    return this.client.rpcCall('getNftEditions', params);
  }

  /** Search assets with filters */
  async searchAssets(params: SearchAssetsParams): Promise<RpcResponse<AssetList>> {
    return this.client.rpcCall('searchAssets', params);
  }
}
