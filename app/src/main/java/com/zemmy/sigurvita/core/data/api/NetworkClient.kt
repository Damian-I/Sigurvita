package com.zemmy.sigurvita.core.data.api

import com.zemmy.sigurvita.core.datastore.UserPreferencesRepository
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object NetworkClient {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    @Volatile
    private var cachedClient: OkHttpClient? = null

    @Volatile
    private var cachedApi: KavitaApi? = null

    /**
     * Returns a singleton OkHttpClient. OkHttp recommends sharing a single client
     * to reuse connection pools, dispatcher threads, and internal caches.
     */
    fun getOkHttpClient(userPreferencesRepository: UserPreferencesRepository): OkHttpClient {
        return cachedClient ?: synchronized(this) {
            cachedClient ?: run {
                val logging = HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                }

                OkHttpClient.Builder()
                    .addInterceptor(AuthInterceptor(userPreferencesRepository))
                    .addInterceptor(logging)
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build().also { cachedClient = it }
            }
        }
    }

    /**
     * Returns a singleton KavitaApi backed by the shared OkHttpClient.
     * Safe to call from any ViewModel — no new threads or connection pools are created.
     */
    fun getKavitaApi(userPreferencesRepository: UserPreferencesRepository): KavitaApi {
        return cachedApi ?: synchronized(this) {
            cachedApi ?: run {
                Retrofit.Builder()
                    .baseUrl("http://localhost/")
                    .client(getOkHttpClient(userPreferencesRepository))
                    .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                    .build()
                    .create(KavitaApi::class.java).also { cachedApi = it }
            }
        }
    }
}
