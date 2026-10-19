package gomeng.dev.stashplayer.feature.performers

import androidx.compose.foundation.layout.Arrangement
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = StashSpacing.SectionGap),
        verticalArrangement = Arrangement.spacedBy(StashSpacing.CardGap),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = horizontalPadding),
            verticalArrangement = Arrangement.spacedBy(StashSpacing.CardGap),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(stashString(R.string.performers_title), style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.weight(1f))
                if (state.hasLoadedOnce) {
                    Text(
                        text = stashString(R.string.performers_count, state.totalCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                placeholder = { Text(stashString(R.string.performers_search)) },
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(StashSpacing.ChipGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PerformerListScope.entries.forEach { entry ->
                    FilterChip(
                        selected = viewModel.scope == entry,
                        onClick = { viewModel.scope = entry },
                        label = { Text(stashString(entry.labelRes)) },
                    )
                }
                Spacer(Modifier.weight(1f))
                PerformerSortMenu(
                    selected = viewModel.sort,
                    onSelect = { viewModel.sort = it },
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                profile == null -> StashEmptyState(
                    state = StashEmptyStateModel(
                        title = stashString(R.string.performers_title),
                        message = stashString(R.string.performers_not_configured),
                    ),
                    modifier = Modifier.padding(horizontal = horizontalPadding),
                )
                state.items.isEmpty() && state.error != null -> StashErrorState(
                    state = StashErrorStateModel(
                        title = stashString(R.string.performers_error_title),
                        message = state.error,
                    ),
                    modifier = Modifier.padding(horizontal = horizontalPadding),
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
                            modifier = Modifier.padding(horizontal = horizontalPadding),
                            onPrimaryAction = { viewModel.scope = PerformerListScope.All },
                        )
                    } else {
                        StashEmptyState(
                            state = StashEmptyStateModel(
                                title = stashString(R.string.performers_empty_title),
                                message = stashString(R.string.performers_empty_message),
                            ),
                            modifier = Modifier.padding(horizontal = horizontalPadding),
                        )
                    }
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = if (isFoldLikeLayout) 140.dp else 104.dp),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = horizontalPadding,
                        end = horizontalPadding,
                        bottom = StashSpacing.BottomContentPadding,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(StashSpacing.CardGap),
                    verticalArrangement = Arrangement.spacedBy(StashSpacing.SectionGap),
                ) {
                    items(state.items, key = { it.id }) { performer ->
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
}

@Composable
private fun PerformerSortMenu(
    selected: PerformerListSort,
    onSelect: (PerformerListSort) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Icon(Icons.Outlined.Sort, contentDescription = null)
            Spacer(Modifier.padding(start = 4.dp))
            Text(stashString(selected.labelRes))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            PerformerListSort.entries.forEach { sort ->
                DropdownMenuItem(
                    text = { Text(stashString(sort.labelRes)) },
                    onClick = {
                        expanded = false
                        onSelect(sort)
                    },
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
