package com.naaammme.bbspace.feature.history.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naaammme.bbspace.core.domain.history.HistoryRepository
import com.naaammme.bbspace.core.model.HistoryItem
import com.naaammme.bbspace.core.model.HistoryTab
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HistorySearchUiState(
    val tab: HistoryTab = HistoryTab.ALL,
    val input: String = "",
    val query: String = "",
    val items: List<HistoryItem> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val errorMessage: String? = null
) {
    val canLoadMore: Boolean
        get() = hasMore && !isLoading && !isLoadingMore
}

@HiltViewModel
class HistorySearchViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: HistoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        HistorySearchUiState(
            tab = HistoryTab.fromBusiness(
                savedStateHandle.get<String>(TAB_ARG).orEmpty()
            )
        )
    )
    val uiState: StateFlow<HistorySearchUiState> = _uiState.asStateFlow()

    private var nextPage = 1

    fun updateKeyword(value: String) {
        _uiState.update { it.copy(input = value) }
    }

    fun submitSearch() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore) return
        val keyword = state.input.trim()
        if (keyword.isBlank()) {
            nextPage = 1
            _uiState.update {
                it.copy(
                    input = "",
                    query = "",
                    items = emptyList(),
                    isLoading = false,
                    isLoadingMore = false,
                    hasMore = false,
                    errorMessage = null
                )
            }
            return
        }
        nextPage = 1
        _uiState.update {
            it.copy(
                input = keyword,
                query = keyword,
                items = emptyList(),
                isLoading = true,
                isLoadingMore = false,
                hasMore = false,
                errorMessage = null
            )
        }
        search(reset = true)
    }

    fun loadMore() {
        if (!_uiState.value.canLoadMore) return
        _uiState.update {
            it.copy(
                isLoadingMore = true,
                errorMessage = null
            )
        }
        search(reset = false)
    }

    private fun search(reset: Boolean) {
        val state = _uiState.value
        val pageNo = if (reset) 1 else nextPage
        viewModelScope.launch {
            runCatching {
                repo.search(
                    keyword = state.query,
                    tab = state.tab,
                    page = pageNo
                )
            }.onSuccess { page ->
                nextPage = pageNo + 1
                _uiState.update {
                    it.copy(
                        items = if (reset) page.items else it.items + page.items,
                        isLoading = false,
                        isLoadingMore = false,
                        hasMore = page.hasMore,
                        errorMessage = null
                    )
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        errorMessage = "搜索历史记录失败"
                    )
                }
            }
        }
    }

    private companion object {
        const val TAB_ARG = "tab"
    }
}
