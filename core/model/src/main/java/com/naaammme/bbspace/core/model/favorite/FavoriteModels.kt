package com.naaammme.bbspace.core.model

import androidx.compose.runtime.Immutable

@Immutable
data class FavoritePage(
    val folders: List<FavoriteFolder>
)

@Immutable
data class FavoriteFolder(
    val id: Long,
    val fid: Long,
    val title: String,
    val cover: String?,
    val attrDesc: String?,
    val mediaCount: Int,
    val createdAtSec: Long,
    val isTop: Boolean
)

@Immutable
data class FavoriteContentCursor(
    val startOid: Long = 0L,
    val startOtype: Int = 0,
    val startScore: Long = 0L
)

@Immutable
data class FavoriteContentPage(
    val items: List<FavoriteContentItem>,
    val cursor: FavoriteContentCursor,
    val hasMore: Boolean
)

@Immutable
data class FavoriteContentItem(
    val key: String,
    val oid: Long,
    val otype: Int,
    val title: String,
    val cover: String?,
    val ownerName: String?,
    val viewText: String?,
    val danmakuText: String?,
    val playbackDesc: String?,
    val typeDesc: String?,
    val isInvalid: Boolean,
    val target: FavoriteContentTarget?
) {
    val isOpenable: Boolean
        get() = target != null
}

sealed interface FavoriteContentTarget {
    @Immutable
    data class Video(val target: VideoTarget) : FavoriteContentTarget

    @Immutable
    data class DynamicDetail(val opusId: String) : FavoriteContentTarget
}
