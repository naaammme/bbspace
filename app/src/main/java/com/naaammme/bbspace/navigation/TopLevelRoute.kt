package com.naaammme.bbspace.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector

enum class TopLevelRoute(val label: String, val icon: ImageVector, val route: String) {
    HOME("首页", Icons.Default.Home, "home"),
    DYNAMIC("动态", Icons.Default.Star, "dynamic"),
    MESSAGE("消息", Icons.Default.Email, "message"),
    PROFILE("我的", Icons.Default.Person, "profile")
}
