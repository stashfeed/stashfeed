package gomeng.dev.stashplayer.feature.downloads

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** The download list kept on the phone, newest first. */
private const val HISTORY_LIMIT = 100
private const val HISTORY_PREFS = "stashfeed_downloads"
private const val HISTORY_KEY = "history"

/** Stores the download list on the phone so it is still there after the app restarts. Progress is not kept. */
internal class StashDownloadHistory(context: Context) {
    private val prefs = context.getSharedPreferences(HISTORY_PREFS, Context.MODE_PRIVATE)

    fun load(): List<StashDownload> = runCatching { decodeDownloadHistory(prefs.getString(HISTORY_KEY, null)) }.getOrDefault(emptyList())

    @Synchronized
    fun save(downloads: List<StashDownload>) {
        prefs.edit().putString(HISTORY_KEY, encodeDownloadHistory(downloads.take(HISTORY_LIMIT))).apply()
    }
}

internal fun encodeDownloadHistory(downloads: List<StashDownload>): String = JSONArray().apply {
    downloads.forEach { download ->
        put(
            JSONObject()
                .put("id", download.id)
                .put("url", download.url)
                .put("title", download.title)
                .put("quality", download.quality)
                .put("thumbnailUrl", download.thumbnailUrl)
                .put("createdAt", download.createdAt)
                .put("phase", download.phase.name)
                .put("sceneId", download.sceneId)
                .put("error", download.error),
        )
    }
}.toString()

internal fun decodeDownloadHistory(json: String?): List<StashDownload> {
    val array = JSONArray(json ?: return emptyList())
    return (0 until array.length()).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        fun text(key: String) = if (item.isNull(key)) null else item.optString(key).takeIf { it.isNotBlank() }
        StashDownload(
            id = text("id") ?: return@mapNotNull null,
            url = text("url") ?: return@mapNotNull null,
            title = text("title"),
            quality = text("quality") ?: "best",
            thumbnailUrl = text("thumbnailUrl"),
            createdAt = item.optLong("createdAt"),
            phase = runCatching { DownloadPhase.valueOf(text("phase").orEmpty()) }.getOrDefault(DownloadPhase.Interrupted),
            sceneId = text("sceneId"),
            error = text("error"),
        )
    }
}
