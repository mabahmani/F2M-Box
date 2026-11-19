package com.mab.f2mbox.data.remote

import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

interface F2MApi {

    /** Server liveness probe used by the connect screen. */
    @retrofit2.http.GET("health")
    suspend fun health(): Envelope<HealthData>

    /** Homepage payload: hero slider, carousels, navigation, … */
    @retrofit2.http.GET("home")
    suspend fun home(): Envelope<HomeData>

    /**
     * Scrapes any url. The envelope's [Envelope.type] selects the payload
     * shape ("list" for listing pages, "movie" for detail pages, …); the
     * data field stays a raw [JsonElement] here and the repository decodes
     * it into the matching model.
     */
    @retrofit2.http.GET("scrape")
    suspend fun scrape(@retrofit2.http.Query("url") url: String): Envelope<JsonElement>

    /**
     * Searches titles by scraping the site's results page (source=html),
     * which returns the same "list" payload as [scrape], so the result
     * renders in the listing grid with its pagination. The json
     * quick-search source (type "search") is not modeled yet.
     */
    @retrofit2.http.GET("search")
    suspend fun search(
        @retrofit2.http.Query("q") query: String,
        @retrofit2.http.Query("source") source: String = "html",
        @retrofit2.http.Query("pages") pages: Int = 1,
    ): Envelope<ListData>
}

object Api {

    const val BASE_URL = "https://f2m-scrapper.onrender.com/api/v1/"

    internal val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Generous timeouts: the backend runs on a free tier that can take a
     * while to answer while its instance wakes up. The home endpoint has
     * been observed taking up to ~2 minutes on a cold start.
     */
    private val okhttp = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(150, TimeUnit.SECONDS)
        .callTimeout(180, TimeUnit.SECONDS)
        .addInterceptor(InMemoryCacheInterceptor())
        .build()

    val client: F2MApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okhttp)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(F2MApi::class.java)
    }
}

/**
 * An OkHttp interceptor that caches successful GET responses in-memory
 * for the lifetime of the application process (until a new process launch).
 */
private class InMemoryCacheInterceptor : okhttp3.Interceptor {
    companion object {
        private val cache = ConcurrentHashMap<String, CachedResponse>()

        fun clear() {
            cache.clear()
        }
    }

    private class CachedResponse(
        val code: Int,
        val message: String,
        val bodyBytes: ByteArray,
        val headers: okhttp3.Headers
    )

    override fun intercept(chain: okhttp3.Interceptor.Chain): okhttp3.Response {
        val request = chain.request()
        val key = request.url.toString()

        if (request.method == "GET") {
            cache[key]?.let { cached ->
                val body = cached.bodyBytes.toResponseBody(
                    cached.headers.get("Content-Type")?.toMediaType()
                )
                return okhttp3.Response.Builder()
                    .request(request)
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(cached.code)
                    .message(cached.message)
                    .headers(cached.headers)
                    .body(body)
                    .build()
            }
        }

        val response = chain.proceed(request)
        if (request.method == "GET" && response.isSuccessful) {
            val bodyBytes = response.body?.bytes() ?: byteArrayOf()
            val contentType = response.body?.contentType()
            val newBody = bodyBytes.toResponseBody(contentType)

            cache[key] = CachedResponse(
                code = response.code,
                message = response.message,
                bodyBytes = bodyBytes,
                headers = response.headers
            )

            return response.newBuilder()
                .body(newBody)
                .build()
        }
        return response
    }
}

/** Clears the in-memory Retrofit/OkHttp response cache. */
fun clearNetworkCache() {
    InMemoryCacheInterceptor.clear()
}
