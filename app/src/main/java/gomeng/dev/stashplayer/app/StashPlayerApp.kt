package gomeng.dev.stashplayer.app

import android.app.Activity
import android.app.Application
import android.annotation.TargetApi
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import gomeng.dev.stashplayer.app.navigation.StashNavHost
import gomeng.dev.stashplayer.app.navigation.isFoldLikeLayoutBySmallestWidthDp
import gomeng.dev.stashplayer.core.debug.StashDebugLogBuffer
import gomeng.dev.stashplayer.core.network.StashSettingsRepository
import gomeng.dev.stashplayer.core.security.RecentsPrivacyProtection
import gomeng.dev.stashplayer.core.security.resolveRecentsPrivacyProtection
import gomeng.dev.stashplayer.core.ui.i18n.updateStashStringContext
import gomeng.dev.stashplayer.core.ui.i18n.withStashAppLanguage
import gomeng.dev.stashplayer.core.ui.theme.StashPlayerTheme
import gomeng.dev.stashplayer.core.ui.theme.StashUiStyle
import gomeng.dev.stashplayer.core.ui.theme.resolveStashDarkTheme
import gomeng.dev.stashplayer.feature.security.BiometricAppLockGate

class StashPlayerApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        updateStashStringContext(this)
        StashDebugLogBuffer.initialize(this)
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            StashDebugLogBuffer.record("Uncaught", "Thread ${thread.name}", throwable)
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                add(ImageDecoderDecoder.Factory())
            } else {
                add(GifDecoder.Factory())
            }
        }
        // Short fade instead of images popping in while scrolling.
        .crossfade(150)
        // Stash marks thumbnails no-cache; reusing the disk cache keeps feeds smooth and saves data.
        .respectCacheHeaders(false)
        .build()
}

@Composable
fun StashPlayerAppRoot() {
    val context = LocalContext.current
    val localView = LocalView.current
    val deviceConfiguration = LocalConfiguration.current
    val repository = remember(context) { StashSettingsRepository(context) }
    val themeMode by repository.themeMode.collectAsState(
        initial = StashSettingsRepository.DEFAULT_THEME_MODE,
    )
    val appLanguage by repository.appLanguage.collectAsState(
        initial = StashSettingsRepository.DEFAULT_APP_LANGUAGE,
    )
    val accentColor by repository.accentColor.collectAsState(
        initial = StashSettingsRepository.DEFAULT_ACCENT_COLOR,
    )
    val uiStyle by repository.uiStyle.collectAsState(initial = StashUiStyle.default)
    val uiScale by repository.uiScale.collectAsState(
        initial = StashSettingsRepository.DEFAULT_UI_SCALE,
    )
    val recentAppsPrivacyEnabled by repository.recentAppsPrivacyEnabled.collectAsState(
        initial = StashSettingsRepository.DEFAULT_RECENT_APPS_PRIVACY_ENABLED,
    )
    val savedProfile by repository.savedServerProfile.collectAsState(initial = null)
    val biometricAppLockEnabledState = repository.biometricAppLockEnabled.collectAsState(initial = null as Boolean?)
    val biometricAppLockEnabled = biometricAppLockEnabledState.value
    var appSessionUnlocked by rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    LaunchedEffect(biometricAppLockEnabled) {
        if (biometricAppLockEnabled == false) {
            appSessionUnlocked = true
        }
    }
    val localizedContext = remember(context, appLanguage) {
        context.withStashAppLanguage(appLanguage)
    }
    val localizedConfiguration = remember(localizedContext) {
        localizedContext.resources.configuration
    }
    updateStashStringContext(localizedContext)
    val isFoldLikeLayout = isFoldLikeLayoutBySmallestWidthDp(deviceConfiguration.smallestScreenWidthDp)
    val activity = remember(context, localView) {
        context.findActivity() ?: localView.context.findActivity()
    }

    RecentsPrivacyEffect(
        activity = activity,
        hideRecentAppsPreview = recentAppsPrivacyEnabled,
    )

    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedConfiguration,
    ) {
        StashPlayerTheme(
            accentColor = accentColor,
            uiStyle = uiStyle,
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                biometricAppLockEnabled?.let { appLockEnabled ->
                    BiometricAppLockGate(
                        hasSavedProfile = savedProfile != null,
                        appLockEnabled = appLockEnabled,
                        appSessionUnlocked = appSessionUnlocked,
                        onUnlocked = { appSessionUnlocked = true },
                    ) {
                        StashNavHost(
                            isFoldLikeLayout = isFoldLikeLayout,
                            uiScale = uiScale,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentsPrivacyEffect(
    activity: Activity?,
    hideRecentAppsPreview: Boolean,
) {
    DisposableEffect(activity, hideRecentAppsPreview) {
        applyRecentsPrivacyProtection(activity, hideRecentAppsPreview)
        onDispose {
            applyRecentsPrivacyProtection(activity, hideRecentAppsPreview = false)
        }
    }
}

private fun applyRecentsPrivacyProtection(
    activity: Activity?,
    hideRecentAppsPreview: Boolean,
) {
    if (activity == null) return
    when (
        resolveRecentsPrivacyProtection(
            hideRecentAppsPreview = hideRecentAppsPreview,
            sdkInt = Build.VERSION.SDK_INT,
        )
    ) {
        RecentsPrivacyProtection.None -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                setRecentsScreenshotEnabled(activity, enabled = true)
            }
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        RecentsPrivacyProtection.RecentsScreenshotDisabled -> {
            setRecentsScreenshotEnabled(activity, enabled = false)
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        RecentsPrivacyProtection.SecureWindowFlag -> {
            activity.window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE,
            )
        }
    }
}

@TargetApi(Build.VERSION_CODES.TIRAMISU)
private fun setRecentsScreenshotEnabled(activity: Activity, enabled: Boolean) {
    activity.setRecentsScreenshotEnabled(enabled)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
