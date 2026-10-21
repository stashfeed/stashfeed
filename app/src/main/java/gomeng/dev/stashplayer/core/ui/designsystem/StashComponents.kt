package gomeng.dev.stashplayer.core.ui.designsystem

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/* ---------------------------------------------------------------------------------------------------------------
 * Motion
 * ------------------------------------------------------------------------------------------------------------- */

/** The standard spring for things that move or resize: quick, no visible bounce. */
fun <T> stashSpring(): FiniteAnimationSpec<T> =
    spring(dampingRatio = StashMotion.SpringDampingStandard, stiffness = StashMotion.SpringStiffnessStandard)

/** For small press and toggle feedback: a touch of bounce. */
fun <T> stashBouncySpring(): FiniteAnimationSpec<T> =
    spring(dampingRatio = StashMotion.SpringDampingBouncy, stiffness = StashMotion.SpringStiffnessFast)

/** Shrinks slightly while pressed and springs back, the press feel used on cards, chips and actions. */
fun Modifier.stashPressScale(interactionSource: MutableInteractionSource, pressedScale: Float = 0.96f): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) pressedScale else 1f, stashBouncySpring(), label = "pressScale")
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/* ---------------------------------------------------------------------------------------------------------------
 * Haptics
 * ------------------------------------------------------------------------------------------------------------- */

/**
 * The few haptics Stashfeed uses, after Android's "less is more" guidance: a light tick for toggles and selection,
 * a confirm for completed actions (rating, O, follow), a reject when something can't be done.
 */
@Stable
class StashHaptics internal constructor(private val view: android.view.View) {
    fun tick() {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun confirm() {
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    }

    fun reject() {
        view.performHapticFeedback(HapticFeedbackConstants.REJECT)
    }

    fun longPress() {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }
}

@Composable
fun rememberStashHaptics(): StashHaptics {
    val view = LocalView.current
    return remember(view) { StashHaptics(view) }
}

/* ---------------------------------------------------------------------------------------------------------------
 * Top bar
 * ------------------------------------------------------------------------------------------------------------- */

/**
 * The one top bar pattern: optional leading control, a title (or custom [titleContent] such as the logo),
 * and trailing actions. 56 dp tall, screen padding on the start side.
 */
@Composable
fun StashTopBar(
    title: String?,
    modifier: Modifier = Modifier,
    navigation: (@Composable () -> Unit)? = null,
    titleContent: (@Composable RowScope.() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(start = if (navigation != null) StashSpace.XS else StashSpace.L, end = StashSpace.XS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        navigation?.invoke()
        if (titleContent != null) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, content = titleContent)
        } else {
            Text(
                text = title.orEmpty(),
                style = if (navigation != null) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = if (navigation != null) StashSpace.XS else 0.dp),
            )
        }
        actions()
    }
}

/* ---------------------------------------------------------------------------------------------------------------
 * Segmented tabs
 * ------------------------------------------------------------------------------------------------------------- */

/**
 * Pill tabs for switching between sibling lists (Videos / Photos / Galleries, Following / All). The selected pill
 * is filled with the content color, the others sit on a tonal surface.
 */
@Composable
fun <T> StashSegmentedTabs(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: Dp = StashSpace.L,
) {
    val haptics = rememberStashHaptics()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = contentPadding, vertical = StashSpace.XS),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val container by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceContainer,
                animationSpec = tween(180),
                label = "segContainer",
            )
            val content by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = tween(180),
                label = "segContent",
            )
            val interaction = remember { MutableInteractionSource() }
            Surface(
                onClick = {
                    if (!isSelected) haptics.tick()
                    onSelect(option)
                },
                shape = RoundedCornerShape(StashRadius.Pill),
                color = container,
                contentColor = content,
                interactionSource = interaction,
                modifier = Modifier
                    .stashPressScale(interaction)
                    .semantics {
                        role = Role.Tab
                        this.selected = isSelected
                    },
            ) {
                Text(
                    text = label(option),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/* ---------------------------------------------------------------------------------------------------------------
 * Loading placeholders
 * ------------------------------------------------------------------------------------------------------------- */

/** A shimmering placeholder fill; loading screens show the shape of what's coming instead of a spinner. */
fun Modifier.stashShimmer(shape: Shape = RoundedCornerShape(StashRadius.S)): Modifier = composed {
    val base = MaterialTheme.colorScheme.surfaceContainer
    val highlight = MaterialTheme.colorScheme.surfaceContainerHigh
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerProgress",
    )
    clip(shape).drawWithCache {
        val width = size.width
        onDrawBehind {
            val x = -width + progress * width * 3f
            drawRect(base)
            drawRect(
                Brush.linearGradient(
                    colors = listOf(base, highlight, base),
                    start = Offset(x, 0f),
                    end = Offset(x + width, size.height),
                ),
            )
        }
    }
}

/** A placeholder block of a given size. */
@Composable
fun StashSkeleton(modifier: Modifier, shape: Shape = RoundedCornerShape(StashRadius.S)) {
    Box(modifier.stashShimmer(shape))
}

/* ---------------------------------------------------------------------------------------------------------------
 * Labels and badges
 * ------------------------------------------------------------------------------------------------------------- */

/** Small uppercase label above a group (filter sections, settings groups). */
@Composable
fun StashSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(horizontal = StashSpace.L, vertical = StashSpace.S),
    )
}

/** Duration / resolution / count badge drawn on top of media. */
@Composable
fun StashMediaBadge(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = StashColors.OnMedia,
        maxLines = 1,
        modifier = modifier
            .background(StashColors.MediaBadge, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** Tonal pill used for compact actions next to media (★ 4.5, 💧 3, Later). */
@Composable
fun StashTonalPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val container by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        animationSpec = tween(160),
        label = "pillContainer",
    )
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(StashRadius.Pill),
        color = container,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        interactionSource = interaction,
        modifier = modifier.stashPressScale(interaction, 0.92f),
    ) {
        Row(
            modifier = Modifier.height(36.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            content = content,
        )
    }
}

/**
 * Placeholder for a loading media grid: the same columns and aspect ratio as the real cards, with title lines
 * under each tile when [textLines] > 0. Shown instead of a spinner on first load.
 */
@Composable
fun StashMediaGridSkeleton(
    columns: Int,
    mediaAspectRatio: Float,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = StashSpace.L,
    gap: Dp = StashSpace.M,
    rows: Int = 4,
    textLines: Int = 2,
    mediaShape: Shape = RoundedCornerShape(StashRadius.S),
) {
    Column(
        modifier = modifier.padding(horizontal = horizontalPadding),
        verticalArrangement = Arrangement.spacedBy(gap + StashSpace.XS),
    ) {
        repeat(rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                repeat(columns) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(StashSpace.S),
                    ) {
                        StashSkeleton(
                            Modifier.fillMaxWidth().aspectRatio(mediaAspectRatio),
                            mediaShape,
                        )
                        repeat(textLines) { line ->
                            StashSkeleton(Modifier.fillMaxWidth(if (line == 0) 0.85f else 0.5f).height(10.dp))
                        }
                    }
                }
            }
        }
    }
}

/* ---------------------------------------------------------------------------------------------------------------
 * Grouped lists (library, settings)
 * ------------------------------------------------------------------------------------------------------------- */

/** A rounded group of rows on the first surface step; rows inside are separated by hairlines. */
@Composable
fun StashListGroup(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(StashSpace.S)) {
        title?.let {
            Text(
                text = it.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = StashSpace.XS),
            )
        }
        Surface(
            shape = RoundedCornerShape(StashRadius.M),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(content = content)
        }
    }
}

/** One row of a [StashListGroup]: optional icon, title and supporting text, trailing value or control. */
@Composable
fun StashListRow(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    value: String? = null,
    showBadge: Boolean = false,
    showDivider: Boolean = true,
    showChevron: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = StashSpace.L, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(StashSpace.M + 2.dp),
        ) {
            icon?.let {
                androidx.compose.material3.Icon(it, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                supporting?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (showBadge) {
                Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape))
            }
            value?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            when {
                trailing != null -> trailing()
                onClick != null && showChevron -> androidx.compose.material3.Icon(
                    androidx.compose.material.icons.Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (showDivider) {
            androidx.compose.material3.HorizontalDivider(
                modifier = Modifier.padding(start = if (icon != null) 54.dp else StashSpace.L),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}
