package app.tripplanner.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitViewController
import app.tripplanner.IosBridges
import app.tripplanner.shared.core.model.Stop

@Composable
actual fun MapView(
    stops: List<Stop>,
    selectedStopId: String?,
    onStopTapped: (String) -> Unit,
    modifier: Modifier,
) {
    val factory = IosBridges.mapViewControllerFactory
    if (factory == null) {
        Text("Map unavailable: register a factory in MainViewController")
        return
    }
    UIKitViewController(factory = factory, modifier = modifier.fillMaxSize())
    // TODO Phase 0 spike: pass stops/selection through a MapController interface,
    // implemented by the Swift wrapper (iosApp/MapViewFactory.swift)
}
