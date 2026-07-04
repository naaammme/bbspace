package com.naaammme.bbspace.core.feed

import com.naaammme.bbspace.core.auth.AuthStore
import com.naaammme.bbspace.core.common.BiliConstants
import com.naaammme.bbspace.core.common.media.httpsImageUrl
import com.naaammme.bbspace.core.model.DescButton
import com.naaammme.bbspace.core.model.FeedArgs
import com.naaammme.bbspace.core.model.FeedDislikeContext
import com.naaammme.bbspace.core.model.FeedItem
import com.naaammme.bbspace.core.model.FeedToast
import com.naaammme.bbspace.core.model.InterestAge
import com.naaammme.bbspace.core.model.InterestChoose
import com.naaammme.bbspace.core.model.InterestGender
import com.naaammme.bbspace.core.model.InterestItem
import com.naaammme.bbspace.core.model.InterestSubItem
import com.naaammme.bbspace.core.model.LiveRoute
import com.naaammme.bbspace.core.model.LiveRouteTool
import com.naaammme.bbspace.core.model.RcmdReason
import com.naaammme.bbspace.core.model.ThreePointItem
import com.naaammme.bbspace.core.model.ThreePointReason
import com.naaammme.bbspace.core.model.ThreePointReasonKind
import com.naaammme.bbspace.core.model.VideoSrc
import com.naaammme.bbspace.core.model.VideoTarget
import com.naaammme.bbspace.core.model.VideoTargetTool
import com.naaammme.bbspace.core.settings.AppSettings
import com.naaammme.bbspace.infra.network.BiliRestClient
import com.naaammme.bbspace.infra.network.BiliRestParamBuilder
import com.naaammme.bbspace.infra.network.BiliRestProfile
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import org.json.JSONObject

data class FeedResult(val items: List<FeedItem>, val toast: FeedToast?, val interestChoose: InterestChoose? = null)

@Singleton
class FeedRepository @Inject constructor(
    private val restClient: BiliRestClient,
    private val restParamBuilder: BiliRestParamBuilder,
    private val authStore: AuthStore,
    private val appSettings: AppSettings
) {

    companion object {
        private const val FEED_ENDPOINT = "/x/v2/feed/index"
    }

    private val _toastFlow = MutableSharedFlow<FeedToast>(extraBufferCapacity = 1)
    val toastFlow: SharedFlow<FeedToast> = _toastFlow

    suspend fun fetchFeed(idx: Long, pull: Boolean, flush: Int): FeedResult {
        val hdFeed = appSettings.hdFeed.first()
        val profile = if (hdFeed) BiliRestProfile.HD else BiliRestProfile.APP
        val json = restClient.getSigned(
            url = "${BiliConstants.BASE_URL_APP}$FEED_ENDPOINT",
            params = buildParams(idx, pull, flush, profile, hdFeed),
            profile = profile
        )
        return parseResponse(json, hdFeed)
    }

    suspend fun fetchFeedWithInterest(
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
        return parseResponse(json, hdFeed)
    }

    private fun parseResponse(json: JSONObject, useHdProfile: Boolean): FeedResult {
        val data = json.optJSONObject("data")
        val items = parseItems(data?.optJSONArray("items"), useHdProfile)
        val toast = data?.optJSONObject("toast")?.let { t ->
            if (t.optBoolean("has_toast")) {
                val msg = FeedToast(true, t.optString("toast_message"))
                _toastFlow.tryEmit(msg)
                msg
            } else {
                null
            }
        }
        val interestChoose = data?.optJSONObject("interest_choose")?.let(::parseInterestChoose)
        return FeedResult(items, toast, interestChoose)
    }

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
        val token = if (hdFeed) authStore.getHdAccessKeyForCurrent() else authStore.accessToken
        val isColdStart = idx == 0L
        return restParamBuilder.app(profile, ts, token) + buildMap {
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
            put("login_event", when {
                !isColdStart -> "0"
                token.isNotEmpty() -> "2"
                else -> "1"
            })
            put("network", "wifi")
            put("open_event", if (isColdStart) "cold" else "hot")
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

    private val adCardGotos = setOf("banner", "ad_web_s", "ad_inline_egg", "ad_web", "ad_web_gif")

    private fun parseItems(arr: org.json.JSONArray?, useHdProfile: Boolean): List<FeedItem> {
        if (arr == null) return emptyList()
        val result = mutableListOf<FeedItem>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            if (obj.optString("card_goto") in adCardGotos) continue
            result.add(parseFeedItem(obj, useHdProfile))
        }
        return result
    }

    private fun parseFeedItem(obj: JSONObject, useHdProfile: Boolean): FeedItem {
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
            .takeIf(String::isNotEmpty)
            ?: obj.optString("report_flow_data").takeIf(String::isNotEmpty)
            ?: VideoTargetTool.arg(uri, "report_flow_data")
        val reportData = card.optString("report_data")
            .takeIf(String::isNotEmpty)
            ?: obj.optString("report_data").takeIf(String::isNotEmpty)
            ?: VideoTargetTool.arg(uri, "report_data")
        val cardGoto = card.optString("card_goto").ifBlank { obj.optString("card_goto") }
        val goto = card.optString("goto").ifBlank { obj.optString("goto") }
        val param = card.optString("param").ifBlank { obj.optString("param") }
        val title = card.optString("title")
            .ifBlank { item?.optString("subtitle").orEmpty() }
            .ifBlank { obj.optString("title") }
        val cover = card.optString("cover")
            .ifBlank { item?.optString("large_cover").orEmpty() }
            .ifBlank { obj.optString("cover") }
            .httpsImageUrl()
        val ownerName = descBtn?.optString("text")
            ?.takeIf(String::isNotEmpty)
            ?: args?.optString("up_name")?.takeIf(String::isNotEmpty)
        val isLive = isLiveCard(cardGoto, goto, uri, player)
        val isPugv = cardGoto == "ketang" || goto == "ketang"
        val isPgc = cardGoto == "bangumi" || goto == "bangumi" ||
            cardGoto == "ad_ogv" || goto == "ad_ogv"
        val ugcAid = if (!isPgc && !isPugv) {
            param.toLongOrNull() ?: VideoTargetTool.aid(uri)
        } else {
            null
        }
        val pugvAid = if (isPugv) {
            param.toLongOrNull() ?: args?.optLong("aid")?.takeIf { it > 0L }
        } else {
            null
        }
        val cid = player?.optLong("cid")?.takeIf { it > 0L } ?: VideoTargetTool.cid(uri)
        val seasonId = player?.optLong("season_id")
            ?.takeIf { it > 0L }
            ?: VideoTargetTool.arg(uri, "season_id")?.toLongOrNull()
        val pugvEpId = if (isPugv) VideoTargetTool.epId(uri) else null
        val pgcEpId = if (isPgc) param.toLongOrNull() ?: VideoTargetTool.epId(uri) else null
        val target = if (isLive) {
            null
        } else {
            val src = VideoTargetTool.feed(
                trackId = card.optString("track_id").takeIf(String::isNotEmpty)
                    ?: obj.optString("track_id").takeIf(String::isNotEmpty),
                reportFlowData = reportFlowData
            )
            when {
                isPugv -> {
                    if (pugvEpId == null && seasonId == null) {
                        null
                    } else {
                        VideoTarget.Pugv(
                            aid = pugvAid ?: 0L,
                            epId = pugvEpId ?: 0L,
                            seasonId = seasonId,
                            src = src
                        )
                    }
                }

                isPgc -> {
                    if (pgcEpId == null && seasonId == null) {
                        null
                    } else {
                        VideoTarget.Pgc(
                            epId = pgcEpId ?: 0L,
                            seasonId = seasonId,
                            subType = player?.optInt("sub_type")?.takeIf { it >= 0 },
                            src = src
                        )
                    }
                }

                else -> ugcAid?.let {
                    VideoTarget.Ugc(
                        aid = it,
                        cid = cid ?: 0L,
                        bvid = player?.optString("bvid")?.takeIf(String::isNotEmpty)
                            ?: VideoTargetTool.bvid(uri),
                        src = src
                    )
                }
            }
        }
        val liveRoute = if (isLive) {
            resolveLiveRoomId(param, player, args)?.let { roomId ->
                LiveRoute(
                    roomId = roomId,
                    title = title.takeIf(String::isNotBlank),
                    cover = cover.takeIf(String::isNotBlank),
                    ownerName = ownerName,
                    onlineText = card.optString("cover_left_text_1").takeIf(String::isNotEmpty)
                        ?: obj.optString("cover_left_text_1").takeIf(String::isNotEmpty),
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
            coverLeftText1 = card.optString("cover_left_text_1").takeIf(String::isNotEmpty)
                ?: obj.optString("cover_left_text_1").takeIf(String::isNotEmpty),
            coverLeftText2 = card.optString("cover_left_text_2").takeIf(String::isNotEmpty)
                ?: obj.optString("cover_left_text_2").takeIf(String::isNotEmpty),
            coverRightText = card.optString("cover_right_text").takeIf(String::isNotEmpty)
                ?: obj.optString("cover_right_text").takeIf(String::isNotEmpty),
            idx = obj.optLong("idx"),
            target = target,
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
                    textColor = it.optString("text_color").takeIf(String::isNotEmpty),
                    bgColor = it.optString("bg_color").takeIf(String::isNotEmpty),
                    textColorNight = it.optString("text_color_night").takeIf(String::isNotEmpty),
                    bgColorNight = it.optString("bg_color_night").takeIf(String::isNotEmpty)
                )
            },
            args = args?.let {
                FeedArgs(
                    upId = it.optLong("up_id"),
                    upName = it.optString("up_name").takeIf(String::isNotEmpty),
                    tid = it.optInt("tid"),
                    tname = it.optString("tname").takeIf(String::isNotEmpty),
                    aid = it.optLong("aid")
                )
            },
            threePointV2 = obj.optJSONArray("three_point_v2")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    val item = arr.optJSONObject(i) ?: return@mapNotNull null
                    ThreePointItem(
                        title = item.optString("title"),
                        subtitle = item.optString("subtitle").takeIf(String::isNotEmpty),
                        type = item.optString("type"),
                        reasons = parseReasonArray(
                            arr = item.optJSONArray("reasons"),
                            kind = item.optString("type").toReasonKind()
                        ),
                        feedbacks = parseReasonArray(
                            arr = item.optJSONArray("feedbacks"),
                            kind = ThreePointReasonKind.FEEDBACK
                        )
                    )
                }
            },
            dislikeContext = buildDislikeContext(
                param = param,
                goto = goto,
                useHdProfile = useHdProfile,
                src = target?.src,
                reportData = reportData,
                trackId = target?.src?.trackId,
                args = args
            )
        )
    }

    private fun parseReasonArray(
        arr: org.json.JSONArray?,
        kind: ThreePointReasonKind
    ): List<ThreePointReason>? {
        if (arr == null || arr.length() == 0) return null
        return (0 until arr.length()).mapNotNull { index ->
            val reason = arr.optJSONObject(index) ?: return@mapNotNull null
            ThreePointReason(
                id = reason.optInt("id"),
                name = reason.optString("name"),
                toast = reason.optString("toast"),
                extra = reason.optString("extend").takeIf(String::isNotEmpty),
                kind = kind
            )
        }
    }

    private fun buildDislikeContext(
        param: String,
        goto: String,
        useHdProfile: Boolean,
        src: VideoSrc?,
        reportData: String?,
        trackId: String?,
        args: JSONObject?
    ): FeedDislikeContext? {
        if (param.isBlank() || goto.isBlank()) return null
        val upId = args?.optLong("up_id")?.takeIf { it > 0L }
        val aid = args?.optLong("aid")?.takeIf { it > 0L }
        val tid = args?.optLong("tid")?.takeIf { it > 0L }
        return FeedDislikeContext(
            id = param,
            goto = goto,
            useHdProfile = useHdProfile,
            spmid = src?.fromSpmid?.takeIf(String::isNotBlank) ?: VideoTargetTool.FROM_SPMID_FEED,
            fromSpmid = src?.fromSpmid?.takeIf(String::isNotBlank) ?: VideoTargetTool.FROM_SPMID_FEED,
            fromModule = null,
            trackId = trackId?.takeIf(String::isNotBlank),
            reportData = reportData?.takeIf(String::isNotBlank),
            mid = upId,
            rid = aid,
            tagId = tid
        )
    }

    private fun String.toReasonKind(): ThreePointReasonKind {
        return when (this) {
            "feedback" -> ThreePointReasonKind.FEEDBACK
            else -> ThreePointReasonKind.DISLIKE
        }
    }

    private fun parseInterestChoose(obj: JSONObject): InterestChoose {
        val genders = obj.optJSONArray("genders")?.let { arr ->
            (0 until arr.length()).map {
                val item = arr.getJSONObject(it)
                InterestGender(id = item.optInt("id"), title = item.optString("title"))
            }
        } ?: emptyList()
        val ages = obj.optJSONArray("ages")?.let { arr ->
            (0 until arr.length()).map {
                val item = arr.getJSONObject(it)
                InterestAge(id = item.optInt("id"), title = item.optString("title"))
            }
        } ?: emptyList()
        val items = obj.optJSONArray("items")?.let { arr ->
            (0 until arr.length()).map {
                val item = arr.getJSONObject(it)
                val subItems = item.optJSONArray("sub_items")?.let { subArr ->
                    (0 until subArr.length()).map { subIndex ->
                        val sub = subArr.getJSONObject(subIndex)
                        InterestSubItem(id = sub.optInt("id"), name = sub.optString("name"))
                    }
                } ?: emptyList()
                InterestItem(
                    id = item.optInt("id"),
                    name = item.optString("name"),
                    icon = item.optString("icon"),
                    subItems = subItems
                )
            }
        } ?: emptyList()
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
        player: JSONObject?,
        args: JSONObject?
    ): Long? {
        return player?.optLong("room_id")?.takeIf { it > 0L }
            ?: args?.optLong("room_id")?.takeIf { it > 0L }
            ?: param.toLongOrNull()
    }
}
