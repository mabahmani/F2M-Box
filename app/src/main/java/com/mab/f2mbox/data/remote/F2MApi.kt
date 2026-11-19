package com.mab.f2mbox.data.remote

import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

interface F2MApi {

    /** Server liveness probe used by the connect screen. */
    @retrofit2.http.GET("health")
    suspend fun health(): Envelope<HealthData>

    /** Homepage payload: hero slider, carousels, navigation, … */
    @retrofit2.http.GET("home")
    suspend fun home(): Envelope<HomeData>
}

object Api {

    const val BASE_URL = "https://f2m-scrapper.onrender.com/api/v1/"

    private val json = Json {
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
