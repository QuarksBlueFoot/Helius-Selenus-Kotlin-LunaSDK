package xyz.selenus.luna.keys

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Contract tests for the [StealthAddress] toolkit.
 *
 * The crucial property: **sender derive + recipient scan converge on the
 * same stealth address**, and the recipient gets back a spending scalar
 * such that `spendingScalar · G == stealthAddress` (proving they can sign
 * for it).
 */
class StealthAddressTest {

    // Per-test fresh meta-address (recipient's two keypairs)
    private fun freshMeta(): Triple<SolanaKeypair, SolanaKeypair, StealthAddress.MetaAddress> {
        val spending = SolanaKeypair.generate()
        val viewing = SolanaKeypair.generate()
        return Triple(
            spending,
            viewing,
            StealthAddress.MetaAddress(
                spendingPublicKey = spending.publicKeyBytes,
                viewingPublicKey = viewing.publicKeyBytes
            )
        )
    }

    // ── Core protocol round-trip ──────────────────────────────────────

    @Test fun `sender derive and recipient scan converge on the same address`() {
        val (spending, viewing, meta) = freshMeta()

        // Sender side
        val envelope = StealthAddress.derive(meta)

        // Recipient side: scan with viewing key + spending key
        val match = StealthAddress.scan(
            viewingSecretSeed = viewing.secretKeyBytes,
            spendingSecretSeed = spending.secretKeyBytes,
            ephemeralPublicKey = envelope.ephemeralPublicKey,
            observedRecipientAddress = envelope.stealthAddress
        )

        assertNotNull(match, "recipient failed to detect the stealth address derived for them")
        assertTrue(envelope.stealthAddress.contentEquals(match.stealthAddress))
    }

    @Test fun `recovered spending scalar can derive the stealth pubkey`() {
        // Property: scan returns spendingScalar such that spendingScalar·G == stealthAddress.
        val (spending, viewing, meta) = freshMeta()
        val envelope = StealthAddress.derive(meta)

        val match = StealthAddress.scan(
            viewing.secretKeyBytes,
            spending.secretKeyBytes,
            envelope.ephemeralPublicKey,
            envelope.stealthAddress
        )!!

        // Multiply the recovered scalar by the base point and confirm it
        // equals the stealth address.
        val scalarAsBig = leToBigInt(match.spendingScalar)
        val derivedPub = Ed25519Derive.scalarMultiplyBaseBytes(scalarAsBig)

        assertTrue(
            derivedPub.contentEquals(envelope.stealthAddress),
            "spendingScalar·G != stealthAddress — recipient cannot actually sign for this address"
        )
    }

    @Test fun `unrelated recipient cannot scan for someone else's stealth address`() {
        val (_, _, aliceMeta) = freshMeta()
        val (mallorySpending, malloryViewing, _) = freshMeta()

        val envelope = StealthAddress.derive(aliceMeta)

        // Mallory uses her own keys — she shouldn't find a match.
        val match = StealthAddress.scan(
            malloryViewing.secretKeyBytes,
            mallorySpending.secretKeyBytes,
            envelope.ephemeralPublicKey,
            envelope.stealthAddress
        )

        assertNull(match, "Mallory should not be able to scan for Alice's stealth addresses")
    }

    @Test fun `each derive produces a different stealth address even for same recipient`() {
        // Property: two independent derive() calls for the same meta-address
        // produce different stealth addresses (and different ephemerals).
        val (_, _, meta) = freshMeta()
        val a = StealthAddress.derive(meta)
        val b = StealthAddress.derive(meta)

        assertTrue(!a.stealthAddress.contentEquals(b.stealthAddress),
            "two derive() calls produced the same stealth address — ephemeral re-use!")
        assertTrue(!a.ephemeralPublicKey.contentEquals(b.ephemeralPublicKey),
            "two derive() calls produced the same ephemeral pubkey")
    }

    @Test fun `deterministic derivation with explicit ephemeral keypair`() {
        // Sender supplies their own ephemeral keypair → deterministic stealth address.
        val (_, _, meta) = freshMeta()
        val ephemeral = SolanaKeypair.generate()

        val a = StealthAddress.derive(meta, ephemeral)
        val b = StealthAddress.derive(meta, ephemeral)

        assertTrue(a.stealthAddress.contentEquals(b.stealthAddress))
        assertTrue(a.ephemeralPublicKey.contentEquals(b.ephemeralPublicKey))
    }

    // ── Multiple-transfer scanning (typical recipient usage) ──────────

    @Test fun `recipient correctly distinguishes their transfers from noise`() {
        // Simulate 5 transfers: 2 to me, 3 to other people. Confirm I find
        // exactly the 2 mine.
        val (mySpend, myView, myMeta) = freshMeta()
        val (_, _, theirMeta1) = freshMeta()
        val (_, _, theirMeta2) = freshMeta()

        val envelopes = listOf(
            StealthAddress.derive(myMeta),       // mine
            StealthAddress.derive(theirMeta1),   // not mine
            StealthAddress.derive(myMeta),       // mine
            StealthAddress.derive(theirMeta2),   // not mine
            StealthAddress.derive(theirMeta1)    // not mine
        )

        val mine = envelopes.mapNotNull { env ->
            StealthAddress.scan(
                myView.secretKeyBytes,
                mySpend.secretKeyBytes,
                env.ephemeralPublicKey,
                env.stealthAddress
            )
        }

        assertEquals(2, mine.size, "should have found exactly 2 stealth addresses")
        assertTrue(mine[0].stealthAddress.contentEquals(envelopes[0].stealthAddress))
        assertTrue(mine[1].stealthAddress.contentEquals(envelopes[2].stealthAddress))
    }

    // ── Validation ───────────────────────────────────────────────────

    @Test fun `MetaAddress rejects wrong-length keys`() {
        assertFailsWith<IllegalArgumentException> {
            StealthAddress.MetaAddress(spendingPublicKey = ByteArray(31), viewingPublicKey = ByteArray(32))
        }
        assertFailsWith<IllegalArgumentException> {
            StealthAddress.MetaAddress(spendingPublicKey = ByteArray(32), viewingPublicKey = ByteArray(33))
        }
    }

    @Test fun `scan rejects wrong-length inputs`() {
        val (spending, viewing, _) = freshMeta()
        assertFailsWith<IllegalArgumentException> {
            StealthAddress.scan(
                viewingSecretSeed = ByteArray(31),
                spendingSecretSeed = spending.secretKeyBytes,
                ephemeralPublicKey = ByteArray(32),
                observedRecipientAddress = ByteArray(32)
            )
        }
    }

    // ── sharedScalar exposed for memo encryption ──────────────────────

    @Test fun `sharedScalar is symmetric between sender and recipient`() {
        val (spending, viewing, meta) = freshMeta()
        val envelope = StealthAddress.derive(meta)

        // Recipient recomputes the shared scalar via the hashToScalar path:
        val vXScalar = X25519.ed25519SeedToX25519Scalar(viewing.secretKeyBytes)
        val rXPub = X25519.ed25519PublicKeyToX25519(envelope.ephemeralPublicKey)
        val sharedSecret = X25519.ecdh(vXScalar, rXPub)
        val recipientH = StealthAddress.hashToScalar(sharedSecret)

        val recipientScalarBytes = ByteArray(32).also { dst ->
            val be = recipientH.toByteArray()
            val srcStart = if (be.size > 32) be.size - 32 else 0
            val srcLen = be.size - srcStart
            for (i in 0 until srcLen) dst[i] = be[srcStart + srcLen - 1 - i]
        }

        assertTrue(
            envelope.sharedScalar.contentEquals(recipientScalarBytes),
            "sender and recipient computed different sharedScalars"
        )

        // Suppress unused warning — `spending` is fixture
        @Suppress("UNUSED_VARIABLE") val _ignore = spending
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private fun leToBigInt(le: ByteArray): java.math.BigInteger {
        val be = ByteArray(le.size + 1)
        for (i in le.indices) be[i + 1] = le[le.size - 1 - i]
        return java.math.BigInteger(be)
    }
}
