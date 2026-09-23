package app.tripplanner.share

import android.content.Intent
import app.tripplanner.auth.CurrentActivity
import app.tripplanner.shared.platform.ShareSheet

/**
 * System share chooser for invite links (design §8.2).
 *
 * Complexity:
 * - **Time:** O(1).
 * - **Space:** O(1).
 */
class AndroidShareSheet(private val currentActivity: CurrentActivity) : ShareSheet {
    override fun share(text: String, title: String) {
        val activity = currentActivity.activity ?: return
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_TITLE, title)
        }
        activity.startActivity(Intent.createChooser(send, title))
    }
}
