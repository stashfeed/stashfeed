package gomeng.dev.stashplayer.core.ui.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.Icons
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun StashEmptyState(
    state: StashEmptyStateModel,
    modifier: Modifier = Modifier,
    onPrimaryAction: (() -> Unit)? = null,
    icon: ImageVector = Icons.Outlined.AutoAwesome,
) {
    val primaryAction = if (state.hasPrimaryAction && onPrimaryAction != null) {
        StashStateAction(label = state.primaryActionLabel.orEmpty(), onClick = onPrimaryAction)
    } else {
        null
    }
    val panel = stashStatePanelModel(
        title = state.title,
        message = state.message,
        actionLabels = stashRenderedStateActionLabels(
            primaryActionLabel = state.primaryActionLabel,
            hasPrimaryAction = primaryAction != null,
            secondaryActionLabel = null,
            hasSecondaryAction = false,
        ),
        tone = StashStatePanelTone.Empty,
    )
    StashStatePanel(
        model = panel,
        modifier = modifier,
        icon = icon,
        primaryAction = primaryAction,
    )
}

@Composable
fun StashErrorState(
    state: StashErrorStateModel,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    onSecondaryAction: (() -> Unit)? = null,
    icon: ImageVector = Icons.Outlined.CloudOff,
) {
    val primaryAction = if (state.hasPrimaryAction && onRetry != null) {
        StashStateAction(label = state.primaryActionLabel, onClick = onRetry)
    } else {
        null
    }
    val secondaryAction = if (state.hasSecondaryAction && onSecondaryAction != null) {
        StashStateAction(label = state.secondaryActionLabel.orEmpty(), onClick = onSecondaryAction)
    } else {
        null
    }
    val panel = stashStatePanelModel(
        title = state.title,
        message = state.message,
        actionLabels = stashRenderedStateActionLabels(
            primaryActionLabel = state.primaryActionLabel,
            hasPrimaryAction = primaryAction != null,
            secondaryActionLabel = state.secondaryActionLabel,
            hasSecondaryAction = secondaryAction != null,
        ),
        tone = StashStatePanelTone.Error,
    )
    StashStatePanel(
        model = panel,
        modifier = modifier,
        icon = icon,
        primaryAction = primaryAction,
        secondaryAction = secondaryAction,
    )
}

@Composable
fun StashSectionHeader(
    state: StashSectionHeaderModel,
    modifier: Modifier = Modifier,
    onActionClick: (() -> Unit)? = null,
) {
    val presentation = stashSectionHeaderPresentation(state)
    StashSectionHeaderV2(
        title = presentation.title,
        modifier = modifier,
        subtitle = presentation.subtitle,
        countLabel = presentation.countBadgeLabel,
        actionLabel = presentation.actionLabel,
        contentDescription = presentation.accessibilityLabel,
        onActionClick = onActionClick,
    )
}

@Composable
fun StashScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(StashSpacing.CardGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            trailing()
        }
    }
}

private data class StashStateAction(
    val label: String,
    val onClick: () -> Unit,
)

/**
 * Empty and error states: a centred icon in a ring (brand gradient when empty, error tint when something failed),
 * a short title, one line of help and the action. No card around it, so it sits calmly on the page.
 */
@Composable
private fun StashStatePanel(
    model: StashStatePanelModel,
    modifier: Modifier,
    icon: ImageVector,
    primaryAction: StashStateAction? = null,
    secondaryAction: StashStateAction? = null,
) {
    val isError = model.tone == StashStatePanelTone.Error
    val ring = if (isError) {
        androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
    } else {
        gomeng.dev.stashplayer.core.ui.theme.StashBrandGradient
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = StashSpace.XXL, horizontal = StashSpace.L)
            .semantics { contentDescription = model.accessibilityLabel },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(StashSpace.M),
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(72.dp)
                .background(ring, androidx.compose.foundation.shape.CircleShape)
                .padding(2.5.dp)
                .background(MaterialTheme.colorScheme.surfaceContainer, androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(
                icon,
                contentDescription = null,
                tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(30.dp),
            )
        }
        Text(
            text = model.title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (model.message.isNotBlank()) {
            Text(
                text = model.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.widthIn(max = 320.dp),
            )
        }
        if (primaryAction != null || secondaryAction != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(StashSpacing.ChipGap),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = StashSpace.XS),
            ) {
                if (primaryAction != null) {
                    if (isError) {
                        StashSecondaryButton(text = primaryAction.label, onClick = primaryAction.onClick)
                    } else {
                        StashPrimaryButton(text = primaryAction.label, onClick = primaryAction.onClick)
                    }
                }
                if (secondaryAction != null) {
                    StashGhostButton(text = secondaryAction.label, onClick = secondaryAction.onClick)
                }
            }
        }
    }
}

@Composable
private fun StashStatePanelModel.titleColor(): Color = when (tone) {
    StashStatePanelTone.Empty -> MaterialTheme.colorScheme.onSurface
    StashStatePanelTone.Error -> MaterialTheme.colorScheme.error
}
