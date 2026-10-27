package com.zemmy.sigurvita

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.zemmy.sigurvita.core.data.api.SessionEvents
import com.zemmy.sigurvita.core.model.AppThemeMode
import com.zemmy.sigurvita.ui.components.LoadingState
import com.zemmy.sigurvita.ui.navigation.Screen
import com.zemmy.sigurvita.ui.navigation.SigurvitaNavGraph
import com.zemmy.sigurvita.ui.screens.reader.ReaderViewModel
import com.zemmy.sigurvita.ui.screens.reader.VolumeChordDetector
import com.zemmy.sigurvita.ui.theme.SigurvitaTheme

class MainActivity : ComponentActivity() {

    private val readerViewModel: ReaderViewModel by viewModels()

    // Tracks the current navigation route so volume keys are only forwarded on the Reader screen
    @Volatile
    private var currentRoute: String? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Single volume keys page (or step bubbles) as before; holding both for about two seconds toggles
     * speech-bubble navigation mode, the same mode a long-press on a bubble turns on.
     */
    private val volumeChord = VolumeChordDetector(
        scheduler = VolumeChordDetector.Scheduler { delayMillis, action ->
            val runnable = Runnable { action() }
            mainHandler.postDelayed(runnable, delayMillis)
            VolumeChordDetector.Cancellable { mainHandler.removeCallbacks(runnable) }
        },
        onStep = { isVolumeUp ->
            // The step is deferred a few milliseconds, so re-check that the reader is still showing.
            if (isReaderRoute()) readerViewModel.handleVolumeKey(isVolumeUp = isVolumeUp)
        },
        onChord = {
            if (isReaderRoute() && readerViewModel.toggleBubbleNavMode()) {
                window.decorView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        }
    )

    private fun isReaderRoute(): Boolean = currentRoute?.startsWith("reader/") == true

    private fun isVolumeKey(keyCode: Int): Boolean =
        keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN

    @SuppressLint("ContextCastToActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val serverConfigState by SigurvitaApp.instance.userPreferencesRepository.serverConfigFlow
                .collectAsState(initial = null)
            val appearanceState by SigurvitaApp.instance.userPreferencesRepository.appearanceSettingsFlow
                .collectAsState(initial = com.zemmy.sigurvita.core.model.AppearanceSettings())

            val appearance = appearanceState
            val useDark = when (appearance.themeMode) {
                AppThemeMode.SYSTEM -> isSystemInDarkTheme()
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
                AppThemeMode.AMOLED -> true
            }

            val window = (LocalContext.current as? android.app.Activity)?.window
            androidx.compose.runtime.LaunchedEffect(useDark) {
                window?.let {
                    val controller = WindowInsetsControllerCompat(it, it.decorView)
                    controller.isAppearanceLightStatusBars = !useDark
                    controller.isAppearanceLightNavigationBars = !useDark
                }
            }

            SigurvitaTheme(
                themeMode = appearance.themeMode,
                dynamicColor = appearance.dynamicColor
            ) {
                val serverConfig = serverConfigState
                if (serverConfig != null) {
                    val startDestination = if (serverConfig.isLoggedIn) Screen.Home.route else Screen.Auth.route
                    val navController = rememberNavController()

                    // Keep currentRoute in sync with the backstack
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    currentRoute = navBackStackEntry?.destination?.route

                    SigurvitaNavGraph(
                        navController = navController,
                        startDestination = startDestination
                    )

                    val sessionExpired by SessionEvents.sessionExpired.collectAsState()
                    if (sessionExpired && currentRoute != Screen.Auth.route) {
                        AlertDialog(
                            onDismissRequest = { SessionEvents.clear() },
                            title = { Text(stringResource(R.string.session_expired_title)) },
                            text = { Text(stringResource(R.string.session_expired_message)) },
                            confirmButton = {
                                TextButton(onClick = {
                                    SessionEvents.clear()
                                    navController.navigate(Screen.Auth.route) { popUpTo(0) { inclusive = true } }
                                }) { Text(stringResource(R.string.session_expired_sign_in)) }
                            },
                            dismissButton = {
                                TextButton(onClick = { SessionEvents.clear() }) {
                                    Text(stringResource(R.string.session_expired_dismiss))
                                }
                            }
                        )
                    }
                } else {
                    LoadingState()
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Only intercept volume keys when the Reader screen is active and volume-key navigation is on
        if (isVolumeKey(keyCode) && isReaderRoute() && readerViewModel.isVolumeKeyNavigationEnabled) {
            volumeChord.onKeyDown(
                isVolumeUp = keyCode == KeyEvent.KEYCODE_VOLUME_UP,
                isRepeat = (event?.repeatCount ?: 0) > 0
            )
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (isVolumeKey(keyCode)) {
            // Always tell the detector, even off the reader, so a held-key flag can never go stale.
            volumeChord.onKeyUp(isVolumeUp = keyCode == KeyEvent.KEYCODE_VOLUME_UP)
            if (isReaderRoute() && readerViewModel.isVolumeKeyNavigationEnabled) return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Key-up events can be lost when focus goes away (shade, dialog, app switch): forget held keys.
        if (!hasFocus) volumeChord.reset()
    }

    override fun onPause() {
        volumeChord.reset()
        super.onPause()
    }
}
