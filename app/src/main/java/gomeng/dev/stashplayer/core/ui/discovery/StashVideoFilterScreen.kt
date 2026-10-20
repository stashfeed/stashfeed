package gomeng.dev.stashplayer.core.ui.discovery

import gomeng.dev.stashplayer.core.ui.designsystem.StashRadius
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import gomeng.dev.stashplayer.R
import gomeng.dev.stashplayer.core.local.LocalSavedVideoFilter
import gomeng.dev.stashplayer.core.model.StashMediaFormatFilter
import gomeng.dev.stashplayer.core.model.StashOCounterComparator
import gomeng.dev.stashplayer.core.model.StashPlaybackState
import gomeng.dev.stashplayer.core.model.StashRatingFilterSliderSelection
import gomeng.dev.stashplayer.core.model.StashSelectedEntity
import gomeng.dev.stashplayer.core.model.StashSelectedTag
import gomeng.dev.stashplayer.core.model.StashServerSavedFilter
import gomeng.dev.stashplayer.core.model.StashSortDirection
import gomeng.dev.stashplayer.core.model.StashVideoFileType
import gomeng.dev.stashplayer.core.model.StashVideoFilterCategory
import gomeng.dev.stashplayer.core.model.StashVideoFilterState
import gomeng.dev.stashplayer.core.model.buildStashDateRangeFromInputs
import gomeng.dev.stashplayer.core.model.buildStashDurationRangeFromMinuteInputs
import gomeng.dev.stashplayer.core.model.buildStashOCounterFilterFromInput
import gomeng.dev.stashplayer.core.model.buildStashRatingRangeFromRatingStepSelection
import gomeng.dev.stashplayer.core.model.findMatchingStashDurationPreset
import gomeng.dev.stashplayer.core.model.nextStashRandomSortSeed
import gomeng.dev.stashplayer.core.model.normalizeStashRatingFilterSliderSelection
import gomeng.dev.stashplayer.core.model.stashDurationPresetOptions
import gomeng.dev.stashplayer.core.model.stashOCounterComparatorOptions
import gomeng.dev.stashplayer.core.model.stashResolutionFilterOptions
import gomeng.dev.stashplayer.core.model.toStashRatingStepFromRating100
import gomeng.dev.stashplayer.core.model.toggleStashPlaybackState
import gomeng.dev.stashplayer.core.model.toggleStashVideoFileType
import gomeng.dev.stashplayer.core.model.withServerSavedFilter
import gomeng.dev.stashplayer.core.ui.components.RatingFilterSliderPair
import gomeng.dev.stashplayer.core.ui.components.StashFilterActionRow
import gomeng.dev.stashplayer.core.ui.components.StashFilterScreen
import gomeng.dev.stashplayer.core.ui.components.StashFilterSection
import gomeng.dev.stashplayer.core.ui.components.StashFilterSortBlock
import gomeng.dev.stashplayer.core.ui.components.StashFilterSwitchRow
import gomeng.dev.stashplayer.core.ui.components.StashHeaderChip
import gomeng.dev.stashplayer.core.ui.components.StashInlineEntityPicker
import gomeng.dev.stashplayer.core.ui.components.rememberStashFilterSectionState
import gomeng.dev.stashplayer.core.ui.designsystem.StashVisibilityFilterChip
import gomeng.dev.stashplayer.core.ui.i18n.stashString

/** What the video filter screen edits: sort, page size and the filter itself. */
data class StashVideoFilterDraft<T>(
    val sort: T,
    val direction: StashSortDirection,
    val pageSize: Int?,
    val filter: StashVideoFilterState,
)

/** Selection actions offered in the filter screen's "Actions" section; each closes the screen. */
data class StashSceneSelectionActions(
    val selectionCount: Int,
    val visibleCount: Int,
    val onSelectAll: () -> Unit,
    val onInvert: () -> Unit,
    val onClear: () -> Unit,
    val onPlay: () -> Unit,
    val onDelete: (() -> Unit)?,
)

/** Section keys of the video filter screen; a chip in the header opens the screen at its section. */
object StashVideoFilterSectionKeys {
    const val Saved = "saved"
    const val Tags = "tags"
    const val People = "people"
    const val DateDuration = "date"
    const val RatingQuality = "rating"
    const val Local = "local"
    const val Actions = "actions"
}

fun StashVideoFilterCategory.sectionKey(): String = when (this) {
    StashVideoFilterCategory.Tag -> StashVideoFilterSectionKeys.Tags
    StashVideoFilterCategory.Performer,
    StashVideoFilterCategory.Studio,
    -> StashVideoFilterSectionKeys.People
    StashVideoFilterCategory.DateRange,
    StashVideoFilterCategory.DurationRange,
    StashVideoFilterCategory.OCounter,
    StashVideoFilterCategory.PlaybackState,
    -> StashVideoFilterSectionKeys.DateDuration
    StashVideoFilterCategory.Rating,
    StashVideoFilterCategory.MediaFormat,
    -> StashVideoFilterSectionKeys.RatingQuality
    StashVideoFilterCategory.LocalFavorite -> StashVideoFilterSectionKeys.Local
    StashVideoFilterCategory.RandomShuffle -> ""
    StashVideoFilterCategory.SavedFilter -> StashVideoFilterSectionKeys.Saved
}

/** Removes one category from a video filter (the × on a header chip). */
fun StashVideoFilterState.cleared(category: StashVideoFilterCategory): StashVideoFilterState = when (category) {
    StashVideoFilterCategory.Tag -> copy(tags = emptyList())
    StashVideoFilterCategory.Performer -> copy(performers = emptyList())
    StashVideoFilterCategory.Studio -> copy(studios = emptyList())
    StashVideoFilterCategory.DateRange -> copy(dateRange = null)
    StashVideoFilterCategory.DurationRange -> copy(durationRange = null)
    StashVideoFilterCategory.OCounter -> copy(oCounterFilter = null)
    StashVideoFilterCategory.Rating -> copy(ratingRange = null)
    StashVideoFilterCategory.PlaybackState -> copy(playbackState = null)
    StashVideoFilterCategory.LocalFavorite -> copy(localFavoriteOnly = false)
    StashVideoFilterCategory.MediaFormat -> copy(mediaFormat = StashMediaFormatFilter())
    StashVideoFilterCategory.RandomShuffle -> copy(randomShuffle = false, randomShuffleSeed = null)
    StashVideoFilterCategory.SavedFilter -> copy(savedFilter = null)
}

/** Header chips for the active criteria of a video filter. */
fun StashVideoFilterState.headerChips(
    onOpenSection: (String) -> Unit,
    onApply: (StashVideoFilterState) -> Unit,
): List<StashHeaderChip> = activeFilterChips().map { chip ->
    StashHeaderChip(
        key = chip.category.id,
        label = chip.label,
        onClick = { onOpenSection(chip.category.sectionKey()) },
        onClear = { onApply(cleared(chip.category)) },
    )
}

/**
 * Fullscreen filter for scene lists (Explore videos, a performer's scenes). Everything is edited as a draft and
 * only applied with "Show N results".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> StashVideoFilterScreen(
    initial: StashVideoFilterDraft<T>,
    sortOptions: List<T>,
    sortLabel: (T) -> String,
    pageSizeOptions: List<Int>?,
    searchTags: suspend (String) -> List<StashSelectedEntity>,
    searchPerformers: (suspend (String) -> List<StashSelectedEntity>)?,
    searchStudios: suspend (String) -> List<StashSelectedEntity>,
    countResults: (suspend (StashVideoFilterDraft<T>) -> Int?)?,
    onApply: (StashVideoFilterDraft<T>) -> Unit,
    onDismiss: () -> Unit,
    initialSection: String? = null,
    localSavedFilters: List<LocalSavedVideoFilter> = emptyList(),
    serverFilters: List<StashServerSavedFilter> = emptyList(),
    onSaveFilter: ((String, StashVideoFilterState) -> Unit)? = null,
    onDeleteSavedFilter: ((LocalSavedVideoFilter) -> Unit)? = null,
    showRandom: Boolean = true,
    showLocal: Boolean = true,
    actions: StashSceneSelectionActions? = null,
) {
    var draft by remember { mutableStateOf(initial) }
    val filter = draft.filter
    fun update(transform: (StashVideoFilterState) -> StashVideoFilterState) {
        draft = draft.copy(filter = transform(draft.filter))
    }
    var expanded by rememberStashFilterSectionState(initialSection?.takeIf { it.isNotBlank() })
    val chipsByCategory = filter.activeFilterChips().groupBy { it.category }
    fun summaryOf(vararg categories: StashVideoFilterCategory): String? =
        categories.flatMap { chipsByCategory[it].orEmpty() }.joinToString(" · ") { it.label }.ifBlank { null }
    fun countOf(vararg categories: StashVideoFilterCategory) = categories.count { chipsByCategory.containsKey(it) }

    StashFilterScreen(
        title = stashString(R.string.browse_filter_button),
        draftKey = draft,
        countResults = countResults?.let { counter -> { counter(draft) } },
        onReset = { draft = draft.copy(filter = StashVideoFilterState()) },
        onApply = {
            onApply(draft)
            onDismiss()
        },
        onDismiss = onDismiss,
    ) {
        StashFilterSortBlock(
            options = sortOptions,
            selected = draft.sort,
            optionLabel = sortLabel,
            onSelect = { draft = draft.copy(sort = it) },
            direction = draft.direction,
            onDirectionChange = { draft = draft.copy(direction = it) },
            random = if (showRandom) filter.randomShuffle else null,
            onRandomChange = if (showRandom) {
                { on -> update { it.copy(randomShuffle = on, randomShuffleSeed = if (on) nextStashRandomSortSeed() else null) } }
            } else {
                null
            },
            pageSizes = pageSizeOptions,
            pageSize = draft.pageSize,
            onPageSizeChange = { draft = draft.copy(pageSize = it) },
        )

        if (onSaveFilter != null || serverFilters.isNotEmpty() || localSavedFilters.isNotEmpty()) {
            StashFilterSection(
                key = StashVideoFilterSectionKeys.Saved,
                title = stashString(R.string.auto_kr_0344),
                summary = filter.savedFilter?.name ?: stashString(R.string.filter_screen_saved_count, localSavedFilters.size + serverFilters.size),
                activeCount = countOf(StashVideoFilterCategory.SavedFilter),
                expandedKey = expanded,
                onExpandedKeyChange = { expanded = it },
            ) {
                SavedVideoFiltersEditor(
                    filter = filter,
                    localSavedFilters = localSavedFilters,
                    serverFilters = serverFilters,
                    onApplyFilter = { applied -> draft = draft.copy(filter = applied) },
                    onSaveFilter = onSaveFilter,
                    onDeleteSavedFilter = onDeleteSavedFilter,
                )
            }
        }

        StashFilterSection(
            key = StashVideoFilterSectionKeys.Tags,
            title = stashString(R.string.filter_screen_tags),
            summary = filter.tags.joinToString(", ") { it.name }.ifBlank { stashString(R.string.filter_screen_any) },
            activeCount = countOf(StashVideoFilterCategory.Tag),
            expandedKey = expanded,
            onExpandedKeyChange = { expanded = it },
        ) {
            StashInlineEntityPicker(
                searchLabel = stashString(R.string.filter_screen_search_tags),
                selected = filter.tags.map { StashSelectedEntity(it.id, it.name) },
                onSelectedChange = { picked -> update { it.copy(tags = picked.map { e -> StashSelectedTag(e.id, e.name) }) } },
                search = searchTags,
            )
            StashFilterSwitchRow(
                label = stashString(R.string.filter_screen_tags_match_any),
                checked = filter.tagsMatchAny,
                onCheckedChange = { on -> update { it.copy(tagsMatchAny = on) } },
            )
        }

        StashFilterSection(
            key = StashVideoFilterSectionKeys.People,
            title = stashString(
                if (searchPerformers != null) R.string.filter_screen_people else R.string.people_filter_tab_studios,
            ),
            summary = summaryOf(StashVideoFilterCategory.Performer, StashVideoFilterCategory.Studio)
                ?: stashString(R.string.filter_screen_any),
            activeCount = countOf(StashVideoFilterCategory.Performer, StashVideoFilterCategory.Studio),
            expandedKey = expanded,
            onExpandedKeyChange = { expanded = it },
        ) {
            if (searchPerformers != null) {
                FilterSubheading(stashString(R.string.people_filter_tab_performers))
                StashInlineEntityPicker(
                    searchLabel = stashString(R.string.people_filter_search_performers),
                    selected = filter.performers,
                    onSelectedChange = { picked -> update { it.copy(performers = picked) } },
                    search = searchPerformers,
                )
                FilterSubheading(stashString(R.string.people_filter_tab_studios))
            }
            StashInlineEntityPicker(
                searchLabel = stashString(R.string.people_filter_search_studios),
                selected = filter.studios,
                onSelectedChange = { picked -> update { it.copy(studios = picked) } },
                search = searchStudios,
            )
        }

        StashFilterSection(
            key = StashVideoFilterSectionKeys.DateDuration,
            title = stashString(R.string.filter_screen_date_duration),
            summary = summaryOf(
                StashVideoFilterCategory.DateRange,
                StashVideoFilterCategory.DurationRange,
                StashVideoFilterCategory.OCounter,
                StashVideoFilterCategory.PlaybackState,
            ) ?: stashString(R.string.filter_screen_any),
            activeCount = countOf(
                StashVideoFilterCategory.DateRange,
                StashVideoFilterCategory.DurationRange,
                StashVideoFilterCategory.OCounter,
                StashVideoFilterCategory.PlaybackState,
            ),
            expandedKey = expanded,
            onExpandedKeyChange = { expanded = it },
        ) {
            DateDurationEditor(filter = filter, onChange = { updated -> draft = draft.copy(filter = updated) })
        }

        StashFilterSection(
            key = StashVideoFilterSectionKeys.RatingQuality,
            title = stashString(R.string.filter_screen_rating_quality),
            summary = summaryOf(StashVideoFilterCategory.Rating, StashVideoFilterCategory.MediaFormat)
                ?: stashString(R.string.filter_screen_any),
            activeCount = countOf(StashVideoFilterCategory.Rating, StashVideoFilterCategory.MediaFormat),
            expandedKey = expanded,
            onExpandedKeyChange = { expanded = it },
        ) {
            RatingQualityEditor(filter = filter, onChange = { updated -> draft = draft.copy(filter = updated) })
        }

        if (showLocal) {
            StashFilterSection(
                key = StashVideoFilterSectionKeys.Local,
                title = stashString(R.string.filter_screen_local),
                summary = summaryOf(StashVideoFilterCategory.LocalFavorite) ?: stashString(R.string.filter_screen_any),
                activeCount = countOf(StashVideoFilterCategory.LocalFavorite),
                expandedKey = expanded,
                onExpandedKeyChange = { expanded = it },
            ) {
                StashFilterSwitchRow(
                    label = stashString(R.string.auto_kr_0159),
                    supporting = stashString(R.string.filter_screen_local_hint),
                    checked = filter.localFavoriteOnly,
                    onCheckedChange = { on -> update { it.copy(localFavoriteOnly = on) } },
                )
            }
        }

        if (actions != null) {
            StashFilterSection(
                key = StashVideoFilterSectionKeys.Actions,
                title = stashString(R.string.filter_screen_actions),
                summary = if (actions.selectionCount > 0) {
                    stashString(R.string.filter_screen_selected_count, actions.selectionCount)
                } else {
                    stashString(R.string.filter_screen_actions_hint)
                },
                activeCount = 0,
                expandedKey = expanded,
                onExpandedKeyChange = { expanded = it },
            ) {
                fun run(action: () -> Unit) {
                    action()
                    onDismiss()
                }
                StashFilterActionRow(stashString(R.string.filter_screen_select_all, actions.visibleCount), { run(actions.onSelectAll) }, actions.visibleCount > 0)
                StashFilterActionRow(stashString(R.string.filter_screen_invert_selection), { run(actions.onInvert) }, actions.visibleCount > 0)
                StashFilterActionRow(stashString(R.string.filter_screen_play_selection), { run(actions.onPlay) }, actions.selectionCount > 0)
                actions.onDelete?.let { delete ->
                    StashFilterActionRow(stashString(R.string.filter_screen_delete_selection), { run(delete) }, actions.selectionCount > 0)
                }
                StashFilterActionRow(stashString(R.string.filter_screen_clear_selection), { run(actions.onClear) }, actions.selectionCount > 0)
            }
        }
    }
}

@Composable
internal fun FilterSubheading(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SavedVideoFiltersEditor(
    filter: StashVideoFilterState,
    localSavedFilters: List<LocalSavedVideoFilter>,
    serverFilters: List<StashServerSavedFilter>,
    onApplyFilter: (StashVideoFilterState) -> Unit,
    onSaveFilter: ((String, StashVideoFilterState) -> Unit)?,
    onDeleteSavedFilter: ((LocalSavedVideoFilter) -> Unit)?,
) {
    if (localSavedFilters.isNotEmpty()) {
        FilterSubheading(stashString(R.string.filter_screen_saved_on_device))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            localSavedFilters.forEach { saved ->
                InputChip(
                    selected = filter == saved.filterState,
                    onClick = { onApplyFilter(saved.filterState) },
                    label = { Text(saved.name, maxLines = 1) },
                    trailingIcon = onDeleteSavedFilter?.let { delete ->
                        {
                            IconButton(onClick = { delete(saved) }, modifier = Modifier.size(24.dp)) {
                                Icon(
                                    Icons.Outlined.Close,
                                    contentDescription = stashString(R.string.filter_screen_delete_saved, saved.name),
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    },
                )
            }
        }
    }
    if (serverFilters.isNotEmpty()) {
        FilterSubheading(stashString(R.string.saved_filters_from_stash))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            serverFilters.forEach { server ->
                StashVisibilityFilterChip(
                    selected = filter.savedFilter?.id == "stash:${server.id}",
                    onClick = {
                        onApplyFilter(
                            if (filter.savedFilter?.id == "stash:${server.id}") filter.copy(savedFilter = null) else filter.withServerSavedFilter(server),
                        )
                    },
                    label = { Text(server.name, maxLines = 1) },
                )
            }
        }
    }
    if (onSaveFilter != null) {
        var name by remember { mutableStateOf("") }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text(stashString(R.string.filter_screen_save_as)) },
                shape = RoundedCornerShape(StashRadius.M),
            )
            Button(
                onClick = {
                    onSaveFilter(name.trim(), filter)
                    name = ""
                },
                enabled = name.isNotBlank() && !filter.isEmpty,
            ) { Text(stashString(R.string.filter_screen_save)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DateDurationEditor(filter: StashVideoFilterState, onChange: (StashVideoFilterState) -> Unit) {
    var startDate by remember { mutableStateOf(filter.dateRange?.start.orEmpty()) }
    var endDate by remember { mutableStateOf(filter.dateRange?.end.orEmpty()) }
    var minMinutes by remember { mutableStateOf(filter.durationRange?.minSeconds?.let { (it / 60).toString() }.orEmpty()) }
    var maxMinutes by remember { mutableStateOf(filter.durationRange?.maxSeconds?.let { (it / 60).toString() }.orEmpty()) }
    var oComparator by remember { mutableStateOf(filter.oCounterFilter?.comparator ?: stashOCounterComparatorOptions().first()) }
    var oValue by remember { mutableStateOf(filter.oCounterFilter?.value?.toString().orEmpty()) }
    // A reset of the whole draft clears the inputs too.
    if (filter.dateRange == null && (startDate.isNotEmpty() || endDate.isNotEmpty()) && buildStashDateRangeFromInputs(startDate, endDate) != null) {
        startDate = ""
        endDate = ""
    }
    if (filter.durationRange == null && buildStashDurationRangeFromMinuteInputs(minMinutes, maxMinutes) != null) {
        minMinutes = ""
        maxMinutes = ""
    }
    if (filter.oCounterFilter == null && buildStashOCounterFilterFromInput(oComparator, oValue) != null) oValue = ""

    fun commit(
        start: String = startDate,
        end: String = endDate,
        min: String = minMinutes,
        max: String = maxMinutes,
        comparator: StashOCounterComparator = oComparator,
        oCount: String = oValue,
        playback: StashPlaybackState? = filter.playbackState,
    ) {
        onChange(
            filter.copy(
                dateRange = buildStashDateRangeFromInputs(start, end),
                durationRange = buildStashDurationRangeFromMinuteInputs(min, max),
                oCounterFilter = buildStashOCounterFilterFromInput(comparator, oCount),
                playbackState = playback,
            ),
        )
    }

    FilterSubheading(stashString(R.string.auto_kr_0304))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = startDate,
            onValueChange = { startDate = it; commit(start = it) },
            modifier = Modifier.weight(1f),
            singleLine = true,
            label = { Text(stashString(R.string.auto_kr_0306)) },
            placeholder = { Text("YYYY-MM-DD") },
            shape = RoundedCornerShape(StashRadius.M),
        )
        OutlinedTextField(
            value = endDate,
            onValueChange = { endDate = it; commit(end = it) },
            modifier = Modifier.weight(1f),
            singleLine = true,
            label = { Text(stashString(R.string.auto_kr_0307)) },
            placeholder = { Text("YYYY-MM-DD") },
            shape = RoundedCornerShape(StashRadius.M),
        )
    }

    FilterSubheading(stashString(R.string.auto_kr_0308))
    val selectedPreset = findMatchingStashDurationPreset(buildStashDurationRangeFromMinuteInputs(minMinutes, maxMinutes))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        stashDurationPresetOptions().forEach { preset ->
            StashVisibilityFilterChip(
                selected = selectedPreset == preset,
                onClick = {
                    val min = if (selectedPreset == preset) "" else preset.minMinutes?.toString().orEmpty()
                    val max = if (selectedPreset == preset) "" else preset.maxMinutes?.toString().orEmpty()
                    minMinutes = min
                    maxMinutes = max
                    commit(min = min, max = max)
                },
                label = { Text(preset.label) },
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = minMinutes,
            onValueChange = { v -> v.filter(Char::isDigit).let { minMinutes = it; commit(min = it) } },
            modifier = Modifier.weight(1f),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            label = { Text(stashString(R.string.auto_kr_0310)) },
            shape = RoundedCornerShape(StashRadius.M),
        )
        OutlinedTextField(
            value = maxMinutes,
            onValueChange = { v -> v.filter(Char::isDigit).let { maxMinutes = it; commit(max = it) } },
            modifier = Modifier.weight(1f),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            label = { Text(stashString(R.string.auto_kr_0312)) },
            shape = RoundedCornerShape(StashRadius.M),
        )
    }

    FilterSubheading(stashString(R.string.scene_filter_o_count_label))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        stashOCounterComparatorOptions().forEach { comparator ->
            StashVisibilityFilterChip(
                selected = oComparator == comparator,
                onClick = { oComparator = comparator; commit(comparator = comparator) },
                label = { Text(comparator.symbol) },
            )
        }
        OutlinedTextField(
            value = oValue,
            onValueChange = { v -> v.filter(Char::isDigit).let { oValue = it; commit(oCount = it) } },
            modifier = Modifier.weight(1f),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            placeholder = { Text("0") },
            shape = RoundedCornerShape(StashRadius.M),
        )
    }

    FilterSubheading(stashString(R.string.auto_kr_0314))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        StashPlaybackState.entries.forEach { state ->
            StashVisibilityFilterChip(
                selected = filter.playbackState == state,
                onClick = { commit(playback = toggleStashPlaybackState(filter.playbackState, state)) },
                label = { Text(state.label) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RatingQualityEditor(filter: StashVideoFilterState, onChange: (StashVideoFilterState) -> Unit) {
    val selection = normalizeStashRatingFilterSliderSelection(
        minStep = filter.ratingRange?.min?.toStashRatingStepFromRating100()?.takeIf { it > 0 },
        maxStep = filter.ratingRange?.max?.toStashRatingStepFromRating100()?.takeIf { it > 0 },
    )
    fun setRating(updated: StashRatingFilterSliderSelection) {
        onChange(filter.copy(ratingRange = buildStashRatingRangeFromRatingStepSelection(updated.minStep, updated.maxStep)))
    }
    FilterSubheading(stashString(R.string.auto_kr_0234))
    RatingFilterSliderPair(
        selection = selection,
        onMinStepChange = { step -> setRating(normalizeStashRatingFilterSliderSelection(step.takeIf { it > 0 }, selection.maxStep)) },
        onMaxStepChange = { step -> setRating(normalizeStashRatingFilterSliderSelection(selection.minStep, step.takeIf { it > 0 })) },
    )
    FilterSubheading(stashString(R.string.auto_kr_0335))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        stashResolutionFilterOptions().forEach { option ->
            StashVisibilityFilterChip(
                selected = filter.mediaFormat.resolution == option.resolution,
                onClick = { onChange(filter.copy(mediaFormat = filter.mediaFormat.copy(resolution = option.resolution))) },
                label = { Text(option.label) },
            )
        }
    }
    FilterSubheading(stashString(R.string.auto_kr_0337))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        StashVideoFileType.entries.forEach { type ->
            StashVisibilityFilterChip(
                selected = type in filter.mediaFormat.fileTypes,
                onClick = {
                    onChange(filter.copy(mediaFormat = filter.mediaFormat.copy(fileTypes = toggleStashVideoFileType(filter.mediaFormat.fileTypes, type))))
                },
                label = { Text(type.label) },
            )
        }
    }
}
