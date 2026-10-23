package com.zemmy.sigurvita.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zemmy.sigurvita.SigurvitaApp
import com.zemmy.sigurvita.core.model.AutoDeleteOption
import com.zemmy.sigurvita.core.model.FitMode
import com.zemmy.sigurvita.core.model.ReaderSettings
import com.zemmy.sigurvita.core.model.ReadingMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.zemmy.sigurvita.core.model.ReaderBackground
import com.zemmy.sigurvita.core.model.ReaderOrientation
class SettingsViewModel : ViewModel() {

    private val app = SigurvitaApp.instance
    private val preferencesRepository = app.userPreferencesRepository

    val settings: StateFlow<ReaderSettings> = preferencesRepository.readerSettingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ReaderSettings()
    )

    fun updateReadingMode(mode: ReadingMode) {
        viewModelScope.launch {
            preferencesRepository.updateReadingMode(mode)
        }
    }

    fun updateFitMode(mode: FitMode) {
        viewModelScope.launch {
            preferencesRepository.updateFitMode(mode)
        }
    }


    fun updateReaderBackground(background: ReaderBackground) {
        viewModelScope.launch {
            preferencesRepository.updateReaderBackground(background)
        }
    }

    fun updateOrientation(orientation: ReaderOrientation) {
        viewModelScope.launch {
            preferencesRepository.updateReaderOrientation(orientation)
        }
    }

    fun updateShowPageNumber(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateShowPageNumber(enabled)
        }
    }

    fun updateVolumeKeyNav(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateVolumeKeyNavigation(enabled)
        }
    }

    fun updateVolumeKeyInvert(invert: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateVolumeKeyInvert(invert)
        }
    }

    fun updateTouchEdgeNav(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateTouchEdgeNavigation(enabled)
        }
    }

    fun updateKeepScreenOn(keep: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateKeepScreenOn(keep)
        }
    }

    fun updateSpeechBubbleDetectionEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateSpeechBubbleDetectionEnabled(enabled)
        }
    }

    fun updateSpeechBubbleBoxesEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateSpeechBubbleBoxesEnabled(enabled)
        }
    }

    fun updateSpeechBubbleConfidence(confidence: Float) {
        viewModelScope.launch {
            preferencesRepository.updateSpeechBubbleConfidence(confidence)
        }
    }

    fun updatePanelAwareOrder(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updatePanelAwareOrder(enabled)
        }
    }

    fun updatePanelDebugOverlay(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updatePanelDebugOverlay(enabled)
        }
    }

    fun updateAutoDeleteOption(option: AutoDeleteOption) {
        viewModelScope.launch {
            preferencesRepository.updateAutoDeleteOption(option)
        }
    }

    fun updateColorScapeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateColorScapeEnabled(enabled)
        }
    }
    fun logout() {
        viewModelScope.launch {
            preferencesRepository.clearServerConfig()
            app.colorScapeRepository.clear()
        }
    }

    fun refreshLogin(onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val config = preferencesRepository.serverConfigFlow.first()
            if (config.serverUrl.isBlank() || config.apiKey.isBlank()) {
                onResult(false, "No active server connection configured.")
                return@launch
            }
            val result = app.authRepository.testConnectionAndSave(config.serverUrl, config.apiKey)
            if (result.isSuccess) {
                onResult(true, null)
            } else {
                onResult(false, result.exceptionOrNull()?.message ?: "Failed to refresh login")
            }
        }
    }
}
