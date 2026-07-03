package com.naaammme.bbspace.feature.bbspace

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naaammme.bbspace.core.designsystem.component.CollapsingTopBarScaffold
import com.naaammme.bbspace.core.model.CommentSubjectTool
import com.naaammme.bbspace.core.model.PUBLISHED_RECORD_KIND_COMMENT
import com.naaammme.bbspace.core.model.PUBLISHED_RECORD_KIND_LIVE_DANMAKU
import com.naaammme.bbspace.core.model.PUBLISHED_RECORD_KIND_VIDEO_DANMAKU
import com.naaammme.bbspace.core.model.LiveRoute
import com.naaammme.bbspace.core.model.PublishedRecord
import com.naaammme.bbspace.core.model.SpaceRoute
import com.naaammme.bbspace.core.model.VideoTarget
import com.naaammme.bbspace.core.model.VideoTargetTool
import com.naaammme.bbspace.feature.bbspace.commentsearch.CommentSearchPane
import com.naaammme.bbspace.feature.bbspace.playback.PlaybackHistoryPane
import com.naaammme.bbspace.feature.bbspace.playback.PlaybackHistoryViewModel
import com.naaammme.bbspace.feature.bbspace.publishedrecord.PublishedRecordPane
import com.naaammme.bbspace.feature.bbspace.publishedrecord.PublishedRecordViewModel
import com.naaammme.bbspace.feature.bbspace.relation.RelationCheckPane
import com.naaammme.bbspace.feature.comment.CommentPanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BbSpaceScreen(
    onBack: () -> Unit,
    onOpenSpace: (SpaceRoute) -> Unit = {},
    onOpenVideoDetail: (VideoTarget) -> Unit = {},
    onOpenDynamicDetail: (String) -> Unit = {},
    onOpenLiveDetail: (LiveRoute) -> Unit = {}
) {
    val playbackHistoryVm: PlaybackHistoryViewModel = hiltViewModel()
    val playbackHistoryState by playbackHistoryVm.uiState.collectAsStateWithLifecycle()
    val publishedRecordVm: PublishedRecordViewModel = hiltViewModel()
    val publishedRecordState by publishedRecordVm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exportJson = rememberExportJson()
    var page by rememberSaveable { mutableStateOf(BbSpacePage.HOME) }
    var selectedCommentRecord by remember { mutableStateOf<PublishedRecord?>(null) }
    val openPublishedRecordTarget: (PublishedRecord) -> Unit = { record ->
        record.targetId.takeIf { it > 0L }?.let { targetId ->
            when (record.kind) {
                PUBLISHED_RECORD_KIND_COMMENT -> {
                    if (record.targetType == CommentSubjectTool.TYPE_VIDEO) {
                        onOpenVideoDetail(
                            VideoTarget.Ugc(
                                aid = targetId,
                                cid = 0L,
                                src = VideoTargetTool.history()
                            )
                        )
                    } else {
                        onOpenDynamicDetail(targetId.toString())
                    }
                }
                PUBLISHED_RECORD_KIND_VIDEO_DANMAKU -> {
                    onOpenVideoDetail(
                        VideoTarget.Ugc(
                            aid = targetId,
                            cid = 0L,
                            src = VideoTargetTool.history()
                        )
                    )
                }
                PUBLISHED_RECORD_KIND_LIVE_DANMAKU -> {
                    onOpenLiveDetail(LiveRoute(roomId = targetId))
                }
            }
        }
    }
    val handleBack = {
        when (page) {
            BbSpacePage.HOME -> onBack()
            BbSpacePage.COMMENT_DETAIL -> {
                page = BbSpacePage.PUBLISHED_RECORD
                selectedCommentRecord = null
            }
            else -> {
                page = BbSpacePage.HOME
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val json = context.contentResolver.openInputStream(uri)?.use { input ->
                            String(input.readBytes(), Charsets.UTF_8)
                        } ?: error("导入文件读取失败")
                        publishedRecordVm.importJson(json)
                    }
                }
                result.onSuccess { count ->
                    Toast.makeText(context, "已导入 $count 条记录", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(context, "导入失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    BackHandler(enabled = page != BbSpacePage.HOME) {
        handleBack()
    }

    CollapsingTopBarScaffold(
        topBar = { scrollBehavior ->
            if (page != BbSpacePage.COMMENT_DETAIL) {
                TopAppBar(
                    title = {
                        Text(
                            text = when (page) {
                                BbSpacePage.HOME -> "bb空间"
                                BbSpacePage.PLAYBACK_HISTORY -> "播放历史(${playbackHistoryState.items.size})"
                                BbSpacePage.PUBLISHED_RECORD -> "我发布的(${publishedRecordState.totalCount})"
                                BbSpacePage.COMMENT_DETAIL -> "评论详情"
                                BbSpacePage.RELATION_CHECK -> "查询关系"
                                BbSpacePage.COMMENT_SEARCH -> "查评论"
                            }
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = handleBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        if (page == BbSpacePage.PUBLISHED_RECORD) {
                            TextButton(
                                onClick = {
                                    importLauncher.launch(arrayOf("application/json", "text/*"))
                                }
                            ) {
                                Text("导入")
                            }
                            TextButton(
                                onClick = {
                                    if (publishedRecordState.totalCount == 0) {
                                        Toast.makeText(context, "暂无发布记录", Toast.LENGTH_SHORT).show()
                                    } else {
                                        scope.launch {
                                            exportJson(
                                                "bbspace_published_records.json",
                                                publishedRecordVm.exportJson()
                                            )
                                        }
                                    }
                                },
                                enabled = !publishedRecordState.isLoading
                            ) {
                                Text("导出")
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior
                )
            }
        }
    ) { padding ->
        when (page) {
            BbSpacePage.HOME -> {
                BbSpaceHomePane(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    onOpenPlaybackHistory = { page = BbSpacePage.PLAYBACK_HISTORY },
                    onOpenPublishedRecord = { page = BbSpacePage.PUBLISHED_RECORD },
                    onOpenRelationCheck = { page = BbSpacePage.RELATION_CHECK },
                    onOpenCommentSearch = { page = BbSpacePage.COMMENT_SEARCH }
                )
            }
            BbSpacePage.PLAYBACK_HISTORY -> {
                PlaybackHistoryPane(
                    vm = playbackHistoryVm,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp)
                )
            }
            BbSpacePage.PUBLISHED_RECORD -> {
                PublishedRecordPane(
                    vm = publishedRecordVm,
                    onOpenCommentDetail = { record ->
                        selectedCommentRecord = record
                        page = BbSpacePage.COMMENT_DETAIL
                    },
                    onOpenTarget = openPublishedRecordTarget,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp)
                )
            }
            BbSpacePage.COMMENT_DETAIL -> {
                CommentPanel(
                    subject = null,
                    detailRecord = selectedCommentRecord,
                    onOpenSpace = onOpenSpace,
                    onDismissDetail = handleBack,
                )
            }
            BbSpacePage.RELATION_CHECK -> {
                RelationCheckPane(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp)
                )
            }
            BbSpacePage.COMMENT_SEARCH -> {
                CommentSearchPane(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun BbSpaceHomePane(
    modifier: Modifier = Modifier,
    onOpenPlaybackHistory: () -> Unit,
    onOpenPublishedRecord: () -> Unit,
    onOpenRelationCheck: () -> Unit,
    onOpenCommentSearch: () -> Unit
) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        BbSpaceEntryCard(
            title = "播放历史",
            subtitle = "查看本地播放记录",
            icon = Icons.Default.DateRange,
            modifier = Modifier.fillMaxWidth(),
            onClick = onOpenPlaybackHistory
        )
        BbSpaceEntryCard(
            title = "我发布的",
            subtitle = "查看本地发布记录",
            icon = Icons.Default.DateRange,
            modifier = Modifier.fillMaxWidth(),
            onClick = onOpenPublishedRecord
        )
        BbSpaceEntryCard(
            title = "查询关系",
            subtitle = "输入两个 UID 查询拉黑和关注关系",
            icon = Icons.Default.Person,
            modifier = Modifier.fillMaxWidth(),
            onClick = onOpenRelationCheck
        )
        BbSpaceEntryCard(
            title = "查评论",
            subtitle = "输入 UID 查询历史评论",
            icon = Icons.Default.DateRange,
            modifier = Modifier.fillMaxWidth(),
            onClick = onOpenCommentSearch
        )
    }
}

@Composable
private fun BbSpaceEntryCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier,
        onClick = onClick,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "进入",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private enum class BbSpacePage {
    HOME,
    PLAYBACK_HISTORY,
    PUBLISHED_RECORD,
    COMMENT_DETAIL,
    RELATION_CHECK,
    COMMENT_SEARCH
}
