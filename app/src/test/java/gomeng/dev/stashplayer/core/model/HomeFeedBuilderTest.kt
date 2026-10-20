package gomeng.dev.stashplayer.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeFeedBuilderTest {
    private val jane = FeedPerformer("p1", "Jane", gender = StashPerformerGender.Female)
    private val joe = FeedPerformer("p2", "Joe", gender = StashPerformerGender.Male, favorite = true)

    private fun scene(id: String, createdAt: String, performers: List<FeedPerformer> = listOf(jane), date: String? = null) = FeedScene(
        scene = SceneCardModel(id = id, title = id, durationText = "", studio = "", progress = 0f),
        performers = performers,
        createdAt = createdAt,
        date = date,
    )

    private fun gallery(id: String, createdAt: String, paths: List<String> = emptyList(), performers: List<FeedPerformer> = listOf(jane)) =
        FeedGallery(GalleryCardModel(id = id, title = id), performers, createdAt, paths)

    private fun day(d: Int, hour: Int = 12) = "2026-10-%02dT%02d:00:00+02:00".format(d, hour)

    @Test
    fun `newest added first, reposts flagged by release date`() {
        val feed = buildHomeFeed(
            scenes = listOf(
                scene("old-release", day(10), date = "2019-01-01"),
                scene("fresh", day(12), date = "2026-10-11"),
            ),
            galleries = listOf(gallery("g1", day(11))),
            settings = HomeFeedSettings(),
            windowStartDate = "2026-09-19",
        )
        assertEquals(listOf("scene:fresh", "gallery:g1", "scene:old-release"), feed.items.map { it.key })
        val repost = feed.items.last() as HomeFeedItem.Post
        assertTrue(repost.isRepost)
    }

    @Test
    fun `many scenes of one performer stay separate posts`() {
        val burst = (1..8).map { scene("b$it", day(10 + it % 7, hour = it)) }
        val feed = buildHomeFeed(burst, emptyList(), HomeFeedSettings(), "2026-09-01")

        assertEquals(8, feed.items.size)
        assertTrue(feed.items.all { it is HomeFeedItem.Post })
    }

    @Test
    fun `seven scenes stay separate posts`() {
        val feed = buildHomeFeed((1..7).map { scene("s$it", day(10, hour = it)) }, emptyList(), HomeFeedSettings(), "2026-09-01")
        assertEquals(7, feed.items.size)
    }

    @Test
    fun `stories skip disallowed genders and list favorites first`() {
        val feed = buildHomeFeed(
            scenes = listOf(scene("a", day(12), listOf(jane, joe)), scene("b", day(13), listOf(jane))),
            galleries = emptyList(),
            settings = HomeFeedSettings(),
            windowStartDate = "2026-09-01",
        )
        assertEquals(listOf("p2", "p1"), feed.stories.map { it.performer.id })
        assertEquals(listOf("b", "a"), feed.stories[1].scenes.map { it.scene.id })

        val femaleOnly = buildHomeFeed(
            scenes = listOf(scene("a", day(12), listOf(jane, joe))),
            galleries = emptyList(),
            settings = HomeFeedSettings(allowedGenders = setOf(StashPerformerGender.Female)),
            windowStartDate = "2026-09-01",
        )
        assertEquals(listOf("p1"), femaleOnly.stories.map { it.performer.id })
    }

    @Test
    fun `scenes without performers are counted and hidden by default`() {
        val unidentified = scene("u", day(12), performers = emptyList())
        val hidden = buildHomeFeed(listOf(unidentified), emptyList(), HomeFeedSettings(), "2026-09-01")
        assertTrue(hidden.items.isEmpty())
        assertEquals(1, hidden.unidentifiedSceneCount)

        val shown = buildHomeFeed(listOf(unidentified), emptyList(), HomeFeedSettings(hiddenKinds = emptySet()), "2026-09-01")
        assertEquals(1, shown.items.size)
    }

    @Test
    fun `gallery folders matching the ignore list are dropped`() {
        val feed = buildHomeFeed(
            scenes = emptyList(),
            galleries = listOf(
                gallery("shots", day(12), paths = listOf("/data/Jane/Screenshots/set1")),
                gallery("cover", day(12), paths = listOf("C:\\data\\Jane\\cover")),
                gallery("real", day(12), paths = listOf("/data/Jane/Beach set")),
            ),
            settings = HomeFeedSettings(),
            windowStartDate = "2026-09-01",
        )
        assertEquals(listOf("gallery:real"), feed.items.map { it.key })
    }

    @Test
    fun `hidden kinds are left out`() {
        val feed = buildHomeFeed(
            scenes = listOf(scene("post", day(12)), scene("repost", day(12), date = "2001-01-01")),
            galleries = listOf(gallery("g", day(12))),
            settings = HomeFeedSettings(hiddenKinds = setOf(HomeFeedKind.Reposts, HomeFeedKind.Galleries)),
            windowStartDate = "2026-09-01",
        )
        assertEquals(listOf("scene:post"), feed.items.map { it.key })
        assertFalse(feed.stories.isEmpty())
    }

    @Test
    fun `seen posts older than the keep window are pruned`() {
        val seen = mapOf("scene:old" to "2026-06-01", "scene:edge" to "2026-07-22", "scene:new" to "2026-10-19")
        val pruned = pruneSeenFeedPosts(seen, java.time.LocalDate.parse("2026-10-20"), keepDays = 90)
        assertEquals(setOf("scene:edge", "scene:new"), pruned.keys)
    }

    @Test
    fun `feed splits into unseen and seen in feed order`() {
        val feed = buildHomeFeed(
            scenes = listOf(scene("a", day(13)), scene("b", day(12)), scene("c", day(11))),
            galleries = emptyList(),
            settings = HomeFeedSettings(),
            windowStartDate = "2026-09-01",
        )
        val sections = feed.splitBySeen(setOf("scene:b"))
        assertEquals(listOf("scene:a", "scene:c"), sections.unseen.map { it.key })
        assertEquals(listOf("scene:b"), sections.seen.map { it.key })
    }
}
