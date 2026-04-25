package com.naaammme.bbspace.core.data.download

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.bapis.bilibili.app.playerunite.v1.PlayViewUniteReply
import com.bapis.bilibili.app.playerunite.v1.PlayViewUniteReq
import com.bapis.bilibili.playershared.CodeType
import com.bapis.bilibili.playershared.DashItem
import com.bapis.bilibili.playershared.DashVideo
import com.bapis.bilibili.playershared.DolbyItem
import com.bapis.bilibili.playershared.PlayCtrl
import com.bapis.bilibili.playershared.Stream
import com.bapis.bilibili.playershared.VideoVod
import com.naaammme.bbspace.core.common.UserAgentBuilder
import com.naaammme.bbspace.core.domain.download.VideoDownloadRepository
import com.naaammme.bbspace.core.model.VideoDownloadKind
import com.naaammme.bbspace.core.model.VideoDownloadProgress
import com.naaammme.bbspace.core.model.VideoDownloadRequest
import com.naaammme.bbspace.core.model.VideoDownloadTask
import com.naaammme.bbspace.core.model.VideoDownloadTaskStatus
import com.naaammme.bbspace.core.model.VideoRoute
import com.naaammme.bbspace.core.model.toPlayableParams
import com.naaammme.bbspace.infra.grpc.BiliGrpcClient
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request

@Singleton
class VideoDownloadRepoImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val grpcClient: BiliGrpcClient,
    private val okHttpClient: OkHttpClient
) : VideoDownloadRepository {

    private val _tasks = MutableStateFlow<List<VideoDownloadTask>>(emptyList())
    override val tasks: StateFlow<List<VideoDownloadTask>> = _tasks.asStateFlow()
    private val nextTaskId = AtomicLong(1L)
    private val drainMutex = Mutex()

    override fun enqueue(request: VideoDownloadRequest): Long {
        val id = nextTaskId.getAndIncrement()
        _tasks.update { tasks ->
            tasks + VideoDownloadTask(
                id = id,
                title = request.taskTitle(),
                request = request
            )
        }
        return id
    }

    override suspend fun runPending() {
        drainMutex.withLock {
            while (true) {
                val task = tasks.value.firstOrNull { it.status == VideoDownloadTaskStatus.WAITING }
                    ?: break
                runTask(task)
            }
        }
    }

    private suspend fun runTask(task: VideoDownloadTask) {
        updateTask(task.id) {
            it.copy(
                status = VideoDownloadTaskStatus.RUNNING,
                progress = VideoDownloadProgress.Preparing,
                error = null
            )
        }
        try {
            download(task.request, task.id).collect { progress ->
                updateTask(task.id) {
                    it.copy(
                        status = if (progress is VideoDownloadProgress.Done) {
                            VideoDownloadTaskStatus.DONE
                        } else {
                            VideoDownloadTaskStatus.RUNNING
                        },
                        progress = progress,
                        error = null
                    )
                }
            }
        } catch (e: CancellationException) {
            updateTask(task.id) {
                it.copy(
                    status = VideoDownloadTaskStatus.WAITING,
                    progress = null,
                    error = null
                )
            }
            throw e
        } catch (e: Exception) {
            updateTask(task.id) {
                it.copy(
                    status = VideoDownloadTaskStatus.FAILED,
                    progress = null,
                    error = e.message ?: "下载失败"
                )
            }
        }
    }

    private fun updateTask(
        taskId: Long,
        transform: (VideoDownloadTask) -> VideoDownloadTask
    ) {
        _tasks.update { tasks ->
            tasks.map { task -> if (task.id == taskId) transform(task) else task }
        }
    }

    private fun download(
        request: VideoDownloadRequest,
        taskId: Long
    ): Flow<VideoDownloadProgress> = flow {
        emit(VideoDownloadProgress.Preparing)
        val source = fetchSource(request)
        val stream = source.selectStream(request.videoQuality)
        val audio = source.selectAudio(stream, request.audioQuality)
        val dir = File(context.cacheDir, "video_download").apply { mkdirs() }
        val videoFile = File(dir, "${taskId}_video.m4s")
        val audioFile = File(dir, "${taskId}_audio.m4s")
        val outFile = File(dir, "${taskId}_out.${request.kind.extension}")

        try {
            when (request.kind) {
                VideoDownloadKind.VIDEO -> {
                    val picked = stream ?: error("没有可下载视频流")
                    val cdn = buildCdns(picked, audio).firstOrNull()
                        ?: error("下载 CDN 无效")
                    val audioUrl = cdn.audioUrl
                    if (audio != null && audioUrl == null) {
                        error("音频下载地址为空")
                    }
                    downloadFile(cdn.videoUrl, videoFile) { done, total ->
                        emit(VideoDownloadProgress.Downloading("视频", done, total))
                    }
                    if (audioUrl != null) {
                        downloadFile(audioUrl, audioFile) { done, total ->
                            emit(VideoDownloadProgress.Downloading("音频", done, total))
                        }
                    }
                    emit(VideoDownloadProgress.Muxing)
                    mux(videoFile, audioFile.takeIf { it.exists() }, outFile)
                }

                VideoDownloadKind.AUDIO -> {
                    val audioUrl = audio.urls().firstOrNull() ?: error("没有可下载音频流")
                    downloadFile(audioUrl, audioFile) { done, total ->
                        emit(VideoDownloadProgress.Downloading("音频", done, total))
                    }
                    emit(VideoDownloadProgress.Muxing)
                    mux(null, audioFile, outFile)
                }
            }
            emit(VideoDownloadProgress.Done(publish(outFile, fileName(request, source, stream, audio), request.kind)))
        } finally {
            videoFile.delete()
            audioFile.delete()
            outFile.delete()
        }
    }.flowOn(Dispatchers.IO)

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
        val playable = request.route.toPlayableParams() ?: error("下载路由无效")
        val videoId = playable.videoId
        val vod = VideoVod.newBuilder()
            .setAid(videoId.aid)
            .setCid(videoId.cid)
            .setQn(request.videoQuality.toLong())
            .setFnval(DOWNLOAD_FNVAL)
            .setFnver(0)
            .setDownload(0)
            .setPreferCodecType(CodeType.CODE265)
            .setIsNeedTrial(true)
            .build()
        val builder = PlayViewUniteReq.newBuilder()
            .setVod(vod)
            .setFromScene(playable.fromScene)
            .setPlayCtrl(PlayCtrl.PLAY_CTRL_DEFAULT)
            .putAllExtraContent(playable.getResolveExtraContent())
        videoId.bvid
            ?.takeIf(String::isNotBlank)
            ?.let(builder::setBvid)
        return builder.build()
    }

    private fun mapReply(
        request: VideoDownloadRequest,
        reply: PlayViewUniteReply
    ): DownloadSource {
        val playable = request.route.toPlayableParams() ?: error("下载路由无效")
        val videoId = if (reply.hasPlayArc()) {
            playable.videoId.copy(
                aid = reply.playArc.aid.takeIf { it > 0L } ?: playable.videoId.aid,
                cid = reply.playArc.cid.takeIf { it > 0L } ?: playable.videoId.cid
            )
        } else {
            playable.videoId
        }
        val audios = buildList {
            addAll(reply.vodInfo.dashAudioList.map(::mapAudio))
            if (reply.vodInfo.hasDolby() && reply.vodInfo.dolby.type != DolbyItem.Type.NONE) {
                addAll(reply.vodInfo.dolby.audioList.map(::mapAudio))
            }
            if (reply.vodInfo.hasLossLessItem() && reply.vodInfo.lossLessItem.isLosslessAudio) {
                add(mapAudio(reply.vodInfo.lossLessItem.audio))
            }
        }
        return DownloadSource(
            aid = videoId.aid,
            cid = videoId.cid,
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
            stream.hasSegmentVideo() -> {
                // TODO 大会员试看会返回 segmentVideo 这里暂时不支持下载 后面再补分段 mp4 下载
                null
            }
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

    private suspend fun downloadFile(
        url: String,
        out: File,
        onProgress: suspend (Long, Long) -> Unit
    ) {
        val request = Request.Builder()
            .url(url)
            .addHeader("user-agent", UserAgentBuilder.buildPlayerUserAgent())
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("下载失败 HTTP ${response.code}")
            }
            val body = response.body ?: error("下载响应为空")
            val total = body.contentLength().coerceAtLeast(0L)
            var done = 0L
            body.byteStream().use { input ->
                FileOutputStream(out).use { output ->
                    val buf = ByteArray(BUF_SIZE)
                    var lastProgressAt = 0L
                    while (true) {
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
        }
    }

    private fun mux(
        videoFile: File?,
        audioFile: File?,
        outFile: File
    ) {
        if (videoFile == null && audioFile == null) error("没有可合并的媒体文件")
        val video = videoFile?.let { MediaExtractor().apply { setDataSource(it.absolutePath) } }
        val audio = audioFile?.let { MediaExtractor().apply { setDataSource(it.absolutePath) } }
        val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var started = false
        var completed = false
        try {
            val videoTrack = video?.let { findTrack(it, "video/") }
            val audioTrack = audio?.let { findTrack(it, "audio/") }
            val outVideoTrack = if (video != null && videoTrack != null) {
                muxer.addTrack(video.getTrackFormat(videoTrack))
            } else {
                null
            }
            val outAudioTrack = if (audio != null && audioTrack != null) {
                muxer.addTrack(audio.getTrackFormat(audioTrack))
            } else {
                null
            }
            muxer.start()
            started = true
            if (video != null && videoTrack != null && outVideoTrack != null) {
                writeTrack(video, videoTrack, muxer, outVideoTrack)
            }
            if (audio != null && audioTrack != null && outAudioTrack != null) {
                writeTrack(audio, audioTrack, muxer, outAudioTrack)
            }
            completed = true
        } finally {
            video?.release()
            audio?.release()
            try {
                if (started && completed) muxer.stop()
            } finally {
                muxer.release()
            }
        }
    }

    private fun findTrack(
        extractor: MediaExtractor,
        mimePrefix: String
    ): Int {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith(mimePrefix)) return i
        }
        error("未找到${if (mimePrefix == "video/") "视频" else "音频"}轨道")
    }

    private fun writeTrack(
        extractor: MediaExtractor,
        inTrack: Int,
        muxer: MediaMuxer,
        outTrack: Int
    ) {
        val format = extractor.getTrackFormat(inTrack)
        val bufferSize = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
            format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).coerceAtLeast(SAMPLE_BUF_SIZE)
        } else {
            SAMPLE_BUF_SIZE
        }
        val buffer = ByteBuffer.allocate(bufferSize)
        val info = MediaCodec.BufferInfo()
        extractor.selectTrack(inTrack)
        while (true) {
            buffer.clear()
            val size = extractor.readSampleData(buffer, 0)
            if (size < 0) break
            info.set(0, size, extractor.sampleTime.coerceAtLeast(0L), extractor.sampleFlags)
            muxer.writeSampleData(outTrack, buffer, info)
            extractor.advance()
        }
    }

    private fun publish(
        file: File,
        fileName: String,
        kind: VideoDownloadKind
    ): String {
        val dirType = kind.directory
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val parent = context.getExternalFilesDir(dirType) ?: error("外部存储不可用")
            val dir = File(parent, ALBUM).apply { mkdirs() }
            val out = File(dir, fileName)
            file.inputStream().use { input ->
                FileOutputStream(out).use { output -> input.copyTo(output) }
            }
            return Uri.fromFile(out).toString()
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, kind.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$dirType/$ALBUM")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(kind.collectionUri, values) ?: error("创建媒体文件失败")
        try {
            resolver.openOutputStream(uri, "w")?.use { output ->
                FileInputStream(file).use { input -> input.copyTo(output) }
            } ?: error("打开媒体文件失败")
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null
            )
            return uri.toString()
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    private fun fileName(
        request: VideoDownloadRequest,
        source: DownloadSource,
        stream: DownloadStream?,
        audio: DownloadAudio?
    ): String {
        val raw = request.title
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: "av${source.aid}_cid${source.cid}"
        val clean = raw.replace(FILE_NAME_REGEX, "_")
            .take(MAX_FILE_NAME_LEN)
            .ifBlank { "video" }
        val quality = when (request.kind) {
            VideoDownloadKind.VIDEO -> stream?.quality
            VideoDownloadKind.AUDIO -> audio?.id
        }?.toString()
        return listOfNotNull(clean, quality).joinToString("_") + ".${request.kind.extension}"
    }

    private fun VideoDownloadRequest.taskTitle(): String {
        title?.trim()
            ?.takeIf(String::isNotBlank)
            ?.let { return it }
        return when (val route = route) {
            is VideoRoute.Ugc -> route.bvid?.takeIf(String::isNotBlank) ?: "av${route.aid}"
            is VideoRoute.Pgc -> "ep${route.epId}"
            is VideoRoute.Pugv -> "pugv ep${route.epId}"
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
            addAll(backupUrls.filter(String::isNotBlank))
            primaryUrl?.takeIf(String::isNotBlank)?.let(::add)
        }.distinct()
    }

    private fun DownloadAudio?.urls(): List<String> {
        this ?: return emptyList()
        return urls(url, backupUrls)
    }

    private val VideoDownloadKind.extension: String
        get() = when (this) {
            VideoDownloadKind.VIDEO -> "mp4"
            VideoDownloadKind.AUDIO -> "m4a"
        }

    private val VideoDownloadKind.mimeType: String
        get() = when (this) {
            VideoDownloadKind.VIDEO -> "video/mp4"
            VideoDownloadKind.AUDIO -> "audio/mp4"
        }

    private val VideoDownloadKind.directory: String
        get() = when (this) {
            VideoDownloadKind.VIDEO -> Environment.DIRECTORY_MOVIES
            VideoDownloadKind.AUDIO -> Environment.DIRECTORY_MUSIC
        }

    private val VideoDownloadKind.collectionUri: Uri
        get() = when (this) {
            VideoDownloadKind.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            VideoDownloadKind.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

    private data class DownloadSource(
        val aid: Long,
        val cid: Long,
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

    private companion object {
        const val ALBUM = "BBSpace"
        const val ENDPOINT = "bilibili.app.playerunite.v1.Player/PlayViewUnite"
        const val DOWNLOAD_FNVAL = 4048
        const val CODECID_HEVC = 12
        const val BUF_SIZE = 256 * 1024
        const val PROGRESS_INTERVAL_MS = 400L
        const val SAMPLE_BUF_SIZE = 4 * 1024 * 1024
        const val MAX_FILE_NAME_LEN = 80
        val FILE_NAME_REGEX = Regex("""[\\/:*?"<>|]""")
    }
}
