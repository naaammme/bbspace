package com.naaammme.bbspace.feature.home.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naaammme.bbspace.core.designsystem.component.CollapsingTopBarScaffold
import com.naaammme.bbspace.core.designsystem.component.FilledTabRow
import com.naaammme.bbspace.core.model.LiveRoute
import com.naaammme.bbspace.core.model.VideoRoute
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val homeTabs = listOf("推荐", "直播")

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToSearch: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onOpenVideo: (VideoRoute) -> Unit = {},
    onOpenLive: (LiveRoute) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val items = viewModel.items.collectAsStateWithLifecycle().value
    val interestChoose = viewModel.interestChoose.collectAsStateWithLifecycle().value
    val pagerState = rememberPagerState(pageCount = { homeTabs.size })
    val scope = rememberCoroutineScope()

    if (interestChoose != null) {
        InterestDialog(
            data = interestChoose,
            onDismiss = viewModel::dismissInterest,
            onConfirm = { id, result, posIds -> viewModel.submitInterest(id, result, posIds) }
        )
    }

    CollapsingTopBarScaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = { scrollBehavior ->
            HomeTopBar(
                scrollBehavior = scrollBehavior,
                selectedIndex = pagerState.currentPage,
                onNavigateToSearch = onNavigateToSearch,
                onNavigateToSettings = onNavigateToSettings,
                onSelectTab = { page ->
                    scope.launch { pagerState.animateScrollToPage(page) }
                }
            )
        }
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) { page ->
            when (page) {
                0 -> HomeVideoPage(
                    items = items,
                    isRefreshing = viewModel.isRefreshing,
                    isLoadingMore = viewModel.isLoadingMore,
                    errorMessage = viewModel.errorMessage,
                    onRefresh = viewModel::refresh,
                    onLoadMore = viewModel::loadMore,
                    onOpenVideo = onOpenVideo,
                    onOpenLive = onOpenLive
                )

                else -> HomeLivePage(
                    isActive = pagerState.currentPage == page,
                    onOpenLive = onOpenLive
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopBar(
    scrollBehavior: TopAppBarScrollBehavior,
    selectedIndex: Int,
    onNavigateToSearch: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onSelectTab: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .clipToBounds()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val heightPx = placeable.height.toFloat()
                val state = scrollBehavior.state
                val limit = -heightPx
                if (state.heightOffsetLimit != limit) state.heightOffsetLimit = limit
                val offsetY = state.heightOffset.roundToInt()
                val layoutHeight = (placeable.height + offsetY).coerceAtLeast(0)
                layout(placeable.width, layoutHeight) {
                    placeable.placeRelative(0, offsetY)
                }
            }
            .padding(top = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Card(
                onClick = onNavigateToSearch,
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(
                    MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "搜索",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onNavigateToSettings) {
                Icon(Icons.Default.Settings, "设置")
            }
        }
        FilledTabRow(
            tabs = homeTabs,
            selectedIndex = selectedIndex,
            onSelect = onSelectTab,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 0.dp, bottom = 3.dp)
        )
    }
}
