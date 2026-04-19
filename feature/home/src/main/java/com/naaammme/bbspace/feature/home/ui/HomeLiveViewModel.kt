package com.naaammme.bbspace.feature.home.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naaammme.bbspace.core.common.log.Logger
import com.naaammme.bbspace.core.domain.live.LiveRecommendRepository
import com.naaammme.bbspace.core.model.LiveRecommendItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class HomeLiveViewModel @Inject constructor(
    private val repository: LiveRecommendRepository
) : ViewModel() {

    companion object {
        private const val TAG = "HomeLiveViewModel"
    }

    private val _items = MutableStateFlow<List<LiveRecommendItem>>(emptyList())
    val items = _items.asStateFlow()

    var isRefreshing by mutableStateOf(false)
        private set

    var isLoadingMore by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    private var nextPage = 1
    private var relationPage = 1
    private var loginEvent = 1
    private var hasMore = true
    private var hasLoaded = false

    fun ensureLoaded() {
        if (hasLoaded || isRefreshing) return
        refresh()
    }

    fun refresh() {
        if (isRefreshing) return
        viewModelScope.launch {
            try {
                isRefreshing = true
                errorMessage = null
                val page = repository.fetchRecommendPage(
                    page = 1,
                    relationPage = relationPage,
                    isRefresh = hasLoaded,
                    loginEvent = loginEvent
                )
                _items.value = page.items
                nextPage = 2
                hasMore = page.hasMore
                hasLoaded = true
                loginEvent = 0
            } catch (e: Exception) {
                Logger.e(TAG, e) { "刷新直播推荐失败" }
                errorMessage = e.message
            } finally {
                isRefreshing = false
            }
        }
    }

    fun loadMore() {
        if (isRefreshing || isLoadingMore || !hasMore) return
        viewModelScope.launch {
            try {
                isLoadingMore = true
                errorMessage = null
                val page = repository.fetchRecommendPage(
                    page = nextPage,
                    relationPage = relationPage,
                    isRefresh = false,
                    loginEvent = loginEvent
                )
                _items.value = _items.value + page.items
                nextPage++
                hasMore = page.hasMore
                hasLoaded = true
                loginEvent = 0
            } catch (e: Exception) {
                Logger.e(TAG, e) { "加载更多直播推荐失败" }
                errorMessage = e.message
            } finally {
                isLoadingMore = false
            }
        }
    }
}
