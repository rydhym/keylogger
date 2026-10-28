package com.example.inputmonitor.ui.testbench

import com.example.inputmonitor.data.export.CsvEventExporterImpl
import com.example.inputmonitor.data.session.SessionGateImpl
import com.example.inputmonitor.domain.model.EventSource
import com.example.inputmonitor.domain.model.EventType
import com.example.inputmonitor.domain.model.InputClassification
import com.example.inputmonitor.domain.model.InteractionEvent
import com.example.inputmonitor.domain.model.MonitoringSession
import com.example.inputmonitor.domain.model.RawInteractionEvent
import com.example.inputmonitor.domain.model.SessionState
import com.example.inputmonitor.domain.privacy.PrivacyFilterImpl
import com.example.inputmonitor.domain.session.SessionManager
import com.example.inputmonitor.platform.ime.KeyAction
import com.example.inputmonitor.platform.ime.KeyProcessor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

/**
 * End-to-end ground truth validation tests for controlled application inputs (SRS.md §66-§72, AC-29, AC-30, AC-31, AC-32).
 *
 * Verifies that the controlled test environment correctly isolates permitted synthetic application
 * input from strictly redacted credential/password/PIN inputs.
 */
class ControlledInputPrivacyTest {

    private val privacyFilter = PrivacyFilterImpl()

    @Test
    fun normalTextInput_preservesSyntheticTextAndClassifiesCorrectly_ac29() {
        val field = TestBenchFieldType.NORMAL_TEXT
        val syntheticText = "HELLO ANDROID"

        val rawEvent = RawInteractionEvent(
            timestamp = 1000L,
            source = EventSource.KEYBOARD,
            eventType = EventType.TEXT_COMMITTED,
            packageName = "com.example.inputmonitor",
            candidateData = syntheticText,
            inputClassification = field.expectedClassification,
            metadata = mapOf("fieldCategory" to "TEXT")
        )

        val sanitized = privacyFilter.sanitize(rawEvent)

        assertEquals(EventType.TEXT_COMMITTED, sanitized.eventType)
        assertEquals(InputClassification.NORMAL_TEXT, sanitized.inputClassification)
        assertEquals(syntheticText, sanitized.eventData)
        assertFalse(sanitized.inputClassification.isSensitive)
    }

    @Test
    fun emailAndUrlInput_preservesSyntheticDataAndClassifiesCorrectly_ac29() {
        // Email
        val emailRaw = RawInteractionEvent(
            timestamp = 1100L,
            source = EventSource.KEYBOARD,
            eventType = EventType.TEXT_COMMITTED,
            packageName = "com.example.inputmonitor",
            candidateData = TestBenchFieldType.EMAIL.sampleValue,
            inputClassification = TestBenchFieldType.EMAIL.expectedClassification,
            metadata = mapOf("fieldCategory" to "EMAIL")
        )
        val sanitizedEmail = privacyFilter.sanitize(emailRaw)
        assertEquals(InputClassification.EMAIL_ADDRESS, sanitizedEmail.inputClassification)
        assertEquals("tester@example.com", sanitizedEmail.eventData)

        // URL
        val urlRaw = RawInteractionEvent(
            timestamp = 1200L,
            source = EventSource.KEYBOARD,
            eventType = EventType.TEXT_COMMITTED,
            packageName = "com.example.inputmonitor",
            candidateData = TestBenchFieldType.URL.sampleValue,
            inputClassification = TestBenchFieldType.URL.expectedClassification,
            metadata = mapOf("fieldCategory" to "URL")
        )
        val sanitizedUrl = privacyFilter.sanitize(urlRaw)
        assertEquals(InputClassification.NORMAL_TEXT, sanitizedUrl.inputClassification)
        assertEquals("https://example.org/test", sanitizedUrl.eventData)
    }

    @Test
    fun passwordInput_unconditionallyRedactsCredentialData_ac30() {
        val field = TestBenchFieldType.PASSWORD
        val secretPassword = "SuperSecretPassword123!"

        val rawEvent = RawInteractionEvent(
            timestamp = 2000L,
            source = EventSource.KEYBOARD,
            eventType = EventType.TEXT_COMMITTED,
            packageName = "com.example.inputmonitor",
            candidateData = secretPassword,
            inputClassification = field.expectedClassification,
            metadata = mapOf("fieldCategory" to "PASSWORD")
        )

        val sanitized = privacyFilter.sanitize(rawEvent)

        // Security Invariant Assertions:
        assertEquals(EventType.SENSITIVE_INPUT_DETECTED, sanitized.eventType)
        assertEquals(InputClassification.SENSITIVE_PASSWORD, sanitized.inputClassification)
        assertTrue(sanitized.inputClassification.isSensitive)
        assertNull("Raw password must never be retained in sanitized event", sanitized.eventData)
        assertNotEquals(secretPassword, sanitized.eventData)
    }

    @Test
    fun pinInput_unconditionallyRedactsNumericPinData_ac30() {
        val field = TestBenchFieldType.PIN
        val secretPin = "9876"

        val rawEvent = RawInteractionEvent(
            timestamp = 3000L,
            source = EventSource.KEYBOARD,
            eventType = EventType.TEXT_COMMITTED,
            packageName = "com.example.inputmonitor",
            candidateData = secretPin,
            inputClassification = field.expectedClassification,
            metadata = mapOf("fieldCategory" to "NUMERIC_PASSWORD")
        )

        val sanitized = privacyFilter.sanitize(rawEvent)

        // Security Invariant Assertions:
        assertEquals(EventType.SENSITIVE_INPUT_DETECTED, sanitized.eventType)
        assertEquals(InputClassification.SENSITIVE_PIN, sanitized.inputClassification)
        assertTrue(sanitized.inputClassification.isSensitive)
        assertNull("Numeric PIN must never be persisted", sanitized.eventData)
        assertNotEquals(secretPin, sanitized.eventData)
    }

    @Test
    fun exportPipeline_redactsSensitiveEvents_leavingNoRawCredentialTrace_ac31() = runTest {
        val events = listOf(
            InteractionEvent(
                id = 1L,
                sessionId = "sess-test",
                timestamp = 1000L,
                source = EventSource.KEYBOARD,
                eventType = EventType.TEXT_COMMITTED,
                packageName = "com.example.inputmonitor",
                applicationName = "Input Monitor",
                eventData = "HELLO ANDROID",
                inputClassification = InputClassification.NORMAL_TEXT
            ),
            InteractionEvent(
                id = 2L,
                sessionId = "sess-test",
                timestamp = 2000L,
                source = EventSource.KEYBOARD,
                eventType = EventType.SENSITIVE_INPUT_DETECTED,
                packageName = "com.example.inputmonitor",
                applicationName = "Input Monitor",
                eventData = null,
                inputClassification = InputClassification.SENSITIVE_PASSWORD
            ),
            InteractionEvent(
                id = 3L,
                sessionId = "sess-test",
                timestamp = 3000L,
                source = EventSource.KEYBOARD,
                eventType = EventType.SENSITIVE_INPUT_DETECTED,
                packageName = "com.example.inputmonitor",
                applicationName = "Input Monitor",
                eventData = null,
                inputClassification = InputClassification.SENSITIVE_PIN
            )
        )

        val exporter = CsvEventExporterImpl()
        val validator = com.example.inputmonitor.domain.export.ExportValidatorImpl()
        val sanitizedEvents = events.map { validator.sanitizeForExport(it) }

        val outputStream = ByteArrayOutputStream()
        exporter.writeHeader(outputStream)
        exporter.writeEvents(outputStream, sanitizedEvents)
        val csvOutput = outputStream.toString("UTF-8")

        // Invariant Assertions:
        assertTrue("Permitted synthetic text must appear in CSV", csvOutput.contains("HELLO ANDROID"))
        assertTrue("Sensitive password event must show [REDACTED]", csvOutput.contains("[REDACTED]"))
        assertFalse("Raw password must never exist in export output", csvOutput.contains("SuperSecretPassword123!"))
        assertFalse("Raw PIN must never exist in export output", csvOutput.contains("9876"))
    }

    @Test
    fun sessionGate_stopsPersistenceWhenSessionInactive_withoutDisablingKeyProcessor_ac32() {
        val sessionStateFlow = MutableStateFlow(SessionState.STOPPED)
        val currentSessionFlow = MutableStateFlow<MonitoringSession?>(null)

        val fakeSessionManager = object : SessionManager {
            override val sessionState: StateFlow<SessionState> = sessionStateFlow.asStateFlow()
            override val currentSession: StateFlow<MonitoringSession?> = currentSessionFlow.asStateFlow()
            override suspend fun startSession(name: String, notes: String?): MonitoringSession =
                throw UnsupportedOperationException()
            override suspend fun pauseSession(): Boolean = false
            override suspend fun resumeSession(): Boolean = false
            override suspend fun stopSession(): Boolean = false
            override suspend fun restoreSessionIfActive(): MonitoringSession? = null
        }

        val gate = SessionGateImpl(fakeSessionManager)

        // When session is stopped:
        assertFalse("Session gate must disallow logging when session is STOPPED", gate.canProcessEvents())

        // KeyProcessor typing logic must remain completely functional:
        val keyProcessor = KeyProcessor()
        var committedText: String? = null
        val fakeInputConnection = object : android.view.inputmethod.InputConnection {
            override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence = ""
            override fun getTextAfterCursor(n: Int, flags: Int): CharSequence = ""
            override fun getSelectedText(flags: Int): CharSequence = ""
            override fun getCursorCapsMode(reqModes: Int): Int = 0
            override fun getExtractedText(request: android.view.inputmethod.ExtractedTextRequest?, flags: Int): android.view.inputmethod.ExtractedText? = null
            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean = true
            override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean = true
            override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean = true
            override fun setComposingRegion(start: Int, end: Int): Boolean = true
            override fun finishComposingText(): Boolean = true
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                committedText = text?.toString()
                return true
            }
            override fun commitCompletion(text: android.view.inputmethod.CompletionInfo?): Boolean = true
            override fun commitCorrection(correctionInfo: android.view.inputmethod.CorrectionInfo?): Boolean = true
            override fun setSelection(start: Int, end: Int): Boolean = true
            override fun performEditorAction(editorAction: Int): Boolean = true
            override fun performContextMenuAction(id: Int): Boolean = true
            override fun beginBatchEdit(): Boolean = true
            override fun endBatchEdit(): Boolean = true
            override fun sendKeyEvent(event: android.view.KeyEvent?): Boolean = true
            override fun clearMetaKeyStates(states: Int): Boolean = true
            override fun reportFullscreenMode(enabled: Boolean): Boolean = true
            override fun performPrivateCommand(action: String?, data: android.os.Bundle?): Boolean = true
            override fun requestCursorUpdates(cursorUpdateMode: Int): Boolean = true
            override fun getHandler(): android.os.Handler? = null
            override fun closeConnection() {}
            override fun commitContent(inputContentInfo: android.view.inputmethod.InputContentInfo, flags: Int, opts: android.os.Bundle?): Boolean = true
        }

        keyProcessor.processKey(KeyAction.Character("A"), fakeInputConnection, null)
        assertEquals("Typing MUST succeed even when session is stopped (AC-32)", "A", committedText)
    }
}
