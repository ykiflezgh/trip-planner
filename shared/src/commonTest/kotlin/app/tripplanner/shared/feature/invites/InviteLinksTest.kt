package app.tripplanner.shared.feature.invites

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InviteLinksTest {
    private val host = "tripplanner-dev-fe0a4.web.app"

    @Test
    fun buildsAndParsesHostedLink() {
        val url = InviteLinks.joinUrl(host, "Ab12-_xyz")
        assertEquals("https://$host/join/Ab12-_xyz", url)
        assertEquals("Ab12-_xyz", InviteLinks.parseJoinCode(url))
        assertEquals("Ab12-_xyz", InviteLinks.parseJoinCode("$url/?utm=1#x"))
    }

    @Test
    fun parsesCustomScheme() {
        assertEquals("k9Q_-7", InviteLinks.parseJoinCode("tripplanner://join/k9Q_-7"))
    }

    @Test
    fun rejectsOtherLinksAndBadCodes() {
        assertNull(InviteLinks.parseJoinCode("https://$host/"))
        assertNull(InviteLinks.parseJoinCode("https://$host/join/"))
        assertNull(InviteLinks.parseJoinCode("https://$host/trips/abc"))
        assertNull(InviteLinks.parseJoinCode("https://$host/join/a b"))
        assertNull(InviteLinks.parseJoinCode("mailto:x@y.z"))
    }

    @Test
    fun normalizesPastedInput() {
        assertEquals("abcd12", InviteLinks.normalizeCodeInput("  abcd12 "))
        assertEquals("abcd12", InviteLinks.normalizeCodeInput("https://$host/join/abcd12"))
        assertNull(InviteLinks.normalizeCodeInput("ab"))
    }
}
