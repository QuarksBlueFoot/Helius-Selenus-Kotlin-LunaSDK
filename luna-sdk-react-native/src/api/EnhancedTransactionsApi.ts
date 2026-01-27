/**
 * Enhanced Transactions API
 * 
 * Parse and enrich transaction data with human-readable information
 */

import type { LunaHeliusClient } from '../LunaHeliusClient';
import type { RpcResponse, EnhancedTransaction } from '../types';

export class EnhancedTransactionsApi {
  private readonly apiUrl: string;

  constructor(private readonly client: LunaHeliusClient) {
    this.apiUrl = 'https://api.helius.xyz/v0';
  }

  /** Parse a single transaction */
  async parseTransaction(
    signature: string
  ): Promise<RpcResponse<EnhancedTransaction[]>> {
    return this.parseTransactions([signature]);
  }

  /** Parse multiple transactions */
  async parseTransactions(
    signatures: string[]
  ): Promise<RpcResponse<EnhancedTransaction[]>> {
    try {
      const response = await fetch(
        `${this.apiUrl}/transactions?api-key=${(this.client as any).apiKey}`,
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ transactions: signatures }),
        }
      );

      if (!response.ok) {
        return {
          result: null,
          error: { code: response.status, message: response.statusText, data: null },
        };
      }

      const data = await response.json();
      return { result: data, error: null };
    } catch (error) {
      return {
        result: null,
        error: { code: -1, message: (error as Error).message, data: null },
      };
    }
  }

  /** Get transaction history for an address */
  async getTransactionHistory(
    address: string,
    options?: {
      before?: string;
      until?: string;
      limit?: number;
      source?: string;
      type?: string;
    }
  ): Promise<RpcResponse<EnhancedTransaction[]>> {
    try {
      const params = new URLSearchParams();
      params.append('api-key', (this.client as any).apiKey);
      if (options?.before) params.append('before', options.before);
      if (options?.until) params.append('until', options.until);
      if (options?.limit) params.append('limit', options.limit.toString());
      if (options?.source) params.append('source', options.source);
      if (options?.type) params.append('type', options.type);

      const response = await fetch(
        `${this.apiUrl}/addresses/${address}/transactions?${params}`,
        { method: 'GET' }
      );

      if (!response.ok) {
        return {
          result: null,
          error: { code: response.status, message: response.statusText, data: null },
        };
      }

      const data = await response.json();
      return { result: data, error: null };
    } catch (error) {
      return {
        result: null,
        error: { code: -1, message: (error as Error).message, data: null },
      };
    }
  }

  /** Get parsed transaction history for an address */
  async getParsedTransactionHistory(
    address: string,
    options?: {
      before?: string;
      until?: string;
      limit?: number;
    }
  ): Promise<RpcResponse<EnhancedTransaction[]>> {
    return this.getTransactionHistory(address, options);
  }
}
