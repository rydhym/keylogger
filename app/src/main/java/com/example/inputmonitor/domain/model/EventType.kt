package com.example.inputmonitor.domain.model

/**
 * Category of interaction or lifecycle event (Architecture.md §35).
 */
enum class EventType {
    // Keyboard events
    KEY_PRESS,
    TEXT_COMMITTED,
    DELETE_KEY,
    ACTION_KEY,

    // Accessibility events
    VIEW_CLICKED,
    VIEW_FOCUSED,
    VIEW_SCROLLED,
    VIEW_SELECTED,
    WINDOW_STATE_CHANGED,
    WINDOWS_CHANGED,

    // Security / Lifecycle events
    SENSITIVE_INPUT_DETECTED,
    SESSION_START,
    SESSION_PAUSE,
    SESSION_RESUME,
    SESSION_STOP
}
