package app.tripplanner

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import app.tripplanner.push.AndroidPushTokenProvider
import app.tripplanner.push.TripMessagingService
import app.tripplanner.shared.feature.links.DeepLinkIntake
import app.tripplanner.shared.platform.PushTokenProvider
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val intake: DeepLinkIntake by inject()
    private val push: PushTokenProvider by inject()

    // POST_NOTIFICATIONS (Android 13+): the result lands in the provider's pending request.
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            (push as? AndroidPushTokenProvider)?.onPermissionResult(granted)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        (push as? AndroidPushTokenProvider)?.permissionLauncher = notificationPermission
        // Koin + Firebase are initialised in TripPlannerApp (Application) / google-services.
        setContent { App() }
        offerDeepLink(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        offerDeepLink(intent)
    }

    /**
     * App Link `https://<host>/join/{code}` (design §8.2) or a notification tap carrying the
     * trip/event ids (design §10); anything else is ignored.
     */
    private fun offerDeepLink(intent: Intent?) {
        if (intent == null) return
        val tripId = intent.getStringExtra(TripMessagingService.EXTRA_TRIP_ID)
        if (tripId != null) {
            intake.offerTrip(tripId, intent.getStringExtra(TripMessagingService.EXTRA_EVENT_ID))
            intent.removeExtra(TripMessagingService.EXTRA_TRIP_ID) // consume: a config change must not re-fire it
            return
        }
        intent.dataString?.let { intake.offer(it) }
    }
}
