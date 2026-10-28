package com.example.inputmonitor.ui.testbench

import com.example.inputmonitor.core.DispatcherProvider
import com.example.inputmonitor.domain.model.EventCounts
import com.example.inputmonitor.domain.model.InputClassification
import com.example.inputmonitor.domain.model.InteractionEvent
import com.example.inputmonitor.domain.model.InteractionStatistics
import com.example.inputmonitor.domain.model.MonitoringSession
import com.example.inputmonitor.domain.model.SessionState
import com.example.inputmonitor.domain.model.StatisticsSummary
import com.example.inputmonitor.domain.repository.EventRepository
import com.example.inputmonitor.domain.session.SessionManager
import com.example.inputmonitor.domain.usecase.ObserveActiveSessionUseCase
import com.example.inputmonitor.domain.usecase.ObserveSessionStateUseCase
import com.example.inputmonitor.domain.usecase.StartMonitoringSessionUseCase
import com.example.inputmonitor.domain.usecase.StopMonitoringSessionUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TestBenchViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testDispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
        override val unconfined: CoroutineDispatcher = testDispatcher
    }

    private class FakeSessionManager : SessionManager {
        val _sessionState = MutableStateFlow(SessionState.STOPPED)
        override val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

        val _currentSession = MutableStateFlow<MonitoringSession?>(null)
        override val currentSession: StateFlow<MonitoringSession?> = _currentSession.asStateFlow()

        var startSessionCalled = false
        var stopSessionCalled = false

        override suspend fun startSession(name: String, notes: String?): MonitoringSession {
            startSessionCalled = true
            val session = MonitoringSession(
                id = "sess-123",
                name = name,
                startTime = System.currentTimeMillis(),
                state = SessionState.ACTIVE
            )
            _currentSession.value = session
            _sessionState.value = SessionState.ACTIVE
            return session
        }

        override suspend fun pauseSession(): Boolean = true
        override suspend fun resumeSession(): Boolean = true

        override suspend fun stopSession(): Boolean {
            stopSessionCalled = true
            _sessionState.value = SessionState.STOPPED
            return true
        }

        override suspend fun restoreSessionIfActive(): MonitoringSession? = null
    }

    private val fakeSessionManager = FakeSessionManager()
    private val observeActiveSessionUseCase = ObserveActiveSessionUseCase(fakeSessionManager)
    private val observeSessionStateUseCase = ObserveSessionStateUseCase(fakeSessionManager)
    private val startMonitoringSessionUseCase = StartMonitoringSessionUseCase(fakeSessionManager)
    private val stopMonitoringSessionUseCase = StopMonitoringSessionUseCase(fakeSessionManager)

    private val fakeEventRepository = FakeEventRepository()

    private class FakeEventRepository : EventRepository {
        val latestEventFlow = MutableStateFlow<InteractionEvent?>(null)
        val statisticsFlow = MutableStateFlow(
            InteractionStatistics(
                summary = StatisticsSummary(
                    totalEvents = 25L,
                    keyboardEvents = 20L,
                    sensitiveSuppressions = 5L
                )
            )
        )

        override suspend fun insertEvent(event: InteractionEvent): Long = 1L
        override suspend fun insertEvents(events: List<InteractionEvent>): List<Long> = emptyList()
        override suspend fun getEventById(id: Long): InteractionEvent? = null
        override fun observeEventsForSession(sessionId: String): Flow<List<InteractionEvent>> = flowOf(emptyList())
        override suspend fun getEventsForSession(sessionId: String): List<InteractionEvent> = emptyList()
        override suspend fun getEventsForSessionPaged(sessionId: String, limit: Int, offset: Int): List<InteractionEvent> = emptyList()
        override suspend fun getEventsFiltered(
            sessionId: String,
            packageFilter: String?,
            eventTypeFilter: String?,
            startTime: Long?,
            endTime: Long?
        ): List<InteractionEvent> = emptyList()
        override suspend fun getEventCountForSession(sessionId: String): Int = 0
        override fun observeEventCountForSession(sessionId: String): Flow<Int> = flowOf(0)
        override fun observeEventCounts(sessionId: String?): Flow<EventCounts> = flowOf(EventCounts())
        override fun observeLatestEvent(): Flow<InteractionEvent?> = latestEventFlow
        override fun observeEventsPaged(
            filter: com.example.inputmonitor.domain.model.EventFilterCriteria,
            limit: Int,
            offset: Int
        ): Flow<List<InteractionEvent>> = flowOf(emptyList())
        override fun observeFilteredCount(filter: com.example.inputmonitor.domain.model.EventFilterCriteria): Flow<Int> = flowOf(0)
        override fun observeDistinctPackageNames(): Flow<List<String>> = flowOf(emptyList())
        override fun observeStatistics(sessionId: String?): Flow<InteractionStatistics> = statisticsFlow
        override suspend fun getStatistics(sessionId: String?): InteractionStatistics = statisticsFlow.value
        override suspend fun deleteEventsForSession(sessionId: String): Boolean = true
        override suspend fun deleteAllEvents(): Boolean = true
    }

    private lateinit var viewModel: TestBenchViewModel

    @Before
    fun setUp() {
        kotlinx.coroutines.Dispatchers.setMain(testDispatcher)
        viewModel = TestBenchViewModel(
            observeActiveSessionUseCase = observeActiveSessionUseCase,
            observeSessionStateUseCase = observeSessionStateUseCase,
            startMonitoringSessionUseCase = startMonitoringSessionUseCase,
            stopMonitoringSessionUseCase = stopMonitoringSessionUseCase,
            eventRepository = fakeEventRepository,
            dispatchers = testDispatchers
        )
    }

    @After
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun initialState_defaultsCorrectly() = runTest {
        advanceUntilIdle()
        val state = viewModel.uiState.value

        assertNull(state.activeSession)
        assertEquals(SessionState.STOPPED, state.sessionState)
        assertFalse(state.isSessionActive)
        assertEquals(TestBenchFieldType.NORMAL_TEXT, state.activeField)
        assertEquals(InputClassification.NORMAL_TEXT, state.detectedClassification)
        assertFalse(state.isSensitiveMode)
        assertTrue(state.fieldValues.values.all { it.isEmpty() })
        assertNull(state.submissionResult)
        assertNull(state.feedbackMessage)
        assertNull(state.errorMessage)
    }

    @Test
    fun activeSessionUpdate_reflectsInUiState() = runTest {
        val session = MonitoringSession(
            id = "sess-1",
            name = "Active_Test_Session",
            startTime = 5000L,
            state = SessionState.ACTIVE
        )
        fakeSessionManager._currentSession.value = session
        fakeSessionManager._sessionState.value = SessionState.ACTIVE
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(session, state.activeSession)
        assertEquals(SessionState.ACTIVE, state.sessionState)
        assertTrue(state.isSessionActive)
        assertEquals(25L, state.totalEventsInSession)
        assertEquals(20L, state.keyboardEventsInSession)
        assertEquals(5L, state.sensitiveSuppressionsInSession)
    }

    @Test
    fun focusChanges_updatesActiveFieldAndClassification_forNonSensitiveFields() = runTest {
        advanceUntilIdle()

        // 1. Normal Text
        viewModel.onFieldFocused(TestBenchFieldType.NORMAL_TEXT)
        advanceUntilIdle()
        assertEquals(TestBenchFieldType.NORMAL_TEXT, viewModel.uiState.value.activeField)
        assertEquals(InputClassification.NORMAL_TEXT, viewModel.uiState.value.detectedClassification)
        assertFalse(viewModel.uiState.value.isSensitiveMode)

        // 2. Email Address
        viewModel.onFieldFocused(TestBenchFieldType.EMAIL)
        advanceUntilIdle()
        assertEquals(TestBenchFieldType.EMAIL, viewModel.uiState.value.activeField)
        assertEquals(InputClassification.EMAIL_ADDRESS, viewModel.uiState.value.detectedClassification)
        assertFalse(viewModel.uiState.value.isSensitiveMode)

        // 3. URL
        viewModel.onFieldFocused(TestBenchFieldType.URL)
        advanceUntilIdle()
        assertEquals(TestBenchFieldType.URL, viewModel.uiState.value.activeField)
        assertEquals(InputClassification.NORMAL_TEXT, viewModel.uiState.value.detectedClassification)
        assertFalse(viewModel.uiState.value.isSensitiveMode)

        // 4. Number
        viewModel.onFieldFocused(TestBenchFieldType.NUMBER)
        advanceUntilIdle()
        assertEquals(TestBenchFieldType.NUMBER, viewModel.uiState.value.activeField)
        assertEquals(InputClassification.NUMBER, viewModel.uiState.value.detectedClassification)
        assertFalse(viewModel.uiState.value.isSensitiveMode)

        // 5. Multiline
        viewModel.onFieldFocused(TestBenchFieldType.MULTILINE)
        advanceUntilIdle()
        assertEquals(TestBenchFieldType.MULTILINE, viewModel.uiState.value.activeField)
        assertEquals(InputClassification.NORMAL_TEXT, viewModel.uiState.value.detectedClassification)
        assertFalse(viewModel.uiState.value.isSensitiveMode)
    }

    @Test
    fun focusChanges_updatesActiveFieldAndClassification_forSensitiveFields() = runTest {
        advanceUntilIdle()

        // 1. Password field
        viewModel.onFieldFocused(TestBenchFieldType.PASSWORD)
        advanceUntilIdle()
        assertEquals(TestBenchFieldType.PASSWORD, viewModel.uiState.value.activeField)
        assertEquals(InputClassification.SENSITIVE_PASSWORD, viewModel.uiState.value.detectedClassification)
        assertTrue(viewModel.uiState.value.isSensitiveMode)

        // 2. PIN field
        viewModel.onFieldFocused(TestBenchFieldType.PIN)
        advanceUntilIdle()
        assertEquals(TestBenchFieldType.PIN, viewModel.uiState.value.activeField)
        assertEquals(InputClassification.SENSITIVE_PIN, viewModel.uiState.value.detectedClassification)
        assertTrue(viewModel.uiState.value.isSensitiveMode)
    }

    @Test
    fun fieldValueChanged_updatesSpecificField() = runTest {
        advanceUntilIdle()

        viewModel.onFieldValueChanged(TestBenchFieldType.NORMAL_TEXT, "Hello World")
        viewModel.onFieldValueChanged(TestBenchFieldType.PASSWORD, "SuperSecret123")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Hello World", state.getFieldValue(TestBenchFieldType.NORMAL_TEXT))
        assertEquals("SuperSecret123", state.getFieldValue(TestBenchFieldType.PASSWORD))
        assertEquals("", state.getFieldValue(TestBenchFieldType.EMAIL))
    }

    @Test
    fun clearAllFields_resetsAllEntries() = runTest {
        advanceUntilIdle()

        viewModel.onFieldValueChanged(TestBenchFieldType.NORMAL_TEXT, "Some text")
        viewModel.onFieldValueChanged(TestBenchFieldType.PIN, "1234")
        advanceUntilIdle()

        viewModel.clearAllFields()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.fieldValues.values.all { it.isEmpty() })
        assertEquals("All test fields cleared.", state.feedbackMessage)
        assertNull(state.submissionResult)
    }

    @Test
    fun populateSyntheticTestData_populatesAllKnownGroundTruthValues() = runTest {
        advanceUntilIdle()

        viewModel.populateSyntheticTestData()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(TestBenchFieldType.NORMAL_TEXT.sampleValue, state.getFieldValue(TestBenchFieldType.NORMAL_TEXT))
        assertEquals(TestBenchFieldType.EMAIL.sampleValue, state.getFieldValue(TestBenchFieldType.EMAIL))
        assertEquals(TestBenchFieldType.URL.sampleValue, state.getFieldValue(TestBenchFieldType.URL))
        assertEquals(TestBenchFieldType.NUMBER.sampleValue, state.getFieldValue(TestBenchFieldType.NUMBER))
        assertEquals(TestBenchFieldType.MULTILINE.sampleValue, state.getFieldValue(TestBenchFieldType.MULTILINE))
        assertEquals(TestBenchFieldType.PASSWORD.sampleValue, state.getFieldValue(TestBenchFieldType.PASSWORD))
        assertEquals(TestBenchFieldType.PIN.sampleValue, state.getFieldValue(TestBenchFieldType.PIN))
        assertNotNull(state.feedbackMessage)
    }

    @Test
    fun quickSessionStart_invokesUseCaseAndUpdatesFeedback() = runTest {
        advanceUntilIdle()

        viewModel.startQuickTestSession()
        advanceUntilIdle()

        assertTrue(fakeSessionManager.startSessionCalled)
        assertEquals("Test monitoring session started.", viewModel.uiState.value.feedbackMessage)
    }

    @Test
    fun quickSessionStop_invokesUseCaseAndUpdatesFeedback() = runTest {
        advanceUntilIdle()

        viewModel.stopQuickTestSession()
        advanceUntilIdle()

        assertTrue(fakeSessionManager.stopSessionCalled)
        assertEquals("Monitoring session stopped.", viewModel.uiState.value.feedbackMessage)
    }

    @Test
    fun submitTestInputs_evaluatesInvariantsAndCreatesSubmissionResult() = runTest {
        advanceUntilIdle()

        viewModel.populateSyntheticTestData()
        advanceUntilIdle()

        viewModel.submitTestInputs()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.submissionResult)
        val result = state.submissionResult!!
        assertEquals(7, result.totalFieldsSubmitted)
        assertEquals(2, result.sensitiveFieldsCount)
        assertEquals(5, result.nonSensitiveFieldsCount)
        assertTrue(result.isCompliant)
        assertEquals(4, result.invariantsVerified.size)
        assertTrue(result.invariantsVerified.any { it.contains("AC-29") })
        assertTrue(result.invariantsVerified.any { it.contains("AC-30") })
        assertTrue(result.invariantsVerified.any { it.contains("AC-31") })
        assertTrue(result.invariantsVerified.any { it.contains("AC-32") })
    }

    @Test
    fun dismissMessages_resetsFeedbackAndSubmission() = runTest {
        advanceUntilIdle()

        viewModel.populateSyntheticTestData()
        viewModel.submitTestInputs()
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.feedbackMessage)
        assertNotNull(viewModel.uiState.value.submissionResult)

        viewModel.dismissFeedback()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.feedbackMessage)

        viewModel.dismissSubmissionResult()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.submissionResult)
    }
}
