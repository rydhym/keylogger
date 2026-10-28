package com.example.inputmonitor.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * Unit tests for DateTimeUtils verifying formatting consistency and edge cases.
 */
class DateTimeUtilsTest {

    @Test
    fun `formatDateTime produces formatted string`() {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.set(2026, Calendar.OCTOBER, 19, 14, 30, 0)
        cal.set(Calendar.MILLISECOND, 0)

        val result = DateTimeUtils.formatDateTime(cal.timeInMillis)
        assertTrue(result.isNotEmpty())
        assertTrue(result.contains(":"))
    }

    @Test
    fun `formatSessionStart produces formatted string`() {
        val cal = Calendar.getInstance()
        cal.set(2026, Calendar.OCTOBER, 19, 10, 15, 30)

        val result = DateTimeUtils.formatSessionStart(cal.timeInMillis)
        assertTrue(result.contains("("))
        assertTrue(result.contains(")"))
    }

    @Test
    fun `formatShortDate and formatTimeOnly produce non-empty strings`() {
        val timestamp = System.currentTimeMillis()
        val shortDate = DateTimeUtils.formatShortDate(timestamp)
        val timeOnly = DateTimeUtils.formatTimeOnly(timestamp)

        assertTrue(shortDate.isNotEmpty())
        assertTrue(timeOnly.matches(Regex("""\d{1,2}:\d{2}""")))
    }

    @Test
    fun `generateDefaultSessionName produces valid prefix and timestamp`() {
        val sessionName = DateTimeUtils.generateDefaultSessionName()
        assertTrue(sessionName.startsWith("Session_"))
        assertTrue(sessionName.matches(Regex("""Session_\d{8}_\d{4}""")))
    }

    @Test
    fun `generateFileTimestamp produces 8digit_6digit pattern`() {
        val tag = DateTimeUtils.generateFileTimestamp()
        assertTrue(tag.matches(Regex("""\d{8}_\d{6}""")))
    }

    @Test
    fun `formatDuration formats sub-minute durations as seconds`() {
        val result = DateTimeUtils.formatDuration(0.5)
        assertEquals("30s", result)

        val zero = DateTimeUtils.formatDuration(0.0)
        assertEquals("0s", zero)
    }

    @Test
    fun `formatDuration formats sub-hour durations with decimal minute`() {
        val result = DateTimeUtils.formatDuration(15.5)
        assertEquals("15.5m", result)
    }

    @Test
    fun `formatDuration formats multi-hour durations with hours and minutes`() {
        val result = DateTimeUtils.formatDuration(125.0)
        assertEquals("2h 5m", result)

        val exactHours = DateTimeUtils.formatDuration(180.0)
        assertEquals("3h 0m", exactHours)
    }
}
