package com.example.inputmonitor.ui.testbench

import com.example.inputmonitor.domain.model.InputClassification
import com.example.inputmonitor.domain.model.InteractionEvent
import com.example.inputmonitor.domain.model.MonitoringSession
import com.example.inputmonitor.domain.model.SessionState

/**
 * Result structure produced when synthetic test input is submitted for verification (SRS.md §66).
 */
data class TestBenchSubmissionResult(
    val timestamp: Long,
    val totalFieldsSubmitted: Int,
    val sensitiveFieldsCount: Int,
    val nonSensitiveFieldsCount: Int,
    val invariantsVerified: List<String>,
    val isCompliant: Boolean
)

/**
 * UI State for the Controlled Application Use Test Screen (IMPLEMENTATION_PLAN.md §56, design.md §Screen 6).
 */
data class TestBenchUiState(
    val activeSession: MonitoringSession? = null,
    val sessionState: SessionState = SessionState.STOPPED,
    val totalEventsInSession: Long = 0,
    val keyboardEventsInSession: Long = 0,
    val sensitiveSuppressionsInSession: Long = 0,
    val lastRecordedEvent: InteractionEvent? = null,
    val activeField: TestBenchFieldType = TestBenchFieldType.NORMAL_TEXT,
    val detectedClassification: InputClassification = InputClassification.NORMAL_TEXT,
    val isSensitiveMode: Boolean = false,
    val fieldValues: Map<TestBenchFieldType, String> = TestBenchFieldType.entries.associateWith { "" },
    val submissionResult: TestBenchSubmissionResult? = null,
    val feedbackMessage: String? = null,
    val errorMessage: String? = null
) {
    val isSessionActive: Boolean
        get() = sessionState == SessionState.ACTIVE && activeSession != null

    fun getFieldValue(fieldType: TestBenchFieldType): String =
        fieldValues[fieldType] ?: ""
}
