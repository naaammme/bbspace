package com.naaammme.bbspace.feature.space.note

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun SpaceNoteTitleButton(
    uid: Long,
    name: String,
    face: String?,
    viewModel: SpaceNoteViewModel = hiltViewModel()
) {
    LaunchedEffect(uid) {
        viewModel.observeNote(uid)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDialog by rememberSaveable { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val success = withContext(Dispatchers.IO) {
                    runCatching {
                        val outputStream = context.contentResolver.openOutputStream(uri)
                        viewModel.exportNoteJson(outputStream)
                    }.getOrElse { false }
                }
                Toast.makeText(
                    context,
                    if (success) "已导出" else "导出失败",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val inputStream = context.contentResolver.openInputStream(uri)
                            ?: throw Exception("InputStream is null")
                        viewModel.importNoteJson(inputStream).getOrThrow()
                    }
                }
                result.onSuccess { count ->
                    Toast.makeText(context, "已导入 $count 条备注", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(context, "导入失败: 格式错误或读取异常", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    TextButton(
        onClick = { showDialog = true },
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(
            text = state.content.ifBlank { "备注" }.replace('\n', ' '),
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }

    if (showDialog) {
        SpaceNoteDialog(
            content = state.content,
            onDismiss = { showDialog = false },
            onSave = { content ->
                viewModel.saveNote(
                    uid = uid,
                    name = name,
                    face = face,
                    content = content
                )
            },
            onExport = {
                exportLauncher.launch("bbspace_space_notes.json")
            },
            onImport = {
                importLauncher.launch(arrayOf("application/json", "text/*"))
            }
        )
    }
}

@Composable
private fun SpaceNoteDialog(
    content: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    var input by rememberSaveable(content) { mutableStateOf(content) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = null,
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onImport) {
                        Text(text = "导入")
                    }
                    OutlinedButton(onClick = onExport) {
                        Text(text = "导出")
                    }
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6,
                    placeholder = {
                        Text(text = "输入备注内容")
                    }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(input)
                    onDismiss()
                }
            ) {
                Text(text = "保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "取消")
            }
        }
    )
}
