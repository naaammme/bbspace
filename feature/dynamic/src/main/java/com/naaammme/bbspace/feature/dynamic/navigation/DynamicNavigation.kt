package com.naaammme.bbspace.feature.dynamic.navigation

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.window.core.layout.WindowWidthSizeClass
import com.naaammme.bbspace.core.model.SpaceRoute
import com.naaammme.bbspace.feature.dynamic.detail.DynamicDetailScreen

const val DYNAMIC_ROUTE = "dynamic"

const val DYNAMIC_DETAIL_OPUS_ID_ARG = "opusId"
private const val DYNAMIC_DETAIL_ROUTE = "dynamic_detail/{${DYNAMIC_DETAIL_OPUS_ID_ARG}}"

fun NavController.navigateToDynamicDetail(opusId: String) {
    navigate("dynamic_detail/$opusId")
}

fun NavGraphBuilder.dynamicDetailScreen(
    onBack: () -> Unit,
    onOpenSpace: (SpaceRoute) -> Unit
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
        val widthClass = currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass
        val isExpanded = widthClass == WindowWidthSizeClass.EXPANDED
        DynamicDetailScreen(
            onBack = onBack,
            onOpenSpace = onOpenSpace,
            isExpanded = isExpanded
        )
    }
}
