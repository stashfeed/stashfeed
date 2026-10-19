package gomeng.dev.stashplayer.feature.feed

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gomeng.dev.stashplayer.core.model.FeedGallery
import gomeng.dev.stashplayer.core.model.FeedScene
import gomeng.dev.stashplayer.core.model.HomeFeed
import gomeng.dev.stashplayer.core.model.HomeFeedFilter
import gomeng.dev.stashplayer.core.model.HomeFeedSettings
import gomeng.dev.stashplayer.core.model.buildHomeFeed
import gomeng.dev.stashplayer.core.network.HomeFeedWindowField
import gomeng.dev.stashplayer.core.network.StashGraphQlClient
import gomeng.dev.stashplayer.core.network.StashServerProfile
import gomeng.dev.stashplayer.core.network.redactStashCredentialText
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
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

internal class HomeFeedViewModel : ViewModel() {
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
    fun bind(profile: StashServerProfile, newSettings: HomeFeedSettings, force: Boolean = false) {
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
                state = HomeFeedState.Ready(loaded.toFeed())
            }.onFailure { throwable ->
                if (source == null) {
                    state = HomeFeedState.Failed(redactStashCredentialText(throwable.message ?: throwable::class.simpleName.orEmpty()))
                }
            }
        }
    }

    private fun HomeFeedSource.toFeed(): HomeFeed = buildHomeFeed(scenes, galleries, settings, windowStartDate)
}
