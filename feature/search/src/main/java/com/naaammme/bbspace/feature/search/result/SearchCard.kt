package com.naaammme.bbspace.feature.search.result

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.naaammme.bbspace.core.designsystem.component.CoverImage
import com.naaammme.bbspace.core.designsystem.component.rememberHighlightedTitle
import com.naaammme.bbspace.core.model.SearchAuthor
import com.naaammme.bbspace.core.model.SearchFeedbackSec
import com.naaammme.bbspace.core.model.SearchVideo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchAuthorCard(
    author: SearchAuthor,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CoverImage(
                url = author.avatar,
                contentDescription = author.name,
                modifier = Modifier.size(64.dp),
                shape = MaterialTheme.shapes.large
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = author.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                author.sign?.let { sign ->
                    Text(
                        text = sign,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "粉丝 ${author.fansText} · 稿件 ${author.archivesText} · Lv${author.level}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchCard(
    video: SearchVideo,
    onClick: () -> Unit
) {
    val titleText = rememberHighlightedTitle(
        text = video.title,
        highlightColor = MaterialTheme.colorScheme.primary
    )
    val metaText = remember(video.danmakuText, video.publishTimeText) {
        listOfNotNull("${video.danmakuText} 弹幕", video.publishTimeText).joinToString(" · ")
    }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CoverImage(
                url = video.cover,
                contentDescription = video.title,
                modifier = Modifier
                    .weight(0.38f)
                    .aspectRatio(16f / 10f)
            ) {
                Text(
                    text = video.viewText,
                    modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
                Text(
                    text = video.duration,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
            }
            Column(
                modifier = Modifier.weight(0.62f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(text = titleText, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)

                Text(
                    text = video.author,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = metaText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (video.feedbacks.isNotEmpty()) {
                        SearchFeedbackMenu(video.feedbacks)
                    }
                }

                video.reason?.let { reason ->
                    Text(
                        text = reason,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.secondaryContainer,
                                MaterialTheme.shapes.extraSmall
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchFeedbackMenu(feedbacks: List<SearchFeedbackSec>) {
    var show by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(24.dp)
            .clickable { show = true },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.MoreVert,
            contentDescription = "反馈"
        )
    }

    if (show) {
        AlertDialog(
            onDismissRequest = { show = false },
            confirmButton = {
                TextButton(onClick = { show = false }) {
                    Text("关闭")
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    feedbacks.forEachIndexed { secIndex, sec ->
                        Text(
                            text = sec.title.ifBlank { sec.type.ifBlank { "反馈" } },
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        sec.items.forEachIndexed { itemIndex, item ->
                            Text(
                                text = item.text,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                            if (itemIndex != sec.items.lastIndex) {
                                HorizontalDivider()
                            }
                        }
                        if (secIndex != feedbacks.lastIndex) {
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }
            }
        )
    }
}
