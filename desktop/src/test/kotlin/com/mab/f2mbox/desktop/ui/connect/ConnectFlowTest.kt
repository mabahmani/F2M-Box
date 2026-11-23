package com.mab.f2mbox.desktop.ui.connect

import com.mab.f2mbox.data.remote.HomeData
import com.mab.f2mbox.data.remote.HeroSlide
import com.mab.f2mbox.desktop.platform.Strings
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the two decision points of the desktop connect gate: a parsed
 * home page without a hero slider is NOT healthy (parked/error pages
 * parse but are not the real site), and errors classify into the same
 * user-facing messages as the Android build.
 */
class ConnectFlowTest {

    @Test
    fun `home without hero slider is unhealthy — parked or error page`() {
        assertFalse(isHomeHealthy(HomeData(title = "error page", heroSlider = emptyList())))
        assertTrue(
            isHomeHealthy(
                HomeData(
                    title = "F2M",
                    heroSlider = listOf(HeroSlide(title = "فیلم", url = "/m/1")),
                )
            )
        )
    }

    @Test
    fun `errors map to the same messages as the android build`() {
        assertEquals(Strings.errorNoInternet, describe(UnknownHostException()))
        assertEquals(Strings.errorTimeout, describe(SocketTimeoutException()))
        assertEquals(Strings.errorConnect, describe(ConnectException()))
        assertEquals(Strings.errorServer, describe(java.io.IOException()))
        assertEquals(Strings.errorUnexpectedResponse, describe(SerializationException("bad json")))
        assertEquals(Strings.errorUnknown, describe(IllegalStateException("boom")))
        assertEquals(Strings.errorUnknown, describe(null))
    }
}
