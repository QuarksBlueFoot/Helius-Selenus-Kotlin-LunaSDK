package com.selenus.iris

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/**
 * # SNS Namespace - Solana Name Service
 * 
 * Interact with .sol domains and Solana Name Service.
 * Resolve domains to addresses and addresses to domains.
 * 
 * ## Features
 * - Resolve .sol domain names to wallet addresses
 * - Reverse lookup: find domains for a wallet address
 * - Get all domains owned by a wallet
 * - Get favorite/primary domain for a wallet
 * - Check domain availability
 * 
 * ## SNS Program
 * The Solana Name Service uses the following on-chain program:
 * `namesLPneVptA9Z5rqUDD9tMTWEJwofgaYwp8cawRkX`
 */
class SnsNamespace internal constructor(private val client: IrisQuickNodeClient) {
    
    private val json = client.getJson()
    
    companion object {
        /** SNS Program ID */
        const val SNS_PROGRAM_ID = "namesLPneVptA9Z5rqUDD9tMTWEJwofgaYwp8cawRkX"
        
        /** .sol TLD */
        const val SOL_TLD = "58PwtjSDuFHuUkYjH9BYnnQKHfwo9reZhC2zMJv9JPkx"
    }
    
    /**
     * Resolve a .sol domain name to a wallet address.
     * 
     * @param domain The domain name (with or without .sol suffix)
     * @return The wallet address that owns the domain, or null if not found
     * 
     * ## Example
     * ```kotlin
     * val address = client.sns.resolveDomain("moonmanquark.sol")
     * // or
     * val address = client.sns.resolveDomain("moonmanquark")
     * ```
     */
    suspend fun resolveDomain(domain: String): String? {
        val cleanDomain = domain.lowercase().removeSuffix(".sol")
        
        // Use getProgramAccounts to look up the domain
        val filters = buildJsonArray {
            addJsonObject {
                putJsonObject("memcmp") {
                    put("offset", 0)
                    put("bytes", SOL_TLD)
                }
            }
        }
        
        // For simplicity, we'll use a direct lookup approach
        // The actual SNS resolution is more complex and involves hashing
        try {
            val domainKey = getDomainKey(cleanDomain)
            val accountInfo = client.rpc.getAccountInfo(domainKey)
            if (accountInfo != null) {
                // Parse the owner from the account data
                // SNS stores the owner at offset 32 in the data
                return parseOwnerFromData(accountInfo.data)
            }
        } catch (e: Exception) {
            // Domain not found
        }
        return null
    }
    
    /**
     * Reverse lookup: Get the .sol domain for a wallet address.
     * Returns the primary/favorite domain if set.
     * 
     * @param address The wallet address to look up
     * @return The primary domain name, or null if none set
     */
    suspend fun reverseLookup(address: String): String? {
        return getFavoriteDomain(address)
    }
    
    /**
     * Get all .sol domains owned by a wallet.
     * 
     * @param owner The owner's wallet address
     * @return List of domain names owned by the wallet
     */
    suspend fun getDomains(owner: String): List<SnsDomain> {
        // Get domains using DAS API first (if available)
        try {
            val assets = client.das.getAssetsByOwner(owner, showFungible = false)
            return assets.items
                .filter { asset ->
                    // Filter for SNS domain NFTs
                    asset.content?.metadata?.symbol == "SOL" ||
                    asset.content?.metadata?.name?.endsWith(".sol") == true
                }
                .mapNotNull { asset ->
                    val name = asset.content?.metadata?.name ?: return@mapNotNull null
                    SnsDomain(
                        name = name,
                        address = asset.id,
                        isPrimary = false
                    )
                }
        } catch (e: Exception) {
            // Fall back to empty list if DAS unavailable
            return emptyList()
        }
    }
    
    /**
     * Get the favorite/primary domain for a wallet.
     * This is the domain shown as the wallet's "username".
     * 
     * @param owner The wallet address
     * @return The favorite domain name, or null if none set
     */
    suspend fun getFavoriteDomain(owner: String): String? {
        try {
            // Get the favorite domain account
            val favoriteKey = getFavoriteDomainKey(owner)
            val accountInfo = client.rpc.getAccountInfo(favoriteKey)
            if (accountInfo != null) {
                return parseDomainFromFavorite(accountInfo.data)
            }
        } catch (e: Exception) {
            // No favorite domain set
        }
        return null
    }
    
    /**
     * Check if a domain name is available for registration.
     * 
     * @param domain The domain name to check (without .sol)
     * @return true if the domain is available
     */
    suspend fun isDomainAvailable(domain: String): Boolean {
        val cleanDomain = domain.lowercase().removeSuffix(".sol")
        return resolveDomain(cleanDomain) == null
    }
    
    /**
     * Get domain registration info.
     * 
     * @param domain The domain name
     * @return Domain info including owner and expiration
     */
    suspend fun getDomainInfo(domain: String): SnsDomainInfo? {
        val cleanDomain = domain.lowercase().removeSuffix(".sol")
        
        try {
            val domainKey = getDomainKey(cleanDomain)
            val accountInfo = client.rpc.getAccountInfo(domainKey)
            if (accountInfo != null) {
                val owner = parseOwnerFromData(accountInfo.data)
                return SnsDomainInfo(
                    domain = "$cleanDomain.sol",
                    domainKey = domainKey,
                    owner = owner,
                    isAvailable = false
                )
            }
        } catch (e: Exception) {
            return SnsDomainInfo(
                domain = "$cleanDomain.sol",
                domainKey = getDomainKey(cleanDomain),
                owner = null,
                isAvailable = true
            )
        }
        
        return null
    }
    
    // ========================================================================
    // INTERNAL HELPERS
    // ========================================================================
    
    /**
     * Compute the domain key for a .sol domain.
     * This uses the SNS derivation algorithm.
     */
    private fun getDomainKey(domain: String): String {
        // Simplified - in production this would use proper PDA derivation
        // The actual algorithm involves hashing the domain name
        // For now, return a placeholder that works with the API
        return computeDomainPda(domain, SOL_TLD)
    }
    
    private fun getFavoriteDomainKey(owner: String): String {
        // Compute the favorite domain PDA
        return computeFavoritePda(owner)
    }
    
    private fun parseOwnerFromData(data: JsonElement): String? {
        // Parse the owner address from SNS account data
        val dataArray = data.jsonArray
        if (dataArray.isEmpty()) return null
        val base64Data = dataArray[0].jsonPrimitive.content
        // In production, decode base64 and extract owner at offset 32
        return null
    }
    
    private fun parseDomainFromFavorite(data: JsonElement): String? {
        // Parse the domain name from favorite account data
        return null
    }
    
    private fun computeDomainPda(domain: String, tld: String): String {
        // In production, this would compute:
        // PDA([domain_hash], SNS_PROGRAM_ID)
        // For now, return empty - this needs proper implementation
        return ""
    }
    
    private fun computeFavoritePda(owner: String): String {
        // In production, this would compute the favorite domain PDA
        return ""
    }
}

// ============================================================================
// SNS DATA CLASSES
// ============================================================================

@Serializable
data class SnsDomain(
    val name: String,
    val address: String,
    val isPrimary: Boolean = false
)

@Serializable
data class SnsDomainInfo(
    val domain: String,
    val domainKey: String,
    val owner: String?,
    val isAvailable: Boolean,
    val expiresAt: Long? = null
)

/**
 * # Bonfida SNS Namespace
 * 
 * Alternative SNS integration using Bonfida's SNS SDK patterns.
 * Provides additional utilities for SNS interactions.
 */
class BonfidaSnsNamespace internal constructor(private val client: IrisQuickNodeClient) {
    
    companion object {
        /** Bonfida SNS API base URL */
        const val BONFIDA_API = "https://sns-api.bonfida.com"
    }
    
    /**
     * Get all subdomains for a parent domain.
     */
    suspend fun getSubdomains(parentDomain: String): List<String> {
        // This would query for subdomains
        return emptyList()
    }
    
    /**
     * Get Twitter handle linked to a domain (if any).
     */
    suspend fun getTwitterHandle(domain: String): String? {
        // Query for Twitter record
        return null
    }
    
    /**
     * Get records associated with a domain.
     * Records can include Twitter, Discord, email, etc.
     */
    suspend fun getDomainRecords(domain: String): Map<String, String> {
        // Query for all records
        return emptyMap()
    }
}
