package gomeng.dev.stashplayer.core.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import gomeng.dev.stashplayer.R
import gomeng.dev.stashplayer.core.ui.i18n.stashString
import kotlin.math.roundToInt

/**
 * How far a browse screen's header is folded, in pixels: 0 shows tabs, search and the filter row; [rangePx]
 * leaves only the slim search bar. The offset follows the finger one-to-one (no timed animation), and settles to
 * the nearer end once a scroll stops.
 */
@Stable
class StashCollapsingHeaderState {
    var offsetPx by mutableFloatStateOf(0f)
        private set

    /** Collapsible height (tabs + filter row); set by the header's layout. */
    internal var rangePx = 0f

    val fraction: Float get() = if (rangePx <= 0f) 0f else (offsetPx / rangePx).coerceIn(0f, 1f)

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val dy = available.y
            if (dy != 0f && rangePx > 0f) offsetPx = (offsetPx - dy).coerceIn(0f, rangePx)
            // Nothing is consumed: the list scrolls by the same amount, so the header moves with the content.
            return Offset.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            settle()
            return Velocity.Zero
        }
    }

    private suspend fun settle() {
        if (offsetPx <= 0f || offsetPx >= rangePx) return
        val target = if (offsetPx > rangePx / 2f) rangePx else 0f
        animate(offsetPx, target, animationSpec = spring(stiffness = 900f)) { value, _ -> offsetPx = value }
    }

    fun expand() {
        offsetPx = 0f
    }
}

@Composable
fun rememberStashCollapsingHeaderState(): StashCollapsingHeaderState = remember { StashCollapsingHeaderState() }

/** Tabs an enclosing screen (Explore's Videos / Photos / Galleries) puts at the top of the inner screen's header. */
val LocalStashBrowseTabs = compositionLocalOf<(@Composable () -> Unit)?> { null }

/** The Compact / Large switch shown in browse headers. */
enum class StashBrowseLayout { Compact, Large }

/**
 * The shared header of browse screens:
 * ```
 * [ tabs                                  ]   ← folds away
 * [ 🔍 search                     (⚙) (▦) ]   ← stays; the small filter + layout buttons fade in when folded
 * [ Filter (n)  chip  chip  chip  …   ▦  ]   ← folds away
 * ```
 * [content] fills the whole area beneath the header and receives the top padding its list must start at.
 */
@Composable
fun StashBrowseHeaderScaffold(
    state: StashCollapsingHeaderState,
    searchValue: String,
    onSearchChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    filterCount: Int,
    onOpenFilter: () -> Unit,
    layout: StashBrowseLayout?,
    onToggleLayout: (() -> Unit)?,
    horizontalPadding: Dp,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    searchPlaceholder: String = stashString(R.string.auto_kr_0402),
    tabs: (@Composable () -> Unit)? = LocalStashBrowseTabs.current,
    /** Extra controls shown in the filter row before the chips (e.g. a selection bar). */
    leadingControls: (@Composable RowScope.() -> Unit)? = null,
    chips: List<StashHeaderChip> = emptyList(),
    content: @Composable (PaddingValues) -> Unit,
) {
    val density = LocalDensity.current
    var expandedHeightPx by remember { mutableIntStateOf(0) }
    val background = MaterialTheme.colorScheme.background

    Layout(
        modifier = modifier.nestedScroll(state.connection).clipToBounds(),
        content = {
            Box { content(PaddingValues(top = with(density) { expandedHeightPx.toDp() })) }
            // Filter row (drawn beneath the search bar, so it slides under it while folding).
            Box(
                modifier = Modifier
                    .background(background)
                    .graphicsLayer { alpha = 1f - (state.fraction * 1.6f).coerceAtMost(1f) },
            ) {
                StashBrowseFilterRow(
                    filterCount = filterCount,
                    onOpenFilter = onOpenFilter,
                    layout = layout,
                    onToggleLayout = onToggleLayout,
                    horizontalPadding = horizontalPadding,
                    enabled = enabled,
                    leadingControls = leadingControls,
                    chips = chips,
                )
            }
            Box(modifier = Modifier.background(background)) {
                HeaderSearchRow(
                    state = state,
                    value = searchValue,
                    onValueChange = onSearchChange,
                    onClear = onClearSearch,
                    placeholder = searchPlaceholder,
                    filterCount = filterCount,
                    onOpenFilter = onOpenFilter,
                    layout = layout,
                    onToggleLayout = onToggleLayout,
                    horizontalPadding = horizontalPadding,
                    enabled = enabled,
                )
            }
            Box(
                modifier = Modifier
                    .background(background)
                    .graphicsLayer { alpha = 1f - (state.fraction * 1.4f).coerceAtMost(1f) },
            ) {
                tabs?.invoke()
            }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val rowConstraints = Constraints(minWidth = width, maxWidth = width)
        val filterRow = measurables[1].measure(rowConstraints)
        val searchRow = measurables[2].measure(rowConstraints)
        val tabRow = measurables[3].measure(rowConstraints)
        val tabsHeight = tabRow.height
        val filterHeight = filterRow.height
        val expanded = tabsHeight + searchRow.height + filterHeight
        state.rangePx = (tabsHeight + filterHeight).toFloat()
        if (expanded != expandedHeightPx) expandedHeightPx = expanded
        val content = measurables[0].measure(constraints)
        layout(width, constraints.maxHeight) {
            val f = state.fraction
            val tabsY = (-tabsHeight * f).roundToInt()
            val searchY = (tabsHeight * (1f - f)).roundToInt()
            val filterY = searchY + searchRow.height - (filterHeight * f).roundToInt()
            content.place(0, 0)
            filterRow.place(0, filterY)
            searchRow.place(0, searchY)
            tabRow.place(0, tabsY)
        }
    }
}

/** One active filter in the header's chip row: tap edits it, the × clears it. */
data class StashHeaderChip(
    val key: String,
    val label: String,
    val onClick: () -> Unit,
    val onClear: (() -> Unit)?,
)

@Composable
private fun HeaderSearchRow(
    state: StashCollapsingHeaderState,
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    placeholder: String,
    filterCount: Int,
    onOpenFilter: () -> Unit,
    layout: StashBrowseLayout?,
    onToggleLayout: (() -> Unit)?,
    horizontalPadding: Dp,
    enabled: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StashSearchPill(
            value = value,
            onValueChange = onValueChange,
            onClear = onClear,
            placeholder = placeholder,
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
        // The compact controls grow in as the filter row folds away; their width follows the fold fraction.
        Row(
            modifier = Modifier
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))
                    val visibleWidth = (placeable.width * state.fraction).roundToInt()
                    layout(visibleWidth, placeable.height) { placeable.place(0, 0) }
                }
                .clipToBounds()
                .graphicsLayer { alpha = state.fraction },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onOpenFilter, enabled = enabled && state.fraction > 0.5f) {
                FilterIcon(filterCount)
            }
            if (layout != null && onToggleLayout != null) {
                IconButton(onClick = onToggleLayout, enabled = state.fraction > 0.5f) { LayoutIcon(layout) }
            }
        }
    }
}

/** The "Filter (n) · chips · layout" row; the header uses it, and lists without a collapsing header too. */
@Composable
fun StashBrowseFilterRow(
    filterCount: Int,
    onOpenFilter: () -> Unit,
    layout: StashBrowseLayout?,
    onToggleLayout: (() -> Unit)?,
    horizontalPadding: Dp,
    enabled: Boolean,
    leadingControls: (@Composable RowScope.() -> Unit)?,
    chips: List<StashHeaderChip>,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = horizontalPadding, end = (horizontalPadding - 8.dp).coerceAtLeast(0.dp), top = 2.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StashFilterPillButton(count = filterCount, onClick = onOpenFilter, enabled = enabled)
        leadingControls?.let {
            Spacer(Modifier.width(8.dp))
            it()
        }
        LazyRow(
            modifier = Modifier.weight(1f).padding(start = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(chips.size, key = { chips[it].key }) { index ->
                HeaderChip(chips[index])
            }
        }
        if (layout != null && onToggleLayout != null) {
            IconButton(onClick = onToggleLayout) { LayoutIcon(layout) }
        }
    }
}

/** Rounded search field, slimmer than an outlined text field. */
@Composable
fun StashSearchPill(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
        cursorBrush = SolidColor(colors.primary),
        modifier = modifier.height(44.dp),
        decorationBox = { inner ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(colors.surfaceContainerHigh, CircleShape)
                    .padding(start = 14.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Search, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant, maxLines = 1)
                    }
                    inner()
                }
                if (value.isNotEmpty()) {
                    IconButton(onClick = onClear, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Outlined.Close, contentDescription = stashString(R.string.auto_kr_0403), modifier = Modifier.size(18.dp))
                    }
                }
            }
        },
    )
}

/** "Filter (n)" pill that opens the filter screen. */
@Composable
fun StashFilterPillButton(count: Int, onClick: () -> Unit, enabled: Boolean = true) {
    val colors = MaterialTheme.colorScheme
    val active = count > 0
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = if (active) colors.primary else colors.surfaceContainerHigh,
        contentColor = if (active) colors.onPrimary else colors.onSurface,
    ) {
        Row(
            modifier = Modifier.height(36.dp).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                text = if (active) stashString(R.string.browse_filter_button_count, count) else stashString(R.string.browse_filter_button),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun HeaderChip(chip: StashHeaderChip) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .height(32.dp)
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp))
            .background(colors.secondaryContainer.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
            .clickable(onClick = chip.onClick)
            .padding(start = 12.dp, end = if (chip.onClear != null) 2.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            chip.label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSecondaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        chip.onClear?.let { clear ->
            IconButton(onClick = clear, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = stashString(R.string.auto_kr_0403),
                    modifier = Modifier.size(14.dp),
                    tint = colors.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
private fun FilterIcon(count: Int) {
    if (count > 0) {
        BadgedBox(badge = { Badge { Text(count.toString()) } }) { Icon(Icons.Outlined.Tune, contentDescription = stashString(R.string.browse_filter_button)) }
    } else {
        Icon(Icons.Outlined.Tune, contentDescription = stashString(R.string.browse_filter_button))
    }
}

@Composable
private fun LayoutIcon(layout: StashBrowseLayout) {
    // Shows the layout a tap switches to.
    when (layout) {
        StashBrowseLayout.Compact -> Icon(Icons.Outlined.ViewAgenda, contentDescription = stashString(R.string.auto_kr_0084))
        StashBrowseLayout.Large -> Icon(Icons.Outlined.GridView, contentDescription = stashString(R.string.auto_kr_0083))
    }
}

/** "3 selected ×" pill shown in the filter row while cards are selected. */
@Composable
fun StashSelectionPill(count: Int, onClick: () -> Unit, onClear: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(onClick = onClick, shape = CircleShape, color = colors.tertiaryContainer, contentColor = colors.onTertiaryContainer) {
        Row(modifier = Modifier.height(36.dp).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stashString(R.string.browse_selection_count, count), style = MaterialTheme.typography.labelLarge, maxLines = 1)
            IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Outlined.Close, contentDescription = stashString(R.string.filter_screen_clear_selection), modifier = Modifier.size(16.dp))
            }
        }
    }
}
