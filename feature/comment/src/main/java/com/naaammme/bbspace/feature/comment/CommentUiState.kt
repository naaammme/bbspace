package com.naaammme.bbspace.feature.comment

import androidx.compose.runtime.Immutable
import com.naaammme.bbspace.core.model.COMMENT_FILTER_ALL
import com.naaammme.bbspace.core.model.CommentFilterTag
import com.naaammme.bbspace.core.model.CommentReply
import com.naaammme.bbspace.core.model.CommentSort
import com.naaammme.bbspace.core.model.CommentSubject
import com.naaammme.bbspace.feature.comment.thread.CommentThreadState

@Immutable
data class CommentUiState(
    val subject: CommentSubject? = null,
    val currentMid: Long = 0L,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val busyReplyIds: Set<Long> = emptySet(),
    val error: String? = null,
    val loadMoreError: String? = null,
    val title: String = "评论",
    val count: Long = 0L,
    val sort: CommentSort = CommentSort.HOT,
    val canSwitchSort: Boolean = true,
    val filterTags: List<CommentFilterTag> = emptyList(),
    val selectedFilter: String = COMMENT_FILTER_ALL,
    val items: List<CommentReply> = emptyList(),
    val threadPane: CommentThreadState? = null,
    val replyCheckDialogText: String? = null,
    val nextOffset: String? = null,
    val endText: String? = null
) {
    val hasMore: Boolean
        get() = nextOffset != null
}
