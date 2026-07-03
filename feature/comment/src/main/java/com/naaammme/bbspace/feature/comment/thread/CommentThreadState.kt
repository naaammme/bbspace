package com.naaammme.bbspace.feature.comment.thread

import androidx.compose.runtime.Immutable
import com.naaammme.bbspace.core.model.CommentReply
import com.naaammme.bbspace.core.model.CommentSort

@Immutable
data class CommentThreadState(
    val title: String = "回复详情",
    val rootRpid: Long,
    val root: CommentReply,
    val count: Long = 0L,
    val sort: CommentSort = CommentSort.HOT,
    val canSwitchSort: Boolean = true,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val loadMoreError: String? = null,
    val items: List<CommentReply> = emptyList(),
    val nextOffset: String? = null,
    val highlightRpid: Long = 0L
) {
    val hasMore: Boolean
        get() = nextOffset != null
}
