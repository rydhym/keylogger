package com.example.inputmonitor.data.export

import com.example.inputmonitor.domain.model.EventSource
import com.example.inputmonitor.domain.model.EventType
import com.example.inputmonitor.domain.model.InputClassification
import com.example.inputmonitor.domain.model.InteractionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream

/**
 * Unit tests for [CsvEventExporter] verifying RFC-4180 CSV compliance, header serialization,
 * and character escaping (SRS.md §54, Architecture.md §58).
 */
class CsvEventExporterTest {

    private lateinit var exporter: CsvEventExporterImpl

    @Before
    fun setUp() {
        exporter = CsvEventExporterImpl()
    }

    @Test
    fun writeHeader_emitsExpectedRfc4180Columns() {
        val outputStream = ByteArrayOutputStream()
        val bytesWritten = exporter.writeHeader(outputStream)

        val csvString = outputStream.toString("UTF-8")
        assertTrue(bytesWritten > 0)
        assertTrue(csvString.startsWith("timestamp,formatted_time,session_id,source,event_type,package_name,application_name,event_data,classification\r\n"))
    }

    @Test
    fun writeEvents_formatsRowsCorrectly() {
        val outputStream = ByteArrayOutputStream()
        val event = InteractionEvent(
            sessionId = "sess-100",
            timestamp = 1700000000000L,
            source = EventSource.KEYBOARD,
            eventType = EventType.TEXT_COMMITTED,
            packageName = "com.example.notes",
            applicationName = "Notes App",
            eventData = "Hello World",
            inputClassification = InputClassification.NORMAL_TEXT
        )

        val bytesWritten = exporter.writeEvents(outputStream, listOf(event))
        val output = outputStream.toString("UTF-8")

        assertTrue(bytesWritten > 0)
        assertTrue(output.contains("1700000000000"))
        assertTrue(output.contains("sess-100"))
        assertTrue(output.contains("KEYBOARD"))
        assertTrue(output.contains("TEXT_COMMITTED"))
        assertTrue(output.contains("com.example.notes"))
        assertTrue(output.contains("Notes App"))
        assertTrue(output.contains("Hello World"))
        assertTrue(output.contains("NORMAL_TEXT"))
    }

    @Test
    fun escapeCsv_handlesSpecialCharacters() {
        // Plain string
        assertEquals("SimpleText", exporter.escapeCsv("SimpleText"))

        // String with comma
        assertEquals("\"Item 1, Item 2\"", exporter.escapeCsv("Item 1, Item 2"))

        // String with quotes
        assertEquals("\"He said \"\"Hello\"\"\"", exporter.escapeCsv("He said \"Hello\""))

        // String with newlines
        assertEquals("\"Line 1\nLine 2\"", exporter.escapeCsv("Line 1\nLine 2"))
        assertEquals("\"Line 1\r\nLine 2\"", exporter.escapeCsv("Line 1\r\nLine 2"))

        // Empty string
        assertEquals("", exporter.escapeCsv(""))
    }

    @Test
    fun writeEvents_emptyList_returnsZeroBytes() {
        val outputStream = ByteArrayOutputStream()
        val bytesWritten = exporter.writeEvents(outputStream, emptyList())
        assertEquals(0L, bytesWritten)
        assertEquals("", outputStream.toString())
    }
}
