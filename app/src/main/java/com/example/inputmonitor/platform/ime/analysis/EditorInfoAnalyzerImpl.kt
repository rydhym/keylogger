package com.example.inputmonitor.platform.ime.analysis

import android.os.Bundle
import android.text.InputType
import android.view.inputmethod.EditorInfo
import com.example.inputmonitor.domain.model.InputClassification
import com.example.inputmonitor.domain.privacy.EditorContext
import com.example.inputmonitor.domain.privacy.FieldCategory

/**
 * Production implementation of [EditorInfoAnalyzer] (Architecture.md §26, §27, SECURITY.md §8).
 *
 * Inspects bitmasks, variations, autofill hints, and hint text to accurately classify
 * password, PIN, credential, and standard input fields.
 */
class EditorInfoAnalyzerImpl : EditorInfoAnalyzer {

    override fun analyze(editorInfo: EditorInfo?): EditorContext {
        if (editorInfo == null) {
            return EditorContext(
                fieldCategory = FieldCategory.UNKNOWN,
                isSensitive = false,
                isMultiline = false,
                imeAction = EditorInfo.IME_ACTION_NONE,
                imeActionLabel = "↵",
                packageName = null,
                inputType = 0,
                hintText = null,
                suggestedClassification = InputClassification.UNSPECIFIED
            )
        }

        val inputType = editorInfo.inputType
        val inputClass = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        val flags = inputType and InputType.TYPE_MASK_FLAGS
        val isMultiline = (flags and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0

        val imeAction = editorInfo.imeOptions and EditorInfo.IME_MASK_ACTION
        val imeActionLabel = resolveActionLabel(imeAction, isMultiline)

        val hint = editorInfo.hintText?.toString()
        val packageName = editorInfo.packageName

        // 1. Check field name, hint text, and extras for explicit sensitive hints
        val attributeClassification = checkFieldAttributes(editorInfo.fieldName, hint, editorInfo.extras)
        if (attributeClassification != null) {
            val category = when (attributeClassification) {
                InputClassification.SENSITIVE_PASSWORD -> FieldCategory.PASSWORD
                InputClassification.SENSITIVE_PIN -> FieldCategory.NUMERIC_PASSWORD
                InputClassification.SENSITIVE_CREDIT_CARD,
                InputClassification.SENSITIVE_OTP -> FieldCategory.OTHER_SENSITIVE
                else -> FieldCategory.TEXT
            }
            return EditorContext(
                fieldCategory = category,
                isSensitive = true,
                isMultiline = isMultiline,
                imeAction = imeAction,
                imeActionLabel = imeActionLabel,
                packageName = packageName,
                inputType = inputType,
                hintText = hint,
                suggestedClassification = attributeClassification
            )
        }

        // 2. Classify by InputType bitmasks
        var fieldCategory: FieldCategory
        var isSensitive = false
        var suggestedClassification = InputClassification.UNSPECIFIED

        when (inputClass) {
            InputType.TYPE_CLASS_PHONE -> {
                fieldCategory = FieldCategory.PHONE
                suggestedClassification = InputClassification.NUMBER
            }
            InputType.TYPE_CLASS_NUMBER -> {
                if (variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD) {
                    fieldCategory = FieldCategory.NUMERIC_PASSWORD
                    isSensitive = true
                    suggestedClassification = InputClassification.SENSITIVE_PIN
                } else {
                    fieldCategory = FieldCategory.NUMBER
                    suggestedClassification = InputClassification.NUMBER
                }
            }
            InputType.TYPE_CLASS_TEXT -> {
                when (variation) {
                    InputType.TYPE_TEXT_VARIATION_PASSWORD,
                    InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                    InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD -> {
                        fieldCategory = FieldCategory.PASSWORD
                        isSensitive = true
                        suggestedClassification = InputClassification.SENSITIVE_PASSWORD
                    }
                    InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                    InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> {
                        fieldCategory = FieldCategory.EMAIL
                        suggestedClassification = InputClassification.EMAIL_ADDRESS
                    }
                    InputType.TYPE_TEXT_VARIATION_URI -> {
                        fieldCategory = FieldCategory.URL
                        suggestedClassification = InputClassification.NORMAL_TEXT
                    }
                    else -> {
                        if (isMultiline) {
                            fieldCategory = FieldCategory.MULTILINE
                            suggestedClassification = InputClassification.NORMAL_TEXT
                        } else if (imeAction == EditorInfo.IME_ACTION_SEARCH) {
                            fieldCategory = FieldCategory.TEXT
                            suggestedClassification = InputClassification.SEARCH_QUERY
                        } else {
                            fieldCategory = FieldCategory.TEXT
                            suggestedClassification = InputClassification.NORMAL_TEXT
                        }
                    }
                }
            }
            InputType.TYPE_NULL -> {
                fieldCategory = FieldCategory.UNKNOWN
                suggestedClassification = InputClassification.UNSPECIFIED
            }
            else -> {
                fieldCategory = FieldCategory.UNKNOWN
                suggestedClassification = InputClassification.UNSPECIFIED
            }
        }

        return EditorContext(
            fieldCategory = fieldCategory,
            isSensitive = isSensitive,
            isMultiline = isMultiline,
            imeAction = imeAction,
            imeActionLabel = imeActionLabel,
            packageName = packageName,
            inputType = inputType,
            hintText = hint,
            suggestedClassification = suggestedClassification
        )
    }

    private fun resolveActionLabel(action: Int, isMultiline: Boolean): String {
        return when (action) {
            EditorInfo.IME_ACTION_GO -> "Go"
            EditorInfo.IME_ACTION_SEARCH -> "Search"
            EditorInfo.IME_ACTION_SEND -> "Send"
            EditorInfo.IME_ACTION_NEXT -> "Next"
            EditorInfo.IME_ACTION_DONE -> "Done"
            else -> "↵"
        }
    }

    private fun checkFieldAttributes(fieldName: String?, hint: String?, extras: Bundle?): InputClassification? {
        val candidates = mutableListOf<String>()
        if (fieldName != null) candidates.add(fieldName)
        if (hint != null) candidates.add(hint)
        if (extras != null) {
            val hints = extras.getStringArray("android.view.autofill.extra.HINTS")
            if (hints != null) candidates.addAll(hints)
        }

        for (candidate in candidates) {
            val lower = candidate.lowercase()
            when {
                lower.contains("password") || lower.contains("passcode") || lower.contains("secret") -> {
                    return InputClassification.SENSITIVE_PASSWORD
                }
                lower.contains("creditcardsecuritycode") || lower.contains("cvv") || lower.contains("cvc") ||
                lower.contains("creditcardnumber") || lower.contains("card_number") -> {
                    return InputClassification.SENSITIVE_CREDIT_CARD
                }
                lower.contains("otp") || lower.contains("smsotpkey") || lower.contains("verification_code") || lower.contains("2fa") -> {
                    return InputClassification.SENSITIVE_OTP
                }
                lower.contains("pin") -> {
                    return InputClassification.SENSITIVE_PIN
                }
            }
        }
        return null
    }
}
