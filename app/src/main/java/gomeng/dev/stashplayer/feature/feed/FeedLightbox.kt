package gomeng.dev.stashplayer.feature.feed

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import gomeng.dev.stashplayer.R
import gomeng.dev.stashplayer.core.model.GalleryImageModel
import gomeng.dev.stashplayer.core.network.StashServerProfile
import gomeng.dev.stashplayer.core.ui.components.rememberStashThumbnailModel
import gomeng.dev.stashplayer.core.ui.i18n.stashString
import kotlin.math.abs
import kotlin.math.sign
import kotlinx.coroutines.launch

/** Start loading the next page this many images before the end, like the plugin. */
private const val LIGHTBOX_PRELOAD_DISTANCE = 4

/** Swipe to close: share of the screen height the photo must travel, or the fling speed (px/s) that is enough. */
private const val LIGHTBOX_DISMISS_DISTANCE = 0.22f
private const val LIGHTBOX_DISMISS_VELOCITY = 1_800f

/** How far the photo shrinks while it is dragged away, at a full screen height. */
private const val LIGHTBOX_DRAG_MIN_SCALE = 0.85f

/**
 * What the full-screen viewer shows. [images] is read on every frame, so a list that grows while the viewer is
 * open (more pages loaded through [onNeedMore]) appears without reopening it.
 */
@Stable
class FeedLightboxRequest(
    val title: String,
    val startIndex: Int,
    val totalCount: Int,
    val images: () -> List<GalleryImageModel>,
    val onNeedMore: () -> Unit,
    /** Called with the photo shown when the viewer closes, so the carousel behind it can show the same one. */
    val onClosed: (Int) -> Unit = {},
)

/**
 * Full-screen photo viewer with the Binge plugin's gestures: swipe sideways to change photo, tap the left half
 * for the previous one and the right half for the next. Swiping up or down drags the photo away and closes the
 * viewer, as on Reddit. Hides the system bars while open.
 */
@Composable
fun FeedLightbox(
    request: FeedLightboxRequest,
    serverProfile: StashServerProfile?,
    onDismiss: () -> Unit,
) {
    val images = request.images()
    val pageCount = images.size
    val pagerState = rememberPagerState(initialPage = request.startIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))) { pageCount }
    val scope = rememberCoroutineScope()
    val dragOffset = remember { Animatable(0f) }
    var viewerHeight by remember { mutableFloatStateOf(1f) }
    val close = {
        request.onClosed(pagerState.currentPage)
        onDismiss()
    }
    BackHandler(onBack = close)
    ImmersiveSystemBars()
    // 0 at rest, 1 once the photo has been dragged a full screen height away.
    val dragFraction = (abs(dragOffset.value) / viewerHeight).coerceIn(0f, 1f)

    LaunchedEffect(pagerState, pageCount) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            if (page >= pageCount - LIGHTBOX_PRELOAD_DISTANCE && pageCount < request.totalCount) request.onNeedMore()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { viewerHeight = it.height.toFloat().coerceAtLeast(1f) }
            .background(Color.Black.copy(alpha = 1f - (dragFraction * 1.6f).coerceAtMost(1f)))
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta -> scope.launch { dragOffset.snapTo(dragOffset.value + delta) } },
                onDragStopped = { velocity ->
                    val farEnough = abs(dragOffset.value) > viewerHeight * LIGHTBOX_DISMISS_DISTANCE
                    if (farEnough || abs(velocity) > LIGHTBOX_DISMISS_VELOCITY) {
                        // Let the photo leave the screen in the direction it was thrown, then close.
                        val direction = if (dragOffset.value != 0f) sign(dragOffset.value) else sign(velocity)
                        dragOffset.animateTo(direction * viewerHeight, tween(180))
                        close()
                    } else {
                        dragOffset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                    }
                },
            ),
    ) {
        if (pageCount == 0) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center))
        } else {
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                key = { index -> images.getOrNull(index)?.id ?: index },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY = dragOffset.value
                        val scale = 1f - (1f - LIGHTBOX_DRAG_MIN_SCALE) * dragFraction
                        scaleX = scale
                        scaleY = scale
                    }
                    .pointerInput(pageCount) {
                        detectTapGestures { offset ->
                            val target = if (offset.x < size.width / 2f) pagerState.currentPage - 1 else pagerState.currentPage + 1
                            if (target in 0 until pageCount) scope.launch { pagerState.animateScrollToPage(target) }
                        }
                    },
            ) { page ->
                val image = images[page]
                // The small thumbnail shows at once; the full image fades in over it.
                val thumbnail = rememberStashThumbnailModel(image.thumbnailUrl ?: image.previewUrl, serverProfile)
                val full = rememberStashThumbnailModel(image.bestViewerImageUrl, serverProfile)
                Box(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(model = thumbnail, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                    AsyncImage(model = full, contentDescription = image.title, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = 1f - (dragFraction * 4f).coerceAtMost(1f) }
                .safeDrawingPadding()
                .background(Color.Black.copy(alpha = 0.35f))
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = close) {
                Icon(Icons.Outlined.Close, contentDescription = stashString(R.string.auto_kr_0192), tint = Color.White)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = request.title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stashString(R.string.feed_lightbox_counter, pagerState.currentPage + 1, maxOf(request.totalCount, pageCount)),
                    color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

/** Hides status and navigation bars while composed; swiping from an edge shows them briefly. */
@Composable
private fun ImmersiveSystemBars() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
