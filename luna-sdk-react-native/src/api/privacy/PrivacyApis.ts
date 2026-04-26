/**
 * Privacy APIs - v5.7.0
 *
 * Real cryptographic stealth addresses + ECDH for Solana wallets, mirroring
 * the JVM `xyz.selenus.luna.keys.StealthAddress` toolkit.
 *
 * Built on @noble/curves (audited pure-JS Ed25519 + X25519) and @noble/hashes
 * (audited SHA-512). No native deps — works under React Native, Node, and
 * browsers.
 *
 * The previous v5.2.0 implementation was a fail-loud stub that threw on
 * every call (replaced earlier this session because the v5.1 incarnation
 * silently returned hardcoded fake values). This v5.7 release ships a real
 * implementation — see `xyz.selenus.luna.keys.StealthAddress` for the
 * JVM-side equivalent and the protocol math.
 */

import type { LunaHeliusClient } from '../../LunaHeliusClient';
import type {
  RpcResponse,
  StealthAddress as StealthAddressDto,
  PrivacyScore,
  GraphPrivacyAnalysis,
  ShieldedTransactionPattern,
} from '../../types';

import { ed25519, x25519 } from '@noble/curves/ed25519';
import { sha512 } from '@noble/hashes/sha512';
import { mod } from '@noble/curves/abstract/modular';
import bs58 from 'bs58';

// ── Constants ────────────────────────────────────────────────────────

/** Domain separator for stealth-scalar derivation (matches the JVM side). */
const STEALTH_DOMAIN = 'luna-stealth-v1';

/**
 * Group order of the Ed25519 prime-order subgroup. Same as the JVM-side
 * `Ed25519Derive.ORDER_L`. Used to reduce the SHA-512 hash mod L.
 */
const L = 7237005577332262213973186563042994240857116359379907606001950938285454250989n;

/**
 * Ed25519 base point in compressed form. @noble/curves exposes the point
 * via ed25519.ExtendedPoint.BASE; we encode lazily.
 */
const BASE_POINT = ed25519.ExtendedPoint.BASE;

// ── Public API ───────────────────────────────────────────────────────

/**
 * Stealth meta-address — what the recipient publishes so senders can address
 * them privately. Matches `xyz.selenus.luna.keys.StealthAddress.MetaAddress`.
 */
export interface MetaAddress {
  /** Recipient's public spending key (32 bytes, base58 or raw). */
  spendingPublicKey: Uint8Array;
  /** Recipient's public viewing key (32 bytes, base58 or raw). */
  viewingPublicKey: Uint8Array;
}

/** Sender's output: the stealth address + the ephemeral pubkey to publish. */
export interface StealthEnvelope {
  /** One-time receiving address (32 bytes). */
  stealthAddress: Uint8Array;
  /** Ephemeral pubkey to publish on-chain (32 bytes). */
  ephemeralPublicKey: Uint8Array;
  /** The shared scalar — exposed for memo encryption (32 bytes). */
  sharedScalar: Uint8Array;
}

/** Recipient's scan match — the address is yours, plus the spending scalar. */
export interface ScanMatch {
  stealthAddress: Uint8Array;
  /**
   * 32-byte Ed25519 scalar `(s + h) mod L`. Sign for the stealth address by
   * supplying this scalar to a primitive that accepts a raw scalar (NOT the
   * standard Ed25519 sign which derives the scalar from a seed).
   */
  spendingScalar: Uint8Array;
}

// ── Stealth address API ──────────────────────────────────────────────

/**
 * Sender derives a one-time stealth address for a recipient meta-address.
 * Generates a fresh ephemeral keypair via @noble/curves's CSPRNG.
 *
 * Matches the JVM `StealthAddress.derive(meta)` exactly — a meta-address
 * derived on the JVM and a stealth address derived in RN are interoperable.
 */
export function deriveStealthAddress(meta: MetaAddress): StealthEnvelope {
  validate32Bytes('spendingPublicKey', meta.spendingPublicKey);
  validate32Bytes('viewingPublicKey', meta.viewingPublicKey);

  // 1. Fresh ephemeral keypair (Ed25519). Use ed25519.utils.randomPrivateKey
  //    which samples 32 bytes from the platform CSPRNG.
  const ephemeralSeed = ed25519.utils.randomPrivateKey();
  const ephemeralPub = ed25519.getPublicKey(ephemeralSeed);

  // 2. shared = X25519(r, V): convert ephemeral seed → X25519 scalar; convert
  //    viewing pubkey → X25519 pubkey via birational map; ECDH.
  const rXScalar = ed25519SeedToX25519Scalar(ephemeralSeed);
  const vXPub = ed25519PublicKeyToX25519(meta.viewingPublicKey);
  const sharedSecret = x25519.getSharedSecret(rXScalar, vXPub);

  // 3. h = H(domain || shared) mod L
  const h = hashToScalar(sharedSecret);

  // 4. P = S + h·G — Ed25519 point arithmetic via @noble/curves
  const S = ed25519.ExtendedPoint.fromHex(meta.spendingPublicKey);
  const hG = BASE_POINT.multiply(h);
  const P = S.add(hG);

  return {
    stealthAddress: P.toRawBytes(),
    ephemeralPublicKey: ephemeralPub,
    sharedScalar: bigintToLeBytes(h, 32),
  };
}

/**
 * Recipient scans for stealth payments by replaying the protocol math with
 * their viewing key. Returns null if the (ephemeral, observedAddress) pair
 * isn't a stealth payment to them; returns a [ScanMatch] when it is.
 *
 * Matches the JVM `StealthAddress.scan(...)` exactly.
 */
export function scanStealthAddress(
  viewingSecretSeed: Uint8Array,
  spendingSecretSeed: Uint8Array,
  ephemeralPublicKey: Uint8Array,
  observedRecipientAddress: Uint8Array
): ScanMatch | null {
  validate32Bytes('viewingSecretSeed', viewingSecretSeed);
  validate32Bytes('spendingSecretSeed', spendingSecretSeed);
  validate32Bytes('ephemeralPublicKey', ephemeralPublicKey);
  validate32Bytes('observedRecipientAddress', observedRecipientAddress);

  // shared = X25519(v, R): symmetric to the sender's X25519(r, V).
  const vXScalar = ed25519SeedToX25519Scalar(viewingSecretSeed);
  const rXPub = ed25519PublicKeyToX25519(ephemeralPublicKey);
  const sharedSecret = x25519.getSharedSecret(vXScalar, rXPub);

  const h = hashToScalar(sharedSecret);

  // candidate_P = S + h·G where S is derived from spendingSecretSeed.
  const spendingPub = ed25519.getPublicKey(spendingSecretSeed);
  const S = ed25519.ExtendedPoint.fromHex(spendingPub);
  const hG = BASE_POINT.multiply(h);
  const candidate = S.add(hG).toRawBytes();

  if (!equalBytes(candidate, observedRecipientAddress)) return null;

  // Recovered spending scalar = (s + h) mod L
  const s = clampedScalarFromSeed(spendingSecretSeed);
  const spendingScalar = mod(s + h, L);
  return {
    stealthAddress: observedRecipientAddress,
    spendingScalar: bigintToLeBytes(spendingScalar, 32),
  };
}

// ── StealthAddressApi (legacy class shape, for the existing Iris client) ──

/**
 * Class-shape wrapper preserved for backward source compatibility with the
 * existing iris-react-native client surface. Prefer the free functions above
 * for new code.
 */
export class StealthAddressApi {
  constructor(private readonly _client: LunaHeliusClient) {}

  /**
   * Generate a stealth address for receiving funds privately.
   *
   * @param recipientSpendingPubkey base58-encoded 32-byte Ed25519 spending pubkey
   * @param recipientViewingPubkey  base58-encoded 32-byte Ed25519 viewing pubkey
   */
  async generateStealthAddress(
    recipientSpendingPubkey: string,
    recipientViewingPubkey: string
  ): Promise<RpcResponse<StealthAddressDto>> {
    const meta: MetaAddress = {
      spendingPublicKey: bs58.decode(recipientSpendingPubkey),
      viewingPublicKey: bs58.decode(recipientViewingPubkey),
    };
    const env = deriveStealthAddress(meta);
    return {
      result: {
        ephemeralPubkey: bs58.encode(env.ephemeralPublicKey),
        stealthAddress: bs58.encode(env.stealthAddress),
        viewTag: bs58.encode(env.sharedScalar.slice(0, 4)), // first 4 bytes = quick-scan tag
      },
      error: null,
    };
  }

  /**
   * Scan an `(ephemeralPubkey, observedAddress)` pair to determine whether
   * it was a stealth payment to you.
   */
  async scanForPayments(params: {
    viewingSecretBase58: string;
    spendingSecretBase58: string;
    ephemeralPublicKeyBase58: string;
    observedAddressBase58: string;
  }): Promise<RpcResponse<{ matched: boolean; spendingScalar: string | null }>> {
    const match = scanStealthAddress(
      bs58.decode(params.viewingSecretBase58),
      bs58.decode(params.spendingSecretBase58),
      bs58.decode(params.ephemeralPublicKeyBase58),
      bs58.decode(params.observedAddressBase58)
    );
    return {
      result: match
        ? { matched: true, spendingScalar: bs58.encode(match.spendingScalar) }
        : { matched: false, spendingScalar: null },
      error: null,
    };
  }
}

// ── Internal: Ed25519 ↔ X25519 birational conversion ─────────────────

/**
 * Convert an Ed25519 32-byte secret seed to the matching X25519 scalar.
 * Both curves derive the working scalar by hashing the seed with SHA-512,
 * taking the first 32 bytes, and applying the same RFC 7748 / RFC 8032
 * clamping. Mirrors the JVM `X25519.ed25519SeedToX25519Scalar`.
 */
function ed25519SeedToX25519Scalar(seed: Uint8Array): Uint8Array {
  const h = sha512(seed);
  const s = h.slice(0, 32);
  s[0] &= 0xf8;
  s[31] &= 0x7f;
  s[31] |= 0x40;
  return s;
}

/**
 * Convert an Ed25519 32-byte public key to the matching X25519 public key
 * via the birational map `u = (1 + y) / (1 - y) mod p`. Mirrors the JVM
 * `X25519.ed25519PublicKeyToX25519`.
 */
function ed25519PublicKeyToX25519(edPub: Uint8Array): Uint8Array {
  validate32Bytes('edPub', edPub);
  // Strip the sign bit from byte 31 to recover canonical y bytes.
  const yBytes = new Uint8Array(edPub);
  yBytes[31] &= 0x7f;
  const y = leBytesToBigint(yBytes);

  const P = (1n << 255n) - 19n;
  if (y >= P) {
    throw new Error('Ed25519 public key encodes a non-canonical y >= p');
  }

  const oneMinusY = mod(1n - y, P);
  if (oneMinusY === 0n) {
    throw new Error('Ed25519 public key is the identity (y=1) — no Montgomery conversion');
  }
  const u = mod((1n + y) * modInverse(oneMinusY, P), P);
  return bigintToLeBytes(u, 32);
}

// ── Internal: SHA-512 → scalar mod L ─────────────────────────────────

function hashToScalar(sharedSecret: Uint8Array): bigint {
  // H(domain || 0x00 || shared)
  const domain = new TextEncoder().encode(STEALTH_DOMAIN);
  const buf = new Uint8Array(domain.length + 1 + sharedSecret.length);
  buf.set(domain, 0);
  buf[domain.length] = 0x00;
  buf.set(sharedSecret, domain.length + 1);
  const digest = sha512(buf);
  return mod(leBytesToBigint(digest), L);
}

function clampedScalarFromSeed(seed: Uint8Array): bigint {
  const h = sha512(seed);
  const s = h.slice(0, 32);
  s[0] &= 0xf8;
  s[31] &= 0x7f;
  s[31] |= 0x40;
  return leBytesToBigint(s);
}

// ── Internal: bigint <-> little-endian bytes ─────────────────────────

function leBytesToBigint(bytes: Uint8Array): bigint {
  let v = 0n;
  for (let i = bytes.length - 1; i >= 0; i--) {
    v = (v << 8n) | BigInt(bytes[i]);
  }
  return v;
}

function bigintToLeBytes(value: bigint, length: number): Uint8Array {
  const out = new Uint8Array(length);
  let v = value;
  for (let i = 0; i < length; i++) {
    out[i] = Number(v & 0xffn);
    v >>= 8n;
  }
  return out;
}

function modInverse(a: bigint, p: bigint): bigint {
  // Fermat's little theorem since p is prime: a^(p-2) mod p.
  return modPow(a, p - 2n, p);
}

function modPow(base: bigint, exp: bigint, mod_: bigint): bigint {
  let result = 1n;
  let b = mod(base, mod_);
  let e = exp;
  while (e > 0n) {
    if (e & 1n) result = mod(result * b, mod_);
    b = mod(b * b, mod_);
    e >>= 1n;
  }
  return result;
}

// ── Validation helpers ───────────────────────────────────────────────

function validate32Bytes(name: string, b: Uint8Array): void {
  if (b.length !== 32) {
    throw new Error(`${name} must be 32 bytes (got ${b.length})`);
  }
}

function equalBytes(a: Uint8Array, b: Uint8Array): boolean {
  if (a.length !== b.length) return false;
  let r = 0;
  for (let i = 0; i < a.length; i++) r |= a[i] ^ b[i];
  return r === 0;
}

// ── Other Privacy APIs (Privacy Pool / Score / Graph — unchanged stubs) ──

/** Privacy pool analysis — placeholder shape preserved for API compat. */
export class PrivacyPoolApi {
  constructor(private readonly _client: LunaHeliusClient) {}

  async getAvailablePools(): Promise<RpcResponse<any[]>> {
    return {
      result: [
        { denomination: 0.1, poolSize: 1000, anonymitySet: 500 },
        { denomination: 1, poolSize: 5000, anonymitySet: 2500 },
        { denomination: 10, poolSize: 2000, anonymitySet: 1000 },
        { denomination: 100, poolSize: 500, anonymitySet: 250 },
      ],
      error: null,
    };
  }

  async calculateOptimalDeposit(amount: number): Promise<RpcResponse<any>> {
    const denominations = [100, 10, 1, 0.1];
    const splits: Array<{ denomination: number; count: number }> = [];
    let remaining = amount;
    for (const d of denominations) {
      const count = Math.floor(remaining / d);
      if (count > 0) {
        splits.push({ denomination: d, count });
        remaining -= count * d;
      }
    }
    return { result: { splits, remaining }, error: null };
  }
}

/** Privacy score engine — placeholder; real scoring belongs in a heuristics module. */
export class PrivacyScoreEngineApi {
  constructor(private readonly _client: LunaHeliusClient) {}

  async calculateScore(_address: string): Promise<RpcResponse<PrivacyScore>> {
    return {
      result: {
        overallScore: 50,
        privacyGrade: 'C',
        factors: { addressReuse: 50, amountCorrelation: 50, timingPatterns: 50, exchangeExposure: 50 },
        recommendations: ['Use stealth addresses for high-value transfers'],
      },
      error: null,
    };
  }
}

/** Transaction graph privacy analysis — placeholder. */
export class TransactionGraphPrivacyApi {
  constructor(private readonly _client: LunaHeliusClient) {}

  async analyzePrivacyLeaks(_address: string, _depth: number): Promise<RpcResponse<GraphPrivacyAnalysis>> {
    return {
      result: { overallRiskScore: 50, leaksDetected: [], linkedAddressCount: 0, recommendations: [] },
      error: null,
    };
  }
}

/** Shielded pattern analysis — placeholder. */
export class ShieldedPatternApi {
  constructor(private readonly _client: LunaHeliusClient) {}

  async analyzeShieldedRatio(_address: string): Promise<RpcResponse<ShieldedTransactionPattern>> {
    return {
      result: { shieldedRatio: 0.0, totalTransfers: 0, shieldedTransfers: 0, transparentTransfers: 0 },
      error: null,
    };
  }
}
