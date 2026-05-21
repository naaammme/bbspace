package com.naaammme.bbspace.feature.favorite.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.naaammme.bbspace.core.model.FavoriteContentTarget
import com.naaammme.bbspace.feature.favorite.FavoriteScreen

const val FAVORITE_ROUTE = "favorite"

fun NavController.navigateToFavorite() {
    navigate(FAVORITE_ROUTE)
}

fun NavGraphBuilder.favoriteScreen(
    onBack: () -> Unit,
    onOpenContent: (FavoriteContentTarget) -> Unit
) {
    composable(FAVORITE_ROUTE) {
        FavoriteScreen(
            onBack = onBack,
            onOpenContent = onOpenContent
        )
    }
}
