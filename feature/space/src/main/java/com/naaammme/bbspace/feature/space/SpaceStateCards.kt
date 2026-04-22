package com.naaammme.bbspace.feature.space

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.naaammme.bbspace.core.designsystem.component.SkeletonBlock
import com.naaammme.bbspace.core.designsystem.component.VideoListCardSkeleton

@Composable
internal fun SpaceLoading(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(
            key = "loading_banner",
            contentType = "banner"
        ) {
            SkeletonBlock(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 6f),
                shape = MaterialTheme.shapes.medium
            )
        }
        item(
            key = "loading_profile",
            contentType = "profile"
        ) {
            SpaceProfileSkeleton()
        }
        items(
            count = PAGE_SKELETON_COUNT,
            key = { index -> "loading_video_$index" },
            contentType = { "video_skeleton" }
        ) {
            VideoListCardSkeleton()
        }
    }
}

@Composable
private fun SpaceProfileSkeleton() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SkeletonBlock(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape),
                    shape = CircleShape
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SkeletonBlock(
                        modifier = Modifier
                            .fillMaxWidth(0.42f)
                            .height(22.dp),
                        shape = MaterialTheme.shapes.extraSmall
                    )
                    SkeletonBlock(
                        modifier = Modifier
                            .fillMaxWidth(0.3f)
                            .height(16.dp),
                        shape = MaterialTheme.shapes.extraSmall
                    )
                }
            }
            SkeletonBlock(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp),
                shape = MaterialTheme.shapes.extraSmall
            )
            SkeletonBlock(
                modifier = Modifier
                    .fillMaxWidth(0.78f)
                    .height(16.dp),
                shape = MaterialTheme.shapes.extraSmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(4) {
                    SkeletonBlock(
                        modifier = Modifier
                            .width(68.dp)
                            .height(28.dp),
                        shape = MaterialTheme.shapes.small
                    )
                }
            }
        }
    }
}

@Composable
internal fun SpaceError(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        RetryCard(
            text = message,
            button = "重试",
            onRetry = onRetry,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
internal fun StateCard(
    text: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
internal fun RetryCard(
    text: String,
    button: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
            TextButton(onClick = onRetry) {
                Text(button)
            }
        }
    }
}

private const val PAGE_SKELETON_COUNT = 4
