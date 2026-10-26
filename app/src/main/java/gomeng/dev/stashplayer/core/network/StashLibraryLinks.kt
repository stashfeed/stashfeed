package gomeng.dev.stashplayer.core.network

import org.json.JSONArray
import org.json.JSONObject

/**
 * What a download adds to the library: finding what is already there by its source URL, matching performer, tag
 * and studio names to the library (and creating missing ones when the Stash Downloader's settings say so), and
 * filling in scenes, images and galleries.
 */
class StashLibraryLinks(private val client: StashGraphQlClient) {
    suspend fun findSceneIdByUrl(url: String): String? = firstId(FIND_SCENES_BY_URL, "findScenes", "scenes", mapOf("url" to urlMatchKey(url)))

    suspend fun findGalleryIdByUrl(url: String): String? = firstId(FIND_GALLERIES_BY_URL, "findGalleries", "galleries", mapOf("url" to urlMatchKey(url)))

    suspend fun findImageIdByUrl(url: String): String? = firstId(FIND_IMAGES_BY_URL, "findImages", "images", mapOf("url" to urlMatchKey(url)))

    suspend fun findImageIdByPath(path: String): String? = firstId(FIND_IMAGES_BY_PATH, "findImages", "images", mapOf("path" to path, "modifier" to "EQUALS", "perPage" to 1))

    /** Every image whose file lies in [folder]. */
    suspend fun findImageIdsInFolder(folder: String): List<String> =
        ids(FIND_IMAGES_BY_PATH, "findImages", "images", mapOf("path" to folder.trimEnd('/') + "/", "modifier" to "INCLUDES", "perPage" to -1))

    /** The gallery Stash made of [folder] itself, when it creates galleries from folders. */
    suspend fun findGalleryIdByFolder(folder: String): String? = firstId(FIND_GALLERIES_BY_PATH, "findGalleries", "galleries", mapOf("path" to folder.trimEnd('/')))

    /** A performer whose URLs contain [fragment], e.g. "reddit.com/user/name" as the Reddit Stash Agent stores them. */
    suspend fun findPerformerIdByUrl(fragment: String): String? = firstId(FIND_PERFORMERS_BY_URL, "findPerformers", "performers", mapOf("url" to fragment))

    suspend fun resolvePerformers(names: List<String>, create: Boolean): List<String> =
        names.mapNotNull { resolve(it, FIND_PERFORMERS_BY_NAME, "findPerformers", "performers", CREATE_PERFORMER.takeIf { create }, "performerCreate") }.distinct()

    suspend fun resolveTags(names: List<String>, create: Boolean): List<String> =
        names.mapNotNull { resolve(it, FIND_TAGS_BY_NAME, "findTags", "tags", CREATE_TAG.takeIf { create }, "tagCreate") }.distinct()

    suspend fun resolveStudio(name: String?, create: Boolean): String? =
        name?.let { resolve(it, FIND_STUDIOS_BY_NAME, "findStudios", "studios", CREATE_STUDIO.takeIf { create }, "studioCreate") }

    suspend fun updateScene(id: String, details: LibraryDetails) {
        graphQlData(client.executeRaw(SCENE_UPDATE, mapOf("input" to details.input(id))))
    }

    suspend fun updateImage(id: String, details: LibraryDetails) {
        graphQlData(client.executeRaw(IMAGE_UPDATE, mapOf("input" to details.input(id))))
    }

    suspend fun updateGallery(id: String, details: LibraryDetails) {
        graphQlData(client.executeRaw(GALLERY_UPDATE, mapOf("input" to details.input(id))))
    }

    /** Creates a gallery with [imageIds]; returns its ID. */
    suspend fun createGallery(details: LibraryDetails, imageIds: List<String>): String {
        val input = details.input(id = null)
        val id = graphQlData(client.executeRaw(GALLERY_CREATE, mapOf("input" to input)))
            .optJSONObject("galleryCreate")?.optNullableString("id") ?: error("Stash did not create the gallery")
        if (imageIds.isNotEmpty()) addImagesToGallery(id, imageIds)
        return id
    }

    /** Adds [performerIds] to every image in [imageIds], keeping the performers they have. */
    suspend fun addPerformersToImages(imageIds: List<String>, performerIds: List<String>) {
        if (imageIds.isEmpty() || performerIds.isEmpty()) return
        graphQlData(
            client.executeRaw(
                BULK_IMAGE_UPDATE,
                mapOf("input" to mapOf("ids" to imageIds, "performer_ids" to mapOf("ids" to performerIds, "mode" to "ADD"))),
            ),
        )
    }

    suspend fun addImagesToGallery(galleryId: String, imageIds: List<String>) {
        graphQlData(client.executeRaw(ADD_GALLERY_IMAGES, mapOf("input" to mapOf("gallery_id" to galleryId, "image_ids" to imageIds))))
    }

    /** The ID of an item named [name] (or with it as an alias), ignoring case; created when [createMutation] is given. */
    private suspend fun resolve(
        name: String,
        findQuery: String,
        findField: String,
        listField: String,
        createMutation: String?,
        createField: String,
    ): String? {
        val wanted = name.trim().takeIf { it.isNotBlank() } ?: return null
        val items = graphQlData(client.executeRaw(findQuery, mapOf("q" to wanted)))
            .optJSONObject(findField)?.optJSONArray(listField) ?: JSONArray()
        val match = (0 until items.length()).mapNotNull { items.optJSONObject(it) }.firstOrNull { item ->
            item.optNullableString("name").equals(wanted, ignoreCase = true) ||
                item.stringList("alias_list").any { it.equals(wanted, ignoreCase = true) } ||
                item.stringList("aliases").any { it.equals(wanted, ignoreCase = true) }
        }
        match?.optNullableString("id")?.let { return it }
        createMutation ?: return null
        return graphQlData(client.executeRaw(createMutation, mapOf("name" to wanted)))
            .optJSONObject(createField)?.optNullableString("id")
    }

    private suspend fun firstId(query: String, field: String, listField: String, variables: Map<String, Any?>): String? =
        ids(query, field, listField, variables).firstOrNull()

    private suspend fun ids(query: String, field: String, listField: String, variables: Map<String, Any?>): List<String> {
        val items = graphQlData(client.executeRaw(query, variables)).optJSONObject(field)?.optJSONArray(listField) ?: JSONArray()
        return (0 until items.length()).mapNotNull { items.optJSONObject(it)?.optNullableString("id") }
    }

    private companion object {
        val FIND_SCENES_BY_URL = """
            query LinksScenesByUrl(${'$'}url: String!) {
              findScenes(scene_filter: { url: { value: ${'$'}url, modifier: INCLUDES } }, filter: { per_page: 1 }) { scenes { id } }
            }
        """
        val FIND_GALLERIES_BY_URL = """
            query LinksGalleriesByUrl(${'$'}url: String!) {
              findGalleries(gallery_filter: { url: { value: ${'$'}url, modifier: INCLUDES } }, filter: { per_page: 1 }) { galleries { id } }
            }
        """
        val FIND_IMAGES_BY_URL = """
            query LinksImagesByUrl(${'$'}url: String!) {
              findImages(image_filter: { url: { value: ${'$'}url, modifier: INCLUDES } }, filter: { per_page: 1 }) { images { id } }
            }
        """
        val FIND_IMAGES_BY_PATH = """
            query LinksImagesByPath(${'$'}path: String!, ${'$'}modifier: CriterionModifier!, ${'$'}perPage: Int!) {
              findImages(image_filter: { path: { value: ${'$'}path, modifier: ${'$'}modifier } }, filter: { per_page: ${'$'}perPage, sort: "path", direction: ASC }) {
                images { id }
              }
            }
        """
        val FIND_GALLERIES_BY_PATH = """
            query LinksGalleriesByPath(${'$'}path: String!) {
              findGalleries(gallery_filter: { path: { value: ${'$'}path, modifier: EQUALS } }, filter: { per_page: 1 }) { galleries { id } }
            }
        """
        val FIND_PERFORMERS_BY_URL = """
            query LinksPerformersByUrl(${'$'}url: String!) {
              findPerformers(performer_filter: { url: { value: ${'$'}url, modifier: INCLUDES } }, filter: { per_page: 1 }) { performers { id } }
            }
        """
        val FIND_PERFORMERS_BY_NAME = """
            query LinksPerformersByName(${'$'}q: String!) {
              findPerformers(filter: { q: ${'$'}q, per_page: 25 }) { performers { id name alias_list } }
            }
        """
        val FIND_TAGS_BY_NAME = """
            query LinksTagsByName(${'$'}q: String!) {
              findTags(filter: { q: ${'$'}q, per_page: 25 }) { tags { id name aliases } }
            }
        """
        val FIND_STUDIOS_BY_NAME = """
            query LinksStudiosByName(${'$'}q: String!) {
              findStudios(filter: { q: ${'$'}q, per_page: 25 }) { studios { id name aliases } }
            }
        """
        val CREATE_PERFORMER = """
            mutation LinksPerformerCreate(${'$'}name: String!) { performerCreate(input: { name: ${'$'}name }) { id } }
        """
        val CREATE_TAG = """
            mutation LinksTagCreate(${'$'}name: String!) { tagCreate(input: { name: ${'$'}name }) { id } }
        """
        val CREATE_STUDIO = """
            mutation LinksStudioCreate(${'$'}name: String!) { studioCreate(input: { name: ${'$'}name }) { id } }
        """
        val SCENE_UPDATE = """
            mutation LinksSceneUpdate(${'$'}input: SceneUpdateInput!) { sceneUpdate(input: ${'$'}input) { id } }
        """
        val IMAGE_UPDATE = """
            mutation LinksImageUpdate(${'$'}input: ImageUpdateInput!) { imageUpdate(input: ${'$'}input) { id } }
        """
        val GALLERY_UPDATE = """
            mutation LinksGalleryUpdate(${'$'}input: GalleryUpdateInput!) { galleryUpdate(input: ${'$'}input) { id } }
        """
        val GALLERY_CREATE = """
            mutation LinksGalleryCreate(${'$'}input: GalleryCreateInput!) { galleryCreate(input: ${'$'}input) { id } }
        """
        val BULK_IMAGE_UPDATE = """
            mutation LinksBulkImageUpdate(${'$'}input: BulkImageUpdateInput!) { bulkImageUpdate(input: ${'$'}input) { id } }
        """
        val ADD_GALLERY_IMAGES = """
            mutation LinksAddGalleryImages(${'$'}input: GalleryAddInput!) { addGalleryImages(input: ${'$'}input) }
        """
    }
}

/** Title, source URL and linked IDs to set on a new scene, image or gallery; empty lists are left out. */
data class LibraryDetails(
    val title: String?,
    val url: String,
    val performerIds: List<String> = emptyList(),
    val tagIds: List<String> = emptyList(),
    val studioId: String? = null,
) {
    internal fun input(id: String?): Map<String, Any?> = buildMap {
        id?.let { put("id", it) }
        title?.takeIf { it.isNotBlank() }?.let { put("title", it) }
        put("urls", listOf(url))
        if (performerIds.isNotEmpty()) put("performer_ids", performerIds)
        if (tagIds.isNotEmpty()) put("tag_ids", tagIds)
        studioId?.let { put("studio_id", it) }
    }
}

/**
 * The part of a URL that identifies it in the library, so http/https, "www." and tracking parameters don't hide a
 * duplicate: "https://www.reddit.com/r/x/comments/abc/t/?utm_source=share" → "reddit.com/r/x/comments/abc/t".
 */
fun urlMatchKey(url: String): String = cleanSourceUrl(url)
    .substringAfter("://")
    .removePrefix("www.")
    .removePrefix("m.")
    .removePrefix("old.")
    .trimEnd('/')

/** Tracking parameters that sharing adds and that don't change what a link points to. */
private val TRACKING_PARAMETER = Regex("^(utm_[a-z]+|share_id|context|ref|ref_source|si|fbclid|gclid|igshid)$", RegexOption.IGNORE_CASE)

/** [url] without tracking parameters or a fragment; Reddit links keep no query at all. Stored as the source URL. */
fun cleanSourceUrl(url: String): String {
    val trimmed = url.trim().substringBefore('#')
    val base = trimmed.substringBefore('?')
    val query = trimmed.substringAfter('?', "")
    if (query.isEmpty() || "reddit.com" in base || "redd.it" in base) return base
    val kept = query.split('&').filter { it.isNotBlank() && !TRACKING_PARAMETER.matches(it.substringBefore('=')) }
    return if (kept.isEmpty()) base else "$base?${kept.joinToString("&")}"
}

private fun JSONObject.stringList(key: String): List<String> {
    val array = optJSONArray(key) ?: return emptyList()
    return (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
}
