package com.naaammme.bbspace.feature.space

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.naaammme.bbspace.core.common.media.thumbnailUrl
import com.naaammme.bbspace.core.designsystem.component.FilledTabRow
import com.naaammme.bbspace.core.designsystem.component.VideoListCardSkeleton
import com.naaammme.bbspace.core.model.SpaceVideo
import com.naaammme.bbspace.core.model.VideoRoute
import com.naaammme.bbspace.feature.space.model.SpaceArchiveUiState
import java.util.Locale

internal fun LazyListScope.spaceArchiveSection(
    state: SpaceArchiveUiState,
    onOpenVideo: (VideoRoute) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onSelectOrder: (String) -> Unit
) {
    if (state.orders.size > 1) {
        item(
            key = "archive_order",
            contentType = "order"
        ) {
            val titles = remember(state.orders) {
                state.orders.map { it.title }
            }
            val selectedIndex = remember(state.orders, state.selectedOrder) {
                state.orders.indexOfFirst { it.value == state.selectedOrder }
                    .coerceAtLeast(0)
            }
            FilledTabRow(
                tabs = titles,
                selectedIndex = selectedIndex,
                onSelect = { index ->
                    onSelectOrder(state.orders[index].value)
                }
            )
        }
    }

    if (!state.message.isNullOrBlank()) {
        item(
            key = "archive_error",
            contentType = "state"
        ) {
            RetryCard(
                text = state.message.orEmpty(),
                button = "重试",
                onRetry = onRetry
            )
        }
    }

    when {
        state.isRefreshing && state.videos.isEmpty() -> {
            items(
                count = INIT_SKELETON_COUNT,
                key = { index -> "archive_skeleton_$index" },
                contentType = { "video_skeleton" }
            ) {
                VideoListCardSkeleton()
            }
        }

        state.showEmpty -> {
            item(
                key = "archive_empty",
                contentType = "state"
            ) {
                StateCard(text = "这个空间还没有公开视频")
            }
        }

        else -> {
            items(
                items = state.videos,
                key = { "${it.aid}_${it.cid}" },
                contentType = { "video" }
            ) { video ->
                SpaceVideoCard(
                    video = video,
                    onClick = { onOpenVideo(video.route) }
                )
            }
        }
    }

    when {
        state.isLoadingMore -> {
            items(
                count = LOAD_MORE_SKELETON_COUNT,
                key = { index -> "archive_loading_more_$index" },
                contentType = { "video_skeleton" }
            ) {
                VideoListCardSkeleton()
            }
        }

        !state.loadMoreError.isNullOrBlank() -> {
            item(
                key = "archive_load_more_error",
                contentType = "state"
            ) {
                RetryCard(
                    text = state.loadMoreError.orEmpty(),
                    button = "重试",
                    onRetry = onLoadMore
                )
            }
        }

        !state.hasMore && state.videos.isNotEmpty() -> {
            item(
                key = "archive_end",
                contentType = "state"
            ) {
                StateCard(text = "没有更多视频了")
            }
        }
    }
}

@Composable
private fun SpaceVideoCard(
    video: SpaceVideo,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val imageRequest = remember(context, video.cover) {
        ImageRequest.Builder(context)
            .data(thumbnailUrl(video.cover))
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
    }
    val meta = remember(video.author, video.categoryName, video.publishTimeText) {
        listOfNotNull(
            video.author,
            video.categoryName,
            video.publishTimeText
        ).joinToString(" · ")
    }
    val durationText = remember(video.durationSec) {
        video.durationSec.takeIf { it > 0L }?.let(::formatDuration)
    }
    val stats = remember(video.viewText, video.danmakuText, durationText) {
        listOfNotNull(
            video.viewText.takeIf(String::isNotBlank)?.let { "$it 播放" },
            video.danmakuText?.let { "$it 弹幕" },
            durationText
        ).joinToString(" · ")
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(0.38f)
                    .aspectRatio(16f / 10f)
                    .clip(MaterialTheme.shapes.medium)
            ) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = video.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                if (durationText != null) {
                    Text(
                        text = durationText,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .background(
                                color = Color.Black.copy(alpha = 0.56f),
                                shape = MaterialTheme.shapes.extraSmall
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }

            Column(
                modifier = Modifier.weight(0.62f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (meta.isNotBlank()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (stats.isNotBlank()) {
                    Text(
                        text = stats,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun formatDuration(durationSec: Long): String {
    val minute = durationSec / 60
    val second = durationSec % 60
    val hour = minute / 60
    return if (hour > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hour, minute % 60, second)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minute, second)
    }
}

private const val INIT_SKELETON_COUNT = 4
private const val LOAD_MORE_SKELETON_COUNT = 2
