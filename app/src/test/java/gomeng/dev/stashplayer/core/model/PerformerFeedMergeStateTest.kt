package gomeng.dev.stashplayer.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformerFeedMergeStateTest {
    private fun scene(id: String, createdAt: String) = PerformerFeedItem.Scene(
        scene = SceneCardModel(id = id, title = id, durationText = "", studio = "", progress = 0f),
        date = null,
        createdAt = createdAt,
    )

    private fun gallery(id: String, createdAt: String) = PerformerFeedItem.Gallery(
        gallery = GalleryCardModel(id = id, title = id),
        createdAt = createdAt,
    )

    @Test
    fun `merges both sources newest first`() {
        val state = PerformerFeedMergeState()
            .withScenePage(PerformerFeedPage(listOf(scene("s1", "2024-05-03"), scene("s2", "2024-05-01")), totalCount = 2), perPage = 20)
            .withGalleryPage(PerformerFeedPage(listOf(gallery("g1", "2024-05-02")), totalCount = 1), perPage = 20)
            .merge()

        assertEquals(listOf("scene:s1", "gallery:g1", "scene:s2"), state.emitted.map { it.key })
        assertTrue(state.isComplete)
    }

    @Test
    fun `stops merging when a source with more pages runs dry`() {
        val state = PerformerFeedMergeState()
            .withScenePage(PerformerFeedPage(listOf(scene("s1", "2024-05-03"), scene("s2", "2024-05-01")), totalCount = 2), perPage = 20)
            // One gallery loaded, but the server has 5: the next gallery might be newer than s2.
            .withGalleryPage(PerformerFeedPage(listOf(gallery("g1", "2024-05-02")), totalCount = 5), perPage = 1)
            .merge()

        assertEquals(listOf("scene:s1", "gallery:g1"), state.emitted.map { it.key })
        assertTrue(state.needsGalleryFetch)
        assertFalse(state.needsSceneFetch)
        assertFalse(state.isComplete)
    }

    @Test
    fun `continues after the missing page arrives`() {
        val state = PerformerFeedMergeState()
            .withScenePage(PerformerFeedPage(listOf(scene("s1", "2024-05-03"), scene("s2", "2024-05-01")), totalCount = 2), perPage = 20)
            .withGalleryPage(PerformerFeedPage(listOf(gallery("g1", "2024-05-02")), totalCount = 2), perPage = 1)
            .merge()
            .withGalleryPage(PerformerFeedPage(listOf(gallery("g2", "2024-04-01")), totalCount = 2), perPage = 1)
            .merge()

        assertEquals(listOf("scene:s1", "gallery:g1", "scene:s2", "gallery:g2"), state.emitted.map { it.key })
        assertTrue(state.isComplete)
    }

    @Test
    fun `empty sources complete without items`() {
        val state = PerformerFeedMergeState()
            .withScenePage(PerformerFeedPage(emptyList(), totalCount = 0), perPage = 20)
            .withGalleryPage(PerformerFeedPage(emptyList(), totalCount = 0), perPage = 20)
            .merge()

        assertTrue(state.emitted.isEmpty())
        assertTrue(state.isComplete)
    }
}
