package app.tripplanner.push

import app.tripplanner.shared.platform.PushTokenProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Implemented in Swift (iosApp/PushBridge.swift): asks UNUserNotificationCenter for permission
 * and registers for remote notifications; the FCM token arrives separately via [setPushToken].
 */
interface PushBridge {
    fun requestPermission(callback: (granted: Boolean) -> Unit)
}

/**
 * iOS actual for the push boundary (design §6.4). The token flow is process-wide because the
 * Firebase Messaging delegate lives in the Swift app delegate, outside Koin.
 */
class IosPushTokenProvider(private val bridge: PushBridge) : PushTokenProvider {
    override val token: StateFlow<String?> get() = tokens

    /**
     * Complexity:
     * - **Time:** O(1) plus the system prompt.
     * - **Space:** O(1).
     */
    override suspend fun requestPermission(): Boolean = suspendCancellableCoroutine { cont ->
        bridge.requestPermission { granted -> if (cont.isActive) cont.resume(granted) }
    }

    companion object {
        private val tokens = MutableStateFlow<String?>(null)

        /**
         * Complexity:
         * - **Time:** O(1).
         * - **Space:** O(1).
         */
        fun setToken(token: String?) { tokens.value = token }
    }
}
