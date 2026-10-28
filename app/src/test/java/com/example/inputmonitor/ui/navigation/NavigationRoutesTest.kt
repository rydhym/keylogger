package com.example.inputmonitor.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NavigationRoutesTest {

    @Test
    fun routes_areDistinct() {
        val routes = listOf(
            Screen.Onboarding.route,
            Screen.Setup.route,
            Screen.Dashboard.route,
            Screen.Events.route,
            Screen.Statistics.route,
            Screen.Export.route,
            Screen.Settings.route,
            Screen.TestBench.route
        )
        val distinctRoutes = routes.distinct()
        assertEquals("Each screen route must be unique", routes.size, distinctRoutes.size)
    }

    @Test
    fun titles_areNonEmpty() {
        val screens = listOf(
            Screen.Onboarding,
            Screen.Setup,
            Screen.Dashboard,
            Screen.Events,
            Screen.Statistics,
            Screen.Export,
            Screen.Settings,
            Screen.TestBench
        )
        screens.forEach { screen ->
            assertNotEquals("", screen.title.trim())
            assertNotEquals("", screen.route.trim())
        }
    }
}
