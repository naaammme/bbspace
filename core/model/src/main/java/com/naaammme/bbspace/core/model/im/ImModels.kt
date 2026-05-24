package com.naaammme.bbspace.core.model

import androidx.compose.runtime.Immutable

enum class ImSessionTab(
    val title: String
) {
    DEFAULT("全部"),
    FOLLOW("关注"),
    STRANGER("陌生人")
}

@Immutable
data class ImPaginationOffset(
    val normalOffset: Long,
    val topOffset: Long
)

@Immutable
data class ImPaginationParams(
    val offsets: Map<Int, ImPaginationOffset> = emptyMap(),
    val hasMore: Boolean = false
)

@Immutable
data class ImPage(
    val tabs: List<ImSessionTab>,
    val currentTab: ImSessionTab,
    val paginationParams: ImPaginationParams? = null,
    val sessions: List<ImSessionItem>
)

@Immutable
data class ImSessionItem(
    val key: String,
    val talkerId: Long?,
    val sessionType: Int?,
    val sessionTypeLabel: String? = null,
    val name: String,
    val avatar: String? = null,
    val summary: String = "",
    val unreadText: String? = null,
    val unreadCount: Long = 0,
    val timeMicros: Long = 0,
    val isPinned: Boolean = false,
    val isMuted: Boolean = false
)

@Immutable
data class ImConversationPage(
    val messages: List<ImMessage>,
    val hasMoreHistory: Boolean
)

@Immutable
object ImMsgType {
    const val TEXT = 1
    const val IMAGE = 2
    const val NOTICE = 10
    const val SYSTEM_NOTICE = 18
    val SHARE_TYPES = setOf(4, 7, 11, 12, 13, 14, 15)
}

data class ImMessage(
    val key: Long,
    val seqNo: Long,
    val senderUid: Long,
    val receiverId: Long,
    val msgType: Int,
    val content: String,
    val imageUrl: String? = null,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val timestampSec: Long,
    val isSelf: Boolean,
    val isRecalled: Boolean,
    val shareCoverUrl: String? = null,
    val shareViewCount: Long = 0L,
    val shareAid: Long = 0L,
    val noticeTitle: String? = null,
    val noticeText: String? = null,
    val noticeActionText: String? = null,
    val noticeCoverUrl: String? = null,
    val noticeDetailText: String? = null
)
