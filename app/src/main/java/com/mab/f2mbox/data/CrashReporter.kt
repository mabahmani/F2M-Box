package com.mab.f2mbox.data

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Catches uncaught exceptions and keeps the last one on disk so the logs
 * screen can show what killed the previous session — on a TV there is no
 * logcat to look at.
 *
 * The handler writes synchronously (a crash has no coroutines) and then
 * delegates to the system handler, so the process still dies normally.
 * Only the most recent crash is kept.
 */
object CrashReporter {

    private const val FILE_NAME = "last_crash.txt"

    /** Installs the handler; call once from `Activity.onCreate`. */
    fun install(context: Context) {
        val systemHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                    .format(Date())
                File(context.filesDir, FILE_NAME).writeText(
                    "$stamp  thread=${thread.name}\n${Log.getStackTraceString(throwable)}",
                )
            }
            // Let the system crash normally (dialog, process teardown).
            systemHandler?.uncaughtException(thread, throwable)
        }
    }

    /** The last crash of a previous session, or null. */
    fun pending(context: Context): String? = runCatching {
        File(context.filesDir, FILE_NAME)
            .takeIf { it.isFile }
            ?.readText()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }.getOrNull()

    /** Drops the stored crash report. */
    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE_NAME).delete() }
    }
}
