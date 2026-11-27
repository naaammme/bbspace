package com.naaammme.bbspace.feature.webview.navigation

import android.net.Uri
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.naaammme.bbspace.feature.webview.WebViewScreen

const val WEBVIEW_ROUTE = "webview"
private const val URL_ARG = "url"

fun NavController.navigateToWebView(url: String) {
    val encoded = Uri.encode(url)
    navigate("$WEBVIEW_ROUTE?$URL_ARG=$encoded")
}

fun NavGraphBuilder.webViewScreen(
    onBack: () -> Unit,
    onOpenExternal: (String) -> Unit
) {
    composable(
        route = "$WEBVIEW_ROUTE?$URL_ARG={$URL_ARG}",
        arguments = listOf(
            navArgument(URL_ARG) { type = NavType.StringType }
        )
    ) { backStackEntry ->
        val url = backStackEntry.arguments?.getString(URL_ARG).orEmpty()
        WebViewScreen(
            url = url,
            onBack = onBack,
            onOpenExternal = onOpenExternal
        )
    }
}
