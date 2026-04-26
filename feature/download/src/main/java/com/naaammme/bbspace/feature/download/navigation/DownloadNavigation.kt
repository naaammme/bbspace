package com.naaammme.bbspace.feature.download.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.naaammme.bbspace.feature.download.DownloadPlayerScreen
import com.naaammme.bbspace.feature.download.DownloadScreen
import com.naaammme.bbspace.feature.download.model.DownloadViewModel

private const val DOWNLOAD_ROUTE = "download"
private const val TASK_ID_ARG = "taskId"
private const val DOWNLOAD_PLAYER_ROUTE = "download/player/{$TASK_ID_ARG}"

fun NavController.navigateToDownload() {
    navigate(DOWNLOAD_ROUTE)
}

fun NavController.navigateToDownloadPlayer(taskId: Long) {
    navigate("download/player/$taskId")
}

fun NavGraphBuilder.downloadScreen(
    navController: NavController,
    onBack: () -> Unit,
    viewModel: DownloadViewModel
) {
    composable(DOWNLOAD_ROUTE) {
        DownloadScreen(
            onBack = onBack,
            onOpenPlayer = { taskId -> navController.navigateToDownloadPlayer(taskId) },
            viewModel = viewModel
        )
    }

    composable(
        route = DOWNLOAD_PLAYER_ROUTE,
        arguments = listOf(
            navArgument(TASK_ID_ARG) { type = NavType.LongType }
        )
    ) {
        DownloadPlayerScreen(onBack = onBack)
    }
}
