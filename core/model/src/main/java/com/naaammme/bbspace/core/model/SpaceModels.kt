package com.naaammme.bbspace.core.model

import androidx.compose.runtime.Immutable

@Immutable
data class SpaceRoute(
    val mid: Long = 0L,
    val name: String? = null,
    val from: Int = SpaceRouteTool.FROM_DEFAULT,
    val fromViewAid: Long? = null
)

object SpaceRouteTool {
    const val FROM_DEFAULT = 0
}

@Immutable
data class SpaceHome(
    val profile: SpaceProfile,
    val bannerUrl: String?,
    val videos: List<SpaceVideo>,
    val orders: List<SpaceOrderOption>,
    val defaultOrder: String,
    val hasMore: Boolean
)

@Immutable
data class SpaceProfile(
    val mid: Long,
    val name: String,
    val face: String?,
    val sign: String,
    val level: Int,
    val fansCount: Long,
    val followingCount: Long,
    val likeCount: Long,
    val videoCount: Int,
    val articleCount: Int,
    val seasonCount: Int,
    val seriesCount: Int,
    val tags: List<String>
)

@Immutable
data class SpaceVideo(
    val aid: Long,
    val cid: Long,
    val route: VideoRoute.Ugc,
    val title: String,
    val cover: String,
    val author: String?,
    val categoryName: String?,
    val durationSec: Long,
    val viewText: String,
    val danmakuText: String?,
    val publishTimeText: String?
)

@Immutable
data class SpaceOrderOption(
    val title: String,
    val value: String
)

@Immutable
data class SpaceArchivePage(
    val videos: List<SpaceVideo>,
    val orders: List<SpaceOrderOption>,
    val hasMore: Boolean
)
