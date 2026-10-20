package gomeng.dev.stashplayer.feature.feed

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gomeng.dev.stashplayer.core.model.FeedGallery
import gomeng.dev.stashplayer.core.model.GalleryImageModel
import gomeng.dev.stashplayer.core.model.SceneCardModel
import gomeng.dev.stashplayer.core.model.StashSortDirection
import gomeng.dev.stashplayer.core.model.FeedScene
import gomeng.dev.stashplayer.core.model.HomeFeed
import gomeng.dev.stashplayer.core.model.HomeFeedFilter
import gomeng.dev.stashplayer.core.model.HomeFeedItem
import gomeng.dev.stashplayer.core.model.HomeFeedSettings
import gomeng.dev.stashplayer.core.model.buildHomeFeed
import gomeng.dev.stashplayer.core.model.pruneSeenFeedPosts
import gomeng.dev.stashplayer.core.network.UI_KEY_SEEN_FEED_POSTS
import gomeng.dev.stashplayer.core.network.seenFeedPosts
import gomeng.dev.stashplayer.core.network.HomeFeedWindowField
import gomeng.dev.stashplayer.core.network.StashGraphQlClient
import gomeng.dev.stashplayer.core.network.StashServerProfile
import gomeng.dev.stashplayer.core.network.redactStashCredentialText
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Instant
import java.time.temporal.ChronoUnit

sealed interface HomeFeedState {
    data object Loading : HomeFeedState
    data class Ready(val feed: HomeFeed) : HomeFeedState
    data class Failed(val message: String) : HomeFeedState
}

/** Raw window results; rebuilt into a [HomeFeed] locally when only display settings change. */
private data class HomeFeedSource(
    val scenes: List<FeedScene>,
    val galleries: List<FeedGallery>,
    val windowStartDate: String,
)

/** Plugin page size for a gallery's photos in the feed. */
private const val FEED_GALLERY_PAGE_SIZE = 60

data class FeedGalleryImages(
    val images: List<GalleryImageModel> = emptyList(),
    val nextPage: Int = 1,
    val hasMore: Boolean = true,
    val isLoading: Boolean = false,
)

/** Batch seen-post writes instead of hitting the server for every post scrolled past. */
private const val SEEN_SAVE_DEBOUNCE_MS = 4_000L

internal class HomeFeedViewModel : ViewModel() {
    /** Photos per gallery post, loaded when the post first scrolls into view. */
    val galleryImages = mutableStateMapOf<String, FeedGalleryImages>()
    var actionError by mutableStateOf<String?>(null)

    /** Post keys that were seen before the last load / refresh; only these move below the divider. */
    var seenSnapshot by mutableStateOf<Set<String>>(emptySet())
        private set
    var showSeenPosts by mutableStateOf(false)

    /** Every seen post (key → its added date), mirrored to the server's UI config. */
    private val seenPosts = linkedMapOf<String, String>()
    private var seenLoadedFor: StashServerProfile? = null
    private var seenDirty = false
    private var seenSaveJob: Job? = null

    var state by mutableStateOf<HomeFeedState>(HomeFeedState.Loading)
        private set
    var filter by mutableStateOf(HomeFeedFilter())
    var isFilterOpen by mutableStateOf(false)
    var isSettingsOpen by mutableStateOf(false)
    var openStoryIndex by mutableStateOf<Int?>(null)

    private var source: HomeFeedSource? = null
    private var sourceKey: Any? = null
    private var settings = HomeFeedSettings()
    private var job: Job? = null

    /**
     * Loads the window when the server, window length or filter changed; other settings (genders, hidden
     * kinds, folders) only rebuild the feed from what is already loaded.
     */
    private var profile: StashServerProfile? = null

    fun bind(profile: StashServerProfile, newSettings: HomeFeedSettings, force: Boolean = false) {
        if (profile != this.profile) {
            galleryImages.clear()
            seenPosts.clear()
            seenLoadedFor = null
        }
        this.profile = profile
        settings = newSettings
        val key = listOf(profile, newSettings.lookbackDays, filter)
        if (!force && key == sourceKey) {
            source?.let { state = HomeFeedState.Ready(it.toFeed()) }
            return
        }
        sourceKey = key
        load(profile, newSettings.lookbackDays, filter)
    }

    private fun load(profile: StashServerProfile, lookbackDays: Int, activeFilter: HomeFeedFilter) {
        job?.cancel()
        if (source == null) state = HomeFeedState.Loading
        job = viewModelScope.launch {
            val since = Instant.now().minus(lookbackDays.toLong(), ChronoUnit.DAYS)
            val sinceTimestamp = since.toString()
            val sinceDate = sinceTimestamp.take(10)
            val criteria = activeFilter.toGraphQlCriteria()
            val result = runCatching {
                coroutineScope {
                    val client = StashGraphQlClient(profile)
                    if (seenLoadedFor != profile) {
                        // A failing UI-config read just means nothing counts as seen yet.
                        runCatching { client.findUiConfig().seenFeedPosts() }.onSuccess { stored ->
                            stored.forEach { (key, date) -> seenPosts.putIfAbsent(key, date) }
                            seenLoadedFor = profile
                        }
                    }
                    // A scene counts as new when it was added OR released inside the window, like the plugin.
                    val scenes = listOf(
                        async { client.findHomeFeedScenes(HomeFeedWindowField.CreatedAt, sinceTimestamp, criteria) },
                        async { client.findHomeFeedScenes(HomeFeedWindowField.Date, sinceDate, criteria) },
                    )
                    val galleries = listOf(
                        async { client.findHomeFeedGalleries(HomeFeedWindowField.CreatedAt, sinceTimestamp, criteria) },
                        async { client.findHomeFeedGalleries(HomeFeedWindowField.Date, sinceDate, criteria) },
                    )
                    HomeFeedSource(
                        scenes = scenes.awaitAll().flatten(),
                        galleries = galleries.awaitAll().flatten(),
                        windowStartDate = sinceDate,
                    )
                }
            }
            result.onSuccess { loaded ->
                source = loaded
                seenSnapshot = seenPosts.keys.toSet()
                showSeenPosts = false
                state = HomeFeedState.Ready(loaded.toFeed())
            }.onFailure { throwable ->
                if (source == null) {
                    state = HomeFeedState.Failed(redactStashCredentialText(throwable.message ?: throwable::class.simpleName.orEmpty()))
                }
            }
        }
    }

    private fun HomeFeedSource.toFeed(): HomeFeed = buildHomeFeed(scenes, galleries, settings, windowStartDate)

    /** Every scene in feed order, so the player can continue through the feed. */
    fun playableScenes(): List<SceneCardModel> = (state as? HomeFeedState.Ready)?.feed?.items.orEmpty().flatMap { item ->
        when (item) {
            is HomeFeedItem.Post -> listOf(item.scene.scene)
            is HomeFeedItem.Pack -> item.scenes.map { it.scene }
            is HomeFeedItem.GalleryPost -> emptyList()
        }
    }

    /** Records a post as seen; written to the server a few seconds later, batched with others. */
    fun markSeen(key: String, createdAt: String) {
        if (seenPosts.containsKey(key)) return
        seenPosts[key] = createdAt.take(10)
        seenDirty = true
        seenSaveJob?.cancel()
        seenSaveJob = viewModelScope.launch {
            delay(SEEN_SAVE_DEBOUNCE_MS)
            flushSeen()
        }
    }

    /** Writes pending seen posts now (also called when leaving Home). */
    fun flushSeenNow() {
        if (!seenDirty) return
        seenSaveJob?.cancel()
        seenSaveJob = viewModelScope.launch { flushSeen() }
    }

    private suspend fun flushSeen() {
        val activeProfile = profile ?: return
        if (!seenDirty) return
        val pruned = pruneSeenFeedPosts(seenPosts, LocalDate.now())
        seenPosts.keys.retainAll(pruned.keys)
        seenDirty = false
        runCatching { StashGraphQlClient(activeProfile).setUiSetting(UI_KEY_SEEN_FEED_POSTS, pruned) }
            .onFailure { seenDirty = true }
    }

    fun ensureGalleryImages(galleryId: String) {
        if (galleryImages[galleryId] == null) loadMoreGalleryImages(galleryId)
    }

    fun loadMoreGalleryImages(galleryId: String) {
        val activeProfile = profile ?: return
        val current = galleryImages[galleryId] ?: FeedGalleryImages()
        if (current.isLoading || !current.hasMore) return
        galleryImages[galleryId] = current.copy(isLoading = true)
        viewModelScope.launch {
            val result = runCatching {
                StashGraphQlClient(activeProfile).findGalleryImagesPage(
                    galleryId = galleryId,
                    perPage = FEED_GALLERY_PAGE_SIZE,
                    page = current.nextPage,
                    sort = "path",
                    direction = StashSortDirection.Asc,
                )
            }
            val latest = galleryImages[galleryId] ?: current
            galleryImages[galleryId] = result.fold(
                onSuccess = { page ->
                    val merged = (latest.images + page.images).distinctBy { it.id }
                    latest.copy(
                        images = merged,
                        nextPage = current.nextPage + 1,
                        hasMore = page.images.isNotEmpty() && merged.size < page.totalCount,
                        isLoading = false,
                    )
                },
                // Keep what loaded; a failed page just stops the carousel from growing.
                onFailure = { latest.copy(isLoading = false, hasMore = false) },
            )
        }
    }

    fun rateScene(sceneId: String, rating100: Int?) {
        updateScene(sceneId) { it.copy(rating100 = rating100) }
        runSceneAction { client -> client.updateSceneRating(sceneId, rating100) }
    }

    fun addO(sceneId: String) {
        updateScene(sceneId) { it.copy(oCounter = (it.oCounter ?: 0) + 1) }
        runSceneAction { client -> client.addO(sceneId).also { count -> updateScene(sceneId) { it.copy(oCounter = count) } } }
    }

    fun removeO(sceneId: String) {
        val current = source?.scenes?.firstOrNull { it.scene.id == sceneId }?.scene?.oCounter ?: 0
        if (current <= 0) return
        updateScene(sceneId) { it.copy(oCounter = current - 1) }
        runSceneAction { client -> client.deleteO(sceneId).also { count -> updateScene(sceneId) { it.copy(oCounter = count) } } }
    }

    /** Applies a change to every copy of the scene in the loaded window and rebuilds the feed. */
    private fun updateScene(sceneId: String, transform: (SceneCardModel) -> SceneCardModel) {
        val loaded = source ?: return
        source = loaded.copy(
            scenes = loaded.scenes.map { feedScene ->
                if (feedScene.scene.id == sceneId) feedScene.copy(scene = transform(feedScene.scene)) else feedScene
            },
        )
        source?.let { state = HomeFeedState.Ready(it.toFeed()) }
    }

    private fun runSceneAction(action: suspend (StashGraphQlClient) -> Unit) {
        val activeProfile = profile ?: return
        viewModelScope.launch {
            runCatching { action(StashGraphQlClient(activeProfile)) }.onFailure { throwable ->
                actionError = redactStashCredentialText(throwable.message ?: throwable::class.simpleName.orEmpty())
            }
        }
    }
}
