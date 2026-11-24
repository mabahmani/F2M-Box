package com.mab.f2mbox.ui.list

import androidx.compose.runtime.Composable

@Composable
fun RememberListPageBackHandler(
    routeKey: Any,
    canPopPage: Boolean,
    onPopPage: () -> Unit,
    onNavigateBack: () -> Unit,
    registerEsc: ((() -> Unit)?) -> Unit = {},
) {
    val onBack: () -> Unit = {
        if (canPopPage) onPopPage() else onNavigateBack()
    }
    PlatformListPageBackHandler(routeKey, onBack, registerEsc)
}

@Composable
internal expect fun PlatformListPageBackHandler(
    routeKey: Any,
    onBack: () -> Unit,
    registerEsc: ((() -> Unit)?) -> Unit,
)