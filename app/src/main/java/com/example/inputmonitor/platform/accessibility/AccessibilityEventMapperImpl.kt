package com.example.inputmonitor.platform.accessibility

import android.view.accessibility.AccessibilityEvent
import com.example.inputmonitor.domain.model.EventSource
import com.example.inputmonitor.domain.model.EventType
import com.example.inputmonitor.domain.model.InputClassification
import com.example.inputmonitor.domain.model.RawInteractionEvent

/**
 * Production implementation of [AccessibilityEventMapper] (Architecture.md §21, §22, SECURITY.md §4).
 *
 * Implements strict Content Minimization:
 * - Omits user-typed text, editable content, and message bodies.
 * - Detects password fields via [AccessibilityEventInfo.isPassword] and class name inspection,
 *   classifying them as [InputClassification.SENSITIVE_PASSWORD] and nullifying candidateData.
 * - Captures structural interaction metadata (className, contentDescription if non-sensitive, itemCount).
 */
class AccessibilityEventMapperImpl : AccessibilityEventMapper {

    override fun map(event: AccessibilityEventInfo): RawInteractionEvent {
        val eventType = when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> EventType.VIEW_CLICKED
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> EventType.VIEW_FOCUSED
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> EventType.VIEW_SCROLLED
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> EventType.WINDOW_STATE_CHANGED
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> EventType.WINDOWS_CHANGED
            AccessibilityEvent.TYPE_VIEW_SELECTED -> EventType.VIEW_SELECTED
            else -> EventType.VIEW_CLICKED
        }

        val className = event.className?.toString()
        val isPasswordEvent = event.isPassword || (className != null && (
            className.contains("Password", ignoreCase = true) ||
            className.contains("Pin", ignoreCase = true)
        ))

        val inputClassification = if (isPasswordEvent) {
            InputClassification.SENSITIVE_PASSWORD
        } else {
            InputClassification.NORMAL_TEXT
        }

        // Content Minimization: Never capture text or candidateData for sensitive inputs.
        // For non-sensitive view clicks/selections, contentDescription may be retained as metadata.
        val candidateData = if (isPasswordEvent) {
            null
        } else {
            event.contentDescription?.toString()?.takeIf { it.isNotBlank() }
        }

        val metadata = buildMap {
            className?.let { put("className", it) }
            if (event.itemCount > 0) {
                put("itemCount", event.itemCount.toString())
            }
            if (isPasswordEvent) {
                put("isPassword", "true")
            }
            put("action", event.action.toString())
        }

        val timestamp = if (event.eventTime > 0) event.eventTime else System.currentTimeMillis()

        return RawInteractionEvent(
            timestamp = timestamp,
            source = EventSource.ACCESSIBILITY,
            eventType = eventType,
            packageName = event.packageName?.toString(),
            candidateData = candidateData,
            inputClassification = inputClassification,
            metadata = metadata
        )
    }
}
