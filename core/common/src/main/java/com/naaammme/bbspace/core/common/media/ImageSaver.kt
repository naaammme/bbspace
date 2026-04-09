package com.naaammme.bbspace.core.common.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection
import java.util.Locale

object ImageSaver {
    fun needsLegacyWritePermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
    }

    fun saveUrl(
        context: Context,
        url: String,
        album: String = "BBSpace",
        prefix: String = "bbspace"
    ): Uri {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
        }
        val resolver = context.contentResolver
        var outUri: Uri? = null
        return runCatching {
            val code = conn.responseCode
            if (code !in 200..299) {
                error("下载图片失败 $code")
            }
            val mime = resolveMimeType(url, conn.contentType)
            val ext = resolveExt(url, mime)
            val name = "${prefix}_${System.currentTimeMillis()}.$ext"
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        "${Environment.DIRECTORY_PICTURES}/$album"
                    )
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }
            outUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("创建图片失败")
            conn.inputStream.use { input ->
                resolver.openOutputStream(outUri, "w")?.use { output ->
                    input.copyTo(output)
                } ?: error("打开图片文件失败")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val done = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                resolver.update(outUri, done, null, null)
            }
            outUri!!
        }.onFailure {
            outUri?.let { resolver.delete(it, null, null) }
        }.also {
            conn.disconnect()
        }.getOrThrow()
    }

    private fun resolveMimeType(
        url: String,
        contentType: String?
    ): String {
        val mime = contentType
            ?.substringBefore(';')
            ?.trim()
            ?.takeIf(String::isNotBlank)
        if (mime != null) {
            return mime
        }
        return URLConnection.guessContentTypeFromName(url.substringBefore('?')) ?: "image/jpeg"
    }

    private fun resolveExt(
        url: String,
        mime: String
    ): String {
        val ext = url
            .substringBefore('?')
            .substringAfterLast('.', "")
            .lowercase(Locale.ROOT)
        if (ext in SUPPORTED_EXT) {
            return ext
        }
        return when (mime.lowercase(Locale.ROOT)) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            "image/bmp" -> "bmp"
            else -> "jpg"
        }
    }

    private val SUPPORTED_EXT = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
}
