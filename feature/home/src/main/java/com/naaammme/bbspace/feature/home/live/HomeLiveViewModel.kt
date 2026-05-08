package com.naaammme.bbspace.feature.home.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naaammme.bbspace.core.common.log.Logger
import com.naaammme.bbspace.core.domain.live.LiveRecommendRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class HomeLiveViewModel @Inject constructor(
    private val repository: LiveRecommendRepository
) : ViewModel() {

    companion object {
        private const val TAG = "HomeLiveViewModel"
    }

    private val _uiState = MutableStateFlow(HomeLiveUiState())
    val uiState = _uiState.asStateFlow()

    private var nextPage = 1
    private var relationPage = 1
    private var loginEvent = 1
    private var hasMore = true
    private var hasLoaded = false

    fun ensureLoaded() {
        if (hasLoaded) return
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                _uiState.update {
                    it.copy(
                        isRefreshing = true,
                        errorMessage = null
                    )
                }
                val page = repository.fetchRecommendPage(
                    page = 1,
                    relationPage = relationPage,
                    isRefresh = hasLoaded,
                    loginEvent = loginEvent
                )
                _uiState.update {
                    it.copy(
                        upList = page.upList,
                        items = page.items
                    )
                }
                nextPage = 2
                hasMore = page.hasMore
                hasLoaded = true
                loginEvent = 0
            } catch (e: Exception) {
                Logger.e(TAG, e) { "刷新直播推荐失败" }
                _uiState.update { it.copy(errorMessage = e.message) }
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isRefreshing || state.isLoadingMore || !hasMore) return
        viewModelScope.launch {
            try {
                _uiState.update {
                    it.copy(
                        isLoadingMore = true,
                        errorMessage = null
                    )
                }
                val page = repository.fetchRecommendPage(
                    page = nextPage,
                    relationPage = relationPage,
                    isRefresh = false,
                    loginEvent = loginEvent
                )
                _uiState.update {
                    it.copy(
                        upList = it.upList ?: page.upList,
                        items = it.items + page.items
                    )
                }
                nextPage++
                hasMore = page.hasMore
                hasLoaded = true
                loginEvent = 0
            } catch (e: Exception) {
                Logger.e(TAG, e) { "加载更多直播推荐失败" }
                _uiState.update { it.copy(errorMessage = e.message) }
            } finally {
                _uiState.update { it.copy(isLoadingMore = false) }
            }
        }
    }
}
