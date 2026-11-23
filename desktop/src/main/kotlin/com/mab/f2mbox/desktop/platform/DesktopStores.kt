package com.mab.f2mbox.desktop.platform

import com.mab.f2mbox.data.WatchedItem
import com.mab.f2mbox.scraper.BaseUrlResolver
import com.mab.f2mbox.storage.WatchedCodec
import com.mab.f2mbox.storage.WatchedStorage
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * [BaseUrlResolver.KeyValueStore] backed by a JSON map file. The
 * resolver hops to IO itself, but the file IO here stays on the IO
 * dispatcher as well, mirroring the Android SharedPreferences store.
 */
class FileKeyValueStore(private val file: Path) : BaseUrlResolver.KeyValueStore {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val serializer = MapSerializer(String.serializer(), String.serializer())
    private val mutex = Mutex()

    override suspend fun getString(key: String): String? =
        withContext(Dispatchers.IO) { read()[key] }

    override suspend fun putString(key: String, value: String) {
        withContext(Dispatchers.IO) {
            mutex.withLock { writeMutation { it + (key to value) } }
        }
    }

    override suspend fun remove(key: String) {
        withContext(Dispatchers.IO) {
            mutex.withLock { writeMutation { it - key } }
        }
    }

    private fun read(): Map<String, String> {
        if (!Files.exists(file)) return emptyMap()
        return try {
            json.decodeFromString(serializer, Files.readString(file))
        } catch (_: Exception) {
            // A corrupt state file must not brick the resolver.
            emptyMap()
        }
    }

    private fun writeMutation(mutation: (Map<String, String>) -> Map<String, String>) {
        file.parent?.let { Files.createDirectories(it) }
        val next = mutation(read())
        Files.writeString(file, json.encodeToString(serializer, next))
    }
}

/**
 * [WatchedStorage] backed by a JSON list file, routed through the
 * shared [WatchedCodec] so desktop and Android histories are shaped
 * identically.
 */
class DesktopWatchedStorage(private val file: Path) : WatchedStorage {

    override fun load(): List<WatchedItem> {
        if (!Files.exists(file)) return emptyList()
        return try {
            WatchedCodec.decode(Files.readString(file))
        } catch (_: Exception) {
            emptyList()
        }
    }

    override fun save(items: List<WatchedItem>) {
        file.parent?.let { Files.createDirectories(it) }
        Files.writeString(file, WatchedCodec.encode(items))
    }
}

/** Where F2M Box keeps its desktop state (config + history). */
fun defaultAppSupportDir(): Path {
    val home = Path.of(System.getProperty("user.home"))
    val os = System.getProperty("os.name").lowercase()
    val base = when {
        os.contains("mac") || os.contains("darwin") ->
            home.resolve("Library/Application Support")
        os.contains("windows") ->
            System.getenv("APPDATA")?.let { Path.of(it) } ?: home.resolve("AppData/Roaming")
        else -> // Linux/BSD: XDG data home with the historical default
            System.getenv("XDG_DATA_HOME")?.let { Path.of(it) }
                ?: home.resolve(".local/share")
    }
    return base.resolve("F2MBox")
}

/**
 * The two shared-module stores wired for desktop. The resolver state
 * and the watch history live side by side under the app-support dir.
 */
fun desktopStores(baseDir: Path = defaultAppSupportDir()):
    Pair<BaseUrlResolver.KeyValueStore, WatchedStorage> =
    FileKeyValueStore(baseDir.resolve("scraper-state.json")) to
        DesktopWatchedStorage(baseDir.resolve("watched.json"))
