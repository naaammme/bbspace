package com.naaammme.bbspace.feature.live.navigation

import android.net.Uri
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.naaammme.bbspace.core.model.LiveRoute
import com.naaammme.bbspace.feature.live.LiveScreen

private const val ROOM_ID_ARG = "roomId"
private const val TITLE_ARG = "title"
private const val COVER_ARG = "cover"
private const val OWNER_ARG = "ownerName"
private const val ONLINE_ARG = "onlineText"

private const val LIVE_ROUTE =
    "live/{$ROOM_ID_ARG}" +
            "?$TITLE_ARG={$TITLE_ARG}" +
            "&$COVER_ARG={$COVER_ARG}" +
            "&$OWNER_ARG={$OWNER_ARG}" +
            "&$ONLINE_ARG={$ONLINE_ARG}"

fun NavController.navigateToLive(route: LiveRoute) {
    navigate(
        "live/${route.roomId}" +
                "?$TITLE_ARG=${Uri.encode(route.title.orEmpty())}" +
                "&$COVER_ARG=${Uri.encode(route.cover.orEmpty())}" +
                "&$OWNER_ARG=${Uri.encode(route.ownerName.orEmpty())}" +
                "&$ONLINE_ARG=${Uri.encode(route.onlineText.orEmpty())}"
    )
}

fun NavGraphBuilder.liveScreen(
    onBack: () -> Unit
) {
    composable(
        route = LIVE_ROUTE,
        arguments = listOf(
            navArgument(ROOM_ID_ARG) { type = NavType.LongType },
            navArgument(TITLE_ARG) {
                type = NavType.StringType
                defaultValue = ""
            },
            navArgument(COVER_ARG) {
                type = NavType.StringType
                defaultValue = ""
            },
            navArgument(OWNER_ARG) {
                type = NavType.StringType
                defaultValue = ""
            },
            navArgument(ONLINE_ARG) {
                type = NavType.StringType
                defaultValue = ""
            }
        )
    ) {
        LiveScreen(onBack = onBack)
    }
}
