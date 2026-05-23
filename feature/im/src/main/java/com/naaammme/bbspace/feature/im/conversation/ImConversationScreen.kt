package com.naaammme.bbspace.feature.im.conversation

import androidx.compose.foundation.background
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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
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
import com.naaammme.bbspace.core.model.ImMessage
import com.naaammme.bbspace.core.model.ImMsgType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImConversationScreen(
    onBack: () -> Unit,
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
        derivedStateOf {
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

@Composable
private fun ImMessageBubble(
    item: ConversationMessageItem,
    avatar: String?,
    title: String?
) {
    val message = item.message
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isSelf) Alignment.End else Alignment.Start
    ) {
        if (message.isSelf) {
            MessageContent(item = item)
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Box(modifier = Modifier.size(32.dp)) {
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
private fun MessageContent(item: ConversationMessageItem) {
    val message = item.message
    Column(
        horizontalAlignment = if (message.isSelf) Alignment.End else Alignment.Start
    ) {
        if (!message.imageUrl.isNullOrBlank()) {
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
        } else {
            Surface(
                color = if (message.isSelf) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
                shape = MaterialTheme.shapes.medium
            ) {
                val bodyColor = if (message.isSelf) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
                val timeColor = bodyColor.copy(alpha = 0.58f)
                val timeFontSize = MaterialTheme.typography.labelSmall.fontSize
                val text = remember(item.displayText, item.timeText, timeColor, timeFontSize) {
                    if (item.timeText.isEmpty()) {
                        buildAnnotatedString { append(item.displayText) }
                    } else {
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
                }
                Text(
                    text = text,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = bodyColor
                )
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
            contentType = if (message.imageUrl.isNullOrBlank()) CONTENT_TYPE_TEXT else CONTENT_TYPE_IMAGE
        )
    }
}

private fun ImMessage.displayText(): String {
    if (isRecalled) return "消息已撤回"
    return content.ifBlank {
        when (msgType) {
            ImMsgType.IMAGE -> "[图片]"
            in ImMsgType.SHARE_TYPES -> "[分享]"
            else -> "[暂不支持的消息类型 $msgType]"
        }
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

private const val CONTENT_TYPE_TEXT = "text"
private const val CONTENT_TYPE_IMAGE = "image"

