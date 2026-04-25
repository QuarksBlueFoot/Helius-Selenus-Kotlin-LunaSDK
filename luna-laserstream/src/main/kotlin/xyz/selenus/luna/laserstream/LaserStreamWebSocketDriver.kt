package xyz.selenus.luna.laserstream

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import xyz.selenus.luna.LunaHeliusClient

/**
 * Pluggable WebSocket transport for [LaserStreamApi.enhancedWebSocketSubscriptions].
 *
 * Splitting this out keeps the subscription wrapper testable without binding
 * to OkHttp directly — fakes can implement this interface and emit a scripted
 * sequence of frames.
 */
fun interface LaserStreamWebSocketDriver {
    /**
     * Open a WebSocket to [url], send each entry of [subscriptions] on
     * connect, and emit every text frame received via the returned cold
     * [Flow]. Cancellation must close the underlying socket.
     */
    fun connect(url: String, subscriptions: List<String>): Flow<String>
}

/**
 * Default driver: real OkHttp WebSocket client. Reuses the [OkHttpClient]
 * configured on the [LunaHeliusClient] so connection-pool, interceptors, and
 * timeout policy stay consistent across the SDK.
 *
 * Behavior:
 *  - Sends every subscription request immediately after the WS opens.
 *  - Forwards all text frames into the Flow.
 *  - On unexpected close (network drop, server `Goaway`, etc.), throws a
 *    [LaserStreamWebSocketClosedException] inside the Flow so the wrapper
 *    in [LaserStreamApi.enhancedWebSocketSubscriptions] can reconnect.
 */
fun defaultWebSocketDriver(client: LunaHeliusClient): LaserStreamWebSocketDriver =
    DefaultLaserStreamWebSocketDriver(client.httpClient)

internal class DefaultLaserStreamWebSocketDriver(
    private val httpClient: OkHttpClient
) : LaserStreamWebSocketDriver {

    override fun connect(url: String, subscriptions: List<String>): Flow<String> = callbackFlow {
        val request = Request.Builder().url(url).build()
        val ws = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                subscriptions.forEach { webSocket.send(it) }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                trySend(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                // Atlas uses text frames for subscription notifications; if a
                // server-initiated binary frame arrives, decode as UTF-8 best-effort.
                trySend(bytes.utf8())
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                // Normal close → complete the Flow.
                if (code == 1000) close() else close(LaserStreamWebSocketClosedException(code, reason))
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                close(t)
            }
        })

        awaitClose {
            // Code 1000 = normal closure
            ws.close(1000, "client cancelled")
        }
    }
}

/**
 * Thrown into the WebSocket Flow when the server closes with a non-1000 code.
 * The [LaserStreamApi.enhancedWebSocketSubscriptions] wrapper catches this
 * and reconnects according to the [ReconnectPolicy].
 */
class LaserStreamWebSocketClosedException(
    val code: Int,
    val reason: String
) : RuntimeException("WebSocket closed: code=$code reason=$reason")
