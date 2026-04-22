package com.naaammme.bbspace.core.designsystem.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest

@Immutable
data class PreviewImage(
    val url: String,
    val thumbnailUrl: String = url,
    val width: Float = 0f,
    val height: Float = 0f
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PreviewImageRow(
    images: List<PreviewImage>,
    modifier: Modifier = Modifier,
    onSaveImage: ((PreviewImage) -> Unit)? = null
) {
    if (images.isEmpty()) return

    val context = LocalContext.current
    var previewIdx by remember { mutableIntStateOf(-1) }

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(
            items = images,
            key = { index, image -> "${image.url}_$index" }
        ) { index, image ->
            val req = remember(image.thumbnailUrl) {
                ImageRequest.Builder(context)
                    .data(image.thumbnailUrl)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .build()
            }
            val ratio = remember(image.width, image.height) {
                if (image.width > 0f && image.height > 0f) {
                    (image.width / image.height).coerceIn(0.75f, 2f)
                } else {
                    4f / 3f
                }
            }
            AsyncImage(
                model = req,
                contentDescription = "图片",
                modifier = Modifier
                    .width(168.dp)
                    .aspectRatio(ratio)
                    .clip(MaterialTheme.shapes.large)
                    .clickable { previewIdx = index },
                contentScale = ContentScale.Crop
            )
        }
    }

    if (previewIdx in images.indices) {
        PreviewImageDialog(
            images = images,
            startIdx = previewIdx,
            onDismiss = { previewIdx = -1 },
            onSaveImage = onSaveImage
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PreviewImageDialog(
    images: List<PreviewImage>,
    startIdx: Int,
    onDismiss: () -> Unit,
    onSaveImage: ((PreviewImage) -> Unit)?
) {
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
                        onSaveImage = onSaveImage?.let { save -> { save(images[page]) } },
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
    onSaveImage: (() -> Unit)?,
    onScaleChange: (Float) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val req = remember(image.url) {
        ImageRequest.Builder(context)
            .data(image.url)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
    }
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
        val tfState = rememberTransformableState { zoomChange, panChange, _ ->
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

        AsyncImage(
            model = req,
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
                .combinedClickable(
                    onClick = {
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            onDismiss()
                        }
                    },
                    onLongClick = onSaveImage
                )
                .transformable(
                    state = tfState,
                    canPan = { scale > 1f }
                ),
            contentScale = ContentScale.Fit
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
