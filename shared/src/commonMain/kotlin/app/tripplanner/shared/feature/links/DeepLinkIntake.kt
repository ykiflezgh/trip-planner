package app.tripplanner.shared.feature.links

import app.tripplanner.shared.feature.invites.InviteLinks
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Where an external entry point wants the app to go once the user is signed in. */
sealed interface DeepLink {
    /** Invite link or pasted code (design §8.2). */
    data class Join(val code: String) : DeepLink
    /** Notification tap (design §10): open the trip and, when known, select the event. */
    data class OpenTrip(val tripId: String, val eventId: String? = null) : DeepLink
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
     * Invite links / codes only; returns false for anything else so callers can pass it on.
     *
     * Complexity:
     * - **Time:** O(N) to parse an N-character URL or code.
     * - **Space:** O(1).
     */
    fun offer(urlOrCode: String): Boolean {
        val code = InviteLinks.normalizeCodeInput(urlOrCode) ?: return false
        _pending.value = DeepLink.Join(code)
        return true
    }

    /**
     * Complexity:
     * - **Time:** O(1).
     * - **Space:** O(1).
     */
    fun offerTrip(tripId: String, eventId: String?): Boolean {
        if (tripId.isBlank()) return false
        _pending.value = DeepLink.OpenTrip(tripId, eventId?.takeIf { it.isNotBlank() })
        return true
    }

    /**
     * Complexity:
     * - **Time:** O(1).
     * - **Space:** O(1).
     */
    fun consume(): DeepLink? = _pending.value.also { _pending.value = null }
}
