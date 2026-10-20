package gomeng.dev.stashplayer.core.network

import gomeng.dev.stashplayer.core.model.FeedGallery
import gomeng.dev.stashplayer.core.model.FeedPerformer
import gomeng.dev.stashplayer.core.model.FeedScene
import gomeng.dev.stashplayer.core.model.GalleryCardModel
import gomeng.dev.stashplayer.core.model.SceneCardModel
import gomeng.dev.stashplayer.core.model.SceneCardTagChip
import gomeng.dev.stashplayer.core.model.StashPerformerGender
import gomeng.dev.stashplayer.core.player.formatPlayerPosition
import org.json.JSONArray
import org.json.JSONObject

/** Upper bound per window query; a 90-day window on a busy library can otherwise return thousands. */
const val HOME_FEED_MAX_SCENES_PER_QUERY = 400
const val HOME_FEED_MAX_GALLERIES_PER_QUERY = 200

private const val FEED_PERFORMER_FIELDS = "performers { id name image_path gender favorite }"

/** Which timestamp the window applies to: when it was added (`created_at`) or released (`date`). */
enum class HomeFeedWindowField(val graphQlField: String) {
    CreatedAt("created_at"),
    Date("date"),
}

internal val HOME_FEED_SCENES_QUERY = """
    query HomeFeedScenes(${'$'}perPage: Int!, ${'$'}sort: String!, ${'$'}sceneFilter: SceneFilterType) {
      findScenes(filter: { per_page: ${'$'}perPage, page: 1, sort: ${'$'}sort, direction: DESC }, scene_filter: ${'$'}sceneFilter) {
        scenes {
          id
          title
          date
          created_at
          resume_time
          play_count
          o_counter
          rating100
          studio { name }
          files { ... on VideoFile { duration width height basename } }
          paths { screenshot preview webp }
          tags { id name }
          $FEED_PERFORMER_FIELDS
        }
      }
    }
"""

internal val HOME_FEED_GALLERIES_QUERY = """
    query HomeFeedGalleries(${'$'}perPage: Int!, ${'$'}sort: String!, ${'$'}galleryFilter: GalleryFilterType) {
      findGalleries(filter: { per_page: ${'$'}perPage, page: 1, sort: ${'$'}sort, direction: DESC }, gallery_filter: ${'$'}galleryFilter) {
        galleries {
          id
          title
          date
          created_at
          image_count
          studio { name }
          folder { path basename }
          files { path basename }
          paths { cover }
          $FEED_PERFORMER_FIELDS
        }
      }
    }
"""

/**
 * @param since ISO timestamp for [HomeFeedWindowField.CreatedAt], `yyyy-MM-dd` for [HomeFeedWindowField.Date].
 * @param extraFilter additional scene criteria (performers / tags / studios from the feed filter).
 */
internal fun buildHomeFeedScenesVariables(
    field: HomeFeedWindowField,
    since: String,
    extraFilter: Map<String, Any?> = emptyMap(),
): Map<String, Any?> = mapOf(
    "perPage" to HOME_FEED_MAX_SCENES_PER_QUERY,
    "sort" to field.graphQlField,
    "sceneFilter" to extraFilter + (field.graphQlField to mapOf("value" to since, "modifier" to "GREATER_THAN")),
)

internal fun buildHomeFeedGalleriesVariables(
    field: HomeFeedWindowField,
    since: String,
    extraFilter: Map<String, Any?> = emptyMap(),
): Map<String, Any?> = mapOf(
    "perPage" to HOME_FEED_MAX_GALLERIES_PER_QUERY,
    "sort" to field.graphQlField,
    "galleryFilter" to extraFilter + (field.graphQlField to mapOf("value" to since, "modifier" to "GREATER_THAN")),
)

internal fun parseHomeFeedScenesResponse(json: String, resolveUrl: (String) -> String): List<FeedScene> =
    parseGraphQlData(json).optJSONObject("findScenes")?.optJSONArray("scenes").objects().mapNotNull { scene ->
        val id = scene.optTrimmedString("id") ?: return@mapNotNull null
        val createdAt = scene.optTrimmedString("created_at") ?: return@mapNotNull null
        val file = scene.optJSONArray("files").objects().firstOrNull()
        val duration = file?.optDouble("duration", 0.0)?.takeUnless { it.isNaN() } ?: 0.0
        val resume = scene.optDouble("resume_time", 0.0).takeUnless { it.isNaN() } ?: 0.0
        val paths = scene.optJSONObject("paths")
        val performers = scene.optJSONArray("performers").objects().mapNotNull { it.toFeedPerformerOrNull(resolveUrl) }
        FeedScene(
            scene = SceneCardModel(
                id = id,
                title = scene.optTrimmedString("title") ?: file?.optTrimmedString("basename") ?: id,
                durationText = formatPlayerPosition((duration * 1000.0).toLong()),
                studio = scene.optJSONObject("studio")?.optTrimmedString("name").orEmpty(),
                progress = if (duration > 0.0) (resume / duration).toFloat().coerceIn(0f, 1f) else 0f,
                thumbnailUrl = paths?.optTrimmedString("screenshot")?.let(resolveUrl),
                playCount = scene.optIntOrNull("play_count"),
                tagChips = scene.optJSONArray("tags").objects().mapNotNull { it.toChipOrNull() },
                performerChips = performers.map { SceneCardTagChip(id = it.id, label = it.name) },
                date = scene.optTrimmedString("date"),
                createdAt = createdAt,
                oCounter = scene.optIntOrNull("o_counter"),
                rating100 = scene.optIntOrNull("rating100"),
            ),
            performers = performers,
            previewUrl = paths?.optTrimmedString("preview")?.let(resolveUrl),
            animatedPreviewUrl = paths?.optTrimmedString("webp")?.let(resolveUrl),
            createdAt = createdAt,
            date = scene.optTrimmedString("date"),
            width = file?.optIntOrNull("width"),
            height = file?.optIntOrNull("height"),
        )
    }

internal fun parseHomeFeedGalleriesResponse(json: String, resolveUrl: (String) -> String): List<FeedGallery> =
    parseGraphQlData(json).optJSONObject("findGalleries")?.optJSONArray("galleries").objects().mapNotNull { gallery ->
        val id = gallery.optTrimmedString("id") ?: return@mapNotNull null
        val createdAt = gallery.optTrimmedString("created_at") ?: return@mapNotNull null
        val folder = gallery.optJSONObject("folder")
        val files = gallery.optJSONArray("files").objects()
        val performers = gallery.optJSONArray("performers").objects().mapNotNull { it.toFeedPerformerOrNull(resolveUrl) }
        FeedGallery(
            gallery = GalleryCardModel(
                id = id,
                title = gallery.optTrimmedString("title")
                    ?: folder?.optTrimmedString("basename")
                    ?: files.firstNotNullOfOrNull { it.optTrimmedString("basename") }
                    ?: id,
                coverUrl = gallery.optJSONObject("paths")?.optTrimmedString("cover")?.let(resolveUrl),
                imageCount = gallery.optIntOrNull("image_count"),
                date = gallery.optTrimmedString("date"),
                studio = gallery.optJSONObject("studio")?.optTrimmedString("name"),
                performerCount = performers.size,
                performerChips = performers.map { SceneCardTagChip(id = it.id, label = it.name) },
                createdAt = createdAt,
            ),
            performers = performers,
            createdAt = createdAt,
            paths = listOfNotNull(folder?.optTrimmedString("path")) + files.mapNotNull { it.optTrimmedString("path") },
        )
    }

private fun JSONObject.toFeedPerformerOrNull(resolveUrl: (String) -> String): FeedPerformer? {
    val id = optTrimmedString("id") ?: return null
    val name = optTrimmedString("name") ?: return null
    return FeedPerformer(
        id = id,
        name = name,
        imageUrl = optTrimmedString("image_path")?.let(resolveUrl),
        gender = StashPerformerGender.fromGraphQl(optTrimmedString("gender")),
        favorite = optBoolean("favorite", false),
    )
}

private fun JSONObject.toChipOrNull(): SceneCardTagChip? {
    val id = optTrimmedString("id") ?: return null
    val name = optTrimmedString("name") ?: return null
    return SceneCardTagChip(id = id, label = name)
}

private fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

private fun JSONObject.optTrimmedString(name: String): String? =
    if (!has(name) || isNull(name)) null else optString(name).trim().takeIf { it.isNotBlank() }

private fun JSONObject.optIntOrNull(name: String): Int? =
    if (!has(name) || isNull(name)) null else optInt(name)
