package xyz.selenus.luna.laserstream

import kotlinx.coroutines.flow.Flow

/**
 * # LaserStream gRPC transport contract (BYO)
 *
 * The Yellowstone gRPC protobuf is large (~300 lines, ~30 message types) and
 * its code-gen pipeline is a heavy Gradle dependency we do not want to bake
 * into the LunaSDK Kotlin core. Instead we expose this minimal interface so
 * advanced consumers can wire up:
 *
 *  - The official Helius **LaserStream Rust client** through JNI/UniFFI.
 *  - A **protobuf-generated Java/Kotlin gRPC stub** they own and update.
 *  - An in-memory **fake** for tests.
 *
 * The framework handles reconnect-with-backoff (see [LaserStreamApi.grpcSubscribe]),
 * so implementations only need to handle a *single* subscription attempt and
 * surface failures via exceptions thrown from the returned Flow.
 *
 * ## Implementation contract
 *  - [subscribe] returns a cold [Flow]. Collecting it opens the connection.
 *  - Cancelling the collecting coroutine MUST close the underlying gRPC call.
 *  - On unexpected disconnect, throw an exception inside the Flow — the
 *    framework wrapper will catch it and reconnect according to
 *    [LaserStreamConfig.reconnect].
 *  - When [LaserStreamConfig.replayFromSlot] is set, the implementation
 *    should request historical replay from that slot (LaserStream's
 *    signature feature; vanilla Yellowstone gRPC drops anything that
 *    arrived before subscription).
 */
interface LaserStreamGrpcTransport {

    /**
     * Open a single subscription attempt. The Flow should emit one
     * [LaserStreamUpdate] per protocol message.
     *
     * @throws Exception via the returned Flow on transport failure. The
     *   wrapper in [LaserStreamApi.grpcSubscribe] catches and reconnects.
     */
    fun subscribe(
        cfg: LaserStreamConfig,
        request: LaserStreamSubscriptionRequest
    ): Flow<LaserStreamUpdate>
}

/**
 * Yellowstone-compatible subscription filter. Mirrors the on-the-wire
 * `SubscribeRequest` shape but stays free of protobuf types so this module
 * can ship without the protobuf classpath.
 *
 * @property accounts Per-key account filters (key = arbitrary user label).
 * @property slots Per-key slot filters.
 * @property transactions Per-key transaction filters.
 * @property entry Whether to subscribe to entry notifications.
 * @property blocksMeta Whether to subscribe to block-meta notifications.
 * @property commitment One of `"processed"`, `"confirmed"`, `"finalized"`.
 */
data class LaserStreamSubscriptionRequest(
    val accounts: Map<String, AccountFilter> = emptyMap(),
    val slots: Map<String, SlotFilter> = emptyMap(),
    val transactions: Map<String, TransactionFilter> = emptyMap(),
    val entry: Boolean = false,
    val blocksMeta: Boolean = false,
    val commitment: String? = null
) {
    /**
     * Convenience factory matching common use-cases.
     */
    companion object {
        /** Subscribe to all transactions touching any of [addresses]. */
        fun txByAddress(
            addresses: List<String>,
            commitment: String? = "confirmed",
            includeVote: Boolean = false,
            includeFailed: Boolean = false
        ) = LaserStreamSubscriptionRequest(
            transactions = mapOf(
                "default" to TransactionFilter(
                    accountInclude = addresses,
                    vote = includeVote,
                    failed = includeFailed
                )
            ),
            commitment = commitment
        )

        /** Subscribe to account changes for any of [addresses]. */
        fun accountByAddress(
            addresses: List<String>,
            commitment: String? = "confirmed"
        ) = LaserStreamSubscriptionRequest(
            accounts = mapOf(
                "default" to AccountFilter(account = addresses)
            ),
            commitment = commitment
        )

        /** Subscribe only to slot updates (lightweight ticker). */
        fun slotsOnly(commitment: String? = "confirmed") = LaserStreamSubscriptionRequest(
            slots = mapOf("default" to SlotFilter()),
            commitment = commitment
        )
    }
}

/** Yellowstone account filter. */
data class AccountFilter(
    /** Specific accounts to watch. */
    val account: List<String> = emptyList(),
    /** Watch every account owned by these programs. */
    val owner: List<String> = emptyList(),
    /** Memcmp / dataSize filters serialized as JSON-friendly maps. */
    val filters: List<Map<String, Any>> = emptyList()
)

/** Yellowstone slot filter. */
data class SlotFilter(
    /** Whether to filter by status (`"confirmed"`/`"finalized"`). */
    val filterByCommitment: Boolean = false
)

/** Yellowstone transaction filter. */
data class TransactionFilter(
    /** Allow-list addresses; the transaction must touch at least one. */
    val accountInclude: List<String> = emptyList(),
    /** Block-list addresses; the transaction must touch none. */
    val accountExclude: List<String> = emptyList(),
    /** Required addresses; the transaction must touch all. */
    val accountRequired: List<String> = emptyList(),
    /** Pin to a specific signature (one-shot). */
    val signature: String? = null,
    /** Include vote transactions. */
    val vote: Boolean = false,
    /** Include failed transactions. */
    val failed: Boolean = false
)

/**
 * Discriminated update returned by a LaserStream subscription. Implementations
 * convert protobuf messages into one of these variants. Keeps the Flow type
 * simple while preserving the message kind.
 */
sealed class LaserStreamUpdate {
    /** Wall-clock millis the SDK received the message. Useful for latency math. */
    abstract val receivedAtEpochMs: Long

    data class Account(
        val account: String,
        val owner: String,
        val lamports: Long,
        val data: ByteArray,
        val executable: Boolean,
        val rentEpoch: Long,
        val slot: Long,
        override val receivedAtEpochMs: Long = System.currentTimeMillis()
    ) : LaserStreamUpdate() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Account) return false
            return account == other.account &&
                owner == other.owner &&
                lamports == other.lamports &&
                data.contentEquals(other.data) &&
                executable == other.executable &&
                rentEpoch == other.rentEpoch &&
                slot == other.slot
        }
        override fun hashCode(): Int {
            var r = account.hashCode()
            r = 31 * r + owner.hashCode()
            r = 31 * r + lamports.hashCode()
            r = 31 * r + data.contentHashCode()
            r = 31 * r + executable.hashCode()
            r = 31 * r + rentEpoch.hashCode()
            r = 31 * r + slot.hashCode()
            return r
        }
    }

    data class Transaction(
        val signature: String,
        val slot: Long,
        val isVote: Boolean,
        val failed: Boolean,
        /** Raw transaction bytes. Decode with your preferred Solana lib. */
        val transaction: ByteArray,
        override val receivedAtEpochMs: Long = System.currentTimeMillis()
    ) : LaserStreamUpdate() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Transaction) return false
            return signature == other.signature &&
                slot == other.slot &&
                isVote == other.isVote &&
                failed == other.failed &&
                transaction.contentEquals(other.transaction)
        }
        override fun hashCode(): Int {
            var r = signature.hashCode()
            r = 31 * r + slot.hashCode()
            r = 31 * r + isVote.hashCode()
            r = 31 * r + failed.hashCode()
            r = 31 * r + transaction.contentHashCode()
            return r
        }
    }

    data class Slot(
        val slot: Long,
        val parent: Long?,
        val status: String,
        override val receivedAtEpochMs: Long = System.currentTimeMillis()
    ) : LaserStreamUpdate()

    data class Ping(
        override val receivedAtEpochMs: Long = System.currentTimeMillis()
    ) : LaserStreamUpdate()

    /**
     * Escape hatch for protobuf message kinds not yet modelled here. Carries
     * the raw bytes so callers can decode themselves without forcing an SDK
     * upgrade for new Yellowstone message types.
     */
    data class Raw(
        val kind: String,
        val payload: ByteArray,
        override val receivedAtEpochMs: Long = System.currentTimeMillis()
    ) : LaserStreamUpdate() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Raw) return false
            return kind == other.kind && payload.contentEquals(other.payload)
        }
        override fun hashCode(): Int = 31 * kind.hashCode() + payload.contentHashCode()
    }
}
