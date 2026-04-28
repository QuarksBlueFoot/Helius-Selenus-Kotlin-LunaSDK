@file:JvmName("WalletTypes")
package xyz.selenus.luna.wallet

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * # Wallet API data model
 *
 * Mirrors the response shapes documented at
 * <https://www.helius.dev/docs/api-reference/wallet-api>. The Wallet API is
 * marked **Beta** by Helius, and Helius reserves the right to change response
 * formats. Every type below is annotated with a `@SerialName` matching the
 * raw JSON key so that field renames in the spec only require touching this
 * one file.
 *
 * Two design choices worth knowing:
 *  - All numeric balances flow through as [Double] (or `Long` for raw lamport
 *    counts) because the Helius API normalizes by `decimals` for human-readable
 *    fields. Do not use these for pricing math without re-attaching the raw
 *    [TokenBalance.decimals] / [Transfer.amountRaw] context.
 *  - Optional fields use Kotlin `null` rather than empty strings or sentinel
 *    values so callers can safely use `?.let { ... }` and `?:` defaults.
 */

/**
 * Identity record for a known on-chain entity (exchange hot wallet, protocol
 * treasury, market maker, etc). Returned by both [WalletApi.getIdentity] and
 * [WalletApi.getBatchIdentity].
 */
@Serializable
data class WalletIdentity(
    /** The Solana address this identity describes. */
    val address: String,
    /** Coarse entity type, e.g. `"exchange"`, `"protocol"`. */
    val type: String,
    /** Display label, e.g. `"Binance 1"`, `"Jupiter v6 Aggregator"`. */
    val name: String,
    /** Industry category, e.g. `"Centralized Exchange"`, `"DEX"`. */
    val category: String,
    /** Free-form classification tags. May be empty. */
    val tags: List<String> = emptyList()
)

/**
 * Token program family. The Wallet API distinguishes legacy SPL Token from the
 * Token-2022 (Token Extensions) program because confidential transfers, hooks
 * and transfer fees only exist on the latter.
 */
@Serializable
enum class WalletTokenProgram {
    @SerialName("spl-token") SPL_TOKEN,
    @SerialName("token-2022") TOKEN_2022
}

/**
 * One row in the [WalletBalancesResponse]. Native SOL appears with mint
 * address `So11111111111111111111111111111111111111112` when
 * `showNative = true`.
 */
@Serializable
data class WalletTokenBalance(
    /** Mint address of the token. */
    val mint: String,
    /** Symbol (e.g. `"USDC"`) or `null` if Helius could not identify the mint. */
    val symbol: String? = null,
    /** Display name (e.g. `"USD Coin"`) or `null` if unknown. */
    val name: String? = null,
    /** Human-readable balance, already divided by [decimals]. */
    val balance: Double,
    /** Number of decimal places of the underlying mint. */
    val decimals: Int,
    /** Spot USD price per whole token, or `null` when no price is indexed. */
    val pricePerToken: Double? = null,
    /** Total USD value of the position, or `null` if [pricePerToken] is unknown. */
    val usdValue: Double? = null,
    /** URL to the token's logo, or `null` if not indexed. */
    val logoUri: String? = null,
    /** Whether the mint lives on legacy SPL Token or Token-2022. */
    val tokenProgram: WalletTokenProgram = WalletTokenProgram.SPL_TOKEN
)

/**
 * NFT entry returned alongside [WalletBalancesResponse.balances] when
 * `showNfts = true`. Helius caps NFTs at 100 entries on the first page.
 */
@Serializable
data class WalletNft(
    val mint: String,
    val name: String? = null,
    val imageUri: String? = null,
    val collectionName: String? = null,
    val collectionAddress: String? = null,
    /** True for state-compressed (cNFT) assets. */
    val compressed: Boolean = false
)

/** Pagination envelope used by the page-style endpoints (`/balances`). */
@Serializable
data class WalletPagePagination(
    val page: Int,
    val limit: Int,
    val hasMore: Boolean
)

/** Response from [WalletApi.getBalances]. */
@Serializable
data class WalletBalancesResponse(
    /** Token positions, sorted by USD value descending. */
    val balances: List<WalletTokenBalance>,
    /** Optional NFT list, populated only when `showNfts = true`. */
    val nfts: List<WalletNft>? = null,
    /** USD total of [balances] on the current page (NOT the whole portfolio). */
    val totalUsdValue: Double = 0.0,
    val pagination: WalletPagePagination
)

/**
 * One balance delta inside a [WalletHistoryTransaction]. Sign convention: a
 * positive [amount] means the wallet *received* the change.
 */
@Serializable
data class WalletBalanceChange(
    /** Mint address; `"SOL"` is used for native lamports in this surface. */
    val mint: String,
    val amount: Double,
    val decimals: Int
)

/** Single parsed transaction returned by [WalletApi.getHistory]. */
@Serializable
data class WalletHistoryTransaction(
    val signature: String,
    /** Unix seconds when the transaction landed; `null` if still unconfirmed. */
    val timestamp: Long? = null,
    val slot: Long,
    /** Fee paid in SOL (already divided by 1e9). */
    val fee: Double,
    val feePayer: String,
    /** Non-null when the transaction failed; carries the runtime error string. */
    val error: String? = null,
    val balanceChanges: List<WalletBalanceChange> = emptyList()
)

/** Cursor pagination envelope used by `/history` and `/transfers`. */
@Serializable
data class WalletCursorPagination(
    val hasMore: Boolean,
    /** Opaque cursor — feed back into the next request's `before`/`cursor` arg. */
    val nextCursor: String? = null
)

/** Response from [WalletApi.getHistory]. */
@Serializable
data class WalletHistoryResponse(
    val data: List<WalletHistoryTransaction>,
    val pagination: WalletCursorPagination
)

/** Direction of a [WalletTransfer] relative to the wallet you queried. */
@Serializable
enum class WalletTransferDirection {
    @SerialName("in") IN,
    @SerialName("out") OUT
}

/** A single SPL/SOL transfer touching the queried wallet. */
@Serializable
data class WalletTransfer(
    val signature: String,
    val timestamp: Long,
    val direction: WalletTransferDirection,
    /** The other party (sender if [direction] is `IN`, recipient if `OUT`). */
    val counterparty: String,
    val mint: String,
    val symbol: String? = null,
    /** Human-readable amount, already divided by [decimals]. */
    val amount: Double,
    /**
     * Raw amount in the smallest unit (lamports for SOL, raw token units for
     * SPL). Returned as a string so callers don't lose precision on amounts
     * that exceed `Long.MAX_VALUE`.
     */
    val amountRaw: String,
    val decimals: Int
)

/** Response from [WalletApi.getTransfers]. */
@Serializable
data class WalletTransfersResponse(
    val data: List<WalletTransfer>,
    val pagination: WalletCursorPagination
)

/** Response from [WalletApi.getFundedBy]. Carries the wallet's first funder. */
@Serializable
data class WalletFundingSource(
    /** Address that originally funded this wallet. */
    val funder: String,
    /** Display name of the funder if known (e.g. `"Coinbase 2"`). */
    val funderName: String? = null,
    /** Type of the funder, e.g. `"exchange"`. */
    val funderType: String? = null,
    val mint: String,
    val symbol: String,
    val amount: Double,
    val amountRaw: String,
    val decimals: Int,
    val signature: String,
    val timestamp: Long,
    /** ISO 8601 UTC timestamp string, parallel to [timestamp]. */
    val date: String,
    val slot: Long,
    val explorerUrl: String
)

/**
 * Subset of [WalletHistoryTransaction] types Helius parses. Use [WalletTxType.raw]
 * when you need to pass an unrecognised type the SDK doesn't yet enumerate, so
 * upstream Helius additions don't require an SDK release.
 */
@Suppress("unused")
sealed class WalletTxType(val raw: String) {
    object Swap : WalletTxType("SWAP")
    object Transfer : WalletTxType("TRANSFER")
    object NftSale : WalletTxType("NFT_SALE")
    object NftBid : WalletTxType("NFT_BID")
    object NftListing : WalletTxType("NFT_LISTING")
    object NftMint : WalletTxType("NFT_MINT")
    object NftCancelListing : WalletTxType("NFT_CANCEL_LISTING")
    object TokenMint : WalletTxType("TOKEN_MINT")
    object Burn : WalletTxType("BURN")
    object CompressedNftMint : WalletTxType("COMPRESSED_NFT_MINT")
    object CompressedNftTransfer : WalletTxType("COMPRESSED_NFT_TRANSFER")
    object CompressedNftBurn : WalletTxType("COMPRESSED_NFT_BURN")
    /** Pass an arbitrary, future / undocumented Helius transaction type. */
    class Custom(value: String) : WalletTxType(value)
}

/**
 * Filter for [WalletApi.getHistory] controlling whether transactions on the
 * wallet's associated token accounts are included. `BALANCE_CHANGED` is the
 * Helius-recommended default — it cuts spam without dropping legitimate
 * balance-affecting txs.
 */
@Serializable
enum class WalletAtaFilter {
    @SerialName("none") NONE,
    @SerialName("balanceChanged") BALANCE_CHANGED,
    @SerialName("all") ALL
}
