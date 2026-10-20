package gomeng.dev.stashplayer.feature.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import gomeng.dev.stashplayer.R
import gomeng.dev.stashplayer.core.model.StashGallerySortOption
import gomeng.dev.stashplayer.core.model.StashRatingRange
import gomeng.dev.stashplayer.core.model.StashSelectedEntity
import gomeng.dev.stashplayer.core.model.StashServerSavedFilter
import gomeng.dev.stashplayer.core.model.StashSortDirection
import gomeng.dev.stashplayer.core.model.buildStashRatingRangeFromRatingStepSelection
import gomeng.dev.stashplayer.core.model.normalizeStashRatingFilterSliderSelection
import gomeng.dev.stashplayer.core.model.toStashRatingStepFromRating100
import gomeng.dev.stashplayer.core.ui.components.RatingFilterSliderPair
import gomeng.dev.stashplayer.core.ui.components.StashFilterActionRow
import gomeng.dev.stashplayer.core.ui.components.StashFilterScreen
import gomeng.dev.stashplayer.core.ui.components.StashFilterSection
import gomeng.dev.stashplayer.core.ui.components.StashFilterSortBlock
import gomeng.dev.stashplayer.core.ui.components.StashInlineEntityPicker
import gomeng.dev.stashplayer.core.ui.components.rememberStashFilterSectionState
import gomeng.dev.stashplayer.core.ui.designsystem.StashVisibilityFilterChip
import gomeng.dev.stashplayer.core.ui.i18n.stashString

internal const val GALLERY_SECTION_SAVED = "saved"
internal const val GALLERY_SECTION_RATING = "rating"
internal const val GALLERY_SECTION_MORE = "more"
internal const val GALLERY_SECTION_ACTIONS = "actions"

/** What the gallery / image filter screen edits. [reshuffle] asks for a new random seed on apply. */
internal data class GalleryFilterDraft<F>(
    val sort: StashGallerySortOption,
    val direction: StashSortDirection,
    val pageSize: Int,
    val filter: F,
    val reshuffle: Boolean = false,
)

/** One searchable entity criterion (tags, performers, …) of a gallery or image filter. */
internal data class GalleryEntitySectionSpec<F>(
    val key: String,
    val title: String,
    val searchLabel: String,
    val selected: (F) -> List<StashSelectedEntity>,
    val withSelected: (F, List<StashSelectedEntity>) -> F,
    val search: suspend (String) -> List<StashSelectedEntity>,
    val allowTyped: Boolean = false,
)

/** An active criterion: its label, how to clear it, and the section that edits it (null: "More criteria"). */
internal data class GalleryFilterChipSpec<F>(
    val key: String,
    val label: String,
    val sectionKey: String?,
    val clear: (F) -> F,
)

internal data class GallerySavedFilterOption<F>(val id: String, val name: String, val filter: F)

internal data class GallerySelectionActions(
    val selectionCount: Int,
    val visibleCount: Int,
    val onSelectAll: () -> Unit,
    val onInvert: () -> Unit,
    val onOpenSelection: () -> Unit,
    val onOpenRandom: () -> Unit,
    val onClear: () -> Unit,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <F> GalleryLikeFilterScreen(
    initial: GalleryFilterDraft<F>,
    sortOptions: List<StashGallerySortOption>,
    pageSizeOptions: List<Int>,
    emptyFilter: F,
    entitySections: List<GalleryEntitySectionSpec<F>>,
    rating: (F) -> StashRatingRange?,
    withRating: (F, StashRatingRange?) -> F,
    chips: (F) -> List<GalleryFilterChipSpec<F>>,
    localSaved: List<GallerySavedFilterOption<F>>,
    serverFilters: List<StashServerSavedFilter>,
    savedRefId: (F) -> String?,
    applyServer: (F, StashServerSavedFilter) -> F,
    clearSaved: (F) -> F,
    onSaveFilter: (String, F) -> Unit,
    onDeleteSavedFilter: (String) -> Unit,
    countResults: suspend (GalleryFilterDraft<F>) -> Int?,
    onApply: (GalleryFilterDraft<F>) -> Unit,
    onDismiss: () -> Unit,
    initialSection: String? = null,
    actions: GallerySelectionActions? = null,
    /** False hides the saved-filter section (a performer's photos have no saved filters). */
    allowSaving: Boolean = true,
) {
    var draft by remember { mutableStateOf(initial) }
    var expanded by rememberStashFilterSectionState(initialSection)
    val activeChips = chips(draft.filter)
    fun update(transform: (F) -> F) {
        draft = draft.copy(filter = transform(draft.filter))
    }

    StashFilterScreen(
        title = stashString(R.string.browse_filter_button),
        draftKey = draft.copy(reshuffle = false),
        countResults = { countResults(draft) },
        onReset = { draft = draft.copy(filter = emptyFilter) },
        onApply = {
            onApply(draft)
            onDismiss()
        },
        onDismiss = onDismiss,
    ) {
        StashFilterSortBlock(
            options = sortOptions,
            selected = draft.sort,
            optionLabel = { it.label },
            onSelect = { draft = draft.copy(sort = it, direction = it.defaultDirection) },
            direction = draft.direction,
            onDirectionChange = { draft = draft.copy(direction = it) },
            random = draft.reshuffle,
            onRandomChange = { draft = draft.copy(reshuffle = it) },
            pageSizes = pageSizeOptions,
            pageSize = draft.pageSize,
            onPageSizeChange = { draft = draft.copy(pageSize = it) },
        )

        if (allowSaving) StashFilterSection(
            key = GALLERY_SECTION_SAVED,
            title = stashString(R.string.auto_kr_0344),
            summary = activeChips.firstOrNull { it.sectionKey == GALLERY_SECTION_SAVED }?.label
                ?: stashString(R.string.filter_screen_saved_count, localSaved.size + serverFilters.size),
            activeCount = activeChips.count { it.sectionKey == GALLERY_SECTION_SAVED },
            expandedKey = expanded,
            onExpandedKeyChange = { expanded = it },
        ) {
            if (localSaved.isNotEmpty()) {
                Text(stashString(R.string.filter_screen_saved_on_device), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    localSaved.forEach { saved ->
                        InputChip(
                            selected = draft.filter == saved.filter,
                            onClick = { update { saved.filter } },
                            label = { Text(saved.name, maxLines = 1) },
                            trailingIcon = {
                                IconButton(onClick = { onDeleteSavedFilter(saved.id) }, modifier = Modifier.size(24.dp)) {
                                    Icon(
                                        Icons.Outlined.Close,
                                        contentDescription = stashString(R.string.filter_screen_delete_saved, saved.name),
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            },
                        )
                    }
                }
            }
            if (serverFilters.isNotEmpty()) {
                Text(stashString(R.string.saved_filters_from_stash), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    serverFilters.forEach { server ->
                        val active = savedRefId(draft.filter) == "stash:${server.id}"
                        StashVisibilityFilterChip(
                            selected = active,
                            onClick = { update { if (active) clearSaved(it) else applyServer(it, server) } },
                            label = { Text(server.name, maxLines = 1) },
                        )
                    }
                }
            }
            var name by remember { mutableStateOf("") }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text(stashString(R.string.filter_screen_save_as)) },
                    shape = RoundedCornerShape(14.dp),
                )
                Button(
                    onClick = {
                        onSaveFilter(name.trim(), draft.filter)
                        name = ""
                    },
                    enabled = name.isNotBlank() && activeChips.isNotEmpty(),
                ) { Text(stashString(R.string.filter_screen_save)) }
            }
        }

        entitySections.forEach { spec ->
            val selected = spec.selected(draft.filter)
            StashFilterSection(
                key = spec.key,
                title = spec.title,
                summary = selected.joinToString(", ") { it.name }.ifBlank { stashString(R.string.filter_screen_any) },
                activeCount = if (selected.isEmpty()) 0 else 1,
                expandedKey = expanded,
                onExpandedKeyChange = { expanded = it },
            ) {
                StashInlineEntityPicker(
                    searchLabel = spec.searchLabel,
                    selected = selected,
                    onSelectedChange = { picked -> update { spec.withSelected(it, picked) } },
                    search = spec.search,
                    allowTypedOption = spec.allowTyped,
                )
            }
        }

        val ratingRange = rating(draft.filter)
        StashFilterSection(
            key = GALLERY_SECTION_RATING,
            title = stashString(R.string.auto_kr_0234),
            summary = activeChips.firstOrNull { it.sectionKey == GALLERY_SECTION_RATING }?.label ?: stashString(R.string.filter_screen_any),
            activeCount = if (ratingRange?.isEmpty == false) 1 else 0,
            expandedKey = expanded,
            onExpandedKeyChange = { expanded = it },
        ) {
            val selection = normalizeStashRatingFilterSliderSelection(
                minStep = ratingRange?.min?.toStashRatingStepFromRating100()?.takeIf { it > 0 },
                maxStep = ratingRange?.max?.toStashRatingStepFromRating100()?.takeIf { it > 0 },
            )
            RatingFilterSliderPair(
                selection = selection,
                onMinStepChange = { step ->
                    val next = normalizeStashRatingFilterSliderSelection(step.takeIf { it > 0 }, selection.maxStep)
                    update { withRating(it, buildStashRatingRangeFromRatingStepSelection(next.minStep, next.maxStep)) }
                },
                onMaxStepChange = { step ->
                    val next = normalizeStashRatingFilterSliderSelection(selection.minStep, step.takeIf { it > 0 })
                    update { withRating(it, buildStashRatingRangeFromRatingStepSelection(next.minStep, next.maxStep)) }
                },
            )
        }

        val moreChips = activeChips.filter { it.sectionKey == null }
        if (moreChips.isNotEmpty()) {
            StashFilterSection(
                key = GALLERY_SECTION_MORE,
                title = stashString(R.string.filter_screen_more_criteria),
                summary = moreChips.joinToString(" · ") { it.label },
                activeCount = moreChips.size,
                expandedKey = expanded,
                onExpandedKeyChange = { expanded = it },
            ) {
                Text(
                    stashString(R.string.filter_screen_more_criteria_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    moreChips.forEach { chip ->
                        InputChip(
                            selected = true,
                            onClick = { update(chip.clear) },
                            label = { Text(chip.label, maxLines = 1) },
                            trailingIcon = { Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        )
                    }
                }
            }
        }

        if (actions != null) {
            StashFilterSection(
                key = GALLERY_SECTION_ACTIONS,
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
                StashFilterActionRow(stashString(R.string.filter_screen_open_selection), { run(actions.onOpenSelection) }, actions.selectionCount > 0)
                StashFilterActionRow(stashString(R.string.filter_screen_open_random), { run(actions.onOpenRandom) }, actions.visibleCount > 0)
                StashFilterActionRow(stashString(R.string.filter_screen_clear_selection), { run(actions.onClear) }, actions.selectionCount > 0)
            }
        }
    }
}

/** Which section of the gallery filter screen edits a criterion; null puts it under "More criteria". */
internal fun gomeng.dev.stashplayer.core.model.StashGalleryFilterCategory.gallerySectionKey(): String? = when (this) {
    gomeng.dev.stashplayer.core.model.StashGalleryFilterCategory.Tags -> GalleryEntityFilterKind.Tags.name
    gomeng.dev.stashplayer.core.model.StashGalleryFilterCategory.Studios -> GalleryEntityFilterKind.Studios.name
    gomeng.dev.stashplayer.core.model.StashGalleryFilterCategory.Performers -> GalleryEntityFilterKind.Performers.name
    gomeng.dev.stashplayer.core.model.StashGalleryFilterCategory.Scenes -> GalleryEntityFilterKind.Scenes.name
    gomeng.dev.stashplayer.core.model.StashGalleryFilterCategory.ParentFolders -> GalleryEntityFilterKind.ParentFolders.name
    gomeng.dev.stashplayer.core.model.StashGalleryFilterCategory.Rating -> GALLERY_SECTION_RATING
    gomeng.dev.stashplayer.core.model.StashGalleryFilterCategory.SavedFilter -> GALLERY_SECTION_SAVED
    else -> null
}

internal fun gomeng.dev.stashplayer.core.model.StashImageFilterCategory.imageSectionKey(): String? = when (this) {
    gomeng.dev.stashplayer.core.model.StashImageFilterCategory.Tags -> ImageEntityFilterKind.Tags.name
    gomeng.dev.stashplayer.core.model.StashImageFilterCategory.PerformerTags -> ImageEntityFilterKind.PerformerTags.name
    gomeng.dev.stashplayer.core.model.StashImageFilterCategory.Studios -> ImageEntityFilterKind.Studios.name
    gomeng.dev.stashplayer.core.model.StashImageFilterCategory.Performers -> ImageEntityFilterKind.Performers.name
    gomeng.dev.stashplayer.core.model.StashImageFilterCategory.Galleries -> ImageEntityFilterKind.Galleries.name
    gomeng.dev.stashplayer.core.model.StashImageFilterCategory.Rating -> GALLERY_SECTION_RATING
    gomeng.dev.stashplayer.core.model.StashImageFilterCategory.SavedFilter -> GALLERY_SECTION_SAVED
    else -> null
}
