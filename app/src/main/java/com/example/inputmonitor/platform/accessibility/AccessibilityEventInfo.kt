package com.example.inputmonitor.platform.accessibility

import android.view.accessibility.AccessibilityEvent

/**
 * Decoupled representation of accessibility event attributes (Architecture.md §21).
 *
 * Enables deterministic JVM unit testing without requiring Android OS accessibility IPC.
 */
interface AccessibilityEventInfo {
    val eventType: Int
    val packageName: CharSequence?
    val className: CharSequence?
    val contentDescription: CharSequence?
    val isPassword: Boolean
    val itemCount: Int
    val action: Int
    val eventTime: Long
}

/**
 * Adapts an Android [AccessibilityEvent] into [AccessibilityEventInfo].
 * Snapshots attributes immediately to decouple from the Android event recycler.
 */
fun AccessibilityEvent.toEventInfo(): AccessibilityEventInfo {
    return SimpleAccessibilityEventInfo(
        eventType = this.eventType,
        packageName = this.packageName?.toString(),
        className = this.className?.toString(),
        contentDescription = this.contentDescription?.toString(),
        isPassword = this.isPassword,
        itemCount = this.itemCount,
        action = this.action,
        eventTime = if (this.eventTime > 0) this.eventTime else System.currentTimeMillis()
    )
}

/**
 * Lightweight data class for testing and decoupled event processing.
 */
data class SimpleAccessibilityEventInfo(
    override val eventType: Int,
    override val packageName: CharSequence? = null,
    override val className: CharSequence? = null,
    override val contentDescription: CharSequence? = null,
    override val isPassword: Boolean = false,
    override val itemCount: Int = 0,
    override val action: Int = 0,
    override val eventTime: Long = System.currentTimeMillis()
) : AccessibilityEventInfo
