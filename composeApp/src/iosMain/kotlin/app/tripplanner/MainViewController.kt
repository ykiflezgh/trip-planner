package app.tripplanner

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/** Factory registered by iosApp at startup so the map's Swift wrapper reaches Kotlin (SS6.4). */
object IosBridges {
    var mapViewControllerFactory: (() -> UIViewController)? = null
}

fun MainViewController(mapFactory: () -> UIViewController): UIViewController {
    IosBridges.mapViewControllerFactory = mapFactory
    return ComposeUIViewController { App() }
}
