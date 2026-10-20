package com.zemmy.sigurvita.core.data.api

import com.zemmy.sigurvita.core.datastore.UserPreferencesRepository
import com.zemmy.sigurvita.core.model.ServerConfig
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Authenticator
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Called by OkHttp when the server answers HTTP 401 (Unauthorized: the credentials sent are missing,
 * invalid or expired). The app trusts the stored session at startup and never blocks on a login call;
 * this only runs when the server actually rejects a request.
 *
 * 1. Gets a new JWT with the stored API key (POST /api/Plugin/authenticate) and retries the request once.
 * 2. If the server rejects the API key as well, marks the session as expired so the UI can ask the user to
 *    sign in again. A network failure is NOT treated as an expired session.
 */
class TokenAuthenticator(
    private val userPreferencesRepository: UserPreferencesRepository
) : Authenticator {

    private sealed interface RefreshResult {
        data class Token(val token: String) : RefreshResult
        data object Rejected : RefreshResult
        data object NetworkError : RefreshResult
    }

    // Plain client: no interceptors and no authenticator, so a failing refresh cannot loop
    private val refreshClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val lock = Any()

    @Volatile
    private var lastRejectedAt = 0L

    override fun authenticate(route: Route?, response: Response): Request? {
        val request = response.request

        // Never try to renew the login call itself (a wrong API key on the sign-in screen is not an expiry)
        if (request.url.encodedPath.endsWith("/api/Plugin/authenticate")) return null
        // One retry only
        if (responseCount(response) > 1) return null

        synchronized(lock) {
            val config = userPreferencesRepository.getServerConfigSync()
            if (!config.isLoggedIn) return null

            val usedToken = request.header("Authorization")?.removePrefix("Bearer ")?.trim()

            // Another request already renewed the token while this one waited for the lock
            if (config.jwtToken.isNotBlank() && config.jwtToken != usedToken) {
                return request.newBuilder()
                    .header("Authorization", "Bearer ${config.jwtToken}")
                    .build()
            }

            // The server rejected the API key a moment ago: do not hammer it
            if (System.currentTimeMillis() - lastRejectedAt < REJECT_COOLDOWN_MS) return null

            if (config.apiKey.isBlank()) {
                markRejected()
                return null
            }

            return when (val result = fetchToken(config)) {
                is RefreshResult.Token -> {
                    runBlocking {
                        userPreferencesRepository.saveServerConfig(
                            serverUrl = config.serverUrl,
                            apiKey = config.apiKey,
                            jwtToken = result.token,
                            userId = config.userId
                        )
                    }
                    request.newBuilder()
                        .header("Authorization", "Bearer ${result.token}")
                        .build()
                }
                RefreshResult.Rejected -> {
                    markRejected()
                    null
                }
                RefreshResult.NetworkError -> null
            }
        }
    }

    private fun markRejected() {
        lastRejectedAt = System.currentTimeMillis()
        SessionEvents.markExpired()
    }

    private fun fetchToken(config: ServerConfig): RefreshResult {
        val base = config.serverUrl.toHttpUrlOrNull() ?: return RefreshResult.Rejected
        val url = base.newBuilder()
            .addPathSegments("api/Plugin/authenticate")
            .addQueryParameter("apiKey", config.apiKey)
            .addQueryParameter("pluginName", "Sigurvita")
            .build()
        val request = Request.Builder()
            .url(url)
            .post("".toRequestBody(null))
            .build()

        return try {
            refreshClient.newCall(request).execute().use { res ->
                if (res.isSuccessful) {
                    val token = parseToken(res.body?.string())
                    if (token != null) RefreshResult.Token(token) else RefreshResult.Rejected
                } else if (res.code in 400..499) {
                    RefreshResult.Rejected
                } else {
                    // 5xx: the server is having trouble, which says nothing about the session
                    RefreshResult.NetworkError
                }
            }
        } catch (_: IOException) {
            RefreshResult.NetworkError
        }
    }

    private fun parseToken(body: String?): String? {
        if (body.isNullOrBlank()) return null
        return try {
            Json.parseToJsonElement(body).jsonObject["token"]?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    private companion object {
        const val REJECT_COOLDOWN_MS = 30_000L
    }
}
