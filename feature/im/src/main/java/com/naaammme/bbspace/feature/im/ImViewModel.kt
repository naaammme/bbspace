package com.naaammme.bbspace.feature.im

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naaammme.bbspace.core.common.log.Logger
import com.naaammme.bbspace.core.auth.AuthRepository
import com.naaammme.bbspace.core.im.ImRepository
import com.naaammme.bbspace.core.model.ImPaginationParams
import com.naaammme.bbspace.core.model.ImSessionItem
import com.naaammme.bbspace.core.model.ImSessionTab
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ImViewModel @Inject constructor(
    private val authRepo: AuthRepository,
    private val imRepo: ImRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImUiState())
    val uiState: StateFlow<ImUiState> = _uiState.asStateFlow()
    private val cache = mutableMapOf<ImSessionTab, ImTabCache>()
    private var reqId = 0L

    init {
        viewModelScope.launch {
            authRepo.currentMidFlow.collect { mid ->
                reqId += 1L
                if (mid > 0) {
                    _uiState.update { state ->
                        state.copy(
                            isLoggedIn = true,
                            errorMessage = null,
                            loadMoreError = null
                        )
                    }
                    val state = _uiState.value
                    if (state.sessions.isNotEmpty() || cache.isNotEmpty()) return@collect
                    refresh(forceLoading = true)
                } else {
                    cache.clear()
                    _uiState.value = ImUiState(isLoggedIn = false)
                }
            }
        }
    }

    fun selectTab(tab: ImSessionTab) {
        val state = _uiState.value
        if (state.currentTab == tab || state.isLoading || state.isRefreshing || state.isLoadingMore) return
        cache[tab]?.let { cached ->
            _uiState.value = state.copy(
                tabs = ImSessionTab.entries,
                currentTab = tab,
                sessions = cached.sessions,
                paginationParams = cached.paginationParams,
                isLoading = false,
                isRefreshing = false,
                isLoadingMore = false,
                errorMessage = null,
                loadMoreError = null
            )
            return
        }
        reqId += 1L
        _uiState.update {
            it.copy(
                currentTab = tab,
                sessions = emptyList(),
                isLoading = true,
                isRefreshing = false,
                isLoadingMore = false,
                paginationParams = null,
                errorMessage = null,
                loadMoreError = null
            )
        }
        viewModelScope.launch {
            load(tab, null, true)
        }
    }

    fun refresh(forceLoading: Boolean = false) {
        val state = _uiState.value
        if (!state.isLoggedIn || state.isLoading || state.isRefreshing || state.isLoadingMore) return
        reqId += 1L
        _uiState.update {
            it.copy(
                isLoading = forceLoading || it.sessions.isEmpty(),
                isRefreshing = !forceLoading && it.sessions.isNotEmpty(),
                isLoadingMore = false,
                errorMessage = null,
                loadMoreError = null
            )
        }
        viewModelScope.launch {
            load(state.currentTab, null, true)
        }
    }

    fun loadMore() {
        val state = _uiState.value
        val nextParams = state.paginationParams ?: return
        if (!state.canLoadMore) return
        reqId += 1L
        _uiState.update {
            it.copy(
                isLoadingMore = true,
                loadMoreError = null
            )
        }
        viewModelScope.launch {
            load(state.currentTab, nextParams, false)
        }
    }

    private suspend fun load(
        tab: ImSessionTab,
        paginationParams: ImPaginationParams?,
        reset: Boolean
    ) {
        val callId = reqId
        try {
            val page = imRepo.fetchSessions(
                tab = tab,
                paginationParams = paginationParams
            )
            if (callId != reqId) return
            _uiState.update { state ->
                if (state.currentTab != tab) return@update state
                val sessions = mergeSessions(state.sessions, page.sessions, reset)
                cache[tab] = ImTabCache(
                    sessions = sessions,
                    paginationParams = page.paginationParams
                )
                state.copy(
                    tabs = page.tabs,
                    currentTab = page.currentTab,
                    sessions = sessions,
                    paginationParams = page.paginationParams,
                    isLoading = false,
                    isRefreshing = false,
                    isLoadingMore = false,
                    errorMessage = null,
                    loadMoreError = null,
                    isLoggedIn = true
                )
            }
        } catch (e: Exception) {
            if (callId != reqId) return
            val msg = e.userMessage(
                default = if (reset) LOAD_ERR else LOAD_MORE_ERR
            )
            Logger.e(TAG, e) { msg }
            _uiState.update { state ->
                if (state.currentTab != tab) return@update state
                if (reset) {
                    state.copy(
                        isLoading = false,
                        isRefreshing = false,
                        isLoadingMore = false,
                        errorMessage = msg
                    )
                } else {
                    state.copy(
                        isLoading = false,
                        isRefreshing = false,
                        isLoadingMore = false,
                        loadMoreError = msg
                    )
                }
            }
        }
    }

    private fun Throwable.userMessage(
        default: String
    ): String {
        return message?.takeIf(String::isNotBlank) ?: default
    }

    private fun mergeSessions(
        current: List<ImSessionItem>,
        incoming: List<ImSessionItem>,
        reset: Boolean
    ): List<ImSessionItem> {
        if (reset) return incoming
        if (current.isEmpty()) return incoming
        if (incoming.isEmpty()) return current
        val merged = LinkedHashMap<String, ImSessionItem>(current.size + incoming.size)
        current.forEach { merged[it.key] = it }
        incoming.forEach { merged[it.key] = it }
        return merged.values.toList()
    }

    private data class ImTabCache(
        val sessions: List<ImSessionItem>,
        val paginationParams: ImPaginationParams?
    )

    private companion object {
        const val TAG = "ImViewModel"
        const val LOAD_ERR = "加载消息列表失败"
        const val LOAD_MORE_ERR = "加载更多消息失败"
    }
}
