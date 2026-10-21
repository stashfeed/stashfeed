package gomeng.dev.stashplayer.core.ui.theme

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import gomeng.dev.stashplayer.R

/**
 * Stashfeed's one look: a near-black, slightly cool canvas, layered surfaces for depth, and one accent color.
 * Media sits edge to edge on top of it, so the surfaces stay quiet.
 */
private object StashfeedPalette {
    val Canvas = Color(0xFF0B0B0F)
    val ContainerLowest = Color(0xFF060609)
    val ContainerLow = Color(0xFF111116)
    val Container = Color(0xFF15151B)
    val ContainerHigh = Color(0xFF1E1E26)
    val ContainerHighest = Color(0xFF2A2A34)
    val OnCanvas = Color(0xFFF5F4F8)
    val OnCanvasMuted = Color(0xFFA8A6B4)
    val Outline = Color(0xFF34343F)
    val OutlineVariant = Color(0xFF22222B)
    val Star = Color(0xFFFACC15)
    val Error = Color(0xFFFF6B81)
    val OnError = Color(0xFF2B0710)
    val ErrorContainer = Color(0xFF5C1A28)
    val OnErrorContainer = Color(0xFFFFDDE3)
}

/** Accent hue plus the ink drawn on it, a tinted container and the ink on that container. */
private data class StashAccentPalette(
    val primary: Color,
    val onPrimary: Color,
    val container: Color,
    val onContainer: Color,
)

private fun StashAccentColor.palette(): StashAccentPalette = when (this) {
    StashAccentColor.Purple -> StashAccentPalette(Color(0xFFB26BFF), Color(0xFF12051F), Color(0xFF3A1E5C), Color(0xFFF0E3FF))
    StashAccentColor.Rose -> StashAccentPalette(Color(0xFFF472B6), Color(0xFF22030F), Color(0xFF5C1838), Color(0xFFFFE1EF))
    StashAccentColor.Blue -> StashAccentPalette(Color(0xFF6AA9FF), Color(0xFF03132A), Color(0xFF17345C), Color(0xFFDDEBFF))
    StashAccentColor.Amber -> StashAccentPalette(Color(0xFFFF9F43), Color(0xFF2A1300), Color(0xFF5C3410), Color(0xFFFFE8D2))
    StashAccentColor.Cyan -> StashAccentPalette(Color(0xFF34D399), Color(0xFF022116), Color(0xFF0F4A36), Color(0xFFD4F7E9))
}

private fun stashColorScheme(accent: StashAccentPalette): ColorScheme = darkColorScheme(
    primary = accent.primary,
    onPrimary = accent.onPrimary,
    primaryContainer = accent.container,
    onPrimaryContainer = accent.onContainer,
    secondary = accent.primary,
    onSecondary = accent.onPrimary,
    secondaryContainer = StashfeedPalette.ContainerHigh,
    onSecondaryContainer = StashfeedPalette.OnCanvas,
    tertiary = StashfeedPalette.Star,
    onTertiary = Color(0xFF221A03),
    background = StashfeedPalette.Canvas,
    onBackground = StashfeedPalette.OnCanvas,
    surface = StashfeedPalette.Canvas,
    onSurface = StashfeedPalette.OnCanvas,
    surfaceVariant = StashfeedPalette.ContainerHigh,
    onSurfaceVariant = StashfeedPalette.OnCanvasMuted,
    surfaceTint = Color.Transparent,
    surfaceBright = StashfeedPalette.ContainerHighest,
    surfaceDim = StashfeedPalette.ContainerLowest,
    surfaceContainerLowest = StashfeedPalette.ContainerLowest,
    surfaceContainerLow = StashfeedPalette.ContainerLow,
    surfaceContainer = StashfeedPalette.Container,
    surfaceContainerHigh = StashfeedPalette.ContainerHigh,
    surfaceContainerHighest = StashfeedPalette.ContainerHighest,
    inverseSurface = StashfeedPalette.OnCanvas,
    inverseOnSurface = StashfeedPalette.Canvas,
    inversePrimary = accent.container,
    outline = StashfeedPalette.Outline,
    outlineVariant = StashfeedPalette.OutlineVariant,
    error = StashfeedPalette.Error,
    onError = StashfeedPalette.OnError,
    errorContainer = StashfeedPalette.ErrorContainer,
    onErrorContainer = StashfeedPalette.OnErrorContainer,
    scrim = Color.Black,
)

/** The wallpaper's accent (Android 12+) on Stashfeed's own dark surfaces. */
private fun wallpaperAccent(dynamic: ColorScheme) = StashAccentPalette(
    primary = dynamic.primary,
    onPrimary = dynamic.onPrimary,
    container = dynamic.primaryContainer,
    onContainer = dynamic.onPrimaryContainer,
)

/** Swatch shown next to each accent in Settings. */
fun stashAccentSwatch(accentColor: StashAccentColor): Color = accentColor.palette().primary

data class StashThemeColorSnapshot(
    val backgroundArgb: Long,
    val onSurfaceArgb: Long,
    val onSurfaceVariantArgb: Long,
    val primaryArgb: Long,
    val onPrimaryArgb: Long,
)

fun stashThemeColorSnapshot(accentColor: StashAccentColor = StashAccentColor.default): StashThemeColorSnapshot {
    val accent = accentColor.palette()
    return StashThemeColorSnapshot(
        backgroundArgb = StashfeedPalette.Canvas.argbLong(),
        onSurfaceArgb = StashfeedPalette.OnCanvas.argbLong(),
        onSurfaceVariantArgb = StashfeedPalette.OnCanvasMuted.argbLong(),
        primaryArgb = accent.primary.argbLong(),
        onPrimaryArgb = accent.onPrimary.argbLong(),
    )
}

private fun Color.argbLong(): Long = toArgb().toLong() and 0xFFFFFFFFL

fun stashThemeContrastRatio(foregroundArgb: Long, backgroundArgb: Long): Double {
    val lighter = maxOf(relativeLuminance(foregroundArgb), relativeLuminance(backgroundArgb))
    val darker = minOf(relativeLuminance(foregroundArgb), relativeLuminance(backgroundArgb))
    return (lighter + 0.05) / (darker + 0.05)
}

private fun relativeLuminance(argb: Long): Double {
    fun channel(shift: Int): Double {
        val c = ((argb shr shift) and 0xFF) / 255.0
        return if (c <= 0.04045) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
    }
    return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
}

/** Three radii and a pill: 8 for small parts (chips, thumbnails), 16 for cards, 28 for sheets and dialogs. */
private val StashfeedShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@OptIn(ExperimentalTextApi::class)
private fun inter(weight: Int) = Font(
    resId = R.font.inter_variable,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Inter, from a single variable font file. */
val InterFamily = FontFamily(inter(400), inter(500), inter(600), inter(700), inter(800))

/**
 * Inter wants tighter tracking than Roboto: no positive letter spacing on body text, negative on large sizes.
 * Digits are tabular everywhere so durations, counts and timestamps line up.
 */
private fun stashTypography(): Typography {
    fun style(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) = TextStyle(
        fontFamily = InterFamily,
        fontSize = size.sp,
        lineHeight = line.sp,
        fontWeight = weight,
        letterSpacing = tracking.em,
        fontFeatureSettings = "tnum",
    )
    return Typography(
        displayLarge = style(48, 54, FontWeight.ExtraBold, -0.03),
        displayMedium = style(40, 46, FontWeight.ExtraBold, -0.03),
        displaySmall = style(34, 40, FontWeight.ExtraBold, -0.025),
        headlineLarge = style(30, 36, FontWeight.ExtraBold, -0.02),
        headlineMedium = style(26, 32, FontWeight.Bold, -0.02),
        headlineSmall = style(22, 28, FontWeight.Bold, -0.015),
        titleLarge = style(20, 26, FontWeight.Bold, -0.01),
        titleMedium = style(16, 22, FontWeight.SemiBold, -0.005),
        titleSmall = style(14, 20, FontWeight.SemiBold),
        bodyLarge = style(16, 24, FontWeight.Normal),
        bodyMedium = style(14, 20, FontWeight.Normal),
        bodySmall = style(12, 16, FontWeight.Normal, 0.005),
        labelLarge = style(14, 20, FontWeight.SemiBold),
        labelMedium = style(12, 16, FontWeight.SemiBold, 0.01),
        labelSmall = style(11, 14, FontWeight.SemiBold, 0.02),
    )
}

private val StashTypography = stashTypography()

/**
 * Stashfeed is always dark. [uiStyle] only picks where the accent comes from: Stashfeed's own [accentColor], or
 * the wallpaper on Android 12+ ([StashUiStyle.MaterialYou]).
 */
@Composable
fun StashPlayerTheme(
    accentColor: StashAccentColor = StashAccentColor.default,
    uiStyle: StashUiStyle = StashUiStyle.default,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val useWallpaper = uiStyle == StashUiStyle.MaterialYou && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme = remember(accentColor, useWallpaper, context) {
        val accent = if (useWallpaper) wallpaperAccent(dynamicDarkColorScheme(context)) else accentColor.palette()
        stashColorScheme(accent)
    }
    val tokens = remember(colorScheme.primary) { stashStyleTokens(colorScheme.primary) }
    CompositionLocalProvider(LocalStashStyleTokens provides tokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = StashfeedShapes,
            typography = StashTypography,
            content = content,
        )
    }
}
