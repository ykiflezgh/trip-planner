package app.tripplanner.shared.feature.notifications

import app.tripplanner.shared.core.model.ActivityEvent

/** Data-message keys the Function sends (firebase/functions/src/notifications.ts). */
object PushKeys {
    const val KIND = "kind"            // "single" | "digest"
    const val TRIP_ID = "tripId"
    const val TRIP_NAME = "tripName"
    const val ACTOR_ID = "actorId"
    const val ACTOR_NAME = "actorName"
    const val TYPE = "type"
    const val STOP_ID = "stopId"
    const val STOP_NAME = "stopName"
    const val DAY = "day"
    const val FROM_DAY = "fromDay"
    const val COUNT = "count"
}

/** Parsed FCM data payload; one tray entry per (trip, actor) so a digest replaces the singles (design §10). */
data class PushPayload(
    val kind: String,
    val tripId: String,
    val tripName: String,
    val actorId: String,
    val actorName: String,
    val type: String,
    val stopId: String?,
    val stopName: String,
    val day: Int,
    val fromDay: Int?,
    val count: Int,
) {
    val isDigest: Boolean get() = kind == "digest"

    /**
     * Stable per (trip, actor): the burst digest overwrites the earlier single notifications.
     *
     * Complexity:
     * - **Time:** O(L) hash of the L-character key.
     * - **Space:** O(1).
     */
    val notificationId: Int get() = "$tripId:$actorId".hashCode()

    companion object {
        /**
         * Complexity:
         * - **Time:** O(1) map lookups.
         * - **Space:** O(1).
         */
        fun parse(data: Map<String, String>): PushPayload? {
            val tripId = data[PushKeys.TRIP_ID]?.takeIf { it.isNotBlank() } ?: return null
            return PushPayload(
                kind = data[PushKeys.KIND] ?: "single",
                tripId = tripId,
                tripName = data[PushKeys.TRIP_NAME].orEmpty(),
                actorId = data[PushKeys.ACTOR_ID].orEmpty(),
                actorName = data[PushKeys.ACTOR_NAME].orEmpty(),
                type = data[PushKeys.TYPE].orEmpty(),
                stopId = data[PushKeys.STOP_ID]?.takeIf { it.isNotBlank() },
                stopName = data[PushKeys.STOP_NAME].orEmpty(),
                day = data[PushKeys.DAY]?.toIntOrNull() ?: 0,
                fromDay = data[PushKeys.FROM_DAY]?.toIntOrNull(),
                count = data[PushKeys.COUNT]?.toIntOrNull() ?: 1,
            )
        }
    }
}

/**
 * Client-side wording for feed rows and Android notifications (design §10: data messages, the
 * client localizes). iOS receives the same facts as `loc-key`/`loc-args` and formats them from
 * Localizable.strings; keep the two in step.
 */
object NotificationText {
    /**
     * One sentence for an activity event, e.g. "Ana added Louvre to Day 2".
     *
     * Complexity:
     * - **Time:** O(L) string building for the L-character result.
     * - **Space:** O(L).
     */
    fun describe(type: String, actorName: String, stopName: String, day: Int, fromDay: Int?): String {
        val who = actorName.ifBlank { "Someone" }
        val what = stopName.ifBlank { "a stop" }
        return when (type) {
            "stop_added" -> "$who added $what to Day ${day + 1}"
            "stop_removed" -> "$who removed $what from Day ${day + 1}"
            "stop_moved" -> if (fromDay != null && fromDay != day) "$who moved $what from Day ${fromDay + 1} to Day ${day + 1}" else "$who reordered $what on Day ${day + 1}"
            "stop_edited" -> "$who edited $what"
            "member_joined" -> "$who joined the trip"
            else -> "$who changed the plan"
        }
    }

    /**
     * Complexity:
     * - **Time:** O(L).
     * - **Space:** O(L).
     */
    fun describe(event: ActivityEvent): String = describe(event.type, event.actorName, event.stopName, event.day, event.fromDay)

    /**
     * Notification title/body. Digest: "Ana made 4 changes to Paris".
     *
     * Complexity:
     * - **Time:** O(L).
     * - **Space:** O(L).
     */
    fun notification(p: PushPayload): Pair<String, String> {
        val title = p.tripName.ifBlank { "Trip Planner" }
        val who = p.actorName.ifBlank { "Someone" }
        val body = if (p.isDigest) {
            "$who made ${p.count} changes" + (if (p.stopName.isNotBlank()) " · latest: ${describe(p.type, "", p.stopName, p.day, p.fromDay).removePrefix("Someone ")}" else "")
        } else {
            describe(p.type, p.actorName, p.stopName, p.day, p.fromDay)
        }
        return title to body
    }
}
