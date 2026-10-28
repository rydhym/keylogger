package com.example.inputmonitor.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.inputmonitor.ui.dashboard.DashboardScreen
import com.example.inputmonitor.ui.events.EventsScreen
import com.example.inputmonitor.ui.onboarding.OnboardingScreen
import com.example.inputmonitor.ui.settings.SettingsScreen
import com.example.inputmonitor.ui.setup.SetupScreen
import com.example.inputmonitor.ui.statistics.StatisticsScreen

/**
 * Top-level application navigation graph (Architecture.md §13).
 */
@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val container = (context.applicationContext as com.example.inputmonitor.MonitoringApplication).container

    val setupViewModel: com.example.inputmonitor.ui.setup.SetupViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = com.example.inputmonitor.ui.setup.SetupViewModel.provideFactory(
            setupStateManager = container.setupStateManager,
            permissionCoordinator = container.permissionCoordinator,
            preferencesRepository = container.preferencesRepository
        )
    )

    val setupState by setupViewModel.setupState.collectAsState()
    val isPermissionDenied by setupViewModel.isPermissionDenied.collectAsState()
    val startDestination = if (setupState.isOnboardingCompleted) {
        Screen.Dashboard.route
    } else {
        Screen.Onboarding.route
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onNavigateToSetup = {
                    navController.navigate(Screen.Setup.route)
                }
            )
        }

        composable(Screen.Setup.route) {
            SetupScreen(
                setupState = setupState,
                onOpenAccessibilitySettings = { setupViewModel.openAccessibilitySettings(context) },
                onOpenKeyboardSettings = { setupViewModel.openKeyboardSettings(context) },
                onShowKeyboardPicker = { setupViewModel.showKeyboardPicker(context) },
                onCompleteSetup = {
                    setupViewModel.completeSetup {
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.Onboarding.route) { inclusive = true }
                        }
                    }
                },
                requiredPermissions = container.permissionCoordinator.getRequiredPermissions(),
                isPermissionDenied = isPermissionDenied,
                onPermissionsResult = { setupViewModel.onPermissionsResult(it) },
                onDismissPermissionDeniedAlert = { setupViewModel.dismissPermissionDeniedAlert() }
            )
        }

        composable(Screen.Dashboard.route) {
            val dashboardViewModel: com.example.inputmonitor.ui.dashboard.DashboardViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = com.example.inputmonitor.ui.dashboard.DashboardViewModel.provideFactory(
                    startUseCase = container.startMonitoringSessionUseCase,
                    pauseUseCase = container.pauseMonitoringSessionUseCase,
                    resumeUseCase = container.resumeMonitoringSessionUseCase,
                    stopUseCase = container.stopMonitoringSessionUseCase,
                    observeActiveUseCase = container.observeActiveSessionUseCase,
                    observeStateUseCase = container.observeSessionStateUseCase,
                    setupStateManager = container.setupStateManager,
                    eventRepository = container.eventRepository
                )
            )

            val uiState by dashboardViewModel.uiState.collectAsState()

            DashboardScreen(
                uiState = uiState,
                onStartSession = { name, notes -> dashboardViewModel.startSession(name, notes) },
                onPauseSession = { dashboardViewModel.pauseSession() },
                onResumeSession = { dashboardViewModel.resumeSession() },
                onStopSession = { dashboardViewModel.stopSession() },
                onRefreshSetup = { dashboardViewModel.refreshSetup() },
                onNavigateToEvents = {
                    navController.navigate(Screen.Events.route)
                },
                onNavigateToStatistics = {
                    navController.navigate(Screen.Statistics.route)
                },
                onNavigateToExport = {
                    navController.navigate(Screen.Export.route)
                },
                onNavigateToTestBench = {
                    navController.navigate(Screen.TestBench.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToSetup = {
                    navController.navigate(Screen.Setup.route)
                }
            )
        }

        composable(Screen.Events.route) {
            val timelineViewModel: com.example.inputmonitor.ui.events.TimelineViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = com.example.inputmonitor.ui.events.TimelineViewModel.provideFactory(
                    eventRepository = container.eventRepository,
                    sessionRepository = container.sessionRepository,
                    observeActiveSessionUseCase = container.observeActiveSessionUseCase
                )
            )

            val timelineUiState by timelineViewModel.uiState.collectAsState()

            EventsScreen(
                uiState = timelineUiState,
                onNavigateBack = { navController.popBackStack() },
                onSearchQueryChange = { timelineViewModel.onSearchQueryInputChanged(it) },
                onEventTypeFilterChange = { timelineViewModel.setEventTypeFilter(it) },
                onSourceFilterChange = { timelineViewModel.setSourceFilter(it) },
                onPackageFilterChange = { timelineViewModel.setPackageFilter(it) },
                onSessionFilterChange = { timelineViewModel.setSessionFilter(it) },
                onClassificationFilterChange = { timelineViewModel.setClassificationFilter(it) },
                onClearFilters = { timelineViewModel.clearFilters() },
                onNextPage = { timelineViewModel.nextPage() },
                onPreviousPage = { timelineViewModel.previousPage() },
                onSelectEvent = { timelineViewModel.selectEvent(it) },
                onSetFilterDialogVisible = { timelineViewModel.setFilterDialogVisible(it) },
                onApplySuggestion = { timelineViewModel.applySuggestion(it) }
            )
        }

        composable(Screen.Statistics.route) {
            val statisticsViewModel: com.example.inputmonitor.ui.statistics.StatisticsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = com.example.inputmonitor.ui.statistics.StatisticsViewModel.provideFactory(
                    eventRepository = container.eventRepository,
                    sessionRepository = container.sessionRepository
                )
            )

            val statisticsUiState by statisticsViewModel.uiState.collectAsState()

            StatisticsScreen(
                uiState = statisticsUiState,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onSelectSession = { sessionId ->
                    statisticsViewModel.selectSession(sessionId)
                },
                onSetDropdownExpanded = { expanded ->
                    statisticsViewModel.setDropdownExpanded(expanded)
                }
            )
        }

        composable(Screen.Export.route) {
            val exportViewModel: com.example.inputmonitor.ui.export.ExportViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = com.example.inputmonitor.ui.export.ExportViewModel.provideFactory(
                    exportSessionUseCase = container.exportSessionUseCase,
                    sessionRepository = container.sessionRepository,
                    eventRepository = container.eventRepository
                )
            )

            val exportUiState by exportViewModel.uiState.collectAsState()

            com.example.inputmonitor.ui.export.ExportScreen(
                uiState = exportUiState,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onSelectSession = { sessionId ->
                    exportViewModel.selectSession(sessionId)
                },
                onSetDropdownExpanded = { expanded ->
                    exportViewModel.setSessionDropdownExpanded(expanded)
                },
                onExportRequested = { uri ->
                    try {
                        val outputStream = context.contentResolver.openOutputStream(uri)
                        if (outputStream != null) {
                            exportViewModel.executeExport(outputStream, uri.toString())
                        }
                    } catch (t: Throwable) {
                        // Handled in ViewModel
                    }
                },
                onDismissResult = {
                    exportViewModel.dismissResult()
                },
                onShareViaShareSheet = {
                    try {
                        val exportDir = java.io.File(context.cacheDir, "exports").apply { mkdirs() }
                        val safeName = exportUiState.selectedSessionName.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
                        val shareFile = java.io.File(exportDir, "inputmonitor_${safeName}_${System.currentTimeMillis()}.csv")
                        val fos = java.io.FileOutputStream(shareFile)
                        val bos = java.io.BufferedOutputStream(fos)
                        exportViewModel.executeExport(bos) { success ->
                            if (success && shareFile.exists() && shareFile.length() > 0) {
                                val contentUri = androidx.core.content.FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    shareFile
                                )
                                val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/csv"
                                    putExtra(android.content.Intent.EXTRA_SUBJECT, "InputMonitor CSV Export - ${exportUiState.selectedSessionName}")
                                    putExtra(android.content.Intent.EXTRA_TEXT, "Exported interaction data report.")
                                    putExtra(android.content.Intent.EXTRA_STREAM, contentUri)
                                    clipData = android.content.ClipData.newUri(context.contentResolver, "CSV Export", contentUri)
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                val chooserIntent = android.content.Intent.createChooser(sendIntent, "Share CSV").apply {
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(chooserIntent)
                            }
                        }
                    } catch (_: Throwable) {
                    }
                }
            )
        }

        composable(Screen.Settings.route) {
            val settingsViewModel: com.example.inputmonitor.ui.settings.SettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = com.example.inputmonitor.ui.settings.SettingsViewModel.provideFactory(
                    sessionRepository = container.sessionRepository,
                    eventRepository = container.eventRepository,
                    preferencesRepository = container.preferencesRepository,
                    deleteSessionUseCase = container.deleteSessionUseCase,
                    deleteAllSessionsUseCase = container.deleteAllSessionsUseCase,
                    deleteAllApplicationDataUseCase = container.deleteAllApplicationDataUseCase,
                    backupSettingsRepository = container.backupSettingsRepository,
                    backupTokenStorage = container.backupTokenStorage,
                    backupRepository = container.backupRepository,
                    autoBackupScheduler = container.autoBackupScheduler,
                    dispatchers = container.dispatchers
                )
            )

            val settingsUiState by settingsViewModel.uiState.collectAsState()

            com.example.inputmonitor.ui.settings.SettingsScreen(
                uiState = settingsUiState,
                onNavigateBack = { navController.popBackStack() },
                onKeyboardLoggingToggle = { settingsViewModel.setKeyboardLoggingEnabled(it) },
                onAccessibilityLoggingToggle = { settingsViewModel.setAccessibilityLoggingEnabled(it) },
                onRequestDeleteSession = { settingsViewModel.requestDeleteSession(it) },
                onConfirmDeleteSession = { settingsViewModel.confirmDeleteSession() },
                onDismissDeleteSessionDialog = { settingsViewModel.dismissDeleteSessionDialog() },
                onRequestDeleteAllSessions = { settingsViewModel.requestDeleteAllSessions() },
                onConfirmDeleteAllSessions = { settingsViewModel.confirmDeleteAllSessions() },
                onDismissDeleteAllSessionsDialog = { settingsViewModel.dismissDeleteAllSessionsDialog() },
                onRequestDeleteAllData = { settingsViewModel.requestDeleteAllData() },
                onConfirmDeleteAllData = { settingsViewModel.confirmDeleteAllData() },
                onDismissDeleteAllDataDialog = { settingsViewModel.dismissDeleteAllDataDialog() },
                onDismissUserMessage = { settingsViewModel.dismissUserMessage() },
                onDismissError = { settingsViewModel.dismissError() },
                onAutomaticBackupToggle = { settingsViewModel.setAutomaticBackupEnabled(it) },
                onWifiOnlyToggle = { settingsViewModel.setWifiOnly(it) },
                onTriggerBackupNow = { settingsViewModel.triggerBackupNow() },
                onRequestConnectBackup = { settingsViewModel.showConnectBackupDialog(true) },
                onDismissConnectBackupDialog = { settingsViewModel.showConnectBackupDialog(false) },
                onConfirmConnectBackup = { endpoint, token -> settingsViewModel.connectBackup(endpoint, token) },
                onRequestDisconnectBackup = { settingsViewModel.showDisconnectBackupDialog(true) },
                onDismissDisconnectBackupDialog = { settingsViewModel.showDisconnectBackupDialog(false) },
                onConfirmDisconnectBackup = { settingsViewModel.disconnectBackup() }
            )
        }

        composable(Screen.TestBench.route) {
            val testBenchViewModel: com.example.inputmonitor.ui.testbench.TestBenchViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                factory = com.example.inputmonitor.ui.testbench.TestBenchViewModel.provideFactory(
                    observeActiveSessionUseCase = container.observeActiveSessionUseCase,
                    observeSessionStateUseCase = container.observeSessionStateUseCase,
                    startMonitoringSessionUseCase = container.startMonitoringSessionUseCase,
                    stopMonitoringSessionUseCase = container.stopMonitoringSessionUseCase,
                    eventRepository = container.eventRepository,
                    dispatchers = container.dispatchers
                )
            )

            val testBenchUiState by testBenchViewModel.uiState.collectAsState()

            com.example.inputmonitor.ui.testbench.TestBenchScreen(
                uiState = testBenchUiState,
                onNavigateBack = { navController.popBackStack() },
                onFieldFocused = { testBenchViewModel.onFieldFocused(it) },
                onFieldValueChanged = { field, value -> testBenchViewModel.onFieldValueChanged(field, value) },
                onClearAllFields = { testBenchViewModel.clearAllFields() },
                onPopulateSyntheticData = { testBenchViewModel.populateSyntheticTestData() },
                onStartQuickSession = { testBenchViewModel.startQuickTestSession() },
                onStopQuickSession = { testBenchViewModel.stopQuickTestSession() },
                onSubmitTestInputs = { testBenchViewModel.submitTestInputs() },
                onDismissFeedback = { testBenchViewModel.dismissFeedback() },
                onDismissSubmissionResult = { testBenchViewModel.dismissSubmissionResult() }
            )
        }
    }
}

