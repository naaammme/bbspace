package com.naaammme.bbspace.feature.home.live

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naaammme.bbspace.core.designsystem.component.AdaptiveMediaGrid
import com.naaammme.bbspace.core.designsystem.component.CoverImage
import com.naaammme.bbspace.core.designsystem.component.UpListRow
import com.naaammme.bbspace.core.designsystem.component.VideoGridCardSkeleton
import com.naaammme.bbspace.core.model.LiveRecommendItem
import com.naaammme.bbspace.core.model.LiveRoute
import com.naaammme.bbspace.core.model.SpaceRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeLivePage(
    isActive: Boolean,
    onOpenLive: (LiveRoute) -> Unit,
    onOpenSpace: (SpaceRoute) -> Unit,
    viewModel: HomeLiveViewModel = hiltViewModel()
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value

    LaunchedEffect(isActive) {
        if (isActive) viewModel.ensureLoaded()
    }
    AdaptiveMediaGrid(
        items = state.items,
        isRefreshing = state.isRefreshing,
        isLoadingMore = state.isLoadingMore,
        onRefresh = viewModel::refresh,
        onLoadMore = viewModel::loadMore,
        modifier = Modifier.fillMaxSize(),
        errorMessage = state.errorMessage,
        loadMoreEnabled = isActive,
        key = { _, item -> item.actionKey() },
        loadingContent = {
            VideoGridCardSkeleton()
        },
        headerContent = state.upList?.let { upList ->
            {
                UpListRow(
                    title = upList.title,
                    items = upList.items,
                    key = { it.uid },
                    name = { it.name },
                    face = { it.face },
                    onClick = { item ->
                        onOpenLive(item.route)
                    }
                )
            }
        },
        emptyContent = {
            LiveEmptyState(state.errorMessage)
        }
    ) { item ->
        LiveRecommendCard(
            item = item,
            onClick = { onOpenLive(item.route) },
            onOpenSpace = onOpenSpace
        )
    }
}

private fun LiveRecommendItem.actionKey(): String {
    return "${roomId}_${sessionId.orEmpty()}"
}

@Composable
private fun LiveEmptyState(errorMessage: String?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "暂无直播推荐",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = errorMessage ?: "下拉试试重新获取",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun LiveRecommendCard(
    item: LiveRecommendItem,
    onClick: () -> Unit,
    onOpenSpace: (SpaceRoute) -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column {
            CoverImage(
                url = item.cover,
                contentDescription = item.title,
                shape = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
            ) {
                item.onlineText?.let { text ->
                    val onlineBgColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
                    val onlineBgShape = MaterialTheme.shapes.extraSmall
                    Text(
                        text = text,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .background(color = onlineBgColor, shape = onlineBgShape)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                val spaceRoute = remember(item.ownerMid, item.ownerName) {
                    item.ownerMid?.let { mid ->
                        SpaceRoute(
                            mid = mid,
                            name = item.ownerName
                        )
                    }
                }
                Text(
                    text = item.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium
                )

                item.ownerName?.let { ownerName ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = ownerName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (spaceRoute == null) {
                            Modifier
                        } else {
                            Modifier.clickable { onOpenSpace(spaceRoute) }
                        }
                    )
                }

                item.areaName?.let { areaName ->
                    Spacer(modifier = Modifier.height(4.dp))
                    val areaBgColor = MaterialTheme.colorScheme.secondaryContainer
                    val areaBgShape = MaterialTheme.shapes.extraSmall
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Text(
                            text = areaName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier
                                .background(color = areaBgColor, shape = areaBgShape)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
