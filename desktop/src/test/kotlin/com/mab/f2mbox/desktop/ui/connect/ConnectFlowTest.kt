package com.mab.f2mbox.desktop.ui.connect

import com.mab.f2mbox.connect.isHomeHealthy
import com.mab.f2mbox.data.remote.HomeData
import com.mab.f2mbox.data.remote.HeroSlide
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the desktop connect gate: a parsed home page without a hero slider
 * is not healthy. Error classification lives in `:shared`
 * ([com.mab.f2mbox.connect.ConnectErrorsTest]).
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
                ),
            ),
        )
    }
}