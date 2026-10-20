package gomeng.dev.stashplayer.feature.explore

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.snapshotFlow
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import gomeng.dev.stashplayer.core.ui.components.ScenePreviewController
import gomeng.dev.stashplayer.core.ui.components.ScenePreviewSurface
import gomeng.dev.stashplayer.core.ui.components.rememberScenePreviewController

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import gomeng.dev.stashplayer.core.discovery.StashDiscoveryFilterAction
import gomeng.dev.stashplayer.core.discovery.StashDiscoveryFilterSection
import gomeng.dev.stashplayer.core.discovery.StashDiscoveryOpenSheet
import gomeng.dev.stashplayer.core.discovery.StashDiscoveryTagOptionsState
import gomeng.dev.stashplayer.core.discovery.applyStashDiscoveryManualFilter
import gomeng.dev.stashplayer.core.discovery.applyStashDiscoveryRandomShuffleAction
import gomeng.dev.stashplayer.core.discovery.applyStashDiscoverySavedFilter
import gomeng.dev.stashplayer.core.discovery.buildStashDiscoveryFavoriteToggleDecision
import gomeng.dev.stashplayer.core.discovery.buildStashDiscoveryQueueAddDecision
import gomeng.dev.stashplayer.core.discovery.buildStashDiscoveryWatchLaterToggleDecision
import gomeng.dev.stashplayer.core.discovery.clearStashDiscoveryFilterSection
import gomeng.dev.stashplayer.core.discovery.applyTagOptionsFailure
import gomeng.dev.stashplayer.core.discovery.applyTagOptionsSuccess
import gomeng.dev.stashplayer.core.discovery.dismiss
import gomeng.dev.stashplayer.core.discovery.open
import gomeng.dev.stashplayer.core.discovery.startTagOptionsRequest
import gomeng.dev.stashplayer.core.discovery.toFilterSheetVisibility
import gomeng.dev.stashplayer.core.discovery.withTagOptionsQuery
import gomeng.dev.stashplayer.core.local.StashLocalLibraryRepository
import gomeng.dev.stashplayer.core.local.appliedFilterState
import gomeng.dev.stashplayer.core.local.applyLocalFavoriteFilter
import gomeng.dev.stashplayer.core.model.SceneBulkDeleteConfirmationState
import gomeng.dev.stashplayer.core.model.SceneBulkDeleteResult
import gomeng.dev.stashplayer.core.model.StashSceneDeleteOptions
import gomeng.dev.stashplayer.core.model.SceneCardModel
import gomeng.dev.stashplayer.core.model.SceneSelectionState
import gomeng.dev.stashplayer.core.model.StashScenesViewMode
import gomeng.dev.stashplayer.core.model.StashExplorePageState
import gomeng.dev.stashplayer.core.model.StashExploreSortOption
import gomeng.dev.stashplayer.core.model.StashSortDirection
import gomeng.dev.stashplayer.core.model.defaultStashExplorePageSizeOptions
import gomeng.dev.stashplayer.core.model.defaultStashExploreSortOptions
import gomeng.dev.stashplayer.core.model.defaultExploreControlsExpanded
import gomeng.dev.stashplayer.core.model.initialFromPersisted
import gomeng.dev.stashplayer.core.model.normalizeStashDiscoveryQuery
import gomeng.dev.stashplayer.core.model.shouldLoadExploreResultsFromServer
import gomeng.dev.stashplayer.core.model.shouldShowSceneCardQuickActionsInMediaGrid
import gomeng.dev.stashplayer.core.model.showExploreSupportingChrome
import gomeng.dev.stashplayer.core.model.shouldUseLocalFavoriteExploreResults
import gomeng.dev.stashplayer.core.model.stashDiscoveryResultCountLabel
import gomeng.dev.stashplayer.core.model.stashMediaGridColumnCount
import gomeng.dev.stashplayer.core.model.stashMediaGridThumbnailHeightDp
import gomeng.dev.stashplayer.core.model.toPersistedFilterState
import gomeng.dev.stashplayer.core.model.afterBulkDelete
import gomeng.dev.stashplayer.core.model.withoutBulkDeletedSceneIds
import gomeng.dev.stashplayer.core.model.withoutBulkDeletedScenes
import gomeng.dev.stashplayer.core.network.redactStashCredentialText
import gomeng.dev.stashplayer.core.network.StashGraphQlClient
import gomeng.dev.stashplayer.core.network.StashServerProfile
import gomeng.dev.stashplayer.core.network.StashSettingsRepository
import gomeng.dev.stashplayer.core.player.PlayerPlaybackQueueContinuation
import gomeng.dev.stashplayer.core.ui.components.SceneCard
import gomeng.dev.stashplayer.core.ui.components.SceneBulkDeleteConfirmationDialog
import gomeng.dev.stashplayer.core.ui.components.StashActiveVideoFilterChipsRow
import gomeng.dev.stashplayer.core.ui.components.rememberStashThumbnailModel
import gomeng.dev.stashplayer.core.ui.designsystem.StashEmptyState
import gomeng.dev.stashplayer.core.ui.designsystem.StashEmptyStateModel
import gomeng.dev.stashplayer.core.ui.designsystem.StashErrorState
import gomeng.dev.stashplayer.core.ui.designsystem.StashErrorStateModel
import gomeng.dev.stashplayer.core.ui.designsystem.StashSecondaryButton
import gomeng.dev.stashplayer.core.ui.designsystem.StashSpacing
import gomeng.dev.stashplayer.core.ui.designsystem.stashDiscoveryVisualPolicy
import gomeng.dev.stashplayer.core.ui.discovery.StashDiscoveryFilterSheets
import gomeng.dev.stashplayer.core.ui.discovery.StashScenesToolbar
import gomeng.dev.stashplayer.core.ui.discovery.StashVideoFilterGroupRow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import gomeng.dev.stashplayer.R
import gomeng.dev.stashplayer.core.model.StashSavedFilterMode
import gomeng.dev.stashplayer.core.model.StashServerSavedFilter
import gomeng.dev.stashplayer.core.model.withServerSavedFilter
import gomeng.dev.stashplayer.core.ui.i18n.stashString
import gomeng.dev.stashplayer.core.model.StashSelectedEntity
import gomeng.dev.stashplayer.core.model.StashVideoFilterState
import gomeng.dev.stashplayer.core.ui.components.StashBrowseHeaderScaffold
import gomeng.dev.stashplayer.core.ui.components.StashBrowseLayout
import gomeng.dev.stashplayer.core.ui.components.StashSelectionPill
import gomeng.dev.stashplayer.core.ui.components.rememberStashCollapsingHeaderState
import gomeng.dev.stashplayer.core.ui.discovery.StashSceneSelectionActions
import gomeng.dev.stashplayer.core.ui.discovery.StashVideoFilterDraft
import gomeng.dev.stashplayer.core.ui.discovery.StashVideoFilterScreen
import gomeng.dev.stashplayer.core.ui.discovery.StashVideoFilterSectionKeys
import gomeng.dev.stashplayer.core.ui.discovery.headerChips

private val SEARCH_DEBOUNCE_MS = 500L

internal data class ExploreRouteResetLoadKey(
    val profileRevision: Int,
    val query: String,
    val sortOption: StashExploreSortOption,
    val sortDirection: StashSortDirection,
    val pageSize: Int,
    val videoFilter: Any,
    val reloadToken: Int,
    val sourceRevision: Int = 0,
)

internal class ExploreRouteStateViewModel : ViewModel() {
    var inputText by mutableStateOf("")
    var pageState by mutableStateOf(StashExplorePageState.initial(defaultStashExploreSortOptions().first()))
    var didApplyPersistedExploreState by mutableStateOf(false)
    var reloadToken by mutableIntStateOf(0)
    var requestSerial by mutableLongStateOf(0L)
    var openSheet by mutableStateOf<StashDiscoveryOpenSheet>(StashDiscoveryOpenSheet.None)
    var savedFilterName by mutableStateOf("")
    var tagOptionsState by mutableStateOf(StashDiscoveryTagOptionsState())
    /** Filters saved in the Stash web UI (scenes). */
    var serverFilters by mutableStateOf<List<StashServerSavedFilter>>(emptyList())
    var serverFiltersLoadedFor by mutableStateOf<Any?>(null)
    /** The web UI's default filter is applied once, on a fresh Explore without any filter or search. */
    var defaultFilterChecked by mutableStateOf(false)
    var lastServerResetLoadKey by mutableStateOf<ExploreRouteResetLoadKey?>(null)
    var lastLocalFavoriteResetLoadKey by mutableStateOf<ExploreRouteResetLoadKey?>(null)

    fun clearTransientLoading() {
        if (pageState.isLoading) pageState = pageState.copy(isLoading = false)
    }
}

/** The centered card starts its preview after scrolling has rested this long. */
private const val EXPLORE_AUTO_PREVIEW_DELAY_MS = 350L
/** Holding a card this long starts its preview (shorter than the system long-press). */
private const val EXPLORE_HOLD_PREVIEW_MS = 300L

/**
 * Touch and hold plays [previewUrl] on this card until the finger lifts. Observes on the Initial pass so the
 * card's own click still works; a lift after the preview started is consumed so it doesn't open the scene.
 */
private fun Modifier.holdToPreview(
    sceneId: String,
    previewUrl: String?,
    durationText: String?,
    preview: ScenePreviewController,
    scope: kotlinx.coroutines.CoroutineScope,
): Modifier = pointerInput(sceneId, previewUrl) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val start = scope.launch {
            delay(EXPLORE_HOLD_PREVIEW_MS)
            preview.play(sceneId, previewUrl, durationText)
        }
        var previewing = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            previewing = previewing || preview.activeSceneId == sceneId
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            // Scrolling the list cancels the hold.
            if (!previewing && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) break
            if (!change.pressed) {
                if (previewing) change.consume()
                break
            }
        }
        start.cancel()
        if (preview.activeSceneId == sceneId) preview.stop()
    }
}

@Composable
fun ExploreRoute(
    isFoldLikeLayout: Boolean,
    onOpenScene: (String, List<SceneCardModel>, Boolean, PlayerPlaybackQueueContinuation?) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val sortOptions = remember { defaultStashExploreSortOptions() }
    val pageSizeOptions = remember { defaultStashExplorePageSizeOptions() }
    val routeState: ExploreRouteStateViewModel = viewModel()
    DisposableEffect(Unit) {
        onDispose { routeState.clearTransientLoading() }
    }
    val context = LocalContext.current
    val settingsRepository = remember(context) { StashSettingsRepository(context) }
    val localRepository = remember(context) { StashLocalLibraryRepository(context) }
    val profile by settingsRepository.serverProfile.collectAsState(initial = null)
    val favoriteSceneIds by localRepository.favoriteSceneIds.collectAsState(initial = emptySet())
    val favoriteScenes by localRepository.favoriteScenes.collectAsState(initial = emptyList())
    val watchLaterSceneIds by localRepository.watchLaterSceneIds.collectAsState(initial = emptySet())
    val queueSceneIds by localRepository.queueSceneIds.collectAsState(initial = emptySet())
    val savedVideoFilters by localRepository.savedVideoFilters.collectAsState(initial = emptyList())
    val persistedExploreFilterState by localRepository.persistedExploreFilterState.collectAsState(initial = null)
    val recentExploreFilters by localRepository.recentExploreVideoFilters.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var inputText by routeState::inputText
    var pageState by routeState::pageState
    var didApplyPersistedExploreState by routeState::didApplyPersistedExploreState
    var reloadToken by routeState::reloadToken
    var requestSerial by routeState::requestSerial
    var openSheet by routeState::openSheet
    val filterSheetVisibility = openSheet.toFilterSheetVisibility()
    val isTagFilterOpen = filterSheetVisibility.isTagFilterOpen
    var savedFilterName by routeState::savedFilterName
    var tagOptionsState by routeState::tagOptionsState
    val snackbarHostState = remember { SnackbarHostState() }
    val exploreGridState = rememberLazyGridState()
    val exploreListState = rememberLazyListState()

    fun loadTags(query: String) {
        val activeProfile = profile ?: return
        val started = tagOptionsState.startTagOptionsRequest(
            query = query,
            profileRevision = activeProfile.hashCode(),
        )
        tagOptionsState = started.state
        scope.launch {
            runCatching {
                StashGraphQlClient(activeProfile).findTags(query = query)
            }.onSuccess { tags ->
                val currentProfile = profile ?: return@onSuccess
                tagOptionsState = tagOptionsState.applyTagOptionsSuccess(
                    request = started.request,
                    currentProfileRevision = currentProfile.hashCode(),
                    options = tags,
                )
            }.onFailure {
                val currentProfile = profile ?: return@onFailure
                tagOptionsState = tagOptionsState.applyTagOptionsFailure(
                    request = started.request,
                    currentProfileRevision = currentProfile.hashCode(),
                    errorMessage = it.message,
                )
            }
        }
    }

    fun loadPage(page: Int, reset: Boolean = false) {
        val activeProfile = profile ?: return
        val activeQuery = pageState.query
        if (!pageState.hasExploreIntent) return
        if (!shouldLoadExploreResultsFromServer(activeQuery, pageState.videoFilter)) return
        if (!reset && pageState.isLoading) return
        val activeSort = pageState.sortOption
        val activeDirection = pageState.sortDirection
        val activePageSize = pageState.pageSize
        val activeVideoFilter = pageState.videoFilter
        val requestId = requestSerial + 1L
        requestSerial = requestId
        pageState = if (reset) {
            StashExplorePageState.initial(activeSort)
                .copy(
                    query = activeQuery,
                    sortDirection = activeDirection,
                    pageSize = activePageSize,
                    videoFilter = activeVideoFilter,
                )
                .loading()
        } else {
            pageState.loading()
        }
        scope.launch {
            runCatching {
                StashGraphQlClient(activeProfile).findSceneCardsPage(
                    perPage = activePageSize,
                    page = page,
                    query = activeQuery.ifBlank { null },
                    sort = activeSort.sort,
                    direction = activeDirection,
                    videoFilter = activeVideoFilter,
                )
            }.onSuccess { result ->
                if (
                    requestSerial != requestId ||
                    profile != activeProfile ||
                    pageState.sortOption != activeSort ||
                    pageState.sortDirection != activeDirection ||
                    pageState.pageSize != activePageSize ||
                    pageState.videoFilter != activeVideoFilter ||
                    pageState.query != activeQuery
                ) {
                    return@onSuccess
                }
                pageState = if (page == 1) {
                    StashExplorePageState.initial(activeSort)
                        .copy(
                            query = activeQuery,
                            sortDirection = activeDirection,
                            pageSize = activePageSize,
                            videoFilter = activeVideoFilter,
                        )
                        .withFirstPage(result.scenes, result.totalCount, activePageSize)
                } else {
                    pageState.withNextPage(result.scenes, result.totalCount, activePageSize)
                }
            }.onFailure {
                if (
                    requestSerial != requestId ||
                    profile != activeProfile ||
                    pageState.sortOption != activeSort ||
                    pageState.sortDirection != activeDirection ||
                    pageState.pageSize != activePageSize ||
                    pageState.videoFilter != activeVideoFilter ||
                    pageState.query != activeQuery
                ) {
                    return@onFailure
                }
                pageState = pageState.failed(it.message ?: stashString(R.string.auto_kr_0518))
            }
        }
    }

    fun applyExploreFilterAction(
        action: StashDiscoveryFilterAction,
        saveRecent: Boolean = action.shouldPromoteRecent,
    ) {
        if (action.shouldReload) {
            requestSerial += 1L
            pageState = pageState.withVideoFilter(action.videoFilter)
        }
        if (saveRecent) {
            scope.launch { localRepository.saveRecentExploreVideoFilter(action.videoFilter) }
        }
    }

    LaunchedEffect(inputText) {
        delay(SEARCH_DEBOUNCE_MS)
        val normalizedQuery = normalizeStashDiscoveryQuery(inputText)
        if (normalizedQuery != pageState.query) {
            requestSerial += 1L
            pageState = pageState.withQuery(normalizedQuery)
        }
    }

    LaunchedEffect(persistedExploreFilterState) {
        val persisted = persistedExploreFilterState ?: return@LaunchedEffect
        if (!didApplyPersistedExploreState) {
            val restored = StashExplorePageState.initialFromPersisted(sortOptions, pageSizeOptions, persisted)
            pageState = restored
            inputText = restored.query
            didApplyPersistedExploreState = true
        }
    }

    // Saved filters from the Stash web UI, plus its default filter for a fresh, unfiltered Explore.
    LaunchedEffect(profile, didApplyPersistedExploreState) {
        val activeProfile = profile ?: return@LaunchedEffect
        if (!didApplyPersistedExploreState) return@LaunchedEffect
        val client = StashGraphQlClient(activeProfile)
        if (routeState.serverFiltersLoadedFor != activeProfile) {
            runCatching { client.findServerSavedFilters(StashSavedFilterMode.Scenes) }.onSuccess {
                routeState.serverFilters = it
                routeState.serverFiltersLoadedFor = activeProfile
            }
        }
        if (!routeState.defaultFilterChecked) {
            routeState.defaultFilterChecked = true
            if (pageState.videoFilter.isEmpty && pageState.query.isBlank()) {
                runCatching { client.findServerDefaultFilter(StashSavedFilterMode.Scenes) }.getOrNull()
                    ?.takeIf { it.criteria.isNotEmpty() }
                    ?.let { defaultFilter ->
                        applyExploreFilterAction(
                            applyStashDiscoveryManualFilter(pageState.videoFilter, pageState.videoFilter.withServerSavedFilter(defaultFilter)),
                            saveRecent = false,
                        )
                    }
            }
        }
    }

    LaunchedEffect(
        didApplyPersistedExploreState,
        pageState.query,
        pageState.sortOption,
        pageState.sortDirection,
        pageState.pageSize,
        pageState.videoFilter,
    ) {
        if (didApplyPersistedExploreState) {
            localRepository.saveExploreFilterState(pageState.toPersistedFilterState())
        }
    }

    LaunchedEffect(
        profile,
        didApplyPersistedExploreState,
        pageState.query,
        pageState.sortOption,
        pageState.sortDirection,
        pageState.pageSize,
        pageState.videoFilter,
        reloadToken,
    ) {
        if (
            profile != null &&
            didApplyPersistedExploreState &&
            pageState.hasExploreIntent &&
            shouldLoadExploreResultsFromServer(pageState.query, pageState.videoFilter)
        ) {
            val resetLoadKey = ExploreRouteResetLoadKey(
                profileRevision = profile.hashCode(),
                query = pageState.query,
                sortOption = pageState.sortOption,
                sortDirection = pageState.sortDirection,
                pageSize = pageState.pageSize,
                videoFilter = pageState.videoFilter,
                reloadToken = reloadToken,
            )
            if (routeState.lastServerResetLoadKey != resetLoadKey || pageState.totalCount == null) {
                routeState.lastServerResetLoadKey = resetLoadKey
                loadPage(page = 1, reset = true)
            }
        }
    }

    LaunchedEffect(
        favoriteScenes,
        didApplyPersistedExploreState,
        pageState.query,
        pageState.sortOption,
        pageState.sortDirection,
        pageState.pageSize,
        pageState.videoFilter,
    ) {
        if (didApplyPersistedExploreState && shouldUseLocalFavoriteExploreResults(pageState.query, pageState.videoFilter)) {
            val resetLoadKey = ExploreRouteResetLoadKey(
                profileRevision = 0,
                query = pageState.query,
                sortOption = pageState.sortOption,
                sortDirection = pageState.sortDirection,
                pageSize = pageState.pageSize,
                videoFilter = pageState.videoFilter,
                reloadToken = 0,
                sourceRevision = favoriteScenes.hashCode(),
            )
            if (routeState.lastLocalFavoriteResetLoadKey != resetLoadKey || pageState.totalCount == null) {
                routeState.lastLocalFavoriteResetLoadKey = resetLoadKey
                requestSerial += 1L
                pageState = StashExplorePageState.initial(pageState.sortOption)
                    .copy(
                        query = pageState.query,
                        sortDirection = pageState.sortDirection,
                        pageSize = pageState.pageSize,
                        videoFilter = pageState.videoFilter,
                    )
                    .withFirstPage(favoriteScenes, favoriteScenes.size, favoriteScenes.size.coerceAtLeast(1))
            }
        }
    }

    LaunchedEffect(isTagFilterOpen, tagOptionsState.query, profile) {
        if (isTagFilterOpen && profile != null) {
            delay(250L)
            loadTags(tagOptionsState.query)
        }
    }

    fun applyFilterDraft(draft: StashVideoFilterDraft<StashExploreSortOption>) {
        var next = pageState
        if (draft.sort != next.sortOption) next = next.forSort(draft.sort)
        if (draft.direction != next.sortDirection) next = next.withSortDirection(draft.direction)
        draft.pageSize?.takeIf { it != next.pageSize }?.let { next = next.withPageSize(it) }
        if (next != pageState) {
            requestSerial += 1L
            pageState = next
        }
        applyExploreFilterAction(applyStashDiscoveryManualFilter(pageState.videoFilter, draft.filter))
    }

    ExploreContent(
        isFoldLikeLayout = isFoldLikeLayout,
        inputText = inputText,
        onInputTextChange = { inputText = it },
        onClearInput = {
            inputText = ""
            requestSerial += 1L
            pageState = pageState.withQuery("")
        },
        pageState = pageState,
        exploreGridState = exploreGridState,
        exploreListState = exploreListState,
        isConfigured = profile != null,
        serverProfile = profile,
        favoriteSceneIds = favoriteSceneIds,
        watchLaterSceneIds = watchLaterSceneIds,
        queueSceneIds = queueSceneIds,
        onApplyVideoFilter = { updated ->
            applyExploreFilterAction(applyStashDiscoveryManualFilter(pageState.videoFilter, updated), saveRecent = false)
        },
        filterScreen = { section, actions, onDismiss ->
            StashVideoFilterScreen(
                initial = StashVideoFilterDraft(
                    sort = pageState.sortOption,
                    direction = pageState.sortDirection,
                    pageSize = pageState.pageSize,
                    filter = pageState.videoFilter,
                ),
                sortOptions = sortOptions,
                sortLabel = { it.label },
                pageSizeOptions = pageSizeOptions,
                searchTags = { query ->
                    profile?.let { p -> StashGraphQlClient(p).findTags(query = query).map { StashSelectedEntity(it.id, it.name) } }.orEmpty()
                },
                searchPerformers = { query -> profile?.let { StashGraphQlClient(it).findPerformers(query = query) }.orEmpty() },
                searchStudios = { query -> profile?.let { StashGraphQlClient(it).findStudios(query = query) }.orEmpty() },
                countResults = { draft ->
                    val activeProfile = profile
                    if (activeProfile == null || draft.filter.localFavoriteOnly) {
                        null
                    } else {
                        StashGraphQlClient(activeProfile).findSceneCardsPage(
                            perPage = 1,
                            query = pageState.query.ifBlank { null },
                            sort = draft.sort.sort,
                            direction = draft.direction,
                            videoFilter = draft.filter,
                        ).totalCount
                    }
                },
                onApply = ::applyFilterDraft,
                onDismiss = onDismiss,
                initialSection = section,
                localSavedFilters = savedVideoFilters,
                serverFilters = routeState.serverFilters,
                onSaveFilter = { name, filterToSave ->
                    scope.launch { localRepository.saveVideoFilter(name, filterToSave) }
                },
                onDeleteSavedFilter = { saved -> scope.launch { localRepository.deleteSavedVideoFilter(saved.id) } },
                actions = actions,
            )
        },
        onToggleFavorite = { scene ->
            val decision = buildStashDiscoveryFavoriteToggleDecision(scene.id, favoriteSceneIds)
            scope.launch {
                localRepository.setFavorite(scene, decision.shouldEnable)
                decision.feedbackText?.let { snackbarHostState.showSnackbar(it) }
            }
        },
        onToggleWatchLater = { scene ->
            val decision = buildStashDiscoveryWatchLaterToggleDecision(scene.id, watchLaterSceneIds)
            scope.launch { localRepository.setWatchLater(scene, decision.shouldEnable) }
        },
        onAddToQueue = { scene ->
            val decision = buildStashDiscoveryQueueAddDecision(scene.id, queueSceneIds)
            scope.launch {
                if (decision.shouldAdd) {
                    localRepository.addToQueue(scene)
                    snackbarHostState.showSnackbar(decision.feedbackText)
                } else {
                    localRepository.removeFromQueue(scene.id)
                    snackbarHostState.showSnackbar(stashString(R.string.scene_card_queue_removed_feedback))
                }
            }
        },
        onDeleteSelectedScenes = { sceneIds, deleteOptions ->
            val activeProfile = profile
            if (activeProfile == null) {
                SceneBulkDeleteResult(
                    requestedSceneIds = sceneIds,
                    deletedSceneIds = emptySet(),
                    failedSceneIds = sceneIds.associateWith { stashString(R.string.auto_kr_0410) },
                )
            } else {
                val result = StashGraphQlClient(activeProfile).deleteScenes(sceneIds, deleteOptions)
                if (result.deletedSceneIds.isNotEmpty()) {
                    localRepository.removeScenesFromLocalSnapshots(result.deletedSceneIds)
                    pageState = pageState.copy(
                        results = pageState.results.withoutBulkDeletedScenes(result),
                        totalCount = pageState.totalCount?.let { (it - result.deletedSceneIds.size).coerceAtLeast(0) },
                    )
                }
                result
            }
        },
        onRetry = {
            if (pageState.results.isEmpty()) {
                reloadToken++
            } else if (!pageState.isLoading) {
                loadPage(pageState.nextPage)
            }
        },
        onLoadMore = {
            if (!pageState.isLoading && pageState.hasMore) {
                loadPage(pageState.nextPage)
            }
        },
        onOpenSettings = onOpenSettings,
        snackbarHostState = snackbarHostState,
        onOpenScene = { sceneId, scenes, randomShuffle ->
            onOpenScene(
                sceneId,
                scenes,
                randomShuffle,
                if (pageState.query.isBlank()) {
                    PlayerPlaybackQueueContinuation.Browse(
                        sort = pageState.sortOption.sort,
                        direction = pageState.sortDirection,
                        videoFilter = pageState.videoFilter,
                        nextPage = pageState.nextPage,
                        pageSize = pageState.pageSize,
                        hasMore = pageState.hasMore,
                    )
                } else {
                    PlayerPlaybackQueueContinuation.Explore(
                        query = pageState.query,
                        sort = pageState.sortOption.sort,
                        direction = pageState.sortDirection,
                        videoFilter = pageState.videoFilter,
                        nextPage = pageState.nextPage,
                        pageSize = pageState.pageSize,
                        hasMore = pageState.hasMore,
                    )
                },
            )
        },
    )
}

@Composable
private fun ExploreContent(
    isFoldLikeLayout: Boolean,
    inputText: String,
    onInputTextChange: (String) -> Unit,
    onClearInput: () -> Unit,
    pageState: StashExplorePageState,
    exploreGridState: LazyGridState,
    exploreListState: LazyListState,
    isConfigured: Boolean,
    serverProfile: StashServerProfile?,
    favoriteSceneIds: Set<String>,
    watchLaterSceneIds: Set<String>,
    queueSceneIds: Set<String>,
    onApplyVideoFilter: (StashVideoFilterState) -> Unit,
    filterScreen: @Composable (section: String?, actions: StashSceneSelectionActions, onDismiss: () -> Unit) -> Unit,
    onToggleFavorite: (SceneCardModel) -> Unit,
    onToggleWatchLater: (SceneCardModel) -> Unit,
    onAddToQueue: (SceneCardModel) -> Unit,
    onDeleteSelectedScenes: suspend (List<String>, StashSceneDeleteOptions) -> SceneBulkDeleteResult,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenSettings: () -> Unit,
    snackbarHostState: SnackbarHostState,
    onOpenScene: (String, List<SceneCardModel>, Boolean) -> Unit,
) {
    val visualPolicy = stashDiscoveryVisualPolicy()
    val horizontalPadding = if (isFoldLikeLayout) {
        visualPolicy.expandedHorizontalPaddingDp.dp
    } else {
        visualPolicy.compactHorizontalPaddingDp.dp
    }
    val gridGap = visualPolicy.gridItemGapDp.dp
    val bottomContentPadding = visualPolicy.gridBottomPaddingDp.dp
    val columns = stashMediaGridColumnCount(isFoldLikeLayout)
    val thumbnailHeight = stashMediaGridThumbnailHeightDp(isFoldLikeLayout).dp
    val results = pageState.results.applyLocalFavoriteFilter(pageState.videoFilter.localFavoriteOnly, favoriteSceneIds)
    val totalCount = pageState.totalCount
    val visibleResultIds = results.map { it.id }
    var selectionState by remember { mutableStateOf(SceneSelectionState()) }
    val layoutContext = LocalContext.current
    val layoutRepository = remember(layoutContext) { StashSettingsRepository(layoutContext) }
    val viewMode by layoutRepository.exploreLayout.collectAsState(initial = StashScenesViewMode.Grid)
    val preview = rememberScenePreviewController(serverProfile)
    val holdScope = rememberCoroutineScope()
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val largeThumbnailHeight = ((screenWidthDp - horizontalPadding.value * 2) * 9f / 16f).dp
    var deleteConfirmation by remember { mutableStateOf(SceneBulkDeleteConfirmationState.Hidden) }
    var previousVisibleResultIds by remember { mutableStateOf(visibleResultIds) }
    val scope = rememberCoroutineScope()
    val headerState = rememberStashCollapsingHeaderState()
    var filterScreenOpen by rememberSaveable { mutableStateOf(false) }
    var filterScreenSection by rememberSaveable { mutableStateOf<String?>(null) }
    fun openFilter(section: String?) {
        filterScreenSection = section
        filterScreenOpen = true
    }

    LaunchedEffect(
        pageState.query,
        pageState.sortOption,
        pageState.sortDirection,
        pageState.pageSize,
        pageState.videoFilter,
    ) {
        selectionState = selectionState.clear()
    }

    LaunchedEffect(visibleResultIds) {
        selectionState = selectionState.clearIfResultIdentityChanged(previousVisibleResultIds, visibleResultIds)
        previousVisibleResultIds = visibleResultIds
    }

    if (filterScreenOpen) {
        filterScreen(
            filterScreenSection,
            StashSceneSelectionActions(
                selectionCount = selectionState.selectedCount,
                visibleCount = results.size,
                onSelectAll = { selectionState = selectionState.selectVisibleScenes(results.map { it.id }) },
                onInvert = { selectionState = selectionState.invertVisibleSelection(results.map { it.id }) },
                onClear = { selectionState = selectionState.clear() },
                onPlay = {
                    selectionState.toolbarPlaybackRequest(results)?.let { request ->
                        selectionState = request.nextSelectionState
                        onOpenScene(request.startSceneId, request.scenes, pageState.videoFilter.randomShuffle)
                    }
                },
                onDelete = { deleteConfirmation = SceneBulkDeleteConfirmationState.open(selectionState.selectedCount) },
            ),
        ) { filterScreenOpen = false }
    }

    SceneBulkDeleteConfirmationDialog(
        state = deleteConfirmation,
        onConfirmationChange = { deleteConfirmation = deleteConfirmation.withConfirmation(it) },
        onDeleteFileChange = { deleteConfirmation = deleteConfirmation.withDeleteFile(it) },
        onDeleteGeneratedChange = { deleteConfirmation = deleteConfirmation.withDeleteGenerated(it) },
        onCancel = { deleteConfirmation = deleteConfirmation.dismiss() },
        onDelete = {
            val selectedIds = selectionState.selectedSceneIds.toList()
            val deleteOptions = deleteConfirmation.deleteOptions
            deleteConfirmation = deleteConfirmation.deleting()
            scope.launch {
                val result = runCatching { onDeleteSelectedScenes(selectedIds, deleteOptions) }.getOrElse { throwable ->
                    SceneBulkDeleteResult(
                        requestedSceneIds = selectedIds,
                        deletedSceneIds = emptySet(),
                        failedSceneIds = selectedIds.associateWith {
                            redactStashCredentialText(throwable.message ?: stashString(R.string.auto_kr_0411))
                        },
                    )
                }
                selectionState = selectionState.afterBulkDelete(result)
                previousVisibleResultIds = previousVisibleResultIds.withoutBulkDeletedSceneIds(result)
                deleteConfirmation = deleteConfirmation.dismiss()
                snackbarHostState.showSnackbar(result.koreanSummary)
            }
        },
    )

    // Large layout: once scrolling settles, the card nearest the middle of the screen plays its preview.
    LaunchedEffect(viewMode, exploreListState, results) {
        if (viewMode != StashScenesViewMode.List) {
            preview.stop()
            return@LaunchedEffect
        }
        snapshotFlow {
            if (exploreListState.isScrollInProgress) {
                null
            } else {
                val layout = exploreListState.layoutInfo
                val center = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
                layout.visibleItemsInfo.minByOrNull { kotlin.math.abs(it.offset + it.size / 2 - center) }?.key as? String
            }
        }.collect { centeredId ->
            if (centeredId == null) return@collect
            delay(EXPLORE_AUTO_PREVIEW_DELAY_MS)
            val scene = results.firstOrNull { it.id == centeredId }
            if (scene != null) preview.play(scene.id, scene.previewUrl, scene.durationText) else preview.stop()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        StashBrowseHeaderScaffold(
            state = headerState,
            searchValue = inputText,
            onSearchChange = onInputTextChange,
            onClearSearch = onClearInput,
            filterCount = pageState.videoFilter.activeFilterCount,
            onOpenFilter = { openFilter(null) },
            layout = if (viewMode == StashScenesViewMode.Grid) StashBrowseLayout.Compact else StashBrowseLayout.Large,
            onToggleLayout = {
                val next = if (viewMode == StashScenesViewMode.Grid) StashScenesViewMode.List else StashScenesViewMode.Grid
                preview.stop()
                holdScope.launch { layoutRepository.setExploreLayout(next) }
            },
            horizontalPadding = horizontalPadding,
            enabled = isConfigured,
            leadingControls = if (selectionState.selectedCount > 0) {
                {
                    StashSelectionPill(
                        count = selectionState.selectedCount,
                        onClick = { openFilter(StashVideoFilterSectionKeys.Actions) },
                        onClear = { selectionState = selectionState.clear() },
                    )
                }
            } else {
                null
            },
            chips = pageState.videoFilter.headerChips(onOpenSection = ::openFilter, onApply = onApplyVideoFilter),
            modifier = Modifier.fillMaxSize(),
        ) { padding ->
            val top = padding.calculateTopPadding()
            when {
                !isConfigured -> StashEmptyState(
                    state = StashEmptyStateModel(
                        title = stashString(R.string.auto_kr_0415),
                        message = stashString(R.string.explore_requires_server_message),
                        primaryActionLabel = stashString(R.string.auto_kr_0270),
                    ),
                    modifier = Modifier.padding(top = top).padding(horizontal = horizontalPadding),
                    onPrimaryAction = onOpenSettings,
                )
                !pageState.hasExploreIntent -> StashEmptyState(
                    state = StashEmptyStateModel(
                        title = stashString(R.string.explore_empty_prompt_title),
                        message = stashString(R.string.explore_empty_prompt_message),
                    ),
                    modifier = Modifier.padding(top = top).padding(horizontal = horizontalPadding),
                )
                pageState.isLoading && results.isEmpty() -> Box(Modifier.fillMaxSize().padding(top = top), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                pageState.error != null && results.isEmpty() -> StashErrorState(
                    state = StashErrorStateModel(
                        title = stashString(R.string.explore_load_failed_title),
                        message = pageState.error,
                        secondaryActionLabel = stashString(R.string.auto_kr_0270),
                    ),
                    modifier = Modifier.padding(top = top).padding(horizontal = horizontalPadding),
                    onRetry = onRetry,
                    onSecondaryAction = onOpenSettings,
                )
                viewMode == StashScenesViewMode.Grid -> LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    state = exploreGridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = horizontalPadding, end = horizontalPadding, top = top, bottom = bottomContentPadding),
                    verticalArrangement = Arrangement.spacedBy(StashSpacing.CardGap),
                    horizontalArrangement = Arrangement.spacedBy(gridGap),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }, contentType = "count") {
                        ExploreResultCount(totalCount, results.size)
                    }
                    items(results, key = { it.id }, contentType = { "scene" }) { scene ->
                        SceneCard(
                            scene = scene,
                            // Compact layout: touch and hold plays the preview; selection starts from the filter screen.
                            modifier = Modifier.holdToPreview(scene.id, scene.previewUrl, scene.durationText, preview, holdScope),
                            thumbnailHeight = thumbnailHeight,
                            thumbnailModel = rememberStashThumbnailModel(scene.thumbnailUrl, serverProfile),
                            isLocalFavorite = scene.id in favoriteSceneIds,
                            isInWatchLater = scene.id in watchLaterSceneIds,
                            isInQueue = scene.id in queueSceneIds,
                            isSelected = scene.id in selectionState.selectedSceneIds,
                            onToggleFavorite = { onToggleFavorite(scene) },
                            onToggleWatchLater = { onToggleWatchLater(scene) },
                            onAddToQueue = { onAddToQueue(scene) },
                            showQuickActions = shouldShowSceneCardQuickActionsInMediaGrid(),
                            onLongClick = if (selectionState.selectedCount > 0) {
                                { selectionState = selectionState.selectFromLongPress(scene.id) }
                            } else {
                                null
                            },
                            thumbnailOverlay = { ScenePreviewSurface(preview, scene.id) },
                            onClick = {
                                val tapResult = selectionState.handleCardTap(scene.id)
                                selectionState = tapResult.state
                                if (tapResult.shouldOpenScene) {
                                    onOpenScene(scene.id, results, pageState.videoFilter.randomShuffle)
                                }
                            },
                        )
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ExploreFooter(pageState = pageState, onRetry = onRetry, onLoadMore = onLoadMore)
                    }
                }
                else -> LazyColumn(
                    state = exploreListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = horizontalPadding, end = horizontalPadding, top = top, bottom = bottomContentPadding),
                    verticalArrangement = Arrangement.spacedBy(StashSpacing.CardGap),
                ) {
                    item(contentType = "count") { ExploreResultCount(totalCount, results.size) }
                    lazyItems(results, key = { it.id }, contentType = { "scene" }) { scene ->
                        SceneCard(
                            scene = scene,
                            thumbnailHeight = largeThumbnailHeight,
                            thumbnailOverlay = { ScenePreviewSurface(preview, scene.id) },
                            thumbnailModel = rememberStashThumbnailModel(scene.thumbnailUrl, serverProfile),
                            isLocalFavorite = scene.id in favoriteSceneIds,
                            isInWatchLater = scene.id in watchLaterSceneIds,
                            isInQueue = scene.id in queueSceneIds,
                            isSelected = scene.id in selectionState.selectedSceneIds,
                            onToggleFavorite = { onToggleFavorite(scene) },
                            onToggleWatchLater = { onToggleWatchLater(scene) },
                            onAddToQueue = { onAddToQueue(scene) },
                            showQuickActions = shouldShowSceneCardQuickActionsInMediaGrid(),
                            onLongClick = { selectionState = selectionState.selectFromLongPress(scene.id) },
                            onClick = {
                                val tapResult = selectionState.handleCardTap(scene.id)
                                selectionState = tapResult.state
                                if (tapResult.shouldOpenScene) {
                                    onOpenScene(scene.id, results, pageState.videoFilter.randomShuffle)
                                }
                            },
                        )
                    }
                    item {
                        ExploreFooter(pageState = pageState, onRetry = onRetry, onLoadMore = onLoadMore)
                    }
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun ExploreResultCount(totalCount: Int?, loadedCount: Int) {
    if (totalCount == null && loadedCount == 0) return
    Text(
        text = stashDiscoveryResultCountLabel(totalCount, loadedCount),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ExploreFooter(
    pageState: StashExplorePageState,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        when {
            pageState.isLoading -> CircularProgressIndicator()
            pageState.error != null -> StashSecondaryButton(text = stashString(R.string.auto_kr_0031), onClick = onRetry)
            pageState.hasMore -> StashSecondaryButton(text = stashString(R.string.auto_kr_0418), onClick = onLoadMore)
            pageState.results.isEmpty() -> StashEmptyState(
                state = StashEmptyStateModel(
                    title = stashString(R.string.explore_no_results_title),
                    message = stashString(R.string.explore_no_results_message),
                ),
            )
            else -> Text(stashString(R.string.auto_kr_0528), style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun directionLabel(direction: StashSortDirection): String = when (direction) {
    StashSortDirection.Desc -> stashString(R.string.auto_kr_0422)
    StashSortDirection.Asc -> stashString(R.string.auto_kr_0423)
}

private fun directionSymbol(direction: StashSortDirection): String = when (direction) {
    StashSortDirection.Desc -> "↓"
    StashSortDirection.Asc -> "↑"
}

