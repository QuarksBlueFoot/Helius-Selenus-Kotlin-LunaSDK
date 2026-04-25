package xyz.selenus.luna.crypto

import java.security.SecureRandom

/**
 * # Process-wide cryptographically-secure RNG
 *
 * Single shared [SecureRandom] instance + ergonomic helpers that mirror the
 * `kotlin.random.Random` surface so call sites flip from
 * `(1..10).random()` to `(1..10).secureRandom()` (or `SecureRng.intInRange`)
 * with minimal churn.
 *
 * ## Why a single shared instance?
 *
 * Constructing a [SecureRandom] is *expensive*: it has to seed itself from
 * the OS CSPRNG (`/dev/urandom` on Unix, `CryptGenRandom` on Windows). The
 * lazy seeding lock then becomes a hot point under contention. Sharing one
 * thread-safe instance across the whole process is the recommended JDK
 * pattern (see `java.util.UUID.randomUUID` for the canonical example).
 *
 * ## Why not `kotlin.random.Random.Default`?
 *
 * `Random.Default` is a `XorWipeRandom` — fast, but **predictable**: an
 * adversary that observes a few outputs can reconstruct future outputs in
 * under a millisecond on commodity hardware. For:
 *  - Stealth-address entropy
 *  - Endpoint rotation in privacy flows
 *  - Decoy transaction timing/amounts
 *  - Anything an adversary might try to correlate or front-run
 * predictability is fatal. Use [SecureRng] for those sites.
 *
 * `kotlin.random.Random.Default` remains correct for non-adversarial
 * randomness (UI shuffles, sample data, jitter on metric collection).
 */
object SecureRng {

    /**
     * The shared backing CSPRNG. Intentionally exposed (instead of hidden
     * behind helpers) for advanced users that need direct access — e.g. to
     * pass to a third-party library that takes a [SecureRandom].
     */
    val instance: SecureRandom = SecureRandom()

    /** Fill [size] bytes from the CSPRNG. */
    fun nextBytes(size: Int): ByteArray {
        require(size >= 0) { "size must be non-negative" }
        val bytes = ByteArray(size)
        if (size > 0) instance.nextBytes(bytes)
        return bytes
    }

    /** Random int in `[0, boundExclusive)`. */
    fun nextInt(boundExclusive: Int): Int {
        require(boundExclusive > 0) { "boundExclusive must be positive" }
        return instance.nextInt(boundExclusive)
    }

    /** Random int in `[origin, boundExclusive)` — matches JDK semantics. */
    fun nextInt(origin: Int, boundExclusive: Int): Int {
        require(origin < boundExclusive) { "origin must be < boundExclusive" }
        return instance.nextInt(origin, boundExclusive)
    }

    /**
     * Random long in `[origin, boundExclusive)`. Implemented via two
     * `nextInt` calls because [SecureRandom.nextLong] is not bound-aware on
     * older JDKs.
     */
    fun nextLong(origin: Long, boundExclusive: Long): Long {
        require(origin < boundExclusive) { "origin must be < boundExclusive" }
        val span = boundExclusive - origin
        // Rejection sampling on a uniform u63 to avoid modulo bias.
        var bits: Long
        var sample: Long
        do {
            bits = instance.nextLong() ushr 1     // ensure non-negative
            sample = bits % span
        } while (bits - sample + (span - 1) < 0)
        return origin + sample
    }

    /** Random non-negative double in `[0.0, 1.0)`. */
    fun nextDouble(): Double = instance.nextDouble()

    /** Random boolean. */
    fun nextBoolean(): Boolean = instance.nextBoolean()
}

// ── Range / collection extensions matching the kotlin.random.Random shape ──

/**
 * Cryptographically-secure analogue of `IntRange.random()`. Inclusive of
 * both endpoints — same semantics as the kotlin stdlib version it replaces.
 */
fun IntRange.secureRandom(): Int {
    require(!isEmpty()) { "IntRange must not be empty" }
    // Compute the result via Long math to avoid overflow when last == Int.MAX_VALUE.
    val span = last.toLong() - first.toLong() + 1L
    val offset = SecureRng.nextLong(0L, span)
    return (first.toLong() + offset).toInt()
}

/** Cryptographically-secure analogue of `LongRange.random()`, inclusive both ends. */
fun LongRange.secureRandom(): Long {
    require(!isEmpty()) { "LongRange must not be empty" }
    val end = if (last == Long.MAX_VALUE) Long.MAX_VALUE else last + 1L
    return SecureRng.nextLong(first, end)
}

/** Cryptographically-secure analogue of `List<T>.random()`. */
fun <T> List<T>.secureRandom(): T {
    require(isNotEmpty()) { "List must not be empty" }
    return this[SecureRng.nextInt(size)]
}

/** Cryptographically-secure analogue of `String.random()` (per character). */
fun CharSequence.secureRandom(): Char {
    require(isNotEmpty()) { "CharSequence must not be empty" }
    return this[SecureRng.nextInt(length)]
}

/** Cryptographically-secure analogue of `Array<T>.random()`. */
fun <T> Array<T>.secureRandom(): T {
    require(isNotEmpty()) { "Array must not be empty" }
    return this[SecureRng.nextInt(size)]
}
