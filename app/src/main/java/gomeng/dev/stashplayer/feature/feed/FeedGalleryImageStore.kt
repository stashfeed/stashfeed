package gomeng.dev.stashplayer.feature.feed

import androidx.compose.runtime.mutableStateMapOf
import gomeng.dev.stashplayer.core.model.StashSortDirection
import gomeng.dev.stashplayer.core.network.StashGraphQlClient
import gomeng.dev.stashplayer.core.network.StashServerProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Plugin page size for a gallery's photos in the feed. */
private const val FEED_GALLERY_PAGE_SIZE = 60

/** Photos per gallery post, loaded page by page when the post first scrolls into view (Home and profile feeds). */
internal class FeedGalleryImageStore(
    private val scope: CoroutineScope,
    private val profile: () -> StashServerProfile?,
) {
    val images = mutableStateMapOf<String, FeedGalleryImages>()

    operator fun get(galleryId: String): FeedGalleryImages? = images[galleryId]

    fun clear() = images.clear()

    fun ensure(galleryId: String) {
        if (images[galleryId] == null) loadMore(galleryId)
    }

    fun loadMore(galleryId: String) {
        val activeProfile = profile() ?: return
        val current = images[galleryId] ?: FeedGalleryImages()
        if (current.isLoading || !current.hasMore) return
        images[galleryId] = current.copy(isLoading = true)
        scope.launch {
            val result = runCatching {
                StashGraphQlClient(activeProfile).findGalleryImagesPage(
                    galleryId = galleryId,
                    perPage = FEED_GALLERY_PAGE_SIZE,
                    page = current.nextPage,
                    sort = "path",
                    direction = StashSortDirection.Asc,
                )
            }
            val latest = images[galleryId] ?: current
            images[galleryId] = result.fold(
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
}
