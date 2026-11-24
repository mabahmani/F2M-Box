package com.mab.f2mbox.desktop.ui.connect

import com.mab.f2mbox.connect.ConnectSplashStrings
import com.mab.f2mbox.desktop.platform.Strings

fun desktopConnectSplashStrings(): ConnectSplashStrings = ConnectSplashStrings(
    appName = Strings.appName,
    back = Strings.back,
    retry = Strings.retry,
    viewLogs = Strings.viewLogs,
    domainHint = Strings.errorDomainHint,
    domainFieldPlaceholder = Strings.errorDomainField,
    domainSubmit = Strings.errorDomainSubmit,
    domainInvalidLabel = Strings.errorDomainInvalid,
)