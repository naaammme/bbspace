package com.naaammme.bbspace.feature.comment

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.naaammme.bbspace.core.common.media.thumbnailUrl
import com.naaammme.bbspace.core.designsystem.component.PreviewImage
import com.naaammme.bbspace.core.designsystem.component.PreviewImageRow
import com.naaammme.bbspace.core.model.CommentReply
import com.naaammme.bbspace.core.model.CommentUser
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CommentCard(
    reply: CommentReply,
    isLoading: (Long) -> Boolean,
    onTranslate: (Long) -> Unit,
    onSaveImage: (PreviewImage) -> Unit,
    onOpenReplies: (CommentReply) -> Unit,
    onOpenUser: (CommentUser) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column {
            ReplyBody(
                reply = reply,
                isLoading = isLoading,
                onTranslate = onTranslate,
                onSaveImage = onSaveImage,
                onOpenUser = onOpenUser,
                modifier = Modifier.padding(16.dp)
            )

            if (reply.replyCount > 0L) {
                HorizontalDivider()
                TextButton(
                    onClick = { onOpenReplies(reply) },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        reply.replyEntryText
                            ?.takeIf(String::isNotBlank)
                            ?: "查看 ${reply.replyCount.formatCount()} 条回复"
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ThreadReplyCard(
    reply: CommentReply,
    isLoading: (Long) -> Boolean,
    onTranslate: (Long) -> Unit,
    onSaveImage: (PreviewImage) -> Unit,
    onOpenUser: (CommentUser) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth()
    ) {
        ReplyBody(
            reply = reply,
            isLoading = isLoading,
            onTranslate = onTranslate,
            onSaveImage = onSaveImage,
            onOpenUser = onOpenUser,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReplyBody(
    reply: CommentReply,
    isLoading: (Long) -> Boolean,
    onTranslate: (Long) -> Unit,
    onSaveImage: (PreviewImage) -> Unit,
    onOpenUser: (CommentUser) -> Unit,
    modifier: Modifier = Modifier
) {
    val previewImages = remember(reply.pictures) {
        reply.pictures.map { picture ->
            PreviewImage(
                url = picture.url,
                thumbnailUrl = thumbnailUrl(picture.url) ?: picture.url,
                width = picture.width,
                height = picture.height
            )
        }
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        UserAvatar(
            name = reply.user.name,
            face = reply.user.face,
            onClick = { onOpenUser(reply.user) }
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        modifier = Modifier.clickable { onOpenUser(reply.user) },
                        text = reply.user.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        reply.user.level?.let { level ->
                            MiniChip("Lv.$level")
                        }
                        reply.user.vipLabel?.takeIf(String::isNotBlank)?.let { vip ->
                            MiniChip(vip)
                        }
                        reply.user.medal?.let { medal ->
                            MiniChip(
                                if (medal.level > 0) {
                                    "${medal.name} ${medal.level}"
                                } else {
                                    medal.name
                                }
                            )
                        }
                    }
                }
                reply.topLabel?.let { label ->
                    MiniChip(label)
                }
            }

            ReplyMessage(reply)

            if (previewImages.isNotEmpty()) {
                PreviewImageRow(
                    images = previewImages,
                    onSaveImage = onSaveImage
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = reply.timeText.ifBlank { "刚刚" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "点赞 ${reply.likeCount.formatCount()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (reply.replyCount > 0L) {
                        Text(
                            text = "回复 ${reply.replyCount.formatCount()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                ReplyMenuButton(
                    loading = isLoading(reply.rpid),
                    onTranslate = { onTranslate(reply.rpid) }
                )
            }
        }
    }
}

@Composable
private fun ReplyMessage(reply: CommentReply) {
    val message = reply.message.takeIf(String::isNotBlank)
    val translated = reply.translatedMessage?.takeIf(String::isNotBlank)
    if (message == null && translated == null) return
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        reply.parentName?.takeIf(String::isNotBlank)?.let { name ->
            Text(
                text = "回复 @$name",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        message?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        translated?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ReplyMenuButton(
    loading: Boolean,
    onTranslate: () -> Unit
) {
    var show by remember { mutableStateOf(false) }

    Box {
        IconButton(
            onClick = { show = true },
            enabled = !loading
        ) {
            Icon(Icons.Default.MoreVert, contentDescription = null)
        }
        DropdownMenu(
            expanded = show,
            onDismissRequest = { show = false }
        ) {
            DropdownMenuItem(
                text = { Text("评论翻译") },
                onClick = {
                    show = false
                    onTranslate()
                }
            )
        }
    }
}

@Composable
private fun UserAvatar(
    name: String,
    face: String?,
    onClick: () -> Unit
) {
    val modifier = Modifier.size(44.dp)
    if (!face.isNullOrBlank()) {
        val context = LocalContext.current
        val req = remember(face) {
            ImageRequest.Builder(context)
                .data(thumbnailUrl(face))
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .build()
        }
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            AsyncImage(
                model = req,
                contentDescription = name,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        }
    } else {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Box(
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = name.take(1).ifBlank { "?" },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
private fun MiniChip(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

internal fun Long.formatCount(): String {
    return when {
        this >= 100_000_000L -> formatDecimal(this / 100_000_000f, "亿")
        this >= 10_000L -> formatDecimal(this / 10_000f, "万")
        else -> toString()
    }
}

private fun formatDecimal(
    value: Float,
    suffix: String
): String {
    val text = String.format(Locale.ROOT, "%.1f", value).trimEnd('0').trimEnd('.')
    return "$text$suffix"
}
