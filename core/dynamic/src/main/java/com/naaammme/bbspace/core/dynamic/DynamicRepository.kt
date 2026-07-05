package com.naaammme.bbspace.core.dynamic

import com.bapis.bilibili.app.dynamic.v2.AdParam
import com.bapis.bilibili.app.dynamic.v2.Config
import com.bapis.bilibili.app.dynamic.v2.DynAllReply
import com.bapis.bilibili.app.dynamic.v2.DynAllReq
import com.bapis.bilibili.app.dynamic.v2.DynSpaceReq
import com.bapis.bilibili.app.dynamic.v2.DynSpaceRsp
import com.bapis.bilibili.app.dynamic.v2.DynamicItem
import com.bapis.bilibili.app.dynamic.v2.FeedSortOptionReq
import com.bapis.bilibili.app.dynamic.v2.ModuleAuthor
import com.bapis.bilibili.app.dynamic.v2.ModuleDesc
import com.bapis.bilibili.app.dynamic.v2.ModuleOpusSummary
import com.bapis.bilibili.app.dynamic.v2.ModuleStat
import com.bapis.bilibili.app.dynamic.v2.MdlDynArchive
import com.bapis.bilibili.app.dynamic.v2.MdlDynArticle
import com.bapis.bilibili.app.dynamic.v2.MdlDynDraw
import com.bapis.bilibili.app.dynamic.v2.MdlDynForward
import com.bapis.bilibili.app.dynamic.v2.MdlDynLive
import com.bapis.bilibili.app.dynamic.v2.MdlDynLiveRcmd
import com.bapis.bilibili.app.dynamic.v2.MdlDynUGCSeason
import com.bapis.bilibili.app.dynamic.v2.OpusDetailReq
import com.bapis.bilibili.app.dynamic.v2.OpusDetailResp
import com.bapis.bilibili.app.dynamic.v2.Refresh
import com.naaammme.bbspace.core.common.media.httpsImageUrlOrNull
import com.naaammme.bbspace.core.model.DynamicAuthor
import com.naaammme.bbspace.core.model.DynamicBody
import com.naaammme.bbspace.core.model.DynamicCursor
import com.naaammme.bbspace.core.model.DynamicForwardItem
import com.naaammme.bbspace.core.model.DynamicImage
import com.naaammme.bbspace.core.model.DynamicDetail
import com.naaammme.bbspace.core.model.DynamicDetailAuthor
import com.naaammme.bbspace.core.model.DynamicDetailParagraph
import com.naaammme.bbspace.core.model.DynamicItem as DynamicFeedItem
import com.naaammme.bbspace.core.model.DynamicPage
import com.naaammme.bbspace.core.model.DynamicRefresh
import com.naaammme.bbspace.core.model.DynamicStats
import com.naaammme.bbspace.core.model.DynamicUpItem
import com.naaammme.bbspace.core.model.DynamicUpList
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
class DynamicRepository @Inject constructor(
    private val grpcClient: BiliGrpcClient
) {

    suspend fun fetchAll(
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

    suspend fun fetchOpusDetail(opusId: String, opusType: Int = 0): DynamicDetail {
        val req = OpusDetailReq.newBuilder()
            .setOpusTypeValue(opusType)
            .setOid(opusId.toLong())
            .setShareId("dt.opus-detail.0.0.pv")
            .setShareMode(3)
            .setLocalTime(localTime())
            .setConfig(Config.getDefaultInstance())
            .setAdParam(AdParam.getDefaultInstance())
            .setPattern("outer")
            .build()
        val reply = grpcClient.call(
            endpoint = OPUS_DETAIL_ENDPOINT,
            requestBytes = req.toByteArray(),
            parser = OpusDetailResp.parser()
        )
        return withContext(Dispatchers.Default) {
            mapDetail(reply.opusItem)
        }
    }

    suspend fun fetchSpace(
        hostUid: Long,
        page: Int,
        historyOffset: String = "",
        from: String = "space"
    ): DynamicPage {
        val req = DynSpaceReq.newBuilder()
            .setHostUid(hostUid)
            .setHistoryOffset(historyOffset)
            .setLocalTime(localTime())
            .setPage(page.toLong())
            .setFrom(from)
            .build()
        val reply = grpcClient.call(
            endpoint = SPACE_ENDPOINT,
            requestBytes = req.toByteArray(),
            parser = DynSpaceRsp.parser()
        )
        return withContext(Dispatchers.Default) {
            DynamicPage(
                items = reply.listList.mapNotNull(::mapItem),
                upList = null,
                cursor = DynamicCursor(
                    historyOffset = reply.historyOffset,
                    page = page + 1
                ),
                hasMore = reply.hasMore,
                updateNum = 0L
            )
        }
    }

    private fun mapDetail(opus: com.bapis.bilibili.app.dynamic.v2.OpusItem): DynamicDetail {
        val modules = opus.modulesList
        val author = modules.firstNotNullOfOrNull { module ->
            when {
                module.hasModuleAuthor() -> {
                    val m = module.moduleAuthor
                    val user = m.author
                    DynamicDetailAuthor(
                        mid = m.mid,
                        name = user.name.ifBlank { return@firstNotNullOfOrNull null },
                        face = user.face.blankToNull().httpsImageUrlOrNull(),
                        pubTime = m.ptimeLabelText.blankToNull()
                    )
                }
                module.hasModuleTop() -> {
                    val m = module.moduleTop
                    val user = m.author.author
                    DynamicDetailAuthor(
                        mid = m.author.mid,
                        name = user.name.ifBlank { return@firstNotNullOfOrNull null },
                        face = user.face.blankToNull().httpsImageUrlOrNull(),
                        pubTime = m.author.ptimeLabelText.blankToNull()
                    )
                }
                else -> null
            }
        } ?: DynamicDetailAuthor(mid = 0L, name = "", face = null, pubTime = null)

        val paragraphs = modules.mapNotNull { module ->
            if (!module.hasModuleParagraph()) return@mapNotNull null
            val p = module.moduleParagraph.paragraph
            when (p.paraType.number) {
                1 -> { // TEXT
                    val text = p.text.nodesList.joinToString("") { it.rawText }.blankToNull()
                    DynamicDetailParagraph(
                        type = DynamicDetailParagraph.TYPE_TEXT,
                        text = text,
                        images = emptyList()
                    )
                }
                2 -> { // PICTURES
                    val images = p.pic.pics.itemsList.mapNotNull { img ->
                        img.src.blankToNull().httpsImageUrlOrNull()?.let { url ->
                            DynamicImage(
                                url = url,
                                width = img.width.toInt(),
                                height = img.height.toInt()
                            )
                        }
                    }
                    DynamicDetailParagraph(
                        type = DynamicDetailParagraph.TYPE_PICTURES,
                        text = null,
                        images = images
                    )
                }
                else -> null
            }
        }

        val stats = modules.firstNotNullOfOrNull { module ->
            if (module.hasModuleButtom()) {
                val s = module.moduleButtom.moduleStat
                DynamicStats(
                    repost = s.repost,
                    reply = s.reply,
                    like = s.like
                )
            } else null
        }

        val reply = opus.extend.reply
        return DynamicDetail(
            author = author,
            paragraphs = paragraphs,
            stats = stats,
            replyBizId = reply.replyBizId.takeIf { it > 0L } ?: 0L,
            replyBizType = reply.replyBizType.takeIf { it > 0L } ?: 0L
        )
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
            upList = mapUpList(reply),
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

    private fun mapUpList(reply: DynAllReply): DynamicUpList? {
        val upList = reply.upList
        val items = upList.listList.mapNotNull { item ->
            val uid = item.uid
            val name = item.name.ifBlank { return@mapNotNull null }
            if (uid <= 0L) return@mapNotNull null
            DynamicUpItem(
                uid = uid,
                name = name,
                face = item.face.blankToNull().httpsImageUrlOrNull(),
                hasUpdate = item.hasUpdate,
                trackId = item.trackId.blankToNull()
            )
        }
        if (items.isEmpty()) return null
        return DynamicUpList(
            title = upList.title.blankToNull(),
            items = items
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
                        avatar = info.faceUrl.blankToNull().httpsImageUrlOrNull(),
                        pubAction = info.ptimeLabelText.blankToNull(),
                        pubLocation = null
                    )
                }

                else -> null
            }
        }
        val desc = mapPrimaryText(item) ?: mapExtendDesc(item)
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
            avatar = user.face.blankToNull().httpsImageUrlOrNull(),
            pubAction = author.ptimeLabelText.blankToNull(),
            pubLocation = author.ptimeLocationText.blankToNull()
        )
    }

    private fun mapDescText(desc: ModuleDesc): String? {
        val text = desc.text.blankToNull()
        if (text != null) return text
        return desc.descList.joinToString("") { it.text }.blankToNull()
    }

    private fun mapExtendDesc(item: DynamicItem): String? {
        return item.extend.descList.joinToString("") { it.text }.blankToNull()
    }

    private fun mapOpusSummaryTitle(summary: ModuleOpusSummary): String? {
        return summary.title.text.nodesList.joinToString("") { it.rawText }.blankToNull()
    }

    private fun mapOpusSummaryText(summary: ModuleOpusSummary): String? {
        val title = mapOpusSummaryTitle(summary)
        val text = summary.summary.text.nodesList.joinToString("") { it.rawText }.blankToNull()
        return listOfNotNull(title, text).joinToString("\n").blankToNull()
    }

    private fun mapPrimaryText(item: DynamicItem): String? {
        return item.modulesList.firstNotNullOfOrNull { module ->
            when {
                module.hasModuleDesc() -> mapDescText(module.moduleDesc)
                module.hasModuleParagraph() -> module.moduleParagraph.paragraph.text.nodesList
                    .joinToString("") { it.rawText }
                    .blankToNull()
                module.hasModuleOpusSummary() -> mapOpusSummaryText(module.moduleOpusSummary)
                else -> null
            }
        }
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
                val title = mapOpusSummaryTitle(opusSummary)
                val summary = mapOpusSummaryText(opusSummary)
                return DynamicSummary(
                    body = DynamicBody.Text(summary.orEmpty()),
                    title = title
                )
            }
            return DynamicSummary(
                body = DynamicBody.Unknown(mapExtendDesc(item))
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
                body = DynamicBody.Unknown(mapExtendDesc(item))
            )
        }
    }

    private fun mapArchive(
        archive: MdlDynArchive,
        item: DynamicItem
    ): DynamicSummary {
        val cover = archive.cover.blankToNull().httpsImageUrlOrNull()
        val badge = archive.badgeCategoryList.firstNotNullOfOrNull { it.text.blankToNull() }
            ?: archive.badgeList.firstNotNullOfOrNull { it.text.blankToNull() }
        return DynamicSummary(
            body = DynamicBody.Archive(
                text = mapExtendDesc(item),
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
            image.src.blankToNull().httpsImageUrlOrNull()?.let { url ->
                DynamicImage(
                    url = url,
                    width = image.width.toInt(),
                    height = image.height.toInt()
                )
            }
        }
        val cover = images.firstOrNull()?.url
        val opusSummary = item.modulesList.firstOrNull { it.hasModuleOpusSummary() }?.moduleOpusSummary
            ?: item.extend.takeIf { it.hasOpusSummary() }?.opusSummary
        val text = mapPrimaryText(item) ?: mapExtendDesc(item)
        return DynamicSummary(
            body = DynamicBody.Draw(
                text = text,
                images = images
            ),
            title = opusSummary?.let(::mapOpusSummaryTitle) ?: text,
            cover = cover
        )
    }

    private fun mapArticle(
        article: MdlDynArticle,
        item: DynamicItem
    ): DynamicSummary {
        val cover = article.coversList.firstOrNull().blankToNull().httpsImageUrlOrNull()
        return DynamicSummary(
            body = DynamicBody.Article(
                text = mapExtendDesc(item),
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
        val cover = season.cover.blankToNull().httpsImageUrlOrNull()
        val badge = season.badgeList.firstNotNullOfOrNull { it.text.blankToNull() }
        return DynamicSummary(
            body = DynamicBody.Archive(
                text = mapExtendDesc(item),
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
                cover = live.cover.blankToNull().httpsImageUrlOrNull(),
                ownerName = item.extend.upName.blankToNull(),
                onlineText = live.coverLabel.blankToNull(),
                jumpFrom = LiveRouteTool.JUMP_FROM_HOME_RECOMMEND
            )
        }
        return DynamicSummary(
            body = DynamicBody.Live(
                text = mapExtendDesc(item),
                title = live.title.ifBlank { "直播动态" },
                cover = live.cover.blankToNull().httpsImageUrlOrNull(),
                subTitle = live.coverLabel.blankToNull() ?: live.coverLabel2.blankToNull(),
                badge = live.badge.text.blankToNull()
            ),
            title = live.title.blankToNull(),
            cover = live.cover.blankToNull().httpsImageUrlOrNull(),
            badge = live.badge.text.blankToNull(),
            liveRoute = route
        )
    }

    private fun mapLiveRcmd(
        live: MdlDynLiveRcmd,
        item: DynamicItem
    ): DynamicSummary {
        val content = live.content.blankToNull() ?: return DynamicSummary(
            body = DynamicBody.Unknown(mapExtendDesc(item))
        )
        val info = runCatching {
            JSONObject(content).optJSONObject("live_play_info")
        }.getOrNull() ?: return DynamicSummary(
            body = DynamicBody.Unknown(mapExtendDesc(item))
        )
        val roomId = info.optLong("room_id").takeIf { it > 0L }
        val title = info.optString("title").blankToNull()
        val cover = info.optString("cover").blankToNull().httpsImageUrlOrNull()
        val onlineText = info.optLong("online")
            .takeIf { it > 0L }
            ?.toString()
            ?.plus("人看")
        val ownerName = item.extend.upName.blankToNull()
        return DynamicSummary(
            body = DynamicBody.Live(
                text = mapExtendDesc(item),
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
                module.hasModuleOpusSummary() -> mapOpusSummaryText(module.moduleOpusSummary)
                else -> null
            }
        } ?: mapExtendDesc(origin)
        val originDynamic = origin.modulesList.firstOrNull { it.hasModuleDynamic() }?.moduleDynamic
        val originTitle = when {
            originDynamic?.hasDynArchive() == true -> originDynamic.dynArchive.title.blankToNull()
            originDynamic?.hasDynArticle() == true -> originDynamic.dynArticle.title.blankToNull()
            originDynamic?.hasDynCommonLive() == true -> originDynamic.dynCommonLive.title.blankToNull()
            else -> null
        }
        val originCover = when {
            originDynamic?.hasDynArchive() == true -> originDynamic.dynArchive.cover.blankToNull().httpsImageUrlOrNull()
            originDynamic?.hasDynArticle() == true -> originDynamic.dynArticle.coversList.firstOrNull().blankToNull().httpsImageUrlOrNull()
            originDynamic?.hasDynDraw() == true -> originDynamic.dynDraw.itemsList.firstOrNull()?.src.blankToNull().httpsImageUrlOrNull()
            originDynamic?.hasDynCommonLive() == true -> originDynamic.dynCommonLive.cover.blankToNull().httpsImageUrlOrNull()
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
                text = mapPrimaryText(item) ?: mapExtendDesc(item),
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
                        cover = live.cover.blankToNull().httpsImageUrlOrNull(),
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
                epId?.let { ep ->
                    VideoTarget.Pgc(
                        epId = ep,
                        seasonId = archive.pgcseasonid.takeIf { id -> id > 0L },
                        subType = archive.subtype.takeIf { subType -> subType > 0 }
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
        const val OPUS_DETAIL_ENDPOINT = "bilibili.app.dynamic.v2.Opus/OpusDetail"
        const val SPACE_ENDPOINT = "bilibili.app.dynamic.v2.Dynamic/DynSpace"
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
