package app.tripplanner.shared.core.util

/**
 * Fractional indexing over a base-62 alphabet (design §7): each stop's `order` is a
 * string key, and reordering is always a single-document write of a new key strictly
 * between its neighbours. Concurrent reorders can't collide on a shared "position"
 * because there is none.
 *
 * Invariants:
 *  - Keys are compared as plain strings (ASCII order: 0-9 < A-Z < a-z).
 *  - Generated keys never end in the smallest digit '0', which guarantees a key can
 *    always be generated between any two existing generated keys.
 *  - Keys grow only when items are repeatedly inserted into the same gap; a backend
 *    rebalance rewrites a day's keys when any exceed [REBALANCE_LENGTH].
 */
object FractionalIndex {

    private const val ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
    private val BASE = ALPHABET.length // 62
    const val REBALANCE_LENGTH = 50

    /**
     * Key for the first item in an empty list.
     *
     * Complexity:
     * - **Time:** O(1)
     * - **Space:** O(1) auxiliary space
     */
    fun first(): String = between(null, null)

    /**
     * A key strictly between [a] and [b] (string order).
     * `a == null` means "before everything"; `b == null` means "after everything".
     *
     * Complexity:
     * - **Time:** O(L) where L is the length of the longer key (or prefix depth needed to find a gap).
     * - **Space:** O(L) to construct and return the resulting key string.
     */
    fun between(a: String?, b: String?): String {
        require(a == null || b == null || a < b) { "between($a, $b): a must be < b" }
        val lo = a ?: ""
        val sb = StringBuilder()
        var i = 0
        var upperBounded = b != null
        while (true) {
            val da = if (i < lo.length) digit(lo[i]) else 0
            val db = if (upperBounded && i < b!!.length) digit(b[i]) else BASE
            when {
                db - da > 1 -> { // room for a midpoint digit at this position — done
                    sb.append(ALPHABET[(da + db) / 2])
                    return sb.toString()
                }
                db - da == 1 -> { // pin to da; anything longer with this prefix is < b
                    sb.append(ALPHABET[da])
                    upperBounded = false
                }
                else -> sb.append(ALPHABET[da]) // shared digit, keep scanning
            }
            i++
        }
    }

    /**
     * Evenly spaced keys for [count] items, used by seeding and backend rebalancing.
     *
     * Complexity:
     * - **Time:** O(N * L) where N is [count] and L is average key length (typically O(1) small constant).
     * - **Space:** O(N * L) to hold the list of generated keys.
     */
    fun spread(count: Int): List<String> {
        require(count >= 0)
        val keys = ArrayList<String>(count)
        var prev: String? = null
        repeat(count) {
            // Sequential generation drifts toward one end; good enough for rebalance,
            // which only needs short, ordered, valid keys.
            val next = between(prev, null)
            keys.add(next)
            prev = next
        }
        return keys
    }

    /**
     * Look up the integer index of [c] within [ALPHABET].
     *
     * Complexity:
     * - **Time:** O(|ALPHABET|) = O(62) = O(1) constant time.
     * - **Space:** O(1).
     */
    private fun digit(c: Char): Int {
        val d = ALPHABET.indexOf(c)
        require(d >= 0) { "invalid key character: $c" }
        return d
    }
}
