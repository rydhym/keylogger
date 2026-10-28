package com.example.inputmonitor.platform.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardLayoutTest {

    @Test
    fun qwertyLayout_hasCorrectStructure() {
        val layout = KeyboardLayout.createQwertyLayout(shiftState = ShiftState.OFF, enterLabel = "↵")

        assertEquals(KeyboardLayoutType.QWERTY, layout.type)
        assertEquals(4, layout.rows.size)

        // Row 1: 10 keys (q to p)
        assertEquals(10, layout.rows[0].keys.size)
        assertEquals("q", layout.rows[0].keys.first().label)
        assertEquals("p", layout.rows[0].keys.last().label)

        // Row 2: 9 keys (a to l)
        assertEquals(9, layout.rows[1].keys.size)
        assertEquals("a", layout.rows[1].keys.first().label)
        assertEquals("l", layout.rows[1].keys.last().label)

        // Row 3: 9 keys (Shift + 7 letters + Backspace)
        assertEquals(9, layout.rows[2].keys.size)
        assertTrue(layout.rows[2].keys.first().action is KeyAction.Shift)
        assertEquals("z", layout.rows[2].keys[1].label)
        assertEquals("m", layout.rows[2].keys[7].label)
        assertTrue(layout.rows[2].keys.last().action is KeyAction.Backspace)

        // Row 4: 6 keys (?123, comma, emoji/IME switch, space, period, enter)
        assertEquals(6, layout.rows[3].keys.size)
        assertTrue(layout.rows[3].keys[0].action is KeyAction.SwitchLayout)
        assertEquals(",", layout.rows[3].keys[1].label)
        assertTrue(layout.rows[3].keys[2].action is KeyAction.SwitchIme)
        assertTrue(layout.rows[3].keys[3].action is KeyAction.Space)
        assertEquals(".", layout.rows[3].keys[4].label)
        assertTrue(layout.rows[3].keys[5].action is KeyAction.Enter)
    }

    @Test
    fun qwertyLayout_shifted_and_capsLock_labels() {
        val unshifted = KeyboardLayout.createQwertyLayout(shiftState = ShiftState.OFF)
        val shifted = KeyboardLayout.createQwertyLayout(shiftState = ShiftState.ON_ONCE)
        val capsLock = KeyboardLayout.createQwertyLayout(shiftState = ShiftState.CAPS_LOCK)

        assertEquals("q", unshifted.rows[0].keys[0].label)
        assertEquals("⇧", unshifted.rows[2].keys[0].label)

        assertEquals("Q", shifted.rows[0].keys[0].label)
        assertEquals("⬆", shifted.rows[2].keys[0].label)

        assertEquals("Q", capsLock.rows[0].keys[0].label)
        assertEquals("⇪", capsLock.rows[2].keys[0].label)
    }

    @Test
    fun symbolsLayout_hasNumbersAndPunctuation() {
        val layout = KeyboardLayout.createSymbolsLayout(enterLabel = "↵")

        assertEquals(KeyboardLayoutType.SYMBOLS, layout.type)
        assertEquals(4, layout.rows.size)

        // Row 1: 1-0
        assertEquals(10, layout.rows[0].keys.size)
        assertEquals("1", layout.rows[0].keys.first().label)
        assertEquals("0", layout.rows[0].keys.last().label)

        // Row 2: @ to /
        assertEquals(10, layout.rows[1].keys.size)
        assertEquals("@", layout.rows[1].keys.first().label)
        assertEquals("/", layout.rows[1].keys.last().label)

        // Row 3: =<\ to ? + Backspace
        assertTrue(layout.rows[2].keys.first().action is KeyAction.SwitchLayout)
        assertTrue(layout.rows[2].keys.last().action is KeyAction.Backspace)

        // Row 4: ABC switch
        assertTrue(layout.rows[3].keys.first().action is KeyAction.SwitchLayout)
    }

    @Test
    fun symbolsMoreLayout_hasExtendedSymbols() {
        val layout = KeyboardLayout.createSymbolsMoreLayout(enterLabel = "↵")

        assertEquals(KeyboardLayoutType.SYMBOLS_MORE, layout.type)
        assertEquals(4, layout.rows.size)
        assertEquals("~", layout.rows[0].keys.first().label)
        assertEquals("=", layout.rows[0].keys.last().label)
    }

    @Test
    fun numericAndPhoneLayouts_haveDialpadKeys() {
        val numeric = KeyboardLayout.createNumericLayout()
        assertEquals(KeyboardLayoutType.NUMERIC, numeric.type)
        assertEquals(4, numeric.rows.size)
        assertEquals("1", numeric.rows[0].keys[0].label)

        val phone = KeyboardLayout.createPhoneLayout()
        assertEquals(KeyboardLayoutType.PHONE, phone.type)
        assertEquals(4, phone.rows.size)
        assertTrue(phone.rows[3].keys.any { it.label == "+" })
        assertTrue(phone.rows[3].keys.any { it.label == "#" })
    }

    @Test
    fun emailAndUriLayouts_haveContextualKeys() {
        val email = KeyboardLayout.createQwertyLayout(type = KeyboardLayoutType.EMAIL)
        assertTrue(email.rows[3].keys.any { it.label == "@" })
        assertTrue(email.rows[3].keys.any { it.label == ".com" })

        val uri = KeyboardLayout.createQwertyLayout(type = KeyboardLayoutType.URI)
        assertTrue(uri.rows[3].keys.any { it.label == "/" })
        assertTrue(uri.rows[3].keys.any { it.label == ".com" })
    }
}
