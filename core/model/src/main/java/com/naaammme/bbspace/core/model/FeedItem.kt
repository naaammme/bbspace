package com.naaammme.bbspace.core.model

import androidx.compose.runtime.Immutable

@Immutable
data class FeedItem(
    val cardType: String,
    val cardGoto: String,
    val goto: String,
    val param: String,
    val uri: String,
    val title: String,
    val cover: String,
    val coverLeftText1: String?,
    val coverLeftText2: String?,
    val coverRightText: String?,
    val idx: Long,
    val trackId: String?,
    val descButton: DescButton?,
    val rcmdReason: RcmdReason?,
    val playerArgs: FeedPlayerArgs?,
    val args: FeedArgs?,
    val threePointV2: List<ThreePointItem>?
)

@Immutable
data class DescButton(
    val text: String,
    val uri: String
)

@Immutable
data class RcmdReason(
    val text: String,
    val textColor: String?,
    val bgColor: String?,
    val textColorNight: String?,
    val bgColorNight: String?
)

@Immutable
data class FeedPlayerArgs(
    val aid: Long,
    val cid: Long,
    val duration: Int,
    val type: String?
)

@Immutable
data class FeedArgs(
    val upId: Long,
    val upName: String?,
    val tid: Int,
    val tname: String?,
    val aid: Long
)

@Immutable
data class ThreePointItem(
    val title: String,
    val subtitle: String?,
    val type: String,
    val reasons: List<ThreePointReason>?,
    val feedbacks: List<ThreePointReason>?
)

@Immutable
data class ThreePointReason(
    val id: Int,
    val name: String,
    val toast: String
)

data class FeedToast(
    val hasToast: Boolean,
    val message: String
)
