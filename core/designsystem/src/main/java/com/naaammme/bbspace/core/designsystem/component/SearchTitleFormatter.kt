package com.naaammme.bbspace.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight

private const val HIGHLIGHT_START = "<em class=\"keyword\">"
private const val HIGHLIGHT_END = "</em>"

@Composable
fun rememberHighlightedTitle(
    text: String,
    highlightColor: Color
): AnnotatedString {
    return remember(text, highlightColor) {
        text.toHighlightedTitle(highlightColor)
    }
}

private fun String.toHighlightedTitle(highlightColor: Color): AnnotatedString {
    return buildAnnotatedString {
        var index = 0
        while (true) {
            val start = this@toHighlightedTitle.indexOf(HIGHLIGHT_START, index)
            if (start < 0) {
                append(this@toHighlightedTitle.substring(index))
                break
            }
            val end = this@toHighlightedTitle.indexOf(
                HIGHLIGHT_END,
                start + HIGHLIGHT_START.length
            )
            if (end < 0) {
                append(this@toHighlightedTitle.substring(index))
                break
            }

            append(this@toHighlightedTitle.substring(index, start))
            val keywordStart = length
            append(this@toHighlightedTitle.substring(start + HIGHLIGHT_START.length, end))
            addStyle(
                SpanStyle(color = highlightColor, fontWeight = FontWeight.Medium),
                keywordStart,
                length
            )
            index = end + HIGHLIGHT_END.length
        }
    }
}
