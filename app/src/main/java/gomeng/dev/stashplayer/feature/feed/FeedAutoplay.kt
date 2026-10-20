package gomeng.dev.stashplayer.feature.feed

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import gomeng.dev.stashplayer.core.network.StashServerProfile
import gomeng.dev.stashplayer.core.ui.components.LocalStashPlayerActive
import gomeng.dev.stashplayer.core.ui.components.LocalStashPlayerExpanded
import gomeng.dev.stashplayer.core.ui.components.ScenePreviewController
import gomeng.dev.stashplayer.core.ui.components.StashFeedSound
import gomeng.dev.stashplayer.core.ui.components.previewStreamUrl
import gomeng.dev.stashplayer.core.ui.components.rememberScenePreviewController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

/** A post needs this much of itself on screen before its video starts. */
private const val FEED_AUTOPLAY_MIN_VISIBLE_FRACTION = 0.6f

/** Settle time before switching videos, so flinging past posts doesn't start each one. */
private const val FEED_AUTOPLAY_SETTLE_MS = 220L

/** What a feed post plays inline: the scene and its full video. */
data class FeedInlineVideo(val sceneId: String, val streamUrl: String)

/** The inline video of a scene post, from its preview path (`/scene/{id}/preview` → `/scene/{id}/stream`). */
fun feedInlineVideoFor(sceneId: String, previewUrl: String?): FeedInlineVideo? =
    previewUrl?.let(::previewStreamUrl)?.let { FeedInlineVideo(sceneId, it) }

/**
 * Plays the scene post that is most on screen, muted unless the viewer turned sound on for this session, like
 * Reddit and Instagram. One player serves the whole feed. Silent while any full or mini player is open.
 *
 * @param videoFor the video of the grid item with this key, or null for items that aren't scene posts.
 */
@Composable
internal fun rememberFeedAutoplay(
    gridState: LazyGridState,
    serverProfile: StashServerProfile?,
    videoFor: (key: Any) -> FeedInlineVideo?,
): ScenePreviewController {
    val controller = rememberScenePreviewController(serverProfile)
    val playerActive = LocalStashPlayerActive.current
    val soundOn = StashFeedSound.enabled
    LaunchedEffect(controller, soundOn, playerActive) {
        controller.setVolume(if (soundOn && !playerActive) 1f else 0f)
    }
    val latestVideoFor by rememberUpdatedState(videoFor)

    // Restarting with the full player's state picks the post in view again once the player is minimised.
    val playerExpanded = LocalStashPlayerExpanded.current
    LaunchedEffect(controller, gridState, playerExpanded) {
        if (playerExpanded) return@LaunchedEffect
        snapshotFlow {
            val layout = gridState.layoutInfo
            val viewportStart = layout.viewportStartOffset
            val viewportEnd = layout.viewportEndOffset
            val center = (viewportStart + viewportEnd) / 2
            layout.visibleItemsInfo
                .filter { info ->
                    val height = info.size.height
                    if (height <= 0 || latestVideoFor(info.key) == null) return@filter false
                    val top = info.offset.y
                    val visible = (minOf(top + height, viewportEnd) - maxOf(top, viewportStart)).coerceAtLeast(0)
                    visible.toFloat() / minOf(height, viewportEnd - viewportStart) >= FEED_AUTOPLAY_MIN_VISIBLE_FRACTION
                }
                .minByOrNull { info -> kotlin.math.abs(info.offset.y + info.size.height / 2 - center) }
                ?.key
        }
            .distinctUntilChanged()
            .collectLatest { key ->
                if (key == null) {
                    controller.stop()
                    return@collectLatest
                }
                delay(FEED_AUTOPLAY_SETTLE_MS)
                val video = latestVideoFor(key)
                if (video == null) controller.stop() else controller.playVideo(video.sceneId, video.streamUrl)
            }
    }

    // Leaving the app pauses the feed video; coming back resumes it where it was.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, controller) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> controller.player.player.pause()
                Lifecycle.Event.ON_START -> if (controller.activeSceneId != null && !controller.paused) controller.player.player.play()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return controller
}
