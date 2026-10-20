package gomeng.dev.stashplayer.core.model

/** Which list a saved filter from the Stash web UI belongs to. */
enum class StashSavedFilterMode(val graphQlValue: String, val defaultFilterKey: String) {
    Scenes("SCENES", "scenes"),
    Images("IMAGES", "images"),
    Galleries("GALLERIES", "galleries"),
}

/** A filter saved in the Stash web UI, with its criteria already in GraphQL filter shape. */
data class StashServerSavedFilter(
    val id: String,
    val name: String,
    val criteria: Map<String, Any?>,
    val query: String? = null,
    val sort: String? = null,
    val direction: String? = null,
)

/** Criteria the web UI stores as `{value: "true"}` but GraphQL takes as a plain boolean. */
private val BooleanCriteria = setOf("is_missing", "has_markers", "has_chapters", "organized", "performer_favorite", "interactive")

/** Criteria that carry only a modifier. */
private val ValuelessCriteria = setOf("duplicated", "stash_id_endpoint", "stash_ids_endpoint")

/**
 * Converts a saved filter's `object_filter` (the web UI's criterion format) into a GraphQL filter input:
 * - `{value: {items: [{id}], excluded: [{id}], depth}}` → `{value: [ids], excludes: [ids], depth}`
 * - `{value: {value, value2}}` → `{value, value2}`
 * - boolean criteria → the boolean itself.
 * Unknown shapes pass through unchanged, as the Binge plugin does.
 */
fun convertSavedObjectFilter(objectFilter: Map<String, Any?>): Map<String, Any?> = buildMap {
    objectFilter.forEach { (key, raw) ->
        if (raw == null) return@forEach
        if (key in BooleanCriteria) {
            val value = (raw as? Map<*, *>)?.let { it["value"] } ?: raw
            put(key, value.toBooleanOrSelf())
            return@forEach
        }
        val criterion = raw as? Map<*, *>
        if (criterion == null || "modifier" !in criterion) {
            put(key, raw)
            return@forEach
        }
        put(key, convertCriterion(key, criterion))
    }
}

private fun convertCriterion(key: String, criterion: Map<*, *>): Map<String, Any?> {
    val modifier = criterion["modifier"]
    val value = criterion["value"]
    if (value == null) {
        return if (key in ValuelessCriteria) mapOf("modifier" to modifier) else mapOf("modifier" to modifier, "value" to "")
    }
    val nested = value as? Map<*, *> ?: return mapOf("modifier" to modifier, "value" to value)
    if ("items" in nested) {
        return buildMap {
            put("modifier", modifier)
            put("value", (nested["items"] as? List<*>).orEmpty().mapNotNull { it.entityId() })
            (nested["excluded"] as? List<*>)?.mapNotNull { it.entityId() }?.takeIf { it.isNotEmpty() }?.let { put("excludes", it) }
            (nested["depth"] as? Number)?.let { put("depth", it.toInt()) }
        }
    }
    if ("value" in nested && nested.size <= 2) {
        return buildMap {
            put("modifier", modifier)
            put("value", nested["value"])
            nested["value2"]?.let { put("value2", it) }
        }
    }
    @Suppress("UNCHECKED_CAST")
    return (criterion as Map<String, Any?>)
}

private fun Any?.entityId(): String? = when (this) {
    is Map<*, *> -> this["id"]?.toString()
    is String -> this
    is Number -> toString()
    else -> null
}

private fun Any?.toBooleanOrSelf(): Any? = when (this) {
    is String -> toBooleanStrictOrNull() ?: this
    else -> this
}

/** Applies a Stash saved filter to the video filter: its criteria ride along and the chip shows its name. */
fun StashVideoFilterState.withServerSavedFilter(filter: StashServerSavedFilter): StashVideoFilterState = copy(
    savedFilter = StashSavedFilterRef(id = "stash:${filter.id}", name = filter.name, criteria = filter.criteria),
)

/** Applies a Stash saved filter to the gallery filter. */
fun StashGalleryFilterState.withServerSavedFilter(filter: StashServerSavedFilter): StashGalleryFilterState = copy(
    savedFilter = StashSavedFilterRef(id = "stash:${filter.id}", name = filter.name, criteria = filter.criteria),
)

/** Applies a Stash saved filter to the image filter. */
fun StashImageFilterState.withServerSavedFilter(filter: StashServerSavedFilter): StashImageFilterState = copy(
    savedFilter = StashSavedFilterRef(id = "stash:${filter.id}", name = filter.name, criteria = filter.criteria),
)
