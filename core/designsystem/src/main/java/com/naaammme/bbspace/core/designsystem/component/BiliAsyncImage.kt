package com.naaammme.bbspace.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.naaammme.bbspace.core.common.media.BILI_IMAGE_DEFAULT_Q
import com.naaammme.bbspace.core.common.media.coverThumbnailUrl
import com.naaammme.bbspace.core.common.media.originImageUrl
import com.naaammme.bbspace.core.common.media.thumbnailUrl

enum class BiliImageVariant(
    val q: Int = BILI_IMAGE_DEFAULT_Q,
    val original: Boolean = false
) {
    Avatar,
    CardCover,
    PreviewThumb,
    Banner,
    Original(original = true)
}

@Composable
fun rememberBiliImageRequest(
    url: String?,
    variant: BiliImageVariant = BiliImageVariant.CardCover
): ImageRequest? {
    if (url.isNullOrBlank()) return null
    val context = LocalContext.current
    return remember(context, url, variant) {
        val resolvedUrl = when {
            variant.original -> originImageUrl(url)
            variant == BiliImageVariant.CardCover -> coverThumbnailUrl(url)
            else -> thumbnailUrl(url, variant.q)
        }
        ImageRequest.Builder(context)
            .data(resolvedUrl)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
    }
}

@Composable
fun BiliAsyncImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    variant: BiliImageVariant = BiliImageVariant.CardCover,
    contentScale: ContentScale = ContentScale.Crop
) {
    val request = rememberBiliImageRequest(url = url, variant = variant)
    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale
    )
}
