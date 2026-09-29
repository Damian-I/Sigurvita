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

    fun createOkHttpClient(userPreferencesRepository: UserPreferencesRepository): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        return OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(userPreferencesRepository))
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    fun createKavitaApi(userPreferencesRepository: UserPreferencesRepository): KavitaApi {
        return Retrofit.Builder()
            .baseUrl("http://localhost/")
            .client(createOkHttpClient(userPreferencesRepository))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(KavitaApi::class.java)
    }
}
