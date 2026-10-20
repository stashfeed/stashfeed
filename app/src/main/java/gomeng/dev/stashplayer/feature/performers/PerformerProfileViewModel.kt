package gomeng.dev.stashplayer.feature.performers

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gomeng.dev.stashplayer.core.model.GalleryImageModel
import gomeng.dev.stashplayer.core.model.PerformerDetailModel
import gomeng.dev.stashplayer.core.model.PerformerFeedItem
import gomeng.dev.stashplayer.core.model.PerformerFeedMergeState
import gomeng.dev.stashplayer.core.model.PerformerFeedPage
import gomeng.dev.stashplayer.core.model.PerformerSceneSort
import gomeng.dev.stashplayer.core.model.SceneCardModel
import gomeng.dev.stashplayer.core.model.StashGalleryFilterState
import gomeng.dev.stashplayer.core.model.StashImageFilterState
import gomeng.dev.stashplayer.core.model.StashSelectedEntity
import gomeng.dev.stashplayer.core.model.StashSortDirection
import gomeng.dev.stashplayer.core.model.StashVideoFilterState
import gomeng.dev.stashplayer.core.network.StashGraphQlClient
import gomeng.dev.stashplayer.core.network.StashServerProfile
import androidx.compose.runtime.mutableStateMapOf
import gomeng.dev.stashplayer.core.model.stashImageSortOptions
import gomeng.dev.stashplayer.core.ui.components.StashBrowseLayout
import gomeng.dev.stashplayer.core.ui.discovery.StashVideoFilterDraft
import gomeng.dev.stashplayer.feature.feed.FeedGalleryImageStore
import gomeng.dev.stashplayer.feature.gallery.GalleryFilterDraft
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

private const val PROFILE_FEED_PAGE_SIZE = 20
private const val PROFILE_SCENES_PAGE_SIZE = 30
private const val PROFILE_PHOTOS_PAGE_SIZE = 60

enum class PerformerProfileTab { Feed, Scenes, Photos }

sealed interface PerformerDetailState {
    data object Loading : PerformerDetailState
    data class Ready(val detail: PerformerDetailModel) : PerformerDetailState
    data class Failed(val message: String) : PerformerDetailState
    data object NotFound : PerformerDetailState
}

data class PerformerFeedUiState(
    val merge: PerformerFeedMergeState = PerformerFeedMergeState(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val hasLoadedOnce: Boolean = false,
)

internal class PerformerProfileViewModel : ViewModel() {
    var tab by mutableStateOf(PerformerProfileTab.Feed)
    var sceneSort by mutableStateOf(PerformerSceneSort.Recent)
        private set
    var sceneDirection by mutableStateOf(StashSortDirection.Desc)
        private set
    /** Extra criteria on the Scenes tab; the performer itself is always added. */
    var sceneFilter by mutableStateOf(StashVideoFilterState())
        private set
    var photoSort by mutableStateOf(stashImageSortOptions().first { it.id == "created_at" })
        private set
    var photoDirection by mutableStateOf(StashSortDirection.Desc)
        private set
    var photoFilter by mutableStateOf(StashImageFilterState())
        private set
    var sceneLayout by mutableStateOf(StashBrowseLayout.Compact)
    var photoLayout by mutableStateOf(StashBrowseLayout.Compact)

    /** Rating / O changes made from feed posts, shown until the feed reloads. */
    val sceneOverrides = mutableStateMapOf<String, SceneCardModel>()
    val galleryImages = FeedGalleryImageStore(viewModelScope) { profile }
    var detailState by mutableStateOf<PerformerDetailState>(PerformerDetailState.Loading)
        private set
    var feedState by mutableStateOf(PerformerFeedUiState())
        private set
    var isUpdatingFollow by mutableStateOf(false)
        private set

    private var profile: StashServerProfile? = null
    private var performerId: String = ""
    private var feedJob: Job? = null

    private val performerEntity: StashSelectedEntity
        get() = StashSelectedEntity(id = performerId, name = performerId)

    private fun client(): StashGraphQlClient = StashGraphQlClient(requireNotNull(profile))

    val scenes = PagedLoader<SceneCardModel>(
        scope = viewModelScope,
        pageSize = PROFILE_SCENES_PAGE_SIZE,
        keyOf = { it.id },
        fetch = { page, pageSize ->
            val result = client().findSceneCardsPage(
                perPage = pageSize,
                page = page,
                sort = sceneSort.graphQlSort,
                direction = sceneDirection,
                videoFilter = sceneFilterWithPerformer(sceneFilter),
            )
            LoadedPage(result.scenes, result.totalCount)
        },
    )

    val photos = PagedLoader<GalleryImageModel>(
        scope = viewModelScope,
        pageSize = PROFILE_PHOTOS_PAGE_SIZE,
        keyOf = { it.id },
        fetch = { page, pageSize ->
            val result = client().findImagesPage(
                perPage = pageSize,
                page = page,
                sort = photoSort.serverValue,
                direction = photoDirection,
                imageFilter = photoFilterWithPerformer(photoFilter),
            )
            LoadedPage(result.images, result.totalCount)
        },
    )

    fun bind(activeProfile: StashServerProfile, id: String) {
        if (activeProfile == profile && id == performerId) return
        profile = activeProfile
        performerId = id
        loadDetail()
        resetFeed()
        scenes.reset()
        photos.reset()
    }

    fun loadDetail() {
        val activeProfile = profile ?: return
        detailState = PerformerDetailState.Loading
        viewModelScope.launch {
            detailState = runCatching { StashGraphQlClient(activeProfile).findPerformerDetail(performerId) }.fold(
                onSuccess = { detail -> detail?.let(PerformerDetailState::Ready) ?: PerformerDetailState.NotFound },
                onFailure = { PerformerDetailState.Failed(it.message.orEmpty()) },
            )
        }
    }

    private fun sceneFilterWithPerformer(filter: StashVideoFilterState) =
        filter.copy(performers = (filter.performers + performerEntity).distinctBy { it.id })

    private fun photoFilterWithPerformer(filter: StashImageFilterState) =
        filter.copy(performers = (filter.performers + performerEntity).distinctBy { it.id })

    fun applySceneDraft(draft: StashVideoFilterDraft<PerformerSceneSort>) {
        if (draft.sort == sceneSort && draft.direction == sceneDirection && draft.filter == sceneFilter) return
        sceneSort = draft.sort
        sceneDirection = draft.direction
        sceneFilter = draft.filter
        scenes.reset()
    }

    fun applyPhotoDraft(draft: GalleryFilterDraft<StashImageFilterState>) {
        if (draft.sort == photoSort && draft.direction == photoDirection && draft.filter == photoFilter) return
        photoSort = draft.sort
        photoDirection = draft.direction
        photoFilter = draft.filter
        photos.reset()
    }

    fun clearSceneFilter(filter: StashVideoFilterState) {
        sceneFilter = filter
        scenes.reset()
    }

    fun clearPhotoFilter(filter: StashImageFilterState) {
        photoFilter = filter
        photos.reset()
    }

    suspend fun countScenes(draft: StashVideoFilterDraft<PerformerSceneSort>): Int? {
        val activeProfile = profile ?: return null
        return StashGraphQlClient(activeProfile).findSceneCardsPage(
            perPage = 1,
            sort = draft.sort.graphQlSort,
            direction = draft.direction,
            videoFilter = sceneFilterWithPerformer(draft.filter),
        ).totalCount
    }

    suspend fun countPhotos(draft: GalleryFilterDraft<StashImageFilterState>): Int? {
        val activeProfile = profile ?: return null
        return StashGraphQlClient(activeProfile).findImagesPage(
            perPage = 1,
            sort = draft.sort.serverValue,
            direction = draft.direction,
            imageFilter = photoFilterWithPerformer(draft.filter),
        ).totalCount
    }

    fun rateScene(scene: SceneCardModel, rating100: Int?) = updateScene(scene.copy(rating100 = rating100)) { client ->
        client.updateSceneRating(scene.id, rating100)
        null
    }

    fun addO(scene: SceneCardModel) = updateScene(scene.copy(oCounter = (scene.oCounter ?: 0) + 1)) { it.addO(scene.id) }

    fun removeO(scene: SceneCardModel) {
        if ((scene.oCounter ?: 0) <= 0) return
        updateScene(scene.copy(oCounter = (scene.oCounter ?: 0) - 1)) { it.deleteO(scene.id) }
    }

    /** Shows [optimistic] right away; the server's O count (when returned) replaces it, a failure reverts it. */
    private fun updateScene(optimistic: SceneCardModel, action: suspend (StashGraphQlClient) -> Int?) {
        val activeProfile = profile ?: return
        val previous = sceneOverrides[optimistic.id]
        sceneOverrides[optimistic.id] = optimistic
        viewModelScope.launch {
            runCatching { action(StashGraphQlClient(activeProfile)) }
                .onSuccess { count -> count?.let { sceneOverrides[optimistic.id] = optimistic.copy(oCounter = it) } }
                .onFailure { if (previous != null) sceneOverrides[optimistic.id] = previous else sceneOverrides.remove(optimistic.id) }
        }
    }

    fun toggleFollow(onFailure: (String) -> Unit) {
        val ready = detailState as? PerformerDetailState.Ready ?: return
        if (isUpdatingFollow) return
        val target = !ready.detail.card.favorite
        val activeProfile = profile ?: return
        isUpdatingFollow = true
        detailState = ready.withFavorite(target)
        viewModelScope.launch {
            val result = runCatching { StashGraphQlClient(activeProfile).setPerformerFavorite(performerId, target) }
            isUpdatingFollow = false
            result.onSuccess { confirmed ->
                (detailState as? PerformerDetailState.Ready)?.let { detailState = it.withFavorite(confirmed) }
                PerformerFavoriteChanges.publish(PerformerFavoriteChange(performerId, confirmed))
            }.onFailure { throwable ->
                (detailState as? PerformerDetailState.Ready)?.let { detailState = it.withFavorite(!target) }
                onFailure(throwable.message.orEmpty())
            }
        }
    }

    private fun resetFeed() {
        feedJob?.cancel()
        sceneOverrides.clear()
        galleryImages.clear()
        feedState = PerformerFeedUiState()
        loadMoreFeed()
    }

    /** Fetches whichever source (scenes / galleries) ran dry, then releases every item whose order is certain. */
    fun loadMoreFeed() {
        val current = feedState
        if (current.isLoading || current.merge.isComplete) return
        val activeProfile = profile ?: return
        val requestedPerformer = performerId
        feedState = current.copy(isLoading = true, error = null)
        feedJob = viewModelScope.launch {
            val result = runCatching {
                var merge = current.merge
                // Keep fetching until something new can be shown or both sources are done.
                do {
                    val sceneFetch = if (merge.needsSceneFetch) async { fetchFeedScenes(activeProfile, merge.nextScenePage) } else null
                    val galleryFetch = if (merge.needsGalleryFetch) async { fetchFeedGalleries(activeProfile, merge.nextGalleryPage) } else null
                    sceneFetch?.await()?.let { merge = merge.withScenePage(it, PROFILE_FEED_PAGE_SIZE) }
                    galleryFetch?.await()?.let { merge = merge.withGalleryPage(it, PROFILE_FEED_PAGE_SIZE) }
                    val before = merge.emitted.size
                    merge = merge.merge()
                } while (merge.emitted.size == before && !merge.isComplete && (merge.needsSceneFetch || merge.needsGalleryFetch))
                merge
            }
            if (requestedPerformer != performerId) return@launch
            feedState = result.fold(
                onSuccess = { merge -> PerformerFeedUiState(merge = merge, hasLoadedOnce = true) },
                onFailure = { feedState.copy(isLoading = false, error = it.message.orEmpty(), hasLoadedOnce = true) },
            )
        }
    }

    fun retryFeed() {
        feedState = feedState.copy(error = null)
        loadMoreFeed()
    }

    private suspend fun fetchFeedScenes(
        activeProfile: StashServerProfile,
        page: Int,
    ): PerformerFeedPage<PerformerFeedItem.Scene> {
        val result = StashGraphQlClient(activeProfile).findFeedScenesPage(
            perPage = PROFILE_FEED_PAGE_SIZE,
            page = page,
            sceneFilter = mapOf("performers" to mapOf("value" to listOf(performerId), "modifier" to "INCLUDES")),
        )
        return PerformerFeedPage(
            items = result.items.map { feed ->
                PerformerFeedItem.Scene(scene = feed.scene, date = feed.date, createdAt = feed.createdAt, feed = feed)
            },
            totalCount = result.totalCount,
        )
    }

    private suspend fun fetchFeedGalleries(
        activeProfile: StashServerProfile,
        page: Int,
    ): PerformerFeedPage<PerformerFeedItem.Gallery> {
        val result = StashGraphQlClient(activeProfile).findFeedGalleriesPage(
            perPage = PROFILE_FEED_PAGE_SIZE,
            page = page,
            galleryFilter = mapOf("performers" to mapOf("value" to listOf(performerId), "modifier" to "INCLUDES")),
        )
        return PerformerFeedPage(
            items = result.items.map { feed ->
                PerformerFeedItem.Gallery(gallery = feed.gallery, createdAt = feed.createdAt, feed = feed)
            },
            totalCount = result.totalCount,
        )
    }
}

private fun PerformerDetailState.Ready.withFavorite(favorite: Boolean): PerformerDetailState.Ready =
    copy(detail = detail.copy(card = detail.card.copy(favorite = favorite)))
