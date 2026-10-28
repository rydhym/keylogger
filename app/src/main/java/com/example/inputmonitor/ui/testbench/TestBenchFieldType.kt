package com.example.inputmonitor.ui.testbench

import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.example.inputmonitor.domain.model.InputClassification

/**
 * Enumeration of the 7 controlled input test fields established as known ground truth
 * for IME input monitoring and privacy redaction validation (IMPLEMENTATION_PLAN.md §56, SRS.md §66, design.md §Screen 6).
 */
enum class TestBenchFieldType(
    val id: String,
    val displayName: String,
    val sampleValue: String,
    val keyboardType: KeyboardType,
    val imeAction: ImeAction,
    val isMultiline: Boolean,
    val expectedClassification: InputClassification,
    val isSensitive: Boolean,
    val helperText: String
) {
    NORMAL_TEXT(
        id = "normal_text",
        displayName = "Normal Text",
        sampleValue = "HELLO ANDROID",
        keyboardType = KeyboardType.Text,
        imeAction = ImeAction.Next,
        isMultiline = false,
        expectedClassification = InputClassification.NORMAL_TEXT,
        isSensitive = false,
        helperText = "Standard text; captured as NORMAL_TEXT with plain text data during active sessions (SRS.md §67, AC-29)."
    ),
    EMAIL(
        id = "email",
        displayName = "Email Address",
        sampleValue = "tester@example.com",
        keyboardType = KeyboardType.Email,
        imeAction = ImeAction.Next,
        isMultiline = false,
        expectedClassification = InputClassification.EMAIL_ADDRESS,
        isSensitive = false,
        helperText = "Email variation; captured as EMAIL_ADDRESS with synthetic data during active sessions (SRS.md §66)."
    ),
    URL(
        id = "url",
        displayName = "URL / Web Address",
        sampleValue = "https://example.org/test",
        keyboardType = KeyboardType.Uri,
        imeAction = ImeAction.Next,
        isMultiline = false,
        expectedClassification = InputClassification.NORMAL_TEXT,
        isSensitive = false,
        helperText = "URI variation; classified as NORMAL_TEXT with synthetic web address permitted (IMPLEMENTATION_PLAN.md §56)."
    ),
    NUMBER(
        id = "number",
        displayName = "Numeric Input",
        sampleValue = "9876543210",
        keyboardType = KeyboardType.Number,
        imeAction = ImeAction.Next,
        isMultiline = false,
        expectedClassification = InputClassification.NUMBER,
        isSensitive = false,
        helperText = "Numeric class; captured as NUMBER with synthetic digits during active sessions (SRS.md §66)."
    ),
    MULTILINE(
        id = "multiline",
        displayName = "Multiline Notes",
        sampleValue = "Synthetic Line 1\nSynthetic Line 2",
        keyboardType = KeyboardType.Text,
        imeAction = ImeAction.Default,
        isMultiline = true,
        expectedClassification = InputClassification.NORMAL_TEXT,
        isSensitive = false,
        helperText = "Multiline text; captured as NORMAL_TEXT with newline delimiters preserved (SRS.md §66)."
    ),
    PASSWORD(
        id = "password",
        displayName = "Password (Sensitive)",
        sampleValue = "SecretPassword123!",
        keyboardType = KeyboardType.Password,
        imeAction = ImeAction.Next,
        isMultiline = false,
        expectedClassification = InputClassification.SENSITIVE_PASSWORD,
        isSensitive = true,
        helperText = "Password field; must be classified as SENSITIVE_PASSWORD with zero raw text persistence (SRS.md §68, AC-30)."
    ),
    PIN(
        id = "pin",
        displayName = "Numeric PIN (Sensitive)",
        sampleValue = "4321",
        keyboardType = KeyboardType.NumberPassword,
        imeAction = ImeAction.Done,
        isMultiline = false,
        expectedClassification = InputClassification.SENSITIVE_PIN,
        isSensitive = true,
        helperText = "Numeric PIN field; must be classified as SENSITIVE_PIN with zero raw credential persistence (SRS.md §69, AC-30)."
    );

    companion object {
        fun fromId(id: String): TestBenchFieldType? = entries.find { it.id == id }
    }
}
