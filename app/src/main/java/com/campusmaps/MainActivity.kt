package com.campusmaps

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.campusmaps.ui.CampusMapsApp
import com.campusmaps.ui.MainViewModel
import com.campusmaps.ui.ShortcutViewModel
import com.campusmaps.ui.splash.SplashHost

// The only activity. Portrait is locked in the manifest.
class MainActivity : ComponentActivity() {

    private val container: AppContainer get() = (application as CampusMapsApplication).container

    private val mainViewModel: MainViewModel by viewModels { MainViewModel.Factory(container) }
    private val shortcutViewModel: ShortcutViewModel by viewModels { ShortcutViewModel.Factory(container) }

    override fun onCreate(savedInstanceState: Bundle?) {
        // System splash (logo mark on Ink) until the first frame; it never waits on anything.
        // Must run before super.onCreate: it swaps the launch theme for Theme.CampusMaps.App.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Draw behind the status and navigation bars; each screen adds its own insets.
        enableEdgeToEdge()
        setContent {
            // Animated splash on top of the app, which is composed underneath at once (ui/splash).
            SplashHost {
                CampusMapsApp(app = container, vm = mainViewModel, shortcutVm = shortcutViewModel)
            }
        }
    }
}
