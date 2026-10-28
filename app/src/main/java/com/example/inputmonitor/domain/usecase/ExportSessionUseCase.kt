package com.example.inputmonitor.domain.usecase

import com.example.inputmonitor.core.DispatcherProvider
import com.example.inputmonitor.data.export.CsvEventExporter
import com.example.inputmonitor.domain.export.ExportResult
import com.example.inputmonitor.domain.export.ExportValidator
import com.example.inputmonitor.domain.model.EventFilterCriteria
import com.example.inputmonitor.domain.repository.EventRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.OutputStream

/**
 * Use case coordinating authorized data export, defense-in-depth sanitization, and streaming CSV writing
 * (SRS.md §54, Architecture.md §58, §59, SEC-006, SEC-015).
 */
class ExportSessionUseCase(
    private val eventRepository: EventRepository,
    private val exportValidator: ExportValidator,
    private val csvEventExporter: CsvEventExporter,
    private val dispatchers: DispatcherProvider
) {
    companion object {
        const val CHUNK_SIZE = 250
    }

    suspend operator fun invoke(
        outputStream: OutputStream,
        sessionId: String? = null,
        onProgress: ((exported: Long, total: Long) -> Unit)? = null
    ): ExportResult = withContext(dispatchers.io) {
        try {
            var totalBytesWritten = 0L

            // Step 1: Write CSV header
            totalBytesWritten += csvEventExporter.writeHeader(outputStream)

            // Step 2: Determine total events to export
            val filter = EventFilterCriteria(sessionId = sessionId)
            val totalCount = eventRepository.observeFilteredCount(filter).first().toLong()

            if (totalCount == 0L) {
                onProgress?.invoke(0L, 0L)
                return@withContext ExportResult.Success(
                    eventCount = 0L,
                    bytesWritten = totalBytesWritten
                )
            }

            // Step 3: Stream in bounded chunks
            var offset = 0
            var exportedCount = 0L

            while (offset < totalCount) {
                val batch = eventRepository.observeEventsPaged(
                    filter = filter,
                    limit = CHUNK_SIZE,
                    offset = offset
                ).first()

                if (batch.isEmpty()) {
                    break
                }

                // Step 4: Defense-in-depth sanitization before output
                val sanitizedBatch = batch.map { exportValidator.sanitizeForExport(it) }

                // Step 5: Write serialized CSV rows
                totalBytesWritten += csvEventExporter.writeEvents(outputStream, sanitizedBatch)
                exportedCount += sanitizedBatch.size
                offset += sanitizedBatch.size

                onProgress?.invoke(exportedCount, totalCount)
            }

            ExportResult.Success(
                eventCount = exportedCount,
                bytesWritten = totalBytesWritten
            )
        } catch (t: Throwable) {
            ExportResult.Error(
                message = t.message ?: "Failed to export interaction events",
                throwable = t
            )
        }
    }
}
