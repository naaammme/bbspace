package com.naaammme.bbspace.feature.video.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.naaammme.bbspace.feature.video.VideoScreen

const val VIDEO_ROUTE = "video/{aid}/{cid}"

fun NavController.navigateToVideo(aid: Long, cid: Long) {
    navigate("video/$aid/$cid")
}

fun NavGraphBuilder.videoScreen(onBack: () -> Unit) {
    composable(
        route = VIDEO_ROUTE,
        arguments = listOf(
            navArgument("aid") { type = NavType.LongType },
            navArgument("cid") { type = NavType.LongType }
        )
    ) {
        VideoScreen(onBack = onBack)
    }
}
