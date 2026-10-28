package com.example.inputmonitor.platform.ime.analysis

import android.view.inputmethod.EditorInfo
import com.example.inputmonitor.domain.privacy.EditorContext

/**
 * Analyzes Android [EditorInfo] metadata to produce an [EditorContext]
 * (Architecture.md §26, §27).
 */
interface EditorInfoAnalyzer {

    /**
     * Inspects [editorInfo] to determine field category, sensitivity flags,
     * action labels, and suggested classifications.
     */
    fun analyze(editorInfo: EditorInfo?): EditorContext
}
