package com.naaammme.bbspace.feature.home.interest

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naaammme.bbspace.core.designsystem.component.BiliPullToRefreshBox
import com.naaammme.bbspace.core.designsystem.component.CollapsingTopBarScaffold
import com.naaammme.bbspace.core.model.DistributionAreaItem
import com.naaammme.bbspace.core.model.InterestAreaLabels
import com.naaammme.bbspace.core.model.InterestDistributionMaterial
import com.naaammme.bbspace.core.model.InterestLabel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun InterestScreen(
    onBack: () -> Unit,
    viewModel: InterestViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    CollapsingTopBarScaffold(
        topBar = { scrollBehavior ->
            TopAppBar(
                title = {
                    Text(
                        text = state.response?.pageMaterial?.title
                            ?.takeIf { it.isNotBlank() }
                            ?: "内容偏好调节"
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        BiliPullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                state.errorMessage != null && state.response == null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = state.errorMessage.orEmpty()
                                    .ifBlank { "加载兴趣标签失败" },
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = viewModel::refresh) {
                                Text("重试")
                            }
                        }
                    }
                }

                else -> {
                    val response = state.response ?: return@BiliPullToRefreshBox
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val distMaterial = response.distributionMaterial
                        if (distMaterial != null && distMaterial.areaList.isNotEmpty()) {
                            item(key = "distribution") {
                                DistributionSection(material = distMaterial)
                            }
                        }

                        item(key = "my_interest") {
                            SectionHeader(
                                title = response.pageMaterial?.myInterestTitle
                                    ?.takeIf { it.isNotBlank() }
                                    ?: "我的内容偏好",
                                subtitle = response.pageMaterial?.subtitle
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                response.labels.forEach { label ->
                                    InterestLabelChip(label = label)
                                }
                            }
                        }

                        if (response.allLabels.isNotEmpty()) {
                            item(key = "all_labels") {
                                SectionHeader(title = "全部偏好", subtitle = null)
                            }
                            items(
                                items = response.allLabels,
                                key = { "area_${it.areaName}" }
                            ) { area ->
                                InterestAreaCard(area = area)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String?) {
    Column {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun InterestLabelChip(label: InterestLabel) {
    Text(
        text = label.name,
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.secondaryContainer,
                MaterialTheme.shapes.small
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        style = MaterialTheme.typography.labelLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InterestAreaCard(area: InterestAreaLabels) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceContainerLow,
                MaterialTheme.shapes.medium
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = area.areaName, style = MaterialTheme.typography.titleSmall)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            area.areaLabel.forEach { label ->
                Text(
                    text = label,
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.secondaryContainer,
                            MaterialTheme.shapes.extraSmall
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
private fun DistributionSection(material: InterestDistributionMaterial) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceContainerLow,
                MaterialTheme.shapes.medium
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = material.title.takeIf { it.isNotBlank() } ?: "近期偏好分布",
            style = MaterialTheme.typography.titleMedium
        )
        if (material.subtitle.isNotBlank()) {
            Text(
                text = material.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        val maxCount = material.areaList.maxOfOrNull { it.count } ?: 1
        material.areaList.forEach { item ->
            DistributionBar(item = item, maxCount = maxCount)
        }
    }
}

@Composable
private fun DistributionBar(item: DistributionAreaItem, maxCount: Int) {
    val fraction = item.count.toFloat() / maxCount.coerceAtLeast(1)
    val barColor = if (item.color.isNotBlank()) {
        Color(AndroidColor.parseColor(item.color))
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = item.name,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 2.dp)
        )
        Box(modifier = Modifier.weight(1f)) {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(16.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(barColor)
            )
        }
        Text(
            text = item.count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}
