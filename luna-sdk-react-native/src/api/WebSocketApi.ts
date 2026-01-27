/**
 * WebSocketApi - WebSocket Subscriptions
 * 
 * Real-time subscriptions for accounts, slots, and transactions
 */

import type { LunaHeliusClient, Cluster } from '../LunaHeliusClient';

export type SubscriptionCallback<T> = (data: T) => void;

export interface Subscription {
  id: number;
  unsubscribe: () => void;
}

export class WebSocketApi {
  private ws: WebSocket | null = null;
  private subscriptionId = 0;
  private subscriptions = new Map<number, SubscriptionCallback<any>>();
  private pendingRequests = new Map<string, { resolve: (id: number) => void; reject: (err: Error) => void }>();
  private reconnectAttempts = 0;
  private maxReconnectAttempts = 5;
  private isConnecting = false;

  constructor(private readonly client: LunaHeliusClient) {}

  private getWsUrl(): string {
    const cluster = (this.client as any).cluster;
    const apiKey = (this.client as any).apiKey;
    
    switch (cluster) {
      case 'mainnet-beta':
        return `wss://mainnet.helius-rpc.com/?api-key=${apiKey}`;
      case 'devnet':
        return `wss://devnet.helius-rpc.com/?api-key=${apiKey}`;
      case 'testnet':
        return `wss://testnet.helius-rpc.com/?api-key=${apiKey}`;
      default:
        return `wss://mainnet.helius-rpc.com/?api-key=${apiKey}`;
    }
  }

  /** Connect to WebSocket */
  async connect(): Promise<void> {
    if (this.ws?.readyState === WebSocket.OPEN) return;
    if (this.isConnecting) return;

    this.isConnecting = true;

    return new Promise((resolve, reject) => {
      try {
        this.ws = new WebSocket(this.getWsUrl());

        this.ws.onopen = () => {
          this.isConnecting = false;
          this.reconnectAttempts = 0;
          resolve();
        };

        this.ws.onmessage = (event) => {
          try {
            const data = JSON.parse(event.data);
            
            // Handle subscription confirmation
            if (data.result !== undefined && data.id) {
              const pending = this.pendingRequests.get(data.id);
              if (pending) {
                pending.resolve(data.result);
                this.pendingRequests.delete(data.id);
              }
            }
            
            // Handle subscription notification
            if (data.method && data.params) {
              const subId = data.params.subscription;
              const callback = this.subscriptions.get(subId);
              if (callback) {
                callback(data.params.result);
              }
            }
          } catch (e) {
            console.error('WebSocket message parse error:', e);
          }
        };

        this.ws.onerror = (error) => {
          this.isConnecting = false;
          reject(new Error('WebSocket connection error'));
        };

        this.ws.onclose = () => {
          this.isConnecting = false;
          this.handleReconnect();
        };
      } catch (error) {
        this.isConnecting = false;
        reject(error);
      }
    });
  }

  private handleReconnect(): void {
    if (this.reconnectAttempts < this.maxReconnectAttempts) {
      this.reconnectAttempts++;
      const delay = Math.min(1000 * Math.pow(2, this.reconnectAttempts), 30000);
      setTimeout(() => this.connect(), delay);
    }
  }

  /** Disconnect WebSocket */
  disconnect(): void {
    if (this.ws) {
      this.ws.close();
      this.ws = null;
    }
    this.subscriptions.clear();
    this.pendingRequests.clear();
  }

  private async subscribe(method: string, params: any[]): Promise<number> {
    await this.connect();

    return new Promise((resolve, reject) => {
      const id = Date.now().toString();
      
      this.pendingRequests.set(id, { resolve, reject });

      const message = JSON.stringify({
        jsonrpc: '2.0',
        id,
        method,
        params,
      });

      this.ws?.send(message);

      // Timeout after 30 seconds
      setTimeout(() => {
        if (this.pendingRequests.has(id)) {
          this.pendingRequests.delete(id);
          reject(new Error('Subscription timeout'));
        }
      }, 30000);
    });
  }

  private async unsubscribe(method: string, subscriptionId: number): Promise<boolean> {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) return false;

    return new Promise((resolve) => {
      const id = Date.now().toString();

      const message = JSON.stringify({
        jsonrpc: '2.0',
        id,
        method,
        params: [subscriptionId],
      });

      this.ws?.send(message);
      this.subscriptions.delete(subscriptionId);
      resolve(true);
    });
  }

  /** Subscribe to account changes */
  async accountSubscribe(
    pubkey: string,
    callback: SubscriptionCallback<any>,
    options?: { commitment?: string; encoding?: string }
  ): Promise<Subscription> {
    const params: any[] = [pubkey];
    if (options) {
      params.push({
        commitment: options.commitment ?? 'confirmed',
        encoding: options.encoding ?? 'jsonParsed',
      });
    }

    const subId = await this.subscribe('accountSubscribe', params);
    this.subscriptions.set(subId, callback);

    return {
      id: subId,
      unsubscribe: () => this.unsubscribe('accountUnsubscribe', subId),
    };
  }

  /** Subscribe to logs */
  async logsSubscribe(
    filter: 'all' | 'allWithVotes' | { mentions: string[] },
    callback: SubscriptionCallback<any>,
    options?: { commitment?: string }
  ): Promise<Subscription> {
    const params: any[] = [filter];
    if (options) {
      params.push({ commitment: options.commitment ?? 'confirmed' });
    }

    const subId = await this.subscribe('logsSubscribe', params);
    this.subscriptions.set(subId, callback);

    return {
      id: subId,
      unsubscribe: () => this.unsubscribe('logsUnsubscribe', subId),
    };
  }

  /** Subscribe to program accounts */
  async programSubscribe(
    programId: string,
    callback: SubscriptionCallback<any>,
    options?: { commitment?: string; encoding?: string; filters?: any[] }
  ): Promise<Subscription> {
    const params: any[] = [programId];
    if (options) {
      params.push({
        commitment: options.commitment ?? 'confirmed',
        encoding: options.encoding ?? 'jsonParsed',
        filters: options.filters,
      });
    }

    const subId = await this.subscribe('programSubscribe', params);
    this.subscriptions.set(subId, callback);

    return {
      id: subId,
      unsubscribe: () => this.unsubscribe('programUnsubscribe', subId),
    };
  }

  /** Subscribe to signature status */
  async signatureSubscribe(
    signature: string,
    callback: SubscriptionCallback<any>,
    options?: { commitment?: string }
  ): Promise<Subscription> {
    const params: any[] = [signature];
    if (options) {
      params.push({ commitment: options.commitment ?? 'confirmed' });
    }

    const subId = await this.subscribe('signatureSubscribe', params);
    this.subscriptions.set(subId, callback);

    return {
      id: subId,
      unsubscribe: () => this.unsubscribe('signatureUnsubscribe', subId),
    };
  }

  /** Subscribe to slot changes */
  async slotSubscribe(callback: SubscriptionCallback<any>): Promise<Subscription> {
    const subId = await this.subscribe('slotSubscribe', []);
    this.subscriptions.set(subId, callback);

    return {
      id: subId,
      unsubscribe: () => this.unsubscribe('slotUnsubscribe', subId),
    };
  }

  /** Subscribe to root changes */
  async rootSubscribe(callback: SubscriptionCallback<number>): Promise<Subscription> {
    const subId = await this.subscribe('rootSubscribe', []);
    this.subscriptions.set(subId, callback);

    return {
      id: subId,
      unsubscribe: () => this.unsubscribe('rootUnsubscribe', subId),
    };
  }

  /** Subscribe to slot updates */
  async slotsUpdatesSubscribe(callback: SubscriptionCallback<any>): Promise<Subscription> {
    const subId = await this.subscribe('slotsUpdatesSubscribe', []);
    this.subscriptions.set(subId, callback);

    return {
      id: subId,
      unsubscribe: () => this.unsubscribe('slotsUpdatesUnsubscribe', subId),
    };
  }

  /** Subscribe to vote updates */
  async voteSubscribe(callback: SubscriptionCallback<any>): Promise<Subscription> {
    const subId = await this.subscribe('voteSubscribe', []);
    this.subscriptions.set(subId, callback);

    return {
      id: subId,
      unsubscribe: () => this.unsubscribe('voteUnsubscribe', subId),
    };
  }
}
