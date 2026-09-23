package app.tripplanner.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitViewController
import app.tripplanner.IosBridges
import app.tripplanner.NativeMapStop
import app.tripplanner.shared.core.model.Event

/**
 * GMSMapView hosted through [app.tripplanner.NativeMapFactory]; events and selection are pushed
 * into the Swift controller, taps come back through the factory callback (design §6.4).
 *
 * Complexity:
 * - **Recomposition Time:** O(S) when events change (one [NativeMapStop] per stop is bridged);
 *   O(1) when only the selection changes.
 * - **Composition Memory:** O(1) Compose nodes - the S markers live in the native map.
 */
@Composable
actual fun MapView(
    events: List<Event>,
    selectedEventId: String?,
    onStopTapped: (String) -> Unit,
    modifier: Modifier,
) {
    val factory = IosBridges.nativeMapFactory
    if (factory == null) {
        Text("Map unavailable: register a NativeMapFactory in MainViewController")
        return
    }
    val currentOnStopTapped by rememberUpdatedState(onStopTapped)
    val map = remember(factory) { factory.create { id -> currentOnStopTapped(id) } }

    UIKitViewController(factory = { map.viewController }, modifier = modifier.fillMaxSize())

    LaunchedEffect(map, events) {
        map.setStops(events.mapNotNull { e -> e.stop?.let { NativeMapStop(e.id, e.title, it.location.latitude, it.location.longitude) } })
    }
    LaunchedEffect(map, selectedEventId) { map.setSelectedStop(selectedEventId) }
}
