package xyz.selenus.luna.nlp

/**
 * Entity resolver for domains, tokens, and addresses
 */
interface EntityResolver {
    /**
     * Resolve a .sol or .skr domain to a wallet address
     */
    suspend fun resolveDomain(domain: String): String?
    
    /**
     * Reverse lookup - get domain for an address
     */
    suspend fun reverseLookup(address: String): String?
    
    /**
     * Resolve a token symbol to its mint address
     */
    suspend fun resolveToken(symbol: String): TokenInfo?
    
    /**
     * Check if a symbol is a known token
     */
    fun isKnownToken(symbol: String): Boolean
    
    /**
     * Resolve an address or domain input
     */
    suspend fun resolveAddress(input: String): String?
    
    /**
     * Lookup an alias from address book
     */
    suspend fun lookupAlias(alias: String): String?
    
    companion object {
        /**
         * Create a default entity resolver using Luna RPC
         */
        fun create(
            rpcEndpoint: String,
            apiKey: String? = null,
            addressBook: Map<String, String> = emptyMap()
        ): EntityResolver = DefaultEntityResolver(rpcEndpoint, apiKey, addressBook)
    }
}

/**
 * Token information
 */
data class TokenInfo(
    val symbol: String,
    val name: String,
    val mint: String,
    val decimals: Int,
    val logoUri: String? = null
)

/**
 * Well-known tokens on Solana
 */
object WellKnownTokens {
    val SOL = TokenInfo("SOL", "Solana", "So11111111111111111111111111111111111111112", 9)
    val USDC = TokenInfo("USDC", "USD Coin", "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v", 6)
    val USDT = TokenInfo("USDT", "Tether USD", "Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB", 6)
    val BONK = TokenInfo("BONK", "Bonk", "DezXAZ8z7PnrnRJjz3wXBoRgixCa6xjnB7YaB1pPB263", 5)
    val JUP = TokenInfo("JUP", "Jupiter", "JUPyiwrYJFskUPiHa7hkeR8VUtAeFoSYbKedZNsDvCN", 6)
    val RAY = TokenInfo("RAY", "Raydium", "4k3Dyjzvzp8eMZWUXbBCjEvwSkkk59S5iCNLY3QrkX6R", 6)
    val ORCA = TokenInfo("ORCA", "Orca", "orcaEKTdK7LKz57vaAYr9QeNsVEPfiu6QeMU1kektZE", 6)
    val MSOL = TokenInfo("mSOL", "Marinade staked SOL", "mSoLzYCxHdYgdzU16g5QSh3i5K3z3KZK7ytfqcJm7So", 9)
    val JITOSOL = TokenInfo("JitoSOL", "Jito Staked SOL", "J1toso1uCk3RLmjorhTtrVwY9HJ7X8V9yYac6Y7kGCPn", 9)
    val PYTH = TokenInfo("PYTH", "Pyth Network", "HZ1JovNiVvGrGNiiYvEozEVgZ58xaU3RKwX8eACQBCt3", 6)
    val WIF = TokenInfo("WIF", "dogwifhat", "EKpQGSJtjMFqKZ9KQanSqYXRcF8fBopzLHYxdM65zcjm", 6)
    val RENDER = TokenInfo("RENDER", "Render Token", "rndrizKT3MK1iimdxRdWabcF7Zg7AR5T4nud4EkHBof", 8)
    val HNT = TokenInfo("HNT", "Helium", "hntyVP6YFm1Hg25TN9WGLqM12b8TQmcknKrdu1oxWux", 8)
    val MOBILE = TokenInfo("MOBILE", "Helium Mobile", "mb1eu7TzEc71KxDpsmsKoucSSuuoGLv1drys1oP2jh6", 6)
    val IOT = TokenInfo("IOT", "Helium IOT", "iotEVVZLEywoTn1QdwNPddxPWszn3zFhEot3MfL9fns", 6)
    
    val ALL = listOf(
        SOL, USDC, USDT, BONK, JUP, RAY, ORCA, MSOL, JITOSOL, PYTH, WIF, RENDER, HNT, MOBILE, IOT
    )
    
    val BY_SYMBOL = ALL.associateBy { it.symbol.uppercase() }
    val BY_MINT = ALL.associateBy { it.mint }
    
    fun find(symbol: String): TokenInfo? = BY_SYMBOL[symbol.uppercase()]
    fun findByMint(mint: String): TokenInfo? = BY_MINT[mint]
}
