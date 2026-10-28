package com.example.inputmonitor.platform.ime

import android.text.InputType
import org.junit.Assert.*
import org.junit.Test

class SuggestionPolicyTest {
    @Test fun allowsYouTubeSearchAndWebText() {
        assertTrue(SuggestionPolicy.allows(0x2000b1))
        assertTrue(SuggestionPolicy.allows(0x2a0001, 0x10000003))
        assertFalse(SuggestionPolicy.allows(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD, 3))
        assertTrue(SuggestionPolicy.allows(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT))
    }
    @Test fun excludesPasswordsEmailAndExplicitOptOut() {
        listOf(InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD, InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_URI).forEach {
            assertFalse(SuggestionPolicy.allows(InputType.TYPE_CLASS_TEXT or it))
        }
        assertFalse(SuggestionPolicy.allows(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS))
        assertFalse(SuggestionPolicy.allows(InputType.TYPE_CLASS_NUMBER))
    }
}
