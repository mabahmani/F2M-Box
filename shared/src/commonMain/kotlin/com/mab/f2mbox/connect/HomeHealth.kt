package com.mab.f2mbox.connect

import com.mab.f2mbox.data.remote.HomeData

/**
 * A parsed home page is healthy only when its hero slider came through:
 * parked/error pages can parse cleanly but are not the real site.
 */
fun isHomeHealthy(home: HomeData): Boolean = home.heroSlider.isNotEmpty()