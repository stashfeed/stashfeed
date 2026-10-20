package gomeng.dev.stashplayer.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import gomeng.dev.stashplayer.R
import gomeng.dev.stashplayer.core.ui.i18n.stashString
import gomeng.dev.stashplayer.core.ui.theme.StashBrandGradient

/** What the avatar in the top bars shows and opens: the "You" page with library and settings. */
@Immutable
data class StashAvatarTarget(
    val initial: String,
    val showBadge: Boolean,
    val onClick: () -> Unit,
)

val LocalStashAvatarTarget = staticCompositionLocalOf<StashAvatarTarget?> { null }

/** Round avatar with the brand ring; replaces the Library tab. Renders nothing when no target is provided. */
@Composable
fun StashAvatarButton(modifier: Modifier = Modifier) {
    val target = LocalStashAvatarTarget.current ?: return
    val label = stashString(R.string.avatar_open_library)
    Box(modifier = modifier.size(44.dp), contentAlignment = Alignment.Center) {
        Surface(
            onClick = target.onClick,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .size(34.dp)
                .background(StashBrandGradient, CircleShape)
                .padding(2.dp)
                .semantics { contentDescription = label },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(target.initial, style = MaterialTheme.typography.labelLarge)
            }
        }
        if (target.showBadge) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(9.dp)
                    .background(MaterialTheme.colorScheme.background, CircleShape)
                    .padding(1.5.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
    }
}
