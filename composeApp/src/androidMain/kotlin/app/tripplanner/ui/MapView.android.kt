package app.tripplanner.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import app.tripplanner.shared.core.model.Event
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState

private const val STOP_ZOOM = 14f
private const val FIT_PADDING_DP = 56

/**
 * Maps Compose actual (design §3.1 (4), §6.4): one [Marker] per event, a [Polyline] through
 * the events in itinerary order, camera fitted to the day's events whenever the set of events
 * changes with nothing selected, and following the selection otherwise.
 *
 * Complexity:
 * - **Recomposition Time:** O(S) where S is the number of events (marker states, polyline
 *   points, bounds); camera updates are O(1).
 * - **Composition Memory:** O(S) marker nodes + the polyline + one camera state.
 */
@Composable
actual fun MapView(
    events: List<Event>, // only events that contain a event are passed in
    selectedEventId: String?,
    onStopTapped: (String) -> Unit,
    modifier: Modifier,
) {
    val camera = rememberCameraPositionState()
    // newLatLngBounds needs a laid-out map ("Map size can't be 0"); onMapLoaded is the safe gate.
    var mapLoaded by remember { mutableStateOf(false) }
    val paddingPx = with(LocalDensity.current) { FIT_PADDING_DP.dp.roundToPx() }
    val eventIds = events.map { it.id }

    // Fit the day's events when the set changes (tab switch, add, remove) and nothing is selected.
    LaunchedEffect(mapLoaded, eventIds, selectedEventId == null) {
        if (!mapLoaded || selectedEventId != null || events.isEmpty()) return@LaunchedEffect
        val update = if (events.size == 1) {
            CameraUpdateFactory.newLatLngZoom(events.first().latLng, STOP_ZOOM)
        } else {
            val bounds = LatLngBounds.builder().apply { events.forEach { include(it.latLng) } }.build()
            CameraUpdateFactory.newLatLngBounds(bounds, paddingPx)
        }
        runCatching { camera.animate(update) }
            .onFailure { camera.animate(CameraUpdateFactory.newLatLngZoom(events.first().latLng, STOP_ZOOM)) }
    }
    // Follow the selection without zooming back out.
    LaunchedEffect(mapLoaded, selectedEventId) {
        if (!mapLoaded) return@LaunchedEffect
        val target = events.firstOrNull { it.id == selectedEventId } ?: return@LaunchedEffect
        camera.animate(CameraUpdateFactory.newLatLngZoom(target.latLng, maxOf(camera.position.zoom, STOP_ZOOM)))
    }

    val routeColor = MaterialTheme.colorScheme.primary
    GoogleMap(modifier = modifier, cameraPositionState = camera, onMapLoaded = { mapLoaded = true }) {
        if (events.size >= 2) {
            Polyline(points = events.map { it.latLng }, color = routeColor, width = 8f, zIndex = -1f)
        }
        events.forEach { event ->
            val selected = event.id == selectedEventId
            Marker(
                state = rememberUpdatedMarkerState(position = event.latLng),
                title = event.title,
                icon = BitmapDescriptorFactory.defaultMarker(
                    if (selected) BitmapDescriptorFactory.HUE_AZURE else BitmapDescriptorFactory.HUE_RED,
                ),
                zIndex = if (selected) 1f else 0f,
                onClick = { onStopTapped(event.id); true }, // consumed: selection card replaces the info window
            )
        }
    }
}

private val Event.latLng: LatLng get() = LatLng(stop?.location?.latitude ?: 0.0, stop?.location?.longitude ?: 0.0)
private val Int.dp get() = androidx.compose.ui.unit.Dp(this.toFloat())
