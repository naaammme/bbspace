package com.naaammme.bbspace.feature.live.component

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.foundation.background
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naaammme.bbspace.core.designsystem.component.SearchCapsuleField
import com.naaammme.bbspace.core.designsystem.component.StateMessageCard
import com.naaammme.bbspace.core.model.LivePlaybackViewState
import com.naaammme.bbspace.core.model.LiveRoomMessage
import com.naaammme.bbspace.core.model.LiveRoomSessionState
import com.naaammme.bbspace.core.model.LiveRoute
import com.naaammme.bbspace.feature.live.toUiMessage
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch

@Composable
internal fun LiveDetailPane(
    route: LiveRoute?,
    playbackState: LivePlaybackViewState,
    roomSessionState: StateFlow<LiveRoomSessionState>,
    onSendDanmaku: suspend (String) -> Unit,
    showHeader: Boolean,
    modifier: Modifier = Modifier,
    horizontalPad: Dp = 16.dp
) {
    Column(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = horizontalPad, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LiveDetailMessageList(
            route = route,
            playbackState = playbackState,
            roomSessionState = roomSessionState,
            showHeader = showHeader,
            modifier = Modifier.weight(1f)
        )

        key(route?.roomId) {
            LiveDanmakuInputBar(
                enabled = route?.roomId?.let { it > 0L } == true,
                onSendDanmaku = onSendDanmaku
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.liveMessageItems(
    messages: List<LiveRoomMessage>,
    timeFmt: DateFormat
) {
    items(
        items = messages,
        key = { it.msgId ?: "live_msg_${it.localId}" },
        contentType = { "live_message" }
    ) { msg ->
        val timeText = remember(msg.sendTimeMs) {
            if (msg.sendTimeMs <= 0L) {
                "--:--"
            } else {
                timeFmt.format(Date(msg.sendTimeMs))
            }
        }
        LiveMessageCard(
            message = msg,
            timeText = timeText
        )
    }
}

private const val AUTO_SCROLL_COALESCE_MS = 80L

@Composable
private fun LiveMetaSection(
    route: LiveRoute?,
    playbackState: LivePlaybackViewState,
    lastError: String?
) {
    val tags = remember(route?.roomId, route?.onlineText) {
        listOfNotNull(
            route?.roomId?.takeIf { it > 0L }?.let { "房间 $it" },
            route?.onlineText?.takeIf(String::isNotBlank)
        )
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (tags.isNotEmpty()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tags.forEach { text ->
                    MetaTag(text)
                }
            }
        }

        playbackState.error?.let { error ->
            Text(
                text = error.toUiMessage(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }

        playbackState.playerError?.let { error ->
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }

        lastError?.takeIf(String::isNotBlank)?.let { err ->
            Text(
                text = err,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun LiveMessageCard(
    message: LiveRoomMessage,
    timeText: String
) {
    val user = message.user
    val headText = remember(
        message.title,
        user?.name,
        message.medal?.name,
        message.medal?.level
    ) {
        listOfNotNull(
            message.title,
            user?.name?.takeIf(String::isNotBlank),
            message.medal?.let { "${it.name} ${it.level}" }
        ).joinToString(" · ").ifBlank { "匿名用户" }
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = headText,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun MetaTag(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun LiveDetailMessageList(
    route: LiveRoute?,
    playbackState: LivePlaybackViewState,
    roomSessionState: StateFlow<LiveRoomSessionState>,
    showHeader: Boolean,
    modifier: Modifier = Modifier
) {
    val roomSession by roomSessionState.collectAsStateWithLifecycle()
    val timeFmt = remember {
        DateFormat.getTimeInstance(DateFormat.SHORT)
    }
    val listState = rememberLazyListState()
    val messages = roomSession.messages
    var followNew by remember { mutableStateOf(true) }
    val currentMessages by rememberUpdatedState(messages)

    LaunchedEffect(listState) {
        snapshotFlow { currentMessages.lastOrNull()?.localId }
            .conflate()
            .collect { id ->
                if (id != null && followNew) {
                    delay(AUTO_SCROLL_COALESCE_MS)
                    if (!followNew || listState.isScrollInProgress) return@collect
                    val total = listState.layoutInfo.totalItemsCount
                    if (total > 0) {
                        listState.scrollToItem(total - 1)
                    }
                }
            }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { scrolling ->
                if (!scrolling) {
                    val layout = listState.layoutInfo
                    val lastVisibleIdx = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
                    followNew = lastVisibleIdx >= layout.totalItemsCount - 1
                }
            }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (showHeader) {
            item("title") {
                Text(
                    text = route?.title ?: "直播间 ${route?.roomId ?: 0L}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            route?.ownerName?.let { ownerName ->
                item("owner") {
                    Text(
                        text = ownerName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item("meta") {
            LiveMetaSection(
                route = route,
                playbackState = playbackState,
                lastError = roomSession.lastError
            )
        }

        if (messages.isEmpty() && playbackState.playbackSource != null && playbackState.error == null) {
            item("empty_msg") {
                StateMessageCard(text = "暂时还没有收到弹幕")
            }
        } else if (messages.isNotEmpty()) {
            liveMessageItems(
                messages = messages,
                timeFmt = timeFmt
            )
        }
    }
}

@Composable
private fun LiveDanmakuInputBar(
    enabled: Boolean,
    onSendDanmaku: suspend (String) -> Unit
) {
    val maxLen = 40
    val keyboard = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    var input by rememberSaveable { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(imeVisible) {
        if (!imeVisible) {
            focusManager.clearFocus(force = true)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            focusManager.clearFocus(force = true)
            keyboard?.hide()
        }
    }

    fun send() {
        val draft = input.trim()
        if (!enabled || sending || draft.isEmpty()) return
        scope.launch {
            sending = true
            error = null
            try {
                onSendDanmaku(draft)
                input = ""
                focusManager.clearFocus(force = true)
                keyboard?.hide()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                error = e.message ?: "发送弹幕失败"
            } finally {
                sending = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.medium
            )
            .padding(horizontal = 10.dp, vertical = 0.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SearchCapsuleField(
                value = input,
                onValueChange = {
                    input = it.take(maxLen)
                    if (error != null) {
                        error = null
                    }
                },
                placeholder = "发个弹幕",
                modifier = Modifier.weight(1f),
                showClearAction = false,
                containerColor = Color.Transparent,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() })
            )
            IconButton(
                onClick = ::send,
                enabled = enabled && !sending && input.isNotBlank()
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = if (sending) "发送中" else "发送弹幕"
                )
            }
        }

        error?.takeIf(String::isNotBlank)?.let { err ->
            Text(
                text = err,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
