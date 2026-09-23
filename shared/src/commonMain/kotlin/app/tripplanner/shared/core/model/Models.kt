package app.tripplanner.shared.core.model

import dev.gitlive.firebase.firestore.BaseTimestamp
import dev.gitlive.firebase.firestore.GeoPoint
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

enum class Role { OWNER, EDITOR, VIEWER }
enum class TravelMode { DRIVE, WALK } // TRANSIT deferred; transit_mode_enabled ships off (design SS18)

/** `trips/{id}/days/{day}`: per-day hours override (design v1.2 §7); absent = trip defaults. */
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
    /** Trip-wide day hours and travel mode (design v1.2 §7); `days/{day}` may override the hours. */
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

/**
 * The place an event contains (design v1.2 §7, decision 7): embedded in its event because a stop
 * never exists outside exactly one event. Only [placeId] and [location] are durable; the rest is
 * Places content refreshed within Google's caching window ([fetchedAt]).
 */
@Serializable
data class StopPlace(
    val placeId: String = "",
    val name: String = "",
    val location: GeoPoint = GeoPoint(0.0, 0.0),
    val address: String = "",
    val fetchedAt: BaseTimestamp? = null,
)

/**
 * `trips/{tripId}/events/{eventId}` (design v1.2 §7): the unit of scheduling. An event *may*
 * contain a [stop]; one without is a plain entry ("Flight to Rome", "Free time") that schedules
 * and notifies like any other but is absent from the map and from travel adjacency.
 */
@Serializable
data class Event(
    val id: String = "",
    val title: String = "",
    val day: Int = 0,                     // 0-based day index within the trip
    val order: String = "",               // fractional index key (core/util/FractionalIndex)
    val durationMin: Int = 60,
    /** Pinned wall-clock start "HH:mm" in the trip zone; `null` = flows from the previous event. */
    val fixedStart: String? = null,
    /** Travel mode to the next event with a stop ("driving" | "walking"); `null` = trip default. */
    val modeToNext: String? = null,
    val stop: StopPlace? = null,
    val notes: String = "",
    val category: String = "",
    val addedBy: String = "",
    /** Last editor; the Function's auth context is the primary actor source, this is the fallback (design §10). */
    val updatedBy: String = "",
    val addedAt: BaseTimestamp? = null,        // server timestamps (§7)
    val updatedAt: BaseTimestamp? = null,
    /** Snapshot metadata, not a field: true while a local write awaits the server (design §9). */
    @Transient val pendingSync: Boolean = false,
) {
    /** True when this event contains a place to draw and route through. */
    val hasStop: Boolean get() = stop != null
}

@Serializable
data class TravelLeg(
    val fromEventId: String = "",
    val toEventId: String = "",
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
 * render localized text; [summary] is the server's English fallback. [title] is the event's title.
 */
@Serializable
data class ActivityEvent(
    val id: String = "",
    val type: String = "",                // event_added | event_moved | event_edited | event_removed | event_pinned | event_unpinned | event_resized | member_joined
    val actorId: String = "",
    val actorName: String = "",
    val eventId: String? = null,
    val title: String = "",
    /** Pre-v1.2 documents named the event's title `stopName`; read for old history only. */
    val stopName: String = "",
    val day: Int = 0,                     // 0-based; for event_moved the destination day
    val fromDay: Int? = null,             // event_moved only
    val fixedStart: String? = null,       // event_pinned only ("HH:mm")
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
