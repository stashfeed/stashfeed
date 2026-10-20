package gomeng.dev.stashplayer.core.model

import androidx.annotation.StringRes
import gomeng.dev.stashplayer.R

data class PerformerCardModel(
    val id: String,
    val name: String,
    val imageUrl: String? = null,
    val favorite: Boolean = false,
    val gender: String? = null,
    val sceneCount: Int = 0,
    val imageCount: Int = 0,
    val galleryCount: Int = 0,
    val rating100: Int? = null,
    val oCounter: Int? = null,
)

data class PerformerDetailModel(
    val card: PerformerCardModel,
    val aliases: List<String> = emptyList(),
    val details: String? = null,
    val birthdate: String? = null,
    val country: String? = null,
    val ethnicity: String? = null,
    val heightCm: Int? = null,
    val hairColor: String? = null,
    val eyeColor: String? = null,
    val tags: List<SceneCardTagChip> = emptyList(),
    val urls: List<String> = emptyList(),
)

data class StashPerformerPage(
    val performers: List<PerformerCardModel>,
    val totalCount: Int,
)

/** Which performers the Performers tab lists. "Following" maps to Stash's favorite flag, like the Binge plugin. */
enum class PerformerListScope(@StringRes val labelRes: Int) {
    Following(R.string.performers_scope_following),
    All(R.string.performers_scope_all),
}

enum class PerformerListSort(
    val graphQlSort: String,
    val direction: StashSortDirection,
    @StringRes val labelRes: Int,
) {
    Name("name", StashSortDirection.Asc, R.string.performers_sort_name),
    MostScenes("scenes_count", StashSortDirection.Desc, R.string.performers_sort_most_scenes),
    HighestRated("rating", StashSortDirection.Desc, R.string.performers_sort_rating),
    RecentlyAdded("created_at", StashSortDirection.Desc, R.string.performers_sort_recently_added),
    Random("random", StashSortDirection.Desc, R.string.performers_sort_random),
}

/** Scene sorts on the profile's Scenes tab — the same set the Binge plugin offers. */
enum class PerformerSceneSort(
    val graphQlSort: String,
    @StringRes val labelRes: Int,
) {
    Recent("date", R.string.performer_scene_sort_recent),
    MostViewed("play_count", R.string.performer_scene_sort_most_viewed),
    MostO("o_counter", R.string.performer_scene_sort_most_o),
    HighestRated("rating", R.string.performer_scene_sort_rating),
    RecentlyAdded("created_at", R.string.performer_scene_sort_recently_added),
}

/** One post in a performer's chronological feed (newest added first). */
sealed interface PerformerFeedItem {
    val key: String
    /** Stash `created_at`, ISO-8601; compared lexicographically. */
    val createdAt: String

    data class Scene(
        val scene: SceneCardModel,
        val date: String?,
        override val createdAt: String,
        /** The same scene with performers and frame size, for rendering it as a Home-style post. */
        val feed: FeedScene? = null,
    ) : PerformerFeedItem {
        override val key: String get() = "scene:${scene.id}"
    }

    data class Gallery(
        val gallery: GalleryCardModel,
        override val createdAt: String,
        val feed: FeedGallery? = null,
    ) : PerformerFeedItem {
        override val key: String get() = "gallery:${gallery.id}"
    }
}

data class PerformerFeedPage<T : PerformerFeedItem>(
    val items: List<T>,
    val totalCount: Int,
)

/**
 * Merges two independently paged, newest-first sources (scenes and galleries) into one newest-first feed.
 *
 * Items are only released while both sources can still prove ordering: once a source's buffer runs dry but
 * the server has more pages, merging stops until that source is fetched again ([sourcesNeedingFetch]).
 */
data class PerformerFeedMergeState(
    val emitted: List<PerformerFeedItem> = emptyList(),
    val sceneBuffer: List<PerformerFeedItem.Scene> = emptyList(),
    val galleryBuffer: List<PerformerFeedItem.Gallery> = emptyList(),
    val nextScenePage: Int = 1,
    val nextGalleryPage: Int = 1,
    val scenesExhausted: Boolean = false,
    val galleriesExhausted: Boolean = false,
) {
    val isComplete: Boolean
        get() = scenesExhausted && galleriesExhausted && sceneBuffer.isEmpty() && galleryBuffer.isEmpty()

    val needsSceneFetch: Boolean get() = sceneBuffer.isEmpty() && !scenesExhausted
    val needsGalleryFetch: Boolean get() = galleryBuffer.isEmpty() && !galleriesExhausted

    fun withScenePage(page: PerformerFeedPage<PerformerFeedItem.Scene>, perPage: Int): PerformerFeedMergeState {
        val loadedSoFar = (nextScenePage - 1) * perPage + page.items.size
        return copy(
            sceneBuffer = sceneBuffer + page.items,
            nextScenePage = nextScenePage + 1,
            scenesExhausted = page.items.isEmpty() || loadedSoFar >= page.totalCount,
        )
    }

    fun withGalleryPage(page: PerformerFeedPage<PerformerFeedItem.Gallery>, perPage: Int): PerformerFeedMergeState {
        val loadedSoFar = (nextGalleryPage - 1) * perPage + page.items.size
        return copy(
            galleryBuffer = galleryBuffer + page.items,
            nextGalleryPage = nextGalleryPage + 1,
            galleriesExhausted = page.items.isEmpty() || loadedSoFar >= page.totalCount,
        )
    }

    /** Moves every item whose position is already certain from the buffers into [emitted]. */
    fun merge(): PerformerFeedMergeState {
        val output = emitted.toMutableList()
        var scenes = sceneBuffer
        var galleries = galleryBuffer
        while (true) {
            val sceneHead = scenes.firstOrNull()
            val galleryHead = galleries.firstOrNull()
            val canDecide = when {
                sceneHead != null && galleryHead != null -> true
                sceneHead != null -> galleriesExhausted
                galleryHead != null -> scenesExhausted
                else -> false
            }
            if (!canDecide) break
            if (galleryHead == null || (sceneHead != null && sceneHead.createdAt >= galleryHead.createdAt)) {
                output += sceneHead!!
                scenes = scenes.drop(1)
            } else {
                output += galleryHead
                galleries = galleries.drop(1)
            }
        }
        return copy(emitted = output, sceneBuffer = scenes, galleryBuffer = galleries)
    }
}
