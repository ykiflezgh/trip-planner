package app.tripplanner.shared.feature.invites

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Hands an incoming join link from the platform entry point (App Link intent, `onOpenURL`,
 * or the "Join a trip" dialog) to the navigation graph, which redeems it once the user is
 * signed in (design §8.2).
 */
class InviteIntake {
    private val _pendingCode = MutableStateFlow<String?>(null)
    val pendingCode: StateFlow<String?> = _pendingCode

    /**
     * Complexity:
     * - **Time:** O(N) to parse an N-character URL or code.
     * - **Space:** O(1).
     */
    fun offer(urlOrCode: String): Boolean {
        val code = InviteLinks.normalizeCodeInput(urlOrCode) ?: return false
        _pendingCode.value = code
        return true
    }

    /**
     * Complexity:
     * - **Time:** O(1).
     * - **Space:** O(1).
     */
    fun consume(): String? = _pendingCode.value.also { _pendingCode.value = null }
}
