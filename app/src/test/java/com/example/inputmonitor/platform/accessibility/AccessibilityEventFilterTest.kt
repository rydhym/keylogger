package com.example.inputmonitor.platform.accessibility

import android.view.accessibility.AccessibilityEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [AccessibilityEventFilterImpl] (Architecture.md §20, TESTING.md §12, §14).
 */
class AccessibilityEventFilterTest {

    private val ownPackage = "com.example.inputmonitor"
    private lateinit var filter: AccessibilityEventFilterImpl

    @Before
    fun setUp() {
        filter = AccessibilityEventFilterImpl(
            ownPackageName = ownPackage,
            config = AccessibilityConfig(
                monitorClicks = true,
                monitorFocus = true,
                monitorScrolls = true,
                monitorWindowChanges = true,
                monitorSelections = true,
                ignoreOwnAppPackage = true,
                minScrollIntervalMs = 100L
            )
        )
    }

    private fun createEvent(eventType: Int, pkg: String = "com.example.otherapp"): SimpleAccessibilityEventInfo {
        return SimpleAccessibilityEventInfo(
            eventType = eventType,
            packageName = pkg
        )
    }

    @Test
    fun shouldProcess_nullEvent_returnsFalse() {
        assertFalse(filter.shouldProcess(null as AccessibilityEventInfo?))
    }

    @Test
    fun shouldProcess_ownAppPackage_returnsFalseToPreventFeedbackLoop() {
        val clickEvent = createEvent(AccessibilityEvent.TYPE_VIEW_CLICKED, ownPackage)
        assertFalse("Own app events must be ignored", filter.shouldProcess(clickEvent))

        val focusEvent = createEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED, ownPackage)
        assertFalse("Own app events must be ignored", filter.shouldProcess(focusEvent))
    }

    @Test
    fun shouldProcess_monitoredEventTypes_returnsTrueForExternalApps() {
        assertTrue(filter.shouldProcess(createEvent(AccessibilityEvent.TYPE_VIEW_CLICKED)))
        assertTrue(filter.shouldProcess(createEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED)))
        assertTrue(filter.shouldProcess(createEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)))
        assertTrue(filter.shouldProcess(createEvent(AccessibilityEvent.TYPE_WINDOWS_CHANGED)))
        assertTrue(filter.shouldProcess(createEvent(AccessibilityEvent.TYPE_VIEW_SELECTED)))
    }

    @Test
    fun shouldProcess_unmonitoredEventTypes_returnsFalse() {
        assertFalse(filter.shouldProcess(createEvent(AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED)))
        assertFalse(filter.shouldProcess(createEvent(AccessibilityEvent.TYPE_GESTURE_DETECTION_START)))
        assertFalse(filter.shouldProcess(createEvent(AccessibilityEvent.TYPE_TOUCH_INTERACTION_START)))
    }

    @Test
    fun shouldProcess_disabledCategory_returnsFalse() {
        val noClicksFilter = AccessibilityEventFilterImpl(
            ownPackageName = ownPackage,
            config = AccessibilityConfig(monitorClicks = false)
        )

        assertFalse(noClicksFilter.shouldProcess(createEvent(AccessibilityEvent.TYPE_VIEW_CLICKED)))
    }

    @Test
    fun shouldProcess_scrollStormRateLimiting_suppressesRapidConsecutiveScrolls() {
        val scrollEvent1 = createEvent(AccessibilityEvent.TYPE_VIEW_SCROLLED)
        val scrollEvent2 = createEvent(AccessibilityEvent.TYPE_VIEW_SCROLLED)

        // First scroll accepted
        assertTrue(filter.shouldProcess(scrollEvent1))

        // Immediate second scroll within 100ms interval rejected
        assertFalse(filter.shouldProcess(scrollEvent2))

        // Wait beyond minScrollIntervalMs (100ms)
        Thread.sleep(110)

        // Subsequent scroll accepted
        val scrollEvent3 = createEvent(AccessibilityEvent.TYPE_VIEW_SCROLLED)
        assertTrue(filter.shouldProcess(scrollEvent3))
    }
}
