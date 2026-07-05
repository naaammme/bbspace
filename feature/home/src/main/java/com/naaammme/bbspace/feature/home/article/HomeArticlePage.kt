package com.naaammme.bbspace.feature.home.article

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.naaammme.bbspace.core.designsystem.component.AvatarImage
import com.naaammme.bbspace.core.designsystem.component.CoverImage
import com.naaammme.bbspace.core.designsystem.component.VideoGridCardSkeleton
import com.naaammme.bbspace.core.model.SpaceRoute
import com.naaammme.bbspace.core.model.article.ArticleRecommendItem

@Composable
fun HomeArticlePage(
    isActive: Boolean,
    onOpenArticle: (String, Int) -> Unit,
    onOpenSpace: (SpaceRoute) -> Unit,
    viewModel: HomeArticleViewModel = hiltViewModel()
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
        key = { _, item -> item.id },
        loadingContent = {
            VideoGridCardSkeleton()
        },
        emptyContent = {
            ArticleEmptyState(state.errorMessage)
        }
    ) { item ->
        ArticleRecommendCard(
            item = item,
            onClick = { onOpenArticle(item.id.toString(), 1) },
            onOpenSpace = onOpenSpace
        )
    }
}

@Composable
private fun ArticleEmptyState(errorMessage: String?) {
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
                text = "暂无专栏推荐",
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
private fun ArticleRecommendCard(
    item: ArticleRecommendItem,
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
            )
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val statLine = remember(
                    item.viewCount,
                    item.likeCount,
                    item.replyCount
                ) {
                    buildStatLine(item)
                }
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                item.summary?.let { summary ->
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                ArticleAuthorRow(item = item, onOpenSpace = onOpenSpace)
                if (statLine != null || item.categoryName != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (statLine != null) {
                            Text(
                                text = statLine,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                        item.categoryName?.let { category ->
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(
                                text = category,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier
                                    .background(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = MaterialTheme.shapes.extraSmall
                                    )
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
}

@Composable
private fun ArticleAuthorRow(
    item: ArticleRecommendItem,
    onOpenSpace: (SpaceRoute) -> Unit
) {
    val route = remember(item.authorMid, item.authorName) {
        item.authorMid?.let { mid ->
            SpaceRoute(
                mid = mid,
                name = item.authorName
            )
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AvatarImage(
            url = item.authorFace,
            contentDescription = item.authorName ?: "作者",
            modifier = Modifier.size(20.dp),
            fallbackText = item.authorName?.take(1)
        )
        Spacer(modifier = Modifier.size(6.dp))
        Box(modifier = Modifier.weight(1f)) {
            Text(
                text = item.authorName ?: "专栏作者",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (route == null) {
                    Modifier
                } else {
                    Modifier.clickable { onOpenSpace(route) }
                }
            )
        }
        item.publishTimeText?.let { time ->
            Spacer(modifier = Modifier.size(6.dp))
            Text(
                text = time,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

private fun buildStatLine(item: ArticleRecommendItem): String? {
    return buildList {
        if (item.viewCount > 0) add("${item.viewCount} 阅读")
        if (item.likeCount > 0) add("${item.likeCount} 点赞")
        if (item.replyCount > 0) add("${item.replyCount} 评论")
    }.takeIf { it.isNotEmpty() }?.joinToString("  ")
}
