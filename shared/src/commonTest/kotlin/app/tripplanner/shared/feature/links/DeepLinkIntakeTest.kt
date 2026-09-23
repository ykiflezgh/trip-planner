package app.tripplanner.shared.feature.links

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeepLinkIntakeTest {
    @Test
    fun inviteLinksBecomeJoin() {
        val intake = DeepLinkIntake()
        assertTrue(intake.offer("https://tripplanner-dev-fe0a4.web.app/join/2uIGu4Tr"))
        assertEquals(DeepLink.Join("2uIGu4Tr"), intake.consume())
        assertNull(intake.consume())
    }

    @Test
    fun unrelatedUrlsAreRefused() {
        val intake = DeepLinkIntake()
        assertFalse(intake.offer("https://example.com/other"))
        assertNull(intake.pending.value)
    }

    @Test
    fun notificationTargetsOpenTheTripAndLatestWins() {
        val intake = DeepLinkIntake()
        assertTrue(intake.offerTrip("t1", "s1"))
        assertTrue(intake.offerTrip("t2", ""))
        assertEquals(DeepLink.OpenTrip("t2", null), intake.consume())
        assertFalse(intake.offerTrip("", "s1"))
    }
}
