package com.mab.f2mbox.connect

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.serialization.SerializationException

/** Classified network / parse failures for user-facing copy. */
enum class ConnectFailureReason {
    NoInternet,
    Timeout,
    Connect,
    Server,
    UnexpectedResponse,
    Unknown,
    DomainInvalid,
}

/** User-facing connect error strings (aligned with `strings.xml`). */
object ConnectErrorText {
    const val noInternet =
        "No internet connection. Check your network and try again.\u200E"
    const val timeout =
        "The server took too long to respond. It may be waking up — try again.\u200E"
    const val connect = "Could not connect to the F2M Box server.\u200E"
    const val server = "Server error. Please try again.\u200E"
    const val unexpectedResponse =
        "Received an unexpected response from the server.\u200E"
    const val unknown = "Something went wrong. Please try again.\u200E"
    const val domainInvalid = "Enter a valid domain, e.g. example.com\u200E"
}

fun classifyConnectError(error: Throwable?): ConnectFailureReason = when (error) {
    is UnknownHostException -> ConnectFailureReason.NoInternet
    is SocketTimeoutException -> ConnectFailureReason.Timeout
    is ConnectException -> ConnectFailureReason.Connect
    is java.io.IOException -> ConnectFailureReason.Server
    is SerializationException -> ConnectFailureReason.UnexpectedResponse
    else -> ConnectFailureReason.Unknown
}

fun connectErrorMessage(reason: ConnectFailureReason): String = when (reason) {
    ConnectFailureReason.NoInternet -> ConnectErrorText.noInternet
    ConnectFailureReason.Timeout -> ConnectErrorText.timeout
    ConnectFailureReason.Connect -> ConnectErrorText.connect
    ConnectFailureReason.Server -> ConnectErrorText.server
    ConnectFailureReason.UnexpectedResponse -> ConnectErrorText.unexpectedResponse
    ConnectFailureReason.Unknown -> ConnectErrorText.unknown
    ConnectFailureReason.DomainInvalid -> ConnectErrorText.domainInvalid
}

/** Maps a throwable to localized connect error copy. */
fun describeConnectError(error: Throwable?): String =
    connectErrorMessage(classifyConnectError(error))