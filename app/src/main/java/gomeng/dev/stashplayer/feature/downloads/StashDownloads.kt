package gomeng.dev.stashplayer.feature.downloads

import android.content.Context
import gomeng.dev.stashplayer.core.debug.StashDebugLogBuffer
import gomeng.dev.stashplayer.core.network.DownloaderMetadata
import gomeng.dev.stashplayer.core.network.DownloaderProgress
import gomeng.dev.stashplayer.core.network.DownloaderSettings
import gomeng.dev.stashplayer.core.network.LibraryDetails
import gomeng.dev.stashplayer.core.network.RedditImagePost
import gomeng.dev.stashplayer.core.network.RedditPostResolver
import gomeng.dev.stashplayer.core.network.RedditStashAgent
import gomeng.dev.stashplayer.core.network.RedditStashAgentClient
import gomeng.dev.stashplayer.core.network.StashDownloaderClient
import gomeng.dev.stashplayer.core.network.StashGraphQlClient
import gomeng.dev.stashplayer.core.network.StashJobStatus
import gomeng.dev.stashplayer.core.network.StashLibraryLinks
import gomeng.dev.stashplayer.core.network.StashScanOptions
import gomeng.dev.stashplayer.core.network.StashServerAdminClient
import gomeng.dev.stashplayer.core.network.StashServerProfile
import gomeng.dev.stashplayer.core.network.cleanSourceUrl
import gomeng.dev.stashplayer.core.network.isDirectImageUrl
import gomeng.dev.stashplayer.core.network.isRedditPostLink
import gomeng.dev.stashplayer.core.network.redditPostId
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** What a link turned out to be: a video for yt-dlp, or one or more images from a direct link or a Reddit post. */
enum class DownloadKind { Video, Image, Gallery }

/**
 * Queued: the server job waits behind other jobs (Stash runs plugin tasks one at a time). AlreadyInLibrary: a scene,
 * image or gallery with this source URL exists, so nothing was downloaded. Interrupted: the app was closed while it
 * followed the download, so it no longer knows how it ended.
 */
enum class DownloadPhase { Starting, Queued, Downloading, Importing, Done, AlreadyInLibrary, Failed, Cancelled, Interrupted }

data class StashDownload(
    val id: String,
    val url: String,
    val title: String?,
    val quality: String,
    val thumbnailUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val kind: DownloadKind = DownloadKind.Video,
    /** Names found on the page, linked to the library after the import. */
    val performers: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val studio: String? = null,
    val phase: DownloadPhase = DownloadPhase.Starting,
    val progress: DownloaderProgress? = null,
    /** What the Reddit Stash Agent is doing right now, for downloads it handles. */
    val step: String? = null,
    /** For galleries: images downloaded so far, of [partCount]. */
    val partDone: Int = 0,
    val partCount: Int = 0,
    val jobId: String? = null,
    val sceneId: String? = null,
    val imageId: String? = null,
    val galleryId: String? = null,
    val error: String? = null,
) {
    val active: Boolean get() = phase in ACTIVE_PHASES

    /** Ended without a result that is in the library; can be tried again. */
    val retryable: Boolean get() = phase == DownloadPhase.Failed || phase == DownloadPhase.Cancelled || phase == DownloadPhase.Interrupted
}

private val ACTIVE_PHASES = setOf(DownloadPhase.Starting, DownloadPhase.Queued, DownloadPhase.Downloading, DownloadPhase.Importing)

/** How often a running download is checked, and how long the scan of the new files may take. */
private const val DOWNLOAD_POLL_MS = 1_000L
private const val SCAN_TIMEOUT_MS = 180_000L
private const val LOOKUP_ATTEMPTS = 5
private const val LOOKUP_DELAY_MS = 2_000L

/** After the Stash job ends, how many more polls wait for the plugin's result file. */
private const val RESULT_GRACE_POLLS = 3

/** How long the import waits for the page details (performers, tags) that are fetched next to the download. */
private const val DETAILS_WAIT_MS = 60_000L

/** How often a download handled by the Reddit Stash Agent is checked. */
private const val AGENT_POLL_MS = 2_000L

/** Downloaded images go in this folder of the image library; each gallery gets its own folder in it. */
private const val IMAGE_FOLDER = "Stashfeed"

/**
 * Downloads through the Stash Downloader plugin, kept for the whole app process so they go on while you browse
 * (and, through [StashDownloadService], while the app is in the background). The plugin downloads on the server;
 * scanning the new files and filling in the scene, image or gallery happens here, as on the plugin's own page.
 */
object StashDownloads {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = ConcurrentHashMap<String, Job>()
    private val reddit = RedditPostResolver()

    /** Downloads are handed to the server one at a time, so its queue keeps the order they were started in. */
    private val submitLock = Mutex()
    private val _items = MutableStateFlow<List<StashDownload>>(emptyList())
    val items: StateFlow<List<StashDownload>> = _items.asStateFlow()
    private var history: StashDownloadHistory? = null
    private var appContext: Context? = null

    /** Loads the stored history once per process; downloads after that are saved as they change. */
    fun attach(context: Context) {
        if (history != null) return
        appContext = context.applicationContext
        val store = StashDownloadHistory(context.applicationContext)
        history = store
        val restored = store.load().map { if (it.active) it.copy(phase = DownloadPhase.Interrupted) else it }
        _items.update { current -> current + restored.filter { old -> current.none { it.id == old.id } } }
    }

    /** Starts one download per link, listed and sent to the server in the given order. */
    fun startAll(profile: StashServerProfile, urls: List<String>, quality: String) {
        val downloads = urls.map { newDownload(it, title = null, quality = quality, metadata = null) }
        _items.update { downloads + it }
        save()
        downloads.forEach { launch(profile, it, force = false) }
    }

    /** [title] and [metadata] come from "Check link" when the user did that first; [force] skips the library check. */
    fun start(
        profile: StashServerProfile,
        url: String,
        title: String?,
        quality: String,
        metadata: DownloaderMetadata? = null,
        force: Boolean = false,
    ) {
        val download = newDownload(url, title, quality, metadata)
        _items.update { listOf(download) + it }
        save()
        launch(profile, download, force)
    }

    /** Runs a finished download again in its place: after a failure, or with [force] despite a library match. */
    fun retry(profile: StashServerProfile, id: String, force: Boolean = false) {
        val old = _items.value.firstOrNull { it.id == id } ?: return
        val fresh = StashDownload(
            id = UUID.randomUUID().toString(),
            url = old.url,
            title = old.title,
            quality = old.quality,
            thumbnailUrl = old.thumbnailUrl,
            performers = old.performers,
            tags = old.tags,
            studio = old.studio,
        )
        _items.update { items -> items.map { if (it.id == id) fresh else it } }
        save()
        launch(profile, fresh, force)
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

    private fun newDownload(url: String, title: String?, quality: String, metadata: DownloaderMetadata?) = StashDownload(
        id = UUID.randomUUID().toString(),
        url = cleanSourceUrl(url),
        title = title?.trim()?.ifBlank { null } ?: metadata?.title,
        quality = quality,
        thumbnailUrl = metadata?.thumbnailUrl,
        performers = metadata?.performers.orEmpty(),
        tags = metadata?.tags.orEmpty(),
        studio = metadata?.uploader,
    )

    private fun launch(profile: StashServerProfile, download: StashDownload, force: Boolean) {
        appContext?.let(StashDownloadService::ensureRunning)
        running[download.id] = scope.launch {
            try {
                run(StashGraphQlClient(profile), download, force)
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

    private suspend fun run(graph: StashGraphQlClient, download: StashDownload, force: Boolean) = coroutineScope {
        // Reddit posts go to the Reddit Stash Agent when it's there: it has a Reddit login and imports them itself.
        if (isRedditPostLink(download.url) && redditAgentInstalled(graph)) {
            runWithRedditAgent(graph, download, force)
            return@coroutineScope
        }
        val downloader = StashDownloaderClient(graph)
        val links = StashLibraryLinks(graph)
        val settings = downloader.settings()

        // A Reddit image or gallery post, or a link straight to an image, is downloaded image by image.
        val postLookup = redditPostId(download.url)?.let { runCatching { reddit.imagePost(download.url) } }
        val post = postLookup?.getOrNull()
        // When Reddit won't show the post, yt-dlp still gets a try (it handles Reddit videos); its error then says why.
        val redditRefusal = postLookup?.exceptionOrNull()?.message
        val imageUrls = post?.imageUrls ?: listOf(download.url).filter(::isDirectImageUrl)
        val kind = when {
            imageUrls.size > 1 -> DownloadKind.Gallery
            imageUrls.size == 1 -> DownloadKind.Image
            else -> DownloadKind.Video
        }
        change(download.id) {
            it.copy(
                kind = kind,
                title = it.title ?: post?.title,
                thumbnailUrl = it.thumbnailUrl ?: imageUrls.firstOrNull(),
                partCount = if (kind == DownloadKind.Gallery) imageUrls.size else 0,
            )
        }

        if (!force) {
            val existing = when (kind) {
                DownloadKind.Video -> links.findSceneIdByUrl(download.url)?.let { download.copy(sceneId = it) }
                DownloadKind.Image -> links.findImageIdByUrl(download.url)?.let { download.copy(imageId = it) }
                DownloadKind.Gallery -> links.findGalleryIdByUrl(download.url)?.let { download.copy(galleryId = it) }
            }
            if (existing != null) {
                change(download.id) {
                    it.copy(phase = DownloadPhase.AlreadyInLibrary, sceneId = existing.sceneId, imageId = existing.imageId, galleryId = existing.galleryId)
                }
                return@coroutineScope
            }
        }

        if (kind == DownloadKind.Video) {
            // Title, thumbnail, performers and tags from the page, fetched while the server downloads.
            val details = async { describe(downloader, settings, download.id) }
            val filePath = try {
                downloadFile(graph, downloader, settings, download.id, download.url, download.title, downloader.outputDir(settings), DownloadPart.Single)
            } catch (failure: Exception) {
                if (redditRefusal == null || failure is CancellationException) throw failure
                error("$redditRefusal. yt-dlp could not download it either: ${failure.message}")
            }
            change(download.id) { it.copy(phase = DownloadPhase.Importing) }
            val sceneId = scanAndFind(graph, listOf(filePath)) { downloader.findSceneIdByPath(filePath) }
            withTimeoutOrNull(DETAILS_WAIT_MS) { details.await() } ?: details.cancel()
            sceneId?.let { links.updateScene(it, libraryDetails(links, settings, current(download.id), post)) }
            change(download.id) { it.copy(phase = DownloadPhase.Done, sceneId = sceneId) }
        } else {
            val root = downloader.outputDir(settings, images = true).trimEnd('/') + "/" + IMAGE_FOLDER
            val folder = if (kind == DownloadKind.Gallery) "$root/${galleryFolderName(post, download)}" else root
            val paths = imageUrls.mapIndexed { index, imageUrl ->
                downloadFile(graph, downloader, settings, download.id, imageUrl, null, folder, DownloadPart.Image(index, imageUrls.size))
                    .also { change(download.id) { it.copy(partDone = index + 1) } }
            }
            change(download.id) { it.copy(phase = DownloadPhase.Importing) }
            val details = libraryDetails(links, settings, current(download.id), post)
            if (kind == DownloadKind.Image) {
                val imageId = scanAndFind(graph, paths) { links.findImageIdByPath(paths.single()) }
                imageId?.let { links.updateImage(it, details) }
                change(download.id) { it.copy(phase = DownloadPhase.Done, imageId = imageId) }
            } else {
                val imageIds = scanAndFind(graph, listOf(folder)) {
                    links.findImageIdsInFolder(folder).takeIf { found -> found.size >= paths.size }
                } ?: links.findImageIdsInFolder(folder)
                if (details.performerIds.isNotEmpty()) links.addPerformersToImages(imageIds, details.performerIds)
                // Stash may have made a gallery of the folder itself; otherwise Stashfeed makes one.
                val galleryId = links.findGalleryIdByFolder(folder)?.also { links.updateGallery(it, details) }
                    ?: imageIds.takeIf { it.isNotEmpty() }?.let { links.createGallery(details, it) }
                change(download.id) { it.copy(phase = DownloadPhase.Done, galleryId = galleryId) }
            }
        }
    }

    private suspend fun redditAgentInstalled(graph: StashGraphQlClient): Boolean =
        runCatching { StashServerAdminClient(graph).plugins().any { it.id == RedditStashAgent.PLUGIN_ID && it.enabled } }.getOrDefault(false)

    /** Lets the agent download and import the post, following its job until it ends. */
    private suspend fun runWithRedditAgent(graph: StashGraphQlClient, download: StashDownload, force: Boolean) {
        if (!force) {
            findInLibrary(StashLibraryLinks(graph), download.url)?.let { match ->
                change(download.id) { it.copy(phase = DownloadPhase.AlreadyInLibrary, sceneId = match.sceneId, imageId = match.imageId, galleryId = match.galleryId) }
                return
            }
        }
        val agent = RedditStashAgentClient(StashServerAdminClient(graph))
        var job = agent.downloadPost(download.url)
        while (true) {
            val current = job
            change(download.id) {
                it.copy(
                    phase = if (current.status == "queued") DownloadPhase.Queued else DownloadPhase.Downloading,
                    progress = DownloaderProgress(current.progress * 100f, null, null, null, null),
                    step = current.stepLabel,
                )
            }
            if (current.finished) break
            delay(AGENT_POLL_MS)
            job = runCatching { agent.downloadStatus(current.id) }.getOrElse { current }
        }
        if (job.status != "success") error(job.error ?: "The Reddit Stash Agent could not download the post")
        val kind = when {
            job.galleryId != null || job.imageIds.size > 1 -> DownloadKind.Gallery
            job.imageIds.isNotEmpty() && job.sceneIds.isEmpty() -> DownloadKind.Image
            else -> DownloadKind.Video
        }
        change(download.id) {
            it.copy(
                kind = kind,
                title = it.title ?: job.title,
                phase = if (job.alreadyDownloaded) DownloadPhase.AlreadyInLibrary else DownloadPhase.Done,
                step = null,
                sceneId = job.sceneIds.firstOrNull(),
                // A gallery shows its first image as the thumbnail.
                imageId = job.imageIds.firstOrNull(),
                galleryId = job.galleryId,
            )
        }
    }

    private sealed interface DownloadPart {
        data object Single : DownloadPart
        data class Image(val index: Int, val count: Int) : DownloadPart
    }

    /** One plugin task: hands the URL to the server, follows its progress and returns the downloaded file's path. */
    private suspend fun downloadFile(
        graph: StashGraphQlClient,
        downloader: StashDownloaderClient,
        settings: DownloaderSettings,
        downloadId: String,
        url: String,
        title: String?,
        outputDir: String,
        part: DownloadPart,
    ): String {
        val partId = if (part is DownloadPart.Image) "$downloadId-${part.index}" else downloadId
        val resultId = "stashfeed-$partId"
        val quality = current(downloadId)?.quality ?: "best"
        try {
            val jobId = submitLock.withLock { downloader.startDownload(url, title, quality, outputDir, settings, resultId, partId) }
            change(downloadId) { it.copy(phase = DownloadPhase.Queued, jobId = jobId, progress = null) }
            var gracePolls = 0
            while (true) {
                delay(DOWNLOAD_POLL_MS)
                runCatching { downloader.progress(partId) }.getOrNull()?.let { progress -> change(downloadId) { it.copy(progress = progress) } }
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
        } finally {
            runCatching { downloader.cleanup(resultId, partId) }
        }
    }

    /** Scans just [paths] and waits for the scan, then asks [find] a few times until Stash has indexed the result. */
    private suspend fun <T : Any> scanAndFind(graph: StashGraphQlClient, paths: List<String>, find: suspend () -> T?): T? {
        val options = runCatching { graph.findServerLibrarySettings().scanOptions }.getOrDefault(StashScanOptions())
        val scanJob = graph.scanMetadata(options, paths)
        val deadline = System.currentTimeMillis() + SCAN_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val status = runCatching { graph.findJobStatus(scanJob) }.getOrDefault(StashJobStatus.Running)
            if (status != StashJobStatus.Ready && status != StashJobStatus.Running && status != StashJobStatus.Stopping) break
            delay(DOWNLOAD_POLL_MS)
        }
        repeat(LOOKUP_ATTEMPTS) { attempt ->
            runCatching { find() }.getOrNull()?.let { return it }
            if (attempt < LOOKUP_ATTEMPTS - 1) delay(LOOKUP_DELAY_MS)
        }
        return null
    }

    /** Fills in what the page says about the video: title, thumbnail, performers, tags and the uploader as studio. */
    private suspend fun describe(downloader: StashDownloaderClient, settings: DownloaderSettings, downloadId: String) {
        val download = current(downloadId) ?: return
        if (download.title != null && download.thumbnailUrl != null && (download.performers.isNotEmpty() || download.tags.isNotEmpty())) return
        val metadata = runCatching { downloader.extractMetadata(download.url, settings) }.getOrNull() ?: return
        change(downloadId) {
            it.copy(
                title = it.title ?: metadata.title,
                thumbnailUrl = it.thumbnailUrl ?: metadata.thumbnailUrl,
                performers = it.performers.ifEmpty { metadata.performers },
                tags = it.tags.ifEmpty { metadata.tags },
                studio = it.studio ?: metadata.uploader,
            )
        }
    }

    /**
     * What to set on the new scene, image or gallery. Names are matched to the library and created only when the
     * Stash Downloader's settings allow it; a Reddit post's author is matched through the performer's Reddit URL.
     */
    private suspend fun libraryDetails(
        links: StashLibraryLinks,
        settings: DownloaderSettings,
        download: StashDownload?,
        post: RedditImagePost?,
    ): LibraryDetails {
        download ?: error("Download is gone")
        val redditPerformer = post?.author?.let { author ->
            links.findPerformerIdByUrl("reddit.com/user/$author") ?: links.findPerformerIdByUrl("reddit.com/u/$author")
        }
        val performerIds = runCatching { links.resolvePerformers(download.performers, settings.createPerformers) }.getOrDefault(emptyList())
        return LibraryDetails(
            title = download.title,
            url = download.url,
            performerIds = (listOfNotNull(redditPerformer) + performerIds).distinct(),
            tagIds = runCatching { links.resolveTags(download.tags, settings.createTags) }.getOrDefault(emptyList()),
            studioId = runCatching { links.resolveStudio(download.studio, settings.createStudios) }.getOrNull(),
        )
    }

    private fun current(id: String): StashDownload? = _items.value.firstOrNull { it.id == id }

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

/** "Title [id]" with characters that are not allowed in folder names removed. */
private fun galleryFolderName(post: RedditImagePost?, download: StashDownload): String {
    val name = (post?.title ?: download.title ?: "Gallery").replace(Regex("""[\\/:*?"<>|\n\r\t]"""), " ").trim().take(80)
    val id = post?.id ?: download.id.take(8)
    return "$name [$id]"
}
