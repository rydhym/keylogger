package com.example.inputmonitor.domain.model

/**
 * Domain representation of application usage metrics (SRS.md §51, §52, Architecture.md §57).
 */
data class AppUsageStat(
    val packageName: String,
    val applicationName: String,
    val count: Long,
    val percentage: Float, // 0.0f - 100.0f
    val lastActiveTimestamp: Long
)

/**
 * Domain representation of hourly interaction frequency across a 24-hour day (SRS.md §51).
 */
data class HourlyActivityStat(
    val hourOfDay: Int, // 0..23
    val count: Long,
    val normalizedRatio: Float // 0.0f - 1.0f relative to peak hour
)

/**
 * Summary metrics of interaction events (SRS.md §51, AC-26).
 */
data class StatisticsSummary(
    val totalEvents: Long = 0,
    val keyboardEvents: Long = 0,
    val accessibilityEvents: Long = 0,
    val clicks: Long = 0,
    val focusEvents: Long = 0,
    val scrollEvents: Long = 0,
    val windowChanges: Long = 0,
    val sensitiveSuppressions: Long = 0,
    val distinctApps: Int = 0,
    val firstEventTimestamp: Long? = null,
    val lastEventTimestamp: Long? = null,
    val eventsPerMinute: Double = 0.0
) {
    val durationMinutes: Double
        get() {
            if (firstEventTimestamp == null || lastEventTimestamp == null) return 0.0
            val diffMs = lastEventTimestamp - firstEventTimestamp
            if (diffMs <= 0) return 0.0
            return diffMs / 60000.0
        }
}

/**
 * Comprehensive interaction statistics aggregating summary, app distribution, and hourly trends (Architecture.md §57).
 */
data class InteractionStatistics(
    val summary: StatisticsSummary = StatisticsSummary(),
    val appUsage: List<AppUsageStat> = emptyList(),
    val hourlyActivity: List<HourlyActivityStat> = emptyList()
) {
    val isEmpty: Boolean
        get() = summary.totalEvents == 0L
}
