package com.mab.f2mbox.connect

/** Copy for the connect splash and manual-domain fallback. */
data class ConnectSplashStrings(
    val appName: String,
    val back: String,
    val retry: String,
    val viewLogs: String,
    val domainHint: String,
    val domainFieldPlaceholder: String,
    val domainSubmit: String,
    val domainInvalidLabel: String,
)

/** Defaults aligned with `app/src/main/res/values/strings.xml`. */
object ConnectSplashText {
    const val appName = "F2M Box"
    const val back = "Back"
    const val retry = "Retry"
    const val viewLogs = "Logs"
    const val domainHint =
        "Know the new site address? Enter it below (without https://).\u200E"
    const val domainFieldPlaceholder = "example.com"
    const val domainSubmit = "Connect"
    const val domainInvalidLabel = ConnectErrorText.domainInvalid
}

fun defaultConnectSplashStrings(): ConnectSplashStrings = ConnectSplashStrings(
    appName = ConnectSplashText.appName,
    back = ConnectSplashText.back,
    retry = ConnectSplashText.retry,
    viewLogs = ConnectSplashText.viewLogs,
    domainHint = ConnectSplashText.domainHint,
    domainFieldPlaceholder = ConnectSplashText.domainFieldPlaceholder,
    domainSubmit = ConnectSplashText.domainSubmit,
    domainInvalidLabel = ConnectSplashText.domainInvalidLabel,
)