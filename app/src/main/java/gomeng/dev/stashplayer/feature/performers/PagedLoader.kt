package gomeng.dev.stashplayer.feature.performers

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class PagedListState<T>(
    val items: List<T> = emptyList(),
    val totalCount: Int = 0,
    val nextPage: Int = 1,
    val hasMore: Boolean = true,
    val isLoading: Boolean = false,
    val error: String? = null,
    val hasLoadedOnce: Boolean = false,
)

data class LoadedPage<T>(val items: List<T>, val totalCount: Int)

/**
 * Infinite-scroll pager: [reset] starts over, [loadNext] appends the next page. Responses from a superseded
 * generation (after a reset) are dropped, so changing a search while a page is in flight can't mix results.
 */
class PagedLoader<T>(
    private val scope: CoroutineScope,
    private val pageSize: Int,
    private val keyOf: (T) -> String,
    private val fetch: suspend (page: Int, pageSize: Int) -> LoadedPage<T>,
) {
    var state by mutableStateOf(PagedListState<T>())
        private set

    private var generation = 0
    private var job: Job? = null

    fun reset() {
        job?.cancel()
        generation += 1
        state = PagedListState()
        loadNext()
    }

    fun loadNext() {
        val current = state
        if (current.isLoading || !current.hasMore) return
        val requestGeneration = generation
        state = current.copy(isLoading = true, error = null)
        job = scope.launch {
            val result = runCatching { fetch(current.nextPage, pageSize) }
            if (requestGeneration != generation) return@launch
            state = result.fold(
                onSuccess = { page ->
                    val merged = (state.items + page.items).distinctBy(keyOf)
                    state.copy(
                        items = merged,
                        totalCount = page.totalCount,
                        nextPage = current.nextPage + 1,
                        hasMore = page.items.isNotEmpty() && merged.size < page.totalCount,
                        isLoading = false,
                        hasLoadedOnce = true,
                    )
                },
                onFailure = { throwable ->
                    state.copy(isLoading = false, error = throwable.message.orEmpty(), hasLoadedOnce = true)
                },
            )
        }
    }

    /** Clears an error and tries the failed page again. */
    fun retry() {
        if (state.error == null) return
        state = state.copy(error = null)
        loadNext()
    }

    fun updateItems(transform: (List<T>) -> List<T>) {
        state = state.copy(items = transform(state.items))
    }
}
