package com.zemmy.sigurvita.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zemmy.sigurvita.SigurvitaApp
import com.zemmy.sigurvita.core.util.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
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
    private val authRepository = app.authRepository

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val config = preferencesRepository.serverConfigFlow.first()
            _uiState.update {
                it.copy(
                    serverUrl = config.serverUrl,
                    apiKey = config.apiKey,
                    isConnected = config.isLoggedIn
                )
            }
        }
    }

    fun onServerUrlChanged(url: String) {
        _uiState.update { it.copy(serverUrl = url, errorMessage = null) }
    }

    fun onApiKeyChanged(key: String) {
        _uiState.update { it.copy(apiKey = key, errorMessage = null) }
    }

    fun connectServer() {
        val url = _uiState.value.serverUrl
        val key = _uiState.value.apiKey

        if (url.isBlank() || key.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Server URL and API Key are required.") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            val result = authRepository.testConnectionAndSave(url, key)
            if (result.isSuccess) {
                _uiState.update { it.copy(isLoading = false, isConnected = true) }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = result.exceptionOrNull()?.toUserMessage() ?: "Failed to connect"
                    )
                }
            }
        }
    }
}
