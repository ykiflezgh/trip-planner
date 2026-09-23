package app.tripplanner.shared.feature.notifications

import app.tripplanner.shared.data.AuthRepository
import app.tripplanner.shared.data.UserRepository
import app.tripplanner.shared.platform.PushTokenProvider
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Keeps `users/{uid}.fcmTokens` in step with the device (design §10): whenever a signed-in
 * user and a token are both known, the pair is registered (once per distinct pair), and the
 * profile is refreshed so Functions can name the actor. Process-lifetime singleton.
 */
class PushRegistrar(
    private val auth: AuthRepository,
    private val push: PushTokenProvider,
    private val users: UserRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val log = Logger.withTag("Push")

    /**
     * Complexity:
     * - **Time:** O(1) per (user, token) change: one read + one write.
     * - **Space:** O(1).
     */
    fun start() {
        scope.launch {
            combine(auth.user, push.token) { user, token -> user?.let { Triple(it.uid, it.displayName, it.photoURL) } to token }
                .map { (u, t) -> if (u != null && t != null) u to t else null }
                .filterNotNull()
                .distinctUntilChanged()
                .collect { (u, token) ->
                    val (uid, name, photo) = u
                    try {
                        users.ensureProfile(uid, name, photo)
                        users.registerToken(uid, token)
                    } catch (e: Exception) {
                        log.w { "token registration failed: ${e.message}" }
                    }
                }
        }
        // Ask for permission (Android 13+/iOS prompt) the first time a signed-in session starts.
        scope.launch {
            auth.user.filterNotNull().distinctUntilChanged { a, b -> a.uid == b.uid }.collect {
                val granted = runCatching { push.requestPermission() }.getOrDefault(false)
                log.i { "notification permission granted=$granted" }
            }
        }
    }

    /**
     * Call before `signOut`: drops this device's token so the next user of the device does not
     * receive the previous user's trips. Bounded so sign-out never hangs offline.
     *
     * Complexity:
     * - **Time:** O(1) write, bounded by [SIGN_OUT_TIMEOUT_MS].
     * - **Space:** O(1).
     */
    suspend fun prepareSignOut() {
        val uid = auth.currentUser?.uid ?: return
        val token = push.token.value ?: return
        withTimeoutOrNull(SIGN_OUT_TIMEOUT_MS) {
            runCatching { users.unregisterToken(uid, token) }.onFailure { log.w { "token removal failed: ${it.message}" } }
        }
    }

    companion object { const val SIGN_OUT_TIMEOUT_MS = 3_000L }
}
