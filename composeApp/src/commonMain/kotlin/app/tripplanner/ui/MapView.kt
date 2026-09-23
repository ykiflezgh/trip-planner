package app.tripplanner.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.tripplanner.shared.core.model.Event

/**
 * Platform map (design SS6.4): Maps Compose on Android; GMSMapView hosted from
 * iosApp via a UIViewController factory on iOS. The Kotlin side never links the
 * Objective-C Maps SDK directly.
 */
@Composable
expect fun MapView(
    events: List<Event>,
    selectedEventId: String?,
    onStopTapped: (String) -> Unit,
    modifier: Modifier = Modifier,
)
