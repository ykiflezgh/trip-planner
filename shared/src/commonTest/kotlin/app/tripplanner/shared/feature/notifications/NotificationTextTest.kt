package app.tripplanner.shared.feature.notifications

import app.tripplanner.shared.core.model.ActivityEvent
import app.tripplanner.shared.data.UserRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NotificationTextTest {
    @Test
    fun describesEachEventType() {
        assertEquals("Ana added Louvre to Day 2", NotificationText.describe("stop_added", "Ana", "Louvre", 1, null))
        assertEquals("Ana removed Louvre from Day 1", NotificationText.describe("stop_removed", "Ana", "Louvre", 0, null))
        assertEquals("Ana moved Louvre from Day 1 to Day 3", NotificationText.describe("stop_moved", "Ana", "Louvre", 2, 0))
        assertEquals("Ana reordered Louvre on Day 2", NotificationText.describe("stop_moved", "Ana", "Louvre", 1, 1))
        assertEquals("Ana joined the trip", NotificationText.describe("member_joined", "Ana", "", 0, null))
        assertEquals("Ana moved Louvre to 14:00 on Day 2", NotificationText.describe("entry_pinned", "Ana", "Louvre", 1, null, "14:00"))
        assertEquals("Ana unpinned Louvre", NotificationText.describe("entry_unpinned", "Ana", "Louvre", 1, null))
        assertEquals("Someone changed the plan", NotificationText.describe("mystery", "", "", 0, null))
        assertEquals("Ana added Louvre to Day 2", NotificationText.describe(ActivityEvent(type = "stop_added", actorName = "Ana", stopName = "Louvre", day = 1)))
    }

    @Test
    fun parsesPayloadAndKeysTrayEntryByTripAndActor() {
        val a = PushPayload.parse(mapOf("tripId" to "t1", "actorId" to "u1", "type" to "stop_added", "stopId" to "s1", "day" to "1", "count" to "1"))!!
        val b = PushPayload.parse(mapOf("tripId" to "t1", "actorId" to "u1", "kind" to "digest", "count" to "4"))!!
        assertEquals(a.notificationId, b.notificationId)
        assertEquals("s1", a.stopId)
        assertTrue(b.isDigest)
        assertNull(PushPayload.parse(mapOf("kind" to "single")))
    }

    @Test
    fun formatsSingleAndDigestNotifications() {
        val single = PushPayload.parse(mapOf("tripId" to "t1", "tripName" to "Paris", "actorName" to "Ana", "type" to "stop_added", "stopName" to "Louvre", "day" to "1"))!!
        assertEquals("Paris" to "Ana added Louvre to Day 2", NotificationText.notification(single))
        val digest = PushPayload.parse(mapOf("tripId" to "t1", "tripName" to "Paris", "actorName" to "Ana", "kind" to "digest", "count" to "4", "type" to "stop_moved", "stopName" to "Orsay", "day" to "0"))!!
        assertEquals("Paris" to "Ana made 4 changes · latest: reordered Orsay on Day 1", NotificationText.notification(digest))
    }

    @Test
    fun tokenCapDropsOldestAndDeduplicates() {
        assertEquals(listOf("a", "b"), UserRepository.trimTokens(listOf("a"), "b"))
        assertEquals(listOf("a", "c", "b"), UserRepository.trimTokens(listOf("a", "b", "c"), "b"))
        assertEquals(listOf("2", "3", "4", "5", "6"), UserRepository.trimTokens(listOf("1", "2", "3", "4", "5"), "6"))
    }
}
