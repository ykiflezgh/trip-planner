package app.tripplanner.ui.calendar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.tripplanner.schedule.DaySchedule
import app.tripplanner.schedule.Schedule
import app.tripplanner.schedule.TimedEntry
import app.tripplanner.schedule.Warning
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.math.roundToInt

/**
 * Day view (design v1.1 §6.6): a vertical time grid (one column of [TimeGrid]). Blocks are sized by duration, travel legs
 * are hatched between them, free time stays empty, warnings sit on the affected block. Editors
 * long-press and drag a block to pin it (snapped to 15 min) and drag the bottom edge to resize;
 * viewers get the same grid read-only. Times are wall-clock in the trip zone.
 *
 * Complexity:
 * - **Recomposition Time:** O(E + H) for the E entries and H hour lines; a drag recomposes
 *   only the moving block.
 * - **Composition Memory:** O(E + H).
 */
@Composable
fun DayView(
    schedule: DaySchedule,
    selectedId: String?,
    canEdit: Boolean,
    onSelect: (String) -> Unit,
    onPin: (stopId: String, start: LocalTime) -> Unit,
    onResize: (stopId: String, durationMin: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val range = timeRange(listOf(schedule))
    Column(modifier.verticalScroll(rememberScrollState())) {
        TimeGrid(range, modifier = Modifier.fillMaxWidth()) {
            TimeGridColumn(
                schedule = schedule, range = range, selectedId = selectedId, canEdit = canEdit, compact = false,
                onSelect = onSelect, onPin = onPin, onResize = onResize, modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Trip view (design v1.1 §6.6): up to [DAYS_PER_PAGE] day columns on one shared time axis,
 * horizontally paged for longer trips, the same blocks as Day view. Editable (drag-to-pin,
 * resize) on tablets; read-only on phones, where a tap selects the event and its day instead.
 *
 * Complexity:
 * - **Recomposition Time:** O(D · E + H) for the D visible days, their E entries and H hour lines.
 * - **Composition Memory:** O(D · E + H) for the visible page only (the pager composes one page).
 */
@Composable
fun TripView(
    schedules: List<Pair<Int, DaySchedule?>>,
    dayLabel: (Int) -> String,
    selectedDay: Int,
    selectedId: String?,
    canEdit: Boolean,
    onSelectDay: (Int) -> Unit,
    onSelect: (day: Int, stopId: String) -> Unit,
    onPin: (day: Int, stopId: String, start: LocalTime) -> Unit,
    onResize: (stopId: String, durationMin: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (schedules.isEmpty()) return
    val pages = schedules.chunked(DAYS_PER_PAGE)
    val pager = rememberPagerState(initialPage = (selectedDay / DAYS_PER_PAGE).coerceIn(0, pages.lastIndex)) { pages.size }
    BoxWithConstraints(modifier) {
        val tablet = maxWidth >= TABLET_MIN_WIDTH
        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            val days = pages[page]
            val range = timeRange(days.mapNotNull { it.second })
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(start = LABEL_WIDTH)) {
                    days.forEach { (day, _) ->
                        Text(
                            dayLabel(day),
                            Modifier.weight(1f).clickable { onSelectDay(day) }.padding(vertical = 6.dp, horizontal = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (day == selectedDay) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    TimeGrid(range, modifier = Modifier.fillMaxWidth()) {
                        days.forEach { (day, schedule) ->
                            if (schedule == null) { Box(Modifier.weight(1f)); return@forEach }
                            TimeGridColumn(
                                schedule = schedule, range = range, selectedId = selectedId,
                                canEdit = canEdit && tablet, compact = true,
                                onSelect = { id -> onSelect(day, id) },
                                onPin = { id, start -> onPin(day, id, start) },
                                onResize = onResize,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

private const val DAYS_PER_PAGE = 7
private val TABLET_MIN_WIDTH = 600.dp
private val HOUR_HEIGHT = 64.dp
private val LABEL_WIDTH = 48.dp

/** Vertical extent of a grid in minutes of the day, shared by every column drawn together. */
private data class TimeRange(val firstMinute: Int, val lastMinute: Int) {
    val minuteDp: Dp get() = HOUR_HEIGHT / 60f
    val height: Dp get() = HOUR_HEIGHT * ((lastMinute - firstMinute) / 60f)
    fun y(minuteOfDay: Int): Dp = minuteDp * (minuteOfDay - firstMinute).toFloat()
}

/**
 * Whole hours spanning every schedule's day hours and entries.
 *
 * Complexity:
 * - **Time:** O(D · E).
 * - **Space:** O(1).
 */
private fun timeRange(schedules: List<DaySchedule>): TimeRange {
    val first = schedules.minOfOrNull { s -> minOf(s.hours.start.toMinutes(), s.entries.minOfOrNull { it.start.minuteOfDay() } ?: Int.MAX_VALUE) } ?: 9 * 60
    val last = schedules.maxOfOrNull { s -> maxOf(s.hours.end.toMinutes(), s.entries.maxOfOrNull { it.end.minuteOfDay() } ?: 0) } ?: 21 * 60
    val firstMinute = (first / 60) * 60
    return TimeRange(firstMinute, (((last + 59) / 60) * 60).coerceAtLeast(firstMinute + 60))
}

/**
 * Hour labels and lines behind [columns], which share the axis and split the remaining width.
 *
 * Complexity:
 * - **Recomposition Time:** O(H) for the H hour lines plus the columns.
 * - **Composition Memory:** O(H).
 */
@Composable
private fun TimeGrid(range: TimeRange, modifier: Modifier = Modifier, columns: @Composable RowScope.() -> Unit) {
    Box(modifier.height(range.height + 8.dp).padding(top = 8.dp)) {
        for (m in range.firstMinute..range.lastMinute step 60) {
            val y = range.y(m)
            Text(
                Schedule.formatTime(LocalTime(m / 60 % 24, 0)),
                Modifier.offset(y = y - 8.dp).width(LABEL_WIDTH),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(Modifier.offset(x = LABEL_WIDTH, y = y).fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
        }
        Row(Modifier.fillMaxSize().padding(start = LABEL_WIDTH), content = columns)
    }
}

/**
 * One day's band, legs and blocks on the shared axis.
 *
 * Complexity:
 * - **Recomposition Time:** O(E) for the E entries; a drag recomposes only the moving block.
 * - **Composition Memory:** O(E).
 */
@Composable
private fun TimeGridColumn(
    schedule: DaySchedule,
    range: TimeRange,
    selectedId: String?,
    canEdit: Boolean,
    compact: Boolean,
    onSelect: (String) -> Unit,
    onPin: (stopId: String, start: LocalTime) -> Unit,
    onResize: (stopId: String, durationMin: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier.offset(y = range.y(schedule.hours.start.toMinutes()))
                .fillMaxWidth().height(range.minuteDp * (schedule.hours.end.toMinutes() - schedule.hours.start.toMinutes()).toFloat())
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
        )
        schedule.entries.forEach { entry ->
            entry.travelBefore?.let { leg ->
                val h = range.minuteDp * (leg.end.minuteOfDay() - leg.start.minuteOfDay()).toFloat()
                if (h > 0.dp) HatchedLeg(Modifier.offset(x = 4.dp, y = range.y(leg.start.minuteOfDay())).fillMaxWidth().height(h), pending = leg.pending)
            }
            EntryBlock(
                entry = entry,
                warnings = schedule.warnings.filter { it.entryId == entry.input.id },
                selected = entry.input.id == selectedId,
                canEdit = canEdit,
                compact = compact,
                minuteDp = range.minuteDp,
                top = range.y(entry.start.minuteOfDay()),
                leftInset = 4.dp,
                onSelect = { onSelect(entry.input.id) },
                onPin = { start -> onPin(entry.input.id, start) },
                onResize = { min -> onResize(entry.input.id, min) },
            )
        }
    }
}

/**
 * Complexity:
 * - **Recomposition Time:** O(1) plus the drag delta while moving.
 * - **Composition Memory:** O(1).
 */
@Composable
private fun EntryBlock(
    entry: TimedEntry,
    warnings: List<Warning>,
    selected: Boolean,
    canEdit: Boolean,
    compact: Boolean,
    minuteDp: Dp,
    top: Dp,
    leftInset: Dp,
    onSelect: () -> Unit,
    onPin: (LocalTime) -> Unit,
    onResize: (Int) -> Unit,
) {
    val density = LocalDensity.current
    val minutePx = with(density) { minuteDp.toPx() }
    var dragMinutes by remember(entry.input.id) { mutableFloatStateOf(0f) }
    var resizeMinutes by remember(entry.input.id) { mutableFloatStateOf(0f) }
    val durationMin = entry.start.minutesUntil(entry.end)
    val height = minuteDp * (durationMin + resizeMinutes).coerceAtLeast(Schedule.MIN_DURATION_MIN.toFloat())
    val moving = dragMinutes != 0f
    val container = when {
        moving -> MaterialTheme.colorScheme.tertiaryContainer
        selected -> MaterialTheme.colorScheme.secondaryContainer
        entry.input.kind == app.tripplanner.schedule.EntryKind.CUSTOM -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    Surface(
        color = container,
        shadowElevation = if (moving) 6.dp else 1.dp,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .offset(x = leftInset, y = top)
            .offset { IntOffset(0, (dragMinutes * minutePx).roundToInt()) }
            .fillMaxWidth()
            .padding(end = if (compact) 4.dp else 12.dp)
            .height(height)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onSelect)
            .then(
                if (canEdit) Modifier.pointerInput(entry.input.id, entry.start) {
                    detectDragGesturesAfterLongPress(
                        onDrag = { change, delta -> change.consume(); dragMinutes += delta.y / minutePx },
                        onDragEnd = {
                            val snapped = ((entry.start.minuteOfDay() + dragMinutes) / 15f).roundToInt() * 15
                            dragMinutes = 0f
                            onPin(LocalTime((snapped.coerceIn(0, 24 * 60 - 15)) / 60, snapped.coerceIn(0, 24 * 60 - 15) % 60))
                        },
                        onDragCancel = { dragMinutes = 0f },
                    )
                } else Modifier,
            ),
    ) {
        Box(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = if (compact) 4.dp else 8.dp, vertical = if (compact) 2.dp else 4.dp)) {
                val shownStart = if (moving) LocalTime.fromMinutes(((entry.start.minuteOfDay() + dragMinutes) / 15f).roundToInt() * 15) else entry.start.time
                Text(
                    (if (entry.pinned || moving) "📌 " else "") + Schedule.formatTime(shownStart) +
                        if (compact) "" else " – " + Schedule.formatTime(LocalTime.fromMinutes(shownStart.toMinutes() + durationMin + resizeMinutes.roundToInt())),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
                Text(entry.input.name, style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium, maxLines = if (height < 40.dp || compact) 1 else 2, overflow = TextOverflow.Ellipsis)
                if (compact && warnings.isNotEmpty()) Text("⚠", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                if (!compact) warnings.forEach { w ->
                    Text(
                        when (w) {
                            is Warning.LateArrival -> "⚠ ${w.minutes} min late"
                            is Warning.Overlap -> "⚠ overlaps by ${w.minutes} min"
                            is Warning.DayOverrun -> "⚠ ${w.minutes} min past day end"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            if (canEdit) {
                Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(14.dp)
                        .pointerInput(entry.input.id, durationMin) {
                            detectDragGestures(
                                onDrag = { change, delta -> change.consume(); resizeMinutes += delta.y / minutePx },
                                onDragEnd = {
                                    val newDuration = ((durationMin + resizeMinutes) / 5f).roundToInt() * 5
                                    resizeMinutes = 0f
                                    if (newDuration != durationMin) onResize(newDuration)
                                },
                                onDragCancel = { resizeMinutes = 0f },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.width(32.dp).height(3.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f), RoundedCornerShape(2.dp)))
                }
            }
        }
    }
}

/**
 * Diagonal hatching for a travel leg; lighter while the leg is still being computed.
 *
 * Complexity:
 * - **Recomposition Time:** O(W/8) lines for a W-px wide leg.
 * - **Composition Memory:** O(1).
 */
@Composable
private fun HatchedLeg(modifier: Modifier, pending: Boolean) {
    val color = MaterialTheme.colorScheme.outline.copy(alpha = if (pending) 0.25f else 0.6f)
    Canvas(modifier) {
        var x = -size.height
        while (x < size.width) {
            drawLine(color, Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = 2f)
            x += 8.dp.toPx()
        }
    }
}

private fun LocalTime.toMinutes(): Int = hour * 60 + minute
private fun LocalDateTime.minuteOfDay(): Int = hour * 60 + minute
private fun LocalDateTime.minutesUntil(other: LocalDateTime): Int = ((other.date.toEpochDays() - date.toEpochDays()).toInt()) * 24 * 60 + other.minuteOfDay() - minuteOfDay()
private fun LocalTime.Companion.fromMinutes(m: Int): LocalTime { val c = ((m % (24 * 60)) + 24 * 60) % (24 * 60); return LocalTime(c / 60, c % 60) }

@Suppress("unused")
private val unusedColor: Color = Color.Unspecified
