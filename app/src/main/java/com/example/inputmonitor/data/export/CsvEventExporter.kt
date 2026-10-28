package com.example.inputmonitor.data.export

import com.example.inputmonitor.domain.model.InteractionEvent
import java.io.BufferedWriter
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Interface for streaming interaction events into RFC-4180 compliant CSV format (SRS.md §54, Architecture.md §58).
 */
interface CsvEventExporter {
    /**
     * Writes the CSV header row to [outputStream].
     * Returns the number of bytes written.
     */
    fun writeHeader(outputStream: OutputStream): Long

    /**
     * Serializes and writes a list of [events] to [outputStream].
     * Returns the number of bytes written.
     */
    fun writeEvents(outputStream: OutputStream, events: List<InteractionEvent>): Long
}

/**
 * High-performance streaming RFC-4180 CSV exporter.
 */
class CsvEventExporterImpl : CsvEventExporter {

    companion object {
        val CSV_HEADER = listOf(
            "timestamp",
            "formatted_time",
            "session_id",
            "source",
            "event_type",
            "package_name",
            "application_name",
            "event_data",
            "classification"
        )
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)

    override fun writeHeader(outputStream: OutputStream): Long {
        val writer = OutputStreamWriter(outputStream, StandardCharsets.UTF_8)
        val headerLine = CSV_HEADER.joinToString(separator = ",") + "\r\n"
        val bytes = headerLine.toByteArray(StandardCharsets.UTF_8)
        outputStream.write(bytes)
        outputStream.flush()
        return bytes.size.toLong()
    }

    override fun writeEvents(outputStream: OutputStream, events: List<InteractionEvent>): Long {
        if (events.isEmpty()) return 0L

        val buffer = StringBuilder()
        for (event in events) {
            val formattedTime = synchronized(dateFormat) {
                dateFormat.format(Date(event.timestamp))
            }
            val row = listOf(
                event.timestamp.toString(),
                escapeCsv(formattedTime),
                escapeCsv(event.sessionId),
                escapeCsv(event.source.name),
                escapeCsv(event.eventType.name),
                escapeCsv(event.packageName ?: ""),
                escapeCsv(event.applicationName ?: ""),
                escapeCsv(event.eventData ?: ""),
                escapeCsv(event.inputClassification.name)
            )
            buffer.append(row.joinToString(separator = ",")).append("\r\n")
        }

        val bytes = buffer.toString().toByteArray(StandardCharsets.UTF_8)
        outputStream.write(bytes)
        outputStream.flush()
        return bytes.size.toLong()
    }

    /**
     * Escapes a string for RFC-4180 CSV compliance:
     * - If the value contains commas, quotes, or newlines, wrap in quotes.
     * - Double quotes inside are escaped by doubling them (" -> "").
     */
    internal fun escapeCsv(value: String): String {
        if (value.isEmpty()) return ""

        val needsQuotes = value.contains(',') ||
                value.contains('"') ||
                value.contains('\n') ||
                value.contains('\r')

        return if (needsQuotes) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }
}
