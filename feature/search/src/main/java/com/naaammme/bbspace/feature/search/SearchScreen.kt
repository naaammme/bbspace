package com.naaammme.bbspace.feature.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naaammme.bbspace.core.designsystem.component.CollapsingTopBarScaffold
import com.naaammme.bbspace.core.designsystem.component.FilledTabRow
import com.naaammme.bbspace.core.designsystem.component.StateMessageCard
import com.naaammme.bbspace.core.designsystem.component.VideoListCardSkeleton
import com.naaammme.bbspace.core.model.SearchFilter
import com.naaammme.bbspace.core.model.SearchTime
import com.naaammme.bbspace.core.model.SpaceRoute
import com.naaammme.bbspace.core.model.VideoTarget
import com.naaammme.bbspace.feature.search.result.SearchAuthorCard
import com.naaammme.bbspace.feature.search.filter.SearchFiltersSheet
import com.naaammme.bbspace.feature.search.history.SearchHistoryPanel
import com.naaammme.bbspace.feature.search.result.SearchCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenSpace: (SpaceRoute) -> Unit,
    onOpenVideo: (VideoTarget) -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val authors by viewModel.authors.collectAsStateWithLifecycle()
    val videos by viewModel.videos.collectAsStateWithLifecycle()
    val histories by viewModel.histories.collectAsStateWithLifecycle()
    val historyOrder by viewModel.currentHistoryOrder.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    val hasItems = authors.isNotEmpty() || videos.isNotEmpty()
    val itemCount = authors.size + videos.size
    val sortFilter = viewModel.filters.firstOrNull { it.key == SORT_KEY }
    val filters = viewModel.filters.filterNot { it.key == SORT_KEY }
    val hasActiveExtraFilter = filters.any { viewModel.selectedOf(it.key).isNotEmpty() } || viewModel.time.isActive
    val handleBack = {
        if (!viewModel.consumeBack()) {
            onBack()
        }
    }

    val shouldLoadMore by remember(
        listState,
        itemCount,
        viewModel.canLoadMore
    ) {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            viewModel.canLoadMore &&
                    !viewModel.isLoading &&
                    !viewModel.isLoadingMore &&
                    itemCount > 0 &&
                    last >= itemCount - 3
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
                    viewModel.submitSearch()
                },
                onOpenSpace = { uid ->
                    keyboard?.hide()
                    onOpenSpace(SpaceRoute(mid = uid))
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
                                onDismissKeyboard = { keyboard?.hide() },
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
                        keyboard?.hide()
                        viewModel.applyFilter(filter.key, params)
                    }
                )
            }
            if (sortFilter == null && filters.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    SearchFilterAction(
                        filters = filters,
                        sortSelected = viewModel.selectedOf(SORT_KEY),
                        selectedMap = buildSelectedMap(filters, viewModel),
                        time = viewModel.time,
                        active = hasActiveExtraFilter,
                        onDismissKeyboard = { keyboard?.hide() },
                        onApply = viewModel::applyFilters
                    )
                }
            }

            when {
                viewModel.isLoading && !hasItems -> SearchLoadingList()

                viewModel.errorMessage != null && !hasItems -> {
                    StateMessageCard(
                        text = viewModel.errorMessage.orEmpty().ifBlank { "搜索失败" },
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        isError = true,
                        actionText = "重试",
                        onAction = {
                            keyboard?.hide()
                            viewModel.submitSearch(recordHistory = false)
                        }
                    )
                }

                viewModel.keyword.isBlank() -> {
                    if (histories.isNotEmpty()) {
                        SearchHistoryPanel(
                            histories = histories,
                            order = historyOrder,
                            onToggleOrder = viewModel::toggleHistoryOrder,
                            onSearch = { keyword ->
                                keyboard?.hide()
                                viewModel.submitSearch(keyword)
                            },
                            onDelete = viewModel::deleteHistory
                        )
                    }
                }

                !hasItems -> StateMessageCard(
                    text = "没有找到结果",
                    modifier = Modifier.fillMaxSize().padding(16.dp)
                )

                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            items = authors,
                            key = { it.mid },
                            contentType = { "author" }
                        ) { author ->
                            SearchAuthorCard(
                                author = author,
                                onClick = { onOpenSpace(SpaceRoute(mid = author.mid, name = author.name)) }
                            )
                        }

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

                        if (viewModel.errorMessage != null && itemCount > 0) {
                            item {
                                StateMessageCard(
                                    text = viewModel.errorMessage.orEmpty().ifBlank { "搜索失败" },
                                    isError = true,
                                    actionText = "重试",
                                    onAction = viewModel::loadMore
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
        modifier = Modifier,
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
            imageVector = Icons.Default.Menu,
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
