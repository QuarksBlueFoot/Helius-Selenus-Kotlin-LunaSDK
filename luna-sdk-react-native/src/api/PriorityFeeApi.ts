/**
 * PriorityFeeApi - Priority Fee Estimation
 * 
 * Get optimal priority fees for fast transaction confirmation
 */

import type { LunaHeliusClient } from '../LunaHeliusClient';
import type { RpcResponse, PriorityFeeEstimate } from '../types';

export interface PriorityFeeOptions {
  accountKeys?: string[];
  includeAllPriorityFeeLevels?: boolean;
  lookbackSlots?: number;
  recommended?: boolean;
}

export class PriorityFeeApi {
  constructor(private readonly client: LunaHeliusClient) {}

  /** Get priority fee estimate */
  async getPriorityFeeEstimate(
    options?: PriorityFeeOptions
  ): Promise<RpcResponse<{ priorityFeeLevels: PriorityFeeEstimate }>> {
    const params: any = {};
    
    if (options?.accountKeys) {
      params.accountKeys = options.accountKeys;
    }
    if (options?.includeAllPriorityFeeLevels !== undefined) {
      params.options = {
        ...params.options,
        includeAllPriorityFeeLevels: options.includeAllPriorityFeeLevels,
      };
    }
    if (options?.lookbackSlots !== undefined) {
      params.options = {
        ...params.options,
        lookbackSlots: options.lookbackSlots,
      };
    }
    if (options?.recommended !== undefined) {
      params.options = {
        ...params.options,
        recommended: options.recommended,
      };
    }

    return this.client.rpcCall('getPriorityFeeEstimate', params);
  }

  /** Get recent priority fees from the network */
  async getRecentPriorityFees(
    accounts?: string[]
  ): Promise<RpcResponse<any[]>> {
    const params = accounts ? [accounts] : [];
    return this.client.rpcCall('getRecentPrioritizationFees', params);
  }

  /** Calculate optimal fee for a transaction */
  async calculateOptimalFee(
    serializedTransaction: string,
    options?: {
      priorityLevel?: 'min' | 'low' | 'medium' | 'high' | 'veryHigh' | 'unsafeMax';
    }
  ): Promise<RpcResponse<{ fee: number; priorityLevel: string }>> {
    // Simulate to get accounts involved
    const simulation = await this.client.rpcCall('simulateTransaction', [
      serializedTransaction,
      { encoding: 'base64' },
    ]);

    if (simulation.error) {
      return { result: null, error: simulation.error };
    }

    // Get priority fee estimate for those accounts
    const accounts = simulation.result?.value?.accounts?.map((a: any) => a?.owner).filter(Boolean) || [];
    
    const feeEstimate = await this.getPriorityFeeEstimate({
      accountKeys: accounts,
      includeAllPriorityFeeLevels: true,
    });

    if (feeEstimate.error || !feeEstimate.result) {
      return { result: null, error: feeEstimate.error };
    }

    const levels = feeEstimate.result.priorityFeeLevels;
    const level = options?.priorityLevel ?? 'medium';
    
    const fee = levels[level as keyof PriorityFeeEstimate] ?? levels.medium;

    return {
      result: { fee, priorityLevel: level },
      error: null,
    };
  }
}
