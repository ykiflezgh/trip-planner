package app.tripplanner.shared.feature.stops

import app.tripplanner.shared.core.model.Stop
import app.tripplanner.shared.core.util.FractionalIndex
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StopReorderTest {
    private fun stops(n: Int) = FractionalIndex.spread(n).mapIndexed { i, k -> Stop(id = "s$i", order = k) }

    @Test
    fun moveDownAndUp() {
        val list = stops(4)
        assertEquals(listOf("s1", "s2", "s0", "s3"), StopReorder.move(list, "s0", "s2").map { it.id })
        assertEquals(listOf("s3", "s0", "s1", "s2"), StopReorder.move(list, "s3", "s0").map { it.id })
        assertEquals(list, StopReorder.move(list, "s1", "s1"))
        assertEquals(list, StopReorder.move(list, "missing", "s1"))
    }

    @Test
    fun neighboursAtEdgesAreNull() {
        val list = stops(3)
        assertEquals(null to list[1].order, StopReorder.neighbours(list, "s0"))
        assertEquals(list[0].order to list[2].order, StopReorder.neighbours(list, "s1"))
        assertEquals(list[1].order to null, StopReorder.neighbours(list, "s2"))
        assertNull(StopReorder.neighbours(listOf(list[0]), "s0").first)
    }

    /**
     * Acceptance criterion (planning): FractionalIndex invariants hold over 500 random moves.
     * Simulates what the UI does: local reorder, then the moved stop gets a key between its
     * new neighbours; the list must stay strictly ordered by key with no collisions.
     */
    @Test
    fun soak500RandomMovesKeepKeysStrictlyOrdered() {
        val rnd = Random(42)
        var list = stops(12)
        var maxLen = 0
        repeat(500) {
            val from = list[rnd.nextInt(list.size)].id
            val to = list[rnd.nextInt(list.size)].id
            list = StopReorder.move(list, from, to)
            val (after, before) = StopReorder.neighbours(list, from)
            val key = FractionalIndex.between(after, before)
            list = list.map { if (it.id == from) it.copy(order = key) else it }
            maxLen = maxOf(maxLen, key.length)

            val keys = list.map { it.order }
            assertEquals(keys.sorted(), keys, "keys must stay in list order after move $it")
            assertEquals(keys.size, keys.toSet().size, "keys must be unique after move $it")
        }
        assertTrue(maxLen < 200, "keys grew to $maxLen chars; rebalancing (design §7) is expected around ${FractionalIndex.REBALANCE_LENGTH}")
    }
}
