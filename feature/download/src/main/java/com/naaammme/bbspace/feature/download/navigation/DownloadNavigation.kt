package com.naaammme.bbspace.feature.download.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.naaammme.bbspace.feature.download.DownloadScreen
import com.naaammme.bbspace.feature.download.model.DownloadViewModel

private const val DOWNLOAD_ROUTE = "download"

fun NavController.navigateToDownload() {
    navigate(DOWNLOAD_ROUTE)
}

fun NavGraphBuilder.downloadScreen(
    onBack: () -> Unit,
    viewModel: DownloadViewModel
) {
    composable(DOWNLOAD_ROUTE) {
        DownloadScreen(
            onBack = onBack,
            viewModel = viewModel
        )
    }
}
