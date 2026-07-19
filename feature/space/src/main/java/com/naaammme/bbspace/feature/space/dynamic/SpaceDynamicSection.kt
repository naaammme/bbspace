package com.naaammme.bbspace.feature.space.dynamic

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.naaammme.bbspace.core.designsystem.component.CoverImage
import com.naaammme.bbspace.core.designsystem.component.StateMessageCard
import com.naaammme.bbspace.core.model.DynamicBody
import com.naaammme.bbspace.core.model.DynamicItem
import com.naaammme.bbspace.core.model.LiveRoute
import com.naaammme.bbspace.core.model.VideoTarget
import com.naaammme.bbspace.feature.space.SpaceDynamicUiState

internal fun LazyListScope.spaceDynamicSection(
    state: SpaceDynamicUiState,
    onOpenDynamic: (String) -> Unit,
    onOpenLive: (LiveRoute) -> Unit,
    onOpenVideo: (VideoTarget) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit
) {
    state.message?.let { message ->
        item(key = "dynamic_error", contentType = "state") {
            StateMessageCard(
                text = message,
                isError = true,
                actionText = "重试",
                onAction = onRetry
            )
        }
    }

    when {
        state.showEmpty && !state.isRefreshing -> {
            item(key = "dynamic_empty", contentType = "state") {
                StateMessageCard(text = "这个空间还没有动态")
            }
        }
        else -> {
            items(
                items = state.items,
                key = { it.id },
                contentType = { "dynamic" }
            ) { item ->
                SpaceDynamicCard(
                    item = item,
                    onOpenDynamic = onOpenDynamic,
                    onOpenLive = onOpenLive,
                    onOpenVideo = onOpenVideo
                )
            }
        }
    }

    when {
        state.loadMoreError != null -> {
            item(key = "dynamic_load_more_error", contentType = "state") {
                StateMessageCard(
                    text = state.loadMoreError,
                    isError = true,
                    actionText = "重试",
                    onAction = onLoadMore
                )
            }
        }
        !state.hasMore && state.items.isNotEmpty() -> {
            item(key = "dynamic_end", contentType = "state") {
                StateMessageCard(text = "没有更多动态了")
            }
        }
    }
}

@Composable
private fun SpaceDynamicCard(
    item: DynamicItem,
    onOpenDynamic: (String) -> Unit,
    onOpenLive: (LiveRoute) -> Unit,
    onOpenVideo: (VideoTarget) -> Unit
) {
    val preview = remember(item) { item.toPreview() }
    val badge = remember(item) {
        when (val body = item.body) {
            is DynamicBody.Archive -> body.badge
            is DynamicBody.Live -> body.badge
            is DynamicBody.Forward -> body.origin?.badge
            else -> item.badge
        }
    }
    val liveRoute = item.liveRoute
    val videoTarget = item.videoTarget
    Card(
        onClick = {
            when {
                liveRoute != null -> onOpenLive(liveRoute)
                videoTarget != null -> onOpenVideo(videoTarget)
                else -> onOpenDynamic(item.id)
            }
        },
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
                url = preview.cover,
                contentDescription = preview.title,
                modifier = Modifier
                    .weight(0.38f)
                    .aspectRatio(16f / 10f),
                fallbackContent = {
                    Text(
                        text = preview.type,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            ) {
                badge?.let {
                    Text(
                        text = it,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .background(
                                color = Color.Black.copy(alpha = 0.56f),
                                shape = MaterialTheme.shapes.extraSmall
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Column(
                modifier = Modifier.weight(0.62f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = preview.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (preview.meta.isNotBlank()) {
                    Text(
                        text = preview.meta,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = preview.type,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun DynamicItem.toPreview(): SpaceDynamicPreview {
    val body = body
    val previewTitle = when (body) {
        is DynamicBody.Text -> title ?: body.text.lineSequence().firstOrNull()
        is DynamicBody.Draw -> title ?: body.text?.lineSequence()?.firstOrNull()
        is DynamicBody.Archive -> body.title
        is DynamicBody.Article -> body.title
        is DynamicBody.Live -> body.title
        is DynamicBody.Forward -> body.origin?.title ?: title ?: "转发动态"
        is DynamicBody.Unknown -> title ?: body.text?.lineSequence()?.firstOrNull()
    }?.takeIf(String::isNotBlank) ?: "动态"
    val meta = when (body) {
        is DynamicBody.Archive -> listOfNotNull(body.subTitle).joinToString(" · ")
        is DynamicBody.Article -> listOfNotNull(body.subTitle).joinToString(" · ")
        is DynamicBody.Live -> listOfNotNull(body.subTitle).joinToString(" · ")
        is DynamicBody.Forward -> listOfNotNull(body.origin?.authorName).joinToString(" · ")
        else -> listOfNotNull(publishedText).joinToString(" · ")
    }
    val previewCover = cover ?: when (body) {
        is DynamicBody.Draw -> body.images.firstOrNull()?.url
        is DynamicBody.Forward -> body.origin?.cover
        else -> null
    }
    val type = when {
        videoTarget != null -> "视频"
        body is DynamicBody.Live -> "直播"
        else -> "动态"
    }
    return SpaceDynamicPreview(
        type = type,
        title = previewTitle,
        meta = meta,
        cover = previewCover
    )
}

private data class SpaceDynamicPreview(
    val type: String,
    val title: String,
    val meta: String,
    val cover: String?
)
