package gomeng.dev.stashplayer.feature.edit

import gomeng.dev.stashplayer.core.network.EditableItem
import gomeng.dev.stashplayer.core.network.EditableRef
import gomeng.dev.stashplayer.core.network.ScrapedItem

/** A field a scrape result can fill in. */
enum class ScrapeField { Title, Date, Code, Creator, Details, Studio, Performers, Tags, Urls, Cover }

/** One field of the merge dialog: what the item has, what the scraper found, and whether to take it by default. */
data class ScrapeChange(val field: ScrapeField, val current: String, val scraped: String, val takeByDefault: Boolean)

/**
 * The fields where [scraped] has something [item] doesn't: single values that differ, and performers, tags and URLs
 * that would be added. Like Stash's web UI, empty fields are filled by default and set ones are kept unless chosen.
 */
fun scrapeChanges(item: EditableItem, scraped: ScrapedItem): List<ScrapeChange> = buildList {
    fun single(field: ScrapeField, current: String, new: String?) {
        val value = new?.trim().orEmpty()
        if (value.isNotEmpty() && value != current.trim()) add(ScrapeChange(field, current, value, takeByDefault = current.isBlank()))
    }
    single(ScrapeField.Title, item.title, scraped.title)
    single(ScrapeField.Date, item.date, scraped.date)
    single(ScrapeField.Code, item.code, scraped.code)
    single(ScrapeField.Creator, item.creator, scraped.creator)
    single(ScrapeField.Details, item.details, scraped.details)
    scraped.studio?.takeIf { !it.sameAs(item.studio) }?.let { studio ->
        add(ScrapeChange(ScrapeField.Studio, item.studio?.name.orEmpty(), studio.label(), takeByDefault = item.studio == null))
    }
    newRefs(item.performers, scraped.performers).takeIf { it.isNotEmpty() }?.let { added ->
        add(ScrapeChange(ScrapeField.Performers, item.performers.joinToString { it.name }, added.joinToString { it.label() }, takeByDefault = true))
    }
    newRefs(item.tags, scraped.tags).takeIf { it.isNotEmpty() }?.let { added ->
        add(ScrapeChange(ScrapeField.Tags, item.tags.joinToString { it.name }, added.joinToString { it.label() }, takeByDefault = true))
    }
    scraped.urls.filter { url -> item.urls.none { it.trim().equals(url, ignoreCase = true) } }.takeIf { it.isNotEmpty() }?.let { added ->
        add(ScrapeChange(ScrapeField.Urls, item.urls.joinToString("\n"), added.joinToString("\n"), takeByDefault = true))
    }
    scraped.coverImage?.takeIf { it.isNotBlank() }?.let { add(ScrapeChange(ScrapeField.Cover, "", it, takeByDefault = true)) }
}

/** [item] with the [chosen] fields taken from [scraped]; performers, tags and URLs are added, not replaced. */
fun applyScrape(item: EditableItem, scraped: ScrapedItem, chosen: Set<ScrapeField>): EditableItem {
    var result = item
    fun take(field: ScrapeField) = field in chosen
    if (take(ScrapeField.Title)) scraped.title?.let { result = result.copy(title = it.trim()) }
    if (take(ScrapeField.Date)) scraped.date?.let { result = result.copy(date = it.trim()) }
    if (take(ScrapeField.Code)) scraped.code?.let { result = result.copy(code = it.trim()) }
    if (take(ScrapeField.Creator)) scraped.creator?.let { result = result.copy(creator = it.trim()) }
    if (take(ScrapeField.Details)) scraped.details?.let { result = result.copy(details = it.trim()) }
    if (take(ScrapeField.Studio)) scraped.studio?.let { result = result.copy(studio = it) }
    if (take(ScrapeField.Performers)) result = result.copy(performers = result.performers + newRefs(result.performers, scraped.performers))
    if (take(ScrapeField.Tags)) result = result.copy(tags = result.tags + newRefs(result.tags, scraped.tags))
    if (take(ScrapeField.Urls)) {
        result = result.copy(urls = (result.urls + scraped.urls).filter(String::isNotBlank).distinctBy { it.trim().lowercase() })
    }
    if (take(ScrapeField.Cover)) scraped.coverImage?.let { result = result.copy(coverImage = it) }
    return result
}

/** Scraped names the item doesn't have yet, matched by library ID or, for new ones, by name. */
private fun newRefs(current: List<EditableRef>, scraped: List<EditableRef>): List<EditableRef> =
    scraped.filter { candidate -> current.none { it.sameAs(candidate) } }.distinctBy { it.id ?: it.name.lowercase() }

private fun EditableRef.sameAs(other: EditableRef?): Boolean =
    other != null && if (id != null && other.id != null) id == other.id else name.equals(other.name, ignoreCase = true)

/** New ones get a "+" so the dialog shows they would be created. */
private fun EditableRef.label(): String = if (id == null) "+ $name" else name
