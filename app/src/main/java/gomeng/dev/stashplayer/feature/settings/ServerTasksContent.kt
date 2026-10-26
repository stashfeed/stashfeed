package gomeng.dev.stashplayer.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import gomeng.dev.stashplayer.R
import gomeng.dev.stashplayer.core.debug.StashDebugLogBuffer
import gomeng.dev.stashplayer.core.network.RedditAgentCookies
import gomeng.dev.stashplayer.core.network.RedditAgentStatus
import gomeng.dev.stashplayer.core.network.RedditStashAgent
import gomeng.dev.stashplayer.core.network.RedditStashAgentClient
import gomeng.dev.stashplayer.core.network.StashGenerateOptions
import gomeng.dev.stashplayer.core.network.StashGraphQlClient
import gomeng.dev.stashplayer.core.network.StashJob
import gomeng.dev.stashplayer.core.network.StashJobStatus
import gomeng.dev.stashplayer.core.network.StashPlugin
import gomeng.dev.stashplayer.core.network.StashPluginTask
import gomeng.dev.stashplayer.core.network.StashScanOptions
import gomeng.dev.stashplayer.core.network.StashServerAdminClient
import gomeng.dev.stashplayer.core.network.StashServerDirectory
import gomeng.dev.stashplayer.core.network.StashSettingsRepository
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Settings › Server tasks: Stash's job queue, library tasks, plugins and the Reddit Stash Agent. */
@Composable
internal fun ServerTasksContent() {
    val context = LocalContext.current
    val repository = remember(context) { StashSettingsRepository(context) }
    val savedProfile by repository.savedServerProfile.collectAsState(initial = null)
    val runtimeProfile by repository.serverProfile.collectAsState(initial = null)
    val coroutineScope = rememberCoroutineScope()

    var createGalleriesFromFolders by remember { mutableStateOf<Boolean?>(null) }
    var isServerLibraryLoading by remember { mutableStateOf(false) }
    var isServerLibrarySaving by remember { mutableStateOf(false) }
    var isMetadataScanRunning by remember { mutableStateOf(false) }
    var serverJobs by remember { mutableStateOf(emptyList<StashJob>()) }
    var isJobQueueLoading by remember { mutableStateOf(false) }
    var jobQueueErrorText by remember { mutableStateOf<String?>(null) }
    var jobQueueRefreshKey by remember { mutableStateOf(0) }
    var scanOptions by remember { mutableStateOf(StashScanOptions()) }
    var scanOptionsExpanded by remember { mutableStateOf(false) }
    var libraryPaths by remember { mutableStateOf(emptyList<String>()) }
    var showSelectiveScanDialog by remember { mutableStateOf(false) }
    var selectedScanPaths by remember { mutableStateOf(emptyList<String>()) }
    var scannedDirectory by remember { mutableStateOf<StashServerDirectory?>(null) }
    var isDirectoryLoading by remember { mutableStateOf(false) }
    var directoryErrorText by remember { mutableStateOf<String?>(null) }
    var serverLibraryStatusText by remember { mutableStateOf<String?>(null) }
    var serverLibraryErrorText by remember { mutableStateOf<String?>(null) }
    var showMetadataScanConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(runtimeProfile) {
        createGalleriesFromFolders = null
        libraryPaths = emptyList()
        scanOptions = StashScanOptions()
        selectedScanPaths = emptyList()
        scannedDirectory = null
        directoryErrorText = null
        serverLibraryStatusText = null
        serverLibraryErrorText = null
        val activeProfile = runtimeProfile
        if (activeProfile?.isConfigured() != true) {
            isServerLibraryLoading = false
            return@LaunchedEffect
        }
        isServerLibraryLoading = true
        runCatching { StashGraphQlClient(activeProfile).findServerLibrarySettings() }
            .onSuccess { settings ->
                createGalleriesFromFolders = settings.createGalleriesFromFolders
                libraryPaths = settings.libraryPaths
                scanOptions = settings.scanOptions
            }
            .onFailure {
                StashDebugLogBuffer.record("Settings", "Stash server library settings load failed", it)
                serverLibraryErrorText = it.message ?: context.getString(R.string.settings_server_library_load_failed)
            }
        isServerLibraryLoading = false
    }

    LaunchedEffect(runtimeProfile, jobQueueRefreshKey) {
        val activeProfile = runtimeProfile
        if (activeProfile?.isConfigured() != true) {
            serverJobs = emptyList()
            jobQueueErrorText = null
            return@LaunchedEffect
        }
        val client = StashGraphQlClient(activeProfile)
        isJobQueueLoading = true
        while (true) {
            runCatching { client.findJobQueue() }
                .onSuccess {
                    serverJobs = it
                    jobQueueErrorText = null
                }
                .onFailure { jobQueueErrorText = it.message ?: context.getString(R.string.settings_server_job_queue_failed) }
            isJobQueueLoading = false
            delay(2_000)
        }
    }

    fun startMetadataScan(paths: List<String>? = null) {
        coroutineScope.launch {
            val activeProfile = runtimeProfile
            if (activeProfile == null) {
                serverLibraryStatusText = null
                serverLibraryErrorText = context.getString(R.string.settings_recommendation_no_stash_server)
                return@launch
            }
            isMetadataScanRunning = true
            serverLibraryStatusText = context.getString(R.string.settings_server_metadata_scan_starting)
            serverLibraryErrorText = null
            runCatching { StashGraphQlClient(activeProfile).scanMetadata(scanOptions, paths) }
                .onSuccess { jobId ->
                    serverLibraryStatusText = context.getString(R.string.settings_server_metadata_scan_started, jobId)
                    jobQueueRefreshKey++
                }
                .onFailure {
                    StashDebugLogBuffer.record("Settings", "Stash metadata scan start failed", it)
                    serverLibraryStatusText = null
                    serverLibraryErrorText = it.message ?: context.getString(R.string.settings_server_metadata_scan_failed)
                }
            isMetadataScanRunning = false
        }
    }

    fun persistScanOptions(updated: StashScanOptions) {
        val previous = scanOptions
        scanOptions = updated
        isServerLibrarySaving = true
        serverLibraryErrorText = null
        coroutineScope.launch {
            runCatching { runtimeProfile?.let { StashGraphQlClient(it).setScanOptions(updated) } ?: error("No Stash server") }
                .onFailure {
                    if (scanOptions == updated) scanOptions = previous
                    StashDebugLogBuffer.record("Settings", "Stash scan options save failed", it)
                    serverLibraryErrorText = it.message ?: context.getString(R.string.settings_server_scan_options_save_failed)
                }
            isServerLibrarySaving = false
        }
    }

    fun loadDirectory(path: String) {
        if (isDirectoryLoading) return
        val activeProfile = runtimeProfile ?: return
        isDirectoryLoading = true
        directoryErrorText = null
        coroutineScope.launch {
            runCatching { StashGraphQlClient(activeProfile).findDirectory(path) }
                .onSuccess { scannedDirectory = it }
                .onFailure {
                    StashDebugLogBuffer.record("Settings", "Stash directory load failed", it)
                    directoryErrorText = it.message ?: context.getString(R.string.settings_server_directory_load_failed)
                }
            isDirectoryLoading = false
        }
    }

    if (showMetadataScanConfirm) {
        AlertDialog(
            onDismissRequest = { showMetadataScanConfirm = false },
            title = { Text(stringResource(ServerLibrarySettingsCopy.scanConfirmTitle)) },
            text = { Text(stringResource(R.string.settings_server_metadata_scan_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showMetadataScanConfirm = false
                        startMetadataScan()
                    },
                ) {
                    Text(stringResource(ServerLibrarySettingsCopy.scanConfirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showMetadataScanConfirm = false }) {
                    Text(stringResource(ServerLibrarySettingsCopy.scanCancel))
                }
            },
        )
    }

    if (showSelectiveScanDialog) {
        AlertDialog(
            onDismissRequest = { showSelectiveScanDialog = false },
            title = { Text(stringResource(R.string.settings_server_selective_scan_title)) },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (selectedScanPaths.isNotEmpty()) {
                        Text(stringResource(R.string.settings_server_selected_scan_paths), style = MaterialTheme.typography.titleSmall)
                        selectedScanPaths.forEach { path ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(path, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = { selectedScanPaths = selectedScanPaths - path }) {
                                    Text(stringResource(R.string.settings_server_remove_scan_path))
                                }
                            }
                        }
                    }
                    val directory = scannedDirectory
                    if (directory == null) {
                        Text(stringResource(R.string.settings_server_scan_library_roots), style = MaterialTheme.typography.titleSmall)
                        if (libraryPaths.isEmpty()) {
                            Text(stringResource(R.string.settings_server_scan_no_library_paths))
                        } else {
                            libraryPaths.forEach { path ->
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(path, style = MaterialTheme.typography.bodySmall)
                                    TextButton(
                                        onClick = { loadDirectory(path) },
                                        enabled = !isDirectoryLoading,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(stringResource(R.string.settings_server_browse_scan_path))
                                    }
                                    TextButton(
                                        onClick = { selectedScanPaths = (selectedScanPaths + path).distinct() },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(stringResource(R.string.settings_server_add_scan_path))
                                    }
                                }
                            }
                        }
                    } else {
                        Text(directory.path, style = MaterialTheme.typography.titleSmall)
                        directory.parent?.let { parent ->
                            TextButton(
                                onClick = { loadDirectory(parent) },
                                enabled = !isDirectoryLoading,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.settings_server_parent_directory))
                            }
                        }
                        TextButton(
                            onClick = { selectedScanPaths = (selectedScanPaths + directory.path).distinct() },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.settings_server_add_scan_path))
                        }
                        directory.directories.forEach { path ->
                            TextButton(
                                onClick = { loadDirectory(path) },
                                enabled = !isDirectoryLoading,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(path, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                    if (isDirectoryLoading) Text(stringResource(R.string.settings_server_directory_loading))
                    directoryErrorText?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val paths = selectedScanPaths
                        showSelectiveScanDialog = false
                        startMetadataScan(paths)
                    },
                    enabled = selectedScanPaths.isNotEmpty() && !isDirectoryLoading,
                ) {
                    Text(stringResource(ServerLibrarySettingsCopy.scanConfirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSelectiveScanDialog = false }) {
                    Text(stringResource(ServerLibrarySettingsCopy.scanCancel))
                }
            },
        )
    }

    val adminClient = remember(runtimeProfile) {
        runtimeProfile?.takeIf { it.isConfigured() }?.let { StashServerAdminClient(StashGraphQlClient(it)) }
    }
    val refreshJobs: () -> Unit = { jobQueueRefreshKey++ }

    ServerJobsCard(
        jobs = serverJobs,
        loading = isJobQueueLoading,
        errorText = jobQueueErrorText,
        onStopJob = { job ->
            coroutineScope.launch {
                runCatching { adminClient?.stopJob(job.id) }
                    .onFailure {
                        StashDebugLogBuffer.record("Settings", "Stash stop job failed", it)
                        jobQueueErrorText = it.message ?: context.getString(R.string.settings_tasks_stop_job_failed)
                    }
                refreshJobs()
            }
        },
    )

    ServerLibrarySettingsCard(
        serverConfigured = savedProfile?.isConfigured() == true,
        createGalleriesFromFolders = createGalleriesFromFolders,
        scanOptions = scanOptions,
        scanOptionsExpanded = scanOptionsExpanded,
        loading = isServerLibraryLoading,
        saving = isServerLibrarySaving,
        scanning = isMetadataScanRunning,
        statusText = serverLibraryStatusText,
        errorText = serverLibraryErrorText,
        onToggleCreateGalleries = { enabled ->
            coroutineScope.launch {
                val activeProfile = runtimeProfile
                if (activeProfile == null) {
                    serverLibraryStatusText = null
                    serverLibraryErrorText = context.getString(R.string.settings_recommendation_no_stash_server)
                    return@launch
                }
                isServerLibrarySaving = true
                serverLibraryStatusText = context.getString(R.string.settings_server_library_update_saving)
                serverLibraryErrorText = null
                runCatching { StashGraphQlClient(activeProfile).setCreateGalleriesFromFolders(enabled) }
                    .onSuccess { settings ->
                        createGalleriesFromFolders = settings.createGalleriesFromFolders
                        serverLibraryStatusText = context.getString(R.string.settings_server_library_update_saved)
                    }
                    .onFailure {
                        StashDebugLogBuffer.record("Settings", "Stash server library settings update failed", it)
                        serverLibraryStatusText = null
                        serverLibraryErrorText = it.message ?: context.getString(R.string.settings_server_library_update_failed)
                    }
                isServerLibrarySaving = false
            }
        },
        onScanOptionsExpandedChange = { scanOptionsExpanded = it },
        onScanOptionsChange = ::persistScanOptions,
        onRequestScan = { showMetadataScanConfirm = true },
        onRequestSelectiveScan = {
            selectedScanPaths = emptyList()
            scannedDirectory = null
            directoryErrorText = null
            showSelectiveScanDialog = true
        },
    )

    var plugins by remember { mutableStateOf<List<StashPlugin>?>(null) }
    var pluginsErrorText by remember { mutableStateOf<String?>(null) }
    var pluginsRefreshKey by remember { mutableStateOf(0) }
    LaunchedEffect(adminClient, pluginsRefreshKey) {
        val client = adminClient ?: return@LaunchedEffect
        runCatching { client.plugins() }
            .onSuccess {
                plugins = it
                pluginsErrorText = null
            }
            .onFailure {
                StashDebugLogBuffer.record("Settings", "Stash plugins load failed", it)
                pluginsErrorText = it.message ?: context.getString(R.string.settings_tasks_plugins_failed)
            }
    }

    LibraryTasksCard(adminClient = adminClient, onJobStarted = refreshJobs)
    if (adminClient != null && plugins.orEmpty().any { it.id == RedditStashAgent.PLUGIN_ID && it.enabled }) {
        RedditAgentCard(adminClient = adminClient, onJobStarted = refreshJobs)
    }
    PluginsCard(
        adminClient = adminClient,
        plugins = plugins,
        errorText = pluginsErrorText,
        onPluginsChanged = { pluginsRefreshKey++ },
        onJobStarted = refreshJobs,
    )
}

/** Generate, auto tag and clean for the whole library; each starts a Stash job that shows up in the job queue. */
@Composable
private fun LibraryTasksCard(adminClient: StashServerAdminClient?, onJobStarted: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var generateOptions by remember { mutableStateOf<StashGenerateOptions?>(null) }
    var confirm by remember { mutableStateOf<LibraryTask?>(null) }
    var busy by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf<String?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }

    fun start(task: LibraryTask, action: suspend StashServerAdminClient.() -> String) {
        val client = adminClient ?: return
        busy = true
        errorText = null
        coroutineScope.launch {
            runCatching { client.action() }
                .onSuccess {
                    statusText = context.getString(R.string.settings_tasks_started, context.getString(task.title))
                    onJobStarted()
                }
                .onFailure {
                    StashDebugLogBuffer.record("Settings", "Stash ${task.name} start failed", it)
                    statusText = null
                    errorText = it.message ?: context.getString(R.string.settings_tasks_start_failed)
                }
            busy = false
        }
    }

    generateOptions?.let { options ->
        GenerateDialog(
            options = options,
            onOptionsChange = { generateOptions = it },
            onDismiss = { generateOptions = null },
            onConfirm = {
                generateOptions = null
                start(LibraryTask.Generate) { generate(options) }
            },
        )
    }
    confirm?.let { task ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(stringResource(task.title)) },
            text = { Text(stringResource(task.confirmMessage)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirm = null
                        when (task) {
                            LibraryTask.AutoTag -> start(task) { autoTag() }
                            LibraryTask.Clean -> start(task) { clean() }
                            LibraryTask.Generate -> Unit
                        }
                    },
                ) { Text(stringResource(R.string.settings_tasks_start)) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = null }) { Text(stringResource(ServerLibrarySettingsCopy.scanCancel)) }
            },
        )
    }

    SettingsCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.settings_tasks_library_title), style = MaterialTheme.typography.titleMedium)
            Button(
                onClick = {
                    val client = adminClient ?: return@Button
                    busy = true
                    coroutineScope.launch {
                        generateOptions = runCatching { client.generateDefaults() }.getOrDefault(StashGenerateOptions())
                        busy = false
                    }
                },
                enabled = adminClient != null && !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.settings_tasks_generate)) }
            Button(
                onClick = { confirm = LibraryTask.AutoTag },
                enabled = adminClient != null && !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.settings_tasks_auto_tag)) }
            OutlinedButton(
                onClick = { confirm = LibraryTask.Clean },
                enabled = adminClient != null && !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.settings_tasks_clean)) }
            Text(
                statusText ?: stringResource(R.string.settings_tasks_library_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            errorText?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
    }
}

private enum class LibraryTask(@StringRes val title: Int, @StringRes val confirmMessage: Int) {
    Generate(R.string.settings_tasks_generate, R.string.settings_tasks_generate_description),
    AutoTag(R.string.settings_tasks_auto_tag, R.string.settings_tasks_auto_tag_confirm),
    Clean(R.string.settings_tasks_clean, R.string.settings_tasks_clean_confirm),
}

@Composable
private fun GenerateDialog(
    options: StashGenerateOptions,
    onOptionsChange: (StashGenerateOptions) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_tasks_generate)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    stringResource(R.string.settings_tasks_generate_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ScanOptionRow(R.string.settings_tasks_generate_covers, options.covers, true) { onOptionsChange(options.copy(covers = it)) }
                ScanOptionRow(R.string.settings_tasks_generate_previews, options.previews, true) { onOptionsChange(options.copy(previews = it)) }
                ScanOptionRow(
                    R.string.settings_tasks_generate_image_previews,
                    options.imagePreviews,
                    options.previews,
                    Modifier.padding(start = 16.dp),
                ) { onOptionsChange(options.copy(imagePreviews = it)) }
                ScanOptionRow(R.string.settings_tasks_generate_sprites, options.sprites, true) { onOptionsChange(options.copy(sprites = it)) }
                ScanOptionRow(R.string.settings_tasks_generate_phashes, options.phashes, true) { onOptionsChange(options.copy(phashes = it)) }
                ScanOptionRow(R.string.settings_tasks_generate_markers, options.markers, true) { onOptionsChange(options.copy(markers = it)) }
                ScanOptionRow(R.string.settings_tasks_generate_thumbnails, options.imageThumbnails, true) { onOptionsChange(options.copy(imageThumbnails = it)) }
                ScanOptionRow(R.string.settings_tasks_generate_clip_previews, options.clipPreviews, true) { onOptionsChange(options.copy(clipPreviews = it)) }
                ScanOptionRow(R.string.settings_tasks_generate_overwrite, options.overwrite, true) { onOptionsChange(options.copy(overwrite = it)) }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.settings_tasks_start)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(ServerLibrarySettingsCopy.scanCancel)) } },
    )
}

/** Every installed plugin: switch it on or off, and run its tasks. */
@Composable
private fun PluginsCard(
    adminClient: StashServerAdminClient?,
    plugins: List<StashPlugin>?,
    errorText: String?,
    onPluginsChanged: () -> Unit,
    onJobStarted: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(emptySet<String>()) }
    var busy by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf<String?>(null) }
    var actionErrorText by remember { mutableStateOf<String?>(null) }

    fun act(failureLog: String, action: suspend StashServerAdminClient.() -> Unit, onDone: () -> Unit) {
        val client = adminClient ?: return
        busy = true
        actionErrorText = null
        coroutineScope.launch {
            runCatching { client.action() }
                .onSuccess { onDone() }
                .onFailure {
                    StashDebugLogBuffer.record("Settings", failureLog, it)
                    statusText = null
                    actionErrorText = it.message ?: context.getString(R.string.settings_tasks_start_failed)
                }
            busy = false
        }
    }

    SettingsCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.settings_tasks_plugins_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = {
                        act("Stash plugin reload failed", { reloadPlugins() }) {
                            statusText = context.getString(R.string.settings_tasks_plugins_reloaded)
                            onPluginsChanged()
                        }
                    },
                    enabled = adminClient != null && !busy,
                ) { Text(stringResource(R.string.settings_tasks_plugins_reload)) }
            }
            when {
                adminClient == null -> Text(stringResource(R.string.settings_recommendation_no_stash_server), style = MaterialTheme.typography.bodySmall)
                plugins == null && errorText == null -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                plugins.isNullOrEmpty() && errorText == null -> Text(
                    stringResource(R.string.settings_tasks_plugins_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            plugins.orEmpty().forEach { plugin ->
                PluginRow(
                    plugin = plugin,
                    expanded = plugin.id in expanded,
                    enabled = !busy,
                    onToggleExpanded = { expanded = if (plugin.id in expanded) expanded - plugin.id else expanded + plugin.id },
                    onEnabledChange = { enabled ->
                        act("Stash plugin enable failed", { setPluginEnabled(plugin.id, enabled) }) { onPluginsChanged() }
                    },
                    onRunTask = { task ->
                        act("Stash plugin task start failed", { runPluginTask(plugin.id, task.name) }) {
                            statusText = context.getString(R.string.settings_tasks_started, task.name)
                            onJobStarted()
                        }
                    },
                )
            }
            statusText?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            (actionErrorText ?: errorText)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun PluginRow(
    plugin: StashPlugin,
    expanded: Boolean,
    enabled: Boolean,
    onToggleExpanded: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onRunTask: (StashPluginTask) -> Unit,
) {
    val hasTasks = plugin.enabled && plugin.tasks.isNotEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (hasTasks) Modifier.clickable(onClick = onToggleExpanded) else Modifier),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(plugin.name, style = MaterialTheme.typography.bodyLarge)
                val details = listOfNotNull(
                    plugin.version,
                    plugin.tasks.size.takeIf { it > 0 && plugin.enabled }
                        ?.let { pluralStringResource(R.plurals.settings_tasks_plugin_task_count, it, it) },
                )
                if (details.isNotEmpty()) {
                    Text(
                        details.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Switch(checked = plugin.enabled, enabled = enabled, onCheckedChange = onEnabledChange)
        }
        if (expanded && hasTasks) {
            plugin.tasks.forEach { task ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(task.name, style = MaterialTheme.typography.bodyMedium)
                        task.description?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    TextButton(onClick = { onRunTask(task) }, enabled = enabled) {
                        Text(stringResource(R.string.settings_tasks_run))
                    }
                }
            }
        }
    }
}

/** How often the Reddit Agent card refreshes its status: quickly during a sync, slowly otherwise. */
private const val REDDIT_STATUS_POLL_RUNNING_MS = 3_000L
private const val REDDIT_STATUS_POLL_IDLE_MS = 20_000L
private val REDDIT_INTERVAL_CHOICES = listOf(3.0, 6.0, 12.0, 24.0)
private const val REDDIT_COOKIE_WARNING_DAYS = 14

/** The Reddit Stash Agent plugin: sync status, start and cancel, cookie expiry and the automatic sync. */
@Composable
private fun RedditAgentCard(adminClient: StashServerAdminClient, onJobStarted: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val agent = remember(adminClient) { RedditStashAgentClient(adminClient) }
    var status by remember { mutableStateOf<RedditAgentStatus?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(agent, refreshKey) {
        while (true) {
            runCatching { agent.status() }
                .onSuccess {
                    status = it
                    errorText = null
                }
                .onFailure { errorText = it.message ?: context.getString(R.string.settings_reddit_agent_unreachable) }
            delay(if (status?.running == true) REDDIT_STATUS_POLL_RUNNING_MS else REDDIT_STATUS_POLL_IDLE_MS)
        }
    }

    fun act(failureLog: String, action: suspend RedditStashAgentClient.() -> Unit) {
        busy = true
        errorText = null
        coroutineScope.launch {
            runCatching { agent.action() }
                .onFailure {
                    StashDebugLogBuffer.record("Settings", failureLog, it)
                    errorText = it.message ?: context.getString(R.string.settings_tasks_start_failed)
                }
            busy = false
            refreshKey++
        }
    }

    SettingsCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.settings_reddit_agent_title), style = MaterialTheme.typography.titleMedium)
            val current = status
            when {
                current == null && errorText == null -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                current?.running == true -> {
                    Text(
                        if (current.stepCount > 0 && current.stepLabel != null) {
                            stringResource(R.string.settings_reddit_agent_running_step, current.step, current.stepCount, current.stepLabel)
                        } else {
                            stringResource(R.string.settings_reddit_agent_running)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    LinearProgressIndicator(progress = { current.progress }, modifier = Modifier.fillMaxWidth())
                }
                current != null -> RedditLastSyncText(current)
            }
            current?.let { RedditCookieWarning(it.cookies) }

            if (current?.running == true) {
                OutlinedButton(
                    onClick = { act("Reddit agent cancel failed") { cancelSync() } },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.settings_reddit_agent_cancel)) }
            } else {
                Button(
                    onClick = {
                        act("Reddit agent sync start failed") {
                            startSync()
                            onJobStarted()
                        }
                    },
                    enabled = !busy && errorText == null,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.settings_reddit_agent_start)) }
            }

            current?.let { agentStatus ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_reddit_agent_auto_sync), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            agentStatus.nextRunAt?.takeIf { agentStatus.autoSyncEnabled }
                                ?.let { stringResource(R.string.settings_reddit_agent_next_run, formatAgentTime(it)) }
                                ?: stringResource(R.string.settings_reddit_agent_auto_sync_off),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = agentStatus.autoSyncEnabled,
                        enabled = !busy,
                        onCheckedChange = { enabled ->
                            act("Reddit agent auto-sync change failed") { setAutoSync(enabled, agentStatus.intervalHours) }
                        },
                    )
                }
                if (agentStatus.autoSyncEnabled) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        REDDIT_INTERVAL_CHOICES.forEach { hours ->
                            FilterChip(
                                selected = agentStatus.intervalHours == hours,
                                enabled = !busy,
                                onClick = { act("Reddit agent interval change failed") { setAutoSync(true, hours) } },
                                label = { Text(stringResource(R.string.settings_reddit_agent_interval, hours.roundToInt())) },
                            )
                        }
                    }
                }
            }
            errorText?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun RedditLastSyncText(status: RedditAgentStatus) {
    val finished = (status.lastFinishedAt ?: status.lastStartedAt)?.let(::formatAgentTime)
    val text = when (status.lastResult) {
        null -> stringResource(R.string.settings_reddit_agent_never_synced)
        "success" -> stringResource(R.string.settings_reddit_agent_last_success, finished.orEmpty())
        "cancelled" -> stringResource(R.string.settings_reddit_agent_last_cancelled, finished.orEmpty())
        else -> stringResource(R.string.settings_reddit_agent_last_failed, finished.orEmpty())
    }
    val failed = status.lastResult != null && status.lastResult != "success" && status.lastResult != "cancelled"
    Text(text, style = MaterialTheme.typography.bodyMedium, color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
    if (failed) {
        status.lastError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    } else if (status.lastResult == "success") {
        val results = redditSummaryLine(status.summary, REDDIT_SUMMARY_RESULTS)
        val duration = status.summary["duration_seconds"]?.toDoubleOrNull()?.let { formatDuration(it.roundToLong()) }
        listOfNotNull(results, duration?.let { stringResource(R.string.settings_reddit_agent_duration, it) })
            .joinToString(" · ")
            .takeIf { it.isNotBlank() }
            ?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        redditSummaryLine(status.summary, REDDIT_SUMMARY_PROBLEMS)
            ?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
    }
}

/** The agent's summary has many counters; these are the ones worth a glance, each with its own wording. */
private val REDDIT_SUMMARY_RESULTS = listOf(
    "posts_new" to R.plurals.settings_reddit_agent_posts_new,
    "files_new" to R.plurals.settings_reddit_agent_files_new,
    "galleries_created" to R.plurals.settings_reddit_agent_galleries_created,
)
private val REDDIT_SUMMARY_PROBLEMS = listOf(
    "downloads_failed" to R.plurals.settings_reddit_agent_downloads_failed,
    "feeds_failed" to R.plurals.settings_reddit_agent_feeds_failed,
    "link_errors" to R.plurals.settings_reddit_agent_link_errors,
)

/** The non-zero counters among [keys], joined with dots; null when all are zero or missing. */
@Composable
private fun redditSummaryLine(summary: Map<String, String>, keys: List<Pair<String, Int>>): String? =
    keys.mapNotNull { (key, plural) ->
        val count = summary[key]?.toDoubleOrNull()?.toInt()?.takeIf { it > 0 } ?: return@mapNotNull null
        pluralStringResource(plural, count, count)
    }.joinToString(" · ").takeIf { it.isNotBlank() }

private fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return when {
        hours > 0 -> "$hours h $minutes min"
        minutes > 0 -> "$minutes min"
        else -> "$seconds s"
    }
}

@Composable
private fun RedditCookieWarning(cookies: RedditAgentCookies?) {
    val text = when {
        cookies == null -> return
        !cookies.present -> stringResource(R.string.settings_reddit_agent_cookies_missing)
        cookies.expired -> stringResource(R.string.settings_reddit_agent_cookies_expired)
        cookies.daysLeft != null && cookies.daysLeft <= REDDIT_COOKIE_WARNING_DAYS ->
            pluralStringResource(R.plurals.settings_reddit_agent_cookies_expiring, cookies.daysLeft, cookies.daysLeft)
        else -> return
    }
    val urgent = !cookies.present || cookies.expired
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
    )
}

/** The agent reports ISO-8601 times in UTC; show them in the phone's time zone and format. */
private fun formatAgentTime(value: String): String = runCatching {
    OffsetDateTime.parse(value)
        .atZoneSameInstant(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT))
}.getOrDefault(value)

@Composable
private fun ServerLibrarySettingsCard(
    serverConfigured: Boolean,
    createGalleriesFromFolders: Boolean?,
    scanOptions: StashScanOptions,
    scanOptionsExpanded: Boolean,
    loading: Boolean,
    saving: Boolean,
    scanning: Boolean,
    statusText: String?,
    errorText: String?,
    onToggleCreateGalleries: (Boolean) -> Unit,
    onScanOptionsExpandedChange: (Boolean) -> Unit,
    onScanOptionsChange: (StashScanOptions) -> Unit,
    onRequestScan: () -> Unit,
    onRequestSelectiveScan: () -> Unit,
) {
    val controlsEnabled = serverConfigured && createGalleriesFromFolders != null && !loading && !saving
    SettingsCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(ServerLibrarySettingsCopy.sectionTitle), style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(stringResource(ServerLibrarySettingsCopy.createGalleriesTitle), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(ServerLibrarySettingsCopy.createGalleriesDescription),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = createGalleriesFromFolders == true,
                    enabled = controlsEnabled,
                    onCheckedChange = onToggleCreateGalleries,
                )
            }
            Button(
                onClick = onRequestScan,
                enabled = controlsEnabled && !scanning,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_server_scan_all_button))
            }
            Button(
                onClick = onRequestSelectiveScan,
                enabled = controlsEnabled && !scanning,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_server_selective_scan_button))
            }
            TextButton(
                onClick = { onScanOptionsExpandedChange(!scanOptionsExpanded) },
                enabled = controlsEnabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(
                        if (scanOptionsExpanded) R.string.settings_server_scan_options_hide
                        else R.string.settings_server_scan_options_show,
                    ),
                )
            }
            if (scanOptionsExpanded) {
                ScanOptionRow(
                    title = R.string.settings_server_scan_generate_covers,
                    checked = scanOptions.scanGenerateCovers,
                    enabled = controlsEnabled,
                    onCheckedChange = { onScanOptionsChange(scanOptions.copy(scanGenerateCovers = it)) },
                )
                ScanOptionRow(
                    title = R.string.settings_server_scan_generate_previews,
                    checked = scanOptions.scanGeneratePreviews,
                    enabled = controlsEnabled,
                    onCheckedChange = { onScanOptionsChange(scanOptions.copy(scanGeneratePreviews = it)) },
                )
                ScanOptionRow(
                    title = R.string.settings_server_scan_generate_image_previews,
                    checked = scanOptions.scanGenerateImagePreviews,
                    enabled = controlsEnabled && scanOptions.scanGeneratePreviews,
                    modifier = Modifier.padding(start = 16.dp),
                    onCheckedChange = { onScanOptionsChange(scanOptions.copy(scanGenerateImagePreviews = it)) },
                )
                ScanOptionRow(
                    title = R.string.settings_server_scan_generate_sprites,
                    checked = scanOptions.scanGenerateSprites,
                    enabled = controlsEnabled,
                    onCheckedChange = { onScanOptionsChange(scanOptions.copy(scanGenerateSprites = it)) },
                )
                ScanOptionRow(
                    title = R.string.settings_server_scan_generate_video_phashes,
                    checked = scanOptions.scanGeneratePhashes,
                    enabled = controlsEnabled,
                    onCheckedChange = { onScanOptionsChange(scanOptions.copy(scanGeneratePhashes = it)) },
                )
                ScanOptionRow(
                    title = R.string.settings_server_scan_generate_thumbnails,
                    checked = scanOptions.scanGenerateThumbnails,
                    enabled = controlsEnabled,
                    onCheckedChange = { onScanOptionsChange(scanOptions.copy(scanGenerateThumbnails = it)) },
                )
                ScanOptionRow(
                    title = R.string.settings_server_scan_generate_image_phashes,
                    checked = scanOptions.scanGenerateImagePhashes,
                    enabled = controlsEnabled,
                    onCheckedChange = { onScanOptionsChange(scanOptions.copy(scanGenerateImagePhashes = it)) },
                )
                ScanOptionRow(
                    title = R.string.settings_server_scan_generate_clip_previews,
                    checked = scanOptions.scanGenerateClipPreviews,
                    enabled = controlsEnabled,
                    onCheckedChange = { onScanOptionsChange(scanOptions.copy(scanGenerateClipPreviews = it)) },
                )
                ScanOptionRow(
                    title = R.string.settings_server_scan_force_rescan,
                    checked = scanOptions.rescan,
                    enabled = controlsEnabled,
                    onCheckedChange = { onScanOptionsChange(scanOptions.copy(rescan = it)) },
                )
            }
            Text(
                text = when {
                    loading -> stringResource(R.string.settings_server_library_loading)
                    scanning -> stringResource(R.string.settings_server_metadata_scan_starting)
                    !serverConfigured -> stringResource(R.string.settings_recommendation_no_stash_server)
                    else -> statusText ?: stringResource(R.string.settings_server_metadata_scan_description)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            errorText?.let { text ->
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** Stash's job queue, polled while the page is open; running and queued jobs can be stopped. */
@Composable
private fun ServerJobsCard(
    jobs: List<StashJob>,
    loading: Boolean,
    errorText: String?,
    onStopJob: (StashJob) -> Unit,
) {
    SettingsCard {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.settings_server_job_queue_title), style = MaterialTheme.typography.titleMedium)
            when {
                loading -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                jobs.isEmpty() -> Text(
                    stringResource(R.string.settings_server_job_queue_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> jobs.forEach { job -> ServerJobRow(job, onStop = { onStopJob(job) }) }
            }
            errorText?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun ServerJobRow(job: StashJob, onStop: () -> Unit) {
    val status = when (job.status) {
        StashJobStatus.Ready -> R.string.settings_server_job_status_ready
        StashJobStatus.Running -> R.string.settings_server_job_status_running
        StashJobStatus.Stopping -> R.string.settings_server_job_status_stopping
        StashJobStatus.Finished -> R.string.settings_server_job_status_finished
        StashJobStatus.Failed -> R.string.settings_server_job_status_failed
        StashJobStatus.Cancelled -> R.string.settings_server_job_status_cancelled
        StashJobStatus.Missing -> R.string.settings_server_job_status_unknown
    }
    val stoppable = job.status == StashJobStatus.Ready || job.status == StashJobStatus.Running
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(job.description, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(status), style = MaterialTheme.typography.labelMedium)
            if (stoppable) {
                TextButton(onClick = onStop) { Text(stringResource(R.string.settings_tasks_stop_job)) }
            }
        }
        job.progress?.let { progress ->
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Text(
                stringResource(R.string.settings_server_job_progress, (progress * 100).roundToInt()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } ?: LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        job.subTasks.firstOrNull()?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        job.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun ScanOptionRow(
    @StringRes title: Int,
    checked: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(title), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, enabled = enabled, onCheckedChange = null)
    }
}
