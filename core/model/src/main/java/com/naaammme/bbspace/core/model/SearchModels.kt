package com.naaammme.bbspace.core.model

import androidx.compose.runtime.Immutable

enum class SearchOrder {
    DEFAULT,
    VIEW,
    PUBDATE,
    DANMAKU;

    companion object {
        fun fromParam(param: String?): SearchOrder {
            return when (param) {
                "click" -> VIEW
                "pubdate" -> PUBDATE
                "dm" -> DANMAKU
                else -> DEFAULT
            }
        }
    }
}

@Immutable
data class SearchVideo(
    val aid: Long,
    val cid: Long,
    val route: VideoRoute.Ugc,
    val title: String,
    val cover: String,
    val author: String,
    val duration: String,
    val viewText: String,
    val danmakuText: String,
    val reason: String?,
    val feedbacks: List<SearchFeedbackSec>
)

@Immutable
data class SearchFeedbackSec(
    val title: String,
    val type: String,
    val items: List<SearchFeedbackItem>
)

@Immutable
data class SearchFeedbackItem(
    val id: Int,
    val text: String
)

@Immutable
data class SearchOp(
    val label: String,
    val param: String,
    val isDefault: Boolean
)

@Immutable
data class SearchFilter(
    val key: String,
    val title: String,
    val ops: List<SearchOp>,
    val single: Boolean
)

@Immutable
data class SearchTime(
    val beginS: Long = 0L,
    val endS: Long = 0L
) {
    val isActive: Boolean
        get() = beginS > 0L && endS >= beginS
}

data class SearchReq(
    val keyword: String,
    val next: String = "",
    val order: SearchOrder = SearchOrder.DEFAULT,
    val filterMap: Map<String, String> = emptyMap(),
    val time: SearchTime = SearchTime()
)

@Immutable
data class SearchPage(
    val keyword: String,
    val videos: List<SearchVideo>,
    val next: String,
    val filters: List<SearchFilter>
)
