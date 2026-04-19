package com.naaammme.bbspace.core.data.repository

import android.os.Build
import com.naaammme.bbspace.core.common.BiliConstants
import com.naaammme.bbspace.core.data.AuthStore
import com.naaammme.bbspace.core.domain.live.LiveRecommendRepository
import com.naaammme.bbspace.core.model.LiveRecommendItem
import com.naaammme.bbspace.core.model.LiveRecommendPage
import com.naaammme.bbspace.core.model.LiveRoute
import com.naaammme.bbspace.core.model.LiveRouteTool
import com.naaammme.bbspace.infra.network.BiliRestClient
import com.naaammme.bbspace.infra.network.BiliRestParamBuilder
import com.naaammme.bbspace.infra.network.BiliRestProfile
import java.net.URI
import java.net.URLDecoder
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class LiveRecommendRepoImpl @Inject constructor(
    private val restClient: BiliRestClient,
    private val restParamBuilder: BiliRestParamBuilder,
    private val authStore: AuthStore
) : LiveRecommendRepository {

    override suspend fun fetchRecommendPage(
        page: Int,
        relationPage: Int,
        isRefresh: Boolean,
        loginEvent: Int
    ): LiveRecommendPage {
        val ts = System.currentTimeMillis() / 1000L
        val json = restClient.getSigned(
            url = "${BiliConstants.BASE_URL_LIVE_API}$LIVE_FEED_ENDPOINT",
            params = restParamBuilder.app(BiliRestProfile.APP, ts, authStore.accessToken) + buildMap {
                put("actionKey", "appkey")
                put("ad_extra", "")
                put("device", BiliConstants.PLATFORM)
                put("device_name", Build.MODEL)
                put("fnval", "272")
                put("https_url_req", "0")
                put("is_refresh", if (isRefresh) "1" else "0")
                put("login_event", loginEvent.coerceAtLeast(0).toString())
                put("module_select", "0")
                put("network", "wifi")
                put("out_ad_name", "")
                put("page", page.coerceAtLeast(1).toString())
                put("qn", "0")
                put("relation_page", relationPage.coerceAtLeast(1).toString())
                put("scale", "xxhdpi")
                put("version", BiliConstants.VERSION)
            },
            profile = BiliRestProfile.APP
        )
        return parsePage(json)
    }

    private fun parsePage(json: JSONObject): LiveRecommendPage {
        val data = json.optJSONObject("data")
            ?: throw IllegalStateException("直播推荐缺少 data")
        return LiveRecommendPage(
            items = parseItems(data.optJSONArray("card_list")),
            hasMore = data.optInt("has_more") == 1,
            needRefresh = data.optInt("is_need_refresh") == 1,
            triggerTimeSec = data.optInt("trigger_time")
        )
    }

    private fun parseItems(cardList: JSONArray?): List<LiveRecommendItem> {
        if (cardList == null || cardList.length() == 0) return emptyList()
        return buildList {
            for (i in 0 until cardList.length()) {
                val wrapper = cardList.optJSONObject(i) ?: continue
                if (wrapper.optString("card_type") != SMALL_CARD_TYPE) continue
                val card = wrapper.optJSONObject("card_data")
                    ?.optJSONObject(SMALL_CARD_TYPE)
                    ?: continue
                if (card.optBoolean("is_ad")) continue
                parseItem(card)?.let(::add)
            }
        }
    }

    private fun parseItem(card: JSONObject): LiveRecommendItem? {
        val roomId = card.optLong("id").takeIf { it > 0L }
            ?: parseRoomId(card.optString("link"))
            ?: return null
        val link = card.optString("link")
        val jumpFrom = parseQueryInt(link, "live_from")
            ?: LiveRouteTool.JUMP_FROM_HOME_RECOMMEND
        val cover = card.optString("cover")
            .ifBlank { card.optString("system_cover") }
            .replace("http://", "https://")
        val ownerName = card.optString("uname")
            .takeIf { it.isNotBlank() }
            ?: card.optJSONObject("subtitle_style")
                ?.optString("text")
                ?.takeIf { it.isNotBlank() }
        val areaName = card.optString("area_name")
            .takeIf { it.isNotBlank() }
            ?: card.optJSONObject("cover_left_style")
                ?.optString("text")
                ?.takeIf { it.isNotBlank() }
        val onlineText = card.optJSONObject("cover_right_style")
            ?.optString("text")
            ?.takeIf { it.isNotBlank() }
            ?: card.optJSONObject("watched_show")
                ?.optString("text_large")
                ?.takeIf { it.isNotBlank() }

        return LiveRecommendItem(
            roomId = roomId,
            title = card.optString("title").ifBlank { "直播间 $roomId" },
            cover = cover,
            ownerName = ownerName,
            areaName = areaName,
            onlineText = onlineText,
            sessionId = card.optString("session_id").takeIf { it.isNotBlank() },
            route = LiveRoute(
                roomId = roomId,
                title = card.optString("title").takeIf { it.isNotBlank() },
                cover = cover.takeIf { it.isNotBlank() },
                ownerName = ownerName,
                onlineText = onlineText,
                jumpFrom = jumpFrom
            )
        )
    }

    private fun parseRoomId(link: String): Long? {
        if (link.isBlank()) return null
        return runCatching {
            URI(link).path
                .orEmpty()
                .trimEnd('/')
                .substringAfterLast('/')
                .toLongOrNull()
        }.getOrNull()
    }

    private fun parseQueryInt(
        link: String,
        key: String
    ): Int? {
        val value = parseQueryValue(link, key) ?: return null
        return value.toIntOrNull()
    }

    private fun parseQueryValue(
        link: String,
        key: String
    ): String? {
        if (link.isBlank()) return null
        val rawQuery = runCatching { URI(link).rawQuery }.getOrNull() ?: return null
        if (rawQuery.isBlank()) return null
        return rawQuery.split('&')
            .asSequence()
            .mapNotNull { part ->
                val idx = part.indexOf('=')
                if (idx <= 0) return@mapNotNull null
                val name = part.substring(0, idx)
                if (name != key) return@mapNotNull null
                URLDecoder.decode(part.substring(idx + 1), Charsets.UTF_8.name())
            }
            .firstOrNull()
    }

    private companion object {
        const val LIVE_FEED_ENDPOINT = "/xlive/app-interface/v2/index/feed"
        const val SMALL_CARD_TYPE = "small_card_v1"
    }
}
