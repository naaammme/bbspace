package com.naaammme.bbspace.feature.search

import android.app.DatePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naaammme.bbspace.core.common.media.thumbnailUrl
import com.naaammme.bbspace.core.designsystem.component.VideoListCardSkeleton
import com.naaammme.bbspace.core.designsystem.theme.LocalAnimations
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.naaammme.bbspace.core.model.SearchFeedbackSec
import com.naaammme.bbspace.core.model.SearchFilter
import com.naaammme.bbspace.core.model.SearchOp
import com.naaammme.bbspace.core.model.SearchTime
import com.naaammme.bbspace.core.model.SearchVideo
import com.naaammme.bbspace.core.model.VideoRoute
import java.util.Calendar


@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenVideo: (VideoRoute) -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val videos by viewModel.videos.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var sheetKey by remember { mutableStateOf<String?>(null) }
    val sheetFilter = viewModel.filters.firstOrNull { it.key == sheetKey }
    val shouldLoadMore by remember(
        listState,
        videos,
        viewModel.canLoadMore,
        viewModel.isLoading,
        viewModel.isLoadingMore
    ) {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            viewModel.canLoadMore &&
                    !viewModel.isLoading &&
                    !viewModel.isLoadingMore &&
                    videos.isNotEmpty() &&
                    last >= videos.lastIndex - 2
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            viewModel.loadMore()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            SearchTopBar(
                text = viewModel.input,
                autoFocus = viewModel.keyword.isBlank() && viewModel.input.isBlank(),
                onTextChange = viewModel::updateInput,
                onBack = onBack,
                onSearch = viewModel::submitSearch
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (viewModel.filters.isNotEmpty()) {
                SearchFilterRow(
                    filters = viewModel.filters,
                    time = viewModel.time,
                    hasActive = viewModel.hasActiveFilter,
                    selectedOf = viewModel::selectedOf,
                    onOpen = { sheetKey = it },
                    onClear = viewModel::clearFilters
                )
            }

            when {
                viewModel.isLoading && videos.isEmpty() -> SearchLoadingList()

                viewModel.keyword.isBlank() && videos.isEmpty() -> {
                    SearchHint(text = "输入关键词开始搜索")
                }

                viewModel.errorMessage != null && videos.isEmpty() -> {
                    SearchError(
                        message = viewModel.errorMessage.orEmpty(),
                        onRetry = viewModel::submitSearch
                    )
                }

                videos.isEmpty() -> SearchHint(text = "没有找到视频结果")

                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            items = videos,
                            key = { "${it.aid}_${it.cid}" },
                            contentType = { "video" }
                        ) { video ->
                            SearchCard(
                                video = video,
                                onClick = { onOpenVideo(video.route) }
                            )
                        }

                        if (viewModel.isLoadingMore) {
                            items(
                                count = LOAD_MORE_SKELETON_COUNT,
                                key = { index -> "loading_$index" },
                                contentType = { "skeleton" }
                            ) {
                                VideoListCardSkeleton()
                            }
                        }

                        if (viewModel.errorMessage != null && videos.isNotEmpty()) {
                            item {
                                SearchError(
                                    message = viewModel.errorMessage.orEmpty(),
                                    onRetry = viewModel::loadMore
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    sheetFilter?.let { filter ->
        SearchFilterSheet(
            filter = filter,
            selected = viewModel.selectedOf(filter.key),
            time = viewModel.time,
            onDismiss = { sheetKey = null },
            onApply = { picked, pickedTime ->
                viewModel.applyFilter(filter.key, picked, pickedTime)
                sheetKey = null
            }
        )
    }
}

@Composable
private fun SearchLoadingList(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(
            count = INIT_SKELETON_COUNT,
            key = { index -> "skeleton_$index" },
            contentType = { "skeleton" }
        ) {
            VideoListCardSkeleton()
        }
    }
}

@Composable
private fun SearchHint(text: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SearchError(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = message.ifBlank { "搜索失败" },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error
            )
            TextButton(onClick = onRetry) {
                Text("重试")
            }
        }
    }
}

@Composable
private fun SearchFilterRow(
    filters: List<SearchFilter>,
    time: SearchTime,
    hasActive: Boolean,
    selectedOf: (String) -> Set<String>,
    onOpen: (String) -> Unit,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filters, key = { it.key }) { filter ->
                val selected = selectedOf(filter.key)
                FilterChip(
                    selected = selected.isNotEmpty(),
                    onClick = { onOpen(filter.key) },
                    label = { Text(filterLabel(filter, selected, time)) }
                )
            }
        }

        if (hasActive) {
            TextButton(onClick = onClear) {
                Text("清空")
            }
        }
    }
}

private data class FilterDraft(
    val selected: Set<String>,
    val time: SearchTime
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SearchFilterSheet(
    filter: SearchFilter,
    selected: Set<String>,
    time: SearchTime,
    onDismiss: () -> Unit,
    onApply: (Set<String>, SearchTime) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var draft by remember(filter.key, selected, time) {
        mutableStateOf(
            FilterDraft(
                selected = selected,
                time = if (selected.singleOrNull() == CUSTOM_TIME) time else SearchTime()
            )
        )
    }
    val showCustomTime = filter.key == SINCE_KEY && draft.selected.singleOrNull() == CUSTOM_TIME
    val canApply = !showCustomTime || draft.time.isActive

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            Text(
                text = filter.title,
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = if (filter.single) "单选" else "多选",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filter.ops.forEach { op ->
                    FilterChip(
                        selected = isPicked(op, draft.selected),
                        onClick = {
                            val nextSel = togglePick(filter, op, draft.selected)
                            val nextTime = if (
                                filter.key == SINCE_KEY &&
                                nextSel.singleOrNull() != CUSTOM_TIME
                            ) {
                                SearchTime()
                            } else {
                                draft.time
                            }
                            draft = FilterDraft(nextSel, nextTime)
                        },
                        label = { Text(op.label) }
                    )
                }
            }

            if (showCustomTime) {
                CustomTimePanel(
                    time = draft.time,
                    onChange = { nextTime ->
                        draft = draft.copy(time = nextTime)
                    }
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        draft = FilterDraft(emptySet(), SearchTime())
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("重置")
                }
                TextButton(
                    onClick = { onApply(draft.selected, draft.time) },
                    enabled = canApply,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("确定")
                }
            }
        }
    }
}

@Composable
private fun CustomTimePanel(
    time: SearchTime,
    onChange: (SearchTime) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "自定义时间",
            style = MaterialTheme.typography.titleSmall
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DateBtn(
                label = "开始日期",
                timeS = time.beginS,
                end = false,
                modifier = Modifier.weight(1f)
            ) { picked ->
                val end = when {
                    time.endS == 0L -> 0L
                    time.endS < picked -> endOfDay(picked)
                    else -> time.endS
                }
                onChange(SearchTime(beginS = picked, endS = end))
            }
            DateBtn(
                label = "结束日期",
                timeS = time.endS,
                end = true,
                modifier = Modifier.weight(1f)
            ) { picked ->
                val begin = when {
                    time.beginS == 0L -> 0L
                    time.beginS > picked -> startOfDay(picked)
                    else -> time.beginS
                }
                onChange(SearchTime(beginS = begin, endS = picked))
            }
        }

        Text(
            text = if (time.isActive) timeText(time) else "请选择开始和结束日期",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DateBtn(
    label: String,
    timeS: Long,
    end: Boolean,
    modifier: Modifier = Modifier,
    onPicked: (Long) -> Unit
) {
    val context = LocalContext.current
    OutlinedButton(
        onClick = {
            val cal = Calendar.getInstance().apply {
                if (timeS > 0L) {
                    timeInMillis = timeS * 1000
                }
            }
            DatePickerDialog(
                context,
                { _, year, month, day ->
                    val picked = Calendar.getInstance().apply {
                        set(Calendar.YEAR, year)
                        set(Calendar.MONTH, month)
                        set(Calendar.DAY_OF_MONTH, day)
                        set(Calendar.HOUR_OF_DAY, if (end) 23 else 0)
                        set(Calendar.MINUTE, if (end) 59 else 0)
                        set(Calendar.SECOND, if (end) 59 else 0)
                        set(Calendar.MILLISECOND, if (end) 999 else 0)
                    }
                    onPicked(picked.timeInMillis / 1000)
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        },
        modifier = modifier
    ) {
        Text(
            text = if (timeS > 0L) formatDay(timeS) else label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchCard(
    video: SearchVideo,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val imageRequest = remember(video.cover) {
        ImageRequest.Builder(context)
            .data(thumbnailUrl(video.cover))
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
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
            Box(
                modifier = Modifier
                    .weight(0.38f)
                    .aspectRatio(16f / 10f)
                    .clip(MaterialTheme.shapes.medium)
            ) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = video.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                Text(
                    text = video.viewText,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.56f), MaterialTheme.shapes.extraSmall)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )

                Text(
                    text = video.duration,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.56f), MaterialTheme.shapes.extraSmall)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
            }

            Column(
                modifier = Modifier.weight(0.62f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

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
                        text = "${video.viewText} 播放 · ${video.danmakuText} 弹幕",
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

    IconButton(onClick = { show = true }) {
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

private fun filterLabel(
    filter: SearchFilter,
    selected: Set<String>,
    time: SearchTime
): String {
    if (selected.isEmpty()) return filter.title
    val labels = filter.ops
        .filter { it.param in selected }
        .map {
            if (filter.key == SINCE_KEY && it.param == CUSTOM_TIME && time.isActive) {
                timeText(time)
            } else {
                it.label
            }
        }
    if (labels.isEmpty()) return filter.title
    val summary = if (labels.size == 1) labels.first() else "${labels.first()} +${labels.size - 1}"
    return "${filter.title}: $summary"
}

private fun isPicked(op: SearchOp, picked: Set<String>): Boolean {
    return if (op.isDefault) picked.isEmpty() else op.param in picked
}

private fun togglePick(
    filter: SearchFilter,
    op: SearchOp,
    picked: Set<String>
): Set<String> {
    if (op.isDefault) return emptySet()
    if (filter.single) {
        return if (op.param in picked) emptySet() else setOf(op.param)
    }
    return if (op.param in picked) picked - op.param else picked + op.param
}

private fun timeText(time: SearchTime): String {
    return "${formatDay(time.beginS)} 至 ${formatDay(time.endS)}"
}

private fun formatDay(timeS: Long): String {
    return DateFormat.format("yyyy-MM-dd", timeS * 1000).toString()
}

private fun startOfDay(timeS: Long): Long {
    return Calendar.getInstance().apply {
        timeInMillis = timeS * 1000
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis / 1000
}

private fun endOfDay(timeS: Long): Long {
    return Calendar.getInstance().apply {
        timeInMillis = timeS * 1000
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }.timeInMillis / 1000
}

private const val INIT_SKELETON_COUNT = 8
private const val LOAD_MORE_SKELETON_COUNT = 2
private const val SINCE_KEY = "since"
private const val CUSTOM_TIME = "custom"
