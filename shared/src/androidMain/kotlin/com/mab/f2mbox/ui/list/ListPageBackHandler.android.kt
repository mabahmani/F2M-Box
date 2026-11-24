package com.mab.f2mbox.ui.list

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

@Composable
internal actual fun PlatformListPageBackHandler(
    routeKey: Any,
    onBack: () -> Unit,
    registerEsc: ((() -> Unit)?) -> Unit,
) {
    BackHandler(onBack = onBack)
}