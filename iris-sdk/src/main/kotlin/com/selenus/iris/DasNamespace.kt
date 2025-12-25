package com.selenus.iris

import kotlinx.serialization.json.*

/**
 * # DAS Namespace
 * 
 * Metaplex Digital Asset Standard (DAS) API for comprehensive NFT and token management.
 * 
 * ## Supported Asset Types
 * - Standard NFTs
 * - Compressed NFTs (cNFTs)
 * - Fungible Tokens
 * - MPL Core Assets
 * - Token 2022 Assets
 * 
 * ## Features
 * - Get asset metadata and ownership
 * - Query assets by owner, creator, authority, or group
 * - Search assets with complex filters
 * - Get Merkle proofs for compressed assets
 * - Token account management
 */
class DasNamespace internal constructor(private val client: IrisQuickNodeClient) {
    
    private val json = client.getJson()
    
    // ========================================================================
    // SINGLE ASSET METHODS
    // ========================================================================
    
    /**
     * Returns metadata information for a compressed or standard asset.
     * 
     * @param id The asset ID (mint address)
     * @param showFungible Whether to return token_info for fungible assets
     * @param showUnverifiedCollections Whether to return unverified collection assets
     * @param showCollectionMetadata Whether to include collection metadata
     */
    suspend fun getAsset(
        id: String,
        showFungible: Boolean = false,
        showUnverifiedCollections: Boolean = false,
        showCollectionMetadata: Boolean = false
    ): DasAsset {
        val params = buildJsonObject {
            put("id", id)
            putJsonObject("options") {
                put("showFungible", showFungible)
                put("showUnverifiedCollections", showUnverifiedCollections)
                put("showCollectionMetadata", showCollectionMetadata)
            }
        }
        
        return client.executeRpcCall(
            method = "getAsset",
            params = params,
            resultDeserializer = DasAsset.serializer()
        )
    }
    
    /**
     * Fetch metadata for multiple assets in a single request.
     * 
     * @param ids List of asset IDs
     * @param showFungible Whether to return token_info for fungible assets
     */
    suspend fun getAssets(
        ids: List<String>,
        showFungible: Boolean = false
    ): List<DasAsset> {
        val params = buildJsonObject {
            put("ids", JsonArray(ids.map { JsonPrimitive(it) }))
            putJsonObject("options") {
                put("showFungible", showFungible)
            }
        }
        
        return client.executeRpcCall(
            method = "getAssets",
            params = params,
            resultDeserializer = kotlinx.serialization.builtins.ListSerializer(DasAsset.serializer())
        )
    }
    
    // ========================================================================
    // ASSET PROOF METHODS (Compressed NFTs)
    // ========================================================================
    
    /**
     * Obtain the Merkle proof for a compressed asset.
     * Required for transferring or modifying compressed NFTs.
     * 
     * @param id The asset ID
     */
    suspend fun getAssetProof(id: String): DasAssetProof {
        val params = buildJsonObject {
            put("id", id)
        }
        
        return client.executeRpcCall(
            method = "getAssetProof",
            params = params,
            resultDeserializer = DasAssetProof.serializer()
        )
    }
    
    /**
     * Retrieve Merkle proofs for multiple compressed assets.
     * 
     * @param ids List of asset IDs
     */
    suspend fun getAssetProofs(ids: List<String>): Map<String, DasAssetProof> {
        val params = buildJsonObject {
            put("ids", JsonArray(ids.map { JsonPrimitive(it) }))
        }
        
        val result = client.executeRpcCall(
            method = "getAssetProofs",
            params = params,
            resultDeserializer = JsonElement.serializer()
        )
        
        return result.jsonObject.mapValues { (_, value) ->
            json.decodeFromJsonElement(DasAssetProof.serializer(), value)
        }
    }
    
    // ========================================================================
    // QUERY METHODS
    // ========================================================================
    
    /**
     * List assets owned by a specific wallet address.
     * 
     * @param ownerAddress The owner's wallet address
     * @param page Page number for pagination (1-based)
     * @param limit Number of results per page
     * @param sortBy Field to sort by
     * @param sortDirection Sort direction
     * @param showFungible Include fungible tokens
     */
    suspend fun getAssetsByOwner(
        ownerAddress: String,
        page: Int = 1,
        limit: Int = 1000,
        sortBy: DasSortBy = DasSortBy.CREATED,
        sortDirection: DasSortDirection = DasSortDirection.DESC,
        showFungible: Boolean = false
    ): DasAssetList {
        val params = buildJsonObject {
            put("ownerAddress", ownerAddress)
            put("page", page)
            put("limit", limit)
            putJsonObject("sortBy") {
                put("sortBy", sortBy.value)
                put("sortDirection", sortDirection.value)
            }
            putJsonObject("options") {
                put("showFungible", showFungible)
            }
        }
        
        return client.executeRpcCall(
            method = "getAssetsByOwner",
            params = params,
            resultDeserializer = DasAssetList.serializer()
        )
    }
    
    /**
     * Retrieve assets created by a specified creator.
     * 
     * @param creatorAddress The creator's address
     * @param onlyVerified Only return assets where creator is verified
     * @param page Page number
     * @param limit Results per page
     */
    suspend fun getAssetsByCreator(
        creatorAddress: String,
        onlyVerified: Boolean = true,
        page: Int = 1,
        limit: Int = 1000
    ): DasAssetList {
        val params = buildJsonObject {
            put("creatorAddress", creatorAddress)
            put("onlyVerified", onlyVerified)
            put("page", page)
            put("limit", limit)
        }
        
        return client.executeRpcCall(
            method = "getAssetsByCreator",
            params = params,
            resultDeserializer = DasAssetList.serializer()
        )
    }
    
    /**
     * Fetch all assets controlled by a specific authority.
     * 
     * @param authorityAddress The authority address
     * @param page Page number
     * @param limit Results per page
     */
    suspend fun getAssetsByAuthority(
        authorityAddress: String,
        page: Int = 1,
        limit: Int = 1000
    ): DasAssetList {
        val params = buildJsonObject {
            put("authorityAddress", authorityAddress)
            put("page", page)
            put("limit", limit)
        }
        
        return client.executeRpcCall(
            method = "getAssetsByAuthority",
            params = params,
            resultDeserializer = DasAssetList.serializer()
        )
    }
    
    /**
     * Fetch assets using custom group identifiers (e.g., collection).
     * 
     * @param groupKey The group key (typically "collection")
     * @param groupValue The group value (collection address)
     * @param page Page number
     * @param limit Results per page
     */
    suspend fun getAssetsByGroup(
        groupKey: String,
        groupValue: String,
        page: Int = 1,
        limit: Int = 1000
    ): DasAssetList {
        val params = buildJsonObject {
            put("groupKey", groupKey)
            put("groupValue", groupValue)
            put("page", page)
            put("limit", limit)
        }
        
        return client.executeRpcCall(
            method = "getAssetsByGroup",
            params = params,
            resultDeserializer = DasAssetList.serializer()
        )
    }
    
    /**
     * Get all NFTs in a collection.
     * Convenience wrapper around getAssetsByGroup.
     * 
     * @param collectionAddress The collection's mint address
     * @param page Page number
     * @param limit Results per page
     */
    suspend fun getAssetsByCollection(
        collectionAddress: String,
        page: Int = 1,
        limit: Int = 1000
    ): DasAssetList {
        return getAssetsByGroup(
            groupKey = "collection",
            groupValue = collectionAddress,
            page = page,
            limit = limit
        )
    }
    
    // ========================================================================
    // SEARCH METHODS
    // ========================================================================
    
    /**
     * Search for assets matching specific parameters.
     * Provides the most flexible way to query assets.
     * 
     * @param owner Filter by owner address
     * @param creator Filter by creator address
     * @param collection Filter by collection address
     * @param burnt Filter by burnt status
     * @param compressed Filter by compression status
     * @param frozen Filter by frozen status
     * @param tokenType Filter by token type (nft, fungible, etc.)
     * @param page Page number
     * @param limit Results per page
     */
    suspend fun searchAssets(
        owner: String? = null,
        creator: String? = null,
        collection: String? = null,
        burnt: Boolean? = null,
        compressed: Boolean? = null,
        frozen: Boolean? = null,
        tokenType: String? = null,
        page: Int = 1,
        limit: Int = 1000
    ): DasAssetList {
        val params = buildJsonObject {
            put("page", page)
            put("limit", limit)
            owner?.let { put("ownerAddress", it) }
            creator?.let { put("creatorAddress", it) }
            collection?.let { 
                put("grouping", JsonArray(listOf(
                    JsonPrimitive("collection"),
                    JsonPrimitive(it)
                )))
            }
            burnt?.let { put("burnt", it) }
            compressed?.let { put("compressed", it) }
            frozen?.let { put("frozen", it) }
            tokenType?.let { put("tokenType", it) }
        }
        
        return client.executeRpcCall(
            method = "searchAssets",
            params = params,
            resultDeserializer = DasAssetList.serializer()
        )
    }
    
    // ========================================================================
    // TOKEN ACCOUNT METHODS
    // ========================================================================
    
    /**
     * List token accounts for a specific mint or owner.
     * 
     * @param mint Filter by token mint address
     * @param owner Filter by owner address
     * @param page Page number
     * @param limit Results per page
     */
    suspend fun getTokenAccounts(
        mint: String? = null,
        owner: String? = null,
        page: Int = 1,
        limit: Int = 1000
    ): DasTokenAccountList {
        require(mint != null || owner != null) {
            "Either mint or owner must be provided"
        }
        
        val params = buildJsonObject {
            put("page", page)
            put("limit", limit)
            mint?.let { put("mint", it) }
            owner?.let { put("owner", it) }
        }
        
        return client.executeRpcCall(
            method = "getTokenAccounts",
            params = params,
            resultDeserializer = DasTokenAccountList.serializer()
        )
    }
    
    /**
     * Get token accounts by owner with detailed balance info.
     */
    suspend fun getTokenAccountsByOwner(
        owner: String,
        page: Int = 1,
        limit: Int = 1000
    ): DasTokenAccountList {
        return getTokenAccounts(owner = owner, page = page, limit = limit)
    }
    
    /**
     * Get all holders of a specific token.
     */
    suspend fun getTokenAccountsByMint(
        mint: String,
        page: Int = 1,
        limit: Int = 1000
    ): DasTokenAccountList {
        return getTokenAccounts(mint = mint, page = page, limit = limit)
    }
    
    // ========================================================================
    // SIGNATURE METHODS
    // ========================================================================
    
    /**
     * List transaction signatures for a compressed asset.
     * 
     * @param id The asset ID
     * @param page Page number
     * @param limit Results per page
     */
    suspend fun getAssetSignatures(
        id: String,
        page: Int = 1,
        limit: Int = 1000
    ): JsonElement {
        val params = buildJsonObject {
            put("id", id)
            put("page", page)
            put("limit", limit)
        }
        
        return client.executeRpcCall(
            method = "getAssetSignatures",
            params = params,
            resultDeserializer = JsonElement.serializer()
        )
    }
    
    // ========================================================================
    // EDITION METHODS
    // ========================================================================
    
    /**
     * Retrieve details of edition NFTs of a specified master NFT.
     * 
     * @param mint The master edition mint address
     * @param page Page number
     * @param limit Results per page
     */
    suspend fun getNftEditions(
        mint: String,
        page: Int = 1,
        limit: Int = 1000
    ): JsonElement {
        val params = buildJsonObject {
            put("mint", mint)
            put("page", page)
            put("limit", limit)
        }
        
        return client.executeRpcCall(
            method = "getNftEditions",
            params = params,
            resultDeserializer = JsonElement.serializer()
        )
    }
}

/**
 * Sort options for DAS queries.
 */
enum class DasSortBy(val value: String) {
    ID("id"),
    CREATED("created"),
    RECENT_ACTION("recent_action"),
    NONE("none")
}

/**
 * Sort direction for DAS queries.
 */
enum class DasSortDirection(val value: String) {
    ASC("asc"),
    DESC("desc")
}
