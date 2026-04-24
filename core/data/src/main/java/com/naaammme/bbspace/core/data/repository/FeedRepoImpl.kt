package com.naaammme.bbspace.core.data.repository

import com.naaammme.bbspace.core.common.BiliConstants
import com.naaammme.bbspace.core.data.AppSettings
import com.naaammme.bbspace.core.data.AuthStore
import com.naaammme.bbspace.core.domain.feed.FeedRepository
import com.naaammme.bbspace.core.domain.feed.FeedResult
import com.naaammme.bbspace.core.model.DescButton
import com.naaammme.bbspace.core.model.FeedArgs
import com.naaammme.bbspace.core.model.FeedItem
import com.naaammme.bbspace.core.model.FeedToast
import com.naaammme.bbspace.core.model.InterestAge
import com.naaammme.bbspace.core.model.InterestChoose
import com.naaammme.bbspace.core.model.InterestGender
import com.naaammme.bbspace.core.model.InterestItem
import com.naaammme.bbspace.core.model.InterestSubItem
import com.naaammme.bbspace.core.model.LiveRoute
import com.naaammme.bbspace.core.model.LiveRouteTool
import com.naaammme.bbspace.core.model.PlayBiz
import com.naaammme.bbspace.core.model.RcmdReason
import com.naaammme.bbspace.core.model.ThreePointItem
import com.naaammme.bbspace.core.model.ThreePointReason
import com.naaammme.bbspace.core.model.VideoRoute
import com.naaammme.bbspace.core.model.VideoRouteTool
import java.net.URI
import com.naaammme.bbspace.infra.network.BiliRestClient
import com.naaammme.bbspace.infra.network.BiliRestParamBuilder
import com.naaammme.bbspace.infra.network.BiliRestProfile
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeedRepoImpl @Inject constructor(
    private val restClient: BiliRestClient,
    private val restParamBuilder: BiliRestParamBuilder,
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
        val profile = if (hdFeed) BiliRestProfile.HD else BiliRestProfile.APP
        val json = restClient.getSigned(
            url = "${BiliConstants.BASE_URL_APP}$FEED_ENDPOINT",
            params = buildParams(idx, pull, flush, profile, hdFeed),
            profile = profile
        )

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
        val profile = if (hdFeed) BiliRestProfile.HD else BiliRestProfile.APP
        val json = restClient.getSigned(
            url = "${BiliConstants.BASE_URL_APP}$FEED_ENDPOINT",
            params = buildParams(idx, pull, flush, profile, hdFeed) + mapOf(
                "interest_id" to interestId.toString(),
                "interest_result" to interestResult,
                "interest_pos_ids" to interestPosIds
            ),
            profile = profile
        )

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
    private suspend fun buildParams(
        idx: Long,
        pull: Boolean,
        flush: Int,
        profile: BiliRestProfile,
        hdFeed: Boolean
    ): Map<String, String> {
        val personalizedRcmd = appSettings.personalizedRcmd.first()
        val lessonsMode = appSettings.lessonsMode.first()
        val teenagersMode = appSettings.teenagersMode.first()
        val teenagersAge = appSettings.teenagersAge.first()
        val ts = System.currentTimeMillis() / 1000
        val normalToken = authStore.accessToken
        val hdToken = authStore.getHdAccessKeyForCurrent()
        val token = if (hdFeed) hdToken else normalToken
        return restParamBuilder.app(profile, ts, token) + buildMap { // TODO:feed首页获取视频流
            put("auto_refresh_state", "1")

            put("autoplay_card", "11")
            put("autoplay_timestamp", "0")

            put("client_attr", "0")
            put("column", "2")
            put("column_timestamp", "0")
            put("device_name", android.os.Build.MODEL)
            put("device_type", "0")
            put("disable_rcmd", if (personalizedRcmd) "0" else "1")
            put("flush", flush.toString())

            put("fnval", "272")
            put("fnver", "0")
            put("force_host", "0")
            put("fourk", "0")

            put("guidance", "0")
            put("https_url_req", "0")
            put("idx", idx.toString())
            put("inline_danmu", "2")
            put("inline_sound", "1")
            put("inline_sound_cold_state", "2")
            put("interest_id", "0")
            if (lessonsMode) {
                put("lessons_mode", "1")
            }
            put("login_event", if (token.isNotEmpty()) "0" else "1") // 会显著影响feed结果
            put("network", "wifi")
            put("open_event", if (idx == 0L) "cold" else "hot")

            put("player_extra_content", "{\"short_edge\":\"1080\",\"long_edge\":\"1920\"}")

            put("player_net", "1")
            put("pull", pull.toString())

            put("qn", "80")
            put("qn_policy", "0")

            put("recsys_mode", "0")
            put("splash_creative_id", "0")
            put("splash_id", "")
            if (teenagersMode) {
                put("teenagers_age", teenagersAge.toString())
                put("teenagers_mode", "1")
            }

            put("video_mode", "1")
            put("voice_balance", "1")
            put("volume_balance", "1")
        }
    }

    private val adCardGotos = setOf("banner", "ad_web_s", "ad_web", "ad", "ad_player")

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
        val item = obj.optJSONObject("item")
        val inline = item?.optJSONObject("inline_pgc")
        val card = inline ?: obj
        val args = card.optJSONObject("args") ?: obj.optJSONObject("args")
        val descBtn = card.optJSONObject("desc_button") ?: obj.optJSONObject("desc_button")
        val rcmd = card.optJSONObject("rcmd_reason_style") ?: obj.optJSONObject("rcmd_reason_style")
        val player = card.optJSONObject("player_args")
            ?: item?.optJSONObject("player_args")
            ?: obj.optJSONObject("player_args")
        val uri = card.optString("uri").ifBlank { obj.optString("uri") }
        val reportFlowData = card.optString("report_flow_data")
            .takeIf { it.isNotEmpty() }
            ?: obj.optString("report_flow_data")
            .takeIf { it.isNotEmpty() }
            ?: VideoRouteTool.arg(uri, "report_flow_data")
        val cardGoto = card.optString("card_goto").ifBlank { obj.optString("card_goto") }
        val goto = card.optString("goto").ifBlank { obj.optString("goto") }
        val param = card.optString("param").ifBlank { obj.optString("param") }
        val title = card.optString("title")
            .ifBlank { item?.optString("subtitle").orEmpty() }
            .ifBlank { obj.optString("title") }
        val cover = card.optString("cover")
            .ifBlank { item?.optString("large_cover").orEmpty() }
            .ifBlank { obj.optString("cover") }
            .replace("http://", "https://")
        val ownerName = descBtn?.optString("text")
            ?.takeIf { it.isNotEmpty() }
            ?: args?.optString("up_name")?.takeIf { it.isNotEmpty() }
        val isLive = isLiveCard(cardGoto, goto, uri, player)
        val biz = when {
            cardGoto == "ketang" || goto == "ketang" ||
                    uri.contains("/cheese/play/") -> PlayBiz.PUGV
            cardGoto == "bangumi" || goto == "bangumi" ||
                    cardGoto == "ad_ogv" || goto == "ad_ogv" ||
                    uri.contains("/bangumi/play/") -> PlayBiz.PGC
            else -> PlayBiz.UGC
        }
        val aid = if (biz == PlayBiz.UGC) {
            param.toLongOrNull() ?: VideoRouteTool.aid(uri)
        } else {
            VideoRouteTool.aid(uri)
        }
        val cid = player?.optLong("cid")
            ?.takeIf { it > 0L }
            ?: VideoRouteTool.cid(uri)
        val seasonId = player?.optLong("season_id")
            ?.takeIf { it > 0L }
            ?: VideoRouteTool.arg(uri, "season_id")?.toLongOrNull()
        val epId = when (biz) {
            PlayBiz.PGC -> param.toLongOrNull()
                ?: VideoRouteTool.epId(uri)
            PlayBiz.PUGV -> VideoRouteTool.epId(uri)
            PlayBiz.UGC -> null
        }
        val route = if (isLiveCard(cardGoto, goto, uri, player)) {
            null
        } else {
            val src = VideoRouteTool.feed(
                trackId = card.optString("track_id")
                    .takeIf { value -> value.isNotEmpty() }
                    ?: obj.optString("track_id").takeIf { value -> value.isNotEmpty() },
                reportFlowData = reportFlowData
            )
            when (biz) {
                PlayBiz.UGC -> {
                    if (aid != null) {
                        VideoRoute.Ugc(
                            aid = aid,
                            cid = cid ?: 0L,
                            bvid = player?.optString("bvid")?.takeIf { value -> value.isNotEmpty() }
                                ?: VideoRouteTool.bvid(uri),
                            src = src
                        )
                    } else {
                        null
                    }
                }

                PlayBiz.PGC -> {
                    epId?.let {
                        VideoRoute.Pgc(
                            epId = it,
                            seasonId = seasonId,
                            subType = player?.optInt("sub_type")?.takeIf { value -> value >= 0 },
                            src = src
                        )
                    }
                }

                PlayBiz.PUGV -> {
                    epId?.let {
                        VideoRoute.Pugv(
                            epId = it,
                            seasonId = seasonId,
                            src = src
                        )
                    }
                }
            }
        }
        val liveRoute = if (isLive) {
            resolveLiveRoomId(param, uri, player, args)?.let { roomId ->
                LiveRoute(
                    roomId = roomId,
                    title = title.takeIf { it.isNotBlank() },
                    cover = cover.takeIf { it.isNotBlank() },
                    ownerName = ownerName,
                    onlineText = card.optString("cover_left_text_1")
                        .takeIf { it.isNotEmpty() }
                        ?: obj.optString("cover_left_text_1").takeIf { it.isNotEmpty() },
                    jumpFrom = LiveRouteTool.JUMP_FROM_HOME_RECOMMEND
                )
            }
        } else {
            null
        }

        return FeedItem(
            cardType = card.optString("card_type").ifBlank { obj.optString("card_type") },
            cardGoto = cardGoto,
            goto = goto,
            param = param,
            uri = uri,
            title = title,
            cover = cover,
            coverLeftText1 = card.optString("cover_left_text_1")
                .takeIf { it.isNotEmpty() }
                ?: obj.optString("cover_left_text_1").takeIf { it.isNotEmpty() },
            coverLeftText2 = card.optString("cover_left_text_2")
                .takeIf { it.isNotEmpty() }
                ?: obj.optString("cover_left_text_2").takeIf { it.isNotEmpty() },
            coverRightText = card.optString("cover_right_text")
                .takeIf { it.isNotEmpty() }
                ?: obj.optString("cover_right_text").takeIf { it.isNotEmpty() },
            idx = obj.optLong("idx"),
            route = route,
            liveRoute = liveRoute,
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

    private fun isLiveCard(
        cardGoto: String,
        goto: String,
        uri: String,
        player: JSONObject?
    ): Boolean {
        return cardGoto == "live" ||
                goto == "live" ||
                player?.optInt("is_live") == 1 ||
                player?.optString("type") == "live" ||
                player?.optLong("room_id")?.takeIf { it > 0L } != null ||
                uri.contains("live.bilibili.com")
    }

    private fun resolveLiveRoomId(
        param: String,
        uri: String,
        player: JSONObject?,
        args: JSONObject?
    ): Long? {
        return player?.optLong("room_id")?.takeIf { it > 0L }
            ?: args?.optLong("room_id")?.takeIf { it > 0L }
            ?: param.toLongOrNull()
            ?: VideoRouteTool.arg(uri, "room_id")?.toLongOrNull()
            ?: runCatching {
                URI(uri).path
                    .orEmpty()
                    .trimEnd('/')
                    .substringAfterLast('/')
                    .toLongOrNull()
            }.getOrNull()
    }
}
