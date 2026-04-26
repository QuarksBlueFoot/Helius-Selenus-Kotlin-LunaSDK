package com.selenus.iris

import kotlinx.coroutines.runBlocking
import java.security.SecureRandom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Contract tests for [IrisWhisperNamespace] v2 (real AES-GCM-256).
 *
 * Replaces the previous test surface that asserted only on
 * `startsWith("[ENCRYPTED:")` style placeholders. We now exercise the
 * actual cryptographic contract: `decrypt(encrypt(m, k), k) == m`,
 * decryption fails on tampered ciphertext, and v1 payloads are rejected.
 */
class IrisWhisperTest {

    private fun whisperApi(): IrisWhisperNamespace {
        // Construct a minimal client. The Namespace doesn't actually use the
        // network for encryption — the QuickNode endpoint URL is unused.
        val client = IrisQuickNodeClient(endpoint = "https://example.invalid/")
        return IrisWhisperNamespace(client)
    }

    private val freshKey: ByteArray
        get() = ByteArray(32).also { SecureRandom().nextBytes(it) }

    // ── Round-trip ────────────────────────────────────────────────────

    @Test fun `encrypt then decrypt returns the original plaintext`() = runBlocking {
        val whisper = whisperApi()
        val key = freshKey
        val message = "For February rent — please don't share"

        val memo = whisper.createPrivateMemo(message, key)
        val recovered = whisper.decryptMemo(memo.encryptedPayload, key)

        assertEquals(message, recovered)
    }

    @Test fun `unicode plaintext round-trips losslessly`() = runBlocking {
        val whisper = whisperApi()
        val key = freshKey
        val message = "🐦 Bluefoot Booby NFT — proceeds → Galápagos 🌊"

        val memo = whisper.createPrivateMemo(message, key)
        val recovered = whisper.decryptMemo(memo.encryptedPayload, key)

        assertEquals(message, recovered)
    }

    @Test fun `empty plaintext round-trips`() = runBlocking {
        val whisper = whisperApi()
        val key = freshKey

        val memo = whisper.createPrivateMemo("", key)
        assertEquals("", whisper.decryptMemo(memo.encryptedPayload, key))
    }

    // ── Confidentiality / non-determinism ─────────────────────────────

    @Test fun `same plaintext encrypted twice yields different ciphertexts`() = runBlocking {
        // GCM is randomized via the nonce — encrypting twice must produce
        // different ciphertexts. If they collide, the nonce is broken.
        val whisper = whisperApi()
        val key = freshKey
        val message = "deterministic input"

        val a = whisper.createPrivateMemo(message, key).encryptedPayload
        val b = whisper.createPrivateMemo(message, key).encryptedPayload

        assertNotEquals(a, b, "GCM nonce reuse — encrypting same plaintext twice produced same ciphertext")
    }

    @Test fun `payload uses v2 wire prefix`() = runBlocking {
        val memo = whisperApi().createPrivateMemo("hi", freshKey)
        assertTrue(memo.encryptedPayload.startsWith("whisper:v2:"),
            "expected v2 prefix, got: ${memo.encryptedPayload.take(20)}")
    }

    // ── Authentication failure modes ──────────────────────────────────

    @Test fun `decryption with wrong key throws on GCM tag mismatch`() = runBlocking {
        val whisper = whisperApi()
        val keyA = freshKey
        val keyB = freshKey

        val memo = whisper.createPrivateMemo("secret", keyA)
        assertFailsWith<IrisException> {
            whisper.decryptMemo(memo.encryptedPayload, keyB)
        }
    }

    @Test fun `decryption rejects tampered ciphertext`() = runBlocking {
        val whisper = whisperApi()
        val key = freshKey
        val memo = whisper.createPrivateMemo("original", key)

        // Flip a single character in the base64 portion (after the prefix)
        val prefix = "whisper:v2:"
        val payload = memo.encryptedPayload.removePrefix(prefix)
        // Pick a char in the middle and substitute a different valid base64 char
        val tampered = prefix + payload.substring(0, 10) +
            (if (payload[10] == 'A') 'B' else 'A') +
            payload.substring(11)

        assertFailsWith<IrisException> {
            whisper.decryptMemo(tampered, key)
        }
    }

    @Test fun `decryption rejects truncated payload`() = runBlocking {
        val whisper = whisperApi()
        val key = freshKey
        val memo = whisper.createPrivateMemo("original", key)

        // Lop off the last 10 chars (cuts into the GCM tag region)
        val truncated = memo.encryptedPayload.dropLast(10)

        assertFailsWith<IrisException> {
            whisper.decryptMemo(truncated, key)
        }
    }

    // ── v1 payload rejection ──────────────────────────────────────────

    @Test fun `decryption refuses v1 payloads`() = runBlocking {
        val whisper = whisperApi()
        val v1Payload = "whisper:v1:dGVzdA=="

        assertFailsWith<IrisWhisperVersionException> {
            whisper.decryptMemo(v1Payload, freshKey)
        }
    }

    @Test fun `decryption rejects payloads without scheme prefix`() = runBlocking {
        val whisper = whisperApi()
        assertFailsWith<IrisException> {
            whisper.decryptMemo("not-a-whisper-payload", freshKey)
        }
    }

    // ── Key length validation ─────────────────────────────────────────

    @Test fun `encryption rejects wrong-sized key`() = runBlocking {
        val whisper = whisperApi()
        assertFailsWith<IllegalArgumentException> {
            whisper.createPrivateMemo("hi", ByteArray(31))
        }
        assertFailsWith<IllegalArgumentException> {
            whisper.createPrivateMemo("hi", ByteArray(33))
        }
    }

    @Test fun `decryption rejects wrong-sized key`() = runBlocking {
        val whisper = whisperApi()
        val memo = whisper.createPrivateMemo("hi", freshKey)
        assertFailsWith<IllegalArgumentException> {
            whisper.decryptMemo(memo.encryptedPayload, ByteArray(31))
        }
    }

    // ── Key derivation helpers ────────────────────────────────────────

    @Test fun `passphrase derivation produces 32-byte key`() {
        val key = IrisWhisperNamespace.deriveKeyFromPassphrase(
            passphrase = "correct horse battery staple".toCharArray(),
            salt = ByteArray(16) { it.toByte() }
        )
        assertEquals(32, key.size)
    }

    @Test fun `passphrase derivation is deterministic for same inputs`() {
        val pass = "test-passphrase".toCharArray()
        val salt = ByteArray(16) { 7 }

        val a = IrisWhisperNamespace.deriveKeyFromPassphrase(pass.copyOf(), salt)
        val b = IrisWhisperNamespace.deriveKeyFromPassphrase(pass.copyOf(), salt)

        assertTrue(a.contentEquals(b))
    }

    @Test fun `passphrase derivation rejects short salt`() {
        assertFailsWith<IllegalArgumentException> {
            IrisWhisperNamespace.deriveKeyFromPassphrase(
                "x".toCharArray(),
                salt = ByteArray(15) // too short
            )
        }
    }

    @Test fun `passphrase derivation rejects low iteration counts`() {
        assertFailsWith<IllegalArgumentException> {
            IrisWhisperNamespace.deriveKeyFromPassphrase(
                "x".toCharArray(),
                salt = ByteArray(16),
                iterations = 1000 // way too low
            )
        }
    }

    @Test fun `X25519 derivation produces 32-byte key bound to context`() {
        val secret = ByteArray(32) { it.toByte() }
        val keyA = IrisWhisperNamespace.deriveKeyFromX25519(secret, context = "ctxA")
        val keyB = IrisWhisperNamespace.deriveKeyFromX25519(secret, context = "ctxB")

        assertEquals(32, keyA.size)
        assertEquals(32, keyB.size)
        assertTrue(!keyA.contentEquals(keyB), "context binding broken — same secret with different context produced same key")
    }

    @Test fun `derived key works end-to-end`() = runBlocking {
        val whisper = whisperApi()
        val derivedKey = IrisWhisperNamespace.deriveKeyFromPassphrase(
            "deterministic test pw".toCharArray(),
            ByteArray(16) { 42 },
            iterations = 100_000
        )

        val message = "End-to-end test"
        val memo = whisper.createPrivateMemo(message, derivedKey)
        val recovered = whisper.decryptMemo(memo.encryptedPayload, derivedKey)

        assertEquals(message, recovered)
    }

    // ── End-to-end ECDH between two Solana wallet keypairs ──────────

    @Test fun `Alice and Bob derive the same Whisper key from each other's wallet`() {
        val alice = xyz.selenus.luna.keys.SolanaKeypair.generate()
        val bob = xyz.selenus.luna.keys.SolanaKeypair.generate()

        // Alice computes from her seed + Bob's pubkey
        val aliceKey = IrisWhisperNamespace.deriveKeyFromWallets(
            myKeypair = alice,
            theirPublicKeyBytes = bob.publicKeyBytes
        )
        // Bob computes from his seed + Alice's pubkey
        val bobKey = IrisWhisperNamespace.deriveKeyFromWallets(
            myKeypair = bob,
            theirPublicKeyBytes = alice.publicKeyBytes
        )

        assertEquals(32, aliceKey.size)
        assertTrue(
            aliceKey.contentEquals(bobKey),
            "Whisper key derivation broken — Alice and Bob arrived at different keys"
        )
    }

    @Test fun `wallet-pair derived key encrypts and decrypts a memo end-to-end`() = runBlocking {
        val whisper = whisperApi()
        val alice = xyz.selenus.luna.keys.SolanaKeypair.generate()
        val bob = xyz.selenus.luna.keys.SolanaKeypair.generate()

        val aliceKey = IrisWhisperNamespace.deriveKeyFromWallets(alice, bob.publicKeyBytes)
        val bobKey = IrisWhisperNamespace.deriveKeyFromWallets(bob, alice.publicKeyBytes)

        // Alice encrypts → Bob decrypts using the symmetric ECDH-derived key
        val message = "Bluefoot Booby NFT proceeds: 25.5 SOL → Galápagos"
        val memo = whisper.createPrivateMemo(message, aliceKey)
        val recovered = whisper.decryptMemo(memo.encryptedPayload, bobKey)

        assertEquals(message, recovered)
    }

    @Test fun `wallet-pair derivation is context-bound`() {
        val alice = xyz.selenus.luna.keys.SolanaKeypair.generate()
        val bob = xyz.selenus.luna.keys.SolanaKeypair.generate()

        val keyChat = IrisWhisperNamespace.deriveKeyFromWallets(alice, bob.publicKeyBytes, context = "chat")
        val keyPayment = IrisWhisperNamespace.deriveKeyFromWallets(alice, bob.publicKeyBytes, context = "payment")

        assertTrue(!keyChat.contentEquals(keyPayment),
            "context binding broken — distinct contexts produced same key")
    }

    @Test fun `wallet-pair derivation rejects malformed pubkey`() {
        val alice = xyz.selenus.luna.keys.SolanaKeypair.generate()
        assertFailsWith<IllegalArgumentException> {
            IrisWhisperNamespace.deriveKeyFromWallets(alice, ByteArray(31)) // wrong length
        }
    }
}
