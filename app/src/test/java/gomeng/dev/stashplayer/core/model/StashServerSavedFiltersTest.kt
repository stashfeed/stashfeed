package gomeng.dev.stashplayer.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class StashServerSavedFiltersTest {
    @Test
    fun `labelled id lists become id lists with excludes and depth`() {
        val converted = convertSavedObjectFilter(
            mapOf(
                "tags" to mapOf(
                    "modifier" to "INCLUDES",
                    "value" to mapOf(
                        "items" to listOf(mapOf("id" to "1", "label" to "Beach"), mapOf("id" to "2", "label" to "POV")),
                        "excluded" to listOf(mapOf("id" to "9", "label" to "Bad")),
                        "depth" to 0,
                    ),
                ),
            ),
        )
        assertEquals(
            mapOf("modifier" to "INCLUDES", "value" to listOf("1", "2"), "excludes" to listOf("9"), "depth" to 0),
            converted["tags"],
        )
    }

    @Test
    fun `nested value pairs are flattened`() {
        val converted = convertSavedObjectFilter(
            mapOf("rating100" to mapOf("modifier" to "BETWEEN", "value" to mapOf("value" to 60, "value2" to 100))),
        )
        assertEquals(mapOf("modifier" to "BETWEEN", "value" to 60, "value2" to 100), converted["rating100"])
    }

    @Test
    fun `boolean criteria become booleans`() {
        val converted = convertSavedObjectFilter(
            mapOf(
                "organized" to mapOf("modifier" to "EQUALS", "value" to "true"),
                "performer_favorite" to true,
            ),
        )
        assertEquals(true, converted["organized"])
        assertEquals(true, converted["performer_favorite"])
    }

    @Test
    fun `plain string criteria pass through`() {
        val title = mapOf("modifier" to "INCLUDES", "value" to "beach")
        assertEquals(title, convertSavedObjectFilter(mapOf("title" to title))["title"])
    }
}
