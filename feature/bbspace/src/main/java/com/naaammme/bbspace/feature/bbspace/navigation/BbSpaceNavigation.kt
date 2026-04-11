package com.naaammme.bbspace.feature.bbspace.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.naaammme.bbspace.feature.bbspace.BbSpaceScreen

const val BBSPACE_ROUTE = "bbspace"

fun NavController.navigateToBbSpace() {
    navigate(BBSPACE_ROUTE)
}

fun NavGraphBuilder.bbSpaceScreen(navController: NavHostController) {
    composable(BBSPACE_ROUTE) {
        BbSpaceScreen(onBack = { navController.popBackStack() })
    }
}
