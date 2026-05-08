package com.naaammme.bbspace.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape

@Composable
fun CoverImage(
    url: String?,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    shape: Shape? = MaterialTheme.shapes.medium,
    fallbackContent: (@Composable BoxScope.() -> Unit)? = null,
    content: (@Composable BoxScope.() -> Unit)? = null
) {
    val bgColor = MaterialTheme.colorScheme.surfaceVariant
    val coverModifier = remember(shape, bgColor) {
        if (shape == null) {
            modifier.background(bgColor)
        } else {
            modifier
                .clip(shape)
                .background(bgColor)
        }
    }

    Box(
        modifier = coverModifier,
        contentAlignment = Alignment.Center
    ) {
        if (!url.isNullOrBlank()) {
            BiliAsyncImage(
                url = url,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                variant = BiliImageVariant.CardCover
            )
        } else {
            fallbackContent?.invoke(this)
        }
        content?.invoke(this)
    }
}
