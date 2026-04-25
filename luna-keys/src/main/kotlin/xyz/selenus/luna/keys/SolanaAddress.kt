package xyz.selenus.luna.keys

import java.math.BigInteger

/**
 * Solana address (a 32-byte Ed25519 public key encoded as base58).
 *
 * Ergonomic wrapper that does both syntactic validation (right base58 length,
 * decodes to 32 bytes) AND on-curve validation (that the decoded point lies
 * on the Ed25519 curve — distinguishes wallet addresses from PDAs).
 *
 * On-curve check is cheap (a few field ops) but a strict superset of what
 * the Helius Rust SDK's `is_valid_solana_address` does — that one only
 * checks length. Most Solana indexers DO want the on-curve distinction
 * because PDAs cannot sign, so we expose both.
 */
@JvmInline
value class SolanaAddress private constructor(val base58: String) {

    /** The 32 raw bytes of the public key. */
    val bytes: ByteArray
        get() = Base58.decode(base58)

    override fun toString(): String = base58

    companion object {
        const val LENGTH_BYTES = 32

        /**
         * Parse a base58 string as a Solana address. Returns null if not a
         * valid 32-byte base58. Does NOT check on-curve — use [parseStrict]
         * to require the address to lie on the Ed25519 curve.
         */
        fun parse(base58: String): SolanaAddress? {
            if (base58.isEmpty()) return null
            val raw = try {
                Base58.decode(base58)
            } catch (_: IllegalArgumentException) {
                return null
            }
            return if (raw.size == LENGTH_BYTES) SolanaAddress(base58) else null
        }

        /**
         * Like [parse] but additionally requires the decoded point to be on
         * the Ed25519 curve. Returns null for off-curve points (typical PDAs).
         */
        fun parseStrict(base58: String): SolanaAddress? {
            val candidate = parse(base58) ?: return null
            return if (Ed25519Curve.isOnCurve(candidate.bytes)) candidate else null
        }

        /** Convenience: turn 32 raw key bytes into a [SolanaAddress]. */
        fun fromBytes(bytes: ByteArray): SolanaAddress {
            require(bytes.size == LENGTH_BYTES) {
                "Solana address must be exactly $LENGTH_BYTES bytes (got ${bytes.size})"
            }
            return SolanaAddress(Base58.encode(bytes))
        }
    }
}

/**
 * Quick boolean check matching the Helius Rust SDK's `is_valid_solana_address`
 * — base58, decodes to 32 bytes. Doesn't check on-curve.
 */
fun isValidSolanaAddress(input: String): Boolean = SolanaAddress.parse(input) != null

/**
 * Strict on-curve variant — true only for addresses that could be a wallet
 * pubkey (excludes PDAs).
 */
fun isWalletAddress(input: String): Boolean = SolanaAddress.parseStrict(input) != null

/**
 * Ed25519 on-curve check.
 *
 * The on-curve test uses the standard equation y² = x²·(d·y² + 1) / (d·y² - a)
 * over GF(2^255 - 19) with the Ed25519 d and a parameters. We solve for x²
 * from the encoded y, then check that the candidate x² has a square root in
 * the field — if it does, the point is on the curve.
 *
 * Reference: RFC 8032 §5.1.3.
 */
internal object Ed25519Curve {
    // 2^255 - 19, the Ed25519 prime
    private val P: BigInteger = BigInteger.valueOf(2).pow(255).subtract(BigInteger.valueOf(19))

    // d = -121665/121666 (mod p) — Ed25519 curve constant
    private val D: BigInteger = BigInteger("-121665").mod(P)
        .multiply(BigInteger("121666").modInverse(P)).mod(P)

    // (p - 1) / 2 — exponent for the Legendre symbol
    private val EXP_LEGENDRE: BigInteger = P.subtract(BigInteger.ONE).shiftRight(1)

    /**
     * @param compressedY 32 bytes, little-endian, with the high bit of the last
     *   byte holding the sign of x.
     */
    fun isOnCurve(compressedY: ByteArray): Boolean {
        if (compressedY.size != 32) return false
        // Strip the sign bit and decode y as little-endian.
        val yBytes = compressedY.copyOf().also { it[31] = (it[31].toInt() and 0x7F).toByte() }
        val y = leToBigInt(yBytes)
        if (y >= P) return false  // canonical-form check

        val ySq = y.multiply(y).mod(P)
        val u = ySq.subtract(BigInteger.ONE).mod(P)              // y² - 1
        val v = D.multiply(ySq).add(BigInteger.ONE).mod(P)       // d·y² + 1

        // x² = u/v ; the point is on the curve iff (u/v) has a square root in F_p.
        val xSq = u.multiply(v.modInverse(P)).mod(P)

        // Legendre symbol: a^((p-1)/2) mod p == 1 iff a is a non-zero QR.
        // It's also 0 when a == 0 (which corresponds to x == 0, a valid point).
        val legendre = xSq.modPow(EXP_LEGENDRE, P)
        return legendre == BigInteger.ZERO || legendre == BigInteger.ONE
    }

    private fun leToBigInt(le: ByteArray): BigInteger {
        // Reverse to big-endian so BigInteger reads it correctly, prepend
        // a zero sign byte so it stays unsigned.
        val be = ByteArray(le.size + 1)
        for (i in le.indices) be[i + 1] = le[le.size - 1 - i]
        return BigInteger(be)
    }
}
