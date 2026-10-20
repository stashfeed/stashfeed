package gomeng.dev.stashplayer.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource

/** Scroll distance (px) in one direction before the header hides or comes back. */
private const val CHROME_TOGGLE_THRESHOLD_PX = 24f

/**
 * Hides a screen's header (tabs, search, filters) while scrolling down and brings it back on the first scroll
 * up, like mobile video sites. Attach [connection] with `Modifier.nestedScroll` above the scrolling list.
 */
@Stable
class CollapsingChromeState {
    var visible by mutableStateOf(true)
        private set
    private var accumulated = 0f

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val dy = available.y
            if (dy == 0f) return Offset.Zero
            // Reset when the direction flips so a small wiggle doesn't toggle it.
            if ((dy < 0f) != (accumulated < 0f)) accumulated = 0f
            accumulated += dy
            if (accumulated < -CHROME_TOGGLE_THRESHOLD_PX && visible) visible = false
            if (accumulated > CHROME_TOGGLE_THRESHOLD_PX && !visible) visible = true
            return Offset.Zero
        }
    }

    fun show() {
        visible = true
        accumulated = 0f
    }
}

@Composable
fun rememberCollapsingChromeState(): CollapsingChromeState = remember { CollapsingChromeState() }

/** Whether the enclosing screen's header is currently shown; screens collapse their own controls with it. */
val LocalCollapsingChromeVisible = compositionLocalOf { true }
