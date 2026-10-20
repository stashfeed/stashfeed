package gomeng.dev.stashplayer.feature.performers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.saveable.rememberSaveable
import gomeng.dev.stashplayer.core.ui.components.StashBrowseHeaderScaffold
import gomeng.dev.stashplayer.core.ui.components.StashBrowseLayout
import gomeng.dev.stashplayer.core.ui.components.StashFilterBlockLabel
import gomeng.dev.stashplayer.core.ui.components.StashFilterScreen
import gomeng.dev.stashplayer.core.ui.components.StashHeaderChip
import gomeng.dev.stashplayer.core.ui.components.rememberStashCollapsingHeaderState
import gomeng.dev.stashplayer.core.ui.designsystem.StashVisibilityFilterChip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import gomeng.dev.stashplayer.R
import gomeng.dev.stashplayer.core.model.PerformerCardModel
import gomeng.dev.stashplayer.core.model.PerformerListScope
import gomeng.dev.stashplayer.core.model.PerformerListSort
import gomeng.dev.stashplayer.core.model.nextStashRandomSortSeed
import gomeng.dev.stashplayer.core.network.StashGraphQlClient
import gomeng.dev.stashplayer.core.network.StashServerProfile
import gomeng.dev.stashplayer.core.network.StashSettingsRepository
import gomeng.dev.stashplayer.core.ui.designsystem.StashEmptyState
import gomeng.dev.stashplayer.core.ui.designsystem.StashEmptyStateModel
import gomeng.dev.stashplayer.core.ui.designsystem.StashErrorState
import gomeng.dev.stashplayer.core.ui.designsystem.StashErrorStateModel
import gomeng.dev.stashplayer.core.ui.designsystem.StashSpacing
import gomeng.dev.stashplayer.core.ui.i18n.stashString
import kotlinx.coroutines.delay

private const val PERFORMERS_PAGE_SIZE = 40
private const val PERFORMERS_SEARCH_DEBOUNCE_MS = 300L
private const val PERFORMERS_LOAD_MORE_THRESHOLD = 8

/** Survives navigating into a profile and back, so the list and scroll position stay put. */
internal class PerformersViewModel : ViewModel() {
    var scope by mutableStateOf(PerformerListScope.Following)
    var sort by mutableStateOf(PerformerListSort.Name)
    var query by mutableStateOf("")
    var layout by mutableStateOf(StashBrowseLayout.Compact)
    private var profile: StashServerProfile? = null
    private var randomSeed = nextStashRandomSortSeed()
    private var loadedKey: Any? = null

    val loader = PagedLoader<PerformerCardModel>(
        scope = viewModelScope,
        pageSize = PERFORMERS_PAGE_SIZE,
        keyOf = { it.id },
        fetch = { page, pageSize ->
            val activeProfile = profile ?: error(stashString(R.string.performers_not_configured))
            val result = StashGraphQlClient(activeProfile).findPerformerCardsPage(
                perPage = pageSize,
                page = page,
                query = query,
                scope = scope,
                sort = sort,
                randomSeed = randomSeed,
            )
            LoadedPage(result.performers, result.totalCount)
        },
    )

    /** Reloads only when the server, scope, sort or search actually changed. */
    fun ensureLoaded(activeProfile: StashServerProfile) {
        val key = listOf(activeProfile, scope, sort, query)
        if (key == loadedKey) return
        loadedKey = key
        profile = activeProfile
        if (sort == PerformerListSort.Random) randomSeed = nextStashRandomSortSeed()
        loader.reset()
    }

    /** Applies a follow change made on a profile without reloading the whole list. */
    fun applyFavoriteChange(performerId: String, favorite: Boolean) {
        val isListed = loader.state.items.any { it.id == performerId }
        if (favorite && !isListed && scope == PerformerListScope.Following && profile != null) {
            loader.reset()
            return
        }
        loader.updateItems { items ->
            items.mapNotNull { performer ->
                when {
                    performer.id != performerId -> performer
                    !favorite && scope == PerformerListScope.Following -> null
                    else -> performer.copy(favorite = favorite)
                }
            }
        }
    }
}

@Composable
fun PerformersRoute(
    isFoldLikeLayout: Boolean,
    onOpenPerformer: (String) -> Unit,
) {
    val context = LocalContext.current
    val settingsRepository = remember(context) { StashSettingsRepository(context) }
    val profile by settingsRepository.serverProfile.collectAsState(initial = null)
    val viewModel: PerformersViewModel = viewModel()
    val state = viewModel.loader.state
    var searchText by remember { mutableStateOf(viewModel.query) }
    val gridState = rememberLazyGridState()
    val horizontalPadding = if (isFoldLikeLayout) StashSpacing.ScreenHorizontalExpanded else StashSpacing.ScreenHorizontalCompact

    LaunchedEffect(searchText) {
        if (searchText == viewModel.query) return@LaunchedEffect
        delay(PERFORMERS_SEARCH_DEBOUNCE_MS)
        viewModel.query = searchText.trim()
    }
    LaunchedEffect(profile, viewModel.scope, viewModel.sort, viewModel.query) {
        profile?.let(viewModel::ensureLoaded)
    }
    LaunchedEffect(viewModel) {
        PerformerFavoriteChanges.events.collect { change ->
            viewModel.applyFavoriteChange(change.performerId, change.favorite)
        }
    }
    LaunchedEffect(gridState, state.items.size) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { lastVisible ->
                if (lastVisible >= state.items.size - PERFORMERS_LOAD_MORE_THRESHOLD) viewModel.loader.loadNext()
            }
    }

    val headerState = rememberStashCollapsingHeaderState()
    var filterOpen by rememberSaveable { mutableStateOf(false) }
    if (filterOpen) {
        PerformerFilterScreen(
            scope = viewModel.scope,
            sort = viewModel.sort,
            countResults = { scope, sort ->
                profile?.let {
                    StashGraphQlClient(it).findPerformerCardsPage(perPage = 1, query = viewModel.query, scope = scope, sort = sort).totalCount
                }
            },
            onApply = { scope, sort ->
                viewModel.scope = scope
                viewModel.sort = sort
            },
            onDismiss = { filterOpen = false },
        )
    }
    val chips = buildList {
        if (viewModel.scope == PerformerListScope.Following) {
            add(
                StashHeaderChip(
                    key = "scope",
                    label = stashString(PerformerListScope.Following.labelRes),
                    onClick = { filterOpen = true },
                    onClear = { viewModel.scope = PerformerListScope.All },
                ),
            )
        }
        if (viewModel.sort != PerformerListSort.Name) {
            add(
                StashHeaderChip(
                    key = "sort",
                    label = stashString(viewModel.sort.labelRes),
                    onClick = { filterOpen = true },
                    onClear = { viewModel.sort = PerformerListSort.Name },
                ),
            )
        }
    }
    val large = viewModel.layout == StashBrowseLayout.Large

    StashBrowseHeaderScaffold(
        state = headerState,
        searchValue = searchText,
        onSearchChange = { searchText = it },
        onClearSearch = { searchText = "" },
        searchPlaceholder = stashString(R.string.performers_search),
        filterCount = chips.size,
        onOpenFilter = { filterOpen = true },
        layout = viewModel.layout,
        onToggleLayout = { viewModel.layout = if (large) StashBrowseLayout.Compact else StashBrowseLayout.Large },
        horizontalPadding = horizontalPadding,
        enabled = profile != null,
        tabs = null,
        chips = chips,
        modifier = Modifier.fillMaxSize(),
    ) { padding ->
        val top = padding.calculateTopPadding()
        when {
            profile == null -> StashEmptyState(
                state = StashEmptyStateModel(
                    title = stashString(R.string.performers_title),
                    message = stashString(R.string.performers_not_configured),
                ),
                modifier = Modifier.padding(top = top).padding(horizontal = horizontalPadding),
            )
            state.items.isEmpty() && state.error != null -> StashErrorState(
                state = StashErrorStateModel(
                    title = stashString(R.string.performers_error_title),
                    message = state.error,
                ),
                modifier = Modifier.padding(top = top).padding(horizontal = horizontalPadding),
                onRetry = viewModel.loader::retry,
            )
            state.items.isEmpty() && state.hasLoadedOnce && !state.isLoading -> {
                if (viewModel.scope == PerformerListScope.Following && viewModel.query.isBlank()) {
                    StashEmptyState(
                        state = StashEmptyStateModel(
                            title = stashString(R.string.performers_following_empty_title),
                            message = stashString(R.string.performers_following_empty_message),
                            primaryActionLabel = stashString(R.string.performers_show_all),
                        ),
                        modifier = Modifier.padding(top = top).padding(horizontal = horizontalPadding),
                        onPrimaryAction = { viewModel.scope = PerformerListScope.All },
                    )
                } else {
                    StashEmptyState(
                        state = StashEmptyStateModel(
                            title = stashString(R.string.performers_empty_title),
                            message = stashString(R.string.performers_empty_message),
                        ),
                        modifier = Modifier.padding(top = top).padding(horizontal = horizontalPadding),
                    )
                }
            }
            else -> LazyVerticalGrid(
                columns = if (large) {
                    GridCells.Fixed(if (isFoldLikeLayout) 3 else 2)
                } else {
                    GridCells.Adaptive(minSize = if (isFoldLikeLayout) 140.dp else 104.dp)
                },
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = horizontalPadding,
                    end = horizontalPadding,
                    top = top,
                    bottom = StashSpacing.BottomContentPadding,
                ),
                horizontalArrangement = Arrangement.spacedBy(StashSpacing.CardGap),
                verticalArrangement = Arrangement.spacedBy(StashSpacing.SectionGap),
            ) {
                if (state.hasLoadedOnce) {
                    item(key = "count", span = { GridItemSpan(maxLineSpan) }, contentType = "count") {
                        Text(
                            text = stashString(R.string.performers_count, state.totalCount),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(state.items, key = { it.id }, contentType = { "performer" }) { performer ->
                    PerformerTile(
                        performer = performer,
                        serverProfile = profile,
                        onClick = { onOpenPerformer(performer.id) },
                    )
                }
                if (state.isLoading || state.error != null) {
                    item(key = "footer", span = { GridItemSpan(maxLineSpan) }) {
                        PagedFooter(
                            isLoading = state.isLoading,
                            error = state.error,
                            onRetry = viewModel.loader::retry,
                        )
                    }
                }
            }
        }
    }
}

/** Filter screen of the performer list: who to show and how to sort them. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PerformerFilterScreen(
    scope: PerformerListScope,
    sort: PerformerListSort,
    countResults: suspend (PerformerListScope, PerformerListSort) -> Int?,
    onApply: (PerformerListScope, PerformerListSort) -> Unit,
    onDismiss: () -> Unit,
) {
    var draftScope by remember { mutableStateOf(scope) }
    var draftSort by remember { mutableStateOf(sort) }
    StashFilterScreen(
        title = stashString(R.string.browse_filter_button),
        draftKey = draftScope to draftSort,
        countResults = { countResults(draftScope, draftSort) },
        onReset = {
            draftScope = PerformerListScope.All
            draftSort = PerformerListSort.Name
        },
        onApply = {
            onApply(draftScope, draftSort)
            onDismiss()
        },
        onDismiss = onDismiss,
    ) {
        StashFilterBlockLabel(stashString(R.string.filter_screen_view))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            PerformerListScope.entries.forEachIndexed { index, entry ->
                SegmentedButton(
                    selected = draftScope == entry,
                    onClick = { draftScope = entry },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = PerformerListScope.entries.size),
                ) { Text(stashString(entry.labelRes)) }
            }
        }
        StashFilterBlockLabel(stashString(R.string.filter_screen_sort), modifier = Modifier.padding(top = 8.dp))
        FlowRow(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            PerformerListSort.entries.forEach { entry ->
                StashVisibilityFilterChip(
                    selected = draftSort == entry,
                    onClick = { draftSort = entry },
                    label = { Text(stashString(entry.labelRes)) },
                )
            }
        }
    }
}

/** Spinner while the next page loads, or the error with a retry button. */
@Composable
internal fun PagedFooter(
    isLoading: Boolean,
    error: String?,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = StashSpacing.CardGap),
        contentAlignment = Alignment.Center,
    ) {
        when {
            isLoading -> CircularProgressIndicator()
            error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stashString(R.string.performer_load_more_failed, error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = onRetry) { Text(stashString(R.string.auto_kr_0031)) }
            }
        }
    }
}
