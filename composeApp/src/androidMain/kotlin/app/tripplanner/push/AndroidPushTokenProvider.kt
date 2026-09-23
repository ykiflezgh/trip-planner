package app.tripplanner.push

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import app.tripplanner.auth.CurrentActivity
import app.tripplanner.shared.platform.PushTokenProvider
import co.touchlab.kermit.Logger
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * FCM token on Android (design §6.4): fetched once at startup, then refreshed by
 * [TripMessagingService.onNewToken]. The notification permission (Android 13+) is requested
 * through the foreground activity's launcher, registered by MainActivity.
 */
class AndroidPushTokenProvider(
    private val context: Context,
    @Suppress("unused") private val currentActivity: CurrentActivity,
) : PushTokenProvider {
    private val log = Logger.withTag("Push")
    private val _token = MutableStateFlow<String?>(null)
    override val token: StateFlow<String?> = _token

    /** Set by MainActivity; null before the activity exists (permission then reports "not granted"). */
    var permissionLauncher: ActivityResultLauncher<String>? = null
    private var pendingPermission: CompletableDeferred<Boolean>? = null

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                _token.value = FirebaseMessaging.getInstance().token.await()
                log.i { "FCM token ready" }
            } catch (e: Exception) {
                log.w { "FCM token unavailable: ${e.message}" } // e.g. no Google Play services
            }
        }
        TokenRefresh.listener = { _token.value = it }
    }

    /**
     * Complexity:
     * - **Time:** O(1) plus the system dialog.
     * - **Space:** O(1).
     */
    override suspend fun requestPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return true
        val launcher = permissionLauncher ?: return false
        pendingPermission?.let { return it.await() }
        val deferred = CompletableDeferred<Boolean>().also { pendingPermission = it }
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        return deferred.await()
    }

    /**
     * Complexity:
     * - **Time:** O(1).
     * - **Space:** O(1).
     */
    fun onPermissionResult(granted: Boolean) {
        pendingPermission?.complete(granted)
        pendingPermission = null
    }
}

/** Static hop from the system-created [TripMessagingService] to the Koin-owned provider. */
internal object TokenRefresh {
    var listener: ((String) -> Unit)? = null
}
