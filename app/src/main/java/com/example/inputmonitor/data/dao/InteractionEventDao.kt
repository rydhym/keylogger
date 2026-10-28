package com.example.inputmonitor.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.inputmonitor.data.entity.InteractionEventEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for interaction events (Architecture.md §49).
 */
@Dao
interface InteractionEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: InteractionEventEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<InteractionEventEntity>): List<Long>

    @Query("SELECT * FROM interaction_events WHERE id = :id LIMIT 1")
    suspend fun getEventById(id: Long): InteractionEventEntity?

    @Query("SELECT * FROM interaction_events WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun observeEventsForSession(sessionId: String): Flow<List<InteractionEventEntity>>

    @Query("SELECT * FROM interaction_events WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getEventsForSession(sessionId: String): List<InteractionEventEntity>

    @Query("SELECT * FROM interaction_events WHERE sessionId = :sessionId ORDER BY timestamp ASC LIMIT :limit OFFSET :offset")
    suspend fun getEventsForSessionPaged(sessionId: String, limit: Int, offset: Int): List<InteractionEventEntity>

    @Query("""
        SELECT * FROM interaction_events 
        WHERE sessionId = :sessionId 
          AND (:packageFilter IS NULL OR packageName = :packageFilter)
          AND (:eventTypeFilter IS NULL OR eventType = :eventTypeFilter)
          AND (:startTime IS NULL OR timestamp >= :startTime)
          AND (:endTime IS NULL OR timestamp <= :endTime)
        ORDER BY timestamp ASC
    """)
    suspend fun getEventsFiltered(
        sessionId: String,
        packageFilter: String? = null,
        eventTypeFilter: String? = null,
        startTime: Long? = null,
        endTime: Long? = null
    ): List<InteractionEventEntity>

    @Query("SELECT COUNT(*) FROM interaction_events WHERE sessionId = :sessionId")
    suspend fun getEventCountForSession(sessionId: String): Int

    @Query("SELECT COUNT(*) FROM interaction_events WHERE sessionId = :sessionId")
    fun observeEventCountForSession(sessionId: String): Flow<Int>

    @Query("""
        SELECT 
            COUNT(*) as total,
            COALESCE(SUM(CASE WHEN source = 'KEYBOARD' THEN 1 ELSE 0 END), 0) as keyboard,
            COALESCE(SUM(CASE WHEN source = 'ACCESSIBILITY' THEN 1 ELSE 0 END), 0) as accessibility,
            COALESCE(SUM(CASE WHEN eventType = 'VIEW_CLICKED' THEN 1 ELSE 0 END), 0) as clicks,
            COALESCE(SUM(CASE WHEN eventType = 'VIEW_FOCUSED' THEN 1 ELSE 0 END), 0) as focus,
            COALESCE(SUM(CASE WHEN eventType = 'VIEW_SCROLLED' THEN 1 ELSE 0 END), 0) as scrolls,
            COALESCE(SUM(CASE WHEN eventType IN ('WINDOW_STATE_CHANGED', 'WINDOWS_CHANGED') THEN 1 ELSE 0 END), 0) as windowChanges
        FROM interaction_events
        WHERE (:sessionId IS NULL OR sessionId = :sessionId)
    """)
    fun observeEventCounts(sessionId: String? = null): Flow<EventCountsTuple?>

    @Query("SELECT * FROM interaction_events ORDER BY timestamp DESC LIMIT 1")
    fun observeLatestEvent(): Flow<InteractionEventEntity?>

    @Query("""
        SELECT * FROM interaction_events 
        WHERE (:sessionId IS NULL OR sessionId = :sessionId)
          AND (:packageFilter IS NULL OR packageName = :packageFilter)
          AND (:eventTypeFilter IS NULL OR eventType = :eventTypeFilter)
          AND (:sourceFilter IS NULL OR source = :sourceFilter)
          AND (:classificationFilter IS NULL OR inputClassification = :classificationFilter)
          AND (:startTime IS NULL OR timestamp >= :startTime)
          AND (:endTime IS NULL OR timestamp <= :endTime)
          AND (:searchQuery IS NULL OR (
              (packageName IS NOT NULL AND packageName LIKE '%' || :searchQuery || '%') OR 
              (applicationName IS NOT NULL AND applicationName LIKE '%' || :searchQuery || '%') OR
              (eventType LIKE '%' || :searchQuery || '%') OR
              (sessionId LIKE '%' || :searchQuery || '%') OR
              (eventData IS NOT NULL AND eventData LIKE '%' || :searchQuery || '%')
          ))
        ORDER BY timestamp DESC
        LIMIT :limit OFFSET :offset
    """)
    fun observeEventsFilteredPaged(
        sessionId: String? = null,
        packageFilter: String? = null,
        eventTypeFilter: String? = null,
        sourceFilter: String? = null,
        classificationFilter: String? = null,
        startTime: Long? = null,
        endTime: Long? = null,
        searchQuery: String? = null,
        limit: Int = 50,
        offset: Int = 0
    ): Flow<List<InteractionEventEntity>>

    @Query("""
        SELECT COUNT(*) FROM interaction_events 
        WHERE (:sessionId IS NULL OR sessionId = :sessionId)
          AND (:packageFilter IS NULL OR packageName = :packageFilter)
          AND (:eventTypeFilter IS NULL OR eventType = :eventTypeFilter)
          AND (:sourceFilter IS NULL OR source = :sourceFilter)
          AND (:classificationFilter IS NULL OR inputClassification = :classificationFilter)
          AND (:startTime IS NULL OR timestamp >= :startTime)
          AND (:endTime IS NULL OR timestamp <= :endTime)
          AND (:searchQuery IS NULL OR (
              (packageName IS NOT NULL AND packageName LIKE '%' || :searchQuery || '%') OR 
              (applicationName IS NOT NULL AND applicationName LIKE '%' || :searchQuery || '%') OR
              (eventType LIKE '%' || :searchQuery || '%') OR
              (sessionId LIKE '%' || :searchQuery || '%') OR
              (eventData IS NOT NULL AND eventData LIKE '%' || :searchQuery || '%')
          ))
    """)
    fun observeFilteredCount(
        sessionId: String? = null,
        packageFilter: String? = null,
        eventTypeFilter: String? = null,
        sourceFilter: String? = null,
        classificationFilter: String? = null,
        startTime: Long? = null,
        endTime: Long? = null,
        searchQuery: String? = null
    ): Flow<Int>

    @Query("SELECT DISTINCT packageName FROM interaction_events WHERE packageName IS NOT NULL ORDER BY packageName ASC")
    fun observeDistinctPackageNames(): Flow<List<String>>

    @Query("""
        SELECT DISTINCT applicationName FROM interaction_events 
        WHERE applicationName IS NOT NULL 
          AND applicationName LIKE '%' || :query || '%' 
        ORDER BY applicationName ASC 
        LIMIT :limit
    """)
    fun observeMatchingApplications(query: String, limit: Int = 5): Flow<List<String>>

    @Query("""
        SELECT DISTINCT packageName FROM interaction_events 
        WHERE packageName IS NOT NULL 
          AND packageName LIKE '%' || :query || '%' 
        ORDER BY packageName ASC 
        LIMIT :limit
    """)
    fun observeMatchingPackages(query: String, limit: Int = 5): Flow<List<String>>

    @Query("""
        SELECT DISTINCT applicationName FROM interaction_events 
        WHERE applicationName IS NOT NULL 
          AND applicationName LIKE '%' || :query || '%' 
        ORDER BY applicationName ASC 
        LIMIT :limit
    """)
    suspend fun getMatchingApplications(query: String, limit: Int = 5): List<String>

    @Query("""
        SELECT DISTINCT packageName FROM interaction_events 
        WHERE packageName IS NOT NULL 
          AND packageName LIKE '%' || :query || '%' 
        ORDER BY packageName ASC 
        LIMIT :limit
    """)
    suspend fun getMatchingPackages(query: String, limit: Int = 5): List<String>

    @Query("""
        SELECT 
            COUNT(*) as total,
            COALESCE(SUM(CASE WHEN source = 'KEYBOARD' THEN 1 ELSE 0 END), 0) as keyboard,
            COALESCE(SUM(CASE WHEN source = 'ACCESSIBILITY' THEN 1 ELSE 0 END), 0) as accessibility,
            COALESCE(SUM(CASE WHEN eventType = 'VIEW_CLICKED' THEN 1 ELSE 0 END), 0) as clicks,
            COALESCE(SUM(CASE WHEN eventType = 'VIEW_FOCUSED' THEN 1 ELSE 0 END), 0) as focus,
            COALESCE(SUM(CASE WHEN eventType = 'VIEW_SCROLLED' THEN 1 ELSE 0 END), 0) as scrolls,
            COALESCE(SUM(CASE WHEN eventType IN ('WINDOW_STATE_CHANGED', 'WINDOWS_CHANGED') THEN 1 ELSE 0 END), 0) as windowChanges,
            COALESCE(SUM(CASE WHEN eventType = 'SENSITIVE_INPUT_DETECTED' OR inputClassification LIKE 'SENSITIVE_%' THEN 1 ELSE 0 END), 0) as sensitiveSuppressions,
            COUNT(DISTINCT packageName) as distinctApps,
            MIN(timestamp) as firstTimestamp,
            MAX(timestamp) as lastTimestamp
        FROM interaction_events
        WHERE (:sessionId IS NULL OR sessionId = :sessionId)
    """)
    fun observeStatisticsSummary(sessionId: String? = null): Flow<StatisticsSummaryTuple?>

    @Query("""
        SELECT 
            COUNT(*) as total,
            COALESCE(SUM(CASE WHEN source = 'KEYBOARD' THEN 1 ELSE 0 END), 0) as keyboard,
            COALESCE(SUM(CASE WHEN source = 'ACCESSIBILITY' THEN 1 ELSE 0 END), 0) as accessibility,
            COALESCE(SUM(CASE WHEN eventType = 'VIEW_CLICKED' THEN 1 ELSE 0 END), 0) as clicks,
            COALESCE(SUM(CASE WHEN eventType = 'VIEW_FOCUSED' THEN 1 ELSE 0 END), 0) as focus,
            COALESCE(SUM(CASE WHEN eventType = 'VIEW_SCROLLED' THEN 1 ELSE 0 END), 0) as scrolls,
            COALESCE(SUM(CASE WHEN eventType IN ('WINDOW_STATE_CHANGED', 'WINDOWS_CHANGED') THEN 1 ELSE 0 END), 0) as windowChanges,
            COALESCE(SUM(CASE WHEN eventType = 'SENSITIVE_INPUT_DETECTED' OR inputClassification LIKE 'SENSITIVE_%' THEN 1 ELSE 0 END), 0) as sensitiveSuppressions,
            COUNT(DISTINCT packageName) as distinctApps,
            MIN(timestamp) as firstTimestamp,
            MAX(timestamp) as lastTimestamp
        FROM interaction_events
        WHERE (:sessionId IS NULL OR sessionId = :sessionId)
    """)
    suspend fun getStatisticsSummary(sessionId: String? = null): StatisticsSummaryTuple?

    @Query("""
        SELECT 
            packageName,
            COALESCE(applicationName, packageName) as applicationName,
            COUNT(*) as eventCount,
            MAX(timestamp) as lastActiveTimestamp
        FROM interaction_events
        WHERE (:sessionId IS NULL OR sessionId = :sessionId)
          AND packageName IS NOT NULL
        GROUP BY packageName
        ORDER BY eventCount DESC
    """)
    fun observeAppUsageStats(sessionId: String? = null): Flow<List<AppUsageTuple>>

    @Query("""
        SELECT 
            packageName,
            COALESCE(applicationName, packageName) as applicationName,
            COUNT(*) as eventCount,
            MAX(timestamp) as lastActiveTimestamp
        FROM interaction_events
        WHERE (:sessionId IS NULL OR sessionId = :sessionId)
          AND packageName IS NOT NULL
        GROUP BY packageName
        ORDER BY eventCount DESC
    """)
    suspend fun getAppUsageStats(sessionId: String? = null): List<AppUsageTuple>

    @Query("""
        SELECT 
            CAST(strftime('%H', timestamp / 1000, 'unixepoch', 'localtime') AS INTEGER) as hourOfDay,
            COUNT(*) as eventCount
        FROM interaction_events
        WHERE (:sessionId IS NULL OR sessionId = :sessionId)
          AND timestamp > 0
        GROUP BY hourOfDay
        ORDER BY hourOfDay ASC
    """)
    fun observeHourlyActivity(sessionId: String? = null): Flow<List<HourlyActivityTuple>>

    @Query("""
        SELECT 
            CAST(strftime('%H', timestamp / 1000, 'unixepoch', 'localtime') AS INTEGER) as hourOfDay,
            COUNT(*) as eventCount
        FROM interaction_events
        WHERE (:sessionId IS NULL OR sessionId = :sessionId)
          AND timestamp > 0
        GROUP BY hourOfDay
        ORDER BY hourOfDay ASC
    """)
    suspend fun getHourlyActivity(sessionId: String? = null): List<HourlyActivityTuple>

    @Query("DELETE FROM interaction_events WHERE sessionId = :sessionId")
    suspend fun deleteEventsForSession(sessionId: String): Int

    @Query("DELETE FROM interaction_events")
    suspend fun deleteAllEvents(): Int
}

/**
 * Tuple representing aggregate event metrics without loading entities (Architecture.md §55).
 */
data class EventCountsTuple(
    val total: Long = 0,
    val keyboard: Long = 0,
    val accessibility: Long = 0,
    val clicks: Long = 0,
    val focus: Long = 0,
    val scrolls: Long = 0,
    val windowChanges: Long = 0
)

/**
 * Tuple representing detailed statistical summary metrics (SRS.md §51, Architecture.md §57).
 */
data class StatisticsSummaryTuple(
    val total: Long = 0,
    val keyboard: Long = 0,
    val accessibility: Long = 0,
    val clicks: Long = 0,
    val focus: Long = 0,
    val scrolls: Long = 0,
    val windowChanges: Long = 0,
    val sensitiveSuppressions: Long = 0,
    val distinctApps: Int = 0,
    val firstTimestamp: Long? = null,
    val lastTimestamp: Long? = null
)

/**
 * Tuple representing aggregate application interaction counts (SRS.md §51, §52).
 */
data class AppUsageTuple(
    val packageName: String,
    val applicationName: String,
    val eventCount: Long,
    val lastActiveTimestamp: Long
)

/**
 * Tuple representing hourly interaction volume across a 24-hour cycle (SRS.md §51).
 */
data class HourlyActivityTuple(
    val hourOfDay: Int,
    val eventCount: Long
)

