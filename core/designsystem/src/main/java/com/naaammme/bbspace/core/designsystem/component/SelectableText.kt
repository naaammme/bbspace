package com.naaammme.bbspace.core.designsystem.component

import android.content.ClipData
import android.widget.Toast
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import kotlin.math.max
import kotlin.math.min

private fun popupPositionProvider(offsetPx: Int, selectionTopYPx: Int) = object : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val x = (anchorBounds.center.x - popupContentSize.width / 2)
            .coerceIn(0, windowSize.width - popupContentSize.width)
        val selectionScreenY = anchorBounds.top + selectionTopYPx
        val y = (selectionScreenY - popupContentSize.height - offsetPx)
            .coerceIn(0, windowSize.height - popupContentSize.height)
        return IntOffset(x, y)
    }
}

private data class SelectionRange(val start: Int, val end: Int)

@Composable
fun SelectableText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    var selection by remember { mutableStateOf<SelectionRange?>(null) }
    var isDragging by remember { mutableStateOf(false) }
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    val selectionColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val popupOffsetPx = remember(density) { with(density) { 8.dp.roundToPx() } }
    val selectionLineTopPx = remember(selection, textLayoutResult) {
        val sel = selection ?: return@remember 0
        val layout = textLayoutResult ?: return@remember 0
        val start = min(sel.start, sel.end).coerceIn(0, text.length)
        if (text.isNotEmpty() && start < text.length) {
            layout.getLineTop(layout.getLineForOffset(start)).toInt()
        } else 0
    }
    val positionProvider = remember(popupOffsetPx, selectionLineTopPx) {
        popupPositionProvider(popupOffsetPx, selectionLineTopPx)
    }

    Box(modifier = modifier) {
        Text(
            text = text,
            style = style,
            color = color,
            maxLines = maxLines,
            overflow = overflow,
            onTextLayout = { textLayoutResult = it },
            modifier = Modifier
                .drawBehind {
                    val layout = textLayoutResult ?: return@drawBehind
                    val sel = selection ?: return@drawBehind
                    val start = min(sel.start, sel.end).coerceIn(0, text.length)
                    val end = max(sel.start, sel.end).coerceIn(0, text.length)

                    if (start < end) {
                        // 获取指定文字范围的物理路径，直接绘制背景
                        val path = layout.getPathForRange(start, end)
                        drawPath(path, color = selectionColor)
                    }
                }
                .pointerInput(text) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset: Offset ->
                            val layout = textLayoutResult ?: return@detectDragGesturesAfterLongPress
                            val charOffset = layout.getOffsetForPosition(offset)
                            if (charOffset in 0..text.length) {
                                selection = SelectionRange(charOffset, charOffset)
                                isDragging = true
                            }
                        },
                        onDrag = { change, _ ->
                            val layout = textLayoutResult ?: return@detectDragGesturesAfterLongPress
                            val endOffset = layout.getOffsetForPosition(change.position).coerceIn(0, text.length)
                            selection = selection?.copy(end = endOffset)
                            change.consume()
                        },
                        onDragEnd = {
                            isDragging = false
                            val sel = selection
                            if (sel != null && sel.start == sel.end) {
                                selection = SelectionRange(0, text.length)
                            }
                        },
                        onDragCancel = {
                            isDragging = false
                            selection = null
                        }
                    )
                }
        )

        val sel = selection
        if (sel != null && !isDragging) {
            val start = min(sel.start, sel.end).coerceIn(0, text.length)
            val end = max(sel.start, sel.end).coerceIn(0, text.length)

            if (start < end) {
                val selectedText = text.substring(start, end)
                Popup(
                    popupPositionProvider = positionProvider,
                    onDismissRequest = { selection = null }
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shadowElevation = 4.dp
                    ) {
                        TextButton(onClick = {
                            selection = null
                            if (selectedText.isNotBlank()) {
                                scope.launch {
                                    clipboard.setClipEntry(
                                        ClipData.newPlainText(null, selectedText).toClipEntry()
                                    )
                                }
                                Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Text("复制")
                        }
                    }
                }
            }
        }
    }
}