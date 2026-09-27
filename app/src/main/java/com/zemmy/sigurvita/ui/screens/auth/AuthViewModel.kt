package com.zemmy.sigurvita.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zemmy.sigurvita.SigurvitaApp
import com.zemmy.sigurvita.core.data.api.NetworkClient
import com.zemmy.sigurvita.core.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class AuthUiState(
    val serverUrl: String = "",
    val apiKey: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isConnected: Boolean = false
)

class AuthViewModel : ViewModel() {

    private val app = SigurvitaApp.instance
    private val preferencesRepository = app.userPreferencesRepository
    private val authRepository = AuthRepository(
        NetworkClient.createKavitaApi(preferencesRepository),
        preferencesRepository
    )

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val config = preferencesRepository.serverConfigFlow.first()
            _uiState.value = AuthUiState(
                serverUrl = config.serverUrl,
                apiKey = config.apiKey,
                isConnected = config.isLoggedIn
            )
        }
    }

    fun onServerUrlChanged(url: String) {
        _uiState.value = _uiState.value.copy(serverUrl = url, errorMessage = null)
    }

    fun onApiKeyChanged(key: String) {
        _uiState.value = _uiState.value.copy(apiKey = key, errorMessage = null)
    }

    fun connectServer() {
        val url = _uiState.value.serverUrl
        val key = _uiState.value.apiKey

        if (url.isBlank() || key.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Server URL and API Key are required.")
            return
        }

        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch {
            val result = authRepository.testConnectionAndSave(url, key)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(isLoading = false, isConnected = true)
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = result.exceptionOrNull()?.message ?: "Failed to connect"
                )
            }
        }
    }
}
