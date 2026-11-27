package com.naaammme.bbspace.navigation

import androidx.compose.runtime.Stable
import androidx.navigation.NavHostController
import com.naaammme.bbspace.core.model.LiveRoute
import com.naaammme.bbspace.core.model.SpaceRoute
import com.naaammme.bbspace.core.model.StreamPlaybackTarget
import com.naaammme.bbspace.core.model.VideoTarget
import com.naaammme.bbspace.core.model.WebLinkParser
import com.naaammme.bbspace.core.model.WebLinkTarget
import com.naaammme.bbspace.core.model.listen.ListenItem
import com.naaammme.bbspace.core.navigation.ContentNavigator
import com.naaammme.bbspace.feature.dynamic.navigation.navigateToDynamicDetail
import com.naaammme.bbspace.feature.listen.navigation.navigateToListenDetail
import com.naaammme.bbspace.feature.live.LiveViewModel
import com.naaammme.bbspace.feature.space.navigation.navigateToSpace
import com.naaammme.bbspace.feature.video.VideoViewModel
import com.naaammme.bbspace.feature.webview.navigation.navigateToWebView
import com.naaammme.bbspace.playback.PlaybackHostMode
import com.naaammme.bbspace.playback.PlaybackHostViewModel

@Stable
internal class AppContentNavigator(
    private val navController: NavHostController,
    private val playbackHost: PlaybackHostViewModel,
    private val video: VideoViewModel,
    private val live: LiveViewModel,
    private val dismissPlayback: () -> Unit
) : ContentNavigator {
    override fun openVideo(target: VideoTarget) {
        if (playbackHost.hostMode == PlaybackHostMode.Expanded &&
            playbackHost.currentTarget.value is StreamPlaybackTarget.Video
        ) {
            video.openTarget(target)
        } else {
            video.openRoot(target)
        }
        playbackHost.expand()
    }

    override fun openLive(route: LiveRoute) {
        live.openRoute(route)
        playbackHost.openLive(route)
        playbackHost.expand()
    }

    override fun openSpace(route: SpaceRoute) {
        dismissPlayback()
        navController.navigateToSpace(route)
    }

    override fun openDynamic(opusId: String, opusType: Int) {
        dismissPlayback()
        navController.navigateToDynamicDetail(opusId, opusType)
    }

    override fun openWeb(url: String) {
        when (val target = WebLinkParser.parse(url)) {
            is WebLinkTarget.ToVideo -> openVideo(target.target)
            is WebLinkTarget.ToLive -> openLive(LiveRoute(roomId = target.roomId))
            is WebLinkTarget.ToSpace -> openSpace(SpaceRoute(mid = target.mid))
            WebLinkTarget.External, WebLinkTarget.Stay -> {
                dismissPlayback()
                navController.navigateToWebView(url)
            }
        }
    }

    override fun openListen(item: ListenItem) {
        playbackHost.close()
        navController.navigateToListenDetail(
            item.oid, item.itemType, item.subId, item.title, item.author, item.cover
        )
    }
}
