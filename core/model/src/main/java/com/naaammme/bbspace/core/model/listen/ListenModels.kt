package com.naaammme.bbspace.core.model.listen

import androidx.compose.runtime.Immutable

data class ListenItem(
    val itemType: Int,
    val oid: Long,
    val subId: Long,
    val title: String,
    val cover: String,
    val author: String,
    val authorMid: Long,
    val duration: Long,
    val statView: Int,
    val statReply: Int,
    val message: String
)

data class ListenRcmdResult(
    val items: List<ListenItem>,
    val historyLen: Long,
    val hasMore: Boolean,
    val nextPageToken: String
)

data class ListenPlayInfo(
    val playable: Boolean,
    val audioUrl: String?,
    val durationMs: Long
)

@Immutable
data class ListenPlaybackState(
    val isLoading: Boolean = false,
    val isPreparing: Boolean = false,
    val audioUrl: String? = null,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val isPlaying: Boolean = false,
    val errorMessage: String? = null
)
