package app.tripplanner.share

import app.tripplanner.shared.platform.ShareSheet
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene

/**
 * UIActivityViewController for invite links (design §8.2), presented from the top view controller.
 *
 * Complexity:
 * - **Time:** O(W) to find the key window among W windows.
 * - **Space:** O(1).
 */
class IosShareSheet : ShareSheet {
    override fun share(text: String, title: String) {
        val scene = UIApplication.sharedApplication.connectedScenes.firstOrNull { it is UIWindowScene } as? UIWindowScene ?: return
        val window = scene.windows.firstOrNull { (it as UIWindow).isKeyWindow() } as? UIWindow ?: return
        var top = window.rootViewController ?: return
        while (top.presentedViewController != null) top = top.presentedViewController!!
        val sheet = UIActivityViewController(activityItems = listOf(text), applicationActivities = null)
        top.presentViewController(sheet, animated = true, completion = null)
    }
}
