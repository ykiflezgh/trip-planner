package app.tripplanner.shared.feature.stops

import app.tripplanner.shared.core.model.Stop

/**
 * Pure helpers for drag-and-drop reorder (design §3.1 (4), §7 fractional indexing). The UI
 * reorders a local copy while dragging, then commits one write with the neighbours' keys.
 */
object StopReorder {
    /**
     * Moves the stop [fromId] to the position currently held by [toId].
     *
     * Complexity:
     * - **Time:** O(S) for the S stops of the day.
     * - **Space:** O(S) for the new list.
     */
    fun move(stops: List<Stop>, fromId: String, toId: String): List<Stop> {
        val from = stops.indexOfFirst { it.id == fromId }
        val to = stops.indexOfFirst { it.id == toId }
        if (from < 0 || to < 0 || from == to) return stops
        return stops.toMutableList().apply { add(to, removeAt(from)) }
    }

    /**
     * Order keys of the stops around [id] in [stops]: `(after, before)` for
     * [app.tripplanner.shared.data.TripRepository.moveStop].
     *
     * Complexity:
     * - **Time:** O(S).
     * - **Space:** O(1).
     */
    fun neighbours(stops: List<Stop>, id: String): Pair<String?, String?> {
        val i = stops.indexOfFirst { it.id == id }
        require(i >= 0) { "stop $id not in list" }
        return stops.getOrNull(i - 1)?.order to stops.getOrNull(i + 1)?.order
    }
}
