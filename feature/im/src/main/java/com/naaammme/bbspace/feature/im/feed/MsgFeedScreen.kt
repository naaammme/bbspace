package com.naaammme.bbspace.feature.im.feed

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naaammme.bbspace.core.model.MsgFeedFilter
import com.naaammme.bbspace.core.model.MsgFeedTab
import com.naaammme.bbspace.core.designsystem.component.FilledTabRow
import com.naaammme.bbspace.core.model.PublishedRecord
import com.naaammme.bbspace.core.model.LiveRoute
import com.naaammme.bbspace.core.model.SpaceRoute
import com.naaammme.bbspace.core.model.VideoTarget
import com.naaammme.bbspace.feature.comment.CommentPanel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val FEED_TABS = listOf("收到回复", "收到赞", "关注的人")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MsgFeedScreen(
    onBack: () -> Unit,
    onOpenSpace: (SpaceRoute) -> Unit = {},
    onOpenVideoDetail: (VideoTarget) -> Unit = {},
    onOpenDynamicDetail: (String) -> Unit = {},
    onOpenLiveDetail: (LiveRoute) -> Unit = {},
    vm: MsgFeedViewModel = hiltViewModel()
) {
    val selectedTabIndex by remember(vm) {
        vm.uiState
            .map {
                when {
                    it.tab == MsgFeedTab.LIKES -> 1
                    it.filterType == MsgFeedFilter.FOLLOWING -> 2
                    else -> 0
                }
            }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = 0)
    var detailRecord by remember { mutableStateOf<PublishedRecord?>(null) }

    BackHandler(enabled = detailRecord != null) {
        detailRecord = null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("通知消息") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                FilledTabRow(
                    tabs = FEED_TABS,
                    selectedIndex = selectedTabIndex,
                    onSelect = vm::selectTab
                )
                val openCommentDetail = remember {
                    { record: PublishedRecord -> detailRecord = record }
                }
                MsgFeedPane(
                    onOpenCommentDetail = openCommentDetail,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        AnimatedVisibility(
            visible = detailRecord != null,
            enter = slideInHorizontally { it },
            exit = slideOutHorizontally { it }
        ) {
            CommentPanel(
                subject = null,
                detailRecord = detailRecord,
                onDismissDetail = { detailRecord = null },
                onOpenSpace = onOpenSpace,
                onOpenVideoDetail = onOpenVideoDetail,
                onOpenDynamicDetail = onOpenDynamicDetail,
                onOpenLiveDetail = onOpenLiveDetail,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
