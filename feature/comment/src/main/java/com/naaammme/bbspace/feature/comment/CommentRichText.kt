package com.naaammme.bbspace.feature.comment

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import com.naaammme.bbspace.core.designsystem.component.BiliAsyncImage
import com.naaammme.bbspace.core.designsystem.component.BiliImageVariant
import com.naaammme.bbspace.core.model.CommentEmote

private val COMMENT_EMOTE_REGEX = Regex("(\\[[^\\]]+\\])")

private data class ParsedCommentText(
    val text: AnnotatedString,
    val emotes: List<ParsedCommentEmote>
)

private data class ParsedCommentEmote(
    val id: String,
    val emote: CommentEmote
)

@Composable
internal fun CommentRichText(
    text: String,
    emotes: List<CommentEmote>,
    modifier: Modifier = Modifier,
    style: TextStyle
) {
    val parsed = remember(text, emotes) {
        parseCommentText(text, emotes)
    }
    if (parsed.emotes.isEmpty()) {
        Text(
            text = text,
            style = style,
            modifier = modifier
        )
        return
    }
    val baseSize = style.lineHeight.takeIf { it.isSpecified }
        ?: MaterialTheme.typography.bodyMedium.lineHeight
    val inlineContent = remember(parsed.emotes, baseSize) {
        parsed.emotes.associate { item ->
            item.id to InlineTextContent(
                placeholder = Placeholder(
                    width = emoteSize(item.emote, baseSize),
                    height = emoteSize(item.emote, baseSize),
                    placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
                )
            ) {
                BiliAsyncImage(
                    url = item.emote.url,
                    contentDescription = item.emote.text,
                    modifier = Modifier.fillMaxSize(),
                    variant = BiliImageVariant.Original,
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
    Text(
        text = parsed.text,
        inlineContent = inlineContent,
        style = style,
        modifier = modifier
    )
}

private fun parseCommentText(
    text: String,
    emotes: List<CommentEmote>
): ParsedCommentText {
    if (text.isBlank() || emotes.isEmpty()) {
        return ParsedCommentText(
            text = AnnotatedString(text),
            emotes = emptyList()
        )
    }
    val emoteMap = emotes.associateBy(CommentEmote::text)
    val builder = AnnotatedString.Builder()
    val parsedEmotes = mutableListOf<ParsedCommentEmote>()
    var lastIndex = 0
    COMMENT_EMOTE_REGEX.findAll(text).forEach { match ->
        val emote = emoteMap[match.value] ?: return@forEach
        if (match.range.first > lastIndex) {
            builder.append(text.substring(lastIndex, match.range.first))
        }
        val id = "comment_emote_${parsedEmotes.size}"
        builder.appendInlineContent(id, emote.text)
        parsedEmotes += ParsedCommentEmote(id = id, emote = emote)
        lastIndex = match.range.last + 1
    }
    if (parsedEmotes.isEmpty()) {
        return ParsedCommentText(
            text = AnnotatedString(text),
            emotes = emptyList()
        )
    }
    if (lastIndex < text.length) {
        builder.append(text.substring(lastIndex))
    }
    return ParsedCommentText(
        text = builder.toAnnotatedString(),
        emotes = parsedEmotes
    )
}

private fun emoteSize(
    emote: CommentEmote,
    baseSize: TextUnit
): TextUnit {
    return if (emote.size > 1L) {
        baseSize * 1.5f
    } else {
        baseSize
    }
}
