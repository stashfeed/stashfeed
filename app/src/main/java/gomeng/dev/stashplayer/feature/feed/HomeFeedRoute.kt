package gomeng.dev.stashplayer.feature.feed

import gomeng.dev.stashplayer.core.ui.designsystem.StashRadius
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import gomeng.dev.stashplayer.core.player.ratingStepForStashRating100
import gomeng.dev.stashplayer.core.ui.components.StashAvatarButton
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import gomeng.dev.stashplayer.core.ui.components.StashFeedSound
import gomeng.dev.stashplayer.core.ui.components.ScenePreviewSurface
import gomeng.dev.stashplayer.core.ui.components.ScenePreviewController
import androidx.compose.foundation.interaction.MutableInteractionSource
import gomeng.dev.stashplayer.core.ui.designsystem.stashPressScale
import gomeng.dev.stashplayer.core.ui.designsystem.rememberStashHaptics
import gomeng.dev.stashplayer.core.ui.designsystem.StashColors
import gomeng.dev.stashplayer.core.ui.designsystem.StashSpace
import gomeng.dev.stashplayer.core.ui.designsystem.StashTopBar
import gomeng.dev.stashplayer.core.ui.components.LocalStashThumbnailFlights
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.LayoutCoordinates
import gomeng.dev.stashplayer.core.ui.components.rememberStashThumbnailModel
import gomeng.dev.stashplayer.core.ui.designsystem.StashEmptyState
import gomeng.dev.stashplayer.core.ui.designsystem.StashEmptyStateModel
import gomeng.dev.stashplayer.core.ui.designsystem.StashErrorState
import gomeng.dev.stashplayer.core.ui.designsystem.StashErrorStateModel
import gomeng.dev.stashplayer.core.ui.i18n.stashString
import gomeng.dev.stashplayer.core.ui.theme.LocalStashStyleTokens
import gomeng.dev.stashplayer.core.ui.theme.StashStyleTokens
import gomeng.dev.stashplayer.feature.performers.PerformerImage
import gomeng.dev.stashplayer.feature.shorts.ForYouRatingDialog
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import gomeng.dev.stashplayer.core.model.SEEN_FEED_POST_DWELL_MS
import gomeng.dev.stashplayer.core.model.SEEN_FEED_POST_MIN_VISIBLE_FRACTION
import gomeng.dev.stashplayer.core.model.splitBySeen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How often visible posts are checked for the "seen" dwell time. */
private const val SEEN_POLL_MS = 250L

/** Media taller than 4:5 or wider than ~1.91:1 is cropped, so one post never fills the whole screen. */
/** Tallest post: a full portrait video (9:16) keeps its shape. */
private const val FEED_MEDIA_MIN_RATIO = 9f / 16f
/** Gallery posts without a known size start at the classic 4:5. */
private const val FEED_GALLERY_FALLBACK_RATIO = 4f / 5f
private const val FEED_MEDIA_MAX_RATIO = 1.91f
/** Galleries load their next photos when the carousel gets this close to the end. */
private const val FEED_CAROUSEL_PRELOAD = 3
/** Instagram-style windowed dots: never more than this many. */
private const val FEED_CAROUSEL_MAX_DOTS = 5
private const val STARS_PER_RATING_STEP = 2f

@Composable
fun HomeFeedRoute(
    isFoldLikeLayout: Boolean,
    onOpenScene: (sceneId: String, scenes: List<SceneCardModel>) -> Unit,
    onOpenGallery: (String) -> Unit,
    onOpenPerformer: (String) -> Unit,
    onOpenLightbox: (FeedLightboxRequest) -> Unit,
) {
    val context = LocalContext.current
    val settingsRepository = remember(context) { StashSettingsRepository(context) }
    val feedSettingsRepository = remember(context) { HomeFeedSettingsRepository(context) }
    val profile by settingsRepository.serverProfile.collectAsState(initial = null)
    val feedSettings by feedSettingsRepository.settings.collectAsState(initial = null)
    val viewModel: HomeFeedViewModel = viewModel()
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    val readyItems = (viewModel.state as? HomeFeedState.Ready)?.feed?.items.orEmpty()
    val videosByKey = remember(readyItems) {
        readyItems.filterIsInstance<HomeFeedItem.Post>().mapNotNull { post ->
            feedInlineVideoFor(post.scene.scene.id, post.scene.previewUrl)?.let { post.key to it }
        }.toMap()
    }
    val feedVideo = rememberFeedAutoplay(gridState, profile) { key -> videosByKey[key] }
    val snackbarHostState = remember { SnackbarHostState() }
    val tokens = LocalStashStyleTokens.current
    var ratingTarget by remember { mutableStateOf<SceneCardModel?>(null) }

    LaunchedEffect(profile, feedSettings, viewModel.filter) {
        val activeProfile = profile ?: return@LaunchedEffect
        val activeSettings = feedSettings ?: return@LaunchedEffect
        viewModel.bind(activeProfile, activeSettings)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(viewModel, lifecycleOwner) {
        // Save seen posts when the app goes to the background too, not only when leaving Home.
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) viewModel.flushSeenNow() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.flushSeenNow()
        }
    }

    // A post is "seen" once at least half of it stayed on screen for a second (only counted while hiding is on).
    val readyFeed = (viewModel.state as? HomeFeedState.Ready)?.feed
    val createdAtByKey = remember(readyFeed) { readyFeed?.items?.associate { it.key to it.createdAt }.orEmpty() }
    LaunchedEffect(gridState, createdAtByKey, feedSettings?.hideSeen) {
        if (feedSettings?.hideSeen != true || createdAtByKey.isEmpty()) return@LaunchedEffect
        val visibleSince = HashMap<String, Long>()
        while (true) {
            delay(SEEN_POLL_MS)
            val layout = gridState.layoutInfo
            val now = System.currentTimeMillis()
            val viewportStart = layout.viewportStartOffset
            val viewportEnd = layout.viewportEndOffset
            val onScreen = HashSet<String>()
            layout.visibleItemsInfo.forEach { info ->
                val key = info.key as? String ?: return@forEach
                val createdAt = createdAtByKey[key] ?: return@forEach
                val top = info.offset.y
                val bottom = top + info.size.height
                val visible = (minOf(bottom, viewportEnd) - maxOf(top, viewportStart)).coerceAtLeast(0)
                if (info.size.height > 0 && visible.toFloat() / info.size.height >= SEEN_FEED_POST_MIN_VISIBLE_FRACTION) {
                    onScreen += key
                    val since = visibleSince.getOrPut(key) { now }
                    if (now - since >= SEEN_FEED_POST_DWELL_MS) viewModel.markSeen(key, createdAt)
                }
            }
            visibleSince.keys.retainAll(onScreen)
        }
    }

    LaunchedEffect(viewModel.actionError) {
        viewModel.actionError?.let { message ->
            snackbarHostState.showSnackbar(stashString(R.string.for_you_action_failed, message))
            viewModel.actionError = null
        }
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
    ratingTarget?.let { target ->
        ForYouRatingDialog(
            scene = target,
            onRate = { rating100 ->
                ratingTarget = null
                viewModel.rateScene(target.id, rating100)
            },
            onDismiss = { ratingTarget = null },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(if (isFoldLikeLayout) 2 else 1),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(if (isFoldLikeLayout) 12.dp else 0.dp),
            verticalArrangement = Arrangement.spacedBy(tokens.feedPostGap),
        ) {
            item(key = "header", span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
                HomeFeedHeader(
                    filterCount = viewModel.filter.activeCount,
                    lookbackDays = activeSettings.lookbackDays,
                    onOpenFilter = { viewModel.isFilterOpen = true },
                    onOpenSettings = { viewModel.isSettingsOpen = true },
                    onRefresh = { profile?.let { viewModel.bind(it, activeSettings, force = true) } },
                )
            }
            when (val state = viewModel.state) {
                HomeFeedState.Loading -> items(3, key = { "skeleton$it" }, contentType = { "skeleton" }) { PostSkeleton(tokens) }
                is HomeFeedState.Failed -> item(key = "error", span = { GridItemSpan(maxLineSpan) }) {
                    StashErrorState(
                        state = StashErrorStateModel(title = stashString(R.string.home_feed_error_title), message = state.message),
                        modifier = Modifier.padding(horizontal = 16.dp),
                        onRetry = { profile?.let { viewModel.bind(it, activeSettings, force = true) } },
                    )
                }
                is HomeFeedState.Ready -> {
                    val feed = state.feed
                    if (feed.stories.isNotEmpty()) {
                        item(key = "stories", span = { GridItemSpan(maxLineSpan) }, contentType = "stories") {
                            StoriesRow(
                                stories = feed.stories,
                                serverProfile = profile,
                                ring = tokens,
                                onOpenStory = { index -> viewModel.openStoryIndex = index },
                            )
                        }
                    }
                    if (HomeFeedKind.Unidentified in activeSettings.hiddenKinds && feed.unidentifiedSceneCount > 0) {
                        item(key = "unidentified", span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = stashString(R.string.home_feed_unidentified_hidden, feed.unidentifiedSceneCount),
                                modifier = Modifier.padding(horizontal = 16.dp),
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
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    }
                    val sections = if (activeSettings.hideSeen) feed.splitBySeen(viewModel.seenSnapshot) else null
                    val visibleItems = sections?.unseen ?: feed.items
                    feedPostItems(
                        items = visibleItems,
                        viewModel = viewModel,
                        profile = profile,
                        tokens = tokens,
                        onOpenScene = onOpenScene,
                        onOpenGallery = onOpenGallery,
                        onOpenPerformer = onOpenPerformer,
                        onOpenLightbox = onOpenLightbox,
                        onRate = { ratingTarget = it },
                        inlineVideo = feedVideo,
                    )
                    if (sections != null && sections.seen.isNotEmpty()) {
                        item(key = "caught_up", span = { GridItemSpan(maxLineSpan) }, contentType = "caught_up") {
                            CaughtUpDivider(
                                seenCount = sections.seen.size,
                                expanded = viewModel.showSeenPosts,
                                onToggle = { viewModel.showSeenPosts = !viewModel.showSeenPosts },
                            )
                        }
                        if (viewModel.showSeenPosts) {
                            feedPostItems(
                                items = sections.seen,
                                viewModel = viewModel,
                                profile = profile,
                                tokens = tokens,
                                onOpenScene = onOpenScene,
                                onOpenGallery = onOpenGallery,
                                onOpenPerformer = onOpenPerformer,
                                onOpenLightbox = onOpenLightbox,
                                onRate = { ratingTarget = it },
                                inlineVideo = feedVideo,
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
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

/** One grid item per post; used for both the new posts and the expanded "seen" section. */
private fun LazyGridScope.feedPostItems(
    items: List<HomeFeedItem>,
    viewModel: HomeFeedViewModel,
    profile: StashServerProfile?,
    tokens: StashStyleTokens,
    onOpenScene: (sceneId: String, scenes: List<SceneCardModel>) -> Unit,
    onOpenGallery: (String) -> Unit,
    onOpenPerformer: (String) -> Unit,
    onOpenLightbox: (FeedLightboxRequest) -> Unit,
    onRate: (SceneCardModel) -> Unit,
    inlineVideo: ScenePreviewController,
) {
    items(
        items = items,
        key = { it.key },
        contentType = { item ->
            when (item) {
                is HomeFeedItem.Post -> "post"
                is HomeFeedItem.GalleryPost -> "gallery"
            }
        },
    ) { item ->
        PostCard(tokens) {
            when (item) {
                is HomeFeedItem.Post -> ScenePost(
                    post = item,
                    serverProfile = profile,
                    tokens = tokens,
                    onOpen = { onOpenScene(item.scene.scene.id, viewModel.playableScenes()) },
                    onOpenPerformer = onOpenPerformer,
                    onRate = { onRate(item.scene.scene) },
                    onAddO = { viewModel.addO(item.scene.scene.id) },
                    onRemoveO = { viewModel.removeO(item.scene.scene.id) },
                    inlineVideo = inlineVideo,
                )
                is HomeFeedItem.GalleryPost -> GalleryPost(
                    post = item,
                    images = viewModel.galleryImages[item.gallery.gallery.id],
                    serverProfile = profile,
                    tokens = tokens,
                    onNeedImages = { viewModel.ensureGalleryImages(item.gallery.gallery.id) },
                    onNeedMoreImages = { viewModel.loadMoreGalleryImages(item.gallery.gallery.id) },
                    onOpenPhoto = { index, onClosed ->
                        val galleryId = item.gallery.gallery.id
                        onOpenLightbox(
                            FeedLightboxRequest(
                                title = item.gallery.gallery.title,
                                startIndex = index,
                                totalCount = item.gallery.gallery.imageCount ?: 0,
                                images = { viewModel.galleryImages[galleryId]?.images.orEmpty() },
                                onNeedMore = { viewModel.loadMoreGalleryImages(galleryId) },
                                onClosed = onClosed,
                            ),
                        )
                    },
                    onOpenGallery = { onOpenGallery(item.gallery.gallery.id) },
                    onOpenPerformer = onOpenPerformer,
                )
            }
        }
    }
}

/** "You're all caught up" between new and already-seen posts; the seen ones stay folded until asked for. */
@Composable
private fun CaughtUpDivider(seenCount: Int, expanded: Boolean, onToggle: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .border(BorderStroke(2.5.dp, LocalStashStyleTokens.current.storyRing), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(28.dp))
        }
        Text(stashString(R.string.home_feed_caught_up_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            stashString(R.string.home_feed_caught_up_message),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onToggle) {
            Text(
                if (expanded) {
                    stashString(R.string.home_feed_hide_seen)
                } else {
                    stashString(R.string.home_feed_show_seen, seenCount)
                },
            )
        }
    }
}

/** Social Dark: flat, edge to edge with a hairline below. Material You: a rounded tonal card. */
@Composable
internal fun PostCard(tokens: StashStyleTokens, content: @Composable () -> Unit) {
    val cardModifier = if (tokens.isSocial) {
        Modifier
    } else {
        Modifier
            .padding(horizontal = tokens.feedCardInset)
            .clip(RoundedCornerShape(tokens.feedCardRadius))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(vertical = tokens.feedCardPadding)
    }
    Column(modifier = Modifier.fillMaxWidth().then(cardModifier)) {
        content()
        if (tokens.showFeedDividers) {
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
        }
    }
}

private val StashStyleTokens.textInset: Dp get() = if (isSocial) 16.dp else 14.dp

@Composable
private fun HomeFeedHeader(
    filterCount: Int,
    lookbackDays: Int,
    onOpenFilter: () -> Unit,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit,
) {
    StashTopBar(
        title = null,
        modifier = Modifier.padding(top = StashSpace.XS),
        titleContent = {
            Image(
                painter = painterResource(R.drawable.ic_stashfeed_mark),
                contentDescription = null,
                modifier = Modifier.size(30.dp),
            )
            Column(modifier = Modifier.padding(start = 10.dp)) {
                Text(stashString(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = stashString(R.string.home_feed_window, lookbackDays),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        actions = {
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
            StashAvatarButton()
        },
    )
}

@Composable
private fun StoriesRow(
    stories: List<HomeStory>,
    serverProfile: StashServerProfile?,
    ring: StashStyleTokens,
    onOpenStory: (Int) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        itemsIndexed(stories, key = { _, story -> story.performer.id }) { index, story ->
            Column(
                modifier = Modifier
                    .width(70.dp)
                    .clip(RoundedCornerShape(StashRadius.M))
                    .clickable { onOpenStory(index) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box {
                    PerformerImage(
                        name = story.performer.name,
                        imageUrl = story.performer.imageUrl,
                        serverProfile = serverProfile,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(68.dp)
                            .border(BorderStroke(2.5.dp, ring.storyRing), CircleShape)
                            .padding(4.5.dp)
                            .clip(CircleShape),
                    )
                    Badge(
                        modifier = Modifier.align(Alignment.BottomEnd),
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) { Text(story.scenes.size.toString()) }
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

@Composable
private fun PostHeader(
    performers: List<FeedPerformer>,
    createdAt: String,
    serverProfile: StashServerProfile?,
    tokens: StashStyleTokens,
    onOpenPerformer: (String) -> Unit,
    kindIcon: @Composable (() -> Unit)? = null,
    badge: String? = null,
) {
    Row(
        modifier = Modifier.padding(horizontal = tokens.textInset),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val lead = performers.firstOrNull()
        if (lead != null) {
            PerformerImage(
                name = lead.name,
                imageUrl = lead.imageUrl,
                serverProfile = serverProfile,
                shape = CircleShape,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable { onOpenPerformer(lead.id) },
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val shown = performers.take(3)
                shown.forEachIndexed { index, performer ->
                    Text(
                        text = performer.name + if (index < shown.lastIndex) "," else "",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.clickable { onOpenPerformer(performer.id) },
                    )
                }
                if (performers.size > 3) Text("+${performers.size - 3}", style = MaterialTheme.typography.titleSmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                kindIcon?.invoke()
                Text(
                    text = listOfNotNull(badge, relativeAddedLabel(createdAt)).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PostTitle(title: String, tokens: StashStyleTokens) {
    Text(
        text = title,
        modifier = Modifier.padding(horizontal = tokens.textInset, vertical = 8.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Clamped aspect ratio for a media frame, from its pixel size. */
private fun feedMediaRatio(width: Int?, height: Int?, fallback: Float): Float {
    if (width == null || height == null || width <= 0 || height <= 0) return fallback
    return (width.toFloat() / height.toFloat()).coerceIn(FEED_MEDIA_MIN_RATIO, FEED_MEDIA_MAX_RATIO)
}

@Composable
private fun MediaFrame(
    ratio: Float,
    tokens: StashStyleTokens,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
            .clip(RoundedCornerShape(tokens.feedMediaRadius))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) { content() }
}

@Composable
private fun OverlayBadge(text: String, modifier: Modifier = Modifier, icon: @Composable (() -> Unit)? = null) {
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.62f), RoundedCornerShape(50))
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        icon?.invoke()
        Text(text, style = MaterialTheme.typography.labelMedium, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ActionChip(
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val haptics = LocalHapticFeedback.current
    val stashHaptics = rememberStashHaptics()
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .heightIn(min = 36.dp)
            .stashPressScale(interaction, 0.92f)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .combinedClickable(
                interactionSource = interaction,
                indication = androidx.compose.foundation.LocalIndication.current,
                onClickLabel = contentDescription,
                onClick = {
                    stashHaptics.tick()
                    onClick()
                },
                onLongClick = onLongClick?.let { longClick ->
                    {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        longClick()
                    }
                },
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        icon()
        if (label.isNotEmpty()) Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
internal fun ScenePost(
    post: HomeFeedItem.Post,
    serverProfile: StashServerProfile?,
    tokens: StashStyleTokens,
    onOpen: () -> Unit,
    onOpenPerformer: (String) -> Unit,
    onRate: () -> Unit,
    onAddO: () -> Unit,
    onRemoveO: () -> Unit,
    /** The feed's shared inline player; when set, the video plays in the post and only Open goes to the player. */
    inlineVideo: ScenePreviewController? = null,
) {
    val scene = post.scene.scene
    val model = rememberStashThumbnailModel(scene.thumbnailUrl, serverProfile)
    PostHeader(
        performers = post.scene.performers,
        createdAt = post.createdAt,
        serverProfile = serverProfile,
        tokens = tokens,
        onOpenPerformer = onOpenPerformer,
        kindIcon = if (post.isRepost) {
            { Icon(Icons.Outlined.Repeat, null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            null
        },
        badge = if (post.isRepost) stashString(R.string.home_feed_repost_badge) else null,
    )
    PostTitle(scene.title, tokens)
    val flights = LocalStashThumbnailFlights.current
    val mediaAnchor = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val haptics = rememberStashHaptics()
    val openInPlayer = {
        flights?.record(scene.id, mediaAnchor[0], model)
        onOpen()
    }
    val playingHere = inlineVideo != null && inlineVideo.activeSceneId == scene.id
    // The video's own shape: portrait stays portrait, landscape stays landscape.
    MediaFrame(ratio = feedMediaRatio(post.scene.width, post.scene.height, 16f / 9f), tokens = tokens) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onPlaced { mediaAnchor[0] = it }
                .clickable {
                    if (inlineVideo != null) {
                        // Tapping the video pauses or resumes it (or starts this one); sound is the speaker button,
                        // the full player is Open.
                        haptics.tick()
                        if (inlineVideo.activeSceneId == scene.id) {
                            inlineVideo.togglePause()
                        } else {
                            feedInlineVideoFor(scene.id, post.scene.previewUrl)?.let { inlineVideo.playVideo(it.sceneId, it.streamUrl) }
                        }
                    } else {
                        openInPlayer()
                    }
                },
        ) {
            if (model != null) {
                AsyncImage(model = model, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            inlineVideo?.let { ScenePreviewSurface(it, scene.id) }
            if (!(playingHere && inlineVideo?.firstFrameShown == true && !inlineVideo.paused)) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(56.dp)
                        .background(Color.Black.copy(alpha = 0.45f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
                }
            }
            if (inlineVideo != null) {
                val soundOn = StashFeedSound.enabled
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp)
                        .size(34.dp)
                        .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                        .clickable {
                            haptics.tick()
                            StashFeedSound.enabled = !soundOn
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (soundOn) Icons.AutoMirrored.Outlined.VolumeUp else Icons.AutoMirrored.Outlined.VolumeOff,
                        contentDescription = stashString(if (soundOn) R.string.feed_sound_off else R.string.feed_sound_on),
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            if (scene.durationText.isNotBlank()) {
                OverlayBadge(scene.durationText, Modifier.align(Alignment.BottomEnd).padding(10.dp))
            }
        }
    }
    SceneActionBar(scene = scene, tokens = tokens, onRate = onRate, onAddO = onAddO, onRemoveO = onRemoveO, onOpen = openInPlayer)
    val meta = listOfNotNull(
        scene.studio.takeIf { it.isNotBlank() && it != "Stash" },
        post.scene.date,
    ).joinToString(" · ")
    val tags = scene.tagChips.take(4).joinToString(" ") { "#${it.label.replace(" ", "")}" }
    if (meta.isNotBlank() || tags.isNotBlank()) {
        Text(
            text = listOf(tags, meta).filter { it.isNotBlank() }.joinToString("  ·  "),
            modifier = Modifier.padding(horizontal = tokens.textInset),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SceneActionBar(
    scene: SceneCardModel,
    tokens: StashStyleTokens,
    onRate: () -> Unit,
    onAddO: () -> Unit,
    onRemoveO: () -> Unit,
    onOpen: () -> Unit,
) {
    val stars = ratingStepForStashRating100(scene.rating100) / STARS_PER_RATING_STEP
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = tokens.textInset, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionChip(
            label = if (stars > 0f) formatStars(stars) else "",
            contentDescription = stashString(R.string.for_you_rate),
            onClick = onRate,
            icon = {
                Icon(
                    if (stars > 0f) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = null,
                    tint = if (stars > 0f) StashColors.Star else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp),
                )
            },
        )
        ActionChip(
            label = (scene.oCounter ?: 0).toString(),
            contentDescription = stashString(R.string.for_you_o_counter, scene.oCounter ?: 0),
            onClick = onAddO,
            onLongClick = onRemoveO,
            icon = { Icon(Icons.Outlined.WaterDrop, contentDescription = null, modifier = Modifier.size(18.dp)) },
        )
        Spacer(Modifier.weight(1f))
        ActionChip(
            label = stashString(R.string.home_feed_open),
            contentDescription = stashString(R.string.for_you_open_full),
            onClick = onOpen,
            icon = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp)) },
        )
    }
}

private fun formatStars(stars: Float): String = if (stars % 1f == 0f) stars.toInt().toString() else stars.toString()

/**
 * Reddit-style gallery post with the plugin's carousel: swipe through the photos, tap one to open it full
 * screen, the last slide opens the whole gallery. Photos load when the post first appears.
 */
@Composable
internal fun GalleryPost(
    post: HomeFeedItem.GalleryPost,
    images: FeedGalleryImages?,
    serverProfile: StashServerProfile?,
    tokens: StashStyleTokens,
    onNeedImages: () -> Unit,
    onNeedMoreImages: () -> Unit,
    /** Opens photo `index` full screen; the viewer reports the photo it closed on through the second argument. */
    onOpenPhoto: (index: Int, onClosed: (Int) -> Unit) -> Unit,
    onOpenGallery: () -> Unit,
    onOpenPerformer: (String) -> Unit,
) {
    val gallery = post.gallery.gallery
    LaunchedEffect(gallery.id) { onNeedImages() }
    val loaded = images?.images.orEmpty()
    val totalCount = maxOf(gallery.imageCount ?: 0, loaded.size)
    val first = loaded.firstOrNull()
    // The frame keeps the first photo's shape for every slide, so the post doesn't jump while swiping.
    val ratio = feedMediaRatio(first?.width, first?.height, FEED_GALLERY_FALLBACK_RATIO)
    // Just the photos (the post itself is the gallery view); only the cover until the photos arrive.
    val slideCount = maxOf(1, loaded.size)
    val pagerState = rememberPagerState { slideCount }
    val scope = rememberCoroutineScope()
    // Back from full screen, the carousel shows the photo the viewer was closed on.
    val showClosedPhoto: (Int) -> Unit = { index ->
        scope.launch { pagerState.scrollToPage(index.coerceIn(0, (pagerState.pageCount - 1).coerceAtLeast(0))) }
    }

    LaunchedEffect(pagerState, loaded.size, images?.hasMore) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            if (loaded.isNotEmpty() && page >= loaded.size - FEED_CAROUSEL_PRELOAD && images?.hasMore == true) onNeedMoreImages()
        }
    }

    PostHeader(
        performers = post.gallery.performers,
        createdAt = post.createdAt,
        serverProfile = serverProfile,
        tokens = tokens,
        onOpenPerformer = onOpenPerformer,
        kindIcon = { Icon(Icons.Outlined.Collections, null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        badge = stashString(R.string.home_feed_gallery_kind),
    )
    PostTitle(gallery.title, tokens)
    MediaFrame(ratio = ratio, tokens = tokens) {
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            key = { index -> loaded.getOrNull(index)?.id ?: "cover$index" },
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val image = loaded.getOrNull(page)
            when {
                loaded.isEmpty() -> {
                    val cover = rememberStashThumbnailModel(gallery.bestThumbnailUrl, serverProfile)
                    Box(Modifier.fillMaxSize().clickable(onClick = onOpenGallery)) {
                        if (cover != null) AsyncImage(model = cover, contentDescription = gallery.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                }
                image != null -> {
                    val model = rememberStashThumbnailModel(image.thumbnailUrl ?: image.previewUrl ?: image.imageUrl, serverProfile)
                    Box(Modifier.fillMaxSize().clickable { onOpenPhoto(page, showClosedPhoto) }) {
                        AsyncImage(model = model, contentDescription = image.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                }
                else -> Unit
            }
        }
        OverlayBadge(
            text = if (loaded.isNotEmpty()) "${pagerState.currentPage + 1}/$totalCount" else totalCount.toString(),
            modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
            icon = { Icon(Icons.Outlined.PhotoLibrary, null, tint = Color.White, modifier = Modifier.size(13.dp)) },
        )
    }
    if (slideCount > 1) {
        CarouselDots(count = slideCount, current = pagerState.currentPage)
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = tokens.textInset, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = listOfNotNull(gallery.studio, gallery.date).joinToString(" · "),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        ActionChip(
            label = stashString(R.string.home_feed_open),
            contentDescription = stashString(R.string.home_feed_open_gallery),
            onClick = onOpenGallery,
            icon = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp)) },
        )
    }
}

/** At most [FEED_CAROUSEL_MAX_DOTS] dots that slide along with the position, like Instagram. */
@Composable
private fun CarouselDots(count: Int, current: Int) {
    val visible = minOf(count, FEED_CAROUSEL_MAX_DOTS)
    val start = (current - visible / 2).coerceIn(0, count - visible)
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (index in start until start + visible) {
            val active = index == current
            val edge = (index == start && start > 0) || (index == start + visible - 1 && start + visible < count)
            Box(
                Modifier
                    .size(if (active) 7.dp else if (edge) 4.dp else 6.dp)
                    .background(
                        if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                        CircleShape,
                    ),
            )
        }
    }
}

@Composable
private fun PostSkeleton(tokens: StashStyleTokens) {
    val block = MaterialTheme.colorScheme.surfaceContainerHigh
    PostCard(tokens) {
        Row(Modifier.padding(horizontal = tokens.textInset), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).background(block, CircleShape))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.width(140.dp).height(12.dp).background(block, RoundedCornerShape(6.dp)))
        }
        Box(Modifier.padding(horizontal = tokens.textInset, vertical = 12.dp).width(240.dp).height(14.dp).background(block, RoundedCornerShape(7.dp)))
        Box(Modifier.fillMaxWidth().aspectRatio(4f / 5f).clip(RoundedCornerShape(tokens.feedMediaRadius)).background(block))
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
