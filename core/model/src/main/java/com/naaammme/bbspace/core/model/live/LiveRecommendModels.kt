package com.naaammme.bbspace.core.model

import androidx.compose.runtime.Immutable

@Immutable
data class LiveRecommendItem(
    val roomId: Long,
    val title: String,
    val cover: String,
    val ownerMid: Long?,
    val ownerName: String?,
    val areaName: String?,
    val onlineText: String?,
    val sessionId: String?,
    val route: LiveRoute
)

@Immutable
data class LiveRecommendPage(
    val upList: LiveRecommendUpList? = null,
    val items: List<LiveRecommendItem>,
    val hasMore: Boolean,
    val needRefresh: Boolean,
    val triggerTimeSec: Int
)

@Immutable
data class LiveRecommendUpList(
    val title: String?,
    val items: List<LiveRecommendUpItem>
)

@Immutable
data class LiveRecommendUpItem(
    val uid: Long,
    val name: String,
    val face: String?,
    val route: LiveRoute
)
