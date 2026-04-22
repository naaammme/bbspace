package com.naaammme.bbspace.feature.comment

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naaammme.bbspace.core.common.log.Logger
import com.naaammme.bbspace.core.common.media.ImageSaver
import com.naaammme.bbspace.core.designsystem.component.CommentCardSkeleton
import com.naaammme.bbspace.core.designsystem.component.CommentHeaderSkeleton
import com.naaammme.bbspace.core.designsystem.component.PreviewImage
import com.naaammme.bbspace.core.model.CommentSubjectTool
import com.naaammme.bbspace.core.model.CommentSort
import com.naaammme.bbspace.core.model.CommentSubject
import com.naaammme.bbspace.core.model.CommentUser
import com.naaammme.bbspace.core.model.SpaceRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CommentPanel(
    subject: CommentSubject?,
    onOpenSpace: (SpaceRoute) -> Unit = {},
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    viewModel: CommentViewModel = hiltViewModel()
) {
    LaunchedEffect(subject) {
        viewModel.bind(subject)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isInitLoading = subject != null && uiState.loading && uiState.items.isEmpty()
    val context = LocalContext.current
    val appCtx = remember(context) { context.applicationContext }
    val scope = rememberCoroutineScope()
    val onSaveImage: (PreviewImage) -> Unit = { image ->
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { ImageSaver.saveUrl(appCtx, image.url) }
            }
            result
                .onSuccess {
                    Toast.makeText(context, "已保存到相册", Toast.LENGTH_SHORT).show()
                }
                .onFailure { err ->
                    Logger.e(COMMENT_TAG, err as? Exception) {
                        "save comment image failed url=${image.url}"
                    }
                    Toast.makeText(
                        context,
                        err.message ?: "保存图片失败",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }
    }
    val listState = rememberLazyListState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            uiState.threadPane == null &&
            uiState.hasMore &&
                !uiState.loading &&
                !uiState.loadingMore &&
                uiState.items.isNotEmpty() &&
                total > 0 &&
                last >= total - 3
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            viewModel.loadMore()
        }
    }

    LaunchedEffect(viewModel, context) {
        viewModel.msg.collectLatest { text ->
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }
    }

    BackHandler(enabled = uiState.threadPane != null) {
        viewModel.closeReplyThread()
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(
                key = "comment_header",
                contentType = "header"
            ) {
                if (isInitLoading) {
                    CommentHeaderSkeleton()
                } else {
                    CommentHeader(
                        state = uiState,
                        onToggleSort = {
                            val next = if (uiState.sort == CommentSort.HOT) {
                                CommentSort.TIME
                            } else {
                                CommentSort.HOT
                            }
                            viewModel.selectSort(next)
                        }
                    )
                }
            }

            when {
                subject == null -> {
                    item(
                        key = "comment_empty_subject",
                        contentType = "state"
                    ) {
                        StateCard(text = "暂无评论信息")
                    }
                }

                isInitLoading -> {
                    items(
                        count = INIT_SKELETON_COUNT,
                        key = { index -> "comment_skeleton_$index" },
                        contentType = { "reply_skeleton" }
                    ) {
                        CommentCardSkeleton()
                    }
                }

                !uiState.error.isNullOrBlank() && uiState.items.isEmpty() -> {
                    item(
                        key = "comment_error",
                        contentType = "state"
                    ) {
                        RetryCard(
                            text = uiState.error.orEmpty(),
                            button = "重试",
                            onRetry = viewModel::retry
                        )
                    }
                }

                uiState.items.isEmpty() -> {
                    item(
                        key = "comment_no_data",
                        contentType = "state"
                    ) {
                        StateCard(text = "还没有评论")
                    }
                }

                else -> {
                    items(
                        items = uiState.items,
                        key = { it.rpid },
                        contentType = { "reply" }
                    ) { reply ->
                        CommentCard(
                            reply = reply,
                            isLoading = { rpid -> rpid in uiState.loadingReplyIds },
                            onTranslate = viewModel::translateReply,
                            onSaveImage = onSaveImage,
                            onOpenReplies = viewModel::openReplyThread,
                            onOpenUser = { user ->
                                user.toSpaceRoute(subject)?.let(onOpenSpace)
                            }
                        )
                    }
                }
            }

            if (uiState.loadingMore) {
                items(
                    count = LOAD_MORE_SKELETON_COUNT,
                    key = { index -> "comment_loading_more_$index" },
                    contentType = { "reply_skeleton" }
                ) {
                    CommentCardSkeleton()
                }
            } else if (!uiState.loadMoreError.isNullOrBlank()) {
                item(
                    key = "comment_load_more_error",
                    contentType = "footer"
                ) {
                    RetryCard(
                        text = uiState.loadMoreError.orEmpty(),
                        button = "重试",
                        onRetry = viewModel::loadMore
                    )
                }
            } else if (!uiState.hasMore && uiState.items.isNotEmpty()) {
                item(
                    key = "comment_end",
                    contentType = "footer"
                ) {
                    StateCard(
                        text = uiState.endText
                            ?: if (uiState.sort == CommentSort.HOT) {
                                "热门评论已展示完"
                            } else {
                                "没有更多评论"
                            }
                    )
                }
            }
        }
        // 评论详情过渡动画
        AnimatedContent(
            targetState = uiState.threadPane,
            contentKey = { it != null },
            transitionSpec = {
                (slideInHorizontally { fullWidth -> fullWidth } + fadeIn())
                    .togetherWith(slideOutHorizontally { fullWidth -> fullWidth } + fadeOut())
            },
            label = "comment_thread_pane"
        ) { threadPane ->
            if (threadPane != null) {
                CommentThreadPane(
                    state = threadPane,
                    isLoading = { rpid -> rpid in uiState.loadingReplyIds },
                    onSaveImage = onSaveImage,
                    onDismiss = viewModel::closeReplyThread,
                    onToggleSort = viewModel::toggleReplyThreadSort,
                    onTranslate = viewModel::translateReply,
                    onLoadMore = viewModel::loadMoreReplyThread,
                    onRetry = viewModel::retryReplyThread,
                    onOpenUser = { user ->
                        user.toSpaceRoute(subject)?.let(onOpenSpace)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun CommentHeader(
    state: CommentUiState,
    onToggleSort: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = headerCount(state.count),
            style = MaterialTheme.typography.titleSmall
        )
        if (state.canSwitchSort) {
            SortSwitch(
                sort = state.sort,
                onClick = onToggleSort
            )
        } else {
            Text(
                text = sortText(state.sort),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SortSwitch(
    sort: CommentSort,
    onClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = sortText(sort),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

private fun sortText(sort: CommentSort): String {
    return when (sort) {
        CommentSort.HOT -> "热门"
        CommentSort.TIME -> "时间"
    }
}

private fun headerCount(count: Long): String {
    return if (count > 0L) {
        "${count.formatCount()} 条评论"
    } else {
        "暂无评论"
    }
}

@Composable
internal fun StateCard(text: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
internal fun RetryCard(
    text: String,
    button: String,
    onRetry: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
            TextButton(onClick = onRetry) {
                Text(button)
            }
        }
    }
}

private const val INIT_SKELETON_COUNT = 4
private const val LOAD_MORE_SKELETON_COUNT = 2
private const val COMMENT_TAG = "CommentPanel"

private fun CommentUser.toSpaceRoute(subject: CommentSubject?): SpaceRoute? {
    if (mid <= 0L && name.isBlank()) return null
    return SpaceRoute(
        mid = mid,
        name = name.takeIf(String::isNotBlank),
        fromViewAid = subject
            ?.takeIf { it.type == CommentSubjectTool.TYPE_VIDEO }
            ?.oid
            ?.takeIf { it > 0L }
    )
}
