package com.naaammme.bbspace.feature.bbspace

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naaammme.bbspace.core.designsystem.component.CollapsingTopBarScaffold
import com.naaammme.bbspace.feature.bbspace.playback.PlaybackHistoryPane

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BbSpaceScreen(
    onBack: () -> Unit,
    vm: BbSpaceViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf(BbSpacePage.HOME) }
    val handleBack = {
        if (page == BbSpacePage.HOME) {
            onBack()
        } else {
            page = BbSpacePage.HOME
        }
    }

    BackHandler(enabled = page != BbSpacePage.HOME) {
        page = BbSpacePage.HOME
    }

    CollapsingTopBarScaffold(
        topBar = { scrollBehavior ->
            TopAppBar(
                title = {
                    Text(
                        text = when (page) {
                            BbSpacePage.HOME -> "bb空间"
                            BbSpacePage.PLAYBACK_HISTORY -> "播放历史"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = handleBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        when (page) {
            BbSpacePage.HOME -> {
                BbSpaceHomePane(
                    playbackCount = state.playbackCount,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    onOpenPlaybackHistory = { page = BbSpacePage.PLAYBACK_HISTORY }
                )
            }
            BbSpacePage.PLAYBACK_HISTORY -> {
                PlaybackHistoryPane(
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
    playbackCount: Int,
    modifier: Modifier = Modifier,
    onOpenPlaybackHistory: () -> Unit
) {
    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        BbSpaceEntryCard(
            title = "播放历史",
            subtitle = "当前有 $playbackCount 条本地记录",
            icon = Icons.Default.DateRange,
            modifier = Modifier.fillMaxWidth(),
            onClick = onOpenPlaybackHistory
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
    PLAYBACK_HISTORY
}
