package app.tripplanner.ui

import androidx.compose.foundation.clickable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import app.tripplanner.schedule.DaySchedule
import app.tripplanner.schedule.Schedule
import app.tripplanner.schedule.TimedEntry
import app.tripplanner.schedule.Warning
import app.tripplanner.shared.core.util.FractionalIndex
import app.tripplanner.ui.calendar.DayView
import app.tripplanner.ui.calendar.TripView
import kotlinx.datetime.LocalTime
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import app.tripplanner.shared.feature.calendar.TimeDisplay
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tripplanner.shared.core.model.Stop
import app.tripplanner.shared.core.model.TravelLeg
import app.tripplanner.shared.core.model.TravelMode
import app.tripplanner.shared.core.model.Trip
import app.tripplanner.shared.feature.trips.TravelText
import app.tripplanner.shared.feature.stops.StopReorder
import app.tripplanner.shared.feature.trips.TripDays
import app.tripplanner.shared.feature.trips.DaySuggestion
import app.tripplanner.shared.feature.trips.TripDetailViewModel
import app.tripplanner.shared.platform.ShareSheet
import kotlinx.coroutines.flow.first
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Trip detail (design §3.1 (3-4)): day tabs filter the map and the stop list to one day; rows
 * reorder by dragging their handle (one fractional-key write on drop, [StopReorder]); the stop
 * sheet moves a stop to another day or removes it.
 *
 * Complexity:
 * - **Recomposition Time:** O(S log S) when stops change (days sorted, then flattened) and O(V)
 *   for the V rows in the viewport; O(V) per drag frame (the local list is re-emitted); O(1)
 *   when only the selection or day changes.
 * - **Composition Memory:** O(D + V) tab and row nodes plus the map's own nodes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(tripId: String, name: String, focusStopId: String? = null, onOpenActivity: (String) -> Unit, onBack: () -> Unit) {
    val vm: TripDetailViewModel = koinViewModel(key = tripId, parameters = { parametersOf(tripId) })
    val state by vm.state.collectAsStateWithLifecycle()
    val title = state.trip?.name?.takeIf { it.isNotBlank() } ?: name
    var showMenu by remember { mutableStateOf(false) }
    var showDayHours by remember { mutableStateOf(false) }
    // Agenda (list) or Day (time grid) - two views of the same computed schedule (design v1.1 §6.6).
    // Agenda (list), Day (time grid) or Trip (multi-day grid) - three views of the same computed schedule.
    var view by remember { mutableStateOf(CalendarView.AGENDA) }
    val dayView = view == CalendarView.DAY
    // Time-zone toggle (design v1.1 §6.6): display in trip time or device-local time; never stored.
    var showLocal by remember { mutableStateOf(false) }
    val deviceZone = remember { TimeZone.currentSystemDefault() }
    val tripZone = remember(state.trip?.timeZone) { state.trip?.timeZone?.let(TimeDisplay::zone) }
    val canToggleZone = tripZone != null && state.trip != null &&
        TimeDisplay.differs(tripZone, deviceZone, LocalDateTime(TripDays.date(state.trip!!, state.selectedDay) ?: LocalDate(2000, 1, 1), LocalTime(12, 0)))
    val zoned = showLocal && canToggleZone
    // A previewed suggestion (design §8.7) freezes reordering until it is applied or dismissed.
    val editing = state.canEdit && state.suggestion == null
    val display: (DaySchedule?) -> DaySchedule? = { sch -> if (zoned && sch != null) TimeDisplay.shift(sch, tripZone!!, deviceZone) else sch }
    // A time picked on a zone-shifted grid, back in trip wall-clock terms (the trip date is the anchor).
    val toTripTime: (day: Int, LocalTime) -> LocalTime = { day, t ->
        if (!zoned) t else {
            val date = TripDays.date(state.trip!!, day) ?: LocalDate(2000, 1, 1)
            listOf(0, 1, -1).map { d -> TimeDisplay.convert(LocalDateTime(LocalDate.fromEpochDays(date.toEpochDays() + d), t), deviceZone, tripZone!!) }
                .firstOrNull { it.date == date }?.time ?: TimeDisplay.convert(LocalDateTime(date, t), deviceZone, tripZone!!).time
        }
    }
    val schedule = display(state.schedule)
    val timed = remember(schedule) { schedule?.entries?.associateBy { it.input.id }.orEmpty() }
    val warningsById = remember(schedule) { schedule?.warnings?.groupBy { it.entryId }.orEmpty() }
    // Notification deep link (design §10): select the changed stop once it is loaded.
    LaunchedEffect(focusStopId) { focusStopId?.let(vm::focusStop) }
    val dayStops = state.stopsForSelectedDay
    val selected = dayStops.firstOrNull { it.id == state.selectedStopId }
        ?: state.stopsByDay.values.flatten().firstOrNull { it.id == state.selectedStopId }
    var showAddStop by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    // Local copy reordered while dragging; resets whenever the day's stops change remotely.
    var localStops by remember(dayStops) { mutableStateOf(dayStops) }
    val listState = rememberLazyListState()
    val reorderable = rememberReorderableLazyListState(listState) { from, to ->
        localStops = StopReorder.move(localStops, from.key as String, to.key as String)
    }

    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); vm.consumeMessage() }
    }
    val shareSheet: ShareSheet = koinInject()
    LaunchedEffect(state.shareUrl) {
        state.shareUrl?.let { url ->
            vm.consumeShare()
            shareSheet.share("Join my trip \"$title\" on Trip Planner: $url", "Invite to $title")
        }
    }
    // Pending for a while although online: the connection is probably stalled - offer a kick.
    var staleSync by remember { mutableStateOf(false) }
    LaunchedEffect(state.pendingSync, state.online) {
        staleSync = false
        if (state.pendingSync && state.online) {
            kotlinx.coroutines.delay(STALE_SYNC_AFTER_MS)
            staleSync = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                // material-icons is not in commonMain (Android only gets it transitively); text arrow for now.
                navigationIcon = { TextButton(onClick = onBack) { Text("←", style = MaterialTheme.typography.titleLarge) } },
                actions = {
                    if (state.isOwner) {
                        TextButton(onClick = vm::share, enabled = !state.sharing && state.online) { Text(if (state.sharing) "Sharing\u2026" else "Share") }
                    }
                    TextButton(onClick = { showMenu = true }) { Text("\u22ee", style = MaterialTheme.typography.titleLarge) }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("Activity") }, onClick = { showMenu = false; onOpenActivity(title) })
                        DropdownMenuItem(
                            text = { Text(if (state.muted) "Unmute notifications" else "Mute notifications") },
                            onClick = { showMenu = false; vm.setMuted(!state.muted) },
                        )
                        if (state.canEdit) {
                            DropdownMenuItem(text = { Text("Day hours\u2026") }, onClick = { showMenu = false; showDayHours = true })
                        }
                        if (state.canEdit && state.suggestOrderEnabled && state.stopsForSelectedDay.size >= 2) {
                            DropdownMenuItem(
                                text = { Text(if (state.suggesting) "Asking Claude\u2026" else "Suggest an order for Day ${state.selectedDay + 1}") },
                                enabled = !state.suggesting && state.online && state.suggestion == null,
                                onClick = { showMenu = false; vm.suggestOrder() },
                            )
                        }
                        if (state.calendarFeedEnabled) {
                            DropdownMenuItem(
                                text = { Text(if (state.calendarBusy) "Preparing calendar link\u2026" else "Add to my calendar\u2026") },
                                enabled = !state.calendarBusy && state.online,
                                onClick = { showMenu = false; vm.addToCalendar() },
                            )
                            DropdownMenuItem(
                                text = { Text("Remove my calendar links") },
                                enabled = !state.calendarBusy && state.online,
                                onClick = { showMenu = false; vm.revokeCalendarLinks() },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { showAddStop = true }) { Text("Add stop") }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (staleSync) {
                Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Still syncing\u2026 this is taking longer than usual", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { vm.retrySync(); staleSync = false }) { Text("Retry") }
                    }
                }
            }
            if (!state.online) {
                Surface(color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Offline \u2014 changes are saved on this device and sync when you're back online",
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            DayTabs(trip = state.trip, dayCount = state.dayCount, selected = state.selectedDay, onSelect = vm::selectDay)
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = view == CalendarView.AGENDA, onClick = { view = CalendarView.AGENDA }, label = { Text("Agenda") })
                FilterChip(selected = view == CalendarView.DAY, onClick = { view = CalendarView.DAY }, label = { Text("Day") })
                if (state.dayCount > 1) FilterChip(selected = view == CalendarView.TRIP, onClick = { view = CalendarView.TRIP }, label = { Text("Trip") })
                if (canToggleZone) {
                    FilterChip(
                        selected = showLocal,
                        onClick = { showLocal = !showLocal },
                        label = { Text("\uD83D\uDD52 " + if (showLocal) "Local" else TimeDisplay.label(tripZone!!.id)) },
                    )
                }
                schedule?.let { sch ->
                    Text(
                        "${Schedule.formatTime(sch.hours.start)} \u2013 ${Schedule.formatTime(sch.hours.end)}" + if (sch.warnings.isNotEmpty()) "  \u00b7 \u26A0 ${sch.warnings.size}" else "",
                        Modifier.align(Alignment.CenterVertically),
                        maxLines = 1,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (sch.warnings.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            state.suggestion?.let { sg -> SuggestionBanner(sg, onApply = vm::applySuggestion, onDismiss = vm::dismissSuggestion) }
            if (view == CalendarView.TRIP && state.trip != null) {
                TripView(
                    schedules = (0 until state.dayCount).map { it to display(state.scheduleFor(it)) },
                    dayLabel = { day -> dayLabel(state.trip, day) },
                    selectedDay = state.selectedDay,
                    selectedId = state.selectedStopId,
                    canEdit = editing,
                    onSelectDay = vm::selectDay,
                    onSelect = { day, id -> vm.selectDay(day); vm.selectStop(id) },
                    onPin = { day, id, start ->
                        val sch = display(state.scheduleFor(day))
                        vm.pinEntry(id, Schedule.formatTime(toTripTime(day, start)), sch?.let { chronologicalOrder(it, state.stopsByDay[day].orEmpty(), id, start) })
                    },
                    onResize = vm::resizeEntry,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (dayView && schedule != null) {
                DayView(
                    schedule = schedule,
                    selectedId = state.selectedStopId,
                    canEdit = editing,
                    onSelect = vm::selectStop,
                    onPin = { id, start -> vm.pinEntry(id, Schedule.formatTime(toTripTime(state.selectedDay, start)), chronologicalOrder(schedule, dayStops, id, start)) },
                    onResize = vm::resizeEntry,
                    modifier = Modifier.fillMaxSize(),
                )
            } else
            LazyColumn(Modifier.fillMaxSize().testTagCompat("stop_list"), state = listState) {
                item(key = "map") {
                    Box(Modifier.fillMaxWidth().height(MAP_HEIGHT)) {
                        MapView(
                            stops = localStops.filter { it.hasPlace }, // custom entries have no place (design v1.1 §7)
                            selectedStopId = state.selectedStopId,
                            onStopTapped = vm::selectStop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        when {
                            state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                            state.error != null -> Text(state.error!!, Modifier.align(Alignment.Center).padding(24.dp))
                        }
                    }
                }
                item(key = "header") {
                    Text(
                        when {
                            state.loading -> "Loading…"
                            localStops.isEmpty() -> "No stops on this day — add one"
                            else -> "${localStops.size} stop${if (localStops.size == 1) "" else "s"} · drag ≡ to reorder"
                        } + if (state.pendingSync) "  \u00b7 syncing\u2026" else "",
                        Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                items(localStops, key = { it.id }) { stop ->
                    ReorderableItem(reorderable, key = stop.id) { isDragging ->
                        // Leg from the previous *place* stop (custom entries have none): the engine's
                        // adjacency when the schedule is ready, else the local (possibly mid-drag) neighbour.
                        val index = localStops.indexOfFirst { it.id == stop.id }
                        val previous = localStops.take(index).lastOrNull { it.hasPlace }
                        val engineLeg = timed[stop.id]?.travelBefore
                        val pair = when {
                            !stop.hasPlace -> null
                            engineLeg != null -> engineLeg.fromStopId to engineLeg.toStopId
                            previous != null -> previous.id to stop.id
                            else -> null
                        }
                        Column {
                        if (pair != null) {
                            TravelConnector(legs = TravelMode.entries.map { it to state.leg(pair.first, pair.second, it) })
                        }
                        StopRow(
                            stop = stop,
                            timed = timed[stop.id],
                            warnings = warningsById[stop.id].orEmpty(),
                            position = localStops.indexOfFirst { it.id == stop.id } + 1,
                            selected = stop.id == state.selectedStopId,
                            dragging = isDragging,
                            onClick = { vm.selectStop(stop.id) },
                            onDropped = {
                                val (after, before) = StopReorder.neighbours(localStops, stop.id)
                                vm.moveStop(stop.id, state.selectedDay, after, before)
                            },
                        )
                        }
                    }
                }
                item(key = "spacer") { Spacer(Modifier.height(88.dp)) } // keep the last row clear of the FAB
            }
        }
    }

    state.feedUrl?.let { url ->
        CalendarFeedDialog(url = url, onMessage = { vm.showMessage(it) }, onDismiss = vm::consumeFeedUrl)
    }
    if (showDayHours) {
        // Day hours are stored as trip wall-clock, so the editor always works on the *unshifted*
        // schedule, whatever the display toggle shows (review on #5); the label says which zone.
        val stored = state.schedule
        DayHoursDialog(
            label = dayLabel(state.trip, state.selectedDay) + (if (zoned) " \u00b7 ${TimeDisplay.label(tripZone!!.id)} time" else ""),
            start = stored?.hours?.start?.let(Schedule::formatTime) ?: state.trip?.defaultDayStart.orEmpty(),
            end = stored?.hours?.end?.let(Schedule::formatTime) ?: state.trip?.defaultDayEnd.orEmpty(),
            onSave = { st, en -> vm.setDayHours(state.selectedDay, st, en); showDayHours = false },
            onDismiss = { showDayHours = false },
        )
    }
    if (showAddStop) {
        AddStopSheet(
            tripId = tripId,
            dayCount = state.dayCount,
            stopsByDay = state.stopsByDay,
            initialDay = state.selectedDay,
            onAdded = { id -> showAddStop = false; vm.selectStop(id) },
            neighboursAt = { day, hhmm ->
                val sch = state.scheduleFor(day)
                val t = Schedule.parseTime(hhmm)
                if (sch == null || t == null) (state.stopsByDay[day]?.maxOfOrNull { it.order } to null)
                else {
                    val stops = state.stopsByDay[day].orEmpty()
                    val orderOf = { id: String -> stops.firstOrNull { it.id == id }?.order }
                    sch.entries.lastOrNull { it.start.time <= t }?.input?.id?.let(orderOf) to sch.entries.firstOrNull { it.start.time > t }?.input?.id?.let(orderOf)
                }
            },
            onDismiss = { showAddStop = false },
        )
    } else {
        selected?.let { stop ->
            StopSheet(
                stop = stop,
                trip = state.trip,
                dayCount = state.dayCount,
                timed = timed[stop.id],
                zoneHint = if (zoned) TimeDisplay.label(tripZone!!.id) else null,
                canEdit = editing,
                onPin = { hhmm -> vm.pinEntry(stop.id, hhmm, null) },
                onUnpin = { vm.unpinEntry(stop.id) },
                onMoveToDay = { day -> vm.moveToDay(stop.id, day); vm.selectDay(day) },
                onRemove = { vm.deleteStop(stop.id) },
                onDismiss = { vm.selectStop(null) },
            )
        }
    }
}

private val MAP_HEIGHT = 320.dp
private const val STALE_SYNC_AFTER_MS = 30_000L

/** testTag is Android-only semantics today; keep the call site tidy without pulling ui-test deps. */
private fun Modifier.testTagCompat(@Suppress("UNUSED_PARAMETER") tag: String): Modifier = this

/**
 * Travel from the previous stop (design §8.3), one entry per mode: the Function's leg when
 * present, a dash when Routes had no answer, and a shimmer while the leg is still being
 * computed - never blocking.
 *
 * Complexity:
 * - **Recomposition Time:** O(M) for the M modes; the shimmer is one shared infinite animation.
 * - **Composition Memory:** O(M).
 */
@Composable
private fun TravelConnector(legs: List<Pair<TravelMode, TravelLeg?>>) {
    val transition = rememberInfiniteTransition(label = "leg-shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.25f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "alpha",
    )
    Row(
        Modifier.fillMaxWidth().padding(start = 52.dp, end = 16.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        legs.forEach { (mode, leg) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (mode == TravelMode.DRIVE) "\uD83D\uDE97" else "\uD83D\uDEB6", style = MaterialTheme.typography.bodySmall)
                if (leg == null) {
                    Box(
                        Modifier.height(12.dp).width(72.dp).clip(RoundedCornerShape(6.dp)).alpha(alpha)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                } else {
                    Text(TravelText.format(leg), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/**
 * Order key that keeps the list chronological after a pin (design v1.1 §6.6): the entry lands
 * between the entries whose starts bracket the new time, or stays where it is if that is already
 * the case.
 *
 * Complexity:
 * - **Time:** O(E) over the day's E entries.
 * - **Space:** O(E).
 */
private fun chronologicalOrder(schedule: DaySchedule, stops: List<Stop>, stopId: String, start: LocalTime): String? {
    val others = schedule.entries.filter { it.input.id != stopId }
    val before = others.lastOrNull { it.start.time <= start }
    val after = others.firstOrNull { it.start.time > start }
    val current = schedule.entries.indexOfFirst { it.input.id == stopId }
    val beforeIdx = before?.let { schedule.entries.indexOf(it) } ?: -1
    if (beforeIdx == current - 1) return null // already in place
    val orderOf = { id: String -> stops.firstOrNull { it.id == id }?.order }
    return FractionalIndex.between(before?.input?.id?.let(orderOf), after?.input?.id?.let(orderOf))
}

/**
 * Per-day start and end (design v1.1 §8.4 step 4).
 *
 * Complexity:
 * - **Recomposition Time:** O(1) per keystroke.
 * - **Composition Memory:** O(1).
 */
@Composable
private fun DayHoursDialog(label: String, start: String, end: String, onSave: (String, String) -> Unit, onDismiss: () -> Unit) {
    var s by remember { mutableStateOf(start) }
    var e by remember { mutableStateOf(end) }
    val valid = Schedule.parseTime(s) != null && Schedule.parseTime(e) != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Day hours \u00b7 $label") },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = s, onValueChange = { s = it.take(5) }, label = { Text("Start") }, placeholder = { Text("09:00") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(value = e, onValueChange = { e = it.take(5) }, label = { Text("End") }, placeholder = { Text("21:00") }, singleLine = true, modifier = Modifier.weight(1f))
            }
        },
        confirmButton = { Button(onClick = { onSave(s, e) }, enabled = valid) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Preview strip for a Claude suggestion (design §8.7): the calendar below already shows the
 * proposed order; Apply writes it, Dismiss restores the stored order.
 *
 * Complexity:
 * - **Recomposition Time:** O(W) for W warnings.
 * - **Composition Memory:** O(W).
 */
@Composable
private fun SuggestionBanner(sg: DaySuggestion, onApply: () -> Unit, onDismiss: () -> Unit) {
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Suggested order for Day ${sg.day + 1} \u00b7 preview", style = MaterialTheme.typography.titleSmall)
            if (sg.rationale.isNotBlank()) Text(sg.rationale, style = MaterialTheme.typography.bodySmall)
            sg.warnings.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Dismiss") }
                Button(onClick = onApply) { Text("Apply") }
            }
        }
    }
}

/**
 * "Add to my calendar" result (design §8.6): the private feed link, copyable or opened as
 * `webcal://` so the platform's calendar app subscribes to it.
 *
 * Complexity:
 * - **Recomposition Time:** O(1).
 * - **Composition Memory:** O(1).
 */
@Composable
private fun CalendarFeedDialog(url: String, onMessage: (String) -> Unit, onDismiss: () -> Unit) {
    @Suppress("DEPRECATION") val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to my calendar") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Subscribe your calendar app to this link. It is a read-only copy of the itinerary and can lag the app by up to an hour.")
                Text(url, style = MaterialTheme.typography.bodySmall)
                Text("Anyone with the link can read the itinerary. \"Remove my calendar links\" in the menu revokes it.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(onClick = {
                val webcal = "webcal://" + url.removePrefix("https://")
                runCatching { uriHandler.openUri(webcal) }
                    .recoverCatching { uriHandler.openUri(url) }
                    .onFailure { onMessage("No calendar app could open the link; copy it instead") }
            }) { Text("Open") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { clipboard.setText(AnnotatedString(url)); onMessage("Calendar link copied") }) { Text("Copy link") }
                TextButton(onClick = onDismiss) { Text("Done") }
            }
        },
    )
}

/**
 * Day tabs labelled with the calendar date when the trip document is available.
 *
 * Complexity:
 * - **Recomposition Time:** O(D) for D days.
 * - **Composition Memory:** O(D).
 */
@Composable
private fun DayTabs(trip: Trip?, dayCount: Int, selected: Int, onSelect: (Int) -> Unit) {
    val index = selected.coerceIn(0, (dayCount - 1).coerceAtLeast(0))
    ScrollableTabRow(
        selectedTabIndex = index,
        edgePadding = 8.dp,
        // A deep link can select a later day in the same frame the trip document (and its day
        // count) arrives; the row's positions still belong to the previous layout, so guard the index.
        indicator = { positions -> if (index < positions.size) TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(positions[index])) },
    ) {
        repeat(dayCount) { day ->
            Tab(
                selected = day == selected,
                onClick = { onSelect(day) },
                text = { Text(dayLabel(trip, day)) },
            )
        }
    }
}

/**
 * Complexity:
 * - **Time:** O(1).
 * - **Space:** O(1).
 */
private fun dayLabel(trip: Trip?, day: Int): String {
    val date = trip?.let { TripDays.date(it, day) } ?: return "Day ${day + 1}"
    val month = date.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
    return "Day ${day + 1} · $month ${date.day}"
}

/**
 * One stop in the day's list with a drag handle; highlighted when selected, raised while dragging.
 *
 * Complexity:
 * - **Recomposition Time:** O(1).
 * - **Composition Memory:** O(1).
 */
@Composable
private fun ReorderableCollectionItemScope.StopRow(
    stop: Stop,
    timed: TimedEntry?,
    warnings: List<Warning>,
    position: Int,
    selected: Boolean,
    dragging: Boolean,
    onClick: () -> Unit,
    onDropped: () -> Unit,
) {
    Surface(shadowElevation = if (dragging) 6.dp else 0.dp) {
        ListItem(
            modifier = Modifier.clickable(onClick = onClick),
            leadingContent = { Text("$position", style = MaterialTheme.typography.titleMedium) },
            headlineContent = { Text(stop.name) },
            supportingContent = {
                Column {
                    timed?.let { t ->
                        Text(
                            (if (t.pinned) "\uD83D\uDCCC " else "") + Schedule.formatTime(t.start.time) + " \u2013 " + Schedule.formatTime(t.end.time) +
                                (if (t.gapBeforeMin > 0) "  \u00b7 ${t.gapBeforeMin} min free before" else ""),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    if (stop.kind != Stop.KIND_CUSTOM || stop.address.isNotBlank()) Text(stop.address + if (stop.pendingSync) "  \u00b7 syncing\u2026" else "")
                    warnings.forEach { w ->
                        Text(
                            when (w) {
                                is Warning.LateArrival -> "\u26A0 ${w.minutes} min late for the pinned time"
                                is Warning.Overlap -> "\u26A0 overlaps the previous entry by ${w.minutes} min"
                                is Warning.DayOverrun -> "\u26A0 runs ${w.minutes} min past the day's end"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            trailingContent = {
                Text(
                    "≡",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 8.dp).draggableHandle(onDragStopped = onDropped),
                )
            },
            colors = if (selected) {
                ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            } else {
                ListItemDefaults.colors()
            },
        )
    }
}

/**
 * Stop details over the map: notes (local until Phase 1 persists them), move to another day,
 * remove.
 *
 * Complexity:
 * - **Recomposition Time:** O(D) for the D day chips; O(1) per keystroke.
 * - **Composition Memory:** O(D + N) for the chips and the N-character note.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StopSheet(
    stop: Stop,
    trip: Trip?,
    dayCount: Int,
    timed: TimedEntry?,
    /** When the screen shows local time, the pin field still takes trip time: say which zone. */
    zoneHint: String?,
    canEdit: Boolean,
    onPin: (String) -> Unit,
    onUnpin: () -> Unit,
    onMoveToDay: (Int) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var note by remember(stop.id) { mutableStateOf(stop.notes) }

    // Perf signpost from the sheet entering composition until it is fully expanded.
    LaunchedEffect(stop.id) { snapshotFlow { sheetState.currentValue }.first { it == SheetValue.Expanded } }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).imePadding()) {
            Text(stop.name, style = MaterialTheme.typography.titleLarge)
            Text(stop.address, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${dayLabel(trip, stop.day)} · ${stop.durationMin} min" +
                    (timed?.let { "  \u00b7 ${Schedule.formatTime(it.start.time)} \u2013 ${Schedule.formatTime(it.end.time)}" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
            )
            if (canEdit) {
                Spacer(Modifier.height(8.dp))
                var pinText by remember(stop.id, stop.fixedStart) { mutableStateOf(stop.fixedStart ?: timed?.let { Schedule.formatTime(it.start.time) } ?: "") }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = pinText,
                        onValueChange = { pinText = it.take(5) },
                        label = { Text((if (stop.fixedStart != null) "Pinned at" else "Pin to time") + (zoneHint?.let { " \u00b7 $it" } ?: "")) },
                        placeholder = { Text("HH:mm") },
                        singleLine = true,
                        isError = pinText.isNotBlank() && Schedule.parseTime(pinText) == null,
                        modifier = Modifier.width(140.dp),
                    )
                    TextButton(onClick = { onPin(pinText) }, enabled = Schedule.parseTime(pinText) != null && pinText != stop.fixedStart) { Text("Pin") }
                    if (stop.fixedStart != null) TextButton(onClick = onUnpin) { Text("Unpin") }
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            if (dayCount > 1) {
                Spacer(Modifier.height(16.dp))
                Text("Move to", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(dayCount) { day ->
                        FilterChip(
                            selected = day == stop.day,
                            enabled = day != stop.day,
                            onClick = { onMoveToDay(day) },
                            label = { Text("Day ${day + 1}") },
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onRemove) { Text("Remove stop", color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Which of the three calendar views (design v1.1 §6.6) the trip screen shows. */
private enum class CalendarView { AGENDA, DAY, TRIP }
