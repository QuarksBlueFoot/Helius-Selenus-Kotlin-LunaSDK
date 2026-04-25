package xyz.selenus.luna.webhooks

import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * Real Ed25519 signature verifier for Helius webhook deliveries.
 *
 * Uses JDK 17's native EdDSA provider (Java 15+ ships Ed25519 in the standard
 * library — no Bouncy Castle required). The full chain is:
 *   1. Decode the base58-encoded public key (32 bytes, Ed25519 raw format).
 *   2. Wrap the 32-byte raw key in an X.509 SubjectPublicKeyInfo envelope so
 *      `KeyFactory.generatePublic` accepts it.
 *   3. Decode the base58-encoded signature (64 bytes).
 *   4. Verify against the raw request body.
 *
 * Constant-time comparison is delegated to JDK's `Signature.verify` which is
 * implemented over the platform crypto provider's constant-time Ed25519 verify.
 *
 * **Throws nothing** — any malformed input or verification failure returns
 * `false`. This is deliberate: webhook handlers should never throw on bad
 * inputs from the network.
 */
internal object WebhookSignatureVerifier {

    /** Static prefix for an Ed25519 SubjectPublicKeyInfo (12 bytes). */
    private val ED25519_X509_PREFIX = byteArrayOf(
        0x30, 0x2A,                         // SEQUENCE, length 42
        0x30, 0x05,                         // SEQUENCE, length 5
        0x06, 0x03, 0x2B, 0x65, 0x70,       // OID 1.3.101.112 (id-Ed25519)
        0x03, 0x21, 0x00                    // BIT STRING, length 33, 0 unused bits
    )

    fun verify(body: ByteArray, signatureBase58: String, publicKeyBase58: String): Boolean = try {
        val rawPubKey = Base58.decode(publicKeyBase58)
        require(rawPubKey.size == 32) { "Ed25519 public key must be 32 bytes, got ${rawPubKey.size}" }

        val signatureBytes = Base58.decode(signatureBase58)
        require(signatureBytes.size == 64) { "Ed25519 signature must be 64 bytes, got ${signatureBytes.size}" }

        val x509 = ED25519_X509_PREFIX + rawPubKey
        val publicKey = KeyFactory.getInstance("Ed25519")
            .generatePublic(X509EncodedKeySpec(x509))

        Signature.getInstance("Ed25519").apply {
            initVerify(publicKey)
            update(body)
        }.verify(signatureBytes)
    } catch (t: Throwable) {
        // Any decode / parse / verify failure → false. Webhook handlers
        // must never throw on adversarial input.
        false
    }

    /**
     * Constant-time byte equality. Useful for callers that need to compare
     * MAC-like webhook tokens without leaking length-or-content timing.
     *
     * Exposed as part of [WebhookSignatureVerifier]'s utility surface even
     * though Ed25519 verify already runs constant-time internally — webhook
     * implementations sometimes layer additional shared-secret tokens.
     */
    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].toInt() xor b[i].toInt())
        }
        return result == 0
    }

    /** SHA-256 helper exposed for callers that want to double-check body integrity. */
    fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)
}

/**
 * Minimal base58 codec (Bitcoin alphabet, used by Solana). No external
 * dependencies — kept internal because the public Solana base58 alphabet is
 * stable and we only need encode/decode for fixed-width keys + signatures.
 *
 * Constant-factor performance is acceptable for webhook-rate workloads
 * (signatures are 88 chars; pubkeys are 44 chars). For high-throughput tx
 * processing prefer a vetted library.
 */
internal object Base58 {
    private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    private val INDEXES = IntArray(128) { -1 }.also {
        for ((i, c) in ALPHABET.withIndex()) it[c.code] = i
    }

    fun encode(input: ByteArray): String {
        if (input.isEmpty()) return ""
        // Count leading zeros — they map to '1' chars in base58 output.
        var leadingZeros = 0
        while (leadingZeros < input.size && input[leadingZeros].toInt() == 0) leadingZeros++

        val copy = input.copyOf()
        val encoded = CharArray(input.size * 2) // upper bound (~log_58(256) ≈ 1.37×)
        var outputStart = encoded.size

        var inputStart = leadingZeros
        while (inputStart < copy.size) {
            encoded[--outputStart] = ALPHABET[divmod(copy, inputStart, 256, 58).toInt()]
            if (copy[inputStart].toInt() == 0) inputStart++
        }
        // Emit leading '1's for each leading zero byte
        repeat(leadingZeros) { encoded[--outputStart] = ALPHABET[0] }
        return String(encoded, outputStart, encoded.size - outputStart)
    }

    fun decode(input: String): ByteArray {
        if (input.isEmpty()) return ByteArray(0)
        val input58 = ByteArray(input.length)
        for ((i, c) in input.withIndex()) {
            val digit = if (c.code < 128) INDEXES[c.code] else -1
            require(digit >= 0) { "Invalid base58 character '$c' at index $i" }
            input58[i] = digit.toByte()
        }
        var leadingZeros = 0
        while (leadingZeros < input58.size && input58[leadingZeros].toInt() == 0) leadingZeros++

        val decoded = ByteArray(input.length)
        var outputStart = decoded.size

        var inputStart = leadingZeros
        while (inputStart < input58.size) {
            decoded[--outputStart] = divmod(input58, inputStart, 58, 256)
            if (input58[inputStart].toInt() == 0) inputStart++
        }
        // Skip extra leading zeros that came out of the math
        while (outputStart < decoded.size && decoded[outputStart].toInt() == 0) outputStart++
        return decoded.copyOfRange(outputStart - leadingZeros, decoded.size)
    }

    private fun divmod(number: ByteArray, firstDigit: Int, base: Int, divisor: Int): Byte {
        var remainder = 0
        for (i in firstDigit until number.size) {
            val digit = number[i].toInt() and 0xFF
            val temp = remainder * base + digit
            number[i] = (temp / divisor).toByte()
            remainder = temp % divisor
        }
        return remainder.toByte()
    }
}
