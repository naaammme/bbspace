package com.naaammme.bbspace.core.designsystem.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.widget.Toast
import androidx.compose.runtime.rememberCoroutineScope
import com.naaammme.bbspace.core.common.media.ImageSaver
import com.naaammme.bbspace.core.common.log.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class PreviewImage(
    val url: String,
    val width: Float = 0f,
    val height: Float = 0f
)

@Composable
fun PreviewImageGrid(
    images: List<PreviewImage>,
    modifier: Modifier = Modifier
) {
    if (images.isEmpty()) return

    var previewIdx by remember { mutableIntStateOf(-1) }
    val columns = remember(images.size) { previewGridColumns(images.size) }
    val rows = remember(images, columns) { images.withIndex().chunked(columns) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        rows.forEach { rowImages ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowImages.forEach { item ->
                    PreviewGridItem(
                        image = item.value,
                        single = images.size == 1,
                        onClick = { previewIdx = item.index },
                        modifier = if (images.size == 1) {
                            Modifier.fillMaxWidth(0.72f)
                        } else {
                            Modifier.weight(1f)
                        }
                    )
                }
                if (images.size > 1) {
                    repeat(columns - rowImages.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }

    if (previewIdx in images.indices) {
        PreviewImageDialog(
            images = images,
            startIdx = previewIdx,
            onDismiss = { previewIdx = -1 }
        )
    }
}

@Composable
private fun PreviewGridItem(
    image: PreviewImage,
    single: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ratio = remember(image.width, image.height, single) {
        if (single) {
            previewSingleImageRatio(image)
        } else {
            1f
        }
    }
    BiliAsyncImage(
        url = image.url,
        contentDescription = "图片",
        modifier = modifier
            .aspectRatio(ratio)
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick),
        contentScale = ContentScale.Crop,
        variant = BiliImageVariant.PreviewThumb
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PreviewImageDialog(
    images: List<PreviewImage>,
    startIdx: Int,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = startIdx,
        pageCount = { images.size }
    )
    var currentScale by remember(startIdx) { mutableFloatStateOf(1f) }

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
                    userScrollEnabled = currentScale <= 1.01f,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    PreviewImagePage(
                        image = images[page],
                        active = pagerState.currentPage == page,
                        onDismiss = onDismiss,
                        onScaleChange = { scale ->
                            if (pagerState.currentPage == page) {
                                currentScale = scale
                            }
                        }
                    )
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.CenterStart)
                    ) {
                        Text(
                            text = "关闭",
                            color = Color.White
                        )
                    }
                    Text(
                        text = "${pagerState.currentPage + 1}/${images.size}",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    TextButton(
                        onClick = {
                            val currentImage = images.getOrNull(pagerState.currentPage)
                            if (currentImage != null) {
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        runCatching { ImageSaver.saveUrl(context, currentImage.url) }
                                    }
                                    result.onSuccess {
                                        Toast.makeText(context, "已保存到相册", Toast.LENGTH_SHORT).show()
                                    }.onFailure { err ->
                                        Logger.e("PreviewImageDialog", err as? Exception) { "save image failed url=${currentImage.url}" }
                                        Toast.makeText(context, err.message ?: "保存图片失败", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        Text(
                            text = "保存",
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PreviewImagePage(
    image: PreviewImage,
    active: Boolean,
    onDismiss: () -> Unit,
    onScaleChange: (Float) -> Unit
) {
    val density = LocalDensity.current
    var scale by remember(image.url) { mutableFloatStateOf(1f) }
    var offset by remember(image.url) { mutableStateOf(Offset.Zero) }

    LaunchedEffect(active) {
        if (!active) {
            scale = 1f
            offset = Offset.Zero
        }
    }
    LaunchedEffect(scale, active) {
        if (active) {
            onScaleChange(scale)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val tfState = rememberTransformableState { _, zoomChange, panChange, _ ->
            val nextScale = (scale * zoomChange).coerceIn(1f, 5f)
            scale = nextScale
            offset = if (nextScale <= 1f) {
                Offset.Zero
            } else {
                clampOffset(
                    offset = offset + panChange,
                    maxX = widthPx * (nextScale - 1f) / 2f,
                    maxY = heightPx * (nextScale - 1f) / 2f
                )
            }
        }

        BiliAsyncImage(
            url = image.url,
            contentDescription = "图片预览",
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
                .clickable {
                    if (scale > 1f) {
                        scale = 1f
                        offset = Offset.Zero
                    } else {
                        onDismiss()
                    }
                }
                .transformable(
                    state = tfState,
                    canPan = { scale > 1f }
                ),
            contentScale = ContentScale.Fit,
            variant = BiliImageVariant.PreviewThumb
        )
    }
}

private fun clampOffset(
    offset: Offset,
    maxX: Float,
    maxY: Float
): Offset {
    return Offset(
        x = offset.x.coerceIn(-maxX, maxX),
        y = offset.y.coerceIn(-maxY, maxY)
    )
}

private fun previewGridColumns(count: Int): Int {
    return when (count) {
        1 -> 1
        2, 4 -> 2
        else -> 3
    }
}

private fun previewSingleImageRatio(image: PreviewImage): Float {
    return if (image.width > 0f && image.height > 0f) {
        (image.width / image.height).coerceIn(0.75f, 1.8f)
    } else {
        4f / 3f
    }
}
