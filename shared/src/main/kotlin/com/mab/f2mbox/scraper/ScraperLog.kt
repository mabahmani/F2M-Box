package com.mab.f2mbox.scraper

import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Tiny in-memory log shared by the scraper engine and the connect
 * screen: every engine event (config fetch, cached-url hits, canonical
 * hops, health checks) is buffered with a timestamp so the UI can show
 * live progress instead of a static hint.
 */
object ScraperLog {

    private const val MAX_ENTRIES = 80

    private val buffer = ArrayDeque<String>()

    private val stampFormat =
        SimpleDateFormat("HH:mm:ss", Locale.US)

    private val _lines = MutableStateFlow<List<String>>(emptyList())

    /** Newest last; UI shows the tail of this list. */
    val lines: StateFlow<List<String>> = _lines

    /** Appends [message] to the buffer. */
    fun log(message: String) {
        synchronized(buffer) {
            val stamp = stampFormat.format(Date(System.currentTimeMillis()))
            buffer.addLast("$stamp  $message")
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
