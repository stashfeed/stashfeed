package gomeng.dev.stashplayer.core.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScenePreviewFallbackTest {
    @Test
    fun `preview url maps to the stream and keeps the api key`() {
        assertEquals(
            "http://stash:9999/scene/42/stream?apikey=abc",
            previewStreamUrl("http://stash:9999/scene/42/preview?apikey=abc"),
        )
        assertEquals("http://stash/scene/7/stream", previewStreamUrl("http://stash/scene/7/preview"))
        assertNull(previewStreamUrl("http://stash/image/7/preview"))
    }

    @Test
    fun `duration text parses minutes and hours`() {
        assertEquals(3_406_000L, "56:46".toDurationMsOrNull())
        assertEquals(3_723_000L, "1:02:03".toDurationMsOrNull())
        assertNull("--".toDurationMsOrNull())
    }
}
