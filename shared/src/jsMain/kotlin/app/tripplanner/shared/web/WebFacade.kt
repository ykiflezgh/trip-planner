package app.tripplanner.shared.web

import app.tripplanner.schedule.ScheduleJs
import app.tripplanner.shared.web.externals.getAuth
import app.tripplanner.shared.web.externals.getRedirectResult
import app.tripplanner.shared.core.model.TravelMode
import app.tripplanner.shared.core.util.FractionalIndex
import app.tripplanner.shared.core.model.Trip
import app.tripplanner.shared.feature.calendar.TimeDisplay
import app.tripplanner.shared.feature.trips.TripDays
import app.tripplanner.shared.di.AppConfig
import app.tripplanner.shared.di.sharedModule
import app.tripplanner.shared.feature.activity.ActivityFeedViewModel
import app.tripplanner.shared.feature.invites.JoinTripViewModel
import app.tripplanner.shared.feature.trips.TripDetailUiState
import app.tripplanner.shared.feature.trips.TripDetailViewModel
import app.tripplanner.shared.feature.trips.TripListUiState
import app.tripplanner.shared.feature.trips.TripListViewModel
import androidx.lifecycle.ViewModelStore
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseOptions
import dev.gitlive.firebase.initialize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.koin.core.context.startKoin
import org.koin.core.parameter.parametersOf
import org.koin.mp.KoinPlatform
import kotlin.js.JsExport
import kotlin.js.Promise

/**
 * The web client's door into the Kotlin core (design v1.1.1 §6.5, companion §6.2): plain
 * objects out, callbacks and Promises in, because `@JsExport` cannot carry `Flow` or `suspend`.
 * React consumes this through `lib/kotlin` and the `useKotlinState` hook; Kotlin types never
 * reach a component.
 */
@JsExport
object TripPlannerWeb {
    private val scope = MainScope()

    /**
     * Process-wide startup: Firebase default app, Koin with the shared and browser modules.
     * Mirrors `initKoin` on iOS and `TripPlannerApp` on Android.
     *
     * Complexity:
     * - **Time:** O(D) Koin definitions.
     * - **Space:** O(D).
     */
    fun start(applicationId: String, apiKey: String, projectId: String, authDomain: String, gcmSenderId: String, appLinkHost: String) {
        Firebase.initialize(
            options = FirebaseOptions(applicationId = applicationId, apiKey = apiKey, projectId = projectId, authDomain = authDomain, gcmSenderId = gcmSenderId),
        )
        val config = if (appLinkHost.isBlank()) AppConfig() else AppConfig(appLinkHost = appLinkHost)
        // No Places key: the web resolves places through the JS Places library (companion §8.3), never the Ktor client.
        startKoin { modules(sharedModule(placesApiKey = "", config = config), jsModule()) }
    }

    /**
     * Finishes a redirect sign-in after the browser returns (companion §8.1); resolves false when
     * none was pending. A plain promise chain on purpose: awaiting this Firebase promise from a
     * coroutine on Dispatchers.Main raised a "fatal exception in coroutines machinery" in the
     * W0 spike (kotlinx-coroutines 1.10.2 on JS), which is logged as an open question.
     *
     * Complexity:
     * - **Time:** O(1).
     * - **Space:** O(1).
     */
    fun resumeRedirect(): Promise<Boolean> = getRedirectResult(getAuth()).then<Boolean> { result: dynamic -> result != null }

    /**
     * Web push (companion §10): the page obtains the FCM token itself (permission prompts need a
     * user gesture in browsers) and hands it here; `PushRegistrar` stores it in `users/{uid}.fcmTokens`.
     *
     * Complexity:
     * - **Time:** O(1).
     * - **Space:** O(1).
     */
    fun setPushToken(token: String?) {
        (KoinPlatform.getKoin().get<app.tripplanner.shared.platform.PushTokenProvider>() as JsPushTokenProvider).setToken(token)
    }

    /**
     * Complexity:
     * - **Time:** O(1).
     * - **Space:** O(1).
     */
    fun activity(tripId: String): ActivityFacade = ActivityFacade(KoinPlatform.getKoin().get { parametersOf(tripId) }, scope)

    /**
     * Complexity:
     * - **Time:** O(1).
     * - **Space:** O(1).
     */
    fun join(code: String): JoinFacade = JoinFacade(KoinPlatform.getKoin().get { parametersOf(code) }, scope)

    /**
     * Complexity:
     * - **Time:** O(1).
     * - **Space:** O(1).
     */
    fun tripList(): TripListFacade = TripListFacade(KoinPlatform.getKoin().get(), scope)

    /**
     * Complexity:
     * - **Time:** O(1).
     * - **Space:** O(1).
     */
    fun tripDetail(tripId: String): TripDetailFacade = TripDetailFacade(KoinPlatform.getKoin().get { parametersOf(tripId) }, scope)

    /**
     * The schedule engine, same build as the apps and the feed (design §6.5): JSON in, JSON out.
     *
     * Complexity:
     * - **Time:** O(E) for E entries.
     * - **Space:** O(E).
     */
    fun computeDay(dateIso: String, dayStart: String, dayEnd: String, entriesJson: String, travelJson: String, defaultMode: String): String =
        ScheduleJs.computeDay(dateIso, dayStart, dayEnd, entriesJson, travelJson, defaultMode)
}

/** Trip list screen state (design companion §6.2). `subscribe` returns the unsubscribe function. */
@JsExport
class TripListFacade internal constructor(private val vm: TripListViewModel, private val scope: CoroutineScope) {
    /**
     * Complexity:
     * - **Time:** O(T) per emission for T trips.
     * - **Space:** O(T).
     */
    fun subscribe(onState: (Any) -> Unit): () -> Unit = vm.state.subscribeJs(scope) { onState(it.toJs()) }
    fun signIn() = vm.signIn()
    fun signOut() = vm.signOut()
    fun dismissError() = vm.dismissError()
}

/**
 * Trip detail screen state: the stops of the selected day, both travel legs between consecutive
 * place stops, and the computed schedule, in trip time or (after [setLocalTime]) device-local
 * time exactly as the apps' toggle does (design v1.1 §6.6).
 */
@JsExport
class TripDetailFacade internal constructor(private val vm: TripDetailViewModel, private val scope: CoroutineScope) {
    private val localTime = MutableStateFlow(false)
    // ViewModel.clear() is internal; a private store owns the ViewModel so close() can clear it.
    private val store = ViewModelStore().apply { put("trip", vm) }

    /**
     * Complexity:
     * - **Time:** O(S) per emission for S stops on the selected day.
     * - **Space:** O(S).
     */
    fun subscribe(onState: (Any) -> Unit): () -> Unit {
        val job: Job = scope.launch { combine(vm.state, localTime) { s, local -> s.toJs(local) }.collect { onState(it) } }
        return { job.cancel() }
    }
    fun selectDay(day: Int) = vm.selectDay(day)
    fun selectStop(stopId: String?) = vm.selectStop(stopId)
    /** Show times in the browser's zone instead of the trip's; display only, nothing stored. */
    fun setLocalTime(enabled: Boolean) { localTime.value = enabled }

    // Editing (W2, companion §6.2 use cases). Every call is one optimistic Firestore write.
    fun addPlaceStop(placeId: String, name: String, address: String, lat: Double, lng: Double, durationMin: Int, notes: String, day: Int, afterOrder: String?, beforeOrder: String?): String? =
        vm.addPlaceStop(placeId, name, address, lat, lng, durationMin, notes, day, afterOrder, beforeOrder)
    fun addCustomEntry(name: String, durationMin: Int, fixedStart: String?, notes: String, day: Int, afterOrder: String?, beforeOrder: String?): String? =
        vm.addCustomEntry(name, durationMin, fixedStart, notes, day, afterOrder, beforeOrder)
    fun moveStop(stopId: String, day: Int, afterOrder: String?, beforeOrder: String?) = vm.moveStop(stopId, day, afterOrder, beforeOrder)
    fun moveToDay(stopId: String, day: Int) = vm.moveToDay(stopId, day)
    fun deleteStop(stopId: String) = vm.deleteStop(stopId)
    fun setDayHours(day: Int, start: String, end: String) = vm.setDayHours(day, start, end)
    fun pinEntry(stopId: String, hhmm: String, order: String?) = vm.pinEntry(stopId, hhmm, order)
    /**
     * Drag-to-pin from the grids (design §6.6): the key between the chronological neighbours is
     * computed here so every client uses one fractional-index algorithm; [keepOrder] skips the key
     * when the entry is already between those neighbours. A different [day] moves and pins in one write.
     *
     * Complexity:
     * - **Time:** O(L) for the key of length L.
     * - **Space:** O(L).
     */
    fun pinAt(stopId: String, day: Int, hhmm: String, afterOrder: String?, beforeOrder: String?, keepOrder: Boolean) {
        val currentDay = vm.state.value.stopsByDay.entries.firstOrNull { (_, s) -> s.any { it.id == stopId } }?.key
        val order = if (keepOrder && day == currentDay) null else FractionalIndex.between(afterOrder, beforeOrder)
        if (currentDay == null || day == currentDay) vm.pinEntry(stopId, hhmm, order) else vm.pinEntryOnDay(stopId, day, hhmm, order ?: FractionalIndex.between(afterOrder, beforeOrder))
    }
    fun unpinEntry(stopId: String) = vm.unpinEntry(stopId)
    fun resizeEntry(stopId: String, durationMin: Int) = vm.resizeEntry(stopId, durationMin)
    fun updateSettings(name: String, startDate: String, endDate: String, timeZone: String, defaultDayStart: String, defaultDayEnd: String, defaultTravelMode: String) =
        vm.updateSettings(name, startDate, endDate, timeZone, defaultDayStart, defaultDayEnd, defaultTravelMode)
    fun consumeMessage() = vm.consumeMessage()
    fun showMessage(text: String) = vm.showMessage(text)

    // Collaboration (W4, companion §8.5–§8.7, §10).
    fun share() = vm.share()
    fun consumeShare() = vm.consumeShare()
    fun addToCalendar() = vm.addToCalendar()
    fun revokeCalendarLinks() = vm.revokeCalendarLinks()
    fun consumeFeedUrl() = vm.consumeFeedUrl()
    fun suggestOrder() = vm.suggestOrder()
    fun applySuggestion() = vm.applySuggestion()
    fun dismissSuggestion() = vm.dismissSuggestion()
    fun setMuted(muted: Boolean) = vm.setMuted(muted)
    /**
     * Releases the ViewModel (Firestore listeners detach once the last subscriber is gone).
     *
     * Complexity:
     * - **Time:** O(1).
     * - **Space:** O(1).
     */
    fun close() = store.clear()
}

/** Activity feed of one trip (design §10): rows already described in Kotlin (`NotificationText`). */
@JsExport
class ActivityFacade internal constructor(private val vm: ActivityFeedViewModel, private val scope: CoroutineScope) {
    private val store = ViewModelStore().apply { put("activity", vm) }
    /**
     * Complexity:
     * - **Time:** O(E) per emission for E events.
     * - **Space:** O(E).
     */
    fun subscribe(onState: (Any) -> Unit): () -> Unit = vm.state.subscribeJs(scope) { st ->
        val o = obj()
        o.loading = st.loading; o.error = st.error
        o.rows = st.rows.map { r ->
            val j = obj()
            j.id = r.event.id; j.text = r.text; j.mine = r.mine; j.type = r.event.type; j.stopId = r.event.stopId; j.day = r.event.day
            j.createdAtMs = (r.event.createdAt as? dev.gitlive.firebase.firestore.Timestamp)?.let { (it.seconds * 1000 + it.nanoseconds / 1_000_000).toDouble() }
            j
        }.toTypedArray()
        onState(o)
    }
    fun close() = store.clear()
}

/** Invite redemption (design §8.2): `join()` calls the Function; the state carries the trip to open. */
@JsExport
class JoinFacade internal constructor(private val vm: JoinTripViewModel, private val scope: CoroutineScope) {
    private val store = ViewModelStore().apply { put("join", vm) }
    /**
     * Complexity:
     * - **Time:** O(1) per emission.
     * - **Space:** O(1).
     */
    fun subscribe(onState: (Any) -> Unit): () -> Unit = vm.state.subscribeJs(scope) { st ->
        val o = obj()
        o.joining = st.joining; o.error = st.error; o.joinedTripId = st.joinedTripId; o.joinedTripName = st.joinedTripName; o.alreadyMember = st.alreadyMember
        onState(o)
    }
    fun join() = vm.join()
    fun close() = store.clear()
}

private fun <T> StateFlow<T>.subscribeJs(scope: CoroutineScope, onEach: (T) -> Unit): () -> Unit {
    val job: Job = scope.launch { collect { onEach(it) } }
    return { job.cancel() }
}

private fun obj(): dynamic = js("({})")

private fun Trip.toJs(): dynamic {
    val o = obj()
    o.id = id; o.name = name; o.startDate = startDate; o.endDate = endDate; o.timeZone = timeZone
    o.defaultDayStart = defaultDayStart; o.defaultDayEnd = defaultDayEnd; o.defaultTravelMode = defaultTravelMode
    o.ownerId = ownerId; o.memberIds = memberIds.toTypedArray(); o.pendingSync = pendingSync
    return o
}

private fun TripListUiState.toJs(): dynamic {
    val o = obj()
    o.loading = loading; o.signedIn = signedIn; o.userName = userName; o.signingIn = signingIn
    o.pushEnabled = pushEnabled; o.error = error
    o.trips = trips.map { t -> t.toJs() }.toTypedArray()
    return o
}

private fun TripDetailUiState.toJs(local: Boolean): dynamic {
    val o = obj()
    o.loading = loading; o.error = error; o.message = message; o.online = online; o.selectedDay = selectedDay; o.selectedStopId = selectedStopId
    // Raw day hours in trip time for the Day-hours dialog (never the zone-shifted schedule, cf. the Android fix in PR #6).
    o.dayHoursStart = dayHours[selectedDay]?.start ?: trip?.defaultDayStart
    o.dayHoursEnd = dayHours[selectedDay]?.end ?: trip?.defaultDayEnd
    o.dayCount = dayCount; o.canEdit = canEdit; o.isOwner = isOwner; o.pendingSync = pendingSync
    o.sharing = sharing; o.shareUrl = shareUrl; o.muted = muted
    o.calendarBusy = calendarBusy; o.feedUrl = feedUrl; o.calendarFeedEnabled = calendarFeedEnabled
    o.suggestOrderEnabled = suggestOrderEnabled; o.suggesting = suggesting
    o.suggestion = suggestion?.let { sg -> val j = obj(); j.day = sg.day; j.rationale = sg.rationale; j.warnings = sg.warnings.toTypedArray(); j.orderedStopIds = sg.orderedStopIds.toTypedArray(); j }
    o.memberCount = trip?.memberIds?.size ?: 0
    o.trip = trip?.toJs()
    val dayStops = stopsForSelectedDay
    o.stops = dayStops.map { s ->
        val j = obj()
        j.id = s.id; j.name = s.name; j.kind = s.kind; j.day = s.day; j.order = s.order; j.durationMin = s.durationMin
        j.fixedStart = s.fixedStart; j.address = s.address; j.lat = s.lat; j.lng = s.lng; j.notes = s.notes
        j
    }.toTypedArray()
    // Both modes between consecutive place stops (design §8.3), as the Android agenda shows them.
    val places = dayStops.filter { it.hasPlace }
    o.legs = places.zipWithNext().flatMap { (a, b) ->
        TravelMode.entries.mapNotNull { mode ->
            legs[Triple(a.id, b.id, mode)]?.let { leg ->
                val j = obj()
                j.from = a.id; j.to = b.id; j.mode = mode.name.lowercase(); j.seconds = leg.seconds; j.meters = leg.meters; j.error = leg.error
                j
            }
        }
    }.toTypedArray()
    // Time-zone toggle (design v1.1 §6.6): shift the computed schedule through instants, never the stored values.
    val tripZone = trip?.timeZone?.let(TimeDisplay::zone)
    val device = TimeZone.currentSystemDefault()
    val date = trip?.let { TripDays.date(it, selectedDay) }
    val differs = tripZone != null && date != null && TimeDisplay.differs(tripZone, device, LocalDateTime(date, LocalTime(12, 0)))
    fun display(s: app.tripplanner.schedule.DaySchedule) = if (local && differs && tripZone != null) TimeDisplay.shift(s, tripZone, device) else s
    o.scheduleJson = schedule?.let { app.tripplanner.schedule.ScheduleJson.encode(display(it)) }
    // Every day for the Trip view (companion §6.6); the day's stops travel with it for names and coordinates.
    o.schedules = (0 until dayCount).map { d -> scheduleFor(d)?.let { app.tripplanner.schedule.ScheduleJson.encode(display(it)) } }.toTypedArray()
    o.stopsByDay = (0 until dayCount).map { d ->
        stopsByDay[d].orEmpty().map { s ->
            val j = obj()
            j.id = s.id; j.name = s.name; j.kind = s.kind; j.day = s.day; j.order = s.order; j.durationMin = s.durationMin
            j.fixedStart = s.fixedStart; j.address = s.address; j.lat = s.lat; j.lng = s.lng; j.notes = s.notes
            j
        }.toTypedArray()
    }.toTypedArray()
    o.tripZone = trip?.timeZone; o.deviceZone = device.id; o.zoneDiffers = differs; o.localTime = local && differs
    o.zoneLabel = TimeDisplay.label(if (local && differs) device.id else trip?.timeZone.orEmpty())
    return o
}
