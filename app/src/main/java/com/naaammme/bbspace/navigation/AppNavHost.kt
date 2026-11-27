package com.naaammme.bbspace.navigation

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.naaammme.bbspace.AppShellViewModel
import com.naaammme.bbspace.core.designsystem.component.roundScreenSafePadding
import com.naaammme.bbspace.core.designsystem.theme.buildNavTransitions
import com.naaammme.bbspace.core.model.ImSessionItem
import com.naaammme.bbspace.core.model.LiveRoute
import com.naaammme.bbspace.core.model.SpaceRoute
import com.naaammme.bbspace.core.model.StreamPlaybackTarget
import com.naaammme.bbspace.core.model.WebLinkTarget
import com.naaammme.bbspace.core.navigation.LocalContentNavigator
import com.naaammme.bbspace.feature.auth.navigation.ACCOUNT_ROUTE
import com.naaammme.bbspace.feature.auth.navigation.LOGIN_ROUTE
import com.naaammme.bbspace.feature.auth.navigation.SMS_LOGIN_ROUTE
import com.naaammme.bbspace.feature.auth.navigation.accountScreen
import com.naaammme.bbspace.feature.auth.navigation.loginScreen
import com.naaammme.bbspace.feature.auth.navigation.smsLoginScreen
import com.naaammme.bbspace.feature.bbspace.navigation.bbSpaceScreen
import com.naaammme.bbspace.feature.bbspace.navigation.navigateToBbSpace
import com.naaammme.bbspace.feature.download.DownloadViewModel
import com.naaammme.bbspace.feature.download.navigation.downloadScreen
import com.naaammme.bbspace.feature.download.navigation.navigateToDownload
import com.naaammme.bbspace.feature.dynamic.DynamicScreen
import com.naaammme.bbspace.feature.dynamic.navigation.dynamicDetailScreen
import com.naaammme.bbspace.feature.favorite.navigation.favoriteScreen
import com.naaammme.bbspace.feature.favorite.navigation.navigateToFavorite
import com.naaammme.bbspace.feature.favorite.navigation.navigateToFavoriteFolder
import com.naaammme.bbspace.feature.history.navigation.historyScreen
import com.naaammme.bbspace.feature.history.navigation.historySearchScreen
import com.naaammme.bbspace.feature.history.navigation.navigateToHistory
import com.naaammme.bbspace.feature.history.navigation.navigateToHistorySearch
import com.naaammme.bbspace.feature.history.navigation.navigateToWatchLater
import com.naaammme.bbspace.feature.history.navigation.watchLaterScreen
import com.naaammme.bbspace.feature.home.HomeScreen
import com.naaammme.bbspace.feature.home.interest.InterestScreen
import com.naaammme.bbspace.feature.im.ImScreen
import com.naaammme.bbspace.feature.im.navigation.imConversationScreen
import com.naaammme.bbspace.feature.im.navigation.imMsgFeedScreen
import com.naaammme.bbspace.feature.im.navigation.navigateToImConversation
import com.naaammme.bbspace.feature.im.navigation.navigateToMsgFeed
import com.naaammme.bbspace.feature.listen.navigation.listenDetailScreen
import com.naaammme.bbspace.feature.live.LiveViewModel
import com.naaammme.bbspace.feature.search.navigation.navigateToSearch
import com.naaammme.bbspace.feature.search.navigation.searchScreen
import com.naaammme.bbspace.feature.settings.navigation.HOME_INTEREST_ROUTE
import com.naaammme.bbspace.feature.settings.navigation.SETTINGS_ROUTE
import com.naaammme.bbspace.feature.settings.navigation.SettingsNavigation
import com.naaammme.bbspace.feature.space.navigation.navigateToSpaceRelation
import com.naaammme.bbspace.feature.space.navigation.navigateToSpaceSearch
import com.naaammme.bbspace.feature.space.navigation.spaceRelationScreen
import com.naaammme.bbspace.feature.space.navigation.spaceScreen
import com.naaammme.bbspace.feature.space.navigation.spaceSearchScreen
import com.naaammme.bbspace.feature.user.UserDest
import com.naaammme.bbspace.feature.user.UserScreen
import com.naaammme.bbspace.feature.user.UserViewModel
import com.naaammme.bbspace.feature.video.VideoViewModel
import com.naaammme.bbspace.feature.webview.navigation.webViewScreen
import com.naaammme.bbspace.playback.PlaybackHost
import com.naaammme.bbspace.playback.PlaybackHostMode
import com.naaammme.bbspace.playback.PlaybackHostViewModel

private const val MAIN_ROUTE = "main"
private val topLevelNavEdgePadding = 16.dp
private val topLevelNavGap = 8.dp
private val topLevelNavScrollThreshold = 72.dp
private const val topLevelNavAnimationDurationMillis = 220
@Composable
fun AppNavHost(
    settingsNavigation: SettingsNavigation,
    appLink: WebLinkTarget? = null,
    onAppLinkConsumed: () -> Unit = {}
) {
    val rootNavController = rememberNavController()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val shellViewModel: AppShellViewModel = hiltViewModel()
    val roundPaddingScale by shellViewModel.roundScreenSafePaddingScale.collectAsStateWithLifecycle()
    val navTransitionConfig by shellViewModel.navTransitionConfig.collectAsStateWithLifecycle()
    val roundSafePadding = roundScreenSafePadding(roundPaddingScale)
    var currentTab by rememberSaveable { mutableStateOf(TopLevelRoute.HOME) }
    val downloadViewModel: DownloadViewModel = hiltViewModel()
    val playbackHostViewModel: PlaybackHostViewModel = hiltViewModel()
    val videoViewModel: VideoViewModel = hiltViewModel()
    val liveViewModel: LiveViewModel = hiltViewModel()
    val hostMode = playbackHostViewModel.hostMode
    var forcedDismissMode by remember { mutableStateOf<PlaybackHostMode?>(null) }
    val playbackMode = when {
        hostMode != PlaybackHostMode.Expanded -> hostMode
        forcedDismissMode != null -> forcedDismissMode!!
        else -> hostMode
    }
    val closeVideoHost: () -> Unit = {
        playbackHostViewModel.close()
    }
    val dismissPlaybackHost: () -> Unit = {
        if (playbackHostViewModel.miniPlayerAvailable.value) {
            playbackHostViewModel.minimize()
        } else {
            when (playbackHostViewModel.currentTarget.value) {
                is StreamPlaybackTarget.Video -> closeVideoHost()
                is StreamPlaybackTarget.Live, null -> playbackHostViewModel.close()
            }
        }
    }
    val collapseExpandedPlayback = {
        if (hostMode == PlaybackHostMode.Expanded) {
            forcedDismissMode = if (
                playbackHostViewModel.miniPlayerAvailable.value &&
                playbackHostViewModel.currentTarget.value != null
            ) {
                PlaybackHostMode.Mini
            } else {
                PlaybackHostMode.Hidden
            }
        }
    }
    val openDownloadFromVideo: () -> Unit = {
        collapseExpandedPlayback()
        dismissPlaybackHost()
        rootNavController.navigateToDownload()
    }
    val goHomeFromVideo: () -> Unit = {
        collapseExpandedPlayback()
        dismissPlaybackHost()
        rootNavController.popBackStack(MAIN_ROUTE, false)
        currentTab = TopLevelRoute.HOME
    }
    val currentDismissPlayback by rememberUpdatedState {
        if (playbackHostViewModel.hostMode == PlaybackHostMode.Expanded) {
            collapseExpandedPlayback()
            dismissPlaybackHost()
        }
    }
    // 保持 navigator 引用稳定，避免重设 LocalContentNavigator 触发整棵路由树重组
    val navigator = remember(rootNavController, playbackHostViewModel, videoViewModel, liveViewModel) {
        AppContentNavigator(
            navController = rootNavController,
            playbackHost = playbackHostViewModel,
            video = videoViewModel,
            live = liveViewModel,
            dismissPlayback = { currentDismissPlayback() }
        )
    }
    val transitions = remember(navTransitionConfig) {
        buildNavTransitions<NavBackStackEntry>(
            navTransitionConfig.transitionStyle,
            navTransitionConfig.animationSpeed
        )
    }

    LaunchedEffect(hostMode, forcedDismissMode) {
        if (forcedDismissMode != null && hostMode != PlaybackHostMode.Expanded) {
            forcedDismissMode = null
        }
    }

    LaunchedEffect(appLink) {
        when (val target = appLink ?: return@LaunchedEffect) {
            is WebLinkTarget.ToVideo -> navigator.openVideo(target.target)
            is WebLinkTarget.ToSpace -> navigator.openSpace(
                SpaceRoute(mid = target.mid)
            )
            is WebLinkTarget.ToLive -> navigator.openLive(
                LiveRoute(roomId = target.roomId)
            )
            is WebLinkTarget.External,
            is WebLinkTarget.Stay -> Unit
        }
        onAppLinkConsumed()
    }

    CompositionLocalProvider(LocalContentNavigator provides navigator) {
        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(roundSafePadding),
                navController = rootNavController,
                startDestination = MAIN_ROUTE,
                enterTransition = { transitions.enter(this) },
                exitTransition = { transitions.exit(this) },
                popEnterTransition = { transitions.popEnter(this) },
                popExitTransition = { transitions.popExit(this) }
            ) {
                composable(MAIN_ROUTE) {
                    MainTabsScaffold(
                        shellViewModel = shellViewModel,
                        currentTab = currentTab,
                        onTabChange = { currentTab = it },
                        onNavigateToSearch = { rootNavController.navigateToSearch() },
                        onNavigateToSettings = { rootNavController.navigate(SETTINGS_ROUTE) },
                        onNavigateToAccount = { rootNavController.navigate(ACCOUNT_ROUTE) },
                        onNavigateToBbSpace = { rootNavController.navigateToBbSpace() },
                        onNavigateFromUser = { dest ->
                            when (dest) {
                                UserDest.History -> rootNavController.navigateToHistory()
                                UserDest.Favorite -> rootNavController.navigateToFavorite()
                                UserDest.WatchLater -> rootNavController.navigateToWatchLater()
                            }
                        },
                        onNavigateToDownload = { rootNavController.navigateToDownload() },
                        onNavigateToMsgFeed = { rootNavController.navigateToMsgFeed() },
                        onNavigateToImConversation = { item ->
                            rootNavController.navigateToImConversation(item)
                        }
                    )
                }

                loginScreen(
                    onLoginSuccess = { rootNavController.popBackStack() },
                    onBack = { rootNavController.popBackStack() },
                    onSwitchToSms = {
                        rootNavController.navigate(SMS_LOGIN_ROUTE) {
                            popUpTo(LOGIN_ROUTE) { inclusive = true }
                        }
                    }
                )

                smsLoginScreen(
                    onLoginSuccess = { rootNavController.popBackStack() },
                    onBack = { rootNavController.popBackStack() },
                    onSwitchToQr = {
                        rootNavController.navigate(LOGIN_ROUTE) {
                            popUpTo(SMS_LOGIN_ROUTE) { inclusive = true }
                        }
                    }
                )

                accountScreen(
                    onBack = { rootNavController.popBackStack() },
                    onAddAccount = { rootNavController.navigate(SMS_LOGIN_ROUTE) },
                    onSwitched = { rootNavController.popBackStack() }
                )

                bbSpaceScreen(
                    onBack = { rootNavController.popBackStack() }
                )
                settingsNavigation.register(this, rootNavController)
                searchScreen(
                    onBack = { rootNavController.popBackStack() }
                )
                historyScreen(
                    onBack = { rootNavController.popBackStack() },
                    onSearch = { tab -> rootNavController.navigateToHistorySearch(tab) }
                )
                historySearchScreen(
                    onBack = { rootNavController.popBackStack() }
                )
                watchLaterScreen(
                    onBack = { rootNavController.popBackStack() }
                )
                favoriteScreen(
                    onBack = { rootNavController.popBackStack() },
                    onOpenFolder = { fid ->
                        rootNavController.navigateToFavoriteFolder(fid)
                    }
                )
                composable(HOME_INTEREST_ROUTE) {
                    InterestScreen(onBack = { rootNavController.popBackStack() })
                }
                spaceScreen(
                    onBack = { rootNavController.popBackStack() },
                    onOpenIm = { mid, name, avatar ->
                        rootNavController.navigateToImConversation(
                            ImSessionItem(
                                key = "space:$mid",
                                talkerId = mid,
                                sessionType = 1,
                                name = name,
                                avatar = avatar
                            )
                        )
                    },
                    onOpenRelation = { vmid, initialTab ->
                        rootNavController.navigateToSpaceRelation(vmid, initialTab)
                    },
                    onOpenSearch = rootNavController::navigateToSpaceSearch
                )
                spaceRelationScreen(
                    onBack = { rootNavController.popBackStack() }
                )
                spaceSearchScreen(
                    onBack = { rootNavController.popBackStack() }
                )
                downloadScreen(
                    navController = rootNavController,
                    onBack = { rootNavController.popBackStack() },
                    viewModel = downloadViewModel,
                    closePlaybackHost = closeVideoHost
                )

                dynamicDetailScreen(
                    onBack = { rootNavController.popBackStack() }
                )

                listenDetailScreen(
                    onBack = { rootNavController.popBackStack() }
                )

                imConversationScreen(
                    onBack = { rootNavController.popBackStack() }
                )

                imMsgFeedScreen(
                    onBack = { rootNavController.popBackStack() }
                )
                webViewScreen(
                    onBack = { rootNavController.popBackStack() },
                    onOpenExternal = { url ->
                        runCatching { uriHandler.openUri(url) }.onFailure {
                            Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            PlaybackHost(
                mode = playbackMode,
                playbackHostViewModel = playbackHostViewModel,
                onExpand = {
                    playbackHostViewModel.expand()
                },
                onTogglePlay = playbackHostViewModel::togglePlayPause,
                onClose = {
                    when (playbackHostViewModel.currentTarget.value) {
                        is StreamPlaybackTarget.Video -> closeVideoHost()
                        is StreamPlaybackTarget.Live -> playbackHostViewModel.close()
                        null -> Unit
                    }
                },
                onDismissExpanded = dismissPlaybackHost,
                onGoHome = goHomeFromVideo,
                onOpenDownloadCache = openDownloadFromVideo,
                onStartDownload = downloadViewModel::enqueueDownload,
                videoViewModel = videoViewModel,
                liveViewModel = liveViewModel
            )
        }
    }
}

@Composable
private fun MainTabsScaffold(
    shellViewModel: AppShellViewModel,
    currentTab: TopLevelRoute,
    onTabChange: (TopLevelRoute) -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAccount: () -> Unit,
    onNavigateToBbSpace: () -> Unit,
    onNavigateFromUser: (UserDest) -> Unit,
    onNavigateToDownload: () -> Unit,
    onNavigateToMsgFeed: () -> Unit,
    onNavigateToImConversation: (ImSessionItem) -> Unit
) {
    val saveableStateHolder = rememberSaveableStateHolder()
    val userViewModel: UserViewModel = hiltViewModel()
    val userState by userViewModel.uiState.collectAsStateWithLifecycle()
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val useStartAlignedBottomToolbar = windowSizeClass.isWidthAtLeastBreakpoint(600)
    val fixBottomBar by shellViewModel.fixBottomBar.collectAsStateWithLifecycle()
    val navVisibilityController = rememberTopLevelNavVisibilityController(fixBottomBar)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .nestedScroll(navVisibilityController.connection)
    ) {
        TopLevelRoute.entries.forEach { tab ->
            if (currentTab == tab) {
                saveableStateHolder.SaveableStateProvider(tab.route) {
                    when (tab) {
                        TopLevelRoute.HOME -> HomeScreen(
                            onNavigateToSearch = onNavigateToSearch,
                            onNavigateToProfile = { onTabChange(TopLevelRoute.PROFILE) },
                            profileAvatar = userState.user?.avatar
                        )
                        TopLevelRoute.DYNAMIC -> DynamicScreen()
                        TopLevelRoute.MESSAGE -> ImScreen(
                            onOpenConversation = onNavigateToImConversation,
                            onOpenMsgFeed = onNavigateToMsgFeed
                        )
                        TopLevelRoute.PROFILE -> UserScreen(
                            onNavigateToAccount = onNavigateToAccount,
                            onNavigateToSettings = onNavigateToSettings,
                            onNavigateToBbSpace = onNavigateToBbSpace,
                            onNavigate = onNavigateFromUser,
                            onNavigateToDownload = onNavigateToDownload
                        )
                    }
                }
            }
        }

        TopLevelFloatingNavigation(
            modifier = Modifier
                .align(
                    if (useStartAlignedBottomToolbar) Alignment.BottomStart else Alignment.BottomCenter
                )
                .padding(
                    start = if (useStartAlignedBottomToolbar) topLevelNavEdgePadding else 0.dp,
                    bottom = topLevelNavEdgePadding
                )
                .zIndex(1f),
            currentTab = currentTab,
            visibilityController = navVisibilityController,
            onTabChange = onTabChange,
            onNavigateToSearch = onNavigateToSearch
        )
    }
}

@Composable
private fun TopLevelFloatingNavigation(
    modifier: Modifier = Modifier,
    currentTab: TopLevelRoute,
    visibilityController: TopLevelNavVisibilityController,
    onTabChange: (TopLevelRoute) -> Unit,
    onNavigateToSearch: () -> Unit
) {
    val toolbarShape = MaterialTheme.shapes.extraLarge
    val edgePaddingPx = with(LocalDensity.current) { topLevelNavEdgePadding.toPx() }
    var hiddenDistancePx by remember { mutableFloatStateOf(0f) }
    val animatedOffsetPx by animateFloatAsState(
        targetValue = if (visibilityController.hidden) hiddenDistancePx + edgePaddingPx else 0f,
        animationSpec = tween(durationMillis = topLevelNavAnimationDurationMillis),
        label = "top level nav offset"
    )
    val animatedModifier = modifier
        .onSizeChanged { size ->
            hiddenDistancePx = size.height.toFloat()
        }
        .graphicsLayer {
            translationY = animatedOffsetPx
        }
    val tabs: @Composable () -> Unit = {
        TopLevelRoute.entries.forEach { tab ->
            TopLevelFloatingNavigationItem(
                tab = tab,
                selected = currentTab == tab,
                onClick = { onTabChange(tab) }
            )
        }
    }
    val searchFab: @Composable () -> Unit = {
        FloatingActionButton(
            onClick = onNavigateToSearch,
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 0.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "搜索"
            )
        }
    }

    Row(
        modifier = animatedModifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = toolbarShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ) {
            Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), content = { tabs() })
        }
        Spacer(modifier = Modifier.width(topLevelNavGap))
        searchFab()
    }
}

@Composable
private fun TopLevelFloatingNavigationItem(
    tab: TopLevelRoute,
    selected: Boolean,
    onClick: () -> Unit
) {
    if (selected) {
        FilledIconButton(onClick = onClick) {
            Icon(
                imageVector = tab.icon,
                contentDescription = tab.label
            )
        }
    } else {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = tab.icon,
                contentDescription = tab.label,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Stable
private class TopLevelNavVisibilityController(
    thresholdPx: Float,
    initialHidden: Boolean = false,
    private val fixBottomBar: Boolean = false
) {
    private val triggerThresholdPx = thresholdPx
    private var accumulatedScroll = 0f

    var hidden by mutableStateOf(initialHidden)
        private set

    val connection = object : NestedScrollConnection {
        @Suppress("SameReturnValue")
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (fixBottomBar) return Offset.Zero
            if (source != NestedScrollSource.UserInput) return Offset.Zero
            if (available.y == 0f) return Offset.Zero
            if (kotlin.math.abs(available.y) < kotlin.math.abs(available.x)) return Offset.Zero
            updateVisibility(available.y)
            return Offset.Zero
        }
    }

    private fun updateVisibility(deltaY: Float) {
        if (deltaY == 0f) return
        if ((deltaY < 0f && accumulatedScroll > 0f) || (deltaY > 0f && accumulatedScroll < 0f)) {
            accumulatedScroll = 0f
        }
        accumulatedScroll += deltaY
        when {
            accumulatedScroll <= -triggerThresholdPx -> {
                if (!hidden) {
                    hidden = true
                }
                accumulatedScroll = 0f
            }
            accumulatedScroll >= triggerThresholdPx -> {
                if (hidden) {
                    hidden = false
                }
                accumulatedScroll = 0f
            }
        }
    }
}

@Composable
private fun rememberTopLevelNavVisibilityController(
    fixBottomBar: Boolean = false
): TopLevelNavVisibilityController {
    val thresholdPx = with(LocalDensity.current) { topLevelNavScrollThreshold.toPx() }
    return remember(thresholdPx, fixBottomBar) {
        TopLevelNavVisibilityController(thresholdPx = thresholdPx, fixBottomBar = fixBottomBar)
    }
}
