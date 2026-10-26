package gomeng.dev.stashplayer.core.network

import org.json.JSONObject

/**
 * The Reddit Stash Agent plugin (github.com/carport727/reddit-stash-agent). Its plugin ID, task names and operation
 * modes are the plugin's public interface. A sync is started as a task, so it shows up in Stash's job queue with
 * progress, and the status comes from the read-only `status` operation.
 */
object RedditStashAgent {
    const val PLUGIN_ID = "reddit-stash-agent"
    const val TASK_START_SYNC = "Start Reddit Sync"

    const val SETTING_ENABLED = "enabled"
    const val SETTING_INTERVAL_HOURS = "interval_hours"
    const val DEFAULT_INTERVAL_HOURS = 6.0
}

private const val OUTDATED_AGENT_MESSAGE =
    "The Reddit Stash Agent on your server is older than 0.8.0 and can't download single posts. Update the agent container."

/** A single-post download on the agent (0.8.0+), as `download_post` and `download_status` report it. */
data class RedditAgentDownload(
    val id: String,
    /** queued, running, success or failed. */
    val status: String,
    val progress: Float,
    val stepLabel: String?,
    val error: String?,
    val title: String?,
    val alreadyDownloaded: Boolean,
    val sceneIds: List<String>,
    val imageIds: List<String>,
    val galleryId: String?,
) {
    val finished: Boolean get() = status != "queued" && status != "running"
}

data class RedditAgentCookies(
    val present: Boolean,
    val expired: Boolean,
    val daysLeft: Int?,
    val expiresAt: String?,
)

data class RedditAgentStatus(
    val running: Boolean,
    val lastResult: String?,
    val lastStartedAt: String?,
    val lastFinishedAt: String?,
    val lastError: String?,
    val step: Int,
    val stepCount: Int,
    val stepLabel: String?,
    val progress: Float,
    val summary: Map<String, String>,
    val autoSyncEnabled: Boolean,
    val intervalHours: Double,
    val nextRunAt: String?,
    val cookies: RedditAgentCookies?,
    val version: String?,
)

class RedditStashAgentClient(private val admin: StashServerAdminClient) {
    suspend fun status(): RedditAgentStatus = parseRedditAgentStatus(operation("status"))

    /** Queues "Start Reddit Sync": Stash runs it as a job that follows the sync with progress until it ends. */
    suspend fun startSync(): String = admin.runPluginTask(RedditStashAgent.PLUGIN_ID, RedditStashAgent.TASK_START_SYNC)

    suspend fun cancelSync() {
        operation("cancel")
    }

    /** Hands one Reddit post to the agent, which downloads it with its Reddit login and imports it into Stash. */
    suspend fun downloadPost(url: String): RedditAgentDownload = try {
        parseRedditAgentDownload(operation(mapOf("mode" to "download_post", "url" to url, "wait" to false)))
    } catch (failure: IllegalStateException) {
        // The plugin in Stash is new enough, but the agent service behind it doesn't know single downloads yet.
        if ("HTTP 404" in failure.message.orEmpty()) error(OUTDATED_AGENT_MESSAGE) else throw failure
    }

    suspend fun downloadStatus(id: String): RedditAgentDownload =
        parseRedditAgentDownload(operation(mapOf("mode" to "download_status", "id" to id)))

    /** Saves the auto-sync settings in Stash, then pushes them to the agent, which only reads them on "config". */
    suspend fun setAutoSync(enabled: Boolean, intervalHours: Double) {
        val settings = admin.pluginSettings(RedditStashAgent.PLUGIN_ID).toMap()
        settings[RedditStashAgent.SETTING_ENABLED] = enabled
        settings[RedditStashAgent.SETTING_INTERVAL_HOURS] = intervalHours
        admin.configurePlugin(RedditStashAgent.PLUGIN_ID, settings)
        operation("config")
    }

    private suspend fun operation(mode: String): JSONObject = operation(mapOf("mode" to mode))

    private suspend fun operation(args: Map<String, Any?>): JSONObject =
        admin.runPluginOperation(RedditStashAgent.PLUGIN_ID, args) ?: JSONObject()
}

private fun JSONObject.toMap(): MutableMap<String, Any?> =
    keys().asSequence().associateWithTo(mutableMapOf()) { key -> if (isNull(key)) null else get(key) }

internal fun parseRedditAgentStatus(json: JSONObject): RedditAgentStatus {
    val scheduler = json.optJSONObject("scheduler") ?: JSONObject()
    val summary = json.optJSONObject("summary") ?: JSONObject()
    return RedditAgentStatus(
        running = json.optBoolean("running"),
        lastResult = json.optNullableString("last_result"),
        lastStartedAt = json.optNullableString("last_started_at"),
        lastFinishedAt = json.optNullableString("last_finished_at"),
        lastError = json.optNullableString("last_error"),
        step = json.optInt("step"),
        stepCount = json.optInt("step_count"),
        stepLabel = json.optNullableString("step_label"),
        progress = json.optDouble("progress", 0.0).toFloat().coerceIn(0f, 1f),
        summary = summary.keys().asSequence().associateWith { key -> summary.opt(key)?.toString().orEmpty() },
        autoSyncEnabled = scheduler.optBoolean("enabled"),
        intervalHours = scheduler.optDouble("interval_hours", RedditStashAgent.DEFAULT_INTERVAL_HOURS),
        nextRunAt = scheduler.optNullableString("next_run_at"),
        cookies = json.optJSONObject("reddit_cookies")?.let { cookies ->
            RedditAgentCookies(
                present = cookies.optBoolean("present"),
                expired = cookies.optBoolean("expired"),
                daysLeft = if (cookies.isNull("days_left") || !cookies.has("days_left")) null else cookies.optInt("days_left"),
                expiresAt = cookies.optNullableString("expires_at"),
            )
        },
        version = json.optNullableString("version"),
    )
}

internal fun parseRedditAgentDownload(json: JSONObject): RedditAgentDownload {
    val result = json.optJSONObject("result") ?: JSONObject()
    fun ids(key: String): List<String> {
        val array = result.optJSONArray(key) ?: return emptyList()
        return (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
    }
    return RedditAgentDownload(
        id = json.optNullableString("id") ?: error("The agent returned no download job"),
        status = json.optNullableString("status") ?: "queued",
        progress = json.optDouble("progress", 0.0).toFloat().coerceIn(0f, 1f),
        stepLabel = json.optNullableString("step_label"),
        error = json.optNullableString("error"),
        title = result.optNullableString("title"),
        alreadyDownloaded = result.optBoolean("already_downloaded"),
        sceneIds = ids("scene_ids"),
        imageIds = ids("image_ids"),
        galleryId = result.optNullableString("gallery_id"),
    )
}
