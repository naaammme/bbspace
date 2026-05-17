package com.naaammme.bbspace.core.data.repository

import com.naaammme.bbspace.core.common.BiliConstants
import com.naaammme.bbspace.core.data.AuthStore
import com.naaammme.bbspace.core.domain.history.WatchLaterRepository
import com.naaammme.bbspace.core.model.VideoTarget
import com.naaammme.bbspace.core.model.VideoTargetTool
import com.naaammme.bbspace.core.model.WatchLaterCursor
import com.naaammme.bbspace.core.model.WatchLaterItem
import com.naaammme.bbspace.core.model.WatchLaterPage
import com.naaammme.bbspace.core.model.WatchLaterTab
import com.naaammme.bbspace.infra.network.BiliRestClient
import com.naaammme.bbspace.infra.network.BiliRestParamBuilder
import com.naaammme.bbspace.infra.network.BiliRestProfile
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Singleton
class WatchLaterRepoImpl @Inject constructor(
    private val restClient: BiliRestClient,
    private val restParamBuilder: BiliRestParamBuilder,
    private val authStore: AuthStore
) : WatchLaterRepository {

    override suspend fun fetchPage(
        tab: WatchLaterTab,
        asc: Boolean,
        cursor: WatchLaterCursor
    ): WatchLaterPage {
        val accessToken = authStore.accessToken
        check(accessToken.isNotBlank()) { "请先登录" }
        val ts = System.currentTimeMillis() / 1000
        val json = restClient.getSigned(
            url = "${BiliConstants.BASE_URL_API}$WATCH_LATER_LIST_ENDPOINT",
            params = restParamBuilder.app(BiliRestProfile.APP, ts, accessToken) + mapOf(
                "asc" to asc.toString(),
                "sort_field" to tab.sortField.toString(),
                "split_key" to cursor.splitKey,
                "start_key" to cursor.startKey
            ),
            profile = BiliRestProfile.APP
        )
        return withContext(Dispatchers.Default) {
            val data = json.getJSONObject("data")
            val nextKey = data.optString("next_key")
            val nextSplitKey = data.optString("split_key")
            WatchLaterPage(
                items = buildList {
                    val list = data.optJSONArray("list") ?: return@buildList
                    for (i in 0 until list.length()) {
                        mapItem(list.optJSONObject(i))?.let(::add)
                    }
                },
                cursor = WatchLaterCursor(startKey = nextKey, splitKey = nextSplitKey),
                hasMore = data.optBoolean("has_more"),
                countText = data.optString("show_count").blankToNull()
            )
        }
    }

    private fun mapItem(item: JSONObject?): WatchLaterItem? {
        item ?: return null
        val aid = item.optLong("aid")
        val cid = item.optLong("cid")
        val title = item.optString("title")
        val cardType = item.optInt("card_type")
        val uri = item.optString("uri")
        val key = buildString {
            append(cardType)
            append(':')
            append(aid)
            append(':')
            append(cid)
            append(':')
            append(item.optLong("add_at"))
        }
        if (cardType != CARD_TYPE_VIDEO) {
            return WatchLaterItem(
                key = key,
                cardType = cardType,
                title = title,
                intro = item.optString("desc").blankToNull(),
                cover = item.optString("pic").toHttps(),
                ownerName = null,
                viewText = null,
                danmakuText = null,
                durationSec = null,
                progressSec = null,
                addedAtSec = item.optLong("add_at"),
                badge = null,
                target = null
            )
        }
        val owner = item.optJSONObject("owner")
        val bvid = item.optString("bvid").blankToNull()
        return WatchLaterItem(
            key = key,
            cardType = cardType,
            title = title,
            intro = item.optString("desc").blankToNull(),
            cover = item.optString("pic").toHttps(),
            ownerName = owner?.optString("name").blankToNull(),
            viewText = item.optString("left_text").blankToNull(),
            danmakuText = item.optString("right_text").blankToNull(),
            durationSec = item.optLong("duration").takeIf { it > 0L },
            progressSec = item.optLong("progress").takeIf { it >= 0L },
            addedAtSec = item.optLong("add_at"),
            badge = item.optString("pgc_label").blankToNull(),
            target = buildTarget(aid, cid, uri, bvid, item.optJSONObject("page")?.optLong("cid") ?: 0L)
        )
    }

    private fun buildTarget(
        aid: Long,
        cid: Long,
        uri: String,
        bvid: String?,
        pageCid: Long
    ): VideoTarget? {
        val targetAid = aid.takeIf { it > 0L } ?: VideoTargetTool.aid(uri) ?: return null
        val targetCid = cid.takeIf { it > 0L } ?: pageCid.takeIf { it > 0L } ?: VideoTargetTool.cid(uri) ?: return null
        return VideoTarget.Ugc(
            aid = targetAid,
            cid = targetCid,
            bvid = bvid,
            src = WATCH_LATER_VIDEO_SRC
        )
    }

    private fun String?.blankToNull(): String? {
        return this?.takeIf { it.isNotBlank() }
    }

    private fun String?.toHttps(): String? {
        return this?.replace("http://", "https://")?.blankToNull()
    }

    private companion object {
        const val WATCH_LATER_LIST_ENDPOINT = "/x/v2/history/toview/v2/list"
        const val CARD_TYPE_VIDEO = 0
        val WATCH_LATER_VIDEO_SRC = VideoTargetTool.watchLater()
    }
}
