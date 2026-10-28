package com.example.inputmonitor.domain.privacy

import com.example.inputmonitor.domain.model.InputClassification

/**
 * Contextual metadata analyzed from the active input editor (Architecture.md §26).
 * Used by KeyboardController for visual adaptations and by SensitiveInputDetector
 * for privacy classification.
 */
data class EditorContext(
    val fieldCategory: FieldCategory,
    val isSensitive: Boolean,
    val isMultiline: Boolean,
    val imeAction: Int,
    val imeActionLabel: String,
    val packageName: String?,
    val inputType: Int = 0,
    val hintText: String? = null,
    val suggestedClassification: InputClassification = InputClassification.UNSPECIFIED
)
