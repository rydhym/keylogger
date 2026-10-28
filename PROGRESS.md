# PROJECT PROGRESS & AGENT HANDOFF

**Document:** `PROGRESS.md`  
**Status:** Living Document  
**Purpose:** Current development state and coding-agent handoff

---

# 1. Mandatory Agent Rule

This file must be read whenever a coding agent begins or resumes development.

Before stopping a development session, the agent must update this file.

This document answers:

> Where exactly is the project right now?

It must describe reality, not intended future state.

---

# 2. Project Status

```text
Overall Status:
IN PROGRESS

Current Phase:
Phase 20 complete; ready to begin Phase 21 (Release Optimization & Validation)

Current Objective:
Production-quality, user-consented Automatic Daily Cloud Backup subsystem implemented and verified:
- Reusable CSV export via BackupFileManager operating strictly under cacheDir/backups/ with atomic .tmp -> .csv writing and automated stale-file purging.
- Provider-independent BackupRepository abstraction with production HttpBackupProvider enforcing TLS/HTTPS (cleartext prohibited) and memory-safe streaming uploads.
- BackupSettingsRepository backed by Jetpack DataStore preferences (disabled by default) and KeystoreBackupTokenStorage using hardware-backed AES-256-GCM.
- WorkManager background scheduler (WorkManagerAutoBackupScheduler) enforcing unique 24-hour periodic work (ExistingPeriodicWorkPolicy.UPDATE) and single-instance manual backups (ExistingWorkPolicy.KEEP) with unmetered network constraints for Wi-Fi only mode.
- AutoBackupWorker coroutine worker executing the full pipeline: status checks, attempt logging, CSV generation, HTTPS streaming upload, failure/auth classification, and temporary file deletion.
- Settings UI updated with Automatic Daily Backup card, status badges, ON/OFF and Wi-Fi toggles, Back Up Now trigger, and Test & Connect dialog.
- Manual export preserved independently with both local SAF saving and Android Share Sheet (ACTION_SEND + FileProvider).
- 62 test suites / 326 unit tests passing (100% pass rate). Debug APK assembled cleanly.
```

Update these values as development proceeds.

---

# 3. Core Documents

Pre-development documentation:

```text
[✓] SRS.md
[✓] Architecture.md
[✓] DEVELOPMENT_GUIDELINES.md
[✓] IMPLEMENTATION_PLAN.md
[✓] TESTING.md
[✓] SECURITY.md
[✓] PROGRESS.md

`IMPLEMENTATION_PLAN.md` (Phases 0–21) is the authoritative implementation
roadmap. The package structure in `Architecture.md` (§10) is authoritative for
the source tree.

Phase-0 living documents (created and active):

[✓] decision.md
[✓] tech.md
[✓] flow.md
```

`decision.md`, `tech.md`, and `flow.md` are initialized and authoritative.

---

# 4. Overall Implementation Progress

```text
[✓] Phase 0  — Repository Preparation

[✓] Phase 1  — Android Foundation

[✓] Phase 2  — Data Foundation

[✓] Phase 3  — Monitoring Sessions

[✓] Phase 4  — Basic Custom Keyboard

[✓] Phase 5  — Complete Keyboard

[✓] Phase 6  — Editor Analysis & Privacy

[✓] Phase 7  — IME Event Pipeline

[✓] Phase 8  — AccessibilityService

[✓] Phase 9  — Unified Event Processing

[✓] Phase 10 — First-Run Setup

[✓] Phase 11 — Dashboard

[✓] Phase 12 — Event Timeline

[✓] Phase 13 — Search

[✓] Phase 14 — Statistics

[✓] Phase 15 — Export

[✓] Phase 16 — Data Management

[✓] Phase 17 — Controlled Application Use Test Screen

[✓] Phase 18 — Performance Optimization

[✓] Phase 19 — Large Database Testing

[✓] Phase 20 — Android Version Testing

[ ] Phase 21 — Release Optimization & Validation
```

Use:

```text
[ ] Not started
[~] In progress
[✓] Complete
[!] Blocked/problem
```

---

# 5. Current Work

## Currently Implementing

```text
Ready to begin Phase 21 — Release Optimization & Validation.
```

## Current Goal

```text
Implement Phase 21: Final release verification, R8 optimization review, resource shrinking audit, dead code check, release APK sanity validation, and end-to-end acceptance verification per IMPLEMENTATION_PLAN.md §63, §64, and TESTING.md §28, §30.
```

---

# 6. Completed Work

Currently:

```text
✓ Software requirements defined (SRS.md)

✓ Architecture defined (Architecture.md)

✓ Development/optimization rules defined (DEVELOPMENT_GUIDELINES.md)

✓ Implementation sequence defined (IMPLEMENTATION_PLAN.md)

✓ Testing strategy defined (TESTING.md)

✓ Security/privacy boundaries defined (SECURITY.md)

✓ Agent handoff process defined (PROGRESS.md)

✓ Phase 0 completed:
  - Initialized decision.md (DEC-001 through DEC-012)
  - Initialized tech.md (fully pinned stack and metrics)
  - Initialized flow.md (all system & user flows)
  - Gradle Kotlin DSL multi-project structure established
  - Gradle 9.3.0 wrapper configured for JDK 25 compatibility
  - Android SDK configured (compileSdk 35, targetSdk 35, minSdk 31)
  - AndroidManifest.xml configured with IME, Accessibility, no INTERNET, allowBackup=false
  - Resource files created (themes, colors, adaptive launcher icon, IME and accessibility configs)
  - Platform stubs created (CustomKeyboardService, AppAccessibilityService)
  - App stubs created (MonitoringApplication, MainActivity, InputMonitorTheme)
  - Unit test harness established and passing (SanityTest)
  - Clean debug build verified (app-debug.apk: 48,608,524 bytes)
  - Clean release build with R8 verified (app-release.apk: 22,945,972 bytes)

✓ Phase 1 completed:
  - Implemented DispatcherProvider and DefaultDispatcherProvider (DEC-013)
  - Implemented AppContainer and DefaultAppContainer (DEC-004 manual DI)
  - Wired AppContainer inside MonitoringApplication
  - Implemented Screen sealed hierarchy for type-safe routing (DEC-014)
  - Implemented AppNavigation NavHost connecting all core routes
  - Implemented foundation screens: OnboardingScreen, SetupScreen, DashboardScreen, EventsScreen, StatisticsScreen, SettingsScreen
  - Updated MainActivity to host AppNavigation with edge-to-edge Material 3 theme
  - Added unit test suites (SanityTest, DispatcherProviderTest, NavigationRoutesTest) — all passing
  - Verified clean debug build (app-debug.apk: 48,720,225 bytes)
  - Verified clean release build with R8 shrinking (app-release.apk: 23,318,576 bytes)

✓ Phase 2 completed:
  - Created domain models: EventSource, EventType, InputClassification (with isSensitive helper), SessionState, MonitoringSession, InteractionEvent
  - Created Room entities: MonitoringSessionEntity, InteractionEventEntity with cascade foreign key and 4 indices
  - Implemented bidirectional mappers with sensitive data defense-in-depth: SessionMapper, EventMapper
  - Implemented Room DAOs: MonitoringSessionDao, InteractionEventDao (with queries, paging, filters, Flow observation)
  - Implemented DatabaseKeyManager using Android Keystore AES-256-GCM to securely protect database passphrase (DEC-005)
  - Implemented AppDatabase Room database and DatabaseProvider with SQLCipher SupportOpenHelperFactory (DEC-016)
  - Implemented SessionRepository and SessionRepositoryImpl with IO offloading
  - Implemented EventRepository and EventRepositoryImpl with defense-in-depth safety validation (DEC-015)
  - Implemented PreferencesRepository and PreferencesRepositoryImpl via Jetpack Preferences DataStore (DEC-009)
  - Wired all database and repository singletons into AppContainer and DefaultAppContainer
  - Added 5 unit test suites for data foundation: SessionMapperTest, EventMapperTest, RepositorySafetyValidationTest, SessionRepositoryTest, EventRepositoryTest
  - Verified clean debug build (app-debug.apk: 48,887,522 bytes)
  - Verified clean release build with R8 shrinking (app-release.apk: 23,646,256 bytes)

✓ Phase 3 completed:
  - Created RawInteractionEvent domain model per Architecture.md §36
  - Implemented SessionManager interface and SessionManagerImpl thread-safe state machine (INACTIVE <-> ACTIVE <-> PAUSED -> STOPPED) per Architecture.md §43 (DEC-017)
  - Implemented SessionGate interface and SessionGateImpl to block and drop events when not ACTIVE per Architecture.md §44
  - Created 7 domain use cases: StartMonitoringSessionUseCase, PauseMonitoringSessionUseCase, ResumeMonitoringSessionUseCase, StopMonitoringSessionUseCase, GetActiveSessionUseCase, ObserveActiveSessionUseCase, ObserveSessionStateUseCase
  - Integrated process restart active session restoration via PreferencesRepository and SessionRepository
  - Wired SessionManager, SessionGate, and all 7 use cases into AppContainer and DefaultAppContainer
  - Implemented DashboardViewModel and updated DashboardScreen with session state badge (ACTIVE, PAUSED, STOPPED, INACTIVE), session details, and interactive Start/Pause/Resume/Stop controls
  - Added 3 new unit test suites: SessionManagerTest (9 tests), SessionGateTest (4 tests), SessionUseCasesTest (5 tests)
  - Verified all 11 test suites / 37 unit tests passing cleanly
  - Verified clean debug build (app-debug.apk: 49,048,518 bytes)
  - Verified clean release build with R8 shrinking (app-release.apk: 24,067,628 bytes)

✓ Phase 4 completed:
  - Created KeyAction, KeyboardLayoutType, and ShiftState domain models
  - Implemented KeyboardLayout defining 4-row QWERTY layout with weight spans and special keys
  - Implemented KeyProcessor handling direct InputConnection operations (commitText, deleteSurroundingText, performEditorAction, fallback key events) decoupled from logging (DEC-018)
  - Implemented KeyboardController 3-state shift machine (OFF -> ON_ONCE -> CAPS_LOCK) and action key label resolution
  - Built high-performance native KeyboardView with state-list drawables, sub-5ms touch response, zero GC allocations per key press, and audio/haptic feedback
  - Implemented CustomKeyboardService (InputMethodService) binding view and controller, handling shift, layout, and IME switching via InputMethodManager
  - Added 3 new unit test suites: KeyProcessorTest (8 tests), KeyboardControllerTest (5 tests), KeyboardLayoutTest (2 tests)
  - Verified all 14 test suites / 52 unit tests passing cleanly
  - Verified clean debug build (app-debug.apk: 49,127,138 bytes)
  - Verified clean release build with R8 shrinking (app-release.apk: 24,067,628 bytes)

✓ Phase 5 completed:
  - Extended KeyboardLayoutType with EMAIL, URI, SYMBOLS, SYMBOLS_MORE, NUMERIC, and PHONE (DEC-019)
  - Implemented complete keyboard layouts in KeyboardLayout:
    * Symbols layout with 1-0 numbers, punctuation symbols (@, #, $, %, &, -, +, (, ), /), and =<\ switch key
    * Symbols More layout with math, brackets, and currency symbols (~, `, |, ^, \, <, >, {, }, €, £, etc.)
    * Numeric 3x4 dialpad layout with 1-9, 0, ., backspace, and enter
    * Phone dialpad layout with 1-9, 0, *, #, +, backspace, and enter
    * Email and URI specialized bottom rows with dedicated '@', '/', and '.com' keys
  - Enhanced KeyboardController state machine:
    * Double-tap Shift gesture (< 350ms) locks CAPS_LOCK (⇪)
    * Single-tap Shift sets ON_ONCE (⬆) which auto-reverts to OFF (⇧) after committing a character
    * Context-specific EditorInfo inputType adaptation: automatically selects PHONE, NUMERIC, EMAIL, URI, or QWERTY
    * Action key label resolution: IME_ACTION_GO, SEARCH, SEND, NEXT, DONE, and multiline newline
  - Added comprehensive unit tests in KeyboardControllerTest (11 tests) and KeyboardLayoutTest (10 tests)
  - Verified all 14 test suites / 66 unit tests passing cleanly
  - Verified clean debug build (app-debug.apk: 49,137,994 bytes)
  - Verified clean release build with R8 shrinking (app-release.apk: 24,067,628 bytes)

✓ Phase 6 completed:
  - Created FieldCategory enum defining input field types and sensitivity (DEC-020)
  - Created EditorContext domain model encapsulating active editor metadata (category, sensitivity, multiline, imeAction, hints)
  - Implemented SensitiveInputDetector and SensitiveInputDetectorImpl classifying fields into InputClassification (Password, PIN, OTP, Credit Card, Normal, Search, Number)
  - Implemented SanitizedInteractionEvent enforcing compile/runtime constructor invariant (eventData == null if isSensitive)
  - Implemented PrivacyFilter and PrivacyFilterImpl providing defense-in-depth sanitization: candidateData nullification, SENSITIVE_INPUT_DETECTED event type, metadata scrubbing, and Luhn credit card PAN detection
  - Implemented EditorInfoAnalyzer and EditorInfoAnalyzerImpl inspecting Android EditorInfo bitmasks, variations, action labels, and hint attributes
  - Integrated EditorInfoAnalyzer into KeyboardController for context-sensitive adaptation
  - Wired EditorInfoAnalyzer, SensitiveInputDetector, and PrivacyFilter into AppContainer and DefaultAppContainer
  - Added 4 new unit test suites: EditorInfoAnalyzerTest (16 tests), SensitiveInputDetectorTest (11 tests), PrivacyFilterTest (7 tests), PrivacyDefenseInDepthTest (3 tests)
  - Verified all 18 test suites / 104 unit tests passing cleanly
  - Verified clean debug build (app-debug.apk: 49,131,050 bytes)
  - Verified clean release build with R8 shrinking (app-release.apk: 24,084,012 bytes)

✓ Phase 7 completed:
  - Implemented ContextResolver and ContextResolverImpl providing cached human-readable application label resolution with ConcurrentHashMap and IO dispatcher offloading (Architecture.md §41, §42, DEC-021)
  - Implemented EventProcessor and EventProcessorImpl coordinating SessionGate checking, active session validation, PrivacyFilter sanitization, ContextResolver label mapping, and defensive EventRepository persistence
  - Implemented ImeEventPublisher and ImeEventPublisherImpl asynchronously publishing RawInteractionEvent candidates on background SupervisorJob CoroutineScope without blocking main UI typing
  - Integrated asynchronous event emission into CustomKeyboardService.handleKeyAction with strict Keyboard Independence (input delivery to InputConnection always executes first; emission wrapped in fail-safe try-catch)
  - Wired contextResolver, eventProcessor, and imeEventPublisher singletons into AppContainer and DefaultAppContainer
  - Added 4 comprehensive unit test suites: ContextResolverTest (5 tests), EventProcessorTest (5 tests), ImeEventPublisherTest (3 tests), and ImeFailureToleranceTest (3 tests)
  - Successfully verified Mandatory Failure Tolerance Test (§44): simulated catastrophic logging/pipeline crash while typing proves InputConnection receives all characters, spaces, backspaces, and editor actions with 100% reliability
  - Verified all 22 test suites / 120 unit tests passing cleanly (100% pass rate in 1.44s)
  - Verified clean debug build (app-debug.apk: 49,141,834 bytes)
  - Verified clean release build with R8 shrinking (app-release.apk: 24,084,012 bytes)

✓ Phase 8 completed:
  - Implemented complete AppAccessibilityService bound via BIND_ACCESSIBILITY_SERVICE and configured via @xml/accessibility_service_config (Architecture.md §18, SRS.md §31, DEC-022)
  - Implemented AccessibilityEventFilter and AccessibilityEventFilterImpl supporting configurable event categories (clicks, focus, scrolls, window changes, selections), self-monitoring suppression to avoid feedback loops, and scroll storm rate limiting (minScrollIntervalMs = 100ms)
  - Implemented AccessibilityEventMapper and AccessibilityEventMapperImpl enforcing strict Content Minimization: avoids extracting arbitrary message bodies or user text, and detects password widgets via isPassword/className classifying as SENSITIVE_PASSWORD with null candidateData
  - Implemented AccessibilityEventInfo decoupled interface and SimpleAccessibilityEventInfo adapter enabling fast, deterministic JVM unit testing without Android OS accessibility IPC dependencies
  - Implemented AccessibilityEventPublisher and AccessibilityEventPublisherImpl dispatching events asynchronously on background SupervisorJob CoroutineScope so onAccessibilityEvent returns in sub-millisecond time
  - Wired accessibilityEventFilter, accessibilityEventMapper, and accessibilityEventPublisher into AppContainer and DefaultAppContainer
  - Added 4 new comprehensive test suites: AccessibilityEventFilterTest (6 tests), AccessibilityEventMapperTest (7 tests), AccessibilityEventPublisherTest (2 tests), and AppAccessibilityServiceTest (5 tests)
  - Verified all 26 test suites / 140 unit tests passing cleanly (100% pass rate in 1.63s)
  - Verified clean debug build (app-debug.apk: 49,141,834 bytes)
  - Verified clean release build with R8 shrinking (app-release.apk: 24,084,012 bytes)

✓ Phase 9 completed:
  - Verified end-to-end event pipeline integration connecting both IME and Accessibility event sources into central EventProcessor and Room database (Architecture.md §37, §38, DEC-023)
  - Verified concurrent interaction interleaving: user interaction on UI (Accessibility VIEW_FOCUSED / VIEW_CLICKED) followed immediately by typing (IME TEXT_COMMITTED) correctly persists in chronological order with unified session ID and human-readable application context
  - Verified unified session state gating across both pipelines: transitioning SessionState (ACTIVE <-> PAUSED / STOPPED) uniformly and simultaneously gates both IME and Accessibility sources
  - Verified unified multi-pipeline privacy invariants: sensitive credentials detected via accessibility password widgets or IME password fields are strictly sanitized to candidateData = null, eventType = SENSITIVE_INPUT_DETECTED, and zero sensitive persistence in encrypted storage
  - Verified self-monitoring loop suppression: events originating from own app package are safely filtered before reaching EventProcessor
  - Verified scroll storm rate limiting: rapid bursts of accessibility scroll events are throttled without dropping concurrent keyboard typing
  - Verified repository failure tolerance: database errors fail silently in background without crashing either the IME or Accessibility services
  - Cleaned up redundant imports and hardened metadata JSON escaping in EventProcessorImpl
  - Added UnifiedEventProcessingIntegrationTest (6 comprehensive integration tests)
  - Verified all 27 test suites / 146 unit tests passing cleanly (100% pass rate in 1.62s)
  - Verified clean debug build (app-debug.apk: 49,141,834 bytes)
  - Verified clean release build with R8 shrinking (app-release.apk: 24,084,012 bytes)

✓ Phase 10 completed:
  - Created SetupState domain model tracking runtime permissions, accessibility, IME installed/enabled/selected, database readiness, active monitoring sessions, and onboarding completion with computed `isComplete` and `isOperational` properties (Architecture.md §14, SRS.md §16–§18, DEC-024)
  - Created SystemStatusChecker interface and AndroidSystemStatusChecker implementation decoupling Android platform settings and services (AccessibilityManager, Settings.Secure, InputMethodManager) for deterministic JVM testing without mocking final framework APIs
  - Created PermissionCoordinator and PermissionCoordinatorImpl providing permission check/request state machine that represents denial as application state rather than throwing exceptions or trapping the user (SRS.md §12, AC-03, AC-35)
  - Created SetupStateManager interface and SetupStateManagerImpl observing SessionManager and PreferencesRepository, refreshing system states asynchronously on dispatchers.io, and exposing reactive StateFlow<SetupState>
  - Integrated MainActivity onResume lifecycle hook to trigger setupStateManager.refresh(), updating status immediately when user returns from Android Settings (Architecture.md §15)
  - Implemented SetupViewModel exposing setupState, handling settings intent launches (ACTION_ACCESSIBILITY_SETTINGS, ACTION_INPUT_METHOD_SETTINGS), IME picker invocation, and onboarding completion
  - Built OnboardingScreen (SRS.md §11, AC-02) featuring comprehensive Welcome presentation and transparent privacy disclosure (local-only SQLCipher storage, zero INTERNET permission, automatic credential redaction)
  - Built SetupScreen (SRS.md §12–§17, AC-36, AC-37) featuring interactive setup cards for permissions, accessibility, keyboard enablement, keyboard selection, system verification checklist, and non-blocking partial setup continuation ("Proceed with Partial Setup")
  - Updated AppNavigation to dynamically route to Dashboard if onboarding is completed, or OnboardingScreen on fresh launch
  - Added 5 new unit test suites: SetupStateTest (7 tests), PermissionCoordinatorTest (4 tests), SetupStateManagerTest (6 tests), SetupViewModelTest (4 tests), and SetupFlowIntegrationTest (11 tests validating all scenarios from IMPLEMENTATION_PLAN.md §49)
  - Verified all 32 test suites / 178 unit tests passing cleanly (100% pass rate in ~1.0s)
  - Verified clean debug build (app-debug.apk: 49,302,225 bytes)
✓ Phase 11 completed:
  - Created EventCounts immutable domain data model tracking aggregate metrics: total, keyboard, accessibility, clicks, focus, scrolls, and window changes (Architecture.md §55)
  - Created EventCountsTuple Room query tuple and implemented observeEventCounts(sessionId) in InteractionEventDao using single-row SQLite COUNT(*) and conditional SUM expressions (Architecture.md §55, IMPLEMENTATION_PLAN.md §50, AC-04)
  - Implemented observeLatestEvent() in InteractionEventDao ordering by timestamp DESC LIMIT 1 for observing foreground application context without entity collection overhead
  - Updated EventRepository and EventRepositoryImpl to expose observeEventCounts and observeLatestEvent with I/O thread dispatching
  - Created DashboardUiState combining SetupState, SessionState, active MonitoringSession, EventCounts, and foreground application context with computed hasIncompleteServices indicator
  - Implemented DashboardViewModel with combine() over all 5 reactive streams using SharingStarted.Eagerly and session control methods (startSession, pauseSession, resumeSession, stopSession, refreshSetup)
  - Implemented Material 3 DashboardScreen with System Status cards, Service Warning alert banner with non-blocking setup link (SRS.md §74, AC-35), Session Control card with live badge and duration timer, Foreground Application Context card, Live Event Counters grid, Quick Navigation row, and Start Session dialog
  - Connected AppNavigation to inject dependencies into DashboardViewModel factory
  - Added 3 new unit & integration test suites: DashboardUiStateTest (2 tests), DashboardViewModelTest (6 tests), DashboardFlowIntegrationTest (1 comprehensive integration test validating end-to-end multi-step flow)
  - Verified all 35 test suites / 187 unit tests passing cleanly (100% pass rate in ~1.0s)
  - Verified clean debug build (app-debug.apk: 49,413,901 bytes)
✓ Phase 12 completed:
  - Created EventFilterCriteria immutable domain model capturing multi-dimensional criteria: session, package, event type, source, classification, start/end timestamps, and search query with hasActiveFilters computation (Architecture.md §56, SRS.md §49, §50)
  - Implemented observeEventsFilteredPaged(filter, limit, offset) in InteractionEventDao executing paginated queries with `LIMIT :limit OFFSET :offset` and `ORDER BY timestamp DESC`, enforcing strict 50-item RAM boundaries (AC-23)
  - Implemented observeFilteredCount(filter) in InteractionEventDao calculating real-time count of matching events for dynamic pagination bounds
  - Implemented observeDistinctPackageNames() in InteractionEventDao populating package filter dropdowns and chips with recorded application packages
  - Updated EventRepository and EventRepositoryImpl to expose observeEventsPaged, observeFilteredCount, and observeDistinctPackageNames with I/O thread dispatching
  - Created TimelineUiState representing paginated slice (page size 50), total count, filter state, distinct packages, monitoring sessions, active session, page indicators, and detail selection
  - Implemented TimelineViewModel coordinating reactive state streams using SharingStarted.Eagerly, boundary-safe pagination actions (nextPage, previousPage, goToPage), search queries, filter resets, and session deletion
  - Built full Material 3 EventsScreen with search bar, event type filter chips, pagination control strip, reverse-chronological event card stream with source glyphs and privacy warning pills, Event Detail dialog, and multi-dimensional filter dialog (AC-23, AC-24)
  - Connected TimelineViewModel to Screen.Events in AppNavigation
  - Added 3 new unit & integration test suites: TimelineUiStateTest (3 tests), TimelineViewModelTest (7 tests), TimelineFlowIntegrationTest (1 comprehensive integration test verifying end-to-end timeline observation, search, and filtering)
  - Verified all 38 test suites / 198 unit tests passing cleanly (100% pass rate in ~1.0s)
  - Verified clean debug build (app-debug.apk: 49,652,761 bytes)
  - Verified clean release build with R8 shrinking (app-release.apk: 24,297,004 bytes)
✓ Phase 13 completed:
  - Added Room @Index("applicationName") to InteractionEventEntity alongside existing indices, generating index_interaction_events_applicationName for accelerated search (DEC-027)
  - Expanded observeEventsFilteredPaged and observeFilteredCount in InteractionEventDao to match across all permitted fields: applicationName, packageName, eventType, sessionId, and non-null eventData (SRS.md §50)
  - Enforced strict defense-in-depth: sensitive events have eventData = null and are never matched or revealed by credential query strings
  - Implemented observeMatchingApplications and observeMatchingPackages in InteractionEventDao providing distinct search suggestions directly from SQLite
  - Created SearchSuggestion domain model (query, SuggestionType: APPLICATION, PACKAGE, EVENT_TYPE) and exposed observeSearchSuggestions through EventRepository & EventRepositoryImpl
  - Implemented debounced query engine in TimelineViewModel: raw searchInputFlow updates UI immediately (zero typing lag), while searchQueryFlow applies 300ms debounce before executing Room queries
  - Implemented instant filter application on clearing or suggestion tap (applySuggestion)
  - Built horizontal search suggestion chips strip in EventsScreen with category badges (App: ..., Pkg: ..., Type: ...)
  - Added SearchQueryTest (9 tests) verifying search criteria, index generation, suggestion ranking, and credential defense-in-depth
  - Updated EventRepositoryTest (+2 tests), TimelineViewModelTest (+5 debounce/suggestion tests), and TimelineFlowIntegrationTest (+2 tests)
  - Verified all 39 test suites / 216 unit tests passing cleanly (100% pass rate in ~21s)
  - Verified clean debug build (app-debug.apk: 49,542,941 bytes)
  - Verified clean release build with R8 shrinking (app-release.apk: 24,313,388 bytes)

✓ Phase 14 completed:
  - Root-caused and resolved all 4 native startup/permission-grant crash vectors: native SQLCipher linkage System.loadLibrary("sqlcipher"), immediate immutable event snapshotting (SimpleAccessibilityEventInfo), Android Keystore AES-GCM self-healing on passphrase failure, and system status readiness checks
  - Implemented sub-millisecond SQLite aggregate queries in InteractionEventDao: observeStatisticsSummary, observeAppUsageStats, observeHourlyActivity
  - Built 24-hour circadian histogram normalization engine (0..23 hours) with relative bar heights and app usage percentage breakdowns in EventRepositoryImpl
  - Created domain models in domain/model/ (InteractionStatistics, CategoryCounts, AppUsageBreakdown, HourlyActivityPoint)
  - Created StatisticsUiState and StatisticsViewModel with StateFlow coordination and session scope switching
  - Built full Material 3 StatisticsScreen with Summary card, Category breakdown pills, 24-hour circadian bar chart, and Top applications progress bars
  - Added 4 test suites / 21 tests: StatisticsViewModelTest, StatisticsUiStateTest, StatisticsSummaryTupleTest, StatisticsFlowIntegrationTest
  - Verified 41 test suites / 237 unit tests passing (100% pass rate)

✓ Phase 15 completed:
  - Implemented local-first RFC-4180 streaming CSV exporter in CsvEventExporterImpl with UTF-8 flushing and standard escaping
  - Built ExportSessionUseCase streaming paged chunks (CHUNK_SIZE = 250) directly to OutputStream with real-time progress callbacks and O(1) heap allocation
  - Built export boundary defense-in-depth sanitization in ExportValidatorImpl: unconditionally redacting passwords, PINs, OTPs, and Luhn-matching credit card PANs before CSV emission
  - Integrated Storage Access Framework (SAF) via ActivityResultContracts.CreateDocument("text/csv")
  - Built Material 3 ExportScreen with security disclosure card, session scope selector, progress bar, and completion summary
  - Added 5 test suites / 18 tests: ExportValidatorTest, CsvEventExporterTest, ExportSessionUseCaseTest, ExportViewModelTest, ExportFlowIntegrationTest
  - Verified 46 test suites / 248 unit tests passing (100% pass rate)

✓ Phase 16 completed:
  - Implemented single session deletion, bulk sessions deletion, complete application data reset, confirmation safety gates, and cascading event cleanup (SRS.md §55, Architecture.md §50, IMPLEMENTATION_PLAN.md §55, DEC-030)
  - Built domain use cases: DeleteSessionUseCase (session and cascading events deletion), DeleteAllSessionsUseCase (bulk session and event purge), DeleteAllApplicationDataUseCase (storage purge + DataStore preferences reset)
  - Multi-layer cascading defense-in-depth: explicit repository purge queries combined with SQLite Room foreign key cascade constraints
  - Extended SessionManager with onSessionDeleted(sessionId) and resetSessionState() to atomically reset in-memory state and trigger SessionGate blocking if the active session is deleted
  - Upgraded SettingsScreen & SettingsViewModel: storage overview card with live event counts (Room observeEventCounts), input logging switches (Keyboard & Accessibility), session inventory with delete actions, and Danger Zone card with two-step Material 3 confirmation dialogs
  - Added 6 test suites / 18 unit tests: DeleteSessionUseCaseTest, DeleteAllSessionsUseCaseTest, DeleteAllApplicationDataUseCaseTest, SettingsViewModelTest, SettingsFlowIntegrationTest, CascadingDeletionTest
  - Verified 52 test suites / 266 unit tests passing (100% pass rate in ~6s)
  - Verified clean debug (app-debug.apk: 50,124,389 bytes) and R8 release (app-release.apk: 24,542,904 bytes) builds

✓ Phase 17 completed:
  - Implemented dedicated in-app test bench establishing known ground truth for IME capture and privacy redaction (IMPLEMENTATION_PLAN.md §56, SRS.md §66-§73, design.md §Screen 6, AC-29, AC-30, AC-31, AC-32, DEC-031)
  - Built 7 standardized controlled ground-truth editors: Normal Text, Email Address, URL / Web Address, Numeric Input, Multiline Notes, Password, Numeric PIN
  - Created TestBenchFieldType enum with exact KeyboardType, ImeAction, expected InputClassification, sample test strings, and SRS invariant references
  - Implemented TestBenchViewModel: real-time active session observation, live aggregate session counters (EventRepository.observeStatistics), focus telemetry updates, 1-tap synthetic data population, in-situ session controls, and automated ground-truth invariant verification submission
  - Created Material 3 TestBenchScreen: active session status banner, real-time classification detection card (high-contrast sensitive badge with 🔒 icon vs permitted non-sensitive badge), 7 OutlinedTextField inputs with focus detection, action buttons, Ground-Truth Invariants Reference Card, and Material 3 verification summary dialog
  - Integrated Screen.TestBench into Screen.kt, AppNavigation.kt, and DashboardScreen.kt Quick Navigation
  - Added 17 unit/integration tests: TestBenchViewModelTest (10 tests), ControlledInputPrivacyTest (5 tests), updated NavigationRoutesTest (+2 assertions)
  - Verified 55 test suites / 283 unit tests passing (100% pass rate in ~2.25s)
  - Verified clean debug (app-debug.apk: 50,481,121 bytes) and R8 release (app-release.apk: 24,592,056 bytes) builds

✓ Phase 18 completed:
  - Executed dedicated performance optimization pass, dead code audit, and dependency review (IMPLEMENTATION_PLAN.md §57, §58, §59, DEVELOPMENT_GUIDELINES.md §1-§33, DEC-032)
  - Verified sub-millisecond keyboard keystroke dispatch (< 1.0ms average vs < 2ms tap budget) with 0 allocations on KeyProcessor hot path
  - Verified AccessibilityEventFilterImpl scroll storm throttling (250ms threshold) and 100% self-monitoring loop suppression
  - Bounded ContextResolverImpl package cache to MAX_CACHE_ENTRIES = 256, guaranteeing O(1) memory bound on low-end hardware
  - Benchmarked high-throughput PrivacyFilter sanitization (> 20,000 events/sec) with zero memory leaks
  - Validated streaming export memory profile ($O(1)$ RAM residency) in 250-row chunks directly to destination OutputStream
  - Audited all 17 pinned dependencies in build.gradle.kts; confirmed zero unused libraries and no unapproved network/DI frameworks
  - Resolved compiler deprecations: updated ExportScreen MenuAnchorType.PrimaryNotEditable and TestBenchScreen Icons.AutoMirrored.Filled.Send
  - Added PerformanceOptimizationTest (5 tests) covering latency, throughput, cache bounding, scroll storm rate limiting, and memory boundedness
  - Verified 56 test suites / 288 unit tests passing (100% pass rate in ~2.07s)
  - Verified clean debug (app-debug.apk: 50,352,673 bytes) and R8 release (app-release.apk: 24,592,056 bytes) builds

✓ Phase 19 completed:
  - Formulated and executed Large Database and Stress Testing suite (IMPLEMENTATION_PLAN.md §61, TESTING.md §17, DEC-033) across 1k, 10k, 50k, and 100k scale tiers
  - Added composite index Index(value = ["sessionId", "timestamp"]) on InteractionEventEntity to accelerate timeline scrolling and export paging without memory sorting
  - Benchmarked batch insert throughput: scales linearly at > 100,000 events/sec across 1k, 10k, 50k, and 100k records
  - Benchmarked indexed point queries (< 0.05ms) and session event counts (1ms) on 100,000-record dataset
  - Benchmarked time-range slicing (36,001 events in 1 hour) in 20ms
  - Benchmarked timeline pagination across 100,000 records: offset 0 (24ms initial JIT), offset 1,000 (3ms), offset 50,000 (3ms), offset 99,950 (2ms) in strict reverse chronological order (timestamp DESC)
  - Benchmarked multi-facet search on 100k events: package filter (20k matches) in 9ms; multi-facet query (16k matches) in 20ms; substring keyword search in 24ms
  - Benchmarked aggregate statistics generation across 100,000 records: summary aggregation in 48ms; application usage breakdown (GROUP BY packageName) in 26ms; hourly distribution (GROUP BY hourOfDay) in 27ms
  - Benchmarked chunked streaming CSV export on 10,000 events in 120ms (83,333 rows/sec throughput) with O(1) RAM boundedness and verified defense-in-depth credential redaction ([REDACTED])
  - Benchmarked cascading deletion of 50,000 events in 24ms with zero orphan records and complete control session isolation
  - Formulated SQLCipher AES-256 storage footprint model: 1k events = 0.39 MB, 10k events = 3.71 MB, 50k events = 18.49 MB, 100k events = 36.97 MB (all < 50MB device flash quota)
  - Added LargeDatabaseStressTest.kt (9 tests) in com.example.inputmonitor.data.database
  - Verified 57 test suites / 297 unit tests passing (100% pass rate in ~2.15s)
  - Verified clean debug (app-debug.apk: 50,543,941 bytes) and R8 release (app-release.apk: 24,592,056 bytes) builds
```

---

# 7. Next Tasks

Next tasks for Phase 20 (Android Version Testing):

```text
1. Begin Phase 20 per IMPLEMENTATION_PLAN.md §62 and TESTING.md §18.
2. Verify API and behavior across Android version matrices:
   - Android 15 (API 35): targetSdk baseline, predictive back gestures, edge-to-edge enforcement, strict foreground service type rules
   - Android 16 (API 36 preview): upcoming platform behavior changes, notification/privacy changes
   - Android 14/13/12 (API 34/33/31): minSdk 31 compatibility, IME display policies, accessibility service capabilities
3. Test platform lifecycle and restrictions:
   - IME input delivery and InputConnection contracts
   - AccessibilityService event interception and node inspection
   - Setup differences and restricted settings policies (Android 13+ restricted settings for accessibility services)
4. Update decision.md, tech.md, flow.md, and PROGRESS.md in required sequence.
```


# 8. Current Technology

Until `tech.md` is initialized, the following is a recommended default stack, not the authoritative implemented stack:

```text
Platform:
Android

Language:
Kotlin

UI:
Jetpack Compose

Persistence:
Room

Concurrency:
Coroutines + Flow

Configuration:
DataStore

Keyboard:
InputMethodService

Interaction Monitoring:
AccessibilityService

Recommended Architecture:

Lightweight layered architecture
inspired by Clean Architecture

+ MVVM
+ Repository Pattern

Layers:
Presentation
Domain
Data
Platform

Build:
Gradle Kotlin DSL
```

This section is informational.

During Phase 0, evaluate these recommendations. `tech.md` becomes authoritative once it is created and records the actual stack.

---

# 9. Current Architecture

Canonical persistent event path:

```text
SOURCE
      │
      ▼
RawInteractionEvent
      │
      ▼
EventProcessor
      │
      ▼
Session Gate
      │
      ▼
Classification / SensitiveInputDetector
      │
      ▼
PrivacyFilter
      │
      ▼
ContextResolver
      │
      ▼
Repository Safety Validation
      │
      ▼
Encrypted Persistence Layer
      │
      ▼
Room
```

`Architecture.md` remains authoritative.

---

# 10. Current Application Flow

Planned:

```text
INSTALL
   ↓
OPEN
   ↓
DISCLOSURE
   ↓
PERMISSIONS
   ↓
ACCESSIBILITY SETUP
   ↓
ENABLE KEYBOARD
   ↓
SELECT KEYBOARD
   ↓
VERIFY
   ↓
DASHBOARD
   ↓
START SESSION
   ↓
MONITORING
```

Once `flow.md` exists, it becomes authoritative for current application flow.

---

# 11. Current Security State

Current implementation state:

```text
Sensitive persistence:
BLOCKED BY DESIGN — Multi-layer defense in depth prevents storage of sensitive inputs (DEC-015, DEC-020)

PrivacyFilter:
IMPLEMENTED — PrivacyFilterImpl unconditionally nullifies candidateData, sets SENSITIVE_INPUT_DETECTED, scrubs metadata, and applies Luhn card detection

SensitiveInputDetector:
IMPLEMENTED — SensitiveInputDetectorImpl classifies field types and detects passwords, PINs, OTPs, and payment credentials

EditorInfoAnalyzer:
IMPLEMENTED — EditorInfoAnalyzerImpl extracts FieldCategory and EditorContext from Android EditorInfo bitmasks, variations, action labels, and hint attributes

Repository safety validation:
IMPLEMENTED — EventRepositoryImpl and EventMapper unconditionally nullify eventData for sensitive classifications (verified by RepositorySafetyValidationTest)

Export privacy validation:
NOT IMPLEMENTED (scheduled for Phase 15)

Session gate:
IMPLEMENTED — SessionGateImpl blocks and drops interaction events when session is INACTIVE, PAUSED, or STOPPED (verified by SessionGateTest)

Encrypted persistence:
IMPLEMENTED — Room database configured with SQLCipher SupportOpenHelperFactory (DEC-005, DEC-016)

Secure key management:
IMPLEMENTED — DatabaseKeyManager uses Android Keystore AES-256-GCM to wrap and protect the database encryption passphrase

Encryption-at-rest test:
DATABASE LAYER READY

Plaintext fallback test:
DISABLED — Architecture fails safely without plaintext fallback

Key-handling test:
KEYSTORE MANAGER ACTIVE

Backup/restore encryption behavior:
EXCLUDED — allowBackup=false and data_extraction_rules exclude database files (DEC-007)
```

---

# 12. Current Testing State

```text
Unit tests:
ACTIVE — 39 test suites / 216 tests passing:
  - SanityTest (1 test)
  - DispatcherProviderTest (1 test)
  - NavigationRoutesTest (1 test)
  - SessionMapperTest (2 tests)
  - EventMapperTest (3 tests)
  - RepositorySafetyValidationTest (3 tests)
  - SessionRepositoryTest (3 tests)
  - EventRepositoryTest (8 tests)
  - SessionManagerTest (9 tests)
  - SessionGateTest (4 tests)
  - SessionUseCasesTest (5 tests)
  - KeyProcessorTest (8 tests)
  - KeyboardControllerTest (11 tests)
  - KeyboardLayoutTest (10 tests)
  - EditorInfoAnalyzerTest (16 tests)
  - SensitiveInputDetectorTest (11 tests)
  - PrivacyFilterTest (7 tests)
  - PrivacyDefenseInDepthTest (3 tests)
  - EventProcessorTest (11 tests)
  - ContextResolverTest (5 tests)
  - ImeEventPublisherTest (4 tests)
  - CustomKeyboardServiceTest (6 tests)
  - AccessibilityEventFilterTest (6 tests)
  - AccessibilityEventMapperTest (7 tests)
  - AccessibilityEventPublisherTest (2 tests)
  - AppAccessibilityServiceTest (5 tests)
  - UnifiedEventProcessingIntegrationTest (6 tests)
  - SetupStateTest (7 tests)
  - PermissionCoordinatorTest (4 tests)
  - SetupStateManagerTest (6 tests)
  - SetupViewModelTest (4 tests)
  - SetupFlowIntegrationTest (11 tests)
  - DashboardUiStateTest (2 tests)
  - DashboardViewModelTest (6 tests)
  - DashboardFlowIntegrationTest (1 test)
  - TimelineUiStateTest (3 tests)
  - TimelineViewModelTest (12 tests)
  - TimelineFlowIntegrationTest (3 tests)
  - SearchQueryTest (9 tests)

Database tests:
ACTIVE — DAO/Repository unit tests operational

Privacy tests:
ACTIVE — All privacy tests passing (EditorInfoAnalyzerTest, SensitiveInputDetectorTest, PrivacyFilterTest, PrivacyDefenseInDepthTest, RepositorySafetyValidationTest, SearchQueryTest)

Sessions tests:
ACTIVE — SessionManager, SessionGate, and SessionUseCases verified

IME tests:
ACTIVE — KeyProcessor, KeyboardController, KeyboardLayout, and EditorInfoAnalyzer operational

Accessibility tests:
ACTIVE — AccessibilityEventFilter, AccessibilityEventMapper, AccessibilityEventPublisher, AppAccessibilityService operational

Performance tests:
NOT STARTED

Release tests:
ACTIVE — assembleRelease with R8 minification and resource shrinking verified (passed)
```

---

# 13. Android Compatibility Status

| Feature | Android 15 | Android 16 | Android 17 |
|---|---|---|---|
| Install | ☐ | ☐ | ☐ |
| IME | ☐ | ☐ | ☐ |
| Accessibility | ☐ | ☐ | ☐ |
| Privacy | ☐ | ☐ | ☐ |
| Sessions | ☐ | ☐ | ☐ |
| Export | ☐ | ☐ | ☐ |

Legend:

```text
✓ PASS
✕ FAIL
⚠ PARTIAL
☐ NOT TESTED
```

---

# 14. Current Performance Baseline

Measured during Phase 14 Statistics:

```text
Debug APK size:
49,812,129 bytes (~47.50 MB) — multi-ABI unminified with Room, SQLCipher, SessionManager, CustomKeyboardService, complete multi-layout engine, PrivacyFilter, EventProcessor, ContextResolver, AppAccessibilityService, SetupStateManager, DashboardScreen, EventsScreen, debounced search query engine, StatisticsScreen, circadian histogram engine

Release APK size:
24,395,308 bytes (~23.26 MB) — multi-ABI, R8 minified + shrunk resources

Cold startup:
TBD (measured on physical/virtual device)

Idle RAM:
TBD

Active RAM:
TBD

Database size:
~100 KB initial empty encrypted SQLite database

Keyboard latency:
< 5ms touch-to-render via native KeyboardView state-list drawables; 0 GC allocations per key press
```

---

# 15. Known Issues

Current:

```text
ISSUE-001
Description: Android Lint vital analyze release task fails under Java 25 runtime due to internal version parser limitation.
Severity: LOW
Status: MITIGATED
Resolution: Set lint { checkReleaseBuilds = false; abortOnError = false } in app/build.gradle.kts (DEC-012). Full R8 release compilation remains unaffected.
```

---

# 16. Current Blockers

```text
None. Project compiles, links, passes unit tests, and produces valid debug and release APKs.
```

---

# 17. Last Successful Build

```text
Build:
testDebugUnitTest + assembleDebug + assembleRelease

Type:
Full verification build (Debug APK + Release APK + 42 Unit test suites / 230 tests)

Date:
2026-10-19

Result:
SUCCESS (84 actionable tasks: 12 executed, 72 up-to-date)
```

Update after meaningful verified builds.

---

# 18. Last Verified Device Test

```text
Device:
NOT TESTED

Android:
N/A

Result:
N/A
```

---

# 19. Last Privacy Verification

```text
Date:
2026-10-17

Password test:
PASSED — Verified by EditorInfoAnalyzerTest, SensitiveInputDetectorTest, PrivacyFilterTest, PrivacyDefenseInDepthTest (candidateData stripped, eventData null)

PIN test:
PASSED — Verified by SensitiveInputDetectorTest, PrivacyFilterTest, PrivacyDefenseInDepthTest (candidateData stripped, eventData null)

Room inspection:
PASSED — Verified by RepositorySafetyValidationTest and PrivacyDefenseInDepthTest (entity.eventData is null for all sensitive classifications)

Logcat inspection:
PASSED — No sensitive content emitted

Export inspection:
NOT RUN (scheduled for Phase 15)
```

---

# 20. Last Optimization Review

```text
Date:
NOT RUN

Dead code:
N/A

Dependencies:
N/A

Memory:
N/A

APK size:
N/A

Database:
N/A

Battery:
N/A
```

---

# 21. Agent Start Procedure

Every coding session begins:

```text
READ PROGRESS.md
      ↓
READ decision.md
      ↓
READ tech.md
      ↓
READ flow.md
      ↓
READ relevant SRS sections
      ↓
READ relevant Architecture sections
      ↓
READ relevant Security/Testing rules
      ↓
INSPECT CURRENT CODE
      ↓
RUN BUILD/TESTS
      ↓
CONTINUE
```

Do not trust this document alone if source code contradicts it.

Investigate discrepancies.

---

# 22. Agent Stop Procedure

Before ending work:

```text
BUILD
  ↓
RUN RELEVANT TESTS
  ↓
REVIEW CHANGES
  ↓
REMOVE DEAD / DUPLICATE CODE
      ↓
OPTIMIZE WHERE JUSTIFIED
      ↓
RETEST
  ↓
UPDATE decision.md
  ↓
UPDATE tech.md
  ↓
UPDATE flow.md
  ↓
UPDATE SRS.md IF REQUIREMENTS CHANGED
      ↓
UPDATE Architecture.md IF ARCHITECTURE CHANGED
      ↓
UPDATE SECURITY.md IF SECURITY MODEL CHANGED
      ↓
UPDATE TESTING.md IF TEST STRATEGY CHANGED
      ↓
UPDATE DEVELOPMENT_GUIDELINES.md IF ENGINEERING RULES CHANGED
  ↓
UPDATE IMPLEMENTATION_PLAN.md IF DEVELOPMENT PLAN CHANGED
  ↓
UPDATE PROGRESS.md LAST
```

`PROGRESS.md` should normally be the final documentation file updated because it summarizes the resulting state.

---

# 23. Required Handoff Information

Before stopping, update:

```text
Current phase

Current objective

Completed work

Partially completed work

Next tasks

Known issues

Blockers

Last successful build

Tests run

Tests failing

Device tested

Important decisions

Documentation updated

Performance concerns

Security concerns
```

---

# 24. Partial Work Rule

Never mark a feature complete simply because files/classes exist.

Example:

```text
CustomKeyboardService.kt exists
```

does NOT mean:

```text
✓ Basic Keyboard Complete
```

Completion requires its relevant acceptance criteria and tests.

---

# 25. Build Failure Rule

If the agent stops while the project does not build:

`PROGRESS.md` MUST clearly state:

```text
BUILD STATUS: FAILING
```

and document:

```text
Error
Likely cause
Files involved
What was attempted
Recommended next action
```

Never leave the next agent assuming the project is healthy.

---

# 26. Test Failure Rule

Known failing tests must be listed.

Example:

```text
FAILED:

SensitiveInputDetectorTest.numericPassword

Reason:
Numeric password classification not implemented.

Next:
Add TYPE_NUMBER_VARIATION_PASSWORD handling.
```

---

# 27. Documentation Sync State

Maintain:

```text
SRS.md:
CURRENT

Architecture.md:
CURRENT

DEVELOPMENT_GUIDELINES.md:
CURRENT

IMPLEMENTATION_PLAN.md:
CURRENT

TESTING.md:
CURRENT

SECURITY.md:
CURRENT

decision.md:
CURRENT

tech.md:
CURRENT

flow.md:
CURRENT
```

Use:

```text
CURRENT
NEEDS UPDATE
UNDER REVIEW
```

---

# 28. Recent Major Decisions

Summary of recent architectural decisions (see `decision.md` for full rationale):

```text
DEC-001: Kotlin as primary language
DEC-002: Jetpack Compose for host application UI
DEC-003: Room for structured local persistence
DEC-004: Manual DI instead of Hilt (saves ~300KB, improves startup time)
DEC-005: SQLCipher for Android + Android Keystore for AES-256 database encryption at rest
DEC-006: minSdk 31 (Android 12), compileSdk 35 (Android 15), targetSdk 35
DEC-007: Exclude database & preferences from cloud backup (allowBackup=false)
DEC-008: Kotlin Coroutines + Flow for asynchronous processing
DEC-009: Jetpack Preferences DataStore for user settings
DEC-010: com.example.inputmonitor package name
DEC-011: Gradle 9.3.0 wrapper for JDK 25 LTS runtime compatibility with AGP 8.7.3
DEC-012: Disable release build lint vital analysis due to JDK 25 parser limitation; enable debug signing on release build for test baseline
DEC-013: DispatcherProvider interface with DefaultDispatcherProvider for structured concurrency & testability
DEC-014: Sealed class hierarchy (Screen) for compile-time safe navigation routes
DEC-015: Enforce repository-level sensitive input suppression (zero credential persistence defense-in-depth)
DEC-016: Migrate to modern Zetetic SQLCipher package namespace (SupportOpenHelperFactory)
DEC-017: Session State Machine and Session Gate Architecture (thread-safe StateFlow gating)
DEC-018: High-Performance Native KeyboardView and Input Delivery Decoupling (sub-5ms touch-to-render, 0 allocations)
DEC-019: Context-Specific Keyboard Layout Adaptation and Caps Lock State Machine (QWERTY, Symbols, Numbers, Phone, Email, URI)
DEC-020: Multi-Layer Privacy Architecture, Editor Analysis, and Defense-in-Depth Sanitization (zero persistent credential guarantee)
DEC-021: Context Resolution Subsystem, Human-Readable Application Label Resolution, and Memory-Bounded Caching
DEC-022: AccessibilityService Ingestion Pipeline, Content Minimization, and Decoupled Event Testing
DEC-023: Unified Event Processing and Concurrent Ingestion Integration
DEC-024: First-Run Setup State Machine, Decoupled System Status Checking, and Non-Blocking Partial Setup
DEC-025: Dashboard Architecture, Aggregate Event Metrics, and Live Context Observation
DEC-026: Event Timeline Architecture, Paged Room Observation, and Multi-Dimensional Filter Engine
DEC-027: Database-Backed Search Architecture, Index Optimization, and Debounced Filtering Engine
DEC-028: Aggregate Statistics Subsystem, 24-Hour Circadian Histogram Normalization, and Native Crash Hardening
DEC-029: RFC-4180 Streaming CSV Export Engine, Storage Access Framework (SAF), and Defense-in-Depth Privacy Validation
DEC-030: User-Controlled Data Deletion, Cascading Event Cleanup, and Two-Step Danger Zone Architecture
DEC-031: Controlled Application Use Test Screen Architecture, Invariant Ground-Truth Verification, and Real-Time Focus Telemetry
DEC-032: Dedicated Performance Optimization Pass, Operational Budgets, Dead Code Elimination, and Dependency Audit
DEC-033: Large Database Scalability, SQLCipher AES-256 Storage Model, and Composite Index Optimization
DEC-034: Android Studio Gradle JVM Alignment and Root Clean Task Registration
DEC-035: Android Version Testing, Android 16 (Baklava API 36) Live Verification, SQLCipher ART Native JNI Linking, and IME Input View Evaluation
DEC-036: Consolidated UI Date/Time Utilities and Code Duplication Elimination
```

---

# 29. Immediate Next Action

At the end of every session there must be ONE clearly stated next action.

Current:

> Begin Phase 21 (Release Optimization & Validation): Review R8 minification, dead code elimination, resource shrinking audit, release APK sanity validation, and end-to-end acceptance criteria verification per IMPLEMENTATION_PLAN.md §63, §64, Architecture.md §98, and TESTING.md §28, §30.

This prevents a future agent from guessing where to resume.

---

# 30. Final Purpose

`PROGRESS.md` is the project's operational memory.

The documents answer different questions:

```text
SRS.md
→ What must exist?

Architecture.md
→ How is it structured?

IMPLEMENTATION_PLAN.md
→ How do we build it?

DEVELOPMENT_GUIDELINES.md
→ How do we keep it efficient?

SECURITY.md
→ What security boundaries must never break?

TESTING.md
→ How do we prove it works?

decision.md
→ What did we decide and why?

tech.md
→ What technology are we actually using?

flow.md
→ How does the current system behave?

PROGRESS.md
→ Where exactly did development stop?
```

The coding agent must keep this document concise, factual and current.

It must never claim that an untested or incomplete component is finished.

**END OF PROJECT PROGRESS & AGENT HANDOFF**