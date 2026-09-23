package app.tripplanner.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.tripplanner.shared.platform.Reminder
import app.tripplanner.shared.platform.ReminderScheduler
import co.touchlab.kermit.Logger

/**
 * Reminders as inexact wake-up alarms (design §6.4: `setAndAllowWhileIdle`, no exact-alarm
 * permission) delivered to [ReminderReceiver]. The ids of the alarms this app owns are kept in
 * SharedPreferences so a resync cancels exactly that set and nothing else. Alarms do not survive
 * a reboot; the next app start re-syncs them.
 */
class AndroidReminderScheduler(private val context: Context) : ReminderScheduler {
    private val log = Logger.withTag("Reminders")
    private val alarms get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val store get() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Complexity:
     * - **Time:** O(P + R) for the P previously owned ids and the R new reminders.
     * - **Space:** O(R).
     */
    override fun replace(reminders: List<Reminder>) {
        val owned = store.getStringSet(KEY_IDS, emptySet()).orEmpty()
        for (id in owned) alarms.cancel(pending(id, null))
        for (r in reminders) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.fireAtEpochSeconds * 1000, pending(r.id, r))
        }
        store.edit().putStringSet(KEY_IDS, reminders.map { it.id }.toSet()).apply()
        log.i { "replaced ${owned.size} reminders with ${reminders.size}" }
    }

    /**
     * Same request code and intent shape for schedule and cancel, so `cancel` matches.
     *
     * Complexity:
     * - **Time:** O(1).
     * - **Space:** O(1).
     */
    private fun pending(id: String, r: Reminder?): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION
            data = android.net.Uri.parse("tripplanner://reminder/$id") // distinct per id for filterEquals
            if (r != null) {
                putExtra(ReminderReceiver.EXTRA_ID, r.id)
                putExtra(ReminderReceiver.EXTRA_TRIP_ID, r.tripId)
                putExtra(ReminderReceiver.EXTRA_STOP_ID, r.stopId)
                putExtra(ReminderReceiver.EXTRA_TITLE, r.title)
                putExtra(ReminderReceiver.EXTRA_BODY, r.body)
            }
        }
        return PendingIntent.getBroadcast(context, id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {
        const val PREFS = "reminders"
        const val KEY_IDS = "owned_ids"
    }
}
