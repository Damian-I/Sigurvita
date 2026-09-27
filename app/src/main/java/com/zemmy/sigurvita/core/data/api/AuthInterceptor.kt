package com.zemmy.sigurvita.core.data.api

import com.zemmy.sigurvita.core.datastore.UserPreferencesRepository
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(
    private val userPreferencesRepository: UserPreferencesRepository
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val serverConfig = userPreferencesRepository.currentServerConfig

        val requestBuilder = originalRequest.newBuilder()

        if (serverConfig.jwtToken.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer ${serverConfig.jwtToken}")
        } else if (serverConfig.apiKey.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer ${serverConfig.apiKey}")
        }

        val originalUrl = originalRequest.url
        if (serverConfig.serverUrl.isNotBlank()) {
            val newBaseUrl = serverConfig.serverUrl.toHttpUrlOrNull()
            if (newBaseUrl != null) {
                val newUrl = originalUrl.newBuilder()
                    .scheme(newBaseUrl.scheme)
                    .host(newBaseUrl.host)
                    .port(newBaseUrl.port)
                    .build()
                requestBuilder.url(newUrl)
            }
        }

        return chain.proceed(requestBuilder.build())
    }
}
