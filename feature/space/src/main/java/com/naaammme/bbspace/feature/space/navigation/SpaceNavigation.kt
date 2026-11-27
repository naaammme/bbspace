package com.naaammme.bbspace.feature.space.navigation

import android.net.Uri
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.naaammme.bbspace.core.model.SpaceRoute
import com.naaammme.bbspace.core.model.SpaceRouteTool
import com.naaammme.bbspace.feature.space.SpaceScreen
import com.naaammme.bbspace.feature.space.search.SpaceSearchScreen

const val SPACE_MID_ARG = "mid"
const val SPACE_NAME_ARG = "name"
const val SPACE_FROM_ARG = "from"
const val SPACE_FROM_VIEW_AID_ARG = "fromViewAid"

private const val SPACE_ROUTE =
    "space/{$SPACE_MID_ARG}" +
            "?$SPACE_NAME_ARG={$SPACE_NAME_ARG}" +
            "&$SPACE_FROM_ARG={$SPACE_FROM_ARG}" +
            "&$SPACE_FROM_VIEW_AID_ARG={$SPACE_FROM_VIEW_AID_ARG}"

fun NavController.navigateToSpace(route: SpaceRoute) {
    navigate(
        "space/${route.mid}" +
                "?$SPACE_NAME_ARG=${Uri.encode(route.name.orEmpty())}" +
                "&$SPACE_FROM_ARG=${route.from}" +
                "&$SPACE_FROM_VIEW_AID_ARG=${route.fromViewAid ?: -1L}"
    )
}

fun NavController.navigateToSpaceSearch(mid: Long) {
    navigate("space_search/$mid") {
        launchSingleTop = true
    }
}

fun NavGraphBuilder.spaceScreen(
    onBack: () -> Unit,
    onOpenIm: ((Long, String, String?) -> Unit)? = null,
    onOpenRelation: (Long, Int) -> Unit = { _, _ -> },
    onOpenSearch: (Long) -> Unit = {}
) {
    composable(
        route = SPACE_ROUTE,
        arguments = listOf(
            navArgument(SPACE_MID_ARG) {
                type = NavType.LongType
                defaultValue = 0L
            },
            navArgument(SPACE_NAME_ARG) {
                type = NavType.StringType
                defaultValue = ""
            },
            navArgument(SPACE_FROM_ARG) {
                type = NavType.IntType
                defaultValue = SpaceRouteTool.FROM_DEFAULT
            },
            navArgument(SPACE_FROM_VIEW_AID_ARG) {
                type = NavType.LongType
                defaultValue = -1L
            }
        )
    ) {
        SpaceScreen(
            onBack = onBack,
            onOpenIm = onOpenIm,
            onOpenRelation = onOpenRelation,
            onOpenSearch = onOpenSearch
        )
    }
}

fun NavGraphBuilder.spaceSearchScreen(
    onBack: () -> Unit
) {
    composable(
        route = "space_search/{$SPACE_MID_ARG}",
        arguments = listOf(
            navArgument(SPACE_MID_ARG) {
                type = NavType.LongType
                defaultValue = 0L
            }
        )
    ) {
        SpaceSearchScreen(
            onBack = onBack
        )
    }
}

fun NavController.navigateToSpaceRelation(vmid: Long, initialTab: Int) {
    navigate("space_relation/$vmid?initialTab=$initialTab")
}

fun NavGraphBuilder.spaceRelationScreen(
    onBack: () -> Unit
) {
    composable(
        route = "space_relation/{vmid}?initialTab={initialTab}",
        arguments = listOf(
            navArgument("vmid") {
                type = NavType.LongType
                defaultValue = 0L
            },
            navArgument("initialTab") {
                type = NavType.IntType
                defaultValue = 0
            }
        )
    ) {
        com.naaammme.bbspace.feature.space.relation.RelationScreen(
            onBack = onBack
        )
    }
}
