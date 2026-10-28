package com.example.inputmonitor.platform.accessibility

import android.view.accessibility.AccessibilityEvent
import com.example.inputmonitor.domain.model.EventSource
import com.example.inputmonitor.domain.model.EventType
import com.example.inputmonitor.domain.model.InputClassification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [AccessibilityEventMapperImpl] (Architecture.md §21, §22, SECURITY.md §4).
 */
class AccessibilityEventMapperTest {

    private lateinit var mapper: AccessibilityEventMapperImpl

    @Before
    fun setUp() {
        mapper = AccessibilityEventMapperImpl()
    }

    private fun createEvent(
        eventType: Int,
        pkg: String = "com.example.targetapp",
        className: String = "android.widget.Button",
        contentDesc: String? = null,
        isPassword: Boolean = false
    ): SimpleAccessibilityEventInfo {
        return SimpleAccessibilityEventInfo(
            eventType = eventType,
            packageName = pkg,
            className = className,
            contentDescription = contentDesc,
            isPassword = isPassword,
            eventTime = 1700000000000L
        )
    }

    @Test
    fun map_viewClicked_mapsCorrectlyToViewClicked() {
        val event = createEvent(
            eventType = AccessibilityEvent.TYPE_VIEW_CLICKED,
            pkg = "com.example.app",
            className = "android.widget.Button",
            contentDesc = "Submit Order"
        )

        val raw = mapper.map(event)

        assertEquals(EventSource.ACCESSIBILITY, raw.source)
        assertEquals(EventType.VIEW_CLICKED, raw.eventType)
        assertEquals("com.example.app", raw.packageName)
        assertEquals("Submit Order", raw.candidateData)
        assertEquals(InputClassification.NORMAL_TEXT, raw.inputClassification)
        assertEquals("android.widget.Button", raw.metadata["className"])
    }

    @Test
    fun map_viewFocused_mapsToViewFocused() {
        val event = createEvent(
            eventType = AccessibilityEvent.TYPE_VIEW_FOCUSED,
            pkg = "com.example.app",
            className = "android.widget.EditText"
        )

        val raw = mapper.map(event)

        assertEquals(EventType.VIEW_FOCUSED, raw.eventType)
        assertEquals(EventSource.ACCESSIBILITY, raw.source)
        assertEquals("com.example.app", raw.packageName)
    }

    @Test
    fun map_viewScrolled_mapsToViewScrolled() {
        val event = createEvent(
            eventType = AccessibilityEvent.TYPE_VIEW_SCROLLED,
            pkg = "com.example.app",
            className = "androidx.recyclerview.widget.RecyclerView"
        )

        val raw = mapper.map(event)

        assertEquals(EventType.VIEW_SCROLLED, raw.eventType)
    }

    @Test
    fun map_windowStateChanged_mapsToWindowStateChanged() {
        val event = createEvent(
            eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            pkg = "com.example.settings",
            className = "com.example.settings.SubSettingsActivity"
        )

        val raw = mapper.map(event)

        assertEquals(EventType.WINDOW_STATE_CHANGED, raw.eventType)
        assertEquals("com.example.settings", raw.packageName)
    }

    @Test
    fun map_viewSelected_mapsToViewSelected() {
        val event = createEvent(
            eventType = AccessibilityEvent.TYPE_VIEW_SELECTED,
            pkg = "com.example.app",
            contentDesc = "Tab 2"
        )

        val raw = mapper.map(event)

        assertEquals(EventType.VIEW_SELECTED, raw.eventType)
        assertEquals("Tab 2", raw.candidateData)
    }

    @Test
    fun map_passwordFlagEvent_strictlyNullifiesCandidateDataAndClassifiesSensitive() {
        val event = createEvent(
            eventType = AccessibilityEvent.TYPE_VIEW_FOCUSED,
            pkg = "com.example.login",
            className = "android.widget.EditText",
            contentDesc = "Password input",
            isPassword = true
        )

        val raw = mapper.map(event)

        assertNull("Password event must have null candidateData", raw.candidateData)
        assertEquals(InputClassification.SENSITIVE_PASSWORD, raw.inputClassification)
        assertEquals("true", raw.metadata["isPassword"])
    }

    @Test
    fun map_passwordClassName_strictlyNullifiesCandidateDataAndClassifiesSensitive() {
        val event = createEvent(
            eventType = AccessibilityEvent.TYPE_VIEW_CLICKED,
            pkg = "com.example.bank",
            className = "com.example.bank.ui.PinCodeEntryView",
            contentDesc = "Enter PIN",
            isPassword = false
        )

        val raw = mapper.map(event)

        assertNull("PIN view must have null candidateData", raw.candidateData)
        assertEquals(InputClassification.SENSITIVE_PASSWORD, raw.inputClassification)
    }
}
