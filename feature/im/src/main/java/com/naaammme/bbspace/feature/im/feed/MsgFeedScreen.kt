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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naaammme.bbspace.core.model.MsgFeedFilter
import com.naaammme.bbspace.core.designsystem.component.FilledTabRow
import com.naaammme.bbspace.core.model.PublishedRecord
import com.naaammme.bbspace.core.model.LiveRoute
import com.naaammme.bbspace.core.model.SpaceRoute
import com.naaammme.bbspace.core.model.VideoTarget
import com.naaammme.bbspace.feature.comment.CommentPanel

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
    val state by vm.uiState.collectAsStateWithLifecycle()
    var detailRecord by remember { mutableStateOf<PublishedRecord?>(null) }

    BackHandler(enabled = detailRecord != null) {
        detailRecord = null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("通知评论") },
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
                    tabs = listOf("全部", "关注的人"),
                    selectedIndex = state.filterType.index,
                    onSelect = { index ->
                        when (index) {
                            0 -> vm.changeFilter(MsgFeedFilter.ALL)
                            1 -> vm.changeFilter(MsgFeedFilter.FOLLOWING)
                        }
                    },
                    modifier = Modifier
                )
                MsgFeedPane(
                    onOpenCommentDetail = { record ->
                        detailRecord = record
                    },
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
