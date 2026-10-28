package com.example.inputmonitor.domain.model

/**
 * State of a monitoring session (Architecture.md §43).
 */
enum class SessionState {
    INACTIVE,
    ACTIVE,
    PAUSED,
    STOPPED
}
