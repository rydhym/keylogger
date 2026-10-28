package com.example.inputmonitor.di

import android.content.Context
import com.example.inputmonitor.core.DefaultDispatcherProvider
import com.example.inputmonitor.core.DispatcherProvider

import com.example.inputmonitor.data.database.AppDatabase
import com.example.inputmonitor.data.database.DatabaseProvider
import com.example.inputmonitor.data.preferences.PreferencesRepositoryImpl
import com.example.inputmonitor.data.repository.EventRepositoryImpl
import com.example.inputmonitor.data.repository.SessionRepositoryImpl
import com.example.inputmonitor.data.session.SessionGateImpl
import com.example.inputmonitor.data.session.SessionManagerImpl
import com.example.inputmonitor.domain.repository.EventRepository
import com.example.inputmonitor.domain.repository.PreferencesRepository
import com.example.inputmonitor.domain.repository.SessionRepository
import com.example.inputmonitor.domain.session.SessionGate
import com.example.inputmonitor.domain.session.SessionManager
import com.example.inputmonitor.domain.usecase.GetActiveSessionUseCase
import com.example.inputmonitor.domain.usecase.ObserveActiveSessionUseCase
import com.example.inputmonitor.domain.usecase.ObserveSessionStateUseCase
import com.example.inputmonitor.domain.usecase.PauseMonitoringSessionUseCase
import com.example.inputmonitor.domain.usecase.ResumeMonitoringSessionUseCase
import com.example.inputmonitor.domain.usecase.StartMonitoringSessionUseCase
import com.example.inputmonitor.domain.usecase.StopMonitoringSessionUseCase
import com.example.inputmonitor.domain.privacy.PrivacyFilter
import com.example.inputmonitor.domain.privacy.PrivacyFilterImpl
import com.example.inputmonitor.domain.privacy.SensitiveInputDetector
import com.example.inputmonitor.domain.privacy.SensitiveInputDetectorImpl
import com.example.inputmonitor.platform.ime.analysis.EditorInfoAnalyzer
import com.example.inputmonitor.platform.ime.analysis.EditorInfoAnalyzerImpl

import com.example.inputmonitor.domain.context.ContextResolver
import com.example.inputmonitor.domain.processor.EventProcessor
import com.example.inputmonitor.domain.processor.EventProcessorImpl
import com.example.inputmonitor.platform.ime.ImeEventPublisher
import com.example.inputmonitor.platform.ime.ImeEventPublisherImpl
import com.example.inputmonitor.platform.packages.ContextResolverImpl
import com.example.inputmonitor.platform.accessibility.AccessibilityEventFilter
import com.example.inputmonitor.platform.accessibility.AccessibilityEventFilterImpl
import com.example.inputmonitor.platform.accessibility.AccessibilityEventMapper
import com.example.inputmonitor.platform.accessibility.AccessibilityEventMapperImpl
import com.example.inputmonitor.platform.accessibility.AccessibilityEventPublisher
import com.example.inputmonitor.platform.accessibility.AccessibilityEventPublisherImpl

/**
 * Application-level dependency container implementing manual DI (DEC-004).
 *
 * Provides single-point initialization and access to core application singletons
 * without Hilt runtime overhead or annotation processing costs.
 */
interface AppContainer {
    val context: Context
    val dispatchers: DispatcherProvider
    val database: AppDatabase
    val sessionRepository: SessionRepository
    val eventRepository: EventRepository
    val preferencesRepository: PreferencesRepository
    val sessionManager: SessionManager
    val sessionGate: SessionGate
    val editorInfoAnalyzer: EditorInfoAnalyzer
    val sensitiveInputDetector: SensitiveInputDetector
    val privacyFilter: PrivacyFilter
    val contextResolver: ContextResolver
    val eventProcessor: EventProcessor
    val imeEventPublisher: ImeEventPublisher
    val accessibilityEventFilter: AccessibilityEventFilter
    val accessibilityEventMapper: AccessibilityEventMapper
    val accessibilityEventPublisher: AccessibilityEventPublisher
    val systemStatusChecker: com.example.inputmonitor.platform.system.SystemStatusChecker
    val permissionCoordinator: com.example.inputmonitor.platform.permissions.PermissionCoordinator
    val setupStateManager: com.example.inputmonitor.domain.setup.SetupStateManager
    val startMonitoringSessionUseCase: StartMonitoringSessionUseCase
    val pauseMonitoringSessionUseCase: PauseMonitoringSessionUseCase
    val resumeMonitoringSessionUseCase: ResumeMonitoringSessionUseCase
    val stopMonitoringSessionUseCase: StopMonitoringSessionUseCase
    val getActiveSessionUseCase: GetActiveSessionUseCase
    val observeActiveSessionUseCase: ObserveActiveSessionUseCase
    val observeSessionStateUseCase: ObserveSessionStateUseCase
    val exportValidator: com.example.inputmonitor.domain.export.ExportValidator
    val csvEventExporter: com.example.inputmonitor.data.export.CsvEventExporter
    val exportSessionUseCase: com.example.inputmonitor.domain.usecase.ExportSessionUseCase
    val deleteSessionUseCase: com.example.inputmonitor.domain.usecase.DeleteSessionUseCase
    val deleteAllSessionsUseCase: com.example.inputmonitor.domain.usecase.DeleteAllSessionsUseCase
    val deleteAllApplicationDataUseCase: com.example.inputmonitor.domain.usecase.DeleteAllApplicationDataUseCase
    val backupSettingsRepository: com.example.inputmonitor.domain.backup.repository.BackupSettingsRepository
    val backupTokenStorage: com.example.inputmonitor.data.backup.security.BackupTokenStorage
    val backupFileManager: com.example.inputmonitor.data.backup.file.BackupFileManager
    val backupRepository: com.example.inputmonitor.domain.backup.repository.BackupRepository
    val autoBackupScheduler: com.example.inputmonitor.platform.backup.AutoBackupScheduler
}


/**
 * Default implementation of [AppContainer] initialized in [com.example.inputmonitor.MonitoringApplication].
 */
class DefaultAppContainer(
    override val context: Context
) : AppContainer {

    override val dispatchers: DispatcherProvider by lazy {
        DefaultDispatcherProvider()
    }

    override val database: AppDatabase by lazy {
        DatabaseProvider.createEncryptedDatabase(context)
    }

    override val sessionRepository: SessionRepository by lazy {
        SessionRepositoryImpl(database.monitoringSessionDao(), dispatchers)
    }

    override val eventRepository: EventRepository by lazy {
        EventRepositoryImpl(database.interactionEventDao(), dispatchers)
    }

    override val preferencesRepository: PreferencesRepository by lazy {
        PreferencesRepositoryImpl(context, dispatchers)
    }

    override val sessionManager: SessionManager by lazy {
        SessionManagerImpl(sessionRepository, preferencesRepository, dispatchers)
    }

    override val sessionGate: SessionGate by lazy {
        SessionGateImpl(sessionManager)
    }

    override val editorInfoAnalyzer: EditorInfoAnalyzer by lazy {
        EditorInfoAnalyzerImpl()
    }

    override val sensitiveInputDetector: SensitiveInputDetector by lazy {
        SensitiveInputDetectorImpl()
    }

    override val privacyFilter: PrivacyFilter by lazy {
        PrivacyFilterImpl()
    }

    override val contextResolver: ContextResolver by lazy {
        ContextResolverImpl(context, dispatchers)
    }

    override val eventProcessor: EventProcessor by lazy {
        EventProcessorImpl(
            sessionGate = sessionGate,
            sessionManager = sessionManager,
            privacyFilter = privacyFilter,
            contextResolver = contextResolver,
            eventRepository = eventRepository,
            dispatchers = dispatchers
        )
    }

    override val imeEventPublisher: ImeEventPublisher by lazy {
        ImeEventPublisherImpl(eventProcessor, dispatchers)
    }

    override val accessibilityEventFilter: AccessibilityEventFilter by lazy {
        AccessibilityEventFilterImpl(context.packageName)
    }

    override val accessibilityEventMapper: AccessibilityEventMapper by lazy {
        AccessibilityEventMapperImpl()
    }

    override val accessibilityEventPublisher: AccessibilityEventPublisher by lazy {
        AccessibilityEventPublisherImpl(eventProcessor, dispatchers)
    }

    override val systemStatusChecker: com.example.inputmonitor.platform.system.SystemStatusChecker by lazy {
        com.example.inputmonitor.platform.system.AndroidSystemStatusChecker(context, database)
    }

    override val permissionCoordinator: com.example.inputmonitor.platform.permissions.PermissionCoordinator by lazy {
        com.example.inputmonitor.platform.permissions.PermissionCoordinatorImpl(context)
    }

    override val setupStateManager: com.example.inputmonitor.domain.setup.SetupStateManager by lazy {
        com.example.inputmonitor.platform.system.SetupStateManagerImpl(
            systemStatusChecker = systemStatusChecker,
            permissionCoordinator = permissionCoordinator,
            sessionManager = sessionManager,
            preferencesRepository = preferencesRepository,
            dispatchers = dispatchers
        )
    }

    override val startMonitoringSessionUseCase: StartMonitoringSessionUseCase by lazy {
        StartMonitoringSessionUseCase(sessionManager)
    }

    override val pauseMonitoringSessionUseCase: PauseMonitoringSessionUseCase by lazy {
        PauseMonitoringSessionUseCase(sessionManager)
    }

    override val resumeMonitoringSessionUseCase: ResumeMonitoringSessionUseCase by lazy {
        ResumeMonitoringSessionUseCase(sessionManager)
    }

    override val stopMonitoringSessionUseCase: StopMonitoringSessionUseCase by lazy {
        StopMonitoringSessionUseCase(sessionManager)
    }

    override val getActiveSessionUseCase: GetActiveSessionUseCase by lazy {
        GetActiveSessionUseCase(sessionManager)
    }

    override val observeActiveSessionUseCase: ObserveActiveSessionUseCase by lazy {
        ObserveActiveSessionUseCase(sessionManager)
    }

    override val observeSessionStateUseCase: ObserveSessionStateUseCase by lazy {
        ObserveSessionStateUseCase(sessionManager)
    }

    override val exportValidator: com.example.inputmonitor.domain.export.ExportValidator by lazy {
        com.example.inputmonitor.domain.export.ExportValidatorImpl()
    }

    override val csvEventExporter: com.example.inputmonitor.data.export.CsvEventExporter by lazy {
        com.example.inputmonitor.data.export.CsvEventExporterImpl()
    }

    override val exportSessionUseCase: com.example.inputmonitor.domain.usecase.ExportSessionUseCase by lazy {
        com.example.inputmonitor.domain.usecase.ExportSessionUseCase(
            eventRepository = eventRepository,
            exportValidator = exportValidator,
            csvEventExporter = csvEventExporter,
            dispatchers = dispatchers
        )
    }

    override val deleteSessionUseCase: com.example.inputmonitor.domain.usecase.DeleteSessionUseCase by lazy {
        com.example.inputmonitor.domain.usecase.DeleteSessionUseCase(
            sessionRepository = sessionRepository,
            eventRepository = eventRepository,
            sessionManager = sessionManager,
            dispatchers = dispatchers
        )
    }

    override val deleteAllSessionsUseCase: com.example.inputmonitor.domain.usecase.DeleteAllSessionsUseCase by lazy {
        com.example.inputmonitor.domain.usecase.DeleteAllSessionsUseCase(
            sessionRepository = sessionRepository,
            eventRepository = eventRepository,
            sessionManager = sessionManager,
            dispatchers = dispatchers
        )
    }

    override val deleteAllApplicationDataUseCase: com.example.inputmonitor.domain.usecase.DeleteAllApplicationDataUseCase by lazy {
        com.example.inputmonitor.domain.usecase.DeleteAllApplicationDataUseCase(
            sessionRepository = sessionRepository,
            eventRepository = eventRepository,
            preferencesRepository = preferencesRepository,
            sessionManager = sessionManager,
            dispatchers = dispatchers
        )
    }

    override val backupSettingsRepository: com.example.inputmonitor.domain.backup.repository.BackupSettingsRepository by lazy {
        com.example.inputmonitor.data.backup.repository.BackupSettingsRepositoryImpl(context, dispatchers)
    }

    override val backupTokenStorage: com.example.inputmonitor.data.backup.security.BackupTokenStorage by lazy {
        com.example.inputmonitor.data.backup.security.KeystoreBackupTokenStorage(context, dispatchers)
    }

    override val backupFileManager: com.example.inputmonitor.data.backup.file.BackupFileManager by lazy {
        com.example.inputmonitor.data.backup.file.BackupFileManager(context, exportSessionUseCase, dispatchers)
    }

    override val backupRepository: com.example.inputmonitor.domain.backup.repository.BackupRepository by lazy {
        com.example.inputmonitor.data.backup.provider.HttpBackupProvider(
            backupSettingsRepository = backupSettingsRepository,
            tokenStorage = backupTokenStorage,
            dispatchers = dispatchers
        )
    }

    override val autoBackupScheduler: com.example.inputmonitor.platform.backup.AutoBackupScheduler by lazy {
        com.example.inputmonitor.platform.backup.WorkManagerAutoBackupScheduler(context)
    }
}

