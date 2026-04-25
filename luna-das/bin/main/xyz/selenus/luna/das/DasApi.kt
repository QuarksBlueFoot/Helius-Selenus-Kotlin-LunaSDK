package xyz.selenus.luna.das

import kotlinx.coroutines.delay
import kotlinx.serialization.json.*
import xyz.selenus.luna.LunaHeliusClient
import xyz.selenus.luna.RpcError
import xyz.selenus.luna.RpcResponse

/** Digital Asset Standard (DAS) API namespace. */
class DasApi internal constructor(private val client: LunaHeliusClient) {
    /**
     * Fetch a single asset by its unique identifier.  Returns a JSON tree containing
     * on-chain and off-chain metadata, ownership details and compression state for any
     * Solana digital asset.
     *
     * @param assetId The mint address or asset ID of the NFT, token or cNFT.
     * @param showFungible Whether to show fungible tokens.
     * @param showUnverifiedCollections Whether to show unverified collections.
     * @param showCollectionMetadata Whether to show collection metadata.
     * @param showInscription Whether to show inscription data.
     */
    suspend fun getAsset(
        assetId: String,
        showFungible: Boolean? = null,
        showUnverifiedCollections: Boolean? = null,
        showCollectionMetadata: Boolean? = null,
        showInscription: Boolean? = null
    ): RpcResponse<JsonElement> {
        val options = buildJsonObject {
            showFungible?.let { put("showFungible", it) }
            showUnverifiedCollections?.let { put("showUnverifiedCollections", it) }
            showCollectionMetadata?.let { put("showCollectionMetadata", it) }
            showInscription?.let { put("showInscription", it) }
        }
        val params = buildJsonObject {
            put("id", assetId)
            if (options.isNotEmpty()) put("options", options)
        }
        return client.rpcCall("getAsset", params)
    }

    /**
     * Retrieve a list of digital assets owned by a wallet with optional pagination and
     * sorting.  This is the easiest way to fetch all NFTs and fungible
     * tokens held by a user.
     *
     * @param ownerAddress Wallet address whose assets should be listed.
     * @param page Optional page number (1-indexed).  When omitted the first page is returned.
     * @param limit Optional page size.  When omitted the default server limit is used.
     * @param sortBy Optional sort specification.
     * @param before Optional cursor for pagination (before this asset ID).
     * @param after Optional cursor for pagination (after this asset ID).
     * @param showFungible Whether to show fungible tokens.
     * @param showUnverifiedCollections Whether to show unverified collections.
     * @param showCollectionMetadata Whether to show collection metadata.
     * @param showInscription Whether to show inscription data.
     */
    suspend fun getAssetsByOwner(
        ownerAddress: String,
        page: Int? = null,
        limit: Int? = null,
        sortBy: LunaHeliusClient.SortBy? = null,
        before: String? = null,
        after: String? = null,
        showFungible: Boolean? = null,
        showUnverifiedCollections: Boolean? = null,
        showCollectionMetadata: Boolean? = null,
        showInscription: Boolean? = null
    ): RpcResponse<JsonElement> {
        val options = buildJsonObject {
            showFungible?.let { put("showFungible", it) }
            showUnverifiedCollections?.let { put("showUnverifiedCollections", it) }
            showCollectionMetadata?.let { put("showCollectionMetadata", it) }
            showInscription?.let { put("showInscription", it) }
        }
        val params = buildJsonObject {
            put("ownerAddress", ownerAddress)
            page?.let { put("page", it) }
            limit?.let { put("limit", it) }
            before?.let { put("before", it) }
            after?.let { put("after", it) }
            sortBy?.let { sort ->
                putJsonObject("sortBy") {
                    put("sortBy", sort.sortBy)
                    put("sortDirection", sort.sortDirection)
                }
            }
            if (options.isNotEmpty()) put("options", options)
        }
        return client.rpcCall("getAssetsByOwner", params)
    }

    /**
     * Search for assets by arbitrary fields.  Accepts a JSON object of search filters
     * as documented in the Helius searchAssets endpoint.  Passing an empty map
     * returns all assets.  See the official docs for supported search keys.
     */
    suspend fun searchAssets(filters: Map<String, String>): RpcResponse<JsonElement> {
        val params = buildJsonObject {
            for ((k, v) in filters) put(k, v)
        }
        return client.rpcCall("searchAssets", params)
    }

    /**
     * Fetch multiple assets by their IDs (up to 1 000).  Use this method when you
     * need to fetch many assets in a single request.
     * @param assetIds A list of asset identifiers.
     * @param showFungible Whether to show fungible tokens.
     * @param showUnverifiedCollections Whether to show unverified collections.
     * @param showCollectionMetadata Whether to show collection metadata.
     * @param showInscription Whether to show inscription data.
     */
    suspend fun getAssetBatch(
        assetIds: List<String>,
        showFungible: Boolean? = null,
        showUnverifiedCollections: Boolean? = null,
        showCollectionMetadata: Boolean? = null,
        showInscription: Boolean? = null
    ): RpcResponse<JsonElement> {
        val options = buildJsonObject {
            showFungible?.let { put("showFungible", it) }
            showUnverifiedCollections?.let { put("showUnverifiedCollections", it) }
            showCollectionMetadata?.let { put("showCollectionMetadata", it) }
            showInscription?.let { put("showInscription", it) }
        }
        val params = buildJsonObject {
            put("ids", JsonArray(assetIds.map { JsonPrimitive(it) }))
            if (options.isNotEmpty()) put("options", options)
        }
        return client.rpcCall("getAssetBatch", params)
    }


    /**
     * Retrieve a Merkle proof for a compressed NFT by its ID.
     * @param assetId The identifier of the compressed asset.
     */
    suspend fun getAssetProof(assetId: String): RpcResponse<JsonElement> {
        val params = buildJsonObject { put("id", assetId) }
        return client.rpcCall("getAssetProof", params)
    }

    /**
     * Fetch Merkle proofs for multiple compressed NFTs.
     * @param assetIds The list of compressed asset IDs.
     */
    suspend fun getAssetProofBatch(assetIds: List<String>): RpcResponse<JsonElement> {
        val params = buildJsonObject {
            put("ids", JsonArray(assetIds.map { JsonPrimitive(it) }))
        }
        return client.rpcCall("getAssetProofBatch", params)
    }

    /**
     * Get a list of assets with a specific authority.
     * @param authorityAddress The authority address.
     * @param page Optional page number.
     * @param limit Optional page size.
     * @param before Optional cursor for pagination.
     * @param after Optional cursor for pagination.
     * @param showFungible Whether to show fungible tokens.
     * @param showUnverifiedCollections Whether to show unverified collections.
     * @param showCollectionMetadata Whether to show collection metadata.
     * @param showInscription Whether to show inscription data.
     */
    suspend fun getAssetsByAuthority(
        authorityAddress: String,
        page: Int? = null,
        limit: Int? = null,
        before: String? = null,
        after: String? = null,
        showFungible: Boolean? = null,
        showUnverifiedCollections: Boolean? = null,
        showCollectionMetadata: Boolean? = null,
        showInscription: Boolean? = null
    ): RpcResponse<JsonElement> {
        val options = buildJsonObject {
            showFungible?.let { put("showFungible", it) }
            showUnverifiedCollections?.let { put("showUnverifiedCollections", it) }
            showCollectionMetadata?.let { put("showCollectionMetadata", it) }
            showInscription?.let { put("showInscription", it) }
        }
        val params = buildJsonObject {
            put("authorityAddress", authorityAddress)
            page?.let { put("page", it) }
            limit?.let { put("limit", it) }
            before?.let { put("before", it) }
            after?.let { put("after", it) }
            if (options.isNotEmpty()) put("options", options)
        }
        return client.rpcCall("getAssetsByAuthority", params)
    }

    /**
     * Retrieve a list of assets created by the given creator address.
     * @param creatorAddress The address of the asset creator.
     * @param page Optional page number.
     * @param limit Optional page size.
     * @param before Optional cursor for pagination.
     * @param after Optional cursor for pagination.
     * @param showFungible Whether to show fungible tokens.
     * @param showUnverifiedCollections Whether to show unverified collections.
     * @param showCollectionMetadata Whether to show collection metadata.
     * @param showInscription Whether to show inscription data.
     */
    suspend fun getAssetsByCreator(
        creatorAddress: String,
        page: Int? = null,
        limit: Int? = null,
        before: String? = null,
        after: String? = null,
        showFungible: Boolean? = null,
        showUnverifiedCollections: Boolean? = null,
        showCollectionMetadata: Boolean? = null,
        showInscription: Boolean? = null
    ): RpcResponse<JsonElement> {
        val options = buildJsonObject {
            showFungible?.let { put("showFungible", it) }
            showUnverifiedCollections?.let { put("showUnverifiedCollections", it) }
            showCollectionMetadata?.let { put("showCollectionMetadata", it) }
            showInscription?.let { put("showInscription", it) }
        }
        val params = buildJsonObject {
            put("creatorAddress", creatorAddress)
            page?.let { put("page", it) }
            limit?.let { put("limit", it) }
            before?.let { put("before", it) }
            after?.let { put("after", it) }
            if (options.isNotEmpty()) put("options", options)
        }
        return client.rpcCall("getAssetsByCreator", params)
    }

    /**
     * Return assets that belong to a specific group key and value.
     * Useful for fetching mints for NFT collections.
     * @param groupKey The group key (e.g. "collection").
     * @param groupValue The value for the group key.
     * @param page Optional page number.
     * @param limit Optional page size.
     * @param before Optional cursor for pagination.
     * @param after Optional cursor for pagination.
     * @param showFungible Whether to show fungible tokens.
     * @param showUnverifiedCollections Whether to show unverified collections.
     * @param showCollectionMetadata Whether to show collection metadata.
     * @param showInscription Whether to show inscription data.
     */
    suspend fun getAssetsByGroup(
        groupKey: String,
        groupValue: String,
        page: Int? = null,
        limit: Int? = null,
        before: String? = null,
        after: String? = null,
        showFungible: Boolean? = null,
        showUnverifiedCollections: Boolean? = null,
        showCollectionMetadata: Boolean? = null,
        showInscription: Boolean? = null
    ): RpcResponse<JsonElement> {
        val options = buildJsonObject {
            showFungible?.let { put("showFungible", it) }
            showUnverifiedCollections?.let { put("showUnverifiedCollections", it) }
            showCollectionMetadata?.let { put("showCollectionMetadata", it) }
            showInscription?.let { put("showInscription", it) }
        }
        val params = buildJsonObject {
            put("groupKey", groupKey)
            put("groupValue", groupValue)
            page?.let { put("page", it) }
            limit?.let { put("limit", it) }
            before?.let { put("before", it) }
            after?.let { put("after", it) }
            if (options.isNotEmpty()) put("options", options)
        }
        return client.rpcCall("getAssetsByGroup", params)
    }


    /**
     * Get edition NFTs for a given master NFT.
     * @param masterAssetId The master NFT's asset ID.
     * @param page Optional page number.
     * @param limit Optional page size.
     */
    suspend fun getNftEditions(
        masterAssetId: String,
        page: Int? = null,
        limit: Int? = null
    ): RpcResponse<JsonElement> {
        val params = buildJsonObject {
            put("id", masterAssetId)
            page?.let { put("page", it) }
            limit?.let { put("limit", it) }
        }
        return client.rpcCall("getNftEditions", params)
    }

    /**
     * Return token accounts by mint or by owner.
     * Provide either a `mint` to fetch all accounts for a token, or an `owner`
     * address to fetch all token accounts owned by that address.
     * @param mint Optional token mint address.
     * @param owner Optional owner address.
     * @param page Optional page number.
     * @param limit Optional page size.
     * @param before Optional cursor for pagination.
     * @param after Optional cursor for pagination.
     * @param showZeroBalance Whether to show accounts with zero balance.
     */
    suspend fun getTokenAccounts(
        mint: String? = null,
        owner: String? = null,
        page: Int? = null,
        limit: Int? = null,
        before: String? = null,
        after: String? = null,
        showZeroBalance: Boolean? = null
    ): RpcResponse<JsonElement> {
        val options = buildJsonObject {
            showZeroBalance?.let { put("showZeroBalance", it) }
        }
        val params = buildJsonObject {
            mint?.let { put("mint", it) }
            owner?.let { put("owner", it) }
            page?.let { put("page", it) }
            limit?.let { put("limit", it) }
            before?.let { put("before", it) }
            after?.let { put("after", it) }
            if (options.isNotEmpty()) put("options", options)
        }
        return client.rpcCall("getTokenAccounts", params)
    }


    /**
     * Retrieve transaction signatures involving a specific asset (NFT or token)
     * with chronological order.
     *
     * @param assetId The asset identifier.
     * @param page The page number (1-indexed).
     * @param limit The maximum number of signatures to return.
     * @param before The cursor for paginating backwards.
     * @param after The cursor for paginating forwards.
     */
    suspend fun getSignaturesForAsset(
        assetId: String,
        page: Int? = null,
        limit: Int? = null,
        before: String? = null,
        after: String? = null
    ): RpcResponse<JsonElement> {
        val params = buildJsonObject {
            put("id", assetId)
            page?.let { put("page", it) }
            limit?.let { put("limit", it) }
            before?.let { put("before", it) }
            after?.let { put("after", it) }
        }
        return client.rpcCall("getSignaturesForAsset", params)
    }
}

/**
 * Access the DasApi namespace from a [LunaHeliusClient].
 *
 * Import this extension to enable the `client.das` style:
 * ```
 * import xyz.selenus.luna.das.das
 * client.das.<method>()
 * ```
 */
val LunaHeliusClient.das: DasApi
    get() = DasApi(this)
