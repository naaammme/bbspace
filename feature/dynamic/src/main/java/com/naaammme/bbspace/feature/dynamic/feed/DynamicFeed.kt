package com.naaammme.bbspace.feature.dynamic.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.naaammme.bbspace.core.designsystem.component.AvatarImage
import com.naaammme.bbspace.core.designsystem.component.CoverImage
import com.naaammme.bbspace.core.designsystem.component.DynamicCardSkeleton
import com.naaammme.bbspace.core.designsystem.component.UpListRow
import com.naaammme.bbspace.core.model.DynamicBody
import com.naaammme.bbspace.core.model.DynamicImage
import com.naaammme.bbspace.core.model.DynamicItem
import com.naaammme.bbspace.core.model.DynamicUpList
import com.naaammme.bbspace.core.model.LiveRoute
import com.naaammme.bbspace.core.model.SpaceRoute
import com.naaammme.bbspace.core.model.VideoTarget

@Composable
fun DynamicFeed(
    upList: DynamicUpList?,
    items: List<DynamicItem>,
    listState: LazyListState,
    isLoadingMore: Boolean,
    errorMessage: String?,
    errorOnLoadMore: Boolean,
    onRetry: () -> Unit,
    onOpenVideo: (VideoTarget) -> Unit,
    onOpenSpace: (SpaceRoute) -> Unit,
    onOpenLive: (LiveRoute) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        upList?.let { data ->
            item(
                key = "dynamic_up_list",
                contentType = "dynamic_up_list"
            ) {
                UpListRow(
                    title = data.title,
                    items = data.items,
                    key = { it.uid },
                    name = { it.name },
                    face = { it.face },
                    onClick = { item ->
                        onOpenSpace(SpaceRoute(mid = item.uid, name = item.name))
                    }
                )
            }
        }

        items(
            items = items,
            key = { it.id },
            contentType = { it.type }
        ) { item ->
            DynamicCard(
                item = item,
                onOpenVideo = onOpenVideo,
                onOpenSpace = onOpenSpace,
                onOpenLive = onOpenLive
            )
        }

        if (isLoadingMore) {
            items(
                count = LOAD_MORE_SKELETON_COUNT,
                key = { index -> "dynamic_loading_$index" },
                contentType = { "loading" }
            ) {
                DynamicCardSkeleton()
            }
        }

        if (errorMessage != null && items.isNotEmpty()) {
            item(
                key = "dynamic_error",
                contentType = "error"
            ) {
                DynamicError(
                    message = errorMessage,
                    onRetry = onRetry,
                    isLoadMore = errorOnLoadMore
                )
            }
        }
    }
}

@Composable
private fun DynamicCard(
    item: DynamicItem,
    onOpenVideo: (VideoTarget) -> Unit,
    onOpenSpace: (SpaceRoute) -> Unit,
    onOpenLive: (LiveRoute) -> Unit
) {
    val onClick = {
        val liveRoute = item.liveRoute
        val videoTarget = item.videoTarget
        when {
            liveRoute != null -> onOpenLive(liveRoute)
            videoTarget != null -> onOpenVideo(videoTarget)
        }
    }
    val modifier = Modifier.fillMaxWidth()
    val colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    )
    val elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    if (item.canOpen) {
        Card(
            onClick = onClick,
            modifier = modifier,
            colors = colors,
            elevation = elevation
        ) {
            DynamicCardContent(item = item, onOpenSpace = onOpenSpace)
        }
    } else {
        Card(
            modifier = modifier,
            colors = colors,
            elevation = elevation
        ) {
            DynamicCardContent(item = item, onOpenSpace = onOpenSpace)
        }
    }
}

@Composable
private fun DynamicCardContent(
    item: DynamicItem,
    onOpenSpace: (SpaceRoute) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        DynamicHeader(item = item, onOpenSpace = onOpenSpace)
        DynamicBodyContent(item = item)
        item.stats?.let { stats ->
            Text(
                text = "转发 ${formatCount(stats.repost)}  评论 ${formatCount(stats.reply)}  点赞 ${formatCount(stats.like)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DynamicHeader(
    item: DynamicItem,
    onOpenSpace: (SpaceRoute) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AvatarImage(
            url = item.author?.avatar,
            contentDescription = item.author?.name ?: "用户",
            modifier = Modifier.size(42.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            val route = item.spaceRoute
            val nameModifier = remember(route) {
                if (route == null) Modifier
                else Modifier.clickable { onOpenSpace(route) }
            }
            Text(
                text = item.author?.name ?: "动态",
                style = MaterialTheme.typography.titleSmall,
                modifier = nameModifier,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val meta = listOfNotNull(item.publishedText, item.author?.pubLocation)
                .joinToString(" · ")
                .ifBlank { item.type }
            Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DynamicBodyContent(item: DynamicItem) {
    when (val body = item.body) {
        is DynamicBody.Text -> {
            DynamicText(body.text)
        }

        is DynamicBody.Draw -> {
            body.text?.let { text ->
                DynamicText(text)
            }
            if (body.images.isNotEmpty()) {
                DynamicImageRow(body.images)
            }
        }

        is DynamicBody.Archive -> {
            body.text?.let { text ->
                DynamicText(text)
            }
            DynamicMediaCard(
                title = body.title,
                subTitle = body.subTitle,
                cover = body.cover,
                badge = body.badge
            )
        }

        is DynamicBody.Article -> {
            body.text?.let { text ->
                DynamicText(text)
            }
            DynamicMediaCard(
                title = body.title,
                subTitle = body.subTitle,
                cover = body.cover,
                badge = null
            )
        }

        is DynamicBody.Live -> {
            body.text?.let { text ->
                DynamicText(text)
            }
            DynamicMediaCard(
                title = body.title,
                subTitle = body.subTitle,
                cover = body.cover,
                badge = body.badge
            )
        }

        is DynamicBody.Forward -> {
            body.text?.let { text ->
                DynamicText(text)
            }
            body.origin?.let { origin ->
                val forwardBgColor = MaterialTheme.colorScheme.surface
                val forwardBgShape = MaterialTheme.shapes.medium
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(color = forwardBgColor, shape = forwardBgShape)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    origin.authorName?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    origin.bodyText?.let { text ->
                        DynamicText(text)
                    }
                    if (!origin.title.isNullOrBlank() || !origin.cover.isNullOrBlank()) {
                        DynamicMediaCard(
                            title = origin.title.orEmpty(),
                            subTitle = null,
                            cover = origin.cover,
                            badge = origin.badge
                        )
                    }
                }
            }
        }

        is DynamicBody.Unknown -> {
            body.text?.let { text ->
                DynamicText(text)
            }
        }
    }
}

@Composable
private fun DynamicMediaCard(
    title: String,
    subTitle: String?,
    cover: String?,
    badge: String?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        cover?.let {
            CoverImage(
                url = it,
                modifier = Modifier
                    .width(132.dp)
                    .aspectRatio(16f / 10f),
                shape = MaterialTheme.shapes.small
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            subTitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            badge?.let {
                val badgeBgColor = MaterialTheme.colorScheme.secondaryContainer
                val badgeBgShape = MaterialTheme.shapes.extraSmall
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier
                        .background(badgeBgColor, badgeBgShape)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun DynamicImageRow(images: List<DynamicImage>) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(
            items = images,
            key = { it.url }
        ) { image ->
            CoverImage(
                url = image.url,
                modifier = Modifier
                    .width(140.dp)
                    .aspectRatio(image.aspectRatio()),
                shape = MaterialTheme.shapes.small
            )
        }
    }
}

@Composable
private fun DynamicText(text: String) {
    if (text.isBlank()) return
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 8,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun DynamicError(
    message: String,
    onRetry: () -> Unit,
    isLoadMore: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = if (message.isBlank()) "加载动态失败" else message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
            Text(
                text = if (isLoadMore) "点击重试加载更多" else "点击重试",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onRetry)
            )
        }
    }
}

private fun DynamicImage.aspectRatio(): Float {
    return if (width > 0 && height > 0) {
        width.toFloat() / height.toFloat()
    } else {
        1f
    }
}

private fun formatCount(value: Long): String {
    return when {
        value >= 100_000_000L -> {
            val number = value / 100_000_000f
            if (number >= 10f) "${number.toInt()}亿" else "%.1f亿".format(number)
        }

        value >= 10_000L -> {
            val number = value / 10_000f
            if (number >= 10f) "${number.toInt()}万" else "%.1f万".format(number)
        }

        else -> value.toString()
    }
}

private const val LOAD_MORE_SKELETON_COUNT = 3
