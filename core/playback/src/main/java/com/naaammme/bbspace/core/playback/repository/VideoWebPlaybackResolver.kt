package com.naaammme.bbspace.core.playback.repository

import com.naaammme.bbspace.core.settings.AppSettings
import com.naaammme.bbspace.core.model.PlaybackAudio
import com.naaammme.bbspace.core.model.PlaybackRequest
import com.naaammme.bbspace.core.model.PlaybackSource
import com.naaammme.bbspace.core.model.PlaybackStream
import com.naaammme.bbspace.core.model.QualityOption
import com.naaammme.bbspace.infra.web.WebPlayUrlClient
import com.naaammme.bbspace.infra.web.WebPlayUrlRequest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class VideoWebPlaybackResolver @Inject constructor(
    private val appSettings: AppSettings,
    private val webPlayUrlClient: WebPlayUrlClient
) {
    suspend fun fetchPlaybackSource(request: PlaybackRequest): PlaybackSource {
        val ids = request.ids
        val preferredCodecId = appSettings.preferredCodec.first().toVideoCodecId()
        val json = webPlayUrlClient.fetchPlayback(
            WebPlayUrlRequest(
                aid = ids.aid,
                bvid = ids.bvid?.takeIf(String::isNotBlank),
                cid = ids.cid
            )
        )
        return mapPlaybackSource(request, json, preferredCodecId)
    }

    private fun mapPlaybackSource(
        request: PlaybackRequest,
        json: JSONObject,
        preferredCodecId: Int
    ): PlaybackSource {
        val data = json.optJSONObject("data") ?: error("web 取流缺少 data")
        val dash = data.optJSONObject("dash") ?: error("web 取流缺少 dash")
        val descriptions = buildQualityDescriptions(data)
        val audios = buildAudios(dash)
        val streams = buildStreams(dash, descriptions, preferredCodecId)
        if (streams.isEmpty()) error("web 取流没有可用视频流")

        return PlaybackSource(
            biz = request.playable.biz.biz,
            durationMs = data.optLong("timelength"),
            streams = streams,
            audios = audios,
            qualityOptions = streams.map(::mapQuality).distinctBy(QualityOption::quality),
            resumePositionMs = request.seekToMs,
            isPreview = false,
            supportProject = false,
            supplementType = null
        )
    }

    private fun buildStreams(
        dash: JSONObject,
        descriptions: Map<Int, String>,
        preferredCodecId: Int
    ): List<PlaybackStream> {
        val videos = dash.optJSONArray("video") ?: return emptyList()
        val byQuality = linkedMapOf<Int, MutableList<PlaybackStream.Dash>>()
        for (i in 0 until videos.length()) {
            val item = videos.optJSONObject(i) ?: continue
            val videoUrl = item.optString("baseUrl").ifBlank { item.optString("base_url") }
            if (videoUrl.isBlank()) continue
            val quality = item.optInt("id")
            byQuality.getOrPut(quality) { mutableListOf() }.add(
                PlaybackStream.Dash(
                    quality = quality,
                    format = "dash",
                    description = descriptions[quality] ?: "画质 $quality",
                    width = item.optInt("width").takeIf { it > 0 },
                    height = item.optInt("height").takeIf { it > 0 },
                    mimeType = item.optString("mimeType").ifBlank {
                        item.optString("mime_type").ifBlank { "video/mp4" }
                    },
                    needVip = false,
                    needLogin = false,
                    supportDrm = false,
                    videoUrl = videoUrl,
                    videoBackupUrls = item.optStringList("backupUrl", "backup_url"),
                    audioId = null,
                    bandwidth = item.optInt("bandwidth"),
                    codecId = item.optInt("codecid"),
                    frameRate = item.optString("frameRate").ifBlank {
                        item.optString("frame_rate").takeIf(String::isNotBlank)
                    }
                )
            )
        }
        return buildList {
            byQuality.values.forEach { group ->
                val (preferred, others) = group.partition { it.codecId == preferredCodecId }
                addAll(preferred)
                addAll(others)
            }
        }
    }

    private fun Int.toVideoCodecId(): Int {
        return when (this) {
            1 -> CODECID_AVC
            2 -> CODECID_HEVC
            3 -> CODECID_AV1
            else -> CODECID_HEVC
        }
    }

    private fun buildAudios(dash: JSONObject): List<PlaybackAudio> {
        val audios = dash.optJSONArray("audio") ?: return emptyList()
        return buildList {
            for (i in 0 until audios.length()) {
                val item = audios.optJSONObject(i) ?: continue
                val audioUrl = item.optString("baseUrl").ifBlank { item.optString("base_url") }
                if (audioUrl.isBlank()) continue
                add(
                    PlaybackAudio(
                        id = item.optInt("id"),
                        url = audioUrl,
                        backupUrls = item.optStringList("backupUrl", "backup_url"),
                        bandwidth = item.optInt("bandwidth"),
                        codecId = item.optInt("codecid"),
                        mimeType = item.optString("mimeType").ifBlank {
                            item.optString("mime_type").ifBlank { "audio/mp4" }
                        }
                    )
                )
            }
        }
    }

    private fun buildQualityDescriptions(data: JSONObject): Map<Int, String> {
        val qualities = data.optJSONArray("accept_quality") ?: return emptyMap()
        val descriptions = data.optJSONArray("accept_description") ?: return emptyMap()
        return buildMap {
            val count = minOf(qualities.length(), descriptions.length())
            for (i in 0 until count) {
                put(qualities.optInt(i), descriptions.optString(i))
            }
        }
    }

    private fun mapQuality(stream: PlaybackStream): QualityOption {
        val shortLabel = stream.description.substringAfterLast(' ').ifBlank { stream.description }
        return QualityOption(
            quality = stream.quality,
            format = stream.format,
            description = stream.description,
            displayDescription = shortLabel,
            needVip = stream.needVip,
            needLogin = stream.needLogin,
            vipFree = false,
            supportDrm = stream.supportDrm,
            limit = null
        )
    }

    private fun JSONObject.optStringList(
        primaryName: String,
        fallbackName: String
    ): List<String> {
        val primary = optJSONArray(primaryName)
        val fallback = optJSONArray(fallbackName)
        return (primary ?: fallback).toStringList()
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                val value = optString(i)
                if (value.isNotBlank()) add(value)
            }
        }.distinct()
    }

    private companion object {
        const val CODECID_AVC = 7
        const val CODECID_HEVC = 12
        const val CODECID_AV1 = 13
    }
}
