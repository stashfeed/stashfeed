package gomeng.dev.stashplayer.core.ui.components

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.Player
import gomeng.dev.stashplayer.core.network.StashServerProfile
import gomeng.dev.stashplayer.core.player.AspectRatioMode
import gomeng.dev.stashplayer.core.player.StashPlayerController
import gomeng.dev.stashplayer.core.player.stashReelPreloadPolicy
import gomeng.dev.stashplayer.feature.player.PlayerSurface

/**
 * One muted, looping player that plays the preview clip of whichever card is "hovered" (centered in the large
 * layout, held in the compact one). A single player keeps memory and decoders flat however long the list is.
 */
@Stable
class ScenePreviewController(context: Context, headers: Map<String, String>) {
    val player = StashPlayerController(context, headers, stashReelPreloadPolicy())

    /** The card currently previewing, and whether its first frame is on screen yet. */
    var activeSceneId by mutableStateOf<String?>(null)
        private set
    var firstFrameShown by mutableStateOf(false)
        private set

    init {
        player.player.volume = 0f
        player.player.repeatMode = Player.REPEAT_MODE_ONE
        player.player.addListener(
            object : Player.Listener {
                override fun onRenderedFirstFrame() {
                    firstFrameShown = true
                }
            },
        )
    }

    fun play(sceneId: String, previewUrl: String?) {
        if (previewUrl.isNullOrBlank()) {
            stop()
            return
        }
        if (sceneId == activeSceneId) return
        activeSceneId = sceneId
        firstFrameShown = false
        player.prepare(uri = Uri.parse(previewUrl), title = sceneId, playWhenReady = true)
    }

    fun stop() {
        if (activeSceneId == null) return
        activeSceneId = null
        firstFrameShown = false
        player.player.stop()
    }

    fun release() = player.release()
}

@Composable
fun rememberScenePreviewController(serverProfile: StashServerProfile?): ScenePreviewController {
    val context = LocalContext.current.applicationContext
    val headers = remember(serverProfile) { serverProfile?.let { it.authHeadersFor(it.graphQlUrl()) }.orEmpty() }
    val controller = remember(context, headers) { ScenePreviewController(context, headers) }
    DisposableEffect(controller) { onDispose { controller.release() } }
    return controller
}

/** Puts the preview over a card's thumbnail while that card is the one previewing. */
@Composable
fun BoxScope.ScenePreviewSurface(preview: ScenePreviewController, sceneId: String) {
    if (preview.activeSceneId == sceneId && preview.firstFrameShown) {
        PlayerSurface(controller = preview.player, aspectRatioMode = AspectRatioMode.Crop, modifier = Modifier.matchParentSize())
    }
}
