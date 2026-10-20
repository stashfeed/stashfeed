package gomeng.dev.stashplayer.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The two looks the app can wear; picked under Settings › Appearance. */
enum class StashUiStyle(val persistedValue: String) {
    /** Instagram-like: pure black, edge-to-edge media, brand gradient. Always dark. */
    SocialDark("social_dark"),
    /** Android's Material You: colors from the wallpaper (Android 12+), soft rounded cards. */
    MaterialYou("material_you"),
    ;

    companion object {
        val default: StashUiStyle = SocialDark

        fun fromPersistedValue(value: String?): StashUiStyle =
            entries.firstOrNull { it.persistedValue == value } ?: default
    }
}

val StashBrandPink = Color(0xFFF472B6)
val StashBrandPurple = Color(0xFFA855F7)
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

fun stashStyleTokens(style: StashUiStyle, primary: Color, tertiary: Color): StashStyleTokens = when (style) {
    StashUiStyle.SocialDark -> StashStyleTokens(
        style = style,
        feedCardInset = 0.dp,
        feedCardRadius = 0.dp,
        feedCardPadding = 0.dp,
        feedMediaRadius = 0.dp,
        feedPostGap = 6.dp,
        showFeedDividers = true,
        storyRing = StashBrandGradient,
        navUsesPill = false,
    )
    StashUiStyle.MaterialYou -> StashStyleTokens(
        style = style,
        feedCardInset = 12.dp,
        feedCardRadius = 28.dp,
        feedCardPadding = 14.dp,
        feedMediaRadius = 20.dp,
        feedPostGap = 12.dp,
        showFeedDividers = false,
        storyRing = Brush.linearGradient(listOf(primary, tertiary)),
        navUsesPill = true,
    )
}

val LocalStashStyleTokens = staticCompositionLocalOf {
    stashStyleTokens(StashUiStyle.default, StashBrandPurple, StashBrandPink)
}
