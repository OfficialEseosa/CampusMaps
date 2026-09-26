package com.campusmaps

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.campusmaps.ui.CampusMapsApp
import com.campusmaps.ui.MainViewModel
import com.campusmaps.ui.ShortcutViewModel

// The only activity. Portrait is locked in the manifest.
class MainActivity : ComponentActivity() {

    private val container: AppContainer get() = (application as CampusMapsApplication).container

    private val mainViewModel: MainViewModel by viewModels { MainViewModel.Factory(container) }
    private val shortcutViewModel: ShortcutViewModel by viewModels { ShortcutViewModel.Factory(container) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Draw behind the status and navigation bars; each screen adds its own insets.
        enableEdgeToEdge()
        setContent {
            CampusMapsApp(app = container, vm = mainViewModel, shortcutVm = shortcutViewModel)
        }
    }
}
