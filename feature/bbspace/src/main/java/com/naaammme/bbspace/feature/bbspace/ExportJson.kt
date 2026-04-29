package com.naaammme.bbspace.feature.bbspace

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

@Composable
fun rememberExportJson(): (fileName: String, json: String) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            pending?.let { json ->
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, "已导出", Toast.LENGTH_SHORT).show()
            }
        }
        pending = null
    }

    return { fileName, json ->
        pending = json
        launcher.launch(fileName)
    }
}
