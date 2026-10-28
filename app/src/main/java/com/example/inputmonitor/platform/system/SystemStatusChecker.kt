package com.example.inputmonitor.platform.system

/**
 * Interface isolating Android system and settings queries from domain logic (Architecture.md §9, §14).
 *
 * Enables clean separation of concerns and deterministic JVM testing without
 * mocking final Android system services.
 */
interface SystemStatusChecker {
    /**
     * Checks if the application's [com.example.inputmonitor.platform.accessibility.AppAccessibilityService]
     * is actively enabled in Android Accessibility Settings.
     */
    fun isAccessibilityServiceEnabled(): Boolean

    /**
     * Checks if the Custom Keyboard is installed and recognized as an Android IME.
     */
    fun isImeInstalled(): Boolean

    /**
     * Checks if the Custom Keyboard is enabled in Android's input method list.
     */
    fun isImeEnabled(): Boolean

    /**
     * Checks if the Custom Keyboard is currently selected as the active Android IME.
     */
    fun isImeSelected(): Boolean

    /**
     * Checks if the encrypted SQLite database is ready for reading and writing.
     */
    fun isDatabaseReady(): Boolean
}
