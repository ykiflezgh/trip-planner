package app.tripplanner.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import app.tripplanner.shared.core.model.Stop
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState

private const val STOP_ZOOM = 13f

/**
 * Maps Compose actual: one [Marker] per stop, camera follows the selection.
 *
 * Complexity:
 * - **Recomposition Time:** O(S) where S is the number of stops (marker state updates and a
 *   linear scan for the focused stop); the camera update is O(1).
 * - **Composition Memory:** O(S) marker nodes + one camera state.
 */
@Composable
actual fun MapView(
    stops: List<Stop>,
    selectedStopId: String?,
    onStopTapped: (String) -> Unit,
    modifier: Modifier,
) {
    val camera = rememberCameraPositionState()
    // Follow the selection; before any selection, land on the first stop once stops arrive.
    val focus = stops.firstOrNull { it.id == selectedStopId } ?: stops.firstOrNull()
    LaunchedEffect(focus?.id) {
        val target = focus ?: return@LaunchedEffect
        val position = CameraPosition.fromLatLngZoom(LatLng(target.lat, target.lng), STOP_ZOOM)
        if (selectedStopId == null) camera.position = position
        else camera.animate(CameraUpdateFactory.newCameraPosition(position))
    }
    GoogleMap(modifier = modifier, cameraPositionState = camera) {
        stops.forEach { stop ->
            val selected = stop.id == selectedStopId
            Marker(
                state = rememberUpdatedMarkerState(position = LatLng(stop.lat, stop.lng)),
                title = stop.name,
                icon = BitmapDescriptorFactory.defaultMarker(
                    if (selected) BitmapDescriptorFactory.HUE_AZURE else BitmapDescriptorFactory.HUE_RED,
                ),
                zIndex = if (selected) 1f else 0f,
                onClick = { onStopTapped(stop.id); true }, // consumed: selection card replaces the info window
            )
        }
    }
    // TODO Phase 1: camera bounds to the day's stops; polyline between consecutive stops
}
