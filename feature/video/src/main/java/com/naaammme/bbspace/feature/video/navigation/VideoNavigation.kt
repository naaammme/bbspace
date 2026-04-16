package com.naaammme.bbspace.feature.video.navigation

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.naaammme.bbspace.core.model.PlayBiz
import com.naaammme.bbspace.core.model.VideoRoute
import com.naaammme.bbspace.core.model.VideoRouteTool
import com.naaammme.bbspace.feature.video.VideoScreen

private const val AID_ARG = "aid"
private const val CID_ARG = "cid"
private const val BVID_ARG = "bvid"
private const val BIZ_ARG = "biz"
private const val SEASON_ID_ARG = "seasonId"
private const val EP_ID_ARG = "epId"
private const val SUB_TYPE_ARG = "subType"
private const val FROM_ARG = "from"
private const val FROM_SPMID_ARG = "fromSpmid"
private const val TRACK_ID_ARG = "trackId"
private const val REPORT_ARG = "report"

private const val UGC_VIDEO_ROUTE =
    "video/ugc/{$AID_ARG}/{$CID_ARG}" +
            "?$BIZ_ARG={$BIZ_ARG}" +
            "&$BVID_ARG={$BVID_ARG}" +
            "&$FROM_ARG={$FROM_ARG}" +
            "&$FROM_SPMID_ARG={$FROM_SPMID_ARG}" +
            "&$TRACK_ID_ARG={$TRACK_ID_ARG}" +
            "&$REPORT_ARG={$REPORT_ARG}"

private const val PGC_VIDEO_ROUTE =
    "video/pgc/{$EP_ID_ARG}" +
            "?$BIZ_ARG={$BIZ_ARG}" +
            "&$SEASON_ID_ARG={$SEASON_ID_ARG}" +
            "&$SUB_TYPE_ARG={$SUB_TYPE_ARG}" +
            "&$FROM_ARG={$FROM_ARG}" +
            "&$FROM_SPMID_ARG={$FROM_SPMID_ARG}" +
            "&$TRACK_ID_ARG={$TRACK_ID_ARG}" +
            "&$REPORT_ARG={$REPORT_ARG}"

private const val PUGV_VIDEO_ROUTE =
    "video/pugv/{$EP_ID_ARG}" +
            "?$BIZ_ARG={$BIZ_ARG}" +
            "&$SEASON_ID_ARG={$SEASON_ID_ARG}" +
            "&$FROM_ARG={$FROM_ARG}" +
            "&$FROM_SPMID_ARG={$FROM_SPMID_ARG}" +
            "&$TRACK_ID_ARG={$TRACK_ID_ARG}" +
            "&$REPORT_ARG={$REPORT_ARG}"

private const val VIDEO_ROUTE_PREFIX = "video/"

fun isVideoRoutePattern(route: String?): Boolean {
    return route
        ?.substringBefore('?')
        ?.startsWith(VIDEO_ROUTE_PREFIX) == true
}

fun NavController.navigateToVideo(route: VideoRoute) {
    when (route) {
        is VideoRoute.Ugc -> navigateToUgcVideo(route)
        is VideoRoute.Pgc -> navigateToPgcVideo(route)
        is VideoRoute.Pugv -> navigateToPugvVideo(route)
    }
}

private fun NavController.navigateToUgcVideo(route: VideoRoute.Ugc) {
    val bvid = Uri.encode(route.bvid.orEmpty())
    val fromSpmid = Uri.encode(route.src.fromSpmid)
    val trackId = Uri.encode(route.src.trackId.orEmpty())
    val report = Uri.encode(route.src.reportFlowData.orEmpty())
    navigate(
        "video/ugc/${route.aid}/${route.cid}" +
                "?$BIZ_ARG=${PlayBiz.UGC.name}" +
                "&$BVID_ARG=$bvid" +
                "&$FROM_ARG=${route.src.from}" +
                "&$FROM_SPMID_ARG=$fromSpmid" +
                "&$TRACK_ID_ARG=$trackId" +
                "&$REPORT_ARG=$report"
    )
}

private fun NavController.navigateToPgcVideo(route: VideoRoute.Pgc) {
    val fromSpmid = Uri.encode(route.src.fromSpmid)
    val trackId = Uri.encode(route.src.trackId.orEmpty())
    val report = Uri.encode(route.src.reportFlowData.orEmpty())
    navigate(
        "video/pgc/${route.epId}" +
                "?$BIZ_ARG=${PlayBiz.PGC.name}" +
                "&$SEASON_ID_ARG=${route.seasonId ?: -1L}" +
                "&$SUB_TYPE_ARG=${route.subType ?: -1}" +
                "&$FROM_ARG=${route.src.from}" +
                "&$FROM_SPMID_ARG=$fromSpmid" +
                "&$TRACK_ID_ARG=$trackId" +
                "&$REPORT_ARG=$report"
    )
}

private fun NavController.navigateToPugvVideo(route: VideoRoute.Pugv) {
    val fromSpmid = Uri.encode(route.src.fromSpmid)
    val trackId = Uri.encode(route.src.trackId.orEmpty())
    val report = Uri.encode(route.src.reportFlowData.orEmpty())
    navigate(
        "video/pugv/${route.epId}" +
                "?$BIZ_ARG=${PlayBiz.PUGV.name}" +
                "&$SEASON_ID_ARG=${route.seasonId ?: -1L}" +
                "&$FROM_ARG=${route.src.from}" +
                "&$FROM_SPMID_ARG=$fromSpmid" +
                "&$TRACK_ID_ARG=$trackId" +
                "&$REPORT_ARG=$report"
    )
}

@OptIn(UnstableApi::class)
fun NavGraphBuilder.videoScreen(
    onBack: () -> Unit,
    onOpenVideo: (VideoRoute) -> Unit,
) {
    composable(
        route = UGC_VIDEO_ROUTE,
        arguments = listOf(
            navArgument(AID_ARG) { type = NavType.LongType },
            navArgument(CID_ARG) { type = NavType.LongType },
            navArgument(BIZ_ARG) {
                type = NavType.StringType
                defaultValue = PlayBiz.UGC.name
            },
            navArgument(BVID_ARG) {
                type = NavType.StringType
                defaultValue = ""
            },
            navArgument(FROM_ARG) {
                type = NavType.StringType
                defaultValue = VideoRouteTool.FROM_FEED
            },
            navArgument(FROM_SPMID_ARG) {
                type = NavType.StringType
                defaultValue = VideoRouteTool.FROM_SPMID_FEED
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

    composable(
        route = PGC_VIDEO_ROUTE,
        arguments = listOf(
            navArgument(EP_ID_ARG) { type = NavType.LongType },
            navArgument(BIZ_ARG) {
                type = NavType.StringType
                defaultValue = PlayBiz.PGC.name
            },
            navArgument(SEASON_ID_ARG) {
                type = NavType.LongType
                defaultValue = -1L
            },
            navArgument(SUB_TYPE_ARG) {
                type = NavType.IntType
                defaultValue = -1
            },
            navArgument(FROM_ARG) {
                type = NavType.StringType
                defaultValue = VideoRouteTool.FROM_FEED
            },
            navArgument(FROM_SPMID_ARG) {
                type = NavType.StringType
                defaultValue = VideoRouteTool.FROM_SPMID_FEED
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

    composable(
        route = PUGV_VIDEO_ROUTE,
        arguments = listOf(
            navArgument(EP_ID_ARG) { type = NavType.LongType },
            navArgument(BIZ_ARG) {
                type = NavType.StringType
                defaultValue = PlayBiz.PUGV.name
            },
            navArgument(SEASON_ID_ARG) {
                type = NavType.LongType
                defaultValue = -1L
            },
            navArgument(FROM_ARG) {
                type = NavType.StringType
                defaultValue = VideoRouteTool.FROM_FEED
            },
            navArgument(FROM_SPMID_ARG) {
                type = NavType.StringType
                defaultValue = VideoRouteTool.FROM_SPMID_FEED
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
