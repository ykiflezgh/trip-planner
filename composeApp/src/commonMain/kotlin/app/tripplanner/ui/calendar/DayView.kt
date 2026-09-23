package app.tripplanner.ui.calendar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.tripplanner.schedule.DaySchedule
import app.tripplanner.schedule.Schedule
import app.tripplanner.schedule.TimedEvent
import app.tripplanner.schedule.Warning
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.math.roundToInt

/**
 * Day view (design v1.2 §6.6): a vertical time grid. Blocks are sized by duration, travel legs
 * are hatched between them, free time stays empty, warnings sit on the affected block. Editors
 * long-press and drag a block to pin it (snapped to 15 min) and drag the bottom edge to resize;
 * viewers get the same grid read-only. Times are wall-clock in the trip zone.
 *
 * Complexity:
 * - **Recomposition Time:** O(E + H) for the E events and H hour lines; a drag recomposes
 *   only the moving block.
 * - **Composition Memory:** O(E + H).
 */
@Composable
fun DayView(
    schedule: DaySchedule,
    selectedId: String?,
    canEdit: Boolean,
    onSelect: (String) -> Unit,
    onPin: (eventId: String, start: LocalTime) -> Unit,
    onResize: (eventId: String, durationMin: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hourHeight = 64.dp
    val firstMinute = minOf(schedule.hours.start.toMinutes(), schedule.events.minOfOrNull { it.start.minuteOfDay() } ?: Int.MAX_VALUE).let { (it / 60) * 60 }
    val lastMinute = maxOf(schedule.hours.end.toMinutes(), schedule.events.maxOfOrNull { it.end.minuteOfDay() } ?: 0).let { ((it + 59) / 60) * 60 }.coerceAtLeast(firstMinute + 60)
    val gridHeight = hourHeight * ((lastMinute - firstMinute) / 60f)
    val labelWidth = 48.dp
    val minuteDp: Dp = hourHeight / 60f

    Column(modifier.verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(gridHeight).padding(top = 8.dp)) {
            // Hour lines and labels
            for (m in firstMinute..lastMinute step 60) {
                val y = minuteDp * (m - firstMinute).toFloat()
                Text(
                    Schedule.formatTime(LocalTime(m / 60 % 24, 0)),
                    Modifier.offset(y = y - 8.dp).width(labelWidth),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(Modifier.offset(x = labelWidth, y = y).fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
            }
            // Day hours band
            Box(
                Modifier.offset(x = labelWidth, y = minuteDp * (schedule.hours.start.toMinutes() - firstMinute).toFloat())
                    .fillMaxWidth().height(minuteDp * (schedule.hours.end.toMinutes() - schedule.hours.start.toMinutes()).toFloat())
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
            )
            schedule.events.forEach { event ->
                event.travelBefore?.let { leg ->
                    val top = minuteDp * (leg.start.minuteOfDay() - firstMinute).toFloat()
                    val h = minuteDp * (leg.end.minuteOfDay() - leg.start.minuteOfDay()).toFloat()
                    if (h > 0.dp) HatchedLeg(Modifier.offset(x = labelWidth + 4.dp, y = top).fillMaxWidth().height(h), pending = leg.pending)
                }
                val warnings = schedule.warnings.filter { it.eventId == event.input.id }
                EventBlock(
                    event = event,
                    warnings = warnings,
                    selected = event.input.id == selectedId,
                    canEdit = canEdit,
                    minuteDp = minuteDp,
                    top = minuteDp * (event.start.minuteOfDay() - firstMinute).toFloat(),
                    leftInset = labelWidth + 4.dp,
                    onSelect = { onSelect(event.input.id) },
                    onPin = { start -> onPin(event.input.id, start) },
                    onResize = { min -> onResize(event.input.id, min) },
                )
            }
        }
    }
}

/**
 * Complexity:
 * - **Recomposition Time:** O(1) plus the drag delta while moving.
 * - **Composition Memory:** O(1).
 */
@Composable
private fun EventBlock(
    event: TimedEvent,
    warnings: List<Warning>,
    selected: Boolean,
    canEdit: Boolean,
    minuteDp: Dp,
    top: Dp,
    leftInset: Dp,
    onSelect: () -> Unit,
    onPin: (LocalTime) -> Unit,
    onResize: (Int) -> Unit,
) {
    val density = LocalDensity.current
    val minutePx = with(density) { minuteDp.toPx() }
    var dragMinutes by remember(event.input.id) { mutableFloatStateOf(0f) }
    var resizeMinutes by remember(event.input.id) { mutableFloatStateOf(0f) }
    val durationMin = event.start.minutesUntil(event.end)
    val height = minuteDp * (durationMin + resizeMinutes).coerceAtLeast(Schedule.MIN_DURATION_MIN.toFloat())
    val moving = dragMinutes != 0f
    val container = when {
        moving -> MaterialTheme.colorScheme.tertiaryContainer
        selected -> MaterialTheme.colorScheme.secondaryContainer
        !event.input.hasStop -> MaterialTheme.colorScheme.surfaceVariant
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
            .padding(end = 12.dp)
            .height(height)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onSelect)
            .then(
                if (canEdit) Modifier.pointerInput(event.input.id, event.start) {
                    detectDragGesturesAfterLongPress(
                        onDrag = { change, delta -> change.consume(); dragMinutes += delta.y / minutePx },
                        onDragEnd = {
                            val snapped = ((event.start.minuteOfDay() + dragMinutes) / 15f).roundToInt() * 15
                            dragMinutes = 0f
                            onPin(LocalTime((snapped.coerceIn(0, 24 * 60 - 15)) / 60, snapped.coerceIn(0, 24 * 60 - 15) % 60))
                        },
                        onDragCancel = { dragMinutes = 0f },
                    )
                } else Modifier,
            ),
    ) {
        Box(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                val shownStart = if (moving) LocalTime.fromMinutes(((event.start.minuteOfDay() + dragMinutes) / 15f).roundToInt() * 15) else event.start.time
                Text(
                    (if (event.pinned || moving) "📌 " else "") + Schedule.formatTime(shownStart) + " – " + Schedule.formatTime(LocalTime.fromMinutes(shownStart.toMinutes() + durationMin + resizeMinutes.roundToInt())),
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(event.input.title, style = MaterialTheme.typography.bodyMedium, maxLines = if (height < 40.dp) 1 else 2)
                warnings.forEach { w ->
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
                        .pointerInput(event.input.id, durationMin) {
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
