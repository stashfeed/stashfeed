package gomeng.dev.stashplayer.feature.downloads

import android.content.Context
import gomeng.dev.stashplayer.core.debug.StashDebugLogBuffer
import gomeng.dev.stashplayer.core.network.DownloaderProgress
import gomeng.dev.stashplayer.core.network.StashDownloaderClient
import gomeng.dev.stashplayer.core.network.StashGraphQlClient
import gomeng.dev.stashplayer.core.network.StashJobStatus
import gomeng.dev.stashplayer.core.network.StashScanOptions
import gomeng.dev.stashplayer.core.network.StashServerAdminClient
import gomeng.dev.stashplayer.core.network.StashServerProfile
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Queued: the server job waits behind other jobs (Stash runs plugin tasks one at a time). Interrupted: the app was
 * closed while it followed the download, so it no longer knows how it ended.
 */
enum class DownloadPhase { Starting, Queued, Downloading, Importing, Done, Failed, Cancelled, Interrupted }

data class StashDownload(
    val id: String,
    val url: String,
    val title: String?,
    val quality: String,
    val thumbnailUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val phase: DownloadPhase = DownloadPhase.Starting,
    val progress: DownloaderProgress? = null,
    val jobId: String? = null,
    val filePath: String? = null,
    val sceneId: String? = null,
    val error: String? = null,
) {
    val active: Boolean get() = phase in ACTIVE_PHASES
}

private val ACTIVE_PHASES = setOf(DownloadPhase.Starting, DownloadPhase.Queued, DownloadPhase.Downloading, DownloadPhase.Importing)

/** How often a running download is checked, and how long the scan of the new file may take. */
private const val DOWNLOAD_POLL_MS = 1_000L
private const val SCAN_TIMEOUT_MS = 180_000L
private const val SCENE_LOOKUP_ATTEMPTS = 5
private const val SCENE_LOOKUP_DELAY_MS = 2_000L

/** After the Stash job ends, how many more polls wait for the plugin's result file. */
private const val RESULT_GRACE_POLLS = 3

/**
 * Downloads through the Stash Downloader plugin, kept for the whole app process so they go on while you browse.
 * The download itself runs on the server; scanning the new file and filling in its scene happens here once the
 * file is there, as on the plugin's own page.
 */
object StashDownloads {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = ConcurrentHashMap<String, Job>()

    /** Downloads are handed to the server one at a time, so its queue keeps the order they were started in. */
    private val submitLock = Mutex()
    private val _items = MutableStateFlow<List<StashDownload>>(emptyList())
    val items: StateFlow<List<StashDownload>> = _items.asStateFlow()
    private var history: StashDownloadHistory? = null

    /** Loads the stored history once per process; downloads after that are saved as they change. */
    fun attach(context: Context) {
        if (history != null) return
        val store = StashDownloadHistory(context.applicationContext)
        history = store
        val restored = store.load().map { if (it.active) it.copy(phase = DownloadPhase.Interrupted) else it }
        _items.update { current -> current + restored.filter { old -> current.none { it.id == old.id } } }
    }

    /** Starts one download per link, listed and sent to the server in the given order. */
    fun startAll(profile: StashServerProfile, urls: List<String>, quality: String) {
        val downloads = urls.map { StashDownload(id = UUID.randomUUID().toString(), url = it, title = null, quality = quality) }
        _items.update { downloads + it }
        save()
        downloads.forEach { launch(profile, it) }
    }

    /** [title] and [thumbnailUrl] come from "Check link" when the user did that first. */
    fun start(profile: StashServerProfile, url: String, title: String?, quality: String, thumbnailUrl: String? = null) {
        val download = StashDownload(
            id = UUID.randomUUID().toString(),
            url = url,
            title = title?.trim()?.ifBlank { null },
            quality = quality,
            thumbnailUrl = thumbnailUrl,
        )
        _items.update { listOf(download) + it }
        save()
        launch(profile, download)
    }

    private fun launch(profile: StashServerProfile, download: StashDownload) {
        if (download.title == null || download.thumbnailUrl == null) describe(StashGraphQlClient(profile), download)
        running[download.id] = scope.launch {
            try {
                run(StashGraphQlClient(profile), download)
            } catch (cancelled: CancellationException) {
                change(download.id) { it.copy(phase = DownloadPhase.Cancelled) }
                throw cancelled
            } catch (failure: Throwable) {
                StashDebugLogBuffer.record("Downloads", "Download of ${download.url} failed", failure)
                change(download.id) { it.copy(phase = DownloadPhase.Failed, error = failure.message ?: "Download failed") }
            } finally {
                running.remove(download.id)
            }
        }
    }

    /** Stops the server job too, so the plugin stops downloading. */
    fun cancel(profile: StashServerProfile, id: String) {
        val jobId = _items.value.firstOrNull { it.id == id }?.jobId
        running.remove(id)?.cancel()
        change(id) { it.copy(phase = DownloadPhase.Cancelled) }
        if (jobId != null) scope.launch { runCatching { StashServerAdminClient(StashGraphQlClient(profile)).stopJob(jobId) } }
    }

    fun clearFinished() {
        _items.update { items -> items.filter { it.active } }
        save()
    }

    /** Fills in title and thumbnail from what yt-dlp finds on the page, next to the download, not before it. */
    private fun describe(graph: StashGraphQlClient, download: StashDownload) = scope.launch {
        val downloader = StashDownloaderClient(graph)
        val metadata = runCatching { downloader.extractMetadata(download.url, downloader.settings()) }.getOrNull() ?: return@launch
        change(download.id) { it.copy(title = it.title ?: metadata.title, thumbnailUrl = it.thumbnailUrl ?: metadata.thumbnailUrl) }
    }

    private suspend fun run(graph: StashGraphQlClient, download: StashDownload) {
        val downloader = StashDownloaderClient(graph)
        val resultId = "stashfeed-${download.id}"
        val progressId = download.id
        try {
            val jobId = submitLock.withLock {
                val settings = downloader.settings()
                downloader.startDownload(download.url, download.title, download.quality, downloader.outputDir(settings), settings, resultId, progressId)
            }
            change(download.id) { it.copy(phase = DownloadPhase.Queued, jobId = jobId) }

            val filePath = awaitFile(graph, downloader, jobId, resultId, progressId, download.id)
            change(download.id) { it.copy(phase = DownloadPhase.Importing, filePath = filePath) }

            val sceneId = importFile(graph, downloader, filePath)
            sceneId?.let { downloader.updateScene(it, download.title, download.url) }
            change(download.id) { it.copy(phase = DownloadPhase.Done, sceneId = sceneId) }
        } finally {
            runCatching { downloader.cleanup(resultId, progressId) }
        }
    }

    /** Follows the plugin's progress file until its result file names the downloaded file. */
    private suspend fun awaitFile(
        graph: StashGraphQlClient,
        downloader: StashDownloaderClient,
        jobId: String,
        resultId: String,
        progressId: String,
        downloadId: String,
    ): String {
        var gracePolls = 0
        while (true) {
            delay(DOWNLOAD_POLL_MS)
            runCatching { downloader.progress(progressId) }.getOrNull()?.let { progress -> change(downloadId) { it.copy(progress = progress) } }
            downloader.result(resultId)?.let { return it }
            when (runCatching { graph.findJobStatus(jobId) }.getOrDefault(StashJobStatus.Running)) {
                StashJobStatus.Ready -> change(downloadId) { it.copy(phase = DownloadPhase.Queued) }
                StashJobStatus.Running -> change(downloadId) { it.copy(phase = DownloadPhase.Downloading) }
                StashJobStatus.Failed -> error("The download task failed on the server. See the Stash log for details.")
                StashJobStatus.Cancelled -> error("The download was cancelled on the server.")
                StashJobStatus.Finished, StashJobStatus.Missing ->
                    if (++gracePolls > RESULT_GRACE_POLLS) error("The download ended without a file. See the Stash log for details.")
                else -> Unit
            }
        }
    }

    /** Scans just the new file, then finds the scene Stash made of it (none for images). */
    private suspend fun importFile(graph: StashGraphQlClient, downloader: StashDownloaderClient, filePath: String): String? {
        val options = runCatching { graph.findServerLibrarySettings().scanOptions }.getOrDefault(StashScanOptions())
        val scanJob = graph.scanMetadata(options, listOf(filePath))
        val deadline = System.currentTimeMillis() + SCAN_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val status = runCatching { graph.findJobStatus(scanJob) }.getOrDefault(StashJobStatus.Running)
            if (status != StashJobStatus.Ready && status != StashJobStatus.Running && status != StashJobStatus.Stopping) break
            delay(DOWNLOAD_POLL_MS)
        }
        repeat(SCENE_LOOKUP_ATTEMPTS) { attempt ->
            runCatching { downloader.findSceneIdByPath(filePath) }.getOrNull()?.let { return it }
            if (attempt < SCENE_LOOKUP_ATTEMPTS - 1) delay(SCENE_LOOKUP_DELAY_MS)
        }
        return null
    }

    /** Applies [transform]; saves the history unless only the progress moved (that changes every second). */
    private fun change(id: String, transform: (StashDownload) -> StashDownload) {
        var lasting = false
        _items.update { items ->
            items.map { item ->
                if (item.id != id) return@map item
                transform(item).also { updated -> lasting = updated.copy(progress = item.progress) != item }
            }
        }
        if (lasting) save()
    }

    private fun save() {
        val store = history ?: return
        scope.launch { store.save(_items.value) }
    }
}
