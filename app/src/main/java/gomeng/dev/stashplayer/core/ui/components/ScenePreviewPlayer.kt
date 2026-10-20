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
import android.view.LayoutInflater
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.viewinterop.AndroidView
import android.util.Log
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import gomeng.dev.stashplayer.R
import gomeng.dev.stashplayer.core.network.StashServerProfile
import gomeng.dev.stashplayer.core.player.StashPlayerController
import gomeng.dev.stashplayer.core.player.stashReelPreloadPolicy

/**
 * One muted, looping player that plays the preview clip of whichever card is "hovered" (centered in the large
 * layout, held in the compact one). A single player keeps memory and decoders flat however long the list is.
 */
@Stable
class ScenePreviewController(context: Context, private val headers: Map<String, String>) {
    val player = StashPlayerController(context, headers, stashReelPreloadPolicy(), useCache = false)

    /** The card currently previewing, and whether its first frame is on screen yet. */
    var activeSceneId by mutableStateOf<String?>(null)
        private set
    var firstFrameShown by mutableStateOf(false)
        private set

    /** Paused by the viewer (tap on a feed video); cleared when another video starts. */
    var paused by mutableStateOf(false)
        private set

    fun togglePause() {
        if (activeSceneId == null) return
        paused = !paused
        if (paused) player.player.pause() else player.player.play()
    }

    init {
        player.player.volume = 0f
        player.player.repeatMode = Player.REPEAT_MODE_ONE
        player.player.addListener(
            object : Player.Listener {
                override fun onRenderedFirstFrame() {
                    firstFrameShown = true
                }

                override fun onPlayerError(error: PlaybackException) {
                    // No generated preview clip on the server (404 and the like): loop a muted bit of the video.
                    val fallback = pendingFallback
                    pendingFallback = null
                    if (fallback != null && activeSceneId == fallback.sceneId) {
                        player.prepare(
                            uri = Uri.parse(fallback.streamUrl),
                            title = fallback.sceneId,
                            playWhenReady = true,
                            requestHeaders = headers,
                            clipStartMs = fallback.startMs,
                            clipEndMs = fallback.startMs + PREVIEW_FALLBACK_LENGTH_MS,
                        )
                    } else {
                        Log.w("StashyPreview", "Preview of scene $activeSceneId failed: ${error.errorCodeName}")
                    }
                }
            },
        )
    }

    private data class Fallback(val sceneId: String, val streamUrl: String, val startMs: Long)
    private var pendingFallback: Fallback? = null

    /** @param durationText the card's "mm:ss" / "h:mm:ss" length, to pick where a fallback loop starts. */
    fun play(sceneId: String, previewUrl: String?, durationText: String? = null) {
        if (previewUrl.isNullOrBlank()) {
            stop()
            return
        }
        if (sceneId == activeSceneId) return
        activeSceneId = sceneId
        firstFrameShown = false
        pendingFallback = previewStreamUrl(previewUrl)?.let { stream ->
            val durationMs = durationText?.toDurationMsOrNull() ?: 0L
            Fallback(sceneId, stream, startMs = (durationMs / 5).coerceAtLeast(0L))
        }
        // prepare() replaces the request headers, so the server's auth headers go with every clip.
        player.prepare(uri = Uri.parse(previewUrl), title = sceneId, playWhenReady = true, requestHeaders = headers)
    }

    /** Plays the whole video in a loop (feed posts), from the start. */
    fun playVideo(sceneId: String, streamUrl: String?) {
        if (streamUrl.isNullOrBlank()) {
            stop()
            return
        }
        if (sceneId == activeSceneId) return
        activeSceneId = sceneId
        firstFrameShown = false
        paused = false
        pendingFallback = null
        player.prepare(uri = Uri.parse(streamUrl), title = sceneId, playWhenReady = true, requestHeaders = headers)
    }

    fun setVolume(volume: Float) {
        player.player.volume = volume
    }

    fun stop() {
        if (activeSceneId == null) return
        activeSceneId = null
        pendingFallback = null
        firstFrameShown = false
        paused = false
        player.player.stop()
    }

    fun release() = player.release()
}

/** Length of the loop cut from the video when a scene has no generated preview clip. */
private const val PREVIEW_FALLBACK_LENGTH_MS = 12_000L

/** Stash serves a scene's preview at `/scene/{id}/preview` and the video at `/scene/{id}/stream`. */
internal fun previewStreamUrl(previewUrl: String): String? {
    val match = Regex("/scene/([^/?#]+)/preview(?=$|[?#])").find(previewUrl) ?: return null
    return previewUrl.replaceRange(match.range, "/scene/${match.groupValues[1]}/stream")
}

/** "56:46" or "1:02:03" → milliseconds. */
internal fun String.toDurationMsOrNull(): Long? {
    val parts = trim().split(':').map { it.toLongOrNull() ?: return null }
    if (parts.isEmpty() || parts.size > 3) return null
    return parts.fold(0L) { total, part -> total * 60 + part } * 1000
}

/**
 * Whether feed videos play with sound. Off by default and for the life of the app process (one session): turning it
 * on once keeps it on while you scroll, until the app is closed.
 */
object StashFeedSound {
    var enabled by mutableStateOf(false)
}

/** True while any player (full or mini) is open; feed videos then stay silent so two sounds never mix. */
val LocalStashPlayerActive = androidx.compose.runtime.compositionLocalOf { false }

/** True while the full player covers the screen; previews and other video underneath stay quiet. */
val LocalStashPlayerExpanded = androidx.compose.runtime.compositionLocalOf { false }

@Composable
fun rememberScenePreviewController(serverProfile: StashServerProfile?): ScenePreviewController {
    val context = LocalContext.current.applicationContext
    val headers = remember(serverProfile) { serverProfile?.let { it.authHeadersFor(it.graphQlUrl()) }.orEmpty() }
    val controller = remember(context, headers) { ScenePreviewController(context, headers) }
    DisposableEffect(controller) { onDispose { controller.release() } }
    val playerExpanded = LocalStashPlayerExpanded.current
    androidx.compose.runtime.LaunchedEffect(playerExpanded) { if (playerExpanded) controller.stop() }
    return controller
}

/**
 * Puts the preview over a card's thumbnail while that card is the one previewing. The view is attached as soon as
 * the card becomes active (the player needs its surface to render anything) and stays invisible until the first
 * frame, so the thumbnail shows instead of a black box.
 */
@Composable
fun BoxScope.ScenePreviewSurface(preview: ScenePreviewController, sceneId: String) {
    if (preview.activeSceneId != sceneId) return
    AndroidView(
        modifier = Modifier
            .matchParentSize()
            .graphicsLayer { alpha = if (preview.firstFrameShown) 1f else 0f },
        factory = { context ->
            (LayoutInflater.from(context).inflate(R.layout.scene_preview_player_view, null) as PlayerView).apply {
                player = preview.player.player
            }
        },
        update = { view -> view.player = preview.player.player },
        onRelease = { view -> view.player = null },
    )
}
