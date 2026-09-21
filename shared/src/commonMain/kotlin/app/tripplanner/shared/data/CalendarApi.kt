package app.tripplanner.shared.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

/**
 * Google Calendar export (design SS8.4): the caller passes a fresh OAuth access token
 * from GoogleAuthorizer (scope calendar.events) — tokens are never stored.
 * Events are created in the trip's IANA timeZone. Re-export should PATCH by the
 * eventId recorded on the stop instead of inserting again (TODO in use case).
 */
class CalendarApi(private val http: HttpClient) {

    @Serializable data class EventDateTime(val dateTime: String, val timeZone: String)
    @Serializable data class Event(
        val summary: String,
        val location: String,
        val description: String,
        val start: EventDateTime,
        val end: EventDateTime,
    )
    @Serializable data class CreatedEvent(val id: String = "")

    /**
     * Inserts an event into the user's primary Google Calendar.
     *
     * Complexity:
     * - **Time:** O(E) where E is the serialized event payload size (network round-trip + JSON serialize/deserialize).
     * - **Space:** O(E) auxiliary space for the HTTP request and response payload buffers.
     */
    suspend fun insert(accessToken: String, event: Event): CreatedEvent =
        http.post("https://www.googleapis.com/calendar/v3/calendars/primary/events") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $accessToken")
            setBody(event)
        }.body()
}
