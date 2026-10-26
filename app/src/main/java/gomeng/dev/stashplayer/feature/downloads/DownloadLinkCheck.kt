package gomeng.dev.stashplayer.feature.downloads

import gomeng.dev.stashplayer.core.network.DownloaderMetadata
import gomeng.dev.stashplayer.core.network.RedditPostResolver
import gomeng.dev.stashplayer.core.network.StashDownloaderClient
import gomeng.dev.stashplayer.core.network.StashGraphQlClient
import gomeng.dev.stashplayer.core.network.StashLibraryLinks
import gomeng.dev.stashplayer.core.network.StashServerProfile
import gomeng.dev.stashplayer.core.network.cleanSourceUrl
import gomeng.dev.stashplayer.core.network.isDirectImageUrl
import gomeng.dev.stashplayer.core.network.redditPostId

/** Something in the library that already has a link as its source. */
data class LibraryMatch(val sceneId: String? = null, val imageId: String? = null, val galleryId: String? = null)

/** What "Check link" found: details for the form, the number of images (0 for a video) and any library match. */
data class DownloadLinkCheck(val metadata: DownloaderMetadata, val imageCount: Int, val duplicate: LibraryMatch?)

/**
 * Looks at a link before downloading: a Reddit image or gallery post is read on the phone, a direct image link is
 * taken as it is, anything else goes to yt-dlp on the server. Then checks whether the library already has it.
 */
suspend fun checkDownloadLink(profile: StashServerProfile, link: String): DownloadLinkCheck {
    val graph = StashGraphQlClient(profile)
    val links = StashLibraryLinks(graph)
    val source = cleanSourceUrl(link)
    val postLookup = redditPostId(link)?.let { runCatching { RedditPostResolver().imagePost(link) } }
    val post = postLookup?.getOrNull()
    return when {
        post != null -> DownloadLinkCheck(
            metadata = DownloaderMetadata(
                title = post.title,
                uploader = listOfNotNull(post.author?.let { "u/$it" }, post.subreddit?.let { "r/$it" }).joinToString(" · ").ifBlank { null },
                durationSeconds = null,
                height = null,
                thumbnailUrl = post.imageUrls.first(),
                performers = emptyList(),
                tags = emptyList(),
            ),
            imageCount = post.imageUrls.size,
            duplicate = if (post.imageUrls.size > 1) {
                links.findGalleryIdByUrl(source)?.let { LibraryMatch(galleryId = it) }
            } else {
                links.findImageIdByUrl(source)?.let { LibraryMatch(imageId = it) }
            },
        )
        isDirectImageUrl(link) -> DownloadLinkCheck(
            metadata = DownloaderMetadata(
                title = null,
                uploader = null,
                durationSeconds = null,
                height = null,
                thumbnailUrl = link,
                performers = emptyList(),
                tags = emptyList(),
            ),
            imageCount = 1,
            duplicate = links.findImageIdByUrl(source)?.let { LibraryMatch(imageId = it) },
        )
        else -> {
            val downloader = StashDownloaderClient(graph)
            val metadata = try {
                downloader.extractMetadata(link, downloader.settings())
            } catch (failure: Exception) {
                val refusal = postLookup?.exceptionOrNull()?.message ?: throw failure
                error("$refusal. yt-dlp could not read it either: ${failure.message}")
            }
            DownloadLinkCheck(
                metadata = metadata,
                imageCount = 0,
                duplicate = links.findSceneIdByUrl(source)?.let { LibraryMatch(sceneId = it) },
            )
        }
    }
}
