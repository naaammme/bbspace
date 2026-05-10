package com.naaammme.bbspace.feature.home.listen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naaammme.bbspace.core.common.log.Logger
import com.naaammme.bbspace.core.domain.listen.ListenRepository
import com.naaammme.bbspace.core.model.listen.ListenItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ListenHomeViewModel @Inject constructor(
    private val listenRepo: ListenRepository
) : ViewModel() {

    companion object {
        private const val TAG = "ListenHomeVM"
    }

    private val _uiState = MutableStateFlow(ListenHomeUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadInitial()
    }

    private fun loadInitial() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
                val result = listenRepo.fetchRcmdPlaylist(needTopCards = true)
                _uiState.update {
                    it.copy(
                        items = result.items,
                        isRefreshing = false,
                        hasMore = result.hasMore,
                        nextPageToken = result.nextPageToken
                    )
                }
            } catch (e: Exception) {
                Logger.e(TAG, e) { "加载听视频推荐失败" }
                _uiState.update { it.copy(isRefreshing = false, errorMessage = e.message) }
            }
        }
    }

    fun refresh() {
        if (_uiState.value.isRefreshing) return
        _uiState.update { it.copy(isRefreshing = true) }
        loadInitial()
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoadingMore || state.isRefreshing || !state.hasMore) return
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoadingMore = true) }
                val result = listenRepo.fetchRcmdPlaylistNext(state.nextPageToken)
                _uiState.update {
                    it.copy(
                        items = it.items + result.items,
                        isLoadingMore = false,
                        hasMore = result.hasMore,
                        nextPageToken = result.nextPageToken
                    )
                }
            } catch (e: Exception) {
                Logger.e(TAG, e) { "加载更多失败" }
                _uiState.update { it.copy(isLoadingMore = false) }
            }
        }
    }
}

data class ListenHomeUiState(
    val items: List<ListenItem> = emptyList(),
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val nextPageToken: String = "",
    val errorMessage: String? = null
)
