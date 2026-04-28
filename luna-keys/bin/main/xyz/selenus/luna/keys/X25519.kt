package xyz.selenus.luna.keys

import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.interfaces.XECPrivateKey
import java.security.interfaces.XECPublicKey
import java.security.spec.NamedParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.security.spec.XECPublicKeySpec
import javax.crypto.KeyAgreement

/**
 * # X25519 ECDH (RFC 7748) wrapper + Ed25519 ↔ X25519 birational conversion
 *
 * Solana wallet keys are Ed25519 (Edwards25519). To do ECDH between two
 * wallets — for encrypting memos, deriving stealth-address shared secrets,
 * etc. — you have two clean options:
 *
 *   1. Convert both wallet keys to X25519 (Curve25519 / Montgomery form)
 *      via the birational equivalence and run X25519 ECDH directly. This
 *      module's preferred path: [ed25519PublicKeyToX25519] +
 *      [ed25519SeedToX25519Scalar] + [ecdh].
 *   2. Keep the keys on Edwards25519 and implement Edwards-form ECDH
 *      directly (`shared = my_scalar * their_pubkey_point`). Possible with
 *      the primitives in [Ed25519Derive] but requires exposing a public
 *      `pointMul`. We've left that closed for now to avoid surfacing
 *      half-implemented stealth-address machinery.
 *
 * The X25519 implementation here uses the JDK 17 native XDH provider
 * (introduced JDK 11). No Bouncy Castle. ECDH itself runs in constant time
 * because the JDK's X25519 implementation is constant-time.
 *
 * **Birational caveat**: the Edwards → Montgomery conversion is well-defined
 * only for points of small index in the cofactor-8 subgroup. For all
 * practical Solana wallet keys (which are produced by Ed25519 keygen and
 * thus live in the prime-order subgroup) this is not a concern, but
 * malicious inputs may include low-order points; [ed25519PublicKeyToX25519]
 * does NOT clear the cofactor and callers MUST validate inputs upstream.
 */
object X25519 {

    /** RFC 7748 X25519 key + scalar length: 32 bytes. */
    const val KEY_BYTES = 32

    /** RFC 7748 ECDH shared-secret length: 32 bytes. */
    const val SHARED_SECRET_BYTES = 32

    // ── ECDH ───────────────────────────────────────────────────────────

    /**
     * Compute the X25519 ECDH shared secret. Returns 32 bytes.
     *
     * The output is NOT a uniformly-random AES key — it's the raw X25519
     * group element. Run it through a KDF (HKDF-Expand or similar) before
     * using as a symmetric key. See `IrisWhisperNamespace.deriveKeyFromX25519`
     * in iris-sdk for the canonical "ECDH → AES-GCM key" pattern.
     *
     * @param mySecretScalar 32-byte X25519 scalar (already clamped per
     *   RFC 7748 §5). For Solana wallets, derive via
     *   [ed25519SeedToX25519Scalar].
     * @param theirPublicKey 32-byte X25519 public key (Montgomery u-coord).
     *   For Solana wallets, derive via [ed25519PublicKeyToX25519].
     */
    fun ecdh(mySecretScalar: ByteArray, theirPublicKey: ByteArray): ByteArray {
        require(mySecretScalar.size == KEY_BYTES) {
            "secret scalar must be $KEY_BYTES bytes (got ${mySecretScalar.size})"
        }
        require(theirPublicKey.size == KEY_BYTES) {
            "public key must be $KEY_BYTES bytes (got ${theirPublicKey.size})"
        }

        val priv = jdkPrivateKey(mySecretScalar)
        val pub = jdkPublicKey(theirPublicKey)

        return KeyAgreement.getInstance("XDH").run {
            init(priv)
            doPhase(pub, true)
            generateSecret()
        }
    }

    /**
     * Generate a fresh X25519 keypair. Returns `(secretScalar, publicKey)`.
     * Both 32 bytes.
     */
    fun generate(): Pair<ByteArray, ByteArray> {
        val kp = KeyPairGenerator.getInstance("XDH").apply {
            initialize(NamedParameterSpec.X25519)
        }.generateKeyPair()

        val priv = kp.private as XECPrivateKey
        val pub = kp.public as XECPublicKey

        // The JDK exposes the secret scalar as Optional<byte[]>; we require it.
        val secretBytes = priv.scalar.orElseThrow {
            IllegalStateException("JDK XDH provider did not expose the X25519 scalar")
        }
        require(secretBytes.size == KEY_BYTES) {
            "JDK returned X25519 scalar of size ${secretBytes.size} (expected $KEY_BYTES)"
        }

        // Public key: u-coordinate as little-endian bytes
        val publicBytes = uCoordToBytes(pub.u)
        return secretBytes to publicBytes
    }

    // ── Ed25519 → X25519 conversion ────────────────────────────────────

    /**
     * Convert an Ed25519 public key (32 bytes) to its X25519 equivalent
     * (32 bytes), per RFC 7748 §4.1: `u = (1 + y) / (1 - y) mod p`.
     *
     * The Ed25519 public-key bytes encode `y` in little-endian, with the
     * sign of `x` in the high bit of the last byte — that sign bit is
     * **discarded** by the conversion (X25519 lives on the Montgomery form
     * which only uses the u-coordinate).
     *
     * @throws IllegalArgumentException if [edPublicKey] is not 32 bytes or
     *   represents `y = 1` (singularity in the conversion: `(1 - y) = 0`,
     *   no inverse). `y = 1` is the Edwards identity — not a valid wallet pubkey.
     */
    fun ed25519PublicKeyToX25519(edPublicKey: ByteArray): ByteArray {
        require(edPublicKey.size == 32) { "Ed25519 public key must be 32 bytes (got ${edPublicKey.size})" }

        // Strip the sign bit from byte 31 to get the canonical y bytes.
        val yBytes = edPublicKey.copyOf().also { it[31] = (it[31].toInt() and 0x7F).toByte() }
        val y = leToBigInt(yBytes)

        require(y < P) { "Ed25519 public key encodes a non-canonical y >= p" }

        val oneMinusY = BigInteger.ONE.subtract(y).mod(P)
        require(oneMinusY != BigInteger.ZERO) {
            "Ed25519 public key is the identity element (y=1), no Montgomery conversion exists"
        }

        // u = (1 + y) / (1 - y) mod p
        val u = BigInteger.ONE.add(y).mod(P)
            .multiply(oneMinusY.modInverse(P))
            .mod(P)

        return bigIntToLe(u, 32)
    }

    /**
     * Convert an Ed25519 32-byte secret seed to the X25519 scalar that
     * matches the same wallet identity.
     *
     * Both Ed25519 and X25519 derive the working scalar by hashing the seed
     * with SHA-512, taking the first 32 bytes, and applying the same
     * RFC 7748 / RFC 8032 clamping. So an Ed25519 keypair holder can do
     * X25519 ECDH using `ed25519SeedToX25519Scalar(seed)` as the X25519
     * private input — the matching X25519 public key is
     * `ed25519PublicKeyToX25519(ed25519PubKey)`.
     */
    fun ed25519SeedToX25519Scalar(seed: ByteArray): ByteArray {
        require(seed.size == 32) { "Ed25519 seed must be 32 bytes (got ${seed.size})" }
        val h = MessageDigest.getInstance("SHA-512").digest(seed)
        val s = h.copyOfRange(0, 32)
        // RFC 7748 §5 clamping (same bit pattern as RFC 8032 §5.1.5):
        s[0] = (s[0].toInt() and 0xF8).toByte()
        s[31] = (s[31].toInt() and 0x7F).toByte()
        s[31] = (s[31].toInt() or 0x40).toByte()
        return s
    }

    /**
     * One-shot helper: convert a Solana wallet keypair (Ed25519) into the
     * matching X25519 private+public for ECDH. Returns `(scalar, pubKey)`.
     */
    fun ed25519KeypairToX25519(seed: ByteArray, edPublicKey: ByteArray): Pair<ByteArray, ByteArray> =
        ed25519SeedToX25519Scalar(seed) to ed25519PublicKeyToX25519(edPublicKey)

    // ── Internal: JDK XDH key construction from raw bytes ──────────────

    private fun jdkPrivateKey(scalar: ByteArray): java.security.PrivateKey {
        // PKCS#8 envelope for X25519 raw-32-byte scalar:
        // SEQUENCE(46), INTEGER(0), AlgorithmIdentifier(X25519),
        // OCTET STRING containing OCTET STRING containing scalar
        val pkcs8 = X25519_PKCS8_PREFIX + scalar
        return KeyFactory.getInstance("XDH").generatePrivate(PKCS8EncodedKeySpec(pkcs8))
    }

    private fun jdkPublicKey(uBytes: ByteArray): java.security.PublicKey {
        // Use the typed XECPublicKeySpec — cleaner than building an X.509 envelope.
        val u = leToBigInt(uBytes)
        val spec = XECPublicKeySpec(NamedParameterSpec.X25519, u)
        return KeyFactory.getInstance("XDH").generatePublic(spec)
    }

    private fun uCoordToBytes(u: BigInteger): ByteArray = bigIntToLe(u.mod(P), 32)

    // ── Field constants + endian helpers ───────────────────────────────

    /** p = 2^255 - 19, shared with Ed25519. */
    private val P: BigInteger = BigInteger.valueOf(2).pow(255).subtract(BigInteger.valueOf(19))

    /** PKCS#8 prefix for an X25519 raw 32-byte scalar (16 bytes). */
    private val X25519_PKCS8_PREFIX = byteArrayOf(
        0x30, 0x2E,
        0x02, 0x01, 0x00,
        0x30, 0x05, 0x06, 0x03, 0x2B, 0x65, 0x6E, // OID 1.3.101.110 (id-X25519)
        0x04, 0x22, 0x04, 0x20
    )

    private fun leToBigInt(le: ByteArray): BigInteger {
        val be = ByteArray(le.size + 1)
        for (i in le.indices) be[i + 1] = le[le.size - 1 - i]
        return BigInteger(be)
    }

    private fun bigIntToLe(value: BigInteger, length: Int): ByteArray {
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
