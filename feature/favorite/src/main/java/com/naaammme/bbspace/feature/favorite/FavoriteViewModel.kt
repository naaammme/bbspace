package com.naaammme.bbspace.feature.favorite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naaammme.bbspace.core.common.log.Logger
import com.naaammme.bbspace.core.domain.favorite.FavoriteRepository
import com.naaammme.bbspace.core.model.FavoriteContentCursor
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@HiltViewModel
class FavoriteViewModel @Inject constructor(
    private val repo: FavoriteRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        FavoriteUiState(
            content = FavoriteContentUiState(isLoading = true)
        )
    )
    val uiState: StateFlow<FavoriteUiState> = _uiState.asStateFlow()

    private var contentJob: Job? = null
    private var folderJob: Job? = null

    init {
        loadContent(reset = true, refreshing = false)
    }

    fun selectTab(tab: FavoriteTab) {
        val state = _uiState.value
        if (state.tab == tab) return
        val shouldLoad = when (tab) {
            FavoriteTab.CONTENT -> state.content.items.isEmpty()
            FavoriteTab.FOLDER -> state.folder.folders.isEmpty()
        }
        _uiState.update { it.copy(tab = tab) }
        if (!shouldLoad) return
        when (tab) {
            FavoriteTab.CONTENT -> loadContent(reset = true, refreshing = false)
            FavoriteTab.FOLDER -> loadFolders(refreshing = false)
        }
    }

    fun refresh() {
        when (_uiState.value.tab) {
            FavoriteTab.CONTENT -> {
                contentJob?.cancel()
                loadContent(reset = true, refreshing = true)
            }

            FavoriteTab.FOLDER -> {
                folderJob?.cancel()
                loadFolders(refreshing = true)
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value.content
        if (!state.canLoadMore) return
        loadContent(reset = false, refreshing = false)
    }

    private fun loadFolders(refreshing: Boolean) {
        if (folderJob?.isActive == true) return
        folderJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    folder = it.folder.copy(
                        isLoading = !refreshing && it.folder.folders.isEmpty(),
                        isRefreshing = refreshing,
                        errorMessage = null
                    )
                )
            }
            runCatching {
                repo.fetchMyFavorites()
            }.onSuccess { page ->
                _uiState.update {
                    it.copy(
                        folder = it.folder.copy(
                            folders = page.folders,
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = null
                        )
                    )
                }
            }.onFailure { e ->
                if (!isActive) return@launch
                Logger.e(TAG, e) { "加载收藏夹失败" }
                _uiState.update {
                    it.copy(
                        folder = it.folder.copy(
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = e.message ?: "加载收藏失败"
                        )
                    )
                }
            }
        }
    }

    private fun loadContent(
        reset: Boolean,
        refreshing: Boolean
    ) {
        if (contentJob?.isActive == true) return
        val reqCursor = if (reset) FavoriteContentCursor() else _uiState.value.content.cursor
        contentJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    content = it.content.copy(
                        isLoading = !refreshing && reset && it.content.items.isEmpty(),
                        isRefreshing = refreshing,
                        isLoadingMore = !reset,
                        errorMessage = null,
                        errorOnLoadMore = false
                    )
                )
            }
            runCatching {
                repo.fetchFavoriteContents(reqCursor)
            }.onSuccess { page ->
                _uiState.update {
                    it.copy(
                        content = it.content.copy(
                            items = if (reset) page.items else it.content.items + page.items,
                            cursor = page.cursor,
                            hasMore = page.hasMore,
                            isLoading = false,
                            isRefreshing = false,
                            isLoadingMore = false,
                            errorMessage = null,
                            errorOnLoadMore = false
                        )
                    )
                }
            }.onFailure { e ->
                if (!isActive) return@launch
                Logger.e(TAG, e) { "加载全部收藏失败" }
                _uiState.update {
                    it.copy(
                        content = it.content.copy(
                            isLoading = false,
                            isRefreshing = false,
                            isLoadingMore = false,
                            errorMessage = e.message ?: "加载收藏失败",
                            errorOnLoadMore = !reset
                        )
                    )
                }
            }
        }
    }

    private companion object {
        const val TAG = "FavoriteViewModel"
    }
}
