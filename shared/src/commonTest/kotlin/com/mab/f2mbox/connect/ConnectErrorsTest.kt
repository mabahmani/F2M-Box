package com.mab.f2mbox.connect

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.SerializationException

class ConnectErrorsTest {
    @Test
    fun describe_mapsThrowablesToCopy() {
        assertEquals(ConnectErrorText.noInternet, describeConnectError(UnknownHostException()))
        assertEquals(ConnectErrorText.timeout, describeConnectError(SocketTimeoutException()))
        assertEquals(ConnectErrorText.connect, describeConnectError(ConnectException()))
        assertEquals(ConnectErrorText.server, describeConnectError(java.io.IOException()))
        assertEquals(
            ConnectErrorText.unexpectedResponse,
            describeConnectError(SerializationException("bad json")),
        )
        assertEquals(ConnectErrorText.unknown, describeConnectError(IllegalStateException("boom")))
        assertEquals(ConnectErrorText.unknown, describeConnectError(null))
    }
}