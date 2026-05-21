package com.naaammme.bbspace.feature.user

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naaammme.bbspace.core.designsystem.component.CollapsingTopBarScaffold
import com.naaammme.bbspace.core.model.SpaceRoute
import com.naaammme.bbspace.feature.user.component.AccountExpiredDialog
import com.naaammme.bbspace.feature.user.entry.UserEntrySection
import com.naaammme.bbspace.feature.user.profile.UserProfileSection

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

    CollapsingTopBarScaffold(
        topBar = { scrollBehavior ->
            TopAppBar(
                title = { Text("我的") },
                actions = {
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
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            UserProfileSection(
                user = state.user,
                onOpenSpace = onOpenSpace
            )

            Spacer(modifier = Modifier.height(16.dp))

            UserEntrySection(
                onNavigateToBbSpace = onNavigateToBbSpace,
                onNavigate = onNavigate,
                onNavigateToDownload = onNavigateToDownload
            )
        }
    }

    if (state.showAccountExpiredDialog) {
        AccountExpiredDialog(onDismiss = vm::dismissAccountExpiredDialog)
    }
}
