package app.tripplanner.shared.core.model

import dev.gitlive.firebase.firestore.BaseTimestamp
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

enum class Role { OWNER, EDITOR, VIEWER }
enum class TravelMode { DRIVE, WALK } // TRANSIT deferred; transit_mode_enabled ships off (design SS18)

@Serializable
data class Trip(
    val id: String = "",
    val name: String = "",
    val startDate: String = "",           // ISO-8601 date
    val endDate: String = "",
    val timeZone: String = "UTC",         // IANA id, drives day boundaries + Calendar export
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
    val placeId: String = "",
    val name: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val address: String = "",
    val day: Int = 0,                     // 0-based day index within the trip
    val order: String = "",               // fractional index key (core/util/FractionalIndex)
    val durationMin: Int = 60,
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
)

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
