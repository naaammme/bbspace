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

@Immutable
data class AppUpdateDialogState(
    val title: String,
    val desc: String,
    val confirmText: String? = null,
    val url: String? = null
)

@Composable
fun AppUpdateDialog(
    state: AppUpdateDialogState,
    onDismiss: () -> Unit,
    onOpenUrl: (String) -> Unit
) {
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(state.title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
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
