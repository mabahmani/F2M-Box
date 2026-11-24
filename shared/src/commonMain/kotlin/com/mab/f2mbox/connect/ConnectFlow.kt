package com.mab.f2mbox.connect

import com.mab.f2mbox.data.remote.HomeData
import com.mab.f2mbox.scraper.F2MScraper
import com.mab.f2mbox.scraper.ScraperLog
import kotlinx.coroutines.delay

object ConnectDefaults {
    const val MAX_AUTO_RETRIES = 3
    const val RETRY_DELAY_MS = 2_500L
}

/** Result of one connect-gate run. */
sealed interface ConnectOutcome {
    data class Ready(val home: HomeData, val domain: String?) : ConnectOutcome
    data class Failed(val message: String) : ConnectOutcome
}

/**
 * Verifies an optional manual domain, then retries until a healthy home page
 * is loaded or retries are exhausted. Logs through [ScraperLog].
 */
suspend fun connectFlow(
    scraper: F2MScraper,
    manualDomain: String?,
    attempt: Int = 0,
    maxAutoRetries: Int = ConnectDefaults.MAX_AUTO_RETRIES,
    retryDelayMs: Long = ConnectDefaults.RETRY_DELAY_MS,
): ConnectOutcome {
    ScraperLog.clear()
    ScraperLog.log("connect attempt $attempt starting")

    scraper.launchBootstrapRefresh()

    if (manualDomain != null) {
        val accepted = scraper.submitManualDomain(manualDomain)
        ScraperLog.log("connect: manual domain '$manualDomain' accepted=$accepted")
        if (!accepted) {
            return ConnectOutcome.Failed(ConnectErrorText.domainInvalid)
        }
    }

    var lastError: Throwable? = null
    for (i in 0 until maxAutoRetries) {
        if (i > 0) delay(retryDelayMs)
        scraper.home().fold(
            onSuccess = { home ->
                if (!isHomeHealthy(home)) {
                    ScraperLog.log(
                        "connect: attempt $i parsed a page with no hero slider → unhealthy",
                    )
                    lastError = IllegalStateException("home page has no hero slider")
                } else {
                    ScraperLog.log("connect: attempt $i succeeded ✓")
                    ScraperLog.log("connect: ready — hero slides: ${home.heroSlider.size}")
                    return ConnectOutcome.Ready(home, scraper.activeDomain())
                }
            },
            onFailure = { error ->
                ScraperLog.log("connect: attempt $i failed — ${error.message}")
                lastError = error
            },
        )
    }
    return ConnectOutcome.Failed(describeConnectError(lastError))
}