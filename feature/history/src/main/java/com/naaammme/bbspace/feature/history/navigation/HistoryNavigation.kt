package com.naaammme.bbspace.feature.history.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.naaammme.bbspace.core.model.LiveRoute
import com.naaammme.bbspace.core.model.VideoTarget
import com.naaammme.bbspace.feature.history.HistoryScreen
import com.naaammme.bbspace.feature.history.watchlater.WatchLaterScreen

const val HISTORY_ROUTE = "history"
const val WATCH_LATER_ROUTE = "watch_later"

fun NavController.navigateToHistory() {
    navigate(HISTORY_ROUTE)
}

fun NavController.navigateToWatchLater() {
    navigate(WATCH_LATER_ROUTE)
}

fun NavGraphBuilder.historyScreen(
    onBack: () -> Unit,
    onOpenVideo: (VideoTarget) -> Unit,
    onOpenLive: (LiveRoute) -> Unit,
    onOpenDynamicDetail: (String, Int) -> Unit
) {
    composable(HISTORY_ROUTE) {
        HistoryScreen(
            onBack = onBack,
            onOpenVideo = onOpenVideo,
            onOpenLive = onOpenLive,
            onOpenDynamicDetail = onOpenDynamicDetail
        )
    }
}

fun NavGraphBuilder.watchLaterScreen(
    onBack: () -> Unit,
    onOpenVideo: (VideoTarget) -> Unit
) {
    composable(WATCH_LATER_ROUTE) {
        WatchLaterScreen(
            onBack = onBack,
            onOpenVideo = onOpenVideo
        )
    }
}
