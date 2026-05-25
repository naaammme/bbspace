package com.naaammme.bbspace.core.model

import androidx.compose.runtime.Immutable

enum class LiveRoomSessionStatus {
    Idle,
    Connecting,
    Authorizing,
    Running,
    Reconnecting,
    Closed
}

@Immutable
data class LiveRoomUser(
    val uid: Long,
    val name: String,
    val avatar: String? = null,
    val nameColor: String? = null
)

@Immutable
data class LiveRoomMedal(
    val name: String,
    val level: Int,
    val colorStart: String? = null,
    val colorEnd: String? = null,
    val colorBorder: String? = null
)

@Immutable
data class LiveRoomMessage(
    val localId: Long,
    val msgId: String? = null,
    val cmd: String,
    val title: String? = null,
    val content: String,
    val user: LiveRoomUser? = null,
    val medal: LiveRoomMedal? = null,
    val mode: Int = 1,
    val fontSize: Int = 25,
    val color: Int = 0xFFFFFF,
    val sendTimeMs: Long = 0L,
    val isMirror: Boolean = false,
    val isAck: Boolean = false,
    val msgType: Int? = null,
    val extra: String? = null
)

@Immutable
data class LiveRoomPanelState(
    val watchedText: String? = null,
    val onlineRankText: String? = null,
    val rankChangedText: String? = null
)

fun LiveRoomPanelState.merge(
    next: LiveRoomPanelState
): LiveRoomPanelState {
    return copy(
        watchedText = next.watchedText ?: watchedText,
        onlineRankText = next.onlineRankText ?: onlineRankText,
        rankChangedText = next.rankChangedText ?: rankChangedText
    )
}

@Immutable
data class LiveRoomSessionState(
    val roomId: Long = 0L,
    val status: LiveRoomSessionStatus = LiveRoomSessionStatus.Idle,
    val popularCount: Long = 0L,
    val panel: LiveRoomPanelState = LiveRoomPanelState(),
    val messages: List<LiveRoomMessage> = emptyList(),
    val latestMessage: LiveRoomMessage? = null,
    val queueId: String? = null,
    val retryCount: Int = 0,
    val lastHeartbeatAtMs: Long = 0L,
    val lastConnectAtMs: Long = 0L,
    val lastError: String? = null
)
