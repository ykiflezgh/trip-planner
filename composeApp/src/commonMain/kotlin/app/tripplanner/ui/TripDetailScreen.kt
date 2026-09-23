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
import app.tripplanner.schedule.TimedEvent
import app.tripplanner.schedule.Warning
import app.tripplanner.shared.core.util.FractionalIndex
import app.tripplanner.ui.calendar.DayView
import kotlinx.datetime.LocalTime
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import app.tripplanner.shared.core.model.Event
import app.tripplanner.shared.core.model.TravelLeg
import app.tripplanner.shared.core.model.TravelMode
import app.tripplanner.shared.core.model.Trip
import app.tripplanner.shared.feature.trips.TravelText
import app.tripplanner.shared.feature.events.EventReorder
import app.tripplanner.shared.feature.trips.TripDays
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
 * Trip detail (design §3.1 (3-4)): day tabs filter the map and the event list to one day; rows
 * reorder by dragging their handle (one fractional-key write on drop, [EventReorder]); the event
 * sheet moves a event to another day or removes it.
 *
 * Complexity:
 * - **Recomposition Time:** O(S log S) when stops change (days sorted, then flattened) and O(V)
 *   for the V rows in the viewport; O(V) per drag frame (the local list is re-emitted); O(1)
 *   when only the selection or day changes.
 * - **Composition Memory:** O(D + V) tab and row nodes plus the map's own nodes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(tripId: String, name: String, focusEventId: String? = null, onOpenActivity: (String) -> Unit, onBack: () -> Unit) {
    val vm: TripDetailViewModel = koinViewModel(key = tripId, parameters = { parametersOf(tripId) })
    val state by vm.state.collectAsStateWithLifecycle()
    val title = state.trip?.name?.takeIf { it.isNotBlank() } ?: name
    var showMenu by remember { mutableStateOf(false) }
    var showDayHours by remember { mutableStateOf(false) }
    // Agenda (list) or Day (time grid) - two views of the same computed schedule (design v1.1 §6.6).
    var dayView by remember { mutableStateOf(false) }
    val schedule = state.schedule
    val timed = remember(schedule) { schedule?.events?.associateBy { it.input.id }.orEmpty() }
    val warningsById = remember(schedule) { schedule?.warnings?.groupBy { it.eventId }.orEmpty() }
    // Notification deep link (design §10): select the changed event once it is loaded.
    LaunchedEffect(focusEventId) { focusEventId?.let(vm::focusEvent) }
    val dayEvents = state.eventsForSelectedDay
    val selected = dayEvents.firstOrNull { it.id == state.selectedEventId }
        ?: state.eventsByDay.values.flatten().firstOrNull { it.id == state.selectedEventId }
    var showAddEvent by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    // Local copy reordered while dragging; resets whenever the day's stops change remotely.
    var localEvents by remember(dayEvents) { mutableStateOf(dayEvents) }
    val listState = rememberLazyListState()
    val reorderable = rememberReorderableLazyListState(listState) { from, to ->
        localEvents = EventReorder.move(localEvents, from.key as String, to.key as String)
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
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { showAddEvent = true }) { Text("Add event") }
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
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !dayView, onClick = { dayView = false }, label = { Text("Agenda") })
                FilterChip(selected = dayView, onClick = { dayView = true }, label = { Text("Day") })
                schedule?.let { sch ->
                    Text(
                        "${Schedule.formatTime(sch.hours.start)} \u2013 ${Schedule.formatTime(sch.hours.end)}" + if (sch.warnings.isNotEmpty()) "  \u00b7 \u26A0 ${sch.warnings.size}" else "",
                        Modifier.align(Alignment.CenterVertically),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (sch.warnings.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (dayView && schedule != null) {
                DayView(
                    schedule = schedule,
                    selectedId = state.selectedEventId,
                    canEdit = state.canEdit,
                    onSelect = vm::selectEvent,
                    onPin = { id, start -> vm.pinEvent(id, Schedule.formatTime(start), chronologicalOrder(schedule, dayEvents, id, start)) },
                    onResize = vm::resizeEvent,
                    modifier = Modifier.fillMaxSize(),
                )
            } else
            LazyColumn(Modifier.fillMaxSize().testTagCompat("event_list"), state = listState) {
                item(key = "map") {
                    Box(Modifier.fillMaxWidth().height(MAP_HEIGHT)) {
                        MapView(
                            events = localEvents.filter { it.hasStop }, // markers are the stops events contain (design v1.2 §3.1)
                            selectedEventId = state.selectedEventId,
                            onStopTapped = vm::selectEvent,
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
                            localEvents.isEmpty() -> "No events on this day — add one"
                            else -> "${localEvents.size} event${if (localEvents.size == 1) "" else "s"} · drag ≡ to reorder"
                        } + if (state.pendingSync) "  \u00b7 syncing\u2026" else "",
                        Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                items(localEvents, key = { it.id }) { event ->
                    ReorderableItem(reorderable, key = event.id) { isDragging ->
                        // Leg from the previous event *with a stop*: the engine's adjacency when the schedule
                        // is ready, else the local (possibly mid-drag) neighbour.
                        val index = localEvents.indexOfFirst { it.id == event.id }
                        val previous = localEvents.take(index).lastOrNull { it.hasStop }
                        val engineLeg = timed[event.id]?.travelBefore
                        val pair = when {
                            !event.hasStop -> null
                            engineLeg != null -> engineLeg.fromStopId to engineLeg.toStopId
                            previous != null -> previous.id to event.id
                            else -> null
                        }
                        Column {
                        if (pair != null) {
                            TravelConnector(legs = TravelMode.entries.map { it to state.leg(pair.first, pair.second, it) })
                        }
                        EventRow(
                            event = event,
                            timed = timed[event.id],
                            warnings = warningsById[event.id].orEmpty(),
                            position = localEvents.indexOfFirst { it.id == event.id } + 1,
                            selected = event.id == state.selectedEventId,
                            dragging = isDragging,
                            onClick = { vm.selectEvent(event.id) },
                            onDropped = {
                                val (after, before) = EventReorder.neighbours(localEvents, event.id)
                                vm.moveEvent(event.id, state.selectedDay, after, before)
                            },
                        )
                        }
                    }
                }
                item(key = "spacer") { Spacer(Modifier.height(88.dp)) } // keep the last row clear of the FAB
            }
        }
    }

    if (showDayHours) {
        DayHoursDialog(
            label = dayLabel(state.trip, state.selectedDay),
            start = schedule?.hours?.start?.let(Schedule::formatTime) ?: state.trip?.defaultDayStart.orEmpty(),
            end = schedule?.hours?.end?.let(Schedule::formatTime) ?: state.trip?.defaultDayEnd.orEmpty(),
            onSave = { st, en -> vm.setDayHours(state.selectedDay, st, en); showDayHours = false },
            onDismiss = { showDayHours = false },
        )
    }
    if (showAddEvent) {
        AddEventSheet(
            tripId = tripId,
            dayCount = state.dayCount,
            eventsByDay = state.eventsByDay,
            initialDay = state.selectedDay,
            onAdded = { id -> showAddEvent = false; vm.selectEvent(id) },
            neighboursAt = { day, hhmm ->
                val sch = state.scheduleFor(day)
                val t = Schedule.parseTime(hhmm)
                if (sch == null || t == null) (state.eventsByDay[day]?.maxOfOrNull { it.order } to null)
                else {
                    val events = state.eventsByDay[day].orEmpty()
                    val orderOf = { id: String -> events.firstOrNull { it.id == id }?.order }
                    sch.events.lastOrNull { it.start.time <= t }?.input?.id?.let(orderOf) to sch.events.firstOrNull { it.start.time > t }?.input?.id?.let(orderOf)
                }
            },
            onDismiss = { showAddEvent = false },
        )
    } else {
        selected?.let { event ->
            EventSheet(
                event = event,
                trip = state.trip,
                dayCount = state.dayCount,
                timed = timed[event.id],
                canEdit = state.canEdit,
                onPin = { hhmm -> vm.pinEvent(event.id, hhmm, null) },
                onUnpin = { vm.unpinEvent(event.id) },
                onMoveToDay = { day -> vm.moveToDay(event.id, day); vm.selectDay(day) },
                onRemove = { vm.deleteEvent(event.id) },
                onDismiss = { vm.selectEvent(null) },
            )
        }
    }
}

private val MAP_HEIGHT = 320.dp
private const val STALE_SYNC_AFTER_MS = 30_000L

/** testTag is Android-only semantics today; keep the call site tidy without pulling ui-test deps. */
private fun Modifier.testTagCompat(@Suppress("UNUSED_PARAMETER") tag: String): Modifier = this

/**
 * Travel from the previous event (design §8.3), one entry per mode: the Function's leg when
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
private fun chronologicalOrder(schedule: DaySchedule, events: List<Event>, eventId: String, start: LocalTime): String? {
    val others = schedule.events.filter { it.input.id != eventId }
    val before = others.lastOrNull { it.start.time <= start }
    val after = others.firstOrNull { it.start.time > start }
    val current = schedule.events.indexOfFirst { it.input.id == eventId }
    val beforeIdx = before?.let { schedule.events.indexOf(it) } ?: -1
    if (beforeIdx == current - 1) return null // already in place
    val orderOf = { id: String -> events.firstOrNull { it.id == id }?.order }
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
 * Day tabs labelled with the calendar date when the trip document is available.
 *
 * Complexity:
 * - **Recomposition Time:** O(D) for D days.
 * - **Composition Memory:** O(D).
 */
@Composable
private fun DayTabs(trip: Trip?, dayCount: Int, selected: Int, onSelect: (Int) -> Unit) {
    ScrollableTabRow(selectedTabIndex = selected.coerceIn(0, (dayCount - 1).coerceAtLeast(0)), edgePadding = 8.dp) {
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
 * One event in the day's list with a drag handle; highlighted when selected, raised while dragging.
 *
 * Complexity:
 * - **Recomposition Time:** O(1).
 * - **Composition Memory:** O(1).
 */
@Composable
private fun ReorderableCollectionItemScope.EventRow(
    event: Event,
    timed: TimedEvent?,
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
            headlineContent = { Text(event.title) },
            supportingContent = {
                Column {
                    timed?.let { t ->
                        Text(
                            (if (t.pinned) "\uD83D\uDCCC " else "") + Schedule.formatTime(t.start.time) + " \u2013 " + Schedule.formatTime(t.end.time) +
                                (if (t.gapBeforeMin > 0) "  \u00b7 ${t.gapBeforeMin} min free before" else ""),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    event.stop?.let { Text(it.address + if (event.pendingSync) "  \u00b7 syncing\u2026" else "") } ?: if (event.pendingSync) Text("syncing\u2026") else Unit
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
 * Event details over the map: notes (local until Phase 1 persists them), move to another day,
 * remove.
 *
 * Complexity:
 * - **Recomposition Time:** O(D) for the D day chips; O(1) per keystroke.
 * - **Composition Memory:** O(D + N) for the chips and the N-character note.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventSheet(
    event: Event,
    trip: Trip?,
    dayCount: Int,
    timed: TimedEvent?,
    canEdit: Boolean,
    onPin: (String) -> Unit,
    onUnpin: () -> Unit,
    onMoveToDay: (Int) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var note by remember(event.id) { mutableStateOf(event.notes) }

    // Perf signpost from the sheet entering composition until it is fully expanded.
    LaunchedEffect(event.id) { snapshotFlow { sheetState.currentValue }.first { it == SheetValue.Expanded } }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).imePadding()) {
            Text(event.title, style = MaterialTheme.typography.titleLarge)
            event.stop?.let { Text(it.address, style = MaterialTheme.typography.bodyMedium) }
            Text(
                "${dayLabel(trip, event.day)} · ${event.durationMin} min" +
                    (timed?.let { "  \u00b7 ${Schedule.formatTime(it.start.time)} \u2013 ${Schedule.formatTime(it.end.time)}" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
            )
            if (canEdit) {
                Spacer(Modifier.height(8.dp))
                var pinText by remember(event.id, event.fixedStart) { mutableStateOf(event.fixedStart ?: timed?.let { Schedule.formatTime(it.start.time) } ?: "") }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = pinText,
                        onValueChange = { pinText = it.take(5) },
                        label = { Text(if (event.fixedStart != null) "Pinned at" else "Pin to time") },
                        placeholder = { Text("HH:mm") },
                        singleLine = true,
                        isError = pinText.isNotBlank() && Schedule.parseTime(pinText) == null,
                        modifier = Modifier.width(140.dp),
                    )
                    TextButton(onClick = { onPin(pinText) }, enabled = Schedule.parseTime(pinText) != null && pinText != event.fixedStart) { Text("Pin") }
                    if (event.fixedStart != null) TextButton(onClick = onUnpin) { Text("Unpin") }
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
                            selected = day == event.day,
                            enabled = day != event.day,
                            onClick = { onMoveToDay(day) },
                            label = { Text("Day ${day + 1}") },
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onRemove) { Text("Remove event", color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(24.dp))
        }
    }
}
