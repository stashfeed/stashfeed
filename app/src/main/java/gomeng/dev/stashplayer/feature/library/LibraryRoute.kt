package gomeng.dev.stashplayer.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.material3.Badge
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import gomeng.dev.stashplayer.R
import gomeng.dev.stashplayer.core.local.StashLocalLibraryRepository
import gomeng.dev.stashplayer.core.ui.designsystem.StashSpacing
import gomeng.dev.stashplayer.core.ui.i18n.stashString

/** Hub for everything that isn't a feed: the on-device lists and the app settings. */
@Composable
fun LibraryRoute(
    isFoldLikeLayout: Boolean,
    hasAvailableUpdate: Boolean,
    onOpenQueue: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDashboard: () -> Unit = {},
) {
    val context = LocalContext.current
    val repository = remember(context) { StashLocalLibraryRepository(context) }
    val queueScenes by repository.queueScenes.collectAsState(initial = emptyList())
    val watchLaterScenes by repository.watchLaterScenes.collectAsState(initial = emptyList())
    val favoriteScenes by repository.favoriteScenes.collectAsState(initial = emptyList())
    val historyScenes by repository.playbackHistoryScenes.collectAsState(initial = emptyList())
    val horizontalPadding = if (isFoldLikeLayout) StashSpacing.ScreenHorizontalExpanded else StashSpacing.ScreenHorizontalCompact

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = horizontalPadding)
            .padding(top = StashSpacing.SectionGap, bottom = StashSpacing.BottomContentPadding),
        verticalArrangement = Arrangement.spacedBy(StashSpacing.CardGap),
    ) {
        Text(stashString(R.string.navigation_library_label), style = MaterialTheme.typography.headlineLarge)
        LibraryEntry(
            icon = Icons.Outlined.PlayCircleOutline,
            title = stashString(R.string.library_dashboard_title),
            subtitle = stashString(R.string.library_dashboard_subtitle),
            onClick = onOpenDashboard,
        )
        LibraryEntry(
            icon = Icons.AutoMirrored.Outlined.PlaylistPlay,
            title = stashString(R.string.library_queue_title),
            subtitle = stashString(R.string.library_item_count, queueScenes.size),
            onClick = onOpenQueue,
        )
        LibraryEntry(
            icon = Icons.Outlined.WatchLater,
            title = stashString(R.string.library_watch_later_title),
            subtitle = stashString(R.string.library_item_count, watchLaterScenes.size),
            onClick = onOpenQueue,
        )
        LibraryEntry(
            icon = Icons.Outlined.FavoriteBorder,
            title = stashString(R.string.library_favorites_title),
            subtitle = stashString(R.string.library_item_count, favoriteScenes.size),
            onClick = onOpenQueue,
        )
        LibraryEntry(
            icon = Icons.Outlined.History,
            title = stashString(R.string.library_history_title),
            subtitle = stashString(R.string.library_item_count, historyScenes.size),
            onClick = onOpenQueue,
        )
        LibraryEntry(
            icon = Icons.Outlined.Settings,
            title = stashString(R.string.navigation_settings_label),
            subtitle = stashString(R.string.library_settings_subtitle),
            showBadge = hasAvailableUpdate,
            onClick = onOpenSettings,
        )
    }
}

@Composable
private fun LibraryEntry(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    showBadge: Boolean = false,
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(StashSpacing.CardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(StashSpacing.CardPadding),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (showBadge) Badge()
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
        }
    }
}
