package com.naaammme.bbspace.core.model

import androidx.compose.runtime.Immutable

@Immutable
data class DynamicCursor(
    val historyOffset: String = "",
    val updateBaseline: String = "",
    val assistBaseline: String = "0",
    val page: Int = 1
)

enum class DynamicRefresh {
    NEW,
    HISTORY
}

@Immutable
data class DynamicPage(
    val items: List<DynamicItem>,
    val upList: DynamicUpList? = null,
    val cursor: DynamicCursor,
    val hasMore: Boolean,
    val updateNum: Long
)

@Immutable
data class DynamicUpList(
    val title: String?,
    val items: List<DynamicUpItem>
)

@Immutable
data class DynamicUpItem(
    val uid: Long,
    val name: String,
    val face: String?,
    val hasUpdate: Boolean,
    val trackId: String?
)

@Immutable
data class DynamicItem(
    val id: String,
    val type: String,
    val author: DynamicAuthor?,
    val body: DynamicBody,
    val stats: DynamicStats?,
    val publishedText: String?,
    val desc: String?,
    val title: String?,
    val cover: String?,
    val badge: String?,
    val videoTarget: VideoTarget?,
    val liveRoute: LiveRoute?,
    val spaceRoute: SpaceRoute?,
    val trackId: String?,
    val reportFlowData: String?,
    val canOpen: Boolean
)

@Immutable
data class DynamicAuthor(
    val mid: Long,
    val name: String,
    val avatar: String?,
    val pubAction: String?,
    val pubLocation: String?
)

@Immutable
data class DynamicStats(
    val repost: Long,
    val reply: Long,
    val like: Long
)

sealed interface DynamicBody {
    @Immutable
    data class Text(
        val text: String
    ) : DynamicBody

    @Immutable
    data class Draw(
        val text: String?,
        val images: List<DynamicImage>
    ) : DynamicBody

    @Immutable
    data class Archive(
        val text: String?,
        val title: String,
        val cover: String?,
        val subTitle: String?,
        val badge: String?
    ) : DynamicBody

    @Immutable
    data class Article(
        val text: String?,
        val title: String,
        val cover: String?,
        val subTitle: String?
    ) : DynamicBody

    @Immutable
    data class Live(
        val text: String?,
        val title: String,
        val cover: String?,
        val subTitle: String?,
        val badge: String?
    ) : DynamicBody

    @Immutable
    data class Forward(
        val text: String?,
        val origin: DynamicForwardItem?
    ) : DynamicBody

    @Immutable
    data class Unknown(
        val text: String?
    ) : DynamicBody
}

@Immutable
data class DynamicForwardItem(
    val authorName: String?,
    val bodyText: String?,
    val title: String?,
    val cover: String?,
    val badge: String?
)

@Immutable
data class DynamicImage(
    val url: String,
    val width: Int,
    val height: Int
)

@Immutable
data class DynamicDetail(
    val author: DynamicDetailAuthor,
    val paragraphs: List<DynamicDetailParagraph>,
    val stats: DynamicStats?,
    val replyBizId: Long = 0L,
    val replyBizType: Long = 0L,
    val hasMore: Boolean = false
)

@Immutable
data class DynamicDetailAuthor(
    val mid: Long,
    val name: String,
    val face: String?,
    val pubTime: String?
)

@Immutable
data class DynamicDetailParagraph(
    val type: String,
    val text: String?,
    val images: List<DynamicImage>
) {
    companion object {
        const val TYPE_TEXT = "TEXT"
        const val TYPE_PICTURES = "PICTURES"
    }
}
