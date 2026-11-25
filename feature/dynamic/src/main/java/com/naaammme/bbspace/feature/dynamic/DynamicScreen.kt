package com.naaammme.bbspace.feature.dynamic

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naaammme.bbspace.core.designsystem.component.BiliPullToRefreshBox
import com.naaammme.bbspace.core.designsystem.component.FilledTabRow
import com.naaammme.bbspace.core.designsystem.component.StateMessageCard
import com.naaammme.bbspace.core.model.DynamicBody
import com.naaammme.bbspace.core.model.DynamicItem
import com.naaammme.bbspace.core.model.DynamicUpList
import com.naaammme.bbspace.core.model.LiveRoute
import com.naaammme.bbspace.core.model.SpaceRoute
import com.naaammme.bbspace.core.model.VideoTarget
import com.naaammme.bbspace.feature.dynamic.feed.DynamicFeed
import kotlinx.coroutines.launch

private val dynamicTabs = listOf("全部", "视频", "专栏")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DynamicScreen(
    onOpenVideo: (VideoTarget) -> Unit,
    onOpenSpace: (SpaceRoute) -> Unit,
    onOpenLive: (LiveRoute) -> Unit,
    onOpenDynamic: (String) -> Unit,
    showUpList: Boolean = true,
    viewModel: DynamicViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState { dynamicTabs.size }
    val allListState = rememberLazyListState()
    val videoListState = rememberLazyListState()
    val articleListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // 视频页签：只保留投稿视频动态（转发、给评论回复等不算）
    val videoItems = remember(state.items) {
        state.items.filter { it.body is DynamicBody.Archive }
    }
    // 专栏页签：只保留专栏/文章动态
    val articleItems = remember(state.items) {
        state.items.filter { it.body is DynamicBody.Article }
    }

    val activePage = pagerState.currentPage
    val activeListState = when (activePage) {
        1 -> videoListState
        2 -> articleListState
        else -> allListState
    }
    val activeItems = when (activePage) {
        1 -> videoItems
        2 -> articleItems
        else -> state.items
    }

    // 触底或筛选页签为空（还有更多可加载）时加载更多；
    // loadMore 内部有 canLoadMore/isLoadingMore 保护，请求串行且会随 hasMore=false 终止。
    // 筛选页签连续多次加载都没有新增内容时停止自动加载（避免连发大量请求），可手动续载。
    var staleAutoLoads by remember(activePage) { mutableStateOf(0) }
    var lastAutoLoadCount by remember(activePage) { mutableStateOf(-1) }
    val autoLoadBlocked = activePage != 0 && staleAutoLoads >= MAX_STALE_AUTO_LOADS
    val findingMore = activePage != 0 && activeItems.isEmpty() &&
        state.loadMoreError.isNullOrBlank() &&
        (state.isLoadingMore || (state.hasMore && !autoLoadBlocked))
    val shouldLoadMore by remember(
        state.canLoadMore,
        state.isLoadingMore,
        state.loadMoreError,
        activeItems,
        activePage,
        activeListState
    ) {
        derivedStateOf {
            val total = activeListState.layoutInfo.totalItemsCount
            val last = activeListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val nearEnd = total > 0 && last >= total - LOAD_MORE_TRIGGER_OFFSET
            val emptyTab = activePage != 0 && total == 0
            val capped = activePage != 0 && staleAutoLoads >= MAX_STALE_AUTO_LOADS
            state.canLoadMore &&
                state.loadMoreError.isNullOrBlank() &&
                !capped &&
                (nearEnd || emptyTab)
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            val size = activeItems.size
            staleAutoLoads = if (size == lastAutoLoadCount) staleAutoLoads + 1 else 0
            lastAutoLoadCount = size
            viewModel.loadMore()
        }
    }
    val manualLoadMore = activePage != 0 && autoLoadBlocked && state.hasMore
    val onManualLoadMore: () -> Unit = {
        staleAutoLoads = 0
        lastAutoLoadCount = -1
        viewModel.loadMore()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("动态") }
                )
                FilledTabRow(
                    tabs = dynamicTabs,
                    selectedIndex = activePage,
                    onSelect = { page ->
                        scope.launch { pagerState.animateScrollToPage(page) }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }
    ) { padding ->
        BiliPullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                !state.isLoggedIn -> {
                    StateMessageCard(
                        text = "请先登录后查看动态",
                        modifier = Modifier.fillMaxSize().padding(24.dp)
                    )
                }

                state.isLoading && state.items.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                state.errorMessage != null && state.items.isEmpty() -> {
                    StateMessageCard(
                        text = state.errorMessage.orEmpty().ifBlank { "加载动态失败" },
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        isError = true
                    )
                }

                else -> {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        DynamicTabPage(
                            page = page,
                            allItems = state.items,
                            videoItems = videoItems,
                            articleItems = articleItems,
                            allListState = allListState,
                            videoListState = videoListState,
                            articleListState = articleListState,
                            showUpList = showUpList,
                            upList = state.upList,
                            isLoadingMore = state.isLoadingMore,
                            findingMore = findingMore && page == activePage,
                            manualLoadMore = manualLoadMore && page == activePage,
                            errorMessage = state.errorMessage,
                            loadMoreError = state.loadMoreError,
                            onRetryRefresh = viewModel::refresh,
                            onRetryLoadMore = viewModel::loadMore,
                            onManualLoadMore = onManualLoadMore,
                            onOpenVideo = onOpenVideo,
                            onOpenSpace = onOpenSpace,
                            onOpenLive = onOpenLive,
                            onOpenDynamic = onOpenDynamic
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DynamicTabPage(
    page: Int,
    allItems: List<DynamicItem>,
    videoItems: List<DynamicItem>,
    articleItems: List<DynamicItem>,
    allListState: LazyListState,
    videoListState: LazyListState,
    articleListState: LazyListState,
    showUpList: Boolean,
    upList: DynamicUpList?,
    isLoadingMore: Boolean,
    findingMore: Boolean,
    manualLoadMore: Boolean,
    errorMessage: String?,
    loadMoreError: String?,
    onRetryRefresh: () -> Unit,
    onRetryLoadMore: () -> Unit,
    onManualLoadMore: () -> Unit,
    onOpenVideo: (VideoTarget) -> Unit,
    onOpenSpace: (SpaceRoute) -> Unit,
    onOpenLive: (LiveRoute) -> Unit,
    onOpenDynamic: (String) -> Unit
) {
    val pageItems = when (page) {
        1 -> videoItems
        2 -> articleItems
        else -> allItems
    }
    when {
        pageItems.isNotEmpty() -> {
            DynamicFeed(
                upList = if (page == 0 && showUpList) upList else null,
                items = pageItems,
                listState = when (page) {
                    1 -> videoListState
                    2 -> articleListState
                    else -> allListState
                },
                isLoadingMore = isLoadingMore,
                errorMessage = errorMessage,
                loadMoreError = loadMoreError,
                manualLoadMore = manualLoadMore,
                onManualLoadMore = onManualLoadMore,
                onRetryRefresh = onRetryRefresh,
                onRetryLoadMore = onRetryLoadMore,
                onOpenVideo = onOpenVideo,
                onOpenSpace = onOpenSpace,
                onOpenLive = onOpenLive,
                onOpenDynamic = onOpenDynamic,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 筛选页签为空但正在自动向后查找更多内容
        findingMore -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        // 筛选页签为空且自动加载失败：提供重试
        page != 0 && loadMoreError != null -> {
            Box(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                StateMessageCard(
                    text = loadMoreError.ifBlank { "加载更多动态失败" },
                    isError = true,
                    actionText = "点击重试",
                    onAction = onRetryLoadMore
                )
            }
        }

        else -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                StateMessageCard(
                    text = when (page) {
                        1 -> "暂无视频动态"
                        2 -> "暂无专栏动态"
                        else -> "暂无动态"
                    },
                    modifier = Modifier.fillMaxSize().padding(24.dp)
                )
            }
        }
    }
}

private const val LOAD_MORE_TRIGGER_OFFSET = 4
private const val MAX_STALE_AUTO_LOADS = 6
