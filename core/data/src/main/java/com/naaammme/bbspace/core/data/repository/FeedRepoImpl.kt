package com.naaammme.bbspace.core.data.repository

import com.naaammme.bbspace.core.common.BiliConstants
import com.naaammme.bbspace.core.data.AppSettings
import com.naaammme.bbspace.core.data.AuthStore
import com.naaammme.bbspace.core.domain.feed.FeedRepository
import com.naaammme.bbspace.core.domain.feed.FeedResult
import com.naaammme.bbspace.core.model.DescButton
import com.naaammme.bbspace.core.model.FeedArgs
import com.naaammme.bbspace.core.model.FeedItem
import com.naaammme.bbspace.core.model.FeedPlayerArgs
import com.naaammme.bbspace.core.model.FeedToast
import com.naaammme.bbspace.core.model.InterestAge
import com.naaammme.bbspace.core.model.InterestChoose
import com.naaammme.bbspace.core.model.InterestGender
import com.naaammme.bbspace.core.model.InterestItem
import com.naaammme.bbspace.core.model.InterestSubItem
import com.naaammme.bbspace.core.model.RcmdReason
import com.naaammme.bbspace.core.model.ThreePointItem
import com.naaammme.bbspace.core.model.ThreePointReason
import com.naaammme.bbspace.infra.network.BiliRestClient
import com.naaammme.bbspace.infra.crypto.AppSigner
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeedRepoImpl @Inject constructor(
    private val restClient: BiliRestClient,
    private val authStore: AuthStore,
    private val appSettings: AppSettings
) : FeedRepository {

    companion object {
        private const val FEED_ENDPOINT = "/x/v2/feed/index"
    }

    private val _toastFlow = MutableSharedFlow<FeedToast>(extraBufferCapacity = 1)
    override val toastFlow: SharedFlow<FeedToast> = _toastFlow

    override suspend fun fetchFeed(idx: Long, pull: Boolean, flush: Int): FeedResult {
        val hdFeed = appSettings.hdFeed.first()
        val appKey = if (hdFeed) BiliConstants.APP_KEY_HD else BiliConstants.APP_KEY
        val appSec = if (hdFeed) BiliConstants.APP_SEC_HD else BiliConstants.APP_SEC
        val reqParams = buildParams(idx, pull, flush, hdFeed)
        val signedQuery = AppSigner.sign(reqParams, appKey, appSec)
        val url ="${BiliConstants.BASE_URL_APP}$FEED_ENDPOINT?$signedQuery"

        val json = restClient.getUrl(fullUrl = url)

        return parseResponse(json)
    }

    override suspend fun fetchFeedWithInterest(
        idx: Long,
        pull: Boolean,
        flush: Int,
        interestId: Int,
        interestResult: String,
        interestPosIds: String
    ): FeedResult {
        val hdFeed = appSettings.hdFeed.first()
        val appKey = if (hdFeed) BiliConstants.APP_KEY_HD else BiliConstants.APP_KEY
        val appSec = if (hdFeed) BiliConstants.APP_SEC_HD else BiliConstants.APP_SEC
        val reqParams = buildParams(idx, pull, flush, hdFeed) + mapOf(
            "interest_id" to interestId.toString(),
            "interest_result" to interestResult,
            "interest_pos_ids" to interestPosIds
        )
        val signedQuery = AppSigner.sign(reqParams, appKey, appSec)
        val url = "${BiliConstants.BASE_URL_APP}$FEED_ENDPOINT?$signedQuery"

        val json = restClient.getUrl(fullUrl = url)

        return parseResponse(json)
    }

    private fun parseResponse(json: JSONObject): FeedResult {
        val data = json.optJSONObject("data")
        val items = parseItems(data?.optJSONArray("items"))
        val toast = data?.optJSONObject("toast")?.let { t ->
            if (t.optBoolean("has_toast")) {
                val msg = FeedToast(true, t.optString("toast_message"))
                _toastFlow.tryEmit(msg)
                msg
            } else null
        }
        val interestChoose = data?.optJSONObject("interest_choose")?.let { parseInterestChoose(it) }
        return FeedResult(items, toast, interestChoose)
    }

    /*
    disable_rcmd 个性化推荐
    interest_id 未登录个性化推荐
    client_attr 优先杜比hdr

    类型 picture,av,live,vertical_av,bangumi
     */
    private suspend fun buildParams(idx: Long, pull: Boolean, flush: Int, hdFeed: Boolean): Map<String, String> {
        val personalizedRcmd = appSettings.personalizedRcmd.first()
        val ts = (System.currentTimeMillis() / 1000).toString()
        val normalToken = authStore.accessToken
        val hdToken = authStore.getHdAccessKeyForCurrent()
        val token = if (hdFeed) hdToken else normalToken
        val mobiApp = if (hdFeed) BiliConstants.MOBI_APP_HD else BiliConstants.MOBI_APP
        val buildStr = if (hdFeed) BiliConstants.BUILD_STR_HD else BiliConstants.BUILD_STR
        val statistics = if (hdFeed) BiliConstants.STATISTICS_JSON_HD else BiliConstants.STATISTICS_JSON
        return buildMap { // TODO:feed首页获取视频流
            put("auto_refresh_state", "1")
            // put("autoplay_card", "2")
            // put("autoplay_timestamp", ts)
            put("build", buildStr)
            put("c_locale", "zh-Hans_CN")
            put("channel", BiliConstants.CHANNEL)
            put("client_attr", "0")
            put("column", "2")
            put("column_timestamp", "0")
            put("device_name", android.os.Build.MODEL)
            put("device_type", "0")
            put("disable_rcmd", if (personalizedRcmd) "0" else "1")
            put("flush", flush.toString())
            // put("fnval", "272")
            // put("fnver", "0")
            // put("force_host", "0")
            // put("fourk", "0")
            put("guidance", "0")
            put("https_url_req", "0")
            put("idx", idx.toString())
            put("interest_id", "0")
            put("login_event", if (token.isNotEmpty()) "0" else "1") // 会显著影响feed结果
            put("mobi_app", mobiApp)
            put("network", "wifi")
            put("open_event", if (idx == 0L) "cold" else "")
            put("platform", BiliConstants.PLATFORM)
            // player_extra_content
            put("player_net", "1")
            put("pull", pull.toString())
            // put("qn", "32")
            // qn_policy
            put("recsys_mode", "0")
            put("s_locale", "zh-Hans_CN")
            put("splash_creative_id", "0")
            put("splash_id", "")
            put("statistics", statistics)
            put("ts", ts)
            // video_mode
            // voice_balance
            // put("volume_balance", "1")
            if (token.isNotEmpty()) {
                put("access_key", token)
            }
        }
    }

    private val adCardGotos = setOf("banner", "ad_web_s", "ad_web", "ad")

    private fun parseItems(arr: org.json.JSONArray?): List<FeedItem> {
        if (arr == null) return emptyList()
        val result = mutableListOf<FeedItem>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            if (obj.optString("card_goto") in adCardGotos) continue
            result.add(parseFeedItem(obj))
        }
        return result
    }

    private fun parseFeedItem(obj: JSONObject): FeedItem {
        val args = obj.optJSONObject("args")
        val descBtn = obj.optJSONObject("desc_button")
        val rcmd = obj.optJSONObject("rcmd_reason_style")
        val player = obj.optJSONObject("player_args")

        return FeedItem(
            cardType = obj.optString("card_type"),
            cardGoto = obj.optString("card_goto"),
            goto = obj.optString("goto"),
            param = obj.optString("param"),
            uri = obj.optString("uri"),
            title = obj.optString("title"),
            cover = obj.optString("cover").replace("http://", "https://"),
            coverLeftText1 = obj.optString("cover_left_text_1").takeIf { it.isNotEmpty() },
            coverLeftText2 = obj.optString("cover_left_text_2").takeIf { it.isNotEmpty() },
            coverRightText = obj.optString("cover_right_text").takeIf { it.isNotEmpty() },
            idx = obj.optLong("idx"),
            trackId = obj.optString("track_id").takeIf { it.isNotEmpty() },
            descButton = descBtn?.let {
                DescButton(
                    text = it.optString("text"),
                    uri = it.optString("uri")
                )
            },
            rcmdReason = rcmd?.let {
                RcmdReason(
                    text = it.optString("text"),
                    textColor = it.optString("text_color").takeIf { s -> s.isNotEmpty() },
                    bgColor = it.optString("bg_color").takeIf { s -> s.isNotEmpty() },
                    textColorNight = it.optString("text_color_night").takeIf { s -> s.isNotEmpty() },
                    bgColorNight = it.optString("bg_color_night").takeIf { s -> s.isNotEmpty() }
                )
            },
            playerArgs = player?.let {
                FeedPlayerArgs(
                    aid = it.optLong("aid"),
                    cid = it.optLong("cid"),
                    duration = it.optInt("duration"),
                    type = it.optString("type").takeIf { s -> s.isNotEmpty() }
                )
            },
            args = args?.let {
                FeedArgs(
                    upId = it.optLong("up_id"),
                    upName = it.optString("up_name").takeIf { s -> s.isNotEmpty() },
                    tid = it.optInt("tid"),
                    tname = it.optString("tname").takeIf { s -> s.isNotEmpty() },
                    aid = it.optLong("aid")
                )
            },
            threePointV2 = obj.optJSONArray("three_point_v2")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    val item = arr.optJSONObject(i) ?: return@mapNotNull null
                    ThreePointItem(
                        title = item.optString("title"),
                        subtitle = item.optString("subtitle").takeIf { it.isNotEmpty() },
                        type = item.optString("type"),
                        reasons = parseReasonArray(item.optJSONArray("reasons")),
                        feedbacks = parseReasonArray(item.optJSONArray("feedbacks"))
                    )
                }
            }
        )
    }

    private fun parseReasonArray(arr: org.json.JSONArray?): List<ThreePointReason>? {
        if (arr == null || arr.length() == 0) return null
        return (0 until arr.length()).mapNotNull { j ->
            val r = arr.optJSONObject(j) ?: return@mapNotNull null
            ThreePointReason(id = r.optInt("id"), name = r.optString("name"), toast = r.optString("toast"))
        }
    }

    private fun parseInterestChoose(obj: JSONObject): InterestChoose {
        val gendersArr = obj.optJSONArray("genders")
        val genders = if (gendersArr != null) (0 until gendersArr.length()).map {
            val g = gendersArr.getJSONObject(it)
            InterestGender(id = g.optInt("id"), title = g.optString("title"))
        } else emptyList()
        val agesArr = obj.optJSONArray("ages")
        val ages = if (agesArr != null) (0 until agesArr.length()).map {
            val a = agesArr.getJSONObject(it)
            InterestAge(id = a.optInt("id"), title = a.optString("title"))
        } else emptyList()
        val itemsArr = obj.optJSONArray("items")
        val items = if (itemsArr != null) (0 until itemsArr.length()).map {
            val item = itemsArr.getJSONObject(it)
            val subArr = item.optJSONArray("sub_items")
            val subItems = if (subArr != null) (0 until subArr.length()).map { s ->
                val sub = subArr.getJSONObject(s)
                InterestSubItem(id = sub.optInt("id"), name = sub.optString("name"))
            } else emptyList()
            InterestItem(id = item.optInt("id"), name = item.optString("name"), icon = item.optString("icon"), subItems = subItems)
        } else emptyList()
        return InterestChoose(
            style = obj.optInt("style"),
            uniqueId = obj.optInt("unique_id"),
            title = obj.optString("title"),
            subTitle = obj.optString("sub_title"),
            confirmText = obj.optString("confirm_text"),
            genders = genders,
            genderTitle = obj.optString("gender_title"),
            ages = ages,
            ageTitle = obj.optString("age_title"),
            items = items
        )
    }
}
