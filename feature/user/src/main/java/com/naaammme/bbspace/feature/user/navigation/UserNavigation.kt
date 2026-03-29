package com.naaammme.bbspace.feature.user.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.naaammme.bbspace.feature.user.UserScreen

const val USER_ROUTE = "user"

fun NavGraphBuilder.userScreen(
    onNavigateToLogin: () -> Unit,
    onNavigateToAccount: () -> Unit
) {
    composable(USER_ROUTE) {
        UserScreen(
            onNavigateToLogin = onNavigateToLogin,
            onNavigateToAccount = onNavigateToAccount
        )
    }
}
