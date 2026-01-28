package xyz.selenus.luna.nlp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Duration

/**
 * Default entity resolver using Helius RPC and SNS
 */
internal class DefaultEntityResolver(
    private val rpcEndpoint: String,
    private val apiKey: String?,
    private val addressBook: Map<String, String>
) : EntityResolver {
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(Duration.ofSeconds(30))
        .readTimeout(Duration.ofSeconds(30))
        .build()
    
    private val json = Json { ignoreUnknownKeys = true }
    
    // Cache for resolved domains
    private val domainCache = mutableMapOf<String, String>()
    private val tokenCache = mutableMapOf<String, TokenInfo>()
    
    override suspend fun resolveDomain(domain: String): String? = withContext(Dispatchers.IO) {
        // Check cache first
        domainCache[domain.lowercase()]?.let { return@withContext it }
        
        val cleanDomain = domain.lowercase().removeSuffix(".sol").removeSuffix(".skr")
        
        // Try SNS (.sol domains)
        if (domain.endsWith(".sol") || !domain.endsWith(".skr")) {
            resolveSolDomain(cleanDomain)?.let { address ->
                domainCache[domain.lowercase()] = address
                return@withContext address
            }
        }
        
        // Try SKR domains (.skr)
        if (domain.endsWith(".skr")) {
            resolveSkrDomain(cleanDomain)?.let { address ->
                domainCache[domain.lowercase()] = address
                return@withContext address
            }
        }
        
        null
    }
    
    private suspend fun resolveSolDomain(domain: String): String? {
        return try {
            // Use Helius DAS API for SNS resolution
            val requestBody = buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", 1)
                put("method", "getAssetsByOwner")
                putJsonObject("params") {
                    put("ownerAddress", domain)
                    put("page", 1)
                    put("limit", 1)
                }
            }.toString()
            
            // For now, use a simple approach - call SNS program directly
            // In production, this would use the actual SNS resolution API
            callSnsApi(domain)
        } catch (e: Exception) {
            null
        }
    }
    
    private suspend fun resolveSkrDomain(domain: String): String? {
        return try {
            // SKR domain resolution
            // In production, this would call the SKR resolution service
            callSkrApi(domain)
        } catch (e: Exception) {
            null
        }
    }
    
    private suspend fun callSnsApi(domain: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = "https://sns-sdk-proxy.bonfida.workers.dev/resolve/$domain"
            val request = Request.Builder().url(url).get().build()
            val response = httpClient.newCall(request).execute()
            
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@withContext null
                val jsonResponse = json.parseToJsonElement(body).jsonObject
                jsonResponse["result"]?.jsonPrimitive?.contentOrNull
            } else null
        } catch (e: Exception) {
            null
        }
    }
    
    private suspend fun callSkrApi(domain: String): String? = withContext(Dispatchers.IO) {
        try {
            // SKR uses a similar resolution pattern
            val url = "https://api.skr.domains/v1/resolve/$domain"
            val request = Request.Builder().url(url).get().build()
            val response = httpClient.newCall(request).execute()
            
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@withContext null
                val jsonResponse = json.parseToJsonElement(body).jsonObject
                jsonResponse["address"]?.jsonPrimitive?.contentOrNull
            } else null
        } catch (e: Exception) {
            null
        }
    }
    
    override suspend fun reverseLookup(address: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = "https://sns-sdk-proxy.bonfida.workers.dev/favorite-domain/$address"
            val request = Request.Builder().url(url).get().build()
            val response = httpClient.newCall(request).execute()
            
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@withContext null
                val jsonResponse = json.parseToJsonElement(body).jsonObject
                jsonResponse["result"]?.jsonPrimitive?.contentOrNull?.let { "$it.sol" }
            } else null
        } catch (e: Exception) {
            null
        }
    }
    
    override suspend fun resolveToken(symbol: String): TokenInfo? {
        // Check cache
        tokenCache[symbol.uppercase()]?.let { return it }
        
        // Check well-known tokens
        WellKnownTokens.find(symbol)?.let { token ->
            tokenCache[symbol.uppercase()] = token
            return token
        }
        
        // Try Jupiter token list API
        return fetchTokenFromJupiter(symbol)?.also {
            tokenCache[symbol.uppercase()] = it
        }
    }
    
    private suspend fun fetchTokenFromJupiter(symbol: String): TokenInfo? = withContext(Dispatchers.IO) {
        try {
            val url = "https://token.jup.ag/strict"
            val request = Request.Builder().url(url).get().build()
            val response = httpClient.newCall(request).execute()
            
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@withContext null
                val tokens = json.parseToJsonElement(body).jsonArray
                
                tokens.firstOrNull { token ->
                    token.jsonObject["symbol"]?.jsonPrimitive?.contentOrNull
                        ?.equals(symbol, ignoreCase = true) == true
                }?.jsonObject?.let { tokenObj ->
                    TokenInfo(
                        symbol = tokenObj["symbol"]?.jsonPrimitive?.content ?: symbol,
                        name = tokenObj["name"]?.jsonPrimitive?.content ?: symbol,
                        mint = tokenObj["address"]?.jsonPrimitive?.content ?: return@withContext null,
                        decimals = tokenObj["decimals"]?.jsonPrimitive?.int ?: 9,
                        logoUri = tokenObj["logoURI"]?.jsonPrimitive?.contentOrNull
                    )
                }
            } else null
        } catch (e: Exception) {
            null
        }
    }
    
    override fun isKnownToken(symbol: String): Boolean {
        return WellKnownTokens.BY_SYMBOL.containsKey(symbol.uppercase()) ||
               tokenCache.containsKey(symbol.uppercase())
    }
    
    override suspend fun resolveAddress(input: String): String? {
        // Check if it's already a valid base58 address
        if (isValidBase58Address(input)) {
            return input
        }
        
        // Check address book
        addressBook[input.lowercase()]?.let { return it }
        
        // Try domain resolution
        if (input.contains(".")) {
            return resolveDomain(input)
        }
        
        // Check if it's an alias
        return lookupAlias(input)
    }
    
    override suspend fun lookupAlias(alias: String): String? {
        return addressBook[alias.lowercase().removePrefix("@")]
    }
    
    private fun isValidBase58Address(input: String): Boolean {
        if (input.length !in 32..44) return false
        val base58Chars = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        return input.all { it in base58Chars }
    }
}
