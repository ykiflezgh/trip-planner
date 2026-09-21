package app.tripplanner.shared.core.model

import kotlinx.serialization.Serializable

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
    val roles: Map<String, String> = emptyMap(),
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
)

@Serializable
data class TravelLeg(
    val fromStopId: String,
    val toStopId: String,
    val mode: TravelMode,
    val seconds: Int,
    val meters: Int,
)

@Serializable
data class ActivityEvent(
    val id: String = "",
    val type: String = "",                // stop_added | stop_moved | stop_removed | member_joined ...
    val actorId: String = "",
    val stopId: String? = null,
    val summary: String = "",
)
