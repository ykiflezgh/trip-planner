package app.tripplanner.shared.core.model

import dev.gitlive.firebase.firestore.BaseTimestamp
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

enum class Role { OWNER, EDITOR, VIEWER }
enum class TravelMode { DRIVE, WALK } // TRANSIT deferred; transit_mode_enabled ships off (design SS18)

/** `trips/{id}/days/{day}`: per-day hours override (design v1.1 §7); absent = trip defaults. */
@Serializable
data class DayHoursDoc(
    val day: Int = 0,
    val start: String = "",              // "HH:mm" in the trip zone
    val end: String = "",
    val title: String = "",
    val updatedBy: String = "",
    val updatedAt: BaseTimestamp? = null,
)

@Serializable
data class Trip(
    val id: String = "",
    val name: String = "",
    val startDate: String = "",           // ISO-8601 date
    val endDate: String = "",
    val timeZone: String = "UTC",         // IANA id, drives day boundaries and the calendar
    /** Trip-wide day hours and travel mode (design v1.1 §7); `days/{day}` may override the hours. */
    val defaultDayStart: String = "09:00",
    val defaultDayEnd: String = "21:00",
    val defaultTravelMode: String = "driving", // "driving" | "walking"
    val ownerId: String = "",
    val memberIds: List<String> = emptyList(),
    val roles: Map<String, String> = emptyMap(), // uid -> "owner" | "editor" | "viewer" (lower-case, rules compare strings)
    val createdAt: BaseTimestamp? = null,         // server timestamps (§7); null in a local snapshot before the server ack
    val updatedAt: BaseTimestamp? = null,
    /** Snapshot metadata, not a field: true while a local write awaits the server (design §9). */
    @Transient val pendingSync: Boolean = false,
)

@Serializable
data class Stop(
    val id: String = "",
    /** "place" (from Places) or "custom" (e.g. "Flight to Rome"), design v1.1 §7. */
    val kind: String = "place",
    val placeId: String = "",
    val name: String = "",
    /** Coordinates only for place stops; custom entries have none and never appear on the map. */
    val lat: Double? = null,
    val lng: Double? = null,
    val address: String = "",
    val day: Int = 0,                     // 0-based day index within the trip
    val order: String = "",               // fractional index key (core/util/FractionalIndex)
    val durationMin: Int = 60,
    /** Pinned wall-clock start "HH:mm" in the trip zone; `null` = flows from the previous entry. */
    val fixedStart: String? = null,
    /** Travel mode to the next place stop ("driving" | "walking"); `null` = trip default. */
    val modeToNext: String? = null,
    val notes: String = "",
    val addedBy: String = "",
    /** Last editor (move/edit); the Function's auth context is the primary actor source, this is the fallback (design §10). */
    val updatedBy: String = "",
    val addedAt: BaseTimestamp? = null,        // server timestamps (§7)
    val updatedAt: BaseTimestamp? = null,
    /** When name/address/coords were fetched from Places: only placeId + coordinates are durable, the rest is a cache to refresh within Google's limits (§7). */
    val placeFetchedAt: BaseTimestamp? = null,
    /** Snapshot metadata, not a field: true while a local write awaits the server (design §9). */
    @Transient val pendingSync: Boolean = false,
) {
    /** True for a stop with a location to draw and route through (design v1.1 §7: place stops only). */
    val hasPlace: Boolean get() = kind != KIND_CUSTOM && lat != null && lng != null

    companion object {
        const val KIND_PLACE = "place"
        const val KIND_CUSTOM = "custom"
    }
}

/**
 * `trips/{tripId}/travel/{from}_{to}_{mode}`, written only by Functions (design §7, §8.3).
 * Either [seconds]/[meters] are set or [error] explains why Routes had no answer.
 */
@Serializable
data class TravelLeg(
    val fromStopId: String = "",
    val toStopId: String = "",
    val mode: TravelMode = TravelMode.DRIVE,
    val seconds: Int? = null,
    val meters: Int? = null,
    val error: String? = null,
    val computedAt: Long? = null,      // epoch ms
) {
    val ok: Boolean get() = error == null && seconds != null
}

/**
 * One feed entry, written only by Functions (design §7, §10). Structured fields let the client
 * render localized text; [summary] is the server's English fallback.
 */
@Serializable
data class ActivityEvent(
    val id: String = "",
    val type: String = "",                // stop_added | stop_moved | stop_edited | stop_removed | member_joined
    val actorId: String = "",
    val actorName: String = "",
    val stopId: String? = null,
    val stopName: String = "",
    val day: Int = 0,                     // 0-based; for stop_moved the destination day
    val fromDay: Int? = null,             // stop_moved only
    val fixedStart: String? = null,       // entry_pinned only ("HH:mm")
    val summary: String = "",
    val createdAt: BaseTimestamp? = null,
)

/** `users/{uid}.notificationPrefs` (design §7, §10). Absent fields mean "on". */
@Serializable
data class NotificationPrefs(
    val push: Boolean = true,
    val mutedTripIds: List<String> = emptyList(),
)

/** `users/{uid}` — profile the Functions denormalize into events, FCM tokens, prefs (design §7). */
@Serializable
data class UserProfile(
    val displayName: String = "",
    val photoUrl: String = "",
    val fcmTokens: List<String> = emptyList(),
    val notificationPrefs: NotificationPrefs = NotificationPrefs(),
)
