package com.example.inputmonitor.domain.repository

import com.example.inputmonitor.domain.model.InteractionEvent
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for interaction event persistence and retrieval (Architecture.md §50).
 */
interface EventRepository {
    suspend fun insertEvent(event: InteractionEvent): Long
    suspend fun insertEvents(events: List<InteractionEvent>): List<Long>
    suspend fun getEventById(id: Long): InteractionEvent?
    fun observeEventsForSession(sessionId: String): Flow<List<InteractionEvent>>
    suspend fun getEventsForSession(sessionId: String): List<InteractionEvent>
    suspend fun getEventsForSessionPaged(sessionId: String, limit: Int, offset: Int): List<InteractionEvent>
    suspend fun getEventsFiltered(
        sessionId: String,
        packageFilter: String? = null,
        eventTypeFilter: String? = null,
        startTime: Long? = null,
        endTime: Long? = null
    ): List<InteractionEvent>
    suspend fun getEventCountForSession(sessionId: String): Int
    fun observeEventCountForSession(sessionId: String): Flow<Int>
    fun observeEventCounts(sessionId: String? = null): Flow<com.example.inputmonitor.domain.model.EventCounts>
    fun observeLatestEvent(): Flow<InteractionEvent?>
    fun observeEventsPaged(
        filter: com.example.inputmonitor.domain.model.EventFilterCriteria,
        limit: Int = 50,
        offset: Int = 0
    ): Flow<List<InteractionEvent>>
    fun observeFilteredCount(
        filter: com.example.inputmonitor.domain.model.EventFilterCriteria
    ): Flow<Int>
    fun observeDistinctPackageNames(): Flow<List<String>>
    fun observeMatchingApplications(query: String, limit: Int = 5): Flow<List<String>> =
        kotlinx.coroutines.flow.flowOf(emptyList())
    fun observeMatchingPackages(query: String, limit: Int = 5): Flow<List<String>> =
        kotlinx.coroutines.flow.flowOf(emptyList())
    fun observeSearchSuggestions(query: String, limit: Int = 5): Flow<List<com.example.inputmonitor.domain.model.SearchSuggestion>> =
        kotlinx.coroutines.flow.flowOf(emptyList())
    fun observeStatistics(sessionId: String? = null): Flow<com.example.inputmonitor.domain.model.InteractionStatistics> =
        kotlinx.coroutines.flow.flowOf(com.example.inputmonitor.domain.model.InteractionStatistics())
    suspend fun getStatistics(sessionId: String? = null): com.example.inputmonitor.domain.model.InteractionStatistics =
        com.example.inputmonitor.domain.model.InteractionStatistics()
    suspend fun deleteEventsForSession(sessionId: String): Boolean
    suspend fun deleteAllEvents(): Boolean
}
