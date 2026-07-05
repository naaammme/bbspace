package com.naaammme.bbspace.core.video

import com.bapis.bilibili.app.archive.middleware.v1.PlayerArgs
import com.bapis.bilibili.app.archive.middleware.v1.QnPolicy
import com.bapis.bilibili.app.viewunite.common.Module
import com.bapis.bilibili.app.viewunite.common.RelateCard
import com.bapis.bilibili.app.viewunite.common.ViewEpisode
import com.bapis.bilibili.app.viewunite.common.Stat
import com.bapis.bilibili.app.viewunite.common.UgcSeasons
import com.bapis.bilibili.app.viewunite.pgcanymodel.ViewPgcAny
import com.bapis.bilibili.app.viewunite.pugvanymodel.ViewPugvAny
import com.bapis.bilibili.app.viewunite.ugcanymodel.ViewUgcAny
import com.bapis.bilibili.app.viewunite.v1.Relate
import com.bapis.bilibili.app.viewunite.v1.TabType
import com.bapis.bilibili.app.viewunite.v1.ViewReply
import com.bapis.bilibili.app.viewunite.v1.ViewReq
import com.bapis.bilibili.pagination.Pagination
import com.naaammme.bbspace.core.common.media.httpsImageUrl
import com.naaammme.bbspace.core.model.PlayBiz
import com.naaammme.bbspace.core.model.ResolvedVideoIds
import com.naaammme.bbspace.core.model.VideoDetail
import com.naaammme.bbspace.core.model.VideoDetailResult
import com.naaammme.bbspace.core.model.VideoOwner
import com.naaammme.bbspace.core.model.VideoPagePart
import com.naaammme.bbspace.core.model.VideoRelate
import com.naaammme.bbspace.core.model.VideoRequestIds
import com.naaammme.bbspace.core.model.VideoTarget
import com.naaammme.bbspace.core.model.VideoTargetTool
import com.naaammme.bbspace.core.model.VideoSeason
import com.naaammme.bbspace.core.model.VideoSeasonEpisode
import com.naaammme.bbspace.core.model.VideoSeasonSection
import com.naaammme.bbspace.core.model.VideoSrc
import com.naaammme.bbspace.core.model.VideoStaff
import com.naaammme.bbspace.core.model.VideoStat
import com.naaammme.bbspace.infra.crypto.BiliSessionId
import com.naaammme.bbspace.infra.crypto.DeviceIdentity
import com.naaammme.bbspace.infra.grpc.BiliGrpcClient
import com.naaammme.bbspace.core.model.toUgcTarget
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class VideoDetailRepository @Inject constructor(
    private val grpcClient: BiliGrpcClient,
    private val deviceIdentity: DeviceIdentity
) {
    suspend fun fetchVideoDetail(
        ids: VideoRequestIds,
        src: VideoSrc
    ): VideoDetailResult {
        val reply = withContext(Dispatchers.IO) {
            grpcClient.call(
                endpoint = ENDPOINT,
                requestBytes = buildRequest(ids, src).toByteArray(),
                parser = ViewReply.parser()
            )
        }
        return withContext(Dispatchers.Default) {
            mapReply(ids, reply)
        }
    }

    private fun buildRequest(
        ids: VideoRequestIds,
        src: VideoSrc
    ): ViewReq {
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
            .setFrom(src.from)
            .setSpmid(VideoTargetTool.SPMID)
            .setFromSpmid(src.fromSpmid)
            .setSessionId(BiliSessionId.view(deviceIdentity.buvid))
            .setPlayerArgs(playerArgs)
            .putAllExtraContent(EXTRA_CONTENT)
            .setRelate(
                Relate.newBuilder()
                    .setPagination(Pagination.newBuilder().build())
                    .build()
            )
            .setFromScene(FROM_SCENE)
        ids.aid.takeIf { it > 0L }?.let(builder::setAid)
        ids.bvid?.takeIf(String::isNotBlank)?.let(builder::setBvid)
        ids.cid.takeIf { it > 0L }?.let { builder.putExtraContent("cid", it.toString()) }
        ids.epId.takeIf { it > 0L }?.let { builder.putExtraContent("ep_id", it.toString()) }
        ids.seasonId.takeIf { it > 0L }?.let { builder.putExtraContent("season_id", it.toString()) }
        if (!src.trackId.isNullOrBlank()) {
            builder.trackId = src.trackId
        }
        return builder.build()
    }

    private fun mapReply(
        requestIds: VideoRequestIds,
        reply: ViewReply
    ): VideoDetailResult {
        val ids = resolveIds(requestIds, reply)
        val biz = resolveBiz(requestIds, reply)
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

                    Module.DataCase.SECTION_DATA -> {
                        if (season == null) {
                            season = mapSectionDataSeason(mod.sectionData)
                        }
                    }

                    Module.DataCase.RELATES -> {
                        relates += mapRelates(mod.relates.cardsList)
                    }

                    else -> Unit
                }
            }
        return VideoDetailResult(
            detail = VideoDetail(
                title = title.ifBlank { "视频详情" },
                cover = reply.arc.cover.httpsImageUrl().ifBlank { null },
                owner = mapOwner(reply),
                stat = mapStat(reply.arc.stat),
                pubTs = pubTs,
                tags = tags.distinct(),
                desc = desc,
                staffs = staffs,
                season = season,
                pages = parsePages(reply),
                relates = relates
            ),
            ids = ids,
            biz = biz
        )
    }

    private fun mapOwner(reply: ViewReply): VideoOwner? {
        val owner = reply.owner
        val name = owner.title.ifBlank { return null }
        val fansText = owner.fans.ifBlank {
            owner.fansNum.takeIf { it > 0L }?.toString().orEmpty()
        }.ifBlank { null }
        return VideoOwner(
            mid = owner.mid,
            name = name,
            fansText = fansText,
            arcCountText = owner.arcCount.ifBlank { null },
            face = owner.face.httpsImageUrl().ifBlank { null }
        )
    }

    private fun mapStat(stat: Stat): VideoStat {
        return VideoStat(
            view = stat.vt.text.ifBlank { stat.vt.value.toString() },
            danmaku = stat.danmaku.text.ifBlank { stat.danmaku.value.toString() },
            reply = stat.reply.toString(),
            like = stat.like.toString(),
            coin = stat.coin.toString(),
            fav = stat.fav.toString(),
            share = stat.share.toString()
        )
    }

    private fun mapSeason(season: UgcSeasons): VideoSeason? {
        val title = season.title.ifBlank { season.seasonTitle }.ifBlank { return null }
        val sections = season.sectionList.mapNotNull { sec ->
            val eps = sec.episodesList.mapNotNull { ep ->
                val epTitle = ep.title.ifBlank { return@mapNotNull null }
                val ids = ResolvedVideoIds(
                    aid = ep.aid,
                    cid = ep.cid
                )
                VideoSeasonEpisode(
                    target = ids.toUgcTarget(VideoTargetTool.relate()),
                    cid = ids.cid,
                    title = epTitle,
                    subTitle = ep.coverRightText.ifBlank { null },
                    cover = ep.cover.httpsImageUrl().ifBlank { null }
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

    private fun mapSectionDataSeason(section: com.bapis.bilibili.app.viewunite.common.SectionData): VideoSeason? {
        val sectionTitle = section.title.ifBlank { "选集" }
        val src = VideoTargetTool.relate()
        val eps = section.episodesList.mapNotNull { ep ->
            val epId = ep.epId.takeIf { it > 0L } ?: return@mapNotNull null
            val title = ep.showTitle.ifBlank {
                when {
                    ep.title.isNotBlank() && ep.longTitle.isNotBlank() -> "第${ep.title}话 ${ep.longTitle}"
                    ep.title.isNotBlank() -> "第${ep.title}话"
                    ep.longTitle.isNotBlank() -> ep.longTitle
                    else -> return@mapNotNull null
                }
            }
            VideoSeasonEpisode(
                target = VideoTarget.Pgc(
                    aid = ep.aid,
                    cid = ep.cid,
                    epId = epId,
                    src = src
                ),
                cid = ep.cid,
                title = title,
                subTitle = ep.subtitle.ifBlank { null },
                cover = ep.cover.httpsImageUrl().ifBlank { null }
            )
        }
        if (eps.isEmpty()) return null
        return VideoSeason(
            title = sectionTitle,
            subTitle = section.more.ifBlank { null },
            sections = listOf(
                VideoSeasonSection(
                    title = sectionTitle,
                    eps = eps
                )
            )
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
                ?: VideoTargetTool.aid(basic.uri)
                ?: return@mapNotNull null
            val cid = card.av.cid.takeIf { it > 0L }
                ?: VideoTargetTool.cid(basic.uri)
                ?: return@mapNotNull null
            val ids = ResolvedVideoIds(
                aid = aid,
                cid = cid,
                bvid = VideoTargetTool.bvid(basic.uri)
            )
            val title = basic.title.ifBlank { return@mapNotNull null }
            val viewText = card.av.stat.vt.text.ifBlank {
                card.av.stat.vt.value.toString()
            }.takeIf(String::isNotBlank)
            val danmakuText = card.av.stat.danmaku.text.ifBlank {
                card.av.stat.danmaku.value.toString()
            }.takeIf(String::isNotBlank)
            VideoRelate(
                target = ids.toUgcTarget(
                    VideoTargetTool.relate(
                        trackId = basic.trackId,
                        reportFlowData = basic.reportFlowData,
                        fromSpmidSuffix = basic.fromSpmidSuffix
                    )
                ),
                title = title,
                cover = basic.cover.httpsImageUrl(),
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

    private fun resolveIds(
        requestIds: VideoRequestIds,
        reply: ViewReply
    ): ResolvedVideoIds {
        val arcIds = ResolvedVideoIds(
            aid = reply.arc.aid,
            cid = reply.arc.cid,
            bvid = reply.arc.bvid.takeIf(String::isNotBlank)
        )
        val reportIds = ResolvedVideoIds(
            aid = reply.reportMap["aid"]?.toLongOrNull() ?: 0L,
            cid = reply.reportMap["cid"]?.toLongOrNull() ?: 0L,
            epId = reply.reportMap["epid"]?.toLongOrNull()
                ?: reply.reportMap["ep_id"]?.toLongOrNull()
                ?: 0L,
            seasonId = reply.reportMap["sid"]?.toLongOrNull()
                ?: reply.reportMap["season_id"]?.toLongOrNull()
                ?: 0L,
            bvid = reply.reportMap["bvid"]?.takeIf(String::isNotBlank)
        )
        val supplement = parseSupplementIds(reply)
        val selected = selectCurrentSupplementIds(requestIds, supplement.ids, supplement.current)
        return ResolvedVideoIds(
            aid = requestIds.aid.takeIf { it > 0L }
                ?: selected?.aid?.takeIf { it > 0L }
                ?: reportIds.aid.takeIf { it > 0L }
                ?: arcIds.aid,
            cid = requestIds.cid.takeIf { it > 0L }
                ?: selected?.cid?.takeIf { it > 0L }
                ?: reportIds.cid.takeIf { it > 0L }
                ?: arcIds.cid,
            epId = requestIds.epId.takeIf { it > 0L }
                ?: selected?.epId?.takeIf { it > 0L }
                ?: reportIds.epId.takeIf { it > 0L }
                ?: 0L,
            seasonId = requestIds.seasonId.takeIf { it > 0L }
                ?: selected?.seasonId?.takeIf { it > 0L }
                ?: reportIds.seasonId.takeIf { it > 0L }
                ?: 0L,
            bvid = requestIds.bvid?.takeIf(String::isNotBlank)
                ?: selected?.bvid?.takeIf(String::isNotBlank)
                ?: reportIds.bvid?.takeIf(String::isNotBlank)
                ?: arcIds.bvid?.takeIf(String::isNotBlank)
        )
    }

    private fun resolveBiz(
        requestIds: VideoRequestIds,
        reply: ViewReply
    ): PlayBiz {
        return when {
            reply.supplement.typeUrl.endsWith("ViewPugvAny") -> PlayBiz.PUGV
            reply.supplement.typeUrl.endsWith("ViewPgcAny") -> PlayBiz.PGC
            else -> PlayBiz.UGC
        }
    }

    private fun selectCurrentSupplementIds(
        requestIds: VideoRequestIds,
        ids: List<ResolvedVideoIds>,
        preferred: ResolvedVideoIds?
    ): ResolvedVideoIds? {
        if (ids.isEmpty()) return preferred
        return ids.firstOrNull { requestIds.epId > 0L && it.epId == requestIds.epId }
            ?: ids.firstOrNull { requestIds.cid > 0L && it.cid == requestIds.cid }
            ?: ids.firstOrNull { requestIds.aid > 0L && it.aid == requestIds.aid }
            ?: ids.firstOrNull { preferred?.epId?.takeIf { epId -> epId > 0L } == it.epId }
            ?: preferred
            ?: ids.firstOrNull { requestIds.seasonId > 0L && it.seasonId == requestIds.seasonId }
            ?: ids.first()
    }

    private fun parseSupplementIds(reply: ViewReply): SupplementIds {
        if (!reply.hasSupplement()) return SupplementIds()
        val supplement = reply.supplement
        if (supplement.typeUrl.isBlank() || supplement.value.isEmpty) return SupplementIds()
        return when {
            supplement.typeUrl.endsWith("ViewPgcAny") -> {
                val pgc = ViewPgcAny.parseFrom(supplement.value)
                val seasonId = pgc.ogvData.seasonId
                val ids = pgc.ogvData.reserve.episodesList.map {
                    it.toResolvedIds(seasonId)
                }
                val current = pgc.ogvData.newEp.id
                    .takeIf { it > 0 }
                    ?.toLong()
                    ?.let { epId ->
                        ids.firstOrNull { it.epId == epId } ?: ResolvedVideoIds(
                            epId = epId,
                            seasonId = seasonId
                        )
                    }
                SupplementIds(ids = ids, current = current)
            }

            supplement.typeUrl.endsWith("ViewPugvAny") -> {
                val pugv = ViewPugvAny.parseFrom(supplement.value)
                val seasonId = pugv.seasonOverview.seasonId
                val ids = pugv.sectionInfo.sectionsList.flatMap { section ->
                    section.episodesList.mapNotNull { ep ->
                        if (!ep.hasVideoEpisode()) return@mapNotNull null
                        val video = ep.videoEpisode
                        ResolvedVideoIds(
                            aid = video.aid,
                            cid = video.cid,
                            epId = video.episodeId,
                            seasonId = seasonId,
                            bvid = VideoTargetTool.bvid(video.shareLink)
                        )
                    }
                }
                val current = ids.firstOrNull { id ->
                    pugv.sectionInfo.sectionsList.any { section ->
                        section.episodesList.any { ep ->
                            ep.hasVideoEpisode() &&
                                ep.videoEpisode.episodeId == id.epId &&
                                ep.videoEpisode.history.lastPlay
                        }
                    }
                }
                SupplementIds(ids = ids, current = current)
            }

            else -> SupplementIds()
        }.let { parsed ->
            parsed.copy(ids = parsed.ids.filter { it.hasAny })
        }
    }

    private fun ViewEpisode.toResolvedIds(seasonId: Long): ResolvedVideoIds {
        return ResolvedVideoIds(
            aid = aid,
            cid = cid,
            epId = epId,
            seasonId = seasonId,
            bvid = bvid.takeIf(String::isNotBlank) ?: VideoTargetTool.bvid(shareUrl)
        )
    }

    private data class SupplementIds(
        val ids: List<ResolvedVideoIds> = emptyList(),
        val current: ResolvedVideoIds? = null
    )

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
