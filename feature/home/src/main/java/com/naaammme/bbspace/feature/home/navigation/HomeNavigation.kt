package com.naaammme.bbspace.feature.home.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.naaammme.bbspace.feature.home.ui.HomeScreen

const val HOME_ROUTE = "home"

fun NavGraphBuilder.homeScreen(
    navController: NavHostController,
    onNavigateToSettings: () -> Unit
) {
    composable(HOME_ROUTE) {
        HomeScreen(onNavigateToSettings = onNavigateToSettings)
    }
}
