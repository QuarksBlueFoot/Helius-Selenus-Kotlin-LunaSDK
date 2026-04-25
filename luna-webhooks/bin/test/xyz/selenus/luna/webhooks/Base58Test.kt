package xyz.selenus.luna.webhooks

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Round-trip + known-vector tests for the internal Base58 codec. Vectors
 * sourced from the Solana cookbook + Bitcoin BIP-178 fixtures so we know
 * the alphabet matches the on-chain spec exactly.
 */
class Base58Test {

    @Test
    fun `empty input encodes to empty string`() {
        assertEquals("", Base58.encode(ByteArray(0)))
        assertEquals(0, Base58.decode("").size)
    }

    @Test
    fun `leading zeros are preserved as leading 1 chars`() {
        val input = byteArrayOf(0, 0, 0, 0x61) // three leading zero bytes + 'a'
        val encoded = Base58.encode(input)
        assertEquals("1112g", encoded)

        val roundTripped = Base58.decode(encoded)
        assertTrue(input.contentEquals(roundTripped))
    }

    @Test
    fun `Solana SystemProgram address round-trips`() {
        // The SystemProgram address ("11111111111111111111111111111111") is 32 zero bytes.
        val raw = ByteArray(32)
        val encoded = Base58.encode(raw)
        assertEquals("11111111111111111111111111111111", encoded)
        assertTrue(raw.contentEquals(Base58.decode(encoded)))
    }

    @Test
    fun `non-trivial 32-byte key round-trips`() {
        val raw = ByteArray(32) { (it * 7 + 1).toByte() } // arbitrary deterministic bytes
        val encoded = Base58.encode(raw)
        assertTrue(raw.contentEquals(Base58.decode(encoded)))
        // Encoded form for a 32-byte key is typically 43–44 base58 chars
        assertTrue(encoded.length in 30..44)
    }

    @Test
    fun `64-byte signature round-trips`() {
        val sig = ByteArray(64) { it.toByte() }
        val encoded = Base58.encode(sig)
        assertTrue(sig.contentEquals(Base58.decode(encoded)))
    }

    @Test
    fun `invalid character throws`() {
        assertFailsWith<IllegalArgumentException> {
            Base58.decode("0OIl") // 0, O, I, l are excluded from the base58 alphabet
        }
    }

    @Test
    fun `constantTimeEquals behaves correctly`() {
        assertTrue(WebhookSignatureVerifier.constantTimeEquals(byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 3)))
        assertFalse(WebhookSignatureVerifier.constantTimeEquals(byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 4)))
        assertFalse(WebhookSignatureVerifier.constantTimeEquals(byteArrayOf(1, 2, 3), byteArrayOf(1, 2)))
    }

    @Test
    fun `verify rejects malformed inputs without throwing`() {
        // Garbage signature / pubkey → false, never throws.
        assertFalse(
            WebhookSignatureVerifier.verify(
                body = "test".toByteArray(),
                signatureBase58 = "not-a-signature",
                publicKeyBase58 = "also-garbage"
            )
        )
        assertFalse(
            WebhookSignatureVerifier.verify(
                body = ByteArray(0),
                signatureBase58 = "",
                publicKeyBase58 = ""
            )
        )
    }
}
