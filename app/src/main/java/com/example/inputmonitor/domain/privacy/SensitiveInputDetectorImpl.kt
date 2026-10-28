package com.example.inputmonitor.domain.privacy

import com.example.inputmonitor.domain.model.InputClassification

/**
 * Production implementation of [SensitiveInputDetector].
 * Strictly identifies sensitive credential, password, PIN, and OTP fields and maps them
 * to the appropriate [InputClassification] (Architecture.md §28, SECURITY.md §5, §6).
 */
class SensitiveInputDetectorImpl : SensitiveInputDetector {

    override fun classify(editorContext: EditorContext): InputClassification {
        // If the context explicitly has a sensitive suggested classification, respect it
        if (editorContext.suggestedClassification.isSensitive) {
            return editorContext.suggestedClassification
        }

        return when (editorContext.fieldCategory) {
            FieldCategory.PASSWORD -> InputClassification.SENSITIVE_PASSWORD
            FieldCategory.NUMERIC_PASSWORD -> InputClassification.SENSITIVE_PIN
            FieldCategory.OTHER_SENSITIVE -> {
                val hint = editorContext.hintText?.lowercase() ?: ""
                when {
                    hint.contains("card") || hint.contains("cvv") || hint.contains("cvc") || hint.contains("security code") -> {
                        InputClassification.SENSITIVE_CREDIT_CARD
                    }
                    hint.contains("otp") || hint.contains("verification") || hint.contains("2fa") || hint.contains("auth code") -> {
                        InputClassification.SENSITIVE_OTP
                    }
                    hint.contains("pin") -> {
                        InputClassification.SENSITIVE_PIN
                    }
                    else -> InputClassification.SENSITIVE_PASSWORD
                }
            }
            FieldCategory.EMAIL -> InputClassification.EMAIL_ADDRESS
            FieldCategory.URL -> InputClassification.NORMAL_TEXT
            FieldCategory.NUMBER, FieldCategory.PHONE -> InputClassification.NUMBER
            FieldCategory.TEXT, FieldCategory.MULTILINE -> {
                if (editorContext.imeActionLabel.equals("Search", ignoreCase = true)) {
                    InputClassification.SEARCH_QUERY
                } else {
                    InputClassification.NORMAL_TEXT
                }
            }
            FieldCategory.UNKNOWN -> {
                if (editorContext.isSensitive) {
                    InputClassification.SENSITIVE_PASSWORD
                } else {
                    InputClassification.UNSPECIFIED
                }
            }
        }
    }

    override fun isSensitive(editorContext: EditorContext): Boolean {
        return editorContext.isSensitive || classify(editorContext).isSensitive
    }
}
