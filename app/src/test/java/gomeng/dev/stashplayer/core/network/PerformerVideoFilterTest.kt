package gomeng.dev.stashplayer.core.network

import gomeng.dev.stashplayer.core.model.PerformerListScope
import gomeng.dev.stashplayer.core.model.PerformerListSort
import gomeng.dev.stashplayer.core.model.StashSelectedEntity
import gomeng.dev.stashplayer.core.model.StashSortDirection
import gomeng.dev.stashplayer.core.model.StashVideoFilterState
import gomeng.dev.stashplayer.core.model.deserializeStashVideoFilterState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PerformerVideoFilterTest {
    private val performers = listOf(StashSelectedEntity("7", "Jane Doe"), StashSelectedEntity("9", "A, B: C"))
    private val studios = listOf(StashSelectedEntity("3", "Studio X"))

    @Test
    fun `performers and studios become INCLUDES criteria`() {
        val variables = buildFindScenesVariables(
            perPage = 20,
            page = 1,
            query = null,
            sort = "date",
            direction = StashSortDirection.Desc,
            videoFilter = StashVideoFilterState(performers = performers, studios = studios),
        )

        @Suppress("UNCHECKED_CAST")
        val sceneFilter = variables["sceneFilter"] as Map<String, Any?>
        assertEquals(mapOf("value" to listOf("7", "9"), "modifier" to "INCLUDES"), sceneFilter["performers"])
        assertEquals(mapOf("value" to listOf("3"), "modifier" to "INCLUDES", "depth" to 0), sceneFilter["studios"])
    }

    @Test
    fun `empty filter sends no scene filter`() {
        val variables = buildFindScenesVariables(
            perPage = 20,
            page = 1,
            query = null,
            sort = "date",
            direction = StashSortDirection.Desc,
        )
        assertNull(variables["sceneFilter"])
    }

    @Test
    fun `performers and studios survive storage round trip`() {
        val original = StashVideoFilterState(performers = performers, studios = studios)
        val restored = deserializeStashVideoFilterState(original.serializeForStorage())
        assertEquals(performers, restored.performers)
        assertEquals(studios, restored.studios)
    }

    @Test
    fun `following scope filters favorites`() {
        val following = buildFindPerformerCardsVariables(40, 1, null, PerformerListScope.Following, PerformerListSort.Name)
        val all = buildFindPerformerCardsVariables(40, 1, " ", PerformerListScope.All, PerformerListSort.MostScenes)

        assertEquals(mapOf("filter_favorites" to true), following["performerFilter"])
        assertEquals("ASC", following["direction"])
        assertNull(all["performerFilter"])
        assertNull(all["q"])
        assertEquals("scenes_count", all["sort"])
    }

    @Test
    fun `random sort is seeded so pages stay stable`() {
        val first = buildFindPerformerCardsVariables(40, 1, null, PerformerListScope.All, PerformerListSort.Random, randomSeed = 42)
        val second = buildFindPerformerCardsVariables(40, 2, null, PerformerListScope.All, PerformerListSort.Random, randomSeed = 42)
        assertEquals("random_42", first["sort"])
        assertEquals(first["sort"], second["sort"])
    }
}
