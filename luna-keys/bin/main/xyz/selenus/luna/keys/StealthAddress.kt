package xyz.selenus.luna.keys

import java.math.BigInteger
import java.security.MessageDigest

/**
 * # Stealth address toolkit (dual-key scheme, Ed25519 over Solana)
 *
 * Implements the Monero-style **Dual-Key Stealth Address Protocol** (DKSAP)
 * adapted for Ed25519 / Solana. The recipient publishes a *meta-address*
 * `(S, V)` — a public spending key and a public viewing key. The sender,
 * given only the meta-address, can derive a one-time *stealth address* `P`
 * such that:
 *
 *   - Anyone observing the on-chain transfer to `P` cannot link `P` back to
 *     the recipient's meta-address `(S, V)` without knowing the private
 *     viewing key `v`.
 *   - The recipient can scan the chain with their private viewing key `v`
 *     and detect funds destined for them.
 *   - The recipient can spend funds at `P` because they can derive the
 *     matching private key `s + h` where `s` is their private spending key
 *     and `h` is the per-transfer secret.
 *
 * ## Protocol math (one transfer)
 *
 * **Sender** (knows meta-address `(S, V)` and chooses ephemeral seed `r`):
 * ```
 * R       = r · G                           (ephemeral public key, published with the tx)
 * shared  = X25519(r, V)                    (X25519 ECDH on the birationally-converted keys)
 * h       = H("luna-stealth" ‖ shared) mod L
 * P       = S + h · G                       (one-time stealth address)
 * ```
 *
 * **Recipient** (knows private viewing key `v` and private spending key `s`):
 * ```
 * for each ephemeral R observed on-chain:
 *   shared      = X25519(v, R)              (matches the sender's `shared` by ECDH symmetry)
 *   h           = H("luna-stealth" ‖ shared) mod L
 *   candidate_P = S + h · G
 *   if candidate_P == observed_recipient: this is yours; spending key = (s + h) mod L
 * ```
 *
 * ## Caveats
 *
 *  - `s + h` is a 32-byte scalar. To use it as a Solana keypair, you need a
 *    seed that, when fed through Ed25519's SHA-512 + clamp, produces this
 *    scalar. There's no inverse for that — so the recipient holds a
 *    scalar-only "spending key" rather than a fresh seed. Signing requires
 *    a primitive that accepts a raw scalar (not the standard Ed25519 sign
 *    which derives the scalar from a seed). The toolkit exposes the
 *    derived spending scalar; signing-with-scalar is left to the caller
 *    (or a follow-up module).
 *
 *  - The protocol does NOT specify how `R` is published on-chain. Solana
 *    has no native "stealth address" instruction; common approaches put `R`
 *    in a Memo program instruction adjacent to the transfer.
 *
 *  - This is a **toolkit**, not a turnkey wallet feature. Production use
 *    requires application-level scanning infrastructure and a clear
 *    protocol for the on-chain `R` publication.
 */
object StealthAddress {

    private const val DOMAIN_SEPARATOR = "luna-stealth-v1"

    /**
     * A stealth meta-address — what the recipient publishes (e.g. on a
     * profile page or via DM) so senders can address them privately.
     */
    data class MetaAddress(
        /** Recipient's public spending key (32-byte Ed25519 pubkey). */
        val spendingPublicKey: ByteArray,
        /** Recipient's public viewing key (32-byte Ed25519 pubkey). */
        val viewingPublicKey: ByteArray
    ) {
        init {
            require(spendingPublicKey.size == 32) { "spendingPublicKey must be 32 bytes" }
            require(viewingPublicKey.size == 32) { "viewingPublicKey must be 32 bytes" }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is MetaAddress) return false
            return spendingPublicKey.contentEquals(other.spendingPublicKey) &&
                viewingPublicKey.contentEquals(other.viewingPublicKey)
        }

        override fun hashCode(): Int =
            31 * spendingPublicKey.contentHashCode() + viewingPublicKey.contentHashCode()
    }

    /**
     * Result of one stealth address derivation by the sender. Send funds to
     * [stealthAddress]; publish [ephemeralPublicKey] alongside the tx so the
     * recipient can scan and find this transfer.
     */
    data class StealthEnvelope(
        val stealthAddress: ByteArray,
        val ephemeralPublicKey: ByteArray,
        /**
         * The shared secret `H("luna-stealth-v1" ‖ ECDH)` mod L. Exposed so
         * applications that want to attach an encrypted memo can use it as
         * a key (or as the input to a KDF). Otherwise opaque.
         */
        val sharedScalar: ByteArray
    ) {
        init {
            require(stealthAddress.size == 32) { "stealthAddress must be 32 bytes" }
            require(ephemeralPublicKey.size == 32) { "ephemeralPublicKey must be 32 bytes" }
            require(sharedScalar.size == 32) { "sharedScalar must be 32 bytes" }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is StealthEnvelope) return false
            return stealthAddress.contentEquals(other.stealthAddress) &&
                ephemeralPublicKey.contentEquals(other.ephemeralPublicKey) &&
                sharedScalar.contentEquals(other.sharedScalar)
        }

        override fun hashCode(): Int {
            var r = stealthAddress.contentHashCode()
            r = 31 * r + ephemeralPublicKey.contentHashCode()
            r = 31 * r + sharedScalar.contentHashCode()
            return r
        }
    }

    /**
     * Result of the recipient's scan when they find a matching ephemeral key.
     *
     * @property stealthAddress The one-time address that received funds.
     * @property spendingScalar `(s + h) mod L` — the 32-byte Ed25519 scalar
     *   needed to sign for [stealthAddress]. Note: NOT a full seed; standard
     *   Ed25519 sign() takes a seed and derives the scalar internally. To
     *   sign with a raw scalar you need a primitive that bypasses derivation.
     */
    data class ScanMatch(
        val stealthAddress: ByteArray,
        val spendingScalar: ByteArray
    ) {
        init {
            require(stealthAddress.size == 32) { "stealthAddress must be 32 bytes" }
            require(spendingScalar.size == 32) { "spendingScalar must be 32 bytes" }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ScanMatch) return false
            return stealthAddress.contentEquals(other.stealthAddress) &&
                spendingScalar.contentEquals(other.spendingScalar)
        }

        override fun hashCode(): Int =
            31 * stealthAddress.contentHashCode() + spendingScalar.contentHashCode()
    }

    // ── Sender side ───────────────────────────────────────────────────

    /**
     * Sender derives a one-time stealth address for [meta]. Generates a
     * fresh ephemeral keypair via `SolanaKeypair.generate()`.
     */
    fun derive(meta: MetaAddress): StealthEnvelope {
        val ephemeral = SolanaKeypair.generate()
        return derive(meta, ephemeral)
    }

    /**
     * Sender derives a one-time stealth address using a caller-supplied
     * ephemeral keypair. Useful when the sender wants deterministic stealth
     * addresses (e.g. derived from a SLIP-0010 sub-account so they can
     * reproduce them later).
     */
    fun derive(meta: MetaAddress, ephemeral: SolanaKeypair): StealthEnvelope {
        // shared = X25519(r, V): convert ephemeral's seed to X25519 scalar,
        // convert recipient's viewing pubkey to X25519, run ECDH.
        val rXScalar = X25519.ed25519SeedToX25519Scalar(ephemeral.secretKeyBytes)
        val vXPub = X25519.ed25519PublicKeyToX25519(meta.viewingPublicKey)
        val sharedSecret = X25519.ecdh(rXScalar, vXPub)

        // h = H("luna-stealth-v1" ‖ shared) mod L
        val h = hashToScalar(sharedSecret)

        // P = S + h·G  (compressed point arithmetic on Ed25519)
        val hG = Ed25519Derive.scalarMultiplyBaseBytes(h)
        val stealthAddress = Ed25519Derive.addPointsBytes(meta.spendingPublicKey, hG)
            ?: error("Failed to add S + h·G — meta-address spending key is not a valid Ed25519 point")

        return StealthEnvelope(
            stealthAddress = stealthAddress,
            ephemeralPublicKey = ephemeral.publicKeyBytes,
            sharedScalar = bigIntToFixedLe(h, 32)
        )
    }

    // ── Recipient side ────────────────────────────────────────────────

    /**
     * Recipient checks whether [observedRecipientAddress] (an address that
     * received funds in some transaction) is a stealth address derived for
     * them, given the [ephemeralPublicKey] published alongside the tx.
     *
     * @param viewingSecretSeed Recipient's 32-byte viewing seed.
     * @param spendingSecretSeed Recipient's 32-byte spending seed.
     * @param ephemeralPublicKey 32-byte ephemeral pubkey from the on-chain protocol.
     * @param observedRecipientAddress 32-byte address that received funds.
     *
     * @return [ScanMatch] when the addresses match (this is yours), or null
     *   when they don't (this transfer isn't for you).
     */
    fun scan(
        viewingSecretSeed: ByteArray,
        spendingSecretSeed: ByteArray,
        ephemeralPublicKey: ByteArray,
        observedRecipientAddress: ByteArray
    ): ScanMatch? {
        require(viewingSecretSeed.size == 32) { "viewingSecretSeed must be 32 bytes" }
        require(spendingSecretSeed.size == 32) { "spendingSecretSeed must be 32 bytes" }
        require(ephemeralPublicKey.size == 32) { "ephemeralPublicKey must be 32 bytes" }
        require(observedRecipientAddress.size == 32) { "observedRecipientAddress must be 32 bytes" }

        // shared = X25519(v, R) — symmetric to the sender's X25519(r, V)
        val vXScalar = X25519.ed25519SeedToX25519Scalar(viewingSecretSeed)
        val rXPub = X25519.ed25519PublicKeyToX25519(ephemeralPublicKey)
        val sharedSecret = X25519.ecdh(vXScalar, rXPub)

        val h = hashToScalar(sharedSecret)

        // candidate_P = S + h·G where S is the recipient's spending pubkey,
        // derived from spendingSecretSeed.
        val spendingPubkey = Ed25519Derive.publicKeyFromSeed(spendingSecretSeed)
        val hG = Ed25519Derive.scalarMultiplyBaseBytes(h)
        val candidate = Ed25519Derive.addPointsBytes(spendingPubkey, hG)
            ?: return null

        if (!candidate.contentEquals(observedRecipientAddress)) return null

        // Recovered spending scalar = (s + h) mod L, where s is the
        // recipient's clamped spending scalar derived from the spending seed.
        val s = clampedScalarFromSeed(spendingSecretSeed)
        val spendingScalar = s.add(h).mod(Ed25519Derive.ORDER_L)
        return ScanMatch(
            stealthAddress = observedRecipientAddress,
            spendingScalar = bigIntToFixedLe(spendingScalar, 32)
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────

    /**
     * Hash the X25519 ECDH shared secret to an Ed25519 scalar. We use
     * SHA-512 with a domain separator and reduce mod L. The 64-byte SHA-512
     * output reduced mod L has negligible bias (the bias is ~2^-256), so
     * it's sound to use as a scalar without rejection sampling.
     */
    internal fun hashToScalar(sharedSecret: ByteArray): BigInteger {
        val md = MessageDigest.getInstance("SHA-512")
        md.update(DOMAIN_SEPARATOR.toByteArray(Charsets.US_ASCII))
        md.update(0x00) // separator between domain and payload
        md.update(sharedSecret)
        val digest = md.digest()
        // Interpret as little-endian wide integer, then reduce mod L.
        val value = leToBigInt(digest)
        return value.mod(Ed25519Derive.ORDER_L)
    }

    /**
     * Recover the clamped 32-byte scalar that Ed25519 derives internally
     * from a 32-byte seed (RFC 8032 §5.1.5 step 1–3). Used to reconstruct
     * the recipient's spending scalar `s` for the `(s + h)` computation.
     */
    internal fun clampedScalarFromSeed(seed: ByteArray): BigInteger {
        val h = MessageDigest.getInstance("SHA-512").digest(seed)
        val s = h.copyOfRange(0, 32)
        s[0] = (s[0].toInt() and 0xF8).toByte()
        s[31] = (s[31].toInt() and 0x7F).toByte()
        s[31] = (s[31].toInt() or 0x40).toByte()
        return leToBigInt(s)
    }

    private fun leToBigInt(le: ByteArray): BigInteger {
        val be = ByteArray(le.size + 1)
        for (i in le.indices) be[i + 1] = le[le.size - 1 - i]
        return BigInteger(be)
    }

    private fun bigIntToFixedLe(value: BigInteger, length: Int): ByteArray {
        val be = value.toByteArray()
        val out = ByteArray(length)
        val srcStart = if (be.size > length) be.size - length else 0
        val srcLen = be.size - srcStart
        for (i in 0 until srcLen) {
            out[i] = be[srcStart + srcLen - 1 - i]
        }
        return out
    }
}
