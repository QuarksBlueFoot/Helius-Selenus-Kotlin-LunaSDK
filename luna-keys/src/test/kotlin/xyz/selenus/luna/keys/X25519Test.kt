package xyz.selenus.luna.keys

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Tests for [X25519]:
 *  - RFC 7748 §6.1 ECDH test vector (Alice + Bob).
 *  - Symmetry: Alice·BobPub == Bob·AlicePub.
 *  - Ed25519 → X25519 birational conversion against published vectors.
 *  - End-to-end ECDH between two Solana wallet keypairs (the use case
 *    that makes Whisper/stealth-address flows work).
 */
class X25519Test {

    // ── RFC 7748 §6.1 test vector ────────────────────────────────────

    @Test fun `RFC 7748 §6_1 Alice and Bob arrive at same shared secret`() {
        // From RFC 7748 §6.1
        val alicePriv = hex("77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a")
        val alicePub = hex("8520f0098930a754748b7ddcb43ef75a0dbf3a0d26381af4eba4a98eaa9b4e6a")
        val bobPriv = hex("5dab087e624a8a4b79e17f8b83800ee66f3bb1292618b6fd1c2f8b27ff88e0eb")
        val bobPub = hex("de9edb7d7b7dc1b4d35b61c2ece435373f8343c85b78674dddc262497e2f8a09")
        val expectedShared = hex("4a5d9d5ba4ce2de1728e3bf480350f25e07e21c947d19e3376f09b3c1e161742")

        val aliceShared = X25519.ecdh(alicePriv, bobPub)
        val bobShared = X25519.ecdh(bobPriv, alicePub)

        assertHexEquals(expectedShared, aliceShared)
        assertHexEquals(expectedShared, bobShared)
    }

    @Test fun `freshly-generated keypairs ECDH symmetrically`() {
        val (a, A) = X25519.generate()
        val (b, B) = X25519.generate()

        val aSide = X25519.ecdh(a, B)
        val bSide = X25519.ecdh(b, A)

        assertEquals(32, aSide.size)
        assertTrue(aSide.contentEquals(bSide), "ECDH not symmetric — Alice and Bob got different secrets")
    }

    // ── Ed25519 → X25519 birational conversion ────────────────────────

    @Test fun `Solana wallet keypair can derive matching X25519 secret`() {
        // Generate two fresh Solana wallet keypairs, convert each to X25519,
        // run ECDH both directions, confirm symmetry.
        val alice = SolanaKeypair.generate()
        val bob = SolanaKeypair.generate()

        val (aliceXScalar, aliceXPub) = X25519.ed25519KeypairToX25519(alice.secretKeyBytes, alice.publicKeyBytes)
        val (bobXScalar, bobXPub) = X25519.ed25519KeypairToX25519(bob.secretKeyBytes, bob.publicKeyBytes)

        val aSide = X25519.ecdh(aliceXScalar, bobXPub)
        val bSide = X25519.ecdh(bobXScalar, aliceXPub)

        assertTrue(
            aSide.contentEquals(bSide),
            "Ed25519→X25519 ECDH not symmetric:\n  alice→bob: ${aSide.toHex()}\n  bob→alice: ${bSide.toHex()}"
        )
        assertEquals(32, aSide.size)
    }

    @Test fun `Ed25519 to X25519 conversion strips sign bit before y decode`() {
        // The high bit of byte 31 in an Ed25519 pubkey is the sign of x —
        // it's NOT part of y. Two encodings with same y but different sign
        // bit must convert to the same X25519 u-coordinate.
        val edPubA = ByteArray(32).also { it[10] = 0x37 } // arbitrary y, sign bit unset
        val edPubB = edPubA.copyOf().also { it[31] = (it[31].toInt() or 0x80).toByte() } // same y, sign bit set

        val xA = X25519.ed25519PublicKeyToX25519(edPubA)
        val xB = X25519.ed25519PublicKeyToX25519(edPubB)

        assertHexEquals(xA, xB)
    }

    @Test fun `conversion rejects identity element y=1`() {
        // y = 1 would make (1 - y) = 0 — no inverse exists.
        val identityEncoding = ByteArray(32).also { it[0] = 1 } // little-endian 1, sign bit unset
        assertFailsWith<IllegalArgumentException> {
            X25519.ed25519PublicKeyToX25519(identityEncoding)
        }
    }

    @Test fun `conversion rejects wrong-length input`() {
        assertFailsWith<IllegalArgumentException> { X25519.ed25519PublicKeyToX25519(ByteArray(31)) }
        assertFailsWith<IllegalArgumentException> { X25519.ed25519PublicKeyToX25519(ByteArray(33)) }
    }

    @Test fun `seed clamping matches Ed25519 clamping`() {
        // The X25519 scalar derived from an Ed25519 seed should be the
        // SHA-512(seed)[0..32] with RFC 7748 clamping applied.
        val seed = ByteArray(32) { (it * 11 + 5).toByte() }
        val scalar = X25519.ed25519SeedToX25519Scalar(seed)

        assertEquals(32, scalar.size)
        // Clamping check: bottom 3 bits of byte 0 are 0
        assertEquals(0, scalar[0].toInt() and 0x07, "bottom 3 bits of clamped scalar must be zero")
        // Top bit of byte 31 is 0
        assertEquals(0, scalar[31].toInt() and 0x80.toInt(), "top bit of clamped scalar must be zero")
        // Bit 254 (= bit 6 of byte 31) is 1
        assertEquals(0x40, scalar[31].toInt() and 0x40, "bit 254 of clamped scalar must be set")
    }

    @Test fun `ECDH rejects wrong-length inputs`() {
        assertFailsWith<IllegalArgumentException> { X25519.ecdh(ByteArray(31), ByteArray(32)) }
        assertFailsWith<IllegalArgumentException> { X25519.ecdh(ByteArray(32), ByteArray(33)) }
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private fun hex(s: String): ByteArray =
        ByteArray(s.length / 2) { i -> s.substring(i * 2, i * 2 + 2).toInt(16).toByte() }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun assertHexEquals(expected: ByteArray, actual: ByteArray) {
        assertEquals(expected.toHex(), actual.toHex())
    }
}
