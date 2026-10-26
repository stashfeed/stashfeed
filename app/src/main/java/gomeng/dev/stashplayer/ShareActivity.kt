package gomeng.dev.stashplayer

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import gomeng.dev.stashplayer.feature.downloads.StashShareInbox

/**
 * "Share → Download to Stash" from other apps. Hands the link to the inbox and brings up Stashfeed's own task,
 * so the app does not open a second time inside the sharing app.
 */
class ShareActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.action == Intent.ACTION_SEND) {
            StashShareInbox.offer(intent.getStringExtra(Intent.EXTRA_TEXT))
        }
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
        )
        finish()
    }
}
