# Application Flow

**Document:** `flow.md`
**Status:** Living Document / Authoritative
**Purpose:** How the application currently flows. Must remain synchronized with implementation.

---

# 1. Overall Application Flow

```text
INSTALL
   ↓
FIRST LAUNCH
   ↓
DISCLOSURE / ONBOARDING
   ↓
PERMISSIONS
   ↓
ACCESSIBILITY SETUP
   ↓
ENABLE KEYBOARD
   ↓
SELECT KEYBOARD
   ↓
VERIFY SETUP
   ↓
DASHBOARD
   ↓
START MONITORING SESSION
   ↓
COLLECT PERMITTED EVENTS
   ↓
EVENT PROCESSOR / SESSION GATE / CLASSIFICATION
   ↓
PRIVACY FILTER
   ↓
CONTEXT RESOLVER
   ↓
REPOSITORY SAFETY VALIDATION
   ↓
ENCRYPTED PERSISTENCE LAYER
   ↓
ROOM / LOCAL STORAGE
   ↓
VIEW / ANALYZE / EXPORT
```

---

# 2. First Installation Flow

```text
User installs APK
   ↓
Android registers application
   ↓
User opens application
   ↓
MonitoringApplication.onCreate()
   ↓
MainActivity launches
   ↓
Check onboarding completion (DataStore)
   ↓
Show Onboarding/Disclosure screen
   ↓
User acknowledges
   ↓
Navigate to Setup
```

---

# 3. Permission Flow

```text
Setup Screen
   ↓
Check required runtime permissions
   ↓
Request missing permissions
   ↓
User grants / denies
   ↓
SetupStateManager.refresh()
   ↓
Update UI state
```

---

# 4. Accessibility Setup Flow

```text
Setup Screen
   ↓
User taps "Enable Accessibility"
   ↓
Open Android Accessibility Settings
   ↓
User enables AppAccessibilityService
   ↓
User returns to app
   ↓
SetupStateManager.refresh()
   ↓
UI updates accessibility status
```

---

# 5. Keyboard Setup Flow

```text
Setup Screen
   ↓
User taps "Enable Keyboard"
   ↓
Open Android Input Method Settings
   ↓
User enables CustomKeyboardService
   ↓
User returns to app
   ↓
User taps "Select Keyboard"
   ↓
Show Android IME picker
   ↓
User selects CustomKeyboardService
   ↓
SetupStateManager.refresh()
   ↓
```

---

# 5.1. Setup Verification & Partial Setup Flow

```text
Setup Screen
   ↓
System Verification Checklist (SRS.md §16)
   • Permissions: Ready
   • Accessibility: Enabled / Disabled
   • Custom Keyboard: Enabled / Not Enabled
   • Current Keyboard: Selected / Other Keyboard
   • Sensitive Protection: Active
   • Database: Ready
   ↓
Evaluate SetupState (Architecture.md §14, §15)
   ├── Setup Complete (isComplete == true)
   │      ↓
   │   [Complete Setup & Open Dashboard]
   │      ↓
   │   Set onboardingCompleted = true
   │      ↓
   │   Navigate to Dashboard
   │
   └── Setup Incomplete / Skipped (isComplete == false)
          ↓
       [Proceed with Partial Setup] (SRS.md §17, AC-35)
          ↓
       Set onboardingCompleted = true
          ↓
       Navigate to Dashboard (Never traps or loops the user)
```

---

# 6. Keyboard Runtime Flow

```text
TEXT FIELD FOCUSED
   ↓
ANDROID STARTS IME
   ↓
onStartInput(EditorInfo)
   ↓
EditorInfoAnalyzer
   ↓
SensitiveInputDetector
   ↓
KeyboardController updates state
   ↓
Render appropriate layout
   ↓
USER PRESSES KEY
   ↓
KeyProcessor
   │
   ├──→ InputConnection → TARGET APP  (always, immediately)
   │
   └──→ Interaction Event Candidate (best-effort)
              ↓
         Session Gate (active session?)
              ↓
         Classification / SensitiveInputDetector
              ↓
         Privacy Filter
              ↓
         ContextResolver
              ↓
         Repository Safety Validation
              ↓
         Encrypted Persistence Layer
              ↓
         Room
```

---

# 7. Monitoring Session Flow & State Machine

```text
               ┌────────────────────────────────────────────────────────┐
               │                        INACTIVE                        │
               └───────────────────────────┬────────────────────────────┘
                                           │
                                  startSession(name, notes)
                                           │
                                           ▼
               ┌────────────────────────────────────────────────────────┐
               │                         ACTIVE                         │
               │   • PreferencesRepository.setActiveSessionId(id)       │
               │   • SessionGate.canProcessEvents() == true             │
               │   • UI badge: ● ACTIVE                                 │
               └───────────────┬────────────────────────▲───────────────┘
                               │                        │
                  pauseSession()                        resumeSession()
                               │                        │
                               ▼                        │
               ┌────────────────────────────────────────┴───────────────┐
               │                         PAUSED                         │
               │   • SessionGate.canProcessEvents() == false (DROP)     │
               │   • UI badge: ⏸ PAUSED                                 │
               └───────────────┬────────────────────────────────────────┘
                               │
                          stopSession()
                               │
                               ▼
               ┌────────────────────────────────────────────────────────┐
               │                        STOPPED                         │
               │   • endTime recorded in Room database                  │
               │   • PreferencesRepository.setActiveSessionId(null)     │
               │   • SessionGate.canProcessEvents() == false (DROP)     │
               │   • UI badge: ■ STOPPED                                │
               └────────────────────────────────────────────────────────┘

Process Restart Recovery Flow:
MonitoringApplication.onCreate()
   ↓
SessionManager.restoreSessionIfActive()
   ↓
PreferencesRepository.activeSessionId?
   ├── NULL ──► State remains INACTIVE
   └── ID   ──► Query SessionRepository.getSessionById(ID)
                  ├── State != STOPPED ──► Restore ACTIVE / PAUSED state
                  └── State == STOPPED ──► Clear preference, state STOPPED
```

## 7.1 Session Gate Flow

```text
Platform Service (IME / Accessibility)
             │
             ▼
    RawInteractionEvent
             │
             ▼
        SessionGate
             │
   canProcessEvents()?
             │
     ┌───────┴───────┐
     │               │
    YES              NO
(Session ACTIVE)  (INACTIVE / PAUSED / STOPPED)
     │               │
     ▼               ▼
Privacy Pipeline  DROP EVENT
     │            (Zero processing,
     ▼             zero persistence)
Encrypted DB
```

---

# 8. Accessibility Event Flow

```text
Android System
   ↓
onAccessibilityEvent()
   ↓
AccessibilityEventFilter (enabled category?)
   ├── Ignore → return
   └── Accept
         ↓
   AccessibilityEventMapper
         ↓
   RawInteractionEvent
         ↓
   Enqueue asynchronously → return from callback
         ↓
   EventProcessor (background)
         ↓
   (same pipeline as §6)
```

---

# 9. IME Keyboard Input Flow

```text
User touches key on KeyboardView
   │
   ├── Audio feedback (AudioManager.playSoundEffect)
   ├── Haptic feedback (HapticFeedbackConstants.KEYBOARD_TAP)
   │
   ▼
CustomKeyboardService.handleKeyAction(action)
   │
   ├── KeyAction.Shift ────────► KeyboardController.toggleShift() ─► refresh KeyboardView
   ├── KeyAction.SwitchIme ────► InputMethodManager.showInputMethodPicker()
   ├── KeyAction.SwitchLayout ─► KeyboardController.switchLayout() ─► refresh KeyboardView
   │
   └── KeyAction.Character / Space / Backspace / Enter
         │
         ▼
       KeyProcessor.processKey(action, currentInputConnection, editorInfo)
         │
         ├── Character ──► InputConnection.commitText(char, 1)
         │                   │
         │                   ▼ (if Shift was ON_ONCE, reset to OFF)
         │                 KeyboardController.onCharacterTyped()
         │
         ├── Space ──────► InputConnection.commitText(" ", 1)
         ├── Backspace ──► InputConnection.deleteSurroundingText(1, 0)
         └── Enter ──────► performEditorAction(actionId) or commitText("\n", 1)
```

## 9.1 Keyboard Layout Adaptation & Switching Flow

```text
Editor started (CustomKeyboardService.onStartInputView)
   │
   ▼
KeyboardController.onStartInputView(info)
   │
   ├── info.inputType & TYPE_CLASS_PHONE != 0 ──────────────► KeyboardLayoutType.PHONE
   ├── info.inputType & TYPE_CLASS_NUMBER != 0 ─────────────► KeyboardLayoutType.NUMERIC
   ├── info.inputType == TYPE_NULL ─────────────────────────► KeyboardLayoutType.NUMERIC
   ├── info.inputType & TYPE_MASK_VARIATION == EMAIL_ADDRESS ► KeyboardLayoutType.EMAIL
   ├── info.inputType & TYPE_MASK_VARIATION == URI ─────────► KeyboardLayoutType.URI
   └── default ─────────────────────────────────────────────► KeyboardLayoutType.QWERTY
   │
   └── Action Key Label: IME_ACTION_GO ("Go"), SEARCH ("Search"), SEND ("Send"),
       NEXT ("Next"), DONE ("Done"), multiline (↵)

Interactive Layout Switching:
[?123]   ──► KeyboardLayoutType.SYMBOLS
[=<\ ]   ──► KeyboardLayoutType.SYMBOLS_MORE
[ABC]    ──► KeyboardLayoutType.QWERTY (or EMAIL / URI based on context)
[123#]   ──► Toggle between NUMERIC and PHONE / SYMBOLS
Double-Tap [⇧] (within 350ms) ──► Caps Lock (⇪)
Single-Tap [⇧] ──► Shift Once (⬆) ──► Reverts to (⇧) on alphanumeric key press
```

---

# 10. Sensitive Input Flow

```text
ACTIVE EDITOR
   ↓
EditorInfo
   ↓
EditorInfoAnalyzer
   ↓
SensitiveInputDetector
   ↓
┌────────────────┐
│                │
NORMAL        SENSITIVE
│                │
▼                ▼
Normal event   CONTENT DISCARDED
processing     Metadata-only persisted
│                │
└────────┬───────┘
         ↓
  Event Processor
         ↓
  Repository
```

Classification = SENSITIVE implies:
- eventData = NULL
- Key preview popups suppressed
- Suggestion strip shows privacy badge
- Keyboard continues functioning normally

---

# 11. Database Flow

```text
Repository receives validated event
   ↓
Repository Safety Validation (enforce eventData = NULL if isSensitive)
   ↓
Map domain model → Room entity (sanitizes sensitive data)
   ↓
DatabaseKeyManager retrieves AES-GCM protected passphrase from Android Keystore
   ↓
SQLCipher SupportOpenHelperFactory opens encrypted SQLite database
   ↓
Write to encrypted .db file (input_monitor_encrypted.db)
   ↓
Room DAOs provide reactive Flow<List<Entity>> to Repositories
   ↓
Repositories map to domain models for UI/ViewModel consumption
```

---

# 12. Dashboard Flow

```text
Dashboard Screen Launched
   ↓
DashboardViewModel initializes StateFlow (SharingStarted.Eagerly)
   ├── combine(
   │     observeSessionStateUseCase(),
   │     observeActiveSessionUseCase(),
   │     setupStateManager.observeSetupState(),
   │     eventRepository.observeEventCounts(),
   │     eventRepository.observeLatestEvent()
   │   )
   ↓
UI State Emits: DashboardUiState
   │
   ├── [System Capability Card]
   │     • Custom Keyboard: Enabled / Selected
   │     • Accessibility Service: Active / Inactive
   │     • Protection Layer: Active (Zero Credential Storage)
   │     • Database: Ready
   │
   ├── [Incomplete Services Warning Banner] (SRS.md §74, AC-35)
   │     • Displayed if accessibilityEnabled == false or imeSelected == false
   │     • "Fix Configuration" button routes to Setup Wizard without blocking
   │
   ├── [Session Control Card]
   │     • Session Name & State Badge (ACTIVE / PAUSED / STOPPED / INACTIVE)
   │     • Duration elapsed / Start timestamp
   │     • State Machine Actions:
   │         - INACTIVE / STOPPED → [Start Session] (Opens name/notes dialog)
   │         - ACTIVE → [Pause Session], [Stop Session]
   │         - PAUSED → [Resume Session], [Stop Session]
   │
   ├── [Current Application Context Card]
   │     • Latest interaction app: Human-readable label (e.g. "Notes Application")
   │     • Target package: e.g. "com.example.notes"
   │     • Derived reactively from observeLatestEvent() without memory loading
   │
   ├── [Live Event Counters Grid] (Architecture.md §55, AC-04)
   │     • Total Events (SQLite COUNT(*))
   │     • Keyboard Keystrokes (source == 'KEYBOARD')
   │     • Clicks (eventType == 'VIEW_CLICKED')
   │     • Focus Changes (eventType == 'VIEW_FOCUSED')
   │     • Window Changes (eventType IN ('WINDOW_STATE_CHANGED', 'WINDOWS_CHANGED'))
   │     • Scrolls (eventType == 'VIEW_SCROLLED')
   │     • Computed entirely in SQLite via EventCountsTuple (Zero Entity RAM allocation)
   │
   └── [Quick Navigation Row]
         • [Timeline] → Navigates to Event Timeline Screen
         • [Setup Wizard] → Navigates to Setup / Permissions Verification
         • [Settings] → Navigates to App Configuration Screen
```

---

# 12.1. Event Timeline & Filtering Flow

```text
EventsScreen Launched (Screen.Events)
   ↓
TimelineViewModel initializes StateFlow (SharingStarted.Eagerly)
   ├── observeEventsPaged(filter, limit = 50, offset = page * 50)
   ├── observeFilteredCount(filter)
   ├── observeDistinctPackageNames()
   ├── observeAllSessions()
   └── observeActiveSession()
   ↓
TimelineUiState Emits
   │
   ├── [Top App Bar]
   │     • Total matching events count
   │     • Active filter badge indicator (⚙●)
   │     • Instant "Reset Filters" action
   │
   ├── [Search Bar] (SRS.md §50)
   │     • Text search matching application name, package, or event data
   │     • Immediate reactive SQLite LIKE query execution
   │     • Automatically resets pagination to Page 0
   │
   ├── [Event Type Filter Chips Row]
   │     • All Types / ⌨ Typing / 👆 Clicks / 👁 Focus / ↕ Scrolls / 🪟 Windows / 🔒 Sensitive
   │     • Single-tap filtering resets page to 0
   │
   ├── [Pagination Control Strip] (Architecture.md §56, AC-23)
   │     • Record range indicator (e.g. "1–50 of 214")
   │     • Page navigation buttons: [‹ Prev] [Page 1 of 5] [Next ›]
   │     • Bounds-safe: disables buttons at page boundaries
   │
   ├── [Reverse-Chronological Event Stream] (Newest First)
   │     • LazyColumn bounded to 50 items (zero heap exhaustion risk)
   │     • Event Card:
   │         - Glyph: ⌨ / 👆 / 👁 / ↕ / 🪟 / 🔒
   │         - Header: App Name ("Notes Application") + Time (18:42:15.342)
   │         - Subtitle: Package Name (com.example.notes)
   │         - Pills: Event Type + Source (KEYBOARD / ACCESSIBILITY)
   │         - Body: Text snippet if normal, or "🔒 Content not stored" if sensitive
   │
   ├── [Event Detail Dialog]
   │     • Tapping any card opens comprehensive interaction metadata
   │     • ID, Session ID, Timestamp, App, Package, Source, Type, Classification
   │     • Sensitive field notice explaining zero credential persistence
   │     • Formatted recorded data and metadata preview
   │
   └── [Multi-Dimensional Filter Dialog] (SRS.md §49, AC-24)
         • Filter by Source: All / Keyboard / Accessibility
         • Filter by Package: Distinct recorded application packages
         • Filter by Session: All Sessions / Specific monitoring session
         • Filter by Classification: All / Normal / Passwords
```

---

# 12.2. Database-Backed Search & Auto-Suggestion Flow

```text
User Focuses Search Field in EventsScreen
   ↓
User Types Keystroke (e.g. "note")
   ↓
TimelineViewModel.onSearchQueryInputChanged("note")
   ├── Updates raw searchInputFlow immediately (UI displays typed character without latency)
   └── Resets pagination pageIndex to 0
   ↓
Reactive Search Pipeline:
   ├── Branch A: Real-Time Auto-Suggestions
   │     observeSearchSuggestions("note", limit = 10)
   │     ├── Query Room: observeMatchingApplications("note")
   │     ├── Query Room: observeMatchingPackages("note")
   │     ├── Map matching EventTypes (e.g. "KEYBOARD", "VIEW_CLICKED")
   │     └── Emit searchSuggestions: List<SearchSuggestion> to UI
   │
   └── Branch B: Debounced Database Query
         debounce { if (query.isBlank()) 0L else 300L }
         ↓
         searchQueryFlow emits debounced "note"
         ↓
         Triggers observeEventsFilteredPaged & observeFilteredCount
         ├── SQLite Index index_interaction_events_applicationName accelerates search
         ├── Matches across:
         │     - applicationName LIKE '%note%'
         │     - packageName LIKE '%note%'
         │     - eventType LIKE '%note%'
         │     - sessionId LIKE '%note%'
         │     - eventData IS NOT NULL AND eventData LIKE '%note%'
         ├── Sensitive events have eventData = null (never matched by credential strings)
         └── Emits filtered List<InteractionEvent> and totalCount to UI

User Interaction Options:
   ├── Taps Suggestion Chip (e.g. "App: Notes")
   │     ↓
   │     TimelineViewModel.applySuggestion(suggestion)
   │     ↓
   │     Immediately updates searchInputFlow & searchQueryFlow (no debounce delay)
   │     Suggestions list cleared, filtered timeline refreshed instantly
   │
   └── Taps Clear Button (✕)
         ↓
         TimelineViewModel.clearSearch()
         ↓
         Query immediately cleared (0ms debounce), full event list restored
```

---

# 13. Export Flow

```text
User selects "Export Data" from Dashboard or Navigation
   ↓
ExportScreen displays privacy disclosure & scope selector
   ↓
User selects scope (All Sessions or Specific Session)
   ↓
User taps "Select Destination & Export CSV"
   ↓
Storage Access Framework (SAF) ActivityResultContracts.CreateDocument("text/csv")
   ↓
User chooses local destination file URI
   ↓
ContentResolver.openOutputStream(uri)
   ↓
ExportSessionUseCase (IO Dispatcher)
   ├── CsvEventExporter.writeHeader(outputStream)
   └── Loop: Paged batch query (CHUNK_SIZE = 250)
         ↓
       ExportValidator (Sanitize & redact passwords/PINs/OTPs/PANs)
         ↓
       CsvEventExporter.writeEvents(outputStream, sanitizedBatch)
         ↓
       Progress callback updates UI (exported / total)
   ↓
OutputStream.flush() & close()
   ↓
ExportResult.Success dialog displayed with record count & bytes
```

---

# 14. Error/Failure Flow

```text
Database failure:
   → Keyboard continues functioning
   → Event persistence fails silently
   → Dashboard shows error state

Encryption key unavailable:
   → Database cannot open
   → Create new database + new key
   → Old data is lost (documented behavior)
   → Keyboard continues functioning

Event pipeline failure:
   → InputConnection delivery unaffected
   → Monitoring event dropped
   → Keyboard continues functioning

Privacy filter failure:
   → Content-bearing event NOT persisted (fail closed)
   → Keyboard continues functioning
```

---

# 15. Interaction Statistics Flow

```text
Room Aggregate Queries (SQLite Database)
  ├── observeStatisticsSummary(sessionId)  ──>  StatisticsSummaryTuple
  ├── observeAppUsageStats(sessionId)       ──>  List<AppUsageTuple>
  └── observeHourlyActivity(sessionId)      ──>  List<HourlyActivityTuple>
                                                   │
                                                   ▼
                                  EventRepositoryImpl (IO Dispatcher)
                                    │ • 24-Hour Circadian Normalization (0..23)
                                    │ • Application Usage Percentages
                                    │ • Interaction Velocity (events/min)
                                    │ • Defense-in-Depth Privacy Preservation
                                    ▼
                                  InteractionStatistics Domain Model
                                                   │
                                                   ▼
                                  StatisticsViewModel (StateFlow)
                                    │ • Session selection (All Sessions vs specific)
                                    │ • Dropdown menu expansion state
                                    ▼
                                  StatisticsUiState
                                                   │
                                                   ▼
                                   StatisticsScreen (Jetpack Compose)
          ┌─────────────────────────────────────────────────────────────────┐
          │ • Summary Metric Card: Total events, velocity, active duration   │
          │ • Category Breakdown Pills: Keyboard, Accessibility, Clicks, etc│
          │ • Circadian 24-Hour Activity Chart: Normalized vertical bars    │
          │ • Application Breakdown List: App name, count, % progress bar   │
          │ • Session Filter Dropdown: Global vs scoped session inspection  │
          │ • Empty State Screen: Informational placeholder when no events   │
          └─────────────────────────────────────────────────────────────────┘
```

---

---

# 16. Data Management Flow

```text
User Navigates to Settings & Data Management
   ↓
SettingsScreen displays:
   ├── Local Storage Overview (Total sessions, Total events, AES-256 SQLCipher status)
   ├── Logging Preferences (Keyboard & Accessibility capture switches)
   ├── Recorded Sessions Inventory (Session metadata, status pills, delete buttons)
   └── Danger Zone (Irreversible bulk deletion actions)

Action A: Delete Individual Session
   User taps Delete (trash icon) on session item
      ↓
   Two-Step Confirmation: Material 3 AlertDialog ("Delete Session?")
      ↓ User confirms
   SettingsViewModel.confirmDeleteSession()
      ↓
   DeleteSessionUseCase(sessionId)
      ├── EventRepository.deleteEventsForSession(sessionId) [Explicit Cascading Cleanup]
      ├── SessionRepository.deleteSession(sessionId) [Room Delete]
      └── SessionManager.onSessionDeleted(sessionId) [In-memory reset if active]
      ↓
   Reactive Room flows update counts and session list automatically
   Snackbar confirms: "Session \"[name]\" and its events were deleted."

Action B: Delete All Sessions
   User taps "Delete All Sessions" in Danger Zone
      ↓
   Two-Step Confirmation: Material 3 AlertDialog ("Delete All Sessions?")
      ↓ User confirms
   SettingsViewModel.confirmDeleteAllSessions()
      ↓
   DeleteAllSessionsUseCase()
      ├── EventRepository.deleteAllEvents()
      ├── SessionRepository.deleteAllSessions()
      └── SessionManager.resetSessionState()
      ↓
   UI state reflects 0 sessions and 0 events
   Snackbar confirms: "All sessions and recorded events were permanently deleted."

Action C: Reset All Application Data
   User taps "Reset All Application Data" in Danger Zone
      ↓
   Two-Step Confirmation: Material 3 AlertDialog ("Reset All Application Data?")
      ↓ User confirms
   SettingsViewModel.confirmDeleteAllData()
      ↓
   DeleteAllApplicationDataUseCase()
      ├── EventRepository.deleteAllEvents()
      ├── SessionRepository.deleteAllSessions()
      ├── SessionManager.resetSessionState()
      └── PreferencesRepository.clearAllPreferences()
      ↓
   All data wiped; DataStore preferences reset to factory state
   Snackbar confirms: "All application data and preferences have been reset."
```

---

# 17. Controlled Application Use Test Screen Flow

```text
User navigates to TestBenchScreen (via Dashboard "Test Bench" button)
   │
   ├── Focus Telemetry Flow
   │      User taps into an input field:
   │      Modifier.onFocusChanged { if (isFocused) onFieldFocused(fieldType) }
   │         ↓
   │      TestBenchViewModel.onFieldFocused(fieldType)
   │         ├── Updates activeField StateFlow
   │         ├── Updates detectedClassification StateFlow
   │         └── Computes isSensitiveMode = fieldType.isSensitive
   │         ↓
   │      TestBenchScreen renders Live Telemetry Card:
   │         ├── If SENSITIVE (Password / PIN):
   │         │     - Deep red alert container
   │         │     - High-contrast badge: [ 🔒 SENSITIVE_PASSWORD ] or [ 🔒 SENSITIVE_PIN ]
   │         │     - Policy: Key preview popups suppressed, raw text never persisted
   │         └── If NON-SENSITIVE (Normal, Email, URL, Number, Multiline):
   │               - Primary/Secondary container
   │               - High-contrast badge: [ ✓ NORMAL_TEXT ] / [ ✓ EMAIL_ADDRESS ]
   │               - Policy: Synthetic text capture permitted when session active
   │
   ├── Quick Session Control Flow
   │      User taps [Start Test] / [Stop]:
   │         ↓
   │      TestBenchViewModel.startQuickTestSession() / stopQuickTestSession()
   │         ├── Calls StartMonitoringSessionUseCase / StopMonitoringSessionUseCase
   │         └── Updates session status pill & reactive counters in real-time
   │
   ├── 1-Tap Sample Population Flow
   │      User taps [Populate Samples]:
   │         ↓
   │      TestBenchViewModel.populateSyntheticTestData()
   │         └── Fills all 7 fields with standardized ground-truth values
   │
   └── Invariant Submission & Verification Flow (SRS.md §66 [SUBMIT])
          User taps [Submit Test Verification]:
             ↓
          TestBenchViewModel.submitTestInputs()
             ├── Evaluates filled fields against privacy invariants:
             │     - AC-29: Permitted synthetic non-sensitive text (Normal, Email, URL, Number, Multiline)
             │     - AC-30: Password and PIN fields classified as sensitive with credential suppression
             │     - AC-31: Zero credential persistence guaranteed across Room SQLCipher and export pipelines
             │     - AC-32: Keyboard typing and IME input methods remain operational regardless of session status
             └── Sets TestBenchSubmissionResult
             ↓
          TestBenchScreen presents SubmissionResultDialog:
             ├── Displays timestamp and counts of verified fields
             └── Confirms 100% compliance across all 4 invariants
```

---

# 18. Performance Optimization Flow

```text
Keyboard Keystroke Hot Path (DEVELOPMENT_GUIDELINES.md §17)
   User touches a keycap on Gboard layout:
      ↓
   CustomKeyboardService.onAction(KeyAction.Character("a"))
      ├── 1. KeyProcessor.processKey() [Executed FIRST on UI thread]
      │        ├── Synchronous InputConnection.commitText() (< 1ms, 0 allocations)
      │        └── Immediately rendered to target app (< 2ms hardware tap budget)
      │
      └── 2. emitKeyEventCandidate() [Asynchronous Auxiliary Path]
               └── ImeEventPublisherImpl.publish()
                     └── Dispatches to background coroutine scope
                           └── EventProcessorImpl.process() (sanitization, context resolution, Room persistence)

Accessibility Event Filter & Throttle Path (DEVELOPMENT_GUIDELINES.md §16, §31)
   Android OS invokes onAccessibilityEvent(event):
      ↓
   AppAccessibilityService.onAccessibilityEvent(event)
      ├── Snapshot event attributes into SimpleAccessibilityEventInfo (immediate Android recycler decoupling)
      │
      ├── AccessibilityEventFilterImpl.shouldProcess(info)
      │     ├── Checks packageName != ownPackage (100% self-monitoring loop suppression)
      │     └── If TYPE_VIEW_SCROLLED:
      │           ├── Checks (now - lastScrollTimeMs) >= 250ms
      │           └── Drops high-frequency burst events (> 95% CPU savings during rapid scrolls)
      │
      └── Background Dispatch: AccessibilityEventPublisherImpl.publish() (non-blocking SupervisorJob)

In-Memory Context & Package Resolution Path (DEVELOPMENT_GUIDELINES.md §30)
   EventProcessor requests context resolution for packageName:
      ↓
   ContextResolverImpl.resolve(packageName)
      ├── Fast-Path: labelCache[packageName] hit -> Returns instantly in < 0.1ms (no IPC/dispatcher jump)
      │
      └── Slow-Path (Cache Miss):
            ├── Dispatches to withContext(dispatchers.io)
            ├── Resolves label via PackageManager
            ├── Enforces cache bound: if (labelCache.size >= MAX_CACHE_ENTRIES) evict oldest entry
            └── Caches label for all subsequent keystrokes in this application
```

---

# 19. Large Database Scalability & Stress Testing Flow

```text
High-Volume Batch Insertion & Scalability Flow (IMPLEMENTATION_PLAN.md §61)
   Monitoring Engine / Test Harness emits synthetic batches (1k, 10k, 50k, 100k events):
      ↓
   InteractionEventDao.insertEvents(batch)
      ├── Sequential primary key ID allocation (AtomicLong / auto-increment)
      ├── In-memory B-Tree index insertion across 6 indexes (sub-linear $O(\log N)$)
      └── Linear scaling verification: > 100,000 events/sec throughput with 0 memory stalls

Bounded Timeline Pagination Flow (100,000 Records)
   User scrolls Timeline or navigates to page:
      ↓
   EventRepositoryImpl.observeEventsPaged(filter, limit = 50, offset = N)
      ├── SQLite seeks composite index (sessionId, timestamp) directly
      ├── Traverses B-tree backwards (ORDER BY timestamp DESC)
      ├── Scans ONLY requested 50 rows (Zero full-table scan or memory residency)
      └── Emits 50 items to UI in 2-3ms per page

Chunked Streaming Export Flow (10,000+ Records)
   User initiates CSV Export:
      ↓
   ExportSessionUseCase.invoke(outputStream, sessionId)
      ├── 1. Writes RFC-4180 CSV header directly to output stream
      ├── 2. Determines totalCount via indexed COUNT(*)
      └── 3. While (offset < totalCount):
               ├── Fetches bounded chunk of 250 records
               ├── Passes through ExportValidatorImpl (redacts passwords/PINs to [REDACTED])
               ├── Serializes directly to OutputStreamWriter (StandardCharsets.UTF_8)
               ├── Flushes bytes to destination without expanding heap ($O(1)$ RAM)
               └── Reports progress onProgress(exported, total)

Cascading Deletion Flow (50,000+ Records)
   User deletes monitoring session:
      ↓
   SessionRepositoryImpl.deleteSession(sessionId)
      ├── SQLite PRAGMA foreign_keys = ON invokes ON DELETE CASCADE
      ├── Atomically purges parent session and all 50,000 child records in 24ms
      └── Control sessions and unrelated events remain 100% intact with 0 orphan records
```

---

# 20. Android Version Testing & Android 16 Runtime Flow

```text
Android 16 (Baklava API 36) Application Startup & Dynamic JNI Linking Flow
   User launches app or Android boots:
      ↓
   MonitoringApplication.onCreate()
      ├── 1. System.loadLibrary("sqlcipher") [Explicit JNI Binding]
      │        ├── Loads libsqlcipher.so from APK /data/app native library directory
      │        ├── Binds native method symbols (SQLiteConnection.nativeOpen, etc.)
      │        └── Prevents UnsatisfiedLinkError during subsequent Room background initialization
      │
      └── 2. DefaultAppContainer(applicationContext) initialization
               ├── KeystoreKeyManager retrieves/creates database AES-256 passphrase
               ├── SupportOpenHelperFactory configures SQLiteCipher open helper
               └── CoroutineScope restores active monitoring session if active before reboot

Android 16 IME Input View Evaluation & Hard-Keyboard Coexistence Flow
   Target application gains focus on EditText:
      ↓
   Android OS invokes CustomKeyboardService:
      ├── onStartInput(attribute, restarting) -> Updates editor context & classification
      │
      ├── onEvaluateInputViewShown() -> Unconditionally returns TRUE
      │     ├── Overrides platform default (which hides virtual IME on emulators/hardware keyboards)
      │     └── Guarantees virtual keyboard view renders across all emulator and device environments
      │
      ├── onCreateInputView() / onStartInputView() -> Renders KeyboardView
      │
      └── User taps keycap -> KeyProcessor delivers character to InputConnection synchronously
```

---

# Current Implementation State

```text
Phase 0: Project initialization (COMPLETE)
Phase 1: Android foundation, navigation, edge-to-edge Compose UI (COMPLETE)
Phase 2: Data foundation, Room encrypted database, KeyStore AES-GCM, Repositories, defense-in-depth safety validation (COMPLETE)
Phase 3: Monitoring sessions, SessionManager state machine (INACTIVE <-> ACTIVE <-> PAUSED -> STOPPED), SessionGate event blocking, lifecycle use cases, Dashboard UI controls & badges (COMPLETE)
Phase 4: Custom keyboard foundation, CustomKeyboardService, QWERTY layout, InputConnection text delivery, shift state machine, audio/haptic feedback (COMPLETE)
Phase 5: Complete keyboard layouts (Symbols, Numbers, Phone, Email, URI, Caps lock, Editor actions) (COMPLETE)
Phase 6: Editor Analysis and Privacy (EditorInfoAnalyzer, SensitiveInputDetector, PrivacyFilter) (COMPLETE)
Phase 7: IME Event Pipeline (ImeEventPublisher, RawInteractionEvent emission, EventProcessor, ContextResolver, failure tolerance) (COMPLETE)
Phase 8: AccessibilityService (AppAccessibilityService, AccessibilityEventFilter, AccessibilityEventMapper, AccessibilityEventPublisher) (COMPLETE)
Phase 9: Unified Event Processing & Integration (Concurrent event interleaving, unified session gating, multi-pipeline privacy invariants, failure tolerance integration) (COMPLETE)
Phase 10: First-Run Setup & Onboarding UI (COMPLETE)
Phase 11: Dashboard Architecture & Aggregate Event Metrics (COMPLETE)
Phase 12: Event Timeline, Paged Observation & Multi-Dimensional Filtering (COMPLETE)
Phase 13: Database-Backed Search, Index Optimization & Debounced Query Engine (COMPLETE)
Phase 14: Interaction Statistics Engine & UI (COMPLETE)
Phase 15: Export Engine (COMPLETE)
Phase 16: Data Management (COMPLETE)
Phase 17: Controlled Application Use Test Screen (COMPLETE)
Phase 18: Performance Optimization (COMPLETE)
Phase 19: Large Database Testing (COMPLETE)
Phase 20: Android Version Testing (COMPLETE)
Phase 21: Release Optimization & Validation (NEXT)
```





