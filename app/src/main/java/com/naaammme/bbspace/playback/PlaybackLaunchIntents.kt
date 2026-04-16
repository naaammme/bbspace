package com.naaammme.bbspace.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.naaammme.bbspace.MainActivity
import com.naaammme.bbspace.core.model.PlayBiz
import com.naaammme.bbspace.core.model.VideoRoute
import com.naaammme.bbspace.core.model.VideoRouteTool

internal object PlaybackLaunchIntents {
    private const val ACTION_OPEN_VIDEO = "com.naaammme.bbspace.action.OPEN_VIDEO"
    private const val REQ_OPEN_VIDEO = 1001
    private const val EXTRA_BIZ = "playback.biz"
    private const val EXTRA_AID = "playback.aid"
    private const val EXTRA_CID = "playback.cid"
    private const val EXTRA_BVID = "playback.bvid"
    private const val EXTRA_EP_ID = "playback.epId"
    private const val EXTRA_SEASON_ID = "playback.seasonId"
    private const val EXTRA_SUB_TYPE = "playback.subType"
    private const val EXTRA_FROM = "playback.from"
    private const val EXTRA_FROM_SPMID = "playback.fromSpmid"
    private const val EXTRA_TRACK_ID = "playback.trackId"
    private const val EXTRA_REPORT = "playback.report"

    fun createContentIntent(
        context: Context,
        route: VideoRoute?
    ): PendingIntent? {
        route ?: return null
        return PendingIntent.getActivity(
            context,
            REQ_OPEN_VIDEO,
            createOpenVideoIntent(context, route),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun consumeRoute(intent: Intent?): VideoRoute? {
        val srcIntent = intent ?: return null
        val biz = srcIntent.getStringExtra(EXTRA_BIZ) ?: return null
        val src = VideoRouteTool.custom(
            from = srcIntent.getStringExtra(EXTRA_FROM),
            fromSpmid = srcIntent.getStringExtra(EXTRA_FROM_SPMID),
            trackId = srcIntent.getStringExtra(EXTRA_TRACK_ID),
            reportFlowData = srcIntent.getStringExtra(EXTRA_REPORT)
        )
        return when (PlayBiz.from(biz)) {
            PlayBiz.UGC -> {
                val aid = srcIntent.getLongExtra(EXTRA_AID, 0L)
                val cid = srcIntent.getLongExtra(EXTRA_CID, 0L)
                if (aid <= 0L || cid <= 0L) return null
                VideoRoute.Ugc(
                    aid = aid,
                    cid = cid,
                    bvid = srcIntent.getStringExtra(EXTRA_BVID),
                    src = src
                )
            }

            PlayBiz.PGC -> {
                val epId = srcIntent.getLongExtra(EXTRA_EP_ID, 0L)
                if (epId <= 0L) return null
                VideoRoute.Pgc(
                    epId = epId,
                    seasonId = srcIntent.getLongExtra(EXTRA_SEASON_ID, -1L).takeIf { it > 0L },
                    subType = srcIntent.getIntExtra(EXTRA_SUB_TYPE, -1).takeIf { it >= 0 },
                    src = src
                )
            }

            PlayBiz.PUGV -> {
                val epId = srcIntent.getLongExtra(EXTRA_EP_ID, 0L)
                if (epId <= 0L) return null
                VideoRoute.Pugv(
                    epId = epId,
                    seasonId = srcIntent.getLongExtra(EXTRA_SEASON_ID, -1L).takeIf { it > 0L },
                    src = src
                )
            }
        }
    }

    private fun createOpenVideoIntent(
        context: Context,
        route: VideoRoute
    ): Intent {
        return Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_VIDEO
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putRoute(route)
        }
    }

    private fun Intent.putRoute(route: VideoRoute) {
        when (route) {
            is VideoRoute.Ugc -> {
                putExtra(EXTRA_BIZ, PlayBiz.UGC.name)
                putExtra(EXTRA_AID, route.aid)
                putExtra(EXTRA_CID, route.cid)
                putExtra(EXTRA_BVID, route.bvid)
            }

            is VideoRoute.Pgc -> {
                putExtra(EXTRA_BIZ, PlayBiz.PGC.name)
                putExtra(EXTRA_EP_ID, route.epId)
                route.seasonId?.let { putExtra(EXTRA_SEASON_ID, it) }
                route.subType?.let { putExtra(EXTRA_SUB_TYPE, it) }
            }

            is VideoRoute.Pugv -> {
                putExtra(EXTRA_BIZ, PlayBiz.PUGV.name)
                putExtra(EXTRA_EP_ID, route.epId)
                route.seasonId?.let { putExtra(EXTRA_SEASON_ID, it) }
            }
        }
        putExtra(EXTRA_FROM, route.src.from)
        putExtra(EXTRA_FROM_SPMID, route.src.fromSpmid)
        putExtra(EXTRA_TRACK_ID, route.src.trackId)
        putExtra(EXTRA_REPORT, route.src.reportFlowData)
    }
}
