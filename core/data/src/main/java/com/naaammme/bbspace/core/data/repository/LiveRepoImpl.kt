package com.naaammme.bbspace.core.data.repository

import android.os.Build
import com.naaammme.bbspace.core.common.BiliConstants
import com.naaammme.bbspace.core.data.AuthStore
import com.naaammme.bbspace.core.domain.live.LiveRepository
import com.naaammme.bbspace.core.model.LivePlaybackSource
import com.naaammme.bbspace.core.model.LiveQualityOption
import com.naaammme.bbspace.core.model.LiveStatus
import com.naaammme.bbspace.infra.crypto.DeviceIdentity
import com.naaammme.bbspace.infra.network.BiliRestClient
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class LiveRepoImpl @Inject constructor(
    private val restClient: BiliRestClient,
    private val authStore: AuthStore,
    private val deviceIdentity: DeviceIdentity
) : LiveRepository {

    override suspend fun fetchPlaybackSource(
        roomId: Long,
        qn: Int
    ): LivePlaybackSource {
        val json = restClient.getSigned(
            url = "${BiliConstants.BASE_URL_LIVE_API}$ROOM_PLAY_INFO_ENDPOINT",
            params = buildParams(roomId, qn)
        )
        return parsePlaybackSource(roomId, json)
    }

    private fun buildParams(
        roomId: Long,
        qn: Int
    ): Map<String, String> {
        val ts = (System.currentTimeMillis() / 1000L).toString()
        return buildMap {
            put("build", BiliConstants.BUILD_STR)
            put("buvid", deviceIdentity.buvid)
            put("device", BiliConstants.PLATFORM)
            put("device_name", Build.MODEL)
            put("format", "0")
            put("free_type", "0")
            put("hdr_type", "0")
            put("http", "0")
            put("mobi_app", BiliConstants.MOBI_APP)
            put("network", "wifi")
            put("no_playurl", "0")
            put("only_audio", "0")
            put("only_video", "0")
            put("platform", BiliConstants.PLATFORM)
            put("play_type", "0")
            put("protocol", "0")
            put("qn", qn.coerceAtLeast(0).toString())
            put("room_id", roomId.toString())
            put("codec", "0")
            put("mask", "0")
            put("dolby", "0")
            put("special_scenario", "2")
            put("statistics", BiliConstants.STATISTICS_JSON)
            put("supported_drms", "0,3")
            put("ts", ts)
            put("version", BiliConstants.VERSION)
            authStore.accessToken.takeIf(String::isNotBlank)?.let { put("access_key", it) }
        }
    }

    private fun parsePlaybackSource(
        roomId: Long,
        json: JSONObject
    ): LivePlaybackSource {
        val data = json.optJSONObject("data")
            ?: throw IllegalStateException("直播取流缺少 data")
        val liveStatus = LiveStatus.from(data.optInt("live_status"))
        val playurl = data.optJSONObject("playurl_info")
            ?.optJSONObject("playurl")
            ?: throw NoPlayableStreamException(
                if (liveStatus == LiveStatus.Offline) "当前未开播" else "暂无可用直播流"
            )
        val qualityOptions = parseQualityOptions(playurl.optJSONArray("g_qn_desc"))
        val codec = findCodec(playurl.optJSONArray("stream"))
            ?: throw NoPlayableStreamException("暂无可用 FLV 直播流")
        val baseUrl = codec.optString("base_url")
        val urlInfoArr = codec.optJSONArray("url_info")
        val urls = buildUrls(baseUrl, urlInfoArr)
        val currentQn = codec.optInt("current_qn")
        val currentDesc = qualityOptions.firstOrNull { it.qn == currentQn }?.description
            ?: "原画"

        return LivePlaybackSource(
            roomId = roomId,
            liveStatus = liveStatus,
            currentQn = currentQn,
            currentDescription = currentDesc,
            qualityOptions = qualityOptions,
            protocol = "http_stream",
            format = "flv",
            codec = codec.optString("codec_name").ifBlank { "avc" },
            primaryUrl = urls.first(),
            backupUrls = urls.drop(1),
            session = codec.optString("session").takeIf(String::isNotBlank)
        )
    }

    private fun parseQualityOptions(arr: JSONArray?): List<LiveQualityOption> {
        if (arr == null || arr.length() == 0) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val qn = item.optInt("qn")
                if (qn <= 0 || any { it.qn == qn }) continue
                val desc = item.optString("desc").ifBlank { "画质 $qn" }
                add(
                    LiveQualityOption(
                        qn = qn,
                        description = desc
                    )
                )
            }
        }
    }

    private fun findCodec(streamArr: JSONArray?): JSONObject? {
        if (streamArr == null) return null
        return findCodec(streamArr, "http_stream", "flv", "avc")
            ?: findCodec(streamArr, "http_stream", "flv", null)
    }

    private fun findCodec(
        streamArr: JSONArray,
        protocolName: String,
        formatName: String,
        codecName: String?
    ): JSONObject? {
        for (i in 0 until streamArr.length()) {
            val stream = streamArr.optJSONObject(i) ?: continue
            if (stream.optString("protocol_name") != protocolName) continue
            val formats = stream.optJSONArray("format") ?: continue
            for (j in 0 until formats.length()) {
                val format = formats.optJSONObject(j) ?: continue
                if (format.optString("format_name") != formatName) continue
                val codecs = format.optJSONArray("codec") ?: continue
                for (k in 0 until codecs.length()) {
                    val codec = codecs.optJSONObject(k) ?: continue
                    if (codecName == null || codec.optString("codec_name") == codecName) {
                        return codec
                    }
                }
            }
        }
        return null
    }

    private fun buildUrls(
        baseUrl: String,
        urlInfoArr: JSONArray?
    ): List<String> {
        if (baseUrl.isBlank() || urlInfoArr == null || urlInfoArr.length() == 0) {
            throw NoPlayableStreamException("直播流地址为空")
        }
        return buildList {
            for (i in 0 until urlInfoArr.length()) {
                val info = urlInfoArr.optJSONObject(i) ?: continue
                val host = info.optString("host")
                val extra = info.optString("extra")
                if (host.isBlank() || extra.isBlank()) continue
                add(joinUrl(host, baseUrl, extra))
            }
        }.distinct().ifEmpty {
            throw NoPlayableStreamException("直播流地址为空")
        }
    }

    private fun joinUrl(
        host: String,
        baseUrl: String,
        extra: String
    ): String {
        return when {
            baseUrl.endsWith("?") || baseUrl.endsWith("&") -> host + baseUrl + extra
            baseUrl.contains('?') -> host + baseUrl + "&" + extra
            else -> host + baseUrl + "?" + extra
        }
    }

    private companion object {
        const val ROOM_PLAY_INFO_ENDPOINT = "/xlive/app-room/v2/index/getRoomPlayInfo"
    }
}

private class NoPlayableStreamException(
    message: String
) : IllegalStateException(message)
