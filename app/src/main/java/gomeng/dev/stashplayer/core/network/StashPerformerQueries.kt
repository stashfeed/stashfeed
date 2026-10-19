package gomeng.dev.stashplayer.core.network

import gomeng.dev.stashplayer.core.model.PerformerCardModel
import gomeng.dev.stashplayer.core.model.PerformerDetailModel
import gomeng.dev.stashplayer.core.model.PerformerListScope
import gomeng.dev.stashplayer.core.model.PerformerListSort
import gomeng.dev.stashplayer.core.model.SceneCardTagChip
import gomeng.dev.stashplayer.core.model.StashPerformerPage
import gomeng.dev.stashplayer.core.model.nextStashRandomSortSeed
import gomeng.dev.stashplayer.core.model.normalizeStashRandomSortSeed
import org.json.JSONArray
import org.json.JSONObject

private const val PERFORMER_CARD_FIELDS = """
    id
    name
    image_path
    favorite
    gender
    scene_count
    image_count
    gallery_count
    rating100
    o_counter
"""

internal val FIND_PERFORMER_CARDS_QUERY = """
    query FindPerformerCards(${'$'}perPage: Int!, ${'$'}page: Int!, ${'$'}q: String, ${'$'}sort: String!, ${'$'}direction: SortDirectionEnum!, ${'$'}performerFilter: PerformerFilterType) {
      findPerformers(filter: { per_page: ${'$'}perPage, page: ${'$'}page, q: ${'$'}q, sort: ${'$'}sort, direction: ${'$'}direction }, performer_filter: ${'$'}performerFilter) {
        count
        performers {
          $PERFORMER_CARD_FIELDS
        }
      }
    }
"""

internal val FIND_PERFORMER_DETAIL_QUERY = """
    query FindPerformerDetail(${'$'}id: ID!) {
      findPerformer(id: ${'$'}id) {
        $PERFORMER_CARD_FIELDS
        alias_list
        details
        birthdate
        country
        ethnicity
        height_cm
        hair_color
        eye_color
        urls
        tags { id name }
      }
    }
"""

internal val PERFORMER_SET_FAVORITE_MUTATION = """
    mutation PerformerSetFavorite(${'$'}id: ID!, ${'$'}favorite: Boolean!) {
      performerUpdate(input: { id: ${'$'}id, favorite: ${'$'}favorite }) { id favorite }
    }
"""

internal fun buildFindPerformerCardsVariables(
    perPage: Int,
    page: Int,
    query: String?,
    scope: PerformerListScope,
    sort: PerformerListSort,
    randomSeed: Int? = null,
): Map<String, Any?> = mapOf(
    "perPage" to perPage,
    "page" to page.coerceAtLeast(1),
    "q" to query?.trim()?.takeIf { it.isNotBlank() },
    // A seeded random sort keeps paging stable; a bare "random" would reshuffle every page.
    "sort" to if (sort == PerformerListSort.Random) {
        "random_${normalizeStashRandomSortSeed(randomSeed ?: nextStashRandomSortSeed())}"
    } else {
        sort.graphQlSort
    },
    "direction" to sort.direction.graphQlValue,
    "performerFilter" to when (scope) {
        PerformerListScope.Following -> mapOf("filter_favorites" to true)
        PerformerListScope.All -> null
    },
)

internal fun parseFindPerformerCardsResponse(json: String, resolveUrl: (String) -> String): StashPerformerPage {
    val data = parseGraphQlData(json)
    val payload = data.optJSONObject("findPerformers") ?: return StashPerformerPage(emptyList(), 0)
    val performers = payload.optJSONArray("performers").objects().mapNotNull { it.toPerformerCardOrNull(resolveUrl) }
    return StashPerformerPage(performers = performers, totalCount = payload.optInt("count", performers.size))
}

internal fun parseFindPerformerDetailResponse(json: String, resolveUrl: (String) -> String): PerformerDetailModel? {
    val performer = parseGraphQlData(json).optJSONObject("findPerformer") ?: return null
    val card = performer.toPerformerCardOrNull(resolveUrl) ?: return null
    return PerformerDetailModel(
        card = card,
        aliases = performer.optJSONArray("alias_list").strings(),
        details = performer.optTrimmedString("details"),
        birthdate = performer.optTrimmedString("birthdate"),
        country = performer.optTrimmedString("country"),
        ethnicity = performer.optTrimmedString("ethnicity"),
        heightCm = performer.optIntOrNull("height_cm"),
        hairColor = performer.optTrimmedString("hair_color"),
        eyeColor = performer.optTrimmedString("eye_color"),
        tags = performer.optJSONArray("tags").objects().mapNotNull { tag ->
            val id = tag.optTrimmedString("id") ?: return@mapNotNull null
            val name = tag.optTrimmedString("name") ?: return@mapNotNull null
            SceneCardTagChip(id = id, label = name)
        },
        urls = performer.optJSONArray("urls").strings(),
    )
}

internal fun parsePerformerSetFavoriteResponse(json: String): Boolean =
    parseGraphQlData(json).optJSONObject("performerUpdate")?.optBoolean("favorite", false) == true

private fun JSONObject.toPerformerCardOrNull(resolveUrl: (String) -> String): PerformerCardModel? {
    val id = optTrimmedString("id") ?: return null
    val name = optTrimmedString("name") ?: return null
    return PerformerCardModel(
        id = id,
        name = name,
        imageUrl = optTrimmedString("image_path")?.let(resolveUrl),
        favorite = optBoolean("favorite", false),
        gender = optTrimmedString("gender"),
        sceneCount = optInt("scene_count", 0),
        imageCount = optInt("image_count", 0),
        galleryCount = optInt("gallery_count", 0),
        rating100 = optIntOrNull("rating100"),
        oCounter = optIntOrNull("o_counter"),
    )
}

/** Returns the GraphQL `data` object, throwing the server's first error message when there is one. */
private fun parseGraphQlData(json: String): JSONObject {
    val root = JSONObject(json)
    root.optJSONArray("errors")?.takeIf { it.length() > 0 }?.let { errors ->
        val message = errors.optJSONObject(0)?.optString("message").orEmpty().ifBlank { "Stash GraphQL error" }
        error(redactStashCredentialText(message))
    }
    return root.optJSONObject("data") ?: JSONObject()
}

private fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

private fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it).trim().takeIf(String::isNotBlank) }

private fun JSONObject.optTrimmedString(name: String): String? =
    if (isNull(name)) null else optString(name).trim().takeIf { it.isNotBlank() }

private fun JSONObject.optIntOrNull(name: String): Int? =
    if (!has(name) || isNull(name)) null else optInt(name)
