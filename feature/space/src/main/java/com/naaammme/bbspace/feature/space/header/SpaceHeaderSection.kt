package com.naaammme.bbspace.feature.space.header

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.naaammme.bbspace.core.designsystem.component.AvatarImage
import com.naaammme.bbspace.core.designsystem.component.BiliAsyncImage
import com.naaammme.bbspace.core.designsystem.component.PreviewImage
import com.naaammme.bbspace.core.designsystem.component.PreviewImageDialog
import com.naaammme.bbspace.core.designsystem.component.SelectableText
import com.naaammme.bbspace.feature.space.SpaceHeaderUiState

internal fun LazyListScope.spaceHeaderSection(
    state: SpaceHeaderUiState,
    onOpenFollowings: () -> Unit,
    onOpenFollowers: () -> Unit
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
        ProfileCard(
            state = state,
            onOpenFollowings = onOpenFollowings,
            onOpenFollowers = onOpenFollowers
        )
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
        BiliAsyncImage(
            url = imageUrl,
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
private fun ProfileCard(
    state: SpaceHeaderUiState,
    onOpenFollowings: () -> Unit,
    onOpenFollowers: () -> Unit
) {
    val profile = state.profile
    val faceUrl = profile.face
    var showAvatarPreview by remember { mutableStateOf(false) }

    if (showAvatarPreview && !faceUrl.isNullOrBlank()) {
        PreviewImageDialog(
            images = listOf(PreviewImage(url = faceUrl)),
            startIdx = 0,
            onDismiss = { showAvatarPreview = false },
            onSaveImage = null
        )
    }

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
                AvatarImage(
                    url = faceUrl,
                    contentDescription = profile.name,
                    modifier = Modifier
                        .size(72.dp)
                        .clickable(enabled = !faceUrl.isNullOrBlank()) {
                            showAvatarPreview = true
                        },
                    fallbackContent = {
                        Text(
                            text = profile.name.take(1),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SelectableText(
                        text = profile.name,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    SelectableText(
                        text = "UID ${profile.mid}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SelectableText(
                            text = "Lv${profile.level}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        profile.vipLabel?.let { vipLabel ->
                            Surface(
                                color = MaterialTheme.colorScheme.secondary,
                                shape = MaterialTheme.shapes.extraSmall
                            ) {
                                Text(
                                    text = vipLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (profile.sign.isNotBlank()) {
                SelectableText(
                    text = profile.sign,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SpaceStatChip("粉丝", profile.fansCount.toString(), onClick = onOpenFollowers)
                SpaceStatChip("关注", profile.followingCount.toString(), onClick = onOpenFollowings)
                if (profile.likeCount > 0L) {
                    SpaceStatChip("获赞", profile.likeCount.toString())
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
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
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
private fun SpaceStatChip(
    label: String,
    value: String,
    onClick: (() -> Unit)? = null
) {
    val shape = MaterialTheme.shapes.small
    Surface(
        modifier = if (onClick != null) {
            Modifier
                .clip(shape)
                .clickable(onClick = onClick)
        } else {
            Modifier
        },
        color = MaterialTheme.colorScheme.surface,
        shape = shape
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SelectableText(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SelectableText(
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
        SelectableText(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}
