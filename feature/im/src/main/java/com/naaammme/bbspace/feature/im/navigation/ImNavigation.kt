package com.naaammme.bbspace.feature.im.navigation

import android.net.Uri
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.naaammme.bbspace.core.model.ImSessionItem
import com.naaammme.bbspace.feature.im.conversation.ImConversationScreen

const val IM_ROUTE = "im"

private const val TALKER_ID_ARG = "talkerId"
private const val SESSION_TYPE_ARG = "sessionType"
private const val TITLE_ARG = "title"
private const val AVATAR_ARG = "avatar"
private const val IM_CONVERSATION_ROUTE =
    "im/conversation/{$TALKER_ID_ARG}/{$SESSION_TYPE_ARG}?$TITLE_ARG={$TITLE_ARG}&$AVATAR_ARG={$AVATAR_ARG}"

fun NavController.navigateToImConversation(
    item: ImSessionItem
) {
    val talkerId = item.talkerId ?: return
    val sessionType = item.sessionType ?: return
    navigate(
        "im/conversation/$talkerId/$sessionType?title=${Uri.encode(item.name)}&avatar=${Uri.encode(item.avatar.orEmpty())}"
    )
}

fun NavGraphBuilder.imConversationScreen(
    onBack: () -> Unit,
    onOpenSpace: ((Long) -> Unit)? = null,
    onOpenVideo: ((Long) -> Unit)? = null
) {
    composable(
        route = IM_CONVERSATION_ROUTE,
        arguments = listOf(
            navArgument(TALKER_ID_ARG) { type = NavType.LongType },
            navArgument(SESSION_TYPE_ARG) { type = NavType.IntType },
            navArgument(TITLE_ARG) {
                type = NavType.StringType
                defaultValue = ""
            },
            navArgument(AVATAR_ARG) {
                type = NavType.StringType
                defaultValue = ""
            }
        )
    ) {
        ImConversationScreen(
            onBack = onBack,
            onOpenSpace = onOpenSpace,
            onOpenVideo = onOpenVideo
        )
    }
}
