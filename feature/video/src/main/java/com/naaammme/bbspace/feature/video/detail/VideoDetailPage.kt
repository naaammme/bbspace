package com.naaammme.bbspace.feature.video.detail

import android.text.format.DateFormat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.naaammme.bbspace.core.designsystem.component.BiliAsyncImage
import com.naaammme.bbspace.core.designsystem.component.VideoDetailInfoSkeleton
import com.naaammme.bbspace.core.designsystem.component.VideoRelateCardSkeleton
import com.naaammme.bbspace.core.model.CommentSubject
import com.naaammme.bbspace.core.model.QualityOption
import com.naaammme.bbspace.core.model.SpaceRoute
import com.naaammme.bbspace.core.model.SpaceRouteTool
import com.naaammme.bbspace.core.model.VideoDetail
import com.naaammme.bbspace.core.model.VideoOwner
import com.naaammme.bbspace.core.model.VideoPagePart
import com.naaammme.bbspace.core.model.VideoRelate
import com.naaammme.bbspace.core.model.VideoTarget
import com.naaammme.bbspace.core.model.VideoSeason
import com.naaammme.bbspace.core.model.VideoSeasonEpisode
import com.naaammme.bbspace.core.model.VideoStat
import com.naaammme.bbspace.feature.comment.CommentPanel
import com.naaammme.bbspace.feature.video.VideoPageState
import com.naaammme.bbspace.feature.video.formatDuration

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun VideoDetailPage(
    pageState: VideoPageState,
    commentSubject: CommentSubject?,
    isExpanded: Boolean,
    playerSpaceWidth: Dp,
    playerSpaceHeight: Dp,
    onOpenVideo: (VideoTarget) -> Unit,
    onOpenSpace: (SpaceRoute) -> Unit,
    onDownloadClick: () -> Unit,
    onOpenEpisode: (VideoTarget.Ugc) -> Unit,
    onSwitchPage: (Long) -> Unit
) {
    val detail = pageState.detail
    var descOn by rememberSaveable(detail?.aid) { mutableStateOf(false) }
    var tagOn by rememberSaveable(detail?.aid) { mutableStateOf(false) }
    var sheetTp by rememberSaveable(detail?.aid) { mutableStateOf<String?>(null) }

    if (isExpanded) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .width(playerSpaceWidth)
                    .height(playerSpaceHeight)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(Color.Black)
            )

            DetailPager(
                modifier = Modifier
                    .weight(1f),
                pageState = pageState,
                commentSubject = commentSubject,
                horizontalPad = 0.dp,
                infoTopPad = 0.dp,
                descOn = descOn,
                tagOn = tagOn,
                onToggleDesc = { descOn = !descOn },
                onToggleTag = { tagOn = !tagOn },
                onSeasonClick = { sheetTp = SHEET_SEASON },
                onPageClick = { sheetTp = SHEET_PAGE },
                onOpenVideo = onOpenVideo,
                onOpenSpace = onOpenSpace,
                onDownloadClick = onDownloadClick
            )
        }
    } else {
        DetailPager(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(top = playerSpaceHeight),
            pageState = pageState,
            commentSubject = commentSubject,
            horizontalPad = 16.dp,
            infoTopPad = 16.dp,
            descOn = descOn,
            tagOn = tagOn,
            onToggleDesc = { descOn = !descOn },
            onToggleTag = { tagOn = !tagOn },
            onSeasonClick = { sheetTp = SHEET_SEASON },
            onPageClick = { sheetTp = SHEET_PAGE },
            onOpenVideo = onOpenVideo,
            onOpenSpace = onOpenSpace,
            onDownloadClick = onDownloadClick
        )
    }

    detail?.season?.takeIf { sheetTp == SHEET_SEASON }?.let { season ->
        SeasonSheet(
            season = season,
            curCid = pageState.curCid,
            onDismiss = { sheetTp = null },
            onOpenEpisode = { route ->
                sheetTp = null
                onOpenEpisode(route)
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DetailPager(
    modifier: Modifier,
    pageState: VideoPageState,
    commentSubject: CommentSubject?,
    horizontalPad: Dp,
    infoTopPad: Dp,
    descOn: Boolean,
    tagOn: Boolean,
    onToggleDesc: () -> Unit,
    onToggleTag: () -> Unit,
    onSeasonClick: () -> Unit,
    onPageClick: () -> Unit,
    onOpenVideo: (VideoTarget) -> Unit,
    onOpenSpace: (SpaceRoute) -> Unit,
    onDownloadClick: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val itemMod = if (horizontalPad > 0.dp) {
        Modifier.padding(horizontal = horizontalPad)
    } else {
        Modifier
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize()
    ) { page ->
        when (page) {
            0 -> VideoInfoList(
                pageState = pageState,
                modifier = itemMod,
                topPad = infoTopPad,
                descOn = descOn,
                tagOn = tagOn,
                onToggleDesc = onToggleDesc,
                onToggleTag = onToggleTag,
                onSeasonClick = onSeasonClick,
                onPageClick = onPageClick,
                onOpenVideo = onOpenVideo,
                onOpenSpace = onOpenSpace,
                onDownloadClick = onDownloadClick
            )

            else -> CommentPanel(
                subject = commentSubject,
                onOpenSpace = onOpenSpace,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = horizontalPad,
                    top = 12.dp,
                    end = horizontalPad,
                    bottom = 20.dp
                )
            )
        }
    }
}

@Composable
private fun VideoInfoList(
    pageState: VideoPageState,
    modifier: Modifier,
    topPad: Dp,
    descOn: Boolean,
    tagOn: Boolean,
    onToggleDesc: () -> Unit,
    onToggleTag: () -> Unit,
    onSeasonClick: () -> Unit,
    onPageClick: () -> Unit,
    onOpenVideo: (VideoTarget) -> Unit,
    onOpenSpace: (SpaceRoute) -> Unit,
    onDownloadClick: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topPad, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        detailItems(
            pageState = pageState,
            itemMod = modifier,
            descOn = descOn,
            tagOn = tagOn,
            onToggleDesc = onToggleDesc,
            onToggleTag = onToggleTag,
            onSeasonClick = onSeasonClick,
            onPageClick = onPageClick,
            onOpenVideo = onOpenVideo,
            onOpenSpace = onOpenSpace,
            onDownloadClick = onDownloadClick
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
    onOpenVideo: (VideoTarget) -> Unit,
    onOpenSpace: (SpaceRoute) -> Unit,
    onDownloadClick: () -> Unit
) {
    when {
        pageState.detailLoading -> {
            item(
                key = "detail_loading_summary",
                contentType = "skeleton"
            ) {
                VideoDetailInfoSkeleton(modifier = itemMod)
            }
            items(
                count = DETAIL_RELATE_SKELETON_COUNT,
                key = { index -> "detail_loading_relate_$index" },
                contentType = { "skeleton" }
            ) {
                VideoRelateCardSkeleton(modifier = itemMod)
            }
        }

        !pageState.detailError.isNullOrBlank() -> {
            item(
                key = "detail_error",
                contentType = "state"
            ) {
                StateCard(
                    text = pageState.detailError,
                    modifier = itemMod,
                    isError = true
                )
            }
        }

        pageState.detail != null -> {
            val detail = pageState.detail
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
                    onOpenSpace = onOpenSpace,
                    onDownloadClick = onDownloadClick,
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
                items(
                    items = detail.relates,
                    key = { "${it.target.aid}_${it.target.cid}" },
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
    modifier: Modifier = Modifier,
    descOn: Boolean,
    tagOn: Boolean,
    onToggleDesc: () -> Unit,
    onToggleTag: () -> Unit,
    onOpenSpace: (SpaceRoute) -> Unit,
    onDownloadClick: () -> Unit
) {
    val spaceRoute = detail.toSpaceRouteOrNull()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        detail.owner?.let { owner ->
            OwnerCapsule(
                owner = owner,
                onClick = spaceRoute?.let { route ->
                    { onOpenSpace(route) }
                }
            )
        }
        InfoCapsule(
            detail = detail,
            descOn = descOn,
            tagOn = tagOn,
            onToggleDesc = onToggleDesc,
            onToggleTag = onToggleTag
        )
        ActionCapsule(
            stat = detail.stat,
            onDownloadClick = onDownloadClick
        )
    }
}

@Composable
private fun OwnerCapsule(
    owner: VideoOwner,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    CapsuleCard(
        modifier = modifier,
        onClick = onClick
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            owner.face?.takeIf(String::isNotBlank)?.let { face ->
                BiliAsyncImage(
                    url = face,
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
    stat: VideoStat?,
    modifier: Modifier = Modifier,
    onDownloadClick: () -> Unit
) {
    CapsuleCard(modifier = modifier) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            stat?.let {
                ActionChip("点赞", it.like)
                ActionChip("投币", it.coin)
                ActionChip("收藏", it.fav)
                ActionChip("分享", it.share)
            }
            ActionChip(
                label = "下载",
                onClick = onDownloadClick
            )
        }
    }
}

@Composable
private fun CapsuleCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    if (onClick != null) {
        Card(
            onClick = onClick,
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
    } else {
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
    value: String? = null,
    onClick: (() -> Unit)? = null
) {
    Surface(
        modifier = if (onClick != null) {
            Modifier.clickable(onClick = onClick)
        } else {
            Modifier
        },
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Row(
            modifier = Modifier
                .defaultMinSize(minWidth = 76.dp, minHeight = 36.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = if (value == null) {
                    MaterialTheme.typography.titleSmall
                } else {
                    MaterialTheme.typography.labelMedium
                },
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            value?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
private fun SeasonEntryCard(
    season: VideoSeason,
    curCid: Long?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
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
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val (title, subTitle, countText) = remember(pages, curCid) {
        pageEntryText(pages, curCid)
    }

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
    onOpenEpisode: (VideoTarget.Ugc) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val initIdx = remember(season, curCid) { seasonSheetIndex(season, curCid) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initIdx)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        LazyColumn(
            state = listState,
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
                    key = { "ep_${it.target.aid}_${it.target.cid}" },
                    contentType = { "episode" }
                ) { ep ->
                    SeasonEpisodeRow(
                        ep = ep,
                        selected = ep.target.cid == curCid,
                        onClick = {
                            if (ep.target.cid != curCid) {
                                onOpenEpisode(ep.target)
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
    val pageSheetUi = remember(pages) { buildPageSheetUi(pages) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val initIdx = remember(pages, curCid) { pageSheetIndex(pageSheetUi, curCid) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initIdx)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        LazyColumn(
            state = listState,
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
                items = pageSheetUi.items,
                key = { page -> "page_${page.cid}" },
                contentType = { "page" }
            ) { page ->
                PageSheetRow(
                    title = page.title,
                    subTitle = page.subTitle,
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
            BiliAsyncImage(
                url = cover,
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
                    CurBadge()
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
                CurBadge()
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
    onOpenVideo: (VideoTarget) -> Unit,
    modifier: Modifier = Modifier
) {
    val meta = listOfNotNull(
        relate.viewText?.let { "$it 播放" },
        relate.danmakuText?.let { "$it 弹幕" },
        relate.durationText
    ).joinToString(" · ")

    Card(
        onClick = { onOpenVideo(relate.target) },
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            BiliAsyncImage(
                url = relate.cover,
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
private fun CurBadge() {
    Surface(
        color = MaterialTheme.colorScheme.primary,
        shape = MaterialTheme.shapes.extraSmall
    ) {
        Text(
            text = "当前播放",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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
        .firstOrNull { it.target.cid == curCid }
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
    val curIdx = curCid?.let { pages.indexOfFirst { it.cid == curCid } } ?: -1
    val curPage = pages.getOrNull(curIdx)
    return Triple(
        if (curPage != null) buildPageTitle(curIdx, curPage.part) else "查看分P列表",
        curPage?.durationSec?.takeIf { it > 0L }?.let { formatDuration(it * 1000) }.orEmpty(),
        "${pages.size} 个分 P"
    )
}

private fun seasonSheetIndex(season: VideoSeason, curCid: Long?): Int {
    if (curCid == null) return 0
    var secItemIdx = 1
    season.sections.forEach { sec ->
        val epIdx = sec.eps.indexOfFirst { it.target.cid == curCid }
        if (epIdx >= 0) {
            return secItemIdx + 1 + epIdx
        }
        secItemIdx += 1 + sec.eps.size
    }
    return 0
}

private fun pageSheetIndex(
    pageSheetUi: PageSheetUi,
    curCid: Long?
): Int {
    val pageIdx = curCid?.let { targetCid -> pageSheetUi.indexByCid[targetCid] } ?: -1
    return if (pageIdx >= 0) pageIdx + 1 else 0
}

private fun buildPageSheetUi(pages: List<VideoPagePart>): PageSheetUi {
    val items = ArrayList<PageSheetItem>(pages.size)
    val indexByCid = HashMap<Long, Int>(pages.size)
    pages.forEachIndexed { index, page ->
        items += PageSheetItem(
            cid = page.cid,
            title = buildPageTitle(index, page.part),
            subTitle = page.durationSec.takeIf { it > 0L }?.let { formatDuration(it * 1000) }
        )
        indexByCid[page.cid] = index
    }
    return PageSheetUi(
        items = items,
        indexByCid = indexByCid
    )
}

private fun buildPageTitle(index: Int, part: String): String {
    return "P${index + 1} $part"
}

private fun formatPubTime(ts: Long): String {
    return DateFormat.format("yyyy-MM-dd HH:mm", ts * 1000).toString()
}

private fun VideoDetail.toSpaceRouteOrNull(): SpaceRoute? {
    val owner = owner ?: return null
    if (owner.mid <= 0L && owner.name.isBlank()) return null
    return SpaceRoute(
        mid = owner.mid,
        name = owner.name.takeIf(String::isNotBlank),
        from = SpaceRouteTool.FROM_DEFAULT,
        fromViewAid = aid.takeIf { it > 0L }
    )
}

private const val DETAIL_RELATE_SKELETON_COUNT = 2
private const val SHEET_SEASON = "season"
private const val SHEET_PAGE = "page"

@Immutable
private data class PageSheetUi(
    val items: List<PageSheetItem>,
    val indexByCid: Map<Long, Int>
)

@Immutable
private data class PageSheetItem(
    val cid: Long,
    val title: String,
    val subTitle: String?
)
