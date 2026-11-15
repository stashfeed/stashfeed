package gomeng.dev.stashplayer.feature.edit

import androidx.activity.compose.BackHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material.icons.outlined.Add
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import gomeng.dev.stashplayer.R
import gomeng.dev.stashplayer.core.debug.StashDebugLogBuffer
import gomeng.dev.stashplayer.core.network.EditableItem
import gomeng.dev.stashplayer.core.network.EditableKind
import gomeng.dev.stashplayer.core.network.EditableRef
import gomeng.dev.stashplayer.core.network.StashGraphQlClient
import gomeng.dev.stashplayer.core.network.StashItemEditing
import gomeng.dev.stashplayer.core.network.StashSettingsRepository
import gomeng.dev.stashplayer.core.ui.designsystem.StashRadius
import gomeng.dev.stashplayer.core.ui.designsystem.StashSpace
import gomeng.dev.stashplayer.core.ui.designsystem.StashSpacing
import gomeng.dev.stashplayer.core.ui.designsystem.StashTopBar
import kotlinx.coroutines.launch

/** Matches what Stash accepts as a date: a year, a year and month, or a full date. */
private val STASH_DATE = Regex("""^\d{4}(-\d{2}(-\d{2})?)?$""")

/**
 * Edit a scene, gallery or image like in Stash's web UI: every field, studio, performers and tags (created on save
 * when new), and scraping with the server's scrapers. Nothing is written until Save.
 */
@Composable
fun EditItemRoute(
    kind: EditableKind,
    id: String,
    isFoldLikeLayout: Boolean,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val settingsRepository = remember(context) { StashSettingsRepository(context) }
    val profile by settingsRepository.serverProfile.collectAsState(initial = null)
    val editing = remember(profile) { profile?.takeIf { it.isConfigured() }?.let { StashItemEditing(StashGraphQlClient(it)) } }
    val scope = rememberCoroutineScope()

    var original by remember { mutableStateOf<EditableItem?>(null) }
    var draft by remember { mutableStateOf<EditableItem?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var confirmLeave by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf<PickerKind?>(null) }
    var scraping by remember { mutableStateOf(false) }

    LaunchedEffect(editing, kind, id) {
        val client = editing ?: return@LaunchedEffect
        runCatching { client.load(kind, id) }
            .onSuccess {
                original = it
                draft = it
            }
            .onFailure {
                StashDebugLogBuffer.record("Edit", "Loading $kind $id failed", it)
                loadError = it.message ?: context.getString(R.string.edit_load_failed)
            }
    }

    val dirty = draft != null && draft != original
    val dateValid = draft?.date?.let { it.isBlank() || STASH_DATE.matches(it.trim()) } ?: true
    fun leave() {
        if (dirty) confirmLeave = true else onBack()
    }
    BackHandler(onBack = ::leave)

    fun save() {
        val client = editing ?: return
        val item = draft ?: return
        saving = true
        saveError = null
        scope.launch {
            runCatching { client.save(item) }
                .onSuccess { onBack() }
                .onFailure {
                    StashDebugLogBuffer.record("Edit", "Saving $kind $id failed", it)
                    saveError = it.message ?: context.getString(R.string.edit_save_failed)
                }
            saving = false
        }
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(stringResource(R.string.edit_discard_title)) },
            text = { Text(stringResource(R.string.edit_discard_message)) },
            confirmButton = { TextButton(onClick = { confirmLeave = false; onBack() }) { Text(stringResource(R.string.edit_discard)) } },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text(stringResource(R.string.edit_keep_editing)) } },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        StashTopBar(
            title = stringResource(
                when (kind) {
                    EditableKind.Scene -> R.string.edit_title_scene
                    EditableKind.Gallery -> R.string.edit_title_gallery
                    EditableKind.Image -> R.string.edit_title_image
                },
            ),
            navigation = {
                IconButton(onClick = ::leave) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.settings_back_button))
                }
            },
            actions = {
                IconButton(onClick = { scraping = true }, enabled = draft != null && !saving) {
                    Icon(Icons.Outlined.AutoFixHigh, contentDescription = stringResource(R.string.edit_scrape))
                }
                // The one filled button on the screen, live only once there is something to save.
                Button(onClick = ::save, enabled = dirty && dateValid && !saving, modifier = Modifier.padding(end = 8.dp)) {
                    Text(stringResource(if (saving) R.string.edit_saving else R.string.edit_save))
                }
            },
        )
        val unsaved = changedFieldCount(original, draft)
        if (unsaved > 0 && !saving) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (isFoldLikeLayout) StashSpacing.ScreenHorizontalExpanded else StashSpacing.ScreenHorizontalCompact),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    pluralStringResource(R.plurals.edit_unsaved_changes, unsaved, unsaved),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { draft = original }) { Text(stringResource(R.string.edit_discard)) }
            }
        }
        val current = draft
        when {
            loadError != null -> Text(
                loadError.orEmpty(),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(StashSpace.L),
            )
            current == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else -> EditForm(
                item = current,
                dateValid = dateValid,
                saveError = saveError,
                isFoldLikeLayout = isFoldLikeLayout,
                onChange = { draft = it },
                onPick = { picker = it },
            )
        }
    }

    val current = draft
    if (current != null && editing != null) {
        picker?.let { kindToPick ->
            EntityPickerSheet(
                kind = kindToPick,
                editing = editing,
                selected = when (kindToPick) {
                    PickerKind.Studio -> listOfNotNull(current.studio)
                    PickerKind.Performer -> current.performers
                    PickerKind.Tag -> current.tags
                },
                onPick = { ref ->
                    draft = when (kindToPick) {
                        PickerKind.Studio -> current.copy(studio = ref)
                        PickerKind.Performer -> current.copy(performers = (current.performers + ref).distinctBy { it.id ?: it.name.lowercase() })
                        PickerKind.Tag -> current.copy(tags = (current.tags + ref).distinctBy { it.id ?: it.name.lowercase() })
                    }
                    if (kindToPick == PickerKind.Studio) picker = null
                },
                onDismiss = { picker = null },
            )
        }
        if (scraping) {
            ScrapeSheet(
                item = current,
                editing = editing,
                onApply = { merged ->
                    draft = merged
                    scraping = false
                },
                onDismiss = { scraping = false },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditForm(
    item: EditableItem,
    dateValid: Boolean,
    saveError: String?,
    isFoldLikeLayout: Boolean,
    onChange: (EditableItem) -> Unit,
    onPick: (PickerKind) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (isFoldLikeLayout) StashSpacing.ScreenHorizontalExpanded else StashSpacing.ScreenHorizontalCompact)
            .padding(top = StashSpace.S, bottom = StashSpacing.BottomContentPadding),
        verticalArrangement = Arrangement.spacedBy(StashSpace.M),
    ) {
        saveError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        item.fileName?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        EditCard {
            OutlinedTextField(
                value = item.title,
                onValueChange = { onChange(item.copy(title = it)) },
                label = { Text(stringResource(R.string.edit_field_title)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(StashSpace.S)) {
                OutlinedTextField(
                    value = item.date,
                    onValueChange = { onChange(item.copy(date = it)) },
                    label = { Text(stringResource(R.string.edit_field_date)) },
                    placeholder = { Text("YYYY-MM-DD") },
                    isError = !dateValid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = item.code,
                    onValueChange = { onChange(item.copy(code = it)) },
                    label = { Text(stringResource(R.string.edit_field_code)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                value = item.creator,
                onValueChange = { onChange(item.copy(creator = it)) },
                label = { Text(stringResource(if (item.kind == EditableKind.Scene) R.string.edit_field_director else R.string.edit_field_photographer)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            RatingRow(rating100 = item.rating100, onChange = { onChange(item.copy(rating100 = it)) })
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.edit_field_organized), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = item.organized, onCheckedChange = { onChange(item.copy(organized = it)) })
            }
        }

        EditCard {
            SectionHeader(stringResource(R.string.edit_field_studio), stringResource(R.string.edit_choose)) { onPick(PickerKind.Studio) }
            item.studio?.let { studio ->
                RefChip(studio) { onChange(item.copy(studio = null)) }
            } ?: Text(stringResource(R.string.edit_none), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            SectionHeader(stringResource(R.string.edit_field_performers), stringResource(R.string.edit_add)) { onPick(PickerKind.Performer) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item.performers.forEach { ref -> RefChip(ref) { onChange(item.copy(performers = item.performers - ref)) } }
            }
            SectionHeader(stringResource(R.string.edit_field_tags), stringResource(R.string.edit_add)) { onPick(PickerKind.Tag) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item.tags.forEach { ref -> RefChip(ref) { onChange(item.copy(tags = item.tags - ref)) } }
            }
        }

        EditCard {
            Text(stringResource(R.string.edit_field_urls), style = MaterialTheme.typography.titleSmall)
            item.urls.forEachIndexed { index, url ->
                OutlinedTextField(
                    value = url,
                    onValueChange = { value -> onChange(item.copy(urls = item.urls.toMutableList().also { it[index] = value })) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    trailingIcon = {
                        IconButton(onClick = { onChange(item.copy(urls = item.urls.filterIndexed { i, _ -> i != index })) }) {
                            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.edit_remove))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            TextButton(onClick = { onChange(item.copy(urls = item.urls + "")) }) { Text(stringResource(R.string.edit_add_url)) }
        }

        EditCard {
            OutlinedTextField(
                value = item.details,
                onValueChange = { onChange(item.copy(details = it)) },
                label = { Text(stringResource(R.string.edit_field_details)) },
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item.coverImage?.let { cover ->
            EditCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.edit_new_cover), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onChange(item.copy(coverImage = null)) }) { Text(stringResource(R.string.edit_remove)) }
                }
                AsyncImage(model = cover, contentDescription = null, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun EditCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(StashRadius.M),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}

@Composable
private fun SectionHeader(title: String, action: String, onAction: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        TextButton(onClick = onAction) { Text(action) }
    }
}

/** A performer, tag or studio; new ones (created on save) are marked. */
@Composable
internal fun RefChip(ref: EditableRef, onRemove: (() -> Unit)? = null) {
    val isNew = ref.id == null
    InputChip(
        selected = false,
        onClick = { onRemove?.invoke() },
        label = {
            Text(
                if (isNew) stringResource(R.string.edit_new_entity, ref.name) else listOfNotNull(ref.name, ref.detail).joinToString(" · "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        // New ones get the accent edge and a "+", so it is clear they will be created on save.
        leadingIcon = if (isNew) ({ Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp)) }) else null,
        border = if (isNew) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)) else InputChipDefaults.inputChipBorder(enabled = true, selected = false),
        trailingIcon = onRemove?.let { { Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.edit_remove), modifier = Modifier.size(16.dp)) } },
    )
}

/** How many fields differ between what was loaded and the form, for the unsaved-changes line. */
private fun changedFieldCount(original: EditableItem?, draft: EditableItem?): Int {
    if (original == null || draft == null) return 0
    return listOf<(EditableItem) -> Any?>(
        { it.title }, { it.code }, { it.date }, { it.details }, { it.creator }, { it.urls }, { it.rating100 },
        { it.organized }, { it.studio }, { it.performers }, { it.tags }, { it.coverImage },
    ).count { field -> field(original) != field(draft) }
}

/** Five stars in Stash's 0–100 scale (20 per star); tapping the current rating clears it. */
@Composable
private fun RatingRow(rating100: Int?, onChange: (Int?) -> Unit) {
    val stars = rating100?.let { (it + 10) / 20 } ?: 0
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.edit_field_rating), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        (1..5).forEach { star ->
            IconButton(onClick = { onChange(if (star == stars) null else star * 20) }, modifier = Modifier.size(48.dp)) {
                Icon(
                    if (star <= stars) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    contentDescription = stringResource(R.string.edit_rating_stars, star),
                    tint = if (star <= stars) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
