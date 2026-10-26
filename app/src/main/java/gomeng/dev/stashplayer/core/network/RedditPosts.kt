package gomeng.dev.stashplayer.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/** A Reddit post that holds images: one, or a gallery of several, in the post's order. */
data class RedditImagePost(
    val id: String,
    val title: String,
    val author: String?,
    val subreddit: String?,
    val permalink: String,
    val imageUrls: List<String>,
)

/**
 * Reads image and gallery posts from Reddit's public JSON, so their images can be downloaded one by one. Video
 * posts and links elsewhere return null: yt-dlp handles those.
 */
class RedditPostResolver(private val http: OkHttpClient = OkHttpClient()) {
    suspend fun imagePost(url: String): RedditImagePost? {
        val id = redditPostId(url) ?: return null
        val json = withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("https://www.reddit.com/comments/$id.json?raw_json=1")
                .header("User-Agent", "android:app.stashfeed:1 (Stashfeed)")
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Reddit refused to show the post without a login (HTTP ${response.code})")
                response.body?.string().orEmpty()
            }
        }
        return parseRedditImagePost(json)
    }
}

private val REDDIT_POST_PATH = Regex("""(?:reddit\.com/(?:r/[^/]+/)?(?:comments|gallery)/|redd\.it/)([a-z0-9]{4,10})""", RegexOption.IGNORE_CASE)

private val REDDIT_SHARE_PATH = Regex("""reddit\.com/r/[^/]+/s/[A-Za-z0-9]+""", RegexOption.IGNORE_CASE)

/**
 * Whether [url] points at a Reddit post: a post, gallery or redd.it link, or a share link ("/r/name/s/code") that
 * only Reddit can turn into the post. The Reddit Stash Agent takes all of them.
 */
fun isRedditPostLink(url: String): Boolean = redditPostId(url) != null || REDDIT_SHARE_PATH.containsMatchIn(url)

/** The post ID in a Reddit post, gallery or redd.it link; null for anything else (also i.redd.it images and share links). */
internal fun redditPostId(url: String): String? {
    if ("i.redd.it" in url || "v.redd.it" in url || "preview.redd.it" in url) return null
    return REDDIT_POST_PATH.find(url)?.groupValues?.get(1)?.lowercase()
}

internal fun parseRedditImagePost(json: String): RedditImagePost? {
    val post = JSONArray(json).optJSONObject(0)?.optJSONObject("data")?.optJSONArray("children")
        ?.optJSONObject(0)?.optJSONObject("data") ?: return null
    if (post.optBoolean("is_video")) return null
    val images = if (post.optBoolean("is_gallery")) galleryImages(post) else listOfNotNull(singleImage(post))
    if (images.isEmpty()) return null
    return RedditImagePost(
        id = post.optString("id"),
        title = post.optNullableString("title") ?: post.optString("id"),
        author = post.optNullableString("author")?.takeIf { it != "[deleted]" },
        subreddit = post.optNullableString("subreddit"),
        permalink = "https://www.reddit.com" + post.optString("permalink").ifBlank { "/comments/${post.optString("id")}/" },
        imageUrls = images,
    )
}

/** Gallery images in the post's order; animated ones as their GIF. */
private fun galleryImages(post: JSONObject): List<String> {
    val items = post.optJSONObject("gallery_data")?.optJSONArray("items") ?: return emptyList()
    val media = post.optJSONObject("media_metadata") ?: return emptyList()
    return (0 until items.length()).mapNotNull { index ->
        val mediaId = items.optJSONObject(index)?.optNullableString("media_id") ?: return@mapNotNull null
        val source = media.optJSONObject(mediaId)?.optJSONObject("s") ?: return@mapNotNull null
        (source.optNullableString("u") ?: source.optNullableString("gif"))?.takeIf { it.startsWith("http") }
    }
}

private val IMAGE_EXTENSION = Regex("""\.(jpe?g|png|gif|webp)(\?|$)""", RegexOption.IGNORE_CASE)

private fun singleImage(post: JSONObject): String? {
    val target = post.optNullableString("url_overridden_by_dest") ?: post.optNullableString("url") ?: return null
    return target.takeIf { IMAGE_EXTENSION.containsMatchIn(it) }
}

/** Whether [url] points straight at an image file, which the plugin downloads without yt-dlp. */
fun isDirectImageUrl(url: String): Boolean = IMAGE_EXTENSION.containsMatchIn(url.substringBefore('#'))
