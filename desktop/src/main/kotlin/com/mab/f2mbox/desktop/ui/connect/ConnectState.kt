package com.mab.f2mbox.desktop.ui.connect

import com.mab.f2mbox.data.remote.HomeData
import com.mab.f2mbox.desktop.platform.Strings
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.serialization.SerializationException

/** Where the app stands while reaching the F2M site. */
sealed interface ConnectState {
    data object Connecting : ConnectState
    data object Ready : ConnectState
    data class Failed(val message: String) : ConnectState
}

/**
 * A parsed home page is healthy only when its hero slider came through:
 * parked/error pages can parse cleanly but are not the real site.
 */
internal fun isHomeHealthy(home: HomeData): Boolean = home.heroSlider.isNotEmpty()

/**
 * Turns network/serialization failures into a user-facing message,
 * classifying exactly like the Android build's `describe`.
 */
internal fun describe(error: Throwable?): String = when (error) {
    is UnknownHostException -> Strings.errorNoInternet
    is SocketTimeoutException -> Strings.errorTimeout
    is ConnectException -> Strings.errorConnect
    is java.io.IOException -> Strings.errorServer
    is SerializationException -> Strings.errorUnexpectedResponse
    else -> Strings.errorUnknown
}
