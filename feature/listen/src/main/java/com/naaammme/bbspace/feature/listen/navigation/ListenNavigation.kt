package com.naaammme.bbspace.feature.listen.navigation

import android.net.Uri
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.naaammme.bbspace.feature.listen.detail.ListenDetailScreen

const val LISTEN_DETAIL_ROUTE = "listen_detail"

fun NavController.navigateToListenDetail(
    oid: Long,
    itemType: Int,
    subId: Long,
    title: String,
    author: String,
    cover: String
) {
    val encodedTitle = Uri.encode(title)
    val encodedAuthor = Uri.encode(author)
    val encodedCover = Uri.encode(cover)
    navigate("$LISTEN_DETAIL_ROUTE/$oid/$itemType/$subId/$encodedTitle/$encodedAuthor/$encodedCover")
}

fun NavGraphBuilder.listenDetailScreen(
    onBack: () -> Unit
) {
    composable(
        route = "$LISTEN_DETAIL_ROUTE/{oid}/{itemType}/{subId}/{title}/{author}/{cover}",
        arguments = listOf(
            navArgument("oid") { type = NavType.LongType },
            navArgument("itemType") { type = NavType.IntType },
            navArgument("subId") { type = NavType.LongType },
            navArgument("title") { type = NavType.StringType },
            navArgument("author") { type = NavType.StringType },
            navArgument("cover") { type = NavType.StringType }
        )
    ) { backStackEntry ->
        val oid = backStackEntry.arguments?.getLong("oid") ?: 0L
        val itemType = backStackEntry.arguments?.getInt("itemType") ?: 0
        val subId = backStackEntry.arguments?.getLong("subId") ?: 0L
        val title = backStackEntry.arguments?.getString("title")?.let { Uri.decode(it) } ?: ""
        val author = backStackEntry.arguments?.getString("author")?.let { Uri.decode(it) } ?: ""
        val cover = backStackEntry.arguments?.getString("cover")?.let { Uri.decode(it) } ?: ""
        ListenDetailScreen(
            oid = oid,
            itemType = itemType,
            subId = subId,
            title = title,
            author = author,
            cover = cover,
            onBack = onBack
        )
    }
}
