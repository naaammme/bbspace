package com.naaammme.bbspace.core.data.repository

import com.bapis.bilibili.app.archive.middleware.v1.PlayerArgs
import com.bapis.bilibili.app.archive.middleware.v1.QnPolicy
import com.bapis.bilibili.app.dynamic.v2.DynAllReply
import com.bapis.bilibili.app.dynamic.v2.DynAllReq
import com.bapis.bilibili.app.dynamic.v2.DynamicItem
import com.bapis.bilibili.app.dynamic.v2.FeedSortOptionReq
import com.bapis.bilibili.app.dynamic.v2.ModuleAuthor
import com.bapis.bilibili.app.dynamic.v2.ModuleDesc
import com.bapis.bilibili.app.dynamic.v2.ModuleStat
import com.bapis.bilibili.app.dynamic.v2.MdlDynArchive
import com.bapis.bilibili.app.dynamic.v2.MdlDynArticle
import com.bapis.bilibili.app.dynamic.v2.MdlDynDraw
import com.bapis.bilibili.app.dynamic.v2.MdlDynForward
import com.bapis.bilibili.app.dynamic.v2.MdlDynLive
import com.bapis.bilibili.app.dynamic.v2.MdlDynLiveRcmd
import com.bapis.bilibili.app.dynamic.v2.MdlDynUGCSeason
import com.bapis.bilibili.app.dynamic.v2.Refresh
import com.naaammme.bbspace.core.domain.dynamic.DynamicRepository
import com.naaammme.bbspace.core.model.DynamicAuthor
import com.naaammme.bbspace.core.model.DynamicBody
import com.naaammme.bbspace.core.model.DynamicCursor
import com.naaammme.bbspace.core.model.DynamicForwardItem
import com.naaammme.bbspace.core.model.DynamicImage
import com.naaammme.bbspace.core.model.DynamicItem as DynamicFeedItem
import com.naaammme.bbspace.core.model.DynamicPage
import com.naaammme.bbspace.core.model.DynamicRefresh
import com.naaammme.bbspace.core.model.DynamicStats
import com.naaammme.bbspace.core.model.LiveRoute
import com.naaammme.bbspace.core.model.LiveRouteTool
import com.naaammme.bbspace.core.model.SpaceRoute
import com.naaammme.bbspace.core.model.VideoTarget
import com.naaammme.bbspace.core.model.VideoTargetTool
import com.naaammme.bbspace.infra.grpc.BiliGrpcClient
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Singleton
class DynamicRepoImpl @Inject constructor(
    private val grpcClient: BiliGrpcClient
) : DynamicRepository {

    override suspend fun fetchAll(
        cursor: DynamicCursor,
        refresh: DynamicRefresh
    ): DynamicPage {
        val reply = grpcClient.call(
            endpoint = ENDPOINT,
            requestBytes = buildRequest(cursor, refresh).toByteArray(),
            parser = DynAllReply.parser()
        )
        return withContext(Dispatchers.Default) {
            mapReply(reply, cursor)
        }
    }

    private fun buildRequest(
        cursor: DynamicCursor,
        refresh: DynamicRefresh
    ): DynAllReq {
        return DynAllReq.newBuilder()
            .setUpdateBaseline(cursor.updateBaseline)
            .setOffset(cursor.historyOffset)
            .setPage(cursor.page)
            .setRefreshType(refresh.toProto())
            .setAssistBaseline(cursor.assistBaseline)
            .setLocalTime(localTime())
            .setColdStart(if (refresh == DynamicRefresh.NEW && cursor.page == 1) 1 else 0)
            .setPlayerArgs(buildPlayerArgs())
            .setReqSortOption(
                FeedSortOptionReq.newBuilder()
                    .setIsColdRefresh(refresh == DynamicRefresh.NEW && cursor.page == 1)
                    .build()
            )
            .build()
    }

    private fun mapReply(
        reply: DynAllReply,
        cursor: DynamicCursor
    ): DynamicPage {
        val list = reply.dynamicList
        return DynamicPage(
            items = list.listList.mapNotNull(::mapItem),
            cursor = DynamicCursor(
                historyOffset = list.historyOffset,
                updateBaseline = list.updateBaseline,
                assistBaseline = cursor.assistBaseline,
                page = cursor.page + 1
            ),
            hasMore = list.hasMore,
            updateNum = list.updateNum
        )
    }

    private fun mapItem(item: DynamicItem): DynamicFeedItem? {
        val extend = item.extend
        val id = extend.dynIdStr.ifBlank { return null }
        val author = item.modulesList.firstNotNullOfOrNull { module ->
            when {
                module.hasModuleAuthor() -> mapAuthor(module.moduleAuthor)
                module.hasModuleAuthorForward() -> {
                    val info = module.moduleAuthorForward
                    val name = info.titleList.joinToString("") { it.text }.ifBlank { return@firstNotNullOfOrNull null }
                    DynamicAuthor(
                        mid = info.uid,
                        name = name,
                        avatar = info.faceUrl.toHttps(),
                        pubAction = info.ptimeLabelText.blankToNull(),
                        pubLocation = null
                    )
                }

                else -> null
            }
        }
        val desc = item.modulesList.firstNotNullOfOrNull { module ->
            when {
                module.hasModuleDesc() -> mapDescText(module.moduleDesc)
                module.hasModuleParagraph() -> module.moduleParagraph.paragraph.text.nodesList
                    .joinToString("") { it.word.words }
                    .blankToNull()
                module.hasModuleOpusSummary() -> mapOpusSummarySummary(module.moduleOpusSummary)
                else -> null
            }
        } ?: extend.descList.joinToString("") { it.text }.blankToNull()
        val stat = item.modulesList.firstNotNullOfOrNull { module ->
            when {
                module.hasModuleStat() -> mapStats(module.moduleStat)
                module.hasModuleStatForward() -> mapStats(module.moduleStatForward)
                else -> null
            }
        }
        val summary = mapDynamicSummary(item)
        val spaceRoute = author?.mid?.takeIf { it > 0L }?.let { mid ->
            SpaceRoute(mid = mid, name = author.name)
        }
        return DynamicFeedItem(
            id = id,
            type = item.cardType.name,
            author = author,
            body = summary.body,
            stats = stat,
            publishedText = author?.pubAction,
            desc = desc,
            title = summary.title,
            cover = summary.cover,
            badge = summary.badge,
            videoTarget = summary.videoTarget,
            liveRoute = summary.liveRoute,
            spaceRoute = spaceRoute,
            trackId = extend.trackId.blankToNull(),
            reportFlowData = extend.reportMetricData.blankToNull(),
            canOpen = summary.videoTarget != null || summary.liveRoute != null
        )
    }

    private fun mapAuthor(author: ModuleAuthor): DynamicAuthor? {
        val user = author.author
        val name = user.name.ifBlank { return null }
        return DynamicAuthor(
            mid = author.mid,
            name = name,
            avatar = user.face.toHttps(),
            pubAction = author.ptimeLabelText.blankToNull(),
            pubLocation = author.ptimeLocationText.blankToNull()
        )
    }

    private fun mapDescText(desc: ModuleDesc): String? {
        val text = desc.text.blankToNull()
        if (text != null) return text
        return desc.descList.joinToString("") { it.text }.blankToNull()
    }

    private fun mapOpusSummarySummary(summary: com.bapis.bilibili.app.dynamic.v2.ModuleOpusSummary): String? {
        return summary.summary.text.nodesList.joinToString("") { it.rawText }.blankToNull()
    }

    private fun mapStats(stat: ModuleStat): DynamicStats {
        return DynamicStats(
            repost = stat.repost,
            reply = stat.reply,
            like = stat.like
        )
    }

    private fun mapDynamicSummary(item: DynamicItem): DynamicSummary {
        val dynamicModule = item.modulesList.firstOrNull { it.hasModuleDynamic() }?.moduleDynamic
        if (dynamicModule == null) {
            val opusSummary = item.modulesList.firstOrNull { it.hasModuleOpusSummary() }?.moduleOpusSummary
                ?: item.extend.takeIf { it.hasOpusSummary() }?.opusSummary
            if (opusSummary != null) {
                val title = opusSummary.title.text.nodesList.joinToString("") { it.rawText }.blankToNull()
                val summary = mapOpusSummarySummary(opusSummary)
                return DynamicSummary(
                    body = DynamicBody.Text(listOfNotNull(title, summary).joinToString("\n")),
                    title = title
                )
            }
            return DynamicSummary(
                body = DynamicBody.Unknown(item.extend.descList.joinToString("") { it.text }.blankToNull())
            )
        }
        return when {
            dynamicModule.hasDynArchive() -> mapArchive(dynamicModule.dynArchive, item)
            dynamicModule.hasDynDraw() -> mapDraw(dynamicModule.dynDraw, item)
            dynamicModule.hasDynArticle() -> mapArticle(dynamicModule.dynArticle, item)
            dynamicModule.hasDynForward() -> mapForward(dynamicModule.dynForward, item)
            dynamicModule.hasDynCommonLive() -> mapLive(dynamicModule.dynCommonLive, item)
            dynamicModule.hasDynLiveRcmd() -> mapLiveRcmd(dynamicModule.dynLiveRcmd, item)
            dynamicModule.hasDynUgcSeason() -> mapUgcSeason(dynamicModule.dynUgcSeason, item)
            dynamicModule.hasDynChargingArchive() -> mapArchive(dynamicModule.dynChargingArchive.archiveInfo, item)
            else -> DynamicSummary(
                body = DynamicBody.Unknown(item.extend.descList.joinToString("") { it.text }.blankToNull())
            )
        }
    }

    private fun mapArchive(
        archive: MdlDynArchive,
        item: DynamicItem
    ): DynamicSummary {
        val cover = archive.cover.toHttps()
        val badge = archive.badgeCategoryList.firstNotNullOfOrNull { it.text.blankToNull() }
            ?: archive.badgeList.firstNotNullOfOrNull { it.text.blankToNull() }
        return DynamicSummary(
            body = DynamicBody.Archive(
                text = item.extend.descList.joinToString("") { it.text }.blankToNull(),
                title = archive.title.ifBlank { "视频动态" },
                cover = cover,
                subTitle = archive.coverLeftText1.blankToNull()
                    ?: archive.coverLeftText2.blankToNull()
                    ?: archive.coverLeftText3.blankToNull(),
                badge = badge
            ),
            title = archive.title.blankToNull(),
            cover = cover,
            badge = badge,
            videoTarget = resolveArchiveTarget(archive, item),
            liveRoute = null
        )
    }

    private fun mapDraw(
        draw: MdlDynDraw,
        item: DynamicItem
    ): DynamicSummary {
        val images = draw.itemsList.mapNotNull { image ->
            image.src.toHttps()?.let { url ->
                DynamicImage(
                    url = url,
                    width = image.width.toInt(),
                    height = image.height.toInt()
                )
            }
        }
        val cover = images.firstOrNull()?.url
        val text = item.extend.descList.joinToString("") { it.text }.blankToNull()
        return DynamicSummary(
            body = DynamicBody.Draw(
                text = text,
                images = images
            ),
            title = text,
            cover = cover
        )
    }

    private fun mapArticle(
        article: MdlDynArticle,
        item: DynamicItem
    ): DynamicSummary {
        val cover = article.coversList.firstOrNull().toHttps()
        return DynamicSummary(
            body = DynamicBody.Article(
                text = item.extend.descList.joinToString("") { it.text }.blankToNull(),
                title = article.title.ifBlank { "专栏动态" },
                cover = cover,
                subTitle = article.desc.blankToNull()
            ),
            title = article.title.blankToNull(),
            cover = cover,
            badge = article.label.blankToNull()
        )
    }

    private fun mapUgcSeason(
        season: MdlDynUGCSeason,
        item: DynamicItem
    ): DynamicSummary {
        val cover = season.cover.toHttps()
        val badge = season.badgeList.firstNotNullOfOrNull { it.text.blankToNull() }
        return DynamicSummary(
            body = DynamicBody.Archive(
                text = item.extend.descList.joinToString("") { it.text }.blankToNull(),
                title = season.title.ifBlank { "视频合集" },
                cover = cover,
                subTitle = season.coverLeftText1.blankToNull()
                    ?: season.coverLeftText2.blankToNull()
                    ?: season.coverLeftText3.blankToNull(),
                badge = badge
            ),
            title = season.title.blankToNull(),
            cover = cover,
            badge = badge,
            videoTarget = resolveUgcSeasonTarget(season, item)
        )
    }

    private fun mapLive(
        live: MdlDynLive,
        item: DynamicItem
    ): DynamicSummary {
        val roomId = live.id.takeIf { it > 0L }
        val route = roomId?.let {
            LiveRoute(
                roomId = it,
                title = live.title.blankToNull(),
                cover = live.cover.toHttps(),
                ownerName = item.extend.upName.blankToNull(),
                onlineText = live.coverLabel.blankToNull(),
                jumpFrom = LiveRouteTool.JUMP_FROM_HOME_RECOMMEND
            )
        }
        return DynamicSummary(
            body = DynamicBody.Live(
                text = item.extend.descList.joinToString("") { it.text }.blankToNull(),
                title = live.title.ifBlank { "直播动态" },
                cover = live.cover.toHttps(),
                subTitle = live.coverLabel.blankToNull() ?: live.coverLabel2.blankToNull(),
                badge = live.badge.text.blankToNull()
            ),
            title = live.title.blankToNull(),
            cover = live.cover.toHttps(),
            badge = live.badge.text.blankToNull(),
            liveRoute = route
        )
    }

    private fun mapLiveRcmd(
        live: MdlDynLiveRcmd,
        item: DynamicItem
    ): DynamicSummary {
        val content = live.content.blankToNull() ?: return DynamicSummary(
            body = DynamicBody.Unknown(item.extend.descList.joinToString("") { it.text }.blankToNull())
        )
        val info = runCatching {
            JSONObject(content).optJSONObject("live_play_info")
        }.getOrNull() ?: return DynamicSummary(
            body = DynamicBody.Unknown(item.extend.descList.joinToString("") { it.text }.blankToNull())
        )
        val roomId = info.optLong("room_id").takeIf { it > 0L }
        val title = info.optString("title").blankToNull()
        val cover = info.optString("cover").toHttps()
        val onlineText = info.optLong("online")
            .takeIf { it > 0L }
            ?.toString()
            ?.plus("人看")
        val ownerName = item.extend.upName.blankToNull()
        return DynamicSummary(
            body = DynamicBody.Live(
                text = item.extend.descList.joinToString("") { it.text }.blankToNull(),
                title = title ?: "直播动态",
                cover = cover,
                subTitle = info.optString("area_name").blankToNull(),
                badge = info.optString("parent_area_name").blankToNull()
            ),
            title = title,
            cover = cover,
            badge = info.optString("parent_area_name").blankToNull(),
            liveRoute = roomId?.let {
                LiveRoute(
                    roomId = it,
                    title = title,
                    cover = cover,
                    ownerName = ownerName,
                    onlineText = onlineText,
                    jumpFrom = LiveRouteTool.JUMP_FROM_HOME_RECOMMEND
                )
            }
        )
    }

    private fun mapForward(
        forward: MdlDynForward,
        item: DynamicItem
    ): DynamicSummary {
        val origin = forward.item
        val originAuthor = origin.modulesList.firstNotNullOfOrNull { module ->
            when {
                module.hasModuleAuthor() -> module.moduleAuthor.author.name.blankToNull()
                module.hasModuleAuthorForward() -> module.moduleAuthorForward.titleList
                    .joinToString("") { it.text }
                    .blankToNull()
                else -> null
            }
        }
        val originDesc = origin.modulesList.firstNotNullOfOrNull { module ->
            when {
                module.hasModuleDesc() -> mapDescText(module.moduleDesc)
                else -> null
            }
        } ?: origin.extend.descList.joinToString("") { it.text }.blankToNull()
        val originDynamic = origin.modulesList.firstOrNull { it.hasModuleDynamic() }?.moduleDynamic
        val originTitle = when {
            originDynamic?.hasDynArchive() == true -> originDynamic.dynArchive.title.blankToNull()
            originDynamic?.hasDynArticle() == true -> originDynamic.dynArticle.title.blankToNull()
            originDynamic?.hasDynCommonLive() == true -> originDynamic.dynCommonLive.title.blankToNull()
            else -> null
        }
        val originCover = when {
            originDynamic?.hasDynArchive() == true -> originDynamic.dynArchive.cover.toHttps()
            originDynamic?.hasDynArticle() == true -> originDynamic.dynArticle.coversList.firstOrNull().toHttps()
            originDynamic?.hasDynDraw() == true -> originDynamic.dynDraw.itemsList.firstOrNull()?.src.toHttps()
            originDynamic?.hasDynCommonLive() == true -> originDynamic.dynCommonLive.cover.toHttps()
            else -> null
        }
        val originBadge = when {
            originDynamic?.hasDynArchive() == true -> originDynamic.dynArchive.badgeCategoryList
                .firstNotNullOfOrNull { it.text.blankToNull() }
            originDynamic?.hasDynArticle() == true -> originDynamic.dynArticle.label.blankToNull()
            originDynamic?.hasDynCommonLive() == true -> originDynamic.dynCommonLive.badge.text.blankToNull()
            else -> null
        }
        return DynamicSummary(
            body = DynamicBody.Forward(
                text = item.extend.descList.joinToString("") { it.text }.blankToNull(),
                origin = DynamicForwardItem(
                    authorName = originAuthor,
                    bodyText = originDesc,
                    title = originTitle,
                    cover = originCover,
                    badge = originBadge
                )
            ),
            title = originTitle,
            cover = originCover,
            badge = originBadge,
            videoTarget = if (originDynamic?.hasDynArchive() == true) {
                resolveArchiveTarget(originDynamic.dynArchive, origin)
            } else {
                null
            },
            liveRoute = if (originDynamic?.hasDynCommonLive() == true) {
                val live = originDynamic.dynCommonLive
                live.id.takeIf { it > 0L }?.let { roomId ->
                    LiveRoute(
                        roomId = roomId,
                        title = live.title.blankToNull(),
                        cover = live.cover.toHttps(),
                        ownerName = origin.extend.upName.blankToNull(),
                        onlineText = live.coverLabel.blankToNull(),
                        jumpFrom = LiveRouteTool.JUMP_FROM_HOME_RECOMMEND
                    )
                }
            } else {
                null
            }
        )
    }

    private fun resolveArchiveTarget(
        archive: MdlDynArchive,
        item: DynamicItem
    ): VideoTarget? {
        return when {
            archive.ispgc || archive.episodeid > 0L || archive.pgcseasonid > 0L -> {
                val epId = archive.episodeid.takeIf { it > 0L }
                epId?.let {
                    VideoTarget.Pgc(
                        epId = it,
                        seasonId = archive.pgcseasonid.takeIf { id -> id > 0L },
                        subType = archive.subtype.takeIf { it > 0 }
                    )
                }
            }

            else -> {
                val aid = archive.avid.takeIf { it > 0L }
                val cid = archive.cid.takeIf { it > 0L }
                if (aid != null && cid != null) {
                    VideoTarget.Ugc(
                        aid = aid,
                        cid = cid,
                        bvid = archive.bvid.blankToNull(),
                        src = VideoTargetTool.dynamic(
                            trackId = item.extend.trackId.blankToNull(),
                            reportFlowData = item.extend.reportMetricData.blankToNull()
                        )
                    )
                } else {
                    null
                }
            }
        }
    }

    private fun resolveUgcSeasonTarget(
        season: MdlDynUGCSeason,
        item: DynamicItem
    ): VideoTarget? {
        val aid = season.avid.takeIf { it > 0L } ?: return null
        val cid = season.cid.takeIf { it > 0L } ?: return null
        return VideoTarget.Ugc(
            aid = aid,
            cid = cid,
            src = VideoTargetTool.dynamic(
                trackId = item.extend.trackId.blankToNull(),
                reportFlowData = item.extend.reportMetricData.blankToNull()
            )
        )
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

    private fun localTime(): Int {
        return TimeZone.getDefault().rawOffset / 3_600_000
    }

    private fun DynamicRefresh.toProto(): Refresh {
        return when (this) {
            DynamicRefresh.NEW -> Refresh.refresh_new
            DynamicRefresh.HISTORY -> Refresh.refresh_history
        }
    }

    private fun String?.blankToNull(): String? {
        return this?.takeIf { it.isNotBlank() }
    }

    private fun String?.toHttps(): String? {
        return this?.replace("http://", "https://")?.blankToNull()
    }

    private data class DynamicSummary(
        val body: DynamicBody,
        val title: String? = null,
        val cover: String? = null,
        val badge: String? = null,
        val videoTarget: VideoTarget? = null,
        val liveRoute: LiveRoute? = null
    )

    private companion object {
        const val ENDPOINT = "bilibili.app.dynamic.v2.Dynamic/DynAll"
        const val DEFAULT_QN = 80L
        const val DEFAULT_FNVER = 0L
        const val DEFAULT_FNVAL = 272L
        const val DEFAULT_FORCE_HOST = 0L
        const val DEFAULT_VOICE_BALANCE = 1L
        const val DEFAULT_CLIENT_ATTR = 0L
        const val SHORT_EDGE = "1080"
        const val LONG_EDGE = "1920"
    }
}
