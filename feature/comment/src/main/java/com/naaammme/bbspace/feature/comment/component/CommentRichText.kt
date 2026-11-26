package com.naaammme.bbspace.feature.comment.component

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.withLink
import com.naaammme.bbspace.core.designsystem.component.BiliAsyncImage
import com.naaammme.bbspace.core.designsystem.component.BiliImageVariant
import com.naaammme.bbspace.core.designsystem.component.copyTextOnLongPress
import com.naaammme.bbspace.core.model.CommentEmote
import com.naaammme.bbspace.core.model.CommentUser
import com.naaammme.bbspace.core.model.CommentWebLink

private const val EMOTE_PATTERN = """\[[^\[\]]+]"""
// 官方客户端仅识别白名单内的 urlsMap 链接，这里保留正文普通 HTTP 链接识别
private const val WEB_URL_PATTERN = """(?i:https?://)(?:\[[a-fA-F0-9:.]+])?[^\s\p{Z}\[\]<>"“”‘’，。！？；、：【】（）《》]*"""
private const val MENTION_END = """(?=\s|$)"""
private val COMMENT_TOKEN_REGEX = Regex("$WEB_URL_PATTERN|$EMOTE_PATTERN")

private data class ParsedCommentText(
    val text: AnnotatedString,
    val emotes: Set<CommentEmote>
)

@Composable
internal fun CommentRichText(
    text: String,
    onAction: (CommentReplyAction) -> Unit,
    emotes: List<CommentEmote>,
    mentions: Map<String, Long>,
    webLinks: Map<String, CommentWebLink>
) {
    val style = MaterialTheme.typography.bodyMedium
    val modifier = Modifier.copyTextOnLongPress(text, "评论")
    val linkColor = MaterialTheme.colorScheme.primary
    val currentOnAction by rememberUpdatedState(onAction)
    val parsed = remember(text, emotes, mentions, webLinks, linkColor) {
        parseCommentText(text, emotes, mentions, webLinks, linkColor) {
            currentOnAction(it)
        }
    }
    if (parsed == null) {
        Text(text = text, style = style, modifier = modifier)
        return
    }
    val baseSize = style.lineHeight
    val inlineContent = remember(parsed.emotes, baseSize) {
        if (parsed.emotes.isEmpty()) return@remember emptyMap()
        parsed.emotes.associate { emote ->
            val size = if (emote.size > 1L) baseSize * 1.5f else baseSize
            emote.text to InlineTextContent(
                placeholder = Placeholder(size, size, PlaceholderVerticalAlign.TextCenter)
            ) {
                BiliAsyncImage(
                    url = emote.url,
                    contentDescription = emote.text,
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
    emotes: List<CommentEmote>,
    mentions: Map<String, Long>,
    webLinks: Map<String, CommentWebLink>,
    linkColor: Color,
    onAction: (CommentReplyAction) -> Unit
): ParsedCommentText? {
    val hasMentions = mentions.isNotEmpty() && '@' in text
    if (emotes.isEmpty() && !hasMentions && webLinks.isEmpty() && "://" !in text) return null

    val regex = if (!hasMentions && webLinks.isEmpty()) {
        COMMENT_TOKEN_REGEX
    } else {
        val patterns = buildList {
            webLinks.keys.filter(String::isNotBlank).sortedByDescending(String::length).forEach {
                add(Regex.escape(it))
            }
            add(WEB_URL_PATTERN)
            if (hasMentions) {
                mentions.keys.filter(String::isNotBlank).sortedByDescending(String::length).forEach {
                    add(Regex.escape("@$it") + MENTION_END)
                }
            }
            if (emotes.isNotEmpty()) add(EMOTE_PATTERN)
        }
        Regex(patterns.joinToString("|"))
    }
    val matches = regex.findAll(text).iterator()
    if (!matches.hasNext()) return null

    val emoteMap = emotes.associateBy(CommentEmote::text)
    val linkStyles = TextLinkStyles(SpanStyle(color = linkColor))
    val builder = AnnotatedString.Builder()
    val parsedEmotes = mutableSetOf<CommentEmote>()
    var lastIndex = 0
    matches.forEach { match ->
        val token = match.value
        val exactLink = webLinks[token]
        val linkToken = when {
            exactLink != null -> token
            token.startsWith("http", ignoreCase = true) -> {
                val trimmed = token.trimEnd('.', ',', ';', ':', '?', '!')
                if ('(' in trimmed) trimmed else trimmed.trimEnd(')')
            }
            else -> null
        }
        val webLink = exactLink ?: linkToken?.let(webLinks::get)
        val url = webLink?.url ?: linkToken?.takeIf { !Uri.parse(it).host.isNullOrBlank() }
        val name = token.takeIf { it.startsWith('@') }?.substring(1)
        val mid = name?.let(mentions::get)?.takeIf { it > 0L }
        val emote = emoteMap[token]

        if (url == null && mid == null && emote == null) return@forEach
        builder.append(text, lastIndex, match.range.first)
        when {
            url != null -> {
                val label = webLink?.title?.ifBlank { null }
                builder.withLink(
                    LinkAnnotation.Url(url, linkStyles) { onAction(CommentReplyAction.OpenWeb(url)) }
                ) {
                    append(label ?: linkToken ?: token)
                }
                if (label == null) {
                    builder.append(token, linkToken?.length ?: token.length, token.length)
                }
            }
            name != null && mid != null -> {
                val user = CommentUser(mid, name)
                builder.withLink(
                    LinkAnnotation.Clickable(name, linkStyles) { onAction(CommentReplyAction.OpenUser(user)) }
                ) {
                    append(token)
                }
            }
            emote != null -> {
                builder.appendInlineContent(emote.text, emote.text)
                parsedEmotes.add(emote)
            }
        }
        lastIndex = match.range.last + 1
    }
    if (lastIndex == 0) return null
    builder.append(text, lastIndex, text.length)
    return ParsedCommentText(builder.toAnnotatedString(), parsedEmotes)
}
