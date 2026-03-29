package com.naaammme.bbspace.core.data

import android.content.Context
import android.net.Uri
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.naaammme.bbspace.core.common.log.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FontManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val fontsDir = File(context.filesDir, "fonts").apply { mkdirs() }

    companion object {
        private const val TAG = "FontManager"
        private const val CUSTOM_FONT_NAME = "custom.ttf"
    }

    suspend fun importFont(uri: Uri): Result<String> {
        return try {
            val destFile = File(fontsDir, CUSTOM_FONT_NAME)

            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return Result.failure(Exception("无法读取字体文件"))

            Logger.i(TAG) { "字体导入成功: ${destFile.absolutePath}" }
            Result.success(CUSTOM_FONT_NAME)
        } catch (e: Exception) {
            Logger.e(TAG, e) { "字体导入失败" }
            Result.failure(e)
        }
    }

    fun loadCustomFont(): FontFamily? {
        val file = File(fontsDir, CUSTOM_FONT_NAME)
        if (!file.exists()) {
            Logger.d(TAG) { "自定义字体不存在" }
            return null
        }

        return try {
            FontFamily(Font(file))
        } catch (e: Exception) {
            Logger.e(TAG, e) { "加载自定义字体失败" }
            null
        }
    }

    fun hasCustomFont(): Boolean {
        return File(fontsDir, CUSTOM_FONT_NAME).exists()
    }

    fun deleteCustomFont(): Boolean {
        val file = File(fontsDir, CUSTOM_FONT_NAME)
        return if (file.exists()) {
            file.delete().also {
                Logger.i(TAG) { "自定义字体已删除" }
            }
        } else {
            false
        }
    }
}
