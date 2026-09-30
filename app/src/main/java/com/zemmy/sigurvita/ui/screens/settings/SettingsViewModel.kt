package com.zemmy.sigurvita.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zemmy.sigurvita.SigurvitaApp
import com.zemmy.sigurvita.core.model.AutoDeleteOption
import com.zemmy.sigurvita.core.model.DownloadFormat
import com.zemmy.sigurvita.core.model.DownloadLocation
import com.zemmy.sigurvita.core.model.FitMode
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

    fun updateFitMode(mode: FitMode) {
        viewModelScope.launch {
            preferencesRepository.updateFitMode(mode)
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

    fun updateDownloadLocation(location: DownloadLocation) {
        viewModelScope.launch {
            preferencesRepository.updateDownloadLocation(location)
        }
    }

    fun updateDownloadFormat(format: DownloadFormat) {
        viewModelScope.launch {
            preferencesRepository.updateDownloadFormat(format)
        }
    }

    fun updateDownloadPath(path: String) {
        viewModelScope.launch {
            preferencesRepository.updateDownloadPath(path)
        }
    }

    fun updateAutoDeleteOption(option: AutoDeleteOption) {
        viewModelScope.launch {
            preferencesRepository.updateAutoDeleteOption(option)
        }
    }

    fun logout() {
        viewModelScope.launch {
            preferencesRepository.clearServerConfig()
        }
    }
}
