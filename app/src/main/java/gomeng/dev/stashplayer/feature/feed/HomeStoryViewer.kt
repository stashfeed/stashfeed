package gomeng.dev.stashplayer.feature.feed

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import gomeng.dev.stashplayer.R
import gomeng.dev.stashplayer.core.model.HomeStory
import gomeng.dev.stashplayer.core.model.SceneCardModel
import gomeng.dev.stashplayer.core.network.StashServerProfile
import gomeng.dev.stashplayer.core.network.buildStashThumbnailRequestSpec
import gomeng.dev.stashplayer.core.player.AspectRatioMode
import gomeng.dev.stashplayer.core.player.StashPlayerController
import gomeng.dev.stashplayer.core.ui.components.rememberStashThumbnailModel
import gomeng.dev.stashplayer.core.ui.i18n.stashString
import gomeng.dev.stashplayer.feature.performers.PerformerImage
import gomeng.dev.stashplayer.feature.player.PlayerSurface
import kotlinx.coroutines.delay

/** A story segment never runs longer than this, even if the preview clip is longer. */
private const val STORY_MAX_SEGMENT_MS = 15_000L
/** Scenes without a playable preview show their animated preview / screenshot this long. */
private const val STORY_STILL_SEGMENT_MS = 5_000L
/** A clip that hasn't started after this long counts as unplayable (some software decoders stall). */
private const val STORY_START_TIMEOUT_MS = 3_000L
private const val STORY_TICK_MS = 100L

/**
 * Full-screen story viewer: plays each new scene's preview clip in turn, then moves on to the next performer.
 * Tap the left third to go back, anywhere else to skip ahead.
 */
@Composable
internal fun HomeStoryViewer(
    stories: List<HomeStory>,
    initialStoryIndex: Int,
    serverProfile: StashServerProfile?,
    onDismiss: () -> Unit,
    onOpenPerformer: (String) -> Unit,
    onOpenScene: (scene: SceneCardModel, scenes: List<SceneCardModel>) -> Unit,
) {
    val context = LocalContext.current
    var storyIndex by remember { mutableIntStateOf(initialStoryIndex.coerceIn(0, stories.lastIndex)) }
    var sceneIndex by remember { mutableIntStateOf(0) }
    var muted by remember { mutableStateOf(false) }
    var segmentProgress by remember { mutableFloatStateOf(0f) }
    var videoUnplayable by remember { mutableStateOf(false) }
    val story = stories[storyIndex]
    val current = story.scenes[sceneIndex.coerceIn(0, story.scenes.lastIndex)]
    val headers = remember(serverProfile) {
        serverProfile?.let { profile -> profile.authHeadersFor(profile.graphQlUrl()) }.orEmpty()
    }
    val controller = remember(context, headers) { StashPlayerController(context, headers) }
    DisposableEffect(controller) { onDispose { controller.release() } }
    BackHandler(onBack = onDismiss)

    fun next() {
        when {
            sceneIndex < story.scenes.lastIndex -> sceneIndex += 1
            storyIndex < stories.lastIndex -> {
                storyIndex += 1
                sceneIndex = 0
            }
            else -> onDismiss()
        }
    }

    fun previous() {
        when {
            sceneIndex > 0 -> sceneIndex -= 1
            storyIndex > 0 -> {
                storyIndex -= 1
                sceneIndex = 0
            }
        }
    }
    val latestNext by rememberUpdatedState(::next)

    val previewSpec = remember(current.previewUrl, serverProfile) { buildStashThumbnailRequestSpec(current.previewUrl, serverProfile) }
    LaunchedEffect(muted, controller) { controller.player.volume = if (muted) 0f else 1f }
    LaunchedEffect(current.scene.id, previewSpec) {
        segmentProgress = 0f
        videoUnplayable = false
        suspend fun showStill() {
            controller.player.stop()
            var elapsed = 0L
            while (elapsed < STORY_STILL_SEGMENT_MS) {
                delay(STORY_TICK_MS)
                elapsed += STORY_TICK_MS
                segmentProgress = elapsed / STORY_STILL_SEGMENT_MS.toFloat()
            }
            latestNext()
        }
        if (previewSpec == null) {
            showStill()
            return@LaunchedEffect
        }
        controller.prepare(
            uri = Uri.parse(previewSpec.url),
            title = current.scene.title,
            requestHeaders = previewSpec.requestHeaders.ifEmpty { headers },
        )
        var elapsed = 0L
        var waiting = 0L
        while (true) {
            delay(STORY_TICK_MS)
            val player = controller.player
            if (player.isPlaying) elapsed += STORY_TICK_MS else if (elapsed == 0L) waiting += STORY_TICK_MS
            if (elapsed == 0L && (waiting >= STORY_START_TIMEOUT_MS || controller.lastError != null)) {
                videoUnplayable = true
                showStill()
                return@LaunchedEffect
            }
            val duration = player.duration.takeIf { it > 0L }?.coerceAtMost(STORY_MAX_SEGMENT_MS) ?: STORY_MAX_SEGMENT_MS
            segmentProgress = (player.currentPosition.coerceAtLeast(0L) / duration.toFloat()).coerceIn(0f, 1f)
            val ended = player.playbackState == Player.STATE_ENDED
            // A clip that fails to play shouldn't freeze the story.
            val failed = controller.lastError != null
            if (ended || failed || elapsed >= STORY_MAX_SEGMENT_MS) break
        }
        latestNext()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(storyIndex, sceneIndex) {
                detectTapGestures { offset ->
                    if (offset.x < size.width / 3f) previous() else next()
                }
            },
    ) {
        val screenshot = rememberStashThumbnailModel(current.scene.thumbnailUrl, serverProfile)
        val animated = rememberStashThumbnailModel(current.animatedPreviewUrl, serverProfile)
        if (previewSpec != null && !videoUnplayable) {
            PlayerSurface(controller = controller, aspectRatioMode = AspectRatioMode.Fit, modifier = Modifier.fillMaxSize())
        } else {
            // Screenshot underneath, animated preview on top: if the WebP wasn't generated the screenshot shows.
            screenshot?.let { AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
            animated?.let { AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                story.scenes.indices.forEach { index ->
                    LinearProgressIndicator(
                        progress = {
                            when {
                                index < sceneIndex -> 1f
                                index == sceneIndex -> segmentProgress
                                else -> 0f
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.3f),
                        drawStopIndicator = {},
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onOpenPerformer(story.performer.id) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PerformerImage(
                        name = story.performer.name,
                        imageUrl = story.performer.imageUrl,
                        serverProfile = serverProfile,
                        shape = CircleShape,
                        modifier = Modifier.size(36.dp),
                    )
                    Column {
                        Text(story.performer.name, color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                        Text(relativeAddedLabel(current.createdAt), color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall)
                    }
                }
                IconButton(onClick = { muted = !muted }) {
                    Icon(
                        if (muted) Icons.AutoMirrored.Outlined.VolumeOff else Icons.AutoMirrored.Outlined.VolumeUp,
                        contentDescription = stashString(if (muted) R.string.home_story_unmute else R.string.home_story_mute),
                        tint = Color.White,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = stashString(R.string.auto_kr_0192), tint = Color.White)
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                current.scene.title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
            Button(onClick = { onOpenScene(current.scene, story.scenes.map { it.scene }) }) {
                Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stashString(R.string.home_story_watch_full), modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}
