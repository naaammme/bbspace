package com.naaammme.bbspace.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.naaammme.bbspace.core.designsystem.theme.LocalPullRefreshThreshold

private val MinPullRefreshIndicatorThreshold = 40.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BiliPullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit
) {
    val state = rememberPullToRefreshState()
    val triggerThreshold = LocalPullRefreshThreshold.current
    val indicatorThreshold = triggerThreshold.coerceAtLeast(MinPullRefreshIndicatorThreshold)

    Box(
        modifier = modifier.pullToRefresh(
            state = state,
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            threshold = triggerThreshold
        ),
        contentAlignment = contentAlignment
    ) {
        content()
        // Keep the indicator fully visible when the trigger threshold is set very small.
        PullToRefreshDefaults.Indicator(
            modifier = Modifier.align(Alignment.TopCenter),
            state = state,
            isRefreshing = isRefreshing,
            threshold = indicatorThreshold
        )
    }
}
