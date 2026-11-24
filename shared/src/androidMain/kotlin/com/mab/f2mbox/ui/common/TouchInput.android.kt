package com.mab.f2mbox.ui.common

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

/**
 * Touch-first navigation on phones/tablets. TV devices keep D-pad behavior even
 * when they advertise [PackageManager.FEATURE_TOUCHSCREEN] (common on emulators).
 */
fun Context.resolveUsesTouchInput(): Boolean {
    if (!packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)) {
        return false
    }
    val uiMode = (getSystemService(Context.UI_MODE_SERVICE) as UiModeManager).currentModeType
    return uiMode != Configuration.UI_MODE_TYPE_TELEVISION
}