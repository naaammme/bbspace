package com.naaammme.bbspace.core.data.update

import android.content.Context
import com.naaammme.bbspace.core.common.log.Logger
import com.naaammme.bbspace.core.designsystem.component.AppUpdateDialogState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class AppReleaseInfo(
    val version: String,
    val url: String,
    val desc: String?
)

sealed interface AppUpdateCheckResult {
    data class UpToDate(val release: AppReleaseInfo) : AppUpdateCheckResult
    data class HasUpdate(val release: AppReleaseInfo) : AppUpdateCheckResult
}

fun AppUpdateCheckResult.toDialogState(): AppUpdateDialogState {
    return when (this) {
        is AppUpdateCheckResult.UpToDate -> AppUpdateDialogState(
            title = "已是最新版本",
            desc = release.desc ?: "当前已是最新版本"
        )
        is AppUpdateCheckResult.HasUpdate -> release.toDialogState()
    }
}

fun AppReleaseInfo.toDialogState(): AppUpdateDialogState {
    return AppUpdateDialogState(
        title = "发现新版本 v$version",
        desc = desc ?: "暂无更新说明",
        confirmText = "前往下载",
        url = url
    )
}

fun Throwable.toDialogState(): AppUpdateDialogState {
    return AppUpdateDialogState(
        title = "检查更新失败",
        desc = message ?: "未知错误"
    )
}

@Singleton
class AppUpdateChecker @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "AppUpdateChecker"
        private const val RELEASES_API = "https://api.github.com/repos/naaammme/bbspace/releases/latest"
    }

    suspend fun check(): Result<AppUpdateCheckResult> = withContext(Dispatchers.IO) {
        runCatching {
            val release = fetchLatestRelease()
            if (release.version == currentVersionName()) {
                AppUpdateCheckResult.UpToDate(release)
            } else {
                AppUpdateCheckResult.HasUpdate(release)
            }
        }.onFailure { error ->
            Logger.w(TAG) { "检查更新失败: ${error.message}" }
        }
    }

    private fun fetchLatestRelease(): AppReleaseInfo {
        val request = Request.Builder()
            .url(RELEASES_API)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "BBSpace")
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("请求失败 ${response.code}")
            }
            val body = response.body?.string() ?: error("响应为空")
            val json = JSONObject(body)
            return AppReleaseInfo(
                version = json.getString("tag_name").trimStart('v', 'V'),
                url = json.getString("html_url"),
                desc = json.optString("body")
                    .replace("\r\n", "\n")
                    .trim()
                    .takeIf(String::isNotBlank)
            )
        }
    }

    private fun currentVersionName(): String {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return (info.versionName ?: "")
            .trimStart('v', 'V')
    }
}
