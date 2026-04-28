package xyz.selenus.luna.keys

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Tests [Slip10] against the [SLIP-0010 Ed25519 test vectors](https://github.com/satoshilabs/slips/blob/master/slip-0010.md#test-vectors-for-ed25519).
 *
 * If any of these fail, our HD derivation diverges from every other Solana
 * wallet (Phantom, Solflare, Backpack, the Solana CLI). Wallet imports would
 * silently produce different keys and users would lose access to funds.
 */
class Slip10Test {

    // ── SLIP-0010 official test vector 1 ─────────────────────────────

    /**
     * From SLIP-0010 §"Test vector 1 for ed25519":
     *   seed (hex) = 000102030405060708090a0b0c0d0e0f
     *   m       → fingerprint=00000000  chain=90046a93de5380a72b5e45010748567d5ea02bbf6522f979e05c0d8d8ca9fffb  key=2b4be7f19ee27bbf30c667b642d5f4aa69fd169872f8fc3059c08ebae2eb19e7
     *   m/0'    → key=68e0fe46dfb67e368c75379acec591dab19df41a06ab5b3afe0aa86c2cf18d6f
     *   m/0'/1' → key=b1d0bad404bf35da785a64ca1ac54b2617211d2777696fbffaf208f746ae84f2
     */
    @Test fun `SLIP-0010 vector 1 master`() {
        val seed = hex("000102030405060708090a0b0c0d0e0f")
        val k = Slip10.derivePath(seed, "m")
        assertHexEquals(hex("2b4be7f19ee27bbf30c667b642d5f4aa69fd169872f8fc3059c08ebae2eb19e7"), k.privateScalar)
        assertHexEquals(hex("90046a93de5380a72b5e45010748567d5ea02bbf6522f979e05c0d8d8ca9fffb"), k.chainCode)
    }

    @Test fun `SLIP-0010 vector 1 m_0H`() {
        val seed = hex("000102030405060708090a0b0c0d0e0f")
        val k = Slip10.derivePath(seed, "m/0'")
        assertHexEquals(hex("68e0fe46dfb67e368c75379acec591dab19df41a06ab5b3afe0aa86c2cf18d6f"), k.privateScalar)
    }

    @Test fun `SLIP-0010 vector 1 m_0H_1H`() {
        val seed = hex("000102030405060708090a0b0c0d0e0f")
        val k = Slip10.derivePath(seed, "m/0'/1'")
        assertHexEquals(hex("b1d0bad404bf35da785a64ca1ac54b2617211d2777696fbffaf208f746ae84f2"), k.privateScalar)
    }

    @Test fun `SLIP-0010 vector 1 deep path m_0H_1H_2H_2H_1000000000H`() {
        // Full vector path from SLIP-0010
        val seed = hex("000102030405060708090a0b0c0d0e0f")
        val k = Slip10.derivePath(seed, "m/0'/1'/2'/2'/1000000000'")
        assertHexEquals(hex("8f94d394a8e8fd6b1bc2f3f49f5c47e385281d5c17e65324b0f62483e37e8793"), k.privateScalar)
    }

    // ── SLIP-0010 official test vector 2 (longer seed) ───────────────

    @Test fun `SLIP-0010 vector 2 master`() {
        val seed = hex(
            "fffcf9f6f3f0edeae7e4e1dedbd8d5d2cfccc9c6c3c0bdbab7b4b1aeaba8a5a29f9c999693908d8a8784817e7b7875726f6c696663605d5a5754514e4b484542"
        )
        val k = Slip10.derivePath(seed, "m")
        assertHexEquals(hex("171cb88b1b3c1db25add599712e36245d75bc65a1a5c9e18d76f9f2b1eab4012"), k.privateScalar)
    }

    @Test fun `SLIP-0010 vector 2 m_0H`() {
        val seed = hex(
            "fffcf9f6f3f0edeae7e4e1dedbd8d5d2cfccc9c6c3c0bdbab7b4b1aeaba8a5a29f9c999693908d8a8784817e7b7875726f6c696663605d5a5754514e4b484542"
        )
        val k = Slip10.derivePath(seed, "m/0'")
        assertHexEquals(hex("1559eb2bbec5790b0c65d8693e4d0875b1747f4970ae8b650486ed7470845635"), k.privateScalar)
    }

    // ── Path parsing ─────────────────────────────────────────────────

    @Test fun `non-hardened path component is rejected`() {
        val seed = hex("000102030405060708090a0b0c0d0e0f")
        assertFailsWith<Slip10NonHardenedException> {
            Slip10.derivePath(seed, "m/44/501'/0'/0'") // 44 missing apostrophe
        }
        assertFailsWith<Slip10NonHardenedException> {
            Slip10.derivePath(seed, "m/44'/501/0'/0'") // 501 missing apostrophe
        }
    }

    @Test fun `path must start with m`() {
        val seed = hex("000102030405060708090a0b0c0d0e0f")
        assertFailsWith<IllegalArgumentException> {
            Slip10.derivePath(seed, "44'/501'/0'/0'") // missing 'm/'
        }
    }

    @Test fun `seed length is bounded`() {
        assertFailsWith<IllegalArgumentException> {
            Slip10.derivePath(ByteArray(15), "m") // too short
        }
        assertFailsWith<IllegalArgumentException> {
            Slip10.derivePath(ByteArray(65), "m") // too long
        }
    }

    @Test fun `path component out of range is rejected`() {
        val seed = hex("000102030405060708090a0b0c0d0e0f")
        assertFailsWith<IllegalArgumentException> {
            // 2^31 (= 0x80000000) wrapped through the apostrophe is out of range
            Slip10.derivePath(seed, "m/2147483648'")
        }
    }

    // ── Solana convenience helper ────────────────────────────────────

    @Test fun `derivePhantomAccount returns a working SolanaKeypair`() {
        val seed = hex("000102030405060708090a0b0c0d0e0f")
        val account0 = Slip10.derivePhantomAccount(seed, 0)
        val account1 = Slip10.derivePhantomAccount(seed, 1)

        // Different account indices must produce different keypairs.
        assertNotEquals(account0.publicKeyBase58, account1.publicKeyBase58)

        // Each derived keypair must round-trip sign + verify.
        val msg = "test".toByteArray()
        assertTrue(account0.verify(msg, account0.sign(msg)))
        assertTrue(account1.verify(msg, account1.sign(msg)))
    }

    @Test fun `derivePhantomAccount is deterministic for same seed and index`() {
        val seed = hex("000102030405060708090a0b0c0d0e0f")
        val a = Slip10.derivePhantomAccount(seed, 7)
        val b = Slip10.derivePhantomAccount(seed, 7)
        assertEquals(a.publicKeyBase58, b.publicKeyBase58)
        assertTrue(a.secretKeyBytes.contentEquals(b.secretKeyBytes))
    }

    @Test fun `derivePhantomAccount rejects negative index`() {
        val seed = hex("000102030405060708090a0b0c0d0e0f")
        assertFailsWith<IllegalArgumentException> {
            Slip10.derivePhantomAccount(seed, -1)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private fun hex(s: String): ByteArray =
        ByteArray(s.length / 2) { i -> s.substring(i * 2, i * 2 + 2).toInt(16).toByte() }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun assertHexEquals(expected: ByteArray, actual: ByteArray) {
        assertEquals(expected.toHex(), actual.toHex())
    }
}
