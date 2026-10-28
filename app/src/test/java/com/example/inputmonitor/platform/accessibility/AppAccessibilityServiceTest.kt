package com.example.inputmonitor.platform.accessibility

import android.view.accessibility.AccessibilityEvent
import com.example.inputmonitor.domain.model.EventSource
import com.example.inputmonitor.domain.model.EventType
import com.example.inputmonitor.domain.model.RawInteractionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [AppAccessibilityService] (Architecture.md §18, TESTING.md §12, §13).
 */
class AppAccessibilityServiceTest {

    private class RecordingPublisher : AccessibilityEventPublisher {
        val publishedEvents = mutableListOf<RawInteractionEvent>()
        var shouldThrow: Boolean = false

        override fun publish(event: RawInteractionEvent) {
            if (shouldThrow) throw RuntimeException("Simulated Publisher Error")
            publishedEvents.add(event)
        }
    }

    private class ConfigurableFilter(var permitEvents: Boolean = true) : AccessibilityEventFilter {
        var callCount = 0

        override fun shouldProcess(event: AccessibilityEventInfo?): Boolean {
            callCount++
            return permitEvents && event != null
        }
    }

    private lateinit var service: AppAccessibilityService
    private lateinit var filter: ConfigurableFilter
    private lateinit var mapper: AccessibilityEventMapperImpl
    private lateinit var publisher: RecordingPublisher

    @Before
    fun setUp() {
        service = AppAccessibilityService()
        filter = ConfigurableFilter(permitEvents = true)
        mapper = AccessibilityEventMapperImpl()
        publisher = RecordingPublisher()

        service.filterOverride = filter
        service.mapperOverride = mapper
        service.publisherOverride = publisher
    }

    private fun createEvent(eventType: Int = AccessibilityEvent.TYPE_VIEW_CLICKED): SimpleAccessibilityEventInfo {
        return SimpleAccessibilityEventInfo(
            eventType = eventType,
            packageName = "com.example.target",
            contentDescription = "Confirm Button",
            eventTime = 1700000000000L
        )
    }

    @Test
    fun handleEvent_nullEvent_ignoredSafely() {
        service.handleEvent(null)

        assertEquals(0, filter.callCount)
        assertEquals(0, publisher.publishedEvents.size)
    }

    @Test
    fun handleEvent_whenFilteredOut_doesNotPublish() {
        filter.permitEvents = false
        val event = createEvent()

        service.handleEvent(event)

        assertEquals(1, filter.callCount)
        assertEquals(0, publisher.publishedEvents.size)
    }

    @Test
    fun handleEvent_whenPermitted_mapsAndPublishesEvent() {
        val event = createEvent(AccessibilityEvent.TYPE_VIEW_CLICKED)

        service.handleEvent(event)

        assertEquals(1, filter.callCount)
        assertEquals(1, publisher.publishedEvents.size)

        val published = publisher.publishedEvents.first()
        assertEquals(EventSource.ACCESSIBILITY, published.source)
        assertEquals(EventType.VIEW_CLICKED, published.eventType)
        assertEquals("com.example.target", published.packageName)
        assertEquals("Confirm Button", published.candidateData)
    }

    @Test
    fun handleEvent_whenPublisherThrowsException_catchesSafely() {
        publisher.shouldThrow = true
        val event = createEvent()

        // Must not crash or propagate exception to caller
        service.handleEvent(event)

        assertTrue(true)
    }

    @Test
    fun onInterrupt_runsWithoutException() {
        service.onInterrupt()
        assertTrue(true)
    }
}
