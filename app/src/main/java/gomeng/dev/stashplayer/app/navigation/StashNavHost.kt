package gomeng.dev.stashplayer.app.navigation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.foundation.rememberScrollState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemColors
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemColors
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import gomeng.dev.stashplayer.R
import gomeng.dev.stashplayer.core.local.StashLocalLibraryRepository
import gomeng.dev.stashplayer.core.local.applyLocalFavoriteFilter
import gomeng.dev.stashplayer.core.model.GalleryImageModel
import gomeng.dev.stashplayer.core.model.SceneCardModel
import gomeng.dev.stashplayer.core.debug.StashDebugLogBuffer
import gomeng.dev.stashplayer.core.player.PlayerPlaybackQueue
import gomeng.dev.stashplayer.core.player.PlayerPlaybackQueueContinuation
import gomeng.dev.stashplayer.core.player.PlayerPresentationMode
import gomeng.dev.stashplayer.core.player.appendLoadedResultPlaybackQueue
import gomeng.dev.stashplayer.core.player.afterLoadedPage
import gomeng.dev.stashplayer.core.player.handOffHomeResumePlaybackQueue
import gomeng.dev.stashplayer.core.player.handOffLoadedResultPlaybackQueue
import gomeng.dev.stashplayer.core.player.shouldApplyPlayerShuffle
import gomeng.dev.stashplayer.core.player.shouldLoadMorePlayerPlaylistItems
import gomeng.dev.stashplayer.core.network.StashGraphQlClient
import gomeng.dev.stashplayer.core.network.AppUpdateChecker
import gomeng.dev.stashplayer.core.network.AppUpdateNotice
import gomeng.dev.stashplayer.core.network.StashLoginClient
import gomeng.dev.stashplayer.core.network.StashSettingsRepository
import gomeng.dev.stashplayer.core.network.passwordSessionStartupRefreshKey
import gomeng.dev.stashplayer.core.network.shouldRefreshPasswordSessionOnStartup
import gomeng.dev.stashplayer.core.player.PlaybackOrientationMode
import gomeng.dev.stashplayer.core.ui.theme.LocalStashStyleTokens
import gomeng.dev.stashplayer.core.ui.components.LocalStashBrowseTabs
import gomeng.dev.stashplayer.app.StashLaunchState
import gomeng.dev.stashplayer.core.ui.components.LocalStashAvatarTarget
import gomeng.dev.stashplayer.core.ui.components.StashAvatarTarget
import gomeng.dev.stashplayer.core.ui.components.LocalStashPlayerExpanded
import gomeng.dev.stashplayer.core.ui.components.LocalStashPlayerActive
import gomeng.dev.stashplayer.core.ui.designsystem.StashSegmentedTabs
import gomeng.dev.stashplayer.core.ui.designsystem.stashSpring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.ExperimentalSharedTransitionApi
import gomeng.dev.stashplayer.core.ui.components.StashThumbnailFlightOverlay
import gomeng.dev.stashplayer.core.ui.components.StashThumbnailFlightRegistry
import gomeng.dev.stashplayer.core.ui.components.StashThumbnailFlight
import gomeng.dev.stashplayer.core.ui.components.LocalStashThumbnailFlights
import gomeng.dev.stashplayer.core.ui.components.LocalStashNavAnimatedScope
import gomeng.dev.stashplayer.core.ui.components.LocalStashSharedTransitionScope
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Rect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.key
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import gomeng.dev.stashplayer.core.ui.designsystem.StashSpace
import gomeng.dev.stashplayer.core.model.StashGalleryBrowseMode
import gomeng.dev.stashplayer.core.ui.theme.StashUiScale
import gomeng.dev.stashplayer.core.ui.theme.StashUiScaleProvider
import gomeng.dev.stashplayer.feature.gallery.GalleryDetailRoute
import gomeng.dev.stashplayer.feature.gallery.GalleryPhotoViewerOverlay
import gomeng.dev.stashplayer.feature.gallery.GalleryRoute
import gomeng.dev.stashplayer.feature.feed.FeedLightbox
import gomeng.dev.stashplayer.feature.feed.FeedLightboxRequest
import gomeng.dev.stashplayer.feature.feed.HomeFeedRoute
import gomeng.dev.stashplayer.feature.downloads.DownloadsRoute
import gomeng.dev.stashplayer.feature.downloads.StashShareInbox
import gomeng.dev.stashplayer.feature.home.HomeRoute
import gomeng.dev.stashplayer.feature.library.LibraryRoute
import gomeng.dev.stashplayer.feature.performers.PerformerProfileRoute
import gomeng.dev.stashplayer.feature.performers.PerformersRoute
import gomeng.dev.stashplayer.core.ui.designsystem.StashSpacing
import gomeng.dev.stashplayer.feature.player.PlayerRoute
import gomeng.dev.stashplayer.feature.queue.QueueRoute
import gomeng.dev.stashplayer.feature.explore.ExploreRoute
import gomeng.dev.stashplayer.feature.shorts.ShortsRoute
import gomeng.dev.stashplayer.feature.settings.SettingsDestinations
import gomeng.dev.stashplayer.feature.settings.SettingsDetailRoute
import gomeng.dev.stashplayer.feature.settings.SettingsRoute
import gomeng.dev.stashplayer.feature.settings.SettingsSection
import gomeng.dev.stashplayer.feature.setup.ServerSetupRoute
import kotlinx.coroutines.flow.first

/** Height of the bottom navigation bar the mini player floats above (without the system inset). */
private val MiniPlayerAboveNavigationBar = 80.dp

private fun isTabSwitch(from: String?, to: String?): Boolean {
    val tabs = TopLevelDestination.entries.map { it.route }
    return from in tabs && to in tabs
}

private enum class TopLevelDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    Home("home", topLevelDestinationLabelResource("home"), Icons.Outlined.Home, Icons.Filled.Home),
    ForYou("shorts", topLevelDestinationLabelResource("shorts"), Icons.Outlined.PlayCircle, Icons.Filled.PlayCircle),
    Explore("browse", topLevelDestinationLabelResource("browse"), Icons.Outlined.Explore, Icons.Filled.Explore),
    Performers("performers", topLevelDestinationLabelResource("performers"), Icons.Outlined.People, Icons.Filled.People),
    Library("library", topLevelDestinationLabelResource("library"), Icons.Outlined.VideoLibrary, Icons.Filled.VideoLibrary),
}

/** Screens that used to be tabs; they now open from Explore or Library. */
private const val GalleryRoutePath = "gallery"
private const val QueueRoutePath = "queue"
private const val DownloadsRoutePath = "downloads"
private const val LegacyHomeRoutePath = "home/dashboard"

private data class ActiveGalleryPhotoViewer(
    val initialIndex: Int,
    val images: List<GalleryImageModel>,
)

@Composable
private fun TopLevelDestinationIcon(
    destination: TopLevelDestination,
    label: String,
    showBadge: Boolean,
    selected: Boolean = false,
) {
    val icon = if (selected) destination.selectedIcon else destination.icon
    if (showBadge) {
        BadgedBox(badge = { Badge() }) {
            androidx.compose.material3.Icon(icon, contentDescription = label)
        }
    } else {
        androidx.compose.material3.Icon(icon, contentDescription = label)
    }
}

/** Social Dark: plain icons, no pill. Material You: the standard tonal pill. */
@Composable
private fun stashNavigationBarItemColors(): NavigationBarItemColors {
    val scheme = MaterialTheme.colorScheme
    return if (LocalStashStyleTokens.current.navUsesPill) {
        NavigationBarItemDefaults.colors(
            selectedIconColor = scheme.onSecondaryContainer,
            selectedTextColor = scheme.onSurface,
            indicatorColor = scheme.secondaryContainer,
            unselectedIconColor = scheme.onSurfaceVariant,
            unselectedTextColor = scheme.onSurfaceVariant,
        )
    } else {
        NavigationBarItemDefaults.colors(
            selectedIconColor = scheme.onSurface,
            selectedTextColor = scheme.onSurface,
            indicatorColor = Color.Transparent,
            unselectedIconColor = scheme.onSurfaceVariant,
            unselectedTextColor = scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun stashNavigationRailItemColors(): NavigationRailItemColors {
    val scheme = MaterialTheme.colorScheme
    return if (LocalStashStyleTokens.current.navUsesPill) {
        NavigationRailItemDefaults.colors(
            selectedIconColor = scheme.onSecondaryContainer,
            selectedTextColor = scheme.onSurface,
            indicatorColor = scheme.secondaryContainer,
            unselectedIconColor = scheme.onSurfaceVariant,
            unselectedTextColor = scheme.onSurfaceVariant,
        )
    } else {
        NavigationRailItemDefaults.colors(
            selectedIconColor = scheme.onSurface,
            selectedTextColor = scheme.onSurface,
            indicatorColor = scheme.surfaceContainerHigh,
            unselectedIconColor = scheme.onSurfaceVariant,
            unselectedTextColor = scheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun StashNavHost(
    isFoldLikeLayout: Boolean,
    uiScale: StashUiScale = StashUiScale.default,
) {
    val context = LocalContext.current
    val settingsRepository = remember(context) { StashSettingsRepository(context) }
    val localRepository = remember(context) { StashLocalLibraryRepository(context) }
    val savedProfile by settingsRepository.savedServerProfile.collectAsState(initial = StashLaunchState.initialSavedProfile)
    val runtimeProfile by settingsRepository.serverProfile.collectAsState(initial = null)
    val playbackOrientationMode by settingsRepository.playbackOrientationMode.collectAsState(
        initial = StashSettingsRepository.DEFAULT_PLAYBACK_ORIENTATION_MODE,
    )
    val favoriteSceneIds by localRepository.favoriteSceneIds.collectAsState(initial = emptySet())
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val updateChecker = remember { AppUpdateChecker() }
    // Library and settings live behind the avatar in the top bars, so the bar keeps four tabs.
    val topLevelDestinations = TopLevelDestination.entries.filter { it != TopLevelDestination.Library }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route
    var playbackQueue by remember { mutableStateOf(PlayerPlaybackQueue.Empty) }
    var playbackQueueRestored by remember { mutableStateOf(false) }
    var playbackQueueContinuation by remember { mutableStateOf<PlayerPlaybackQueueContinuation?>(null) }
    var playerPresentationMode by remember { mutableStateOf(PlayerPresentationMode.WatchPage) }
    var playerLandscapeAutoFullscreen by remember { mutableStateOf(false) }
    var playerPlaybackSpeed by remember { mutableFloatStateOf(1f) }
    var refreshedPasswordSessionKey by remember { mutableStateOf<String?>(null) }
    var availableUpdateNotice by remember { mutableStateOf<AppUpdateNotice?>(null) }
    var updateChangelogNotice by remember { mutableStateOf<AppUpdateNotice?>(null) }
    var activeGalleryPhotoViewer by remember { mutableStateOf<ActiveGalleryPhotoViewer?>(null) }
    var activeFeedLightbox by remember { mutableStateOf<FeedLightboxRequest?>(null) }
    /** The scene in the player layer above the navigation; null when no player is open. */
    var activePlayerSceneId by rememberSaveable { mutableStateOf<String?>(null) }
    var playerMinimized by rememberSaveable { mutableStateOf(false) }
    var playerPauseSignal by remember { mutableIntStateOf(0) }
    val playerExpanded = activePlayerSceneId != null && !playerMinimized
    val thumbnailFlights = remember { StashThumbnailFlightRegistry() }
    var activeFlight by remember { mutableStateOf<StashThumbnailFlight?>(null) }
    // Chrome (bars, orientation, UI scale) behaves as before: as if the player were the current screen.
    val chromeRoute = if (playerExpanded) PlayerRoutePattern else currentRoute
    // The For You reel has its own sound; a running mini player pauses when you open it.
    LaunchedEffect(currentRoute) {
        if (currentRoute == TopLevelDestination.ForYou.route && activePlayerSceneId != null && playerMinimized) playerPauseSignal += 1
    }
    val navigationChromePolicy = stashNavigationChromeVisualPolicy()
    val activeUiScale = if (isPlayerRoute(chromeRoute)) StashUiScale.Default else uiScale
    val activity = remember(context) { context.findActivity() }
    AppOrientationEffect(
        activity = activity,
        request = resolveAppOrientationRequest(
            isFoldLikeLayout = isFoldLikeLayout,
            route = chromeRoute,
            playerPresentationMode = playerPresentationMode,
            playbackOrientationMode = playbackOrientationMode,
        ),
    )

    LaunchedEffect(Unit) {
        val restoredQueue = runCatching { localRepository.persistedPlaybackQueue.first() }.getOrNull()
        if (!playbackQueueRestored) {
            playbackQueue = restoredQueue ?: PlayerPlaybackQueue.Empty
            playbackQueueRestored = true
        }
    }

    LaunchedEffect(playbackQueue, playbackQueueRestored) {
        if (playbackQueueRestored) localRepository.savePlaybackQueue(playbackQueue)
    }

    fun updatePlaybackQueue(queue: PlayerPlaybackQueue) {
        playbackQueueRestored = true
        playbackQueue = queue
    }

    // A link shared to Stashfeed opens Downloads, which takes it from the inbox.
    val sharedLinks by StashShareInbox.pending.collectAsState()
    LaunchedEffect(sharedLinks, savedProfile) {
        if (sharedLinks.isNotEmpty() && savedProfile != null && currentRoute != DownloadsRoutePath) {
            navController.navigate(DownloadsRoutePath) { launchSingleTop = true }
        }
    }

    LaunchedEffect(savedProfile, currentRoute) {
        if (shouldRedirectSetupWithSavedProfile(savedProfile != null, currentRoute)) {
            navController.navigate(TopLevelDestination.Home.route) {
                popUpTo(SetupRoute) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(savedProfile) {
        val profile = savedProfile ?: return@LaunchedEffect
        if (!shouldRefreshPasswordSessionOnStartup(profile)) return@LaunchedEffect
        val refreshKey = passwordSessionStartupRefreshKey(profile)
        if (refreshedPasswordSessionKey == refreshKey) return@LaunchedEffect
        refreshedPasswordSessionKey = refreshKey
        runCatching {
            StashLoginClient().loginWithPassword(
                baseUrl = profile.baseUrl,
                username = profile.username,
                password = profile.password,
            )
        }.onSuccess { login ->
            settingsRepository.saveServerProfile(profile.copy(sessionCookie = login.sessionCookie))
        }.onFailure { throwable ->
            StashDebugLogBuffer.record("Navigation", "Password session refresh failed", throwable)
        }
    }

    LaunchedEffect(Unit) {
        runCatching { updateChecker.checkForUpdate(currentAppVersionName(context)) }
            .onSuccess { notice ->
                if (notice != null) {
                    availableUpdateNotice = notice
                    val result = snackbarHostState.showSnackbar(
                        message = context.getString(
                            R.string.settings_support_update_available_snackbar,
                            notice.latestVersionName,
                        ),
                        actionLabel = context.getString(R.string.settings_support_update_changelog_action),
                        withDismissAction = true,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        updateChangelogNotice = notice
                    }
                }
            }
            .onFailure { throwable ->
                StashDebugLogBuffer.record("Navigation", "App update check failed", throwable)
            }
    }

    fun showPlayer(sceneId: String) {
        activeFlight = thumbnailFlights.take(sceneId)
        activePlayerSceneId = sceneId
        playerMinimized = false
    }

    fun openPlaybackQueueScene(
        sceneId: String,
        scenes: List<SceneCardModel>,
        randomShuffle: Boolean = false,
        continuation: PlayerPlaybackQueueContinuation? = null,
        resumeExisting: Boolean = false,
    ) {
        val nextQueue = if (resumeExisting) {
            handOffHomeResumePlaybackQueue(playbackQueue, scenes, sceneId)
        } else {
            handOffLoadedResultPlaybackQueue(
                currentQueue = playbackQueue,
                scenes = scenes,
                selectedSceneId = sceneId,
                randomShuffle = shouldApplyPlayerShuffle(randomShuffle, continuation),
            )
        }
        updatePlaybackQueue(nextQueue)
        val presentation = resolvePlayerPresentationLaunchStateForOpenedScene(
            openedFromActivePlayer = playerExpanded,
            currentMode = playerPresentationMode,
            currentLandscapeAutoFullscreen = playerLandscapeAutoFullscreen,
        )
        playerPresentationMode = presentation.mode
        playerLandscapeAutoFullscreen = presentation.landscapeAutoFullscreen
        playbackQueueContinuation = continuation
        showPlayer(sceneId)
    }

    suspend fun ensurePlaylistTrailingItems(sceneId: String, minimumTrailingCount: Int) {
        val activeProfile = runtimeProfile ?: return
        var continuation = playbackQueueContinuation ?: return
        var queue = playbackQueue.withCurrent(sceneId)
        if (
            !shouldLoadMorePlayerPlaylistItems(
                queue = queue,
                currentSceneId = sceneId,
                minimumTrailingCount = minimumTrailingCount,
                hasMore = continuation.hasMore,
            )
        ) {
            return
        }

        val client = StashGraphQlClient(activeProfile)
        while (
            shouldLoadMorePlayerPlaylistItems(
                queue = queue,
                currentSceneId = sceneId,
                minimumTrailingCount = minimumTrailingCount,
                hasMore = continuation.hasMore,
            )
        ) {
            val pageToLoad = continuation.nextPage
            val result = runCatching {
                when (val activeContinuation = continuation) {
                    is PlayerPlaybackQueueContinuation.Browse -> client.findSceneCardsPage(
                        perPage = activeContinuation.pageSize,
                        page = pageToLoad,
                        sort = activeContinuation.sort,
                        direction = activeContinuation.direction,
                        videoFilter = activeContinuation.videoFilter,
                    )
                    is PlayerPlaybackQueueContinuation.Explore -> client.findSceneCardsPage(
                        perPage = activeContinuation.pageSize,
                        page = pageToLoad,
                        query = activeContinuation.query,
                        sort = activeContinuation.sort,
                        direction = activeContinuation.direction,
                        videoFilter = activeContinuation.videoFilter,
                    )
                }
            }.getOrNull() ?: break
            val scenes = result.scenes.applyLocalFavoriteFilter(
                continuation.videoFilterState().localFavoriteOnly,
                favoriteSceneIds,
            )
            continuation = continuation.afterLoadedPage(pageToLoad, result.totalCount)
            queue = appendLoadedResultPlaybackQueue(queue, scenes)
            updatePlaybackQueue(queue)
            playbackQueueContinuation = continuation
        }
    }

    fun navigateTopLevel(destination: TopLevelDestination) {
        val statePolicy = resolveTopLevelNavigationStatePolicy(destination.route)
        navController.navigate(destination.route) {
            popUpTo(navController.graph.startDestinationId) {
                saveState = statePolicy.saveState
            }
            launchSingleTop = true
            restoreState = statePolicy.restoreState
        }
    }

    fun navigateSecondary(route: String) {
        navController.navigate(route) { launchSingleTop = true }
    }

    fun replaceCurrentPlayerScene(sceneId: String) {
        val presentation = resolvePlayerPresentationLaunchStateForOpenedScene(
            openedFromActivePlayer = true,
            currentMode = playerPresentationMode,
            currentLandscapeAutoFullscreen = playerLandscapeAutoFullscreen,
        )
        playerPresentationMode = presentation.mode
        playerLandscapeAutoFullscreen = presentation.landscapeAutoFullscreen
        activePlayerSceneId = sceneId
    }

    /** Leaving the player shrinks it into the mini player; the × there closes it. */
    fun exitPlayer() {
        playerMinimized = true
    }

    fun closePlayer() {
        activePlayerSceneId = null
        playerMinimized = false
    }

    StashUiScaleProvider(uiScale = activeUiScale) {
    val avatarTarget = remember(runtimeProfile?.name, availableUpdateNotice) {
        StashAvatarTarget(
            initial = runtimeProfile?.name?.trim()?.firstOrNull()?.uppercase() ?: "S",
            showBadge = availableUpdateNotice != null,
            onClick = { navigateSecondary(TopLevelDestination.Library.route) },
        )
    }
    CompositionLocalProvider(LocalStashAvatarTarget provides avatarTarget, LocalStashPlayerExpanded provides playerExpanded, LocalStashPlayerActive provides (activePlayerSceneId != null), LocalStashThumbnailFlights provides thumbnailFlights) {
        updateChangelogNotice?.let { notice ->
            AppUpdateChangelogDialog(
                notice = notice,
                onOpenRelease = {
                    updateChangelogNotice = null
                    openExternalUrl(context, notice.releaseUrl)
                },
                onDismiss = { updateChangelogNotice = null },
            )
        }
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    if (shouldShowBottomNavigation(chromeRoute, isFoldLikeLayout)) {
                        val styleTokens = LocalStashStyleTokens.current
                        val navigationDividerColor = if (styleTokens.isSocial) {
                            MaterialTheme.colorScheme.outlineVariant
                        } else {
                            Color.Transparent
                        }
                        val navigationContainerColor = if (styleTokens.isSocial) {
                            MaterialTheme.colorScheme.background
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        }
                        NavigationBar(
                            modifier = Modifier
                                .drawBehind {
                                    drawLine(
                                        color = navigationDividerColor,
                                        start = Offset.Zero,
                                        end = Offset(size.width, 0f),
                                        strokeWidth = 1.dp.toPx(),
                                    )
                                },
                            containerColor = navigationContainerColor,
                            tonalElevation = 0.dp,
                            windowInsets = NavigationBarDefaults.windowInsets,
                        ) {
                            topLevelDestinations.forEach { destination ->
                                val destinationLabel = stringResource(destination.labelRes)
                                val selected = isTopLevelDestinationSelected(currentRoute, currentDestination?.hierarchy?.map { it.route }, destination)
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        navigateTopLevel(destination)
                                    },
                                    icon = {
                                        TopLevelDestinationIcon(
                                            destination = destination,
                                            label = destinationLabel,
                                            showBadge = destination == TopLevelDestination.Library &&
                                                availableUpdateNotice != null,
                                            selected = selected,
                                        )
                                    },
                                    colors = stashNavigationBarItemColors(),
                                    label = {
                                        Text(
                                            destinationLabel,
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                        )
                                    },
                                )
                            }
                        }
                    }
                },
            ) { paddingValues ->
            androidx.compose.foundation.layout.Row {
                val navHostModifier = if (shouldApplyScaffoldChromePadding(chromeRoute)) {
                    Modifier.weight(1f).padding(paddingValues)
                } else {
                    Modifier.weight(1f)
                }
                if (shouldShowNavigationRail(chromeRoute, isFoldLikeLayout)) {
                    NavigationRail(
                        containerColor = if (LocalStashStyleTokens.current.isSocial) {
                            MaterialTheme.colorScheme.background
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        },
                    ) {
                        topLevelDestinations.forEach { destination ->
                            val destinationLabel = stringResource(destination.labelRes)
                            val selected = isTopLevelDestinationSelected(currentRoute, currentDestination?.hierarchy?.map { it.route }, destination)
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    navigateTopLevel(destination)
                                },
                                icon = {
                                    TopLevelDestinationIcon(
                                        destination = destination,
                                        label = destinationLabel,
                                        showBadge = destination == TopLevelDestination.Library &&
                                            availableUpdateNotice != null,
                                        selected = selected,
                                    )
                                },
                                colors = stashNavigationRailItemColors(),
                                label = { Text(destinationLabel) },
                            )
                        }
                    }
                }

                SharedTransitionLayout(modifier = navHostModifier) {
                CompositionLocalProvider(LocalStashSharedTransitionScope provides this) {
                NavHost(
                    navController = navController,
                    startDestination = resolveStashStartDestination(savedProfile != null),
                    modifier = Modifier.fillMaxSize(),
                    // Tabs cross-fade; pushed screens slide in a little and settle on a spring. The same
                    // transitions drive the predictive back preview.
                    enterTransition = {
                        if (isTabSwitch(initialState.destination.route, targetState.destination.route)) {
                            fadeIn(tween(200))
                        } else {
                            fadeIn(tween(220)) + slideInHorizontally(stashSpring()) { it / 10 }
                        }
                    },
                    exitTransition = { fadeOut(tween(160)) },
                    popEnterTransition = { fadeIn(tween(220)) },
                    popExitTransition = {
                        if (isTabSwitch(initialState.destination.route, targetState.destination.route)) {
                            fadeOut(tween(160))
                        } else {
                            fadeOut(tween(180)) + slideOutHorizontally(stashSpring()) { it / 10 }
                        }
                    },
                ) {
                    composable(SetupRoute) {
                        ServerSetupRoute(
                            isFoldLikeLayout = isFoldLikeLayout,
                            onContinue = {
                                navController.navigate(TopLevelDestination.Home.route) {
                                    popUpTo(SetupRoute) { inclusive = true }
                                }
                            },
                        )
                    }
                    composable(SetupResetRoute) {
                        ServerSetupRoute(
                            isFoldLikeLayout = isFoldLikeLayout,
                            onContinue = {
                                navController.navigate(TopLevelDestination.Home.route) {
                                    popUpTo(SetupResetRoute) { inclusive = true }
                                }
                            },
                        )
                    }
                    composable(TopLevelDestination.Home.route) {
                        HomeFeedRoute(
                            isFoldLikeLayout = isFoldLikeLayout,
                            onOpenLightbox = { request -> activeFeedLightbox = request },
                            onOpenScene = { sceneId, scenes -> openPlaybackQueueScene(sceneId = sceneId, scenes = scenes) },
                            onOpenGallery = { galleryId -> navController.navigate(galleryDetailRouteForGallery(galleryId)) },
                            onOpenPerformer = { performerId ->
                                navController.navigate(performerProfileRouteForPerformer(performerId))
                            },
                        )
                    }
                    // The original dashboard-style home stays reachable for its resume / queue shortcuts.
                    composable(LegacyHomeRoutePath) {
                        HomeRoute(
                            isFoldLikeLayout = isFoldLikeLayout,
                            onOpenScene = { sceneId, scenes, randomShuffle ->
                                openPlaybackQueueScene(
                                    sceneId = sceneId,
                                    scenes = scenes,
                                    randomShuffle = randomShuffle,
                                )
                            },
                            onResumeScene = { sceneId, scenes ->
                                openPlaybackQueueScene(
                                    sceneId = sceneId,
                                    scenes = scenes,
                                    resumeExisting = true,
                                )
                            },
                            onOpenSettings = { navigateSecondary(SettingsDestinations.Root) },
                            onOpenQueue = { navigateSecondary(QueueRoutePath) },
                            onOpenFavorites = { navigateSecondary(QueueRoutePath) },
                            onOpenExplore = { navigateTopLevel(TopLevelDestination.Explore) },
                            onOpenShorts = { navigateTopLevel(TopLevelDestination.ForYou) },
                        )
                    }
                    composable(TopLevelDestination.Explore.route) {
                        var segment by rememberSaveable { mutableStateOf(ExploreSegment.Videos) }
                        val segmentTabs: @Composable () -> Unit = {
                            ExploreSegmentRow(
                                selected = segment,
                                onSelect = { segment = it },
                                isFoldLikeLayout = isFoldLikeLayout,
                            )
                        }
                        Box(modifier = Modifier.fillMaxSize()) {
                              CompositionLocalProvider(LocalStashBrowseTabs provides segmentTabs) {
                                when (segment) {
                                    ExploreSegment.Videos -> ExploreRoute(
                                        isFoldLikeLayout = isFoldLikeLayout,
                                        onOpenScene = { sceneId, scenes, randomShuffle, continuation ->
                                            openPlaybackQueueScene(
                                                sceneId = sceneId,
                                                scenes = scenes,
                                                randomShuffle = randomShuffle,
                                                continuation = continuation,
                                            )
                                        },
                                        onOpenSettings = { navigateSecondary(SettingsDestinations.Root) },
                                    )
                                    ExploreSegment.Photos,
                                    ExploreSegment.Galleries,
                                    -> GalleryRoute(
                                        isFoldLikeLayout = isFoldLikeLayout,
                                        onOpenGallery = { galleryId ->
                                            navController.navigate(galleryDetailRouteForGallery(galleryId))
                                        },
                                        onOpenPhoto = { index, images ->
                                            activeGalleryPhotoViewer = ActiveGalleryPhotoViewer(
                                                initialIndex = index,
                                                images = images,
                                            )
                                        },
                                        onOpenSettings = { navigateSecondary(SettingsDestinations.Root) },
                                        forcedBrowseMode = if (segment == ExploreSegment.Photos) {
                                            StashGalleryBrowseMode.Images
                                        } else {
                                            StashGalleryBrowseMode.Galleries
                                        },
                                    )
                                }
                              }
                        }
                    }
                    composable(TopLevelDestination.ForYou.route) {
                        ShortsRoute(
                            isFoldLikeLayout = isFoldLikeLayout,
                            onOpenPerformer = { performerId ->
                                navController.navigate(performerProfileRouteForPerformer(performerId))
                            },
                            onOpenSceneInPlayer = { sceneId, scenes ->
                                openPlaybackQueueScene(sceneId = sceneId, scenes = scenes)
                            },
                        )
                    }
                    composable(TopLevelDestination.Performers.route) {
                        CompositionLocalProvider(LocalStashNavAnimatedScope provides this) {
                            PerformersRoute(
                                isFoldLikeLayout = isFoldLikeLayout,
                                onOpenPerformer = { performerId ->
                                    navController.navigate(performerProfileRouteForPerformer(performerId))
                                },
                            )
                        }
                    }
                    composable(PerformerProfileRoutePattern) { backStackEntry ->
                      CompositionLocalProvider(LocalStashNavAnimatedScope provides this) {
                        PerformerProfileRoute(
                            performerId = backStackEntry.arguments?.getString("performerId").orEmpty(),
                            isFoldLikeLayout = isFoldLikeLayout,
                            onOpenLightbox = { request -> activeFeedLightbox = request },
                            onOpenPerformer = { id -> navController.navigate(performerProfileRouteForPerformer(id)) },
                            onNavigateBack = {
                                if (!navController.popBackStack()) navigateTopLevel(TopLevelDestination.Performers)
                            },
                            onOpenScene = { sceneId, scenes, shuffle ->
                                openPlaybackQueueScene(sceneId = sceneId, scenes = scenes, randomShuffle = shuffle)
                            },
                            onOpenGallery = { galleryId ->
                                navController.navigate(galleryDetailRouteForGallery(galleryId))
                            },
                            onOpenPhoto = { index, images ->
                                activeGalleryPhotoViewer = ActiveGalleryPhotoViewer(
                                    initialIndex = index,
                                    images = images,
                                )
                            },
                        )
                      }
                    }
                    composable(TopLevelDestination.Library.route) {
                        LibraryRoute(
                            isFoldLikeLayout = isFoldLikeLayout,
                            hasAvailableUpdate = availableUpdateNotice != null,
                            onOpenQueue = { navigateSecondary(QueueRoutePath) },
                            onOpenSettings = { navigateSecondary(SettingsDestinations.Root) },
                            onOpenDashboard = { navigateSecondary(LegacyHomeRoutePath) },
                            onBack = { if (!navController.popBackStack()) navigateTopLevel(TopLevelDestination.Home) },
                            onOpenSettingsSection = { route -> navigateSecondary(route) },
                            onOpenDownloads = { navigateSecondary(DownloadsRoutePath) },
                        )
                    }
                    composable(DownloadsRoutePath) {
                        DownloadsRoute(
                            isFoldLikeLayout = isFoldLikeLayout,
                            onBack = { if (!navController.popBackStack()) navigateTopLevel(TopLevelDestination.Home) },
                            onOpenScene = ::showPlayer,
                        )
                    }
                    composable(GalleryRoutePath) {
                        GalleryRoute(
                            isFoldLikeLayout = isFoldLikeLayout,
                            onOpenGallery = { galleryId ->
                                navController.navigate(galleryDetailRouteForGallery(galleryId))
                            },
                            onOpenPhoto = { index, images ->
                                activeGalleryPhotoViewer = ActiveGalleryPhotoViewer(
                                    initialIndex = index,
                                    images = images,
                                )
                            },
                            onOpenSettings = { navigateSecondary(SettingsDestinations.Root) },
                        )
                    }
                    composable("gallery/{galleryId}") { backStackEntry ->
                        GalleryDetailRoute(
                            galleryId = backStackEntry.arguments?.getString("galleryId") ?: "",
                            isFoldLikeLayout = isFoldLikeLayout,
                            onNavigateBack = {
                                if (!navController.popBackStack()) {
                                    navigateTopLevel(TopLevelDestination.Explore)
                                }
                            },
                            onOpenPhoto = { _, index, images ->
                                activeGalleryPhotoViewer = ActiveGalleryPhotoViewer(
                                    initialIndex = index,
                                    images = images,
                                )
                            },
                            onOpenScene = { sceneId ->
                                playerPresentationMode = PlayerPresentationMode.WatchPage
                                playerLandscapeAutoFullscreen = false
                                showPlayer(sceneId)
                            },
                            onOpenGallery = { linkedGalleryId -> navController.navigate(galleryDetailRouteForGallery(linkedGalleryId)) },
                            onOpenSettings = { navigateSecondary(SettingsDestinations.Root) },
                        )
                    }
                    composable(QueueRoutePath) {
                        QueueRoute(
                            isFoldLikeLayout = isFoldLikeLayout,
                            currentSceneId = playbackQueue.currentSceneId,
                            onOpenScene = { sceneId, scenes, randomShuffle ->
                                openPlaybackQueueScene(
                                    sceneId = sceneId,
                                    scenes = scenes,
                                    randomShuffle = randomShuffle,
                                )
                            },
                            onOpenBrowse = { navigateTopLevel(TopLevelDestination.Explore) },
                            onOpenExplore = { navigateTopLevel(TopLevelDestination.Explore) },
                            onNavigateBack = { if (!navController.popBackStack()) navigateTopLevel(TopLevelDestination.Home) },
                        )
                    }
                    composable(SettingsDestinations.Root) {
                        SettingsRoute(
                            isFoldLikeLayout = isFoldLikeLayout,
                            hasAvailableUpdate = availableUpdateNotice != null,
                            onOpenSection = { route -> navController.navigate(route) },
                            onNavigateBack = { if (!navController.popBackStack()) navigateTopLevel(TopLevelDestination.Home) },
                        )
                    }
                    SettingsSection.entries.forEach { section ->
                        composable(section.route) {
                            SettingsDetailRoute(
                                section = section,
                                isFoldLikeLayout = isFoldLikeLayout,
                                availableUpdateNotice = availableUpdateNotice,
                                onUpdateNoticeChange = { availableUpdateNotice = it },
                                onNavigateBack = {
                                    if (!navController.popBackStack()) {
                                        navController.navigate(SettingsDestinations.Root) {
                                            launchSingleTop = true
                                        }
                                    }
                                },
                                onOpenOnboarding = { navController.navigate(SetupResetRoute) },
                            )
                        }
                    }
                }
                }
                }
            }
        }
            activePlayerSceneId?.let { sceneId ->
                // A fresh player per scene, as when it was its own screen.
                key(sceneId) {
                    val playerAlpha = remember { Animatable(if (activeFlight != null) 0f else 1f) }
                    LaunchedEffect(Unit) {
                        if (playerAlpha.value < 1f) {
                            delay(200)
                            playerAlpha.animateTo(1f, tween(280))
                        }
                    }
                    Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (playerMinimized) 1f else playerAlpha.value }) {
                    PlayerRoute(
                        sceneId = sceneId,
                        isFoldLikeLayout = isFoldLikeLayout,
                        playbackQueue = playbackQueue,
                        initialPresentationMode = playerPresentationMode,
                        initialLandscapeAutoFullscreen = playerLandscapeAutoFullscreen,
                        initialPlaybackSpeed = playerPlaybackSpeed,
                        onPlaybackQueueChange = ::updatePlaybackQueue,
                        onPresentationStateChange = { mode, landscapeAutoFullscreen ->
                            playerPresentationMode = mode
                            playerLandscapeAutoFullscreen = landscapeAutoFullscreen
                        },
                        onPlaybackSpeedChange = { playerPlaybackSpeed = it },
                        onOpenScene = { nextSceneId -> replaceCurrentPlayerScene(nextSceneId) },
                        onOpenSettings = {
                            playerMinimized = true
                            navController.navigate(SettingsDestinations.Root)
                        },
                        onExitPlayer = ::exitPlayer,
                        onPlaylistDrawerOpen = ::ensurePlaylistTrailingItems,
                        minimized = playerMinimized,
                        onExpand = { playerMinimized = false },
                        onClosePlayer = ::closePlayer,
                        miniPlayerBottomPadding = if (shouldShowBottomNavigation(currentRoute, isFoldLikeLayout)) {
                            MiniPlayerAboveNavigationBar
                        } else {
                            0.dp
                        } + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                        pauseSignal = playerPauseSignal,
                    )
                    }
                }
            }
            activeFlight?.let { flight ->
                val density = LocalDensity.current
                val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
                val topPx = WindowInsets.statusBars.getTop(density).toFloat()
                StashThumbnailFlightOverlay(
                    flight = flight,
                    // The watch page puts the video full width at the top, 16:9.
                    target = Rect(0f, topPx, screenWidthPx, topPx + screenWidthPx * 9f / 16f),
                    onDone = { activeFlight = null },
                )
            }
            activeFeedLightbox?.let { request ->
                FeedLightbox(
                    request = request,
                    serverProfile = runtimeProfile,
                    onDismiss = { activeFeedLightbox = null },
                )
            }
            activeGalleryPhotoViewer?.let { viewer ->
                GalleryPhotoViewerOverlay(
                    images = viewer.images,
                    initialIndex = viewer.initialIndex,
                    serverProfile = runtimeProfile,
                    onDismiss = { activeGalleryPhotoViewer = null },
                    modifier = Modifier.fillMaxSize(),
                    onOpenLinkedGallery = { galleryId ->
                        activeGalleryPhotoViewer = null
                        navController.navigate(galleryDetailRouteForGallery(galleryId))
                    },
                )
            }
        }
    }
    }
}

@Composable
private fun AppUpdateChangelogDialog(
    notice: AppUpdateNotice,
    onOpenRelease: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    R.string.settings_support_update_changelog_title,
                    notice.latestVersionName,
                ),
            )
        },
        text = {
            Text(
                text = notice.changelog?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.settings_support_update_changelog_empty),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            TextButton(onClick = onOpenRelease) {
                Text(stringResource(R.string.settings_support_update_open_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_support_update_changelog_close))
            }
        },
    )
}

@Composable
private fun AppOrientationEffect(
    activity: Activity?,
    request: AppOrientationRequest,
) {
    DisposableEffect(activity, request) {
        val previousOrientation = activity?.requestedOrientation
        if (activity != null) {
            activity.requestedOrientation = request.toActivityInfoOrientation()
        }
        onDispose {
            if (activity != null && previousOrientation != null) {
                activity.requestedOrientation = previousOrientation
            }
        }
    }
}

private fun AppOrientationRequest.toActivityInfoOrientation(): Int = when (this) {
    AppOrientationRequest.Portrait -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    AppOrientationRequest.Landscape -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    AppOrientationRequest.Sensor -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
    AppOrientationRequest.Unspecified -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun openExternalUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }.onFailure { throwable ->
        StashDebugLogBuffer.record("Navigation", "External URL open failed", throwable)
    }
}

private fun currentAppVersionName(context: Context): String {
    return runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }.getOrDefault("")
}

private fun isTopLevelDestinationSelected(
    currentRoute: String?,
    hierarchyRoutes: Sequence<String?>?,
    destination: TopLevelDestination,
): Boolean = when (destination) {
    TopLevelDestination.Library -> currentRoute == destination.route ||
        currentRoute == QueueRoutePath ||
        currentRoute == LegacyHomeRoutePath ||
        SettingsDestinations.isSettingsRoute(currentRoute)
    TopLevelDestination.Explore -> hierarchyRoutes?.any { it == destination.route } == true ||
        currentRoute == GalleryRoutePath ||
        currentRoute?.startsWith("$GalleryRoutePath/") == true
    TopLevelDestination.Performers -> currentRoute == destination.route || currentRoute == PerformerProfileRoutePattern
    else -> hierarchyRoutes?.any { it == destination.route } == true
}

private enum class ExploreSegment(val labelRes: Int) {
    Videos(R.string.explore_segment_videos),
    Photos(R.string.explore_segment_photos),
    Galleries(R.string.explore_segment_galleries),
}

@Composable
private fun ExploreSegmentRow(
    selected: ExploreSegment,
    onSelect: (ExploreSegment) -> Unit,
    isFoldLikeLayout: Boolean,
) {
    StashSegmentedTabs(
        options = ExploreSegment.entries,
        selected = selected,
        label = { stringResource(it.labelRes) },
        onSelect = onSelect,
        contentPadding = if (isFoldLikeLayout) StashSpacing.ScreenHorizontalExpanded else StashSpacing.ScreenHorizontalCompact,
        modifier = Modifier.padding(top = StashSpace.S),
    )
}

private fun PlayerPlaybackQueueContinuation.videoFilterState() = when (this) {
    is PlayerPlaybackQueueContinuation.Browse -> videoFilter
    is PlayerPlaybackQueueContinuation.Explore -> videoFilter
}

@Composable
private fun StashNavigationColorRole.toNavigationChromeColor(): Color = when (this) {
    StashNavigationColorRole.Surface -> MaterialTheme.colorScheme.surface
    StashNavigationColorRole.OnSurfaceVariant -> MaterialTheme.colorScheme.onSurfaceVariant
    StashNavigationColorRole.Primary -> MaterialTheme.colorScheme.primary
    StashNavigationColorRole.PrimaryContainer -> MaterialTheme.colorScheme.primaryContainer
    StashNavigationColorRole.Outline -> MaterialTheme.colorScheme.outline
}

@Composable
private fun StashNavigationChromeVisualPolicy.navigationBarItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = selectedIconRole.toNavigationChromeColor().copy(alpha = selectedContentAlpha),
    selectedTextColor = selectedTextRole.toNavigationChromeColor().copy(alpha = selectedContentAlpha),
    indicatorColor = selectedIndicatorRole.toNavigationChromeColor().copy(alpha = indicatorAlpha),
    unselectedIconColor = unselectedIconRole.toNavigationChromeColor().copy(alpha = unselectedContentAlpha),
    unselectedTextColor = unselectedTextRole.toNavigationChromeColor().copy(alpha = unselectedContentAlpha),
    disabledIconColor = unselectedIconRole.toNavigationChromeColor().copy(alpha = disabledContentAlpha),
    disabledTextColor = unselectedTextRole.toNavigationChromeColor().copy(alpha = disabledContentAlpha),
)

@Composable
private fun StashNavigationChromeVisualPolicy.navigationRailItemColors() = NavigationRailItemDefaults.colors(
    selectedIconColor = selectedIconRole.toNavigationChromeColor().copy(alpha = selectedContentAlpha),
    selectedTextColor = selectedTextRole.toNavigationChromeColor().copy(alpha = selectedContentAlpha),
    indicatorColor = selectedIndicatorRole.toNavigationChromeColor().copy(alpha = indicatorAlpha),
    unselectedIconColor = unselectedIconRole.toNavigationChromeColor().copy(alpha = unselectedContentAlpha),
    unselectedTextColor = unselectedTextRole.toNavigationChromeColor().copy(alpha = unselectedContentAlpha),
    disabledIconColor = unselectedIconRole.toNavigationChromeColor().copy(alpha = disabledContentAlpha),
    disabledTextColor = unselectedTextRole.toNavigationChromeColor().copy(alpha = disabledContentAlpha),
)
