package app.tripplanner.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.tripplanner.MainActivity
import app.tripplanner.shared.feature.notifications.NotificationText
import app.tripplanner.shared.feature.notifications.PushPayload
import co.touchlab.kermit.Logger
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Data messages from the fan-out Function (design §10) become local notifications worded on the
 * device; one tray entry per (trip, actor), so a burst digest replaces the singles. Tapping
 * opens MainActivity with the trip/stop ids, which the deep-link intake turns into navigation.
 */
class TripMessagingService : FirebaseMessagingService() {
    private val log = Logger.withTag("Push")

    /**
     * Complexity:
     * - **Time:** O(1).
     * - **Space:** O(1).
     */
    override fun onNewToken(token: String) {
        log.i { "FCM token refreshed" }
        TokenRefresh.listener?.invoke(token)
    }

    /**
     * Complexity:
     * - **Time:** O(L) to word the L-character text.
     * - **Space:** O(L).
     */
    override fun onMessageReceived(message: RemoteMessage) {
        val payload = PushPayload.parse(message.data) ?: run { log.w { "push without tripId ignored" }; return }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
        ) {
            log.i { "notification permission missing; dropping ${payload.kind}" }
            return
        }
        val (title, body) = NotificationText.notification(payload)
        val open = Intent(this, MainActivity::class.java).apply {
            action = ACTION_OPEN_TRIP
            putExtra(EXTRA_TRIP_ID, payload.tripId)
            payload.eventId?.let { putExtra(EXTRA_EVENT_ID, it) }
            // singleTop activity: a running instance gets onNewIntent, otherwise a fresh launch.
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            this, payload.notificationId, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mapmode)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setGroup(payload.tripId)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(this).notify(payload.notificationId, notification)
        log.i { "notified ${payload.kind} trip=${payload.tripId} count=${payload.count}" }
    }

    companion object {
        const val CHANNEL_ID = "trip_changes"
        const val ACTION_OPEN_TRIP = "app.tripplanner.OPEN_TRIP"
        const val EXTRA_TRIP_ID = "tripId"
        const val EXTRA_EVENT_ID = "eventId"

        /**
         * Complexity:
         * - **Time:** O(1); idempotent.
         * - **Space:** O(1).
         */
        fun ensureChannel(context: Context) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Trip changes", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Events added, moved, pinned or removed by other members"
                },
            )
        }
    }
}
