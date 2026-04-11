package com.naaammme.bbspace.core.model

import androidx.compose.runtime.Immutable

@Immutable
data class VideoDetail(
    val aid: Long,
    val bvid: String,
    val title: String,
    val cover: String? = null,
    val owner: VideoOwner? = null,
    val stat: VideoStat? = null,
    val pubTs: Long? = null,
    val tags: List<String> = emptyList(),
    val desc: String = "",
    val staffs: List<VideoStaff> = emptyList(),
    val season: VideoSeason? = null,
    val pages: List<VideoPagePart> = emptyList(),
    val relates: List<VideoRelate> = emptyList()
)

@Immutable
data class VideoOwner(
    val mid: Long,
    val name: String,
    val fansText: String?,
    val arcCountText: String?,
    val face: String?
)

@Immutable
data class VideoStat(
    val view: String,
    val danmaku: String,
    val reply: String,
    val like: String,
    val coin: String,
    val fav: String,
    val share: String
)

@Immutable
data class VideoStaff(
    val role: String,
    val name: String
)

@Immutable
data class VideoSeason(
    val title: String,
    val subTitle: String?,
    val sections: List<VideoSeasonSection>
)

@Immutable
data class VideoSeasonSection(
    val title: String,
    val eps: List<VideoSeasonEpisode>
)

@Immutable
data class VideoSeasonEpisode(
    val aid: Long,
    val cid: Long,
    val title: String,
    val subTitle: String?,
    val cover: String?
)

@Immutable
data class VideoPagePart(
    val cid: Long,
    val part: String,
    val durationSec: Long
)

@Immutable
data class VideoRelate(
    val jump: VideoJump,
    val title: String,
    val cover: String,
    val author: String?,
    val durationText: String?,
    val viewText: String?,
    val danmakuText: String?,
    val reason: String?
)
