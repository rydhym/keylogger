package com.example.inputmonitor.platform.ime.analysis

import android.os.Bundle
import android.text.InputType
import android.view.inputmethod.EditorInfo
import com.example.inputmonitor.domain.model.InputClassification
import com.example.inputmonitor.domain.privacy.FieldCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive unit tests for [EditorInfoAnalyzer] (Architecture.md §26, §27, TESTING.md §4, §5).
 */
class EditorInfoAnalyzerTest {

    private lateinit var analyzer: EditorInfoAnalyzer

    @Before
    fun setUp() {
        analyzer = EditorInfoAnalyzerImpl()
    }

    @Test
    fun analyze_nullEditorInfo_returnsUnknownCategoryAndNonSensitive() {
        val context = analyzer.analyze(null)

        assertEquals(FieldCategory.UNKNOWN, context.fieldCategory)
        assertFalse(context.isSensitive)
        assertFalse(context.isMultiline)
        assertEquals("↵", context.imeActionLabel)
        assertNull(context.packageName)
        assertEquals(InputClassification.UNSPECIFIED, context.suggestedClassification)
    }

    @Test
    fun analyze_normalTextField_returnsTextCategory() {
        val editorInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL
            imeOptions = EditorInfo.IME_ACTION_DONE
            packageName = "com.example.notes"
        }

        val context = analyzer.analyze(editorInfo)

        assertEquals(FieldCategory.TEXT, context.fieldCategory)
        assertFalse(context.isSensitive)
        assertFalse(context.isMultiline)
        assertEquals("Done", context.imeActionLabel)
        assertEquals("com.example.notes", context.packageName)
        assertEquals(InputClassification.NORMAL_TEXT, context.suggestedClassification)
    }

    @Test
    fun analyze_multilineTextField_returnsMultilineCategory() {
        val editorInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            imeOptions = EditorInfo.IME_ACTION_NONE
        }

        val context = analyzer.analyze(editorInfo)

        assertEquals(FieldCategory.MULTILINE, context.fieldCategory)
        assertTrue(context.isMultiline)
        assertFalse(context.isSensitive)
        assertEquals(InputClassification.NORMAL_TEXT, context.suggestedClassification)
    }

    @Test
    fun analyze_searchTextField_returnsSearchQueryClassification() {
        val editorInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEARCH
        }

        val context = analyzer.analyze(editorInfo)

        assertEquals(FieldCategory.TEXT, context.fieldCategory)
        assertEquals("Search", context.imeActionLabel)
        assertEquals(InputClassification.SEARCH_QUERY, context.suggestedClassification)
    }

    @Test
    fun analyze_emailAddressField_returnsEmailCategory() {
        val editorInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            imeOptions = EditorInfo.IME_ACTION_NEXT
        }

        val context = analyzer.analyze(editorInfo)

        assertEquals(FieldCategory.EMAIL, context.fieldCategory)
        assertFalse(context.isSensitive)
        assertEquals("Next", context.imeActionLabel)
        assertEquals(InputClassification.EMAIL_ADDRESS, context.suggestedClassification)
    }

    @Test
    fun analyze_webEmailAddressField_returnsEmailCategory() {
        val editorInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
        }

        val context = analyzer.analyze(editorInfo)

        assertEquals(FieldCategory.EMAIL, context.fieldCategory)
        assertEquals(InputClassification.EMAIL_ADDRESS, context.suggestedClassification)
    }

    @Test
    fun analyze_uriField_returnsUrlCategory() {
        val editorInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
        }

        val context = analyzer.analyze(editorInfo)

        assertEquals(FieldCategory.URL, context.fieldCategory)
        assertFalse(context.isSensitive)
        assertEquals("Go", context.imeActionLabel)
        assertEquals(InputClassification.NORMAL_TEXT, context.suggestedClassification)
    }

    @Test
    fun analyze_standardPasswordField_returnsPasswordCategoryAndSensitive() {
        val editorInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        val context = analyzer.analyze(editorInfo)

        assertEquals(FieldCategory.PASSWORD, context.fieldCategory)
        assertTrue(context.isSensitive)
        assertEquals(InputClassification.SENSITIVE_PASSWORD, context.suggestedClassification)
    }

    @Test
    fun analyze_visiblePasswordField_returnsPasswordCategoryAndSensitive() {
        val editorInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        }

        val context = analyzer.analyze(editorInfo)

        assertEquals(FieldCategory.PASSWORD, context.fieldCategory)
        assertTrue(context.isSensitive)
        assertEquals(InputClassification.SENSITIVE_PASSWORD, context.suggestedClassification)
    }

    @Test
    fun analyze_webPasswordField_returnsPasswordCategoryAndSensitive() {
        val editorInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        }

        val context = analyzer.analyze(editorInfo)

        assertEquals(FieldCategory.PASSWORD, context.fieldCategory)
        assertTrue(context.isSensitive)
        assertEquals(InputClassification.SENSITIVE_PASSWORD, context.suggestedClassification)
    }

    @Test
    fun analyze_numericPasswordFieldOrPin_returnsNumericPasswordCategoryAndSensitive() {
        val editorInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }

        val context = analyzer.analyze(editorInfo)

        assertEquals(FieldCategory.NUMERIC_PASSWORD, context.fieldCategory)
        assertTrue(context.isSensitive)
        assertEquals(InputClassification.SENSITIVE_PIN, context.suggestedClassification)
    }

    @Test
    fun analyze_standardNumberField_returnsNumberCategory() {
        val editorInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }

        val context = analyzer.analyze(editorInfo)

        assertEquals(FieldCategory.NUMBER, context.fieldCategory)
        assertFalse(context.isSensitive)
        assertEquals(InputClassification.NUMBER, context.suggestedClassification)
    }

    @Test
    fun analyze_phoneField_returnsPhoneCategory() {
        val editorInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_PHONE
            imeOptions = EditorInfo.IME_ACTION_SEND
        }

        val context = analyzer.analyze(editorInfo)

        assertEquals(FieldCategory.PHONE, context.fieldCategory)
        assertFalse(context.isSensitive)
        assertEquals("Send", context.imeActionLabel)
        assertEquals(InputClassification.NUMBER, context.suggestedClassification)
    }

    @Test
    fun analyze_typeNullField_returnsUnknownCategory() {
        val editorInfo = EditorInfo().apply {
            inputType = InputType.TYPE_NULL
        }

        val context = analyzer.analyze(editorInfo)

        assertEquals(FieldCategory.UNKNOWN, context.fieldCategory)
        assertFalse(context.isSensitive)
    }

    @Test
    fun analyze_sensitiveHintFallback_classifiesAsSensitive() {
        val editorInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hintText = "Enter OTP verification code"
        }

        val context = analyzer.analyze(editorInfo)

        assertEquals(FieldCategory.OTHER_SENSITIVE, context.fieldCategory)
        assertTrue(context.isSensitive)
        assertEquals(InputClassification.SENSITIVE_OTP, context.suggestedClassification)
    }

    @Test
    fun analyze_fieldNameFallback_classifiesAsSensitive() {
        val editorInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT
            fieldName = "user_account_password"
        }

        val context = analyzer.analyze(editorInfo)

        assertEquals(FieldCategory.PASSWORD, context.fieldCategory)
        assertTrue(context.isSensitive)
        assertEquals(InputClassification.SENSITIVE_PASSWORD, context.suggestedClassification)
    }
}
