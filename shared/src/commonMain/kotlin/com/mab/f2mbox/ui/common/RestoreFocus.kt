package com.mab.f2mbox.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/** Runs [block] on enter only when D-pad focus restoration is appropriate (TV). */
@Composable
fun RestoreFocusOnEnter(key: Any? = Unit, block: suspend () -> Unit) {
    if (LocalUsesTouchInput.current) return
    LaunchedEffect(key) {
        block()
    }
}

/** Requests focus once on enter for TV; touch devices leave focus alone. */
@Composable
fun RequestInitialFocusOnEnter(requestFocus: () -> Boolean) {
    if (LocalUsesTouchInput.current) return
    LaunchedEffect(Unit) {
        requestFocus()
    }
}