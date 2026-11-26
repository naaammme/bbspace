package com.naaammme.bbspace.feature.im.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naaammme.bbspace.core.common.log.Logger
import com.naaammme.bbspace.core.im.ImRepository
import com.naaammme.bbspace.core.model.MsgFeedFilter
import com.naaammme.bbspace.core.model.MsgFeedTab
import com.naaammme.bbspace.core.model.im.MsgFeedCursor
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class MsgFeedViewModel @Inject constructor(
    private val imRepo: ImRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MsgFeedUiState())
    val uiState: StateFlow<MsgFeedUiState> = _uiState.asStateFlow()

    private var reqId = 0L
    private var loadJob: Job? = null

    fun selectTab(index: Int) {
        val (tab, filterType) = when (index) {
            1 -> MsgFeedTab.LIKES to MsgFeedFilter.ALL
            2 -> MsgFeedTab.COMMENTS to MsgFeedFilter.FOLLOWING
            else -> MsgFeedTab.COMMENTS to MsgFeedFilter.ALL
        }
        val state = _uiState.value
        if (state.tab == tab && state.filterType == filterType) return
        _uiState.update {
            it.copy(
                tab = tab,
                filterType = filterType,
                items = emptyList(),
                cursor = null,
                isLoading = false,
                isRefreshing = false,
                isLoadingMore = false,
                canLoadMore = false
            )
        }
        refresh(forceLoading = true)
    }

    fun initLoad() {
        if (_uiState.value.items.isNotEmpty()) return
        refresh(forceLoading = true)
    }

    fun refresh(forceLoading: Boolean = false) {
        val state = _uiState.value
        if (state.isLoading || state.isRefreshing || state.isLoadingMore) return
        val callId = ++reqId
        _uiState.update {
            it.copy(
                isLoading = forceLoading || it.items.isEmpty(),
                isRefreshing = !forceLoading && it.items.isNotEmpty(),
                isLoadingMore = false,
                errorMessage = null,
                loadMoreError = null
            )
        }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            load(
                reset = true,
                callId = callId,
                tab = state.tab,
                filterType = state.filterType,
                cursor = null
            )
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (!state.canLoadMore || state.isLoading || state.isRefreshing || state.isLoadingMore) return
        val callId = ++reqId
        val cursor = state.cursor
        _uiState.update {
            it.copy(
                isLoadingMore = true,
                loadMoreError = null
            )
        }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            load(
                reset = false,
                callId = callId,
                tab = state.tab,
                filterType = state.filterType,
                cursor = cursor
            )
        }
    }

    private suspend fun load(
        reset: Boolean,
        callId: Long,
        tab: MsgFeedTab,
        filterType: MsgFeedFilter,
        cursor: MsgFeedCursor?
    ) {
        try {
            val page = imRepo.fetchMsgFeedList(
                tab = tab,
                filterType = filterType,
                cursor = cursor
            )
            if (callId != reqId) return
            _uiState.update { it ->
                val newItems = if (reset) {
                    page.msgCards
                } else {
                    (it.items + page.msgCards).distinctBy { card -> card.msgId }
                }
                val newCursor = page.cursor
                it.copy(
                    items = newItems,
                    cursor = newCursor,
                    isLoading = false,
                    isRefreshing = false,
                    isLoadingMore = false,
                    canLoadMore = newCursor != null && !newCursor.isEnd,
                    errorMessage = null,
                    loadMoreError = null
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (callId != reqId) return
            val msg = e.message.takeIf { !it.isNullOrBlank() } ?: if (reset) "加载失败" else "加载更多失败"
            Logger.e("MsgFeedViewModel", e) { msg }
            _uiState.update {
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
}
