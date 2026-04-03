package com.naaammme.bbspace.feature.video

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowWidthSizeClass
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.naaammme.bbspace.core.model.QualityOption
import com.naaammme.bbspace.core.model.VideoDetail
import com.naaammme.bbspace.core.model.VideoJump
import com.naaammme.bbspace.core.model.VideoOwner
import com.naaammme.bbspace.core.model.VideoPagePart
import com.naaammme.bbspace.core.model.VideoRelate
import com.naaammme.bbspace.core.model.VideoSeason
import com.naaammme.bbspace.core.model.VideoSeasonEpisode
import com.naaammme.bbspace.core.model.VideoStat
import com.naaammme.bbspace.feature.video.model.VideoPageState

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun VideoDetailPage(
    pageState: VideoPageState,
    playerPane: @Composable (Modifier) -> Unit,
    onOpenVideo: (VideoJump) -> Unit,
    onOpenEpisode: (Long, Long) -> Unit,
    onSwitchPage: (Long) -> Unit
) {
    val detail = pageState.detail
    var descOn by rememberSaveable(detail?.aid) { mutableStateOf(false) }
    var tagOn by rememberSaveable(detail?.aid) { mutableStateOf(false) }
    var sheetTp by rememberSaveable(detail?.aid) { mutableStateOf<String?>(null) }
    val widthClass = currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass

    if (widthClass == WindowWidthSizeClass.EXPANDED) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .statusBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .weight(1.08f)
                    .fillMaxHeight()
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(Color.Black)
            ) {
                playerPane(Modifier.fillMaxSize())
            }

            LazyColumn(
                modifier = Modifier
                    .weight(0.92f)
                    .fillMaxHeight(),
                contentPadding = PaddingValues(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                detailItems(
                    pageState = pageState,
                    itemMod = Modifier,
                    descOn = descOn,
                    tagOn = tagOn,
                    onToggleDesc = { descOn = !descOn },
                    onToggleTag = { tagOn = !tagOn },
                    onSeasonClick = { sheetTp = SHEET_SEASON },
                    onPageClick = { sheetTp = SHEET_PAGE },
                    onOpenVideo = onOpenVideo
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
            contentPadding = PaddingValues(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(
                key = "player",
                contentType = "player"
            ) {
                playerPane(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .aspectRatio(16f / 9f)
                        .background(Color.Black)
                )
            }

            detailItems(
                pageState = pageState,
                itemMod = Modifier.padding(horizontal = 16.dp),
                descOn = descOn,
                tagOn = tagOn,
                onToggleDesc = { descOn = !descOn },
                onToggleTag = { tagOn = !tagOn },
                onSeasonClick = { sheetTp = SHEET_SEASON },
                onPageClick = { sheetTp = SHEET_PAGE },
                onOpenVideo = onOpenVideo
            )
        }
    }

    if (sheetTp == SHEET_SEASON && detail?.season != null) {
        SeasonSheet(
            season = detail.season!!,
            curCid = pageState.curCid,
            onDismiss = { sheetTp = null },
            onOpenEpisode = { aid, cid ->
                sheetTp = null
                onOpenEpisode(aid, cid)
            }
        )
    }

    if (sheetTp == SHEET_PAGE && detail != null && detail.pages.size > 1) {
        PageSheet(
            pages = detail.pages,
            curCid = pageState.curCid,
            onDismiss = { sheetTp = null },
            onSwitchPage = { cid ->
                sheetTp = null
                onSwitchPage(cid)
            }
        )
    }
}

private fun LazyListScope.detailItems(
    pageState: VideoPageState,
    itemMod: Modifier,
    descOn: Boolean,
    tagOn: Boolean,
    onToggleDesc: () -> Unit,
    onToggleTag: () -> Unit,
    onSeasonClick: () -> Unit,
    onPageClick: () -> Unit,
    onOpenVideo: (VideoJump) -> Unit
) {
    when {
        pageState.detailLoading -> {
            item(
                key = "detail_loading",
                contentType = "state"
            ) {
                StateCard(
                    text = "正在加载简介",
                    modifier = itemMod
                )
            }
        }

        !pageState.detailError.isNullOrBlank() -> {
            item(
                key = "detail_error",
                contentType = "state"
            ) {
                StateCard(
                    text = pageState.detailError.orEmpty(),
                    modifier = itemMod,
                    isError = true
                )
            }
        }

        pageState.detail != null -> {
            val detail = pageState.detail!!
            item(
                key = "summary",
                contentType = "summary"
            ) {
                VideoSummarySection(
                    detail = detail,
                    descOn = descOn,
                    tagOn = tagOn,
                    onToggleDesc = onToggleDesc,
                    onToggleTag = onToggleTag,
                    modifier = itemMod
                )
            }

            detail.season?.let { season ->
                item(
                    key = "season_entry",
                    contentType = "season_entry"
                ) {
                    SeasonEntryCard(
                        season = season,
                        curCid = pageState.curCid,
                        onClick = onSeasonClick,
                        modifier = itemMod
                    )
                }
            }

            if (detail.pages.size > 1) {
                item(
                    key = "page_entry",
                    contentType = "page_entry"
                ) {
                    PageEntryCard(
                        pages = detail.pages,
                        curCid = pageState.curCid,
                        onClick = onPageClick,
                        modifier = itemMod
                    )
                }
            }

            if (detail.relates.isNotEmpty()) {
                item(
                    key = "relate_title",
                    contentType = "title"
                ) {
                    SectionTitle(
                        text = "相关推荐",
                        modifier = itemMod
                    )
                }

                items(
                    items = detail.relates,
                    key = { "${it.jump.aid}_${it.jump.cid}" },
                    contentType = { "relate" }
                ) { relate ->
                    RelateRow(
                        relate = relate,
                        onOpenVideo = onOpenVideo,
                        modifier = itemMod
                    )
                }
            }
        }

        else -> {
            item(
                key = "detail_empty",
                contentType = "state"
            ) {
                StateCard(
                    text = "暂无简介信息",
                    modifier = itemMod
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VideoSummarySection(
    detail: VideoDetail,
    descOn: Boolean,
    tagOn: Boolean,
    onToggleDesc: () -> Unit,
    onToggleTag: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        detail.owner?.let { owner ->
            OwnerCapsule(owner = owner)
        }
        InfoCapsule(
            detail = detail,
            descOn = descOn,
            tagOn = tagOn,
            onToggleDesc = onToggleDesc,
            onToggleTag = onToggleTag
        )
        detail.stat?.let { stat ->
            ActionCapsule(stat = stat)
        }
    }
}

@Composable
private fun OwnerCapsule(
    owner: VideoOwner,
    modifier: Modifier = Modifier
) {
    CapsuleCard(modifier = modifier) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            owner.face?.takeIf(String::isNotBlank)?.let { face ->
                val context = LocalContext.current
                val imgReq = remember(face) {
                    ImageRequest.Builder(context)
                        .data(face)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build()
                }
                AsyncImage(
                    model = imgReq,
                    contentDescription = owner.name,
                    modifier = Modifier
                        .width(72.dp)
                        .aspectRatio(1f)
                        .clip(MaterialTheme.shapes.large),
                    contentScale = ContentScale.Crop
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = owner.name,
                    style = MaterialTheme.typography.titleMedium
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    owner.fansText?.takeIf(String::isNotBlank)?.let { fans ->
                        SoftChip(fans)
                    }
                    owner.arcCountText?.takeIf(String::isNotBlank)?.let { arcCount ->
                        SoftChip(arcCount)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InfoCapsule(
    detail: VideoDetail,
    descOn: Boolean,
    tagOn: Boolean,
    onToggleDesc: () -> Unit,
    onToggleTag: () -> Unit,
    modifier: Modifier = Modifier
) {
    CapsuleCard(modifier = modifier) {
        Text(
            text = detail.title,
            style = MaterialTheme.typography.titleLarge
        )

        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SoftChip("AV${detail.aid}")
            if (detail.bvid.isNotBlank()) {
                SoftChip(detail.bvid)
            }
            detail.pubTs?.let { ts ->
                SoftChip(formatPubTime(ts))
            }
            detail.stat?.let { stat ->
                SoftChip("${stat.view} 播放")
                SoftChip("${stat.danmaku} 弹幕")
                SoftChip("${stat.reply} 评论")
            }
        }

        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (detail.desc.isNotBlank()) {
                ToggleChip(
                    text = if (descOn) "收起简介" else "展开简介",
                    onClick = onToggleDesc
                )
            }
            if (detail.tags.isNotEmpty()) {
                ToggleChip(
                    text = if (tagOn) "收起标签" else "展开标签",
                    onClick = onToggleTag
                )
            }
        }

        if (descOn && detail.desc.isNotBlank()) {
            Text(
                text = detail.desc,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        if (tagOn && detail.tags.isNotEmpty()) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                detail.tags.forEach { tag ->
                    SoftChip(tag)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionCapsule(
    stat: VideoStat,
    modifier: Modifier = Modifier
) {
    CapsuleCard(modifier = modifier) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ActionChip("点赞", stat.like)
            ActionChip("投币", stat.coin)
            ActionChip("收藏", stat.fav)
            ActionChip("分享", stat.share)
        }
    }
}

@Composable
private fun CapsuleCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun SoftChip(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun ToggleChip(
    text: String,
    onClick: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        // 这里就是使用了 Material 3 的第三色体系 (Tertiary) 
        // 算法会自动生成一个与主色不同但非常和谐的颜色
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun ActionChip(
    label: String,
    value: String
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp), 
            verticalAlignment = Alignment.CenterVertically 
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun SeasonEntryCard(
    season: VideoSeason,
    curCid: Long?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (title, subTitle, countText) = seasonEntryText(season, curCid)

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "合集列表",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            if (subTitle.isNotBlank()) {
                Text(
                    text = subTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = countText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun PageEntryCard(
    pages: List<VideoPagePart>,
    curCid: Long?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (title, subTitle, countText) = pageEntryText(pages, curCid)

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "分P列表",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            if (subTitle.isNotBlank()) {
                Text(
                    text = subTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = countText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SeasonSheet(
    season: VideoSeason,
    curCid: Long?,
    onDismiss: () -> Unit,
    onOpenEpisode: (Long, Long) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(
                key = "season_title",
                contentType = "title"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = season.title,
                        style = MaterialTheme.typography.titleLarge
                    )
                    season.subTitle?.takeIf(String::isNotBlank)?.let { subTitle ->
                        Text(
                            text = subTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            season.sections.forEachIndexed { secIdx, sec ->
                item(
                    key = "sec_$secIdx",
                    contentType = "section"
                ) {
                    if (secIdx > 0) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                    if (sec.title.isNotBlank()) {
                        Text(
                            text = sec.title,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                items(
                    items = sec.eps,
                    key = { "ep_${it.aid}_${it.cid}" },
                    contentType = { "episode" }
                ) { ep ->
                    SeasonEpisodeRow(
                        ep = ep,
                        selected = ep.cid == curCid,
                        onClick = {
                            if (ep.cid != curCid) {
                                onOpenEpisode(ep.aid, ep.cid)
                            }
                        }
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun PageSheet(
    pages: List<VideoPagePart>,
    curCid: Long?,
    onDismiss: () -> Unit,
    onSwitchPage: (Long) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(
                key = "page_title",
                contentType = "title"
            ) {
                Text(
                    text = "分P列表",
                    style = MaterialTheme.typography.titleLarge
                )
            }

            items(
                items = pages,
                key = { "page_${it.cid}" },
                contentType = { "page" }
            ) { page ->
                PageSheetRow(
                    title = pageTitle(pages, page),
                    subTitle = page.durationSec.takeIf { it > 0L }?.let {
                        formatDuration(it * 1000)
                    },
                    selected = page.cid == curCid,
                    onClick = {
                        if (page.cid != curCid) {
                            onSwitchPage(page.cid)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun SeasonEpisodeRow(
    ep: VideoSeasonEpisode,
    selected: Boolean,
    onClick: () -> Unit
) {
    val rowMod = if (selected) {
        Modifier.fillMaxWidth()
    } else {
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    }

    Row(
        modifier = rowMod.padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        ep.cover?.takeIf(String::isNotBlank)?.let { cover ->
            val context = LocalContext.current
            val imgReq = remember(cover) {
                ImageRequest.Builder(context)
                    .data(cover)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .build()
            }
            AsyncImage(
                model = imgReq,
                contentDescription = ep.title,
                modifier = Modifier
                    .width(112.dp)
                    .aspectRatio(16f / 10f)
                    .clip(MaterialTheme.shapes.large),
                contentScale = ContentScale.Crop
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = ep.title,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                if (selected) {
                    CurBadge("当前播放")
                }
            }
            ep.subTitle?.takeIf(String::isNotBlank)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun PageSheetRow(
    title: String,
    subTitle: String?,
    selected: Boolean,
    onClick: () -> Unit
) {
    val rowMod = if (selected) {
        Modifier.fillMaxWidth()
    } else {
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    }

    Column(
        modifier = rowMod.padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            if (selected) {
                CurBadge("当前播放")
            }
        }
        subTitle?.takeIf(String::isNotBlank)?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun RelateRow(
    relate: VideoRelate,
    onOpenVideo: (VideoJump) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageReq = remember(relate.cover) {
        ImageRequest.Builder(context)
            .data(relate.cover)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
    }
    val meta = listOfNotNull(
        relate.viewText?.let { "$it 播放" },
        relate.danmakuText?.let { "$it 弹幕" },
        relate.durationText
    ).joinToString(" · ")

    Card(
        onClick = { onOpenVideo(relate.jump) },
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            AsyncImage(
                model = imageReq,
                contentDescription = relate.title,
                modifier = Modifier
                    .weight(0.38f)
                    .aspectRatio(16f / 10f)
                    .clip(MaterialTheme.shapes.large),
                contentScale = ContentScale.Crop
            )

            Column(
                modifier = Modifier.weight(0.62f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = relate.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                relate.author?.let { author ->
                    Text(
                        text = author,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (meta.isNotBlank()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                relate.reason?.let { reason ->
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
private fun StateCard(
    text: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier
    )
}

@Composable
private fun CurBadge(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.primary,
        shape = MaterialTheme.shapes.extraSmall
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.36f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.64f)
        )
    }
}

@Composable
internal fun QualityOptionItem(
    option: QualityOption,
    isSelected: Boolean,
    onClick: (() -> Unit)? = null
) {
    val rowMod = if (onClick != null) {
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    } else {
        Modifier.fillMaxWidth()
    }
    Row(
        modifier = rowMod.padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = option.description,
                    style = MaterialTheme.typography.bodyLarge
                )
                if (option.needVip) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            text = "大会员",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            option.limit?.message?.let { msg ->
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "已选择",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun seasonEntryText(
    season: VideoSeason,
    curCid: Long?
): Triple<String, String, String> {
    val curEp = season.sections
        .asSequence()
        .flatMap { it.eps.asSequence() }
        .firstOrNull { it.cid == curCid }
    return Triple(
        curEp?.title ?: season.title,
        curEp?.subTitle.orEmpty().ifBlank { season.subTitle.orEmpty() },
        "${season.sections.sumOf { it.eps.size }} 个视频"
    )
}

private fun pageEntryText(
    pages: List<VideoPagePart>,
    curCid: Long?
): Triple<String, String, String> {
    val curPage = pages.firstOrNull { it.cid == curCid }
    return Triple(
        curPage?.let { pageTitle(pages, it) } ?: "查看分P列表",
        curPage?.durationSec?.takeIf { it > 0L }?.let { formatDuration(it * 1000) }.orEmpty(),
        "${pages.size} 个分 P"
    )
}

private fun pageTitle(
    pages: List<VideoPagePart>,
    page: VideoPagePart
): String {
    val idx = pages.indexOfFirst { it.cid == page.cid }
    return if (idx >= 0) {
        "P${idx + 1} ${page.part}"
    } else {
        page.part
    }
}

private fun formatPubTime(ts: Long): String {
    return DateFormat.format("yyyy-MM-dd HH:mm", ts * 1000).toString()
}

private const val SHEET_SEASON = "season"
private const val SHEET_PAGE = "page"