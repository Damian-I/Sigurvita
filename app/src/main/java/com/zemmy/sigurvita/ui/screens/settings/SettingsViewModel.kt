package com.zemmy.sigurvita.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zemmy.sigurvita.SigurvitaApp
import com.zemmy.sigurvita.core.model.ReaderSettings
import com.zemmy.sigurvita.core.model.ReadingMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    fun updateKeepScreenOn(keep: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateKeepScreenOn(keep)
        }
    }

    fun logout() {
        viewModelScope.launch {
            preferencesRepository.clearServerConfig()
        }
    }
}
