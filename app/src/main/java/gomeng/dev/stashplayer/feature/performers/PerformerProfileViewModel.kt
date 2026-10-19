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
                direction = StashSortDirection.Desc,
                videoFilter = StashVideoFilterState(performers = listOf(performerEntity)),
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
                sort = "created_at",
                direction = StashSortDirection.Desc,
                imageFilter = StashImageFilterState(performers = listOf(performerEntity)),
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

    fun selectSceneSort(sort: PerformerSceneSort) {
        if (sort == sceneSort) return
        sceneSort = sort
        scenes.reset()
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
        val result = StashGraphQlClient(activeProfile).findSceneCardsPage(
            perPage = PROFILE_FEED_PAGE_SIZE,
            page = page,
            sort = "created_at",
            direction = StashSortDirection.Desc,
            videoFilter = StashVideoFilterState(performers = listOf(performerEntity)),
        )
        return PerformerFeedPage(
            items = result.scenes.map { scene ->
                PerformerFeedItem.Scene(scene = scene, date = scene.date, createdAt = scene.createdAt.orEmpty())
            },
            totalCount = result.totalCount,
        )
    }

    private suspend fun fetchFeedGalleries(
        activeProfile: StashServerProfile,
        page: Int,
    ): PerformerFeedPage<PerformerFeedItem.Gallery> {
        val result = StashGraphQlClient(activeProfile).findGalleryCardsPage(
            perPage = PROFILE_FEED_PAGE_SIZE,
            page = page,
            sort = "created_at",
            direction = StashSortDirection.Desc,
            galleryFilter = StashGalleryFilterState(performers = listOf(performerEntity)),
        )
        return PerformerFeedPage(
            items = result.galleries.map { gallery ->
                PerformerFeedItem.Gallery(gallery = gallery, createdAt = gallery.createdAt.orEmpty())
            },
            totalCount = result.totalCount,
        )
    }
}

private fun PerformerDetailState.Ready.withFavorite(favorite: Boolean): PerformerDetailState.Ready =
    copy(detail = detail.copy(card = detail.card.copy(favorite = favorite)))
