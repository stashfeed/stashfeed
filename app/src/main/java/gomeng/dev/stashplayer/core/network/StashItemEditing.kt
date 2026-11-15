package gomeng.dev.stashplayer.core.network

import org.json.JSONArray
import org.json.JSONObject

/** What can be edited: a scene, a gallery or an image. Stash's API for the three is nearly the same. */
enum class EditableKind(val field: String, val updateInput: String) {
    Scene("scene", "SceneUpdateInput"),
    Gallery("gallery", "GalleryUpdateInput"),
    Image("image", "ImageUpdateInput"),
}

/** A performer, tag or studio as the editor shows it; [id] is null for one that will be created on save. */
data class EditableRef(val id: String?, val name: String, val detail: String? = null)

/** The editable fields of a scene, gallery or image. [creator] is the director (scenes) or photographer (others). */
data class EditableItem(
    val kind: EditableKind,
    val id: String,
    val title: String = "",
    val code: String = "",
    val date: String = "",
    val details: String = "",
    val creator: String = "",
    val urls: List<String> = emptyList(),
    val rating100: Int? = null,
    val organized: Boolean = false,
    val studio: EditableRef? = null,
    val performers: List<EditableRef> = emptyList(),
    val tags: List<EditableRef> = emptyList(),
    /** Scenes only: a new cover as URL or data URI, from a scraper. */
    val coverImage: String? = null,
    /** The file name, shown as a hint and used as a scrape query. */
    val fileName: String? = null,
)

/** How a scraper can look something up. */
enum class ScrapeMethod { Url, Fragment, Name }

/** A scraper installed on the server, or a stash-box (then [stashBoxEndpoint] is set). */
data class StashScraper(
    val id: String,
    val name: String,
    val methods: Set<ScrapeMethod>,
    val urlPatterns: List<String> = emptyList(),
    val stashBoxEndpoint: String? = null,
)

/** One result of a scrape, with names already matched to the library where Stash could. */
data class ScrapedItem(
    val title: String?,
    val code: String?,
    val date: String?,
    val details: String?,
    val creator: String?,
    val urls: List<String>,
    val studio: EditableRef?,
    val performers: List<EditableRef>,
    val tags: List<EditableRef>,
    val coverImage: String?,
)

class StashItemEditing(private val client: StashGraphQlClient) {
    suspend fun load(kind: EditableKind, id: String): EditableItem =
        parseEditableItem(kind, graphQlData(client.executeRaw(findQuery(kind), mapOf("id" to id))).optJSONObject("find${kind.capitalized()}") ?: error("${kind.capitalized()} $id not found"))

    /** Saves every field; creates performers, tags and a studio that don't exist yet. */
    suspend fun save(item: EditableItem) {
        val studioId = item.studio?.let { it.id ?: create("studioCreate", "StudioCreateInput", it.name) }
        val performerIds = item.performers.map { it.id ?: create("performerCreate", "PerformerCreateInput", it.name) }
        val tagIds = item.tags.map { it.id ?: create("tagCreate", "TagCreateInput", it.name) }
        val input = buildMap<String, Any?> {
            put("id", item.id)
            put("title", item.title.trim())
            put("code", item.code.trim())
            put("date", item.date.trim().ifBlank { null })
            put("details", item.details.trim())
            put(if (item.kind == EditableKind.Scene) "director" else "photographer", item.creator.trim())
            put("urls", item.urls.map(String::trim).filter(String::isNotBlank).distinct())
            put("rating100", item.rating100)
            put("organized", item.organized)
            put("studio_id", studioId)
            put("performer_ids", performerIds.distinct())
            put("tag_ids", tagIds.distinct())
            if (item.kind == EditableKind.Scene) item.coverImage?.let { put("cover_image", it) }
        }
        val mutation = """
            mutation Edit${item.kind.capitalized()}(${'$'}input: ${item.kind.updateInput}!) {
              ${item.kind.field}Update(input: ${'$'}input) { id }
            }
        """
        graphQlData(client.executeRaw(mutation, mapOf("input" to JSONObject(input.mapValues { (_, value) -> value ?: JSONObject.NULL }))))
    }

    suspend fun searchPerformers(query: String): List<EditableRef> = search("findPerformers", "performers", "disambiguation", query)

    suspend fun searchTags(query: String): List<EditableRef> = search("findTags", "tags", null, query)

    suspend fun searchStudios(query: String): List<EditableRef> = search("findStudios", "studios", null, query)

    /** The server's scrapers that can scrape this kind, then its stash-boxes (scenes only, as in the web UI). */
    suspend fun scrapers(kind: EditableKind): List<StashScraper> {
        val data = graphQlData(client.executeRaw(scrapersQuery(kind)))
        return parseScrapers(data, kind) + if (kind == EditableKind.Scene) parseStashBoxes(data) else emptyList()
    }

    /** Scrapes [item] with [scraper]: by its data (fragment), by [query] (name), or by [url]. */
    suspend fun scrape(item: EditableItem, scraper: StashScraper?, method: ScrapeMethod, query: String? = null, url: String? = null): List<ScrapedItem> {
        val kind = item.kind
        val name = kind.capitalized()
        val fields = scrapedFields(kind)
        if (method == ScrapeMethod.Url) {
            val target = url?.trim()?.takeIf { it.isNotBlank() } ?: error("No URL to scrape")
            val query = """
                query ScrapeUrl(${'$'}url: String!) { scrape${name}URL(url: ${'$'}url) { $fields } }
            """
            val result = graphQlData(client.executeRaw(query, mapOf("url" to target))).optJSONObject("scrape${name}URL")
            return listOfNotNull(result?.let { parseScrapedItem(kind, it) })
        }
        scraper ?: error("Choose a scraper")
        val source = scraper.stashBoxEndpoint?.let { mapOf("stash_box_endpoint" to it) } ?: mapOf("scraper_id" to scraper.id)
        val input = when (method) {
            ScrapeMethod.Name -> mapOf("query" to (query?.trim()?.takeIf { it.isNotBlank() } ?: error("Enter something to search for")))
            else -> mapOf("${kind.field}_id" to item.id)
        }
        val inputType = "ScrapeSingle${name}Input"
        val gql = """
            query ScrapeSingle(${'$'}source: ScraperSourceInput!, ${'$'}input: $inputType!) {
              scrapeSingle$name(source: ${'$'}source, input: ${'$'}input) { $fields }
            }
        """
        val results = graphQlData(client.executeRaw(gql, mapOf("source" to source, "input" to input))).optJSONArray("scrapeSingle$name") ?: JSONArray()
        return (0 until results.length()).mapNotNull { results.optJSONObject(it)?.let { json -> parseScrapedItem(kind, json) } }
    }

    private suspend fun search(field: String, listField: String, detailField: String?, query: String): List<EditableRef> {
        val gql = """
            query EditSearch(${'$'}q: String!) {
              $field(filter: { q: ${'$'}q, per_page: 25, sort: "name", direction: ASC }) { $listField { id name ${detailField.orEmpty()} } }
            }
        """
        val items = graphQlData(client.executeRaw(gql, mapOf("q" to query.trim()))).optJSONObject(field)?.optJSONArray(listField) ?: JSONArray()
        return (0 until items.length()).mapNotNull { items.optJSONObject(it)?.toRef(detailField) }
    }

    private suspend fun create(mutation: String, inputType: String, name: String): String {
        val gql = """
            mutation EditCreate(${'$'}input: $inputType!) { $mutation(input: ${'$'}input) { id } }
        """
        return graphQlData(client.executeRaw(gql, mapOf("input" to mapOf("name" to name.trim()))))
            .optJSONObject(mutation)?.optNullableString("id") ?: error("Stash did not create \"$name\"")
    }
}

private fun EditableKind.capitalized(): String = field.replaceFirstChar(Char::uppercase)

private fun findQuery(kind: EditableKind): String {
    val creator = if (kind == EditableKind.Scene) "director" else "photographer"
    val file = when (kind) {
        EditableKind.Scene -> "files { basename }"
        EditableKind.Gallery -> "files { basename } folder { path }"
        EditableKind.Image -> "visual_files { ... on ImageFile { basename } ... on VideoFile { basename } }"
    }
    return """
        query EditFind(${'$'}id: ID!) {
          find${kind.capitalized()}(id: ${'$'}id) {
            id title code date details $creator urls rating100 organized
            studio { id name }
            performers { id name disambiguation }
            tags { id name }
            $file
          }
        }
    """
}

private fun scrapersQuery(kind: EditableKind): String = """
    query EditScrapers {
      listScrapers(types: [${kind.name.uppercase()}]) { id name ${kind.field} { urls supported_scrapes } }
      configuration { general { stashBoxes { endpoint name } } }
    }
"""

private fun scrapedFields(kind: EditableKind): String {
    val creator = if (kind == EditableKind.Scene) "director" else "photographer"
    val cover = if (kind == EditableKind.Scene) "image" else ""
    return """
        title code date details $creator urls $cover
        studio { stored_id name }
        performers { stored_id name disambiguation }
        tags { stored_id name }
    """
}

private fun JSONObject.toRef(detailField: String? = null): EditableRef? {
    val name = optNullableString("name") ?: return null
    return EditableRef(id = optNullableString("id") ?: optNullableString("stored_id"), name = name, detail = detailField?.let { optNullableString(it) })
}

private fun JSONObject.refs(key: String, detailField: String? = null): List<EditableRef> {
    val array = optJSONArray(key) ?: return emptyList()
    return (0 until array.length()).mapNotNull { array.optJSONObject(it)?.toRef(detailField) }
}

private fun JSONObject.strings(key: String): List<String> {
    val array = optJSONArray(key) ?: return emptyList()
    return (0 until array.length()).mapNotNull { array.optString(it).trim().takeIf(String::isNotBlank) }
}

internal fun parseEditableItem(kind: EditableKind, json: JSONObject): EditableItem {
    val fileName = json.optJSONArray("files")?.optJSONObject(0)?.optNullableString("basename")
        ?: json.optJSONArray("visual_files")?.optJSONObject(0)?.optNullableString("basename")
        ?: json.optJSONObject("folder")?.optNullableString("path")?.substringAfterLast('/')
    return EditableItem(
        kind = kind,
        id = json.optString("id"),
        title = json.optNullableString("title").orEmpty(),
        code = json.optNullableString("code").orEmpty(),
        date = json.optNullableString("date").orEmpty(),
        details = json.optNullableString("details").orEmpty(),
        creator = json.optNullableString(if (kind == EditableKind.Scene) "director" else "photographer").orEmpty(),
        urls = json.strings("urls"),
        rating100 = if (json.isNull("rating100") || !json.has("rating100")) null else json.optInt("rating100"),
        organized = json.optBoolean("organized"),
        studio = json.optJSONObject("studio")?.toRef(),
        performers = json.refs("performers", "disambiguation"),
        tags = json.refs("tags"),
        fileName = fileName,
    )
}

internal fun parseScrapers(data: JSONObject, kind: EditableKind): List<StashScraper> {
    val list = data.optJSONArray("listScrapers") ?: return emptyList()
    return (0 until list.length()).mapNotNull { index ->
        val scraper = list.optJSONObject(index) ?: return@mapNotNull null
        val spec = scraper.optJSONObject(kind.field) ?: return@mapNotNull null
        val methods = spec.strings("supported_scrapes").mapNotNull {
            when (it) {
                "URL" -> ScrapeMethod.Url
                "FRAGMENT" -> ScrapeMethod.Fragment
                "NAME" -> ScrapeMethod.Name
                else -> null
            }
        }.toSet()
        StashScraper(
            id = scraper.optNullableString("id") ?: return@mapNotNull null,
            name = scraper.optNullableString("name") ?: scraper.optString("id"),
            methods = methods,
            urlPatterns = spec.strings("urls"),
        )
    }.sortedBy { it.name.lowercase() }
}

internal fun parseStashBoxes(data: JSONObject): List<StashScraper> {
    val boxes = data.optJSONObject("configuration")?.optJSONObject("general")?.optJSONArray("stashBoxes") ?: return emptyList()
    return (0 until boxes.length()).mapNotNull { index ->
        val box = boxes.optJSONObject(index) ?: return@mapNotNull null
        val endpoint = box.optNullableString("endpoint") ?: return@mapNotNull null
        StashScraper(
            id = endpoint,
            name = box.optNullableString("name") ?: endpoint,
            methods = setOf(ScrapeMethod.Fragment, ScrapeMethod.Name),
            stashBoxEndpoint = endpoint,
        )
    }
}

internal fun parseScrapedItem(kind: EditableKind, json: JSONObject): ScrapedItem = ScrapedItem(
    title = json.optNullableString("title"),
    code = json.optNullableString("code"),
    date = json.optNullableString("date"),
    details = json.optNullableString("details"),
    creator = json.optNullableString(if (kind == EditableKind.Scene) "director" else "photographer"),
    urls = json.strings("urls"),
    studio = json.optJSONObject("studio")?.toRef(),
    performers = json.refs("performers", "disambiguation"),
    tags = json.refs("tags"),
    coverImage = if (kind == EditableKind.Scene) json.optNullableString("image") else null,
)

/** Whether [scraper] says it can scrape [url], by the URL patterns it lists. */
fun StashScraper.matchesUrl(url: String): Boolean = urlPatterns.any { pattern -> pattern.isNotBlank() && url.contains(pattern, ignoreCase = true) }
