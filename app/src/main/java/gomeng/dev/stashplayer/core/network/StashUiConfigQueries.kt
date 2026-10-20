package gomeng.dev.stashplayer.core.network

import gomeng.dev.stashplayer.core.model.StashSavedFilterMode
import gomeng.dev.stashplayer.core.model.StashServerSavedFilter
import gomeng.dev.stashplayer.core.model.convertSavedObjectFilter
import org.json.JSONArray
import org.json.JSONObject

/**
 * Stash's UI configuration is a free-form JSON object the web UI keeps its own settings in. Stashy stores its
 * synced state under the `stashy` key there, so it follows the user across devices without a server plugin.
 */
internal val UI_CONFIG_QUERY = """
    query StashyUiConfig {
      configuration { ui }
    }
"""

/** Key of Stashy's seen Home-feed posts inside the UI configuration. */
internal const val UI_KEY_SEEN_FEED_POSTS = "stashy.seenFeedPosts"

internal fun parseUiConfigResponse(json: String): JSONObject =
    parseGraphQlData(json).optJSONObject("configuration")?.optJSONObject("ui") ?: JSONObject()

/** Reads `ui.stashy.seenFeedPosts`: post key → the post's added date (`yyyy-MM-dd`), used for pruning. */
internal fun JSONObject.seenFeedPosts(): Map<String, String> {
    val seen = optJSONObject("stashy")?.optJSONObject("seenFeedPosts") ?: return emptyMap()
    return seen.keys().asSequence().mapNotNull { key ->
        seen.optString(key).takeIf { it.isNotBlank() }?.let { key to it }
    }.toMap()
}

/** Turns org.json values into plain Kotlin maps / lists, the shape GraphQL variables are built from. */
internal fun Any?.jsonToKotlin(): Any? = when (this) {
    null, JSONObject.NULL -> null
    is JSONObject -> keys().asSequence().associateWith { key -> opt(key).jsonToKotlin() }
    is JSONArray -> (0 until length()).map { opt(it).jsonToKotlin() }
    else -> this
}

/** Parses a JSON object string into plain Kotlin maps / lists. */
fun parseJsonObjectToMap(json: String): Map<String, Any?> {
    @Suppress("UNCHECKED_CAST")
    return JSONObject(json).jsonToKotlin() as Map<String, Any?>
}

internal val FIND_SAVED_FILTERS_QUERY = """
    query StashySavedFilters(${'$'}mode: FilterMode!) {
      findSavedFilters(mode: ${'$'}mode) {
        id
        name
        find_filter { q sort direction }
        object_filter
      }
    }
"""

internal fun parseSavedFiltersResponse(json: String): List<StashServerSavedFilter> {
    val filters = parseGraphQlData(json).optJSONArray("findSavedFilters") ?: return emptyList()
    return (0 until filters.length()).mapNotNull { index -> filters.optJSONObject(index)?.toServerSavedFilter() }
}

/** The default filter Stash 0.27+ keeps in the UI config (`ui.defaultFilters.<mode>`). */
internal fun JSONObject.defaultFilter(mode: StashSavedFilterMode): StashServerSavedFilter? {
    val filter = optJSONObject("defaultFilters")?.optJSONObject(mode.defaultFilterKey) ?: return null
    return filter.toServerSavedFilter(fallbackId = "default", fallbackName = "Default")
}

private fun JSONObject.toServerSavedFilter(
    fallbackId: String? = null,
    fallbackName: String? = null,
): StashServerSavedFilter? {
    val id = optString("id").takeIf { it.isNotBlank() } ?: fallbackId ?: return null
    val name = optString("name").takeIf { it.isNotBlank() } ?: fallbackName ?: id
    val findFilter = optJSONObject("find_filter")

    @Suppress("UNCHECKED_CAST")
    val objectFilter = (opt("object_filter").jsonToKotlin() as? Map<String, Any?>).orEmpty()
    return StashServerSavedFilter(
        id = id,
        name = name,
        criteria = convertSavedObjectFilter(objectFilter),
        query = findFilter?.optString("q")?.takeIf { it.isNotBlank() && it != "null" },
        sort = findFilter?.optString("sort")?.takeIf { it.isNotBlank() && it != "null" },
        direction = findFilter?.optString("direction")?.takeIf { it.isNotBlank() && it != "null" },
    )
}
