package gomeng.dev.stashplayer.feature.shorts

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import gomeng.dev.stashplayer.R
import gomeng.dev.stashplayer.core.model.ForYouFeedConfig
import gomeng.dev.stashplayer.core.model.ForYouMode
import gomeng.dev.stashplayer.core.model.SceneCardModel
import gomeng.dev.stashplayer.core.model.SceneCardTagChip
import gomeng.dev.stashplayer.core.model.StashSelectedEntity
import gomeng.dev.stashplayer.core.model.coerceShortsMaxDurationSeconds
import gomeng.dev.stashplayer.core.network.StashGraphQlClient
import gomeng.dev.stashplayer.core.player.ratingStepForStashRating100
import gomeng.dev.stashplayer.core.player.stashRating100ForRatingStep
import gomeng.dev.stashplayer.core.ui.components.StashEntityFilterTab
import gomeng.dev.stashplayer.core.ui.components.StashMultiEntityFilterSheet
import gomeng.dev.stashplayer.core.ui.components.StashStarRatingSlider
import gomeng.dev.stashplayer.core.ui.designsystem.StashRadii
import gomeng.dev.stashplayer.core.ui.i18n.stashString

private val OverlayScrim = Color.Black.copy(alpha = 0.45f)

/** Title, Random / Chained switch and the filter button, floating over the video. */
@Composable
internal fun ForYouHeader(
    config: ForYouFeedConfig,
    onSelectMode: (ForYouMode) -> Unit,
    onOpenFilter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            // The Scaffold already keeps content below the status bar.
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stashString(R.string.for_you_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ForYouMode.entries.forEach { mode ->
                OverlayPill(
                    label = stashString(mode.labelRes),
                    selected = config.mode == mode,
                    onClick = { onSelectMode(mode) },
                )
            }
        }
        val count = config.activeFilterCount
        OverlayPill(
            label = if (count > 0) stashString(R.string.for_you_filter_with_count, count) else stashString(R.string.for_you_filter),
            selected = count > 0,
            onClick = onOpenFilter,
            leadingIcon = { Icon(Icons.Outlined.Tune, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp)) },
        )
    }
}

@Composable
private fun OverlayPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.85f) else OverlayScrim,
                RoundedCornerShape(StashRadii.Pill),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        leadingIcon?.invoke()
        Text(label, style = MaterialTheme.typography.labelLarge, color = Color.White)
    }
}

@Composable
internal fun ForYouFilterSheet(
    config: ForYouFeedConfig,
    shortsMaxDurationSeconds: Int,
    client: StashGraphQlClient?,
    onApply: (ForYouFeedConfig) -> Unit,
    onDismiss: () -> Unit,
) {
    var shortsOnly by remember(config.shortsOnly) { mutableStateOf(config.shortsOnly) }
    StashMultiEntityFilterSheet(
        title = stashString(R.string.for_you_filter_title),
        hint = stashString(R.string.for_you_filter_hint),
        tabs = listOf(
            StashEntityFilterTab(
                label = stashString(R.string.people_filter_tab_performers),
                searchLabel = stashString(R.string.people_filter_search_performers),
                selected = config.performers,
                search = { query -> client?.findPerformers(query = query).orEmpty() },
            ),
            StashEntityFilterTab(
                label = stashString(R.string.for_you_filter_tab_tags),
                searchLabel = stashString(R.string.for_you_filter_search_tags),
                selected = config.tags,
                search = { query ->
                    client?.findTags(query = query).orEmpty().map { StashSelectedEntity(id = it.id, name = it.name) }
                },
            ),
            StashEntityFilterTab(
                label = stashString(R.string.people_filter_tab_studios),
                searchLabel = stashString(R.string.people_filter_search_studios),
                selected = config.studios,
                search = { query -> client?.findStudios(query = query).orEmpty() },
            ),
        ),
        onApply = { selections ->
            onApply(
                config.copy(
                    performers = selections[0],
                    tags = selections[1],
                    studios = selections[2],
                    shortsOnly = shortsOnly,
                ),
            )
        },
        onReset = { shortsOnly = false },
        onDismiss = onDismiss,
        extraContent = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { shortsOnly = !shortsOnly },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stashString(R.string.for_you_shorts_only, coerceShortsMaxDurationSeconds(shortsMaxDurationSeconds)),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Switch(checked = shortsOnly, onCheckedChange = { shortsOnly = it })
            }
        },
    )
}

@Composable
internal fun ForYouRatingDialog(
    scene: SceneCardModel,
    onRate: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    var ratingStep by remember(scene.id) { mutableStateOf(ratingStepForStashRating100(scene.rating100)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stashString(R.string.for_you_rate_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(scene.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                StashStarRatingSlider(
                    ratingStep = ratingStep,
                    contentDescriptionPrefix = stashString(R.string.for_you_rate),
                    enabled = true,
                    onSelectRatingStep = { ratingStep = it },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onRate(stashRating100ForRatingStep(ratingStep)) }) {
                Text(stashString(R.string.auto_kr_0316))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onRate(null) }) { Text(stashString(R.string.for_you_rate_clear)) }
                TextButton(onClick = onDismiss) { Text(stashString(R.string.auto_kr_0296)) }
            }
        },
    )
}

/** Tap adds an O, long-press removes the newest one (same gesture as the Binge plugin). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ForYouOCounterButton(
    count: Int,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val description = stashString(R.string.for_you_o_counter, count)
    Column(
        modifier = Modifier
            .size(width = 48.dp, height = 56.dp)
            .combinedClickable(
                onClick = onIncrement,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDecrement()
                },
            )
            .semantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Outlined.WaterDrop, contentDescription = null, tint = Color.White)
        Text(count.toString(), style = MaterialTheme.typography.labelSmall, color = Color.White)
    }
}

@Composable
internal fun ForYouRatingButton(
    rating100: Int?,
    onClick: () -> Unit,
) {
    val stars = ratingStepForStashRating100(rating100) / 2f
    Column(
        modifier = Modifier
            .size(width = 48.dp, height = 56.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = stashString(R.string.for_you_rate) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = if (stars > 0f) Icons.Filled.Star else Icons.Outlined.StarBorder,
            contentDescription = null,
            tint = if (stars > 0f) Color(0xFFFFC857) else Color.White,
        )
        if (stars > 0f) {
            Text(
                text = if (stars % 1f == 0f) stars.toInt().toString() else stars.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
            )
        }
    }
}

/** "with A, B" under the title; each name opens the performer's profile. */
@Composable
internal fun ForYouPerformerLinks(
    performers: List<SceneCardTagChip>,
    onOpenPerformer: (String) -> Unit,
) {
    if (performers.isEmpty()) return
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        performers.forEach { performer ->
            Row(
                modifier = Modifier
                    .background(OverlayScrim, CircleShape)
                    .clickable { onOpenPerformer(performer.id) }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(Icons.Outlined.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                Text(performer.label, style = MaterialTheme.typography.labelMedium, color = Color.White, maxLines = 1)
            }
        }
    }
}
