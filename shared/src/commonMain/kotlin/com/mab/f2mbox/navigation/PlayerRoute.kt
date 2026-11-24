package com.mab.f2mbox.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/** Platform in-app player (Media3 on TV, VLC on desktop). */
class PlayerRouteContent(
    val content: @Composable (
        url: String,
        title: String,
        subtitleUrl: String?,
        onNavigateBack: () -> Unit,
        registerEsc: ((() -> Unit)?) -> Unit,
    ) -> Unit,
)

val LocalPlayerRouteContent = staticCompositionLocalOf<PlayerRouteContent> {
    error("LocalPlayerRouteContent not provided")
}