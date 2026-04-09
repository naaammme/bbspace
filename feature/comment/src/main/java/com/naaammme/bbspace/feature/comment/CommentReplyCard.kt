package com.naaammme.bbspace.feature.comment

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.naaammme.bbspace.core.common.log.Logger
import com.naaammme.bbspace.core.common.media.ImageSaver
import com.naaammme.bbspace.core.model.CommentPicture
import com.naaammme.bbspace.core.model.CommentReply
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CommentCard(reply: CommentReply) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                UserAvatar(
                    name = reply.user.name,
                    face = reply.user.face
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
                                text = reply.user.name,
                                style = MaterialTheme.typography.titleSmall
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

                    reply.message.takeIf(String::isNotBlank)?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    if (reply.pictures.isNotEmpty()) {
                        PictureRow(reply.pictures)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                }
            }

            if (reply.replies.isNotEmpty()) {
                HorizontalDivider()
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    reply.replyEntryText?.let { title ->
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    reply.replies.forEach { child ->
                        SubReplyCard(reply = child)
                    }
                }
            }
        }
    }
}

@Composable
private fun SubReplyCard(reply: CommentReply) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
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
                        text = reply.user.name,
                        style = MaterialTheme.typography.titleSmall
                    )
                    reply.topLabel?.let { label ->
                        MiniChip(label)
                    }
                }
                Text(
                    text = reply.timeText.ifBlank { "刚刚" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            reply.message.takeIf(String::isNotBlank)?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (reply.pictures.isNotEmpty()) {
                PictureRow(reply.pictures)
            }

            if (reply.replyCount > 0L) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "回复 ${reply.replyCount.formatCount()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PictureRow(pictures: List<CommentPicture>) {
    val context = LocalContext.current
    val appCtx = remember(context) { context.applicationContext }
    val scope = rememberCoroutineScope()
    var previewIdx by remember { mutableIntStateOf(-1) }
    var pendingPicture by remember { mutableStateOf<CommentPicture?>(null) }
    val savePicture: (CommentPicture) -> Unit = { picture ->
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { ImageSaver.saveUrl(appCtx, picture.url) }
            }
            result
                .onSuccess {
                    Toast.makeText(context, "已保存到相册", Toast.LENGTH_SHORT).show()
                }
                .onFailure { err ->
                    Logger.e(TAG, err as? Exception) {
                        "save comment picture failed url=${picture.url}"
                    }
                    Toast.makeText(
                        context,
                        err.message ?: "保存图片失败",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val picture = pendingPicture
        pendingPicture = null
        if (granted && picture != null) {
            savePicture(picture)
        } else if (picture != null) {
            Toast.makeText(
                context,
                "需要存储权限才能保存图片",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    val onSave: (CommentPicture) -> Unit = { picture ->
        if (
            ImageSaver.needsLegacyWritePermission() &&
            context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            pendingPicture = picture
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            savePicture(picture)
        }
    }

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(
            items = pictures,
            key = { idx, picture -> "${picture.url}_$idx" }
        ) { idx, picture ->
            PictureItem(
                picture = picture,
                onClick = { previewIdx = idx },
                onLongClick = { onSave(picture) }
            )
        }
    }

    if (previewIdx in pictures.indices) {
        CommentPicturePreview(
            pictures = pictures,
            startIdx = previewIdx,
            onDismiss = { previewIdx = -1 },
            onSave = onSave
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PictureItem(
    picture: CommentPicture,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current
    val req = remember(picture.url) {
        ImageRequest.Builder(context)
            .data(picture.url)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
    }
    val ratio = remember(picture.width, picture.height) {
        if (picture.width > 0f && picture.height > 0f) {
            (picture.width / picture.height).coerceIn(0.75f, 2f)
        } else {
            4f / 3f
        }
    }
    AsyncImage(
        model = req,
        contentDescription = "评论图片",
        modifier = Modifier
            .width(168.dp)
            .aspectRatio(ratio)
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        contentScale = ContentScale.Crop
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CommentPicturePreview(
    pictures: List<CommentPicture>,
    startIdx: Int,
    onDismiss: () -> Unit,
    onSave: (CommentPicture) -> Unit
) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(
        initialPage = startIdx,
        pageCount = { pictures.size }
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black.copy(alpha = 0.96f)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val picture = pictures[page]
                    val req = remember(picture.url) {
                        ImageRequest.Builder(context)
                            .data(picture.url)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .build()
                    }
                    val isCur = pagerState.currentPage == page
                    var scale by remember(picture.url) { mutableFloatStateOf(1f) }
                    var offset by remember(picture.url) { mutableStateOf(Offset.Zero) }
                    val tfState = rememberTransformableState { zoomChange, panChange, _ ->
                        val nextScale = (scale * zoomChange).coerceIn(1f, 5f)
                        scale = nextScale
                        offset = if (nextScale == 1f) {
                            Offset.Zero
                        } else {
                            offset + panChange
                        }
                    }

                    LaunchedEffect(isCur) {
                        if (!isCur) {
                            scale = 1f
                            offset = Offset.Zero
                        }
                    }

                    AsyncImage(
                        model = req,
                        contentDescription = "评论图片预览",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offset.x
                                translationY = offset.y
                            }
                            .combinedClickable(
                                onClick = {
                                    if (scale > 1f) {
                                        scale = 1f
                                        offset = Offset.Zero
                                    } else {
                                        onDismiss()
                                    }
                                },
                                onLongClick = { onSave(picture) }
                            )
                            .transformable(
                                state = tfState,
                                canPan = { scale > 1f }
                            ),
                        contentScale = ContentScale.Fit
                    )
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = "关闭",
                            color = Color.White
                        )
                    }
                    Text(
                        text = "${pagerState.currentPage + 1}/${pictures.size}",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White
                    )
                }

                Text(
                    text = "单击关闭 双指缩放 长按保存",
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.88f)
                )
            }
        }
    }
}

@Composable
private fun UserAvatar(
    name: String,
    face: String?
) {
    if (!face.isNullOrBlank()) {
        val context = LocalContext.current
        val req = remember(face) {
            ImageRequest.Builder(context)
                .data(face)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .build()
        }
        AsyncImage(
            model = req,
            contentDescription = name,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    } else {
        Surface(
            modifier = Modifier.size(44.dp),
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

private const val TAG = "CommentReplyCard"
