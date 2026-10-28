package com.example.inputmonitor.platform.ime

/**
 * Logical actions that can be triggered by keyboard keys (Architecture.md §31).
 */
sealed class KeyAction {
    data class Character(val text: String, val label: String = text) : KeyAction()
    data object Space : KeyAction()
    data object Backspace : KeyAction()
    data object Enter : KeyAction()
    data object Shift : KeyAction()
    data object SwitchIme : KeyAction()
    data class SwitchLayout(val target: KeyboardLayoutType) : KeyAction()
}

/**
 * Keyboard layout types (SRS.md §21, §23).
 */
enum class KeyboardLayoutType {
    QWERTY,
    EMAIL,
    URI,
    SYMBOLS,
    SYMBOLS_MORE,
    NUMERIC,
    PHONE
}

/**
 * Keyboard shift states.
 */
enum class ShiftState {
    OFF,
    ON_ONCE,
    CAPS_LOCK;

    val isShifted: Boolean
        get() = this == ON_ONCE || this == CAPS_LOCK
}
