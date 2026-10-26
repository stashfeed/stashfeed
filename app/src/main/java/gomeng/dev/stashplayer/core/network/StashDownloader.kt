package gomeng.dev.stashplayer.core.network

import org.json.JSONArray
import org.json.JSONObject

/**
 * The Stash Downloader plugin (github.com/Codename-11/Stash-Downloader): its Python backend downloads a URL with
 * yt-dlp into a folder on the server. Like the plugin's own web page, Stashfeed then scans the file and fills in the
 * new scene. The task name, modes and argument names are the plugin's interface.
 */
object StashDownloaderPlugin {
    const val PLUGIN_ID = "stash-downloader"
    const val TASK_DOWNLOAD = "Download Video"
    const val DEFAULT_OUTPUT_DIR = "/data/StashDownloader"

    val QUALITIES = listOf("best", "1080p", "720p", "480p")
}

/** What yt-dlp found on the page, for the preview before downloading. */
data class DownloaderMetadata(
    val title: String?,
    val uploader: String?,
    val durationSeconds: Double?,
    val height: Int?,
    val thumbnailUrl: String?,
    val performers: List<String>,
    val tags: List<String>,
)

/** A running download's progress file, written by the plugin about every half second. */
data class DownloaderProgress(
    val percentage: Float?,
    val downloadedBytes: Long?,
    val totalBytes: Long?,
    val speedBytesPerSecond: Double?,
    val etaSeconds: Long?,
)

/** The plugin settings that change how a download runs. */
data class DownloaderSettings(
    val downloadPath: String?,
    val ytdlpPath: String?,
    val httpProxy: String?,
    val quality: String?,
    val createPerformers: Boolean = false,
    val createTags: Boolean = false,
    val createStudios: Boolean = false,
)

class StashDownloaderClient(
    private val client: StashGraphQlClient,
    private val admin: StashServerAdminClient = StashServerAdminClient(client),
) {
    suspend fun isInstalled(): Boolean = admin.plugins().any { it.id == StashDownloaderPlugin.PLUGIN_ID && it.enabled }

    suspend fun settings(): DownloaderSettings {
        val settings = admin.pluginSettings(StashDownloaderPlugin.PLUGIN_ID)
        return DownloaderSettings(
            downloadPath = settings.optNullableString("serverDownloadPath"),
            ytdlpPath = settings.optNullableString("ytdlpPath"),
            httpProxy = settings.optNullableString("httpProxy"),
            quality = settings.optNullableString("downloadQuality"),
            createPerformers = settings.optBoolean("autoCreatePerformers", false),
            createTags = settings.optBoolean("autoCreateTags", false),
            createStudios = settings.optBoolean("autoCreateStudios", false),
        )
    }

    /**
     * Where downloads go: the plugin's download path, else the first library that takes videos (or images, for
     * [images]), as the plugin does.
     */
    suspend fun outputDir(settings: DownloaderSettings, images: Boolean = false): String =
        settings.downloadPath ?: parseLibraryPath(client.executeRaw(LIBRARIES_QUERY), images) ?: StashDownloaderPlugin.DEFAULT_OUTPUT_DIR

    suspend fun extractMetadata(url: String, settings: DownloaderSettings): DownloaderMetadata =
        parseDownloaderMetadata(operation(mapOf("mode" to "extract_metadata", "url" to url, "proxy" to settings.httpProxy, "ytdlp_path" to settings.ytdlpPath)))

    /** Queues the download on the server; returns the Stash job ID. */
    suspend fun startDownload(
        url: String,
        title: String?,
        quality: String,
        outputDir: String,
        settings: DownloaderSettings,
        resultId: String,
        progressId: String,
    ): String = admin.runPluginTask(
        StashDownloaderPlugin.PLUGIN_ID,
        StashDownloaderPlugin.TASK_DOWNLOAD,
        mapOf(
            "mode" to "download",
            "url" to url,
            "output_dir" to outputDir,
            // Pass no title for images: they keep the name from their URL, a title would get the extension ".mp4".
            "filename" to title?.takeIf { it.isNotBlank() },
            "quality" to quality,
            "result_id" to resultId,
            "progress_id" to progressId,
            "proxy" to settings.httpProxy,
            "ytdlp_path" to settings.ytdlpPath,
        ),
    )

    suspend fun progress(progressId: String): DownloaderProgress? =
        parseDownloaderProgress(readResult("progress-$progressId"))

    /** The finished download's file path; throws with the plugin's message when it failed. Null while not written yet. */
    suspend fun result(resultId: String): String? = parseDownloaderResult(readResult(resultId))

    suspend fun cleanup(resultId: String, progressId: String) {
        runCatching { operation(mapOf("mode" to "cleanup_result", "result_id" to resultId)) }
        runCatching { operation(mapOf("mode" to "cleanup_result", "result_id" to "progress-$progressId")) }
    }

    /** The scene Stash created for [path] after a scan, if it has one yet. */
    suspend fun findSceneIdByPath(path: String): String? = graphQlData(
        client.executeRaw(FIND_SCENE_BY_PATH_QUERY, mapOf("path" to path)),
    ).optJSONObject("findScenes")?.optJSONArray("scenes")?.optJSONObject(0)?.optNullableString("id")

    private suspend fun readResult(resultId: String): JSONObject = operation(mapOf("mode" to "read_result", "result_id" to resultId))

    private suspend fun operation(args: Map<String, Any?>): JSONObject =
        admin.runPluginOperation(StashDownloaderPlugin.PLUGIN_ID, args.filterValues { it != null }) ?: JSONObject()

    private companion object {
        val LIBRARIES_QUERY = """
            query DownloaderLibraries { configuration { general { stashes { path excludeVideo excludeImage } } } }
        """

        val FIND_SCENE_BY_PATH_QUERY = """
            query DownloaderSceneByPath(${'$'}path: String!) {
              findScenes(scene_filter: { path: { value: ${'$'}path, modifier: EQUALS } }, filter: { per_page: 1 }) {
                scenes { id }
              }
            }
        """
    }
}

internal fun parseLibraryPath(json: String, images: Boolean): String? {
    val stashes = graphQlData(json).optJSONObject("configuration")?.optJSONObject("general")?.optJSONArray("stashes") ?: return null
    return (0 until stashes.length())
        .mapNotNull { stashes.optJSONObject(it) }
        .firstOrNull { !it.optBoolean(if (images) "excludeImage" else "excludeVideo", false) }
        ?.optNullableString("path")
}

internal fun parseDownloaderMetadata(json: JSONObject): DownloaderMetadata {
    fun names(key: String): List<String> {
        val array = json.optJSONArray(key) ?: JSONArray()
        return (0 until array.length()).mapNotNull { array.optString(it).trim().takeIf(String::isNotBlank) }
    }
    return DownloaderMetadata(
        title = json.optNullableString("title"),
        uploader = json.optNullableString("uploader") ?: json.optNullableString("channel"),
        durationSeconds = json.optDouble("duration").takeUnless { it.isNaN() },
        height = json.optInt("height").takeIf { it > 0 },
        thumbnailUrl = json.optNullableString("thumbnail"),
        performers = (names("cast") + names("creators")).distinct(),
        tags = (names("tags") + names("categories")).distinct(),
    )
}

internal fun parseDownloaderProgress(json: JSONObject): DownloaderProgress? {
    if (!json.optBoolean("retrieved")) return null
    fun long(key: String) = json.optDouble(key).takeUnless { it.isNaN() }?.toLong()
    return DownloaderProgress(
        percentage = json.optDouble("percentage").takeUnless { it.isNaN() }?.toFloat()?.coerceIn(0f, 100f),
        downloadedBytes = long("downloaded_bytes"),
        totalBytes = long("total_bytes")?.takeIf { it > 0 },
        speedBytesPerSecond = json.optDouble("speed").takeUnless { it.isNaN() || it <= 0.0 },
        etaSeconds = long("eta"),
    )
}

internal fun parseDownloaderResult(json: JSONObject): String? {
    if (!json.optBoolean("retrieved")) return null
    json.optNullableString("task_error")?.let { error(it) }
    return json.optNullableString("file_path") ?: error("Download failed")
}
