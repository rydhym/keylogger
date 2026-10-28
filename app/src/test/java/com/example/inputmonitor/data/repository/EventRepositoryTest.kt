package com.example.inputmonitor.data.repository

import com.example.inputmonitor.core.DispatcherProvider
import com.example.inputmonitor.data.dao.InteractionEventDao
import com.example.inputmonitor.data.entity.InteractionEventEntity
import com.example.inputmonitor.domain.model.EventSource
import com.example.inputmonitor.domain.model.EventType
import com.example.inputmonitor.domain.model.InputClassification
import com.example.inputmonitor.domain.model.InteractionEvent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EventRepositoryTest {

    private val testDispatcher = Dispatchers.Unconfined
    private val testDispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
        override val unconfined: CoroutineDispatcher = testDispatcher
    }

    private class FakeInteractionEventDao : InteractionEventDao {
        val events = mutableListOf<InteractionEventEntity>()
        private var autoId = 1L

        override suspend fun insertEvent(event: InteractionEventEntity): Long {
            val assignedId = if (event.id == 0L) autoId++ else event.id
            events.add(event.copy(id = assignedId))
            return assignedId
        }

        override suspend fun insertEvents(entities: List<InteractionEventEntity>): List<Long> {
            return entities.map { insertEvent(it) }
        }

        override suspend fun getEventById(id: Long): InteractionEventEntity? {
            return events.firstOrNull { it.id == id }
        }

        override fun observeEventsForSession(sessionId: String): Flow<List<InteractionEventEntity>> {
            return flowOf(events.filter { it.sessionId == sessionId }.sortedBy { it.timestamp })
        }

        override suspend fun getEventsForSession(sessionId: String): List<InteractionEventEntity> {
            return events.filter { it.sessionId == sessionId }.sortedBy { it.timestamp }
        }

        override suspend fun getEventsForSessionPaged(sessionId: String, limit: Int, offset: Int): List<InteractionEventEntity> {
            return events.filter { it.sessionId == sessionId }
                .sortedBy { it.timestamp }
                .drop(offset)
                .take(limit)
        }

        override suspend fun getEventsFiltered(
            sessionId: String,
            packageFilter: String?,
            eventTypeFilter: String?,
            startTime: Long?,
            endTime: Long?
        ): List<InteractionEventEntity> {
            return events.filter { entity ->
                entity.sessionId == sessionId &&
                        (packageFilter == null || entity.packageName == packageFilter) &&
                        (eventTypeFilter == null || entity.eventType == eventTypeFilter) &&
                        (startTime == null || entity.timestamp >= startTime) &&
                        (endTime == null || entity.timestamp <= endTime)
            }.sortedBy { it.timestamp }
        }

        override suspend fun getEventCountForSession(sessionId: String): Int {
            return events.count { it.sessionId == sessionId }
        }

        override fun observeEventCountForSession(sessionId: String): Flow<Int> {
            return flowOf(events.count { it.sessionId == sessionId })
        }

        override fun observeEventCounts(sessionId: String?): Flow<com.example.inputmonitor.data.dao.EventCountsTuple?> {
            val matching = if (sessionId != null) events.filter { it.sessionId == sessionId } else events
            val tuple = com.example.inputmonitor.data.dao.EventCountsTuple(
                total = matching.size.toLong(),
                keyboard = matching.count { it.source == "KEYBOARD" }.toLong(),
                accessibility = matching.count { it.source == "ACCESSIBILITY" }.toLong(),
                clicks = matching.count { it.eventType == "VIEW_CLICKED" }.toLong(),
                focus = matching.count { it.eventType == "VIEW_FOCUSED" }.toLong(),
                scrolls = matching.count { it.eventType == "VIEW_SCROLLED" }.toLong(),
                windowChanges = matching.count { it.eventType == "WINDOW_STATE_CHANGED" || it.eventType == "WINDOWS_CHANGED" }.toLong()
            )
            return flowOf(tuple)
        }

        override fun observeLatestEvent(): Flow<InteractionEventEntity?> {
            return flowOf(events.maxByOrNull { it.timestamp })
        }

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
            val filtered = events.filter {
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
            }.sortedByDescending { it.timestamp }
            val paged = filtered.drop(offset).take(limit)
            return flowOf(paged)
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
            val filtered = events.filter {
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
            return flowOf(filtered.size)
        }

        override fun observeDistinctPackageNames(): Flow<List<String>> {
            return flowOf(events.mapNotNull { it.packageName }.distinct().sorted())
        }

        override fun observeMatchingApplications(query: String, limit: Int): Flow<List<String>> {
            return flowOf(
                events.mapNotNull { it.applicationName }
                    .distinct()
                    .filter { it.contains(query, ignoreCase = true) }
                    .sorted()
                    .take(limit)
            )
        }

        override fun observeMatchingPackages(query: String, limit: Int): Flow<List<String>> {
            return flowOf(
                events.mapNotNull { it.packageName }
                    .distinct()
                    .filter { it.contains(query, ignoreCase = true) }
                    .sorted()
                    .take(limit)
            )
        }

        override suspend fun getMatchingApplications(query: String, limit: Int): List<String> {
            return events.mapNotNull { it.applicationName }
                .distinct()
                .filter { it.contains(query, ignoreCase = true) }
                .sorted()
                .take(limit)
        }

        override suspend fun getMatchingPackages(query: String, limit: Int): List<String> {
            return events.mapNotNull { it.packageName }
                .distinct()
                .filter { it.contains(query, ignoreCase = true) }
                .sorted()
                .take(limit)
        }

        override fun observeStatisticsSummary(sessionId: String?): Flow<com.example.inputmonitor.data.dao.StatisticsSummaryTuple?> {
            val matching = if (sessionId != null) events.filter { it.sessionId == sessionId } else events
            val tuple = com.example.inputmonitor.data.dao.StatisticsSummaryTuple(
                total = matching.size.toLong(),
                keyboard = matching.count { it.source == "KEYBOARD" }.toLong(),
                accessibility = matching.count { it.source == "ACCESSIBILITY" }.toLong(),
                clicks = matching.count { it.eventType == "VIEW_CLICKED" }.toLong(),
                focus = matching.count { it.eventType == "VIEW_FOCUSED" }.toLong(),
                scrolls = matching.count { it.eventType == "VIEW_SCROLLED" }.toLong(),
                windowChanges = matching.count { it.eventType == "WINDOW_STATE_CHANGED" || it.eventType == "WINDOWS_CHANGED" }.toLong(),
                sensitiveSuppressions = matching.count { it.eventType == "SENSITIVE_INPUT_DETECTED" || it.inputClassification.startsWith("SENSITIVE_") }.toLong(),
                distinctApps = matching.mapNotNull { it.packageName }.distinct().size,
                firstTimestamp = matching.minOfOrNull { it.timestamp },
                lastTimestamp = matching.maxOfOrNull { it.timestamp }
            )
            return flowOf(tuple)
        }

        override suspend fun getStatisticsSummary(sessionId: String?): com.example.inputmonitor.data.dao.StatisticsSummaryTuple? {
            val matching = if (sessionId != null) events.filter { it.sessionId == sessionId } else events
            return com.example.inputmonitor.data.dao.StatisticsSummaryTuple(
                total = matching.size.toLong(),
                keyboard = matching.count { it.source == "KEYBOARD" }.toLong(),
                accessibility = matching.count { it.source == "ACCESSIBILITY" }.toLong(),
                clicks = matching.count { it.eventType == "VIEW_CLICKED" }.toLong(),
                focus = matching.count { it.eventType == "VIEW_FOCUSED" }.toLong(),
                scrolls = matching.count { it.eventType == "VIEW_SCROLLED" }.toLong(),
                windowChanges = matching.count { it.eventType == "WINDOW_STATE_CHANGED" || it.eventType == "WINDOWS_CHANGED" }.toLong(),
                sensitiveSuppressions = matching.count { it.eventType == "SENSITIVE_INPUT_DETECTED" || it.inputClassification.startsWith("SENSITIVE_") }.toLong(),
                distinctApps = matching.mapNotNull { it.packageName }.distinct().size,
                firstTimestamp = matching.minOfOrNull { it.timestamp },
                lastTimestamp = matching.maxOfOrNull { it.timestamp }
            )
        }

        override fun observeAppUsageStats(sessionId: String?): Flow<List<com.example.inputmonitor.data.dao.AppUsageTuple>> {
            val matching = if (sessionId != null) events.filter { it.sessionId == sessionId } else events
            val grouped = matching.filter { it.packageName != null }
                .groupBy { it.packageName!! }
                .map { (pkg, evts) ->
                    com.example.inputmonitor.data.dao.AppUsageTuple(
                        packageName = pkg,
                        applicationName = evts.firstNotNullOfOrNull { it.applicationName } ?: pkg,
                        eventCount = evts.size.toLong(),
                        lastActiveTimestamp = evts.maxOf { it.timestamp }
                    )
                }
                .sortedByDescending { it.eventCount }
            return flowOf(grouped)
        }

        override suspend fun getAppUsageStats(sessionId: String?): List<com.example.inputmonitor.data.dao.AppUsageTuple> {
            val matching = if (sessionId != null) events.filter { it.sessionId == sessionId } else events
            return matching.filter { it.packageName != null }
                .groupBy { it.packageName!! }
                .map { (pkg, evts) ->
                    com.example.inputmonitor.data.dao.AppUsageTuple(
                        packageName = pkg,
                        applicationName = evts.firstNotNullOfOrNull { it.applicationName } ?: pkg,
                        eventCount = evts.size.toLong(),
                        lastActiveTimestamp = evts.maxOf { it.timestamp }
                    )
                }
                .sortedByDescending { it.eventCount }
        }

        override fun observeHourlyActivity(sessionId: String?): Flow<List<com.example.inputmonitor.data.dao.HourlyActivityTuple>> {
            return flowOf(emptyList())
        }

        override suspend fun getHourlyActivity(sessionId: String?): List<com.example.inputmonitor.data.dao.HourlyActivityTuple> {
            return emptyList()
        }

        override suspend fun deleteEventsForSession(sessionId: String): Int {
            val countBefore = events.size
            events.removeAll { it.sessionId == sessionId }
            return countBefore - events.size
        }

        override suspend fun deleteAllEvents(): Int {
            val count = events.size
            events.clear()
            return count
        }
    }

    @Test
    fun insertAndReadEvent_persistsAndRetrievesCorrectly() = runBlocking {
        val fakeDao = FakeInteractionEventDao()
        val repository = EventRepositoryImpl(fakeDao, testDispatchers)

        val event = InteractionEvent(
            sessionId = "sess-1",
            timestamp = 1000L,
            source = EventSource.KEYBOARD,
            eventType = EventType.TEXT_COMMITTED,
            packageName = "com.example.notes",
            applicationName = "Notes",
            eventData = "Hello World",
            inputClassification = InputClassification.NORMAL_TEXT
        )

        val insertedId = repository.insertEvent(event)
        assertTrue(insertedId > 0)

        val retrieved = repository.getEventById(insertedId)
        assertNotNull(retrieved)
        assertEquals("sess-1", retrieved?.sessionId)
        assertEquals("Hello World", retrieved?.eventData)
        assertEquals(InputClassification.NORMAL_TEXT, retrieved?.inputClassification)
    }

    @Test
    fun insertEvents_batchInsertsCorrectly() = runBlocking {
        val fakeDao = FakeInteractionEventDao()
        val repository = EventRepositoryImpl(fakeDao, testDispatchers)

        val events = listOf(
            InteractionEvent(
                sessionId = "sess-1",
                timestamp = 1000L,
                source = EventSource.KEYBOARD,
                eventType = EventType.KEY_PRESS,
                packageName = "com.example.chat",
                applicationName = "Chat",
                eventData = "A",
                inputClassification = InputClassification.NORMAL_TEXT
            ),
            InteractionEvent(
                sessionId = "sess-1",
                timestamp = 1010L,
                source = EventSource.KEYBOARD,
                eventType = EventType.KEY_PRESS,
                packageName = "com.example.chat",
                applicationName = "Chat",
                eventData = "B",
                inputClassification = InputClassification.NORMAL_TEXT
            )
        )

        val ids = repository.insertEvents(events)
        assertEquals(2, ids.size)
        assertEquals(2, repository.getEventCountForSession("sess-1"))
    }

    @Test
    fun getEventsForSessionPaged_returnsCorrectPage() = runBlocking {
        val fakeDao = FakeInteractionEventDao()
        val repository = EventRepositoryImpl(fakeDao, testDispatchers)

        for (i in 1..10) {
            repository.insertEvent(
                InteractionEvent(
                    sessionId = "sess-paged",
                    timestamp = i * 1000L,
                    source = EventSource.KEYBOARD,
                    eventType = EventType.KEY_PRESS,
                    packageName = "com.example.test",
                    applicationName = "Test",
                    eventData = "key-$i",
                    inputClassification = InputClassification.NORMAL_TEXT
                )
            )
        }

        val page1 = repository.getEventsForSessionPaged("sess-paged", limit = 3, offset = 0)
        assertEquals(3, page1.size)
        assertEquals("key-1", page1[0].eventData)
        assertEquals("key-3", page1[2].eventData)

        val page2 = repository.getEventsForSessionPaged("sess-paged", limit = 3, offset = 3)
        assertEquals(3, page2.size)
        assertEquals("key-4", page2[0].eventData)
        assertEquals("key-6", page2[2].eventData)
    }

    @Test
    fun getEventsFiltered_filtersCorrectly() = runBlocking {
        val fakeDao = FakeInteractionEventDao()
        val repository = EventRepositoryImpl(fakeDao, testDispatchers)

        repository.insertEvent(
            InteractionEvent(
                sessionId = "sess-filter",
                timestamp = 1000L,
                source = EventSource.KEYBOARD,
                eventType = EventType.KEY_PRESS,
                packageName = "com.example.app1",
                applicationName = "App 1",
                eventData = "1",
                inputClassification = InputClassification.NORMAL_TEXT
            )
        )
        repository.insertEvent(
            InteractionEvent(
                sessionId = "sess-filter",
                timestamp = 2000L,
                source = EventSource.ACCESSIBILITY,
                eventType = EventType.VIEW_CLICKED,
                packageName = "com.example.app2",
                applicationName = "App 2",
                eventData = "Button Clicked",
                inputClassification = InputClassification.NORMAL_TEXT
            )
        )

        val filteredByPackage = repository.getEventsFiltered(
            sessionId = "sess-filter",
            packageFilter = "com.example.app2",
            eventTypeFilter = null,
            startTime = null,
            endTime = null
        )
        assertEquals(1, filteredByPackage.size)
        assertEquals("com.example.app2", filteredByPackage[0].packageName)

        val filteredByTime = repository.getEventsFiltered(
            sessionId = "sess-filter",
            packageFilter = null,
            eventTypeFilter = null,
            startTime = 1500L,
            endTime = 2500L
        )
        assertEquals(1, filteredByTime.size)
        assertEquals(2000L, filteredByTime[0].timestamp)
    }

    @Test
    fun deleteEventsForSession_deletesOnlyTargetSessionEvents() = runBlocking {
        val fakeDao = FakeInteractionEventDao()
        val repository = EventRepositoryImpl(fakeDao, testDispatchers)

        repository.insertEvent(
            InteractionEvent(
                sessionId = "sess-to-delete",
                timestamp = 1000L,
                source = EventSource.KEYBOARD,
                eventType = EventType.KEY_PRESS,
                packageName = "com.example.test",
                applicationName = "Test",
                eventData = "delete me",
                inputClassification = InputClassification.NORMAL_TEXT
            )
        )
        repository.insertEvent(
            InteractionEvent(
                sessionId = "sess-to-keep",
                timestamp = 1000L,
                source = EventSource.KEYBOARD,
                eventType = EventType.KEY_PRESS,
                packageName = "com.example.test",
                applicationName = "Test",
                eventData = "keep me",
                inputClassification = InputClassification.NORMAL_TEXT
            )
        )

        val success = repository.deleteEventsForSession("sess-to-delete")
        assertTrue(success)
        assertEquals(0, repository.getEventCountForSession("sess-to-delete"))
        assertEquals(1, repository.getEventCountForSession("sess-to-keep"))
    }

    @Test
    fun observeEventsAndCount_emitsCorrectly() = runBlocking {
        val fakeDao = FakeInteractionEventDao()
        val repository = EventRepositoryImpl(fakeDao, testDispatchers)

        repository.insertEvent(
            InteractionEvent(
                sessionId = "sess-flow",
                timestamp = 1000L,
                source = EventSource.KEYBOARD,
                eventType = EventType.KEY_PRESS,
                packageName = "com.example.test",
                applicationName = "Test",
                eventData = "test",
                inputClassification = InputClassification.NORMAL_TEXT
            )
        )

        val count = repository.observeEventCountForSession("sess-flow").first()
        assertEquals(1, count)

        val list = repository.observeEventsForSession("sess-flow").first()
        assertEquals(1, list.size)
        assertEquals("test", list[0].eventData)
    }

    @Test
    fun searchSuggestions_returnsMatchingApplicationsAndPackages() = runBlocking {
        val fakeDao = FakeInteractionEventDao()
        val repository = EventRepositoryImpl(fakeDao, testDispatchers)

        repository.insertEvent(
            InteractionEvent(
                sessionId = "sess-1",
                timestamp = 1000L,
                source = EventSource.KEYBOARD,
                eventType = EventType.TEXT_COMMITTED,
                packageName = "com.google.android.calculator",
                applicationName = "Calculator",
                eventData = "42",
                inputClassification = InputClassification.NORMAL_TEXT
            )
        )
        repository.insertEvent(
            InteractionEvent(
                sessionId = "sess-1",
                timestamp = 1001L,
                source = EventSource.KEYBOARD,
                eventType = EventType.TEXT_COMMITTED,
                packageName = "com.example.calendar",
                applicationName = "Calendar Pro",
                eventData = "meeting",
                inputClassification = InputClassification.NORMAL_TEXT
            )
        )

        // Query matching "Cal" should return Calculator (app), Calendar Pro (app), com.google.android.calculator (pkg), com.example.calendar (pkg)
        val suggestions = repository.observeSearchSuggestions("Cal", limit = 5).first()
        assertTrue(suggestions.isNotEmpty())
        assertTrue(suggestions.any { it.query == "Calculator" && it.type == com.example.inputmonitor.domain.model.SuggestionType.APPLICATION })
        assertTrue(suggestions.any { it.query == "Calendar Pro" && it.type == com.example.inputmonitor.domain.model.SuggestionType.APPLICATION })
        assertTrue(suggestions.any { it.query == "com.google.android.calculator" && it.type == com.example.inputmonitor.domain.model.SuggestionType.PACKAGE })
    }

    @Test
    fun observeEventsPaged_searchQuery_matchesEventTypeAndSessionId() = runBlocking {
        val fakeDao = FakeInteractionEventDao()
        val repository = EventRepositoryImpl(fakeDao, testDispatchers)

        repository.insertEvent(
            InteractionEvent(
                sessionId = "special-session-999",
                timestamp = 1000L,
                source = EventSource.ACCESSIBILITY,
                eventType = EventType.VIEW_CLICKED,
                packageName = "com.example.app",
                applicationName = "App",
                eventData = "button",
                inputClassification = InputClassification.NORMAL_TEXT
            )
        )

        // Match by sessionId
        val sessionMatch = repository.observeEventsPaged(
            filter = com.example.inputmonitor.domain.model.EventFilterCriteria(searchQuery = "special-session")
        ).first()
        assertEquals(1, sessionMatch.size)

        // Match by eventType
        val typeMatch = repository.observeEventsPaged(
            filter = com.example.inputmonitor.domain.model.EventFilterCriteria(searchQuery = "VIEW_CLICKED")
        ).first()
        assertEquals(1, typeMatch.size)
    }
}
