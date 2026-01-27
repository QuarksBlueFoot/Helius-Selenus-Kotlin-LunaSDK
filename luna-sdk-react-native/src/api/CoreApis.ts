import type { LunaHeliusClient } from '../LunaHeliusClient';
import type { RpcResponse } from '../types';

export class SnsApi {
  constructor(private readonly client: LunaHeliusClient) {}

  async resolveDomain(domain: string): Promise<RpcResponse<string>> {
    // SNS domain resolution
    return this.client.rpcCall('getNameServiceAddress', { domain });
  }

  async getDomains(owner: string): Promise<RpcResponse<any[]>> {
    return this.client.rpcCall('getNameServiceDomains', { owner });
  }
}

export class TokenApi {
  constructor(private readonly client: LunaHeliusClient) {}

  async getTokenMetadata(mint: string): Promise<RpcResponse<any>> {
    return this.client.rpcCall('getAsset', { id: mint });
  }

  async getTokenHolders(mint: string): Promise<RpcResponse<any>> {
    return this.client.rpcCall('getTokenLargestAccounts', [mint]);
  }
}

export class NftApi {
  constructor(private readonly client: LunaHeliusClient) {}

  async getNftMetadata(mint: string): Promise<RpcResponse<any>> {
    return this.client.rpcCall('getAsset', { id: mint });
  }

  async getNftsByOwner(owner: string): Promise<RpcResponse<any>> {
    return this.client.rpcCall('getAssetsByOwner', { ownerAddress: owner });
  }

  async getNftsByCollection(collection: string): Promise<RpcResponse<any>> {
    return this.client.rpcCall('getAssetsByGroup', {
      groupKey: 'collection',
      groupValue: collection,
    });
  }
}

export class SmartTransactionApi {
  constructor(private readonly client: LunaHeliusClient) {}

  async sendSmartTransaction(params: {
    transaction: string;
    lastValidBlockHeight: number;
  }): Promise<RpcResponse<string>> {
    return this.client.rpcCall('sendTransaction', [
      params.transaction,
      {
        skipPreflight: true,
        maxRetries: 0,
      },
    ]);
  }

  async buildOptimizedTransaction(params: {
    instructions: any[];
    payer: string;
    priorityLevel?: 'low' | 'medium' | 'high' | 'veryHigh';
  }): Promise<RpcResponse<any>> {
    // This would require local transaction building
    return {
      result: null,
      error: { code: -1, message: 'Use @solana/web3.js for transaction building', data: null },
    };
  }
}

export class JupiterApi {
  private readonly baseUrl = 'https://quote-api.jup.ag/v6';

  constructor(private readonly client: LunaHeliusClient) {}

  async getQuote(params: {
    inputMint: string;
    outputMint: string;
    amount: number;
    slippageBps?: number;
  }): Promise<RpcResponse<any>> {
    try {
      const url = new URL(`${this.baseUrl}/quote`);
      url.searchParams.set('inputMint', params.inputMint);
      url.searchParams.set('outputMint', params.outputMint);
      url.searchParams.set('amount', params.amount.toString());
      url.searchParams.set('slippageBps', (params.slippageBps ?? 50).toString());

      const response = await fetch(url.toString());
      const data = await response.json();
      
      return { result: data, error: null };
    } catch (error) {
      return {
        result: null,
        error: { code: -1, message: (error as Error).message, data: null },
      };
    }
  }

  async getSwapTransaction(params: {
    quoteResponse: any;
    userPublicKey: string;
  }): Promise<RpcResponse<any>> {
    try {
      const response = await fetch(`${this.baseUrl}/swap`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          quoteResponse: params.quoteResponse,
          userPublicKey: params.userPublicKey,
        }),
      });

      const data = await response.json();
      return { result: data, error: null };
    } catch (error) {
      return {
        result: null,
        error: { code: -1, message: (error as Error).message, data: null },
      };
    }
  }
}

export class JitoApi {
  constructor(private readonly client: LunaHeliusClient) {}

  async sendBundle(transactions: string[]): Promise<RpcResponse<string>> {
    return this.client.rpcCall('sendBundle', [transactions]);
  }

  async getBundleStatuses(bundleIds: string[]): Promise<RpcResponse<any>> {
    return this.client.rpcCall('getBundleStatuses', [bundleIds]);
  }

  async getTipAccounts(): Promise<RpcResponse<string[]>> {
    return this.client.rpcCall('getTipAccounts', []);
  }

  async getInflightBundleStatuses(bundleIds: string[]): Promise<RpcResponse<any>> {
    return this.client.rpcCall('getInflightBundleStatuses', [bundleIds]);
  }

  async simulateBundle(params: {
    encodedTransactions: string[];
    simulationBank?: 'processed' | 'confirmed' | 'finalized';
  }): Promise<RpcResponse<any>> {
    return this.client.rpcCall('simulateBundle', params);
  }
}
