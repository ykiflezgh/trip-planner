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
    fun feedEventLinksOpenTheTripAndStop() {
        val intake = DeepLinkIntake()
        assertTrue(intake.offer("tripplanner://trip/8IYqFIfB86A81jgJZmUT/stop/M2K5bF7Q40ikmRRdVYxs"))
        assertEquals(DeepLink.OpenTrip("8IYqFIfB86A81jgJZmUT", "M2K5bF7Q40ikmRRdVYxs"), intake.consume())
        assertTrue(intake.offer("tripplanner://trip/abc"))
        assertEquals(DeepLink.OpenTrip("abc", null), intake.consume())
        assertFalse(intake.offer("tripplanner://trip/abc/other/x"))
        assertFalse(intake.offer("tripplanner://trip/"))
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
