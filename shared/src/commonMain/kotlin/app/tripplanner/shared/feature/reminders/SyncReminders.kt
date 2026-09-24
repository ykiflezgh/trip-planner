package app.tripplanner.shared.feature.reminders

import app.tripplanner.shared.core.model.ReminderPrefs
import app.tripplanner.shared.data.AuthRepository
import app.tripplanner.shared.data.TripRepository
import app.tripplanner.shared.data.UserRepository
import app.tripplanner.shared.platform.ReminderScheduler
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock

/**
 * Keeps the OS's pending reminders equal to what [ReminderPlanner] wants for the next 48 h
 * (design v1.1 §8.5). Runs on app start / foreground, on a change to the open trip, on a data
 * push, and whenever the user's reminder preference changes. Reads each relevant trip once per
 * sync from the local cache (one short-lived listener per collection), so it works offline.
 */
@OptIn(kotlin.time.ExperimentalTime::class)
class SyncReminders(
    private val auth: AuthRepository,
    private val users: UserRepository,
    private val trips: TripRepository,
    private val scheduler: ReminderScheduler,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val now: () -> Long = { Clock.System.now().epochSeconds },
) {
    private val log = Logger.withTag("Reminders")
    private val requests = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /**
     * Complexity:
     * - **Time:** O(1) to start; each sync is O(T · S) over the trips in the window and their stops.
     * - **Space:** O(1).
     */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    fun start() {
        if (!scheduler.supported) { log.i { "reminders unsupported here; sync disabled" }; return }
        // Preference changes and sign-in/out.
        scope.launch {
            auth.user.flatMapLatest { u -> if (u == null) flowOf(null) else users.prefs(u.uid) }
                .collect { request("prefs") }
        }
        // Coalesce bursts (snapshot storms, foreground + push together) into one sync.
        scope.launch { requests.debounce(DEBOUNCE_MS).collect { reason -> sync(reason) } }
    }

    /**
     * Complexity:
     * - **Time:** O(1).
     * - **Space:** O(1).
     */
    fun request(reason: String) { requests.tryEmit(reason) }

    /**
     * Complexity:
     * - **Time:** O(T · S) for T trips overlapping the window and their S stops.
     * - **Space:** O(T · S).
     */
    suspend fun sync(reason: String) {
        val uid = auth.currentUser?.uid ?: run { scheduler.replace(emptyList()); return }
        try {
            val prefs = withTimeoutOrNull(READ_TIMEOUT_MS) { users.prefs(uid).first() } ?: ReminderPrefs().let { return }
            if (!prefs.reminders.enabled) { scheduler.replace(emptyList()); log.i { "sync($reason): off" }; return }
            val t = now()
            val all = withTimeoutOrNull(READ_TIMEOUT_MS) { trips.myTrips(uid).first() } ?: return
            val relevant = all.filter { trip -> TripWindow.overlaps(trip, t, ReminderPlanner.WINDOW_SECONDS) }
            val data = relevant.mapNotNull { trip ->
                withTimeoutOrNull(READ_TIMEOUT_MS) {
                    TripData(
                        trip = trip,
                        stops = trips.stops(trip.id).first(),
                        legs = trips.travel(trip.id).first().associateBy { Triple(it.fromStopId, it.toStopId, it.mode) },
                        dayHours = trips.days(trip.id).first().associateBy { it.day },
                    )
                }
            }
            val plan = ReminderPlanner.plan(data, prefs.reminders, t)
            scheduler.replace(plan)
            log.i { "sync($reason): ${plan.size} reminders across ${data.size} trips" }
        } catch (e: Exception) {
            log.w { "sync($reason) failed: ${e.message}" }
        }
    }

    companion object {
        const val DEBOUNCE_MS = 1_500L
        const val READ_TIMEOUT_MS = 8_000L
    }
}
