package com.zemmy.sigurvita.core.data.api

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Set when the server rejected the stored session and it could not be renewed in the background.
 * The UI shows a sign-in prompt while [sessionExpired] is true; cached content stays usable.
 */
object SessionEvents {
    private val _sessionExpired = MutableStateFlow(false)
    val sessionExpired: StateFlow<Boolean> = _sessionExpired.asStateFlow()

    fun markExpired() {
        _sessionExpired.value = true
    }

    fun clear() {
        _sessionExpired.value = false
    }
}
