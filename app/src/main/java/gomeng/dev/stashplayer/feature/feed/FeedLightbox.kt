package gomeng.dev.stashplayer.feature.feed

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
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
import kotlinx.coroutines.launch

/** Start loading the next page this many images before the end, like the plugin. */
private const val LIGHTBOX_PRELOAD_DISTANCE = 4

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
)

/**
 * Full-screen photo viewer with the Binge plugin's gestures: swipe sideways to change photo, tap the left half
 * for the previous one and the right half for the next. Hides the system bars while open.
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
    BackHandler(onBack = onDismiss)
    ImmersiveSystemBars()

    LaunchedEffect(pagerState, pageCount) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            if (page >= pageCount - LIGHTBOX_PRELOAD_DISTANCE && pageCount < request.totalCount) request.onNeedMore()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
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
                .safeDrawingPadding()
                .background(Color.Black.copy(alpha = 0.35f))
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDismiss) {
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
