package com.mab.f2mbox.ui.connect

import androidx.annotation.StringRes
import com.mab.f2mbox.R
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException

/** Where the app stands while reaching the F2M Scrapper backend. */
sealed interface ConnectState {
    data object Connecting : ConnectState
    data object Ready : ConnectState
    data class Failed(@StringRes val messageRes: Int) : ConnectState
}

/**
 * Turns network/serialization failures into a localized message resource.
 * Resolve with [androidx.compose.ui.res.stringResource] in the UI layer.
 */
@StringRes
internal fun describe(error: Throwable?): Int = when (error) {
    is UnknownHostException -> R.string.error_no_internet
    is SocketTimeoutException -> R.string.error_timeout
    is ConnectException -> R.string.error_connect
    is HttpException -> R.string.error_server
    is SerializationException -> R.string.error_unexpected_response
    else -> R.string.error_unknown
}
