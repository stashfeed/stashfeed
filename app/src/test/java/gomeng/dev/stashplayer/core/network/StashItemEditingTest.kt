package gomeng.dev.stashplayer.core.network

import gomeng.dev.stashplayer.feature.edit.ScrapeField
import gomeng.dev.stashplayer.feature.edit.applyScrape
import gomeng.dev.stashplayer.feature.edit.scrapeChanges
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StashItemEditingTest {
    @Test
    fun parsesSceneWithAllFields() {
        val item = parseEditableItem(
            EditableKind.Scene,
            JSONObject(
                """{"id":"7","title":"Clip","code":"AB-1","date":"2026-01-02","details":"Text","director":"Dee","urls":["https://a.example/1"],
                   "rating100":80,"organized":true,"studio":{"id":"3","name":"Studio"},
                   "performers":[{"id":"1","name":"Ava","disambiguation":"US"}],"tags":[{"id":"9","name":"Outdoor"}],
                   "files":[{"basename":"clip.mp4"}]}""",
            ),
        )
        assertEquals("Dee", item.creator)
        assertEquals(80, item.rating100)
        assertTrue(item.organized)
        assertEquals(EditableRef("3", "Studio"), item.studio)
        assertEquals(EditableRef("1", "Ava", "US"), item.performers.single())
        assertEquals("clip.mp4", item.fileName)
    }

    @Test
    fun imageWithoutRatingOrStudio() {
        val item = parseEditableItem(
            EditableKind.Image,
            JSONObject("""{"id":"5","title":null,"photographer":"Pat","rating100":null,"studio":null,"performers":[],"tags":[],"visual_files":[{"basename":"a.jpg"}]}"""),
        )
        assertEquals("", item.title)
        assertEquals("Pat", item.creator)
        assertNull(item.rating100)
        assertNull(item.studio)
        assertEquals("a.jpg", item.fileName)
    }

    @Test
    fun scrapersKeepOnlyTheKindAndTheirMethods() {
        val data = JSONObject(
            """{"listScrapers":[
                 {"id":"b","name":"Beta","scene":{"urls":["beta.com"],"supported_scrapes":["URL","FRAGMENT"]}},
                 {"id":"a","name":"Alpha","scene":{"urls":[],"supported_scrapes":["NAME"]}},
                 {"id":"g","name":"Gallery only","scene":null}],
               "configuration":{"general":{"stashBoxes":[{"endpoint":"https://stashdb.org/graphql","name":"StashDB"}]}}}""",
        )
        val scrapers = parseScrapers(data, EditableKind.Scene)
        assertEquals(listOf("Alpha", "Beta"), scrapers.map { it.name })
        assertEquals(setOf(ScrapeMethod.Url, ScrapeMethod.Fragment), scrapers[1].methods)
        assertTrue(scrapers[1].matchesUrl("https://www.beta.com/video/1"))
        val boxes = parseStashBoxes(data)
        assertEquals("https://stashdb.org/graphql", boxes.single().stashBoxEndpoint)
    }

    @Test
    fun scrapeFillsEmptyFieldsByDefaultAndAddsNames() {
        val item = EditableItem(
            kind = EditableKind.Scene,
            id = "1",
            title = "My title",
            performers = listOf(EditableRef("1", "Ava")),
            urls = listOf("https://a.example/1"),
        )
        val scraped = ScrapedItem(
            title = "Scraped title",
            code = null,
            date = "2026-03-04",
            details = null,
            creator = null,
            urls = listOf("https://a.example/1", "https://b.example/2"),
            studio = EditableRef(null, "New Studio"),
            performers = listOf(EditableRef("1", "Ava"), EditableRef(null, "Mia")),
            tags = listOf(EditableRef("9", "Outdoor")),
            coverImage = null,
        )
        val changes = scrapeChanges(item, scraped).associateBy { it.field }
        assertFalse(changes.getValue(ScrapeField.Title).takeByDefault)
        assertTrue(changes.getValue(ScrapeField.Date).takeByDefault)
        assertEquals("+ Mia", changes.getValue(ScrapeField.Performers).scraped)
        assertEquals("https://b.example/2", changes.getValue(ScrapeField.Urls).scraped)

        val merged = applyScrape(item, scraped, changes.values.filter { it.takeByDefault }.map { it.field }.toSet())
        assertEquals("My title", merged.title)
        assertEquals("2026-03-04", merged.date)
        assertEquals(listOf("Ava", "Mia"), merged.performers.map { it.name })
        assertNull(merged.performers[1].id)
        assertEquals(listOf("Outdoor"), merged.tags.map { it.name })
        assertEquals(EditableRef(null, "New Studio"), merged.studio)
        assertEquals(2, merged.urls.size)
    }
}
