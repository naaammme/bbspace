package com.naaammme.bbspace.feature.space.note

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun LazyListScope.spaceNoteSection(
    uid: Long,
    name: String,
    face: String?
) {
    item(
        key = "space_note",
        contentType = "note"
    ) {
        SpaceNoteSection(
            uid = uid,
            name = name,
            face = face
        )
    }
}

@Composable
private fun SpaceNoteSection(
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
                if (success) {
                    Toast.makeText(context, "已导出", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "导出失败", Toast.LENGTH_SHORT).show()
                }
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

    SpaceNoteCard(
        state = state,
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

@Composable
private fun SpaceNoteCard(
    state: SpaceNoteUiState,
    onSave: (String) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    var editing by rememberSaveable { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "备注",
                    style = MaterialTheme.typography.titleMedium
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NoteActionChip(
                        text = "导入",
                        onClick = onImport
                    )
                    NoteActionChip(
                        text = "导出",
                        onClick = onExport
                    )
                    NoteActionChip(
                        text = if (state.content.isBlank()) "添加" else "编辑",
                        onClick = { editing = true }
                    )
                }
            }
            if (state.content.isNotBlank()) {
                Text(
                    text = state.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    if (editing) {
        SpaceNoteDialog(
            content = state.content,
            onDismiss = { editing = false },
            onSave = { content ->
                onSave(content)
                editing = false
            }
        )
    }
}

@Composable
private fun NoteActionChip(
    text: String,
    onClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun SpaceNoteDialog(
    content: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var input by rememberSaveable(content) {
        mutableStateOf(content)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "备注")
        },
        text = {
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
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(input) }
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
