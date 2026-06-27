package com.naaammme.bbspace.feature.dynamic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naaammme.bbspace.core.common.log.Logger
import com.naaammme.bbspace.core.auth.AuthRepository
import com.naaammme.bbspace.core.dynamic.DynamicRepository
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
    private val authRepo: AuthRepository,
    private val repo: DynamicRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        DynamicUiState(isLoggedIn = authRepo.currentMidFlow.value > 0)
    )
    val uiState: StateFlow<DynamicUiState> = _uiState.asStateFlow()
    private var authVersion = 0L

    init {
        viewModelScope.launch {
            authRepo.currentMidFlow.collect { mid ->
                authVersion += 1L
                if (mid > 0) {
                    _uiState.update {
                        it.copy(
                            isLoggedIn = true,
                            errorMessage = null,
                            loadMoreError = null
                        )
                    }
                    if (_uiState.value.items.isNotEmpty()) return@collect
                    refresh()
                } else {
                    _uiState.value = DynamicUiState(isLoggedIn = false)
                }
            }
        }
    }

    fun refresh() {
        val state = _uiState.value
        if (!state.isLoggedIn || state.isLoading || state.isRefreshing || state.isLoadingMore) return
        val hasItems = state.items.isNotEmpty()
        _uiState.update {
            it.copy(
                isLoading = !hasItems,
                isRefreshing = hasItems,
                isLoadingMore = false,
                errorMessage = null,
                loadMoreError = null
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
                loadMoreError = null
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
        val version = authVersion
        try {
            val page = repo.fetchAll(
                cursor = if (reset) DEFAULT_CURSOR else state.cursor,
                refresh = refresh
            )
            _uiState.update {
                if (version != authVersion || !it.isLoggedIn) return@update it
                it.copy(
                    upList = page.upList ?: it.upList,
                    items = if (reset) page.items else it.items.mergePage(page.items),
                    cursor = page.cursor,
                    isLoading = false,
                    isRefreshing = false,
                    isLoadingMore = false,
                    hasMore = page.hasMore,
                    errorMessage = null,
                    loadMoreError = null
                )
            }
        } catch (e: Exception) {
            if (version != authVersion) return
            Logger.e(TAG, e) { "加载动态失败" }
            _uiState.update {
                if (!it.isLoggedIn) return@update it
                val msg = e.message ?: if (reset) "加载动态失败" else "加载更多动态失败"
                if (reset) {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        isLoadingMore = false,
                        errorMessage = msg
                    )
                } else {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        isLoadingMore = false,
                        loadMoreError = msg
                    )
                }
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
