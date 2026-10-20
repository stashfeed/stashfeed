package gomeng.dev.stashplayer.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SegmentedButton
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

private const val ENTITY_FILTER_SEARCH_DEBOUNCE_MS = 250L

/** One tab of [StashMultiEntityFilterSheet]: a server search plus the current selection for it. */
data class StashEntityFilterTab(
    val label: String,
    val searchLabel: String,
    val selected: List<StashSelectedEntity>,
    val search: suspend (String) -> List<StashSelectedEntity>,
    /**
     * Whether [selected] lists entries to leave OUT ("all except these"). Null hides the include/exclude
     * switch and "select all" for this tab.
     */
    val excluded: Boolean? = null,
)

/** Picks performers and studios for a video filter (Explore). */
@Composable
fun StashPeopleFilterSheet(
    videoFilter: StashVideoFilterState,
    searchPerformers: suspend (String) -> List<StashSelectedEntity>,
    searchStudios: suspend (String) -> List<StashSelectedEntity>,
    onApply: (performers: List<StashSelectedEntity>, studios: List<StashSelectedEntity>) -> Unit,
    onDismiss: () -> Unit,
) {
    StashMultiEntityFilterSheet(
        title = stashString(R.string.video_filter_people_title),
        hint = stashString(R.string.people_filter_hint),
        tabs = listOf(
            StashEntityFilterTab(
                label = stashString(R.string.people_filter_tab_performers),
                searchLabel = stashString(R.string.people_filter_search_performers),
                selected = videoFilter.performers,
                search = searchPerformers,
            ),
            StashEntityFilterTab(
                label = stashString(R.string.people_filter_tab_studios),
                searchLabel = stashString(R.string.people_filter_search_studios),
                selected = videoFilter.studios,
                search = searchStudios,
            ),
        ),
        onApply = { selections -> onApply(selections[0], selections[1]) },
        onDismiss = onDismiss,
    )
}

/**
 * Bottom sheet with one searchable entity picker per tab. Each tab loads its own options through
 * [StashEntityFilterTab.search]; [onApply] receives the selections in tab order.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StashMultiEntityFilterSheet(
    title: String,
    hint: String,
    tabs: List<StashEntityFilterTab>,
    onApply: (List<List<StashSelectedEntity>>) -> Unit,
    onDismiss: () -> Unit,
    extraContent: (@Composable ColumnScope.() -> Unit)? = null,
    onReset: () -> Unit = {},
    /** Per tab: true = the tab's selection is an exclusion list. Called right before [onApply]. */
    onApplyExcluded: (List<Boolean>) -> Unit = {},
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tab = tabs[selectedTab]
    var drafts by remember(tabs.map { it.selected }) { mutableStateOf(tabs.map { it.selected }) }
    var excludedModes by remember(tabs.map { it.excluded }) { mutableStateOf(tabs.map { it.excluded == true }) }
    var query by remember(selectedTab) { mutableStateOf("") }
    var options by remember(selectedTab) { mutableStateOf<List<StashSelectedEntity>>(emptyList()) }
    var isLoading by remember(selectedTab) { mutableStateOf(false) }
    var error by remember(selectedTab) { mutableStateOf<String?>(null) }
    var retryToken by remember { mutableIntStateOf(0) }

    LaunchedEffect(selectedTab, query, retryToken) {
        delay(ENTITY_FILTER_SEARCH_DEBOUNCE_MS)
        isLoading = true
        error = null
        runCatching { tab.search(query) }
            .onSuccess { options = it }
            .onFailure { error = it.message.orEmpty() }
        isLoading = false
    }

    val draft = drafts[selectedTab]
    val excluding = excludedModes[selectedTab]
    // In exclude mode the draft holds the entries left out, so a chip shows "checked" when it is NOT in it.
    fun toggle(entity: StashSelectedEntity) {
        val updated = if (draft.any { it.id == entity.id }) draft.filterNot { it.id == entity.id } else draft + entity
        drafts = drafts.toMutableList().also { it[selectedTab] = updated }
    }
    fun setMode(exclude: Boolean, clear: Boolean) {
        excludedModes = excludedModes.toMutableList().also { it[selectedTab] = exclude }
        if (clear) drafts = drafts.toMutableList().also { it[selectedTab] = emptyList() }
    }
    val draftIds = draft.map { it.id }.toSet()
    fun isChecked(entity: StashSelectedEntity) = (entity.id in draftIds) != excluding
    val orderedOptions = (draft + options).distinctBy { it.id }
    val totalSelected = drafts.sumOf { it.size }

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
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = stashString(R.string.auto_kr_0192))
                }
            }
            extraContent?.invoke(this)
            ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 0.dp) {
                tabs.forEachIndexed { index, entry ->
                    val count = drafts[index].size
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(if (count > 0) "${entry.label} ($count)" else entry.label) },
                    )
                }
            }
            if (tab.excluded != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(StashSpacing.ChipGap)) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                        SegmentedButton(
                            selected = !excluding,
                            onClick = { if (excluding) setMode(exclude = false, clear = true) },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        ) { Text(stashString(R.string.entity_filter_mode_only), maxLines = 1) }
                        SegmentedButton(
                            selected = excluding,
                            onClick = { if (!excluding) setMode(exclude = true, clear = true) },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        ) { Text(stashString(R.string.entity_filter_mode_except), maxLines = 1) }
                    }
                    // "Select all" = everything, then tapping entries leaves them out.
                    TextButton(onClick = { setMode(exclude = !(excluding && draft.isEmpty()), clear = true) }) {
                        Text(
                            stashString(
                                if (excluding && draft.isEmpty()) R.string.entity_filter_select_none else R.string.entity_filter_select_all,
                            ),
                        )
                    }
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(tab.searchLabel) },
            )
            if (excluding) {
                Text(
                    text = if (draft.isEmpty()) {
                        stashString(R.string.entity_filter_all_selected)
                    } else {
                        stashString(R.string.entity_filter_all_except, draft.size)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (draft.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(StashSpacing.ChipGap),
                    verticalArrangement = Arrangement.spacedBy(StashSpacing.ChipGap),
                ) {
                    draft.forEach { entity ->
                        StashVisibilityFilterChip(
                            selected = !excluding,
                            onClick = { toggle(entity) },
                            label = {
                                Text(
                                    if (excluding) "− ${entity.name}" else "${entity.name} ×",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
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
                                selected = isChecked(entity),
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
                    text = stashString(R.string.people_filter_selected_count, totalSelected),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = {
                        drafts = tabs.map { emptyList() }
                        excludedModes = tabs.map { false }
                        onReset()
                    },
                ) {
                    Text(stashString(R.string.auto_kr_0374))
                }
                Button(
                    onClick = {
                        onApplyExcluded(excludedModes)
                        onApply(drafts)
                        onDismiss()
                    },
                ) {
                    Text(stashString(R.string.auto_kr_0316))
                }
            }
        }
    }
}
