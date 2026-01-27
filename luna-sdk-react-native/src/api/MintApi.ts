/**
 * MintApi - NFT Minting API
 * 
 * Mint compressed and standard NFTs
 */

import type { LunaHeliusClient } from '../LunaHeliusClient';
import type { RpcResponse } from '../types';

export interface MintCompressedNftParams {
  name: string;
  symbol: string;
  uri: string;
  owner: string;
  collection?: string;
  creators?: Array<{ address: string; share: number; verified?: boolean }>;
  sellerFeeBasisPoints?: number;
  delegate?: string;
  isMutable?: boolean;
}

export interface MintResponse {
  signature: string;
  minted: boolean;
  assetId?: string;
}

export class MintApi {
  private readonly apiUrl: string;

  constructor(private readonly client: LunaHeliusClient) {
    this.apiUrl = 'https://api.helius.xyz/v1';
  }

  /** Mint a compressed NFT */
  async mintCompressedNft(params: MintCompressedNftParams): Promise<RpcResponse<MintResponse>> {
    try {
      const response = await fetch(
        `${this.apiUrl}/mint-compressed-nft?api-key=${(this.client as any).apiKey}`,
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(params),
        }
      );

      if (!response.ok) {
        return {
          result: null,
          error: { code: response.status, message: response.statusText, data: null },
        };
      }

      const data = await response.json() as MintResponse;
      return { result: data, error: null };
    } catch (error) {
      return {
        result: null,
        error: { code: -1, message: (error as Error).message, data: null },
      };
    }
  }

  /** Delegate a collection for compressed NFT minting */
  async delegateCollection(params: {
    collectionMint: string;
    updateAuthority: string;
  }): Promise<RpcResponse<{ signature: string }>> {
    try {
      const response = await fetch(
        `${this.apiUrl}/delegate-collection?api-key=${(this.client as any).apiKey}`,
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(params),
        }
      );

      if (!response.ok) {
        return {
          result: null,
          error: { code: response.status, message: response.statusText, data: null },
        };
      }

      const data = await response.json() as { signature: string };
      return { result: data, error: null };
    } catch (error) {
      return {
        result: null,
        error: { code: -1, message: (error as Error).message, data: null },
      };
    }
  }

  /** Revoke collection delegation */
  async revokeCollection(params: {
    collectionMint: string;
    updateAuthority: string;
  }): Promise<RpcResponse<{ signature: string }>> {
    try {
      const response = await fetch(
        `${this.apiUrl}/revoke-collection?api-key=${(this.client as any).apiKey}`,
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(params),
        }
      );

      if (!response.ok) {
        return {
          result: null,
          error: { code: response.status, message: response.statusText, data: null },
        };
      }

      const data = await response.json() as { signature: string };
      return { result: data, error: null };
    } catch (error) {
      return {
        result: null,
        error: { code: -1, message: (error as Error).message, data: null },
      };
    }
  }
}
