package com.zemmy.sigurvita.core.data.repository

import com.zemmy.sigurvita.core.data.api.KavitaApi
import com.zemmy.sigurvita.core.datastore.UserPreferencesRepository
import com.zemmy.sigurvita.core.data.api.SessionEvents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuthRepository(
    private val api: KavitaApi,
    private val preferencesRepository: UserPreferencesRepository
) {
    suspend fun testConnectionAndSave(serverUrl: String, apiKey: String): Result<Boolean> = withContext(Dispatchers.IO) {
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
                    Result.success(true)
                } else {
                    Result.failure(Exception("Failed to authenticate with Kavita server (HTTP ${authRes.code()})"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
