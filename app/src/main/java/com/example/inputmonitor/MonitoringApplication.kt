package com.example.inputmonitor

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Application entry point.
 *
 * Responsibilities:
 * - Initialize application-wide dependencies
 * - Initialize database (future phase)
 * - Initialize repositories (future phase)
 * - Initialize configuration providers (future phase)
 *
 * Must NOT start covert/background monitoring.
 */
class MonitoringApplication : Application() {

    lateinit var container: com.example.inputmonitor.di.AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        try {
            System.loadLibrary("sqlcipher")
        } catch (_: Throwable) {
            // Graceful fallback if native library is preloaded or unavailable
        }

        try {
            container = com.example.inputmonitor.di.DefaultAppContainer(applicationContext)
            kotlinx.coroutines.CoroutineScope(container.dispatchers.io).launch {
                try {
                    container.sessionManager.restoreSessionIfActive()
                } catch (_: Throwable) {
                    // Suppress restart restoration errors to prevent process abort
                }
            }
        } catch (t: Throwable) {
            android.util.Log.e("MonitoringApp", "Failed to initialize application container", t)
        }
    }
}
