package com.naaammme.bbspace.feature.space.archive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.naaammme.bbspace.core.designsystem.component.CoverImage
import com.naaammme.bbspace.core.designsystem.component.FilledTabRow
import com.naaammme.bbspace.core.designsystem.component.StateMessageCard
import com.naaammme.bbspace.core.model.LiveRoute
import com.naaammme.bbspace.core.model.SpaceVideo
import com.naaammme.bbspace.core.model.VideoTarget
import com.naaammme.bbspace.feature.space.SpaceArchiveUiState
import com.naaammme.bbspace.feature.space.SpaceDynamicUiState
import com.naaammme.bbspace.feature.space.SpaceSection
import com.naaammme.bbspace.feature.space.dynamic.spaceDynamicSection
import java.util.Locale

internal fun LazyListScope.spaceArchiveSection(
    state: SpaceArchiveUiState,
    videoCount: Int,
    dynamics: SpaceDynamicUiState,
    section: SpaceSection,
    onOpenVideo: (VideoTarget) -> Unit,
    onSelectOrder: (String) -> Unit,
    onSelectSection: (SpaceSection) -> Unit,
    onOpenDynamic: (String) -> Unit,
    onOpenLive: (LiveRoute) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit
) {
    item(
        key = "space_section_bar",
        contentType = "section_bar"
    ) {
        val selectedIndex = if (section == SpaceSection.VIDEO) 0 else 1
        FilledTabRow(
            tabs = listOf("视频", "动态"),
            selectedIndex = selectedIndex,
            onSelect = { index ->
                onSelectSection(if (index == 0) SpaceSection.VIDEO else SpaceSection.DYNAMIC)
            }
        )
    }

    when (section) {
        SpaceSection.VIDEO -> videoSection(
            state = state,
            videoCount = videoCount,
            onOpenVideo = onOpenVideo,
            onSelectOrder = onSelectOrder,
            onRetryLoadMore = onLoadMore
        )
        SpaceSection.DYNAMIC -> spaceDynamicSection(
            state = dynamics,
            onOpenDynamic = onOpenDynamic,
            onOpenLive = onOpenLive,
            onOpenVideo = onOpenVideo,
            onRetry = onRefresh,
            onLoadMore = onLoadMore
        )
    }
}

private fun LazyListScope.videoSection(
    state: SpaceArchiveUiState,
    videoCount: Int,
    onOpenVideo: (VideoTarget) -> Unit,
    onSelectOrder: (String) -> Unit,
    onRetryLoadMore: () -> Unit
) {
    item(
        key = "archive_toolbar",
        contentType = "toolbar"
    ) {
        val orderIndex = state.orders.indexOfFirst { it.value == state.selectedOrder }
            .takeIf { it >= 0 }
            ?: 0
        val order = state.orders.getOrNull(orderIndex)
        val nextOrder = state.orders
            .takeIf { it.size > 1 }
            ?.get((orderIndex + 1) % state.orders.size)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${videoCount.coerceAtLeast(state.videos.size)} 个视频",
                style = MaterialTheme.typography.titleSmall
            )
            TextButton(
                onClick = { nextOrder?.let { onSelectOrder(it.value) } },
                enabled = nextOrder != null
            ) {
                Text(
                    text = order?.title ?: "排序",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }

    state.message?.let { message ->
        item(key = "archive_error", contentType = "state") {
            StateMessageCard(text = message, isError = true)
        }
    }

    when {
        state.showEmpty && !state.isRefreshing -> {
            item(key = "archive_empty", contentType = "state") {
                StateMessageCard(text = "这个空间还没有公开视频")
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
                    onClick = { onOpenVideo(video.target) }
                )
            }
        }
    }

    when {
        state.loadMoreError != null -> {
            item(key = "archive_load_more_error", contentType = "state") {
                StateMessageCard(
                    text = state.loadMoreError,
                    isError = true,
                    actionText = "重试",
                    onAction = onRetryLoadMore
                )
            }
        }
        !state.hasMore && state.videos.isNotEmpty() -> {
            item(key = "archive_end", contentType = "state") {
                StateMessageCard(text = "没有更多视频了")
            }
        }
    }
}

@Composable
private fun SpaceVideoCard(
    video: SpaceVideo,
    onClick: () -> Unit
) {
    val meta = remember(video.author, video.categoryName, video.publishTimeText) {
        listOfNotNull(video.author, video.categoryName, video.publishTimeText).joinToString(" · ")
    }
    val durationText = remember(video.durationSec) {
        video.durationSec.takeIf { it > 0L }?.let(::formatDuration)
    }
    val stats = remember(video.viewText, video.danmakuText, durationText) {
        listOfNotNull(
            video.viewText.takeIf(String::isNotBlank)?.let { "$it 播放" },
            video.danmakuText?.takeIf(String::isNotBlank)?.let { "$it 弹幕" },
            durationText
        ).joinToString(" · ")
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CoverImage(
                url = video.cover,
                contentDescription = video.title,
                modifier = Modifier
                    .weight(0.38f)
                    .aspectRatio(16f / 10f)
            ) {
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
