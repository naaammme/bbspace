package com.naaammme.bbspace.core.navigation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import com.naaammme.bbspace.core.model.DynamicItem
import com.naaammme.bbspace.core.model.FavoriteContentTarget
import com.naaammme.bbspace.core.model.HistoryTarget
import com.naaammme.bbspace.core.model.LiveRoute
import com.naaammme.bbspace.core.model.SpaceRoute
import com.naaammme.bbspace.core.model.VideoTarget
import com.naaammme.bbspace.core.model.listen.ListenItem

@Stable
interface ContentNavigator {
    fun openVideo(target: VideoTarget)
    fun openLive(route: LiveRoute)
    fun openSpace(route: SpaceRoute)
    fun openDynamic(opusId: String, opusType: Int = 0)
    fun openWeb(url: String)
    fun openListen(item: ListenItem)
}

// 静态 Local 变更会触发整棵子树重组，实现类只保留跳转动作且不要塞入易变状态
val LocalContentNavigator = staticCompositionLocalOf<ContentNavigator> {
    error("ContentNavigator 未提供")
}

fun ContentNavigator.open(item: DynamicItem) {
    val live = item.liveRoute
    val video = item.videoTarget
    when {
        live != null -> openLive(live)
        video != null -> openVideo(video)
        else -> openDynamic(item.id)
    }
}

fun ContentNavigator.open(target: HistoryTarget) {
    when (target) {
        is HistoryTarget.Video -> openVideo(target.target)
        is HistoryTarget.Live -> openLive(target.route)
        is HistoryTarget.Article -> openDynamic(target.opusId, 1)
    }
}

fun ContentNavigator.open(target: FavoriteContentTarget) {
    when (target) {
        is FavoriteContentTarget.Video -> openVideo(target.target)
        is FavoriteContentTarget.DynamicDetail -> openDynamic(target.opusId)
    }
}
