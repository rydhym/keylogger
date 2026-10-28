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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RepositorySafetyValidationTest {

    private val testDispatcher = Dispatchers.Unconfined
    private val testDispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
        override val unconfined: CoroutineDispatcher = testDispatcher
    }

    private class FakeInteractionEventDao : InteractionEventDao {
        val insertedEntities = mutableListOf<InteractionEventEntity>()

        override suspend fun insertEvent(event: InteractionEventEntity): Long {
            insertedEntities.add(event)
            return insertedEntities.size.toLong()
        }

        override suspend fun insertEvents(events: List<InteractionEventEntity>): List<Long> {
            insertedEntities.addAll(events)
            return events.indices.map { it.toLong() }
        }

        override suspend fun getEventById(id: Long): InteractionEventEntity? = null
        override fun observeEventsForSession(sessionId: String): Flow<List<InteractionEventEntity>> = flowOf(emptyList())
        override suspend fun getEventsForSession(sessionId: String): List<InteractionEventEntity> = insertedEntities
        override suspend fun getEventsForSessionPaged(sessionId: String, limit: Int, offset: Int): List<InteractionEventEntity> = emptyList()
        override suspend fun getEventsFiltered(sessionId: String, packageFilter: String?, eventTypeFilter: String?, startTime: Long?, endTime: Long?): List<InteractionEventEntity> = emptyList()
        override suspend fun getEventCountForSession(sessionId: String): Int = insertedEntities.size
        override fun observeEventCountForSession(sessionId: String): Flow<Int> = flowOf(insertedEntities.size)
        override fun observeEventCounts(sessionId: String?): Flow<com.example.inputmonitor.data.dao.EventCountsTuple?> = flowOf(com.example.inputmonitor.data.dao.EventCountsTuple(total = insertedEntities.size.toLong()))
        override fun observeLatestEvent(): Flow<InteractionEventEntity?> = flowOf(insertedEntities.lastOrNull())
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
        ): Flow<List<InteractionEventEntity>> = flowOf(insertedEntities)
        override fun observeFilteredCount(
            sessionId: String?,
            packageFilter: String?,
            eventTypeFilter: String?,
            sourceFilter: String?,
            classificationFilter: String?,
            startTime: Long?,
            endTime: Long?,
            searchQuery: String?
        ): Flow<Int> = flowOf(insertedEntities.size)
        override fun observeDistinctPackageNames(): Flow<List<String>> = flowOf(emptyList())
        override fun observeMatchingApplications(query: String, limit: Int): Flow<List<String>> = flowOf(emptyList())
        override fun observeMatchingPackages(query: String, limit: Int): Flow<List<String>> = flowOf(emptyList())
        override suspend fun getMatchingApplications(query: String, limit: Int): List<String> = emptyList()
        override suspend fun getMatchingPackages(query: String, limit: Int): List<String> = emptyList()
        override fun observeStatisticsSummary(sessionId: String?): Flow<com.example.inputmonitor.data.dao.StatisticsSummaryTuple?> =
            flowOf(com.example.inputmonitor.data.dao.StatisticsSummaryTuple())
        override suspend fun getStatisticsSummary(sessionId: String?): com.example.inputmonitor.data.dao.StatisticsSummaryTuple? =
            com.example.inputmonitor.data.dao.StatisticsSummaryTuple()
        override fun observeAppUsageStats(sessionId: String?): Flow<List<com.example.inputmonitor.data.dao.AppUsageTuple>> =
            flowOf(emptyList())
        override suspend fun getAppUsageStats(sessionId: String?): List<com.example.inputmonitor.data.dao.AppUsageTuple> =
            emptyList()
        override fun observeHourlyActivity(sessionId: String?): Flow<List<com.example.inputmonitor.data.dao.HourlyActivityTuple>> =
            flowOf(emptyList())
        override suspend fun getHourlyActivity(sessionId: String?): List<com.example.inputmonitor.data.dao.HourlyActivityTuple> =
            emptyList()
        override suspend fun deleteEventsForSession(sessionId: String): Int = 0
        override suspend fun deleteAllEvents(): Int = 0
    }

    @Test
    fun repository_suppressesSensitivePasswordContent() = runBlocking {
        val fakeDao = FakeInteractionEventDao()
        val repository = EventRepositoryImpl(fakeDao, testDispatchers)

        val dangerousPasswordEvent = InteractionEvent(
            sessionId = "session-1",
            timestamp = 1000L,
            source = EventSource.KEYBOARD,
            eventType = EventType.KEY_PRESS,
            packageName = "com.example.bank",
            applicationName = "Bank App",
            eventData = "MySecretPassword123!",
            inputClassification = InputClassification.SENSITIVE_PASSWORD
        )

        repository.insertEvent(dangerousPasswordEvent)

        assertEquals(1, fakeDao.insertedEntities.size)
        val persisted = fakeDao.insertedEntities.first()

        assertNull("Repository must enforce null eventData for SENSITIVE_PASSWORD", persisted.eventData)
        assertEquals("SENSITIVE_PASSWORD", persisted.inputClassification)
        assertEquals("com.example.bank", persisted.packageName)
    }

    @Test
    fun repository_suppressesSensitivePinContent() = runBlocking {
        val fakeDao = FakeInteractionEventDao()
        val repository = EventRepositoryImpl(fakeDao, testDispatchers)

        val dangerousPinEvent = InteractionEvent(
            sessionId = "session-1",
            timestamp = 1000L,
            source = EventSource.KEYBOARD,
            eventType = EventType.KEY_PRESS,
            packageName = "com.example.atm",
            applicationName = "ATM App",
            eventData = "9988",
            inputClassification = InputClassification.SENSITIVE_PIN
        )

        repository.insertEvent(dangerousPinEvent)

        val persisted = fakeDao.insertedEntities.first()
        assertNull("Repository must enforce null eventData for SENSITIVE_PIN", persisted.eventData)
    }

    @Test
    fun repository_preservesNormalNonSensitiveContent() = runBlocking {
        val fakeDao = FakeInteractionEventDao()
        val repository = EventRepositoryImpl(fakeDao, testDispatchers)

        val normalEvent = InteractionEvent(
            sessionId = "session-1",
            timestamp = 1000L,
            source = EventSource.KEYBOARD,
            eventType = EventType.TEXT_COMMITTED,
            packageName = "com.example.notes",
            applicationName = "Notes App",
            eventData = "Shopping list: apples, milk",
            inputClassification = InputClassification.NORMAL_TEXT
        )

        repository.insertEvent(normalEvent)

        val persisted = fakeDao.insertedEntities.first()
        assertEquals("Shopping list: apples, milk", persisted.eventData)
        assertEquals("NORMAL_TEXT", persisted.inputClassification)
    }
}
