package app.tripplanner.shared.feature.trips

import app.tripplanner.shared.core.model.Trip
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/**
 * Pure builder for the create-trip flow (design §3.1 (2), §7): the creator becomes the
 * owner and the only member; [Trip.timeZone] drives day boundaries and Calendar export.
 * Kept free of Firebase so the schema rules can be unit-tested.
 */
object NewTrip {
    const val MAX_NAME_LENGTH = 80

    /**
     * Returns a user-facing problem, or `null` when the input is valid.
     *
     * Complexity:
     * - **Time:** O(N) where N is the name length (trim).
     * - **Space:** O(1).
     */
    fun validate(name: String, startDate: LocalDate?, endDate: LocalDate?): String? = when {
        name.isBlank() -> "Give the trip a name"
        name.trim().length > MAX_NAME_LENGTH -> "Name is too long (max $MAX_NAME_LENGTH characters)"
        startDate == null || endDate == null -> "Pick the trip dates"
        endDate < startDate -> "End date is before the start date"
        else -> null
    }

    /**
     * Builds the document to write. Callers must [validate] first.
     *
     * Complexity:
     * - **Time:** O(N) for the trimmed name copy.
     * - **Space:** O(1) - single-element member/role collections.
     */
    fun build(uid: String, name: String, startDate: LocalDate, endDate: LocalDate, timeZone: TimeZone): Trip {
        require(uid.isNotBlank()) { "uid required" }
        require(validate(name, startDate, endDate) == null) { "invalid input" }
        return Trip(
            name = name.trim(),
            startDate = startDate.toString(),
            endDate = endDate.toString(),
            timeZone = timeZone.id,
            ownerId = uid,
            memberIds = listOf(uid),
            roles = mapOf(uid to "owner"),
        )
    }
}
