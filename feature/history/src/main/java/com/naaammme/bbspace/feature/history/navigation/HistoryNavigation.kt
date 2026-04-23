package com.naaammme.bbspace.feature.history.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.naaammme.bbspace.core.model.LiveRoute
import com.naaammme.bbspace.core.model.VideoRoute
import com.naaammme.bbspace.feature.history.HistoryScreen

const val HISTORY_ROUTE = "history"

fun NavController.navigateToHistory() {
    navigate(HISTORY_ROUTE)
}

fun NavGraphBuilder.historyScreen(
    onBack: () -> Unit,
    onOpenVideo: (VideoRoute) -> Unit,
    onOpenLive: (LiveRoute) -> Unit
) {
    composable(HISTORY_ROUTE) {
        HistoryScreen(
            onBack = onBack,
            onOpenVideo = onOpenVideo,
            onOpenLive = onOpenLive
        )
    }
}
