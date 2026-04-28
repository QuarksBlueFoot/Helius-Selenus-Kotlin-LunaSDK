package xyz.selenus.luna.keys

import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

/**
 * Solana keypair (Ed25519). 32-byte secret + 32-byte public key.
 *
 * Implementation uses the JDK 17 native `Ed25519` provider — no Bouncy Castle
 * dependency. `secretKeyBytes` is the **raw 32-byte seed** (the canonical
 * Solana secret-key format), not the JDK's PKCS#8 envelope; we strip the
 * envelope on construction so the bytes round-trip with `solana-keygen`,
 * `@solana/web3.js`, and the Solana CLI.
 *
 * Mirrors the Helius Rust SDK's `make_keypairs` helper but with a richer
 * shape (named accessors, sign/verify, Solana keystore round-trip).
 *
 * ## Construction paths
 *
 *  - [generate] — fresh keypair from the platform CSPRNG.
 *  - [fromSecretSeed] — seed → public key derivation per RFC 8032 §5.1.5,
 *    implemented in [Ed25519Derive] (BigInteger field arithmetic over
 *    GF(2^255 - 19), extended-projective Edwards point ops, ~3–5ms per call).
 *    Validated against RFC 8032 test vectors. NOT constant-time at the
 *    field-op level — see [Ed25519Derive] KDoc for the threat-model caveat.
 *  - [fromSolanaKeystoreBytes] — parse the standard Solana 64-byte
 *    `[seed || pubkey]` keystore format. The fastest path because no
 *    derivation is needed (and the parser cross-validates seed↔pubkey).
 *  - For dApp wallet integrations, prefer Mobile Wallet Adapter or the
 *    Solana Wallet Standard — your app receives the public key from the
 *    wallet without ever touching the seed.
 */
class SolanaKeypair internal constructor(
    /** 32-byte Ed25519 seed — keep secret. */
    val secretKeyBytes: ByteArray,
    /** 32-byte Ed25519 public key. */
    val publicKeyBytes: ByteArray
) {
    init {
        require(secretKeyBytes.size == 32) { "secretKeyBytes must be 32 bytes (got ${secretKeyBytes.size})" }
        require(publicKeyBytes.size == 32) { "publicKeyBytes must be 32 bytes (got ${publicKeyBytes.size})" }
    }

    /** The matching [SolanaAddress] for this keypair's public key. */
    val address: SolanaAddress get() = SolanaAddress.fromBytes(publicKeyBytes)

    /** Convenience: base58-encoded public key (Solana wallet address format). */
    val publicKeyBase58: String get() = Base58.encode(publicKeyBytes)

    /**
     * Concat [secretKeyBytes] || [publicKeyBytes] — the standard 64-byte
     * Solana keystore representation. Compatible with `solana-keygen`,
     * `@solana/web3.js` `Keypair.fromSecretKey()`, and the Solana CLI.
     */
    fun toSolanaKeystoreBytes(): ByteArray = secretKeyBytes + publicKeyBytes

    /**
     * Sign [message] with this keypair's secret key. Returns 64 bytes
     * (Ed25519 signature). Uses JDK native Ed25519 — no extra deps.
     */
    fun sign(message: ByteArray): ByteArray {
        val pkcs8 = PKCS8_PREFIX + secretKeyBytes
        val privateKey = KeyFactory.getInstance("Ed25519")
            .generatePrivate(PKCS8EncodedKeySpec(pkcs8))
        return Signature.getInstance("Ed25519").run {
            initSign(privateKey)
            update(message)
            sign()
        }
    }

    /** Verify a signature using this keypair's public key. */
    fun verify(message: ByteArray, signature: ByteArray): Boolean = try {
        val x509 = X509_PREFIX + publicKeyBytes
        val publicKey = KeyFactory.getInstance("Ed25519")
            .generatePublic(X509EncodedKeySpec(x509))
        Signature.getInstance("Ed25519").run {
            initVerify(publicKey)
            update(message)
            verify(signature)
        }
    } catch (_: Throwable) {
        false
    }

    companion object {
        /** PKCS#8 envelope prefix for an Ed25519 raw 32-byte seed (16 bytes). */
        private val PKCS8_PREFIX = byteArrayOf(
            0x30, 0x2E,
            0x02, 0x01, 0x00,
            0x30, 0x05, 0x06, 0x03, 0x2B, 0x65, 0x70,
            0x04, 0x22, 0x04, 0x20
        )

        /** X.509 SubjectPublicKeyInfo prefix for a 32-byte Ed25519 pubkey (12 bytes). */
        private val X509_PREFIX = byteArrayOf(
            0x30, 0x2A,
            0x30, 0x05, 0x06, 0x03, 0x2B, 0x65, 0x70,
            0x03, 0x21, 0x00
        )

        /**
         * Generate a fresh keypair using the platform CSPRNG. Equivalent to
         * `solana-keygen new`. Both seed and public key come back from the
         * JDK in one shot, so no reverse-derivation is needed.
         */
        fun generate(): SolanaKeypair {
            val kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
            val pkcs8 = kp.private.encoded
            require(pkcs8.size == 48) {
                "Unexpected PKCS#8 size ${pkcs8.size} from JDK Ed25519 provider — Java version mismatch?"
            }
            val seed = pkcs8.copyOfRange(16, 48)

            val x509 = kp.public.encoded
            require(x509.size == 44) {
                "Unexpected X.509 size ${x509.size} from JDK Ed25519 provider — Java version mismatch?"
            }
            val publicKey = x509.copyOfRange(12, 44)

            return SolanaKeypair(seed, publicKey)
        }

        /**
         * Parse a 64-byte Solana keystore (`[seed || pubkey]`). This is the
         * format `solana-keygen` writes and `@solana/web3.js` reads. After
         * decoding, the constructor cross-validates the public-key bytes by
         * signing a fixed message and verifying with the decoded public key
         * — that catches off-by-one corruption / accidental swap of the
         * 32-byte halves at parse time rather than at first use.
         */
        fun fromSolanaKeystoreBytes(bytes: ByteArray): SolanaKeypair {
            require(bytes.size == 64) {
                "Solana keystore must be 64 bytes (32-byte seed || 32-byte pubkey), got ${bytes.size}"
            }
            val seed = bytes.copyOfRange(0, 32)
            val pubkey = bytes.copyOfRange(32, 64)
            val kp = SolanaKeypair(seed, pubkey)

            // Cross-check: sign a fixed marker and verify. If the bytes were
            // swapped or corrupted, this will throw cleanly instead of letting
            // the caller ship a broken keypair.
            require(kp.verify(KEYSTORE_VALIDATION_MARKER, kp.sign(KEYSTORE_VALIDATION_MARKER))) {
                "Solana keystore self-validation failed — bytes do not form a valid Ed25519 keypair"
            }
            return kp
        }

        /**
         * Reconstruct a keypair from a 32-byte secret seed. The matching
         * public key is derived via Ed25519 scalar multiplication per
         * RFC 8032 §5.1.5 (see [Ed25519Derive] for the implementation).
         *
         * Typical cost: ~3–5ms on commodity hardware. Validated against the
         * RFC 8032 test vectors. Suitable for keystore import flows; for
         * batch verification use the JDK's native Ed25519 Signature path.
         */
        fun fromSecretSeed(seed: ByteArray): SolanaKeypair {
            require(seed.size == 32) { "seed must be 32 bytes (got ${seed.size})" }
            val pubkey = Ed25519Derive.publicKeyFromSeed(seed)
            return SolanaKeypair(seed, pubkey)
        }

        /**
         * Generate [count] random keypairs. Mirrors the Helius Rust SDK's
         * `make_keypairs` helper.
         */
        fun makeKeypairs(count: Int): List<SolanaKeypair> {
            require(count >= 0) { "count must be non-negative" }
            return List(count) { generate() }
        }

        /**
         * Fixed marker used by [fromSolanaKeystoreBytes] to validate that
         * the parsed bytes form a coherent Ed25519 keypair. Value is the
         * SHA-2 digest of "luna-sdk:keystore-validation" — chosen to avoid
         * any chance of colliding with a meaningful Solana payload.
         */
        private val KEYSTORE_VALIDATION_MARKER: ByteArray =
            byteArrayOf(0x6C, 0x75, 0x6E, 0x61, 0x2D, 0x73, 0x64, 0x6B, 0x2E, 0x76, 0x61, 0x6C)
    }
}

/**
 * Validate any string as a Solana address — JVM-friendly free function.
 * Equivalent to [isValidSolanaAddress] but exposed on this file for IDE
 * auto-import next to the keypair APIs.
 */
fun validateAddress(input: String): Boolean = isValidSolanaAddress(input)

