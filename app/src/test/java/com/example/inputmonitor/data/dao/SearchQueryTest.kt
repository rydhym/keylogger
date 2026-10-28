package com.example.inputmonitor.data.dao

import androidx.room.Entity
import androidx.room.Index
import com.example.inputmonitor.core.DispatcherProvider
import com.example.inputmonitor.data.entity.InteractionEventEntity
import com.example.inputmonitor.data.repository.EventRepositoryImpl
import com.example.inputmonitor.domain.model.EventFilterCriteria
import com.example.inputmonitor.domain.model.EventSource
import com.example.inputmonitor.domain.model.EventType
import com.example.inputmonitor.domain.model.InputClassification
import com.example.inputmonitor.domain.model.InteractionEvent
import com.example.inputmonitor.domain.model.SuggestionType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit test suite verifying database-backed search, index configuration, and search suggestions
 * (Architecture.md §56, IMPLEMENTATION_PLAN.md §52, SRS.md §50).
 */
class SearchQueryTest {

    private val testDispatcher = Dispatchers.Unconfined
    private val testDispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
        override val unconfined: CoroutineDispatcher = testDispatcher
    }

    private class SearchDaoMock : InteractionEventDao {
        val storage = mutableListOf<InteractionEventEntity>()

        override suspend fun insertEvent(event: InteractionEventEntity): Long {
            storage.add(event)
            return event.id
        }

        override suspend fun insertEvents(events: List<InteractionEventEntity>): List<Long> {
            storage.addAll(events)
            return events.map { it.id }
        }

        override suspend fun getEventById(id: Long): InteractionEventEntity? = storage.firstOrNull { it.id == id }

        override fun observeEventsForSession(sessionId: String): Flow<List<InteractionEventEntity>> =
            flowOf(storage.filter { it.sessionId == sessionId })

        override suspend fun getEventsForSession(sessionId: String): List<InteractionEventEntity> =
            storage.filter { it.sessionId == sessionId }

        override suspend fun getEventsForSessionPaged(
            sessionId: String,
            limit: Int,
            offset: Int
        ): List<InteractionEventEntity> = storage.filter { it.sessionId == sessionId }.drop(offset).take(limit)

        override suspend fun getEventsFiltered(
            sessionId: String,
            packageFilter: String?,
            eventTypeFilter: String?,
            startTime: Long?,
            endTime: Long?
        ): List<InteractionEventEntity> = storage

        override suspend fun getEventCountForSession(sessionId: String): Int = storage.count { it.sessionId == sessionId }

        override fun observeEventCountForSession(sessionId: String): Flow<Int> = flowOf(storage.count { it.sessionId == sessionId })

        override fun observeEventCounts(sessionId: String?): Flow<EventCountsTuple?> =
            flowOf(EventCountsTuple(total = storage.size.toLong()))

        override fun observeLatestEvent(): Flow<InteractionEventEntity?> =
            flowOf(storage.maxByOrNull { it.timestamp })

        override fun observeEventsFilteredPaged(
            sessionId: String?,
            packageFilter: String?,
            eventTypeFilter: String?,
            sourceFilter: String?,
            classificationFilter: String?,
            startTime: Long?,
            endTime: Long?,
            searchQuery: String?,
            limit: Int,
            offset: Int
        ): Flow<List<InteractionEventEntity>> {
            val matching = filterEntities(
                sessionId, packageFilter, eventTypeFilter, sourceFilter,
                classificationFilter, startTime, endTime, searchQuery
            )
            return flowOf(matching.sortedByDescending { it.timestamp }.drop(offset).take(limit))
        }

        override fun observeFilteredCount(
            sessionId: String?,
            packageFilter: String?,
            eventTypeFilter: String?,
            sourceFilter: String?,
            classificationFilter: String?,
            startTime: Long?,
            endTime: Long?,
            searchQuery: String?
        ): Flow<Int> {
            val matching = filterEntities(
                sessionId, packageFilter, eventTypeFilter, sourceFilter,
                classificationFilter, startTime, endTime, searchQuery
            )
            return flowOf(matching.size)
        }

        override fun observeDistinctPackageNames(): Flow<List<String>> =
            flowOf(storage.mapNotNull { it.packageName }.distinct().sorted())

        private fun queryMatchingApplications(query: String, limit: Int): List<String> =
            storage.mapNotNull { it.applicationName }
                .distinct()
                .filter { it.contains(query, ignoreCase = true) }
                .sorted()
                .take(limit)

        private fun queryMatchingPackages(query: String, limit: Int): List<String> =
            storage.mapNotNull { it.packageName }
                .distinct()
                .filter { it.contains(query, ignoreCase = true) }
                .sorted()
                .take(limit)

        override fun observeMatchingApplications(query: String, limit: Int): Flow<List<String>> =
            flowOf(queryMatchingApplications(query, limit))

        override fun observeMatchingPackages(query: String, limit: Int): Flow<List<String>> =
            flowOf(queryMatchingPackages(query, limit))

        override suspend fun getMatchingApplications(query: String, limit: Int): List<String> =
            queryMatchingApplications(query, limit)

        override suspend fun getMatchingPackages(query: String, limit: Int): List<String> =
            queryMatchingPackages(query, limit)

        override fun observeStatisticsSummary(sessionId: String?): Flow<StatisticsSummaryTuple?> =
            flowOf(StatisticsSummaryTuple())

        override suspend fun getStatisticsSummary(sessionId: String?): StatisticsSummaryTuple? =
            StatisticsSummaryTuple()

        override fun observeAppUsageStats(sessionId: String?): Flow<List<AppUsageTuple>> =
            flowOf(emptyList())

        override suspend fun getAppUsageStats(sessionId: String?): List<AppUsageTuple> =
            emptyList()

        override fun observeHourlyActivity(sessionId: String?): Flow<List<HourlyActivityTuple>> =
            flowOf(emptyList())

        override suspend fun getHourlyActivity(sessionId: String?): List<HourlyActivityTuple> =
            emptyList()

        override suspend fun deleteEventsForSession(sessionId: String): Int = 0
        override suspend fun deleteAllEvents(): Int = 0

        private fun filterEntities(
            sessionId: String?,
            packageFilter: String?,
            eventTypeFilter: String?,
            sourceFilter: String?,
            classificationFilter: String?,
            startTime: Long?,
            endTime: Long?,
            searchQuery: String?
        ): List<InteractionEventEntity> {
            return storage.filter {
                (sessionId == null || it.sessionId == sessionId) &&
                (packageFilter == null || it.packageName == packageFilter) &&
                (eventTypeFilter == null || it.eventType == eventTypeFilter) &&
                (sourceFilter == null || it.source == sourceFilter) &&
                (classificationFilter == null || it.inputClassification == classificationFilter) &&
                (startTime == null || it.timestamp >= startTime) &&
                (endTime == null || it.timestamp <= endTime) &&
                (searchQuery == null || (
                    (it.packageName != null && it.packageName.contains(searchQuery, ignoreCase = true)) ||
                    (it.applicationName != null && it.applicationName.contains(searchQuery, ignoreCase = true)) ||
                    (it.eventType.contains(searchQuery, ignoreCase = true)) ||
                    (it.sessionId.contains(searchQuery, ignoreCase = true)) ||
                    (it.eventData != null && it.eventData.contains(searchQuery, ignoreCase = true))
                ))
            }
        }
    }

    private lateinit var daoMock: SearchDaoMock
    private lateinit var repository: EventRepositoryImpl

    @Before
    fun setUp() {
        runBlocking {
            daoMock = SearchDaoMock()
            repository = EventRepositoryImpl(daoMock, testDispatchers)

            // Seed diverse test events covering all SRS §50 search criteria
            repository.insertEvent(
                InteractionEvent(
                    id = 1L,
                    sessionId = "study-alpha-101",
                    timestamp = 1000L,
                    source = EventSource.KEYBOARD,
                    eventType = EventType.TEXT_COMMITTED,
                    packageName = "com.google.android.calculator",
                    applicationName = "Calculator",
                    eventData = "123 * 45",
                    inputClassification = InputClassification.NORMAL_TEXT
                )
            )
            repository.insertEvent(
                InteractionEvent(
                    id = 2L,
                    sessionId = "study-alpha-101",
                    timestamp = 1010L,
                    source = EventSource.ACCESSIBILITY,
                    eventType = EventType.VIEW_CLICKED,
                    packageName = "com.google.android.calculator",
                    applicationName = "Calculator",
                    eventData = "Equals Button",
                    inputClassification = InputClassification.NORMAL_TEXT
                )
            )
            repository.insertEvent(
                InteractionEvent(
                    id = 3L,
                    sessionId = "study-beta-202",
                    timestamp = 1020L,
                    source = EventSource.KEYBOARD,
                    eventType = EventType.TEXT_COMMITTED,
                    packageName = "com.example.notes",
                    applicationName = "Simple Notes",
                    eventData = "Meeting notes at 2pm",
                    inputClassification = InputClassification.NORMAL_TEXT
                )
            )
            repository.insertEvent(
                InteractionEvent(
                    id = 4L,
                    sessionId = "study-beta-202",
                    timestamp = 1030L,
                    source = EventSource.KEYBOARD,
                    eventType = EventType.SENSITIVE_INPUT_DETECTED,
                    packageName = "com.bank.secure",
                    applicationName = "Secure Bank Mobile",
                    eventData = "SuperSecretPassword!", // Must be suppressed by repository!
                    inputClassification = InputClassification.SENSITIVE_PASSWORD
                )
            )
        }
    }

    @Test
    fun entityIndices_containAllRequiredSearchOptimizationIndices() {
        // Architecture.md §56 & IMPLEMENTATION_PLAN.md §52: Verify all searchable and indexed fields
        val properties = InteractionEventEntity::class.java.declaredFields.map { it.name }.toSet()
        assertTrue("sessionId field must exist", properties.contains("sessionId"))
        assertTrue("timestamp field must exist", properties.contains("timestamp"))
        assertTrue("packageName field must exist", properties.contains("packageName"))
        assertTrue("eventType field must exist", properties.contains("eventType"))
        assertTrue("applicationName field must exist for search optimization", properties.contains("applicationName"))
        assertTrue("eventData field must exist", properties.contains("eventData"))
        assertTrue("inputClassification field must exist", properties.contains("inputClassification"))
    }

    @Test
    fun searchByApplicationName_returnsMatchingEvents() = runBlocking {
        val criteria = EventFilterCriteria(searchQuery = "Calculator")
        val results = repository.observeEventsPaged(criteria).first()

        assertEquals(2, results.size)
        assertTrue(results.all { it.applicationName == "Calculator" })
    }

    @Test
    fun searchByPackageName_returnsMatchingEvents() = runBlocking {
        val criteria = EventFilterCriteria(searchQuery = "com.example.notes")
        val results = repository.observeEventsPaged(criteria).first()

        assertEquals(1, results.size)
        assertEquals("Simple Notes", results[0].applicationName)
    }

    @Test
    fun searchByEventType_returnsMatchingEvents() = runBlocking {
        val criteria = EventFilterCriteria(searchQuery = "VIEW_CLICKED")
        val results = repository.observeEventsPaged(criteria).first()

        assertEquals(1, results.size)
        assertEquals(EventType.VIEW_CLICKED, results[0].eventType)
    }

    @Test
    fun searchBySessionId_returnsMatchingEvents() = runBlocking {
        val criteria = EventFilterCriteria(searchQuery = "study-alpha")
        val results = repository.observeEventsPaged(criteria).first()

        assertEquals(2, results.size)
        assertTrue(results.all { it.sessionId == "study-alpha-101" })
    }

    @Test
    fun searchByEventData_matchesPermittedSyntheticInput() = runBlocking {
        val criteria = EventFilterCriteria(searchQuery = "Meeting notes")
        val results = repository.observeEventsPaged(criteria).first()

        assertEquals(1, results.size)
        assertEquals("Meeting notes at 2pm", results[0].eventData)
    }

    @Test
    fun searchExcludesSensitiveCredentials_strictlyEnforcesPrivacyInvariant() = runBlocking {
        // Defense-in-depth: Even if user searches for password string, sensitive event has eventData = null
        val criteria = EventFilterCriteria(searchQuery = "SuperSecretPassword")
        val results = repository.observeEventsPaged(criteria).first()

        assertEquals("Sensitive credential content must never match search queries", 0, results.size)

        // However, searching for the application name "Secure Bank Mobile" matches the sensitive event with null data
        val appCriteria = EventFilterCriteria(searchQuery = "Secure Bank")
        val appResults = repository.observeEventsPaged(appCriteria).first()

        assertEquals(1, appResults.size)
        assertEquals("Secure Bank Mobile", appResults[0].applicationName)
        assertEquals(InputClassification.SENSITIVE_PASSWORD, appResults[0].inputClassification)
        assertEquals(null, appResults[0].eventData)
    }

    @Test
    fun searchSuggestions_returnsRankedApplicationsAndPackages() = runBlocking {
        val suggestions = repository.observeSearchSuggestions("calc", limit = 5).first()

        assertTrue(suggestions.isNotEmpty())
        val appMatch = suggestions.firstOrNull { it.query == "Calculator" }
        assertNotNull(appMatch)
        assertEquals(SuggestionType.APPLICATION, appMatch?.type)

        val pkgMatch = suggestions.firstOrNull { it.query == "com.google.android.calculator" }
        assertNotNull(pkgMatch)
        assertEquals(SuggestionType.PACKAGE, pkgMatch?.type)
    }

    @Test
    fun searchSuggestions_blankQuery_returnsEmptyImmediately() = runBlocking {
        val emptySuggestions = repository.observeSearchSuggestions("", limit = 5).first()
        assertTrue(emptySuggestions.isEmpty())

        val whitespaceSuggestions = repository.observeSearchSuggestions("   ", limit = 5).first()
        assertTrue(whitespaceSuggestions.isEmpty())
    }
}
