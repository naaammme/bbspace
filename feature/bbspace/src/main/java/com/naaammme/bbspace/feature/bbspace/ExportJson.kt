package com.naaammme.bbspace.feature.bbspace

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun rememberExportJson(): (fileName: String, json: String) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingJson by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val json = pendingJson
        pendingJson = null
        if (uri == null || json == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = json.replace("\\/", "/").toByteArray(Charsets.UTF_8)
                    context.contentResolver.openOutputStream(uri, "w")?.use { out ->
                        out.write(bytes)
                    } ?: error("打开导出文件失败")
                }
            }
            if (result.isSuccess) {
                Toast.makeText(context, "已导出", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "导出失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    return { fileName, json ->
        pendingJson = json
        launcher.launch(fileName)
    }
}
