package com.naaammme.bbspace.feature.comment

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun CommentEditorFab(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier.navigationBarsPadding(),
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp
        )
    ) {
        Icon(
            imageVector = Icons.Default.Edit,
            contentDescription = contentDescription
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CommentEditorSheet(
    state: CommentEditorState,
    onDismiss: () -> Unit,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = {
            if (!state.loading) {
                onDismiss()
            }
        },
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (state.target.isReply) "回复评论" else "发表评论",
                        style = MaterialTheme.typography.titleMedium
                    )
                    state.target.parentName?.takeIf(String::isNotBlank)?.let { name ->
                        Text(
                            text = "回复 @$name",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                TextButton(
                    onClick = onSubmit,
                    enabled = state.canSubmit
                ) {
                    Text(
                        if (state.loading) "发送中" else "发送"
                    )
                }
            }
            OutlinedTextField(
                value = state.input,
                onValueChange = onInputChange,
                enabled = !state.loading,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                minLines = 4,
                maxLines = 8,
                placeholder = {
                    Text(
                        if (state.target.isReply) {
                            "输入你的回复"
                        } else {
                            "发一条友善的评论"
                        }
                    )
                }
            )
        }
    }
}
