package com.naaammme.bbspace.core.data.repository

import android.net.Uri
import android.text.Html
import com.bapis.bilibili.app.archive.middleware.v1.PlayerArgs
import com.bapis.bilibili.app.archive.middleware.v1.QnPolicy
import com.bapis.bilibili.pagination.Pagination
import com.bapis.bilibili.polymer.app.search.v1.FilterEntries
import com.bapis.bilibili.polymer.app.search.v1.FeedbackItem
import com.bapis.bilibili.polymer.app.search.v1.FeedbackSection
import com.bapis.bilibili.polymer.app.search.v1.FilterValue
import com.bapis.bilibili.polymer.app.search.v1.Item
import com.bapis.bilibili.polymer.app.search.v1.SearchAllRequest
import com.bapis.bilibili.polymer.app.search.v1.SearchAllResponse
import com.bapis.bilibili.polymer.app.search.v1.Sort
import com.naaammme.bbspace.core.model.SearchFeedbackItem as SearchFdItem
import com.naaammme.bbspace.core.model.SearchFeedbackSec
import com.naaammme.bbspace.core.model.SearchFilter
import com.naaammme.bbspace.core.model.SearchOp
import com.naaammme.bbspace.core.domain.search.SearchRepository
import com.naaammme.bbspace.core.model.SearchOrder
import com.naaammme.bbspace.core.model.SearchPage
import com.naaammme.bbspace.core.model.SearchReq
import com.naaammme.bbspace.core.model.SearchVideo
import com.naaammme.bbspace.infra.grpc.BiliGrpcClient
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchRepoImpl @Inject constructor(
    private val grpcClient: BiliGrpcClient
) : SearchRepository {

    override suspend fun search(req: SearchReq): SearchPage {
        val reqBody = SearchAllRequest.newBuilder()
            .setKeyword(req.keyword)
            .setOrder(req.order.toProto())
            .setTidList(req.filterMap[CATEGORY_KEY].orEmpty())
            .setDurationList(req.filterMap[DURATION_KEY].orEmpty())
            .setFromSource(FROM_SOURCE)
            .setLocalTime(localTime())
            .setPagination(
                Pagination.newBuilder()
                    .setPageSize(PAGE_SIZE)
                    .setNext(req.next)
                    .build()
            )
            .setPlayerArgs(buildPlayerArgs())
            .setUserAct(USER_ACT)
            .setPubTimeBeginS(req.time.beginS)
            .setPubTimeEndS(req.time.endS)
            .build()
            .toBuilder()

        if (req.filterMap.isNotEmpty()) {
            reqBody.putAllFilterMap(req.filterMap)
        }

        val resp = grpcClient.call(
            endpoint = ENDPOINT,
            requestBytes = reqBody.build().toByteArray(),
            parser = SearchAllResponse.parser()
        )

        return SearchPage(
            keyword = resp.keyword.ifBlank { req.keyword },
            trackId = resp.trackid,
            videos = resp.itemList.mapNotNull(::mapVideo),
            next = resp.pagination.next,
            filters = resp.searchFilter.filterEntriesList.mapNotNull(::mapFilter)
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

    private fun mapVideo(item: Item): SearchVideo? {
        if (item.cardItemCase != Item.CardItemCase.AV) return null
        val av = item.av
        val aid = item.param.toLongOrNull() ?: item.uri.toAid() ?: return null
        val cid = item.uri.toCid()
            ?: av.share.video.cid.takeIf { it > 0L }
            ?: return null
        return SearchVideo(
            aid = aid,
            cid = cid,
            title = av.title.cleanHtml(),
            cover = av.cover.replace("http://", "https://"),
            author = av.author,
            duration = av.duration,
            viewText = av.viewContent.ifBlank { av.play.toLong().formatCount() },
            danmakuText = av.danmaku.toLong().formatCount(),
            reason = av.takeIf { it.hasRcmdReason() }?.rcmdReason?.content?.takeIf(String::isNotBlank),
            feedbacks = av.feedback.sectionsList.mapNotNull(::mapFeedbackSec)
        )
    }

    private fun mapFeedbackSec(sec: FeedbackSection): SearchFeedbackSec? {
        val items = sec.itemsList.mapNotNull(::mapFeedbackItem)
        if (items.isEmpty()) return null
        return SearchFeedbackSec(
            title = sec.title,
            type = sec.type,
            items = items
        )
    }

    private fun mapFeedbackItem(item: FeedbackItem): SearchFdItem? {
        val text = item.text.ifBlank { return null }
        return SearchFdItem(
            id = item.id,
            text = text
        )
    }

    private fun mapFilter(entry: FilterEntries): SearchFilter? {
        val key = entry.filterType.ifBlank { return null }
        val ops = entry.valuesList.mapNotNull(::mapOp)
        if (ops.isEmpty()) return null
        return SearchFilter(
            key = key,
            title = entry.title.ifBlank { key },
            ops = ops,
            single = entry.singleSelect
        )
    }

    private fun mapOp(value: FilterValue): SearchOp? {
        val param = when (value.filterParamCase) {
            FilterValue.FilterParamCase.PARAM -> value.param
            FilterValue.FilterParamCase.SORT -> value.sort.toString()
            FilterValue.FilterParamCase.USER_SORT -> value.userSort.toString()
            FilterValue.FilterParamCase.CATEGORY_SORT -> value.categorySort.toString()
            else -> ""
        }
        if (param.isBlank()) return null
        return SearchOp(
            label = value.value.ifBlank { param },
            param = param,
            isDefault = value.subModuleForNeuron.equals(DEFAULT_SUB, ignoreCase = true)
        )
    }

    private fun SearchOrder.toProto(): Sort {
        return when (this) {
            SearchOrder.DEFAULT -> Sort.SORT_DEFAULT
            SearchOrder.VIEW -> Sort.SORT_VIEW_COUNT
            SearchOrder.PUBDATE -> Sort.SORT_PUBLISH_TIME
            SearchOrder.DANMAKU -> Sort.SORT_DANMAKU_COUNT
        }
    }

    private fun String.cleanHtml(): String {
        return Html.fromHtml(this, Html.FROM_HTML_MODE_LEGACY).toString().trim()
    }

    private fun String.toAid(): Long? {
        return Uri.parse(this).lastPathSegment?.toLongOrNull()
    }

    private fun String.toCid(): Long? {
        return Uri.parse(this).getQueryParameter("cid")?.toLongOrNull()
    }

    private fun Long?.formatCount(): String {
        val count = this ?: 0L
        return when {
            count >= 100_000_000L -> formatDecimal(count / 100_000_000f, "亿")
            count >= 10_000L -> formatDecimal(count / 10_000f, "万")
            else -> count.toString()
        }
    }

    private fun formatDecimal(value: Float, suffix: String): String {
        val text = String.format(Locale.ROOT, "%.1f", value)
            .trimEnd('0')
            .trimEnd('.')
        return "$text$suffix"
    }

    private fun localTime(): Int {
        return TimeZone.getDefault().rawOffset / 3_600_000
    }

    private companion object {
        const val ENDPOINT = "bilibili.polymer.app.search.v1.Search/SearchAll"
        const val FROM_SOURCE = "app_search"
        const val PAGE_SIZE = 20
        const val USER_ACT = "{\"act_seq\":[]}"
        const val DEFAULT_SUB = "default"
        const val CATEGORY_KEY = "category"
        const val DURATION_KEY = "duration"
        const val DEFAULT_QN = 64L
        const val DEFAULT_FNVER = 0L
        const val DEFAULT_FNVAL = 272L
        const val DEFAULT_FORCE_HOST = 0L
        const val DEFAULT_VOICE_BALANCE = 1L
        const val DEFAULT_CLIENT_ATTR = 0L
        const val SHORT_EDGE = "1080"
        const val LONG_EDGE = "1920"
    }
}
