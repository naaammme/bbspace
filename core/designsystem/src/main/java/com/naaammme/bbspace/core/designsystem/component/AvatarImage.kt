package com.naaammme.bbspace.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip

@Composable
fun AvatarImage(
    url: String?,
    contentDescription: String,
    modifier: Modifier = Modifier,
    fallbackText: String? = null,
    fallbackContainerColor: Color = Color.Unspecified,
    fallbackContent: (@Composable BoxScope.() -> Unit)? = null
) {
    val shape = CircleShape
    val containerColor = if (fallbackContainerColor != Color.Unspecified) {
        fallbackContainerColor
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    if (url.isNullOrBlank()) {
        Box(
            modifier = modifier
                .clip(shape)
                .background(containerColor),
            contentAlignment = Alignment.Center
        ) {
            when {
                fallbackContent != null -> fallbackContent()
                !fallbackText.isNullOrBlank() -> {
                    Text(
                        text = fallbackText,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        return
    }
    BiliAsyncImage(
        url = url,
        contentDescription = contentDescription,
        modifier = modifier.clip(shape),
        variant = BiliImageVariant.Avatar
    )
}
