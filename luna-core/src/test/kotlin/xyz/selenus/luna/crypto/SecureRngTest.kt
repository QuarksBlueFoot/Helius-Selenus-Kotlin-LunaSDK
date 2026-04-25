package xyz.selenus.luna.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SecureRngTest {

    @Test fun `nextBytes returns requested length`() {
        assertEquals(0, SecureRng.nextBytes(0).size)
        assertEquals(32, SecureRng.nextBytes(32).size)
        assertEquals(1024, SecureRng.nextBytes(1024).size)
    }

    @Test fun `nextBytes negative throws`() {
        assertFailsWith<IllegalArgumentException> { SecureRng.nextBytes(-1) }
    }

    @Test fun `nextBytes is non-trivially random`() {
        // Two consecutive 32-byte draws should differ. Probability of
        // collision is ~2^-256, so a single comparison is enough.
        val a = SecureRng.nextBytes(32)
        val b = SecureRng.nextBytes(32)
        assertTrue(!a.contentEquals(b), "two SecureRng draws collided — vanishingly improbable")
    }

    @Test fun `nextInt boundExclusive must be positive`() {
        assertFailsWith<IllegalArgumentException> { SecureRng.nextInt(0) }
        assertFailsWith<IllegalArgumentException> { SecureRng.nextInt(-1) }
    }

    @Test fun `nextInt stays within bound`() {
        repeat(1000) {
            val v = SecureRng.nextInt(10)
            assertTrue(v in 0..9, "got $v, want 0..9")
        }
    }

    @Test fun `nextInt origin range stays within bounds`() {
        repeat(1000) {
            val v = SecureRng.nextInt(5, 8)
            assertTrue(v in 5..7, "got $v, want 5..7")
        }
    }

    @Test fun `nextLong stays within bounds and avoids modulo bias`() {
        repeat(1000) {
            val v = SecureRng.nextLong(100L, 200L)
            assertTrue(v in 100L..199L, "got $v, want 100..199")
        }
    }

    @Test fun `IntRange secureRandom inclusive both ends`() {
        repeat(1000) {
            val v = (1..10).secureRandom()
            assertTrue(v in 1..10, "got $v, want 1..10")
        }
    }

    @Test fun `IntRange secureRandom of single-element range returns that element`() {
        repeat(50) { assertEquals(7, (7..7).secureRandom()) }
    }

    @Test fun `IntRange secureRandom empty range throws`() {
        assertFailsWith<IllegalArgumentException> { (10..5).secureRandom() }
    }

    @Test fun `LongRange secureRandom inclusive both ends`() {
        repeat(500) {
            val v = (100L..200L).secureRandom()
            assertTrue(v in 100L..200L, "got $v")
        }
    }

    @Test fun `List secureRandom picks an element`() {
        val list = listOf("a", "b", "c", "d")
        val seen = mutableSetOf<String>()
        repeat(200) { seen += list.secureRandom() }
        // With 200 draws from 4 items, every element should appear with overwhelming probability.
        assertEquals(4, seen.size, "expected all 4 list elements to be picked at least once")
    }

    @Test fun `List secureRandom empty throws`() {
        assertFailsWith<IllegalArgumentException> { emptyList<Int>().secureRandom() }
    }

    @Test fun `CharSequence secureRandom returns a contained char`() {
        val s = "hex0123456789abcdef"
        repeat(200) {
            val c = s.secureRandom()
            assertTrue(c in s, "char $c not in $s")
        }
    }

    @Test fun `nextDouble stays in unit interval`() {
        repeat(1000) {
            val v = SecureRng.nextDouble()
            assertTrue(v >= 0.0 && v < 1.0, "got $v")
        }
    }
}
