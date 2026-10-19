package gomeng.dev.stashplayer.feature.performers

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class PerformerFavoriteChange(val performerId: String, val favorite: Boolean)

/** Lets the Performers list reflect a follow/unfollow made on a profile screen without a full reload. */
object PerformerFavoriteChanges {
    private val changes = MutableSharedFlow<PerformerFavoriteChange>(extraBufferCapacity = 16)
    val events: SharedFlow<PerformerFavoriteChange> = changes.asSharedFlow()

    fun publish(change: PerformerFavoriteChange) {
        changes.tryEmit(change)
    }
}
