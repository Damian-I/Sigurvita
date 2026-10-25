package com.zemmy.sigurvita.core.data.repository

import com.zemmy.sigurvita.core.data.api.KavitaApi
import com.zemmy.sigurvita.core.datastore.UserPreferencesRepository
import com.zemmy.sigurvita.core.data.api.SessionEvents
import com.zemmy.sigurvita.core.model.ServerConfig
import com.zemmy.sigurvita.core.util.HttpFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class AuthRepository(
    private val api: KavitaApi,
    private val preferencesRepository: UserPreferencesRepository
) {
    suspend fun testConnectionAndSave(serverUrl: String, apiKey: String): Result<Boolean> = withContext(Dispatchers.IO) {
        // The request interceptor finds the server in the stored config, so the new config has to be saved
        // before it can be tested. If the test fails, the previous config is put back (see the finally block).
        val previous = preferencesRepository.getServerConfigSync()
        var succeeded = false
        try {
            val formattedUrl = serverUrl.trim().removeSuffix("/")
            preferencesRepository.saveServerConfig(serverUrl = formattedUrl, apiKey = apiKey)

            // Try plugin authentication
            val authRes = api.authenticatePlugin(apiKey = apiKey)
            if (authRes.isSuccessful && authRes.body()?.token != null) {
                val token = authRes.body()!!.token!!
                preferencesRepository.saveServerConfig(
                    serverUrl = formattedUrl,
                    apiKey = apiKey,
                    jwtToken = token
                )
                SessionEvents.clear()
                succeeded = true
                Result.success(true)
            } else {
                val accountRes = api.getAccount()
                if (accountRes.isSuccessful && accountRes.body() != null) {
                    val user = accountRes.body()!!
                    preferencesRepository.saveServerConfig(
                        serverUrl = formattedUrl,
                        apiKey = apiKey,
                        userId = user.id
                    )
                    SessionEvents.clear()
                    succeeded = true
                    Result.success(true)
                } else {
                    Result.failure(
                        HttpFailure(authRes.code(), "Failed to authenticate with Kavita server (HTTP ${authRes.code()})")
                    )
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            if (!succeeded) {
                // Failed or cancelled: do not leave an untested server (or a wiped session) behind
                withContext(NonCancellable) { restoreConfig(previous) }
            }
        }
    }

    private suspend fun restoreConfig(previous: ServerConfig) {
        if (previous.isLoggedIn) {
            preferencesRepository.saveServerConfig(
                serverUrl = previous.serverUrl,
                apiKey = previous.apiKey,
                jwtToken = previous.jwtToken,
                userId = previous.userId
            )
        } else {
            preferencesRepository.clearServerConfig()
        }
    }
}
