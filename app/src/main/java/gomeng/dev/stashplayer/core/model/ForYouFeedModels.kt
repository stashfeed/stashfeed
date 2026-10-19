package gomeng.dev.stashplayer.core.model

import androidx.annotation.StringRes
import gomeng.dev.stashplayer.R

enum class ForYouMode(@StringRes val labelRes: Int) {
    /** Random order, ranked by the on-device like / skip signals (the former Shorts feed). */
    Random(R.string.for_you_mode_random),
    /** Session-adaptive chain of related scenes, see [ForYouChainedFeed]. */
    Chained(R.string.for_you_mode_chained),
}

/** What the For You reel shows. Tags match any of the selected ones, like the Binge plugin. */
data class ForYouFeedConfig(
    val mode: ForYouMode = ForYouMode.Random,
    val shortsOnly: Boolean = false,
    val performers: List<StashSelectedEntity> = emptyList(),
    val tags: List<StashSelectedEntity> = emptyList(),
    val studios: List<StashSelectedEntity> = emptyList(),
) {
    val activeFilterCount: Int
        get() = performers.size + tags.size + studios.size + if (shortsOnly) 1 else 0

    /** Constraints shared by every query of the reel; [maxShortsDurationSeconds] applies only when [shortsOnly]. */
    fun baseVideoFilter(maxShortsDurationSeconds: Int): StashVideoFilterState = StashVideoFilterState(
        performers = performers,
        studios = studios,
        tags = tags.map { StashSelectedTag(id = it.id, name = it.name) },
        tagsMatchAny = true,
        durationRange = if (shortsOnly) {
            StashDurationRange(maxSeconds = coerceShortsMaxDurationSeconds(maxShortsDurationSeconds))
        } else {
            null
        },
    )
}
