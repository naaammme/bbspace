package com.naaammme.bbspace.feature.history.watchlater

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naaammme.bbspace.core.common.log.Logger
import com.naaammme.bbspace.core.domain.history.WatchLaterRepository
import com.naaammme.bbspace.core.model.WatchLaterCursor
import com.naaammme.bbspace.core.model.WatchLaterItem
import com.naaammme.bbspace.core.model.WatchLaterTab
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WatchLaterUiState(
    val tab: WatchLaterTab = WatchLaterTab.UNFINISHED,
    val items: List<WatchLaterItem> = emptyList(),
    val countText: String? = null,
    val asc: Boolean = false,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val errorMessage: String? = null,
    val errorOnLoadMore: Boolean = false
) {
    val canLoadMore: Boolean
        get() = hasMore && !isLoading && !isRefreshing && !isLoadingMore
}

@HiltViewModel
class WatchLaterViewModel @Inject constructor(
    private val repo: WatchLaterRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WatchLaterUiState())
    val uiState: StateFlow<WatchLaterUiState> = _uiState.asStateFlow()

    private val cache = mutableMapOf<WatchLaterCacheKey, WatchLaterCache>()
    private var currentStartKey = ""
    private var splitKey = ""

    init {
        refresh()
    }

    fun selectTab(tab: WatchLaterTab) {
        val state = _uiState.value
        if (state.tab == tab || state.isLoading || state.isRefreshing || state.isLoadingMore) return
        val key = WatchLaterCacheKey(tab, state.asc)
        val cached = cache[key]
        if (cached != null) {
            currentStartKey = cached.nextStartKey
            splitKey = cached.splitKey
            _uiState.value = state.copy(
                tab = tab,
                items = cached.items,
                countText = cached.countText,
                hasMore = cached.hasMore,
                errorMessage = null,
                errorOnLoadMore = false
            )
            return
        }
        currentStartKey = ""
        _uiState.update {
            it.copy(
                tab = tab,
                items = emptyList(),
                countText = null,
                isLoading = true,
                isRefreshing = false,
                isLoadingMore = false,
                hasMore = false,
                errorMessage = null,
                errorOnLoadMore = false
            )
        }
        viewModelScope.launch {
            load(tab = tab, asc = state.asc, reset = true, refreshing = false)
        }
    }

    fun toggleSort() {
        val state = _uiState.value
        if (state.isLoading || state.isRefreshing || state.isLoadingMore) return
        val asc = !state.asc
        val key = WatchLaterCacheKey(state.tab, asc)
        val cached = cache[key]
        if (cached != null) {
            currentStartKey = cached.nextStartKey
            splitKey = cached.splitKey
            _uiState.value = state.copy(
                asc = asc,
                items = cached.items,
                countText = cached.countText,
                hasMore = cached.hasMore,
                errorMessage = null,
                errorOnLoadMore = false
            )
            return
        }
        currentStartKey = ""
        _uiState.update {
            it.copy(
                asc = asc,
                items = emptyList(),
                countText = null,
                isLoading = true,
                isRefreshing = false,
                isLoadingMore = false,
                hasMore = false,
                errorMessage = null,
                errorOnLoadMore = false
            )
        }
        viewModelScope.launch {
            load(tab = state.tab, asc = asc, reset = true, refreshing = false)
        }
    }

    fun refresh() {
        val state = _uiState.value
        if (state.isLoading || state.isRefreshing || state.isLoadingMore) return
        val hasItems = state.items.isNotEmpty()
        currentStartKey = ""
        splitKey = ""
        _uiState.update {
            it.copy(
                items = if (hasItems) it.items else emptyList(),
                countText = if (hasItems) it.countText else null,
                isLoading = !hasItems,
                isRefreshing = hasItems,
                isLoadingMore = false,
                hasMore = if (hasItems) it.hasMore else false,
                errorMessage = null,
                errorOnLoadMore = false
            )
        }
        viewModelScope.launch {
            load(tab = state.tab, asc = state.asc, reset = true, refreshing = hasItems)
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (!state.canLoadMore) return
        _uiState.update {
            it.copy(
                isLoadingMore = true,
                errorMessage = null,
                errorOnLoadMore = false
            )
        }
        viewModelScope.launch {
            load(tab = state.tab, asc = state.asc, reset = false, refreshing = false)
        }
    }

    private suspend fun load(
        tab: WatchLaterTab,
        asc: Boolean,
        reset: Boolean,
        refreshing: Boolean
    ) {
        try {
            val reqCursor = WatchLaterCursor(
                startKey = if (reset) "" else currentStartKey,
                splitKey = splitKey
            )
            val page = repo.fetchPage(tab, asc, reqCursor)
            currentStartKey = page.cursor.startKey
            splitKey = page.cursor.splitKey
            val items = if (reset) page.items else _uiState.value.items + page.items
            cache[WatchLaterCacheKey(tab, asc)] = WatchLaterCache(
                items = items,
                nextStartKey = currentStartKey,
                splitKey = splitKey,
                countText = page.countText,
                hasMore = page.hasMore
            )
            _uiState.update {
                if (it.tab != tab || it.asc != asc) return@update it
                it.copy(
                    items = items,
                    countText = page.countText,
                    isLoading = false,
                    isRefreshing = false,
                    isLoadingMore = false,
                    hasMore = page.hasMore,
                    errorMessage = null,
                    errorOnLoadMore = false
                )
            }
        } catch (e: Exception) {
            Logger.e(TAG, e) { "加载稍后再看失败" }
            _uiState.update {
                if (it.tab != tab || it.asc != asc) return@update it
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    isLoadingMore = false,
                    errorMessage = e.message ?: "加载稍后再看失败",
                    errorOnLoadMore = !reset && !refreshing
                )
            }
        }
    }

    private data class WatchLaterCacheKey(
        val tab: WatchLaterTab,
        val asc: Boolean
    )

    private data class WatchLaterCache(
        val items: List<WatchLaterItem>,
        val nextStartKey: String,
        val splitKey: String,
        val countText: String?,
        val hasMore: Boolean
    )

    private companion object {
        const val TAG = "WatchLaterViewModel"
    }
}
