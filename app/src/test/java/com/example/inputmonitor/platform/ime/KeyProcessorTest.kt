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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyProcessorTest {

    private open class FakeInputConnection : InputConnection {
        val committedText = mutableListOf<String>()
        val deletedSurrounding = mutableListOf<Pair<Int, Int>>()
        val performedActions = mutableListOf<Int>()
        val sentKeyEvents = mutableListOf<Int>()
        var selectedTextToReturn: CharSequence? = null
        var textBeforeCursor: String = ""

        override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence = textBeforeCursor.takeLast(n)
        override fun getTextAfterCursor(n: Int, flags: Int): CharSequence = ""
        override fun getSelectedText(flags: Int): CharSequence? = selectedTextToReturn
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
        override fun sendKeyEvent(event: KeyEvent?): Boolean {
            event?.keyCode?.let { sentKeyEvents.add(it) }
            return true
        }
        override fun clearMetaKeyStates(states: Int): Boolean = true
        override fun reportFullscreenMode(enabled: Boolean): Boolean = true
        override fun performPrivateCommand(action: String?, data: Bundle?): Boolean = true
        override fun requestCursorUpdates(cursorUpdateMode: Int): Boolean = true
        override fun getHandler(): Handler? = null
        override fun closeConnection() {}
        override fun commitContent(inputContentInfo: InputContentInfo, flags: Int, opts: Bundle?): Boolean = false
    }

    private val processor = KeyProcessor()

    @Test
    fun processKey_withNullConnection_returnsFalseSafely() {
        val result = processor.processKey(KeyAction.Character("a"), null)
        assertFalse(result)
    }

    @Test
    fun processKey_character_commitsText() {
        val fakeConnection = FakeInputConnection()
        val result = processor.processKey(KeyAction.Character("h"), fakeConnection)

        assertTrue(result)
        assertEquals(listOf("h"), fakeConnection.committedText)
    }

    @Test
    fun processKey_space_commitsSpace() {
        val fakeConnection = FakeInputConnection()
        val result = processor.processKey(KeyAction.Space, fakeConnection)

        assertTrue(result)
        assertEquals(listOf(" "), fakeConnection.committedText)
    }

    @Test
    fun processKey_emoji_commitsCompleteSequence() {
        val connection = FakeInputConnection()
        val emoji = "👨‍👩‍👧‍👦"
        assertTrue(processor.processKey(KeyAction.Character(emoji), connection))
        assertEquals(listOf(emoji), connection.committedText)
    }

    @Test
    fun processKey_backspace_deletesWholeOfferedEmoji() {
        listOf("😀", "❤️", "🇮🇳", "👍🏽", "👨‍👩‍👧‍👦").forEach { emoji ->
            val connection = FakeInputConnection().apply { textBeforeCursor = "hello$emoji" }
            assertTrue(processor.processKey(KeyAction.Backspace, connection))
            assertEquals(listOf(emoji.length to 0), connection.deletedSurrounding)
        }
    }

    @Test
    fun processKey_backspace_withoutSelection_deletesSurroundingText() {
        val fakeConnection = FakeInputConnection()
        val result = processor.processKey(KeyAction.Backspace, fakeConnection)

        assertTrue(result)
        assertEquals(1, fakeConnection.deletedSurrounding.size)
        assertEquals(1 to 0, fakeConnection.deletedSurrounding.first())
    }

    @Test
    fun processKey_backspace_withSelection_replacesSelection() {
        val fakeConnection = FakeInputConnection().apply {
            selectedTextToReturn = "selected text"
        }
        val result = processor.processKey(KeyAction.Backspace, fakeConnection)

        assertTrue(result)
        assertEquals(listOf(""), fakeConnection.committedText)
    }

    @Test
    fun processKey_enter_withAction_performsEditorAction() {
        val fakeConnection = FakeInputConnection()
        val editorInfo = EditorInfo().apply {
            imeOptions = EditorInfo.IME_ACTION_SEARCH
        }
        val result = processor.processKey(KeyAction.Enter, fakeConnection, editorInfo)

        assertTrue(result)
        assertEquals(listOf(EditorInfo.IME_ACTION_SEARCH), fakeConnection.performedActions)
    }

    @Test
    fun processKey_enter_multiline_commitsNewline() {
        val fakeConnection = FakeInputConnection()
        val editorInfo = EditorInfo().apply {
            imeOptions = EditorInfo.IME_ACTION_NONE
            inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE
        }
        val result = processor.processKey(KeyAction.Enter, fakeConnection, editorInfo)

        assertTrue(result)
        assertEquals(listOf("\n"), fakeConnection.committedText)
    }

    @Test
    fun processKey_handlesFailingConnection_safelyWithoutThrowing() {
        val failingConnection = object : FakeInputConnection() {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                throw RuntimeException("Target app IPC dead")
            }
        }

        val result = processor.processKey(KeyAction.Character("z"), failingConnection)
        assertFalse("Must catch remote IPC exceptions and return false", result)
    }
}
