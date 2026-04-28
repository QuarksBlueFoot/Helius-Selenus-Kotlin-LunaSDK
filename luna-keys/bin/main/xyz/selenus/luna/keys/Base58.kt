package xyz.selenus.luna.keys

/**
 * Solana / Bitcoin base58 codec.
 *
 * The exact alphabet is `123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz`
 * (no `0`, `O`, `I`, `l` — the four chars omitted to avoid visual ambiguity).
 *
 * Designed for Solana keys (32-byte pubkeys → 43–44 chars) and signatures
 * (64-byte → 87–88 chars). Constant-factor performance is fine for typical
 * wallet/RPC workloads. For high-throughput indexing, prefer a SIMD-optimized
 * native library.
 */
object Base58 {
    private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"

    private val INDEXES = IntArray(128) { -1 }.also {
        for ((i, c) in ALPHABET.withIndex()) it[c.code] = i
    }

    /** Encode arbitrary bytes to base58. Returns "" for empty input. */
    fun encode(input: ByteArray): String {
        if (input.isEmpty()) return ""
        var leadingZeros = 0
        while (leadingZeros < input.size && input[leadingZeros].toInt() == 0) leadingZeros++

        val copy = input.copyOf()
        val encoded = CharArray(input.size * 2)
        var outputStart = encoded.size

        var inputStart = leadingZeros
        while (inputStart < copy.size) {
            encoded[--outputStart] = ALPHABET[divmod(copy, inputStart, 256, 58).toInt()]
            if (copy[inputStart].toInt() == 0) inputStart++
        }
        repeat(leadingZeros) { encoded[--outputStart] = ALPHABET[0] }
        return String(encoded, outputStart, encoded.size - outputStart)
    }

    /**
     * Decode base58 string to bytes.
     * @throws IllegalArgumentException if [input] contains a non-base58 character.
     */
    fun decode(input: String): ByteArray {
        if (input.isEmpty()) return ByteArray(0)
        val input58 = ByteArray(input.length)
        for ((i, c) in input.withIndex()) {
            val digit = if (c.code < 128) INDEXES[c.code] else -1
            require(digit >= 0) { "Invalid base58 character '$c' at index $i" }
            input58[i] = digit.toByte()
        }
        var leadingZeros = 0
        while (leadingZeros < input58.size && input58[leadingZeros].toInt() == 0) leadingZeros++

        val decoded = ByteArray(input.length)
        var outputStart = decoded.size

        var inputStart = leadingZeros
        while (inputStart < input58.size) {
            decoded[--outputStart] = divmod(input58, inputStart, 58, 256)
            if (input58[inputStart].toInt() == 0) inputStart++
        }
        while (outputStart < decoded.size && decoded[outputStart].toInt() == 0) outputStart++
        return decoded.copyOfRange(outputStart - leadingZeros, decoded.size)
    }

    /** Validate without throwing. Useful for `if (Base58.isValid(s)) ...` flow. */
    fun isValid(input: String): Boolean = try {
        decode(input); true
    } catch (_: IllegalArgumentException) {
        false
    }

    private fun divmod(number: ByteArray, firstDigit: Int, base: Int, divisor: Int): Byte {
        var remainder = 0
        for (i in firstDigit until number.size) {
            val digit = number[i].toInt() and 0xFF
            val temp = remainder * base + digit
            number[i] = (temp / divisor).toByte()
            remainder = temp % divisor
        }
        return remainder.toByte()
    }
}
