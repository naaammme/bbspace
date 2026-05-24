package com.naaammme.bbspace.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.naaammme.bbspace.core.designsystem.theme.ThemeConfig
import com.naaammme.bbspace.core.designsystem.theme.buildNavTransitions
import com.naaammme.bbspace.core.model.FavoriteContentTarget
import com.naaammme.bbspace.core.model.ImSessionItem
import com.naaammme.bbspace.core.model.LiveRoute
import com.naaammme.bbspace.core.model.SpaceRoute
import com.naaammme.bbspace.core.model.StreamPlaybackTarget
import com.naaammme.bbspace.core.model.VideoSrc
import com.naaammme.bbspace.core.model.VideoTarget
import com.naaammme.bbspace.core.model.VideoTargetTool
import com.naaammme.bbspace.core.model.WebLinkTarget
import com.naaammme.bbspace.feature.dynamic.DynamicScreen
import com.naaammme.bbspace.feature.dynamic.navigation.dynamicDetailScreen
import com.naaammme.bbspace.feature.dynamic.navigation.navigateToDynamicDetail
import com.naaammme.bbspace.feature.auth.navigation.ACCOUNT_ROUTE
import com.naaammme.bbspace.feature.auth.navigation.LOGIN_ROUTE
import com.naaammme.bbspace.feature.auth.navigation.SMS_LOGIN_ROUTE
import com.naaammme.bbspace.feature.auth.navigation.accountScreen
import com.naaammme.bbspace.feature.auth.navigation.loginScreen
import com.naaammme.bbspace.feature.auth.navigation.smsLoginScreen
import com.naaammme.bbspace.feature.bbspace.navigation.bbSpaceScreen
import com.naaammme.bbspace.feature.bbspace.navigation.navigateToBbSpace
import com.naaammme.bbspace.feature.download.navigation.downloadScreen
import com.naaammme.bbspace.feature.download.navigation.navigateToDownload
import com.naaammme.bbspace.feature.favorite.navigation.favoriteScreen
import com.naaammme.bbspace.feature.favorite.navigation.navigateToFavorite
import com.naaammme.bbspace.feature.history.navigation.historyScreen
import com.naaammme.bbspace.feature.history.navigation.navigateToHistory
import com.naaammme.bbspace.feature.history.navigation.navigateToWatchLater
import com.naaammme.bbspace.feature.history.navigation.watchLaterScreen
import com.naaammme.bbspace.feature.home.HomeScreen
import com.naaammme.bbspace.feature.im.ImScreen
import com.naaammme.bbspace.feature.im.navigation.imConversationScreen
import com.naaammme.bbspace.feature.im.navigation.navigateToImConversation
import com.naaammme.bbspace.feature.listen.navigation.listenDetailScreen
import com.naaammme.bbspace.feature.listen.navigation.navigateToListenDetail
import com.naaammme.bbspace.feature.live.LiveViewModel
import com.naaammme.bbspace.feature.search.navigation.navigateToSearch
import com.naaammme.bbspace.feature.search.navigation.searchScreen
import com.naaammme.bbspace.feature.space.navigation.navigateToSpace
import com.naaammme.bbspace.feature.space.navigation.spaceScreen
import com.naaammme.bbspace.feature.home.interest.InterestScreen
import com.naaammme.bbspace.feature.settings.navigation.HOME_INTEREST_ROUTE
import com.naaammme.bbspace.feature.settings.navigation.SETTINGS_ROUTE
import com.naaammme.bbspace.feature.settings.navigation.settingsScreen
import com.naaammme.bbspace.feature.download.DownloadViewModel
import com.naaammme.bbspace.feature.user.UserScreen
import com.naaammme.bbspace.feature.user.UserDest
import com.naaammme.bbspace.feature.user.UserViewModel
import com.naaammme.bbspace.feature.video.VideoViewModel
import com.naaammme.bbspace.playback.PlaybackHost
import com.naaammme.bbspace.playback.PlaybackHostMode
import com.naaammme.bbspace.playback.PlaybackHostViewModel

private const val MAIN_ROUTE = "main"

@Composable
fun AppNavHost(
    themeConfig: ThemeConfig = ThemeConfig(),
    appLink: WebLinkTarget? = null,
    onAppLinkConsumed: () -> Unit = {}
) {
    val rootNavController = rememberNavController()
    val downloadViewModel: DownloadViewModel = hiltViewModel()
    val playbackHostViewModel: PlaybackHostViewModel = hiltViewModel()
    val videoViewModel: VideoViewModel = hiltViewModel()
    val liveViewModel: LiveViewModel = hiltViewModel()
    val player by playbackHostViewModel.player.collectAsStateWithLifecycle()
    val target by playbackHostViewModel.currentTarget.collectAsStateWithLifecycle()
    val sessionState by playbackHostViewModel.sessionState.collectAsStateWithLifecycle()
    val pageMeta by playbackHostViewModel.pageMeta.collectAsStateWithLifecycle()
    val backgroundPlaybackEnabled by playbackHostViewModel.backgroundPlaybackEnabled.collectAsStateWithLifecycle()
    val miniPlayerAvailable by playbackHostViewModel.miniPlayerAvailable.collectAsStateWithLifecycle()
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
        if (miniPlayerAvailable) {
            playbackHostViewModel.minimize()
        } else {
            when (target) {
                is StreamPlaybackTarget.Video -> closeVideoHost()
                is StreamPlaybackTarget.Live, null -> playbackHostViewModel.close()
            }
        }
    }
    val collapseExpandedPlayback = {
        if (hostMode == PlaybackHostMode.Expanded) {
            forcedDismissMode = if (miniPlayerAvailable && target != null) {
                PlaybackHostMode.Mini
            } else {
                PlaybackHostMode.Hidden
            }
        }
    }
    val openSpaceFromVideo: (SpaceRoute) -> Unit = { route ->
        collapseExpandedPlayback()
        dismissPlaybackHost()
        rootNavController.navigateToSpace(route)
    }
    val openDownloadFromVideo: () -> Unit = {
        collapseExpandedPlayback()
        dismissPlaybackHost()
        rootNavController.navigateToDownload()
    }
    val openVideo: (VideoTarget) -> Unit = { target ->
        playbackHostViewModel.expand()
        videoViewModel.openRoot(target)
    }
    val openLive: (LiveRoute) -> Unit = { route ->
        playbackHostViewModel.openLive(route)
        playbackHostViewModel.expand()
    }
    val openListenDetail: (Long, Int, Long, String, String, String) -> Unit = {
            oid,
            itemType,
            subId,
            title,
            author,
            cover ->
        playbackHostViewModel.close()
        rootNavController.navigateToListenDetail(oid, itemType, subId, title, author, cover)
    }
    val openArticle: (String, Int) -> Unit = { opusId, opusType ->
        playbackHostViewModel.minimize()
        rootNavController.navigateToDynamicDetail(opusId, opusType)
    }
    val transitions = remember(themeConfig.transitionStyle, themeConfig.animationSpeed) {
        buildNavTransitions<NavBackStackEntry>(
            themeConfig.transitionStyle,
            themeConfig.animationSpeed
        )
    }

    LaunchedEffect(hostMode, forcedDismissMode) {
        if (forcedDismissMode != null && hostMode != PlaybackHostMode.Expanded) {
            forcedDismissMode = null
        }
    }

    LaunchedEffect(appLink) {
        when (val target = appLink ?: return@LaunchedEffect) {
            is WebLinkTarget.ToVideo -> openVideo(target.target)
            is WebLinkTarget.ToSpace -> rootNavController.navigateToSpace(
                SpaceRoute(mid = target.mid)
            )
            is WebLinkTarget.ToLive -> openLive(
                LiveRoute(roomId = target.roomId)
            )
            is WebLinkTarget.External,
            is WebLinkTarget.Stay -> Unit
        }
        onAppLinkConsumed()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = rootNavController,
            startDestination = MAIN_ROUTE,
            enterTransition = { transitions.enter(this) },
            exitTransition = { transitions.exit(this) },
            popEnterTransition = { transitions.popEnter(this) },
            popExitTransition = { transitions.popExit(this) }
        ) {
            composable(MAIN_ROUTE) {
                MainTabsScaffold(
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
                    onNavigateToVideo = openVideo,
                    onNavigateToSpace = rootNavController::navigateToSpace,
                    onNavigateToLive = openLive,
                    onNavigateToArticle = openArticle,
                    onNavigateToDynamicDetail = rootNavController::navigateToDynamicDetail,
                    onNavigateToListenDetail = openListenDetail,
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

            bbSpaceScreen(rootNavController)
            settingsScreen(rootNavController)
            searchScreen(
                onBack = { rootNavController.popBackStack() },
                onOpenVideo = openVideo
            )
            historyScreen(
                onBack = { rootNavController.popBackStack() },
                onOpenVideo = openVideo,
                onOpenLive = openLive,
                onOpenDynamicDetail = rootNavController::navigateToDynamicDetail
            )
            watchLaterScreen(
                onBack = { rootNavController.popBackStack() },
                onOpenVideo = openVideo
            )
            favoriteScreen(
                onBack = { rootNavController.popBackStack() },
                onOpenContent = { target ->
                    when (target) {
                        is FavoriteContentTarget.Video -> openVideo(target.target)
                        is FavoriteContentTarget.DynamicDetail -> {
                            rootNavController.navigateToDynamicDetail(target.opusId)
                        }
                    }
                }
            )
            composable(HOME_INTEREST_ROUTE) {
                InterestScreen(onBack = { rootNavController.popBackStack() })
            }
            spaceScreen(
                onBack = { rootNavController.popBackStack() },
                onOpenVideo = openVideo,
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
                }
            )
            downloadScreen(
                navController = rootNavController,
                onBack = { rootNavController.popBackStack() },
                viewModel = downloadViewModel,
                closePlaybackHost = closeVideoHost
            )

            dynamicDetailScreen(
                onBack = { rootNavController.popBackStack() },
                onOpenSpace = { route ->
                    rootNavController.popBackStack()
                    rootNavController.navigateToSpace(route)
                }
            )

            listenDetailScreen(
                onBack = { rootNavController.popBackStack() }
            )

            imConversationScreen(
                onBack = { rootNavController.popBackStack() },
                onOpenSpace = { mid ->
                    rootNavController.navigateToSpace(SpaceRoute(mid = mid))
                },
                onOpenVideo = { aid ->
                    val target = VideoTarget.Ugc(
                        aid = aid,
                        cid = 0L,
                        src = VideoSrc(
                            from = VideoTargetTool.FROM_DEFAULT,
                            fromSpmid = VideoTargetTool.FROM_SPMID_DEFAULT
                        )
                    )
                    openVideo(target)
                }
            )
        }

        PlaybackHost(
            mode = playbackMode,
            target = target,
            player = player,
            sessionState = sessionState,
            pageMeta = pageMeta,
            miniPlayerAvailable = miniPlayerAvailable,
            backgroundPlaybackEnabled = backgroundPlaybackEnabled,
            onExpand = {
                playbackHostViewModel.expand()
                when (val playbackTarget = target) {
                    is StreamPlaybackTarget.Video -> videoViewModel.syncToPlayback(playbackTarget.target)
                    is StreamPlaybackTarget.Live -> Unit
                    null -> Unit
                }
            },
            onTogglePlay = playbackHostViewModel::togglePlayPause,
            onPauseInBackground = playbackHostViewModel::pause,
            onClose = {
                when (target) {
                    is StreamPlaybackTarget.Video -> closeVideoHost()
                    is StreamPlaybackTarget.Live -> playbackHostViewModel.close()
                    null -> Unit
                }
            },
            onDismissExpanded = dismissPlaybackHost,
            onOpenSpace = openSpaceFromVideo,
            onOpenDownloadCache = openDownloadFromVideo,
            onStartDownload = downloadViewModel::enqueueDownload,
            videoViewModel = videoViewModel,
            liveViewModel = liveViewModel,
            modifier = Modifier.align(Alignment.BottomEnd)
        )
    }
}

@Composable
private fun MainTabsScaffold(
    onNavigateToSearch: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAccount: () -> Unit,
    onNavigateToBbSpace: () -> Unit,
    onNavigateFromUser: (UserDest) -> Unit,
    onNavigateToDownload: () -> Unit,
    onNavigateToVideo: (VideoTarget) -> Unit,
    onNavigateToSpace: (SpaceRoute) -> Unit,
    onNavigateToLive: (LiveRoute) -> Unit,
    onNavigateToArticle: (String, Int) -> Unit,
    onNavigateToDynamicDetail: (String) -> Unit,
    onNavigateToListenDetail: (Long, Int, Long, String, String, String) -> Unit,
    onNavigateToImConversation: (ImSessionItem) -> Unit
) {
    var currentTab by rememberSaveable { mutableStateOf(TopLevelRoute.HOME) }
    val saveableStateHolder = rememberSaveableStateHolder()
    val userViewModel: UserViewModel = hiltViewModel()
    val userState by userViewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            NavigationBar {
                TopLevelRoute.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { currentTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            TopLevelRoute.entries.forEach { tab ->
                if (currentTab == tab) {
                    saveableStateHolder.SaveableStateProvider(tab.route) {
                        when (tab) {
                            TopLevelRoute.HOME -> HomeScreen(
                                onNavigateToSearch = onNavigateToSearch,
                                onNavigateToProfile = { currentTab = TopLevelRoute.PROFILE },
                                profileAvatar = userState.user?.avatar,
                                onOpenVideo = onNavigateToVideo,
                                onOpenSpace = onNavigateToSpace,
                                onOpenLive = onNavigateToLive,
                                onOpenArticle = onNavigateToArticle,
                                onOpenListenItem = onNavigateToListenDetail
                            )
                            TopLevelRoute.DYNAMIC -> DynamicScreen(
                                onOpenVideo = onNavigateToVideo,
                                onOpenSpace = onNavigateToSpace,
                                onOpenLive = onNavigateToLive,
                                onOpenDynamic = onNavigateToDynamicDetail
                            )
                            TopLevelRoute.MESSAGE -> ImScreen(
                                onOpenConversation = onNavigateToImConversation
                            )
                            TopLevelRoute.PROFILE -> UserScreen(
                                onNavigateToAccount = onNavigateToAccount,
                                onNavigateToSettings = onNavigateToSettings,
                                onNavigateToBbSpace = onNavigateToBbSpace,
                                onNavigate = onNavigateFromUser,
                                onNavigateToDownload = onNavigateToDownload,
                                onOpenSpace = onNavigateToSpace
                            )
                        }
                    }
                }
            }
        }
    }
}

