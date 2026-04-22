package com.naaammme.bbspace.feature.home.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.naaammme.bbspace.core.common.media.thumbnailUrl
import com.naaammme.bbspace.core.designsystem.component.AdaptiveMediaGrid
import com.naaammme.bbspace.core.designsystem.component.VideoGridCardSkeleton
import com.naaammme.bbspace.core.model.FeedItem
import com.naaammme.bbspace.core.model.LiveRoute
import com.naaammme.bbspace.core.model.SpaceRoute
import com.naaammme.bbspace.core.model.ThreePointItem
import com.naaammme.bbspace.core.model.VideoRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeVideoPage(
    items: List<FeedItem>,
    isRefreshing: Boolean,
    isLoadingMore: Boolean,
    errorMessage: String?,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenVideo: (VideoRoute) -> Unit,
    onOpenSpace: (SpaceRoute) -> Unit,
    onOpenLive: (LiveRoute) -> Unit
) {
    AdaptiveMediaGrid(
        items = items,
        isRefreshing = isRefreshing,
        isLoadingMore = isLoadingMore,
        onRefresh = onRefresh,
        onLoadMore = onLoadMore,
        modifier = Modifier.fillMaxSize(),
        errorMessage = errorMessage,
        key = { index, item -> "${item.idx}_$index" },
        contentType = { _, item -> item.cardType },
        loadingContent = {
            VideoGridCardSkeleton()
        }
    ) { item ->
        FeedCard(
            item = item,
            onOpenSpace = onOpenSpace,
            onClick = {
                item.liveRoute?.let(onOpenLive)
                    ?: item.route?.let(onOpenVideo)
            }
        )
    }
}

@Composable
private fun FeedCard(
    item: FeedItem,
    onOpenSpace: (SpaceRoute) -> Unit,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val imageRequest = remember(item.cover) {
        ImageRequest.Builder(context)
            .data(thumbnailUrl(item.cover))
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
    }
    Card(
        onClick = onClick,
        enabled = item.route != null || item.liveRoute != null,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
            ) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                val hasLeftText = item.coverLeftText1 != null
                if (hasLeftText) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(4.dp)
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        item.coverLeftText1?.let {
                            Text(it, color = Color.White, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                item.coverRightText?.let { text ->
                    Text(
                        text = text,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                val spaceRoute = item.args?.let { args ->
                    if (args.upId <= 0L && args.upName.isNullOrBlank()) {
                        null
                    } else {
                        SpaceRoute(
                            mid = args.upId,
                            name = args.upName,
                            fromViewAid = args.aid.takeIf { it > 0L }
                                ?: (item.route as? VideoRoute.Ugc)?.aid?.takeIf { it > 0L }
                        )
                    }
                }
                Text(
                    text = item.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val upNameClickModifier = if (spaceRoute == null) {
                        Modifier
                    } else {
                        Modifier.clickable { onOpenSpace(spaceRoute) }
                    }
                    val upName = item.descButton?.text ?: item.args?.upName ?: ""
                    if (upName.isNotEmpty()) {
                        Text(
                            text = upName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .then(upNameClickModifier)
                        )
                    }

                    val threePoint = item.threePointV2
                    if (!threePoint.isNullOrEmpty()) {
                        MoreMenu(threePoint)
                    }
                }

                item.rcmdReason?.let { reason ->
                    if (reason.text.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = reason.text,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.secondaryContainer,
                                    MaterialTheme.shapes.extraSmall
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

@Composable
private fun MoreMenu(items: List<ThreePointItem>) {
    var show by remember { mutableStateOf(false) }
    IconButton(onClick = { show = true }) {
        Icon(Icons.Default.MoreVert, contentDescription = null)
    }
    if (show) {
        AlertDialog(
            onDismissRequest = { show = false },
            confirmButton = {
                TextButton(onClick = { show = false }) { Text("取消") }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    items.forEachIndexed { index, item ->
                        if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        TextButton(
                            onClick = { show = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(item.title, style = MaterialTheme.typography.bodyLarge)
                        }
                        val options = item.reasons.orEmpty() + item.feedbacks.orEmpty()
                        if (options.isNotEmpty()) {
                            options.chunked(2).forEach { pair ->
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    pair.forEach { reason ->
                                        TextButton(
                                            onClick = { show = false },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                reason.name,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    }
                                    if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        )
    }
}
