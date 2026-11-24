package com.mab.f2mbox.ui.navigation

import androidx.compose.runtime.Composable
import com.mab.f2mbox.navigation.PlayerRouteContent
import com.mab.f2mbox.ui.player.PlayerScreen

fun tvPlayerRouteContent(): PlayerRouteContent = PlayerRouteContent { url, title, _, onNavigateBack, _ ->
    PlayerScreen(
        url = url,
        title = title,
        onNavigateBack = onNavigateBack,
    )
}