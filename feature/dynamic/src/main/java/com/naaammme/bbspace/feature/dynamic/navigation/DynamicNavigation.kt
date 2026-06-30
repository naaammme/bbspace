package com.naaammme.bbspace.feature.dynamic.navigation

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.compose.ui.unit.dp
import com.naaammme.bbspace.core.model.SpaceRoute
import com.naaammme.bbspace.feature.dynamic.detail.DynamicDetailScreen

const val DYNAMIC_ROUTE = "dynamic"

const val DYNAMIC_DETAIL_OPUS_ID_ARG = "opusId"
const val DYNAMIC_DETAIL_OPUS_TYPE_ARG = "opusType"
private const val DYNAMIC_DETAIL_ROUTE = "dynamic_detail/{${DYNAMIC_DETAIL_OPUS_ID_ARG}}/{${DYNAMIC_DETAIL_OPUS_TYPE_ARG}}"

fun NavController.navigateToDynamicDetail(opusId: String, opusType: Int = 0) {
    navigate("dynamic_detail/$opusId/$opusType")
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
            },
            navArgument(DYNAMIC_DETAIL_OPUS_TYPE_ARG) {
                type = NavType.IntType
                defaultValue = 0
            }
        )
    ) {
        val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
        val isExpanded = windowSizeClass.isWidthAtLeastBreakpoint(840)
        DynamicDetailScreen(
            onBack = onBack,
            onOpenSpace = onOpenSpace,
            isExpanded = isExpanded
        )
    }
}
