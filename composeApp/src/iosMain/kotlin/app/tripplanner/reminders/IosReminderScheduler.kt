package app.tripplanner.reminders

import app.tripplanner.shared.platform.Reminder
import app.tripplanner.shared.platform.ReminderScheduler

/**
 * Implemented in Swift (iosApp/PushBridge.swift) over UNUserNotificationCenter: calendar-trigger
 * local notifications whose tap carries the trip and stop ids (same handler as an activity push).
 */
interface ReminderBridge {
    fun schedule(id: String, tripId: String, stopId: String, fireAtEpochSeconds: Long, title: String, body: String)
    /** Removes pending and delivered notifications with these ids. */
    fun cancel(ids: List<String>)
}

/**
 * iOS actual for the reminder boundary (design §6.4). iOS caps pending local notifications at
 * 64; the planner already keeps the set at 60, earliest first, over a rolling 48 h window.
 */
class IosReminderScheduler(private val bridge: ReminderBridge) : ReminderScheduler {
    private var owned: List<String> = emptyList()

    /**
     * Complexity:
     * - **Time:** O(P + R) for the P owned ids and R new reminders.
     * - **Space:** O(R).
     */
    override fun replace(reminders: List<Reminder>) {
        if (owned.isNotEmpty()) bridge.cancel(owned)
        reminders.forEach { bridge.schedule(it.id, it.tripId, it.stopId, it.fireAtEpochSeconds, it.title, it.body) }
        owned = reminders.map { it.id }
    }
}
