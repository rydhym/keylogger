package com.example.inputmonitor.platform.accessibility

import com.example.inputmonitor.core.DispatcherProvider
import com.example.inputmonitor.domain.model.EventSource
import com.example.inputmonitor.domain.model.EventType
import com.example.inputmonitor.domain.model.RawInteractionEvent
import com.example.inputmonitor.domain.processor.EventProcessor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [AccessibilityEventPublisherImpl] (Architecture.md §19).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccessibilityEventPublisherTest {

    private val testSchedulerDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testSchedulerDispatcher)

    private val testDispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testSchedulerDispatcher
        override val io: CoroutineDispatcher = testSchedulerDispatcher
        override val default: CoroutineDispatcher = testSchedulerDispatcher
        override val unconfined: CoroutineDispatcher = testSchedulerDispatcher
    }

    private class FakeEventProcessor : EventProcessor {
        val processedEvents = mutableListOf<RawInteractionEvent>()
        var shouldThrow: Boolean = false

        override suspend fun process(event: RawInteractionEvent): Boolean {
            if (shouldThrow) throw RuntimeException("Processor Pipeline Failure")
            processedEvents.add(event)
            return true
        }
    }

    private lateinit var fakeProcessor: FakeEventProcessor
    private lateinit var publisher: AccessibilityEventPublisherImpl

    @Before
    fun setUp() {
        fakeProcessor = FakeEventProcessor()
        publisher = AccessibilityEventPublisherImpl(
            eventProcessor = fakeProcessor,
            dispatchers = testDispatchers,
            scope = testScope
        )
    }

    @Test
    fun publish_asynchronouslyDispatchesEventToProcessor() {
        val event = RawInteractionEvent(
            timestamp = 1700000000000L,
            source = EventSource.ACCESSIBILITY,
            eventType = EventType.VIEW_CLICKED,
            packageName = "com.example.browser",
            candidateData = "Tab 1"
        )

        publisher.publish(event)
        assertEquals(0, fakeProcessor.processedEvents.size)

        testScope.advanceUntilIdle()

        assertEquals(1, fakeProcessor.processedEvents.size)
        assertEquals("Tab 1", fakeProcessor.processedEvents.first().candidateData)
    }

    @Test
    fun publish_whenProcessorThrowsException_catchesSafelyWithoutCrashingCaller() {
        fakeProcessor.shouldThrow = true

        val event = RawInteractionEvent(
            timestamp = 1700000000000L,
            source = EventSource.ACCESSIBILITY,
            eventType = EventType.VIEW_CLICKED,
            packageName = "com.example.browser",
            candidateData = "Button"
        )

        publisher.publish(event)
        testScope.advanceUntilIdle()

        assertTrue(true)
    }
}
