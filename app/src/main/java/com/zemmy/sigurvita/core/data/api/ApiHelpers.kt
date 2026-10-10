package com.zemmy.sigurvita.core.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import retrofit2.Response

/**
 * Standardized coroutine wrapper for Retrofit API calls.
 * Handles Dispatchers.IO context switching, response success validation, body extraction,
 * and converts exceptions into standard [Result] instances.
 */
suspend inline fun <T, R> safeApiCall(
    crossinline call: suspend () -> Response<T>,
    crossinline transform: (T) -> R
): Result<R> = withContext(Dispatchers.IO) {
    try {
        val response = call()
        val body = response.body()
        if (response.isSuccessful && body != null) {
            Result.success(transform(body))
        } else {
            Result.failure(HttpException(response))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}

/**
 * Convenience variant of [safeApiCall] when no transformation is required on response body.
 */
suspend inline fun <T> safeApiCall(
    crossinline call: suspend () -> Response<T>
): Result<T> = safeApiCall(call) { it }
