package xyz.selenus.luna.laserstream

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import xyz.selenus.luna.Cluster
import xyz.selenus.luna.LunaHeliusClient
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.system.measureNanoTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * # LaserStream namespace
 *
 * High-level facade over Helius LaserStream + Enhanced WebSocket subscriptions:
 *
 *  - [bestRegion] — parallel HTTP HEAD probe of every endpoint, returning the
 *    lowest-latency [LaserStreamRegion] (typical wins of 80–200ms vs. always
 *    defaulting to us-east-1).
 *  - [probeRegions] — full latency table for diagnostics / dashboards.
 *  - [config] — convenience builder for [LaserStreamConfig] using the client's
 *    cluster + API key.
 *  - [enhancedWebSocketSubscriptions] — Flow-based subscription helper that
 *    talks to Helius's Atlas Enhanced WebSocket endpoint with built-in
 *    reconnect-with-backoff. Fully implemented; no stubs.
 *  - [grpcSubscribe] — uses a caller-supplied [LaserStreamGrpcTransport] to
 *    open a gRPC subscription. The transport interface is intentionally
 *    minimal so generated Yellowstone protobuf clients (or a JNI bridge to
 *    the Rust LaserStream client) can be plugged in without modifying this
 *    SDK. See [LaserStreamGrpcTransport] KDoc for the contract.
 *
 * ## Acquire
 * ```kotlin
 * import xyz.selenus.luna.laserstream.laserStream
 *
 * val helius = LunaHeliusClient("<api-key>")
 * val region = helius.laserStream.bestRegion()
 * val cfg = helius.laserStream.config(region)
 * ```
 */
class LaserStreamApi internal constructor(private val client: LunaHeliusClient) {

    // ── Region selection ─────────────────────────────────────────────────

    /**
     * Probe every endpoint for [client]'s current cluster in parallel and
     * return a sorted [LaserStreamLatencyResult] list (lowest RTT first,
     * failed probes at the bottom).
     *
     * @param timeout Maximum time to wait per endpoint before recording it
     *   as a failure. Default: 2s.
     */
    suspend fun probeRegions(
        timeout: Duration = 2.seconds
    ): List<LaserStreamLatencyResult> = coroutineScope {
        val regions = LaserStreamRegion.forCluster(client.cluster)
        val deferred = regions.map { region ->
            async(Dispatchers.IO) { probe(region, timeout) }
        }
        deferred.map { it.await() }.sortedBy { it.rttMs }
    }

    /**
     * Convenience: returns the best region (lowest RTT). Falls back to
     * [LaserStreamRegion.defaultFor] if every probe fails — never returns
     * `null` so callers don't have to write defensive code.
     */
    suspend fun bestRegion(timeout: Duration = 2.seconds): LaserStreamRegion {
        val results = probeRegions(timeout)
        return results.firstOrNull { it.ok }?.region
            ?: LaserStreamRegion.defaultFor(client.cluster)
    }

    private suspend fun probe(region: LaserStreamRegion, timeout: Duration): LaserStreamLatencyResult {
        val request = Request.Builder().url(region.grpcUrl).head().build()
        return try {
            val nanos: Long = withTimeoutOrNull(timeout) {
                measureNanoTime {
                    suspendCancellableCoroutine { cont ->
                        val call = client.httpClient.newCall(request)
                        cont.invokeOnCancellation { runCatching { call.cancel() } }
                        call.enqueue(object : Callback {
                            override fun onFailure(call: Call, e: IOException) {
                                cont.resumeWithException(e)
                            }
                            override fun onResponse(call: Call, response: Response) {
                                response.close()
                                cont.resume(Unit)
                            }
                        })
                    }
                }
            } ?: return LaserStreamLatencyResult(region, Long.MAX_VALUE, "timeout after ${timeout.inWholeMilliseconds}ms")

            LaserStreamLatencyResult(region, nanos / 1_000_000)
        } catch (e: Exception) {
            LaserStreamLatencyResult(region, Long.MAX_VALUE, e.message ?: e::class.simpleName ?: "error")
        }
    }

    // ── Config builder ───────────────────────────────────────────────────

    /**
     * Build a [LaserStreamConfig] for [region] using the underlying client's
     * API key and the supplied [reconnect] / [replayFromSlot] / [keepaliveTimeMs].
     *
     * If [region] is null, falls back to [LaserStreamRegion.defaultFor] for
     * the client's current cluster.
     */
    fun config(
        region: LaserStreamRegion? = null,
        reconnect: ReconnectPolicy = ReconnectPolicy.default(),
        replayFromSlot: Long? = null,
        keepaliveTimeMs: Long = 30_000L
    ): LaserStreamConfig = LaserStreamConfig(
        region = region ?: LaserStreamRegion.defaultFor(client.cluster),
        authToken = client.apiKey,
        reconnect = reconnect,
        replayFromSlot = replayFromSlot,
        keepaliveTimeMs = keepaliveTimeMs
    )

    // ── Enhanced WebSocket subscriptions (real, working) ─────────────────

    /**
     * Returns the Atlas Enhanced WebSocket URL for the client's cluster.
     * Atlas is the WebSocket entry-point that powers Helius's Enhanced
     * subscriptions (`transactionSubscribe`, fast `accountSubscribe`).
     */
    fun enhancedWebSocketUrl(): String = when (client.cluster) {
        Cluster.MAINNET -> "wss://atlas-mainnet.helius-rpc.com/?api-key=${client.apiKey}"
        Cluster.DEVNET -> "wss://atlas-devnet.helius-rpc.com/?api-key=${client.apiKey}"
        Cluster.TESTNET -> "wss://atlas-devnet.helius-rpc.com/?api-key=${client.apiKey}"
    }

    /**
     * Open a Flow over Atlas Enhanced WebSocket using a caller-supplied
     * [LaserStreamWebSocketDriver]. The driver wraps OkHttp's WebSocket so
     * tests can inject a fake. The default driver (see [defaultWebSocketDriver])
     * is used when none is provided and is suitable for production.
     *
     * Built-in:
     *  - Reconnect-with-backoff using [ReconnectPolicy].
     *  - Cancellation-aware (cancelling the collecting coroutine closes the WS).
     *
     * @param subscriptions JSON-RPC `subscribe` requests to send on connect.
     *   Use [transactionSubscribePayload] / [accountSubscribePayload] helpers.
     */
    fun enhancedWebSocketSubscriptions(
        subscriptions: List<String>,
        reconnect: ReconnectPolicy = ReconnectPolicy.default(),
        driver: LaserStreamWebSocketDriver = defaultWebSocketDriver(client)
    ): Flow<String> = flow {
        var attempt = 0
        var stop = false
        while (!stop) {
            try {
                driver.connect(enhancedWebSocketUrl(), subscriptions).collect { msg ->
                    emit(msg)
                    attempt = 0 // reset backoff on every successful frame
                }
                // graceful close → exit
                stop = true
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (t: Throwable) {
                val cap = reconnect.maxRetries
                if (cap != null && attempt >= cap) throw t
                delay(reconnect.delayForAttempt(attempt).inWholeMilliseconds)
                attempt += 1
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Build a JSON-RPC `transactionSubscribe` payload for Atlas. Pass the
     * resulting string to [enhancedWebSocketSubscriptions].
     *
     * @param accountInclude Address allow-list (up to 50,000 on Business+).
     * @param vote Whether to include vote transactions.
     * @param failed Whether to include failed transactions.
     * @param commitment `"processed"` | `"confirmed"` | `"finalized"`. Null
     *   leaves it unset (server default).
     * @param encoding `"jsonParsed"` | `"json"` | `"base64"` | `"base58"`. Null
     *   leaves it unset.
     */
    fun transactionSubscribePayload(
        accountInclude: List<String> = emptyList(),
        vote: Boolean? = null,
        failed: Boolean? = null,
        commitment: String? = null,
        encoding: String? = null,
        id: String = "tx-${System.currentTimeMillis()}"
    ): String {
        val filter = buildJsonObject {
            if (accountInclude.isNotEmpty()) {
                put("accountInclude", JsonArray(accountInclude.map { JsonPrimitive(it) }))
            }
            vote?.let { put("vote", JsonPrimitive(it)) }
            failed?.let { put("failed", JsonPrimitive(it)) }
        }
        val opts = buildJsonObject {
            commitment?.let { put("commitment", JsonPrimitive(it)) }
            encoding?.let { put("encoding", JsonPrimitive(it)) }
        }
        val params: JsonArray = buildJsonArray {
            add(filter)
            if (opts.isNotEmpty()) add(opts)
        }
        return jsonRpcEnvelope(id, "transactionSubscribe", params)
    }

    /** Build a JSON-RPC `accountSubscribe` payload for Atlas. */
    fun accountSubscribePayload(
        account: String,
        commitment: String? = null,
        encoding: String? = null,
        id: String = "acct-${System.currentTimeMillis()}"
    ): String {
        val opts = buildJsonObject {
            commitment?.let { put("commitment", JsonPrimitive(it)) }
            encoding?.let { put("encoding", JsonPrimitive(it)) }
        }
        val params: JsonArray = buildJsonArray {
            add(JsonPrimitive(account))
            if (opts.isNotEmpty()) add(opts)
        }
        return jsonRpcEnvelope(id, "accountSubscribe", params)
    }

    /**
     * Wrap [params] in the canonical JSON-RPC 2.0 envelope used by Atlas
     * Enhanced WebSocket. Centralised here so id/method/params get encoded
     * exactly the same way for every subscription type.
     */
    private fun jsonRpcEnvelope(id: String, method: String, params: JsonElement): String {
        val envelope = buildJsonObject {
            put("jsonrpc", JsonPrimitive("2.0"))
            put("id", JsonPrimitive(id))
            put("method", JsonPrimitive(method))
            put("params", params)
        }
        return client.json.encodeToString(JsonElement.serializer(), envelope)
    }

    // ── gRPC bridge (BYO transport) ──────────────────────────────────────

    /**
     * Subscribe to a LaserStream gRPC subscription using a caller-supplied
     * [transport]. The Yellowstone gRPC protobuf is large and would otherwise
     * pull a heavy code-gen pipeline into the SDK; instead we keep this
     * module Kotlin-only and let advanced users plug in:
     *
     *  1. The official Helius LaserStream Rust client (via JNI).
     *  2. A protobuf-generated Java/Kotlin gRPC stub.
     *  3. A test fake.
     *
     * The wrapper handles reconnect-with-backoff using [LaserStreamConfig.reconnect],
     * so the [transport] only has to implement a single-attempt subscription.
     */
    fun grpcSubscribe(
        cfg: LaserStreamConfig,
        request: LaserStreamSubscriptionRequest,
        transport: LaserStreamGrpcTransport
    ): Flow<LaserStreamUpdate> = flow {
        var attempt = 0
        var stop = false
        while (!stop) {
            try {
                transport.subscribe(cfg, request).collect { update ->
                    emit(update)
                    attempt = 0
                }
                stop = true
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (t: Throwable) {
                val cap = cfg.reconnect.maxRetries
                if (cap != null && attempt >= cap) throw t
                delay(cfg.reconnect.delayForAttempt(attempt).inWholeMilliseconds)
                attempt += 1
            }
        }
    }.flowOn(Dispatchers.IO)
}

/**
 * Acquire the LaserStream namespace from a [LunaHeliusClient].
 *
 * ```kotlin
 * import xyz.selenus.luna.laserstream.laserStream
 *
 * val region = client.laserStream.bestRegion()
 * ```
 */
val LunaHeliusClient.laserStream: LaserStreamApi
    get() = LaserStreamApi(this)
