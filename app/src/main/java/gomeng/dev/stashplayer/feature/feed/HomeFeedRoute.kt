package gomeng.dev.stashplayer.feature.feed

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ViewCarousel
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import gomeng.dev.stashplayer.R
import gomeng.dev.stashplayer.core.local.HomeFeedSettingsRepository
import gomeng.dev.stashplayer.core.model.FeedPerformer
import gomeng.dev.stashplayer.core.model.HomeFeedItem
import gomeng.dev.stashplayer.core.model.HomeFeedKind
import gomeng.dev.stashplayer.core.model.HomeFeedSettings
import gomeng.dev.stashplayer.core.model.HomeStory
import gomeng.dev.stashplayer.core.model.SceneCardModel
import gomeng.dev.stashplayer.core.network.StashServerProfile
import gomeng.dev.stashplayer.core.network.StashSettingsRepository
import gomeng.dev.stashplayer.core.ui.components.rememberStashThumbnailModel
import gomeng.dev.stashplayer.core.ui.designsystem.StashEmptyState
import gomeng.dev.stashplayer.core.ui.designsystem.StashEmptyStateModel
import gomeng.dev.stashplayer.core.ui.designsystem.StashErrorState
import gomeng.dev.stashplayer.core.ui.designsystem.StashErrorStateModel
import gomeng.dev.stashplayer.core.ui.designsystem.StashRadii
import gomeng.dev.stashplayer.core.ui.designsystem.StashSpacing
import gomeng.dev.stashplayer.core.ui.i18n.stashString
import gomeng.dev.stashplayer.feature.performers.PerformerImage
import kotlinx.coroutines.launch

private val StoryRing = Brush.linearGradient(listOf(Color(0xFFF472B6), Color(0xFFA855F7), Color(0xFF6AA9FF)))

@Composable
fun HomeFeedRoute(
    isFoldLikeLayout: Boolean,
    onOpenScene: (sceneId: String, scenes: List<SceneCardModel>) -> Unit,
    onOpenGallery: (String) -> Unit,
    onOpenPerformer: (String) -> Unit,
) {
    val context = LocalContext.current
    val settingsRepository = remember(context) { StashSettingsRepository(context) }
    val feedSettingsRepository = remember(context) { HomeFeedSettingsRepository(context) }
    val profile by settingsRepository.serverProfile.collectAsState(initial = null)
    val feedSettings by feedSettingsRepository.settings.collectAsState(initial = null)
    val viewModel: HomeFeedViewModel = viewModel()
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    val horizontalPadding = if (isFoldLikeLayout) StashSpacing.ScreenHorizontalExpanded else StashSpacing.ScreenHorizontalCompact

    LaunchedEffect(profile, feedSettings, viewModel.filter) {
        val activeProfile = profile ?: return@LaunchedEffect
        val activeSettings = feedSettings ?: return@LaunchedEffect
        viewModel.bind(activeProfile, activeSettings)
    }

    val activeSettings = feedSettings ?: HomeFeedSettings()
    if (viewModel.isSettingsOpen) {
        HomeFeedSettingsSheet(
            settings = activeSettings,
            onSave = { updated -> scope.launch { feedSettingsRepository.save(updated) } },
            onDismiss = { viewModel.isSettingsOpen = false },
        )
    }
    if (viewModel.isFilterOpen) {
        HomeFeedFilterSheet(
            filter = viewModel.filter,
            profile = profile,
            onApply = { viewModel.filter = it },
            onDismiss = { viewModel.isFilterOpen = false },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(if (isFoldLikeLayout) 2 else 1),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = horizontalPadding,
                end = horizontalPadding,
                top = StashSpacing.SectionGap,
                bottom = StashSpacing.BottomContentPadding,
            ),
            horizontalArrangement = Arrangement.spacedBy(StashSpacing.CardGap),
            verticalArrangement = Arrangement.spacedBy(StashSpacing.SectionGap),
        ) {
            item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
                HomeFeedHeader(
                    filterCount = viewModel.filter.activeCount,
                    lookbackDays = activeSettings.lookbackDays,
                    onOpenFilter = { viewModel.isFilterOpen = true },
                    onOpenSettings = { viewModel.isSettingsOpen = true },
                    onRefresh = { profile?.let { viewModel.bind(it, activeSettings, force = true) } },
                )
            }
            when (val state = viewModel.state) {
                HomeFeedState.Loading -> item(key = "loading", span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is HomeFeedState.Failed -> item(key = "error", span = { GridItemSpan(maxLineSpan) }) {
                    StashErrorState(
                        state = StashErrorStateModel(title = stashString(R.string.home_feed_error_title), message = state.message),
                        onRetry = { profile?.let { viewModel.bind(it, activeSettings, force = true) } },
                    )
                }
                is HomeFeedState.Ready -> {
                    val feed = state.feed
                    if (feed.stories.isNotEmpty()) {
                        item(key = "stories", span = { GridItemSpan(maxLineSpan) }) {
                            StoriesRow(
                                stories = feed.stories,
                                serverProfile = profile,
                                onOpenStory = { index -> viewModel.openStoryIndex = index },
                            )
                        }
                    }
                    val hiddenUnidentified = HomeFeedKind.Unidentified in activeSettings.hiddenKinds && feed.unidentifiedSceneCount > 0
                    if (hiddenUnidentified) {
                        item(key = "unidentified", span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = stashString(R.string.home_feed_unidentified_hidden, feed.unidentifiedSceneCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (feed.items.isEmpty()) {
                        item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                            StashEmptyState(
                                state = StashEmptyStateModel(
                                    title = stashString(R.string.home_feed_empty_title),
                                    message = stashString(R.string.home_feed_empty_message, activeSettings.lookbackDays),
                                ),
                            )
                        }
                    }
                    val feedScenes = feed.items.flatMap { item ->
                        when (item) {
                            is HomeFeedItem.Post -> listOf(item.scene.scene)
                            is HomeFeedItem.Pack -> item.scenes.map { it.scene }
                            is HomeFeedItem.GalleryPost -> emptyList()
                        }
                    }
                    items(feed.items, key = { it.key }) { item ->
                        when (item) {
                            is HomeFeedItem.Post -> ScenePost(
                                post = item,
                                serverProfile = profile,
                                onOpen = { onOpenScene(item.scene.scene.id, feedScenes) },
                                onOpenPerformer = onOpenPerformer,
                            )
                            is HomeFeedItem.Pack -> PackPost(
                                pack = item,
                                serverProfile = profile,
                                onOpenScene = { scene -> onOpenScene(scene.id, item.scenes.map { it.scene }) },
                                onOpenPerformer = onOpenPerformer,
                            )
                            is HomeFeedItem.GalleryPost -> GalleryPost(
                                post = item,
                                serverProfile = profile,
                                onOpen = { onOpenGallery(item.gallery.gallery.id) },
                                onOpenPerformer = onOpenPerformer,
                            )
                        }
                    }
                }
            }
        }

        val ready = viewModel.state as? HomeFeedState.Ready
        val storyIndex = viewModel.openStoryIndex
        if (ready != null && storyIndex != null && storyIndex in ready.feed.stories.indices) {
            HomeStoryViewer(
                stories = ready.feed.stories,
                initialStoryIndex = storyIndex,
                serverProfile = profile,
                onDismiss = { viewModel.openStoryIndex = null },
                onOpenPerformer = { performerId ->
                    viewModel.openStoryIndex = null
                    onOpenPerformer(performerId)
                },
                onOpenScene = { scene, scenes ->
                    viewModel.openStoryIndex = null
                    onOpenScene(scene.id, scenes)
                },
            )
        }
    }
}

@Composable
private fun HomeFeedHeader(
    filterCount: Int,
    lookbackDays: Int,
    onOpenFilter: () -> Unit,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stashString(R.string.home_feed_title), style = MaterialTheme.typography.headlineLarge)
            Text(
                text = stashString(R.string.home_feed_window, lookbackDays),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onRefresh) {
            Icon(Icons.Outlined.Refresh, contentDescription = stashString(R.string.home_feed_refresh))
        }
        IconButton(onClick = onOpenFilter) {
            BadgedBox(badge = { if (filterCount > 0) Badge { Text(filterCount.toString()) } }) {
                Icon(Icons.Outlined.Tune, contentDescription = stashString(R.string.for_you_filter))
            }
        }
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Outlined.Settings, contentDescription = stashString(R.string.home_feed_settings))
        }
    }
}

@Composable
private fun StoriesRow(
    stories: List<HomeStory>,
    serverProfile: StashServerProfile?,
    onOpenStory: (Int) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(StashSpacing.CardGap)) {
        itemsIndexed(stories, key = { _, story -> story.performer.id }) { index, story ->
            Column(
                modifier = Modifier
                    .width(76.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onOpenStory(index) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box {
                    PerformerImage(
                        name = story.performer.name,
                        imageUrl = story.performer.imageUrl,
                        serverProfile = serverProfile,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(68.dp)
                            .border(BorderStroke(3.dp, StoryRing), CircleShape)
                            .padding(5.dp)
                            .clip(CircleShape),
                    )
                    Badge(modifier = Modifier.align(Alignment.BottomEnd)) { Text(story.scenes.size.toString()) }
                }
                Text(
                    text = story.performer.name,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** "Jane, Joe · Repost · 2 days ago" above every post; names open the profile. */
@Composable
private fun PostHeader(
    performers: List<FeedPerformer>,
    createdAt: String,
    serverProfile: StashServerProfile?,
    onOpenPerformer: (String) -> Unit,
    badge: String? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val lead = performers.firstOrNull()
        if (lead != null) {
            PerformerImage(
                name = lead.name,
                imageUrl = lead.imageUrl,
                serverProfile = serverProfile,
                shape = CircleShape,
                modifier = Modifier
                    .size(36.dp)
                    .clickable { onOpenPerformer(lead.id) },
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                performers.take(3).forEachIndexed { index, performer ->
                    Text(
                        text = performer.name + if (index < minOf(performers.size, 3) - 1) "," else "",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        modifier = Modifier.clickable { onOpenPerformer(performer.id) },
                    )
                }
                if (performers.size > 3) Text("+${performers.size - 3}", style = MaterialTheme.typography.titleSmall)
            }
            Text(
                text = listOfNotNull(badge, relativeAddedLabel(createdAt)).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ScenePost(
    post: HomeFeedItem.Post,
    serverProfile: StashServerProfile?,
    onOpen: () -> Unit,
    onOpenPerformer: (String) -> Unit,
) {
    val scene = post.scene.scene
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PostHeader(
            performers = post.scene.performers,
            createdAt = post.createdAt,
            serverProfile = serverProfile,
            onOpenPerformer = onOpenPerformer,
            badge = if (post.isRepost) stashString(R.string.home_feed_repost_badge) else null,
        )
        PostMedia(
            thumbnailUrl = scene.thumbnailUrl,
            serverProfile = serverProfile,
            badge = scene.durationText,
            badgeIcon = { Icon(if (post.isRepost) Icons.Outlined.Repeat else Icons.Outlined.PlayArrow, null, tint = Color.White, modifier = Modifier.size(14.dp)) },
            onClick = onOpen,
        )
        PostCaption(
            title = scene.title,
            subtitle = listOfNotNull(scene.studio.takeIf { it.isNotBlank() }, post.scene.date).joinToString(" · "),
            tags = scene.tagChips.take(4).map { it.label },
        )
    }
}

@Composable
private fun GalleryPost(
    post: HomeFeedItem.GalleryPost,
    serverProfile: StashServerProfile?,
    onOpen: () -> Unit,
    onOpenPerformer: (String) -> Unit,
) {
    val gallery = post.gallery.gallery
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PostHeader(post.gallery.performers, post.createdAt, serverProfile, onOpenPerformer)
        PostMedia(
            thumbnailUrl = gallery.bestThumbnailUrl,
            serverProfile = serverProfile,
            badge = stashString(R.string.performer_feed_gallery_badge, gallery.imageCount ?: 0),
            badgeIcon = { Icon(Icons.Outlined.PhotoLibrary, null, tint = Color.White, modifier = Modifier.size(14.dp)) },
            onClick = onOpen,
            aspectRatio = 4f / 3f,
        )
        PostCaption(
            title = gallery.title,
            subtitle = listOfNotNull(gallery.studio, gallery.date).joinToString(" · "),
            tags = emptyList(),
        )
    }
}

/** A performer's burst of new scenes: header plus a swipeable strip of the scenes. */
@Composable
private fun PackPost(
    pack: HomeFeedItem.Pack,
    serverProfile: StashServerProfile?,
    onOpenScene: (SceneCardModel) -> Unit,
    onOpenPerformer: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PostHeader(
            performers = listOf(pack.performer),
            createdAt = pack.createdAt,
            serverProfile = serverProfile,
            onOpenPerformer = onOpenPerformer,
            badge = stashString(R.string.home_feed_pack_badge, pack.scenes.size),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(pack.scenes, key = { _, scene -> scene.scene.id }) { _, scene ->
                Column(modifier = Modifier.width(220.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PostMedia(
                        thumbnailUrl = scene.scene.thumbnailUrl,
                        serverProfile = serverProfile,
                        badge = scene.scene.durationText,
                        badgeIcon = { Icon(Icons.Outlined.ViewCarousel, null, tint = Color.White, modifier = Modifier.size(14.dp)) },
                        onClick = { onOpenScene(scene.scene) },
                    )
                    Text(scene.scene.title, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun PostMedia(
    thumbnailUrl: String?,
    serverProfile: StashServerProfile?,
    badge: String,
    badgeIcon: @Composable () -> Unit,
    onClick: () -> Unit,
    aspectRatio: Float = 16f / 9f,
) {
    val model = rememberStashThumbnailModel(thumbnailUrl, serverProfile)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .clip(RoundedCornerShape(StashRadii.Thumbnail))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
    ) {
        if (model != null) {
            AsyncImage(model = model, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        if (badge.isNotBlank()) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.62f), RoundedCornerShape(StashRadii.Pill))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                badgeIcon()
                Text(badge, style = MaterialTheme.typography.labelSmall, color = Color.White)
            }
        }
    }
}

@Composable
private fun PostCaption(title: String, subtitle: String, tags: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val line = listOf(subtitle, tags.joinToString(" ") { "#$it" }).filter { it.isNotBlank() }.joinToString(" · ")
        if (line.isNotBlank()) {
            Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.size(2.dp))
    }
}

/** "today", "yesterday", "5 days ago" — or the date when it's older than a month. */
@Composable
internal fun relativeAddedLabel(createdAt: String): String {
    val millis = createdAt.toEpochMillisCompat() ?: return createdAt.take(10)
    val days = ((System.currentTimeMillis() - millis) / 86_400_000L).toInt()
    return when {
        days <= 0 -> stashString(R.string.home_feed_added_today)
        days == 1 -> stashString(R.string.home_feed_added_yesterday)
        days < 31 -> stashString(R.string.home_feed_added_days_ago, days)
        else -> createdAt.take(10)
    }
}

private fun String.toEpochMillisCompat(): Long? = runCatching { java.time.OffsetDateTime.parse(this).toInstant().toEpochMilli() }.getOrNull()
    ?: runCatching { java.time.Instant.parse(this).toEpochMilli() }.getOrNull()
