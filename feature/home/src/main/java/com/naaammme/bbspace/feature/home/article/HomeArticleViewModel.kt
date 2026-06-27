package com.naaammme.bbspace.feature.home.article

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naaammme.bbspace.core.common.log.Logger
import com.naaammme.bbspace.core.article.ArticleRecommendRepository
import com.naaammme.bbspace.core.model.article.ArticleRecommendItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class HomeArticleViewModel @Inject constructor(
    private val repository: ArticleRecommendRepository
) : ViewModel() {

    companion object {
        private const val TAG = "HomeArticleViewModel"
    }

    private val _uiState = MutableStateFlow(HomeArticleUiState())
    val uiState = _uiState.asStateFlow()

    private var nextPage = 1
    private var aidsLength = 0
    private var hasMore = true
    private var hasLoaded = false

    fun ensureLoaded() {
        if (hasLoaded) return
        refresh()
    }

    fun refresh() {
        val state = _uiState.value
        if (hasLoaded && (state.isRefreshing || state.isLoadingMore)) return
        viewModelScope.launch {
            try {
                _uiState.update {
                    it.copy(
                        isRefreshing = true,
                        errorMessage = null
                    )
                }
                val page = repository.fetchHomePage(
                    page = 1,
                    aids = null
                )
                _uiState.update {
                    it.copy(items = page.items)
                }
                nextPage = 2
                aidsLength = page.aidsLength
                hasMore = page.items.isNotEmpty()
                hasLoaded = true
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Logger.e(TAG, e) { "刷新专栏推荐失败" }
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
                val page = repository.fetchHomePage(
                    page = nextPage,
                    aids = buildAids(state.items, aidsLength)
                )
                _uiState.update {
                    it.copy(items = it.items + page.items)
                }
                nextPage++
                aidsLength = page.aidsLength
                hasMore = page.items.isNotEmpty()
                hasLoaded = true
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Logger.e(TAG, e) { "加载更多专栏推荐失败" }
                _uiState.update { it.copy(errorMessage = e.message) }
            } finally {
                _uiState.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    private fun buildAids(
        items: List<ArticleRecommendItem>,
        count: Int
    ): String? {
        if (items.isEmpty() || count <= 0) return null
        return items.takeLast(count)
            .asReversed()
            .joinToString(",") { it.id.toString() }
            .takeIf { it.isNotBlank() }
    }
}
