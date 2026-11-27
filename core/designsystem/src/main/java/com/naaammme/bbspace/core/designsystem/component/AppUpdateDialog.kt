package com.naaammme.bbspace.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.naaammme.bbspace.core.model.AppReleaseInfo
import com.naaammme.bbspace.core.model.AppUpdateCheckResult

@Immutable
data class AppUpdateDialogState(
    val title: String,
    val desc: String,
    val confirmText: String? = null,
    val url: String? = null
)

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

@Composable
fun AppUpdateDialog(
    state: AppUpdateDialogState,
    onDismiss: () -> Unit,
    onOpenUrl: (String) -> Unit
) {
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            SelectableText(state.title)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SelectableText(
                    text = state.desc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val url = state.url
                    if (url == null) {
                        onDismiss()
                    } else {
                        onOpenUrl(url)
                    }
                }
            ) {
                Text(state.confirmText ?: "知道了")
            }
        },
        dismissButton = if (state.url != null) {
            {
                TextButton(onClick = onDismiss) {
                    Text("关闭")
                }
            }
        } else {
            null
        }
    )
}
