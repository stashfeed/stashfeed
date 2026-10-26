package gomeng.dev.stashplayer.core.network

import gomeng.dev.stashplayer.feature.downloads.DownloadPhase
import gomeng.dev.stashplayer.feature.downloads.StashDownload
import gomeng.dev.stashplayer.feature.downloads.decodeDownloadHistory
import gomeng.dev.stashplayer.feature.downloads.encodeDownloadHistory
import gomeng.dev.stashplayer.feature.downloads.extractSharedUrl
import gomeng.dev.stashplayer.feature.downloads.extractSharedUrls
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class StashDownloaderTest {
    @Test
    fun metadataCombinesCastAndTagLists() {
        val metadata = parseDownloaderMetadata(
            JSONObject(
                """{"title":"Clip","uploader":null,"channel":"Studio","duration":125.0,"height":1080,
                   "cast":["Ava"],"creators":["Ava","Mia"],"tags":["outdoor"],"categories":["hd"," "]}""",
            ),
        )
        assertEquals("Clip", metadata.title)
        assertEquals("Studio", metadata.uploader)
        assertEquals(125.0, metadata.durationSeconds!!, 0.0)
        assertEquals(1080, metadata.height)
        assertEquals(listOf("Ava", "Mia"), metadata.performers)
        assertEquals(listOf("outdoor", "hd"), metadata.tags)
    }

    @Test
    fun progressIsNullUntilTheFileExists() {
        assertNull(parseDownloaderProgress(JSONObject("""{"success":true,"retrieved":false,"not_found":true}""")))
        val progress = parseDownloaderProgress(
            JSONObject("""{"retrieved":true,"status":"downloading","percentage":42.5,"downloaded_bytes":425,"total_bytes":1000,"speed":2048.0,"eta":7}"""),
        )!!
        assertEquals(42.5f, progress.percentage!!, 0.001f)
        assertEquals(1000L, progress.totalBytes)
        assertEquals(7L, progress.etaSeconds)
    }

    @Test
    fun resultGivesFilePathOrThePluginError() {
        assertNull(parseDownloaderResult(JSONObject("""{"retrieved":false}""")))
        assertEquals("/data/videos/Clip.mp4", parseDownloaderResult(JSONObject("""{"retrieved":true,"success":true,"file_path":"/data/videos/Clip.mp4"}""")))
        val error = assertThrows(IllegalStateException::class.java) {
            parseDownloaderResult(JSONObject("""{"retrieved":true,"success":false,"task_error":"yt-dlp is not installed"}"""))
        }
        assertEquals("yt-dlp is not installed", error.message)
    }

    @Test
    fun videoLibraryIsTheFirstThatTakesVideos() {
        val json = """{"data":{"configuration":{"general":{"stashes":[{"path":"/images","excludeVideo":true},{"path":"/videos","excludeVideo":false,"excludeImage":true}]}}}}"""
        assertEquals("/videos", parseLibraryPath(json, images = false))
        assertEquals("/images", parseLibraryPath(json, images = true))
    }

    @Test
    fun sharedTextYieldsTheFirstLink() {
        assertEquals("https://www.reddit.com/r/x/comments/abc/", extractSharedUrl("Look at this: https://www.reddit.com/r/x/comments/abc/."))
        assertEquals("https://example.com/v?id=1", extractSharedUrl("(https://example.com/v?id=1)"))
        assertNull(extractSharedUrl("no link here"))
    }

    @Test
    fun severalLinksKeepOrderWithoutDuplicates() {
        val text = "https://a.example/1\nhttps://b.example/2, https://a.example/1 and https://c.example/3!"
        assertEquals(listOf("https://a.example/1", "https://b.example/2", "https://c.example/3"), extractSharedUrls(text))
    }

    @Test
    fun historyRoundTripsWithoutProgress() {
        val downloads = listOf(
            StashDownload(
                id = "1", url = "https://a.example/v", title = "Clip", quality = "720p", thumbnailUrl = "https://a.example/t.jpg",
                createdAt = 42L, phase = DownloadPhase.Done, sceneId = "7",
                progress = DownloaderProgress(50f, 1L, 2L, 3.0, 4L),
            ),
            StashDownload(id = "2", url = "https://b.example/v", title = null, quality = "best"),
        )
        val restored = decodeDownloadHistory(
            encodeDownloadHistory(downloads),
        )
        assertEquals(downloads.map { it.copy(progress = null) }, restored)
    }
}
