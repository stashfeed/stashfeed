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
import androidx.compose.material3.Surface
import androidx.compose.ui.platform.LocalConfiguration
import gomeng.dev.stashplayer.core.model.FeedGallery
import gomeng.dev.stashplayer.core.model.FeedScene
import gomeng.dev.stashplayer.core.model.HomeFeedItem
import gomeng.dev.stashplayer.core.model.StashGalleryFilterState
import gomeng.dev.stashplayer.core.model.StashImageFilterState
import gomeng.dev.stashplayer.core.model.StashSelectedEntity
import gomeng.dev.stashplayer.core.model.StashSortDirection
import gomeng.dev.stashplayer.core.model.clearStashImageFilterCategory
import gomeng.dev.stashplayer.core.model.stashImageSortOptions
import gomeng.dev.stashplayer.core.network.StashGraphQlClient
import gomeng.dev.stashplayer.core.ui.components.StashBrowseFilterRow
import gomeng.dev.stashplayer.core.ui.components.StashBrowseLayout
import gomeng.dev.stashplayer.core.ui.components.StashHeaderChip
import gomeng.dev.stashplayer.core.ui.discovery.StashVideoFilterDraft
import gomeng.dev.stashplayer.core.ui.discovery.StashVideoFilterScreen
import gomeng.dev.stashplayer.core.ui.discovery.headerChips
import gomeng.dev.stashplayer.core.ui.theme.LocalStashStyleTokens
import gomeng.dev.stashplayer.feature.feed.FeedLightboxRequest
import gomeng.dev.stashplayer.feature.feed.GalleryPost
import gomeng.dev.stashplayer.feature.feed.PostCard
import gomeng.dev.stashplayer.feature.feed.ScenePost
import gomeng.dev.stashplayer.feature.gallery.GalleryEntitySectionSpec
import gomeng.dev.stashplayer.feature.gallery.GalleryFilterChipSpec
import gomeng.dev.stashplayer.feature.gallery.GalleryFilterDraft
import gomeng.dev.stashplayer.feature.gallery.GalleryLikeFilterScreen
import gomeng.dev.stashplayer.feature.gallery.GalleryPhotoLargeCard
import gomeng.dev.stashplayer.feature.gallery.GalleryPhotoTile
import gomeng.dev.stashplayer.feature.gallery.ImageEntityFilterKind
import gomeng.dev.stashplayer.feature.gallery.imageSectionKey
import gomeng.dev.stashplayer.feature.gallery.searchLabel
import gomeng.dev.stashplayer.feature.gallery.selectedFrom
import gomeng.dev.stashplayer.feature.gallery.title
import gomeng.dev.stashplayer.feature.gallery.withSelected
import gomeng.dev.stashplayer.feature.shorts.ForYouRatingDialog
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
    onOpenPerformer: (String) -> Unit = {},
    onOpenLightbox: (FeedLightboxRequest) -> Unit = {},
) {
    val context = LocalContext.current
    val settingsRepository = remember(context) { StashSettingsRepository(context) }
    val profile by settingsRepository.serverProfile.collectAsState(initial = null)
    val viewModel: PerformerProfileViewModel = viewModel()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    val tokens = LocalStashStyleTokens.current
    val horizontalPadding = if (isFoldLikeLayout) StashSpacing.ScreenHorizontalExpanded else StashSpacing.ScreenHorizontalCompact
    val isFeed = viewModel.tab == PerformerProfileTab.Feed
    // Feed posts run edge to edge like on Home; everything else keeps the screen padding.
    val itemPadding = if (isFeed) Modifier.padding(horizontal = horizontalPadding) else Modifier
    val feedSpan = if (isFoldLikeLayout) 6 else PROFILE_GRID_LANES
    val sceneLarge = viewModel.sceneLayout == StashBrowseLayout.Large
    val photoLarge = viewModel.photoLayout == StashBrowseLayout.Large
    val sceneSpan = when {
        sceneLarge -> if (isFoldLikeLayout) 6 else PROFILE_GRID_LANES
        isFoldLikeLayout -> 4
        else -> 6
    }
    val photoSpan = when {
        photoLarge -> if (isFoldLikeLayout) 6 else PROFILE_GRID_LANES
        isFoldLikeLayout -> 3
        else -> 4
    }
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val largeSceneThumbnailHeight = ((screenWidthDp - horizontalPadding.value * 2) * 9f / 16f).dp
    var ratingTarget by remember { mutableStateOf<SceneCardModel?>(null) }
    var filterOpen by rememberSaveable { mutableStateOf(false) }
    var filterSection by rememberSaveable { mutableStateOf<String?>(null) }
    fun openFilter(section: String?) {
        filterSection = section
        filterOpen = true
    }

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

    ratingTarget?.let { target ->
        ForYouRatingDialog(
            scene = target,
            onRate = { rating100 ->
                ratingTarget = null
                viewModel.rateScene(target, rating100)
            },
            onDismiss = { ratingTarget = null },
        )
    }

    if (filterOpen) {
        val client = profile?.let { StashGraphQlClient(it) }
        when (viewModel.tab) {
            PerformerProfileTab.Photos -> GalleryLikeFilterScreen(
                initial = GalleryFilterDraft(viewModel.photoSort, viewModel.photoDirection, 0, viewModel.photoFilter),
                sortOptions = stashImageSortOptions(),
                pageSizeOptions = emptyList(),
                emptyFilter = StashImageFilterState(),
                entitySections = listOf(ImageEntityFilterKind.Tags, ImageEntityFilterKind.Studios, ImageEntityFilterKind.Galleries).map { kind ->
                    GalleryEntitySectionSpec(
                        key = kind.name,
                        title = kind.title(),
                        searchLabel = kind.searchLabel(),
                        selected = { kind.selectedFrom(it) },
                        withSelected = { filter, picked -> kind.withSelected(filter, picked) },
                        search = { query ->
                            when (kind) {
                                ImageEntityFilterKind.Studios -> client?.findStudios(query = query).orEmpty()
                                ImageEntityFilterKind.Galleries -> client?.findGalleryCardsPage(
                                    perPage = 40,
                                    query = query,
                                    sort = "title",
                                    direction = StashSortDirection.Asc,
                                    galleryFilter = StashGalleryFilterState(performers = listOf(StashSelectedEntity(performerId, performerId))),
                                )?.galleries?.map { StashSelectedEntity(it.id, it.title) }.orEmpty()
                                else -> client?.findTags(query = query)?.map { StashSelectedEntity(it.id, it.name) }.orEmpty()
                            }
                        },
                    )
                },
                rating = { it.ratingRange },
                withRating = { filter, range -> filter.copy(ratingRange = range) },
                chips = { filter -> filter.photoChips() },
                localSaved = emptyList(),
                serverFilters = emptyList(),
                savedRefId = { null },
                applyServer = { filter, _ -> filter },
                clearSaved = { it },
                onSaveFilter = { _, _ -> },
                onDeleteSavedFilter = {},
                countResults = viewModel::countPhotos,
                onApply = viewModel::applyPhotoDraft,
                onDismiss = { filterOpen = false },
                initialSection = filterSection,
                allowSaving = false,
            )
            else -> StashVideoFilterScreen(
                initial = StashVideoFilterDraft(viewModel.sceneSort, viewModel.sceneDirection, null, viewModel.sceneFilter),
                sortOptions = PerformerSceneSort.entries,
                sortLabel = { stashString(it.labelRes) },
                pageSizeOptions = null,
                searchTags = { query -> client?.findTags(query = query)?.map { StashSelectedEntity(it.id, it.name) }.orEmpty() },
                searchPerformers = null,
                searchStudios = { query -> client?.findStudios(query = query).orEmpty() },
                countResults = viewModel::countScenes,
                onApply = viewModel::applySceneDraft,
                onDismiss = { filterOpen = false },
                initialSection = filterSection,
                showRandom = false,
                showLocal = false,
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(PROFILE_GRID_LANES),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = if (isFeed) 0.dp else horizontalPadding,
                end = if (isFeed) 0.dp else horizontalPadding,
                bottom = StashSpacing.BottomContentPadding,
            ),
            horizontalArrangement = Arrangement.spacedBy(StashSpacing.CardGap),
            verticalArrangement = Arrangement.spacedBy(if (isFeed) tokens.feedPostGap else StashSpacing.CardGap),
        ) {
            val fullSpan: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }

            item(key = "top_bar", span = fullSpan) {
                Row(modifier = itemPadding, verticalAlignment = Alignment.CenterVertically) {
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
                        modifier = itemPadding,
                        onRetry = viewModel::loadDetail,
                    )
                }
                PerformerDetailState.NotFound -> item(key = "header_missing", span = fullSpan) {
                    StashEmptyState(
                        state = StashEmptyStateModel(title = stashString(R.string.performer_not_found), message = ""),
                        modifier = itemPadding,
                    )
                }
                is PerformerDetailState.Ready -> item(key = "header", span = fullSpan) {
                    Box(itemPadding) {
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
            }

            item(key = "tabs", span = fullSpan) {
                TabRow(selectedTabIndex = viewModel.tab.ordinal, modifier = itemPadding) {
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
                    val feedScenes = feedItems.filterIsInstance<PerformerFeedItem.Scene>().map { viewModel.sceneOverrides[it.scene.id] ?: it.scene }
                    itemsIndexed(
                        feedItems,
                        key = { _, item -> item.key },
                        span = { _, _ -> GridItemSpan(feedSpan) },
                        contentType = { _, item -> if (item is PerformerFeedItem.Scene) "post" else "gallery" },
                    ) { _, item ->
                        PostCard(tokens) {
                            when (item) {
                                is PerformerFeedItem.Scene -> {
                                    val scene = viewModel.sceneOverrides[item.scene.id] ?: item.scene
                                    val feedScene = (item.feed ?: FeedScene(scene = scene, performers = emptyList(), createdAt = item.createdAt, date = item.date))
                                        .copy(scene = scene)
                                    ScenePost(
                                        post = HomeFeedItem.Post(feedScene, isRepost = false),
                                        serverProfile = profile,
                                        tokens = tokens,
                                        onOpen = { onOpenScene(scene.id, feedScenes, false) },
                                        onOpenPerformer = { id -> if (id != performerId) onOpenPerformer(id) },
                                        onRate = { ratingTarget = scene },
                                        onAddO = { viewModel.addO(scene) },
                                        onRemoveO = { viewModel.removeO(scene) },
                                    )
                                }
                                is PerformerFeedItem.Gallery -> {
                                    val feedGallery = item.feed ?: FeedGallery(gallery = item.gallery, performers = emptyList(), createdAt = item.createdAt)
                                    val galleryId = item.gallery.id
                                    GalleryPost(
                                        post = HomeFeedItem.GalleryPost(feedGallery),
                                        images = viewModel.galleryImages[galleryId],
                                        serverProfile = profile,
                                        tokens = tokens,
                                        onNeedImages = { viewModel.galleryImages.ensure(galleryId) },
                                        onNeedMoreImages = { viewModel.galleryImages.loadMore(galleryId) },
                                        onOpenPhoto = { index ->
                                            onOpenLightbox(
                                                FeedLightboxRequest(
                                                    title = item.gallery.title,
                                                    startIndex = index,
                                                    totalCount = item.gallery.imageCount ?: 0,
                                                    images = { viewModel.galleryImages[galleryId]?.images.orEmpty() },
                                                    onNeedMore = { viewModel.galleryImages.loadMore(galleryId) },
                                                ),
                                            )
                                        },
                                        onOpenGallery = { onOpenGallery(galleryId) },
                                        onOpenPerformer = { id -> if (id != performerId) onOpenPerformer(id) },
                                    )
                                }
                            }
                        }
                    }
                    val feed = viewModel.feedState
                    when {
                        feed.isLoading || feed.error != null -> item(key = "feed_footer", span = fullSpan) {
                            PagedFooter(isLoading = feed.isLoading, error = feed.error, onRetry = viewModel::retryFeed)
                        }
                        feed.hasLoadedOnce && feedItems.isEmpty() -> item(key = "feed_empty", span = fullSpan) {
                            Box(itemPadding) { ProfileEmptyText(stashString(R.string.performer_feed_empty)) }
                        }
                    }
                }

                PerformerProfileTab.Scenes -> {
                    item(key = "scene_controls", span = fullSpan) {
                        StashBrowseFilterRow(
                            filterCount = viewModel.sceneFilter.activeFilterCount,
                            onOpenFilter = { openFilter(null) },
                            layout = viewModel.sceneLayout,
                            onToggleLayout = { viewModel.sceneLayout = if (sceneLarge) StashBrowseLayout.Compact else StashBrowseLayout.Large },
                            horizontalPadding = 0.dp,
                            enabled = profile != null,
                            leadingControls = {
                                PlayAllButton(enabled = sceneState.items.isNotEmpty()) {
                                    sceneState.items.firstOrNull()?.let { onOpenScene(it.id, sceneState.items, false) }
                                }
                            },
                            chips = listOf(
                                StashHeaderChip(
                                    key = "sort",
                                    label = stashString(viewModel.sceneSort.labelRes) + if (viewModel.sceneDirection == StashSortDirection.Asc) " ↑" else " ↓",
                                    onClick = { openFilter(null) },
                                    onClear = null,
                                ),
                            ) + viewModel.sceneFilter.headerChips(onOpenSection = ::openFilter, onApply = viewModel::clearSceneFilter),
                        )
                    }
                    itemsIndexed(
                        sceneState.items,
                        key = { _, scene -> scene.id },
                        span = { _, _ -> GridItemSpan(sceneSpan) },
                        contentType = { _, _ -> "scene" },
                    ) { _, scene ->
                        SceneCard(
                            scene = scene,
                            thumbnailModel = rememberStashThumbnailModel(scene.thumbnailUrl, profile),
                            thumbnailHeight = if (sceneLarge) largeSceneThumbnailHeight else if (isFoldLikeLayout) 150.dp else 110.dp,
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
                    item(key = "photo_controls", span = fullSpan) {
                        StashBrowseFilterRow(
                            filterCount = viewModel.photoFilter.activeFilterCount,
                            onOpenFilter = { openFilter(null) },
                            layout = viewModel.photoLayout,
                            onToggleLayout = { viewModel.photoLayout = if (photoLarge) StashBrowseLayout.Compact else StashBrowseLayout.Large },
                            horizontalPadding = 0.dp,
                            enabled = profile != null,
                            leadingControls = null,
                            chips = listOf(
                                StashHeaderChip(
                                    key = "sort",
                                    label = viewModel.photoSort.label + if (viewModel.photoDirection == StashSortDirection.Asc) " ↑" else " ↓",
                                    onClick = { openFilter(null) },
                                    onClear = null,
                                ),
                            ) + viewModel.photoFilter.photoChips().map { chip ->
                                StashHeaderChip(
                                    key = chip.key,
                                    label = chip.label,
                                    onClick = { openFilter(chip.sectionKey) },
                                    onClear = { viewModel.clearPhotoFilter(chip.clear(viewModel.photoFilter)) },
                                )
                            },
                        )
                    }
                    itemsIndexed(
                        photoState.items,
                        key = { _, image -> image.id },
                        span = { _, _ -> GridItemSpan(photoSpan) },
                        contentType = { _, _ -> "photo" },
                    ) { index, image ->
                        val serverProfile = profile
                        if (serverProfile != null) {
                            if (photoLarge) {
                                GalleryPhotoLargeCard(image = image, serverProfile = serverProfile, onClick = { onOpenPhoto(index, photoState.items) })
                            } else {
                                GalleryPhotoTile(image = image, serverProfile = serverProfile, onClick = { onOpenPhoto(index, photoState.items) })
                            }
                        }
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

/** Active photo criteria (except the performer itself, which the tab always adds). */
private fun StashImageFilterState.photoChips(): List<GalleryFilterChipSpec<StashImageFilterState>> =
    activeFilterChips().map { chip ->
        GalleryFilterChipSpec(chip.category.name, chip.label, chip.category.imageSectionKey()) {
            clearStashImageFilterCategory(it, chip.category)
        }
    }

@Composable
private fun PlayAllButton(enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(
            modifier = Modifier.height(36.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stashString(R.string.performer_play_all), style = MaterialTheme.typography.labelLarge)
        }
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
