package com.example.inputmonitor.domain.context

/**
 * Resolves human-readable application labels from Android package names (Architecture.md §41).
 * Implementations must maintain a local cache to prevent repeated PackageManager IPC lookups (§42).
 */
interface ContextResolver {

    /**
     * Resolves the [ApplicationContextInfo] for the given [packageName].
     */
    suspend fun resolve(packageName: String?): ApplicationContextInfo
}
