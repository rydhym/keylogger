package com.example.inputmonitor.domain.privacy

import com.example.inputmonitor.domain.model.InputClassification

/**
 * Interface for identifying sensitive input fields and classifying them into domain
 * [InputClassification] categories (Architecture.md §28, SECURITY.md §8).
 */
interface SensitiveInputDetector {

    /**
     * Maps an analyzed [EditorContext] into its corresponding [InputClassification].
     */
    fun classify(editorContext: EditorContext): InputClassification

    /**
     * Determines whether the given [EditorContext] represents a sensitive input field
     * where content logging must be suppressed.
     */
    fun isSensitive(editorContext: EditorContext): Boolean
}
