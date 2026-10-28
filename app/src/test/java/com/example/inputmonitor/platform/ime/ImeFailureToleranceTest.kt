package com.example.inputmonitor.platform.ime

import android.os.Bundle
import android.os.Handler
import android.view.KeyEvent
import android.view.inputmethod.CompletionInfo
import android.view.inputmethod.CorrectionInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputContentInfo
import com.example.inputmonitor.domain.model.RawInteractionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Mandatory failure tolerance test (§44, Architecture.md §2.1, §32, TESTING.md §9).
 *
 * Simulates catastrophic failure of the logging, database, and event processing pipeline.
 * Proves that keyboard typing, text committing, and InputConnection interaction remain
 * 100% operational with zero user-perceptible degradation.
 */
class ImeFailureToleranceTest {

    private class RecordingInputConnection : InputConnection {
        val committedText = mutableListOf<String>()
        val deletedSurrounding = mutableListOf<Pair<Int, Int>>()
        val performedActions = mutableListOf<Int>()

        override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence = ""
        override fun getTextAfterCursor(n: Int, flags: Int): CharSequence = ""
        override fun getSelectedText(flags: Int): CharSequence? = null
        override fun getCursorCapsMode(reqModes: Int): Int = 0
        override fun getExtractedText(request: ExtractedTextRequest?, flags: Int): ExtractedText? = null
        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            deletedSurrounding.add(beforeLength to afterLength)
            return true
        }
        override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean = true
        override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean = true
        override fun setComposingRegion(start: Int, end: Int): Boolean = true
        override fun finishComposingText(): Boolean = true
        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            committedText.add(text?.toString() ?: "")
            return true
        }
        override fun commitCompletion(text: CompletionInfo?): Boolean = true
        override fun commitCorrection(correctionInfo: CorrectionInfo?): Boolean = true
        override fun setSelection(start: Int, end: Int): Boolean = true
        override fun performEditorAction(editorAction: Int): Boolean {
            performedActions.add(editorAction)
            return true
        }
        override fun performContextMenuAction(id: Int): Boolean = true
        override fun beginBatchEdit(): Boolean = true
        override fun endBatchEdit(): Boolean = true
        override fun sendKeyEvent(event: KeyEvent?): Boolean = true
        override fun clearMetaKeyStates(states: Int): Boolean = true
        override fun reportFullscreenMode(enabled: Boolean): Boolean = true
        override fun performPrivateCommand(action: String?, data: Bundle?): Boolean = true
        override fun requestCursorUpdates(cursorUpdateMode: Int): Boolean = true
        override fun getHandler(): Handler? = null
        override fun closeConnection() {}
        override fun commitContent(inputContentInfo: InputContentInfo, flags: Int, opts: Bundle?): Boolean = true
    }

    private class CrashingEventPublisher : ImeEventPublisher {
        var failureCallCount = 0

        override fun publish(event: RawInteractionEvent) {
            failureCallCount++
            throw RuntimeException("Simulated Catastrophic Pipeline Crash ($failureCallCount)")
        }
    }

    private lateinit var keyboardService: CustomKeyboardService
    private lateinit var inputConnection: RecordingInputConnection
    private lateinit var crashingPublisher: CrashingEventPublisher

    @Before
    fun setUp() {
        keyboardService = CustomKeyboardService()
        inputConnection = RecordingInputConnection()
        crashingPublisher = CrashingEventPublisher()

        val editorInfo = EditorInfo().apply {
            packageName = "com.example.targetapp"
            imeOptions = EditorInfo.IME_ACTION_DONE
        }

        keyboardService.inputConnectionOverride = inputConnection
        keyboardService.editorInfoOverride = editorInfo
        keyboardService.publisherOverride = crashingPublisher
    }

    @Test
    fun typing_whenPipelineCrashesCatastrophically_typingContinuesFlawlessly() {
        // User types: "Hello World"
        val typedChars = listOf("H", "e", "l", "l", "o")
        for (char in typedChars) {
            keyboardService.handleKeyAction(KeyAction.Character(char))
        }

        // Tap Space
        keyboardService.handleKeyAction(KeyAction.Space)

        // Type "W", "o", "r", "l", "d"
        val remainingChars = listOf("W", "o", "r", "l", "d")
        for (char in remainingChars) {
            keyboardService.handleKeyAction(KeyAction.Character(char))
        }

        // Tap Backspace
        keyboardService.handleKeyAction(KeyAction.Backspace)

        // Tap Enter
        keyboardService.handleKeyAction(KeyAction.Enter)

        // Verify that InputConnection received all character commits without any loss
        assertEquals(listOf("H", "e", "l", "l", "o", " ", "W", "o", "r", "l", "d"), inputConnection.committedText)

        // Verify Backspace deleted surrounding text
        assertEquals(1, inputConnection.deletedSurrounding.size)
        assertEquals(1 to 0, inputConnection.deletedSurrounding.first())

        // Verify Enter performed editor action
        assertEquals(1, inputConnection.performedActions.size)
        assertEquals(EditorInfo.IME_ACTION_DONE, inputConnection.performedActions.first())

        // Verify pipeline crash was triggered on each key, but was completely isolated
        assertTrue(crashingPublisher.failureCallCount >= 13)
    }

    @Test
    fun typing_whenPublisherIsNull_typingOperatesNormally() {
        keyboardService.publisherOverride = null

        keyboardService.handleKeyAction(KeyAction.Character("A"))
        keyboardService.handleKeyAction(KeyAction.Space)
        keyboardService.handleKeyAction(KeyAction.Backspace)

        assertEquals(listOf("A", " "), inputConnection.committedText)
        assertEquals(1, inputConnection.deletedSurrounding.size)
    }

    @Test
    fun keyProcessor_pureInputConnectionIndependence() {
        val processor = KeyProcessor()
        val conn = RecordingInputConnection()
        val info = EditorInfo()

        // KeyProcessor works directly and exclusively with InputConnection
        processor.processKey(KeyAction.Character("k"), conn, info)
        processor.processKey(KeyAction.Space, conn, info)
        processor.processKey(KeyAction.Backspace, conn, info)

        assertEquals(listOf("k", " "), conn.committedText)
        assertEquals(listOf(1 to 0), conn.deletedSurrounding)
    }
}
