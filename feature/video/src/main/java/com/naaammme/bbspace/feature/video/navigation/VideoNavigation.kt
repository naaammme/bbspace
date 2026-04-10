package com.naaammme.bbspace.feature.video.navigation

import android.net.Uri
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.naaammme.bbspace.core.model.PlayBiz
import com.naaammme.bbspace.core.model.VideoJump
import com.naaammme.bbspace.core.model.VideoJumpTool
import com.naaammme.bbspace.feature.video.VideoScreen

private const val AID_ARG = "aid"
private const val CID_ARG = "cid"
private const val BVID_ARG = "bvid"
private const val BIZ_ARG = "biz"
private const val SEASON_ID_ARG = "seasonId"
private const val EP_ID_ARG = "epId"
private const val TYPE_ARG = "type"
private const val PLAY_TYPE_ARG = "playType"
private const val SUB_TYPE_ARG = "subType"
private const val FROM_ARG = "from"
private const val FROM_SPMID_ARG = "fromSpmid"
private const val TRACK_ID_ARG = "trackId"
private const val REPORT_ARG = "report"

const val VIDEO_ROUTE =
    "video/{$AID_ARG}/{$CID_ARG}" +
            "?$BVID_ARG={$BVID_ARG}" +
            "&$BIZ_ARG={$BIZ_ARG}" +
            "&$SEASON_ID_ARG={$SEASON_ID_ARG}" +
            "&$EP_ID_ARG={$EP_ID_ARG}" +
            "&$TYPE_ARG={$TYPE_ARG}" +
            "&$PLAY_TYPE_ARG={$PLAY_TYPE_ARG}" +
            "&$SUB_TYPE_ARG={$SUB_TYPE_ARG}" +
            "&$FROM_ARG={$FROM_ARG}" +
            "&$FROM_SPMID_ARG={$FROM_SPMID_ARG}" +
            "&$TRACK_ID_ARG={$TRACK_ID_ARG}" +
            "&$REPORT_ARG={$REPORT_ARG}"

fun NavController.navigateToVideo(jump: VideoJump) {
    val bvid = Uri.encode(jump.bvid.orEmpty())
    val fromSpmid = Uri.encode(jump.src.fromSpmid)
    val trackId = Uri.encode(jump.src.trackId.orEmpty())
    val report = Uri.encode(jump.src.reportFlowData.orEmpty())
    navigate(
        "video/${jump.aid}/${jump.cid}" +
                "?$BVID_ARG=$bvid" +
                "&$BIZ_ARG=${jump.biz.name}" +
                "&$SEASON_ID_ARG=${jump.seasonId ?: -1L}" +
                "&$EP_ID_ARG=${jump.epId ?: -1L}" +
                "&$TYPE_ARG=${jump.type ?: -1}" +
                "&$PLAY_TYPE_ARG=${jump.playType ?: -1}" +
                "&$SUB_TYPE_ARG=${jump.subType ?: -1}" +
                "&$FROM_ARG=${jump.src.from}" +
                "&$FROM_SPMID_ARG=$fromSpmid" +
                "&$TRACK_ID_ARG=$trackId" +
                "&$REPORT_ARG=$report"
    )
}

fun NavGraphBuilder.videoScreen(
    onBack: () -> Unit,
    onOpenVideo: (VideoJump) -> Unit
) {
    composable(
        route = VIDEO_ROUTE,
        arguments = listOf(
            navArgument(AID_ARG) { type = NavType.LongType },
            navArgument(CID_ARG) { type = NavType.LongType },
            navArgument(BVID_ARG) {
                type = NavType.StringType
                defaultValue = ""
            },
            navArgument(BIZ_ARG) {
                type = NavType.StringType
                defaultValue = PlayBiz.UGC.name
            },
            navArgument(SEASON_ID_ARG) {
                type = NavType.LongType
                defaultValue = -1L
            },
            navArgument(EP_ID_ARG) {
                type = NavType.LongType
                defaultValue = -1L
            },
            navArgument(TYPE_ARG) {
                type = NavType.IntType
                defaultValue = -1
            },
            navArgument(PLAY_TYPE_ARG) {
                type = NavType.IntType
                defaultValue = -1
            },
            navArgument(SUB_TYPE_ARG) {
                type = NavType.IntType
                defaultValue = -1
            },
            navArgument(FROM_ARG) {
                type = NavType.StringType
                defaultValue = VideoJumpTool.FROM_FEED
            },
            navArgument(FROM_SPMID_ARG) {
                type = NavType.StringType
                defaultValue = VideoJumpTool.FROM_SPMID_FEED
            },
            navArgument(TRACK_ID_ARG) {
                type = NavType.StringType
                defaultValue = ""
            },
            navArgument(REPORT_ARG) {
                type = NavType.StringType
                defaultValue = ""
            }
        )
    ) {
        VideoScreen(
            onBack = onBack,
            onOpenVideo = onOpenVideo
        )
    }
}
