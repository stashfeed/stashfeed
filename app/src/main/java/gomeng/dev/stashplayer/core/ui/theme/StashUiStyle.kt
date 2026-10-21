package gomeng.dev.stashplayer.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Where the accent color comes from (Settings › Appearance). The look itself is always Stashfeed's dark design;
 * the persisted values stay as they were so existing settings carry over.
 */
enum class StashUiStyle(val persistedValue: String) {
    /** The accent picked in Settings. */
    SocialDark("social_dark"),
    /** The wallpaper's accent (Android 12+); falls back to the picked accent on older devices. */
    MaterialYou("material_you"),
    ;

    companion object {
        val default: StashUiStyle = SocialDark

        fun fromPersistedValue(value: String?): StashUiStyle =
            entries.firstOrNull { it.persistedValue == value } ?: default
    }
}

val StashBrandPink = Color(0xFFF472B6)
val StashBrandPurple = Color(0xFFB26BFF)
val StashBrandBlue = Color(0xFF6AA9FF)

/** Pink → purple → blue: logo, story rings, progress bars in Social Dark. */
val StashBrandGradient: Brush = Brush.linearGradient(listOf(StashBrandPink, StashBrandPurple, StashBrandBlue))

/** Layout tokens that differ between the two styles; read with [LocalStashStyleTokens]. */
@Immutable
data class StashStyleTokens(
    val style: StashUiStyle,
    /** Horizontal inset of feed posts: 0 = edge to edge. */
    val feedCardInset: Dp,
    val feedCardRadius: Dp,
    /** Padding inside a feed post (Material cards); 0 in Social Dark. */
    val feedCardPadding: Dp,
    val feedMediaRadius: Dp,
    val feedPostGap: Dp,
    val showFeedDividers: Boolean,
    val storyRing: Brush,
    /** Bottom navigation: Material's pill indicator vs. Social's plain icons. */
    val navUsesPill: Boolean,
) {
    val isSocial: Boolean get() = style == StashUiStyle.SocialDark
}

fun stashStyleTokens(primary: Color): StashStyleTokens = StashStyleTokens(
    style = StashUiStyle.SocialDark,
    feedCardInset = 0.dp,
    feedCardRadius = 0.dp,
    feedCardPadding = 0.dp,
    feedMediaRadius = 0.dp,
    feedPostGap = 6.dp,
    showFeedDividers = true,
    storyRing = StashBrandGradient,
    navUsesPill = true,
)

val LocalStashStyleTokens = staticCompositionLocalOf {
    stashStyleTokens(StashBrandPurple)
}
