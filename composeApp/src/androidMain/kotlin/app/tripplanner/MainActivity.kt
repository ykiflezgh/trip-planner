package app.tripplanner

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.tripplanner.shared.feature.invites.InviteIntake
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val inviteIntake: InviteIntake by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Koin + Firebase are initialised in TripPlannerApp (Application) / google-services.
        setContent { App() }
        offerInvite(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        offerInvite(intent)
    }

    /** App Link `https://<host>/join/{code}` (design §8.2); anything else is ignored. */
    private fun offerInvite(intent: Intent?) {
        intent?.dataString?.let { inviteIntake.offer(it) }
    }
}
