package com.naaammme.bbspace.feature.im.conversation

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.naaammme.bbspace.core.designsystem.component.AvatarImage
import com.naaammme.bbspace.core.designsystem.component.BiliAsyncImage
import com.naaammme.bbspace.core.designsystem.component.BiliImageVariant
import com.naaammme.bbspace.core.designsystem.component.CoverImage
import com.naaammme.bbspace.core.designsystem.component.copyTextOnLongPress
import com.naaammme.bbspace.core.model.ImMessage
import com.naaammme.bbspace.core.model.ImMsgType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImConversationScreen(
    onBack: () -> Unit,
    onOpenSpace: ((Long) -> Unit)? = null,
    onOpenVideo: ((Long) -> Unit)? = null,
    vm: ImConversationViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val messageItems = remember(state.messages) { state.messages.toConversationMessageItems() }
    val shouldLoadMore by remember(
        state.hasMoreHistory,
        state.isLoadingMore,
        messageItems.size,
        listState
    ) {
        androidx.compose.runtime.derivedStateOf {
            state.hasMoreHistory &&
                messageItems.isNotEmpty() &&
                !state.isLoadingMore &&
                listState.isScrollInProgress &&
                (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) >=
                    listState.layoutInfo.totalItemsCount - 3
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) vm.loadMore()
    }

    LaunchedEffect(state.lastSentMessageKey) {
        if (state.lastSentMessageKey != null) {
            listState.scrollToItem(0)
        }
    }

    CompositionLocalProvider(
        LocalOnOpenSpace provides onOpenSpace,
        LocalOnOpenVideo provides onOpenVideo
    ) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.title.ifBlank { "会话" }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        },
        bottomBar = {
            ImConversationComposer(
                errorMessage = state.sendErrorMessage,
                onClearError = vm::clearSendError,
                onSend = { vm.sendMessage(it) }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                state.isLoading && messageItems.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize())
                }

                messageItems.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "暂无消息",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        reverseLayout = true
                    ) {
                        items(
                            items = messageItems,
                            key = { it.message.key },
                            contentType = { it.contentType }
                        ) { item ->
                            ImMessageBubble(
                                item = item,
                                avatar = state.avatar,
                                title = state.title
                            )
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun ImMessageBubble(
    item: ConversationMessageItem,
    avatar: String?,
    title: String?
) {
    val message = item.message
    if (message.msgType == ImMsgType.SYSTEM_NOTICE) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            SystemNoticeContent(item = item)
        }
        return
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isSelf) Alignment.End else Alignment.Start
    ) {
        if (message.isSelf) {
            MessageContent(item = item)
        } else {
            val onOpenSpace = LocalOnOpenSpace.current
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .then(
                            if (item.showAvatar && onOpenSpace != null && message.senderUid > 0L) {
                                Modifier.clickable { onOpenSpace(message.senderUid) }
                            } else Modifier
                        )
                ) {
                    if (item.showAvatar) {
                        AvatarImage(
                            url = avatar,
                            contentDescription = title ?: "头像",
                            fallbackText = title?.take(1),
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                MessageContent(item = item)
            }
        }
    }
}

@Composable
private fun MessageContent(
    item: ConversationMessageItem
) {
    val message = item.message
    val onOpenVideo = LocalOnOpenVideo.current
    Column(
        horizontalAlignment = if (message.isSelf) Alignment.End else Alignment.Start
    ) {
        when {
            message.msgType == ImMsgType.NOTICE -> {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.widthIn(max = 220.dp)
                ) {
                    Column {
                        if (!message.noticeCoverUrl.isNullOrBlank()) {
                            CoverImage(
                                url = message.noticeCoverUrl,
                                contentDescription = message.noticeTitle ?: "通知",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                            ) {
                                MessageTimeChip(
                                    text = item.timeText,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(end = 6.dp, bottom = 4.dp)
                                )
                            }
                        }
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = message.noticeTitle ?: message.content.ifBlank { "通知" },
                                modifier = Modifier.copyTextOnLongPress(
                                    message.noticeTitle ?: message.content.ifBlank { "通知" },
                                    "消息"
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            message.noticeText?.takeIf(String::isNotBlank)?.let { notice ->
                                Text(
                                    text = notice,
                                    modifier = Modifier.copyTextOnLongPress(notice, "消息"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            message.noticeDetailText?.takeIf(String::isNotBlank)?.let { detail ->
                                Text(
                                    text = detail,
                                    modifier = Modifier.copyTextOnLongPress(detail, "消息"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            message.noticeActionText?.takeIf(String::isNotBlank)?.let { action ->
                                Text(
                                    text = action,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (message.noticeCoverUrl.isNullOrBlank()) {
                                MessageTimeChip(
                                    text = item.timeText,
                                    modifier = Modifier.align(Alignment.End)
                                )
                            }
                        }
                    }
                }
            }

            !message.shareCoverUrl.isNullOrBlank() -> {
                val clickable = onOpenVideo != null && message.shareAid > 0L
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .widthIn(max = 220.dp)
                        .then(if (clickable) Modifier.clickable { onOpenVideo(message.shareAid) } else Modifier)
                ) {
                    val bodyColor = MaterialTheme.colorScheme.onSurface
                    val timeColor = bodyColor.copy(alpha = 0.58f)
                    Column {
                        CoverImage(
                            url = message.shareCoverUrl,
                            contentDescription = message.content.ifBlank { "视频卡片" },
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                        ) {
                            MessageTimeChip(
                                text = item.timeText,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(end = 6.dp, bottom = 4.dp)
                            )
                        }
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = message.content.ifBlank { "视频卡片" },
                                modifier = Modifier.copyTextOnLongPress(
                                    message.content.ifBlank { "视频卡片" },
                                    "消息"
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = bodyColor,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${message.shareViewCount} 播放",
                                style = MaterialTheme.typography.labelSmall,
                                color = timeColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                RecallFlag(message.isRecalled)
            }

            !message.imageUrl.isNullOrBlank() -> {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .widthIn(max = 220.dp)
                ) {
                    Box(modifier = Modifier.aspectRatio(item.imageRatio)) {
                        BiliAsyncImage(
                            url = message.imageUrl,
                            contentDescription = "图片消息",
                            modifier = Modifier.fillMaxSize(),
                            variant = BiliImageVariant.PreviewThumb,
                            contentScale = ContentScale.Fit
                        )
                        MessageTimeChip(
                            text = item.timeText,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 6.dp, bottom = 4.dp)
                        )
                    }
                }
                RecallFlag(message.isRecalled)
            }

            else -> {
                val surfaceColor = if (message.isSelf) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                }
                val bodyColor = if (message.isSelf) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
                val timeColor = bodyColor.copy(alpha = 0.58f)
                Surface(
                    color = surfaceColor,
                    shape = MaterialTheme.shapes.medium
                ) {
                    val timeFontSize = MaterialTheme.typography.labelSmall.fontSize
                    val text = remember(item.displayText, item.timeText, timeColor, timeFontSize) {
                        buildAnnotatedString {
                            append(item.displayText)
                            append(" ")
                            withStyle(
                                SpanStyle(
                                    color = timeColor,
                                    fontSize = timeFontSize
                                )
                            ) {
                                append(item.timeText)
                            }
                        }
                    }
                    Text(
                        text = text,
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .copyTextOnLongPress(item.displayText, "消息"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = bodyColor
                    )
                }
                RecallFlag(message.isRecalled)
            }
        }
    }
}

@Composable
private fun MessageTimeChip(
    text: String,
    modifier: Modifier = Modifier
) {
    if (text.isEmpty()) return
    val chipBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
    Text(
        text = text,
        modifier = modifier
            .background(
                color = chipBg,
                shape = MaterialTheme.shapes.small
            )
            .padding(horizontal = 5.dp, vertical = 1.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun SystemNoticeContent(item: ConversationMessageItem) {
    val message = item.message
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = message.noticeText ?: message.content,
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 4.dp)
                .copyTextOnLongPress(message.noticeText ?: message.content, "消息"),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RecallFlag(
    isRecalled: Boolean
) {
    if (!isRecalled) return
    Text(
        text = "已撤回",
        modifier = Modifier.padding(top = 2.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Immutable
private data class ConversationMessageItem(
    val message: ImMessage,
    val showAvatar: Boolean,
    val displayText: String,
    val timeText: String,
    val imageRatio: Float,
    val contentType: String
)

private fun List<ImMessage>.toConversationMessageItems(): List<ConversationMessageItem> {
    return mapIndexed { index, message ->
        val newerMessage = getOrNull(index - 1)
        ConversationMessageItem(
            message = message,
            showAvatar = !message.isSelf && newerMessage?.senderUid != message.senderUid,
            displayText = message.displayText(),
            timeText = formatMessageTime(message.timestampSec),
            imageRatio = message.imageRatio(),
            contentType = when {
                message.msgType == ImMsgType.SYSTEM_NOTICE -> CONTENT_TYPE_SYSTEM_NOTICE
                message.msgType == ImMsgType.NOTICE -> CONTENT_TYPE_NOTICE
                !message.shareCoverUrl.isNullOrBlank() -> CONTENT_TYPE_SHARE
                !message.imageUrl.isNullOrBlank() -> CONTENT_TYPE_IMAGE
                else -> CONTENT_TYPE_TEXT
            }
        )
    }
}

private fun ImMessage.displayText(): String {
    content.takeIf(String::isNotBlank)?.let { return it }
    return when (msgType) {
        ImMsgType.IMAGE -> "[图片]"
        in ImMsgType.SHARE_TYPES -> "[分享]"
        else -> "[暂不支持的消息类型 $msgType]"
    }
}

private fun ImMessage.imageRatio(): Float {
    if (imageWidth <= 0 || imageHeight <= 0) return 1f
    return (imageWidth.toFloat() / imageHeight).coerceIn(0.45f, 1.8f)
}

private fun formatMessageTime(timestampSec: Long): String {
    if (timestampSec <= 0L) return ""
    return MESSAGE_TIME_FORMAT.format(
        Instant.ofEpochSecond(timestampSec).atZone(ZoneId.systemDefault())
    )
}

private val MESSAGE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private val LocalOnOpenSpace = compositionLocalOf<((Long) -> Unit)?> { null }
private val LocalOnOpenVideo = compositionLocalOf<((Long) -> Unit)?> { null }

private const val CONTENT_TYPE_SYSTEM_NOTICE = "system_notice"
private const val CONTENT_TYPE_NOTICE = "notice"
private const val CONTENT_TYPE_SHARE = "share"
private const val CONTENT_TYPE_TEXT = "text"
private const val CONTENT_TYPE_IMAGE = "image"

