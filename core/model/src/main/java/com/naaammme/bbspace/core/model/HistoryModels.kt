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
    data class Video(val route: VideoRoute) : HistoryTarget

    @Immutable
    data class Live(val route: LiveRoute) : HistoryTarget
}
