package app.tripplanner

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/** Factory registered by iosApp at startup so the map's Swift wrapper reaches Kotlin (§6.4). */
object IosBridges {
    var mapViewControllerFactory: (() -> UIViewController)? = null
}

/**
 * Root Compose controller for the SwiftUI host. Call [initKoin] before this.
 *
 * Complexity:
 * - **Time:** O(1) controller creation; composition cost is documented on [App].
 * - **Space:** O(1) plus the Compose tree.
 */
fun MainViewController(mapFactory: () -> UIViewController): UIViewController {
    IosBridges.mapViewControllerFactory = mapFactory
    return ComposeUIViewController { App() }
}
