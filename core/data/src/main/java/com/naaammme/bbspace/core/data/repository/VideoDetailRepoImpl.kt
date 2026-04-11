package com.naaammme.bbspace.core.data.repository

import com.bapis.bilibili.app.archive.middleware.v1.PlayerArgs
import com.bapis.bilibili.app.archive.middleware.v1.QnPolicy
import com.bapis.bilibili.app.viewunite.common.Module
import com.bapis.bilibili.app.viewunite.common.RelateCard
import com.bapis.bilibili.app.viewunite.common.Stat
import com.bapis.bilibili.app.viewunite.common.UgcSeasons
import com.bapis.bilibili.app.viewunite.ugcanymodel.ViewUgcAny
import com.bapis.bilibili.app.viewunite.v1.Relate
import com.bapis.bilibili.app.viewunite.v1.TabType
import com.bapis.bilibili.app.viewunite.v1.ViewReply
import com.bapis.bilibili.app.viewunite.v1.ViewReq
import com.bapis.bilibili.pagination.Pagination
import com.naaammme.bbspace.core.domain.video.VideoDetailRepository
import com.naaammme.bbspace.core.model.VideoDetail
import com.naaammme.bbspace.core.model.VideoJump
import com.naaammme.bbspace.core.model.VideoOwner
import com.naaammme.bbspace.core.model.VideoPagePart
import com.naaammme.bbspace.core.model.VideoRelate
import com.naaammme.bbspace.core.model.VideoSeason
import com.naaammme.bbspace.core.model.VideoSeasonEpisode
import com.naaammme.bbspace.core.model.VideoSeasonSection
import com.naaammme.bbspace.core.model.VideoStaff
import com.naaammme.bbspace.core.model.VideoStat
import com.naaammme.bbspace.core.model.VideoJumpTool
import com.naaammme.bbspace.infra.crypto.BiliSessionId
import com.naaammme.bbspace.infra.crypto.DeviceIdentity
import com.naaammme.bbspace.infra.grpc.BiliGrpcClient
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class VideoDetailRepoImpl @Inject constructor(
    private val grpcClient: BiliGrpcClient,
    private val deviceIdentity: DeviceIdentity
) : VideoDetailRepository {

    override suspend fun fetchVideoDetail(jump: VideoJump): VideoDetail {
        val reply = withContext(Dispatchers.IO) {
            grpcClient.call(
                endpoint = ENDPOINT,
                requestBytes = buildRequest(jump).toByteArray(),
                parser = ViewReply.parser()
            )
        }
        return withContext(Dispatchers.Default) { mapReply(jump.aid, reply) }
    }

    private fun buildRequest(jump: VideoJump): ViewReq {
        val playerArgs = PlayerArgs.newBuilder()
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

        val builder = ViewReq.newBuilder()
            .setFrom(jump.src.from)
            .setSpmid(VideoJumpTool.SPMID)
            .setFromSpmid(jump.src.fromSpmid)
            .setSessionId(BiliSessionId.view(deviceIdentity.buvid))
            .setPlayerArgs(playerArgs)
            .putAllExtraContent(EXTRA_CONTENT)
            .setRelate(
                Relate.newBuilder()
                    .setPagination(Pagination.newBuilder().build())
                    .build()
            )
            .setFromScene(FROM_SCENE)
        jump.aid.takeIf { it > 0L }?.let(builder::setAid)
        jump.bvid?.takeIf(String::isNotBlank)?.let(builder::setBvid)
        if (!jump.src.trackId.isNullOrBlank()) {
            builder.trackId = jump.src.trackId
        }
        return builder.build()
    }

    private fun mapReply(aid: Long, reply: ViewReply): VideoDetail {
        val resolvedAid = reply.arc.aid.takeIf { it > 0L } ?: aid
        var title = reply.arc.title
        var pubTs: Long? = null
        var desc = ""
        val tags = mutableListOf<String>()
        val staffs = mutableListOf<VideoStaff>()
        val relates = mutableListOf<VideoRelate>()
        var season: VideoSeason? = null

        reply.tab.tabModuleList
            .firstOrNull { it.tabType == TabType.TAB_INTRODUCTION }
            ?.introduction
            ?.modulesList
            ?.forEach { mod ->
                when (mod.dataCase) {
                    Module.DataCase.HEAD_LINE -> {
                        if (title.isBlank()) {
                            title = mod.headLine.content
                        }
                    }

                    Module.DataCase.UGC_INTRODUCTION -> {
                        val ugc = mod.ugcIntroduction
                        tags += ugc.tagsList.mapNotNull { it.name.takeIf(String::isNotBlank) }
                        pubTs = ugc.pubdate.takeIf { it > 0L }
                        desc = ugc.descList.joinToString("\n") { it.text.trim() }.trim()
                    }

                    Module.DataCase.STAFFS -> {
                        staffs += mod.staffs.staffList.mapNotNull { staff ->
                            val name = staff.name.ifBlank { return@mapNotNull null }
                            VideoStaff(
                                role = staff.title.ifBlank { "成员" },
                                name = name
                            )
                        }
                    }

                    Module.DataCase.UGC_SEASON -> {
                        season = mapSeason(mod.ugcSeason)
                    }

                    Module.DataCase.RELATES -> {
                        relates += mapRelates(mod.relates.cardsList)
                    }

                    else -> Unit
                }
            }

        return VideoDetail(
            aid = resolvedAid,
            bvid = reply.arc.bvid,
            title = title.ifBlank { "视频详情" },
            cover = reply.arc.cover.toHttps().ifBlank { null },
            owner = mapOwner(reply),
            stat = mapStat(reply.arc.stat),
            pubTs = pubTs,
            tags = tags.distinct(),
            desc = desc,
            staffs = staffs,
            season = season,
            pages = parsePages(reply),
            relates = relates
        )
    }

    private fun mapOwner(reply: ViewReply): VideoOwner? {
        val owner = reply.owner
        val name = owner.title.ifBlank { return null }
        val fansText = owner.fans.ifBlank {
            owner.fansNum.takeIf { it > 0L }?.let(::formatCount).orEmpty()
        }.ifBlank { null }
        return VideoOwner(
            mid = owner.mid,
            name = name,
            fansText = fansText,
            arcCountText = owner.arcCount.ifBlank { null },
            face = owner.face.toHttps().ifBlank { null }
        )
    }

    private fun mapStat(stat: Stat): VideoStat {
        return VideoStat(
            view = stat.vt.text.ifBlank { formatCount(stat.vt.value) },
            danmaku = stat.danmaku.text.ifBlank { formatCount(stat.danmaku.value) },
            reply = formatCount(stat.reply),
            like = formatCount(stat.like),
            coin = formatCount(stat.coin),
            fav = formatCount(stat.fav),
            share = formatCount(stat.share)
        )
    }

    private fun mapSeason(season: UgcSeasons): VideoSeason? {
        val title = season.title.ifBlank { season.seasonTitle }.ifBlank { return null }
        val sections = season.sectionList.mapNotNull { sec ->
            val eps = sec.episodesList.mapNotNull { ep ->
                val epTitle = ep.title.ifBlank { return@mapNotNull null }
                VideoSeasonEpisode(
                    aid = ep.aid,
                    cid = ep.cid,
                    title = epTitle,
                    subTitle = ep.coverRightText.ifBlank { null },
                    cover = ep.cover.toHttps().ifBlank { null }
                )
            }
            if (eps.isEmpty()) {
                null
            } else {
                VideoSeasonSection(
                    title = sec.title,
                    eps = eps
                )
            }
        }
        return VideoSeason(
            title = title,
            subTitle = season.supernatantTitle.ifBlank { season.unionTitle }.ifBlank { null },
            sections = sections
        )
    }

    private fun parsePages(reply: ViewReply): List<VideoPagePart> {
        if (!reply.hasSupplement()) return emptyList()
        val supplement = reply.supplement
        if (supplement.typeUrl.isBlank() || supplement.value.isEmpty) return emptyList()
        if (!supplement.typeUrl.endsWith("ViewUgcAny")) return emptyList()
        val ugc = ViewUgcAny.parseFrom(supplement.value)
        return ugc.pagesList.mapNotNull { page ->
            val part = page.part.ifBlank { return@mapNotNull null }
            VideoPagePart(
                cid = page.cid,
                part = part,
                durationSec = page.duration
            )
        }
    }

    private fun mapRelates(cards: List<RelateCard>): List<VideoRelate> {
        return cards.mapNotNull { card ->
            if (card.cardCase != RelateCard.CardCase.AV) return@mapNotNull null
            val basic = card.basicInfo
            val aid = basic.id.takeIf { it > 0L }
                ?: VideoJumpTool.aid(basic.uri)
                ?: return@mapNotNull null
            val cid = card.av.cid.takeIf { it > 0L }
                ?: VideoJumpTool.cid(basic.uri)
                ?: return@mapNotNull null
            val title = basic.title.ifBlank { return@mapNotNull null }
            val viewText = card.av.stat.vt.text.ifBlank {
                formatCount(card.av.stat.vt.value)
            }.takeIf(String::isNotBlank)
            val danmakuText = card.av.stat.danmaku.text.ifBlank {
                formatCount(card.av.stat.danmaku.value)
            }.takeIf(String::isNotBlank)
            VideoRelate(
                jump = VideoJump(
                    aid = aid,
                    cid = cid,
                    bvid = VideoJumpTool.bvid(basic.uri),
                    src = VideoJumpTool.relate(
                        trackId = basic.trackId,
                        reportFlowData = basic.reportFlowData,
                        fromSpmidSuffix = basic.fromSpmidSuffix
                    )
                ),
                title = title,
                cover = basic.cover.toHttps(),
                author = basic.author.title.ifBlank { null },
                durationText = card.av.durationText.ifBlank {
                    card.av.duration.takeIf { it > 0L }?.let(::formatDur)
                },
                viewText = viewText,
                danmakuText = danmakuText,
                reason = if (card.av.hasRcmdReason()) {
                    card.av.rcmdReason.text.takeIf(String::isNotBlank)
                } else {
                    null
                }
            )
        }
    }

    private fun formatCount(count: Long): String {
        return when {
            count >= 100_000_000L -> formatDecimal(count / 100_000_000f, "亿")
            count >= 10_000L -> formatDecimal(count / 10_000f, "万")
            else -> count.toString()
        }
    }

    private fun formatDecimal(value: Float, suffix: String): String {
        val text = String.format(Locale.ROOT, "%.1f", value).trimEnd('0').trimEnd('.')
        return "$text$suffix"
    }

    private fun formatDur(durationSec: Long): String {
        val minute = durationSec / 60
        val second = durationSec % 60
        val hour = minute / 60
        return if (hour > 0) {
            String.format(Locale.ROOT, "%d:%02d:%02d", hour, minute % 60, second)
        } else {
            String.format(Locale.ROOT, "%d:%02d", minute, second)
        }
    }

    private fun String.toHttps(): String {
        return replace("http://", "https://")
    }

    private companion object {
        const val ENDPOINT = "bilibili.app.viewunite.v1.View/View"
        const val FROM_SCENE = "normal"
        const val DEFAULT_QN = 64L
        const val DEFAULT_FNVER = 0L
        const val DEFAULT_FNVAL = 272L
        const val DEFAULT_FORCE_HOST = 0L
        const val DEFAULT_VOICE_BALANCE = 1L
        const val DEFAULT_CLIENT_ATTR = 0L
        const val SHORT_EDGE = "1080"
        const val LONG_EDGE = "1920"
        val EXTRA_CONTENT = mapOf(
            "autoplay" to "0",
            "questionaire_info" to "",
            "nature_ad" to "",
            "is_from_ugc_season" to "false",
            "reply_down_style" to "0"
        )
    }
}
