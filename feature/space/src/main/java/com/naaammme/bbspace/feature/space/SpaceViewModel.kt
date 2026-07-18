package com.naaammme.bbspace.feature.space

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naaammme.bbspace.core.common.log.Logger
import com.naaammme.bbspace.core.dynamic.DynamicRepository
import com.naaammme.bbspace.core.model.SpaceProfile
import com.naaammme.bbspace.core.model.SpaceRoute
import com.naaammme.bbspace.core.model.SpaceRouteTool
import com.naaammme.bbspace.core.auth.AuthStore
import com.naaammme.bbspace.core.space.SpaceRepository
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
    private val repo: SpaceRepository,
    private val dynamicRepo: DynamicRepository,
    private val authStore: AuthStore
) : ViewModel() {

    private val route = savedStateHandle.toSpaceRoute()
    private val isValidRoute = route.mid > 0L || !route.name.isNullOrBlank()

    private val _uiState = MutableStateFlow(
        SpaceUiState(header = route.toInitialHeaderState())
    )
    val uiState: StateFlow<SpaceUiState> = _uiState.asStateFlow()

    init {
        if (!isValidRoute) {
            _uiState.update {
                it.copy(archive = it.archive.copy(message = "个人空间参数无效"))
            }
        } else {
            refresh()
        }
    }

    fun refresh() {
        if (!isValidRoute) return
        val state = _uiState.value
        if (
            state.archive.isRefreshing ||
            state.archive.isLoadingMore ||
            state.dynamics.isRefreshing ||
            state.dynamics.isLoadingMore
        ) {
            return
        }
        _uiState.update {
            it.copy(
                archive = it.archive.copy(
                    isRefreshing = true,
                    message = null,
                    loadMoreError = null
                ),
                dynamics = it.dynamics.copy(
                    isRefreshing = true,
                    isLoadingMore = false,
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
                val headerState = SpaceHeaderUiState(
                    profile = home.profile,
                    bannerUrl = home.bannerUrl,
                    isLogin = authStore.mid > 0L,
                    isSelf = authStore.mid > 0L && authStore.mid == home.profile.mid
                )
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
                                message = null,
                                loadMoreError = null
                            )
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
                                message = null,
                                loadMoreError = null
                            )
                        )
                    }
                }
                loadDynamics()
            } catch (e: Exception) {
                Logger.e(TAG, e) { "加载个人空间失败" }
                val message = e.message ?: "加载个人空间失败"
                _uiState.update {
                    it.copy(
                        archive = it.archive.copy(
                            isRefreshing = false,
                            message = message
                        ),
                        dynamics = it.dynamics.copy(
                            isRefreshing = false,
                            isLoadingMore = false,
                            message = message
                        )
                    )
                }
            }
        }
    }

    fun selectOrder(order: String) {
        val state = _uiState.value
        if (route.mid <= 0L || order == state.archive.selectedOrder) return
        if (state.archive.isRefreshing || state.archive.isLoadingMore) return
        val prevArchive = state.archive
        _uiState.update {
            it.copy(
                archive = prevArchive.copy(
                    selectedOrder = order,
                    isRefreshing = true,
                    message = null,
                    loadMoreError = null
                ),
                selectedSection = SpaceSection.VIDEO
            )
        }
        viewModelScope.launch {
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
                            isRefreshing = false,
                            message = e.message ?: "切换排序失败"
                        )
                    )
                }
            }
        }
    }

    fun selectSection(section: SpaceSection) {
        _uiState.update { it.copy(selectedSection = section) }
    }

    fun loadMore() {
        when (_uiState.value.selectedSection) {
            SpaceSection.VIDEO -> loadMoreVideos()
            SpaceSection.DYNAMIC -> loadMoreDynamics()
        }
    }

    fun toggleFollow() {
        val profile = _uiState.value.header?.profile ?: return
        if (profile.mid <= 0L) return
        val isFollow = profile.relation != 1

        viewModelScope.launch {
            try {
                repo.modifyRelation(profile.mid, isFollow)
                updateProfileRelationState(if (isFollow) 1 else -999)
            } catch (e: Exception) {
                Logger.e(TAG, e) { "修改关注状态失败" }
                // TODO: 可以在这里增加发送 Toast 或 Snackbar 的 SideEffect
            }
        }
    }

    fun toggleBlock() {
        val profile = _uiState.value.header?.profile ?: return
        if (profile.mid <= 0L) return
        val isBlack = profile.relation != -1

        viewModelScope.launch {
            try {
                repo.modifyBlacklist(profile.mid, isBlack)
                updateProfileRelationState(if (isBlack) -1 else -999)
            } catch (e: Exception) {
                Logger.e(TAG, e) { "修改拉黑状态失败" }
                // TODO: 可以在这里增加发送 Toast 或 Snackbar 的 SideEffect
            }
        }
    }

    private fun updateProfileRelationState(newRelation: Int) {
        _uiState.update { current ->
            val curProfile = current.header?.profile ?: return@update current
            current.copy(
                header = current.header.copy(
                    profile = curProfile.copy(relation = newRelation)
                )
            )
        }
    }

    private fun loadMoreVideos() {
        val archive = _uiState.value.archive
        if (route.mid <= 0L || !archive.canLoadMore) return
        val cursorAid = archive.videos.lastOrNull()?.aid ?: return
        _uiState.update { it.copy(archive = it.archive.copy(isLoadingMore = true, loadMoreError = null)) }
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

    private fun loadMoreDynamics() {
        val dynamics = _uiState.value.dynamics
        if (route.mid <= 0L || !dynamics.canLoadMore) return
        _uiState.update {
            it.copy(
                dynamics = it.dynamics.copy(
                    isLoadingMore = true,
                    loadMoreError = null
                )
            )
        }
        viewModelScope.launch {
            try {
                val page = dynamicRepo.fetchSpace(
                    hostUid = route.mid,
                    page = dynamics.page,
                    historyOffset = dynamics.historyOffset,
                    from = "space"
                )
                _uiState.update {
                    it.copy(
                        dynamics = it.dynamics.copy(
                            items = mergeDynamics(it.dynamics.items, page.items),
                            historyOffset = page.cursor.historyOffset,
                            page = page.cursor.page,
                            hasMore = page.hasMore,
                            isRefreshing = false,
                            isLoadingMore = false,
                            message = null,
                            loadMoreError = null
                        )
                    )
                }
            } catch (e: Exception) {
                Logger.e(TAG, e) { "加载空间动态更多失败" }
                _uiState.update {
                    it.copy(
                        dynamics = it.dynamics.copy(
                            isLoadingMore = false,
                            loadMoreError = e.message ?: "加载更多失败"
                        )
                    )
                }
            }
        }
    }

    private fun loadDynamics() {
        val current = _uiState.value.dynamics
        if (route.mid <= 0L) return
        viewModelScope.launch {
            try {
                val page = dynamicRepo.fetchSpace(
                    hostUid = route.mid,
                    page = 1,
                    historyOffset = "",
                    from = "space"
                )
                _uiState.update {
                    it.copy(
                        dynamics = it.dynamics.copy(
                            items = page.items,
                            historyOffset = page.cursor.historyOffset,
                            page = page.cursor.page,
                            hasMore = page.hasMore,
                            isRefreshing = false,
                            isLoadingMore = false,
                            message = null,
                            loadMoreError = null
                        )
                    )
                }
            } catch (e: Exception) {
                Logger.e(TAG, e) { "加载空间动态失败" }
                _uiState.update {
                    it.copy(
                        dynamics = current.copy(
                            isRefreshing = false,
                            isLoadingMore = false,
                            message = e.message ?: "加载动态失败"
                        )
                    )
                }
            }
        }
    }

    private fun mergeVideos(
        current: List<com.naaammme.bbspace.core.model.SpaceVideo>,
        next: List<com.naaammme.bbspace.core.model.SpaceVideo>
    ): List<com.naaammme.bbspace.core.model.SpaceVideo> {
        if (next.isEmpty()) return current
        val merged = current.toMutableList()
        val seen = current.asSequence().map { it.aid to it.cid }.toMutableSet()
        next.forEach { video ->
            if (seen.add(video.aid to video.cid)) {
                merged += video
            }
        }
        return merged
    }

    private fun mergeDynamics(
        current: List<com.naaammme.bbspace.core.model.DynamicItem>,
        next: List<com.naaammme.bbspace.core.model.DynamicItem>
    ): List<com.naaammme.bbspace.core.model.DynamicItem> {
        if (next.isEmpty()) return current
        val merged = current.toMutableList()
        val seen = current.mapTo(HashSet(current.size + next.size)) { it.id }
        next.forEach { item ->
            if (seen.add(item.id)) {
                merged += item
            }
        }
        return merged
    }

    private fun SpaceRoute.toInitialHeaderState(): SpaceHeaderUiState? {
        if (mid <= 0L && name.isNullOrBlank()) return null
        return SpaceHeaderUiState(
            profile = SpaceProfile(
                mid = mid,
                name = name?.takeIf(String::isNotBlank) ?: "个人空间",
                face = null,
                sign = "",
                level = 0,
                vipLabel = null,
                fansCount = 0L,
                followingCount = 0L,
                likeCount = 0L,
                videoCount = 0,
                articleCount = 0,
                seasonCount = 0,
                seriesCount = 0,
                tags = emptyList()
            ),
            bannerUrl = null,
            isLogin = authStore.mid > 0L,
            isSelf = authStore.mid > 0L && authStore.mid == mid
        )
    }

    private fun SavedStateHandle.toSpaceRoute(): SpaceRoute {
        return SpaceRoute(
            mid = get<Long>(SPACE_MID_ARG) ?: 0L,
            name = get<String>(SPACE_NAME_ARG)?.takeIf(String::isNotBlank),
            from = get<Int>(SPACE_FROM_ARG) ?: SpaceRouteTool.FROM_DEFAULT,
            fromViewAid = get<Long>(SPACE_FROM_VIEW_AID_ARG)?.takeIf { it > 0L }
        )
    }

    private companion object {
        const val TAG = "SpaceViewModel"
    }
}
