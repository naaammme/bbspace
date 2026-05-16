package com.naaammme.bbspace.feature.dynamic.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naaammme.bbspace.core.designsystem.component.AvatarImage
import com.naaammme.bbspace.core.designsystem.component.CoverImage
import com.naaammme.bbspace.core.model.CommentSubject
import com.naaammme.bbspace.core.model.DynamicDetail
import com.naaammme.bbspace.core.model.DynamicDetailAuthor
import com.naaammme.bbspace.core.model.DynamicDetailParagraph
import com.naaammme.bbspace.core.model.DynamicImage
import com.naaammme.bbspace.core.model.DynamicStats
import com.naaammme.bbspace.core.model.SpaceRoute
import com.naaammme.bbspace.feature.comment.CommentPanel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DynamicDetailScreen(
    onBack: () -> Unit,
    onOpenSpace: (SpaceRoute) -> Unit,
    isExpanded: Boolean = false,
    viewModel: DynamicDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("动态详情") },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                navigationIcon = {
                    Text(
                        text = "←",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier
                            .padding(start = 16.dp, end = 12.dp)
                            .clickable(onClick = onBack)
                    )
                }
            )
        }
    ) { padding ->
        val detail = state.detail
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "加载中...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            state.errorMessage != null && detail == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = state.errorMessage.orEmpty().ifBlank { "加载失败" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "点击重试",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable(onClick = viewModel::retry)
                        )
                    }
                }
            }

            detail != null -> {
                val commentSubject = if (detail.replyBizId > 0L && detail.replyBizType > 0L) {
                    CommentSubject(
                        oid = detail.replyBizId,
                        type = detail.replyBizType
                    )
                } else {
                    null
                }
                if (isExpanded && commentSubject != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            detailContentItems(detail)
                        }
                        CommentPanel(
                            subject = commentSubject,
                            onOpenSpace = onOpenSpace,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentPadding = PaddingValues(
                                horizontal = 16.dp,
                                vertical = 12.dp
                            )
                        )
                    }
                } else if (commentSubject != null) {
                    CommentPanel(
                        subject = commentSubject,
                        onOpenSpace = onOpenSpace,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentPadding = PaddingValues(
                            horizontal = 16.dp,
                            vertical = 12.dp
                        ),
                        header = {
                            DetailContent(detail)
                        }
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .verticalScroll(rememberScrollState())
                    ) {
                        DetailContent(detail)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailContent(detail: DynamicDetail) {
    DynamicDetailHeader(
        author = detail.author,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
    detail.paragraphs.forEach { paragraph ->
        DynamicDetailParagraphItem(paragraph)
    }
    detail.stats?.let { stats ->
        DynamicDetailStats(stats)
    }
}

@Composable
private fun DynamicDetailHeader(
    author: DynamicDetailAuthor,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AvatarImage(
                url = author.face,
                contentDescription = author.name,
                modifier = Modifier.size(44.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = author.name.ifBlank { "用户" },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                author.pubTime?.let { time ->
                    Text(
                        text = time,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun DynamicDetailParagraphItem(paragraph: DynamicDetailParagraph) {
    when (paragraph.type) {
        DynamicDetailParagraph.TYPE_TEXT -> {
            paragraph.text?.let { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }
        DynamicDetailParagraph.TYPE_PICTURES -> {
            if (paragraph.images.isNotEmpty()) {
                DynamicDetailImageGrid(
                    images = paragraph.images,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun DynamicDetailImageGrid(images: List<DynamicImage>, modifier: Modifier = Modifier) {
    val columns = remember(images.size) {
        when (images.size) {
            1 -> 1
            2, 4 -> 2
            else -> 3
        }
    }
    val rows = remember(images, columns) {
        images.chunked(columns)
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        rows.forEach { rowImages ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                rowImages.forEach { image ->
                    CoverImage(
                        url = image.url,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(
                                if (image.width > 0 && image.height > 0) {
                                    image.width.toFloat() / image.height.toFloat()
                                } else {
                                    1f
                                }
                            ),
                        shape = MaterialTheme.shapes.small
                    )
                }
                repeat(columns - rowImages.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DynamicDetailStats(stats: DynamicStats) {
    val text = remember(stats) {
        buildString {
            if (stats.repost > 0) append("转发 ${formatCount(stats.repost)}")
            if (stats.reply > 0) {
                if (isNotEmpty()) append("  ")
                append("评论 ${formatCount(stats.reply)}")
            }
            if (stats.like > 0) {
                if (isNotEmpty()) append("  ")
                append("点赞 ${formatCount(stats.like)}")
            }
        }
    }
    if (text.isNotEmpty()) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}

private fun LazyListScope.detailContentItems(detail: DynamicDetail) {
    item(key = "detail_author", contentType = "author") {
        DynamicDetailHeader(
            author = detail.author,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }

    itemsIndexed(
        items = detail.paragraphs,
        key = { index, _ -> "detail_para_$index" },
        contentType = { _, para -> para.type }
    ) { _, paragraph ->
        DynamicDetailParagraphItem(paragraph)
    }

    detail.stats?.let { stats ->
        item(key = "detail_stats", contentType = "stats") {
            DynamicDetailStats(stats)
        }
    }
}

private fun formatCount(value: Long): String {
    return when {
        value >= 100_000_000L -> {
            val number = value / 100_000_000f
            if (number >= 10f) "${number.toInt()}亿" else "%.1f亿".format(number)
        }
        value >= 10_000L -> {
            val number = value / 10_000f
            if (number >= 10f) "${number.toInt()}万" else "%.1f万".format(number)
        }
        else -> value.toString()
    }
}
