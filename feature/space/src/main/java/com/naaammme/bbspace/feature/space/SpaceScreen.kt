package com.naaammme.bbspace.feature.space

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naaammme.bbspace.core.designsystem.component.CollapsingTopBarScaffold
import com.naaammme.bbspace.core.model.VideoRoute
import com.naaammme.bbspace.feature.space.model.SpaceViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpaceScreen(
    onBack: () -> Unit,
    onOpenVideo: (VideoRoute) -> Unit,
    viewModel: SpaceViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val archiveState by rememberUpdatedState(state.archive)

    LaunchedEffect(listState) {
        snapshotFlow {
            val total = listState.layoutInfo.totalItemsCount
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            total to last
        }
            .distinctUntilChanged()
            .filter { (total, last) ->
                archiveState.canLoadMore &&
                        total > 0 &&
                        last >= total - LOAD_MORE_TRIGGER_OFFSET
            }
            .collect {
                viewModel.loadMore()
            }
    }

    LaunchedEffect(state.archive.selectedOrder) {
        val needScrollTop = listState.firstVisibleItemIndex > 0 ||
                listState.firstVisibleItemScrollOffset > 0
        if (needScrollTop) {
            listState.scrollToItem(0)
        }
    }

    CollapsingTopBarScaffold(
        topBar = { scrollBehavior ->
            TopAppBar(
                title = {
                    Text(text = state.title)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        when {
            state.isPageLoading && state.header == null -> {
                SpaceLoading(modifier = Modifier.padding(padding))
            }

            state.header == null -> {
                SpaceError(
                    message = state.pageErrorMessage ?: "加载个人空间失败",
                    onRetry = viewModel::retry,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                )
            }

            else -> {
                val header = state.header ?: return@CollapsingTopBarScaffold
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    spaceHeaderSection(header)
                    spaceArchiveSection(
                        state = state.archive,
                        onOpenVideo = onOpenVideo,
                        onRetry = viewModel::retry,
                        onLoadMore = viewModel::loadMore,
                        onSelectOrder = viewModel::selectOrder
                    )
                }
            }
        }
    }
}

private const val LOAD_MORE_TRIGGER_OFFSET = 3
