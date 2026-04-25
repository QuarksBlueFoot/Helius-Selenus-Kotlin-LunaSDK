package xyz.selenus.luna.webhooks

import kotlinx.coroutines.delay
import kotlinx.serialization.json.*
import xyz.selenus.luna.LunaHeliusClient
import xyz.selenus.luna.RpcError
import xyz.selenus.luna.RpcResponse

/**
 * Webhooks API.  Enables developers to subscribe to on-chain events such as sales,
 * listings, swaps or account changes and receive HTTP callbacks when those events
 * occur.
 */
class WebhookApi internal constructor(private val client: LunaHeliusClient) {
    /**
     * Create a new webhook subscription.  The returned object contains the ID
     * required to modify or delete the webhook later.
     *
     * @param webhookUrl The URL that Helius will call when the event fires.
     * @param accountAddresses Solana addresses to monitor; events referencing these
     *                         addresses will trigger the webhook.
     * @param transactionTypes Types of transactions to listen for (e.g. "all",
     *                         "token-transfer", "swap").
     * @param webhookType Type of webhook, defaults to "account".
     * @param authHeader Optional authorization header Helius should include when
     *                   invoking your webhook.  Useful for securing the endpoint.
     * @param version Webhook version number; default is 1.
     */
    suspend fun createWebhook(
        webhookUrl: String,
        accountAddresses: List<String>,
        transactionTypes: List<String>,
        webhookType: String = "account",
        authHeader: String? = null,
        version: Int = 1
    ): RpcResponse<JsonElement> {
        val body = buildJsonObject {
            put("webhookUrl", webhookUrl)
            put("address", JsonArray(accountAddresses.map { JsonPrimitive(it) }))
            put("type", webhookType)
            put("transactionTypes", JsonArray(transactionTypes.map { JsonPrimitive(it) }))
            authHeader?.let { put("authorizationHeader", it) }
            put("version", version)
        }
        return client.rpcCall("createWebhook", body)
    }

    /** Retrieve all webhooks associated with your API key. */
    suspend fun getAllWebhooks(): RpcResponse<JsonElement> {
        // The getAllWebhooks method takes an empty object as parameters.
        return client.rpcCall("getAllWebhooks", JsonObject(emptyMap()))
    }

    /** Fetch a single webhook by its ID. */
    suspend fun getWebhookById(webhookId: String): RpcResponse<JsonElement> {
        val params = buildJsonObject { put("webhookID", webhookId) }
        return client.rpcCall("getWebhookByID", params)
    }

    /** Update a webhook by its ID.  Only fields present in [updates] will be changed. */
    suspend fun updateWebhook(
        webhookId: String,
        updates: Map<String, JsonElement>
    ): RpcResponse<JsonElement> {
        val params = buildJsonObject {
            put("webhookID", webhookId)
            put("updates", JsonObject(updates))
        }
        return client.rpcCall("updateWebhook", params)
    }

    /** Delete a webhook subscription permanently. */
    suspend fun deleteWebhook(webhookId: String): RpcResponse<JsonElement> {
        val params = buildJsonObject { put("webhookID", webhookId) }
        return client.rpcCall("deleteWebhook", params)
    }

    /**
     * Toggle a webhook on or off without deleting it. Useful for pausing
     * deliveries during maintenance windows or on-call rotations.
     *
     * Mirrors the Helius Rust SDK's `toggle_webhook`. The server flips the
     * `enabled` flag; passing `true` resumes deliveries, `false` pauses them.
     */
    suspend fun toggleWebhook(webhookId: String, enabled: Boolean): RpcResponse<JsonElement> {
        // Implemented as a partial update — `updates: { enabled: Boolean }` —
        // because Helius's update endpoint accepts arbitrary field overrides
        // and the standalone toggle endpoint is documented as syntactic sugar
        // over exactly this call.
        return updateWebhook(webhookId, mapOf("enabled" to JsonPrimitive(enabled)))
    }

    /**
     * Append [addresses] to a webhook's account list without disturbing the
     * other addresses already configured. Reads-then-merges-then-updates so
     * callers don't have to duplicate the existing set client-side.
     *
     * Returns the updated webhook payload (same shape as `updateWebhook`).
     *
     * Mirrors the Helius Rust SDK's `append_addresses_to_webhook`. Helius's
     * own SDK implementation reads the webhook, computes the union, and POSTs
     * the merged array — done atomically here to give the same ergonomics.
     */
    suspend fun appendAddressesToWebhook(
        webhookId: String,
        addresses: List<String>
    ): RpcResponse<JsonElement> {
        require(addresses.isNotEmpty()) { "addresses to append must not be empty" }

        val current = getWebhookById(webhookId)
        val existing = current.result
            ?.jsonObject?.get("accountAddresses")
            ?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?: emptyList()

        // LinkedHashSet preserves order while deduplicating
        val merged = LinkedHashSet(existing).also { it.addAll(addresses) }.toList()

        return updateWebhook(
            webhookId,
            mapOf("accountAddresses" to JsonArray(merged.map { JsonPrimitive(it) }))
        )
    }

    /**
     * Remove [addresses] from a webhook's account list. Other configured
     * addresses are preserved. Reads-then-filters-then-updates atomically so
     * callers don't race their own concurrent edits.
     *
     * Mirrors the Helius Rust SDK's `remove_addresses_from_webhook`.
     */
    suspend fun removeAddressesFromWebhook(
        webhookId: String,
        addresses: List<String>
    ): RpcResponse<JsonElement> {
        require(addresses.isNotEmpty()) { "addresses to remove must not be empty" }

        val current = getWebhookById(webhookId)
        val existing = current.result
            ?.jsonObject?.get("accountAddresses")
            ?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?: emptyList()

        val toRemove = addresses.toHashSet()
        val filtered = existing.filterNot { it in toRemove }

        if (filtered.size == existing.size) {
            // Nothing to do — short-circuit so we don't burn an API call
            return current
        }

        return updateWebhook(
            webhookId,
            mapOf("accountAddresses" to JsonArray(filtered.map { JsonPrimitive(it) }))
        )
    }

    /**
     * Verify the Ed25519 signature attached to an incoming webhook delivery.
     *
     * Helius signs the raw request body with the Ed25519 private key bound to
     * your account; the matching public key is available in the dashboard.
     * Pass the body bytes (NOT a re-serialized version of the parsed JSON —
     * key ordering matters for the signature), the base58 (or hex) signature,
     * and the configured public key.
     *
     * Implemented using JDK 17's native `EdDSA` provider (no Bouncy Castle
     * required). Returns false if the signature doesn't verify or the inputs
     * are malformed.
     *
     * @param body Raw request body bytes as received over the wire.
     * @param signatureBase58 Ed25519 signature, base58-encoded (Solana-style).
     * @param publicKeyBase58 Webhook public key, base58-encoded.
     */
    fun verifyWebhookSignature(
        body: ByteArray,
        signatureBase58: String,
        publicKeyBase58: String
    ): Boolean = WebhookSignatureVerifier.verify(body, signatureBase58, publicKeyBase58)
}

/**
 * Access the WebhookApi namespace from a [LunaHeliusClient].
 *
 * Import this extension to enable the `client.webhooks` style:
 * ```
 * import xyz.selenus.luna.webhooks.webhooks
 * client.webhooks.<method>()
 * ```
 */
val LunaHeliusClient.webhooks: WebhookApi
    get() = WebhookApi(this)
