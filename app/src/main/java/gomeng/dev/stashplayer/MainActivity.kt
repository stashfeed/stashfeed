package gomeng.dev.stashplayer

import gomeng.dev.stashplayer.app.StashLaunchState
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import gomeng.dev.stashplayer.app.StashPlayerAppRoot
import gomeng.dev.stashplayer.core.player.StashPictureInPictureController

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { !StashLaunchState.ready }
        super.onCreate(savedInstanceState)
        StashPictureInPictureController.setActive(isInPictureInPictureMode)
        setContent {
            StashPlayerAppRoot()
        }
    }

    override fun onUserLeaveHint() {
        if (!StashPictureInPictureController.enterIfEligible(this)) {
            super.onUserLeaveHint()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        StashPictureInPictureController.setActive(isInPictureInPictureMode)
        StashPictureInPictureController.updateParams(this)
    }
}
