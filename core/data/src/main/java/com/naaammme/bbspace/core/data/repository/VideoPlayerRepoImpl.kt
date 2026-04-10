package com.naaammme.bbspace.core.data.repository

import com.bapis.bilibili.app.playerunite.v1.PlayViewUniteReply
import com.bapis.bilibili.app.playerunite.v1.PlayViewUniteReq
import com.bapis.bilibili.playershared.BizType
import com.bapis.bilibili.playershared.CodeType
import com.bapis.bilibili.playershared.DashItem
import com.bapis.bilibili.playershared.DolbyItem
import com.bapis.bilibili.playershared.DashVideo
import com.bapis.bilibili.playershared.PlayCtrl
import com.bapis.bilibili.playershared.ResponseUrl
import com.bapis.bilibili.playershared.Stream
import com.bapis.bilibili.playershared.StreamInfo
import com.bapis.bilibili.playershared.VideoVod
import com.naaammme.bbspace.core.common.log.Logger
import com.naaammme.bbspace.core.data.AppSettings
import com.naaammme.bbspace.core.domain.player.VideoPlayerRepository
import com.naaammme.bbspace.core.model.PlayBiz
import com.naaammme.bbspace.core.model.PlaybackAudio
import com.naaammme.bbspace.core.model.PlaybackControlMode
import com.naaammme.bbspace.core.model.PlaybackRequest
import com.naaammme.bbspace.core.model.PlaybackSource
import com.naaammme.bbspace.core.model.PlaybackStream
import com.naaammme.bbspace.core.model.ProgressiveSegment
import com.naaammme.bbspace.core.model.QualityOption
import com.naaammme.bbspace.core.model.StreamLimitInfo
import com.naaammme.bbspace.core.model.VideoJumpTool
import com.naaammme.bbspace.infra.grpc.BiliGrpcClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoPlayerRepoImpl @Inject constructor(
    private val grpcClient: BiliGrpcClient,
    private val appSettings: AppSettings
) : VideoPlayerRepository {

    override suspend fun fetchPlaybackSource(request: PlaybackRequest): PlaybackSource {
        val reply = withContext(Dispatchers.IO) {
            val req = buildRequest(request)
            grpcClient.call(
                endpoint = ENDPOINT,
                requestBytes = req.toByteArray(),
                parser = PlayViewUniteReply.parser()
            )
        }
        return withContext(Dispatchers.Default) { mapReply(request, reply) }
    }

    private suspend fun buildRequest(request: PlaybackRequest): PlayViewUniteReq {
        val playable = request.playable
        val videoId = playable.videoId
        val enableHdrAnd8k = appSettings.enableHdrAnd8k.first()
        val needTrial = appSettings.needTrial.first()
        val preferredCodec = appSettings.preferredCodec.first()
        val fnval = if (enableHdrAnd8k) 4048 else 272

        val codecType = when (preferredCodec) {
            2 -> CodeType.CODE265
            3 -> CodeType.CODEAV1
            else -> CodeType.CODE264
        }

        val vod = VideoVod.newBuilder()
            .setAid(videoId.aid)
            .setCid(videoId.cid)
            .setQn(80)
            .setFnval(fnval)
            .setFnver(0)
            .setDownload(0)
            .setPreferCodecType(codecType)
            .setIsNeedTrial(needTrial)
            .build()

        val playCtrl = when (request.controlMode) {
            PlaybackControlMode.Simple -> PlayCtrl.PLAY_CTRL_SIMPLE
            PlaybackControlMode.Default -> PlayCtrl.PLAY_CTRL_DEFAULT
        }

        val builder = PlayViewUniteReq.newBuilder()
            .setVod(vod)
            .setSpmid(VideoJumpTool.SPMID)
            .setFromSpmid(playable.src.fromSpmid)
            .setFromScene(playable.fromScene)
            .setPlayCtrl(playCtrl)
            .putAllExtraContent(playable.getResolveExtraContent())

        playable.adExtra
            ?.takeIf(String::isNotBlank)
            ?.let(builder::setAdExtra)

        videoId.bvid
            ?.takeIf(String::isNotBlank)
            ?.let(builder::setBvid)

        return builder.build()
    }

    private fun mapReply(request: PlaybackRequest, reply: PlayViewUniteReply): PlaybackSource {
        val vodInfo = reply.vodInfo
        /*
        TODO:
        这里先信任 playunite 响应里的 playArc aid cid
        这样只传 epid 时也能把后续请求要用的 id 补齐
        如果后面只传 epid 不再稳定返回
        或 playArc 不再带 aid cid
        要改回先走 View 详情接口的 arc
         */
        val resolvedId = if (reply.hasPlayArc()) {
            request.videoId.copy(
                aid = reply.playArc.aid.takeIf { it > 0L } ?: request.videoId.aid,
                cid = reply.playArc.cid.takeIf { it > 0L } ?: request.videoId.cid
            )
        } else {
            request.videoId
        }
        val report = request.playable.getReportCommonParams().copy(
            aid = resolvedId.aid,
            cid = resolvedId.cid
        )
        val biz = if (reply.hasPlayArc()) mapBiz(reply.playArc.videoType) else report.biz
        val audios = buildList {
            addAll(vodInfo.dashAudioList.map(::mapAudio))
            if (vodInfo.hasDolby() && vodInfo.dolby.type != DolbyItem.Type.NONE) {
                addAll(vodInfo.dolby.audioList.map(::mapAudio))
            }
            if (vodInfo.hasLossLessItem() && vodInfo.lossLessItem.isLosslessAudio) {
                add(mapAudio(vodInfo.lossLessItem.audio))
            }
        }
        val streams = vodInfo.streamListList.mapNotNull { mapStream(it, audios) }
        val options = vodInfo.qnPanel.qnItemsList.mapNotNull { item ->
            when {
                item.hasStreamInfo() -> mapQuality(item.streamInfo)
                item.hasQnGroup() -> item.qnGroup.streamInfosList.firstOrNull()?.let(::mapQuality)
                else -> null
            }
        }.ifEmpty {
            vodInfo.streamListList.map { mapQuality(it.streamInfo) }
        }

        // 打印完整响应体用于调试
        Logger.d(TAG) { "Response biz=$biz videos=${streams.size} audios=${audios.size} supplement=${reply.supplement.typeUrl}" }
        streams.forEach { stream ->
            Logger.d(TAG) { "Stream - Q: ${stream.quality}, Format: ${stream.format}, Desc: ${stream.description}, W: ${stream.width}, H: ${stream.height}" }
        }
        audios.forEach { audio ->
            Logger.d(TAG) { "Audio - ID: ${audio.id}, Bandwidth: ${audio.bandwidth}, MimeType: ${audio.mimeType}" }
        }

        return PlaybackSource(
            videoId = resolvedId,
            biz = biz,
            report = report,
            durationMs = if (reply.hasPlayArc() && reply.playArc.durationMs > 0) {
                reply.playArc.durationMs
            } else {
                vodInfo.timelength
            },
            streams = streams,
            audios = audios,
            qualityOptions = options.distinctBy(QualityOption::quality),
            resumePositionMs = if (reply.hasHistory() && reply.history.hasCurrentVideo()) {
                reply.history.currentVideo.progress
            } else {
                request.seekToMs
            },
            isPreview = reply.hasPlayArc() && reply.playArc.isPreview,
            supportProject = vodInfo.supportProject,
            supplementType = reply.supplement.typeUrl.takeIf { reply.hasSupplement() && it.isNotBlank() }
        )
    }

    private fun mapStream(stream: Stream, audios: List<PlaybackAudio>): PlaybackStream? {
        val info = stream.streamInfo
        return when {
            stream.hasDashVideo() -> mapDashStream(info, stream.dashVideo, audios)
            stream.hasSegmentVideo() -> mapProgressiveStream(
                info,
                stream.segmentVideo.segmentList.mapNotNull(::mapResponseUrl)
            )
            stream.hasMultiDashVideo() -> stream.multiDashVideo.dashVideosList.firstOrNull()?.let {
                mapDashStream(info, it, audios)
            }
            else -> null
        }
    }

    private fun mapDashStream(
        info: StreamInfo,
        dash: DashVideo,
        audios: List<PlaybackAudio>
    ): PlaybackStream? {
        if (dash.baseUrl.isBlank()) return null
        return PlaybackStream.Dash(
            quality = info.quality,
            format = info.format,
            description = info.description.ifBlank { info.displayDesc },
            width = dash.width,
            height = dash.height,
            mimeType = "video/mp4",
            needVip = info.needVip,
            needLogin = info.needLogin,
            supportDrm = info.supportDrm,
            videoUrl = dash.baseUrl,
            videoBackupUrls = dash.backupUrlList,
            audioId = dash.audioId.takeIf { it > 0 && audios.any { audio -> audio.id == it } },
            bandwidth = dash.bandwidth,
            codecId = dash.codecid,
            frameRate = dash.frameRate.takeIf(String::isNotBlank)
        )
    }

    private fun mapProgressiveStream(
        info: StreamInfo,
        segments: List<ProgressiveSegment>
    ): PlaybackStream? {
        if (segments.isEmpty()) return null
        return PlaybackStream.Progressive(
            quality = info.quality,
            format = info.format,
            description = info.description.ifBlank { info.displayDesc },
            width = null,
            height = null,
            mimeType = "video/mp4",
            needVip = info.needVip,
            needLogin = info.needLogin,
            supportDrm = info.supportDrm,
            segments = segments
        )
    }

    private fun mapAudio(item: DashItem): PlaybackAudio {
        return PlaybackAudio(
            id = item.id,
            url = item.baseUrl,
            backupUrls = item.backupUrlList,
            bandwidth = item.bandwidth,
            codecId = item.codecid,
            mimeType = "audio/mp4"
        )
    }

    private fun mapResponseUrl(item: ResponseUrl): ProgressiveSegment? {
        val url = item.url.takeIf(String::isNotBlank) ?: return null
        return ProgressiveSegment(
            url = url,
            durationMs = item.length.takeIf { it > 0 }
        )
    }

    private fun mapQuality(info: StreamInfo): QualityOption {
        return QualityOption(
            quality = info.quality,
            format = info.format,
            description = info.description.ifBlank { info.displayDesc },
            displayDescription = info.displayDesc,
            needVip = info.needVip,
            needLogin = info.needLogin,
            vipFree = info.vipFree,
            supportDrm = info.supportDrm,
            limit = if (info.hasLimit()) {
                StreamLimitInfo(
                    title = info.limit.title,
                    message = info.limit.msg,
                    uri = info.limit.uri
                )
            } else {
                null
            }
        )
    }

    private fun mapBiz(type: BizType): PlayBiz {
        return when (type) {
            BizType.BIZ_TYPE_PGC -> PlayBiz.PGC
            BizType.BIZ_TYPE_PUGV -> PlayBiz.PUGV
            else -> PlayBiz.UGC
        }
    }

    private companion object {
        const val TAG = "PlayViewUnite"
        const val ENDPOINT = "bilibili.app.playerunite.v1.Player/PlayViewUnite"
    }
}
