package app.tripplanner.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.tripplanner.shared.core.model.Stop
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberUpdatedMarkerState

@Composable
actual fun MapView(
    stops: List<Stop>,
    selectedStopId: String?,
    onStopTapped: (String) -> Unit,
    modifier: Modifier,
) {
    GoogleMap(modifier = modifier) {
        stops.forEach { stop ->
            Marker(
                state = rememberUpdatedMarkerState(position = LatLng(stop.lat, stop.lng)),
                title = stop.name,
                onClick = { onStopTapped(stop.id); false },
            )
        }
    }
    // TODO Phase 1: camera bounds to day's stops; polyline between consecutive stops
}
