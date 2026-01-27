/**
 * LunaHeliusClient - React Native
 * 
 * The main entry point for all Helius Solana APIs.
 * Provides a unified interface matching the Kotlin SDK functionality.
 */

import { RpcResponse, RpcError } from './types';

export enum Cluster {
  MAINNET = 'mainnet-beta',
  DEVNET = 'devnet',
  TESTNET = 'testnet',
}

export interface LunaClientConfig {
  apiKey: string;
  cluster?: Cluster;
  timeout?: number;
  maxRetries?: number;
}

export class LunaHeliusClient {
  private readonly apiKey: string;
  private readonly cluster: Cluster;
  private readonly timeout: number;
  private readonly maxRetries: number;
  private readonly baseUrl: string;

  // API instances (lazy initialized)
  private _solana?: SolanaApi;
  private _das?: DasApi;
  private _webhook?: WebhookApi;
  private _mint?: MintApi;
  private _priority?: PriorityFeeApi;
  private _zk?: ZkCompressionApi;
  private _enhanced?: EnhancedTransactionsApi;
  private _ws?: WebSocketApi;
  private _staking?: StakingApi;
  private _sns?: SnsApi;
  private _token?: TokenApi;
  private _nft?: NftApi;
  private _smartTransaction?: SmartTransactionApi;
  private _jupiter?: JupiterApi;
  private _jito?: JitoApi;
  private _stealthAddress?: StealthAddressApi;
  private _privacyPool?: PrivacyPoolApi;
  private _txGraphPrivacy?: TransactionGraphPrivacyApi;
  private _shieldedPattern?: ShieldedPatternApi;
  private _privacyScore?: PrivacyScoreEngineApi;

  constructor(config: LunaClientConfig) {
    this.apiKey = config.apiKey;
    this.cluster = config.cluster ?? Cluster.MAINNET;
    this.timeout = config.timeout ?? 30000;
    this.maxRetries = config.maxRetries ?? 3;

    this.baseUrl = this.getBaseUrl();
  }

  private getBaseUrl(): string {
    switch (this.cluster) {
      case Cluster.MAINNET:
        return 'https://mainnet.helius-rpc.com';
      case Cluster.DEVNET:
        return 'https://devnet.helius-rpc.com';
      case Cluster.TESTNET:
        return 'https://testnet.helius-rpc.com';
      default:
        return 'https://mainnet.helius-rpc.com';
    }
  }

  /**
   * Make an RPC call to Helius.
   */
  async rpcCall<T = any>(
    method: string,
    params: any = []
  ): Promise<RpcResponse<T>> {
    const url = `${this.baseUrl}/?api-key=${this.apiKey}`;

    const payload = {
      jsonrpc: '2.0',
      id: Date.now().toString(),
      method,
      params,
    };

    let lastError: Error | null = null;

    for (let attempt = 0; attempt < this.maxRetries; attempt++) {
      try {
        const response = await fetch(url, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify(payload),
        });

        if (response.status === 429) {
          // Rate limited - wait and retry
          await this.sleep(1000 * (attempt + 1));
          continue;
        }

        const json = await response.json();

        if (json.error) {
          return {
            result: null,
            error: {
              code: json.error.code,
              message: json.error.message,
              data: json.error.data,
            },
          };
        }

        return { result: json.result, error: null };
      } catch (error) {
        lastError = error as Error;
        await this.sleep(500 * (attempt + 1));
      }
    }

    return {
      result: null,
      error: {
        code: -1,
        message: lastError?.message ?? 'Unknown error',
        data: null,
      },
    };
  }

  private sleep(ms: number): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, ms));
  }

  // ========== API Accessors ==========

  /** Standard Solana RPC methods */
  get solana(): SolanaApi {
    if (!this._solana) {
      this._solana = new SolanaApi(this);
    }
    return this._solana;
  }

  /** Digital Asset Standard API for NFTs and tokens */
  get das(): DasApi {
    if (!this._das) {
      this._das = new DasApi(this);
    }
    return this._das;
  }

  /** Webhook management API */
  get webhook(): WebhookApi {
    if (!this._webhook) {
      this._webhook = new WebhookApi(this);
    }
    return this._webhook;
  }

  /** NFT minting API */
  get mint(): MintApi {
    if (!this._mint) {
      this._mint = new MintApi(this);
    }
    return this._mint;
  }

  /** Priority fee estimation API */
  get priority(): PriorityFeeApi {
    if (!this._priority) {
      this._priority = new PriorityFeeApi(this);
    }
    return this._priority;
  }

  /** ZK Compression API */
  get zk(): ZkCompressionApi {
    if (!this._zk) {
      this._zk = new ZkCompressionApi(this);
    }
    return this._zk;
  }

  /** Enhanced transaction parsing API */
  get enhanced(): EnhancedTransactionsApi {
    if (!this._enhanced) {
      this._enhanced = new EnhancedTransactionsApi(this);
    }
    return this._enhanced;
  }

  /** WebSocket subscriptions API */
  get ws(): WebSocketApi {
    if (!this._ws) {
      this._ws = new WebSocketApi(this);
    }
    return this._ws;
  }

  /** Staking API */
  get staking(): StakingApi {
    if (!this._staking) {
      this._staking = new StakingApi(this);
    }
    return this._staking;
  }

  /** Solana Name Service API */
  get sns(): SnsApi {
    if (!this._sns) {
      this._sns = new SnsApi(this);
    }
    return this._sns;
  }

  /** Token operations API */
  get token(): TokenApi {
    if (!this._token) {
      this._token = new TokenApi(this);
    }
    return this._token;
  }

  /** NFT operations API */
  get nft(): NftApi {
    if (!this._nft) {
      this._nft = new NftApi(this);
    }
    return this._nft;
  }

  /** Smart transaction building API */
  get smartTransaction(): SmartTransactionApi {
    if (!this._smartTransaction) {
      this._smartTransaction = new SmartTransactionApi(this);
    }
    return this._smartTransaction;
  }

  /** Jupiter DEX aggregation API */
  get jupiter(): JupiterApi {
    if (!this._jupiter) {
      this._jupiter = new JupiterApi(this);
    }
    return this._jupiter;
  }

  /** Jito MEV protection API */
  get jito(): JitoApi {
    if (!this._jito) {
      this._jito = new JitoApi(this);
    }
    return this._jito;
  }

  // ========== Privacy APIs (v5.2.0) ==========

  /** Stealth address generation and management */
  get stealthAddress(): StealthAddressApi {
    if (!this._stealthAddress) {
      this._stealthAddress = new StealthAddressApi(this);
    }
    return this._stealthAddress;
  }

  /** Privacy pool mixing operations */
  get privacyPool(): PrivacyPoolApi {
    if (!this._privacyPool) {
      this._privacyPool = new PrivacyPoolApi(this);
    }
    return this._privacyPool;
  }

  /** Transaction graph privacy analysis */
  get txGraphPrivacy(): TransactionGraphPrivacyApi {
    if (!this._txGraphPrivacy) {
      this._txGraphPrivacy = new TransactionGraphPrivacyApi(this);
    }
    return this._txGraphPrivacy;
  }

  /** Shielded pattern detection */
  get shieldedPattern(): ShieldedPatternApi {
    if (!this._shieldedPattern) {
      this._shieldedPattern = new ShieldedPatternApi(this);
    }
    return this._shieldedPattern;
  }

  /** Privacy score calculation */
  get privacyScore(): PrivacyScoreEngineApi {
    if (!this._privacyScore) {
      this._privacyScore = new PrivacyScoreEngineApi(this);
    }
    return this._privacyScore;
  }
}

// Import API classes (forward declarations for lazy loading)
import { SolanaApi } from './api/SolanaApi';
import { DasApi } from './api/DasApi';
import { WebhookApi } from './api/WebhookApi';
import { MintApi } from './api/MintApi';
import { PriorityFeeApi } from './api/PriorityFeeApi';
import { ZkCompressionApi } from './api/ZkCompressionApi';
import { EnhancedTransactionsApi } from './api/EnhancedTransactionsApi';
import { WebSocketApi } from './api/WebSocketApi';
import { StakingApi } from './api/StakingApi';
import { SnsApi, TokenApi, NftApi, SmartTransactionApi, JupiterApi, JitoApi } from './api/CoreApis';
import { 
  StealthAddressApi, 
  PrivacyPoolApi, 
  TransactionGraphPrivacyApi, 
  ShieldedPatternApi, 
  PrivacyScoreEngineApi 
} from './api/privacy';

