package gomeng.dev.stashplayer.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import gomeng.dev.stashplayer.R
import gomeng.dev.stashplayer.core.model.StashSelectedEntity
import gomeng.dev.stashplayer.core.model.StashVideoFilterState
import gomeng.dev.stashplayer.core.ui.designsystem.StashSpacing
import gomeng.dev.stashplayer.core.ui.designsystem.StashVisibilityFilterChip
import gomeng.dev.stashplayer.core.ui.i18n.stashString
import kotlinx.coroutines.delay

private const val PEOPLE_FILTER_SEARCH_DEBOUNCE_MS = 250L

private enum class PeopleFilterTab { Performers, Studios }

/**
 * Picks performers and studios for a video filter. Loads its own options through [searchPerformers] /
 * [searchStudios] so callers only have to hand over the server search.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StashPeopleFilterSheet(
    videoFilter: StashVideoFilterState,
    searchPerformers: suspend (String) -> List<StashSelectedEntity>,
    searchStudios: suspend (String) -> List<StashSelectedEntity>,
    onApply: (performers: List<StashSelectedEntity>, studios: List<StashSelectedEntity>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(PeopleFilterTab.Performers.ordinal) }
    val tab = PeopleFilterTab.entries[selectedTab]
    var performersDraft by remember(videoFilter.performers) { mutableStateOf(videoFilter.performers) }
    var studiosDraft by remember(videoFilter.studios) { mutableStateOf(videoFilter.studios) }
    var query by remember(tab) { mutableStateOf("") }
    var options by remember(tab) { mutableStateOf<List<StashSelectedEntity>>(emptyList()) }
    var isLoading by remember(tab) { mutableStateOf(false) }
    var error by remember(tab) { mutableStateOf<String?>(null) }
    var retryToken by remember { mutableIntStateOf(0) }

    LaunchedEffect(tab, query, retryToken) {
        delay(PEOPLE_FILTER_SEARCH_DEBOUNCE_MS)
        isLoading = true
        error = null
        runCatching {
            when (tab) {
                PeopleFilterTab.Performers -> searchPerformers(query)
                PeopleFilterTab.Studios -> searchStudios(query)
            }
        }.onSuccess { options = it }
            .onFailure { error = it.message.orEmpty() }
        isLoading = false
    }

    val draft = if (tab == PeopleFilterTab.Performers) performersDraft else studiosDraft
    fun toggle(entity: StashSelectedEntity) {
        val updated = if (draft.any { it.id == entity.id }) draft.filterNot { it.id == entity.id } else draft + entity
        if (tab == PeopleFilterTab.Performers) performersDraft = updated else studiosDraft = updated
    }
    val selectedIds = draft.map { it.id }.toSet()
    val orderedOptions = (draft + options).distinctBy { it.id }

    // Fully expanded right away: half-open, the Apply row sits under the system navigation bar.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = StashSpacing.CardPadding, vertical = StashSpacing.CardGap),
            verticalArrangement = Arrangement.spacedBy(StashSpacing.CardGap),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stashString(R.string.video_filter_people_title), style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = stashString(R.string.people_filter_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = stashString(R.string.auto_kr_0192))
                }
            }
            TabRow(selectedTabIndex = selectedTab) {
                PeopleFilterTab.entries.forEach { entry ->
                    val count = if (entry == PeopleFilterTab.Performers) performersDraft.size else studiosDraft.size
                    val label = when (entry) {
                        PeopleFilterTab.Performers -> stashString(R.string.people_filter_tab_performers)
                        PeopleFilterTab.Studios -> stashString(R.string.people_filter_tab_studios)
                    }
                    Tab(
                        selected = selectedTab == entry.ordinal,
                        onClick = { selectedTab = entry.ordinal },
                        text = { Text(if (count > 0) "$label ($count)" else label) },
                    )
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = {
                    Text(
                        when (tab) {
                            PeopleFilterTab.Performers -> stashString(R.string.people_filter_search_performers)
                            PeopleFilterTab.Studios -> stashString(R.string.people_filter_search_studios)
                        },
                    )
                },
            )
            if (draft.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(StashSpacing.ChipGap),
                    verticalArrangement = Arrangement.spacedBy(StashSpacing.ChipGap),
                ) {
                    draft.forEach { entity ->
                        StashVisibilityFilterChip(
                            selected = true,
                            onClick = { toggle(entity) },
                            label = { Text("${entity.name} ×", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
            }
            Box(modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp)) {
                when {
                    error != null -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stashString(R.string.people_filter_load_failed, error.orEmpty()),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Button(onClick = { retryToken += 1 }) { Text(stashString(R.string.auto_kr_0031)) }
                    }
                    !isLoading && orderedOptions.isEmpty() -> Text(
                        stashString(R.string.people_filter_no_results),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(128.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                        contentPadding = PaddingValues(bottom = StashSpacing.SectionGap),
                        horizontalArrangement = Arrangement.spacedBy(StashSpacing.ChipGap),
                        verticalArrangement = Arrangement.spacedBy(StashSpacing.ChipGap),
                    ) {
                        items(orderedOptions, key = { it.id }) { entity ->
                            StashVisibilityFilterChip(
                                selected = entity.id in selectedIds,
                                onClick = { toggle(entity) },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(entity.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            )
                        }
                    }
                }
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(StashSpacing.CardGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stashString(R.string.people_filter_selected_count, performersDraft.size + studiosDraft.size),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = {
                        performersDraft = emptyList()
                        studiosDraft = emptyList()
                    },
                    enabled = performersDraft.isNotEmpty() || studiosDraft.isNotEmpty(),
                ) {
                    Text(stashString(R.string.auto_kr_0374))
                }
                Button(
                    onClick = {
                        onApply(performersDraft, studiosDraft)
                        onDismiss()
                    },
                ) {
                    Text(stashString(R.string.auto_kr_0316))
                }
            }
        }
    }
}
