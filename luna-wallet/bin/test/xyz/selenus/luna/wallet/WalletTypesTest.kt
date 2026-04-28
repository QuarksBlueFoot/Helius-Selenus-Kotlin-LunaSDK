package xyz.selenus.luna.wallet

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Contract tests for the Wallet API serialization layer.
 *
 * These tests pin the JSON shapes documented at
 * https://www.helius.dev/docs/api-reference/wallet-api so that future API
 * spec changes (it's a Beta surface) trip a test failure rather than silently
 * deserializing into the wrong fields.
 */
class WalletTypesTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun `WalletIdentity decodes documented fields`() {
        val raw = """
            {
              "address": "HXsKP7wrBWaQ8T2Vtjry3Nj3oUgwYcqq9vrHDM12G664",
              "type": "exchange",
              "name": "Binance 1",
              "category": "Centralized Exchange",
              "tags": ["cex", "high-volume"]
            }
        """.trimIndent()

        val id = json.decodeFromString(WalletIdentity.serializer(), raw)

        assertEquals("HXsKP7wrBWaQ8T2Vtjry3Nj3oUgwYcqq9vrHDM12G664", id.address)
        assertEquals("Binance 1", id.name)
        assertEquals(listOf("cex", "high-volume"), id.tags)
    }

    @Test
    fun `WalletIdentity tolerates unknown fields gracefully`() {
        // Helius is Beta — they may add fields. We must not crash.
        val raw = """
            {
              "address": "X",
              "type": "exchange",
              "name": "N",
              "category": "C",
              "tags": [],
              "futureField": "ignored"
            }
        """.trimIndent()
        val id = json.decodeFromString(WalletIdentity.serializer(), raw)
        assertEquals("X", id.address)
    }

    @Test
    fun `Token program enum maps SerialName values`() {
        val splRaw = """
            {
              "mint": "So11111111111111111111111111111111111111112",
              "symbol": "SOL",
              "name": "Solana",
              "balance": 12.5,
              "decimals": 9,
              "pricePerToken": 100.0,
              "usdValue": 1250.0,
              "logoUri": null,
              "tokenProgram": "spl-token"
            }
        """.trimIndent()

        val t22Raw = splRaw.replace("\"spl-token\"", "\"token-2022\"")

        val spl = json.decodeFromString(WalletTokenBalance.serializer(), splRaw)
        val t22 = json.decodeFromString(WalletTokenBalance.serializer(), t22Raw)

        assertEquals(WalletTokenProgram.SPL_TOKEN, spl.tokenProgram)
        assertEquals(WalletTokenProgram.TOKEN_2022, t22.tokenProgram)
        assertNull(spl.logoUri)
    }

    @Test
    fun `BalancesResponse handles missing nfts field`() {
        // showNfts = false → server omits the nfts array entirely
        val raw = """
            {
              "balances": [],
              "totalUsdValue": 0.0,
              "pagination": { "page": 1, "limit": 100, "hasMore": false }
            }
        """.trimIndent()

        val response = json.decodeFromString(WalletBalancesResponse.serializer(), raw)
        assertTrue(response.balances.isEmpty())
        assertNull(response.nfts)
        assertEquals(false, response.pagination.hasMore)
    }

    @Test
    fun `Transfer direction enum round-trips both values`() {
        val inboundRaw = """
            {
              "signature": "sig1",
              "timestamp": 1700000000,
              "direction": "in",
              "counterparty": "addr",
              "mint": "SOL",
              "symbol": "SOL",
              "amount": 1.5,
              "amountRaw": "1500000000",
              "decimals": 9
            }
        """.trimIndent()

        val outboundRaw = inboundRaw.replace("\"in\"", "\"out\"")

        val inbound = json.decodeFromString(WalletTransfer.serializer(), inboundRaw)
        val outbound = json.decodeFromString(WalletTransfer.serializer(), outboundRaw)

        assertEquals(WalletTransferDirection.IN, inbound.direction)
        assertEquals(WalletTransferDirection.OUT, outbound.direction)
    }

    @Test
    fun `HistoryTransaction allows null timestamp and error`() {
        val raw = """
            {
              "signature": "abc",
              "timestamp": null,
              "slot": 12345,
              "fee": 0.000005,
              "feePayer": "payer",
              "error": null,
              "balanceChanges": []
            }
        """.trimIndent()

        val tx = json.decodeFromString(WalletHistoryTransaction.serializer(), raw)
        assertNull(tx.timestamp)
        assertNull(tx.error)
        assertEquals(12345L, tx.slot)
    }

    @Test
    fun `AtaFilter SerialName values match Helius spec`() {
        // We pin the wire format to defend against accidental rename refactors.
        val none = json.encodeToString(WalletAtaFilter.serializer(), WalletAtaFilter.NONE)
        val balanceChanged = json.encodeToString(
            WalletAtaFilter.serializer(),
            WalletAtaFilter.BALANCE_CHANGED
        )
        val all = json.encodeToString(WalletAtaFilter.serializer(), WalletAtaFilter.ALL)

        assertEquals("\"none\"", none)
        assertEquals("\"balanceChanged\"", balanceChanged)
        assertEquals("\"all\"", all)
    }

    @Test
    fun `BatchIdentity decodes empty array`() {
        val list = json.decodeFromString(
            ListSerializer(WalletIdentity.serializer()),
            "[]"
        )
        assertTrue(list.isEmpty())
    }

    @Test
    fun `WalletTxType raw values match Helius-documented strings`() {
        // Compile-time pinning of the documented type values.
        assertEquals("SWAP", WalletTxType.Swap.raw)
        assertEquals("NFT_SALE", WalletTxType.NftSale.raw)
        assertEquals("COMPRESSED_NFT_TRANSFER", WalletTxType.CompressedNftTransfer.raw)
        assertEquals("UNKNOWN_FUTURE_TYPE", WalletTxType.Custom("UNKNOWN_FUTURE_TYPE").raw)
    }

    @Test
    fun `FundingSource preserves all enrichment fields`() {
        val raw = """
            {
              "funder": "F",
              "funderName": "Coinbase 2",
              "funderType": "exchange",
              "mint": "So11111111111111111111111111111111111111112",
              "symbol": "SOL",
              "amount": 0.05,
              "amountRaw": "50000000",
              "decimals": 9,
              "signature": "sig",
              "timestamp": 1700000000,
              "date": "2023-11-14T22:13:20Z",
              "slot": 222,
              "explorerUrl": "https://solscan.io/tx/sig"
            }
        """.trimIndent()

        val src = json.decodeFromString(WalletFundingSource.serializer(), raw)
        assertEquals("Coinbase 2", src.funderName)
        assertEquals("exchange", src.funderType)
        assertNotNull(src.explorerUrl)
    }
}
