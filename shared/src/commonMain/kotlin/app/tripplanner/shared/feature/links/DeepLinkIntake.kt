package app.tripplanner.shared.feature.links

import app.tripplanner.shared.feature.invites.InviteLinks
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Where an external entry point wants the app to go once the user is signed in. */
sealed interface DeepLink {
    /** Invite link or pasted code (design §8.2). */
    data class Join(val code: String) : DeepLink
    /** Notification tap (design §10): open the trip and, when known, select the stop. */
    data class OpenTrip(val tripId: String, val stopId: String? = null) : DeepLink
}

/**
 * Hands an incoming link from the platform entry point (App Link intent, `onOpenURL`,
 * notification tap, or the "Join a trip" dialog) to the navigation graph, which acts on it
 * once the user is signed in. Latest wins; there is at most one pending link.
 */
class DeepLinkIntake {
    private val _pending = MutableStateFlow<DeepLink?>(null)
    val pending: StateFlow<DeepLink?> = _pending

    /**
     * Invite links / codes, or the trip links the ICS feed puts in each event
     * (`tripplanner://trip/{tripId}[/stop/{stopId}]`, design §8.6); returns false for anything
     * else so callers can pass it on.
     *
     * Complexity:
     * - **Time:** O(N) to parse an N-character URL or code.
     * - **Space:** O(1).
     */
    fun offer(urlOrCode: String): Boolean {
        parseTripLink(urlOrCode)?.let { (tripId, stopId) -> return offerTrip(tripId, stopId) }
        val code = InviteLinks.normalizeCodeInput(urlOrCode) ?: return false
        _pending.value = DeepLink.Join(code)
        return true
    }

    /**
     * Complexity:
     * - **Time:** O(N).
     * - **Space:** O(1).
     */
    private fun parseTripLink(url: String): Pair<String, String?>? {
        val t = url.trim()
        if (!t.startsWith("${InviteLinks.CUSTOM_SCHEME}://", ignoreCase = true)) return null
        val parts = t.substringAfter("://").substringBefore('?').substringBefore('#').split('/').filter { it.isNotEmpty() }
        if (parts.size < 2 || !parts[0].equals("trip", ignoreCase = true) || !idChars.matches(parts[1])) return null
        val stopId = if (parts.size == 4 && parts[2].equals("stop", ignoreCase = true) && idChars.matches(parts[3])) parts[3] else null
        if (parts.size != 2 && stopId == null) return null
        return parts[1] to stopId
    }

    /**
     * Complexity:
     * - **Time:** O(1).
     * - **Space:** O(1).
     */
    fun offerTrip(tripId: String, stopId: String?): Boolean {
        if (tripId.isBlank()) return false
        _pending.value = DeepLink.OpenTrip(tripId, stopId?.takeIf { it.isNotBlank() })
        return true
    }

    /**
     * Complexity:
     * - **Time:** O(1).
     * - **Space:** O(1).
     */
    fun consume(): DeepLink? = _pending.value.also { _pending.value = null }

    private companion object {
        val idChars = Regex("[A-Za-z0-9_-]{1,128}")
    }
}
