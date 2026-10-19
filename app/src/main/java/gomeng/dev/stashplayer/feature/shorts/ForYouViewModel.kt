package gomeng.dev.stashplayer.feature.shorts

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import gomeng.dev.stashplayer.core.model.ForYouChainedFeed
import gomeng.dev.stashplayer.core.model.ForYouFeedConfig
import gomeng.dev.stashplayer.core.model.SceneCardModel
import gomeng.dev.stashplayer.core.model.ShortsFeedState
import gomeng.dev.stashplayer.core.model.nextStashRandomSortSeed

/**
 * Keeps the reel across tab switches and trips into a profile or the player: mode, filter, the loaded
 * scenes with the current position, the random seed and the chain's learned signals.
 */
internal class ForYouViewModel : ViewModel() {
    var config by mutableStateOf(ForYouFeedConfig())
    var isFilterOpen by mutableStateOf(false)
    var ratingTarget by mutableStateOf<SceneCardModel?>(null)

    var seed by mutableIntStateOf(nextStashRandomSortSeed())
        private set
    var feedState by mutableStateOf(ShortsFeedState())

    private var feedKey: Any? = null
    private var chainedFeed: ForYouChainedFeed? = null
    private var chainedFeedKey: Any? = null

    /** Starts a fresh feed (new seed, nothing loaded) when the server, settings or filter changed. */
    fun resetFeedIfChanged(key: Any) {
        if (key == feedKey) return
        feedKey = key
        seed = nextStashRandomSortSeed()
        feedState = ShortsFeedState()
        chainedFeed = null
        chainedFeedKey = null
    }

    fun chainedFeedFor(key: Any, create: () -> ForYouChainedFeed?): ForYouChainedFeed? {
        if (key != chainedFeedKey) {
            chainedFeedKey = key
            chainedFeed = create()
        }
        return chainedFeed
    }
}
