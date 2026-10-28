package com.example.inputmonitor.domain.context

/**
 * Resolved application context information (Architecture.md §41).
 */
data class ApplicationContextInfo(
    val packageName: String?,
    val applicationLabel: String?
)
