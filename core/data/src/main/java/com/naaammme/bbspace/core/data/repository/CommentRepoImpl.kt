package com.naaammme.bbspace.core.data.repository

import android.text.format.DateFormat
import com.bapis.bilibili.main.community.reply.v1.DetailListReply
import com.bapis.bilibili.main.community.reply.v1.DetailListReq
import com.bapis.bilibili.main.community.reply.v1.MainListReply
import com.bapis.bilibili.main.community.reply.v1.MainListReq
import com.bapis.bilibili.main.community.reply.v1.Mode
import com.bapis.bilibili.main.community.reply.v1.ReplyInfo
import com.bapis.bilibili.main.community.reply.v1.TranslateReplyReq
import com.bapis.bilibili.main.community.reply.v1.TranslateReplyResp
import com.bapis.bilibili.main.community.reply.v1.WordSearchParam
import com.bapis.bilibili.pagination.FeedPagination
import com.naaammme.bbspace.core.common.AuthProvider
import com.naaammme.bbspace.core.common.BiliConstants
import com.naaammme.bbspace.core.domain.comment.CommentRepository
import com.naaammme.bbspace.core.model.COMMENT_FILTER_ALL
import com.naaammme.bbspace.core.model.CommentFilterTag
import com.naaammme.bbspace.core.model.CommentMedal
import com.naaammme.bbspace.core.model.CommentPage
import com.naaammme.bbspace.core.model.CommentPicture
import com.naaammme.bbspace.core.model.CommentReply
import com.naaammme.bbspace.core.model.CommentReplyDetailPage
import com.naaammme.bbspace.core.model.CommentSort
import com.naaammme.bbspace.core.model.CommentSubject
import com.naaammme.bbspace.core.model.CommentUser
import com.naaammme.bbspace.infra.grpc.BiliGrpcClient
import com.naaammme.bbspace.infra.network.BiliRestClient
import com.naaammme.bbspace.infra.network.BiliRestParamBuilder
import com.naaammme.bbspace.infra.network.BiliRestProfile
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Singleton
class CommentRepoImpl @Inject constructor(
    private val grpcClient: BiliGrpcClient,
    private val restClient: BiliRestClient,
    private val restParamBuilder: BiliRestParamBuilder,
    private val authProvider: AuthProvider
) : CommentRepository {

    override suspend fun deleteReply(
        subject: CommentSubject,
        rpid: Long
    ) {
        val accessToken = authProvider.accessToken
        check(accessToken.isNotBlank()) { "请先登录" }
        val ts = System.currentTimeMillis() / 1000L
        restClient.postSigned(
            url = "${BiliConstants.BASE_URL_API}$DEL_ENDPOINT",
            params = restParamBuilder.app(BiliRestProfile.APP, ts, accessToken) + mapOf(
                "oid" to subject.oid.toString(),
                "type" to subject.type.toString(),
                "rpid" to rpid.toString()
            ),
            profile = BiliRestProfile.APP
        )
    }

    override suspend fun publishReply(
        subject: CommentSubject,
        message: String,
        rootRpid: Long,
        parentRpid: Long,
        sort: CommentSort
    ): CommentReply {
        val accessToken = authProvider.accessToken
        check(accessToken.isNotBlank()) { "请先登录" }
        val ts = System.currentTimeMillis() / 1000L
        val json = restClient.postSigned(
            url = "${BiliConstants.BASE_URL_API}$ADD_ENDPOINT",
            params = restParamBuilder.app(BiliRestProfile.APP, ts, accessToken) + buildMap {
                put("oid", subject.oid.toString())
                put("type", subject.type.toString())
                put("plat", "2")
                put("from", subject.source.from)
                put("message", message.trim())
                put("ordering", sort.toOrdering())
                put("scene", if (rootRpid > 0L || parentRpid > 0L) "detail" else subject.source.scene)
                put("sync_to_dynamic", "false")
                put("has_vote_option", "false")
                if (rootRpid > 0L) {
                    put("root", rootRpid.toString())
                }
                if (parentRpid > 0L) {
                    put("parent", parentRpid.toString())
                }
                subject.source.goTo?.takeIf(String::isNotBlank)?.let { value ->
                    put("goto", value)
                }
                subject.source.spmid.takeIf(String::isNotBlank)?.let { value ->
                    put("spmid", value)
                }
                subject.source.fromSpmid?.takeIf(String::isNotBlank)?.let { value ->
                    put("from_spmid", value)
                }
                subject.source.trackId?.takeIf(String::isNotBlank)?.let { value ->
                    put("track_id", value)
                }
            },
            profile = BiliRestProfile.APP
        )
        val reply = json.optJSONObject("data")
            ?.optJSONObject("reply")
            ?: error("发评响应缺少评论数据")
        return mapPublishedReply(reply)
    }

    override suspend fun fetchMainPage(
        subject: CommentSubject,
        sort: CommentSort,
        filterTag: String,
        offset: String
    ): CommentPage {
        val reply = withContext(Dispatchers.IO) {
            grpcClient.call(
                endpoint = ENDPOINT,
                requestBytes = buildReq(subject, sort, filterTag, offset).toByteArray(),
                parser = MainListReply.parser()
            )
        }
        return withContext(Dispatchers.Default) {
            mapPage(
                subject = subject,
                reqSort = sort,
                filterTag = filterTag,
                reply = reply
            )
        }
    }

    override suspend fun fetchReplyDetail(
        subject: CommentSubject,
        rootRpid: Long,
        sort: CommentSort,
        offset: String
    ): CommentReplyDetailPage {
        val reply = withContext(Dispatchers.IO) {
            grpcClient.call(
                endpoint = DETAIL_ENDPOINT,
                requestBytes = buildDetailReq(
                    subject = subject,
                    rootRpid = rootRpid,
                    sort = sort,
                    offset = offset
                ).toByteArray(),
                parser = DetailListReply.parser()
            )
        }
        return withContext(Dispatchers.Default) {
            val nextOffset = reply.paginationReply.nextOffset.ifBlank { null }
            val root = mapReply(reply.root) ?: error("评论详情缺少根评论")
            CommentReplyDetailPage(
                root = root,
                count = root.replyCount,
                sort = reply.mode.toModelSort(sort),
                canSwitchSort = reply.subjectControl.switcherType > 0L,
                items = mapChildReplies(reply.root.repliesList),
                nextOffset = nextOffset,
                hasMore = nextOffset != null
            )
        }
    }

    override suspend fun fetchTranslatedReply(
        subject: CommentSubject,
        rpid: Long
    ): String? {
        val reply = withContext(Dispatchers.IO) {
            grpcClient.call(
                endpoint = TRANSLATE_ENDPOINT,
                requestBytes = TranslateReplyReq.newBuilder()
                    .setType(subject.type)
                    .setOid(subject.oid)
                    .addRpids(rpid)
                    .build()
                    .toByteArray(),
                parser = TranslateReplyResp.parser()
            )
        }
        return withContext(Dispatchers.Default) {
            reply.translatedRepliesMap[rpid]?.translatedContent?.message?.trim()
        }
    }

    private fun buildReq(
        subject: CommentSubject,
        sort: CommentSort,
        filterTag: String,
        offset: String
    ): MainListReq {
        return MainListReq.newBuilder()
            .setOid(subject.oid)
            .setType(subject.type)
            .setExtra(buildExtra(subject))
            .setAdExtra("")
            .setRpid(0L)
            .setSeekRpid(0L)
            .setFilterTagName(filterTag.ifBlank { COMMENT_FILTER_ALL })
            .setMode(sort.toProto())
            .setPagination(
                FeedPagination.newBuilder()
                    .setOffset(offset)
                    .build()
            )
            .setWordSearchParam(
                WordSearchParam.newBuilder()
                    .setShownCount(0L)
                    .build()
            )
            .build()
    }

    private fun buildDetailReq(
        subject: CommentSubject,
        rootRpid: Long,
        sort: CommentSort,
        offset: String
    ): DetailListReq {
        return DetailListReq.newBuilder()
            .setOid(subject.oid)
            .setType(subject.type)
            .setRoot(rootRpid)
            .setRpid(0L)
            .setMode(sort.toProto())
            .setPagination(
                FeedPagination.newBuilder()
                    .setOffset(offset)
                    .build()
            )
            .setExtra(buildExtra(subject))
            .setAdExtra("")
            .setNeedSubjectTitle(false)
            .build()
    }

    private fun mapPage(
        subject: CommentSubject,
        reqSort: CommentSort,
        filterTag: String,
        reply: MainListReply
    ): CommentPage {
        val pinned = buildPinned(reply)
        val pinnedIds = pinned.mapTo(linkedSetOf()) { it.rpid }
        val items = pinned + reply.repliesList
            .mapNotNull { info -> mapReply(info) }
            .filterNot { it.rpid in pinnedIds }
        val filterTags = reply.subjectControl.supportFilterTagsList
            .mapNotNull { tag ->
                tag.name.takeIf(String::isNotBlank)?.let(::CommentFilterTag)
            }
        val tags = if (filterTags.isEmpty()) {
            emptyList()
        } else {
            buildList {
                add(CommentFilterTag(COMMENT_FILTER_ALL))
                addAll(filterTags.filterNot { it.name == COMMENT_FILTER_ALL })
            }
        }
        val nextOffset = reply.paginationReply.nextOffset.ifBlank { null }
        return CommentPage(
            subject = subject,
            title = reply.subjectControl.title.ifBlank { "评论" },
            count = reply.subjectControl.count,
            sort = reply.mode.toModelSort(reqSort),
            canSwitchSort = reply.subjectControl.switcherType > 0L,
            filterTags = tags,
            selectedFilter = filterTag.ifBlank { COMMENT_FILTER_ALL },
            items = items,
            nextOffset = nextOffset,
            hasMore = nextOffset != null,
            endText = reply.paginationEndText.ifBlank { null }
        )
    }

    private fun buildPinned(reply: MainListReply): List<CommentReply> {
        return uniqueReplies(buildList {
            reply.upTop.toReplyIfPresent("UP置顶")?.let(::add)
            reply.adminTop.toReplyIfPresent("官方置顶")?.let(::add)
            reply.voteTop.toReplyIfPresent("投票置顶")?.let(::add)
            reply.topRepliesList.forEach { info ->
                info.toReplyIfPresent(info.topLabel())?.let(::add)
            }
        })
    }

    private fun uniqueReplies(items: List<CommentReply>): List<CommentReply> {
        if (items.size < 2) return items
        val map = LinkedHashMap<Long, CommentReply>(items.size)
        items.forEach { reply ->
            map.putIfAbsent(reply.rpid, reply)
        }
        return map.values.toList()
    }

    private fun ReplyInfo.toReplyIfPresent(topLabel: String?): CommentReply? {
        if (id <= 0L) return null
        return mapReply(
            info = this,
            topLabel = topLabel
        )
    }

    private fun mapReply(
        info: ReplyInfo,
        topLabel: String? = null
    ): CommentReply? {
        if (info.id <= 0L) return null
        val user = info.member
        val name = user.name.ifBlank {
            if (info.mid > 0L) {
                "用户${info.mid}"
            } else {
                "匿名用户"
            }
        }
        val pictures = info.content.picturesList.mapNotNull { picture ->
            picture.imgSrc.takeIf(String::isNotBlank)?.let { url ->
                CommentPicture(
                    url = url.toHttps(),
                    width = picture.imgWidth.toFloat(),
                    height = picture.imgHeight.toFloat()
                )
            }
        }
        val medal = user.fansMedalName.takeIf(String::isNotBlank)?.let { medalName ->
            CommentMedal(
                name = medalName,
                level = user.fansMedalLevel.toInt()
            )
        }
        return CommentReply(
            rpid = info.id,
            message = info.content.message.trim(),
            likeCount = info.like,
            replyCount = info.count,
            timeText = info.replyControl.timeDesc.ifBlank { formatTime(info.ctime) },
            topLabel = topLabel ?: info.topLabel(),
            replyEntryText = info.replyControl.subReplyTitleText.ifBlank { null },
            parentName = info.parentReplyMember.name.ifBlank { null },
            user = CommentUser(
                mid = info.mid,
                name = name,
                face = user.face.toHttps().ifBlank { null },
                level = user.level.toInt().takeIf { it > 0 },
                vipLabel = user.vipLabelText.ifBlank { null },
                medal = medal
            ),
            pictures = pictures
        )
    }

    private fun mapPublishedReply(reply: JSONObject): CommentReply {
        val member = reply.optJSONObject("member")
        val content = reply.optJSONObject("content")
        val replyControl = reply.optJSONObject("reply_control")
        val vip = member?.optJSONObject("vip")
        val mid = reply.optLong("mid").takeIf { it > 0L }
            ?: member?.optString("mid")?.toLongOrNull()
            ?: 0L
        val name = member?.optString("uname").orEmpty().ifBlank {
            if (mid > 0L) {
                "用户$mid"
            } else {
                "匿名用户"
            }
        }
        return CommentReply(
            rpid = reply.optLong("rpid"),
            message = content?.optString("message").orEmpty().trim(),
            likeCount = reply.optLong("like"),
            replyCount = reply.optLong("count"),
            timeText = replyControl?.optString("time_desc").orEmpty().ifBlank {
                formatTime(reply.optLong("ctime"))
            },
            user = CommentUser(
                mid = mid,
                name = name,
                face = member?.optString("avatar").orEmpty().toHttps().ifBlank { null },
                level = member?.optJSONObject("level_info")
                    ?.optInt("current_level")
                    ?.takeIf { it > 0 },
                vipLabel = vip?.optJSONObject("label")
                    ?.optString("text")
                    ?.takeIf(String::isNotBlank)
            )
        )
    }

    private fun mapChildReplies(items: List<ReplyInfo>): List<CommentReply> {
        return items.asSequence()
            .filter { info ->
                info.id > 0L &&
                    !info.replyControl.invisible &&
                    !info.replyControl.blocked
            }
            .mapNotNull(::mapReply)
            .toList()
    }

    private fun ReplyInfo.topLabel(): String? {
        return when {
            replyControl.isUpTop -> "UP置顶"
            replyControl.isAdminTop -> "官方置顶"
            replyControl.isVoteTop -> "投票置顶"
            else -> null
        }
    }

    private fun CommentSort.toProto(): Mode {
        return when (this) {
            CommentSort.HOT -> Mode.MAIN_LIST_HOT
            CommentSort.TIME -> Mode.MAIN_LIST_TIME
        }
    }

    private fun CommentSort.toOrdering(): String {
        return when (this) {
            CommentSort.HOT -> "heat"
            CommentSort.TIME -> "time"
        }
    }

    private fun Mode.toModelSort(fallback: CommentSort): CommentSort {
        return when (this) {
            Mode.MAIN_LIST_TIME -> CommentSort.TIME
            Mode.MAIN_LIST_HOT, Mode.DEFAULT, Mode.UNSPECIFIED, Mode.UNRECOGNIZED -> {
                if (fallback == CommentSort.TIME) {
                    CommentSort.TIME
                } else {
                    CommentSort.HOT
                }
            }
        }
    }

    private fun buildExtra(subject: CommentSubject): String {
        val items = buildList {
            subject.source.spmid.takeIf(String::isNotBlank)?.let { value ->
                add("\"spmid\":\"${value.jsonEscape()}\"")
            }
            subject.source.fromSpmid?.takeIf(String::isNotBlank)?.let { value ->
                add("\"from_spmid\":\"${value.jsonEscape()}\"")
            }
            subject.source.trackId?.takeIf(String::isNotBlank)?.let { value ->
                add("\"track_id\":\"${value.jsonEscape()}\"")
            }
        }
        return if (items.isEmpty()) {
            ""
        } else {
            "{${items.joinToString(",")}}"
        }
    }

    private fun String.jsonEscape(): String {
        return buildString(length) {
            this@jsonEscape.forEach { ch ->
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(ch)
                }
            }
        }
    }

    private fun formatTime(ctime: Long): String {
        if (ctime <= 0L) return ""
        return DateFormat.format("yyyy-MM-dd HH:mm", ctime * 1000).toString()
    }

    private fun String.toHttps(): String {
        return replace("http://", "https://")
    }

    private companion object {
        const val ENDPOINT = "bilibili.main.community.reply.v1.Reply/MainList"
        const val DETAIL_ENDPOINT = "bilibili.main.community.reply.v1.Reply/DetailList"
        const val TRANSLATE_ENDPOINT = "bilibili.main.community.reply.v1.Reply/TranslateReply"
        const val ADD_ENDPOINT = "/x/v2/reply/add"
        const val DEL_ENDPOINT = "/x/v2/reply/del"
    }
}
