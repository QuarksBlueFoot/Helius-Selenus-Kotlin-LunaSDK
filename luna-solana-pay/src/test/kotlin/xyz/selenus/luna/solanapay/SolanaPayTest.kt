package xyz.selenus.luna.solanapay

import xyz.selenus.luna.keys.SolanaAddress
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SolanaPayTest {

    private val recipient = SolanaAddress.parse("HXsKP7wrBWaQ8T2Vtjry3Nj3oUgwYcqq9vrHDM12G664")!!
    private val usdcMint = SolanaAddress.parse("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v")!!
    private val ref1 = SolanaAddress.parse("11111111111111111111111111111111")!!
    private val ref2 = SolanaAddress.parse("So11111111111111111111111111111111111111112")!!

    // ── Transfer Request — encode ────────────────────────────────────

    @Test
    fun `simple SOL transfer encodes recipient only`() {
        val req = TransferRequest(recipient = recipient)
        assertEquals("solana:HXsKP7wrBWaQ8T2Vtjry3Nj3oUgwYcqq9vrHDM12G664", req.toUri())
    }

    @Test
    fun `SOL transfer with amount uses plain decimal notation`() {
        val req = TransferRequest(recipient = recipient, amount = BigDecimal("1.5"))
        assertEquals("solana:${recipient.base58}?amount=1.5", req.toUri())
    }

    @Test
    fun `amount strips trailing zeros for canonical encoding`() {
        val req = TransferRequest(recipient = recipient, amount = BigDecimal("1.50000"))
        assertEquals("solana:${recipient.base58}?amount=1.5", req.toUri())
    }

    @Test
    fun `SPL token transfer with USDC mint encodes spl-token param`() {
        val req = TransferRequest(
            recipient = recipient,
            amount = BigDecimal("12.34"),
            splToken = usdcMint
        )
        val uri = req.toUri()
        assertTrue(uri.contains("amount=12.34"))
        assertTrue(uri.contains("spl-token=${usdcMint.base58}"))
    }

    @Test
    fun `multiple references encode as repeated reference query params`() {
        val req = TransferRequest(
            recipient = recipient,
            amount = BigDecimal("0.01"),
            references = listOf(ref1, ref2)
        )
        val uri = req.toUri()
        // Both references must appear, in order
        val firstIdx = uri.indexOf("reference=${ref1.base58}")
        val secondIdx = uri.indexOf("reference=${ref2.base58}")
        assertTrue(firstIdx > 0)
        assertTrue(secondIdx > firstIdx)
    }

    @Test
    fun `label and message are URL-encoded with percent-20 spaces`() {
        val req = TransferRequest(
            recipient = recipient,
            label = "Bluefoot Booby Mint",
            message = "Save the Galapagos"
        )
        val uri = req.toUri()
        // %20 (not '+') is what Solana Pay wallets expect
        assertTrue(uri.contains("label=Bluefoot%20Booby%20Mint"))
        assertTrue(uri.contains("message=Save%20the%20Galapagos"))
    }

    @Test
    fun `negative amount rejected`() {
        assertFailsWith<IllegalArgumentException> {
            TransferRequest(recipient = recipient, amount = BigDecimal("-1.0"))
        }
    }

    @Test
    fun `more than 16 references rejected`() {
        val refs = List(17) { ref1 }
        assertFailsWith<IllegalArgumentException> {
            TransferRequest(recipient = recipient, references = refs)
        }
    }

    // ── Transfer Request — parse ─────────────────────────────────────

    @Test
    fun `parse round-trips a full URI`() {
        val original = TransferRequest(
            recipient = recipient,
            amount = BigDecimal("12.34"),
            splToken = usdcMint,
            references = listOf(ref1, ref2),
            label = "Test Label",
            message = "Test Message",
            memo = "memo123"
        )
        val parsed = SolanaPayRequest.parse(original.toUri()) as TransferRequest
        assertEquals(original.recipient, parsed.recipient)
        assertEquals(original.amount?.compareTo(parsed.amount), 0)
        assertEquals(original.splToken, parsed.splToken)
        assertEquals(original.references, parsed.references)
        assertEquals(original.label, parsed.label)
        assertEquals(original.message, parsed.message)
        assertEquals(original.memo, parsed.memo)
    }

    @Test
    fun `parse rejects missing scheme`() {
        assertNull(SolanaPayRequest.parse("https://example.com"))
        assertNull(SolanaPayRequest.parse("not-a-uri"))
    }

    @Test
    fun `parse rejects malformed recipient`() {
        assertNull(SolanaPayRequest.parse("solana:not-a-real-address"))
    }

    @Test
    fun `parse rejects malformed amount`() {
        assertNull(SolanaPayRequest.parse("solana:${recipient.base58}?amount=abc"))
    }

    // ── Transaction Request ──────────────────────────────────────────

    @Test
    fun `transaction request encodes the link with percent-encoded scheme`() {
        val req = TransactionRequest("https://merchant.example.com/checkout?id=abc")
        val uri = req.toUri()
        assertTrue(uri.startsWith("solana:"))
        // Inner https:// becomes %3A%2F%2F when encoded
        assertTrue(uri.contains("https%3A%2F%2Fmerchant.example.com%2Fcheckout"))
    }

    @Test
    fun `transaction request rejects http (not https)`() {
        assertFailsWith<IllegalArgumentException> {
            TransactionRequest("http://insecure.example.com")
        }
    }

    @Test
    fun `transaction request round-trips through parse`() {
        val original = TransactionRequest("https://merchant.example.com/checkout?id=abc")
        val parsed = SolanaPayRequest.parse(original.toUri()) as TransactionRequest
        assertEquals(original.link, parsed.link)
    }

    // ── splTokenAmount + rawAmount conversion ───────────────────────

    @Test
    fun `splTokenAmount converts raw token units to lossless decimal`() {
        // 12,345,678 raw with 6 decimals = 12.345678 USDC
        val req = TransferRequest.splTokenAmount(
            recipient = recipient,
            rawAmount = 12_345_678L,
            decimals = 6,
            splToken = usdcMint
        )
        assertEquals(0, req.amount!!.compareTo(BigDecimal("12.345678")))
        // Round-trip raw → decimal → raw should be lossless
        assertEquals(12_345_678L, req.rawAmount(6))
    }

    @Test
    fun `rawAmount truncates fractional units below decimals precision`() {
        // 1.5 SOL = 1,500,000,000 lamports
        val req = TransferRequest(recipient = recipient, amount = BigDecimal("1.5"))
        assertEquals(1_500_000_000L, req.rawAmount(9))
    }

    @Test
    fun `rawAmount returns null when amount is null`() {
        val req = TransferRequest(recipient = recipient)
        assertNull(req.rawAmount(9))
    }

    @Test
    fun `splTokenAmount rejects negative raw`() {
        assertFailsWith<IllegalArgumentException> {
            TransferRequest.splTokenAmount(recipient = recipient, rawAmount = -1L, decimals = 6)
        }
    }
}
