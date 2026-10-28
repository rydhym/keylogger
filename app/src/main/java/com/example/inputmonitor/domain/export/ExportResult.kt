package com.example.inputmonitor.domain.export

/**
 * Supported local export formats (SRS.md §54, Architecture.md §58).
 */
enum class ExportFormat(val mimeType: String, val fileExtension: String) {
    CSV("text/csv", ".csv")
}

/**
 * Result of a local export operation (Architecture.md §58, §59).
 */
sealed class ExportResult {
    data class Success(
        val eventCount: Long,
        val bytesWritten: Long,
        val targetUriString: String? = null
    ) : ExportResult()

    data class Error(
        val message: String,
        val throwable: Throwable? = null
    ) : ExportResult()
}
