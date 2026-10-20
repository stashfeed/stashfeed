package gomeng.dev.stashplayer.feature.performers

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import gomeng.dev.stashplayer.R
import gomeng.dev.stashplayer.core.ui.components.stashSharedElement
import gomeng.dev.stashplayer.core.model.PerformerCardModel
import gomeng.dev.stashplayer.core.network.StashServerProfile
import gomeng.dev.stashplayer.core.ui.components.rememberStashThumbnailModel
import gomeng.dev.stashplayer.core.ui.designsystem.StashRadii
import gomeng.dev.stashplayer.core.ui.i18n.stashString

/** Performer photo with an initial-letter fallback while there is no image. */
/** Shared-element key of a performer's photo: the list tile morphs into the profile header. */
internal fun performerImageSharedKey(performerId: String) = "performer-image-$performerId"

@Composable
fun PerformerImage(
    name: String,
    imageUrl: String?,
    serverProfile: StashServerProfile?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(StashRadii.Thumbnail),
) {
    val model = rememberStashThumbnailModel(imageUrl, serverProfile)
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.firstOrNull()?.uppercase().orEmpty(),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun PerformerTile(
    performer: PerformerCardModel,
    serverProfile: StashServerProfile?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(StashRadii.Thumbnail))
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box {
            PerformerImage(
                name = performer.name,
                imageUrl = performer.imageUrl,
                serverProfile = serverProfile,
                modifier = Modifier
                    .stashSharedElement(performerImageSharedKey(performer.id))
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f),
            )
            if (performer.favorite) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = stashString(R.string.performer_following),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), CircleShape)
                        .padding(4.dp)
                        .size(16.dp),
                )
            }
        }
        Column(modifier = Modifier.padding(horizontal = 2.dp)) {
            Text(
                text = performer.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stashString(R.string.performers_scene_count, performer.sceneCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}
