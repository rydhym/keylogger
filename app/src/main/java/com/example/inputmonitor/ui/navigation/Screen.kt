package com.example.inputmonitor.ui.navigation

/**
 * Type-safe navigation routes for the application (Architecture.md §13).
 */
sealed class Screen(val route: String, val title: String) {
    object Onboarding : Screen("onboarding", "Welcome & Privacy Disclosure")
    object Setup : Screen("setup", "Application Setup")
    object Dashboard : Screen("dashboard", "Dashboard")
    object Events : Screen("events", "Interaction Events")
    object Statistics : Screen("statistics", "Interaction Statistics")
    object Export : Screen("export", "Export Data")
    object Settings : Screen("settings", "Settings")
    object TestBench : Screen("test_bench", "Controlled Test Bench")
}
