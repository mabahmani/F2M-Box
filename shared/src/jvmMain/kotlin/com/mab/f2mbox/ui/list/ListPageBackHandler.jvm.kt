package com.mab.f2mbox.ui.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

@Composable
internal actual fun PlatformListPageBackHandler(
    routeKey: Any,
    onBack: () -> Unit,
    registerEsc: ((() -> Unit)?) -> Unit,
) {
    DisposableEffect(routeKey) {
        registerEsc(onBack)
        onDispose { registerEsc(null) }
    }
}