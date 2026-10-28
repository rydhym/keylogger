package com.example.inputmonitor.domain.privacy

/**
 * High-level categorization of input fields based on platform EditorInfo metadata
 * (Architecture.md §27, SECURITY.md §8).
 */
enum class FieldCategory {
    TEXT,
    EMAIL,
    URL,
    NUMBER,
    PHONE,
    MULTILINE,
    PASSWORD,
    NUMERIC_PASSWORD,
    OTHER_SENSITIVE,
    UNKNOWN;

    /**
     * Indicates whether this field category represents credential, PIN, password,
     * or secret information that must never have raw content persisted.
     */
    val isSensitive: Boolean
        get() = this in setOf(PASSWORD, NUMERIC_PASSWORD, OTHER_SENSITIVE)
}
