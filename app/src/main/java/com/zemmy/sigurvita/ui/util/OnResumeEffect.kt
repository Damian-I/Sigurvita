package com.zemmy.sigurvita.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.LifecycleResumeEffect

@Composable
fun OnResumeEffect(key: Any? = Unit, onResume: () -> Unit) {
    val current by rememberUpdatedState(onResume)
    LifecycleResumeEffect(key) {
        current()
        onPauseOrDispose { }
    }
}
