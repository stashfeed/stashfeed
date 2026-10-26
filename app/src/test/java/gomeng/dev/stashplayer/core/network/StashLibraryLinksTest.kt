package gomeng.dev.stashplayer.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StashLibraryLinksTest {
    @Test
    fun sourceUrlsLoseTrackingButKeepMeaning() {
        assertEquals(
            "https://www.reddit.com/r/pics/comments/abc123/title/",
            cleanSourceUrl("https://www.reddit.com/r/pics/comments/abc123/title/?utm_source=share&utm_medium=android_app"),
        )
        assertEquals("https://example.com/watch?v=42", cleanSourceUrl("https://example.com/watch?v=42&utm_source=x&si=abc#t=10"))
        assertEquals("https://example.com/v", cleanSourceUrl(" https://example.com/v?fbclid=1 "))
    }

    @Test
    fun matchKeyIgnoresSchemeHostVariantsAndTrailingSlash() {
        assertEquals("reddit.com/r/pics/comments/abc123/title", urlMatchKey("https://old.reddit.com/r/pics/comments/abc123/title/?context=3"))
        assertEquals(urlMatchKey("http://www.example.com/v/1/"), urlMatchKey("https://example.com/v/1"))
    }

    @Test
    fun redditPostIdsComeFromPostAndGalleryLinksOnly() {
        assertEquals("abc123", redditPostId("https://www.reddit.com/r/pics/comments/abc123/some_title/"))
        assertEquals("abc123", redditPostId("https://www.reddit.com/gallery/abc123"))
        assertEquals("abc123", redditPostId("https://redd.it/abc123"))
        assertNull(redditPostId("https://i.redd.it/xyz.jpg"))
        assertNull(redditPostId("https://example.com/comments/abc123"))
    }

    @Test
    fun shareLinksCountAsRedditPosts() {
        assertTrue(isRedditPostLink("https://www.reddit.com/r/pics/s/OBT41hp2sh"))
        assertNull(redditPostId("https://www.reddit.com/r/pics/s/OBT41hp2sh"))
        assertTrue(isRedditPostLink("https://redd.it/abc123"))
        assertFalse(isRedditPostLink("https://i.redd.it/abc.jpg"))
        assertFalse(isRedditPostLink("https://www.reddit.com/r/pics/"))
    }

    @Test
    fun galleryPostKeepsImageOrder() {
        val post = parseRedditImagePost(
            """[{"data":{"children":[{"data":{"id":"abc123","title":"Two pics","author":"someone","subreddit":"pics",
               "permalink":"/r/pics/comments/abc123/two_pics/","is_gallery":true,
               "gallery_data":{"items":[{"media_id":"m2"},{"media_id":"m1"}]},
               "media_metadata":{"m1":{"s":{"u":"https://i.redd.it/one.jpg"}},"m2":{"s":{"gif":"https://i.redd.it/two.gif"}}}}}]}},{}]""",
        )!!
        assertEquals(listOf("https://i.redd.it/two.gif", "https://i.redd.it/one.jpg"), post.imageUrls)
        assertEquals("someone", post.author)
        assertEquals("https://www.reddit.com/r/pics/comments/abc123/two_pics/", post.permalink)
    }

    @Test
    fun singleImagePostAndVideoPost() {
        val image = parseRedditImagePost(
            """[{"data":{"children":[{"data":{"id":"x1","title":"One","author":"[deleted]","permalink":"/r/a/comments/x1/one/",
               "url_overridden_by_dest":"https://i.redd.it/single.png"}}]}}]""",
        )!!
        assertEquals(listOf("https://i.redd.it/single.png"), image.imageUrls)
        assertNull(image.author)
        assertNull(parseRedditImagePost("""[{"data":{"children":[{"data":{"id":"v1","is_video":true,"url":"https://v.redd.it/v1"}}]}}]"""))
        assertNull(parseRedditImagePost("""[{"data":{"children":[{"data":{"id":"l1","url":"https://example.com/article"}}]}}]"""))
    }

    @Test
    fun directImageLinks() {
        assertTrue(isDirectImageUrl("https://i.redd.it/abc.jpeg"))
        assertTrue(isDirectImageUrl("https://cdn.example/x.webp?width=640"))
        assertFalse(isDirectImageUrl("https://example.com/video.mp4"))
    }
}
