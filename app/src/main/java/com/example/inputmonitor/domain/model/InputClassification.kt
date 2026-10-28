package com.example.inputmonitor.domain.model

/**
 * Classification of the input field or interaction context (SECURITY.md §5, Architecture.md §35).
 * Used by PrivacyFilter and Repository to enforce sensitive input suppression.
 */
enum class InputClassification {
    UNSPECIFIED,
    NORMAL_TEXT,
    SEARCH_QUERY,
    EMAIL_ADDRESS,
    NUMBER,
    SENSITIVE_PASSWORD,
    SENSITIVE_PIN,
    SENSITIVE_CREDIT_CARD,
    SENSITIVE_OTP;

    /**
     * Returns true if this classification represents credentials, secrets, or sensitive inputs
     * that must never have raw text persisted.
     */
    val isSensitive: Boolean
        get() = this in setOf(
            SENSITIVE_PASSWORD,
            SENSITIVE_PIN,
            SENSITIVE_CREDIT_CARD,
            SENSITIVE_OTP
        )
}
