package com.example.inputmonitor.domain.privacy

import com.example.inputmonitor.domain.model.InputClassification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [SensitiveInputDetector] (Architecture.md §28, SECURITY.md §5, §6, TESTING.md §5).
 */
class SensitiveInputDetectorTest {

    private lateinit var detector: SensitiveInputDetector

    @Before
    fun setUp() {
        detector = SensitiveInputDetectorImpl()
    }

    @Test
    fun classify_passwordCategory_returnsSensitivePassword() {
        val context = EditorContext(
            fieldCategory = FieldCategory.PASSWORD,
            isSensitive = true,
            isMultiline = false,
            imeAction = 0,
            imeActionLabel = "↵",
            packageName = "com.example.app"
        )

        val classification = detector.classify(context)

        assertEquals(InputClassification.SENSITIVE_PASSWORD, classification)
        assertTrue(classification.isSensitive)
        assertTrue(detector.isSensitive(context))
    }

    @Test
    fun classify_numericPasswordCategory_returnsSensitivePin() {
        val context = EditorContext(
            fieldCategory = FieldCategory.NUMERIC_PASSWORD,
            isSensitive = true,
            isMultiline = false,
            imeAction = 0,
            imeActionLabel = "↵",
            packageName = "com.example.bank"
        )

        val classification = detector.classify(context)

        assertEquals(InputClassification.SENSITIVE_PIN, classification)
        assertTrue(classification.isSensitive)
        assertTrue(detector.isSensitive(context))
    }

    @Test
    fun classify_otherSensitiveCategoryWithOtpHint_returnsSensitiveOtp() {
        val context = EditorContext(
            fieldCategory = FieldCategory.OTHER_SENSITIVE,
            isSensitive = true,
            isMultiline = false,
            imeAction = 0,
            imeActionLabel = "↵",
            packageName = "com.example.auth",
            hintText = "Enter SMS verification code"
        )

        val classification = detector.classify(context)

        assertEquals(InputClassification.SENSITIVE_OTP, classification)
        assertTrue(classification.isSensitive)
        assertTrue(detector.isSensitive(context))
    }

    @Test
    fun classify_otherSensitiveCategoryWithCardCvvHint_returnsSensitiveCreditCard() {
        val context = EditorContext(
            fieldCategory = FieldCategory.OTHER_SENSITIVE,
            isSensitive = true,
            isMultiline = false,
            imeAction = 0,
            imeActionLabel = "↵",
            packageName = "com.example.shop",
            hintText = "Card CVV security code"
        )

        val classification = detector.classify(context)

        assertEquals(InputClassification.SENSITIVE_CREDIT_CARD, classification)
        assertTrue(classification.isSensitive)
        assertTrue(detector.isSensitive(context))
    }

    @Test
    fun classify_emailCategory_returnsEmailAddress() {
        val context = EditorContext(
            fieldCategory = FieldCategory.EMAIL,
            isSensitive = false,
            isMultiline = false,
            imeAction = 0,
            imeActionLabel = "Next",
            packageName = "com.example.mail"
        )

        val classification = detector.classify(context)

        assertEquals(InputClassification.EMAIL_ADDRESS, classification)
        assertFalse(classification.isSensitive)
        assertFalse(detector.isSensitive(context))
    }

    @Test
    fun classify_urlCategory_returnsNormalText() {
        val context = EditorContext(
            fieldCategory = FieldCategory.URL,
            isSensitive = false,
            isMultiline = false,
            imeAction = 0,
            imeActionLabel = "Go",
            packageName = "com.android.chrome"
        )

        val classification = detector.classify(context)

        assertEquals(InputClassification.NORMAL_TEXT, classification)
        assertFalse(classification.isSensitive)
        assertFalse(detector.isSensitive(context))
    }

    @Test
    fun classify_numberAndPhoneCategories_returnNumber() {
        val numberContext = EditorContext(
            fieldCategory = FieldCategory.NUMBER,
            isSensitive = false,
            isMultiline = false,
            imeAction = 0,
            imeActionLabel = "↵",
            packageName = "com.example.calc"
        )
        val phoneContext = EditorContext(
            fieldCategory = FieldCategory.PHONE,
            isSensitive = false,
            isMultiline = false,
            imeAction = 0,
            imeActionLabel = "↵",
            packageName = "com.android.dialer"
        )

        assertEquals(InputClassification.NUMBER, detector.classify(numberContext))
        assertEquals(InputClassification.NUMBER, detector.classify(phoneContext))
        assertFalse(detector.isSensitive(numberContext))
        assertFalse(detector.isSensitive(phoneContext))
    }

    @Test
    fun classify_textCategoryWithSearchAction_returnsSearchQuery() {
        val context = EditorContext(
            fieldCategory = FieldCategory.TEXT,
            isSensitive = false,
            isMultiline = false,
            imeAction = 0,
            imeActionLabel = "Search",
            packageName = "com.google.android.googlequicksearchbox"
        )

        val classification = detector.classify(context)

        assertEquals(InputClassification.SEARCH_QUERY, classification)
        assertFalse(classification.isSensitive)
    }

    @Test
    fun classify_standardTextAndMultilineCategories_returnNormalText() {
        val textContext = EditorContext(
            fieldCategory = FieldCategory.TEXT,
            isSensitive = false,
            isMultiline = false,
            imeAction = 0,
            imeActionLabel = "↵",
            packageName = "com.example.notes"
        )
        val multilineContext = EditorContext(
            fieldCategory = FieldCategory.MULTILINE,
            isSensitive = false,
            isMultiline = true,
            imeAction = 0,
            imeActionLabel = "↵",
            packageName = "com.example.notes"
        )

        assertEquals(InputClassification.NORMAL_TEXT, detector.classify(textContext))
        assertEquals(InputClassification.NORMAL_TEXT, detector.classify(multilineContext))
        assertFalse(detector.isSensitive(textContext))
        assertFalse(detector.isSensitive(multilineContext))
    }

    @Test
    fun classify_unknownCategoryWithoutSensitivity_returnsUnspecified() {
        val context = EditorContext(
            fieldCategory = FieldCategory.UNKNOWN,
            isSensitive = false,
            isMultiline = false,
            imeAction = 0,
            imeActionLabel = "↵",
            packageName = null
        )

        val classification = detector.classify(context)

        assertEquals(InputClassification.UNSPECIFIED, classification)
        assertFalse(classification.isSensitive)
        assertFalse(detector.isSensitive(context))
    }

    @Test
    fun classify_unknownCategoryWithSensitivity_returnsSensitivePassword() {
        val context = EditorContext(
            fieldCategory = FieldCategory.UNKNOWN,
            isSensitive = true,
            isMultiline = false,
            imeAction = 0,
            imeActionLabel = "↵",
            packageName = null
        )

        val classification = detector.classify(context)

        assertEquals(InputClassification.SENSITIVE_PASSWORD, classification)
        assertTrue(classification.isSensitive)
        assertTrue(detector.isSensitive(context))
    }
}
