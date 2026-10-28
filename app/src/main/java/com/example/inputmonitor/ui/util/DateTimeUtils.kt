package com.example.inputmonitor.ui.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Centralized, thread-safe date/time formatting utilities for UI screens.
 * Eliminates repeated SimpleDateFormat instantiations and duplicate duration formatters.
 */
object DateTimeUtils {

    /**
     * Formats a session start timestamp for display: "HH:mm:ss (yyyy-MM-dd)".
     */
    fun formatSessionStart(timestamp: Long): String {
        return SimpleDateFormat("HH:mm:ss (yyyy-MM-dd)", Locale.getDefault()).format(Date(timestamp))
    }

    /**
     * Formats a timestamp into standard date-time string: "yyyy-MM-dd HH:mm:ss".
     */
    fun formatDateTime(timestamp: Long): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
    }

    /**
     * Formats a timestamp for statistics cards / charts: "MMM d, HH:mm".
     */
    fun formatShortDate(timestamp: Long): String {
        return SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(timestamp))
    }

    /**
     * Formats a timestamp to time only: "HH:mm".
     */
    fun formatTimeOnly(timestamp: Long): String {
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
    }

    /**
     * Generates a default human-readable session name based on current time: "Session_yyyyMMdd_HHmm".
     */
    fun generateDefaultSessionName(): String {
        val dateStr = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
        return "Session_$dateStr"
    }

    /**
     * Generates a timestamp string for file export tags: "yyyyMMdd_HHmmss".
     */
    fun generateFileTimestamp(): String {
        return SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    }

    /**
     * Formats duration in minutes to human-readable string:
     * - < 1 min: "45s"
     * - < 60 min: "12.5m"
     * - >= 60 min: "2h 15m"
     */
    fun formatDuration(minutes: Double): String {
        if (minutes < 1.0) {
            val seconds = (minutes * 60).toInt()
            return "${seconds}s"
        }
        if (minutes < 60.0) {
            return String.format(Locale.US, "%.1fm", minutes)
        }
        val hours = (minutes / 60.0).toInt()
        val remMin = (minutes % 60.0).toInt()
        return "${hours}h ${remMin}m"
    }
}
