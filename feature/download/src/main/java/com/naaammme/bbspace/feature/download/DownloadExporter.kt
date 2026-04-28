package com.naaammme.bbspace.feature.download

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.naaammme.bbspace.core.model.VideoDownloadTask
import com.naaammme.bbspace.core.model.VideoDownloadTaskStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.nio.ByteBuffer
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DownloadExporter @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    suspend fun export(
        task: VideoDownloadTask,
        onProgress: (Int) -> Unit
    ): String = withContext(Dispatchers.IO) {
        if (task.status != VideoDownloadTaskStatus.DONE) error("缓存未完成")
        val video = task.videoPath?.let(::File)?.takeIf(File::isFile)
        val audio = task.audioPath?.let(::File)?.takeIf(File::isFile)
        val name = task.title.cleanFileName().ifBlank { "download_${task.id}" }
        if (video != null) {
            return@withContext exportVideo(video, audio, "$name.mp4", onProgress)
        } else {
            val source = audio ?: error("没有可导出的缓存文件")
            onProgress(1)
            val path = saveToDownloads(source, "$name.m4a", "audio/mp4")
            onProgress(100)
            return@withContext path
        }
    }

    private fun exportVideo(
        video: File,
        audio: File?,
        fileName: String,
        onProgress: (Int) -> Unit
    ): String {
        val temp = File(exportCacheDir(), fileName)
        try {
            if (temp.exists() && !temp.delete()) error("清理导出缓存失败")
            muxToMp4(video, audio, temp, onProgress)
            val path = saveToDownloads(temp, fileName, "video/mp4")
            onProgress(100)
            return path
        } finally {
            temp.delete()
        }
    }

    private fun muxToMp4(
        video: File,
        audio: File?,
        outFile: File,
        onProgress: (Int) -> Unit
    ) {
        val videoExtractor = MediaExtractor().apply { setDataSource(video.absolutePath) }
        val audioExtractor = audio?.let { MediaExtractor().apply { setDataSource(it.absolutePath) } }
        val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val totalBytes = (video.length() + (audio?.length() ?: 0L)).coerceAtLeast(1L)
        var doneBytes = 0L
        var lastProgress = 0
        var lastReportBytes = 0L
        fun findTrack(
            extractor: MediaExtractor,
            mimePrefix: String
        ): Int {
            for (index in 0 until extractor.trackCount) {
                val mime = extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith(mimePrefix)) return index
            }
            error("未找到${if (mimePrefix == "video/") "视频" else "音频"}轨道")
        }

        fun writeTrack(
            extractor: MediaExtractor,
            track: Int,
            outTrack: Int
        ) {
            val format = extractor.getTrackFormat(track)
            val bufferSize = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).coerceAtLeast(MIN_BUFFER_SIZE)
            } else {
                MIN_BUFFER_SIZE
            }
            val buffer = ByteBuffer.allocateDirect(bufferSize)
            val info = MediaCodec.BufferInfo()
            extractor.selectTrack(track)
            while (true) {
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                info.set(
                    0,
                    size,
                    extractor.sampleTime.coerceAtLeast(0L),
                    extractor.sampleFlags
                )
                muxer.writeSampleData(outTrack, buffer, info)
                doneBytes += size
                extractor.advance()
                if (doneBytes == totalBytes || doneBytes - lastReportBytes >= PROGRESS_CHECK_BYTES) {
                    lastReportBytes = doneBytes
                    val progress = ((doneBytes * 99L) / totalBytes).toInt().coerceIn(0, 99)
                    if (progress > lastProgress) {
                        lastProgress = progress
                        onProgress(progress)
                    }
                }
            }
        }

        var started = false
        var complete = false
        try {
            val videoTrack = findTrack(videoExtractor, "video/")
            val outVideoTrack = muxer.addTrack(videoExtractor.getTrackFormat(videoTrack))
            val audioTrack = audioExtractor?.let { findTrack(it, "audio/") }
            val outAudioTrack = if (audioExtractor != null && audioTrack != null) {
                muxer.addTrack(audioExtractor.getTrackFormat(audioTrack))
            } else {
                null
            }
            muxer.start()
            started = true
            writeTrack(videoExtractor, videoTrack, outVideoTrack)
            if (audioExtractor != null && audioTrack != null && outAudioTrack != null) {
                writeTrack(audioExtractor, audioTrack, outAudioTrack)
            }
            complete = true
        } finally {
            videoExtractor.release()
            audioExtractor?.release()
            if (started) {
                if (complete) {
                    muxer.stop()
                } else {
                    runCatching { muxer.stop() }
                }
            }
            muxer.release()
        }
    }

    private fun saveToDownloads(
        source: File,
        fileName: String,
        mime: String
    ): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                ALBUM
            )
            if (!dir.exists() && !dir.mkdirs()) error("创建下载目录失败")
            val out = File(dir, fileName)
            source.copyTo(out, overwrite = true)
            MediaScannerConnection.scanFile(context, arrayOf(out.absolutePath), arrayOf(mime), null)
            return out.absolutePath
        }
        val resolver = context.contentResolver
        val values = pendingDownloadValues(fileName, mime)
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("创建导出文件失败")
        try {
            resolver.openOutputStream(uri, "w")?.use { output ->
                source.inputStream().use { input -> input.copyTo(output, COPY_BUF_SIZE) }
            } ?: error("打开导出文件失败")
            publishPendingDownload(uri)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return "${Environment.DIRECTORY_DOWNLOADS}/$ALBUM/$fileName"
    }

    private fun publishPendingDownload(uri: android.net.Uri) {
        context.contentResolver.update(
            uri,
            ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
            null,
            null
        )
    }

    private fun pendingDownloadValues(
        fileName: String,
        mime: String
    ): ContentValues {
        return ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$ALBUM")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
    }

    private fun exportCacheDir(): File {
        return File(context.cacheDir, "download_export").also { dir ->
            if (!dir.exists() && !dir.mkdirs()) error("创建导出缓存失败")
        }
    }

    private fun String.cleanFileName(): String {
        return trim()
            .replace(INVALID_NAME_CHARS, "_")
            .replace(NAME_SPACES, " ")
            .take(MAX_NAME_LEN)
            .trim()
    }

    private companion object {
        const val ALBUM = "BBSpace"
        const val MAX_NAME_LEN = 60
        const val MIN_BUFFER_SIZE = 4 * 1024 * 1024
        const val COPY_BUF_SIZE = 256 * 1024
        const val PROGRESS_CHECK_BYTES = 512 * 1024L
        val INVALID_NAME_CHARS = Regex("""[\\/:*?"<>|]""")
        val NAME_SPACES = Regex("""\s+""")
    }
}
