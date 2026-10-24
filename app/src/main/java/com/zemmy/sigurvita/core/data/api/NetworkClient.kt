package com.zemmy.sigurvita.core.data.api

import android.util.Log
import android.content.pm.ApplicationInfo
import com.zemmy.sigurvita.SigurvitaApp
import com.zemmy.sigurvita.core.datastore.UserPreferencesRepository
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object NetworkClient {
    /**
     * Placeholder host used when building requests. AuthInterceptor rewrites scheme, host and port
     * to the configured Kavita server, so nothing is ever sent to this address.
     */
    const val PLACEHOLDER_BASE_URL = "http://localhost/"
    private val API_KEY_PARAM = Regex("""apiKey=[^&\s]*""", RegexOption.IGNORE_CASE)

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
                // Request lines include query strings, and several URLs carry `apiKey=...`
                // (page images, covers). Mask it so the key never reaches Logcat.
                val logging = HttpLoggingInterceptor { message ->
                    Log.i("okhttp.OkHttpClient", API_KEY_PARAM.replace(message, "apiKey=***"))
                }.apply {
                    val debuggable = (SigurvitaApp.instance.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
                    level = if (debuggable) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
                }

                OkHttpClient.Builder()
                    .addInterceptor(AuthInterceptor(userPreferencesRepository))
                    .addInterceptor(logging)
                    .authenticator(TokenAuthenticator(userPreferencesRepository))
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
                    .baseUrl(PLACEHOLDER_BASE_URL)
                    .client(getOkHttpClient(userPreferencesRepository))
                    .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                    .build()
                    .create(KavitaApi::class.java).also { cachedApi = it }
            }
        }
    }
}
