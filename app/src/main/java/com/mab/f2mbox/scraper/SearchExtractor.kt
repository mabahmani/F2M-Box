package com.mab.f2mbox.scraper

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Quick-search extraction — the port of `search.py`. The site's XHR
 * endpoint returns Meilisearch hits; this normalizes them into a small
 * list payload the UI can render.
 */
object SearchExtractor {

    /** One normalized search hit. */
    @Serializable
    data class SearchData(
        val query: String,
        val sort: String,
        val items: List<SearchItem> = emptyList(),
    )

    @Serializable
    data class SearchItem(
        @SerialName("post_id") val postId: String? = null,
        val title: String? = null,
        @SerialName("title_fa") val titleFa: String? = null,
        val url: String? = null,
        val poster: ImageRefUrl? = null,
        val year: Int? = null,
        val rating: Double? = null,
        val votes: Int? = null,
        @SerialName("status_text") val statusText: String? = null,
        val kind: String? = null,
        val formats: List<String> = emptyList(),
    )

    @Serializable
    data class ImageRefUrl(val url: String? = null)

    private val TITLE_HIGHLIGHT = Regex("""</?em>""")

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Strips Meilisearch `<em>` highlighting and collapses whitespace. */
    private fun cleanTitle(value: String?): String? {
        if (value.isNullOrEmpty()) return null
        val cleaned = TITLE_HIGHLIGHT.replace(value, "")
            .split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")
        return cleaned.ifEmpty { null }
    }

    /** Port of `_formats`. */
    private fun formats(hit: kotlinx.serialization.json.JsonObject): List<String> {
        fun truthy(key: String) = hit[key]?.booleanOrFalse() == true

        val result = mutableListOf<String>()
        if (truthy("is_dubbled")) result.add("دوبله فارسی")
        if (truthy("is_hardsub")) result.add("زیرنویس چسبیده")
        return result
    }

    private fun kotlinx.serialization.json.JsonElement.booleanOrFalse(): Boolean =
        runCatching { jsonPrimitive.content == "true" }.getOrDefault(false)

    /** Port of `extract_search_item`. */
    private fun extractItem(hit: kotlinx.serialization.json.JsonObject, baseUrl: String): SearchItem {
        fun str(key: String): String? = hit[key]?.let {
            runCatching { it.jsonPrimitive.content }.getOrNull()
        }
        val rawUrl = str("url").orEmpty()
        return SearchItem(
            postId = str("post_id"),
            title = cleanTitle(str("title")),
            titleFa = cleanTitle(str("title_fa")),
            url = HtmlUtils.absoluteUrl(baseUrl, rawUrl),
            poster = ImageRefUrl(url = HtmlUtils.absoluteUrl(baseUrl, str("image"))),
            year = str("year")?.toIntOrNull(),
            rating = str("rating")?.toDoubleOrNull(),
            votes = str("votes")?.toIntOrNull(),
            statusText = str("meta_data"),
            kind = if ("/series/" in rawUrl) "series" else "movie",
            formats = formats(hit),
        )
    }

    /** Port of `extract_search`: normalizes a list of hits or {"hits": [...]}. */
    fun extract(baseUrlsafe: String, query: String, payload: String, sort: String): SearchData {
        val root = runCatching { json.parseToJsonElement(payload) }.getOrNull()
        val hitsArray = when {
            root == null -> null
            runCatching { root.jsonObject }.isSuccess -> {
                root.jsonObject["hits"]?.let { runCatching { it.jsonArray }.getOrNull() }
            }
            else -> runCatching { root.jsonArray }.getOrNull()
        }
        val items = hitsArray
            ?.mapNotNull { runCatching { it.jsonObject }.getOrNull() }
            ?.map { extractItem(it, baseUrlsafe) }
            ?: emptyList()
        return SearchData(query = query, sort = sort, items = items)
    }
}
