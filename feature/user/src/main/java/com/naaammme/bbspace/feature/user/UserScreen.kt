package com.naaammme.bbspace.feature.user

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naaammme.bbspace.core.model.SpaceRoute
import com.naaammme.bbspace.feature.user.collage.rememberUserCollageState
import com.naaammme.bbspace.feature.user.collage.UserCollageSection
import com.naaammme.bbspace.feature.user.collage.rememberUserCollagePalette
import com.naaammme.bbspace.feature.user.collage.rememberUserCollageBackgroundBrush
import com.naaammme.bbspace.feature.user.component.AccountExpiredDialog

enum class UserDest {
    History,
    Favorite,
    WatchLater
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserScreen(
    onNavigateToAccount: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToBbSpace: () -> Unit,
    onNavigate: (UserDest) -> Unit,
    onNavigateToDownload: () -> Unit,
    onOpenSpace: (SpaceRoute) -> Unit = {},
    vm: UserViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val palette = rememberUserCollagePalette()
    val backgroundBrush = rememberUserCollageBackgroundBrush(palette)
    val collageState = rememberUserCollageState()

    Scaffold(
        modifier = Modifier.background(brush = backgroundBrush),
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("我的") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                ),
                actions = {
                    IconButton(onClick = collageState::reset) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "重置布局"
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "设置"
                        )
                    }
                    IconButton(onClick = onNavigateToAccount) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = "账号管理"
                        )
                    }
                }
            )
        }
    ) { padding ->
        UserCollageSection(
            user = state.user,
            collageOffsets = collageState.offsets,
            onCollageOffsetChange = collageState::updateOffset,
            onOpenSpace = onOpenSpace,
            onNavigateToBbSpace = onNavigateToBbSpace,
            onNavigate = onNavigate,
            onNavigateToDownload = onNavigateToDownload,
            palette = palette,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }

    if (state.showAccountExpiredDialog) {
        AccountExpiredDialog(onDismiss = vm::dismissAccountExpiredDialog)
    }
}
