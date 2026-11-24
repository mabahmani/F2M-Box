package com.mab.f2mbox.ui.logs

import android.content.Context
import com.mab.f2mbox.data.CrashReporter

fun androidSessionCrashStore(context: Context): SessionCrashStore = object : SessionCrashStore {
    override fun pending(): String? = CrashReporter.pending(context)
    override fun clear() = CrashReporter.clear(context)
}