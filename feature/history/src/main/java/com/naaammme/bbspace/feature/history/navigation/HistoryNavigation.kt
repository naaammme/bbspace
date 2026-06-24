package com.naaammme.bbspace.feature.history.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.naaammme.bbspace.core.model.HistoryTab
import com.naaammme.bbspace.core.model.HistoryTarget
import com.naaammme.bbspace.core.model.VideoTarget
import com.naaammme.bbspace.feature.history.HistoryScreen
import com.naaammme.bbspace.feature.history.search.HistorySearchScreen
import com.naaammme.bbspace.feature.history.watchlater.WatchLaterScreen

const val HISTORY_ROUTE = "history"
const val HISTORY_SEARCH_ROUTE = "history_search"
const val WATCH_LATER_ROUTE = "watch_later"
private const val HISTORY_SEARCH_TAB_ARG = "tab"

fun NavController.navigateToHistory() {
    navigate(HISTORY_ROUTE)
}

fun NavController.navigateToHistorySearch(tab: HistoryTab) {
    navigate("$HISTORY_SEARCH_ROUTE/${tab.business}") {
        launchSingleTop = true
    }
}

fun NavController.navigateToWatchLater() {
    navigate(WATCH_LATER_ROUTE)
}

fun NavGraphBuilder.historyScreen(
    onBack: () -> Unit,
    onSearch: (HistoryTab) -> Unit,
    onOpenHistoryTarget: (HistoryTarget?) -> Unit
) {
    composable(HISTORY_ROUTE) {
        HistoryScreen(
            onBack = onBack,
            onSearch = onSearch,
            onOpenHistoryTarget = onOpenHistoryTarget
        )
    }
}

fun NavGraphBuilder.historySearchScreen(
    onBack: () -> Unit,
    onOpenHistoryTarget: (HistoryTarget?) -> Unit
) {
    composable(
        route = "$HISTORY_SEARCH_ROUTE/{$HISTORY_SEARCH_TAB_ARG}",
        arguments = listOf(
            navArgument(HISTORY_SEARCH_TAB_ARG) {
                type = NavType.StringType
            }
        )
    ) {
        HistorySearchScreen(
            onBack = onBack,
            onOpenHistoryTarget = onOpenHistoryTarget
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
