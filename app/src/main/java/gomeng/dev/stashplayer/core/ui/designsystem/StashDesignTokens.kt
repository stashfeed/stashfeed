package gomeng.dev.stashplayer.core.ui.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Semantic visual tokens for the Stash dark-first media UI.
 *
 * Keep these values small and stable: feature screens should depend on the
 * semantic names instead of copying raw spacing, radius, alpha, or color values.
 */
/** The spacing scale. Every gap, padding and margin in the app is one of these steps. */
object StashSpace {
    val XS = 4.dp
    val S = 8.dp
    val M = 12.dp
    val L = 16.dp
    val XL = 24.dp
    val XXL = 32.dp
}

/** Semantic spacing built from [StashSpace]; screens use these names. */
object StashSpacing {
    val ScreenHorizontalCompact = StashSpace.L
    val ScreenHorizontalExpanded = StashSpace.XL
    val SectionGap = StashSpace.XL
    val CardGap = StashSpace.M
    val CardPadding = StashSpace.L
    val ChipGap = StashSpace.S
    val BottomContentPadding = 96.dp
}

object StashTouch {
    val MinTarget = 48.dp
    val MinAdjacentSpacing = 8.dp
    val PlayerTopSafeInset = 12.dp
    val PlayerSheetHorizontalInsetCompact = 12.dp
    val PlayerSheetContentInset = 16.dp
    val SceneQuickActionMinTarget = 48.dp
}

/** Three radii and a pill: small parts, cards, sheets. Matches MaterialTheme.shapes. */
object StashRadius {
    val S = 8.dp
    val M = 16.dp
    val L = 28.dp
    val Pill = 999.dp
}

object StashRadii {
    val Hero = StashRadius.L
    val Card = StashRadius.M
    val Thumbnail = StashRadius.S
    val Sheet = StashRadius.L
    val Pill = StashRadius.Pill
}

object StashMotion {
    const val OverlayFadeInMs = 150
    const val OverlayFadeOutMs = 220
    const val BottomSheetSettleMs = 300
    const val ToggleFeedbackMs = 180
    const val SeekThumbActiveMs = 100

    /** Springs instead of fixed durations: things that move settle like physical objects. */
    const val SpringStiffnessStandard = 700f
    const val SpringStiffnessFast = 1400f
    const val SpringDampingStandard = 0.85f
    const val SpringDampingBouncy = 0.7f

    fun isPremiumMicroInteraction(durationMs: Int): Boolean = durationMs in 120..300
}

object StashAlpha {
    const val Glass = 0.055f
    const val GlassStrong = 0.085f
    const val SurfaceRaised = 0.78f
    const val SurfaceOverlay = 0.72f
    const val IconAction = 0.86f
    const val IconActionSelected = 0.88f
    const val SelectedContainer = 0.42f
    const val BorderSubtle = 0.08f
    const val BorderStrong = 0.16f
    const val BorderRaised = 0.22f
    const val ScrimBottom = 0.72f
    const val PlayerTopChrome = 0.36f
    const val PlayerQuickActionRail = 0.24f
    const val PlayerChromeBorder = 0.14f
    const val PlayerChromeHighlight = 0.10f
    const val PlayerHudSurface = 0.66f
    const val PlayerStatusCardBorder = 0.18f
    const val DisabledContent = 0.38f
    const val QueueActionSelected = 0.22f
    const val FavoriteActionSelected = 0.24f
    const val WatchLaterActionSelected = 0.26f
}

/**
 * Fixed colors for places that sit on media rather than on the theme (player chrome, badges over thumbnails).
 * Everything that sits on the app's own surfaces takes its color from MaterialTheme.colorScheme instead.
 */
object StashColors {
    val Background = Color(0xFF0B0B0F)
    val Surface = Color(0xFF15151B)
    val SurfaceElevated = Color(0xFF1E1E26)
    val Primary = Color(0xFFB26BFF)
    val PrimaryBlue = Color(0xFF6AA9FF)
    val AccentCyan = Color(0xFF34D399)
    val AccentAmber = Color(0xFFFF9F43)
    val ScrimStrong = Color.Black.copy(alpha = 0.72f)
    val ScrimSubtle = Color.Black.copy(alpha = 0.34f)
    val BufferTrack = Color.White.copy(alpha = 0.28f)
    val PlayerChromeBorder = Color.White.copy(alpha = StashAlpha.PlayerChromeBorder)
    val PlayerChromeHighlight = Color.White.copy(alpha = StashAlpha.PlayerChromeHighlight)
    val PlayerStatusAccent = Color(0xFFC9A0FF)
    val Success = Color(0xFF34D399)
    val Warning = Color(0xFFFACC15)
    val Error = Color(0xFFFF6B81)
    val FocusRing = Color(0xFFC9A0FF)
    val TextPrimary = Color(0xFFF5F4F8)
    val TextSecondary = Color(0xFFA8A6B4)
    /** Text and badges drawn straight on photos and video. */
    val OnMedia = Color.White
    val OnMediaMuted = Color.White.copy(alpha = 0.78f)
    val MediaBadge = Color.Black.copy(alpha = 0.62f)
    val Star = Color(0xFFFACC15)
    val QueueAction = Color(0xFF6AA9FF)
    val QueueActionContainer = PrimaryBlue
    val FavoriteAction = Color(0xFFF472B6)
    val FavoriteActionContainer = Color(0xFFF472B6)
    val WatchLaterAction = Color(0xFFC9A0FF)
    val WatchLaterActionContainer = Primary
}

enum class StashSurfaceRole {
    BaseCard,
    ElevatedCard,
    Sheet,
    ModalOverlay,
    PlayerFullscreenChrome,
    Destructive,
}

enum class StashSurfaceColorRole {
    Background,
    Surface,
    SurfaceContainer,
    SurfaceContainerHigh,
    SurfaceVariant,
    OnSurface,
    OnSurfaceVariant,
    Outline,
    Error,
    ErrorContainer,
    OnError,
    OnErrorContainer,
    PlayerChromeBlack,
    PlayerChromeOnBlack,
}

data class StashSurfaceTreatment(
    val containerRole: StashSurfaceColorRole,
    val contentRole: StashSurfaceColorRole,
    val borderRole: StashSurfaceColorRole,
    val containerAlpha: Float,
    val borderAlpha: Float,
    val tonalElevationDp: Int,
)

fun stashSurfaceTreatment(role: StashSurfaceRole): StashSurfaceTreatment = when (role) {
    StashSurfaceRole.BaseCard -> StashSurfaceTreatment(
        containerRole = StashSurfaceColorRole.SurfaceContainer,
        contentRole = StashSurfaceColorRole.OnSurface,
        borderRole = StashSurfaceColorRole.Outline,
        containerAlpha = 1f,
        borderAlpha = StashAlpha.BorderStrong,
        tonalElevationDp = 2,
    )

    StashSurfaceRole.ElevatedCard -> StashSurfaceTreatment(
        containerRole = StashSurfaceColorRole.SurfaceContainerHigh,
        contentRole = StashSurfaceColorRole.OnSurface,
        borderRole = StashSurfaceColorRole.Outline,
        containerAlpha = 1f,
        borderAlpha = StashAlpha.BorderRaised,
        tonalElevationDp = 4,
    )

    StashSurfaceRole.Sheet -> StashSurfaceTreatment(
        containerRole = StashSurfaceColorRole.SurfaceContainerHigh,
        contentRole = StashSurfaceColorRole.OnSurface,
        borderRole = StashSurfaceColorRole.Outline,
        containerAlpha = 1f,
        borderAlpha = 0.30f,
        tonalElevationDp = 6,
    )

    StashSurfaceRole.ModalOverlay -> StashSurfaceTreatment(
        containerRole = StashSurfaceColorRole.PlayerChromeBlack,
        contentRole = StashSurfaceColorRole.PlayerChromeOnBlack,
        borderRole = StashSurfaceColorRole.PlayerChromeBlack,
        containerAlpha = 0.56f,
        borderAlpha = 0f,
        tonalElevationDp = 0,
    )

    StashSurfaceRole.PlayerFullscreenChrome -> StashSurfaceTreatment(
        containerRole = StashSurfaceColorRole.PlayerChromeBlack,
        contentRole = StashSurfaceColorRole.PlayerChromeOnBlack,
        borderRole = StashSurfaceColorRole.PlayerChromeOnBlack,
        containerAlpha = 0.58f,
        borderAlpha = StashAlpha.PlayerChromeBorder,
        tonalElevationDp = 0,
    )

    StashSurfaceRole.Destructive -> StashSurfaceTreatment(
        containerRole = StashSurfaceColorRole.ErrorContainer,
        contentRole = StashSurfaceColorRole.OnErrorContainer,
        borderRole = StashSurfaceColorRole.Error,
        containerAlpha = 1f,
        borderAlpha = 0.70f,
        tonalElevationDp = 2,
    )
}

object StashPlayerYoutubeVisualTokens {
    const val TopChromeSurfaceAlpha = 0.50f
    const val TopChromeHorizontalInsetDp = 12f
    const val TopChromeTopInsetDp = 12f
    const val TopChromeRowSpacingDp = 8f
    const val TopChromeControlSpacingDp = 8f
    const val TopChromeBubbleSizeDp = 48f
    const val TopChromePillMinWidthDp = 72f
    const val TopChromePillMinHeightDp = 48f
    const val TopChromePillHorizontalPaddingDp = 12f
    const val TopScrimHeightDp = 184f
    const val TopScrimStartAlpha = 0.60f
    const val TopScrimEndAlpha = 0f
    const val CenterPrimaryButtonSizeDp = 72f
    const val CenterSecondaryButtonSizeDp = 56f
    const val TopChromeIconSizeDp = 22f
    const val BottomSheetHorizontalInsetDp = 12f
    const val BottomSheetContentInsetDp = 16f
    const val BottomSheetTopRadiusDp = 28f
    const val BottomSheetBorderAlpha = 0.10f
    const val BottomSheetElevationDp = 10f
    const val BottomSheetHandleWidthDp = 52f
    const val BottomSheetHandleHeightDp = 5f
    const val BottomSheetSeekInactiveTrackAlpha = 0.64f
    const val BottomSheetSeekTouchHeightDp = 48f
    const val BottomSheetSurfaceTopAlpha = 0.88f
    const val BottomSheetSurfaceMiddleAlpha = 0.96f
    const val BottomSheetSurfaceBottomAlpha = 0.98f
    const val BottomSheetTimePrimaryAlpha = 0.88f
    const val BottomSheetTimeSecondaryAlpha = 0.78f
}

data class StashPlayerYoutubeVisualPolicy(
    val minimumTouchTargetDp: Float,
    val minimumAdjacentSpacingDp: Float,
    val topChromeSurfaceAlpha: Float,
    val bottomSheetSurfaceAlpha: Float,
    val bottomSheetTopRadiusDp: Float,
    val bottomSheetHorizontalInsetDp: Float,
    val centerPrimaryButtonSizeDp: Float,
    val centerSecondaryButtonSizeDp: Float,
    val topChromeIconSizeDp: Float,
    val topChromeHorizontalInsetDp: Float,
    val topChromeTopInsetDp: Float,
    val topChromeRowSpacingDp: Float,
    val topChromeControlSpacingDp: Float,
    val topChromeBubbleSizeDp: Float,
    val topChromePillMinWidthDp: Float,
    val topChromePillMinHeightDp: Float,
    val topChromePillHorizontalPaddingDp: Float,
    val topScrimHeightDp: Float,
    val topScrimStartAlpha: Float,
    val topScrimEndAlpha: Float,
    val bottomSheetContentInsetDp: Float,
    val bottomSheetBorderAlpha: Float,
    val bottomSheetElevationDp: Float,
    val bottomSheetHandleWidthDp: Float,
    val bottomSheetHandleHeightDp: Float,
    val bottomSheetSeekInactiveTrackAlpha: Float,
    val bottomSheetSeekTouchHeightDp: Float,
)

fun stashPlayerYoutubeVisualPolicy(): StashPlayerYoutubeVisualPolicy = StashPlayerYoutubeVisualPolicy(
    minimumTouchTargetDp = StashTouch.MinTarget.value,
    minimumAdjacentSpacingDp = StashTouch.MinAdjacentSpacing.value,
    topChromeSurfaceAlpha = StashPlayerYoutubeVisualTokens.TopChromeSurfaceAlpha,
    bottomSheetSurfaceAlpha = StashAlpha.SurfaceRaised,
    bottomSheetTopRadiusDp = StashRadii.Sheet.value,
    bottomSheetHorizontalInsetDp = StashTouch.PlayerSheetHorizontalInsetCompact.value,
    centerPrimaryButtonSizeDp = StashPlayerYoutubeVisualTokens.CenterPrimaryButtonSizeDp,
    centerSecondaryButtonSizeDp = StashPlayerYoutubeVisualTokens.CenterSecondaryButtonSizeDp,
    topChromeIconSizeDp = StashPlayerYoutubeVisualTokens.TopChromeIconSizeDp,
    topChromeHorizontalInsetDp = StashPlayerYoutubeVisualTokens.TopChromeHorizontalInsetDp,
    topChromeTopInsetDp = StashPlayerYoutubeVisualTokens.TopChromeTopInsetDp,
    topChromeRowSpacingDp = StashPlayerYoutubeVisualTokens.TopChromeRowSpacingDp,
    topChromeControlSpacingDp = StashPlayerYoutubeVisualTokens.TopChromeControlSpacingDp,
    topChromeBubbleSizeDp = StashPlayerYoutubeVisualTokens.TopChromeBubbleSizeDp,
    topChromePillMinWidthDp = StashPlayerYoutubeVisualTokens.TopChromePillMinWidthDp,
    topChromePillMinHeightDp = StashPlayerYoutubeVisualTokens.TopChromePillMinHeightDp,
    topChromePillHorizontalPaddingDp = StashPlayerYoutubeVisualTokens.TopChromePillHorizontalPaddingDp,
    topScrimHeightDp = StashPlayerYoutubeVisualTokens.TopScrimHeightDp,
    topScrimStartAlpha = StashPlayerYoutubeVisualTokens.TopScrimStartAlpha,
    topScrimEndAlpha = StashPlayerYoutubeVisualTokens.TopScrimEndAlpha,
    bottomSheetContentInsetDp = StashPlayerYoutubeVisualTokens.BottomSheetContentInsetDp,
    bottomSheetBorderAlpha = StashPlayerYoutubeVisualTokens.BottomSheetBorderAlpha,
    bottomSheetElevationDp = StashPlayerYoutubeVisualTokens.BottomSheetElevationDp,
    bottomSheetHandleWidthDp = StashPlayerYoutubeVisualTokens.BottomSheetHandleWidthDp,
    bottomSheetHandleHeightDp = StashPlayerYoutubeVisualTokens.BottomSheetHandleHeightDp,
    bottomSheetSeekInactiveTrackAlpha = StashPlayerYoutubeVisualTokens.BottomSheetSeekInactiveTrackAlpha,
    bottomSheetSeekTouchHeightDp = StashPlayerYoutubeVisualTokens.BottomSheetSeekTouchHeightDp,
)
