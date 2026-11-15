package gomeng.dev.stashplayer.feature.edit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import gomeng.dev.stashplayer.R
import gomeng.dev.stashplayer.core.network.EditableRef
import gomeng.dev.stashplayer.core.network.StashItemEditing
import kotlinx.coroutines.delay

enum class PickerKind { Studio, Performer, Tag }

/** Typing pauses this long before the server is asked, so every key press doesn't send a search. */
private const val SEARCH_DEBOUNCE_MS = 300L

/**
 * Search the library for a studio, performer or tag and pick it. A name that doesn't exist yet can be added as new;
 * it is created when the item is saved.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EntityPickerSheet(
    kind: PickerKind,
    editing: StashItemEditing,
    selected: List<EditableRef>,
    onPick: (EditableRef) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<EditableRef>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(query) {
        delay(SEARCH_DEBOUNCE_MS)
        loading = true
        runCatching {
            when (kind) {
                PickerKind.Studio -> editing.searchStudios(query)
                PickerKind.Performer -> editing.searchPerformers(query)
                PickerKind.Tag -> editing.searchTags(query)
            }
        }
            .onSuccess {
                results = it
                error = null
            }
            .onFailure { error = it.message }
        loading = false
    }

    val selectedKeys = selected.map { it.id ?: it.name.lowercase() }.toSet()
    val trimmed = query.trim()
    val exactMatch = results.any { it.name.equals(trimmed, ignoreCase = true) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(
                    when (kind) {
                        PickerKind.Studio -> R.string.edit_pick_studio
                        PickerKind.Performer -> R.string.edit_pick_performers
                        PickerKind.Tag -> R.string.edit_pick_tags
                    },
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.edit_search)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                if (trimmed.isNotEmpty() && !exactMatch) {
                    item {
                        PickerRow(
                            title = stringResource(R.string.edit_create_entity, trimmed),
                            detail = null,
                            icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                            onClick = {
                                onPick(EditableRef(id = null, name = trimmed))
                                query = ""
                            },
                        )
                    }
                }
                items(results, key = { it.id ?: it.name }) { ref ->
                    val picked = (ref.id ?: ref.name.lowercase()) in selectedKeys
                    PickerRow(
                        title = ref.name,
                        detail = ref.detail,
                        icon = if (picked) ({ Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }) else null,
                        onClick = { if (!picked) onPick(ref) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PickerRow(title: String, detail: String?, icon: (@Composable () -> Unit)?, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        icon?.invoke()
    }
}
