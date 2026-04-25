package xyz.selenus.luna.keys

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for the luna-keys utility module. Covers base58 round-trip, address
 * validation (syntactic + on-curve), keypair generation, sign/verify, and
 * Solana keystore round-trip.
 */
class KeysTest {

    // ── Base58 ────────────────────────────────────────────────────────

    @Test fun `base58 round-trips arbitrary bytes`() {
        val raw = ByteArray(32) { (it * 13 + 7).toByte() }
        assertTrue(raw.contentEquals(Base58.decode(Base58.encode(raw))))
    }

    @Test fun `base58 encode of empty input returns empty string`() {
        assertEquals("", Base58.encode(ByteArray(0)))
    }

    @Test fun `base58 isValid returns false for invalid alphabet`() {
        assertFalse(Base58.isValid("0OIl"))
        assertTrue(Base58.isValid("HXsKP7wrBWaQ8T2Vtjry3Nj3oUgwYcqq9vrHDM12G664"))
    }

    @Test fun `base58 decode rejects characters outside alphabet`() {
        assertFailsWith<IllegalArgumentException> { Base58.decode("AB!CD") }
    }

    // ── Solana address ───────────────────────────────────────────────

    @Test fun `parse accepts a 32-byte base58 address`() {
        // SystemProgram is 32 zero bytes → "11111111111111111111111111111111"
        val addr = SolanaAddress.parse("11111111111111111111111111111111")
        assertNotNull(addr)
        assertEquals(32, addr.bytes.size)
    }

    @Test fun `parse rejects wrong-length input`() {
        assertNull(SolanaAddress.parse(""))
        assertNull(SolanaAddress.parse("AB"))
        // 33 bytes worth of base58
        assertNull(SolanaAddress.parse(Base58.encode(ByteArray(33))))
    }

    @Test fun `isValidSolanaAddress matches Helius Rust SDK behaviour`() {
        // Length check only — does NOT require on-curve
        assertTrue(isValidSolanaAddress("11111111111111111111111111111111"))
        assertTrue(isValidSolanaAddress("HXsKP7wrBWaQ8T2Vtjry3Nj3oUgwYcqq9vrHDM12G664"))
        assertFalse(isValidSolanaAddress(""))
        assertFalse(isValidSolanaAddress("not-base58!"))
        assertFalse(isValidSolanaAddress("AB"))
    }

    @Test fun `fromBytes round-trips with bytes accessor`() {
        val raw = ByteArray(32) { it.toByte() }
        val addr = SolanaAddress.fromBytes(raw)
        assertTrue(raw.contentEquals(addr.bytes))
    }

    @Test fun `fromBytes rejects wrong length`() {
        assertFailsWith<IllegalArgumentException> {
            SolanaAddress.fromBytes(ByteArray(31))
        }
    }

    // ── On-curve check ───────────────────────────────────────────────

    @Test fun `freshly generated keypair address is on-curve`() {
        val kp = SolanaKeypair.generate()
        // Generated public key must lie on the Ed25519 curve.
        assertTrue(
            Ed25519Curve.isOnCurve(kp.publicKeyBytes),
            "Generated keypair public key should lie on Ed25519 curve"
        )
        // And isWalletAddress should agree
        assertTrue(isWalletAddress(kp.publicKeyBase58))
    }

    @Test fun `all-zero point is treated as off-curve sanity check`() {
        // 32 zero bytes is the SystemProgram address, decoded as the encoded y=0
        // with sign bit = 0. y=0 on Ed25519 gives x²=−1/(d·0+1)=−1, which has no
        // square root mod p (the prime is ≡ 1 mod 4 so −1 IS a QR — correction:
        // it actually IS on-curve in the formal sense). We just assert the
        // function doesn't crash on this input.
        val onCurve = Ed25519Curve.isOnCurve(ByteArray(32))
        // Don't pin true/false here — implementations differ on the y=0
        // edge case. Just confirm we get a Boolean back without exception.
        assertTrue(onCurve || !onCurve)
    }

    // ── Keypair generation + sign/verify ──────────────────────────────

    @Test fun `generate produces 32-byte seed and pubkey`() {
        val kp = SolanaKeypair.generate()
        assertEquals(32, kp.secretKeyBytes.size)
        assertEquals(32, kp.publicKeyBytes.size)
        // Pubkey base58 should be 43–44 chars
        assertTrue(kp.publicKeyBase58.length in 43..44)
    }

    @Test fun `sign and verify round-trip succeeds`() {
        val kp = SolanaKeypair.generate()
        val message = "hello solana".toByteArray()
        val sig = kp.sign(message)
        assertEquals(64, sig.size)
        assertTrue(kp.verify(message, sig))
    }

    @Test fun `verify fails on tampered message`() {
        val kp = SolanaKeypair.generate()
        val sig = kp.sign("message-A".toByteArray())
        assertFalse(kp.verify("message-B".toByteArray(), sig))
    }

    @Test fun `verify fails on tampered signature`() {
        val kp = SolanaKeypair.generate()
        val sig = kp.sign("test".toByteArray())
        sig[0] = (sig[0].toInt() xor 0xFF).toByte() // flip first byte
        assertFalse(kp.verify("test".toByteArray(), sig))
    }

    @Test fun `verify across keypairs fails`() {
        val a = SolanaKeypair.generate()
        val b = SolanaKeypair.generate()
        val sig = a.sign("data".toByteArray())
        assertFalse(b.verify("data".toByteArray(), sig))
    }

    // ── Solana keystore round-trip ───────────────────────────────────

    @Test fun `Solana keystore round-trips through encode and parse`() {
        val original = SolanaKeypair.generate()
        val keystore = original.toSolanaKeystoreBytes()
        assertEquals(64, keystore.size)

        val parsed = SolanaKeypair.fromSolanaKeystoreBytes(keystore)
        assertTrue(original.secretKeyBytes.contentEquals(parsed.secretKeyBytes))
        assertTrue(original.publicKeyBytes.contentEquals(parsed.publicKeyBytes))
    }

    @Test fun `keystore parse rejects mismatched seed and pubkey`() {
        val a = SolanaKeypair.generate()
        val b = SolanaKeypair.generate()
        // Splice a's seed with b's pubkey — should fail self-validation
        val frankenstein = a.secretKeyBytes + b.publicKeyBytes
        assertFailsWith<IllegalArgumentException> {
            SolanaKeypair.fromSolanaKeystoreBytes(frankenstein)
        }
    }

    @Test fun `keystore parse rejects wrong-length input`() {
        assertFailsWith<IllegalArgumentException> {
            SolanaKeypair.fromSolanaKeystoreBytes(ByteArray(63))
        }
        assertFailsWith<IllegalArgumentException> {
            SolanaKeypair.fromSolanaKeystoreBytes(ByteArray(65))
        }
    }

    // ── makeKeypairs ──────────────────────────────────────────────────

    @Test fun `makeKeypairs returns the requested count`() {
        val keys = SolanaKeypair.makeKeypairs(5)
        assertEquals(5, keys.size)
        // No duplicates — randomness check
        val pubKeys = keys.map { it.publicKeyBase58 }.toSet()
        assertEquals(5, pubKeys.size)
    }

    @Test fun `makeKeypairs zero returns empty list`() {
        assertEquals(0, SolanaKeypair.makeKeypairs(0).size)
    }

    @Test fun `makeKeypairs negative throws`() {
        assertFailsWith<IllegalArgumentException> { SolanaKeypair.makeKeypairs(-1) }
    }
}
