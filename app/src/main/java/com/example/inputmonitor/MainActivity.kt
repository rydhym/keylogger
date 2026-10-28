package com.example.inputmonitor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.example.inputmonitor.ui.navigation.AppNavigation
import com.example.inputmonitor.ui.theme.InputMonitorTheme
import kotlinx.coroutines.launch

/**
 * Main host activity.
 *
 * Hosts the Compose navigation graph (Architecture.md §12):
 * Onboarding → Setup → Dashboard ↔ Events / Statistics / Settings
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            InputMonitorTheme {
                AppNavigation()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            val app = applicationContext as? MonitoringApplication
            val container = app?.container
            if (container != null) {
                lifecycleScope.launch {
                    try {
                        container.setupStateManager.refresh()
                    } catch (_: Throwable) {
                        // Suppress background refresh errors
                    }
                }
            }
        } catch (_: Throwable) {
        }
    }
}
