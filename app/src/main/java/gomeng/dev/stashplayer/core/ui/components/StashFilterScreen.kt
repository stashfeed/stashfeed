package gomeng.dev.stashplayer.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.os.Build
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.DialogProperties
import gomeng.dev.stashplayer.R
import gomeng.dev.stashplayer.core.model.StashSelectedEntity
import gomeng.dev.stashplayer.core.model.StashSortDirection
import gomeng.dev.stashplayer.core.ui.designsystem.StashVisibilityFilterChip
import gomeng.dev.stashplayer.core.ui.i18n.stashString
import kotlinx.coroutines.delay

private const val FILTER_COUNT_DEBOUNCE_MS = 350L
private const val ENTITY_PICKER_SEARCH_DEBOUNCE_MS = 250L

/**
 * Fullscreen filter screen shared by the browse screens: a sort block on top, accordion sections below, and a
 * fixed bar with "Reset" and "Show N results". The caller keeps the draft; [countResults] is re-run (debounced)
 * whenever [draftKey] changes so the button shows the live result count.
 */
@Composable
fun StashFilterScreen(
    title: String,
    draftKey: Any,
    countResults: (suspend () -> Int?)?,
    onReset: () -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    var count by remember { mutableStateOf<Int?>(null) }
    var counting by remember { mutableStateOf(false) }
    LaunchedEffect(draftKey) {
        val counter = countResults ?: return@LaunchedEffect
        counting = true
        delay(FILTER_COUNT_DEBOUNCE_MS)
        count = runCatching { counter() }.getOrNull()
        counting = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            window ?: return@SideEffect
            // By default a dialog window stops at the system bars while its content is sized for the whole
            // screen, so the bottom bar ended up off screen. Let the window cover the bars; the content pads them.
            window.attributes = window.attributes.apply {
                // The camera cutout too, or the window starts below it and is short by that much.
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) fitInsetsTypes = 0
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = stashString(R.string.auto_kr_0192))
                    }
                    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = onReset) { Text(stashString(R.string.auto_kr_0374)) }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp),
                    content = content,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(onClick = onReset, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stashString(R.string.auto_kr_0374))
                    }
                    Button(
                        onClick = onApply,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) {
                        if (counting && count == null) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text(
                                text = count?.let { stashString(R.string.filter_screen_show_results, it) }
                                    ?: stashString(R.string.filter_screen_show),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Small caps label above a block of the filter screen. */
@Composable
fun StashFilterBlockLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/**
 * The sort block: field chips, direction, an optional random switch / reshuffle, and page size. Every argument
 * that is null hides its row.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> StashFilterSortBlock(
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    direction: StashSortDirection,
    onDirectionChange: (StashSortDirection) -> Unit,
    random: Boolean? = null,
    onRandomChange: ((Boolean) -> Unit)? = null,
    onReshuffle: (() -> Unit)? = null,
    pageSizes: List<Int>? = null,
    pageSize: Int? = null,
    onPageSizeChange: ((Int) -> Unit)? = null,
) {
    StashFilterBlockLabel(stashString(R.string.filter_screen_sort))
    Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEach { option ->
                StashVisibilityFilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(optionLabel(option)) },
                )
            }
        }
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = direction == StashSortDirection.Desc,
                onClick = { onDirectionChange(StashSortDirection.Desc) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) { Text(stashString(R.string.auto_kr_0422), maxLines = 1) }
            SegmentedButton(
                selected = direction == StashSortDirection.Asc,
                onClick = { onDirectionChange(StashSortDirection.Asc) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) { Text(stashString(R.string.auto_kr_0423), maxLines = 1) }
        }
        if (random != null && onRandomChange != null) {
            StashFilterSwitchRow(
                label = stashString(R.string.filter_screen_random),
                supporting = stashString(R.string.filter_screen_random_hint),
                checked = random,
                onCheckedChange = onRandomChange,
            )
        }
        if (onReshuffle != null) {
            OutlinedButton(onClick = onReshuffle) { Text(stashString(R.string.filter_screen_reshuffle)) }
        }
        if (pageSizes != null && pageSize != null && onPageSizeChange != null) {
            Text(
                stashString(R.string.filter_screen_per_page),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pageSizes.forEach { size ->
                    StashVisibilityFilterChip(
                        selected = size == pageSize,
                        onClick = { onPageSizeChange(size) },
                        label = { Text(size.toString()) },
                    )
                }
            }
        }
    }
    Spacer(Modifier.size(12.dp))
}

@Composable
fun StashFilterSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    supporting: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            supporting?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * One accordion section: a header row with title, a one-line summary of the current choice and an active badge;
 * tapping it unfolds [content] in place. Only one section is open at a time when the caller passes the shared
 * [expandedKey] state.
 */
@Composable
fun StashFilterSection(
    key: String,
    title: String,
    summary: String?,
    activeCount: Int,
    expandedKey: String?,
    onExpandedKeyChange: (String?) -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val expanded = expandedKey == key
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    val colors = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.4f), modifier = Modifier.padding(horizontal = 16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onExpandedKeyChange(if (expanded) null else key) }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                summary?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (activeCount > 0) colors.primary else colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (activeCount > 0) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .background(colors.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(activeCount.toString(), style = MaterialTheme.typography.labelSmall, color = colors.onPrimary)
                }
                Spacer(Modifier.width(8.dp))
            }
            Icon(Icons.Outlined.ExpandMore, contentDescription = null, modifier = Modifier.rotate(rotation))
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
    }
}

/** State for [StashFilterSection]s of one screen: which one is open (at most one). */
@Composable
fun rememberStashFilterSectionState(initial: String? = null) = rememberSaveable { mutableStateOf(initial) }

/**
 * Inline, searchable entity picker for a filter section. [selected] is the draft; with [excluded] non-null an
 * "Only these / All except" switch and "select all" appear and [selected] then lists the entries left out.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StashInlineEntityPicker(
    searchLabel: String,
    selected: List<StashSelectedEntity>,
    onSelectedChange: (List<StashSelectedEntity>) -> Unit,
    search: suspend (String) -> List<StashSelectedEntity>,
    excluded: Boolean? = null,
    onExcludedChange: ((Boolean) -> Unit)? = null,
    allowTypedOption: Boolean = false,
    maxOptions: Int = 40,
) {
    var query by remember { mutableStateOf("") }
    var options by remember { mutableStateOf<List<StashSelectedEntity>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(query, retry) {
        delay(ENTITY_PICKER_SEARCH_DEBOUNCE_MS)
        loading = true
        error = null
        runCatching { search(query) }
            .onSuccess { options = it }
            .onFailure { error = it.message.orEmpty() }
        loading = false
    }
    val excluding = excluded == true
    val selectedIds = selected.map { it.id }.toSet()
    fun toggle(entity: StashSelectedEntity) {
        onSelectedChange(if (entity.id in selectedIds) selected.filterNot { it.id == entity.id } else selected + entity)
    }
    fun isChecked(entity: StashSelectedEntity) = (entity.id in selectedIds) != excluding

    if (excluded != null && onExcludedChange != null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                SegmentedButton(
                    selected = !excluding,
                    onClick = {
                        if (excluding) {
                            onExcludedChange(false)
                            onSelectedChange(emptyList())
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text(stashString(R.string.entity_filter_mode_only), maxLines = 1) }
                SegmentedButton(
                    selected = excluding,
                    onClick = {
                        if (!excluding) {
                            onExcludedChange(true)
                            onSelectedChange(emptyList())
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text(stashString(R.string.entity_filter_mode_except), maxLines = 1) }
            }
            TextButton(
                onClick = {
                    onExcludedChange(!(excluding && selected.isEmpty()))
                    onSelectedChange(emptyList())
                },
            ) {
                Text(
                    stashString(
                        if (excluding && selected.isEmpty()) R.string.entity_filter_select_none else R.string.entity_filter_select_all,
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
        label = { Text(searchLabel) },
        shape = RoundedCornerShape(14.dp),
    )
    if (excluding) {
        Text(
            text = if (selected.isEmpty()) {
                stashString(R.string.entity_filter_all_selected)
            } else {
                stashString(R.string.entity_filter_all_except, selected.size)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    val typed = query.trim()
    val typedOption = if (allowTypedOption && typed.isNotEmpty() && (selected + options).none { it.name.equals(typed, true) }) {
        StashSelectedEntity(id = typed, name = typed)
    } else {
        null
    }
    val shown = (selected + listOfNotNull(typedOption) + options).distinctBy { it.id }.take(maxOptions + selected.size)
    when {
        error != null -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stashString(R.string.people_filter_load_failed, error.orEmpty()),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { retry += 1 }) { Text(stashString(R.string.auto_kr_0031)) }
        }
        !loading && shown.isEmpty() -> Text(
            stashString(R.string.people_filter_no_results),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        else -> Box {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                shown.forEach { entity ->
                    StashVisibilityFilterChip(
                        selected = isChecked(entity),
                        onClick = { toggle(entity) },
                        label = { Text(entity.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
            if (loading) CircularProgressIndicator(modifier = Modifier.size(20.dp).align(Alignment.TopEnd), strokeWidth = 2.dp)
        }
    }
}

/** A single action row in the filter screen's "Actions" section. */
@Composable
fun StashFilterActionRow(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp),
    )
}
