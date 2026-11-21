package com.mab.f2mbox.scraper

import android.text.format.DateFormat
import java.util.ArrayDeque
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Tiny in-memory log shared by the scraper engine and the connect
 * screen: every engine event (config fetch, cached-url hits, canonical
 * hops, health checks) is buffered with a timestamp so the UI can show
 * live progress instead of a static hint. Everything is mirrored to
 * logcat under `F2MScraper`.
 */
object ScraperLog {

    private const val MAX_ENTRIES = 80

    private val buffer = ArrayDeque<String>()

    private val _lines = MutableStateFlow<List<String>>(emptyList())

    /** Newest last; UI shows the tail of this list. */
    val lines: StateFlow<List<String>> = _lines

    /** Appends [message] to the buffer and logcat. */
    fun log(message: String) {
        val now = System.currentTimeMillis()
        val stamp = DateFormat.format("HH:mm:ss", now).toString()
        val line = "$stamp  $message"
        android.util.Log.d("F2MScraper", message)
        synchronized(buffer) {
            buffer.addLast(line)
            while (buffer.size > MAX_ENTRIES) buffer.removeFirst()
            _lines.value = buffer.toList()
        }
    }

    /** Drops the buffer; called when a new connect attempt starts. */
    fun clear() {
        synchronized(buffer) {
            buffer.clear()
            _lines.value = emptyList()
        }
    }
}
