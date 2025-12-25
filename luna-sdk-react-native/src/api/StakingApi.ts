// Stub APIs for remaining modules
import type { LunaHeliusClient } from '../LunaHeliusClient';
import type { RpcResponse } from '../types';

export class StakingApi {
  constructor(private readonly client: LunaHeliusClient) {}

  async getStakeAccounts(owner: string): Promise<RpcResponse<any>> {
    return this.client.rpcCall('getProgramAccounts', [
      'Stake11111111111111111111111111111111111111',
      {
        encoding: 'jsonParsed',
        filters: [
          { memcmp: { offset: 12, bytes: owner } }
        ]
      }
    ]);
  }

  async createStakeTransaction(params: {
    fromAddress: string;
    toAddress: string;
    amount: number;
  }): Promise<RpcResponse<any>> {
    return this.client.rpcCall('createStakeTransaction', params);
  }

  async createUnstakeTransaction(params: {
    fromAddress: string;
    stakeAccount: string;
  }): Promise<RpcResponse<any>> {
    return this.client.rpcCall('createUnstakeTransaction', params);
  }
}
