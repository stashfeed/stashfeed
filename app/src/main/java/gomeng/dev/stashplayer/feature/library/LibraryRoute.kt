package gomeng.dev.stashplayer.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import gomeng.dev.stashplayer.R
import gomeng.dev.stashplayer.core.local.StashLocalLibraryRepository
import gomeng.dev.stashplayer.core.network.StashSettingsRepository
import gomeng.dev.stashplayer.core.ui.designsystem.StashListGroup
import gomeng.dev.stashplayer.core.ui.designsystem.StashListRow
import gomeng.dev.stashplayer.core.ui.designsystem.StashRadius
import gomeng.dev.stashplayer.core.ui.designsystem.StashSpace
import gomeng.dev.stashplayer.core.ui.designsystem.StashSpacing
import gomeng.dev.stashplayer.core.ui.designsystem.StashTopBar
import gomeng.dev.stashplayer.core.ui.designsystem.stashPressScale
import gomeng.dev.stashplayer.core.ui.i18n.stashString
import gomeng.dev.stashplayer.core.ui.theme.StashBrandGradient
import gomeng.dev.stashplayer.feature.settings.SettingsListCopy
import gomeng.dev.stashplayer.feature.settings.SettingsSection

/**
 * The "You" page behind the avatar: who you're connected as, your on-device lists as tiles, and every settings
 * section as plain grouped rows, so nothing needs a tab of its own.
 */
@Composable
fun LibraryRoute(
    isFoldLikeLayout: Boolean,
    hasAvailableUpdate: Boolean,
    onOpenQueue: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDashboard: () -> Unit = {},
    onBack: () -> Unit = {},
    onOpenSettingsSection: (String) -> Unit = { onOpenSettings() },
) {
    val context = LocalContext.current
    val repository = remember(context) { StashLocalLibraryRepository(context) }
    val settingsRepository = remember(context) { StashSettingsRepository(context) }
    val profile by settingsRepository.serverProfile.collectAsState(initial = null)
    val queueScenes by repository.queueScenes.collectAsState(initial = emptyList())
    val watchLaterScenes by repository.watchLaterScenes.collectAsState(initial = emptyList())
    val favoriteScenes by repository.favoriteScenes.collectAsState(initial = emptyList())
    val historyScenes by repository.playbackHistoryScenes.collectAsState(initial = emptyList())
    val horizontalPadding = if (isFoldLikeLayout) StashSpacing.ScreenHorizontalExpanded else StashSpacing.ScreenHorizontalCompact

    Column(modifier = Modifier.fillMaxSize()) {
        StashTopBar(
            title = stashString(R.string.library_you_title),
            navigation = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stashString(R.string.performer_back))
                }
            },
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = horizontalPadding)
                .padding(top = StashSpace.S, bottom = StashSpacing.BottomContentPadding),
            verticalArrangement = Arrangement.spacedBy(StashSpace.XL),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(StashSpace.L)) {
                Box(
                    modifier = Modifier.size(56.dp).background(StashBrandGradient, CircleShape).padding(2.5.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        profile?.name?.trim()?.firstOrNull()?.uppercase() ?: "S",
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(profile?.name.orEmpty().ifBlank { stashString(R.string.app_name) }, style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = profile?.baseUrl?.removePrefix("https://")?.removePrefix("http://")?.trimEnd('/')
                            ?: stashString(R.string.library_not_connected),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(StashSpace.S + 2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(StashSpace.S + 2.dp)) {
                    LibraryTile(Icons.Outlined.FavoriteBorder, stashString(R.string.library_favorites_title), stashString(R.string.library_item_count, favoriteScenes.size), onOpenQueue, Modifier.weight(1f))
                    LibraryTile(Icons.Outlined.WatchLater, stashString(R.string.library_watch_later_title), stashString(R.string.library_item_count, watchLaterScenes.size), onOpenQueue, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(StashSpace.S + 2.dp)) {
                    LibraryTile(Icons.AutoMirrored.Outlined.PlaylistPlay, stashString(R.string.library_queue_title), stashString(R.string.library_item_count, queueScenes.size), onOpenQueue, Modifier.weight(1f))
                    LibraryTile(Icons.Outlined.History, stashString(R.string.library_history_title), stashString(R.string.library_item_count, historyScenes.size), onOpenQueue, Modifier.weight(1f))
                }
            }

            StashListGroup {
                StashListRow(
                    title = stashString(R.string.library_dashboard_title),
                    supporting = stashString(R.string.library_dashboard_subtitle),
                    icon = Icons.Outlined.PlayCircleOutline,
                    showDivider = false,
                    onClick = onOpenDashboard,
                )
            }

            StashListGroup(title = stashString(R.string.navigation_settings_label)) {
                val sections = SettingsListCopy.sections
                sections.forEachIndexed { index, section ->
                    StashListRow(
                        title = stringResource(section.title),
                        supporting = stringResource(section.description),
                        showBadge = hasAvailableUpdate && section == SettingsSection.Support,
                        showDivider = index != sections.lastIndex,
                        onClick = { onOpenSettingsSection(section.route) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(StashRadius.M),
        color = MaterialTheme.colorScheme.surfaceContainer,
        interactionSource = interaction,
        modifier = modifier.height(96.dp).stashPressScale(interaction),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
