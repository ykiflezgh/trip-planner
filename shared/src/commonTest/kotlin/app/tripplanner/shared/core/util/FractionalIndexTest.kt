package app.tripplanner.shared.core.util

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FractionalIndexTest {

    @Test fun firstKeyIsStable() {
        val k = FractionalIndex.first()
        assertTrue(k.isNotEmpty() && !k.endsWith("0"))
    }

    @Test fun betweenIsStrictlyOrdered() {
        val cases = listOf(
            null to null, null to "1", "1" to null, "1" to "2",
            "V" to "W", "1" to "11", "0V" to "0W", "z" to null, null to "0V",
        )
        for ((a, b) in cases) {
            val k = FractionalIndex.between(a, b)
            if (a != null) assertTrue(a < k, "expected $a < $k")
            if (b != null) assertTrue(k < b, "expected $k < $b")
            assertTrue(!k.endsWith("0"), "key must not end in '0': $k")
        }
    }

    @Test fun rejectsInvertedBounds() {
        assertFailsWith<IllegalArgumentException> { FractionalIndex.between("b", "a") }
    }

    @Test fun randomizedInsertionsStaySorted() {
        val rnd = Random(42)
        val keys = mutableListOf(FractionalIndex.first())
        repeat(500) {
            val i = rnd.nextInt(keys.size + 1)
            val a = if (i == 0) null else keys[i - 1]
            val b = if (i == keys.size) null else keys[i]
            keys.add(i, FractionalIndex.between(a, b))
        }
        assertTrue(keys == keys.sorted(), "keys drifted out of order")
        assertTrue(keys.toSet().size == keys.size, "duplicate keys generated")
    }

    @Test fun spreadIsOrdered() {
        val ks = FractionalIndex.spread(10)
        assertTrue(ks == ks.sorted() && ks.toSet().size == 10)
    }
}
