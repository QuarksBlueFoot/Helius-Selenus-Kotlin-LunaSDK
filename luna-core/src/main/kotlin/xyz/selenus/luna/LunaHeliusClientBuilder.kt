package xyz.selenus.luna

import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * # Fluent builder for [LunaHeliusClient]
 *
 * Mirrors the Helius Rust SDK's `HeliusBuilder` pattern (and goes a bit
 * further). Use this when you want:
 *  - Custom HTTP timeouts (default OkHttp is 10s read; some Helius DAS
 *    queries against very large wallets benefit from 30–60s).
 *  - A pre-configured [OkHttpClient] (custom dispatcher, proxy, MITM cert
 *    pinning, etc.) that LunaSDK should reuse.
 *  - Custom interceptors for logging / metrics / retry.
 *  - Request-tagging headers (User-Agent for analytics, request-id for
 *    distributed tracing).
 *  - A custom JSON codec — useful if you need polymorphic discriminator
 *    overrides for your own response types.
 *
 * The builder is **immutable + chainable**: every method returns a new
 * builder so it's safe to share a base configuration across threads.
 *
 * ## Examples
 *
 * Simple — same as the constructor:
 * ```kotlin
 * val client = LunaHeliusClientBuilder("YOUR_API_KEY").build()
 * ```
 *
 * Advanced — production tuning:
 * ```kotlin
 * val client = LunaHeliusClientBuilder("YOUR_API_KEY")
 *     .cluster(Cluster.MAINNET)
 *     .readTimeout(30.seconds)
 *     .userAgent("my-indexer/1.4.0")
 *     .addInterceptor(metricsInterceptor)
 *     .build()
 * ```
 *
 * Custom HTTP client (use this when you already manage your own pool):
 * ```kotlin
 * val client = LunaHeliusClientBuilder("YOUR_API_KEY")
 *     .httpClient(myExistingOkHttpClient)
 *     .build()
 * ```
 */
class LunaHeliusClientBuilder(private val apiKey: String) {

    private var cluster: Cluster = Cluster.MAINNET
    private var connectTimeout: Duration = 10.seconds
    private var readTimeout: Duration = 30.seconds
    private var writeTimeout: Duration = 30.seconds
    private var callTimeout: Duration = 60.seconds
    private var userAgent: String? = null
    private var customHttpClient: OkHttpClient? = null
    private var customJson: Json? = null
    private val interceptors: MutableList<Interceptor> = mutableListOf()

    init {
        require(apiKey.isNotBlank()) { "apiKey must not be blank" }
    }

    /** Cluster to target. Default is mainnet. */
    fun cluster(cluster: Cluster): LunaHeliusClientBuilder = apply { this.cluster = cluster }

    /** TCP/TLS handshake timeout. Default 10s. */
    fun connectTimeout(d: Duration): LunaHeliusClientBuilder = apply { this.connectTimeout = d }

    /** Read timeout (per byte read from socket). Default 30s. */
    fun readTimeout(d: Duration): LunaHeliusClientBuilder = apply { this.readTimeout = d }

    /** Write timeout (per byte written to socket). Default 30s. */
    fun writeTimeout(d: Duration): LunaHeliusClientBuilder = apply { this.writeTimeout = d }

    /** End-to-end timeout for a single call. Default 60s. */
    fun callTimeout(d: Duration): LunaHeliusClientBuilder = apply { this.callTimeout = d }

    /**
     * Adds a User-Agent header to every request. Helius's billing dashboard
     * shows per-UA breakdowns so this is the cleanest way to track which of
     * your services is consuming credits.
     */
    fun userAgent(value: String): LunaHeliusClientBuilder = apply { this.userAgent = value }

    /**
     * Add a custom OkHttp interceptor (logging, metrics, retry, etc.). Multiple
     * interceptors are chained in the order they were added.
     */
    fun addInterceptor(interceptor: Interceptor): LunaHeliusClientBuilder = apply {
        this.interceptors += interceptor
    }

    /**
     * Replace the entire HTTP transport with a caller-supplied [OkHttpClient].
     * Useful when your application already manages a connection pool. When
     * set, [connectTimeout] / [readTimeout] / [writeTimeout] / [callTimeout]
     * / [addInterceptor] / [userAgent] are ignored — your client owns the
     * full configuration.
     */
    fun httpClient(client: OkHttpClient): LunaHeliusClientBuilder = apply {
        this.customHttpClient = client
    }

    /**
     * Replace the JSON codec. The default `Json { ignoreUnknownKeys = true;
     * encodeDefaults = true }` covers every Helius response — only override
     * if you need polymorphic discriminator hooks for your own types.
     */
    fun json(json: Json): LunaHeliusClientBuilder = apply { this.customJson = json }

    /** Build and return a configured [LunaHeliusClient]. */
    fun build(): LunaHeliusClient {
        val httpClient = customHttpClient ?: buildDefaultHttpClient()
        val json = customJson ?: Json { ignoreUnknownKeys = true; encodeDefaults = true }
        return LunaHeliusClient(
            apiKey = apiKey,
            cluster = cluster,
            httpClient = httpClient,
            json = json
        )
    }

    private fun buildDefaultHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(connectTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
            .readTimeout(readTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
            .writeTimeout(writeTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
            .callTimeout(callTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)

        // Add user-agent as a header interceptor so it's set for every call,
        // including those issued through feature modules that build their own
        // Request objects.
        userAgent?.let { ua ->
            builder.addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent", ua)
                    .build()
                chain.proceed(req)
            }
        }
        interceptors.forEach { builder.addInterceptor(it) }
        return builder.build()
    }
}

/**
 * # Multi-cluster factory
 *
 * Mirrors the Helius Rust SDK's `HeliusFactory`. Use when you need multiple
 * clients pointing at different clusters (e.g. dApp that reads mainnet but
 * faucets devnet) and want to share a single HTTP/JSON config across them.
 *
 * ```kotlin
 * val factory = LunaHeliusClientFactory("YOUR_API_KEY")
 *     .userAgent("my-app/1.0")
 *     .readTimeout(30.seconds)
 *
 * val mainnet = factory.create(Cluster.MAINNET)
 * val devnet = factory.create(Cluster.DEVNET)
 * ```
 *
 * The factory holds the configured [OkHttpClient] internally so all clients
 * it produces share the same connection pool.
 */
class LunaHeliusClientFactory(private val apiKey: String) {
    private var connectTimeout: Duration = 10.seconds
    private var readTimeout: Duration = 30.seconds
    private var writeTimeout: Duration = 30.seconds
    private var callTimeout: Duration = 60.seconds
    private var userAgent: String? = null
    private var customJson: Json? = null
    private val interceptors: MutableList<Interceptor> = mutableListOf()

    init {
        require(apiKey.isNotBlank()) { "apiKey must not be blank" }
    }

    fun connectTimeout(d: Duration): LunaHeliusClientFactory = apply { this.connectTimeout = d }
    fun readTimeout(d: Duration): LunaHeliusClientFactory = apply { this.readTimeout = d }
    fun writeTimeout(d: Duration): LunaHeliusClientFactory = apply { this.writeTimeout = d }
    fun callTimeout(d: Duration): LunaHeliusClientFactory = apply { this.callTimeout = d }
    fun userAgent(value: String): LunaHeliusClientFactory = apply { this.userAgent = value }
    fun addInterceptor(interceptor: Interceptor): LunaHeliusClientFactory = apply {
        this.interceptors += interceptor
    }
    fun json(json: Json): LunaHeliusClientFactory = apply { this.customJson = json }

    /**
     * Lazy shared HTTP client. Built on first `create()` call and reused for
     * every subsequent client — so connection pool, interceptors and
     * timeouts are shared across all clusters this factory produces.
     */
    private val sharedHttpClient: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .connectTimeout(connectTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
            .readTimeout(readTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
            .writeTimeout(writeTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
            .callTimeout(callTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)

        userAgent?.let { ua ->
            builder.addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent", ua)
                    .build()
                chain.proceed(req)
            }
        }
        interceptors.forEach { builder.addInterceptor(it) }
        builder.build()
    }

    private val sharedJson: Json by lazy {
        customJson ?: Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }

    /** Create a client for [cluster] sharing this factory's HTTP/JSON config. */
    fun create(cluster: Cluster): LunaHeliusClient = LunaHeliusClient(
        apiKey = apiKey,
        cluster = cluster,
        httpClient = sharedHttpClient,
        json = sharedJson
    )
}
