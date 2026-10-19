package gomeng.dev.stashplayer.core.model

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ForYouChainedFeedTest {
    private fun scene(id: String, performers: List<String> = emptyList(), tags: List<String> = emptyList()) = SceneCardModel(
        id = id,
        title = id,
        durationText = "",
        studio = "",
        progress = 0f,
        performerChips = performers.map { SceneCardTagChip(it, it) },
        tagChips = tags.map { SceneCardTagChip(it, it) },
    )

    private val randomScenes = (1..50).map { scene("r$it") }

    private fun randomPages(page: Int, perPage: Int) = randomScenes.drop((page - 1) * perPage).take(perPage)

    @Test
    fun `without signals every pick is random`() = runBlocking {
        val feed = ForYouChainedFeed(
            fetchRelated = { error("must not query related scenes without signals") },
            fetchRandomPage = ::randomPages,
            random = Random(1),
        )
        val batch = feed.nextBatch(5)
        assertEquals(listOf("r1", "r2", "r3", "r4", "r5"), batch.map { it.id })
    }

    @Test
    fun `chain rate 1 picks related scenes of the played performer`() = runBlocking {
        val requested = mutableListOf<StashVideoFilterState>()
        val related = listOf(scene("a1", performers = listOf("p1")), scene("a2", performers = listOf("p1")))
        val feed = ForYouChainedFeed(
            fetchRelated = { filter -> requested += filter; if (filter.performers.isNotEmpty()) related else emptyList() },
            fetchRandomPage = ::randomPages,
            chainRate = 1.0,
            random = Random(7),
        )
        feed.onPlay(scene("start", performers = listOf("p1"), tags = listOf("t1")))
        val batch = feed.nextBatch(3)

        assertEquals(setOf("a1", "a2"), batch.take(2).map { it.id }.toSet())
        // Related pool used up, so the third pick falls back to random.
        assertEquals("r1", batch[2].id)
        assertEquals(listOf("p1"), requested.first { it.performers.isNotEmpty() }.performers.map { it.id })
        assertTrue("tag queries match any tag", requested.first { it.tags.isNotEmpty() }.tagsMatchAny)
    }

    @Test
    fun `visited scenes are never picked again`() = runBlocking {
        val feed = ForYouChainedFeed(
            fetchRelated = { listOf(scene("start", performers = listOf("p1")), scene("a1", performers = listOf("p1"))) },
            fetchRandomPage = ::randomPages,
            chainRate = 1.0,
            random = Random(3),
        )
        feed.onPlay(scene("start", performers = listOf("p1")))
        val batch = feed.nextBatch(1)
        assertEquals("a1", batch.single().id)
    }

    @Test
    fun `weights decay and the dominant streak forces a branch`() = runBlocking {
        val feed = ForYouChainedFeed(
            fetchRelated = { listOf(scene("a1", performers = listOf("p1"))) },
            fetchRandomPage = ::randomPages,
            chainRate = 1.0,
            branchThreshold = 2,
            random = Random(5),
        )
        feed.onPlay(scene("s1", performers = listOf("p1")))
        feed.onPlay(scene("s2", performers = listOf("p1")))
        assertEquals(2, feed.dominantStreak)
        assertEquals(1.0 * 0.85 + 1.0, feed.performerSignal.getValue("p1"), 1e-9)

        val batch = feed.nextBatch(1)
        assertEquals("forced branch picks random", "r1", batch.single().id)
        assertEquals(0, feed.dominantStreak)
    }

    @Test
    fun `related queries keep the base constraints`() = runBlocking {
        val base = StashVideoFilterState(durationRange = StashDurationRange(maxSeconds = 60))
        val requested = mutableListOf<StashVideoFilterState>()
        val feed = ForYouChainedFeed(
            fetchRelated = { requested += it; emptyList() },
            fetchRandomPage = ::randomPages,
            chainRate = 1.0,
            random = Random(2),
            relatedBaseFilter = base,
        )
        feed.onPlay(scene("s1", performers = listOf("p1"), tags = listOf("t1")))
        feed.nextBatch(1)
        assertTrue(requested.isNotEmpty())
        assertTrue(requested.all { it.durationRange == base.durationRange })
    }
}
