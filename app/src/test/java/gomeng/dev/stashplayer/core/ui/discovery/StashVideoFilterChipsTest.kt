package gomeng.dev.stashplayer.core.ui.discovery

import gomeng.dev.stashplayer.core.model.StashSavedFilterRef
import gomeng.dev.stashplayer.core.model.StashSelectedEntity
import gomeng.dev.stashplayer.core.model.StashSelectedTag
import gomeng.dev.stashplayer.core.model.StashVideoFilterCategory
import gomeng.dev.stashplayer.core.model.StashVideoFilterState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StashVideoFilterChipsTest {
    private val filter = StashVideoFilterState(
        tags = listOf(StashSelectedTag("1", "Beach")),
        performers = listOf(StashSelectedEntity("7", "Ann")),
        localFavoriteOnly = true,
        savedFilter = StashSavedFilterRef(id = "stash:3", name = "Mine", criteria = mapOf("organized" to true)),
    )

    @Test
    fun `clearing a category leaves the others`() {
        val cleared = filter.cleared(StashVideoFilterCategory.Performer)
        assertTrue(cleared.performers.isEmpty())
        assertEquals(filter.tags, cleared.tags)
        assertEquals(filter.savedFilter, cleared.savedFilter)
        assertTrue(cleared.localFavoriteOnly)
    }

    @Test
    fun `every category maps to a filter screen section`() {
        assertEquals(StashVideoFilterSectionKeys.People, StashVideoFilterCategory.Studio.sectionKey())
        assertEquals(StashVideoFilterSectionKeys.DateDuration, StashVideoFilterCategory.OCounter.sectionKey())
        assertEquals(StashVideoFilterSectionKeys.Saved, StashVideoFilterCategory.SavedFilter.sectionKey())
    }

    @Test
    fun `header chips clear their own category`() {
        var applied: StashVideoFilterState? = null
        val chips = filter.headerChips(onOpenSection = {}, onApply = { applied = it })
        chips.first { it.key == StashVideoFilterCategory.Tag.id }.onClear!!.invoke()
        assertTrue(applied!!.tags.isEmpty())
        assertEquals(filter.performers, applied!!.performers)
    }
}
