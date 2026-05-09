package com.naaammme.bbspace.feature.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naaammme.bbspace.core.designsystem.component.CollapsingTopBarScaffold
import com.naaammme.bbspace.core.designsystem.component.FilledTabRow
import com.naaammme.bbspace.core.designsystem.component.VideoListCardSkeleton
import com.naaammme.bbspace.core.model.SearchFilter
import com.naaammme.bbspace.core.model.SearchTime
import com.naaammme.bbspace.core.model.VideoTarget
import com.naaammme.bbspace.feature.search.filter.SearchFiltersSheet
import com.naaammme.bbspace.feature.search.history.SearchHistoryPanel
import com.naaammme.bbspace.feature.search.result.SearchCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenVideo: (VideoTarget) -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val videos by viewModel.videos.collectAsStateWithLifecycle()
    val histories by viewModel.histories.collectAsStateWithLifecycle()
    val historyOrder by viewModel.currentHistoryOrder.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    val sortFilter = viewModel.filters.firstOrNull { it.key == SORT_KEY }
    val filters = viewModel.filters.filterNot { it.key == SORT_KEY }
    val hasActiveExtraFilter = filters.any { viewModel.selectedOf(it.key).isNotEmpty() } || viewModel.time.isActive
    val handleBack = {
        if (!viewModel.consumeBack()) {
            onBack()
        }
    }

    fun dismissKeyboard() {
        focusManager.clearFocus(force = true)
        keyboard?.hide()
    }

    val currentVideos by rememberUpdatedState(videos)
    val shouldLoadMore by remember(
        listState,
        viewModel.canLoadMore,
        viewModel.isLoading,
        viewModel.isLoadingMore
    ) {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            viewModel.canLoadMore &&
                    !viewModel.isLoading &&
                    !viewModel.isLoadingMore &&
                    currentVideos.isNotEmpty() &&
                    last >= currentVideos.lastIndex - 2
        }
    }

    BackHandler(onBack = handleBack)

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            viewModel.loadMore()
        }
    }

    CollapsingTopBarScaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = { scrollBehavior ->
            SearchTopBar(
                text = viewModel.input,
                autoFocus = viewModel.keyword.isBlank() && viewModel.input.isBlank(),
                onTextChange = viewModel::updateInput,
                onBack = handleBack,
                onSearch = {
                    dismissKeyboard()
                    viewModel.submitSearch()
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            sortFilter?.takeIf { it.ops.size > 1 }?.let { filter ->
                val trailing: (@Composable RowScope.() -> Unit)? =
                    if (filters.isNotEmpty()) {
                        {
                            SearchFilterAction(
                                filters = filters,
                                sortSelected = viewModel.selectedOf(SORT_KEY),
                                selectedMap = buildSelectedMap(filters, viewModel),
                                time = viewModel.time,
                                active = hasActiveExtraFilter,
                                onDismissKeyboard = ::dismissKeyboard,
                                onApply = viewModel::applyFilters
                            )
                        }
                    } else {
                        null
                    }
                SearchSortRow(
                    filter = filter,
                    selected = viewModel.selectedOf(filter.key),
                    trailing = trailing,
                    onSelect = { params ->
                        dismissKeyboard()
                        viewModel.applyFilter(filter.key, params)
                    }
                )
            }
            if (sortFilter == null && filters.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    SearchFilterAction(
                        filters = filters,
                        sortSelected = viewModel.selectedOf(SORT_KEY),
                        selectedMap = buildSelectedMap(filters, viewModel),
                        time = viewModel.time,
                        active = hasActiveExtraFilter,
                        onDismissKeyboard = ::dismissKeyboard,
                        onApply = viewModel::applyFilters
                    )
                }
            }

            when {
                viewModel.isLoading && videos.isEmpty() -> SearchLoadingList()

                viewModel.errorMessage != null && videos.isEmpty() -> {
                    SearchError(
                        message = viewModel.errorMessage.orEmpty(),
                        onRetry = {
                            dismissKeyboard()
                            viewModel.submitSearch(recordHistory = false)
                        }
                    )
                }

                viewModel.keyword.isBlank() && videos.isEmpty() -> {
                    if (histories.isEmpty()) {
                        SearchHint(text = "输入关键词开始搜索")
                    } else {
                        SearchHistoryPanel(
                            histories = histories,
                            order = historyOrder,
                            onToggleOrder = viewModel::toggleHistoryOrder,
                            onSearch = { keyword ->
                                dismissKeyboard()
                                viewModel.submitSearch(keyword)
                            },
                            onDelete = viewModel::deleteHistory
                        )
                    }
                }

                videos.isEmpty() -> SearchHint(text = "没有找到视频结果")

                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            items = videos,
                            key = { "${it.aid}_${it.cid}" },
                            contentType = { "video" }
                        ) { video ->
                            SearchCard(
                                video = video,
                                onClick = { onOpenVideo(video.target) }
                            )
                        }

                        if (viewModel.isLoadingMore) {
                            items(
                                count = LOAD_MORE_SKELETON_COUNT,
                                key = { index -> "loading_$index" },
                                contentType = { "skeleton" }
                            ) {
                                VideoListCardSkeleton()
                            }
                        }

                        if (viewModel.errorMessage != null && videos.isNotEmpty()) {
                            item {
                                SearchError(
                                    message = viewModel.errorMessage.orEmpty(),
                                    onRetry = viewModel::loadMore
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchSortRow(
    filter: SearchFilter,
    selected: Set<String>,
    trailing: (@Composable RowScope.() -> Unit)?,
    onSelect: (Set<String>) -> Unit
) {
    val selectedIndex = remember(filter, selected) {
        val pickedIndex = filter.ops.indexOfFirst { op ->
            if (selected.isEmpty()) op.isDefault else op.param in selected
        }
        if (pickedIndex >= 0) pickedIndex else 0
    }
    FilledTabRow(
        tabs = filter.ops.map { it.label },
        selectedIndex = selectedIndex,
        onSelect = { index ->
            val op = filter.ops[index]
            onSelect(if (op.isDefault) emptySet() else setOf(op.param))
        },
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        trailing = trailing
    )
}

@Composable
private fun SearchFilterAction(
    filters: List<SearchFilter>,
    sortSelected: Set<String>,
    selectedMap: Map<String, Set<String>>,
    time: SearchTime,
    active: Boolean,
    onDismissKeyboard: () -> Unit,
    onApply: (Map<String, Set<String>>, SearchTime) -> Unit
) {
    var showFilterSheet by remember { mutableStateOf(false) }

    SearchFilterButton(
        active = active,
        onClick = {
            onDismissKeyboard()
            showFilterSheet = true
        }
    )

    if (showFilterSheet) {
        SearchFiltersSheet(
            filters = filters,
            selectedMap = selectedMap,
            time = time,
            onDismiss = { showFilterSheet = false },
            onApply = { picked, pickedTime ->
                val nextSel = buildMap {
                    if (sortSelected.isNotEmpty()) {
                        put(SORT_KEY, sortSelected)
                    }
                    picked.forEach { (key, value) ->
                        if (value.isEmpty()) return@forEach
                        put(key, value)
                    }
                }
                onApply(nextSel, pickedTime)
                showFilterSheet = false
            }
        )
    }
}

@Composable
private fun SearchFilterButton(
    active: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (active) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Icon(
            imageVector = Icons.Default.MoreVert,
            contentDescription = "筛选",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            tint = if (active) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
private fun SearchLoadingList(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(
            count = INIT_SKELETON_COUNT,
            key = { index -> "skeleton_$index" },
            contentType = { "skeleton" }
        ) {
            VideoListCardSkeleton()
        }
    }
}

@Composable
private fun SearchHint(text: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SearchError(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = message.ifBlank { "搜索失败" },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error
            )
            TextButton(onClick = onRetry) {
                Text("重试")
            }
        }
    }
}

private fun buildSelectedMap(
    filters: List<SearchFilter>,
    vm: SearchViewModel
): Map<String, Set<String>> {
    return buildMap {
        filters.forEach { filter ->
            val picked = vm.selectedOf(filter.key)
            if (picked.isEmpty()) return@forEach
            put(filter.key, picked)
        }
    }
}

private const val INIT_SKELETON_COUNT = 8
private const val LOAD_MORE_SKELETON_COUNT = 2
private const val SORT_KEY = "sort"
