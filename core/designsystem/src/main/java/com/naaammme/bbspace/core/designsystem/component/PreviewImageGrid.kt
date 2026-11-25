package com.naaammme.bbspace.core.designsystem.component

import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.size.Precision
import coil3.size.Scale
import com.naaammme.bbspace.core.common.log.Logger
import com.naaammme.bbspace.core.common.media.ImageSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

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
                        modifier = if (images.size == 1) Modifier.fillMaxWidth(0.72f) else Modifier.weight(1f)
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
        if (single) previewSingleImageRatio(image) else 1f
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

@Composable
fun PreviewImageDialog(
    images: List<PreviewImage>,
    startIdx: Int,
    onDismiss: () -> Unit
) {
    if (images.isEmpty()) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = startIdx.coerceIn(images.indices),
        pageCount = { images.size }
    )
    var savedOriginalUris by remember(images) { mutableStateOf<Map<String, Uri>>(emptyMap()) }
    var savingUrls by remember(images) { mutableStateOf<Set<String>>(emptySet()) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black.copy(alpha = 0.96f)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = 1,
                    key = { page -> "${page}_${images[page].url}" },
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val item = images[page]
                    PreviewImagePage(
                        image = item,
                        originalUri = savedOriginalUris[item.url]?.takeIf { pagerState.settledPage == page },
                        active = pagerState.currentPage == page,
                        onDismiss = onDismiss
                    )
                }

                PreviewTopBar(
                    pagerState = pagerState,
                    images = images,
                    savingUrls = savingUrls,
                    savedOriginalUris = savedOriginalUris,
                    onDismiss = onDismiss,
                    onSave = { currentImage ->
                        val url = currentImage.url
                        if (url in savingUrls || url in savedOriginalUris) return@PreviewTopBar
                        savingUrls = savingUrls + url
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching { ImageSaver.saveUrl(context, url) }
                            }
                            savingUrls = savingUrls - url
                            result.onSuccess { savedUri ->
                                savedOriginalUris = savedOriginalUris + (url to savedUri)
                                Toast.makeText(context, "已保存到相册", Toast.LENGTH_SHORT).show()
                            }.onFailure { err ->
                                Logger.e("PreviewImageDialog", err as? Exception) { "save image failed url=$url" }
                                Toast.makeText(context, err.message ?: "保存图片失败", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        }
    }
}

@Composable
private fun PreviewTopBar(
    pagerState: PagerState,
    images: List<PreviewImage>,
    savingUrls: Set<String>,
    savedOriginalUris: Map<String, Uri>,
    onDismiss: () -> Unit,
    onSave: (PreviewImage) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentImage = images.getOrNull(pagerState.currentPage)
    val isSaving = currentImage != null && currentImage.url in savingUrls
    val isSaved = currentImage != null && currentImage.url in savedOriginalUris
    val canSave = currentImage != null && !isSaving && !isSaved

    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterStart)) {
            Text(text = "关闭", color = Color.White)
        }
        Text(
            text = "${pagerState.currentPage + 1}/${images.size}",
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
            modifier = Modifier.align(Alignment.Center)
        )
        TextButton(
            onClick = { currentImage?.let(onSave) },
            enabled = canSave,
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Text(
                text = when {
                    isSaving -> "保存中"
                    isSaved -> "已保存"
                    else -> "保存"
                },
                color = Color.White.copy(alpha = if (canSave) 1f else 0.5f)
            )
        }
    }
}

@Composable
private fun PreviewImagePage(
    image: PreviewImage,
    originalUri: Uri?,
    active: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    var scale by remember(image.url) { mutableFloatStateOf(1f) }
    var offsetX by remember(image.url) { mutableFloatStateOf(0f) }
    var offsetY by remember(image.url) { mutableFloatStateOf(0f) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var intrinsicSize by remember(image.url) { mutableStateOf(Size.Unspecified) }
    var animJob by remember { mutableStateOf<Job?>(null) }

    var thumbCacheKey by remember(image.url) { mutableStateOf<MemoryCache.Key?>(null) }
    val thumbRequest = rememberBiliImageRequest(url = image.url, variant = BiliImageVariant.PreviewThumb)
    val activeRequest = remember(context, originalUri, thumbRequest, thumbCacheKey) {
        originalUri?.let { uri ->
            ImageRequest.Builder(context)
                .data(uri)
                .placeholderMemoryCacheKey(thumbCacheKey)
                .size(4096, 4096)
                .scale(Scale.FIT)
                .precision(Precision.INEXACT)
                .memoryCachePolicy(CachePolicy.DISABLED)
                .diskCachePolicy(CachePolicy.DISABLED)
                .build()
        } ?: thumbRequest
    }

    LaunchedEffect(active) {
        if (!active) {
            animJob?.cancel()
            scale = 1f
            offsetX = 0f
            offsetY = 0f
        }
    }

    AsyncImage(
        model = activeRequest,
        contentDescription = "图片预览",
        onSuccess = { result ->
            if (result.result.request.data == thumbRequest?.data) thumbCacheKey = result.result.memoryCacheKey
            val size = result.painter.intrinsicSize
            if (size.width.isFinite() && size.height.isFinite() && size.width > 0f && size.height > 0f) {
                intrinsicSize = size
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { viewport = it }
            .pointerInput(image.url) {
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    animJob?.cancel()
                    var panAccum = Offset.Zero
                    var handling = false
                    var prevPressedCount = currentEvent.changes.count { it.pressed }

                    while (true) {
                        val event = awaitPointerEvent()
                        val pressedCount = event.changes.count { it.pressed }
                        if (pressedCount == 0) break

                        val fit = computeFitSize(viewport, intrinsicSize, image)
                        if (pressedCount >= 2) {
                            handling = true
                            if (prevPressedCount >= 2) {
                                val oldScale = scale
                                val maxScale = if (fit.width > 0f && fit.height > 0f) {
                                    maxOf(30f, maxOf(viewport.width / fit.width, viewport.height / fit.height) * 10f)
                                } else {
                                    30f
                                }
                                val nextScale = (oldScale * event.calculateZoom()).coerceIn(1f, maxScale)
                                val factor = if (oldScale > 0f) nextScale / oldScale else 1f
                                val centroid = event.calculateCentroid(true)
                                val pan = event.calculatePan()
                                val clamped = clampOffset(
                                    viewport = viewport,
                                    fit = fit,
                                    x = offsetX * factor + (centroid.x - viewport.width * 0.5f) * (1f - factor) + pan.x,
                                    y = offsetY * factor + (centroid.y - viewport.height * 0.5f) * (1f - factor) + pan.y,
                                    scale = nextScale
                                )
                                scale = nextScale
                                offsetX = clamped.x
                                offsetY = clamped.y
                            }
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        } else {
                            val change = event.changes.first { it.pressed }
                            if (!change.isConsumed && prevPressedCount == 1) {
                                val pan = event.calculatePan()
                                val delta = when {
                                    handling -> pan
                                    scale > 1.02f -> {
                                        panAccum += pan
                                        if (panAccum.getDistance() <= slop) {
                                            null
                                        } else {
                                            val bx = ((fit.width * scale - viewport.width) * 0.5f).coerceAtLeast(0f)
                                            val atEdge = (panAccum.x < 0f && offsetX <= -bx + 1f) ||
                                                (panAccum.x > 0f && offsetX >= bx - 1f)
                                            if (abs(panAccum.x) > abs(panAccum.y) && (bx <= 1f || atEdge)) break
                                            handling = true
                                            panAccum
                                        }
                                    }
                                    else -> null
                                }
                                if (delta != null) {
                                    val clamped = clampOffset(viewport, fit, offsetX + delta.x, offsetY + delta.y, scale)
                                    offsetX = clamped.x
                                    offsetY = clamped.y
                                    if (change.positionChanged()) change.consume()
                                }
                            }
                        }
                        prevPressedCount = pressedCount
                    }
                }
            }
            .pointerInput(image.url) {
                detectTapGestures(
                    onTap = {
                        if (scale <= 1.02f) {
                            currentOnDismiss()
                        } else {
                            animJob?.cancel()
                            val s0 = scale
                            val x0 = offsetX
                            val y0 = offsetY
                            val fit = computeFitSize(viewport, intrinsicSize, image)
                            animJob = scope.launch {
                                animate(1f, 0f, animationSpec = tween(220, easing = FastOutSlowInEasing)) { p, _ ->
                                    val s = 1f + (s0 - 1f) * p
                                    val clamped = clampOffset(viewport, fit, x0 * p, y0 * p, s)
                                    scale = s
                                    offsetX = clamped.x
                                    offsetY = clamped.y
                                }
                            }
                        }
                    }
                )
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offsetX
                translationY = offsetY
            },
        contentScale = ContentScale.Fit
    )
}

private fun computeFitSize(viewport: IntSize, intrinsicSize: Size, image: PreviewImage): Size {
    val vw = viewport.width.toFloat()
    val vh = viewport.height.toFloat()
    if (vw <= 0f || vh <= 0f) return Size.Zero
    val ratio = when {
        intrinsicSize.width.isFinite() && intrinsicSize.height.isFinite() &&
            intrinsicSize.width > 0f && intrinsicSize.height > 0f -> intrinsicSize.width / intrinsicSize.height
        image.width > 0f && image.height > 0f -> image.width / image.height
        else -> return Size(vw, vh)
    }
    return if (ratio > vw / vh) Size(vw, vw / ratio) else Size(vh * ratio, vh)
}

private fun clampOffset(viewport: IntSize, fit: Size, x: Float, y: Float, scale: Float): Offset {
    val bx = ((fit.width * scale - viewport.width) * 0.5f).coerceAtLeast(0f)
    val by = ((fit.height * scale - viewport.height) * 0.5f).coerceAtLeast(0f)
    return Offset(x.coerceIn(-bx, bx), y.coerceIn(-by, by))
}

private fun previewGridColumns(count: Int): Int = when (count) {
    1 -> 1
    2, 4 -> 2
    else -> 3
}

private fun previewSingleImageRatio(image: PreviewImage): Float =
    if (image.width > 0f && image.height > 0f) {
        (image.width / image.height).coerceIn(0.75f, 1.8f)
    } else {
        4f / 3f
    }
