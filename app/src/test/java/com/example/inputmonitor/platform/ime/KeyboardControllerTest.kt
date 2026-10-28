package com.example.inputmonitor.platform.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardControllerTest {

    @Test
    fun initialState_isShiftOffAndQwerty() {
        val controller = KeyboardController()
        assertEquals(ShiftState.OFF, controller.shiftState)
        assertEquals(KeyboardLayoutType.QWERTY, controller.layoutType)
        assertFalse(controller.shiftState.isShifted)

        val layout = controller.currentLayout.value
        assertEquals(KeyboardLayoutType.QWERTY, layout.type)
        assertEquals(4, layout.rows.size)
        assertEquals("q", layout.rows[0].keys[0].label)
    }

    @Test
    fun toggleShift_singleTap_cyclesThroughShiftStates() {
        val controller = KeyboardController()

        // OFF -> ON_ONCE
        controller.toggleShift()
        assertEquals(ShiftState.ON_ONCE, controller.shiftState)
        assertTrue(controller.shiftState.isShifted)
        assertEquals("Q", controller.currentLayout.value.rows[0].keys[0].label)

        // ON_ONCE -> OFF (when tapped after delay > 350ms)
        Thread.sleep(360)
        controller.toggleShift()
        assertEquals(ShiftState.OFF, controller.shiftState)
        assertFalse(controller.shiftState.isShifted)
        assertEquals("q", controller.currentLayout.value.rows[0].keys[0].label)
    }

    @Test
    fun toggleShift_doubleTap_activatesCapsLock() {
        val controller = KeyboardController()

        // Fast double-tap: OFF -> ON_ONCE -> CAPS_LOCK
        controller.toggleShift()
        controller.toggleShift()

        assertEquals(ShiftState.CAPS_LOCK, controller.shiftState)
        assertTrue(controller.shiftState.isShifted)
        assertEquals("⇪", controller.currentLayout.value.rows[2].keys[0].label)
        assertEquals("Q", controller.currentLayout.value.rows[0].keys[0].label)
    }

    @Test
    fun onCharacterTyped_whenShiftOnOnce_resetsToOff() {
        val controller = KeyboardController()
        controller.setShift(ShiftState.ON_ONCE)
        assertEquals(ShiftState.ON_ONCE, controller.shiftState)

        controller.onCharacterTyped()
        assertEquals(ShiftState.OFF, controller.shiftState)
        assertEquals("q", controller.currentLayout.value.rows[0].keys[0].label)
    }

    @Test
    fun onCharacterTyped_whenCapsLock_maintainsCapsLock() {
        val controller = KeyboardController()
        controller.setShift(ShiftState.CAPS_LOCK)
        assertEquals(ShiftState.CAPS_LOCK, controller.shiftState)

        controller.onCharacterTyped()
        assertEquals(ShiftState.CAPS_LOCK, controller.shiftState)
        assertEquals("Q", controller.currentLayout.value.rows[0].keys[0].label)
    }

    @Test
    fun updateEditorInfo_updatesEnterKeyLabel() {
        val controller = KeyboardController()

        controller.updateEditorInfo(EditorInfo().apply { imeOptions = EditorInfo.IME_ACTION_SEARCH })
        assertEquals("Search", controller.enterLabel)

        controller.updateEditorInfo(EditorInfo().apply { imeOptions = EditorInfo.IME_ACTION_GO })
        assertEquals("Go", controller.enterLabel)

        controller.updateEditorInfo(EditorInfo().apply { imeOptions = EditorInfo.IME_ACTION_SEND })
        assertEquals("Send", controller.enterLabel)

        controller.updateEditorInfo(EditorInfo().apply { imeOptions = EditorInfo.IME_ACTION_DONE })
        assertEquals("Done", controller.enterLabel)

        controller.updateEditorInfo(EditorInfo().apply { imeOptions = EditorInfo.IME_ACTION_NEXT })
        assertEquals("Next", controller.enterLabel)

        controller.updateEditorInfo(EditorInfo().apply { imeOptions = EditorInfo.IME_ACTION_NONE })
        assertEquals("↵", controller.enterLabel)
    }

    @Test
    fun updateEditorInfo_switchesToPhoneLayout() {
        val controller = KeyboardController()
        val info = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_PHONE
        }
        controller.updateEditorInfo(info)

        assertEquals(KeyboardLayoutType.PHONE, controller.layoutType)
        val layout = controller.currentLayout.value
        assertEquals(KeyboardLayoutType.PHONE, layout.type)
        assertEquals("1", layout.rows[0].keys[0].label)
    }

    @Test
    fun updateEditorInfo_switchesToNumericLayout() {
        val controller = KeyboardController()
        val info = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        controller.updateEditorInfo(info)

        assertEquals(KeyboardLayoutType.NUMERIC, controller.layoutType)
        val layout = controller.currentLayout.value
        assertEquals(KeyboardLayoutType.NUMERIC, layout.type)
        assertEquals("1", layout.rows[0].keys[0].label)
    }

    @Test
    fun updateEditorInfo_switchesToEmailLayout() {
        val controller = KeyboardController()
        val info = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }
        controller.updateEditorInfo(info)

        assertEquals(KeyboardLayoutType.EMAIL, controller.layoutType)
        val layout = controller.currentLayout.value
        assertEquals(KeyboardLayoutType.EMAIL, layout.type)
        // Email layout has @ in row 4
        assertTrue(layout.rows[3].keys.any { it.label == "@" })
        assertTrue(layout.rows[3].keys.any { it.label == ".com" })
    }

    @Test
    fun updateEditorInfo_switchesToUriLayout() {
        val controller = KeyboardController()
        val info = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        controller.updateEditorInfo(info)

        assertEquals(KeyboardLayoutType.URI, controller.layoutType)
        val layout = controller.currentLayout.value
        assertEquals(KeyboardLayoutType.URI, layout.type)
        // URI layout has / and .com
        assertTrue(layout.rows[3].keys.any { it.label == "/" })
        assertTrue(layout.rows[3].keys.any { it.label == ".com" })
    }

    @Test
    fun switchLayout_switchesBetweenSymbolsAndQwerty() {
        val controller = KeyboardController()

        controller.switchLayout(KeyboardLayoutType.SYMBOLS)
        assertEquals(KeyboardLayoutType.SYMBOLS, controller.layoutType)
        assertEquals(KeyboardLayoutType.SYMBOLS, controller.currentLayout.value.type)
        assertEquals("1", controller.currentLayout.value.rows[0].keys[0].label)

        controller.switchLayout(KeyboardLayoutType.SYMBOLS_MORE)
        assertEquals(KeyboardLayoutType.SYMBOLS_MORE, controller.layoutType)
        assertEquals("~", controller.currentLayout.value.rows[0].keys[0].label)

        controller.switchLayout(KeyboardLayoutType.QWERTY)
        assertEquals(KeyboardLayoutType.QWERTY, controller.layoutType)
        assertEquals("q", controller.currentLayout.value.rows[0].keys[0].label)
    }
}
