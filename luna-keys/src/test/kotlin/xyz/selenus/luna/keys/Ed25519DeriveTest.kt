package xyz.selenus.luna.keys

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Validates [Ed25519Derive] against the canonical [RFC 8032 §7.1
 * test vectors](https://datatracker.ietf.org/doc/html/rfc8032#section-7.1).
 *
 * Each vector is a `(seed, expected-publicKey)` pair. If any of these fail
 * we have a bug in field arithmetic, point arithmetic, or the encoding step.
 *
 * Also includes round-trip tests against [SolanaKeypair.generate]: derive
 * the public key from a freshly-generated seed and confirm it matches what
 * the JDK gave us during generation. This catches subtle issues that the
 * static RFC vectors would miss (wrong endianness on edge cases, etc.).
 */
class Ed25519DeriveTest {

    // ── RFC 8032 §7.1 test vectors ────────────────────────────────────

    @Test fun `RFC 8032 vector 1`() {
        val seed = hex("9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60")
        val expected = hex("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a")
        assertHexEquals(expected, Ed25519Derive.publicKeyFromSeed(seed))
    }

    @Test fun `RFC 8032 vector 2`() {
        val seed = hex("4ccd089b28ff96da9db6c346ec114e0f5b8a319f35aba624da8cf6ed4fb8a6fb")
        val expected = hex("3d4017c3e843895a92b70aa74d1b7ebc9c982ccf2ec4968cc0cd55f12af4660c")
        assertHexEquals(expected, Ed25519Derive.publicKeyFromSeed(seed))
    }

    @Test fun `RFC 8032 vector 3`() {
        val seed = hex("c5aa8df43f9f837bedb7442f31dcb7b166d38535076f094b85ce3a2e0b4458f7")
        val expected = hex("fc51cd8e6218a1a38da47ed00230f0580816ed13ba3303ac5deb911548908025")
        assertHexEquals(expected, Ed25519Derive.publicKeyFromSeed(seed))
    }

    /** RFC 8032 vector 4 (long message) — same key derivation step. */
    @Test fun `RFC 8032 vector 4`() {
        val seed = hex("f5e5767cf153319517630f226876b86c8160cc583bc013744c6bf255f5cc0ee5")
        val expected = hex("278117fc144c72340f67d0f2316e8386ceffbf2b2428c9c51fef7c597f1d426e")
        assertHexEquals(expected, Ed25519Derive.publicKeyFromSeed(seed))
    }

    /** RFC 8032 vector 5 (SHA-of-abc) — derivation step still uses just the seed. */
    @Test fun `RFC 8032 vector 5`() {
        val seed = hex("833fe62409237b9d62ec77587520911e9a759cec1d19755b7da901b96dca3d42")
        val expected = hex("ec172b93ad5e563bf4932c70e1245034c35467ef2efd4d64ebf819683467e2bf")
        assertHexEquals(expected, Ed25519Derive.publicKeyFromSeed(seed))
    }

    // ── Round-trip against JDK keypair generator ──────────────────────

    @Test fun `derivation matches JDK Ed25519 generate for fresh keypairs`() {
        // Generate 10 fresh keypairs, take their seeds, derive the public
        // key, and check it matches what the JDK gave us.
        repeat(10) {
            val kp = SolanaKeypair.generate()
            val derived = Ed25519Derive.publicKeyFromSeed(kp.secretKeyBytes)
            assertTrue(
                kp.publicKeyBytes.contentEquals(derived),
                "derived pubkey ${derived.toHex()} does not match generator's ${kp.publicKeyBytes.toHex()}"
            )
        }
    }

    @Test fun `fromSecretSeed round-trips through sign and verify`() {
        repeat(5) {
            val original = SolanaKeypair.generate()
            // Reconstruct from the seed alone.
            val reconstructed = SolanaKeypair.fromSecretSeed(original.secretKeyBytes)

            // Same key bytes
            assertTrue(original.secretKeyBytes.contentEquals(reconstructed.secretKeyBytes))
            assertTrue(original.publicKeyBytes.contentEquals(reconstructed.publicKeyBytes))

            // Sign with reconstructed, verify with original — they're the same key.
            val msg = "round-trip-$it".toByteArray()
            val sig = reconstructed.sign(msg)
            assertTrue(original.verify(msg, sig))
        }
    }

    @Test fun `fromSecretSeed rejects wrong length`() {
        assertFailsWith<IllegalArgumentException> { SolanaKeypair.fromSecretSeed(ByteArray(31)) }
        assertFailsWith<IllegalArgumentException> { SolanaKeypair.fromSecretSeed(ByteArray(33)) }
    }

    @Test fun `derivation produces a point that lies on the curve`() {
        // Property: every derived public key must satisfy the Ed25519 curve
        // equation. This catches encoding bugs where the y-bytes look right
        // but the implied x is bogus.
        repeat(5) {
            val seed = ByteArray(32) { (it * 17 + 3).toByte() }
            val pub = Ed25519Derive.publicKeyFromSeed(seed)
            assertTrue(
                Ed25519Curve.isOnCurve(pub),
                "derived pubkey ${pub.toHex()} from deterministic seed is not on Ed25519 curve"
            )
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private fun hex(s: String): ByteArray =
        ByteArray(s.length / 2) { i -> s.substring(i * 2, i * 2 + 2).toInt(16).toByte() }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }

    private fun assertHexEquals(expected: ByteArray, actual: ByteArray) {
        assertEquals(
            expected.toHex(),
            actual.toHex(),
            "byte arrays differ"
        )
    }
}
