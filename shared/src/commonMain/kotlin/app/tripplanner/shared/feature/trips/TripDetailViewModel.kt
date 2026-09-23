package app.tripplanner.shared.feature.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tripplanner.schedule.DaySchedule
import app.tripplanner.schedule.Schedule
import app.tripplanner.shared.core.model.DayHoursDoc
import app.tripplanner.shared.core.model.Stop
import app.tripplanner.shared.feature.calendar.DaySchedules
import app.tripplanner.shared.core.model.TravelLeg
import app.tripplanner.shared.core.model.TravelMode
import app.tripplanner.shared.core.model.Trip
import app.tripplanner.shared.data.AuthRepository
import app.tripplanner.shared.data.FirestoreNetworkSync
import app.tripplanner.shared.data.PlanningFunctions
import app.tripplanner.shared.di.AppConfig
import app.tripplanner.shared.feature.invites.InviteLinks
import app.tripplanner.shared.data.TripRepository
import app.tripplanner.shared.data.UserRepository
import app.tripplanner.shared.platform.ConnectivityMonitor
import co.touchlab.kermit.Logger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class TripDetailUiState(
    val loading: Boolean = true,
    val trip: Trip? = null,
    val stopsByDay: Map<Int, List<Stop>> = emptyMap(),
    val selectedDay: Int = 0,
    val selectedStopId: String? = null,
    val error: String? = null,
    /** One-shot user message (e.g. a concurrent delete); the screen shows it and calls [TripDetailViewModel.consumeMessage]. */
    val message: String? = null,
    val online: Boolean = true,
    /** The signed-in user owns this trip: only owners can share it (design §8.2). */
    val isOwner: Boolean = false,
    /** Owner or editor: may reorder, pin, resize and set day hours; viewers see the same calendar read-only. */
    val canEdit: Boolean = false,
    val sharing: Boolean = false,
    /** Per-day hour overrides (design v1.1 §7). */
    val dayHours: Map<Int, DayHoursDoc> = emptyMap(),
    /** Travel legs by (fromStopId, toStopId, mode) (design §8.3); absent = still computing. */
    val legs: Map<Triple<String, String, TravelMode>, TravelLeg> = emptyMap(),
    /** This user muted push for this trip (`users/{uid}.notificationPrefs.mutedTripIds`, design §10). */
    val muted: Boolean = false,
    /** One-shot: the invite link to hand to the platform share sheet; the screen calls [TripDetailViewModel.consumeShare]. */
    val shareUrl: String? = null,
) {
    /** True while any local write on this trip awaits the server (design §9). */
    val pendingSync: Boolean get() = trip?.pendingSync == true || stopsByDay.values.any { day -> day.any { it.pendingSync } }
    /** Days in the trip (inclusive of both ends); 1 until the trip document arrives. */
    val dayCount: Int get() = trip?.let { TripDays.count(it) } ?: 1
    /** Stops of the selected day in itinerary order (the repository sorts by fractional key). */
    val stopsForSelectedDay: List<Stop> get() = stopsByDay[selectedDay].orEmpty()

    /**
     * Complexity:
     * - **Time:** O(1) map lookup.
     * - **Space:** O(1).
     */
    fun leg(fromStopId: String, toStopId: String, mode: TravelMode): TravelLeg? = legs[Triple(fromStopId, toStopId, mode)]

    /**
     * The selected day on the clock (design v1.1 §8.4): computed here from the same data every
     * device holds, never stored. `null` until the trip document arrives.
     *
     * Complexity:
     * - **Time:** O(S) for the S stops of the day (engine pass).
     * - **Space:** O(S).
     */
    val schedule: DaySchedule? get() = scheduleFor(selectedDay)

    /**
     * Complexity:
     * - **Time:** O(S).
     * - **Space:** O(S).
     */
    fun scheduleFor(day: Int): DaySchedule? = trip?.let { DaySchedules.compute(it, day, stopsByDay[day].orEmpty(), legs, dayHours[day]) }
}

class TripDetailViewModel(
    private val tripId: String,
    private val repo: TripRepository,
    connectivity: ConnectivityMonitor,
    private val networkSync: FirestoreNetworkSync,
    private val functions: PlanningFunctions,
    private val auth: AuthRepository,
    private val config: AppConfig,
    private val users: UserRepository,
) : ViewModel() {

    private val log = Logger.withTag("TripDetail")
    private val selectedStopId = MutableStateFlow<String?>(null)
    private val selectedDay = MutableStateFlow(0)
    private val message = MutableStateFlow<String?>(null)
    private val share = MutableStateFlow<Pair<Boolean, String?>>(false to null) // sharing, shareUrl
    /** UI-only state folded into one flow so the outer combine stays within the typed arity. */
    private data class Local(val selectedStopId: String?, val selectedDay: Int, val message: String?, val sharing: Boolean, val shareUrl: String?)

    private val stops = repo.stops(tripId)
        .map { stops -> TripDetailUiState(loading = false, stopsByDay = stops.groupBy { it.day }) }
        .catch { emit(TripDetailUiState(loading = false, error = it.message ?: "Could not load stops")) }
    private val trip = repo.trip(tripId).catch { emit(null) }
    private val travel = repo.travel(tripId).catch { emit(emptyList()) }
    private val days = repo.days(tripId).catch { emit(emptyList()) }
    @OptIn(ExperimentalCoroutinesApi::class)
    private val muted = auth.user.flatMapLatest { u -> if (u == null) flowOf(false) else users.prefs(u.uid).map { tripId in it.mutedTripIds } }.catch { emit(false) }

    init {
        // Writes apply locally at once; rejections arrive here later (design §7, §9).
        viewModelScope.launch {
            repo.writeFailures.collect { f ->
                message.value = if (f.notFound) "That stop was removed by another member" else "Could not ${f.operation}: ${f.message}"
            }
        }
    }

    /**
     * UI state stream. Emits a new [TripDetailUiState] when stops or the selection change.
     *
     * Complexity:
     * - **Time:** O(S) per stops emission, where S is the number of stops in the trip (due to
     *   `stops.groupBy { it.day }`); O(L) per travel emission for L legs; O(1) per trip or
     *   selection change (shallow copy).
     * - **Space:** O(S + L) auxiliary heap space for the grouped stops and the leg map.
     */
    val state: StateFlow<TripDetailUiState> =
        combine(
            combine(stops, trip, connectivity.online, muted) { s, t, online, m ->
                val uid = auth.currentUser?.uid
                val role = uid?.let { t?.roles?.get(it) }
                s.copy(trip = t, online = online, muted = m, isOwner = role == "owner", canEdit = role == "owner" || role == "editor")
            },
            combine(travel, days) { legs, d -> legs.associateBy { Triple(it.fromStopId, it.toStopId, it.mode) } to d.associateBy { it.day } },
            combine(selectedStopId, selectedDay, message, share) { sel, day, msg, sh -> Local(sel, day, msg, sh.first, sh.second) },
        ) { s, td, l ->
            s.copy(legs = td.first, dayHours = td.second, selectedStopId = l.selectedStopId, selectedDay = l.selectedDay, message = l.message, sharing = l.sharing, shareUrl = l.shareUrl)
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripDetailUiState())

    /**
     * Map-marker tap lands here (platform map -> Kotlin, design §6.4). `null` clears.
     *
     * Complexity:
     * - **Time:** O(1) state update + one log line.
     * - **Space:** O(1).
     */
    fun selectStop(stopId: String?) {
        log.i { "selectStop($stopId)" }
        selectedStopId.value = stopId
    }

    /**
     * Notification deep link (design §10): once the stops arrive, show the stop's day and open it.
     * Gives up quietly if the stop is gone (removed since the notification was sent).
     *
     * Complexity:
     * - **Time:** O(S) to locate the stop among S stops on the first snapshot that has it.
     * - **Space:** O(1).
     */
    fun focusStop(stopId: String) {
        viewModelScope.launch {
            val stop = withTimeoutOrNull(FOCUS_TIMEOUT_MS) {
                state.map { s -> s.stopsByDay.values.asSequence().flatten().firstOrNull { it.id == stopId } }.first { it != null }
            } ?: return@launch
            selectedDay.value = stop.day
            selectedStopId.value = stop.id
        }
    }

    /**
     * Complexity:
     * - **Time:** O(1).
     * - **Space:** O(1).
     */
    fun selectDay(day: Int) {
        selectedDay.value = day.coerceAtLeast(0)
    }

    /**
     * Reorder = one write of a new fractional key; neighbours come from current UI order. The
     * write applies locally at once; a NOT_FOUND (deleted by another member) surfaces via
     * [TripRepository.writeFailures] as a message.
     *
     * Complexity:
     * - **Time:** O(L) where L is the length of the fractional index key (effectively O(1)) + one queued write.
     * - **Space:** O(1) auxiliary heap space.
     */
    fun moveStop(stopId: String, day: Int, afterOrder: String?, beforeOrder: String?) {
        repo.moveStop(tripId, stopId, day, afterOrder, beforeOrder, updatedBy = auth.currentUser?.uid.orEmpty())
    }

    /**
     * Cross-day move: the stop lands at the end of [day].
     *
     * Complexity:
     * - **Time:** O(1) lookup of the target day's last key + one write.
     * - **Space:** O(1).
     */
    fun moveToDay(stopId: String, day: Int) {
        val last = state.value.stopsByDay[day]?.lastOrNull()?.order
        moveStop(stopId, day, afterOrder = last, beforeOrder = null)
    }

    /**
     * Complexity:
     * - **Time:** O(1) document delete.
     * - **Space:** O(1).
     */
    fun deleteStop(stopId: String) {
        if (selectedStopId.value == stopId) selectedStopId.value = null
        repo.deleteStop(tripId, stopId)
    }

    /**
     * Owner shares the trip: the Function mints an invite code and the app builds the hosted
     * join link for the platform share sheet (design §8.2).
     *
     * Complexity:
     * - **Time:** O(1) callable round trip.
     * - **Space:** O(1).
     */
    fun share() {
        if (share.value.first) return
        viewModelScope.launch {
            share.value = true to null
            try {
                val invite = functions.createInvite(tripId)
                share.value = false to InviteLinks.joinUrl(config.appLinkHost, invite.code)
            } catch (e: Exception) {
                share.value = false to null
                message.value = e.message ?: "Could not create an invite link"
            }
        }
    }

    /**
     * Complexity:
     * - **Time:** O(1).
     * - **Space:** O(1).
     */
    fun consumeShare() {
        share.value = false to null
    }

    /**
     * Pins an entry to a wall-clock start (drag in Day view, design v1.1 §8.4 step 2): one write
     * that also moves it to [order] so the list stays chronological; `null` order keeps its place.
     *
     * Complexity:
     * - **Time:** O(1) queued write.
     * - **Space:** O(1).
     */
    fun pinEntry(stopId: String, hhmm: String, order: String?) {
        if (Schedule.parseTime(hhmm) == null) return
        repo.setFixedStart(tripId, stopId, hhmm, order, updatedBy = auth.currentUser?.uid.orEmpty())
    }

    /**
     * Returns an entry to the computed flow.
     *
     * Complexity:
     * - **Time:** O(1) queued write.
     * - **Space:** O(1).
     */
    fun unpinEntry(stopId: String) = repo.setFixedStart(tripId, stopId, null, null, updatedBy = auth.currentUser?.uid.orEmpty())

    /**
     * Drag of a block's bottom edge (design v1.1 §8.4 step 3); clamped to the engine's bounds.
     *
     * Complexity:
     * - **Time:** O(1) queued write.
     * - **Space:** O(1).
     */
    fun resizeEntry(stopId: String, durationMin: Int) =
        repo.setDuration(tripId, stopId, durationMin.coerceIn(Schedule.MIN_DURATION_MIN, Schedule.MAX_DURATION_MIN), updatedBy = auth.currentUser?.uid.orEmpty())

    /**
     * Per-day hours (design v1.1 §8.4 step 4). Ignores malformed times.
     *
     * Complexity:
     * - **Time:** O(1) queued write.
     * - **Space:** O(1).
     */
    fun setDayHours(day: Int, start: String, end: String) {
        val s = Schedule.parseTime(start) ?: return
        val e = Schedule.parseTime(end) ?: return
        if (e <= s) { message.value = "The day must end after it starts"; return }
        repo.setDayHours(tripId, day, start, end, updatedBy = auth.currentUser?.uid.orEmpty())
    }

    /**
     * Per-trip mute (design §10); the Function honors it on the next fan-out.
     *
     * Complexity:
     * - **Time:** O(1) merge write.
     * - **Space:** O(1).
     */
    fun setMuted(muted: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            runCatching { users.setTripMuted(uid, tripId, muted) }
                .onFailure { message.value = it.message ?: "Could not update notifications" }
        }
    }

    /**
     * "Still syncing" retry: forces Firestore to drop and re-open its connection.
     *
     * Complexity:
     * - **Time:** O(1).
     * - **Space:** O(1).
     */
    fun retrySync() = networkSync.retry()

    /**
     * Complexity:
     * - **Time:** O(1).
     * - **Space:** O(1).
     */
    fun consumeMessage() {
        message.value = null
    }

    companion object { const val FOCUS_TIMEOUT_MS = 10_000L }
}
