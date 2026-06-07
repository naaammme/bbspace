package com.naaammme.bbspace.feature.favorite.folderdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naaammme.bbspace.core.common.log.Logger
import com.naaammme.bbspace.core.domain.favorite.FavoriteRepository
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
class FavoriteFolderDetailViewModel @Inject constructor(
    private val repo: FavoriteRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val fid = savedStateHandle.get<Long>(FID_ARG) ?: 0L

    private val _uiState = MutableStateFlow(FavoriteFolderDetailUiState(isLoading = true))
    val uiState: StateFlow<FavoriteFolderDetailUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        if (fid > 0L) {
            loadPage(reset = true, refreshing = false)
        } else {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = "收藏夹不存在"
                )
            }
        }
    }

    fun refresh() {
        loadJob?.cancel()
        loadPage(reset = true, refreshing = true)
    }

    fun loadMore() {
        if (!_uiState.value.canLoadMore) return
        loadPage(reset = false, refreshing = false)
    }

    private fun loadPage(
        reset: Boolean,
        refreshing: Boolean
    ) {
        if (fid <= 0L || loadJob?.isActive == true) return
        val reqPage = if (reset) 1 else _uiState.value.page + 1
        loadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = !refreshing && reset && it.items.isEmpty(),
                    isRefreshing = refreshing,
                    isLoadingMore = !reset,
                    errorMessage = null,
                    errorOnLoadMore = false
                )
            }
            runCatching {
                repo.fetchFavoriteFolderContents(fid = fid, page = reqPage)
            }.onSuccess { page ->
                _uiState.update {
                    it.copy(
                        items = if (reset) page.items else it.items + page.items,
                        page = reqPage,
                        totalCount = page.totalCount,
                        hasInvalid = page.hasInvalid,
                        hasMore = page.hasMore,
                        isLoading = false,
                        isRefreshing = false,
                        isLoadingMore = false,
                        errorMessage = null,
                        errorOnLoadMore = false
                    )
                }
            }.onFailure { e ->
                if (!isActive) return@launch
                Logger.e(TAG, e) { "加载收藏夹内容失败 fid=$fid page=$reqPage" }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        isLoadingMore = false,
                        errorMessage = e.message ?: "加载收藏夹失败",
                        errorOnLoadMore = !reset
                    )
                }
            }
        }
    }

    private companion object {
        const val TAG = "FavoriteFolderDetailVm"
        const val FID_ARG = "fid"
    }
}
