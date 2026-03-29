package com.naaammme.bbspace.feature.home.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naaammme.bbspace.core.common.log.Logger
import com.naaammme.bbspace.core.data.AppSettings
import com.naaammme.bbspace.core.domain.feed.FeedRepository
import com.naaammme.bbspace.core.model.FeedItem
import com.naaammme.bbspace.core.model.InterestChoose
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val feedRepo: FeedRepository,
    private val appSettings: AppSettings
) : ViewModel() {

    companion object {
        private const val TAG = "HomeViewModel"
    }

    private val _items = MutableStateFlow<List<FeedItem>>(emptyList())
    val items = _items.asStateFlow()

    private val _interestChoose = MutableStateFlow<InterestChoose?>(null)
    val interestChoose = _interestChoose.asStateFlow()

    var isRefreshing by mutableStateOf(false)
        private set

    var isLoadingMore by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var toastMessage by mutableStateOf("")
        private set

    private var flush = 0

    init {
        viewModelScope.launch {
            feedRepo.toastFlow.collect { toast ->
                if (toast.hasToast) toastMessage = toast.message
            }
        }
        loadInitial()
    }

    private fun loadInitial() {
        viewModelScope.launch {
            try {
                isRefreshing = true
                errorMessage = null
                flush = 0
                val feed = feedRepo.fetchFeed(idx = 0L, pull = true, flush = flush)
                _items.value = feed.items
                flush++
                if (feed.interestChoose != null) {
                    val done = appSettings.interestDone.first()
                    if (!done) _interestChoose.value = feed.interestChoose
                }
            } catch (e: Exception) {
                Logger.e(TAG, e) { "加载首页失败" }
                errorMessage = e.message
            } finally {
                isRefreshing = false
            }
        }
    }

    fun dismissInterest() {
        _interestChoose.value = null
    }

    fun submitInterest(interestId: Int, interestResult: String, interestPosIds: String) {
        viewModelScope.launch {
            try {
                appSettings.markInterestDone()
                _interestChoose.value = null
                isRefreshing = true
                errorMessage = null
                val feed = feedRepo.fetchFeedWithInterest(
                    idx = 0L, pull = true, flush = flush,
                    interestId = interestId,
                    interestResult = interestResult,
                    interestPosIds = interestPosIds
                )
                _items.value = feed.items
                flush++
            } catch (e: Exception) {
                Logger.e(TAG, e) { "提交兴趣失败" }
                errorMessage = e.message
            } finally {
                isRefreshing = false
            }
        }
    }

    fun refresh() {
        if (isRefreshing) return
        viewModelScope.launch {
            try {
                isRefreshing = true
                errorMessage = null
                val currentTopIdx = _items.value.firstOrNull()?.idx ?: 0L
                flush = 0
                val feed = feedRepo.fetchFeed(idx = currentTopIdx, pull = true, flush = flush)
                if (feed.items.isNotEmpty()) {
                    _items.value = withContext(Dispatchers.Default) {
                        feed.items + _items.value
                    }
                    flush++
                }
            } catch (e: Exception) {
                Logger.e(TAG, e) { "刷新失败" }
                errorMessage = e.message
            } finally {
                isRefreshing = false
            }
        }
    }

    fun loadMore() {
        if (isLoadingMore || isRefreshing) return
        viewModelScope.launch {
            try {
                isLoadingMore = true
                val lastIdx = _items.value.lastOrNull()?.idx ?: 0L
                val feed = feedRepo.fetchFeed(idx = lastIdx, pull = false, flush = flush)
                _items.value = _items.value + feed.items
                flush++
            } catch (e: Exception) {
                Logger.e(TAG, e) { "加载更多失败" }
                errorMessage = e.message
            } finally {
                isLoadingMore = false
            }
        }
    }
}
