package com.naaammme.bbspace.feature.home.video

import android.widget.Toast
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.naaammme.bbspace.core.designsystem.component.AdaptiveMediaGrid
import com.naaammme.bbspace.core.designsystem.component.CoverImage
import com.naaammme.bbspace.core.designsystem.component.VideoGridCardSkeleton
import com.naaammme.bbspace.core.model.FeedItem
import com.naaammme.bbspace.core.model.LiveRoute
import com.naaammme.bbspace.core.model.SpaceRoute
import com.naaammme.bbspace.core.model.ThreePointItem
import com.naaammme.bbspace.core.model.ThreePointReason
import com.naaammme.bbspace.core.model.VideoTarget

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeVideoPage(
    items: List<FeedItem>,
    isRefreshing: Boolean,
    isLoadingMore: Boolean,
    errorMessage: String?,
    toastMessage: String,
    dislikedReasons: Map<String, String>,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenVideo: (VideoTarget) -> Unit,
    onOpenSpace: (SpaceRoute) -> Unit,
    onOpenLive: (LiveRoute) -> Unit,
    onDislike: (FeedItem, ThreePointReason) -> Unit,
    onCancelDislike: (FeedItem) -> Unit,
    onToastShown: () -> Unit
) {
    val context = LocalContext.current
    val gridState = rememberLazyStaggeredGridState()
    var wasRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(toastMessage, context) {
        if (toastMessage.isNotEmpty()) {
            Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
            onToastShown()
        }
    }
    LaunchedEffect(isRefreshing, items) {
        if (wasRefreshing && !isRefreshing && items.isNotEmpty()) {
            gridState.scrollToItem(0)
        }
        wasRefreshing = isRefreshing
    }
    AdaptiveMediaGrid(
        items = items,
        isRefreshing = isRefreshing,
        isLoadingMore = isLoadingMore,
        onRefresh = onRefresh,
        onLoadMore = onLoadMore,
        modifier = Modifier.fillMaxSize(),
        state = gridState,
        errorMessage = errorMessage,
        key = { _, item -> item.actionKey() },
        contentType = { _, item -> item.cardType },
        loadingContent = {
            VideoGridCardSkeleton()
        }
    ) { item ->
        FeedCard(
            item = item,
            onOpenSpace = onOpenSpace,
            dislikedReason = dislikedReasons[item.actionKey()],
            onDislike = onDislike,
            onCancelDislike = onCancelDislike,
            onClick = {
                item.liveRoute?.let(onOpenLive)
                    ?: item.target?.let(onOpenVideo)
            }
        )
    }
}

@Composable
private fun FeedCard(
    item: FeedItem,
    onOpenSpace: (SpaceRoute) -> Unit,
    dislikedReason: String?,
    onDislike: (FeedItem, ThreePointReason) -> Unit,
    onCancelDislike: (FeedItem) -> Unit,
    onClick: () -> Unit
) {
    val isDisliked = dislikedReason != null
    val canOpen = !isDisliked && (item.target != null || item.liveRoute != null)
    Card(
        onClick = onClick,
        enabled = canOpen,
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
                if (dislikedReason != null) {
                    DislikedOverlay(
                        reason = dislikedReason,
                        onUndo = { onCancelDislike(item) },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
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
            }
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                val spaceRoute = remember(item.args, item.target) {
                    item.args?.let { args ->
                        if (args.upId <= 0L && args.upName.isNullOrBlank()) {
                            null
                        } else {
                            SpaceRoute(
                                mid = args.upId,
                                name = args.upName,
                                fromViewAid = args.aid.takeIf { it > 0L }
                                    ?: (item.target as? VideoTarget.Ugc)?.aid?.takeIf { it > 0L }
                            )
                        }
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
                    val upName = item.descButton?.text ?: item.args?.upName ?: ""
                    if (upName.isNotEmpty()) {
                        Box(modifier = Modifier.weight(1f)) {
                            Text(
                                text = upName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = if (spaceRoute == null || isDisliked) {
                                    Modifier
                                } else {
                                    Modifier.clickable { onOpenSpace(spaceRoute) }
                                }
                            )
                        }
                    }

                    val threePoint = item.threePointV2
                    if (!isDisliked && !threePoint.isNullOrEmpty()) {
                        MoreMenu(
                            item = item,
                            items = threePoint,
                            onDislike = onDislike
                        )
                    }
                }

                item.rcmdReason?.let { reason ->
                    if (reason.text.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        val rcmdBgColor = MaterialTheme.colorScheme.secondaryContainer
                        val rcmdBgShape = MaterialTheme.shapes.extraSmall
                        Text(
                            text = reason.text,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier
                                .background(rcmdBgColor, rcmdBgShape)
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
private fun MoreMenu(
    item: FeedItem,
    items: List<ThreePointItem>,
    onDislike: (FeedItem, ThreePointReason) -> Unit
) {
    var show by remember { mutableStateOf(false) }
    IconButton(
        onClick = { show = true },
        modifier = Modifier.size(24.dp)
    ) {
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
                    items.forEachIndexed { index, menuItem ->
                        if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        TextButton(
                            onClick = { show = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(menuItem.title, style = MaterialTheme.typography.bodyLarge)
                        }
                        val options = menuItem.reasons.orEmpty() + menuItem.feedbacks.orEmpty()
                        if (options.isNotEmpty()) {
                            options.chunked(2).forEach { pair ->
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    pair.forEach { reason ->
                                        TextButton(
                                            onClick = {
                                                show = false
                                                onDislike(item, reason)
                                            },
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

@Composable
private fun DislikedOverlay(
    reason: String,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = reason,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(10.dp))
            TextButton(onClick = onUndo) {
                Text("撤回")
            }
        }
    }
}

private fun FeedItem.actionKey(): String {
    return "$goto|$param|$idx"
}
