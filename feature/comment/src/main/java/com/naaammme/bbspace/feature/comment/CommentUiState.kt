package com.naaammme.bbspace.feature.comment

import com.naaammme.bbspace.core.model.COMMENT_FILTER_ALL
import com.naaammme.bbspace.core.model.CommentFilterTag
import com.naaammme.bbspace.core.model.CommentReply
import com.naaammme.bbspace.core.model.CommentSort
import com.naaammme.bbspace.core.model.CommentSubject

data class CommentUiState(
    val subject: CommentSubject? = null,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val loadMoreError: String? = null,
    val title: String = "评论",
    val count: Long = 0L,
    val sort: CommentSort = CommentSort.HOT,
    val canSwitchSort: Boolean = true,
    val filterTags: List<CommentFilterTag> = emptyList(),
    val selectedFilter: String = COMMENT_FILTER_ALL,
    val items: List<CommentReply> = emptyList(),
    val nextOffset: String? = null,
    val hasMore: Boolean = false,
    val endText: String? = null
)
