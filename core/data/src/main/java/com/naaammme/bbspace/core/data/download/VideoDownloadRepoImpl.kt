package com.naaammme.bbspace.core.data.download

import android.content.Context
import com.bapis.bilibili.app.playerunite.v1.PlayViewUniteReply
import com.bapis.bilibili.app.playerunite.v1.PlayViewUniteReq
import com.bapis.bilibili.community.service.dm.v1.DanmakuElem
import com.bapis.bilibili.community.service.dm.v1.DmSegCacheReq
import com.bapis.bilibili.community.service.dm.v1.DmSegMobileReply
import com.bapis.bilibili.playershared.CodeType
import com.bapis.bilibili.playershared.DashItem
import com.bapis.bilibili.playershared.DashVideo
import com.bapis.bilibili.playershared.DolbyItem
import com.bapis.bilibili.playershared.PlayCtrl
import com.bapis.bilibili.playershared.Stream
import com.bapis.bilibili.playershared.VideoVod
import com.naaammme.bbspace.core.common.UserAgentBuilder
import com.naaammme.bbspace.core.common.log.Logger
import com.naaammme.bbspace.core.domain.download.VideoDownloadRepository
import com.naaammme.bbspace.core.model.DanmakuItem
import com.naaammme.bbspace.core.model.DownloadDanmakuCache
import com.naaammme.bbspace.core.model.PlayBiz
import com.naaammme.bbspace.core.model.VideoDownloadEnqueueResult
import com.naaammme.bbspace.core.model.VideoDownloadKind
import com.naaammme.bbspace.core.model.VideoDownloadRequest
import com.naaammme.bbspace.core.model.VideoDownloadTask
import com.naaammme.bbspace.core.model.VideoDownloadTaskStatus
import com.naaammme.bbspace.infra.grpc.BiliGrpcClient
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request

@Singleton
class VideoDownloadRepoImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val grpcClient: BiliGrpcClient,
    private val okHttpClient: OkHttpClient,
    private val dao: VideoDownloadDao
) : VideoDownloadRepository {

    override val tasks: Flow<List<VideoDownloadTask>> = dao.observeAll()
        .map { list -> list.map(VideoDownloadEntity::toModel) }

    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val drainMutex = Mutex()
    @Volatile
    private var drainJob: Job? = null
    @Volatile
    private var runningTaskId: Long? = null
    @Volatile
    private var runningCall: Call? = null

    init {
        repoScope.launch {
            recoverRunningTasks()
            ensureDrain()
        }
    }

    override suspend fun enqueue(request: VideoDownloadRequest): VideoDownloadEnqueueResult {
        val now = System.currentTimeMillis()
        val entity = request.toEntity(createdAtMs = now)
        val insertedId = dao.insertIgnore(entity)
        if (insertedId > 0L) {
            ensureDrain()
            return VideoDownloadEnqueueResult.Enqueued(insertedId)
        }
        val key = request.key()
        val existingId = dao.findExistingId(
            biz = key.biz,
            aid = key.aid,
            cid = key.cid,
            epId = key.epId,
            seasonId = key.seasonId,
            kind = request.kind.name
        ) ?: error("下载任务已存在但未找到记录")
        return VideoDownloadEnqueueResult.AlreadyExists(existingId)
    }

    override suspend fun pause(taskId: Long) {
        val task = dao.find(taskId) ?: return
        when (VideoDownloadTaskStatus.valueOf(task.status)) {
            VideoDownloadTaskStatus.WAITING -> {
                dao.update(task.pause())
            }

            VideoDownloadTaskStatus.RUNNING -> {
                stopRunningTask(taskId)
                val latest = dao.find(taskId) ?: return
                dao.update(latest.pause())
                ensureDrain()
            }

            else -> Unit
        }
    }

    override suspend fun resume(taskId: Long) {
        val task = dao.find(taskId) ?: return
        if (VideoDownloadTaskStatus.valueOf(task.status) != VideoDownloadTaskStatus.PAUSED) return
        dao.update(task.waiting())
        ensureDrain()
    }

    override suspend fun delete(taskId: Long) {
        val task = dao.find(taskId) ?: return
        if (VideoDownloadTaskStatus.valueOf(task.status) == VideoDownloadTaskStatus.RUNNING) {
            stopRunningTask(taskId)
        }
        dao.delete(taskId)
        deleteTaskFiles(taskId)
        ensureDrain()
    }

    override fun task(taskId: Long): Flow<VideoDownloadTask?> {
        return dao.observe(taskId).map { it?.toModel() }
    }

    override suspend fun getTask(taskId: Long): VideoDownloadTask? {
        return dao.find(taskId)?.toModel()
    }

    override suspend fun loadDanmaku(taskId: Long): DownloadDanmakuCache? {
        val task = dao.find(taskId) ?: return null
        val aid = task.aid.takeIf { it > 0L } ?: return null
        val cid = task.cid.takeIf { it > 0L } ?: return null
        val file = File(taskDir(taskId), DANMAKU_FILE_NAME)
        if (!file.exists()) {
            return DownloadDanmakuCache(
                aid = aid,
                cid = cid,
                items = emptyList()
            )
        }

        val reply = withContext(Dispatchers.IO) {
            DmSegMobileReply.parseFrom(file.readBytes())
        }
        val items = withContext(Dispatchers.Default) {
            reply.elemsList.map(::mapDanmakuItem)
        }
        return DownloadDanmakuCache(
            aid = aid,
            cid = cid,
            items = items
        )
    }

    private suspend fun recoverRunningTasks() {
        dao.pauseRunningTasks(
            runningStatus = VideoDownloadTaskStatus.RUNNING.name,
            pausedStatus = VideoDownloadTaskStatus.PAUSED.name,
            message = "下载中断，已暂停"
        )
    }

    private fun ensureDrain() {
        if (drainJob?.isActive == true) return
        val job = repoScope.launch {
            drain()
        }
        drainJob = job
        job.invokeOnCompletion {
            if (drainJob === job) {
                drainJob = null
            }
        }
    }

    private suspend fun stopRunningTask(taskId: Long) {
        if (runningTaskId != taskId) return
        runningCall?.cancel()
        val job = drainJob ?: return
        if (job.isActive) {
            job.cancelAndJoin()
        }
    }

    private suspend fun drain() {
        drainMutex.withLock {
            while (true) {
                val task = dao.findFirstByStatus(VideoDownloadTaskStatus.WAITING.name) ?: break
                runningTaskId = task.id
                try {
                    runTask(task)
                } finally {
                    runningTaskId = null
                }
            }
        }
    }

    private suspend fun runTask(task: VideoDownloadEntity) {
        val pending = dao.find(task.id) ?: return
        if (pending.status != VideoDownloadTaskStatus.WAITING.name) return
        var cur = pending.prepareForRun()
        dao.update(cur)
        val dir = taskDir(task.id)
        try {
            if (!dir.exists() && !dir.mkdirs()) {
                error("创建下载目录失败")
            }

            val source = fetchSource(cur.toRequest())
            val resolved = cur.withResolvedVideoId(
                aid = source.aid,
                cid = source.cid
            )
            if (resolved != cur) {
                cur = resolved
                dao.update(cur)
            }
            cacheDanmaku(
                task = cur,
                source = source,
                dir = dir
            )
            val stream = source.selectStream(cur.videoQuality)
            val audio = source.selectAudio(stream, cur.audioQuality)

            when (VideoDownloadKind.from(cur.kind)) {
                VideoDownloadKind.VIDEO -> {
                    val picked = stream ?: error("没有可下载视频流")
                    val cdn = buildCdns(picked, audio).firstOrNull() ?: error("下载地址无效")
                    val videoFile = File(dir, VIDEO_FILE_NAME)
                    downloadFile(cdn.videoUrl, videoFile) { done, total ->
                        cur = cur.downloading("视频", done, total)
                        dao.update(cur)
                    }
                    val audioFile = cdn.audioUrl?.let { audioUrl ->
                        File(dir, AUDIO_FILE_NAME).also { file ->
                            downloadFile(audioUrl, file) { done, total ->
                                cur = cur.downloading("音频", done, total)
                                dao.update(cur)
                            }
                        }
                    }
                    cur = cur.finish(
                        videoPath = videoFile.absolutePath,
                        audioPath = audioFile?.absolutePath,
                        durationMs = source.durationMs
                    )
                    dao.update(cur)
                }

                VideoDownloadKind.AUDIO -> {
                    val picked = audio ?: error("没有可下载音频流")
                    val audioUrl = picked.urls().firstOrNull() ?: error("没有可下载音频流")
                    val audioFile = File(dir, AUDIO_ONLY_FILE_NAME)
                    downloadFile(audioUrl, audioFile) { done, total ->
                        cur = cur.downloading("音频", done, total)
                        dao.update(cur)
                    }
                    cur = cur.finish(
                        videoPath = null,
                        audioPath = audioFile.absolutePath,
                        durationMs = source.durationMs
                    )
                    dao.update(cur)
                }
            }
        } catch (c: CancellationException) {
            throw c
        } catch (e: IOException) {
            currentCoroutineContext().ensureActive()
            val latest = dao.find(task.id) ?: return
            dao.update(latest.pause("下载中断，已暂停"))
        } catch (e: Exception) {
            if (dir.exists()) {
                dir.deleteRecursively()
            }
            val latest = dao.find(task.id) ?: return
            dao.update(latest.fail(e.message ?: "下载失败"))
        }
    }

    private suspend fun fetchSource(request: VideoDownloadRequest): DownloadSource {
        val req = buildRequest(request)
        val reply = grpcClient.call(
            endpoint = ENDPOINT,
            requestBytes = req.toByteArray(),
            parser = PlayViewUniteReply.parser()
        )
        return mapReply(request, reply)
    }

    private fun buildRequest(request: VideoDownloadRequest): PlayViewUniteReq {
        val vod = VideoVod.newBuilder()
            .setAid(request.aid)
            .setCid(request.cid)
            .setQn(request.videoQuality.toLong())
            .setFnval(DOWNLOAD_FNVAL)
            .setFnver(0)
            .setDownload(0)
            .setPreferCodecType(CodeType.CODE265)
            .setIsNeedTrial(true)
            .build()
        val builder = PlayViewUniteReq.newBuilder()
            .setVod(vod)
            .setFromScene(FROM_SCENE)
            .setPlayCtrl(PlayCtrl.PLAY_CTRL_DEFAULT)
        request.extraContent().forEach { (key, value) ->
            builder.putExtraContent(key, value)
        }
        request.bvid
            ?.takeIf(String::isNotBlank)
            ?.let(builder::setBvid)
        return builder.build()
    }

    private fun mapReply(
        request: VideoDownloadRequest,
        reply: PlayViewUniteReply
    ): DownloadSource {
        val audios = buildList {
            addAll(reply.vodInfo.dashAudioList.map(::mapAudio))
            if (reply.vodInfo.hasDolby() && reply.vodInfo.dolby.type != DolbyItem.Type.NONE) {
                addAll(reply.vodInfo.dolby.audioList.map(::mapAudio))
            }
            if (reply.vodInfo.hasLossLessItem() && reply.vodInfo.lossLessItem.isLosslessAudio) {
                add(mapAudio(reply.vodInfo.lossLessItem.audio))
            }
        }
        val resolvedAid = reply.playArc.aid.takeIf { it > 0L } ?: request.aid
        val resolvedCid = reply.playArc.cid.takeIf { it > 0L } ?: request.cid
        return DownloadSource(
            aid = resolvedAid,
            cid = resolvedCid,
            durationMs = if (reply.hasPlayArc() && reply.playArc.durationMs > 0L) {
                reply.playArc.durationMs
            } else {
                reply.vodInfo.timelength
            },
            streams = reply.vodInfo.streamListList.mapNotNull { mapStream(it, audios) },
            audios = audios
        )
    }

    private fun mapStream(
        stream: Stream,
        audios: List<DownloadAudio>
    ): DownloadStream? {
        return when {
            stream.hasDashVideo() -> mapDash(stream.streamInfo.quality, stream.dashVideo, audios)
            stream.hasMultiDashVideo() -> (
                stream.multiDashVideo.dashVideosList.firstOrNull { it.codecid == CODECID_HEVC }
                    ?: stream.multiDashVideo.dashVideosList.firstOrNull()
                )?.let { mapDash(stream.streamInfo.quality, it, audios) }
            else -> null
        }
    }

    private fun mapDash(
        quality: Int,
        dash: DashVideo,
        audios: List<DownloadAudio>
    ): DownloadStream? {
        if (dash.baseUrl.isBlank()) return null
        return DownloadStream(
            quality = quality,
            videoUrl = dash.baseUrl,
            videoBackupUrls = dash.backupUrlList,
            audioId = dash.audioId.takeIf { it > 0 && audios.any { audio -> audio.id == it } }
        )
    }

    private fun mapAudio(item: DashItem): DownloadAudio {
        return DownloadAudio(
            id = item.id,
            url = item.baseUrl,
            backupUrls = item.backupUrlList
        )
    }

    private suspend fun cacheDanmaku(
        task: VideoDownloadEntity,
        source: DownloadSource,
        dir: File
    ) {
        val aid = source.aid.takeIf { it > 0L } ?: task.aid.takeIf { it > 0L } ?: return
        val cid = source.cid.takeIf { it > 0L } ?: task.cid.takeIf { it > 0L } ?: return

        runCatching {
            val reply = grpcClient.call(
                endpoint = DM_SEG_CACHE_ENDPOINT,
                requestBytes = buildDmSegCacheReq(aid = aid, cid = cid).toByteArray(),
                parser = DmSegMobileReply.parser()
            )
            persistDanmaku(
                file = File(dir, DANMAKU_FILE_NAME),
                payload = reply.toByteArray()
            )
            reply.elemsCount
        }.onSuccess { count ->
            Logger.d(TAG) {
                "离线弹幕缓存完成 aid=$aid cid=$cid elems=$count"
            }
        }.onFailure { error ->
            Logger.w(TAG) {
                "离线弹幕缓存失败 aid=$aid cid=$cid, ${error.message}"
            }
        }
    }

    private fun buildDmSegCacheReq(
        aid: Long,
        cid: Long
    ): DmSegCacheReq {
        return DmSegCacheReq.newBuilder()
            .setType(DM_TYPE_VIDEO)
            .setOid(cid)
            .setPid(aid)
            .build()
    }

    private fun persistDanmaku(
        file: File,
        payload: ByteArray
    ) {
        val parent = file.parentFile ?: error("弹幕缓存目录无效")
        if (!parent.exists() && !parent.mkdirs()) {
            error("创建弹幕缓存目录失败")
        }
        val part = File("${file.absolutePath}.part")
        if (part.exists() && !part.delete()) {
            error("清理临时弹幕缓存失败")
        }
        FileOutputStream(part, false).use { output ->
            output.write(payload)
            output.flush()
        }
        if (file.exists() && !file.delete()) {
            error("清理旧弹幕缓存失败")
        }
        if (!part.renameTo(file)) {
            error("保存弹幕缓存失败")
        }
    }

    private fun mapDanmakuItem(elem: DanmakuElem): DanmakuItem {
        return DanmakuItem(
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

    private suspend fun downloadFile(
        url: String,
        out: File,
        onProgress: suspend (Long, Long) -> Unit
    ) {
        if (out.exists()) {
            val size = out.length()
            if (size > 0L) {
                onProgress(size, size)
                return
            }
            if (!out.delete()) {
                error("清理空下载文件失败")
            }
        }
        val part = File("${out.absolutePath}.part")
        val start = part.length()
        currentCoroutineContext().ensureActive()
        val requestBuilder = Request.Builder()
            .url(url)
            .addHeader("user-agent", UserAgentBuilder.buildPlayerUserAgent())
        if (start > 0L) {
            requestBuilder.addHeader("Range", "bytes=$start-")
        }
        val request = requestBuilder.build()
        val call = okHttpClient.newCall(request)
        runningCall = call
        try {
            call.execute().use { response ->
                if (response.code == HTTP_RANGE_NOT_SATISFIABLE && start > 0L) {
                    val total = contentRangeTotal(response.header("Content-Range"))
                    if (total == start && part.renameTo(out)) {
                        onProgress(start, start)
                        return
                    }
                    error("断点续传位置无效")
                }
                if (start > 0L && response.code != HTTP_PARTIAL_CONTENT) {
                    error("服务器不支持断点续传")
                }
                if (!response.isSuccessful) {
                    error("下载失败 HTTP ${response.code}")
                }
                val body = response.body ?: error("下载响应为空")
                val total = contentRangeTotal(response.header("Content-Range"))
                    ?: body.contentLength().takeIf { it >= 0L }?.plus(start)
                    ?: 0L
                var done = start
                body.byteStream().use { input ->
                    FileOutputStream(part, start > 0L).use { output ->
                        val buf = ByteArray(BUF_SIZE)
                        var lastProgressAt = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val len = input.read(buf)
                            if (len < 0) break
                            output.write(buf, 0, len)
                            done += len
                            val now = System.currentTimeMillis()
                            if (done == total || now - lastProgressAt >= PROGRESS_INTERVAL_MS) {
                                lastProgressAt = now
                                onProgress(done, total)
                            }
                        }
                    }
                }
                if (part.length() <= 0L) {
                    error("下载文件为空")
                }
                if (!part.renameTo(out)) {
                    error("保存下载文件失败")
                }
            }
        } finally {
            if (runningCall === call) {
                runningCall = null
            }
        }
    }

    private fun contentRangeTotal(value: String?): Long? {
        return value?.substringAfter('/', missingDelimiterValue = "")?.toLongOrNull()
    }

    private fun taskDir(taskId: Long): File {
        return File(context.filesDir, "$ROOT_DIR_NAME/$taskId")
    }

    private fun deleteTaskFiles(taskId: Long) {
        val dir = taskDir(taskId)
        if (dir.exists()) {
            dir.deleteRecursively()
        }
    }

    private fun DownloadSource.selectStream(quality: Int): DownloadStream? {
        return streams.firstOrNull { it.quality == quality } ?: streams.firstOrNull()
    }

    private fun DownloadSource.selectAudio(
        stream: DownloadStream?,
        preferredId: Int
    ): DownloadAudio? {
        if (audios.isEmpty()) return null
        return audios.firstOrNull { it.id == preferredId && preferredId > 0 }
            ?: audios.firstOrNull { it.id == stream?.audioId }
            ?: audios.firstOrNull()
    }

    private fun buildCdns(
        stream: DownloadStream,
        audio: DownloadAudio?
    ): List<DownloadCdn> {
        val videoUrls = urls(stream.videoUrl, stream.videoBackupUrls)
        if (videoUrls.isEmpty()) return emptyList()
        val audioUrls = urls(audio?.url, audio?.backupUrls ?: emptyList())
        return List(maxOf(videoUrls.size, audioUrls.size)) { index ->
            DownloadCdn(
                videoUrl = videoUrls[index.coerceAtMost(videoUrls.lastIndex)],
                audioUrl = audioUrls.getOrNull(index.coerceAtMost(audioUrls.lastIndex))
            )
        }
    }

    private fun urls(
        primaryUrl: String?,
        backupUrls: List<String>
    ): List<String> {
        return buildList {
            primaryUrl?.takeIf(String::isNotBlank)?.let(::add)
            addAll(backupUrls.filter(String::isNotBlank))
        }.distinct()
    }

    private fun DownloadAudio.urls(): List<String> {
        return urls(url, backupUrls)
    }

    private fun VideoDownloadEntity.toRequest(): VideoDownloadRequest {
        return VideoDownloadRequest(
            biz = PlayBiz.from(biz),
            aid = aid,
            cid = cid,
            bvid = bvid,
            epId = epId,
            seasonId = seasonId,
            kind = VideoDownloadKind.from(kind),
            videoQuality = videoQuality,
            audioQuality = audioQuality
        )
    }

    private fun VideoDownloadRequest.key(): TaskKey {
        return TaskKey(
            biz = biz.name,
            aid = aid,
            cid = cid,
            epId = epId,
            seasonId = seasonId
        )
    }

    private fun VideoDownloadRequest.extraContent(): Map<String, String> {
        return buildMap {
            epId.takeIf { it > 0L }?.let { put("ep_id", it.toString()) }
            seasonId.takeIf { it > 0L }?.let { put("season_id", it.toString()) }
            if (biz == PlayBiz.PUGV) {
                put("biz_type", PUGV_BIZ_TYPE)
            }
        }
    }

    private fun VideoDownloadEntity.withResolvedVideoId(
        aid: Long,
        cid: Long
    ): VideoDownloadEntity {
        val resolvedAid = aid.takeIf { it > 0L } ?: this.aid
        val resolvedCid = cid.takeIf { it > 0L } ?: this.cid
        if (resolvedAid == this.aid && resolvedCid == this.cid) {
            return this
        }
        return copy(
            aid = resolvedAid,
            cid = resolvedCid
        )
    }

    private fun VideoDownloadEntity.prepareForRun(): VideoDownloadEntity {
        return copy(
            status = VideoDownloadTaskStatus.RUNNING.name,
            progressType = ProgressType.PREPARING,
            progressLabel = null,
            doneBytes = 0L,
            totalBytes = 0L,
            error = null,
            videoPath = null,
            audioPath = null,
            durationMs = 0L
        )
    }

    private fun VideoDownloadEntity.waiting(): VideoDownloadEntity {
        return copy(
            status = VideoDownloadTaskStatus.WAITING.name,
            progressType = null,
            progressLabel = null,
            doneBytes = 0L,
            totalBytes = 0L,
            error = null,
            videoPath = null,
            audioPath = null,
            durationMs = 0L
        )
    }

    private fun VideoDownloadEntity.pause(): VideoDownloadEntity {
        return pause(message = null)
    }

    private fun VideoDownloadEntity.pause(message: String?): VideoDownloadEntity {
        return copy(
            status = VideoDownloadTaskStatus.PAUSED.name,
            error = message,
            videoPath = null,
            audioPath = null,
            durationMs = 0L
        )
    }

    private fun VideoDownloadEntity.downloading(
        label: String,
        doneBytes: Long,
        totalBytes: Long
    ): VideoDownloadEntity {
        return copy(
            progressType = ProgressType.DOWNLOADING,
            progressLabel = label,
            doneBytes = doneBytes,
            totalBytes = totalBytes
        )
    }

    private fun VideoDownloadEntity.finish(
        videoPath: String?,
        audioPath: String?,
        durationMs: Long
    ): VideoDownloadEntity {
        return copy(
            status = VideoDownloadTaskStatus.DONE.name,
            progressType = ProgressType.DONE,
            progressLabel = null,
            doneBytes = 0L,
            totalBytes = 0L,
            error = null,
            videoPath = videoPath,
            audioPath = audioPath,
            durationMs = durationMs
        )
    }

    private fun VideoDownloadEntity.fail(message: String): VideoDownloadEntity {
        return copy(
            status = VideoDownloadTaskStatus.FAILED.name,
            progressType = null,
            progressLabel = null,
            doneBytes = 0L,
            totalBytes = 0L,
            error = message,
            videoPath = null,
            audioPath = null,
            durationMs = 0L
        )
    }

    private data class DownloadSource(
        val aid: Long,
        val cid: Long,
        val durationMs: Long,
        val streams: List<DownloadStream>,
        val audios: List<DownloadAudio>
    )

    private data class DownloadStream(
        val quality: Int,
        val videoUrl: String,
        val videoBackupUrls: List<String>,
        val audioId: Int?
    )

    private data class DownloadAudio(
        val id: Int,
        val url: String,
        val backupUrls: List<String>
    )

    private data class DownloadCdn(
        val videoUrl: String,
        val audioUrl: String?
    )

    private data class TaskKey(
        val biz: String,
        val aid: Long,
        val cid: Long,
        val epId: Long,
        val seasonId: Long
    )

    private companion object {
        const val TAG = "VideoDownloadRepo"
        const val ENDPOINT = "bilibili.app.playerunite.v1.Player/PlayViewUnite"
        const val DM_SEG_CACHE_ENDPOINT = "bilibili.community.service.dm.v1.DM/DmSegCache"
        const val FROM_SCENE = "normal"
        const val PUGV_BIZ_TYPE = "3"
        const val DOWNLOAD_FNVAL = 4048
        const val DM_TYPE_VIDEO = 1
        const val CODECID_HEVC = 12
        const val HTTP_PARTIAL_CONTENT = 206
        const val HTTP_RANGE_NOT_SATISFIABLE = 416
        const val BUF_SIZE = 256 * 1024
        const val PROGRESS_INTERVAL_MS = 400L
        const val ROOT_DIR_NAME = "video_download"
        const val DANMAKU_FILE_NAME = "danmaku.pb"
        const val VIDEO_FILE_NAME = "video.m4s"
        const val AUDIO_FILE_NAME = "audio.m4s"
        const val AUDIO_ONLY_FILE_NAME = "audio.m4a"
    }
}
