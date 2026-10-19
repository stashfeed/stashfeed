package gomeng.dev.stashplayer.feature.performers

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import gomeng.dev.stashplayer.R
import gomeng.dev.stashplayer.core.model.GalleryImageModel
import gomeng.dev.stashplayer.core.model.PerformerDetailModel
import gomeng.dev.stashplayer.core.model.PerformerFeedItem
import gomeng.dev.stashplayer.core.model.PerformerSceneSort
import gomeng.dev.stashplayer.core.model.SceneCardModel
import gomeng.dev.stashplayer.core.network.StashServerProfile
import gomeng.dev.stashplayer.core.network.StashSettingsRepository
import gomeng.dev.stashplayer.core.ui.components.SceneCard
import gomeng.dev.stashplayer.core.ui.components.rememberStashThumbnailModel
import gomeng.dev.stashplayer.core.ui.designsystem.StashEmptyState
import gomeng.dev.stashplayer.core.ui.designsystem.StashEmptyStateModel
import gomeng.dev.stashplayer.core.ui.designsystem.StashErrorState
import gomeng.dev.stashplayer.core.ui.designsystem.StashErrorStateModel
import gomeng.dev.stashplayer.core.ui.designsystem.StashRadii
import gomeng.dev.stashplayer.core.ui.designsystem.StashSpacing
import gomeng.dev.stashplayer.core.ui.designsystem.StashTagChip
import gomeng.dev.stashplayer.core.ui.designsystem.StashTagChipModel
import gomeng.dev.stashplayer.core.ui.i18n.stashString
import kotlinx.coroutines.launch

/** The grid has 12 lanes so feed posts, scene cards and photo tiles can use different column counts. */
private const val PROFILE_GRID_LANES = 12
private const val PROFILE_LOAD_MORE_THRESHOLD = 6
private const val PROFILE_VISIBLE_TAGS = 10

@Composable
fun PerformerProfileRoute(
    performerId: String,
    isFoldLikeLayout: Boolean,
    onNavigateBack: () -> Unit,
    onOpenScene: (sceneId: String, scenes: List<SceneCardModel>, shuffle: Boolean) -> Unit,
    onOpenGallery: (String) -> Unit,
    onOpenPhoto: (index: Int, images: List<GalleryImageModel>) -> Unit,
) {
    val context = LocalContext.current
    val settingsRepository = remember(context) { StashSettingsRepository(context) }
    val profile by settingsRepository.serverProfile.collectAsState(initial = null)
    val viewModel: PerformerProfileViewModel = viewModel()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    val horizontalPadding = if (isFoldLikeLayout) StashSpacing.ScreenHorizontalExpanded else StashSpacing.ScreenHorizontalCompact
    val feedSpan = if (isFoldLikeLayout) 6 else PROFILE_GRID_LANES
    val sceneSpan = if (isFoldLikeLayout) 4 else 6
    val photoSpan = if (isFoldLikeLayout) 3 else 4

    LaunchedEffect(profile, performerId) {
        profile?.let { viewModel.bind(it, performerId) }
    }

    val feedItems = viewModel.feedState.merge.emitted
    val sceneState = viewModel.scenes.state
    val photoState = viewModel.photos.state
    val activeItemCount = when (viewModel.tab) {
        PerformerProfileTab.Feed -> feedItems.size
        PerformerProfileTab.Scenes -> sceneState.items.size
        PerformerProfileTab.Photos -> photoState.items.size
    }

    LaunchedEffect(gridState, viewModel.tab, activeItemCount) {
        snapshotFlow {
            val layout = gridState.layoutInfo
            (layout.visibleItemsInfo.lastOrNull()?.index ?: 0) >= layout.totalItemsCount - PROFILE_LOAD_MORE_THRESHOLD
        }.collect { nearEnd ->
            if (!nearEnd) return@collect
            when (viewModel.tab) {
                PerformerProfileTab.Feed -> viewModel.loadMoreFeed()
                PerformerProfileTab.Scenes -> viewModel.scenes.loadNext()
                PerformerProfileTab.Photos -> viewModel.photos.loadNext()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(PROFILE_GRID_LANES),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = horizontalPadding,
                end = horizontalPadding,
                bottom = StashSpacing.BottomContentPadding,
            ),
            horizontalArrangement = Arrangement.spacedBy(StashSpacing.CardGap),
            verticalArrangement = Arrangement.spacedBy(StashSpacing.CardGap),
        ) {
            val fullSpan: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }

            item(key = "top_bar", span = fullSpan) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stashString(R.string.performer_back))
                    }
                }
            }

            when (val detail = viewModel.detailState) {
                PerformerDetailState.Loading -> item(key = "header_loading", span = fullSpan) {
                    PagedFooter(isLoading = true, error = null, onRetry = {})
                }
                is PerformerDetailState.Failed -> item(key = "header_error", span = fullSpan) {
                    StashErrorState(
                        state = StashErrorStateModel(title = stashString(R.string.performer_load_failed), message = detail.message),
                        onRetry = viewModel::loadDetail,
                    )
                }
                PerformerDetailState.NotFound -> item(key = "header_missing", span = fullSpan) {
                    StashEmptyState(
                        state = StashEmptyStateModel(title = stashString(R.string.performer_not_found), message = ""),
                    )
                }
                is PerformerDetailState.Ready -> item(key = "header", span = fullSpan) {
                    PerformerHeader(
                        detail = detail.detail,
                        serverProfile = profile,
                        isUpdatingFollow = viewModel.isUpdatingFollow,
                        onToggleFollow = {
                            viewModel.toggleFollow { message ->
                                scope.launch {
                                    snackbarHostState.showSnackbar(stashString(R.string.performer_follow_failed, message))
                                }
                            }
                        },
                    )
                }
            }

            item(key = "tabs", span = fullSpan) {
                TabRow(selectedTabIndex = viewModel.tab.ordinal) {
                    PerformerProfileTab.entries.forEach { entry ->
                        Tab(
                            selected = viewModel.tab == entry,
                            onClick = { viewModel.tab = entry },
                            text = {
                                Text(
                                    when (entry) {
                                        PerformerProfileTab.Feed -> stashString(R.string.performer_tab_feed)
                                        PerformerProfileTab.Scenes -> stashString(R.string.performer_tab_scenes)
                                        PerformerProfileTab.Photos -> stashString(R.string.performer_tab_photos)
                                    },
                                )
                            },
                        )
                    }
                }
            }

            when (viewModel.tab) {
                PerformerProfileTab.Feed -> {
                    val feedScenes = feedItems.filterIsInstance<PerformerFeedItem.Scene>().map { it.scene }
                    itemsIndexed(feedItems, key = { _, item -> item.key }, span = { _, _ -> GridItemSpan(feedSpan) }) { _, item ->
                        PerformerFeedPost(
                            item = item,
                            serverProfile = profile,
                            onClick = {
                                when (item) {
                                    is PerformerFeedItem.Scene -> onOpenScene(item.scene.id, feedScenes, false)
                                    is PerformerFeedItem.Gallery -> onOpenGallery(item.gallery.id)
                                }
                            },
                        )
                    }
                    val feed = viewModel.feedState
                    when {
                        feed.isLoading || feed.error != null -> item(key = "feed_footer", span = fullSpan) {
                            PagedFooter(isLoading = feed.isLoading, error = feed.error, onRetry = viewModel::retryFeed)
                        }
                        feed.hasLoadedOnce && feedItems.isEmpty() -> item(key = "feed_empty", span = fullSpan) {
                            ProfileEmptyText(stashString(R.string.performer_feed_empty))
                        }
                    }
                }

                PerformerProfileTab.Scenes -> {
                    item(key = "scene_controls", span = fullSpan) {
                        SceneSortRow(
                            selected = viewModel.sceneSort,
                            canPlay = sceneState.items.isNotEmpty(),
                            onSelect = viewModel::selectSceneSort,
                            onPlayAll = {
                                sceneState.items.firstOrNull()?.let { onOpenScene(it.id, sceneState.items, false) }
                            },
                        )
                    }
                    itemsIndexed(sceneState.items, key = { _, scene -> scene.id }, span = { _, _ -> GridItemSpan(sceneSpan) }) { _, scene ->
                        SceneCard(
                            scene = scene,
                            thumbnailModel = rememberStashThumbnailModel(scene.thumbnailUrl, profile),
                            thumbnailHeight = if (isFoldLikeLayout) 150.dp else 110.dp,
                            showQuickActions = false,
                            onClick = { onOpenScene(scene.id, sceneState.items, false) },
                        )
                    }
                    pagedFooterItems(
                        key = "scenes",
                        isLoading = sceneState.isLoading,
                        error = sceneState.error,
                        isEmpty = sceneState.hasLoadedOnce && sceneState.items.isEmpty(),
                        emptyText = stashString(R.string.performer_scenes_empty),
                        onRetry = viewModel.scenes::retry,
                    )
                }

                PerformerProfileTab.Photos -> {
                    itemsIndexed(photoState.items, key = { _, image -> image.id }, span = { _, _ -> GridItemSpan(photoSpan) }) { index, image ->
                        val model = rememberStashThumbnailModel(image.thumbnailUrl ?: image.previewUrl, profile)
                        AsyncImage(
                            model = model,
                            contentDescription = image.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { onOpenPhoto(index, photoState.items) },
                        )
                    }
                    pagedFooterItems(
                        key = "photos",
                        isLoading = photoState.isLoading,
                        error = photoState.error,
                        isEmpty = photoState.hasLoadedOnce && photoState.items.isEmpty(),
                        emptyText = stashString(R.string.performer_photos_empty),
                        onRetry = viewModel.photos::retry,
                    )
                }
            }
        }
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

private fun androidx.compose.foundation.lazy.grid.LazyGridScope.pagedFooterItems(
    key: String,
    isLoading: Boolean,
    error: String?,
    isEmpty: Boolean,
    emptyText: String,
    onRetry: () -> Unit,
) {
    when {
        isLoading || error != null -> item(key = "${key}_footer", span = { GridItemSpan(maxLineSpan) }) {
            PagedFooter(isLoading = isLoading, error = error, onRetry = onRetry)
        }
        isEmpty -> item(key = "${key}_empty", span = { GridItemSpan(maxLineSpan) }) {
            ProfileEmptyText(emptyText)
        }
    }
}

@Composable
private fun ProfileEmptyText(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = StashSpacing.SectionGap),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PerformerHeader(
    detail: PerformerDetailModel,
    serverProfile: StashServerProfile?,
    isUpdatingFollow: Boolean,
    onToggleFollow: () -> Unit,
) {
    val card = detail.card
    var detailsExpanded by rememberSaveable(card.id) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(StashSpacing.CardGap)) {
        Row(horizontalArrangement = Arrangement.spacedBy(StashSpacing.CardPadding)) {
            PerformerImage(
                name = card.name,
                imageUrl = card.imageUrl,
                serverProfile = serverProfile,
                modifier = Modifier
                    .width(112.dp)
                    .aspectRatio(3f / 4f),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = card.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (detail.aliases.isNotEmpty()) {
                    Text(
                        text = stashString(R.string.performer_aliases, detail.aliases.joinToString(", ")),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val facts = listOfNotNull(
                    detail.birthdate,
                    detail.country,
                    detail.heightCm?.let { "$it cm" },
                    detail.hairColor,
                ).joinToString(" · ")
                if (facts.isNotBlank()) {
                    Text(facts, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (card.favorite) {
                    OutlinedButton(onClick = onToggleFollow, enabled = !isUpdatingFollow) {
                        Icon(Icons.Filled.Favorite, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stashString(R.string.performer_following), modifier = Modifier.padding(start = 6.dp))
                    }
                } else {
                    Button(onClick = onToggleFollow, enabled = !isUpdatingFollow) {
                        Icon(Icons.Outlined.FavoriteBorder, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stashString(R.string.performer_follow), modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(StashSpacing.CardGap)) {
            PerformerStat(card.sceneCount, stashString(R.string.performer_stat_scenes), Modifier.weight(1f))
            PerformerStat(card.galleryCount, stashString(R.string.performer_stat_galleries), Modifier.weight(1f))
            PerformerStat(card.imageCount, stashString(R.string.performer_stat_images), Modifier.weight(1f))
            PerformerStat(card.oCounter ?: 0, stashString(R.string.performer_stat_o), Modifier.weight(1f))
        }
        detail.details?.let { text ->
            Column {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (detailsExpanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                )
                TextButton(onClick = { detailsExpanded = !detailsExpanded }) {
                    Text(stashString(if (detailsExpanded) R.string.performer_details_less else R.string.performer_details_more))
                }
            }
        }
        if (detail.tags.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(StashSpacing.ChipGap),
                verticalArrangement = Arrangement.spacedBy(StashSpacing.ChipGap),
            ) {
                detail.tags.take(PROFILE_VISIBLE_TAGS).forEach { tag -> StashTagChip(tag = StashTagChipModel(label = tag.label)) }
            }
        }
    }
}

@Composable
private fun PerformerStat(value: Int, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SceneSortRow(
    selected: PerformerSceneSort,
    canPlay: Boolean,
    onSelect: (PerformerSceneSort) -> Unit,
    onPlayAll: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(StashSpacing.ChipGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(onClick = onPlayAll, enabled = canPlay) {
            Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stashString(R.string.performer_play_all), modifier = Modifier.padding(start = 4.dp))
        }
        PerformerSceneSort.entries.forEach { sort ->
            FilterChip(
                selected = sort == selected,
                onClick = { onSelect(sort) },
                label = { Text(stashString(sort.labelRes)) },
            )
        }
    }
}

/** A feed post: big thumbnail with a type badge, then title and when it was added. */
@Composable
private fun PerformerFeedPost(
    item: PerformerFeedItem,
    serverProfile: StashServerProfile?,
    onClick: () -> Unit,
) {
    val (title, thumbnailUrl, badge, subtitle) = when (item) {
        is PerformerFeedItem.Scene -> FeedPostText(
            title = item.scene.title,
            thumbnailUrl = item.scene.thumbnailUrl,
            badge = item.scene.durationText.ifBlank { stashString(R.string.performer_feed_scene_badge) },
            subtitle = listOfNotNull(
                item.scene.studio.takeIf { it.isNotBlank() },
                item.date,
            ).joinToString(" · "),
        )
        is PerformerFeedItem.Gallery -> FeedPostText(
            title = item.gallery.title,
            thumbnailUrl = item.gallery.bestThumbnailUrl,
            badge = stashString(R.string.performer_feed_gallery_badge, item.gallery.imageCount ?: 0),
            subtitle = listOfNotNull(item.gallery.studio, item.gallery.date).joinToString(" · "),
        )
    }
    val model = rememberStashThumbnailModel(thumbnailUrl, serverProfile)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(StashRadii.Thumbnail))
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(StashRadii.Thumbnail))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (model != null) {
                AsyncImage(
                    model = model,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.62f), RoundedCornerShape(StashRadii.Pill))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = if (item is PerformerFeedItem.Gallery) Icons.Outlined.PhotoLibrary else Icons.Outlined.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
                Text(badge, style = MaterialTheme.typography.labelSmall, color = Color.White)
            }
        }
        Column(modifier = Modifier.padding(horizontal = 4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val added = item.createdAt.take(10).takeIf { it.length == 10 }?.let { stashString(R.string.performer_feed_added, it) }
            val line = listOfNotNull(subtitle.takeIf { it.isNotBlank() }, added).joinToString(" · ")
            if (line.isNotBlank()) {
                Text(
                    line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box(Modifier.height(4.dp))
    }
}

private data class FeedPostText(
    val title: String,
    val thumbnailUrl: String?,
    val badge: String,
    val subtitle: String,
)
