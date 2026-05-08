package com.naaammme.bbspace.feature.dynamic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naaammme.bbspace.core.common.log.Logger
import com.naaammme.bbspace.core.domain.dynamic.DynamicRepository
import com.naaammme.bbspace.core.model.DynamicCursor
import com.naaammme.bbspace.core.model.DynamicItem
import com.naaammme.bbspace.core.model.DynamicRefresh
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class DynamicViewModel @Inject constructor(
    private val repo: DynamicRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DynamicUiState())
    val uiState: StateFlow<DynamicUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val state = _uiState.value
        if (state.isLoading || state.isRefreshing || state.isLoadingMore) return
        val hasItems = state.items.isNotEmpty()
        _uiState.update {
            it.copy(
                isLoading = !hasItems,
                isRefreshing = hasItems,
                isLoadingMore = false,
                errorMessage = null,
                errorOnLoadMore = false,
                cursor = DEFAULT_CURSOR
            )
        }
        viewModelScope.launch {
            load(refresh = DynamicRefresh.NEW, reset = true)
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
            load(refresh = DynamicRefresh.HISTORY, reset = false)
        }
    }

    private suspend fun load(
        refresh: DynamicRefresh,
        reset: Boolean
    ) {
        val state = _uiState.value
        try {
            val page = repo.fetchAll(
                cursor = if (reset) DEFAULT_CURSOR else state.cursor,
                refresh = refresh
            )
            _uiState.update {
                it.copy(
                    items = if (reset) page.items else it.items.mergePage(page.items),
                    cursor = page.cursor,
                    isLoading = false,
                    isRefreshing = false,
                    isLoadingMore = false,
                    hasMore = page.hasMore,
                    errorMessage = null,
                    errorOnLoadMore = false
                )
            }
        } catch (e: Exception) {
            Logger.e(TAG, e) { "加载动态失败" }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    isLoadingMore = false,
                    errorMessage = e.message ?: "加载动态失败",
                    errorOnLoadMore = !reset
                )
            }
        }
    }

    private fun List<DynamicItem>.mergePage(
        incoming: List<DynamicItem>
    ): List<DynamicItem> {
        if (isEmpty()) return incoming
        if (incoming.isEmpty()) return this
        val seen = HashSet<String>(size + incoming.size)
        return buildList(size + incoming.size) {
            for (item in this@mergePage) {
                if (seen.add(item.id)) add(item)
            }
            for (item in incoming) {
                if (seen.add(item.id)) add(item)
            }
        }
    }

    private companion object {
        const val TAG = "DynamicViewModel"
        val DEFAULT_CURSOR = DynamicCursor()
    }
}
