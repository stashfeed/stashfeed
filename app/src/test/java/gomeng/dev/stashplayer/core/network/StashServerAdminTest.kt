package gomeng.dev.stashplayer.core.network

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class StashServerAdminTest {
    @Test
    fun parsesPluginsWithTasksSortedByName() {
        val plugins = parsePluginsResponse(
            """
            {"data":{"plugins":[
              {"id":"reddit-stash-agent","name":"Reddit Stash Agent","version":"0.7.1","description":null,"enabled":true,
               "tasks":[{"name":"Start Reddit Sync","description":"Run the full Reddit sync"},{"name":null}]},
              {"id":"auto","name":"","enabled":false,"tasks":null}
            ]}}
            """,
        )

        assertEquals(listOf("auto", "Reddit Stash Agent"), plugins.map { it.name })
        assertFalse(plugins[0].enabled)
        val reddit = plugins[1]
        assertEquals("0.7.1", reddit.version)
        assertNull(reddit.description)
        assertEquals(listOf(StashPluginTask("Start Reddit Sync", "Run the full Reddit sync")), reddit.tasks)
    }

    @Test
    fun graphQlErrorsBecomeExceptionsWithStashMessage() {
        val error = assertThrows(IllegalStateException::class.java) {
            graphQlData("""{"errors":[{"message":"plugin not found"}],"data":null}""")
        }
        assertEquals("plugin not found", error.message)
    }

    @Test
    fun generateDefaultsFallBackPerField() {
        val options = parseGenerateDefaultsResponse(
            """{"data":{"configuration":{"defaults":{"generate":{"covers":false,"markers":true}}}}}""",
        )
        assertFalse(options.covers)
        assertTrue(options.markers)
        assertTrue(options.sprites)
        assertEquals(StashGenerateOptions(), parseGenerateDefaultsResponse("""{"data":{"configuration":{"defaults":{}}}}"""))
    }

    @Test
    fun imagePreviewsNeedPreviews() {
        val input = buildGenerateInput(StashGenerateOptions(previews = false, imagePreviews = true))
        assertEquals(false, input["imagePreviews"])
    }

    @Test
    fun parsesRunningRedditAgentStatus() {
        val status = parseRedditAgentStatus(
            JSONObject(
                """
                {"running":true,"last_result":"running","last_started_at":"2026-10-25T10:00:00+00:00","last_finished_at":null,
                 "last_error":null,"step":3,"step_count":7,"step_label":"Download media","progress":0.286,
                 "summary":{"posts":12,"files":30},
                 "scheduler":{"enabled":true,"interval_hours":6.0,"next_run_at":"2026-10-25T16:00:00+00:00"},
                 "reddit_cookies":{"present":true,"expired":false,"days_left":9,"expires_at":"2026-11-03T00:00:00+00:00"},
                 "version":"0.7.1"}
                """,
            ),
        )

        assertTrue(status.running)
        assertEquals(3, status.step)
        assertEquals(7, status.stepCount)
        assertEquals("Download media", status.stepLabel)
        assertEquals(0.286f, status.progress, 0.0001f)
        assertEquals(mapOf("posts" to "12", "files" to "30"), status.summary)
        assertTrue(status.autoSyncEnabled)
        assertEquals("2026-10-25T16:00:00+00:00", status.nextRunAt)
        assertEquals(9, status.cookies?.daysLeft)
        assertNull(status.lastFinishedAt)
    }

    @Test
    fun parsesIdleRedditAgentStatusWithoutOptionalParts() {
        val status = parseRedditAgentStatus(JSONObject("""{"running":false,"last_result":"failed","last_error":"HTTP 429"}"""))

        assertFalse(status.running)
        assertEquals("failed", status.lastResult)
        assertEquals("HTTP 429", status.lastError)
        assertFalse(status.autoSyncEnabled)
        assertEquals(RedditStashAgent.DEFAULT_INTERVAL_HOURS, status.intervalHours, 0.0)
        assertNull(status.cookies)
    }

    @Test
    fun parsesRedditAgentDownloadJobs() {
        val running = parseRedditAgentDownload(
            JSONObject("""{"id":"abc123def456","url":"https://www.reddit.com/r/x/comments/k1/","status":"running","progress":0.4,"step_label":"Download media","result":null}"""),
        )
        assertEquals("abc123def456", running.id)
        assertFalse(running.finished)
        assertEquals("Download media", running.stepLabel)
        assertEquals(0.4f, running.progress, 0.0001f)

        val done = parseRedditAgentDownload(
            JSONObject(
                """{"id":"j2","status":"success","progress":1.0,"result":{"title":"Two pics","already_downloaded":true,
                   "scene_ids":[],"image_ids":["11","12"],"gallery_id":"5"}}""",
            ),
        )
        assertTrue(done.finished)
        assertTrue(done.alreadyDownloaded)
        assertEquals(listOf("11", "12"), done.imageIds)
        assertEquals("5", done.galleryId)
        assertEquals("Two pics", done.title)
    }
}
