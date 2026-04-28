package xyz.selenus.luna.solanapay

import xyz.selenus.luna.keys.SolanaAddress
import xyz.selenus.luna.keys.isValidSolanaAddress
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * # Solana Pay — type-safe URI builder + parser
 *
 * Implements the Solana Pay specification at
 * https://docs.solanapay.com/spec.
 *
 * Two URI flavours:
 *  - **Transfer Request** — `solana:<recipient>?amount=...&...`. Wallet
 *    constructs and signs the transaction.
 *  - **Transaction Request** — `solana:<https-link>`. Wallet GETs the URL
 *    for metadata and POSTs the user's pubkey to receive a partially-signed
 *    transaction.
 *
 * Both shapes are first-class here. The builder is immutable; every setter
 * returns a fresh instance so it's safe to reuse a base config.
 *
 * Why separate from the in-monolith `MobileApi.generatePaymentLink`? That
 * helper takes only `Double amount` and stringly-typed params, lacks SPL
 * token + reference + memo support, and doesn't validate addresses or
 * encode amounts losslessly. This module fixes all of that.
 */
sealed class SolanaPayRequest {

    /** Encode this request to its canonical `solana:` URI string. */
    abstract fun toUri(): String

    companion object {
        const val SCHEME = "solana:"

        /**
         * Parse any Solana Pay URI string. Routes to [TransferRequest] or
         * [TransactionRequest] based on whether the path is a Solana address
         * or an https URL. Returns null for malformed input.
         */
        fun parse(uri: String): SolanaPayRequest? {
            if (!uri.startsWith(SCHEME)) return null
            val withoutScheme = uri.removePrefix(SCHEME)
            val (path, query) = splitOnce(withoutScheme, '?')

            // Transaction Request: path is an https URL
            if (path.startsWith("https://") || path.startsWith("https%3A")) {
                val link = URLDecoder.decode(path, "UTF-8")
                if (!link.startsWith("https://")) return null
                return TransactionRequest(link = link)
            }

            // Transfer Request: path must be a valid Solana address
            val recipient = SolanaAddress.parse(path) ?: return null

            // Use the multi-value parser so duplicate `reference` keys are
            // preserved. Other query params can be looked up via firstOrNull.
            val multi = parseQueryStringMulti(query)
            fun first(name: String): String? = multi.firstOrNull { it.first == name }?.second

            val amount = first("amount")?.let {
                runCatching { BigDecimal(it) }.getOrNull() ?: return null
            }
            val splToken = first("spl-token")?.let {
                if (!isValidSolanaAddress(it)) return null
                SolanaAddress.parse(it)!!
            }
            val references = multi.filter { it.first == "reference" }
                .mapNotNull { SolanaAddress.parse(it.second) }
            val label = first("label")
            val message = first("message")
            val memo = first("memo")

            return TransferRequest(
                recipient = recipient,
                amount = amount,
                splToken = splToken,
                references = references,
                label = label,
                message = message,
                memo = memo
            )
        }
    }
}

/**
 * Solana Pay **Transfer Request** — the wallet builds the transaction
 * locally from the URI parameters.
 *
 * @property amount Amount in **decimal units** (e.g. `0.5` SOL or `12.34` USDC,
 *   not lamports / raw token units). The Solana Pay spec uses decimal amounts.
 *   Use [splTokenAmount] when working with raw u64 amounts.
 * @property splToken Optional SPL token mint. When null, the transfer is in
 *   native SOL. When set, [amount] is interpreted in that token's whole units.
 * @property references Up to ~16 reference public keys (typically PDAs) used
 *   for off-chain payment correlation. Multiple references serialise as
 *   repeated `&reference=...` query params per the spec.
 * @property label Wallet-displayed label (e.g. merchant name). Wallets render
 *   this in the confirmation prompt.
 * @property message Wallet-displayed long-form description.
 * @property memo Memo to attach as a Memo program instruction in the tx —
 *   permanently stored on-chain, visible in explorers.
 */
data class TransferRequest(
    val recipient: SolanaAddress,
    val amount: BigDecimal? = null,
    val splToken: SolanaAddress? = null,
    val references: List<SolanaAddress> = emptyList(),
    val label: String? = null,
    val message: String? = null,
    val memo: String? = null
) : SolanaPayRequest() {

    init {
        amount?.let { require(it.signum() >= 0) { "amount must be non-negative (got $it)" } }
        require(references.size <= MAX_REFERENCES) {
            "Solana Pay supports up to $MAX_REFERENCES references (got ${references.size})"
        }
    }

    override fun toUri(): String = buildString {
        append(SCHEME)
        append(recipient.base58)

        val params = mutableListOf<Pair<String, String>>()
        amount?.let {
            // Strip trailing zeros; keep dot notation. Solana Pay forbids
            // scientific notation and requires lossless round-trips.
            params += "amount" to it.stripTrailingZeros().toPlainString()
        }
        splToken?.let { params += "spl-token" to it.base58 }
        references.forEach { params += "reference" to it.base58 }
        label?.let { params += "label" to it }
        message?.let { params += "message" to it }
        memo?.let { params += "memo" to it }

        if (params.isNotEmpty()) {
            append('?')
            append(params.joinToString("&") { (k, v) ->
                "$k=${URLEncoder.encode(v, "UTF-8").replace("+", "%20")}"
            })
        }
    }

    /**
     * Convert [amount] to a raw u64 in token base units, given the mint's
     * [decimals]. Returns null if [amount] is null. Throws if the result
     * exceeds u64 range (which is a bug in the requesting party's flow).
     */
    fun rawAmount(decimals: Int): Long? {
        val a = amount ?: return null
        require(decimals in 0..18) { "decimals must be in [0, 18] (got $decimals)" }
        val scaled = a.multiply(BigDecimal.TEN.pow(decimals)).setScale(0, RoundingMode.DOWN)
        require(scaled.signum() >= 0 && scaled <= MAX_U64_AS_BIGDECIMAL) {
            "Computed raw amount $scaled exceeds u64 range"
        }
        return scaled.toLong()
    }

    companion object {
        /** Solana Pay reference cap per the spec. */
        const val MAX_REFERENCES = 16

        private val MAX_U64_AS_BIGDECIMAL = BigDecimal("18446744073709551615")

        /**
         * Build a TransferRequest where [amount] is supplied in raw
         * smallest-unit form (lamports for SOL, raw token units for SPL).
         * The decimal amount is computed losslessly from [decimals].
         */
        fun splTokenAmount(
            recipient: SolanaAddress,
            rawAmount: Long,
            decimals: Int,
            splToken: SolanaAddress? = null,
            references: List<SolanaAddress> = emptyList(),
            label: String? = null,
            message: String? = null,
            memo: String? = null
        ): TransferRequest {
            require(rawAmount >= 0) { "rawAmount must be non-negative" }
            require(decimals in 0..18) { "decimals must be in [0, 18]" }
            val decimal = BigDecimal(rawAmount).divide(
                BigDecimal.TEN.pow(decimals),
                decimals,
                RoundingMode.DOWN
            )
            return TransferRequest(
                recipient = recipient,
                amount = decimal,
                splToken = splToken,
                references = references,
                label = label,
                message = message,
                memo = memo
            )
        }
    }
}

/**
 * Solana Pay **Transaction Request** — the wallet GETs [link] for metadata
 * (label, icon) then POSTs the user's pubkey to receive a partially-signed,
 * server-generated transaction. Use this when the merchant/server needs to
 * decide the transaction shape at request time (dynamic pricing, gas
 * sponsorship, NFT mint with race protection).
 *
 * @property link Must be HTTPS (HTTP is not allowed by the spec).
 */
data class TransactionRequest(val link: String) : SolanaPayRequest() {

    init {
        require(link.startsWith("https://")) {
            "Solana Pay Transaction Request link must be HTTPS (got: ${link.take(20)}...)"
        }
    }

    override fun toUri(): String =
        SCHEME + URLEncoder.encode(link, "UTF-8").replace("+", "%20")
}

// ── Internal helpers ────────────────────────────────────────────────────

private fun splitOnce(s: String, sep: Char): Pair<String, String> {
    val idx = s.indexOf(sep)
    return if (idx < 0) s to "" else s.substring(0, idx) to s.substring(idx + 1)
}

/**
 * Parse a Solana Pay query string into a list of (key, value) pairs.
 *
 * Returns a list (not a map) because the spec allows duplicate keys —
 * specifically multiple `reference=...` params. A naive `Map<String, String>`
 * would collapse them and silently drop references after the first.
 */
internal fun parseQueryStringMulti(query: String): List<Pair<String, String>> {
    if (query.isEmpty()) return emptyList()
    val out = mutableListOf<Pair<String, String>>()
    for (pair in query.split('&')) {
        if (pair.isEmpty()) continue
        val (k, v) = splitOnce(pair, '=')
        if (k.isEmpty()) continue
        out += URLDecoder.decode(k, "UTF-8") to URLDecoder.decode(v, "UTF-8")
    }
    return out
}
