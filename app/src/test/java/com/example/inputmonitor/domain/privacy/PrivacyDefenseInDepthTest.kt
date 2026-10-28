package com.example.inputmonitor.domain.privacy

import android.text.InputType
import android.view.inputmethod.EditorInfo
import com.example.inputmonitor.core.DefaultDispatcherProvider
import com.example.inputmonitor.data.dao.InteractionEventDao
import com.example.inputmonitor.data.entity.InteractionEventEntity
import com.example.inputmonitor.data.mapper.toEntity
import com.example.inputmonitor.data.repository.EventRepositoryImpl
import com.example.inputmonitor.domain.model.EventSource
import com.example.inputmonitor.domain.model.EventType
import com.example.inputmonitor.domain.model.InputClassification
import com.example.inputmonitor.domain.model.InteractionEvent
import com.example.inputmonitor.domain.model.RawInteractionEvent
import com.example.inputmonitor.platform.ime.analysis.EditorInfoAnalyzer
import com.example.inputmonitor.platform.ime.analysis.EditorInfoAnalyzerImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * End-to-end multi-layer defense-in-depth privacy validation test
 * (Architecture.md §40, SECURITY.md §8, §9, TESTING.md §5, §6, §7).
 *
 * Verifies that synthetic secrets entered in sensitive password or PIN fields
 * NEVER reach domain models, entities, or SQLite persistence in plaintext.
 */
class PrivacyDefenseInDepthTest {

    private lateinit var analyzer: EditorInfoAnalyzer
    private lateinit var detector: SensitiveInputDetector
    private lateinit var privacyFilter: PrivacyFilter
    private lateinit var fakeDao: FakeInteractionEventDao
    private lateinit var repository: EventRepositoryImpl

    private val syntheticPassword = "SyntheticSecretPassword99!"
    private val syntheticPin = "8392"

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
        override suspend fun getEventsForSession(sessionId: String): List<InteractionEventEntity> =
            insertedEntities.filter { it.sessionId == sessionId }
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

    @Before
    fun setUp() {
        analyzer = EditorInfoAnalyzerImpl()
        detector = SensitiveInputDetectorImpl()
        privacyFilter = PrivacyFilterImpl()
        fakeDao = FakeInteractionEventDao()
        repository = EventRepositoryImpl(fakeDao, DefaultDispatcherProvider())
    }

    @Test
    fun endToEndPipeline_passwordField_completelySuppressesSecretAtAllLayers(): Unit = runBlocking {
        // Layer 1: Editor analysis from Android EditorInfo
        val editorInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            packageName = "com.example.bank.login"
            imeOptions = EditorInfo.IME_ACTION_DONE
        }
        val editorContext = analyzer.analyze(editorInfo)
        assertEquals(FieldCategory.PASSWORD, editorContext.fieldCategory)
        assertTrue(editorContext.isSensitive)

        // Layer 2: Sensitive input detection
        val classification = detector.classify(editorContext)
        assertEquals(InputClassification.SENSITIVE_PASSWORD, classification)
        assertTrue(classification.isSensitive)

        // Layer 3: Ephemeral raw event candidate from IME keystroke
        val rawEvent = RawInteractionEvent(
            timestamp = 1700000000000L,
            source = EventSource.KEYBOARD,
            eventType = EventType.TEXT_COMMITTED,
            packageName = editorContext.packageName,
            candidateData = syntheticPassword,
            inputClassification = classification,
            metadata = mapOf("fieldCategory" to editorContext.fieldCategory.name)
        )

        // Layer 4: PrivacyFilter sanitization
        val sanitizedEvent = privacyFilter.sanitize(rawEvent)
        assertNull("PrivacyFilter must set eventData to null", sanitizedEvent.eventData)
        assertEquals(EventType.SENSITIVE_INPUT_DETECTED, sanitizedEvent.eventType)

        // Layer 5: Conversion to persistent domain event
        val domainEvent = InteractionEvent(
            id = 1L,
            sessionId = "session-test-1",
            timestamp = sanitizedEvent.timestamp,
            source = sanitizedEvent.source,
            eventType = sanitizedEvent.eventType,
            packageName = sanitizedEvent.packageName,
            applicationName = "Bank App",
            eventData = sanitizedEvent.eventData,
            inputClassification = sanitizedEvent.inputClassification,
            metadata = "{}"
        )
        assertNull("Domain event data must be null", domainEvent.eventData)

        // Layer 6: Entity mapping
        val entity = domainEvent.toEntity()
        assertNull("Entity eventData must be null", entity.eventData)
        assertFalse(entity.toString().contains(syntheticPassword))

        // Layer 7: Repository persistence safety validation
        repository.insertEvent(domainEvent)

        val persistedEvents = repository.getEventsForSession("session-test-1")
        assertEquals(1, persistedEvents.size)
        val persistedEvent = persistedEvents.first()

        assertNull("Persisted eventData in SQLite must be null", persistedEvent.eventData)
        assertEquals(EventType.SENSITIVE_INPUT_DETECTED, persistedEvent.eventType)
        assertEquals(InputClassification.SENSITIVE_PASSWORD, persistedEvent.inputClassification)
        assertFalse("Persisted event must not contain synthetic password", persistedEvent.toString().contains(syntheticPassword))
    }

    @Test
    fun endToEndPipeline_numericPinField_completelySuppressesPinAtAllLayers(): Unit = runBlocking {
        val editorInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            packageName = "com.example.atm"
        }
        val editorContext = analyzer.analyze(editorInfo)
        val classification = detector.classify(editorContext)
        assertEquals(InputClassification.SENSITIVE_PIN, classification)

        val rawEvent = RawInteractionEvent(
            timestamp = 1700000001000L,
            source = EventSource.KEYBOARD,
            eventType = EventType.KEY_PRESS,
            packageName = editorContext.packageName,
            candidateData = syntheticPin,
            inputClassification = classification
        )

        val sanitizedEvent = privacyFilter.sanitize(rawEvent)
        assertNull(sanitizedEvent.eventData)

        val domainEvent = InteractionEvent(
            id = 2L,
            sessionId = "session-test-2",
            timestamp = sanitizedEvent.timestamp,
            source = sanitizedEvent.source,
            eventType = sanitizedEvent.eventType,
            packageName = sanitizedEvent.packageName,
            applicationName = null,
            eventData = sanitizedEvent.eventData,
            inputClassification = sanitizedEvent.inputClassification,
            metadata = null
        )

        repository.insertEvent(domainEvent)

        val persisted = repository.getEventsForSession("session-test-2").first()
        assertNull(persisted.eventData)
        assertEquals(InputClassification.SENSITIVE_PIN, persisted.inputClassification)
    }

    @Test
    fun repositoryDefenseInDepth_catchesBypassedPrivacyFilter(): Unit = runBlocking {
        // Even if an upstream bug bypassed PrivacyFilter and attempted to submit a sensitive
        // event with candidateData directly to the repository:
        val leakingDomainEvent = InteractionEvent(
            id = 3L,
            sessionId = "session-test-3",
            timestamp = 1700000002000L,
            source = EventSource.KEYBOARD,
            eventType = EventType.KEY_PRESS,
            packageName = "com.example.leak",
            applicationName = null,
            eventData = "leakedSecretToken",
            inputClassification = InputClassification.SENSITIVE_PASSWORD,
            metadata = null
        )

        repository.insertEvent(leakingDomainEvent)

        val persisted = repository.getEventsForSession("session-test-3").first()
        assertNull("Repository safety layer must nullify eventData", persisted.eventData)
    }
}
