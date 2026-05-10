package com.naaammme.bbspace.core.data.repository

import com.bapis.bilibili.app.archive.middleware.v1.PlayerArgs
import com.bapis.bilibili.app.archive.middleware.v1.QnPolicy
import com.bapis.bilibili.app.listener.v1.DetailItem
import com.bapis.bilibili.app.listener.v1.PlayInfo
import com.bapis.bilibili.app.listener.v1.PlayItem
import com.bapis.bilibili.app.listener.v1.PlayURLReq
import com.bapis.bilibili.app.listener.v1.PlayURLResp
import com.bapis.bilibili.app.listener.v1.RcmdPlaylistReq
import com.bapis.bilibili.app.listener.v1.RcmdPlaylistResp
import com.bapis.bilibili.pagination.Pagination
import com.naaammme.bbspace.core.domain.listen.ListenRepository
import com.naaammme.bbspace.core.model.listen.ListenItem
import com.naaammme.bbspace.core.model.listen.ListenPlayInfo
import com.naaammme.bbspace.core.model.listen.ListenRcmdResult
import com.naaammme.bbspace.infra.grpc.BiliGrpcClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ListenRepoImpl @Inject constructor(
    private val grpcClient: BiliGrpcClient
) : ListenRepository {

    override suspend fun fetchRcmdPlaylist(needTopCards: Boolean): ListenRcmdResult {
        val req = RcmdPlaylistReq.newBuilder()
            .setFrom(RcmdPlaylistReq.RcmdFrom.INDEX_ENTRY)
            .setId(0L)
            .setNeedHistory(false)
            .setNeedTopCards(needTopCards)
            .setPlayerArgs(buildPlayerArgs())
            .build()
        val resp = grpcClient.call(
            endpoint = ENDPOINT_RCMD_PLAYLIST,
            requestBytes = req.toByteArray(),
            parser = RcmdPlaylistResp.parser()
        )
        return withContext(Dispatchers.Default) {
            ListenRcmdResult(
                items = resp.listList.map(::mapDetailItem),
                historyLen = resp.historyLen,
                hasMore = resp.hasNextPage(),
                nextPageToken = if (resp.hasNextPage()) resp.nextPage.next else ""
            )
        }
    }

    override suspend fun fetchRcmdPlaylistNext(nextToken: String): ListenRcmdResult {
        val req = RcmdPlaylistReq.newBuilder()
            .setFrom(RcmdPlaylistReq.RcmdFrom.INDEX_ENTRY)
            .setId(0L)
            .setNeedHistory(false)
            .setNeedTopCards(false)
            .setPlayerArgs(buildPlayerArgs())
            .setPage(
                Pagination.newBuilder()
                    .setPageSize(PAGE_SIZE)
                    .setNext(nextToken)
                    .build()
            )
            .build()
        val resp = grpcClient.call(
            endpoint = ENDPOINT_RCMD_PLAYLIST,
            requestBytes = req.toByteArray(),
            parser = RcmdPlaylistResp.parser()
        )
        return withContext(Dispatchers.Default) {
            ListenRcmdResult(
                items = resp.listList.map(::mapDetailItem),
                historyLen = resp.historyLen,
                hasMore = resp.hasNextPage(),
                nextPageToken = if (resp.hasNextPage()) resp.nextPage.next else ""
            )
        }
    }

    override suspend fun fetchPlayUrl(oid: Long, itemType: Int, subId: Long): ListenPlayInfo {
        val req = PlayURLReq.newBuilder()
            .setItem(
                PlayItem.newBuilder()
                    .setItemType(itemType)
                    .setOid(oid)
                    .addSubId(subId)
                    .build()
            )
            .setPlayerArgs(buildPlayerArgs())
            .build()
        val resp = grpcClient.call(
            endpoint = ENDPOINT_PLAY_URL,
            requestBytes = req.toByteArray(),
            parser = PlayURLResp.parser()
        )
        return withContext(Dispatchers.Default) {
            val info = resp.playerInfoMap[subId]
            val durationMs = info?.length ?: 0L
            val audioUrl = info
                ?.takeIf { it.infoCase == PlayInfo.InfoCase.PLAY_DASH }
                ?.playDash
                ?.audioList
                ?.firstOrNull()
                ?.baseUrl
                ?.takeIf { it.isNotEmpty() }
            ListenPlayInfo(
                playable = audioUrl != null,
                audioUrl = audioUrl,
                durationMs = durationMs
            )
        }
    }

    private fun mapDetailItem(detail: DetailItem): ListenItem {
        val arc = detail.arc
        val owner = detail.owner
        val stat = detail.stat
        return ListenItem(
            itemType = detail.item.itemType,
            oid = detail.item.oid,
            subId = detail.partsList.firstOrNull()?.subId ?: 0L,
            title = arc.title,
            cover = arc.cover,
            author = owner.name,
            authorMid = owner.mid,
            duration = arc.duration,
            statView = stat.view,
            statReply = stat.reply,
            message = detail.message
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
            .build()
    }

    private companion object {
        const val ENDPOINT_RCMD_PLAYLIST = "bilibili.app.listener.v1.Listener/RcmdPlaylist"
        const val ENDPOINT_PLAY_URL = "bilibili.app.listener.v1.Listener/PlayURL"
        const val DEFAULT_QN = 64L
        const val DEFAULT_FNVER = 0L
        const val DEFAULT_FNVAL = 272L
        const val DEFAULT_FORCE_HOST = 0L
        const val DEFAULT_VOICE_BALANCE = 1L
        const val DEFAULT_CLIENT_ATTR = 0L
        const val PAGE_SIZE = 20
    }
}
