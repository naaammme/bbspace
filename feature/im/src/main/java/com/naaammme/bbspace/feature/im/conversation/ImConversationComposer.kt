package com.naaammme.bbspace.feature.im.conversation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@Composable
internal fun ImConversationComposer(
    errorMessage: String?,
    onClearError: () -> Unit,
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    var isFocused by remember { mutableStateOf(false) }
    var imeWasVisible by remember { mutableStateOf(false) }
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    // TODO 改成 TextFieldState，后面和输入态清理逻辑一起收敛，避免先在这里引入额外状态同步代码
    var text by rememberSaveable { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(imeVisible) {
        if (imeWasVisible && !imeVisible && isFocused) {
            focusManager.clearFocus(force = true)
        }
        imeWasVisible = imeVisible
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = MaterialTheme.shapes.large
                        )
                ) {
                    BasicTextField(
                        value = text,
                        onValueChange = { newText ->
                            text = newText
                            localError = null
                            if (errorMessage != null) onClearError()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .padding(start = 12.dp, end = 10.dp)
                            .onFocusChanged { isFocused = it.isFocused },
                        singleLine = true,
                        minLines = 1,
                        maxLines = 1,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                val trimmed = text.trimEnd()
                                if (trimmed.isNotEmpty()) {
                                    onSend(trimmed)
                                    text = ""
                                }
                                focusManager.clearFocus(force = true)
                            }
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { innerTextField ->
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (text.isEmpty()) {
                                    Text(
                                        text = "发消息",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                }
                IconButton(
                    onClick = {
                        val trimmed = text.trimEnd()
                        if (trimmed.isEmpty()) {
                            localError = "消息不能为空"
                        } else {
                            onSend(trimmed)
                            text = ""
                            localError = null
                            focusManager.clearFocus(force = true)
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "发送消息"
                    )
                }
            }
            val displayError = localError ?: errorMessage
            if (!displayError.isNullOrBlank()) {
                Text(
                    text = displayError,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
