package com.mab.f2mbox.desktop.ui.navigation

import androidx.compose.runtime.Composable
import com.mab.f2mbox.desktop.ui.player.PlayerScreen
import com.mab.f2mbox.navigation.PlayerRouteContent

fun desktopPlayerRouteContent(): PlayerRouteContent = PlayerRouteContent { url, title, subtitleUrl, onNavigateBack, registerEsc ->
    PlayerScreen(
        url = url,
        title = title,
        subtitleUrl = subtitleUrl,
        onNavigateBack = onNavigateBack,
        registerEsc = registerEsc,
    )
}