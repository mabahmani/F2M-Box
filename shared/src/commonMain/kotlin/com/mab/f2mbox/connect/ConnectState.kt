package com.mab.f2mbox.connect

/** Where the app stands while reaching the F2M site. */
sealed interface ConnectState {
    data object Connecting : ConnectState
    data object Ready : ConnectState
    data class Failed(val message: String) : ConnectState
}