package com.example.inputmonitor.platform.ime

import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection

/**
 * Converts user key actions into target application InputConnection operations (Architecture.md §31).
 *
 * CRITICAL RULE (SRS.md §25, Architecture.md §2.1):
 * Input delivery MUST remain independent of logging, database, or network.
 * Failures in auxiliary components must never block or crash text entry.
 */
class KeyProcessor {

    /**
     * Executes the given [KeyAction] against the target [InputConnection].
     *
     * @param action The key action to process.
     * @param inputConnection The active Android InputConnection, or null if no editor is connected.
     * @param editorInfo Optional EditorInfo describing current action/field.
     * @return true if the action was handled and delivered to InputConnection.
     */
    fun processKey(
        action: KeyAction,
        inputConnection: InputConnection?,
        editorInfo: EditorInfo? = null
    ): Boolean {
        if (inputConnection == null) return false

        return try {
            when (action) {
                is KeyAction.Character -> {
                    inputConnection.commitText(action.text, 1)
                }
                is KeyAction.Space -> {
                    inputConnection.commitText(" ", 1)
                }
                is KeyAction.Backspace -> {
                    val selectedText = inputConnection.getSelectedText(0)
                    if (selectedText.isNullOrEmpty()) {
                        val beforeCursor = inputConnection.getTextBeforeCursor(128, 0)?.toString().orEmpty()
                        val deleted = inputConnection.deleteSurroundingText(EmojiCatalog.deletionLength(beforeCursor), 0)
                        if (!deleted) {
                            sendDownUpKeyEvents(inputConnection, KeyEvent.KEYCODE_DEL)
                        } else {
                            true
                        }
                    } else {
                        inputConnection.commitText("", 1)
                    }
                }
                is KeyAction.Enter -> {
                    val actionId = editorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
                    if (actionId != EditorInfo.IME_ACTION_NONE && actionId != EditorInfo.IME_ACTION_UNSPECIFIED) {
                        inputConnection.performEditorAction(actionId)
                    } else {
                        val isMultiline = (editorInfo?.inputType?.and(InputType.TYPE_TEXT_FLAG_MULTI_LINE)) != 0
                        if (isMultiline) {
                            inputConnection.commitText("\n", 1)
                        } else {
                            sendDownUpKeyEvents(inputConnection, KeyEvent.KEYCODE_ENTER)
                        }
                    }
                }
                is KeyAction.Shift,
                is KeyAction.SwitchIme,
                is KeyAction.SwitchLayout -> {
                    // Handled at controller level
                    true
                }
            }
        } catch (_: Exception) {
            // Guard against target application IPC disconnects (DeadObjectException, etc.)
            false
        }
    }

    private fun sendDownUpKeyEvents(inputConnection: InputConnection, keyCode: Int): Boolean {
        val down = inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        val up = inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        return down && up
    }
}
