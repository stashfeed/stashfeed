package gomeng.dev.stashplayer.feature.downloads

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.content.ClipboardManager
import android.content.Context
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import gomeng.dev.stashplayer.core.ui.components.rememberStashThumbnailModel
import gomeng.dev.stashplayer.core.network.StashServerProfile
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.width
import android.text.format.DateUtils
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import gomeng.dev.stashplayer.R
import gomeng.dev.stashplayer.core.debug.StashDebugLogBuffer
import gomeng.dev.stashplayer.core.network.DownloaderMetadata
import gomeng.dev.stashplayer.core.network.StashDownloaderClient
import gomeng.dev.stashplayer.core.network.StashDownloaderPlugin
import gomeng.dev.stashplayer.core.network.StashGraphQlClient
import gomeng.dev.stashplayer.core.network.StashSettingsRepository
import gomeng.dev.stashplayer.core.ui.designsystem.StashRadius
import gomeng.dev.stashplayer.core.ui.designsystem.StashSpace
import gomeng.dev.stashplayer.core.ui.designsystem.StashSpacing
import gomeng.dev.stashplayer.core.ui.designsystem.StashTopBar
import kotlinx.coroutines.launch

/**
 * Download videos, images and Reddit galleries to the Stash server with the Stash Downloader plugin: paste or share
 * one link and check it first (what it is, and whether it is already in the library), or several links at once to
 * queue them all. The list below follows each download until it is in the library, and keeps the history.
 */
@Composable
fun DownloadsRoute(
    isFoldLikeLayout: Boolean,
    onBack: () -> Unit,
    onOpenScene: (String) -> Unit,
    onOpenGallery: (String) -> Unit,
) {
    val context = LocalContext.current
    val settingsRepository = remember(context) { StashSettingsRepository(context) }
    val profile by settingsRepository.serverProfile.collectAsState(initial = null)
    val downloads by StashDownloads.items.collectAsState()
    val sharedLinks by StashShareInbox.pending.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { StashDownloads.attach(context) }

    var installed by remember { mutableStateOf<Boolean?>(null) }
    // The link field takes one link, or several (one per line) for a bulk download.
    var url by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var quality by remember { mutableStateOf(StashDownloaderPlugin.QUALITIES.first()) }
    var metadata by remember { mutableStateOf<DownloaderMetadata?>(null) }
    var checking by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    // What "Check link" found: how many images a post holds, and what is already in the library under this link.
    var checkedImageCount by remember { mutableStateOf(0) }
    var duplicate by remember { mutableStateOf<LibraryMatch?>(null) }

    // Notifications report finished downloads while the app is in the background (Android 13+ asks first).
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun askForNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun resetCheck() {
        metadata = null
        checkedImageCount = 0
        duplicate = null
        errorText = null
    }

    val links = remember(url) { extractSharedUrls(url) }
    val bulk = links.size > 1
    val activeProfile = profile?.takeIf { it.isConfigured() }
    val client = remember(activeProfile) { activeProfile?.let { StashDownloaderClient(StashGraphQlClient(it)) } }

    LaunchedEffect(client) {
        val downloader = client ?: return@LaunchedEffect
        installed = runCatching { downloader.isInstalled() }.getOrDefault(false)
        if (installed == true) {
            runCatching { downloader.settings() }.getOrNull()?.quality
                ?.takeIf { it in StashDownloaderPlugin.QUALITIES }
                ?.let { quality = it }
        }
    }
    // Links shared from other apps are added to the form; the user still checks them and starts the download.
    LaunchedEffect(sharedLinks) {
        val shared = StashShareInbox.take()
        if (shared.isNotEmpty()) {
            url = (extractSharedUrls(url) + shared).distinct().joinToString("\n")
            resetCheck()
        }
    }

    fun checkLink() {
        val target = activeProfile ?: return
        val link = links.singleOrNull() ?: return
        checking = true
        resetCheck()
        scope.launch {
            runCatching { checkDownloadLink(target, link) }
                .onSuccess { check ->
                    metadata = check.metadata
                    checkedImageCount = check.imageCount
                    duplicate = check.duplicate
                    if (title.isBlank()) title = check.metadata.title.orEmpty()
                }
                .onFailure {
                    StashDebugLogBuffer.record("Downloads", "Link check failed for $link", it)
                    errorText = it.message ?: context.getString(R.string.downloads_check_failed)
                }
            checking = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        StashTopBar(
            title = stringResource(R.string.downloads_title),
            navigation = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.settings_back_button))
                }
            },
            actions = {
                if (downloads.any { !it.active }) {
                    TextButton(onClick = StashDownloads::clearFinished) { Text(stringResource(R.string.downloads_clear_finished)) }
                }
            },
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (isFoldLikeLayout) StashSpacing.ScreenHorizontalExpanded else StashSpacing.ScreenHorizontalCompact)
                .padding(top = StashSpace.S, bottom = StashSpacing.BottomContentPadding),
            verticalArrangement = Arrangement.spacedBy(StashSpace.M),
        ) {
            when {
                activeProfile == null -> InfoCard(stringResource(R.string.settings_recommendation_no_stash_server))
                installed == false -> InfoCard(stringResource(R.string.downloads_plugin_missing))
                else -> DownloadCard {
                    Text(stringResource(R.string.downloads_new_title), style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = url,
                        onValueChange = {
                            url = it
                            // A title taken from the last checked link doesn't belong to a new one.
                            if (title == metadata?.title) title = ""
                            resetCheck()
                        },
                        label = { Text(stringResource(R.string.downloads_url_label)) },
                        supportingText = {
                            Text(
                                if (bulk) pluralStringResource(R.plurals.downloads_link_count, links.size, links.size)
                                else stringResource(R.string.downloads_url_hint),
                            )
                        },
                        maxLines = 6,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    val pasted = extractSharedUrls(clipboardText(context))
                                    if (pasted.isNotEmpty()) {
                                        url = (links + pasted).distinct().joinToString("\n")
                                        resetCheck()
                                    }
                                },
                            ) {
                                Icon(Icons.Outlined.ContentPaste, contentDescription = stringResource(R.string.downloads_paste))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (!bulk) {
                        metadata?.let { found ->
                            found.thumbnailUrl?.let { DownloadThumbnail(it, Modifier.fillMaxWidth()) }
                        }
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text(stringResource(R.string.downloads_title_label)) },
                            supportingText = { Text(stringResource(R.string.downloads_title_hint)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        metadata?.let { MetadataSummary(it, checkedImageCount) }
                        duplicate?.let { match ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    stringResource(R.string.downloads_already_in_library),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.weight(1f),
                                )
                                if (match.sceneId != null || match.galleryId != null) {
                                    TextButton(onClick = { match.sceneId?.let(onOpenScene) ?: match.galleryId?.let(onOpenGallery) }) {
                                        Text(stringResource(R.string.downloads_open))
                                    }
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StashDownloaderPlugin.QUALITIES.forEach { option ->
                            FilterChip(
                                selected = quality == option,
                                onClick = { quality = option },
                                label = { Text(if (option == "best") stringResource(R.string.downloads_quality_best) else option) },
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(StashSpace.S)) {
                        if (!bulk) {
                            OutlinedButton(
                                onClick = ::checkLink,
                                enabled = links.size == 1 && !checking && installed == true,
                                modifier = Modifier.weight(1f),
                            ) { Text(stringResource(if (checking) R.string.downloads_checking else R.string.downloads_check)) }
                        }
                        Button(
                            onClick = {
                                val target = activeProfile ?: return@Button
                                askForNotifications()
                                when {
                                    bulk -> StashDownloads.startAll(target, links, quality)
                                    // Checked and already there: the user chose to download it anyway.
                                    duplicate != null -> StashDownloads.start(target, links.first(), title, quality, metadata, force = true)
                                    else -> StashDownloads.start(target, links.first(), title, quality, metadata)
                                }
                                url = ""
                                title = ""
                                resetCheck()
                            },
                            enabled = links.isNotEmpty() && installed == true,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                when {
                                    bulk -> pluralStringResource(R.plurals.downloads_start_count, links.size, links.size)
                                    duplicate != null -> stringResource(R.string.downloads_download_anyway)
                                    else -> stringResource(R.string.downloads_start)
                                },
                            )
                        }
                    }
                    errorText?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                }
            }

            downloads.forEach { download ->
                DownloadRow(
                    download = download,
                    profile = activeProfile,
                    onCancel = { activeProfile?.let { StashDownloads.cancel(it, download.id) } },
                    onOpen = { download.sceneId?.let(onOpenScene) ?: download.galleryId?.let(onOpenGallery) },
                    onRetry = { force ->
                        activeProfile?.let {
                            askForNotifications()
                            StashDownloads.retry(it, download.id, force)
                        }
                    },
                )
            }
            if (downloads.isEmpty() && installed == true) {
                Text(
                    stringResource(R.string.downloads_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = StashSpace.S),
                )
            }
        }
    }
}

@Composable
private fun DownloadCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(StashRadius.M),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}

@Composable
private fun InfoCard(text: String) = DownloadCard {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun MetadataSummary(metadata: DownloaderMetadata, imageCount: Int) {
    val facts = listOfNotNull(
        imageCount.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.downloads_image_count, it, it) },
        metadata.uploader,
        metadata.durationSeconds?.let { formatSeconds(it.toLong()) },
        metadata.height?.let { "${it}p" },
    )
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (facts.isNotEmpty()) Text(facts.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
        if (metadata.performers.isNotEmpty()) {
            Text(
                stringResource(R.string.downloads_performers, metadata.performers.joinToString(", ")),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (metadata.tags.isNotEmpty()) {
            Text(
                stringResource(R.string.downloads_tags, metadata.tags.take(12).joinToString(", ")),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DownloadRow(
    download: StashDownload,
    profile: StashServerProfile?,
    onCancel: () -> Unit,
    onOpen: () -> Unit,
    onRetry: (force: Boolean) -> Unit,
) {
    // Once the video is in the library, its Stash screenshot; before that, the site's thumbnail.
    val stashThumbnail = rememberStashThumbnailModel(download.sceneId?.let { "${profile?.baseUrl?.trimEnd('/')}/scene/$it/screenshot" }, profile)
    val openable = download.sceneId != null || download.galleryId != null
    val inLibrary = download.phase == DownloadPhase.Done || download.phase == DownloadPhase.AlreadyInLibrary
    DownloadCard {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DownloadThumbnail(
                model = stashThumbnail.takeIf { download.sceneId != null && profile != null } ?: download.thumbnailUrl,
                modifier = Modifier.width(120.dp),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    download.title ?: download.url,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val now = System.currentTimeMillis()
                Text(
                    listOfNotNull(
                        if (now - download.createdAt < DateUtils.MINUTE_IN_MILLIS) stringResource(R.string.downloads_just_now)
                        else DateUtils.getRelativeTimeSpanString(download.createdAt, now, DateUtils.MINUTE_IN_MILLIS).toString(),
                        when (download.kind) {
                            DownloadKind.Video -> null
                            DownloadKind.Image -> stringResource(R.string.downloads_kind_image)
                            DownloadKind.Gallery -> stringResource(R.string.downloads_kind_gallery)
                        },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DownloadStatus(download)
            }
        }
        val cancellable = download.active && download.phase != DownloadPhase.Importing
        if (cancellable || download.retryable || download.phase == DownloadPhase.AlreadyInLibrary || (inLibrary && openable)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (cancellable) TextButton(onClick = onCancel) { Text(stringResource(R.string.downloads_cancel)) }
                if (download.retryable) TextButton(onClick = { onRetry(false) }) { Text(stringResource(R.string.downloads_retry)) }
                if (download.phase == DownloadPhase.AlreadyInLibrary) {
                    TextButton(onClick = { onRetry(true) }) { Text(stringResource(R.string.downloads_download_anyway)) }
                }
                if (inLibrary && openable) Button(onClick = onOpen) { Text(stringResource(R.string.downloads_open)) }
            }
        }
    }
}

@Composable
private fun DownloadStatus(download: StashDownload) {
    val context = LocalContext.current
    val progress = download.progress
    @Composable
    fun line(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) =
        Text(text, style = MaterialTheme.typography.bodySmall, color = color, maxLines = 3, overflow = TextOverflow.Ellipsis)

    // Galleries count their images; the bar then shows the whole gallery, not just the current image.
    val part = if (download.kind == DownloadKind.Gallery && download.partCount > 0) {
        stringResource(R.string.downloads_image_progress, (download.partDone + 1).coerceAtMost(download.partCount), download.partCount)
    } else {
        null
    }
    when (download.phase) {
        DownloadPhase.Starting, DownloadPhase.Queued, DownloadPhase.Importing -> {
            line(
                listOfNotNull(
                    part.takeIf { download.phase == DownloadPhase.Queued },
                    stringResource(
                        when (download.phase) {
                            DownloadPhase.Queued -> R.string.downloads_phase_queued
                            DownloadPhase.Importing -> R.string.downloads_phase_importing
                            else -> R.string.downloads_phase_starting
                        },
                    ),
                ).joinToString(" · "),
            )
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        DownloadPhase.Downloading -> {
            val percent = progress?.percentage
            val overall = when {
                part != null -> (download.partDone + (percent ?: 0f) / 100f) / download.partCount
                percent != null -> percent / 100f
                else -> null
            }
            if (overall != null) {
                LinearProgressIndicator(progress = { overall }, modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            val details = listOfNotNull(
                part,
                percent?.takeIf { part == null }?.let { "${it.toInt()} %" },
                progress?.totalBytes?.takeIf { part == null }?.let { Formatter.formatShortFileSize(context, it) },
                progress?.speedBytesPerSecond?.let { "${Formatter.formatShortFileSize(context, it.toLong())}/s" },
                progress?.etaSeconds?.takeIf { it > 0 && part == null }?.let { stringResource(R.string.downloads_eta, formatSeconds(it)) },
            )
            line(details.joinToString(" · ").ifBlank { stringResource(R.string.downloads_phase_downloading) })
        }
        DownloadPhase.Done -> line(
            stringResource(
                when {
                    download.sceneId != null || download.galleryId != null || download.imageId != null -> R.string.downloads_phase_done
                    else -> R.string.downloads_phase_done_no_scene
                },
            ),
            MaterialTheme.colorScheme.primary,
        )
        DownloadPhase.AlreadyInLibrary -> line(stringResource(R.string.downloads_already_in_library), MaterialTheme.colorScheme.tertiary)
        DownloadPhase.Failed -> line(download.error ?: stringResource(R.string.downloads_phase_failed), MaterialTheme.colorScheme.error)
        DownloadPhase.Cancelled -> line(stringResource(R.string.downloads_phase_cancelled))
        DownloadPhase.Interrupted -> line(stringResource(R.string.downloads_phase_interrupted))
    }
}

private fun clipboardText(context: Context): String? =
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
        ?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()

private fun formatSeconds(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val rest = seconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, rest) else "%d:%02d".format(minutes, rest)
}
