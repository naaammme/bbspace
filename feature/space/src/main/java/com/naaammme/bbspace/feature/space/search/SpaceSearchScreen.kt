package com.naaammme.bbspace.feature.space.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.naaammme.bbspace.core.designsystem.component.SearchCapsuleField
import com.naaammme.bbspace.core.designsystem.component.StateMessageCard
import com.naaammme.bbspace.feature.space.archive.spaceVideoItems
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpaceSearchScreen(
    onBack: () -> Unit,
    viewModel: SpaceSearchViewModel = hiltViewModel()
) {
    val listState = rememberLazyListState()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val submit = remember(keyboard, viewModel) {
        {
            keyboard?.hide()
            viewModel.submit()
        }
    }
    val keyboardOptions = remember { KeyboardOptions(imeAction = ImeAction.Search) }
    val keyboardActions = remember(submit) { KeyboardActions(onSearch = { submit() }) }

    LaunchedEffect(Unit) {
        if (viewModel.query.isBlank()) {
            withFrameNanos { }
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            val total = listState.layoutInfo.totalItemsCount
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            total to last
        }
            .distinctUntilChanged()
            .filter { (total, last) -> total > 0 && last >= total - 3 }
            .collect { viewModel.loadMore() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    SearchCapsuleField(
                        value = viewModel.input,
                        onValueChange = viewModel::updateInput,
                        placeholder = "搜索 UP 主视频",
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        keyboardOptions = keyboardOptions,
                        keyboardActions = keyboardActions
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        keyboard?.hide()
                        onBack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = submit) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "搜索"
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            spaceVideoItems(
                videos = viewModel.videos
            )
            if (viewModel.isLoading) {
                item(key = "loading", contentType = "loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (viewModel.error != null) {
                item(key = "error", contentType = "state") {
                    StateMessageCard(
                        text = viewModel.error.orEmpty(),
                        isError = true,
                        actionText = "重试",
                        onAction = viewModel::retry
                    )
                }
            } else if (viewModel.query.isNotBlank() && viewModel.videos.isEmpty()) {
                item(key = "empty", contentType = "state") {
                    StateMessageCard(text = "没有找到相关视频")
                }
            }
        }
    }
}
