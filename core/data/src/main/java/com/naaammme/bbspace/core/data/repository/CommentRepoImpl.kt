package com.naaammme.bbspace.core.data.repository

import android.text.format.DateFormat
import com.bapis.bilibili.main.community.reply.v1.Emote as ReplyEmote
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
import com.google.protobuf.MessageLite
import com.google.protobuf.Parser
import com.naaammme.bbspace.core.common.AuthProvider
import com.naaammme.bbspace.core.common.BiliConstants
import com.naaammme.bbspace.core.domain.comment.CommentRepository
import com.naaammme.bbspace.core.model.COMMENT_FILTER_ALL
import com.naaammme.bbspace.core.model.CommentEmote
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
import com.naaammme.bbspace.infra.grpc.GrpcFrameCodec
import com.naaammme.bbspace.infra.grpc.GrpcHeaderBuilder
import com.naaammme.bbspace.infra.network.BiliApiException
import com.naaammme.bbspace.infra.network.BiliRestClient
import com.naaammme.bbspace.infra.network.BiliRestParamBuilder
import com.naaammme.bbspace.infra.network.BiliRestProfile
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Calendar

@Singleton
class CommentRepoImpl @Inject constructor(
    private val grpcClient: BiliGrpcClient,
    private val okHttpClient: OkHttpClient,
    private val grpcHeaderBuilder: GrpcHeaderBuilder,
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
        val reply = callCommentGrpc(
            endpoint = ENDPOINT,
            requestBytes = buildReq(subject, sort, filterTag, offset).toByteArray(),
            parser = MainListReply.parser()
        )
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
        val reply = callCommentGrpc(
            endpoint = DETAIL_ENDPOINT,
            requestBytes = buildDetailReq(
                subject = subject,
                rootRpid = rootRpid,
                sort = sort,
                offset = offset
            ).toByteArray(),
            parser = DetailListReply.parser()
        )
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
        val reply = callCommentGrpc(
            endpoint = TRANSLATE_ENDPOINT,
            requestBytes = TranslateReplyReq.newBuilder()
                .setType(subject.type)
                .setOid(subject.oid)
                .addRpids(rpid)
                .build()
                .toByteArray(),
            parser = TranslateReplyResp.parser()
        )
        return withContext(Dispatchers.Default) {
            reply.translatedRepliesMap[rpid]?.translatedContent?.message?.trim()
        }
    }

    private suspend fun <Resp : MessageLite> callCommentGrpc(
        endpoint: String,
        requestBytes: ByteArray,
        parser: Parser<Resp>
    ): Resp {
        if (authProvider.accessToken.isNotBlank()) {
            return grpcClient.call(
                endpoint = endpoint,
                requestBytes = requestBytes,
                parser = parser
            )
        }
        val encodeResult = GrpcFrameCodec.encode(requestBytes)
        val headers = grpcHeaderBuilder.build(compressed = encodeResult.compressed).toMutableMap().apply {
            // B站未登录时带上这两个头会拿不全评论，这里对评论接口单独去掉
            remove("x-bili-device-bin")
            remove("x-bili-metadata-bin")
        }
        val requestBuilder = Request.Builder()
            .url("${BiliConstants.BASE_URL_APP}/$endpoint")
            .post(encodeResult.frame.toRequestBody(null))
        headers.forEach { (key, value) ->
            requestBuilder.addHeader(key, value)
        }
        return withContext(Dispatchers.IO) {
            okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                if (response.code != 200) {
                    throw BiliApiException(response.code, "HTTP ${response.code}: ${response.message}")
                }
                val responseBytes = response.body?.bytes()
                    ?: throw BiliApiException(-1, "Empty gRPC response")
                val payload = GrpcFrameCodec.decode(responseBytes, response.header("grpc-encoding"))
                parser.parseFrom(payload)
            }
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
        val emotes = info.content.emotesMap.entries.mapNotNull { (token, emote) ->
            emote.toModel(token)
        }
        return CommentReply(
            rpid = info.id,
            message = info.content.message.trim(),
            likeCount = info.like,
            replyCount = info.count,
            timeText = formatReplyTime(info.ctime),
            locationText = formatReplyLocation(info.replyControl.location),
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
            emotes = emotes,
            pictures = pictures
        )
    }

    private fun mapPublishedReply(reply: JSONObject): CommentReply {
        val member = reply.optJSONObject("member")
        val content = reply.optJSONObject("content")
        val replyControl = reply.optJSONObject("reply_control")
        val vip = member?.optJSONObject("vip")
        val emotes = mapReplyEmotes(content?.optJSONObject("emotes"))
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
            timeText = formatReplyTime(reply.optLong("ctime")),
            locationText = formatReplyLocation(replyControl?.optString("location").orEmpty()),
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
            ),
            emotes = emotes
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

    private fun formatReplyTime(ctime: Long): String {
        if (ctime <= 0L) return ""
        val timeMs = ctime * 1000L
        val diffMs = (System.currentTimeMillis() - timeMs).coerceAtLeast(0L)
        if (diffMs < MINUTE_MS) return "刚刚"
        if (diffMs < HOUR_MS) return "${diffMs / MINUTE_MS}分钟前"
        if (diffMs < DAY_MS) return "${diffMs / HOUR_MS}小时前"
        val nowCal = Calendar.getInstance()
        val targetCal = Calendar.getInstance().apply {
            timeInMillis = timeMs
        }
        val dayDiff = dayDiff(targetCal, nowCal)
        return when {
            dayDiff == 1 -> "昨天 ${DateFormat.format("HH:mm", timeMs)}"
            dayDiff in 2..3 -> "${dayDiff}天前"
            nowCal.get(Calendar.YEAR) == targetCal.get(Calendar.YEAR) -> {
                DateFormat.format("MM-dd", timeMs).toString()
            }
            else -> DateFormat.format("yyyy-MM-dd", timeMs).toString()
        }
    }

    private fun formatReplyLocation(raw: String): String {
        return raw.trim()
            .replace("IP属地：", "")
            .replace("IP属地:", "")
            .trim()
    }

    private fun mapReplyEmotes(emotes: JSONObject?): List<CommentEmote> {
        if (emotes == null) return emptyList()
        val items = mutableListOf<CommentEmote>()
        val keys = emotes.keys()
        while (keys.hasNext()) {
            val token = keys.next()
            val emote = emotes.optJSONObject(token) ?: continue
            mapReplyEmote(token, emote)?.let(items::add)
        }
        return items
    }

    private fun mapReplyEmote(
        token: String,
        emote: JSONObject
    ): CommentEmote? {
        val url = emote.optString("url")
            .toHttps()
            .ifBlank { return null }
        return CommentEmote(
            text = emote.optString("text").ifBlank { token },
            url = url,
            size = emote.optLong("size").coerceAtLeast(1L)
        )
    }

    private fun ReplyEmote.toModel(token: String): CommentEmote? {
        val url = url.toHttps().ifBlank { return null }
        return CommentEmote(
            text = text.ifBlank { token },
            url = url,
            size = size.coerceAtLeast(1L)
        )
    }

    private fun dayDiff(
        from: Calendar,
        to: Calendar
    ): Int {
        val fromDay = (from.clone() as Calendar).apply { clearTime() }.timeInMillis
        val toDay = (to.clone() as Calendar).apply { clearTime() }.timeInMillis
        return ((toDay - fromDay) / DAY_MS).toInt()
    }

    private fun Calendar.clearTime() {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    private fun String.toHttps(): String {
        return replace("http://", "https://")
    }

    private companion object {
        const val MINUTE_MS = 60_000L
        const val HOUR_MS = 3_600_000L
        const val DAY_MS = 86_400_000L
        const val ENDPOINT = "bilibili.main.community.reply.v1.Reply/MainList"
        const val DETAIL_ENDPOINT = "bilibili.main.community.reply.v1.Reply/DetailList"
        const val TRANSLATE_ENDPOINT = "bilibili.main.community.reply.v1.Reply/TranslateReply"
        const val ADD_ENDPOINT = "/x/v2/reply/add"
        const val DEL_ENDPOINT = "/x/v2/reply/del"
    }
}
