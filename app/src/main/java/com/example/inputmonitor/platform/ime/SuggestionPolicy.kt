package com.example.inputmonitor.platform.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo

internal object SuggestionPolicy {
    fun allows(type: Int, imeOptions: Int = 0): Boolean =
        type and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT &&
            (type and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS == 0 ||
                imeOptions and EditorInfo.IME_MASK_ACTION == EditorInfo.IME_ACTION_SEARCH) &&
            (type and InputType.TYPE_MASK_VARIATION) in setOf(
                InputType.TYPE_TEXT_VARIATION_NORMAL, InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE,
                InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE, InputType.TYPE_TEXT_VARIATION_PERSON_NAME,
                InputType.TYPE_TEXT_VARIATION_FILTER, InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT
            )
}
