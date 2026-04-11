package com.naaammme.bbspace.core.model

import androidx.compose.runtime.Immutable

const val COMMENT_FILTER_ALL = "全部"

@Immutable
data class CommentSource(
    val spmid: String = "",
    val fromSpmid: String? = null,
    val trackId: String? = null
)

@Immutable
data class CommentSubject(
    val oid: Long,
    val type: Long,
    val source: CommentSource = CommentSource()
)

object CommentSubjectTool {
    const val TYPE_VIDEO = 1L
    const val TYPE_DYNAMIC = 17L

    fun video(
        aid: Long,
        src: VideoSrc
    ): CommentSubject {
        return CommentSubject(
            oid = aid,
            type = TYPE_VIDEO,
            source = CommentSource(
                spmid = VideoRouteTool.SPMID,
                fromSpmid = src.fromSpmid,
                trackId = src.trackId
            )
        )
    }
}

enum class CommentSort {
    HOT,
    TIME
}

@Immutable
data class CommentFilterTag(
    val name: String
)

@Immutable
data class CommentPage(
    val subject: CommentSubject,
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

@Immutable
data class CommentReply(
    val rpid: Long,
    val message: String = "",
    val likeCount: Long = 0L,
    val replyCount: Long = 0L,
    val timeText: String = "",
    val topLabel: String? = null,
    val replyEntryText: String? = null,
    val user: CommentUser,
    val pictures: List<CommentPicture> = emptyList(),
    val replies: List<CommentReply> = emptyList()
)

@Immutable
data class CommentUser(
    val mid: Long,
    val name: String,
    val face: String? = null,
    val level: Int? = null,
    val vipLabel: String? = null,
    val medal: CommentMedal? = null
)

@Immutable
data class CommentMedal(
    val name: String,
    val level: Int = 0
)

@Immutable
data class CommentPicture(
    val url: String,
    val width: Float = 0f,
    val height: Float = 0f
)
