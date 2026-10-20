package gomeng.dev.stashplayer.feature.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import gomeng.dev.stashplayer.R
import gomeng.dev.stashplayer.core.local.parseFolderList
import gomeng.dev.stashplayer.core.model.HOME_FEED_LOOKBACK_DAY_OPTIONS
import gomeng.dev.stashplayer.core.model.HomeFeedFilter
import gomeng.dev.stashplayer.core.model.HomeFeedKind
import gomeng.dev.stashplayer.core.model.HomeFeedSettings
import gomeng.dev.stashplayer.core.model.StashPerformerGender
import gomeng.dev.stashplayer.core.model.StashSelectedEntity
import gomeng.dev.stashplayer.core.network.StashGraphQlClient
import gomeng.dev.stashplayer.core.network.StashServerProfile
import gomeng.dev.stashplayer.core.ui.components.StashEntityFilterTab
import gomeng.dev.stashplayer.core.ui.components.StashMultiEntityFilterSheet
import gomeng.dev.stashplayer.core.ui.designsystem.StashSpacing
import gomeng.dev.stashplayer.core.ui.i18n.stashString

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun HomeFeedSettingsSheet(
    settings: HomeFeedSettings,
    onSave: (HomeFeedSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(settings) { mutableStateOf(settings) }
    var foldersText by remember(settings) { mutableStateOf(settings.ignoredGalleryFolders.joinToString(", ")) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = StashSpacing.CardPadding, vertical = StashSpacing.CardGap),
            verticalArrangement = Arrangement.spacedBy(StashSpacing.CardGap),
        ) {
            Text(stashString(R.string.home_feed_settings), style = MaterialTheme.typography.titleLarge)

            SettingsSectionTitle(stashString(R.string.home_feed_settings_window), stashString(R.string.home_feed_settings_window_hint))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(StashSpacing.ChipGap)) {
                HOME_FEED_LOOKBACK_DAY_OPTIONS.forEach { days ->
                    FilterChip(
                        selected = draft.lookbackDays == days,
                        onClick = { draft = draft.copy(lookbackDays = days) },
                        label = { Text(stashString(R.string.home_feed_days, days)) },
                    )
                }
            }

            SettingsSectionTitle(stashString(R.string.home_feed_settings_show), stashString(R.string.home_feed_settings_show_hint))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(StashSpacing.ChipGap)) {
                HomeFeedKind.entries.forEach { kind ->
                    FilterChip(
                        selected = kind !in draft.hiddenKinds,
                        onClick = {
                            draft = draft.copy(
                                hiddenKinds = if (kind in draft.hiddenKinds) draft.hiddenKinds - kind else draft.hiddenKinds + kind,
                            )
                        },
                        label = { Text(stashString(kind.labelRes)) },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    SettingsSectionTitle(stashString(R.string.home_feed_settings_hide_seen), stashString(R.string.home_feed_settings_hide_seen_hint))
                }
                Switch(checked = draft.hideSeen, onCheckedChange = { draft = draft.copy(hideSeen = it) })
            }

            SettingsSectionTitle(stashString(R.string.home_feed_settings_genders), stashString(R.string.home_feed_settings_genders_hint))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(StashSpacing.ChipGap)) {
                StashPerformerGender.entries.forEach { gender ->
                    FilterChip(
                        selected = gender in draft.allowedGenders,
                        onClick = {
                            draft = draft.copy(
                                allowedGenders = if (gender in draft.allowedGenders) draft.allowedGenders - gender else draft.allowedGenders + gender,
                            )
                        },
                        label = { Text(stashString(gender.labelRes)) },
                    )
                }
            }

            SettingsSectionTitle(stashString(R.string.home_feed_settings_folders), stashString(R.string.home_feed_settings_folders_hint))
            OutlinedTextField(
                value = foldersText,
                onValueChange = { foldersText = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(StashSpacing.CardGap, androidx.compose.ui.Alignment.End)) {
                TextButton(onClick = {
                    draft = HomeFeedSettings()
                    foldersText = draft.ignoredGalleryFolders.joinToString(", ")
                }) { Text(stashString(R.string.auto_kr_0374)) }
                Button(onClick = {
                    onSave(draft.copy(ignoredGalleryFolders = parseFolderList(foldersText)))
                    onDismiss()
                }) { Text(stashString(R.string.home_feed_settings_save)) }
            }
        }
    }
}

@Composable
private fun SettingsSectionTitle(title: String, hint: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun HomeFeedFilterSheet(
    filter: HomeFeedFilter,
    profile: StashServerProfile?,
    onApply: (HomeFeedFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    val client = remember(profile) { profile?.let(::StashGraphQlClient) }
    var excludedModes by remember { mutableStateOf(listOf(false, false, false)) }
    StashMultiEntityFilterSheet(
        title = stashString(R.string.home_feed_filter_title),
        hint = stashString(R.string.home_feed_filter_hint),
        tabs = listOf(
            StashEntityFilterTab(
                label = stashString(R.string.people_filter_tab_performers),
                searchLabel = stashString(R.string.people_filter_search_performers),
                selected = filter.performers,
                search = { query -> client?.findPerformers(query = query).orEmpty() },
                excluded = filter.performersExcluded,
            ),
            StashEntityFilterTab(
                label = stashString(R.string.for_you_filter_tab_tags),
                searchLabel = stashString(R.string.for_you_filter_search_tags),
                selected = filter.tags,
                search = { query -> client?.findTags(query = query).orEmpty().map { StashSelectedEntity(it.id, it.name) } },
                excluded = filter.tagsExcluded,
            ),
            StashEntityFilterTab(
                label = stashString(R.string.people_filter_tab_studios),
                searchLabel = stashString(R.string.people_filter_search_studios),
                selected = filter.studios,
                search = { query -> client?.findStudios(query = query).orEmpty() },
                excluded = filter.studiosExcluded,
            ),
        ),
        onApplyExcluded = { modes -> excludedModes = modes },
        onApply = { selections ->
            onApply(
                HomeFeedFilter(
                    performers = selections[0],
                    tags = selections[1],
                    studios = selections[2],
                    performersExcluded = excludedModes.getOrElse(0) { false },
                    tagsExcluded = excludedModes.getOrElse(1) { false },
                    studiosExcluded = excludedModes.getOrElse(2) { false },
                ),
            )
        },
        onDismiss = onDismiss,
    )
}
