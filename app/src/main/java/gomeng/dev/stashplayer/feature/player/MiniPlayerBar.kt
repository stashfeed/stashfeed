package gomeng.dev.stashplayer.feature.player

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import gomeng.dev.stashplayer.R
import gomeng.dev.stashplayer.core.player.AspectRatioMode
import gomeng.dev.stashplayer.core.player.StashPlayerController
import gomeng.dev.stashplayer.core.player.formatPlayerPosition
import gomeng.dev.stashplayer.core.ui.designsystem.StashRadius
import gomeng.dev.stashplayer.core.ui.designsystem.StashSpace
import gomeng.dev.stashplayer.core.ui.designsystem.rememberStashHaptics
import gomeng.dev.stashplayer.core.ui.designsystem.stashSpring
import gomeng.dev.stashplayer.core.ui.i18n.stashString
import kotlinx.coroutines.launch

/** Distance (px) a vertical drag needs before it expands (up) or closes (down) the mini player. */
private const val MINI_PLAYER_DRAG_THRESHOLD_PX = 120f

/**
 * The player shrunk into a bar above the navigation: the same player keeps running, drawn into its own small
 * surface. Tap or swipe up expands it, swipe down or × closes it.
 */
@Composable
internal fun MiniPlayerBar(
    controller: StashPlayerController,
    title: String,
    subtitle: String?,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    bottomPadding: Dp,
    onExpand: () -> Unit,
    onClose: () -> Unit,
) {
    val haptics = rememberStashHaptics()
    val scope = rememberCoroutineScope()
    val dragOffset = remember { Animatable(0f) }
    val progress = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    // Full-size but transparent to touches outside the bar, so the bar sits at the bottom of the screen.
    Box(modifier = Modifier.fillMaxSize().padding(bottom = bottomPadding), contentAlignment = Alignment.BottomCenter) {
        Surface(
            shape = RoundedCornerShape(StashRadius.M),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 12.dp,
            modifier = Modifier
                .padding(horizontal = StashSpace.S, vertical = StashSpace.S)
                .fillMaxWidth()
                .height(64.dp)
                .graphicsLayer {
                    translationY = dragOffset.value
                    alpha = 1f - (dragOffset.value / 400f).coerceIn(0f, 0.6f)
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            val offset = dragOffset.value
                            when {
                                offset > MINI_PLAYER_DRAG_THRESHOLD_PX -> {
                                    haptics.tick()
                                    onClose()
                                }
                                offset < -MINI_PLAYER_DRAG_THRESHOLD_PX / 2 -> onExpand()
                            }
                            scope.launch { dragOffset.animateTo(0f, stashSpring()) }
                        },
                        onDragCancel = { scope.launch { dragOffset.animateTo(0f, stashSpring()) } },
                    ) { change, dragAmount ->
                        change.consume()
                        scope.launch { dragOffset.snapTo((dragOffset.value + dragAmount).coerceAtLeast(-160f)) }
                    }
                }
                .clickable(onClick = onExpand),
        ) {
            Box {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .width(114.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(topStart = StashRadius.M, bottomStart = StashRadius.M))
                            .background(Color.Black),
                    ) {
                        PlayerSurface(controller = controller, aspectRatioMode = AspectRatioMode.Crop, modifier = Modifier.fillMaxWidth().fillMaxHeight())
                    }
                    Column(modifier = Modifier.weight(1f).padding(horizontal = StashSpace.M)) {
                        Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            text = listOfNotNull(subtitle?.takeIf { it.isNotBlank() }, formatPlayerPosition(positionMs)).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = {
                        haptics.tick()
                        controller.playPause()
                    }) {
                        Icon(
                            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = stashString(if (isPlaying) R.string.mini_player_pause else R.string.mini_player_play),
                        )
                    }
                    IconButton(onClick = onClose) {
                        Icon(Icons.Outlined.Close, contentDescription = stashString(R.string.mini_player_close))
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                ) {
                    Box(Modifier.fillMaxWidth(progress).height(2.dp).background(MaterialTheme.colorScheme.primary))
                }
            }
        }
    }
}
