package com.naaammme.bbspace.feature.space.model

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naaammme.bbspace.core.common.log.Logger
import com.naaammme.bbspace.core.domain.space.SpaceRepository
import com.naaammme.bbspace.core.model.SpaceHome
import com.naaammme.bbspace.core.model.SpaceRoute
import com.naaammme.bbspace.core.model.SpaceVideo
import com.naaammme.bbspace.feature.space.navigation.SPACE_FROM_ARG
import com.naaammme.bbspace.feature.space.navigation.SPACE_FROM_VIEW_AID_ARG
import com.naaammme.bbspace.feature.space.navigation.SPACE_MID_ARG
import com.naaammme.bbspace.feature.space.navigation.SPACE_NAME_ARG
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SpaceViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: SpaceRepository
) : ViewModel() {

    private val route = savedStateHandle.toSpaceRoute()

    private val _uiState = MutableStateFlow(SpaceUiState())
    val uiState: StateFlow<SpaceUiState> = _uiState.asStateFlow()

    init {
        if (route.mid <= 0L && route.name.isNullOrBlank()) {
            _uiState.update { it.copy(pageErrorMessage = "个人空间参数无效") }
        } else {
            refresh()
        }
    }

    fun refresh() {
        if (route.mid <= 0L && route.name.isNullOrBlank()) return
        val state = _uiState.value
        if (state.isPageLoading || state.archive.isRefreshing || state.archive.isLoadingMore) return
        val hasHeader = state.header != null
        _uiState.update {
            it.copy(
                isPageLoading = !hasHeader,
                pageErrorMessage = null,
                archive = it.archive.copy(
                    isRefreshing = hasHeader,
                    message = null,
                    loadMoreError = null
                )
            )
        }
        viewModelScope.launch {
            try {
                val home = repo.fetchHome(route)
                val preferredOrder = state.archive.selectedOrder
                    .takeIf { selected -> home.orders.any { it.value == selected } }
                    ?: home.defaultOrder
                val homeOrderState = resolveSpaceOrderState(home.orders, preferredOrder)
                val headerState = home.toHeaderState()
                if (route.mid > 0L && homeOrderState.selectedOrder != home.defaultOrder) {
                    val page = repo.fetchArchive(
                        mid = route.mid,
                        order = homeOrderState.selectedOrder,
                        fromViewAid = route.fromViewAid
                    )
                    val archiveOrderState = resolveSpaceOrderState(
                        nextOrders = page.orders,
                        preferred = homeOrderState.selectedOrder
                    )
                    _uiState.update {
                        it.copy(
                            header = headerState,
                            archive = it.archive.copy(
                                videos = page.videos,
                                orders = archiveOrderState.orders,
                                selectedOrder = archiveOrderState.selectedOrder,
                                hasMore = page.hasMore,
                                isRefreshing = false,
                                isLoadingMore = false,
                                message = null,
                                loadMoreError = null
                            ),
                            isPageLoading = false,
                            pageErrorMessage = null
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            header = headerState,
                            archive = it.archive.copy(
                                videos = home.videos,
                                orders = homeOrderState.orders,
                                selectedOrder = homeOrderState.selectedOrder,
                                hasMore = route.mid > 0L && home.hasMore,
                                isRefreshing = false,
                                isLoadingMore = false,
                                message = null,
                                loadMoreError = null
                            ),
                            isPageLoading = false,
                            pageErrorMessage = null
                        )
                    }
                }
            } catch (e: Exception) {
                Logger.e(TAG, e) { "加载个人空间失败" }
                val message = e.message ?: "加载个人空间失败"
                _uiState.update {
                    if (hasHeader) {
                        it.copy(
                            isPageLoading = false,
                            pageErrorMessage = null,
                            archive = it.archive.copy(
                                isRefreshing = false,
                                isLoadingMore = false,
                                message = message
                            )
                        )
                    } else {
                        it.copy(
                            isPageLoading = false,
                            pageErrorMessage = message,
                            archive = it.archive.copy(
                                isRefreshing = false,
                                isLoadingMore = false
                            )
                        )
                    }
                }
            }
        }
    }

    fun retry() {
        refresh()
    }

    fun selectOrder(order: String) {
        val state = _uiState.value
        if (route.mid <= 0L || order == state.archive.selectedOrder) return
        if (state.isPageLoading || state.archive.isRefreshing || state.archive.isLoadingMore) return
        viewModelScope.launch {
            reloadArchive(order)
        }
    }

    fun loadMore() {
        val archive = _uiState.value.archive
        if (route.mid <= 0L || !archive.canLoadMore) return
        val cursorAid = archive.videos.lastOrNull()?.aid ?: return
        _uiState.update {
            it.copy(
                archive = it.archive.copy(
                    isLoadingMore = true,
                    loadMoreError = null
                )
            )
        }
        viewModelScope.launch {
            try {
                val page = repo.fetchArchive(
                    mid = route.mid,
                    order = archive.selectedOrder,
                    cursorAid = cursorAid,
                    fromViewAid = route.fromViewAid
                )
                val orderState = resolveSpaceOrderState(page.orders, archive.selectedOrder)
                _uiState.update {
                    it.copy(
                        archive = it.archive.copy(
                            videos = mergeVideos(it.archive.videos, page.videos),
                            orders = orderState.orders,
                            selectedOrder = orderState.selectedOrder,
                            hasMore = page.hasMore,
                            isLoadingMore = false,
                            loadMoreError = null
                        )
                    )
                }
            } catch (e: Exception) {
                Logger.e(TAG, e) { "加载空间视频更多失败" }
                _uiState.update {
                    it.copy(
                        archive = it.archive.copy(
                            isLoadingMore = false,
                            loadMoreError = e.message ?: "加载更多失败"
                        )
                    )
                }
            }
        }
    }

    private suspend fun reloadArchive(order: String) {
        val prevArchive = _uiState.value.archive
        _uiState.update {
            it.copy(
                archive = it.archive.copy(
                    videos = emptyList(),
                    selectedOrder = order,
                    hasMore = false,
                    isRefreshing = true,
                    message = null,
                    loadMoreError = null
                )
            )
        }
        try {
            val page = repo.fetchArchive(
                mid = route.mid,
                order = order,
                fromViewAid = route.fromViewAid
            )
            val orderState = resolveSpaceOrderState(page.orders, order)
            _uiState.update {
                it.copy(
                    archive = it.archive.copy(
                        videos = page.videos,
                        orders = orderState.orders,
                        selectedOrder = orderState.selectedOrder,
                        hasMore = page.hasMore,
                        isRefreshing = false,
                        message = null,
                        loadMoreError = null
                    )
                )
            }
        } catch (e: Exception) {
            Logger.e(TAG, e) { "切换空间排序失败" }
            _uiState.update {
                it.copy(
                    archive = prevArchive.copy(
                        message = e.message ?: "切换排序失败",
                        loadMoreError = null
                    )
                )
            }
        }
    }

    private fun SpaceHome.toHeaderState(): SpaceHeaderUiState {
        return SpaceHeaderUiState(
            profile = profile,
            bannerUrl = bannerUrl
        )
    }

    private fun mergeVideos(
        current: List<SpaceVideo>,
        next: List<SpaceVideo>
    ): List<SpaceVideo> {
        if (next.isEmpty()) return current
        val merged = current.toMutableList()
        val seen = current.asSequence()
            .map { it.aid to it.cid }
            .toMutableSet()
        next.forEach { video ->
            if (seen.add(video.aid to video.cid)) {
                merged += video
            }
        }
        return merged
    }

    private companion object {
        const val TAG = "SpaceViewModel"
    }
}

private fun SavedStateHandle.toSpaceRoute(): SpaceRoute {
    val mid = get<Long>(SPACE_MID_ARG) ?: 0L
    val name = get<String>(SPACE_NAME_ARG)?.takeIf(String::isNotBlank)
    val fromViewAid = get<Long>(SPACE_FROM_VIEW_AID_ARG)?.takeIf { it > 0L }
    return SpaceRoute(
        mid = mid,
        name = name,
        from = get<Int>(SPACE_FROM_ARG) ?: 0,
        fromViewAid = fromViewAid
    )
}
