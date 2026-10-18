# ANDROID INTERACTION & INPUT MONITORING APPLICATION

# Architecture Specification

**Document:** `Architecture.md`  
**Version:** 1.0  
**Platform:** Android  
**Language:** Kotlin  
**UI:** Jetpack Compose  
**Architecture Pattern:** Lightweight layered architecture inspired by Clean Architecture + MVVM + Repository Pattern
**Persistence:** Encrypted Persistence Layer + Room  
**Keyboard:** `InputMethodService`  
**Interaction Monitoring:** `AccessibilityService`  
**Concurrency:** Kotlin Coroutines + Flow  
**Dependency Injection:** Hilt  
**Configuration:** DataStore  

Before `tech.md` exists, this stack is a recommended default rather than an authoritative implementation record. Phase 0 shall evaluate it against correctness, maintainability, APK size, resource usage, platform support, dependency overhead, and testing requirements. `tech.md` becomes authoritative for the actual stack once created.

---

# 1. Purpose

This document defines the software architecture for the Android Interaction & Input Monitoring Application specified in `SRS.md`.

The system combines:

- a user-enabled Android custom keyboard;
- an Android AccessibilityService;
- monitoring-session management;
- accessibility interaction metadata;
- non-sensitive keyboard input events;
- privacy/sensitive-input filtering;
- application-context correlation;
- encrypted local Room persistence;
- application visualization and statistics;
- local export.

The architecture prioritizes:

1. Android platform compliance.
2. Explicit user configuration.
3. Separation between keyboard operation and event logging.
4. Sensitive-input suppression.
5. Offline operation.
6. Reliability.
7. Testability.
8. Modular development.

---

# 2. Architectural Principles

The implementation shall follow these principles.

The architecture is a lightweight layered architecture inspired by Clean Architecture, combined with MVVM and the Repository Pattern. Its logical layers are Presentation, Domain, Data, and Platform.

Architectural separation shall not justify unnecessary abstraction. Components and interfaces should exist only where they provide meaningful separation, testability, platform isolation, or maintainability. The design must remain suitable for lower-configuration Android devices and a relatively small APK.

## 2.1 Keyboard Independence

The keyboard must remain functional independently of the application subsystem.

```text
Keyboard UI
    │
    ▼
Key Processor
    │
    ▼
InputConnection
    │
    ▼
Target Application

    │
    └──── best-effort interaction event ────► Event Pipeline
```

Failure of:

- Room;
- event processing;
- monitoring sessions;
- statistics;
- dashboard;
- export;

must not prevent ordinary keyboard operation.

---

# 3. Input Safety

Potentially content-bearing events shall not be written directly to storage.

They must pass through:

```text
Raw Event
    │
    ▼
Classification
    │
    ▼
Privacy Filter
    │
 ┌──┴───────────────┐
 │                  │
 ▼                  ▼
SAFE             SENSITIVE
 │                  │
 ▼                  ▼
Allowed       Remove Content
 │                  │
 └────────┬─────────┘
          ▼
     Persistence
```

Password/PIN/credential contents shall never intentionally reach persistent storage.

---

# 4. High-Level Architecture

```text
┌──────────────────────────────────────────────────────────┐
│                    ANDROID DEVICE                        │
│                                                          │
│  ┌────────────────────┐     ┌─────────────────────────┐  │
│  │ Accessibility      │     │ Custom Keyboard       │  │
│  │ Service            │     │ InputMethodService      │  │
│  └─────────┬──────────┘     └────────────┬────────────┘  │
│            │                              │               │
│            │ AccessibilityEvent           │ IME Event     │
│            │                              │               │
│            └──────────────┬───────────────┘               │
│                           ▼                               │
│                   ┌───────────────┐                       │
│                   │RawInteractionEvent│                      │
│                   └───────┬───────┘                       │
│                           ▼                               │
│                   ┌───────────────┐                       │
│                   │ EventProcessor│                       │
│                   └───────┬───────┘                       │
│                           ▼                               │
│                   ┌───────────────┐                       │
│                   │  Session Gate │                       │
│                   └───────┬───────┘                       │
│                           ▼                               │
│                   ┌───────────────┐                       │
│                   │Classification │                       │
│                   │/Sensitive...  │                       │
│                   └───────┬───────┘                       │
│                           ▼                               │
│                   ┌───────────────┐                       │
│                   │ PrivacyFilter │                       │
│                   └───────┬───────┘                       │
│                           ▼                               │
│                   ┌───────────────┐                       │
│                   │ContextResolver│                       │
│                   └───────┬───────┘                       │
│                           ▼                               │
│                   ┌───────────────┐                       │
│                   │Repository     │                       │
│                   │Safety Validate │                       │
│                   └───────┬───────┘                       │
│                           ▼                               │
│                   ┌───────────────┐                       │
│                   │Encrypted      │                       │
│                   │Persistence    │                       │
│                   │Layer           │                       │
│                   └───────┬───────┘                       │
│                           ▼                               │
│                   ┌───────────────┐                       │
│                   │ Room Database │                       │
│                   └───────┬───────┘                       │
│                           │                               │
│              ┌────────────┼────────────┐                  │
│              ▼            ▼            ▼                  │
│          Timeline     Statistics     Export               │
│              │            │            │                  │
│              └────────────┼────────────┘                  │
│                           ▼                               │
│                     Dashboard                            │
└──────────────────────────────────────────────────────────┘
```

---

# 5. Architecture Layers

The system shall use four principal layers:

```text
┌─────────────────────────────┐
│      PRESENTATION LAYER     │
│ Compose / ViewModels        │
├─────────────────────────────┤
│        DOMAIN LAYER         │
│ Sessions / Events / Rules   │
├─────────────────────────────┤
│         DATA LAYER          │
│ Repository / Room/DataStore │
├─────────────────────────────┤
│      PLATFORM LAYER         │
│ IME / Accessibility/Android │
└─────────────────────────────┘
```

Dependencies should generally point inward toward abstractions/domain logic.

---

# 6. Presentation Layer

Responsible for:

- onboarding;
- setup;
- dashboard;
- monitoring sessions;
- event viewer;
- filters;
- statistics;
- settings;
- export;
- deletion.

Recommended package:

```text
ui/
├── onboarding/
├── setup/
├── dashboard/
├── sessions/
├── events/
├── statistics/
├── settings/
└── components/
```

---

# 7. Domain Layer

The domain layer contains application rules independent of Compose.

Recommended components:

```text
domain/
├── model/
│   ├── InteractionEvent.kt
│   ├── MonitoringSession.kt
│   ├── EventType.kt
│   ├── EventSource.kt
│   ├── InputClassification.kt
│   └── SetupState.kt
│
├── usecase/
│   ├── StartMonitoringSessionUseCase.kt
│   ├── StopMonitoringSessionUseCase.kt
│   ├── PauseMonitoringSessionUseCase.kt
│   ├── ProcessInteractionEventUseCase.kt
│   ├── GetInteractionEventsUseCase.kt
│   ├── DeleteSessionUseCase.kt
│   └── ExportSessionUseCase.kt
│
├── privacy/
│   ├── PrivacyFilter.kt
│   └── SensitiveInputClassifier.kt
└── session/
    └── SessionManager.kt
```

---

# 8. Data Layer

Responsible for:

- Room;
- repositories;
- entity/domain mapping;
- DataStore;
- export;
- persistence queries.

```text
data/
├── database/
├── dao/
├── entity/
├── mapper/
├── repository/
├── preferences/
└── export/
```

---

# 9. Platform Layer

Responsible for Android-specific integration.

```text
platform/
├── accessibility/
├── ime/
├── permissions/
├── packages/
└── system/
```

This layer shall isolate Android APIs from the majority of business logic.

---

# 10. Recommended Project Structure

```text
app/
└── src/main/
    ├── java/com/example/application/
    │
    ├── MonitoringApplication.kt
    ├── MainActivity.kt
    │
    ├── domain/
    │   ├── model/
    │   ├── usecase/
    │   ├── privacy/
    │   └── session/
    │
    ├── data/
    │   ├── database/
    │   ├── dao/
    │   ├── entity/
    │   ├── mapper/
    │   ├── repository/
    │   ├── preferences/
    │   └── export/
    │
    ├── platform/
    │   ├── accessibility/
    │   ├── ime/
    │   ├── permissions/
    │   ├── packages/
    │   └── system/
    │
    ├── di/
    │
    └── ui/
        ├── navigation/
        ├── onboarding/
        ├── setup/
        ├── dashboard/
        ├── sessions/
        ├── events/
        ├── statistics/
        ├── settings/
        └── components/
```

`session/` is not a root package. Session business rules and
`SessionManager` belong under `domain/session/`; persistence support belongs
under `data/`; and session screens belong under `ui/sessions/`. `di/` is the
only optional additional root package and is reserved for dependency-injection
modules.

Resources:

```text
res/
├── drawable/
├── mipmap/
├── values/
└── xml/
    ├── accessibility_service_config.xml
    └── method.xml
```

---

# 11. Application Component

`MonitoringApplication.kt`

Responsibilities:

- initialize Hilt;
- initialize application-wide dependencies;
- initialize database;
- initialize repositories;
- initialize configuration providers.

It shall NOT start covert/background monitoring.

---

# 12. MainActivity

`MainActivity.kt`

Responsibilities:

```text
MainActivity
     │
     ▼
Navigation Host
     │
 ┌───┼─────────────┐
 ▼   ▼             ▼
Setup Dashboard   Other Screens
```

It shall host the Compose UI.

---

# 13. Navigation Architecture

Recommended routes:

```text
WELCOME
   │
   ▼
SETUP
   │
   ▼
SETUP_VERIFY
   │
   ▼
DASHBOARD
   │
   ├── SESSION
   ├── EVENTS
   ├── STATISTICS
   ├── EXPORT
   └── SETTINGS
```

---

# 14. SetupStateManager

`SetupStateManager` is responsible for determining system readiness.

Conceptual interface:

```kotlin
interface SetupStateManager {

    fun observeSetupState(): Flow<SetupState>

    suspend fun refresh()

}
```

`SetupState` conceptually contains:

```text
runtimePermissionsReady
accessibilityEnabled
imeInstalled
imeEnabled
imeSelected
databaseReady
monitoringSessionActive
```

---

# 15. Setup Refresh Lifecycle

The state shall be refreshed when the main application resumes.

```text
MainActivity
     │
     ▼
ON_RESUME
     │
     ▼
SetupStateManager.refresh()
     │
     ├── Check permissions
     ├── Check AccessibilityService
     ├── Check IME enablement
     ├── Check selected IME
     └── Check session
             │
             ▼
        StateFlow update
             │
             ▼
           Compose
```

---

# 16. Android Permission Architecture

Normal runtime permissions shall use Android's supported permission APIs.

Architecture:

```text
Compose Screen
      │
      ▼
Permission Coordinator
      │
      ▼
Android Permission API
      │
      ▼
User Decision
      │
      ▼
SetupStateManager
```

Permission denial shall be represented as application state rather than treated as an exception.

---

# 17. Accessibility Configuration

The application shall provide navigation to the appropriate Android accessibility configuration screen.

The actual enablement remains controlled by Android and the user.

```text
Setup UI
   │
   ▼
Open Android Settings
   │
   ▼
User enables service
   │
   ▼
Return to app
   │
   ▼
SetupStateManager.refresh()
```

---

# 18. AccessibilityService Architecture

Primary class:

```text
AppAccessibilityService
```

Supporting components:

```text
accessibility/
├── AppAccessibilityService.kt
├── AccessibilityEventMapper.kt
├── AccessibilityEventFilter.kt
└── AccessibilityConfig.kt
```

---

# 19. Accessibility Processing Pipeline

```text
Android
   │
   ▼
onAccessibilityEvent()
   │
   ▼
AccessibilityEventFilter
   │
   ├── Ignore
   │
   └── Accept
          │
          ▼
AccessibilityEventMapper
          │
          ▼
RawInteractionEvent
          │
          ▼
EventProcessor
```

`onAccessibilityEvent()` shall perform minimal work.

Expensive database operations must not execute directly inside the callback.

---

# 20. Accessibility Event Filter

The filter shall determine whether an event category is enabled.

Conceptual interface:

```kotlin
interface AccessibilityEventFilter {

    fun shouldProcess(
        event: AccessibilityEvent
    ): Boolean

}
```

Configurable categories may include:

```text
VIEW_CLICKED
VIEW_FOCUSED
VIEW_SCROLLED
WINDOW_STATE_CHANGED
WINDOWS_CHANGED
VIEW_SELECTED
```

---

# 21. Accessibility Event Mapper

The mapper converts Android-specific objects into application-owned models.

```text
AccessibilityEvent
        │
        ▼
AccessibilityEventMapper
        │
        ▼
RawInteractionEvent
```

This prevents Room/domain code from depending directly on `AccessibilityEvent`.

---

# 22. Accessibility Content Minimization

The accessibility component shall primarily collect interaction metadata.

It should avoid unnecessary extraction of:

- visible message content;
- editable text;
- passwords;
- credentials;
- unrelated UI content.

The architecture therefore favors:

```text
Event Type
Timestamp
Package
Window information
Interaction category
```

over arbitrary node text extraction.

---

# 23. Custom Keyboard Architecture

Primary class:

```text
CustomKeyboardService : InputMethodService
```

Supporting components:

```text
ime/
├── CustomKeyboardService.kt
├── KeyboardController.kt
├── KeyboardState.kt
├── KeyboardLayout.kt
├── KeyProcessor.kt
├── EditorInfoAnalyzer.kt
├── SensitiveInputDetector.kt
└── ImeEventPublisher.kt
```

---

# 24. IME Lifecycle

Conceptually:

```text
Android
   │
   ▼
CustomKeyboardService
   │
   ├── onCreate()
   │
   ├── onCreateInputView()
   │
   ├── onStartInput()
   │
   ├── onStartInputView()
   │
   ├── onFinishInputView()
   │
   └── onFinishInput()
```

The exact lifecycle implementation shall follow the Android SDK behavior for the target API level.

---

# 25. IME Initialization

When Android activates the keyboard:

```text
onStartInput(EditorInfo)
        │
        ▼
EditorInfoAnalyzer
        │
        ▼
InputClassification
        │
        ▼
KeyboardController
        │
        ▼
KeyboardState
        │
        ▼
Render appropriate layout
```

---

# 26. EditorInfoAnalyzer

Purpose:

Determine how the keyboard should behave for the active editor.

Conceptual result:

```kotlin
data class EditorContext(
    val fieldCategory: FieldCategory,
    val isSensitive: Boolean,
    val isMultiline: Boolean,
    val imeAction: ImeAction,
    val packageName: String?
)
```

The classifier should use Android-provided editor metadata rather than trying to infer secrets from typed content.

---

# 27. Field Categories

Possible categories:

```text
TEXT
EMAIL
URL
NUMBER
PHONE
MULTILINE
PASSWORD
NUMERIC_PASSWORD
OTHER_SENSITIVE
UNKNOWN
```

`UNKNOWN` may be treated conservatively where persistent content is concerned.

---

# 28. SensitiveInputDetector

Conceptual interface:

```kotlin
interface SensitiveInputDetector {

    fun classify(
        editorContext: EditorContext
    ): InputClassification

}
```

Possible result:

```text
NORMAL
SENSITIVE_PASSWORD
SENSITIVE_PIN
SENSITIVE_CREDENTIAL
UNKNOWN
```

---

# 29. Keyboard Controller

`KeyboardController` controls keyboard state.

Responsibilities:

- Shift;
- Caps Lock;
- alphabetic layout;
- symbols;
- numbers;
- action key;
- layout transitions.

Example:

```text
ALPHABETIC
    │
    ├── SHIFT
    │
    └── 123
          │
          ▼
       SYMBOLS
          │
          └── ABC
```

---

# 30. KeyboardState

Suggested conceptual state:

```kotlin
data class KeyboardState(
    val layout: KeyboardLayout,
    val shiftEnabled: Boolean,
    val capsLock: Boolean,
    val fieldCategory: FieldCategory,
    val applicationLoggingAllowed: Boolean
)
```

---

# 31. KeyProcessor

The key processor converts UI actions into operations against Android's active `InputConnection`.

Conceptually:

```text
Key Press
   │
   ▼
KeyProcessor
   │
   ├── Character
   ├── Delete
   ├── Space
   ├── Enter
   ├── Shift
   └── Layout switch
```

Input delivery to the target application remains the primary path.

---

# 32. Critical IME Separation

Do NOT architect typing as:

```text
Key
 ↓
Database
 ↓
Target Application
```

The required architecture is:

```text
             ┌──► Event Pipeline
             │
KeyProcessor ┤
             │
             └──► InputConnection ──► Target App
```

The Event Pipeline is secondary and must never be required for text delivery.

---

# 33. IME Event Publisher

`ImeEventPublisher` receives sanitized Interaction Event Candidates.

Conceptual interface:

```kotlin
interface ImeEventPublisher {

    fun publish(event: RawInteractionEvent)

}
```

Publishing should be asynchronous and failure-tolerant.

---

# 34. IME Event Decision Flow

```text
Key/Text operation
       │
       ▼
Deliver to InputConnection
       │
       └───────────────┐
                       ▼
              Session active?
                  │
             ┌────┴────┐
             │         │
            NO        YES
             │         │
           Stop        ▼
                 Field classification
                       │
                ┌──────┴──────┐
                │             │
             NORMAL       SENSITIVE
                │             │
                ▼             ▼
          permitted interaction    metadata only
              event
                │             │
                └──────┬──────┘
                       ▼
                 EventProcessor
```

---

# 35. Interaction Event Model

Domain model:

```kotlin
data class InteractionEvent(
    val id: Long,
    val sessionId: String,
    val timestamp: Long,
    val source: EventSource,
    val eventType: EventType,
    val packageName: String?,
    val applicationName: String?,
    val eventData: String?,
    val inputClassification: InputClassification,
    val metadata: String?
)
```

---

# 36. Raw Event Model

Platform components should initially emit a separate raw model.

```kotlin
data class RawInteractionEvent(
    val timestamp: Long,
    val source: EventSource,
    val eventType: EventType,
    val packageName: String?,
    val candidateData: String?,
    val inputClassification: InputClassification,
    val metadata: Map<String, String>
)
```

Raw events shall not be persisted directly.

---

# 37. EventProcessor

`EventProcessor` is the central ingestion point.

Responsibilities:

1. receive raw events;
2. verify active monitoring session;
3. apply privacy rules;
4. resolve application context;
5. normalize metadata;
6. create persistent event;
7. submit to repository.

Architecture:

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
InteractionEvent
    │
    ▼
EventRepository
    │
    ▼
Encrypted Persistence Layer
    │
    ▼
Room / Local Storage
```

---

# 38. EventProcessor Interface

Conceptually:

```kotlin
interface EventProcessor {

    suspend fun process(
        event: RawInteractionEvent
    )

}
```

Platform callbacks should submit work asynchronously rather than block Android callbacks.

---

# 39. PrivacyFilter

This is a security-critical component.

Conceptual interface:

```kotlin
interface PrivacyFilter {

    fun sanitize(
        event: RawInteractionEvent
    ): SanitizedInteractionEvent

}
```

Sensitive event behavior:

```text
candidateData = syntheticSecret

classification = SENSITIVE_PASSWORD

              ↓

PrivacyFilter

              ↓

eventData = null

eventType = SENSITIVE_INPUT_DETECTED
```

---

# 40. Defense in Depth

Sensitive-content suppression shall occur at more than one logical boundary.

Recommended:

```text
IME
 │
 ▼
SensitiveInputDetector
 │
 ▼
Raw event classification
 │
 ▼
PrivacyFilter
 │
 ▼
Repository validation
 │
 ▼
Room
```

The repository should reject an event containing content when its classification is marked sensitive.

---

# 41. ContextResolver

Purpose:

Resolve package-level application context.

Conceptual interface:

```kotlin
interface ContextResolver {

    suspend fun resolve(
        packageName: String?
    ): ApplicationContextInfo

}
```

Result:

```kotlin
data class ApplicationContextInfo(
    val packageName: String?,
    val applicationLabel: String?
)
```

---

# 42. Context Cache

Application labels should be cached.

```text
Package
   │
   ▼
ContextResolver
   │
   ├── Cache hit
   │      │
   │      ▼
   │    Return
   │
   └── Cache miss
          │
          ▼
     PackageManager
          │
          ▼
       Cache
```

This avoids repeated expensive package lookups.

---

# 43. Session Manager

`SessionManager` is the authority for recording state.

Conceptual states:

```text
NONE
ACTIVE
PAUSED
STOPPED
```

Conceptual interface:

```kotlin
interface SessionManager {

    fun observeSession(): Flow<MonitoringSession?>

    suspend fun startSession(name: String)

    suspend fun pauseSession()

    suspend fun resumeSession()

    suspend fun stopSession()

}
```

---

# 44. Session Gate

The event pipeline shall include a session gate.

```text
Incoming Event
      │
      ▼
Session active?
      │
 ┌────┴────┐
 │         │
NO        YES
 │         │
Drop       ▼
      Privacy Pipeline
```

System status events may be treated separately if required.

---

# 45. Database Architecture

Room database:

```text
ApplicationDatabase
      │
      ├── InteractionEventDao
      │
      └── MonitoringSessionDao
```

Initial tables:

```text
monitoring_sessions
interaction_events
```

Derived application statistics should preferably be computed using queries rather than duplicated unnecessarily.

---

# 46. MonitoringSessionEntity

Suggested structure:

```kotlin
@Entity(tableName = "monitoring_sessions")
data class MonitoringSessionEntity(
    @PrimaryKey
    val id: String,

    val name: String,

    val startTime: Long,

    val endTime: Long?,

    val androidVersion: String,

    val deviceModel: String,

    val appVersion: String,

    val notes: String?
)
```

---

# 47. InteractionEventEntity

Suggested structure:

```kotlin
@Entity(
    tableName = "interaction_events",
    indices = [
        Index("sessionId"),
        Index("timestamp"),
        Index("packageName"),
        Index("eventType")
    ]
)
data class InteractionEventEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val sessionId: String,

    val timestamp: Long,

    val source: String,

    val eventType: String,

    val packageName: String?,

    val applicationName: String?,

    val eventData: String?,

    val inputClassification: String,

    val metadata: String?
)
```

---

# 48. Database Relationship

```text
monitoring_sessions
      │
      │ 1
      │
      │
      │ N
      ▼
interaction_events
```

Deleting a session should delete or explicitly handle associated events according to the selected Room relationship strategy.

---

# 49. DAO Architecture

`InteractionEventDao` shall provide operations for:

- insertion;
- paginated retrieval;
- retrieval by session;
- filtering by package;
- filtering by event type;
- filtering by source;
- time-range filtering;
- deletion;
- aggregate statistics.

`MonitoringSessionDao` shall provide:

- create;
- update;
- finish;
- list;
- get;
- delete.

---

# 50. Repository

Domain components shall not directly access DAOs.

```text
ViewModel / UseCase
        │
        ▼
EventRepository
        │
        ▼
Encrypted Persistence Layer
        │
        ▼
    DAO / Room
```

Conceptual interface:

```kotlin
interface EventRepository {

    suspend fun insertEvent(event: InteractionEvent)

    fun observeEvents(
        sessionId: String
    ): Flow<List<InteractionEvent>>

    fun observeSessions():
        Flow<List<MonitoringSession>>

    suspend fun deleteSession(id: String)

}
```

Large event sets should use paging rather than loading the entire database.

---

# 51. Repository Safety Validation

Before insertion:

```text
InteractionEvent
     │
     ▼
Sensitive classification?
     │
 ┌───┴────┐
 │        │
NO       YES
 │        │
 ▼        ▼
Insert   eventData must be NULL
              │
         ┌────┴────┐
         │         │
        YES        NO
         │         │
       Insert    Reject/
                 sanitize
```

This provides defense in depth.

## Encrypted Persistence Layer

```text
EventRepository
    │
    ▼
Encrypted Persistence Layer
    │
    ▼
Room / Local Storage
```

Permitted interaction/event data shall be encrypted at rest using Android-supported cryptographic and secure key-management mechanisms. The key-management mechanism is separate from encrypted data storage; Android Keystore is the preferred platform mechanism to evaluate during Phase 0.

Encryption and database operations shall remain asynchronous and shall not run on the latency-sensitive keyboard input path. The design shall be evaluated for CPU, memory, startup, database-size, battery, and low-end-device impact.

If encrypted persistence cannot be initialized, the application shall not fall back to plaintext persistence. The affected persistence operation shall fail safely while ordinary keyboard input remains functional.

---

# 52. Concurrency Architecture

The system shall use Kotlin Coroutines.

Recommended conceptual pattern:

```text
Android callback
      │
      ▼
Non-blocking submission
      │
      ▼
Coroutine processing
      │
      ▼
Privacy/context pipeline
      │
      ▼
Room IO
```

Database operations shall run away from latency-sensitive UI/IME paths.

---

# 53. Event Buffering

High-frequency accessibility events may produce significant volume.

The system should support:

```text
Event
  │
  ▼
Channel / Flow
  │
  ▼
Processor
  │
  ▼
Batch / sequential persistence
```

The buffer shall have a defined capacity and overflow policy.

Keyboard functionality must never wait for buffer availability.

---

# 54. Backpressure

If event production exceeds persistence capacity:

Priority shall be:

```text
1. Maintain keyboard operation
2. Maintain application stability
3. Preserve important application metadata
4. Drop/coalesce redundant high-frequency events if necessary
```

For example, excessive scroll events may be coalesced.

---

# 55. Dashboard Architecture

```text
DashboardScreen
      │
      ▼
DashboardViewModel
      │
 ┌────┼───────────────┐
 ▼    ▼               ▼
Setup Session       Statistics
State  State          Flow
```

Suggested state:

```kotlin
data class DashboardUiState(
    val setupState: SetupState,
    val activeSession: MonitoringSession?,
    val totalEvents: Long,
    val keyboardEvents: Long,
    val accessibilityEvents: Long,
    val currentApplication: String?
)
```

---

# 56. Event Viewer Architecture

```text
EventScreen
    │
    ▼
EventViewModel
    │
    ▼
EventFilter
    │
    ▼
Repository
    │
    ▼
Paging Query
    │
    ▼
Room
```

Filters may include:

```text
Session
Package
Application
Source
Event type
Classification
Time range
```

---

# 57. Statistics Architecture

Statistics should generally be generated from aggregate Room queries.

Examples:

```sql
COUNT(*)

GROUP BY packageName

GROUP BY eventType

GROUP BY source
```

Avoid loading thousands of events into memory simply to count them.

---

# 58. Export Architecture

```text
Encrypted Local Database
    │
    ▼
Authorized Application Read
    │
    ▼
Export Validation
    │
    ▼
CSV Writer
    │
    ▼
User-selected destination
```

Export shall only occur after explicit user action.

---

# 59. Export Defense

The exporter shall not blindly serialize database objects.

Before output:

```text
Event
 │
 ▼
Sensitive classification?
 │
 ├── NO → normal permitted export
 │
 └── YES
        │
        ▼
 eventData = [REDACTED] / empty
```

This remains necessary even though the database should already exclude sensitive content.

---

# 60. DataStore Architecture

DataStore may contain non-event configuration such as:

```text
onboardingCompleted
recordClickEvents
recordFocusEvents
recordScrollEvents
recordWindowEvents
recordPermittedImeInteractionEvents
```

The sensitive-field filter shall not have a normal disable setting.

---

# 61. Application Startup

```text
Application Process
       │
       ▼
MonitoringApplication
       │
       ▼
Dependency Injection
       │
       ▼
Database
       │
       ▼
Repository
       │
       ▼
MainActivity
       │
       ▼
SetupStateManager
       │
       ▼
Determine Route
```

---

# 62. First-Run Sequence

```text
User
 │
 ▼
Install APK
 │
 ▼
Launch
 │
 ▼
WelcomeScreen
 │
 ▼
Disclosure
 │
 ▼
PermissionSetup
 │
 ▼
AccessibilitySetup
 │
 ▼
IME Enablement
 │
 ▼
IME Selection
 │
 ▼
Verification
 │
 ▼
Dashboard
```

---

# 63. IME Setup Sequence

```text
Application
     │
     ▼
Check IME enabled
     │
     ├── YES ─────────────┐
     │                    │
     └── NO               │
          │               │
          ▼               │
 Open Android Settings    │
          │               │
          ▼               │
 User enables IME         │
          │               │
          └───────────────┘
                  │
                  ▼
          Show IME Picker
                  │
                  ▼
         User selects IME
                  │
                  ▼
         Return to app
                  │
                  ▼
        Refresh SetupState
```

---

# 64. Monitoring Session Sequence

```text
User
    │
    ▼
START SESSION
    │
    ▼
SessionManager
    │
    ▼
Create session
    │
    ▼
Room
    │
    ▼
State = ACTIVE
    │
    ▼
Event pipeline enabled
```

---

# 65. Normal Monitored Input Sequence

```text
User
     │
     ▼
Press keyboard key
     │
     ▼
Keyboard UI
     │
     ▼
KeyProcessor
     │
     ├─────────────────────────┐
     │                         │
     ▼                         ▼
InputConnection          Interaction Event Candidate
     │                         │
     ▼                         ▼
Target App            SensitiveInputDetector
                               │
                               ▼
                            NORMAL
                               │
                               ▼
                         EventProcessor
                               │
                               ▼
                         PrivacyFilter
                               │
                               ▼
                         ContextResolver
                               │
                               ▼
                           Repository
                               │
                               ▼
                              Room
```

---

# 66. Sensitive Input Sequence

```text
Sensitive Field
      │
      ▼
EditorInfoAnalyzer
      │
      ▼
SensitiveInputDetector
      │
      ▼
SENSITIVE
      │
      ├──────────────────────────────┐
      │                              │
      ▼                              ▼
Normal InputConnection        Application pipeline
      │                              │
      ▼                              ▼
Target application            DROP CONTENT
                                     │
                                     ▼
                           metadata-only event
                                     │
                                     ▼
                                 Repository
```

The target application still receives the user's intended input.

The application database does not receive its sensitive contents.

---

# 67. Accessibility Sequence

```text
User interacts with UI
        │
        ▼
Android generates event
        │
        ▼
AppAccessibilityService
        │
        ▼
EventFilter
        │
        ▼
EventMapper
        │
        ▼
EventProcessor
        │
        ▼
PrivacyFilter
        │
        ▼
ContextResolver
        │
        ▼
Repository
        │
        ▼
Room
```

---

# 68. Application Transition Correlation

Events from separate sources shall share timestamps and package context.

Example:

```text
10:01:20 ACCESSIBILITY APP_TRANSITION
         com.example.notes

10:01:21 ACCESSIBILITY VIEW_FOCUSED

10:01:22 IME IME_STARTED
         com.example.notes

10:01:26 IME TEXT_COMMIT
         "hello"

10:01:30 ACCESSIBILITY VIEW_CLICKED
```

This forms the application event timeline.

---

# 69. Process and Lifecycle Considerations

Android may recreate application processes.

The architecture shall therefore avoid relying exclusively on:

- static fields;
- activity-local state;
- singleton mutable state without persistence.

Important monitoring-session state should be recoverable.

---

# 70. Device Reboot

After reboot:

```text
Device boots
    │
    ▼
Android restores system configuration
where applicable
    │
    ▼
User launches Application App
    │
    ▼
SetupStateManager.refresh()
    │
    ▼
Display actual state
```

The application shall not attempt to silently bypass Android configuration after reboot.

---

# 71. Failure Isolation

Components shall fail independently where possible.

```text
Accessibility failure
       │
       └── Keyboard remains operational

Database failure
       │
       └── Keyboard remains operational

Dashboard failure
       │
       └── IME operation independent

Export failure
       │
       └── Existing database remains intact
```

---

# 72. Logging Architecture

Developer logs shall contain only operational information.

Allowed examples:

```text
IME service started
Database initialized
Session started
Event queue size = 20
Accessibility service connected
```

Avoid:

```text
Typed password = ...
PIN = ...
Credential = ...
```

Production/application builds should minimize verbose logging.

---

# 73. Dependency Injection

Hilt should provide:

```text
ApplicationDatabase

InteractionEventDao

MonitoringSessionDao

EventRepository

SessionManager

EventProcessor

PrivacyFilter

ContextResolver

SettingsRepository

ExportManager
```

Android service classes may receive appropriate dependencies through supported Hilt Android integrations.

---

# 74. Suggested Gradle Dependencies

Categories required:

```text
AndroidX Core
Lifecycle
Compose
Navigation Compose
Room
Room KTX
Hilt
Hilt Navigation Compose
DataStore
Coroutines
Kotlin Serialization
Paging
```

Dependency versions should be selected based on the current Android toolchain rather than hard-coded into this architecture document.

---

# 75. Manifest Architecture

The manifest shall declare:

```text
MainActivity

CustomKeyboardService
    → InputMethodService declaration
    → required IME metadata

AppAccessibilityService
    → AccessibilityService declaration
    → accessibility configuration metadata
```

Only permissions required by actual features shall be declared.

---

# 76. IME Metadata

IME metadata shall live under:

```text
res/xml/method.xml
```

This allows Android to recognize the application service as an input method.

The exact schema shall follow the Android SDK version used during implementation.

---

# 77. Accessibility Metadata

Accessibility configuration shall live under:

```text
res/xml/accessibility_service_config.xml
```

It shall configure only the event types/capabilities necessary for the application.

---

# 78. Security Boundary Diagram

```text
┌─────────────────────────────────────────┐
│             ANDROID SYSTEM              │
│                                         │
│   Target Apps                           │
│       │                                 │
│       ├──── InputConnection ◄── IME     │
│       │                                 │
│       └──── Accessibility Events ──┐    │
│                                   │    │
└───────────────────────────────────┼────┘
                                    │
                     MONITORING APP │
                                    ▼
                         Raw Event Boundary
                                    │
                                    ▼
                           Sensitive Classifier
                                    │
                                    ▼
                              Privacy Filter
                                    │
                                    ▼
                          Persistence Boundary
                                    │
                                    ▼
                             Room Database
```

Raw input candidates must bypass the input-safety boundary.

---

# 79. Data Flow Classification

Three major classes of data exist.

## Class A — System Metadata

Examples:

```text
timestamp
package
event type
session
Android version
```

Permitted for application persistence.

## Class B — Controlled Monitored Input

Example:

```text
"hello android"
```

May be persisted only during an active controlled monitoring session when classified as non-sensitive.

## Class C — Sensitive Input

Examples:

```text
password
PIN
OTP
authentication secret
```

Content must not be persisted.

---

# 80. Testing Architecture

Testing shall occur at multiple levels.

```text
Unit Tests
     │
     ▼
Repository Tests
     │
     ▼
Database Tests
     │
     ▼
Privacy Tests
     │
     ▼
IME Instrumentation Tests
     │
     ▼
Accessibility Tests
     │
     ▼
Device Tests
```

---

# 81. Critical Privacy Tests

These tests shall have high priority.

### Test P1

Input classification:

```text
Normal Text
```

Expected:

```text
NORMAL
```

### Test P2

Password editor.

Expected:

```text
SENSITIVE_PASSWORD
```

### Test P3

Numeric password editor.

Expected:

```text
SENSITIVE_PIN
```

### Test P4

Attempt to persist:

```text
classification = SENSITIVE_PASSWORD
eventData = "test-secret"
```

Repository expected behavior:

```text
Reject or sanitize.

Database must not contain "test-secret".
```

---

# 82. Keyboard Reliability Test

Simulate repository failure.

Expected:

```text
User presses H

InputConnection receives H

Target application displays H

Application persistence fails safely

Keyboard remains responsive
```

---

# 83. Event Volume Test

Generate large quantities of scroll/focus events.

Measure:

```text
CPU usage
Memory usage
Queue depth
Database write latency
Dropped/coalesced event count
Keyboard responsiveness
```

---

# 84. Application Device Test Matrix

Recommended:

```text
Android 15
    │
    ├── IME
    ├── Accessibility
    └── Privacy tests

Android 16
    │
    ├── IME
    ├── Accessibility
    └── Privacy tests

Android 17
    │
    ├── IME
    ├── Accessibility
    └── Privacy tests
```

OEM-specific differences should be recorded separately.

---

# 85. Implementation Phases

`IMPLEMENTATION_PLAN.md` (Phases 0–21) is the single authoritative
implementation roadmap. For the authoritative step-by-step phase sequence and
validation gates, refer to `IMPLEMENTATION_PLAN.md` (Phases 0–21).

The architectural milestones map to that roadmap as follows:

| Architectural milestone | Authoritative phases |
|---|---|
| Project, persistence, and session foundation | 0–3 |
| Keyboard, privacy, and event sources | 4–9 |
| Setup, dashboard, analysis, export, and data management | 10–17 |
| Performance, database scale, Android compatibility, and release | 18–21 |

The detailed responsibilities previously described for foundation, keyboard,
privacy, accessibility, setup, dashboard, export, and compatibility remain
requirements; their sequencing and validation gates are maintained in
`IMPLEMENTATION_PLAN.md`.

---

# 95. Final Component Dependency Graph

```text
                    Compose UI
                        │
                        ▼
                    ViewModels
                        │
                        ▼
                     UseCases
                        │
       ┌────────────────┼─────────────────┐
       │                │                 │
       ▼                ▼                 ▼
SetupStateManager SessionManager   EventRepository
                                          │
                                          ▼
                                         Room


ANDROID PLATFORM SOURCES

AccessibilityService
        │
        ▼
 AccessibilityMapper
        │
        ┐
        │
        ▼
    EventProcessor
        ▲
        │
        ┘
ImeEventPublisher
        ▲
        │
CustomKeyboardService
        │
        ▼
InputConnection
        │
        ▼
Target Application


EVENT PIPELINE

RawInteractionEvent
        │
        ▼
Session Gate
        │
        ▼
PrivacyFilter
        │
        ▼
ContextResolver
        │
        ▼
InteractionEvent
        │
        ▼
Repository Safety Validation
        │
        ▼
Room
```

---

# 96. Final Runtime Architecture

```text
                     ANDROID OS
                         │
          ┌──────────────┴──────────────┐
          │                             │
          ▼                             ▼
 Accessibility Events             Text Editors
          │                             │
          ▼                             ▼
ApplicationAccessibility       CustomKeyboardService
      Service                        │
          │                          ├────────────►
          │                          │ InputConnection
          │                          │       │
          │                          │       ▼
          │                          │   Target App
          │                          │
          └────────────┐  ────────────
                       │  │
                       ▼  ▼
                     Raw Events
                         │
                         ▼
                   Session Gate
                         │
                         ▼
                Sensitive Classification
                         │
                         ▼
                   Privacy Filter
                         │
               ┌─────────┴─────────┐
               │                   │
            NORMAL             SENSITIVE
               │                   │
               │                   ▼
               │              Drop Content
               │                   │
               └─────────┬─────────┘
                         ▼
                   ContextResolver
                         │
                         ▼
                  Event Repository
                         │
                         ▼
                    Room Database
                         │
              ┌──────────┼───────────┐
              │          │           │
              ▼          ▼           ▼
          Timeline   Statistics    Export
              │          │           │
              └──────────┼───────────┘
                         ▼
                  Dashboard
```

---

# 97. Architectural Invariants

The following rules shall remain true throughout development.

**INV-01 — Keyboard independence**

The IME shall not depend on successful event logging to deliver input.

**INV-02 — User-controlled activation**

Accessibility and IME activation remain under Android/user control.

**INV-03 — Sensitive content boundary**

Known credential input shall not be intentionally persisted.

**INV-04 — Single event pipeline**

IME and Accessibility interaction events converge through the centralized event-processing architecture.

**INV-05 — Local-first**

Version 1 requires no remote server for application operation.

**INV-06 — Session-based collection**

Ordinary content-bearing interaction events are persistently collected only during explicit active monitoring sessions.

**INV-07 — Defense in depth**

Sensitive-data protection occurs before persistence and is validated again at the repository/export boundaries.

**INV-08 — Failure isolation**

Failures in application functionality shall not unnecessarily break the keyboard.

**INV-09 — Android restrictions respected**

The application shall not attempt to bypass Android's permission, accessibility, IME, application-sandbox, or device-security controls.

---

# 98. Definition of Done

The architecture is successfully implemented when:

1. The APK installs normally.
2. First-run onboarding functions.
3. Required ordinary permissions use Android's supported permission workflow.
4. Accessibility setup uses Android's system-controlled configuration.
5. Android recognizes the custom IME.
6. The user can enable the Custom Keyboard.
7. Android's input-method selector can be presented where supported.
8. The user can select the Custom Keyboard.
9. The keyboard operates across compatible applications.
10. QWERTY, Shift, Space, Backspace, Enter, numbers and symbols function correctly.
11. The keyboard remains operational when event logging is disabled.
12. Application sessions can be explicitly started, paused and stopped.
13. Permitted synthetic non-sensitive monitored input can enter the event pipeline.
14. Sensitive fields produce metadata-only events.
15. Sensitive contents cannot reach Room through the supported event pipeline.
16. Accessibility interaction metadata enters the same event pipeline.
17. Package/application context is resolved where available.
18. Events are stored locally.
19. The timeline can display stored events.
20. Filters and search operate.
21. Statistics are generated efficiently.
22. CSV export works.
23. Export performs privacy validation.
24. Application data can be deleted.
25. Configuration changes are detected after returning from Android Settings.
26. Database/event-pipeline failure does not prevent ordinary keyboard input.
27. Controlled tests pass on the target application device.

---

# 99. Final Architecture Summary

The platform consists of two Android-facing application sources:

```text
AccessibilityService
+
InputMethodService
```

The `InputMethodService` provides the actual user-selected Custom Keyboard. Its primary responsibility is delivering input to the active application's `InputConnection`.

The `AccessibilityService` independently receives permitted Android interaction events.

Neither component writes arbitrary data directly into Room.

Instead, interaction events pass through:

```text
SOURCE
   ↓
RawInteractionEvent
   ↓
Session Gate
   ↓
Sensitive Classification
   ↓
PrivacyFilter
   ↓
ContextResolver
   ↓
EventRepository
   ↓
Safety Validation
   ↓
Room
```

The application layer then consumes the sanitized database through:

```text
Room
 │
 ├── Dashboard
 ├── Timeline
 ├── Search
 ├── Filters
 ├── Statistics
 └── Export
```

This architecture provides a modular Android application in which the custom keyboard, AccessibilityService, privacy controls, application database and analytical interface remain clearly separated and independently testable.

---

**END OF ARCHITECTURE SPECIFICATION**

**Document:** `Architecture.md`  
**Version:** `1.0`