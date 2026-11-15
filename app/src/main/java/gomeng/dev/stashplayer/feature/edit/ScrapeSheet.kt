package gomeng.dev.stashplayer.feature.edit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import gomeng.dev.stashplayer.R
import gomeng.dev.stashplayer.core.debug.StashDebugLogBuffer
import gomeng.dev.stashplayer.core.network.EditableItem
import gomeng.dev.stashplayer.core.network.EditableKind
import gomeng.dev.stashplayer.core.network.ScrapeMethod
import gomeng.dev.stashplayer.core.network.ScrapedItem
import gomeng.dev.stashplayer.core.network.StashItemEditing
import gomeng.dev.stashplayer.core.network.StashScraper
import gomeng.dev.stashplayer.core.network.matchesUrl
import kotlinx.coroutines.launch

/** Where the sheet is: choosing a scraper, picking one of several results, or choosing the fields to take. */
private sealed interface ScrapeStage {
    data object Choose : ScrapeStage
    data object Loading : ScrapeStage
    data class Results(val results: List<ScrapedItem>) : ScrapeStage
    data class Merge(val result: ScrapedItem) : ScrapeStage
}

/**
 * Scrape the item with the server's scrapers: by URL, by the item itself (fragment), or by a search query (also
 * stash-boxes). The chosen fields go into the form; nothing is saved until the user saves the item.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScrapeSheet(
    item: EditableItem,
    editing: StashItemEditing,
    onApply: (EditableItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var scrapers by remember { mutableStateOf<List<StashScraper>?>(null) }
    var stage by remember { mutableStateOf<ScrapeStage>(ScrapeStage.Choose) }
    var error by remember { mutableStateOf<String?>(null) }
    var nothingFound by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf(item.urls.firstOrNull().orEmpty()) }
    var query by remember { mutableStateOf(item.title.ifBlank { item.fileName?.substringBeforeLast('.').orEmpty() }) }

    LaunchedEffect(item.kind) {
        runCatching { editing.scrapers(item.kind) }
            .onSuccess { scrapers = it }
            .onFailure { error = it.message }
    }

    fun run(scraper: StashScraper?, method: ScrapeMethod) {
        stage = ScrapeStage.Loading
        error = null
        nothingFound = false
        scope.launch {
            runCatching { editing.scrape(item, scraper, method, query = query, url = url) }
                .onSuccess { results ->
                    nothingFound = results.isEmpty()
                    stage = when (results.size) {
                        0 -> ScrapeStage.Choose
                        1 -> ScrapeStage.Merge(results.single())
                        else -> ScrapeStage.Results(results)
                    }
                }
                .onFailure {
                    StashDebugLogBuffer.record("Edit", "Scrape with ${scraper?.name ?: "URL"} failed", it)
                    stage = ScrapeStage.Choose
                    error = it.message
                }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp).heightIn(max = 640.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.edit_scrape), style = MaterialTheme.typography.titleMedium)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            if (nothingFound) Text(stringResource(R.string.edit_scrape_nothing), style = MaterialTheme.typography.bodySmall)
            when (val current = stage) {
                ScrapeStage.Loading -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                ScrapeStage.Choose -> ChooseScraper(
                    kind = item.kind,
                    scrapers = scrapers,
                    url = url,
                    query = query,
                    onUrlChange = { url = it },
                    onQueryChange = { query = it },
                    onScrape = ::run,
                )
                is ScrapeStage.Results -> {
                    Text(stringResource(R.string.edit_scrape_pick_result), style = MaterialTheme.typography.bodyMedium)
                    current.results.forEach { result ->
                        ResultRow(result) { stage = ScrapeStage.Merge(result) }
                        HorizontalDivider()
                    }
                    TextButton(onClick = { stage = ScrapeStage.Choose }) { Text(stringResource(R.string.edit_scrape_back)) }
                }
                is ScrapeStage.Merge -> MergeFields(
                    item = item,
                    result = current.result,
                    onApply = onApply,
                    onBack = { stage = ScrapeStage.Choose },
                )
            }
        }
    }
}

@Composable
private fun ChooseScraper(
    kind: EditableKind,
    scrapers: List<StashScraper>?,
    url: String,
    query: String,
    onUrlChange: (String) -> Unit,
    onQueryChange: (String) -> Unit,
    onScrape: (StashScraper?, ScrapeMethod) -> Unit,
) {
    OutlinedTextField(
        value = url,
        onValueChange = onUrlChange,
        label = { Text(stringResource(R.string.edit_scrape_url)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = Modifier.fillMaxWidth(),
    )
    val urlScraper = scrapers?.firstOrNull { ScrapeMethod.Url in it.methods && it.matchesUrl(url) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            urlScraper?.let { stringResource(R.string.edit_scrape_url_match, it.name) } ?: stringResource(R.string.edit_scrape_url_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Button(onClick = { onScrape(null, ScrapeMethod.Url) }, enabled = url.isNotBlank()) { Text(stringResource(R.string.edit_scrape_run)) }
    }
    HorizontalDivider()
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        label = { Text(stringResource(R.string.edit_scrape_query)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    when {
        scrapers == null -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        scrapers.none { ScrapeMethod.Fragment in it.methods || ScrapeMethod.Name in it.methods } -> Text(
            stringResource(R.string.edit_scrape_no_scrapers),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        else -> scrapers.filter { ScrapeMethod.Fragment in it.methods || ScrapeMethod.Name in it.methods }.forEach { scraper ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(scraper.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (scraper.stashBoxEndpoint != null) {
                        Text(stringResource(R.string.edit_scrape_stash_box), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (ScrapeMethod.Fragment in scraper.methods) {
                    OutlinedButton(onClick = { onScrape(scraper, ScrapeMethod.Fragment) }) {
                        Text(
                            stringResource(
                                when (kind) {
                                    EditableKind.Scene -> R.string.edit_scrape_this_scene
                                    EditableKind.Gallery -> R.string.edit_scrape_this_gallery
                                    EditableKind.Image -> R.string.edit_scrape_this_image
                                },
                            ),
                        )
                    }
                }
                if (ScrapeMethod.Name in scraper.methods) {
                    TextButton(onClick = { onScrape(scraper, ScrapeMethod.Name) }, enabled = query.isNotBlank()) {
                        Text(stringResource(R.string.edit_scrape_search))
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(result: ScrapedItem, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp)) {
        Text(result.title ?: "–", style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            listOfNotNull(result.date, result.studio?.name, result.performers.takeIf { it.isNotEmpty() }?.joinToString { it.name }).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MergeFields(item: EditableItem, result: ScrapedItem, onApply: (EditableItem) -> Unit, onBack: () -> Unit) {
    val changes = remember(item, result) { scrapeChanges(item, result) }
    var chosen by remember(changes) { mutableStateOf(changes.filter { it.takeByDefault }.map { it.field }.toSet()) }
    if (changes.isEmpty()) {
        Text(stringResource(R.string.edit_scrape_no_changes), style = MaterialTheme.typography.bodyMedium)
    }
    changes.forEach { change ->
        Row(verticalAlignment = Alignment.Top) {
            Checkbox(checked = change.field in chosen, onCheckedChange = { checked -> chosen = if (checked) chosen + change.field else chosen - change.field })
            Column(modifier = Modifier.weight(1f).padding(top = 12.dp)) {
                Text(stringResource(change.field.label(item.kind)), style = MaterialTheme.typography.titleSmall)
                Text(
                    if (change.field == ScrapeField.Cover) stringResource(R.string.edit_scrape_cover_found) else change.scraped,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                if (change.current.isNotBlank() && change.field !in LIST_FIELDS) {
                    Text(
                        stringResource(R.string.edit_scrape_current, change.current),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.edit_scrape_back)) }
        Button(onClick = { onApply(applyScrape(item, result, chosen)) }, enabled = chosen.isNotEmpty(), modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.edit_scrape_apply))
        }
    }
}

/** Fields whose new values are added to the existing ones, so "current" isn't shown as replaced. */
private val LIST_FIELDS = setOf(ScrapeField.Performers, ScrapeField.Tags, ScrapeField.Urls)

private fun ScrapeField.label(kind: EditableKind): Int = when (this) {
    ScrapeField.Title -> R.string.edit_field_title
    ScrapeField.Date -> R.string.edit_field_date
    ScrapeField.Code -> R.string.edit_field_code
    ScrapeField.Creator -> if (kind == EditableKind.Scene) R.string.edit_field_director else R.string.edit_field_photographer
    ScrapeField.Details -> R.string.edit_field_details
    ScrapeField.Studio -> R.string.edit_field_studio
    ScrapeField.Performers -> R.string.edit_field_performers
    ScrapeField.Tags -> R.string.edit_field_tags
    ScrapeField.Urls -> R.string.edit_field_urls
    ScrapeField.Cover -> R.string.edit_new_cover
}
