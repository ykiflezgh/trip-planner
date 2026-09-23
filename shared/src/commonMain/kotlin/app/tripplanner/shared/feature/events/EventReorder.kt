package app.tripplanner.shared.feature.events

import app.tripplanner.shared.core.model.Event

/**
 * Pure helpers for drag-and-drop reorder (design §3.1 (4), §7 fractional indexing). The UI
 * reorders a local copy while dragging, then commits one write with the neighbours' keys.
 */
object EventReorder {
    /**
     * Moves the event [fromId] to the position currently held by [toId].
     *
     * Complexity:
     * - **Time:** O(S) for the S events of the day.
     * - **Space:** O(S) for the new list.
     */
    fun move(events: List<Event>, fromId: String, toId: String): List<Event> {
        val from = events.indexOfFirst { it.id == fromId }
        val to = events.indexOfFirst { it.id == toId }
        if (from < 0 || to < 0 || from == to) return events
        return events.toMutableList().apply { add(to, removeAt(from)) }
    }

    /**
     * Order keys of the events around [id] in [events]: `(after, before)` for
     * [app.tripplanner.shared.data.TripRepository.moveEvent].
     *
     * Complexity:
     * - **Time:** O(S).
     * - **Space:** O(1).
     */
    fun neighbours(events: List<Event>, id: String): Pair<String?, String?> {
        val i = events.indexOfFirst { it.id == id }
        require(i >= 0) { "event $id not in list" }
        return events.getOrNull(i - 1)?.order to events.getOrNull(i + 1)?.order
    }
}
