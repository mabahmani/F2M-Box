package com.mab.f2mbox.scraper

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [BaseUrlResolver.KeyValueStore] backed by SharedPreferences. All calls
 * hop to the IO dispatcher since the resolver runs off the main thread.
 */
class SharedPreferencesStore(context: Context) : BaseUrlResolver.KeyValueStore {

    private val prefs =
        context.getSharedPreferences("scraper", Context.MODE_PRIVATE)

    override suspend fun getString(key: String): String? =
        withContext(Dispatchers.IO) { prefs.getString(key, null) }

    override suspend fun putString(key: String, value: String) =
        withContext(Dispatchers.IO) {
            prefs.edit().putString(key, value).apply()
        }

    override suspend fun remove(key: String) =
        withContext(Dispatchers.IO) {
            prefs.edit().remove(key).apply()
        }
}
