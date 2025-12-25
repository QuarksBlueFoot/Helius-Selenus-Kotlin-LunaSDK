/**
 * WebhookApi - Webhook Management
 * 
 * Create, update, and manage Helius webhooks
 */

import type { LunaHeliusClient } from '../LunaHeliusClient';
import type { RpcResponse, Webhook, WebhookResponse } from '../types';

export type WebhookType = 'enhanced' | 'enhancedDevnet' | 'raw' | 'rawDevnet' | 'discord';
export type TransactionType = 
  | 'UNKNOWN'
  | 'NFT_BID'
  | 'NFT_BID_CANCELLED'
  | 'NFT_LISTING'
  | 'NFT_CANCEL_LISTING'
  | 'NFT_SALE'
  | 'NFT_MINT'
  | 'NFT_AUCTION_CREATED'
  | 'NFT_AUCTION_UPDATED'
  | 'NFT_AUCTION_CANCELLED'
  | 'NFT_PARTICIPATION_REWARD'
  | 'NFT_MINT_REJECTED'
  | 'CREATE_STORE'
  | 'WHITELIST_CREATOR'
  | 'ADD_TO_WHITELIST'
  | 'REMOVE_FROM_WHITELIST'
  | 'AUCTION_MANAGER_CLAIM_BID'
  | 'EMPTY_PAYMENT_ACCOUNT'
  | 'UPDATE_PRIMARY_SALE_METADATA'
  | 'ADD_TOKEN_TO_VAULT'
  | 'ACTIVATE_VAULT'
  | 'INIT_VAULT'
  | 'INIT_BANK'
  | 'INIT_STAKE'
  | 'MERGE_STAKE'
  | 'SPLIT_STAKE'
  | 'SET_BANK_FLAGS'
  | 'SET_VAULT_LOCK'
  | 'UPDATE_VAULT_OWNER'
  | 'UPDATE_BANK_MANAGER'
  | 'RECORD_RARITY_POINTS'
  | 'ADD_RARITIES_TO_BANK'
  | 'INIT_FARM'
  | 'INIT_FARMER'
  | 'REFRESH_FARMER'
  | 'UPDATE_FARM'
  | 'AUTHORIZE_FUNDER'
  | 'DEAUTHORIZE_FUNDER'
  | 'FUND_REWARD'
  | 'CANCEL_REWARD'
  | 'LOCK_REWARD'
  | 'PAYOUT'
  | 'VALIDATE_SAFETY_DEPOSIT_BOX_V2'
  | 'SET_AUTHORITY'
  | 'INIT_AUCTION_MANAGER_V2'
  | 'UPDATE_EXTERNAL_PRICE_ACCOUNT'
  | 'AUCTION_HOUSE_CREATE'
  | 'CLOSE_ESCROW_ACCOUNT'
  | 'WITHDRAW'
  | 'DEPOSIT'
  | 'TRANSFER'
  | 'BURN'
  | 'BURN_NFT'
  | 'PLATFORM_FEE'
  | 'LOAN'
  | 'REPAY_LOAN'
  | 'ADD_TO_POOL'
  | 'REMOVE_FROM_POOL'
  | 'CLOSE_POSITION'
  | 'UNLABELED'
  | 'CLOSE_ACCOUNT'
  | 'WITHDRAW_GEM'
  | 'DEPOSIT_GEM'
  | 'STAKE_TOKEN'
  | 'UNSTAKE_TOKEN'
  | 'STAKE_SOL'
  | 'UNSTAKE_SOL'
  | 'CLAIM_REWARDS'
  | 'BUY_SUBSCRIPTION'
  | 'SWAP'
  | 'INIT_SWAP'
  | 'CANCEL_SWAP'
  | 'REJECT_SWAP'
  | 'INITIALIZE_ACCOUNT'
  | 'TOKEN_MINT'
  | 'CREATE_APPARAISAL'
  | 'FUSE'
  | 'DEPOSIT_FRACTIONAL_POOL'
  | 'FRACTIONALIZE'
  | 'CREATE_RAFFLE'
  | 'BUY_TICKETS'
  | 'UPDATE_ITEM'
  | 'LIST_ITEM'
  | 'DELIST_ITEM'
  | 'ADD_ITEM'
  | 'CLOSE_ITEM'
  | 'BUY_ITEM'
  | 'FILL_ORDER'
  | 'UPDATE_ORDER'
  | 'CREATE_ORDER'
  | 'CLOSE_ORDER'
  | 'CANCEL_ORDER'
  | 'KICK_ITEM'
  | 'UPGRADE_FOX'
  | 'UPGRADE_FOX_REQUEST'
  | 'LOAN_FOX'
  | 'BORROW_FOX'
  | 'SWITCH_FOX_REQUEST'
  | 'SWITCH_FOX'
  | 'CREATE_ESCROW'
  | 'ACCEPT_REQUEST_ARTIST'
  | 'CANCEL_ESCROW'
  | 'ACCEPT_ESCROW_ARTIST'
  | 'ACCEPT_ESCROW_USER'
  | 'PLACE_BET'
  | 'PLACE_SOL_BET'
  | 'CREATE_BET'
  | 'COMPRESSED_NFT_MINT'
  | 'COMPRESSED_NFT_TRANSFER'
  | 'COMPRESSED_NFT_BURN'
  | 'COMPRESSED_NFT_REDEEM'
  | 'COMPRESSED_NFT_CANCEL_REDEEM'
  | 'COMPRESSED_NFT_DELEGATE'
  | 'COMPRESSED_NFT_VERIFY_CREATOR'
  | 'COMPRESSED_NFT_UNVERIFY_CREATOR'
  | 'COMPRESSED_NFT_VERIFY_COLLECTION'
  | 'COMPRESSED_NFT_UNVERIFY_COLLECTION'
  | 'COMPRESSED_NFT_SET_AND_VERIFY_COLLECTION'
  | 'CREATE_MERKLE_TREE'
  | 'EXECUTE_TRANSACTION'
  | 'APPROVE_TRANSACTION'
  | 'ACTIVATE_TRANSACTION'
  | 'CREATE_TRANSACTION'
  | 'CANCEL_TRANSACTION'
  | 'REJECT_TRANSACTION'
  | 'ADD_INSTRUCTION'
  | 'FINALIZE_VOTE'
  | 'RELINQUISH_VOTE'
  | 'CANCEL_PROPOSAL'
  | 'SIGN_OFF_PROPOSAL'
  | 'CAST_VOTE'
  | 'CREATE_GOVERNANCE'
  | 'CREATE_PROGRAM_GOVERNANCE'
  | 'CREATE_MINT_GOVERNANCE'
  | 'CREATE_TOKEN_GOVERNANCE'
  | 'CREATE_PROPOSAL'
  | 'ADD_SIGNATORY'
  | 'REMOVE_SIGNATORY'
  | 'INSERT_TRANSACTION'
  | 'REMOVE_TRANSACTION'
  | 'SET_GOVERNANCE_DELEGATE'
  | 'CREATE_REALM'
  | 'DEPOSIT_GOVERNING_TOKENS'
  | 'WITHDRAW_GOVERNING_TOKENS'
  | 'CREATE_TOKEN_OWNER_RECORD'
  | 'UPDATE_PROGRAM_METADATA'
  | 'REQUEST_PNFT_MIGRATION'
  | 'START_PNFT_MIGRATION'
  | 'MIGRATE_TO_PNFT'
  | 'ANY';

export interface CreateWebhookParams {
  webhookURL: string;
  transactionTypes: TransactionType[];
  accountAddresses: string[];
  webhookType: WebhookType;
  authHeader?: string;
  txnStatus?: 'all' | 'success' | 'failed';
  encoding?: 'base58' | 'base64';
}

export class WebhookApi {
  private readonly apiUrl: string;

  constructor(private readonly client: LunaHeliusClient) {
    this.apiUrl = 'https://api.helius.xyz/v0';
  }

  /** Get all webhooks */
  async getAllWebhooks(): Promise<RpcResponse<Webhook[]>> {
    try {
      const response = await fetch(
        `${this.apiUrl}/webhooks?api-key=${(this.client as any).apiKey}`,
        { method: 'GET' }
      );

      if (!response.ok) {
        return {
          result: null,
          error: { code: response.status, message: response.statusText, data: null },
        };
      }

      const data = await response.json() as Webhook[];
      return { result: data, error: null };
    } catch (error) {
      return {
        result: null,
        error: { code: -1, message: (error as Error).message, data: null },
      };
    }
  }

  /** Get a specific webhook by ID */
  async getWebhook(webhookId: string): Promise<RpcResponse<Webhook>> {
    try {
      const response = await fetch(
        `${this.apiUrl}/webhooks/${webhookId}?api-key=${(this.client as any).apiKey}`,
        { method: 'GET' }
      );

      if (!response.ok) {
        return {
          result: null,
          error: { code: response.status, message: response.statusText, data: null },
        };
      }

      const data = await response.json() as Webhook;
      return { result: data, error: null };
    } catch (error) {
      return {
        result: null,
        error: { code: -1, message: (error as Error).message, data: null },
      };
    }
  }

  /** Create a new webhook */
  async createWebhook(params: CreateWebhookParams): Promise<RpcResponse<WebhookResponse>> {
    try {
      const response = await fetch(
        `${this.apiUrl}/webhooks?api-key=${(this.client as any).apiKey}`,
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

      const data = await response.json() as WebhookResponse;
      return { result: data, error: null };
    } catch (error) {
      return {
        result: null,
        error: { code: -1, message: (error as Error).message, data: null },
      };
    }
  }

  /** Update an existing webhook */
  async updateWebhook(
    webhookId: string,
    params: Partial<CreateWebhookParams>
  ): Promise<RpcResponse<Webhook>> {
    try {
      const response = await fetch(
        `${this.apiUrl}/webhooks/${webhookId}?api-key=${(this.client as any).apiKey}`,
        {
          method: 'PUT',
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

      const data = await response.json() as Webhook;
      return { result: data, error: null };
    } catch (error) {
      return {
        result: null,
        error: { code: -1, message: (error as Error).message, data: null },
      };
    }
  }

  /** Delete a webhook */
  async deleteWebhook(webhookId: string): Promise<RpcResponse<{ success: boolean }>> {
    try {
      const response = await fetch(
        `${this.apiUrl}/webhooks/${webhookId}?api-key=${(this.client as any).apiKey}`,
        { method: 'DELETE' }
      );

      if (!response.ok) {
        return {
          result: null,
          error: { code: response.status, message: response.statusText, data: null },
        };
      }

      return { result: { success: true }, error: null };
    } catch (error) {
      return {
        result: null,
        error: { code: -1, message: (error as Error).message, data: null },
      };
    }
  }

  /** Append addresses to a webhook */
  async appendAddressesToWebhook(
    webhookId: string,
    addresses: string[]
  ): Promise<RpcResponse<Webhook>> {
    const webhook = await this.getWebhook(webhookId);
    if (webhook.error || !webhook.result) {
      return webhook;
    }

    const currentAddresses = webhook.result.accountAddresses || [];
    const newAddresses = [...new Set([...currentAddresses, ...addresses])];

    return this.updateWebhook(webhookId, { accountAddresses: newAddresses });
  }

  /** Remove addresses from a webhook */
  async removeAddressesFromWebhook(
    webhookId: string,
    addresses: string[]
  ): Promise<RpcResponse<Webhook>> {
    const webhook = await this.getWebhook(webhookId);
    if (webhook.error || !webhook.result) {
      return webhook;
    }

    const addressSet = new Set(addresses);
    const newAddresses = (webhook.result.accountAddresses || []).filter(
      (addr) => !addressSet.has(addr)
    );

    return this.updateWebhook(webhookId, { accountAddresses: newAddresses });
  }
}
