package com.naaammme.bbspace.core.comment

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class ImageAttachmentHandler @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    class PreparedImage(
        val bytes: ByteArray,
        val mimeType: String
    )

    /** 校验大小/尺寸，返回原始字节 */
    suspend fun prepare(uri: Uri): PreparedImage = withContext(Dispatchers.IO) {
        val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
        readImage(uri, mimeType)
    }

    private fun readImage(uri: Uri, mimeType: String): PreparedImage {
        val bytes = readBytes(uri)
        val maxSize = if (mimeType.contains("gif", ignoreCase = true)) {
            MAX_GIF_SIZE
        } else {
            MAX_STATIC_SIZE
        }
        if (bytes.size > maxSize) {
            val msg = if (mimeType.contains("gif", ignoreCase = true)) {
                "GIF图片不能超过5MB"
            } else {
                "图片不能超过50MB"
            }
            throw IllegalArgumentException(msg)
        }
        if (mimeType.contains("gif", ignoreCase = true)) {
            return PreparedImage(bytes, mimeType)
        }
        val (width, height) = decodeBounds(bytes)
        if (width < MIN_DIMENSION || height < MIN_DIMENSION) {
            throw IllegalArgumentException("图片尺寸过小，宽高至少${MIN_DIMENSION}px")
        }
        return PreparedImage(bytes, mimeType)
    }

    private fun readBytes(uri: Uri): ByteArray {
        return context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalArgumentException("无法读取图片")
    }

    private fun decodeBounds(bytes: ByteArray): Pair<Int, Int> {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        val width = opts.outWidth
        val height = opts.outHeight
        if (width <= 0 || height <= 0) {
            throw IllegalArgumentException("无法解码图片")
        }
        return width to height
    }

    companion object {
        private const val MAX_STATIC_SIZE = 50L * 1024 * 1024
        private const val MAX_GIF_SIZE = 5L * 1024 * 1024
        private const val MIN_DIMENSION = 10
    }
}
