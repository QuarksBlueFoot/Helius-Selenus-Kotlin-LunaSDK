package xyz.selenus.luna.keys

import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * # SLIP-0010 hierarchical deterministic wallet derivation (Ed25519 variant)
 *
 * Implements [SLIP-0010](https://github.com/satoshilabs/slips/blob/master/slip-0010.md)
 * for the Ed25519 curve. Every Solana wallet — Phantom, Solflare, Backpack,
 * Helium Wallet, the Solana CLI — derives keypairs from a BIP-39 mnemonic
 * via SLIP-0010 along paths like `m/44'/501'/0'/0'`.
 *
 * Without this, our [SolanaKeypair] only does fresh-from-CSPRNG keys, which
 * means callers can't import a user's seed phrase. With this, the canonical
 * mnemonic-import flow works:
 *
 * ```kotlin
 * // 1. Convert BIP-39 mnemonic → 64-byte seed (use a BIP-39 library)
 * val seed = bip39.seedFromMnemonic("twelve word phrase ...", passphrase = "")
 *
 * // 2. Derive Solana account 0 along the canonical Phantom-compatible path
 * val (privateScalar, _) = Slip10.derivePath(seed, "m/44'/501'/0'/0'")
 *
 * // 3. Build a SolanaKeypair from the derived 32-byte seed
 * val keypair = SolanaKeypair.fromSecretSeed(privateScalar)
 * ```
 *
 * ## Path syntax
 *
 *  - Starts with `m/`
 *  - Components separated by `/`
 *  - **Hardened** components have a trailing apostrophe: `44'`. SLIP-0010 for
 *    Ed25519 ONLY allows hardened derivation — non-hardened components
 *    throw [Slip10NonHardenedException].
 *
 * ## Standard Solana paths
 *
 *  - `m/44'/501'/0'/0'` — Phantom default for account 0
 *  - `m/44'/501'/N'/0'` — Phantom account N
 *  - `m/44'/501'/0'` — older Solana CLI format (3-component)
 */
object Slip10 {

    /** SLIP-0010 master-key seed-MAC key for Ed25519, fixed by the spec. */
    private val ED25519_SEED_KEY: ByteArray = "ed25519 seed".toByteArray(Charsets.US_ASCII)

    /** Hardening offset: bit 31 of the index. */
    private const val HARDENED_OFFSET: Long = 0x80000000L

    /**
     * Derive a child key from [seed] along [path].
     *
     * @param seed BIP-39 seed bytes (typically 64 bytes from PBKDF2 of the
     *   mnemonic). SLIP-0010 accepts any seed of 16–64 bytes; we enforce
     *   that bound.
     * @param path BIP-32-style path string starting with `m`. Every component
     *   must be hardened (trailing `'`).
     * @return `(privateScalar, chainCode)` — both 32 bytes. The 32-byte
     *   private scalar is the Ed25519 seed; feed it to
     *   [SolanaKeypair.fromSecretSeed] to derive the public key and a usable
     *   keypair.
     */
    fun derivePath(seed: ByteArray, path: String): Slip10Key {
        require(seed.size in 16..64) {
            "SLIP-0010 seed must be 16-64 bytes (got ${seed.size})"
        }
        val components = parsePath(path)

        // 1. Master key from HMAC-SHA512(key="ed25519 seed", data=seed)
        var (privKey, chainCode) = hmacSha512Split(ED25519_SEED_KEY, seed)

        // 2. CKDpriv for each component
        for (index in components) {
            val (childPriv, childChain) = ckdPriv(privKey, chainCode, index)
            privKey = childPriv
            chainCode = childChain
        }

        return Slip10Key(privateScalar = privKey, chainCode = chainCode)
    }

    /**
     * Convenience: derive a Solana keypair from [seed] along [path].
     * Returns a fully-resolved [SolanaKeypair] (public key derived via
     * [Ed25519Derive]).
     */
    fun deriveKeypair(seed: ByteArray, path: String): SolanaKeypair {
        val key = derivePath(seed, path)
        return SolanaKeypair.fromSecretSeed(key.privateScalar)
    }

    /**
     * Derive Phantom-compatible account [index] along
     * `m/44'/501'/index'/0'` — the most common Solana path.
     */
    fun derivePhantomAccount(seed: ByteArray, index: Long): SolanaKeypair {
        require(index >= 0) { "account index must be non-negative" }
        return deriveKeypair(seed, "m/44'/501'/$index'/0'")
    }

    // ── Path parsing ──────────────────────────────────────────────────

    private fun parsePath(path: String): List<Long> {
        require(path.startsWith("m") || path.startsWith("M")) {
            "SLIP-0010 path must start with 'm/' (got: '$path')"
        }
        if (path == "m" || path == "M") return emptyList()

        val parts = path.removePrefix("m").removePrefix("M").removePrefix("/").split("/")
        return parts.map { component ->
            require(component.endsWith("'")) {
                throw Slip10NonHardenedException(
                    "SLIP-0010 for Ed25519 only allows hardened derivation. " +
                        "Component '$component' in path '$path' is not hardened. " +
                        "Append an apostrophe (e.g. \"$component'\")."
                )
            }
            val raw = component.removeSuffix("'").toLongOrNull()
                ?: throw IllegalArgumentException("Path component '$component' is not a number")
            require(raw in 0L..0x7FFFFFFFL) {
                "Path component $raw out of range (must be 0..2^31-1)"
            }
            raw or HARDENED_OFFSET
        }
    }

    // ── CKDpriv (SLIP-0010 §1.4 for Ed25519) ──────────────────────────

    /**
     * SLIP-0010 child-key derivation (private parent → private child).
     * Per the spec: for Ed25519 only the hardened branch is defined.
     *
     * Data: `0x00 || ser256(parentPriv) || ser32(index)`
     * Output: HMAC-SHA512(key=parentChainCode, data=Data) → child priv || child chain code
     */
    private fun ckdPriv(parentPriv: ByteArray, parentChain: ByteArray, index: Long): Pair<ByteArray, ByteArray> {
        require(parentPriv.size == 32 && parentChain.size == 32) {
            "parent priv/chain must be 32 bytes each"
        }
        // SLIP-0010 §1.4 only allows hardened derivation for Ed25519.
        require((index and HARDENED_OFFSET) != 0L) {
            "Ed25519 SLIP-0010 only supports hardened derivation"
        }

        val data = ByteArray(1 + 32 + 4)
        data[0] = 0x00 // hardened marker
        System.arraycopy(parentPriv, 0, data, 1, 32)
        // ser32(index): index as 4 bytes BIG-endian
        ByteBuffer.wrap(data, 33, 4).order(ByteOrder.BIG_ENDIAN).putInt(index.toInt())

        return hmacSha512Split(parentChain, data)
    }

    private fun hmacSha512Split(key: ByteArray, data: ByteArray): Pair<ByteArray, ByteArray> {
        val mac = Mac.getInstance("HmacSHA512").apply { init(SecretKeySpec(key, "HmacSHA512")) }
        val full = mac.doFinal(data) // 64 bytes
        return full.copyOfRange(0, 32) to full.copyOfRange(32, 64)
    }
}

/**
 * Derived key from [Slip10.derivePath].
 *
 * @property privateScalar The 32-byte Ed25519 seed (NOT the SHA-512-clamped
 *   scalar — that derivation happens inside [Ed25519Derive] and the JDK
 *   Signature provider). Feed this directly to [SolanaKeypair.fromSecretSeed].
 * @property chainCode The 32-byte chain code. Only relevant if you intend
 *   to derive further child keys via additional [Slip10.derivePath] calls.
 */
data class Slip10Key(
    val privateScalar: ByteArray,
    val chainCode: ByteArray
) {
    init {
        require(privateScalar.size == 32) { "privateScalar must be 32 bytes" }
        require(chainCode.size == 32) { "chainCode must be 32 bytes" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Slip10Key) return false
        return privateScalar.contentEquals(other.privateScalar) &&
            chainCode.contentEquals(other.chainCode)
    }

    override fun hashCode(): Int =
        31 * privateScalar.contentHashCode() + chainCode.contentHashCode()
}

/** Thrown when a SLIP-0010 path component is not hardened. */
class Slip10NonHardenedException(message: String) : IllegalArgumentException(message)
