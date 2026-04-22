package com.naaammme.bbspace.feature.space

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.naaammme.bbspace.core.common.media.thumbnailUrl
import com.naaammme.bbspace.feature.space.model.SpaceHeaderUiState
import java.util.Locale

internal fun LazyListScope.spaceHeaderSection(
    state: SpaceHeaderUiState
) {
    state.bannerUrl?.let { banner ->
        item(
            key = "header_banner",
            contentType = "banner"
        ) {
            BannerCard(imageUrl = banner)
        }
    }

    item(
        key = "header_profile",
        contentType = "profile"
    ) {
        ProfileCard(state = state)
    }
}

@Composable
private fun BannerCard(imageUrl: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 6f),
            contentScale = ContentScale.Crop
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ProfileCard(state: SpaceHeaderUiState) {
    val profile = state.profile
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
                SpaceAvatar(
                    imageUrl = profile.face,
                    name = profile.name
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "UID ${profile.mid}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Lv${profile.level}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (profile.sign.isNotBlank()) {
                Text(
                    text = profile.sign,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SpaceStatChip("粉丝", formatCount(profile.fansCount))
                SpaceStatChip("关注", formatCount(profile.followingCount))
                if (profile.likeCount > 0L) {
                    SpaceStatChip("获赞", formatCount(profile.likeCount))
                }
                SpaceStatChip("视频", profile.videoCount.toString())
                if (profile.articleCount > 0) {
                    SpaceStatChip("图文", profile.articleCount.toString())
                }
                if (profile.seasonCount > 0) {
                    SpaceStatChip("合集", profile.seasonCount.toString())
                }
                if (profile.seriesCount > 0) {
                    SpaceStatChip("系列", profile.seriesCount.toString())
                }
            }

            if (profile.tags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    profile.tags.forEach { tag ->
                        TagChip(text = tag)
                    }
                }
            }
        }
    }
}

@Composable
private fun SpaceAvatar(
    imageUrl: String?,
    name: String
) {
    val context = LocalContext.current
    if (!imageUrl.isNullOrBlank()) {
        val imageRequest = remember(context, imageUrl) {
            ImageRequest.Builder(context)
                .data(thumbnailUrl(imageUrl))
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .build()
        }
        AsyncImage(
            model = imageRequest,
            contentDescription = name,
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name.take(1),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SpaceStatChip(
    label: String,
    value: String
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun TagChip(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

private fun formatCount(count: Long): String {
    return when {
        count >= 100_000_000L -> formatDecimal(count / 100_000_000f, "亿")
        count >= 10_000L -> formatDecimal(count / 10_000f, "万")
        else -> count.toString()
    }
}

private fun formatDecimal(
    value: Float,
    suffix: String
): String {
    val text = String.format(Locale.ROOT, "%.1f", value)
        .trimEnd('0')
        .trimEnd('.')
    return "$text$suffix"
}
