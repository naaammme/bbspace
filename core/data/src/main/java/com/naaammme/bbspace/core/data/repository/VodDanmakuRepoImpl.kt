package com.naaammme.bbspace.core.data.repository

import com.bapis.bilibili.community.service.dm.v1.DmSegMobileReply
import com.bapis.bilibili.community.service.dm.v1.DmSegMobileReq
import com.naaammme.bbspace.core.common.log.Logger
import com.naaammme.bbspace.core.domain.danmaku.VodDanmakuRepository
import com.naaammme.bbspace.core.model.DanmakuItem
import com.naaammme.bbspace.core.model.VodDanmakuRequest
import com.naaammme.bbspace.core.model.VodDanmakuSegment
import com.naaammme.bbspace.infra.grpc.BiliGrpcClient
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class VodDanmakuRepoImpl @Inject constructor(
    private val grpcClient: BiliGrpcClient
) : VodDanmakuRepository {

    override suspend fun fetchSegment(request: VodDanmakuRequest): VodDanmakuSegment {
        val reply = withContext(Dispatchers.IO) {
            grpcClient.call(
                endpoint = ENDPOINT,
                requestBytes = buildRequest(request).toByteArray(),
                parser = DmSegMobileReply.parser()
            )
        }
        return withContext(Dispatchers.Default) {
            mapReply(request, reply)
        }
    }

    private fun buildRequest(request: VodDanmakuRequest): DmSegMobileReq {
        return DmSegMobileReq.newBuilder()
            .setPid(request.videoId.aid)
            .setOid(request.videoId.cid)
            .setType(TYPE_VIDEO)
            .setSegmentIndex(request.segmentIndex)
            .setTeenagersMode(0)
            .setPs(request.positionMs.coerceAtLeast(0L))
            .setPe(0L)
            .setPullMode(PULL_MODE)
            .setFromScene(FROM_SCENE)
            .setSpmid(SPMID)
            .setContextExt("""{"duration":${request.durationMs.coerceAtLeast(0L)}}""")
            .build()
    }

    private fun mapReply(
        request: VodDanmakuRequest,
        reply: DmSegMobileReply
    ): VodDanmakuSegment {
        val elems = reply.elemsList.map { elem ->
            DanmakuItem(
                id = elem.id,
                idStr = elem.idStr,
                progressMs = elem.progress,
                mode = elem.mode,
                fontSize = elem.fontsize,
                color = elem.color.toInt(),
                midHash = elem.midHash,
                content = elem.content,
                createdAtEpochSecond = elem.ctime,
                weight = elem.weight,
                action = elem.action,
                pool = elem.pool,
                attr = elem.attr,
                likeCount = elem.likeCount,
                animation = elem.animation,
                extra = elem.extra,
                colorfulType = elem.colorfulValue,
                type = elem.type,
                oid = elem.oid,
                dmFromType = elem.dmFromValue
            )
        }

        Logger.d(TAG) {
            "Loaded danmaku segment aid=${request.videoId.aid}, cid=${request.videoId.cid}, segment=${request.segmentIndex}, elems=${elems.size}"
        }

        return VodDanmakuSegment(
            request = request,
            items = elems,
            state = reply.state,
            segmentRules = reply.segmentRulesList,
            colorfulSources = reply.colorfulSrcList.associate { colorful ->
                colorful.typeValue to colorful.src
            },
            contextSrc = reply.contextSrc
        )
    }

    private companion object {
        const val TAG = "VodDanmakuRepo"
        const val ENDPOINT = "bilibili.community.service.dm.v1.DM/DmSegMobile"
        const val TYPE_VIDEO = 1
        const val PULL_MODE = 1
        const val FROM_SCENE = 11
        const val SPMID = "united.player-video-detail.0.0"
    }
}
