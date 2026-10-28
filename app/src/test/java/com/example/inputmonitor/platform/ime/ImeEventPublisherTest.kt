package com.example.inputmonitor.platform.ime

import com.example.inputmonitor.core.DispatcherProvider
import com.example.inputmonitor.domain.model.EventSource
import com.example.inputmonitor.domain.model.EventType
import com.example.inputmonitor.domain.model.RawInteractionEvent
import com.example.inputmonitor.domain.processor.EventProcessor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [ImeEventPublisherImpl] (Architecture.md §33, TESTING.md §4).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ImeEventPublisherTest {

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
    private lateinit var publisher: ImeEventPublisherImpl

    @Before
    fun setUp() {
        fakeProcessor = FakeEventProcessor()
        publisher = ImeEventPublisherImpl(
            eventProcessor = fakeProcessor,
            dispatchers = testDispatchers,
            scope = testScope
        )
    }

    @Test
    fun publish_asynchronouslyDispatchesEventToProcessor() {
        val event = RawInteractionEvent(
            timestamp = 1700000000000L,
            source = EventSource.KEYBOARD,
            eventType = EventType.TEXT_COMMITTED,
            packageName = "com.example.editor",
            candidateData = "k"
        )

        // publish must return immediately without waiting for background execution
        publisher.publish(event)
        assertEquals(0, fakeProcessor.processedEvents.size)

        // Advance coroutine scheduler
        testScope.advanceUntilIdle()

        assertEquals(1, fakeProcessor.processedEvents.size)
        assertEquals("k", fakeProcessor.processedEvents.first().candidateData)
    }

    @Test
    fun publish_whenProcessorThrowsException_catchesSafelyWithoutCrashingCaller() {
        fakeProcessor.shouldThrow = true

        val event = RawInteractionEvent(
            timestamp = 1700000000000L,
            source = EventSource.KEYBOARD,
            eventType = EventType.TEXT_COMMITTED,
            packageName = "com.example.editor",
            candidateData = "x"
        )

        // Must not throw exception on the caller's thread
        publisher.publish(event)
        testScope.advanceUntilIdle()

        // Caller survives safely
        assertTrue(true)
    }

    @Test
    fun publish_rapidConsecutiveEvents_allProcessedInOrder() {
        val characters = listOf("a", "b", "c", "d", "e")

        for (char in characters) {
            publisher.publish(
                RawInteractionEvent(
                    timestamp = System.currentTimeMillis(),
                    source = EventSource.KEYBOARD,
                    eventType = EventType.TEXT_COMMITTED,
                    packageName = "com.example.editor",
                    candidateData = char
                )
            )
        }

        testScope.advanceUntilIdle()

        assertEquals(5, fakeProcessor.processedEvents.size)
        assertEquals(characters, fakeProcessor.processedEvents.map { it.candidateData })
    }
}
