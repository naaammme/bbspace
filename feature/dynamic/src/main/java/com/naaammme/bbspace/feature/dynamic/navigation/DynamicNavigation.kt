package com.naaammme.bbspace.feature.dynamic.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.naaammme.bbspace.feature.dynamic.detail.DynamicDetailScreen

const val DYNAMIC_ROUTE = "dynamic"

const val DYNAMIC_DETAIL_OPUS_ID_ARG = "opusId"
private const val DYNAMIC_DETAIL_ROUTE = "dynamic_detail/{${DYNAMIC_DETAIL_OPUS_ID_ARG}}"

fun NavController.navigateToDynamicDetail(opusId: String) {
    navigate("dynamic_detail/$opusId")
}

fun NavGraphBuilder.dynamicDetailScreen(
    onBack: () -> Unit
) {
    composable(
        route = DYNAMIC_DETAIL_ROUTE,
        arguments = listOf(
            navArgument(DYNAMIC_DETAIL_OPUS_ID_ARG) {
                type = NavType.StringType
                defaultValue = ""
            }
        )
    ) {
        DynamicDetailScreen(onBack = onBack)
    }
}
