package app.tripplanner.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.tripplanner.MainActivity
import app.tripplanner.push.TripMessagingService

/**
 * Fires when a reminder alarm goes off: posts the "time to leave" notification whose tap opens
 * the trip at that stop (same extras as an activity push, design §10).
 */
class ReminderReceiver : BroadcastReceiver() {
    /**
     * Complexity:
     * - **Time:** O(1).
     * - **Space:** O(1).
     */
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(context)
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        val open = Intent(context, MainActivity::class.java).apply {
            action = TripMessagingService.ACTION_OPEN_TRIP
            putExtra(TripMessagingService.EXTRA_TRIP_ID, intent.getStringExtra(EXTRA_TRIP_ID))
            putExtra(TripMessagingService.EXTRA_STOP_ID, intent.getStringExtra(EXTRA_STOP_ID))
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val tap = PendingIntent.getActivity(context, id.hashCode(), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle(intent.getStringExtra(EXTRA_TITLE))
            .setContentText(intent.getStringExtra(EXTRA_BODY))
            .setStyle(NotificationCompat.BigTextStyle().bigText(intent.getStringExtra(EXTRA_BODY)))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(context).notify(id.hashCode(), notification)
    }

    companion object {
        const val ACTION = "app.tripplanner.REMINDER"
        const val CHANNEL_ID = "reminders"
        const val EXTRA_ID = "id"
        const val EXTRA_TRIP_ID = "tripId"
        const val EXTRA_STOP_ID = "stopId"
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"

        /**
         * Complexity:
         * - **Time:** O(1); idempotent.
         * - **Space:** O(1).
         */
        fun ensureChannel(context: Context) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Time to leave", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Reminders before each stop, based on the trip schedule"
                },
            )
        }
    }
}
