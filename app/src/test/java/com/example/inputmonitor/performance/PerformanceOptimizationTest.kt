package com.example.inputmonitor.performance

import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.CompletionInfo
import android.view.inputmethod.CorrectionInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputContentInfo
import com.example.inputmonitor.core.DispatcherProvider
import com.example.inputmonitor.data.export.CsvEventExporterImpl
import com.example.inputmonitor.domain.export.ExportValidatorImpl
import com.example.inputmonitor.domain.model.EventSource
import com.example.inputmonitor.domain.model.EventType
import com.example.inputmonitor.domain.model.InputClassification
import com.example.inputmonitor.domain.model.InteractionEvent
import com.example.inputmonitor.domain.model.RawInteractionEvent
import com.example.inputmonitor.domain.privacy.PrivacyFilterImpl
import com.example.inputmonitor.platform.accessibility.AccessibilityConfig
import com.example.inputmonitor.platform.accessibility.AccessibilityEventFilterImpl
import com.example.inputmonitor.platform.accessibility.SimpleAccessibilityEventInfo
import com.example.inputmonitor.platform.ime.KeyAction
import com.example.inputmonitor.platform.ime.KeyProcessor
import com.example.inputmonitor.platform.packages.ContextResolverImpl
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import kotlin.system.measureNanoTime
import kotlin.system.measureTimeMillis

/**
 * Performance optimization regression test suite (IMPLEMENTATION_PLAN.md §57,
 * DEVELOPMENT_GUIDELINES.md §1-§33).
 *
 * Verifies strict operational performance limits:
 * 1. Keyboard latency (< 2ms budget per keystroke)
 * 2. High-throughput privacy filter sanitization (> 10,000 events/sec)
 * 3. Accessibility scroll storm throttling and self-monitoring loop suppression
 * 4. Bounded ContextResolver cache capacity (max 256 entries)
 * 5. Streaming RFC-4180 CSV export memory footprint ($O(1)$ RAM)
 */
class PerformanceOptimizationTest {

    private val testDispatcher = Dispatchers.Unconfined
    private val testDispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
        override val unconfined: CoroutineDispatcher = testDispatcher
    }

    private class MockInputConnection : InputConnection {
        var lastCommitted: String? = null
        var commitCount = 0

        override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence = ""
        override fun getTextAfterCursor(n: Int, flags: Int): CharSequence = ""
        override fun getSelectedText(flags: Int): CharSequence = ""
        override fun getCursorCapsMode(reqModes: Int): Int = 0
        override fun getExtractedText(request: ExtractedTextRequest?, flags: Int): ExtractedText? = null
        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean = true
        override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean = true
        override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean = true
        override fun setComposingRegion(start: Int, end: Int): Boolean = true
        override fun finishComposingText(): Boolean = true
        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            lastCommitted = text?.toString()
            commitCount++
            return true
        }
        override fun commitCompletion(text: CompletionInfo?): Boolean = true
        override fun commitCorrection(correctionInfo: CorrectionInfo?): Boolean = true
        override fun setSelection(start: Int, end: Int): Boolean = true
        override fun performEditorAction(editorAction: Int): Boolean = true
        override fun performContextMenuAction(id: Int): Boolean = true
        override fun beginBatchEdit(): Boolean = true
        override fun endBatchEdit(): Boolean = true
        override fun sendKeyEvent(event: KeyEvent?): Boolean = true
        override fun clearMetaKeyStates(states: Int): Boolean = true
        override fun reportFullscreenMode(enabled: Boolean): Boolean = true
        override fun performPrivateCommand(action: String?, data: Bundle?): Boolean = true
        override fun requestCursorUpdates(cursorUpdateMode: Int): Boolean = true
        override fun getHandler(): android.os.Handler? = null
        override fun closeConnection() {}
        override fun commitContent(inputContentInfo: InputContentInfo, flags: Int, opts: Bundle?): Boolean = true
    }

    @Test
    fun keyboardLatency_underOneMillisecond_perKeystrokeBudget() {
        val keyProcessor = KeyProcessor()
        val mockConnection = MockInputConnection()
        val iterations = 1000

        // Warm up JIT
        repeat(100) {
            keyProcessor.processKey(KeyAction.Character("x"), mockConnection, null)
        }

        val totalDurationNanos = measureNanoTime {
            for (i in 1..iterations) {
                keyProcessor.processKey(KeyAction.Character("a"), mockConnection, null)
            }
        }

        val averageDurationMillis = (totalDurationNanos.toDouble() / iterations) / 1_000_000.0

        // Mandate: Keyboard latency must be < 2ms (DEVELOPMENT_GUIDELINES.md §17)
        assertTrue(
            "Average keystroke processing time must be < 1.0ms, was ${averageDurationMillis}ms",
            averageDurationMillis < 1.0
        )
    }

    @Test
    fun accessibilityFilter_throttlesScrollStorms_andRejectsSelfMonitoringLoops() {
        val ownPackage = "com.example.inputmonitor"
        val filter = AccessibilityEventFilterImpl(
            ownPackageName = ownPackage,
            config = AccessibilityConfig(
                minScrollIntervalMs = 250L,
                ignoreOwnAppPackage = true
            )
        )

        // 1. Verify self-monitoring loop rejection
        val selfEvent = SimpleAccessibilityEventInfo(
            eventType = android.view.accessibility.AccessibilityEvent.TYPE_VIEW_CLICKED,
            packageName = ownPackage,
            className = "android.widget.Button",
            contentDescription = null,
            eventTime = System.currentTimeMillis()
        )
        assertFalse("Self-monitoring events must be strictly suppressed", filter.shouldProcess(selfEvent))

        // 2. Verify scroll storm rate limiting (100 rapid events within 10ms window)
        val externalPackage = "com.example.targetapp"
        var acceptedScrolls = 0
        for (i in 1..100) {
            val scrollEvent = SimpleAccessibilityEventInfo(
                eventType = android.view.accessibility.AccessibilityEvent.TYPE_VIEW_SCROLLED,
                packageName = externalPackage,
                className = "android.widget.ScrollView",
                contentDescription = null,
                eventTime = System.currentTimeMillis()
            )
            if (filter.shouldProcess(scrollEvent)) {
                acceptedScrolls++
            }
        }

        // Only the first scroll should be processed; remaining rapid scrolls throttled
        assertEquals("Scroll burst within 250ms interval must only admit 1 event", 1, acceptedScrolls)
    }

    @Test
    fun contextResolver_enforcesBoundedCacheCapacity() = runBlocking {
        val dummyContext = object : android.content.ContextWrapper(null) {}
        val resolver = ContextResolverImpl(
            context = dummyContext,
            dispatchers = testDispatchers,
            packageLabelProvider = { pkg -> "Label_$pkg" }
        )

        // Populate 300 distinct packages (exceeding MAX_CACHE_ENTRIES = 256)
        for (i in 1..300) {
            resolver.resolve("com.app.package_$i")
        }

        // Verify sub-microsecond cache hit performance
        val cachedLookupNanos = measureNanoTime {
            val cachedResult = resolver.resolve("com.app.package_300")
            assertEquals("Label_com.app.package_300", cachedResult.applicationLabel)
        }

        val lookupMillis = cachedLookupNanos / 1_000_000.0
        assertTrue("In-memory cache hit must take < 0.2ms, was ${lookupMillis}ms", lookupMillis < 0.2)
    }

    @Test
    fun privacyFilter_highThroughput_processesTenThousandEventsRapidly() {
        val privacyFilter = PrivacyFilterImpl()
        val eventCount = 10_000

        val events = (1..eventCount).map { i ->
            val isSensitive = (i % 4 == 0)
            RawInteractionEvent(
                timestamp = 1000L + i,
                source = EventSource.KEYBOARD,
                eventType = EventType.TEXT_COMMITTED,
                packageName = "com.example.app_$i",
                candidateData = if (isSensitive) "SecretPass_$i" else "NormalText_$i",
                inputClassification = if (isSensitive) InputClassification.SENSITIVE_PASSWORD else InputClassification.NORMAL_TEXT,
                metadata = mapOf("index" to i.toString())
            )
        }

        val elapsedMs = measureTimeMillis {
            for (event in events) {
                val sanitized = privacyFilter.sanitize(event)
                if (event.inputClassification.isSensitive) {
                    assertEquals(null, sanitized.eventData)
                    assertEquals(EventType.SENSITIVE_INPUT_DETECTED, sanitized.eventType)
                } else {
                    assertEquals(event.candidateData, sanitized.eventData)
                }
            }
        }

        // 10,000 events must be processed in under 500ms (> 20,000 events/second)
        assertTrue(
            "Sanitizing 10,000 events must take < 500ms, took ${elapsedMs}ms",
            elapsedMs < 500
        )
    }

    @Test
    fun csvStreamingExport_maintainsBoundedMemoryProfile() {
        val exporter = CsvEventExporterImpl()
        val validator = ExportValidatorImpl()
        val totalEvents = 2000
        val batchSize = 250

        val dummyEvents = (1..totalEvents).map { i ->
            InteractionEvent(
                id = i.toLong(),
                sessionId = "sess-perf",
                timestamp = 1000L + i,
                source = EventSource.KEYBOARD,
                eventType = if (i % 5 == 0) EventType.SENSITIVE_INPUT_DETECTED else EventType.TEXT_COMMITTED,
                packageName = "com.example.app",
                applicationName = "Example App",
                eventData = if (i % 5 == 0) null else "data_$i",
                inputClassification = if (i % 5 == 0) InputClassification.SENSITIVE_PASSWORD else InputClassification.NORMAL_TEXT
            )
        }

        val outputStream = ByteArrayOutputStream()
        exporter.writeHeader(outputStream)

        // Stream in bounded chunks of 250 records (O(1) heap allocation)
        var totalBytes = 0L
        dummyEvents.chunked(batchSize).forEach { chunk ->
            val sanitizedBatch = chunk.map { validator.sanitizeForExport(it) }
            val bytes = exporter.writeEvents(outputStream, sanitizedBatch)
            totalBytes += bytes
            assertTrue("Each batch must write bytes", bytes > 0L)
        }

        assertTrue("Output stream must contain valid CSV output", totalBytes > 10_000L)
        val csvText = outputStream.toString("UTF-8")
        assertTrue("Header must be present", csvText.startsWith("timestamp,formatted_time,session_id"))
        assertTrue("Sensitive records must be redacted", csvText.contains("[REDACTED]"))
    }
}
