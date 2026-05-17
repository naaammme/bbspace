package com.naaammme.bbspace.core.model

import androidx.compose.runtime.Immutable

enum class HistoryTab(
    val business: String,
    val title: String
) {
    ALL("all", "全部"),
    ARCHIVE("archive", "视频"),
    LIVE("live", "直播"),
    ARTICLE("article", "专栏"),
    NONFINISH("nonfinish", "未看完")
}

@Immutable
data class HistoryCursor(
    val max: Long = 0L,
    val maxTp: Int = 0
)

@Immutable
data class HistoryPage(
    val items: List<HistoryItem>,
    val cursor: HistoryCursor,
    val hasMore: Boolean
)

@Immutable
data class HistoryItem(
    val key: String,
    val type: String,
    val typeLabel: String,
    val title: String,
    val cover: String?,
    val ownerName: String?,
    val badge: String?,
    val subtitle: String?,
    val deviceLabel: String?,
    val viewedAtSec: Long,
    val progressSec: Long?,
    val durationSec: Long?,
    val target: HistoryTarget?
) {
    val isOpenable: Boolean
        get() = target != null
}

sealed interface HistoryTarget {
    @Immutable
    data class Video(val target: VideoTarget) : HistoryTarget

    @Immutable
    data class Live(val route: LiveRoute) : HistoryTarget

    @Immutable
    data class Article(val opusId: String) : HistoryTarget
}

enum class WatchLaterTab(
    val sortField: Int,
    val title: String
) {
    UNFINISHED(10, "未看完"),
    ALL(1, "全部")
}

@Immutable
data class WatchLaterCursor(
    val startKey: String = "",
    val splitKey: String = ""
)

@Immutable
data class WatchLaterPage(
    val items: List<WatchLaterItem>,
    val cursor: WatchLaterCursor,
    val hasMore: Boolean,
    val countText: String?
)

@Immutable
data class WatchLaterItem(
    val key: String,
    val cardType: Int,
    val title: String,
    val intro: String?,
    val cover: String?,
    val ownerName: String?,
    val viewText: String?,
    val danmakuText: String?,
    val durationSec: Long?,
    val progressSec: Long?,
    val addedAtSec: Long,
    val badge: String?,
    val target: VideoTarget?
) {
    val isOpenable: Boolean
        get() = target != null
}
