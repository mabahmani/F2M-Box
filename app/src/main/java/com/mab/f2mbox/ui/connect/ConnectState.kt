package com.mab.f2mbox.ui.connect

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException

/** Where the app stands while reaching the F2M Scrapper backend. */
sealed interface ConnectState {
    data object Connecting : ConnectState
    data object Ready : ConnectState
    data class Failed(val message: String) : ConnectState
}

/** Turns network/serialization failures into plain, helpful text. */
internal fun describe(error: Throwable?): String = when (error) {
    is UnknownHostException -> "No internet connection. Check your network and try again."
    is SocketTimeoutException -> "The server took too long to respond. It may be waking up — try again."
    is ConnectException -> "Could not connect to the F2M Box server."
    is HttpException -> "Server error (${error.code()}). Please try again."
    is SerializationException -> "Received an unexpected response from the server."
    else -> error?.message ?: "Something went wrong. Please try again."
}
