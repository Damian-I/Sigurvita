package com.zemmy.sigurvita

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.zemmy.sigurvita.ui.components.LoadingState
import com.zemmy.sigurvita.ui.navigation.Screen
import com.zemmy.sigurvita.ui.navigation.SigurvitaNavGraph
import com.zemmy.sigurvita.ui.screens.reader.ReaderViewModel
import com.zemmy.sigurvita.ui.theme.SigurvitaTheme

class MainActivity : ComponentActivity() {

    private val readerViewModel: ReaderViewModel by viewModels()

    // Tracks the current navigation route so volume keys are only forwarded on the Reader screen
    @Volatile
    private var currentRoute: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val serverConfigState by SigurvitaApp.instance.userPreferencesRepository.serverConfigFlow
                .collectAsState(initial = null)

            SigurvitaTheme {
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
                } else {
                    LoadingState()
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Only intercept volume keys when the Reader screen is active
        val route = currentRoute
        if (route != null && route.startsWith("reader/")) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                val handled = readerViewModel.handleVolumeKey(isVolumeUp = true)
                if (handled) return true
            } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                val handled = readerViewModel.handleVolumeKey(isVolumeUp = false)
                if (handled) return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}
