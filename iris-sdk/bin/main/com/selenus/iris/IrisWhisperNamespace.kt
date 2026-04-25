package com.selenus.iris

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * # Iris Whisper — Encrypted On-Chain Memos (v2)
 *
 * AES-GCM-256 encryption for SPL Memo program payloads. Uses a 32-byte
 * shared secret you provide; produces a self-contained ciphertext that
 * fits in a Memo instruction.
 *
 * ## Wire format
 *
 * `whisper:v2:base64url(nonce || ciphertext || tag)` where:
 *  - `nonce` = 12 random bytes (NIST SP 800-38D recommended size)
 *  - `ciphertext` = AES-GCM ciphertext of the UTF-8 plaintext
 *  - `tag` = 16-byte GCM authentication tag
 *
 * v1 (the prior implementation) used `whisper:v1:` and was **NOT actually
 * encryption** — it reversed the string and mocked ECDH. Code attempting to
 * decrypt v1 payloads now throws [IrisWhisperVersionException]; treat any
 * v1 payload as compromised and re-issue v2.
 *
 * ## Where does the shared secret come from?
 *
 * Whisper does **not** do key exchange. Callers supply the 32-byte secret,
 * derived via:
 *   - **X25519 ECDH** between two Curve25519 keypairs ([deriveKeyFromX25519]
 *     helper provided). Solana wallet keys are Ed25519 and require a
 *     birational mapping to X25519 first — see KDoc on that helper.
 *   - **HKDF over a pre-shared secret** ([deriveKeyFromPassphrase]).
 *   - **Out-of-band** (e.g. delivered via the parties' existing E2E channel).
 *
 * This module deliberately does NOT implement Ed25519→X25519 conversion or
 * full ECIES — those are dedicated efforts that warrant their own audit.
 *
 * ## Threat model
 *
 * Provides confidentiality + integrity of the memo payload **only against
 * observers without the shared secret**. It does NOT provide:
 *  - Forward secrecy (stationary AES key per session).
 *  - Sender authentication beyond "knows the shared secret".
 *  - Replay protection — wrap with a nonce/sequence number in the plaintext
 *    if your application requires it.
 */
class IrisWhisperNamespace internal constructor(@Suppress("unused") private val client: IrisQuickNodeClient) {

    /**
     * Encrypt [message] using AES-GCM-256 under [sharedKey] (32 bytes).
     * Returns a [WhisperMemo] whose `instruction` is ready to attach to a
     * Memo program instruction.
     *
     * @throws IllegalArgumentException if [sharedKey] is not exactly 32 bytes.
     */
    suspend fun createPrivateMemo(
        message: String,
        sharedKey: ByteArray
    ): WhisperMemo = withContext(Dispatchers.Default) {
        require(sharedKey.size == AES_KEY_BYTES) {
            "sharedKey must be $AES_KEY_BYTES bytes (got ${sharedKey.size})"
        }

        val nonce = ByteArray(GCM_NONCE_BYTES).also { SECURE_RANDOM.nextBytes(it) }
        val plaintextBytes = message.toByteArray(Charsets.UTF_8)

        val cipher = Cipher.getInstance(AES_GCM_TRANSFORM)
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(sharedKey, "AES"),
            GCMParameterSpec(GCM_TAG_BITS, nonce)
        )
        val ciphertextWithTag = cipher.doFinal(plaintextBytes)

        // Bundle: nonce || ciphertext || tag (the JCE returns ciphertext||tag)
        val bundle = ByteArray(nonce.size + ciphertextWithTag.size).also {
            System.arraycopy(nonce, 0, it, 0, nonce.size)
            System.arraycopy(ciphertextWithTag, 0, it, nonce.size, ciphertextWithTag.size)
        }
        // URL-safe base64 (no padding) so it survives URL contexts cleanly
        val payload = WIRE_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bundle)

        WhisperMemo(
            plaintext = message,
            encryptedPayload = payload,
            instruction = "Memo Instruction: $payload"
        )
    }

    /**
     * Decrypt a Whisper memo payload. Returns the plaintext UTF-8 string.
     *
     * @throws IrisWhisperVersionException if the payload is v1 (broken
     *   prior encoding — must be re-encrypted).
     * @throws IrisException if the payload is malformed or the GCM tag
     *   doesn't verify (wrong key, tampered ciphertext, or truncated input).
     */
    suspend fun decryptMemo(
        encryptedPayload: String,
        sharedKey: ByteArray
    ): String = withContext(Dispatchers.Default) {
        require(sharedKey.size == AES_KEY_BYTES) {
            "sharedKey must be $AES_KEY_BYTES bytes (got ${sharedKey.size})"
        }

        if (encryptedPayload.startsWith("whisper:v1:")) {
            throw IrisWhisperVersionException(
                "v1 Whisper payloads were not actually encrypted (mock crypto). " +
                    "Treat as compromised and re-issue under v2."
            )
        }
        if (!encryptedPayload.startsWith(WIRE_PREFIX)) {
            throw IrisException("Not a valid v2 Whisper memo (missing $WIRE_PREFIX prefix)")
        }

        val bundle = try {
            Base64.getUrlDecoder().decode(encryptedPayload.removePrefix(WIRE_PREFIX))
        } catch (e: IllegalArgumentException) {
            throw IrisException("Whisper payload is not valid base64: ${e.message}")
        }
        if (bundle.size < GCM_NONCE_BYTES + GCM_TAG_BYTES) {
            throw IrisException("Whisper payload too short (got ${bundle.size} bytes)")
        }

        val nonce = bundle.copyOfRange(0, GCM_NONCE_BYTES)
        val ciphertextWithTag = bundle.copyOfRange(GCM_NONCE_BYTES, bundle.size)

        try {
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORM)
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(sharedKey, "AES"),
                GCMParameterSpec(GCM_TAG_BITS, nonce)
            )
            val plaintextBytes = cipher.doFinal(ciphertextWithTag)
            String(plaintextBytes, Charsets.UTF_8)
        } catch (e: javax.crypto.AEADBadTagException) {
            throw IrisException("Whisper decryption failed: GCM tag mismatch (wrong key or tampered payload)")
        } catch (e: Exception) {
            throw IrisException("Whisper decryption failed: ${e.message}")
        }
    }

    companion object {
        /** Wire-format version prefix. v1 is intentionally rejected. */
        const val WIRE_PREFIX = "whisper:v2:"

        /** AES-256 key length. */
        const val AES_KEY_BYTES = 32

        /** AES-GCM nonce length per NIST SP 800-38D §5.2.1.1. */
        const val GCM_NONCE_BYTES = 12

        /** GCM authentication tag length in bits (128 bits = 16 bytes). */
        const val GCM_TAG_BITS = 128
        const val GCM_TAG_BYTES = GCM_TAG_BITS / 8

        private const val AES_GCM_TRANSFORM = "AES/GCM/NoPadding"

        /** Shared CSPRNG (constructing SecureRandom is expensive, this is the JCE pattern). */
        private val SECURE_RANDOM = SecureRandom()

        /**
         * Derive a 32-byte AES key from a passphrase using PBKDF2-HMAC-SHA256.
         * Use this for shared secrets agreed out-of-band (e.g. via a separate
         * messaging app). 600,000 iterations matches OWASP 2024 guidance.
         *
         * @param salt Random 16+ bytes; MUST be unique per user/session.
         *   Re-using salt across users defeats PBKDF2.
         */
        fun deriveKeyFromPassphrase(
            passphrase: CharArray,
            salt: ByteArray,
            iterations: Int = 600_000
        ): ByteArray {
            require(salt.size >= 16) { "salt must be ≥ 16 bytes (got ${salt.size})" }
            require(iterations >= 100_000) {
                "iterations must be ≥ 100,000 (got $iterations) — defends against offline brute-force"
            }
            val spec = PBEKeySpec(passphrase, salt, iterations, AES_KEY_BYTES * 8)
            return try {
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).encoded
            } finally {
                spec.clearPassword() // best effort to scrub passphrase from memory
            }
        }

        /**
         * HKDF-style key derivation: stretch a [sharedSecret] (e.g. an X25519
         * ECDH output) into an AES-256 key bound to a [context] string.
         *
         * Implementation is HKDF-Expand only (assumes [sharedSecret] is
         * already uniformly random — true for X25519 ECDH outputs). For raw
         * non-uniform inputs (e.g. a low-entropy passphrase), use
         * [deriveKeyFromPassphrase] instead.
         */
        fun deriveKeyFromX25519(
            sharedSecret: ByteArray,
            context: String = "iris-whisper-v2"
        ): ByteArray {
            require(sharedSecret.size in 16..64) {
                "sharedSecret must be 16-64 bytes (got ${sharedSecret.size})"
            }
            // HKDF-Expand: T(1) = HMAC(prk, info || 0x01)
            val mac = javax.crypto.Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(sharedSecret, "HmacSHA256"))
            mac.update(context.toByteArray(Charsets.UTF_8))
            mac.update(0x01)
            return mac.doFinal().copyOfRange(0, AES_KEY_BYTES)
        }
    }
}

/**
 * Decrypted memo payload + the on-chain instruction string. The [plaintext]
 * field is convenience for callers that just produced the memo and don't
 * want to round-trip through `decryptMemo` to confirm.
 */
@Serializable
data class WhisperMemo(
    val plaintext: String,
    val encryptedPayload: String,
    val instruction: String
)

/**
 * Thrown when a v1 Whisper payload is encountered. v1 used mock crypto
 * (string reversal) and provided no actual confidentiality. Decryption
 * is refused so callers can't accidentally treat v1 output as private.
 */
class IrisWhisperVersionException(message: String) : RuntimeException(message)
