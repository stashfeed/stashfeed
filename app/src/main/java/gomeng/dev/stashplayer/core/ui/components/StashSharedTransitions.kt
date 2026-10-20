package gomeng.dev.stashplayer.core.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlin.math.roundToInt

/* ---------------------------------------------------------------------------------------------------------------
 * Shared elements between navigation destinations (performer tile → profile)
 * ------------------------------------------------------------------------------------------------------------- */

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalStashSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/** The destination's own AnimatedVisibilityScope, provided by the nav host for each screen. */
val LocalStashNavAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Marks this element as the same thing on two screens; it morphs from one to the other during navigation
 * (and back with predictive back). Does nothing outside the nav host's shared transition layout.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.stashSharedElement(key: String): Modifier {
    val transitionScope = LocalStashSharedTransitionScope.current ?: return this
    val animatedScope = LocalStashNavAnimatedScope.current ?: return this
    return with(transitionScope) {
        this@stashSharedElement.sharedElement(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = animatedScope,
        )
    }
}

/* ---------------------------------------------------------------------------------------------------------------
 * Thumbnail → player flight
 * ------------------------------------------------------------------------------------------------------------- */

/** Where a tapped thumbnail was and what it showed; the player layer flies it into place. */
data class StashThumbnailFlight(
    val sceneId: String,
    val bounds: Rect,
    val model: Any?,
    val createdAtMs: Long = System.currentTimeMillis(),
)

/** Remembers the last tapped scene thumbnail so the player can grow out of it. */
@Stable
class StashThumbnailFlightRegistry {
    var pending by mutableStateOf<StashThumbnailFlight?>(null)
        private set

    fun record(sceneId: String, coordinates: LayoutCoordinates?, model: Any?) {
        val bounds = coordinates?.takeIf { it.isAttached }?.boundsInRoot() ?: return
        pending = StashThumbnailFlight(sceneId, bounds, model)
    }

    /** The flight for [sceneId] if it was tapped just now; consumed on read. */
    fun take(sceneId: String): StashThumbnailFlight? {
        val flight = pending ?: return null
        pending = null
        return flight.takeIf { it.sceneId == sceneId && System.currentTimeMillis() - it.createdAtMs < 1_500 }
    }
}

val LocalStashThumbnailFlights = compositionLocalOf<StashThumbnailFlightRegistry?> { null }

/**
 * Draws [flight]'s thumbnail moving and growing from where it was tapped to [target], then fading out over the
 * player underneath. [onDone] runs when it's gone.
 */
@Composable
fun StashThumbnailFlightOverlay(flight: StashThumbnailFlight, target: Rect, onDone: () -> Unit) {
    val progress = remember(flight) { Animatable(0f) }
    val fade = remember(flight) { Animatable(1f) }
    LaunchedEffect(flight) {
        progress.animateTo(1f, tween(380, easing = androidx.compose.animation.core.FastOutSlowInEasing))
        // Hold the poster where the video will be while the stream starts, then let the player show through.
        kotlinx.coroutines.delay(450)
        fade.animateTo(0f, tween(260))
        onDone()
    }
    val density = LocalDensity.current
    val t = progress.value
    val left = lerp(flight.bounds.left, target.left, t)
    val top = lerp(flight.bounds.top, target.top, t)
    val width = lerp(flight.bounds.width, target.width, t)
    val height = lerp(flight.bounds.height, target.height, t)
    val radius = lerp(8f, 0f, t)
    with(density) {
        Box(
            Modifier
                .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                .size(width.toDp(), height.toDp())
                .graphicsLayer { alpha = fade.value }
                .clip(RoundedCornerShape(radius.dp)),
        ) {
            AsyncImage(
                model = flight.model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(width.toDp(), height.toDp()),
            )
        }
    }
}

private fun lerp(start: Float, end: Float, fraction: Float) = start + (end - start) * fraction
