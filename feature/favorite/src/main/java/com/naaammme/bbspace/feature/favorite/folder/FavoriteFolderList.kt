package com.naaammme.bbspace.feature.favorite.folder

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.naaammme.bbspace.core.designsystem.component.CoverImage
import com.naaammme.bbspace.core.model.FavoriteFolder

@Composable
fun FavoriteFolderList(
    folders: List<FavoriteFolder>,
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(
            items = folders,
            key = { "${it.fid}:${it.id}" },
            contentType = { "folder" }
        ) { folder ->
            FavoriteFolderCard(folder = folder)
        }
    }
}

@Composable
private fun FavoriteFolderCard(
    folder: FavoriteFolder,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CoverImage(
                url = folder.cover,
                contentDescription = null,
                modifier = Modifier
                    .weight(0.28f)
                    .aspectRatio(16f / 10f),
                fallbackContent = {
                    Icon(
                        imageVector = Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = folder.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = remember(folder.attrDesc, folder.mediaCount, folder.isTop, folder.createdAtSec) {
                        buildFolderSubtitle(folder)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun buildFolderSubtitle(folder: FavoriteFolder): String {
    return buildList {
        if (folder.isTop) add("置顶")
        val desc = folder.attrDesc?.trim()
        if (!desc.isNullOrBlank()) {
            add(desc)
        } else {
            add("${folder.mediaCount}个内容")
        }
        folder.createdAtSec.takeIf { it > 0L }?.let {
            add("创建于 ${DateFormat.format("yyyy-MM-dd", it * 1000).toString()}")
        }
    }.joinToString(" · ")
}
