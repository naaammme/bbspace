package com.naaammme.bbspace.feature.search.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.naaammme.bbspace.core.model.VideoRoute
import com.naaammme.bbspace.feature.search.SearchScreen

const val SEARCH_ROUTE = "search"

fun NavController.navigateToSearch() {
    navigate(SEARCH_ROUTE)
}

fun NavGraphBuilder.searchScreen(
    onBack: () -> Unit,
    onOpenVideo: (VideoRoute) -> Unit
) {
    composable(SEARCH_ROUTE) {
        SearchScreen(
            onBack = onBack,
            onOpenVideo = onOpenVideo
        )
    }
}
