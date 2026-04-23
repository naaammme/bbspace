package com.naaammme.bbspace.core.data.repository

import com.bapis.bilibili.app.archive.middleware.v1.PlayerArgs
import com.bapis.bilibili.app.archive.middleware.v1.QnPolicy
import com.bapis.bilibili.app.interfaces.v1.CardArticle
import com.bapis.bilibili.app.interfaces.v1.CardCheese
import com.bapis.bilibili.app.interfaces.v1.CardLive
import com.bapis.bilibili.app.interfaces.v1.CardOGV
import com.bapis.bilibili.app.interfaces.v1.CardUGC
import com.bapis.bilibili.app.interfaces.v1.Cursor
import com.bapis.bilibili.app.interfaces.v1.CursorItem
import com.bapis.bilibili.app.interfaces.v1.CursorV2Reply
import com.bapis.bilibili.app.interfaces.v1.CursorV2Req
import com.bapis.bilibili.app.interfaces.v1.PlayerPreloadParams
import com.naaammme.bbspace.core.domain.history.HistoryRepository
import com.naaammme.bbspace.core.model.HistoryCursor
import com.naaammme.bbspace.core.model.HistoryItem
import com.naaammme.bbspace.core.model.HistoryPage
import com.naaammme.bbspace.core.model.HistoryTab
import com.naaammme.bbspace.core.model.HistoryTarget
import com.naaammme.bbspace.core.model.LiveRoute
import com.naaammme.bbspace.core.model.LiveRouteTool
import com.naaammme.bbspace.core.model.VideoRoute
import com.naaammme.bbspace.core.model.VideoRouteTool
import com.naaammme.bbspace.infra.grpc.BiliGrpcClient
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class HistoryRepoImpl @Inject constructor(
    private val grpcClient: BiliGrpcClient
) : HistoryRepository {

    override suspend fun fetchPage(
        tab: HistoryTab,
        cursor: HistoryCursor
    ): HistoryPage {
        val resp = grpcClient.call(
            endpoint = ENDPOINT,
            requestBytes = buildReq(tab, cursor).toByteArray(),
            parser = CursorV2Reply.parser()
        )
        return withContext(Dispatchers.Default) {
            HistoryPage(
                items = resp.itemsList.mapNotNull(::mapItem),
                cursor = HistoryCursor(
                    max = resp.cursor.max,
                    maxTp = resp.cursor.maxTp
                ),
                hasMore = resp.hasMore
            )
        }
    }

    private fun buildReq(
        tab: HistoryTab,
        cursor: HistoryCursor
    ): CursorV2Req {
        return CursorV2Req.newBuilder()
            .setCursor(
                Cursor.newBuilder()
                    .setMax(cursor.max)
                    .setMaxTp(cursor.maxTp)
                    .build()
            )
            .setBusiness(tab.business)
            .setPlayerPreload(buildPlayerPreload())
            .setPlayerArgs(buildPlayerArgs())
            .setIsLocal(false)
            .build()
    }

    private fun buildPlayerPreload(): PlayerPreloadParams {
        return PlayerPreloadParams.newBuilder()
            .setQn(DEFAULT_QN)
            .setFnver(DEFAULT_FNVER)
            .setFnval(DEFAULT_FNVAL)
            .setForceHost(DEFAULT_FORCE_HOST)
            .setFourk(DEFAULT_FOURK)
            .build()
    }

    private fun buildPlayerArgs(): PlayerArgs {
        return PlayerArgs.newBuilder()
            .setQn(DEFAULT_QN)
            .setFnver(DEFAULT_FNVER)
            .setFnval(DEFAULT_FNVAL)
            .setForceHost(DEFAULT_FORCE_HOST)
            .setVoiceBalance(DEFAULT_VOICE_BALANCE)
            .setQnPolicy(QnPolicy.QN_POLICY_DEFAULT)
            .setClientAttr(DEFAULT_CLIENT_ATTR)
            .putExtraContent("short_edge", SHORT_EDGE)
            .putExtraContent("long_edge", LONG_EDGE)
            .build()
    }

    private fun mapItem(item: CursorItem): HistoryItem? {
        val type = item.business.ifBlank { return null }
        val key = "$type:${item.kid}:${item.oid}:${item.viewAt}"
        return when (item.cardItemCase) {
            CursorItem.CardItemCase.CARD_UGC -> {
                val card = item.cardUgc
                HistoryItem(
                    key = key,
                    type = type,
                    typeLabel = typeLabel(type),
                    title = item.title,
                    cover = card.cover.toHttps(),
                    ownerName = card.name.blankToNull(),
                    badge = card.badge.blankToNull(),
                    subtitle = card.shareSubtitle.blankToNull(),
                    deviceLabel = item.dt.type.toDeviceLabel(),
                    viewedAtSec = item.viewAt,
                    progressSec = card.progress,
                    durationSec = card.duration.takeIf { it > 0L },
                    target = buildUgcTarget(item, card)
                )
            }

            CursorItem.CardItemCase.CARD_OGV -> {
                val card = item.cardOgv
                HistoryItem(
                    key = key,
                    type = type,
                    typeLabel = typeLabel(type),
                    title = item.title,
                    cover = card.cover.toHttps(),
                    ownerName = null,
                    badge = card.badge.blankToNull(),
                    subtitle = card.subtitle.blankToNull(),
                    deviceLabel = item.dt.type.toDeviceLabel(),
                    viewedAtSec = item.viewAt,
                    progressSec = card.progress,
                    durationSec = card.duration.takeIf { it > 0L },
                    target = buildPgcTarget(item)
                )
            }

            CursorItem.CardItemCase.CARD_ARTICLE -> {
                val card = item.cardArticle
                HistoryItem(
                    key = key,
                    type = type,
                    typeLabel = typeLabel(type),
                    title = item.title,
                    cover = card.coversList.firstOrNull().toHttps(),
                    ownerName = card.name.blankToNull(),
                    badge = card.badge.blankToNull(),
                    subtitle = null,
                    deviceLabel = item.dt.type.toDeviceLabel(),
                    viewedAtSec = item.viewAt,
                    progressSec = null,
                    durationSec = null,
                    target = null
                )
            }

            CursorItem.CardItemCase.CARD_LIVE -> {
                val card = item.cardLive
                HistoryItem(
                    key = key,
                    type = type,
                    typeLabel = typeLabel(type),
                    title = item.title,
                    cover = card.cover.toHttps(),
                    ownerName = card.name.blankToNull(),
                    badge = liveStatusLabel(card),
                    subtitle = card.tag.blankToNull(),
                    deviceLabel = item.dt.type.toDeviceLabel(),
                    viewedAtSec = item.viewAt,
                    progressSec = null,
                    durationSec = null,
                    target = buildLiveTarget(item, card)
                )
            }

            CursorItem.CardItemCase.CARD_CHEESE -> {
                val card = item.cardCheese
                HistoryItem(
                    key = key,
                    type = type,
                    typeLabel = typeLabel(type),
                    title = item.title,
                    cover = card.cover.toHttps(),
                    ownerName = null,
                    badge = typeLabel(type),
                    subtitle = card.subtitle.blankToNull(),
                    deviceLabel = item.dt.type.toDeviceLabel(),
                    viewedAtSec = item.viewAt,
                    progressSec = card.progress,
                    durationSec = card.duration.takeIf { it > 0L },
                    target = buildCheeseTarget(item, card)
                )
            }

            else -> null
        }
    }

    private fun buildUgcTarget(
        item: CursorItem,
        card: CardUGC
    ): HistoryTarget.Video? {
        val aid = item.oid.takeIf { it > 0L } ?: VideoRouteTool.aid(item.uri) ?: return null
        val cid = card.cid.takeIf { it > 0L } ?: VideoRouteTool.cid(item.uri) ?: return null
        return HistoryTarget.Video(
            VideoRoute.Ugc(
                aid = aid,
                cid = cid,
                bvid = card.bvid.blankToNull(),
                src = HISTORY_VIDEO_SRC
            )
        )
    }

    private fun buildPgcTarget(item: CursorItem): HistoryTarget.Video? {
        val epId = VideoRouteTool.epId(item.uri) ?: return null
        return HistoryTarget.Video(
            VideoRoute.Pgc(
                epId = epId,
                src = HISTORY_VIDEO_SRC
            )
        )
    }

    private fun buildCheeseTarget(
        item: CursorItem,
        card: CardCheese
    ): HistoryTarget.Video? {
        val epId = VideoRouteTool.epId(item.uri) ?: return null
        return HistoryTarget.Video(
            VideoRoute.Pugv(
                epId = epId,
                seasonId = card.seasonId.takeIf { it > 0L },
                src = HISTORY_VIDEO_SRC
            )
        )
    }

    private fun buildLiveTarget(
        item: CursorItem,
        card: CardLive
    ): HistoryTarget.Live? {
        val roomId = item.oid.takeIf { it > 0L } ?: item.kid.takeIf { it > 0L } ?: return null
        return HistoryTarget.Live(
            LiveRoute(
                roomId = roomId,
                title = item.title.blankToNull(),
                cover = card.cover.toHttps(),
                ownerName = card.name.blankToNull(),
                jumpFrom = LiveRouteTool.JUMP_FROM_HISTORY
            )
        )
    }

    private fun typeLabel(type: String): String {
        return when (type) {
            "archive" -> "视频"
            "pgc" -> "番剧"
            "article" -> "专栏"
            "live" -> "直播"
            "cheese" -> "课程"
            else -> type
        }
    }

    private fun liveStatusLabel(card: CardLive): String? {
        return when (card.status) {
            1 -> "直播中"
            0 -> "未开播"
            else -> null
        }
    }

    private fun String.blankToNull(): String? {
        return takeIf { it.isNotBlank() }
    }

    private fun String?.toHttps(): String? {
        return this?.replace("http://", "https://")?.blankToNull()
    }

    private fun com.bapis.bilibili.app.interfaces.v1.DT.toDeviceLabel(): String? {
        return when (this) {
            com.bapis.bilibili.app.interfaces.v1.DT.Phone -> "手机"
            com.bapis.bilibili.app.interfaces.v1.DT.PC -> "PC"
            com.bapis.bilibili.app.interfaces.v1.DT.Pad,
            com.bapis.bilibili.app.interfaces.v1.DT.AndPad -> "平板"
            com.bapis.bilibili.app.interfaces.v1.DT.TV -> "TV"
            com.bapis.bilibili.app.interfaces.v1.DT.Car -> "车机"
            com.bapis.bilibili.app.interfaces.v1.DT.Iot -> "IoT"
            else -> null
        }
    }

    private companion object {
        const val ENDPOINT = "bilibili.app.interface.v1.History/CursorV2"
        const val DEFAULT_QN = 80L
        const val DEFAULT_FNVER = 0L
        const val DEFAULT_FNVAL = 272L
        const val DEFAULT_FORCE_HOST = 0L
        const val DEFAULT_FOURK = 0L
        const val DEFAULT_VOICE_BALANCE = 1L
        const val DEFAULT_CLIENT_ATTR = 0L
        const val SHORT_EDGE = "1080"
        const val LONG_EDGE = "1920"
        val HISTORY_VIDEO_SRC = VideoRouteTool.history()
    }
}
