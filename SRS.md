# SOFTWARE REQUIREMENTS SPECIFICATION

## Android Interaction & Input Monitoring Application

**Document:** SRS.md  
**Version:** 1.0 Final  
**Platform:** Android  
**Primary Language:** Kotlin  
**Application Type:** Android Application + Custom IME + AccessibilityService  
**Storage:** Local-first / Encrypted Room Database
**Purpose:** Android interaction and input monitoring with a user-enabled Input Method Editor

---

# 1. Introduction

## 1.1 Purpose

This Software Requirements Specification defines the requirements for an Android interaction and input monitoring application. The application combines a user-selected custom keyboard with Android AccessibilityService events to provide local interaction logging, application-context correlation, event visualization, statistics, and data export.
- a custom Android keyboard implemented using `InputMethodService`;
- an Android `AccessibilityService`;
- interaction-event collection;
- keyboard-input monitoring;
- application/context detection;
- centralized event processing;
- sensitive-input filtering;
- local database storage;
- monitoring-session management;
- event visualization;
- statistics;
- local application-data export.

The custom keyboard shall be a genuine Android Input Method Editor that the device owner can manually enable and select as the current keyboard.

Once selected, it shall function throughout compatible applications on the device.

The application is intended for use on user-owned or user-administered Android devices.

It shall not be designed as a covert monitoring application.

---

# 2. Product Objectives

The application objectives are:

1. Provide a functional custom Android keyboard.
2. Allow the user to enable and select the keyboard as an Android IME.
3. Collect supported interaction events through AccessibilityService.
4. Detect application/window transitions where Android exposes them.
5. Correlate keyboard and interaction events with timestamps and application context.
6. Store application events locally.
7. Provide an event timeline and viewer.
8. Provide search and filtering.
9. Provide statistics and application activity information.
10. Provide local export.
11. Remain responsive on lower-configuration Android devices.
12. Operate without requiring a remote server.


---

# 3. High-Level Architecture

```text
                   ANDROID DEVICE
                         │
                         ▼
                 Application
                         │
          ┌──────────────┴──────────────┐
          │                             │
          ▼                             ▼
 AccessibilityService             Custom Keyboard
                                  InputMethodService
          │                             │
          │                             │
 clicks / focus /                 keyboard input
 scroll / windows /               events
 app transitions                       │
          │                             │
          └──────────────┬──────────────┘
                         │
                         ▼
                   Event Processor
                         │
              ┌──────────┴──────────┐
              │                     │
              ▼                     ▼
       Privacy/Safety          Context Resolver
           Filter
              │                     │
              └──────────┬──────────┘
                         ▼
                   Event Repository
                         │
                         ▼
                Encrypted Persistence Layer
                              │
                              ▼
                        Room Database
                         │
             ┌───────────┼───────────┐
             │           │           │
             ▼           ▼           ▼
         Timeline    Statistics    Export
             │           │           │
             └───────────┼───────────┘
                         ▼
                 Dashboard
```

---

# 4. Scope

## 4.1 Included

The application shall provide:

- standard Android installation;
- first-run onboarding;
- application disclosure;
- required runtime-permission handling;
- AccessibilityService configuration;
- custom Android IME;
- keyboard-enablement workflow;
- keyboard-selection workflow;
- QWERTY keyboard;
- numeric/symbol keyboard;
- Shift/Caps Lock;
- Backspace;
- Space;
- Enter/action keys;
- keyboard switching;
- permitted interaction-event monitoring;
- application transition detection;
- timestamped interaction events;
- monitoring sessions;
- centralized event processing;
- sensitive-input detection;
- sensitive-content suppression;
- local Room database;
- dashboard;
- event timeline;
- filtering;
- searching;
- statistics;
- CSV export;
- data deletion.

---

# 5. Explicitly Out of Scope

The application shall NOT implement:

- hidden installation;
- silent permission granting;
- silent AccessibilityService activation;
- silent/default keyboard replacement;
- Android security-control bypasses;
- password recording;
- PIN recording;
- OTP recording;
- payment-card recording;
- authentication-token collection;
- credential extraction;
- hidden remote monitoring;
- unauthorized network exfiltration;
- privilege-escalation exploits;
- root exploits;
- Android vulnerability exploitation;
- attempts to conceal the application from the device owner.

---

# 6. Target Environment

## 6.1 Operating Systems

Primary application targets:

```text
Android 15
Android 16
Android 17
```

The implementation shall allow the same monitoring scenarios to be repeated across Android versions.

---

# 7. Recommended Technology Stack

Until `tech.md` is created during Phase 0, the choices below are recommended defaults unless they are fundamental Android platform requirements. Phase 0 shall evaluate them against correctness, maintainability, APK size, memory, CPU, battery, dependency overhead, Android platform support, and testing requirements. Once created, `tech.md` becomes authoritative for the actual stack. Any meaningful architectural deviation shall be recorded in `decision.md`.

```text
Language:
Kotlin

UI:
Jetpack Compose

Architecture:
Lightweight layered architecture inspired by Clean Architecture
+ MVVM
+ Repository Pattern

Database:
Room

Dependency Injection:
Hilt

Concurrency:
Kotlin Coroutines

Reactive State:
Flow / StateFlow

Preferences:
DataStore

Keyboard:
InputMethodService

Interaction Monitoring:
AccessibilityService

Serialization:
Kotlin Serialization

Build:
Gradle Kotlin DSL
```

---

# 8. Primary User

The primary actor is the:

**User / Device Owner**

The user shall be able to:

- install the application;
- complete onboarding;
- grant applicable permissions;
- enable the AccessibilityService;
- enable the Custom Keyboard;
- select the Custom Keyboard;
- start monitoring sessions;
- stop monitoring sessions;
- pause application recording;
- view events;
- search/filter events;
- view statistics;
- export data;
- delete data.

---

# 9. Major Components

```text
MonitoringApplication
│
├── SetupStateManager
│
├── SessionManager
│
├── AppAccessibilityService
│
├── CustomKeyboardService
│
├── KeyboardController
│
├── EditorInfoAnalyzer
│
├── SensitiveInputDetector
│
├── EventProcessor
│
├── PrivacyFilter
│
├── ContextResolver
│
├── EventRepository
│
├── RoomDatabase
│
├── Dashboard
│
├── EventViewer
│
├── StatisticsEngine
│
├── ExportManager
│
└── SettingsManager
```

---

# 10. First Installation

The intended first-run experience shall be:

```text
Install APK
     │
     ▼
Launch Application
     │
     ▼
Application Disclosure
     │
     ▼
Request Applicable Permissions
     │
     ▼
Configure AccessibilityService
     │
     ▼
Enable Custom Keyboard
     │
     ▼
Select Custom Keyboard
     │
     ▼
Verify Configuration
     │
     ▼
Setup Complete
     │
     ▼
Dashboard
```

---

# 11. Welcome Screen

On first launch, the application shall display:

```text
ANDROID INTERACTION MONITOR

This application provides Android interaction
and input monitoring on a user-configured device.

Features:

• Custom Android keyboard
• Interaction-event monitoring
• Local monitoring sessions
• Event analysis
• Application statistics

Application information is stored locally
unless explicitly exported.

Sensitive credential fields such as passwords
and PINs are excluded from content logging.

[CONTINUE]
```

---

# 12. Permission Setup

The application shall request only Android permissions actually required by implemented features.

For ordinary runtime permissions:

```text
Check Permission
      │
      ├── Granted ──► Continue
      │
      └── Missing
             │
             ▼
      Explain Requirement
             │
             ▼
       Android Permission Dialog
```

The application shall properly handle denial.

The application shall not repeatedly trap the user in permission dialogs.

---

# 13. Accessibility Setup

Accessibility access requires explicit Android system configuration.

The application shall display:

```text
ENABLE INTERACTION MONITORING

The Application Accessibility Service provides
permitted
interaction events including:

• Clicks
• Focus changes
• Scrolling
• Window changes
• Application transitions

Android requires this capability to be
enabled manually.

[OPEN ACCESSIBILITY SETTINGS]

[NOT NOW]
```

After returning to the application, its status shall be checked again.

---

# 14. Keyboard Enablement

The Custom Keyboard shall be implemented as an Android `InputMethodService`.

The application shall provide:

```text
ENABLE CUSTOM KEYBOARD

This application contains an Android
keyboard used for Android interaction and input monitoring.

Android requires you to manually enable
new keyboards.

[ENABLE KEYBOARD]
```

The button shall direct the user to Android's appropriate input-method settings.

---

# 15. Keyboard Selection

After enablement:

```text
SELECT CUSTOM KEYBOARD

The Custom Keyboard is enabled.

Select it as your current keyboard to
use it in compatible applications.

[SELECT KEYBOARD]
```

The application shall invoke Android's official input-method selection mechanism where supported.

Example Android UI:

```text
Choose input method

○ Gboard
● Custom Keyboard
○ Other Keyboard
```

The final selection remains under Android/user control.

---

# 16. Configuration Verification

The application shall display:

```text
SYSTEM STATUS

Application Permissions      ✓ READY
Accessibility Service        ✓ ENABLED
Custom Keyboard            ✓ ENABLED
Current Keyboard             ✓ CUSTOM KEYBOARD
Sensitive Input Protection   ✓ ACTIVE
Local Database               ✓ READY

[START SESSION]
```

---

# 17. Partial Setup

If configuration is incomplete:

```text
SYSTEM STATUS

Accessibility Service        ✓
Custom Keyboard            ✓
Current Keyboard             ✕

[SELECT KEYBOARD]
```

The application shall remain accessible and shall not continuously force the user back into Android Settings.

---

# 18. Configuration State Management

A `SetupStateManager` shall monitor:

```text
Runtime permissions
Accessibility status
IME installation
IME enablement
Current IME
Database availability
Monitoring-session status
```

Configuration shall be refreshed when the application returns to the foreground.

---

# 19. System-Wide Keyboard

Once explicitly enabled and selected by the device owner, the Custom Keyboard shall function as the selected Android IME for compatible text fields.

```text
                  Android
                     │
                 Current IME
                     │
                     ▼
             Custom Keyboard
                     │
       ┌─────────────┼─────────────┐
       ▼             ▼             ▼
     Notes         Browser       Test App
       │             │             │
       └─────────────┼─────────────┘
                     ▼
                 Text Fields
```

Individual applications, device-management policies, specialized fields, or Android security mechanisms may restrict third-party IMEs.

Those restrictions shall be respected.

---

# 20. Keyboard Architecture

```text
CustomKeyboardService
          │
          ├── Keyboard UI
          │
          ├── KeyboardController
          │
          ├── KeyboardState
          │
          ├── KeyProcessor
          │
          ├── EditorInfoAnalyzer
          │
          ├── SensitiveInputDetector
          │
          ├── InputConnection
          │
          └── EventPublisher
```

---

# 21. Keyboard Layout

Version 1 shall provide QWERTY input.

```text
┌────────────────────────────────────┐
│ q  w  e  r  t  y  u  i  o  p      │
│                                    │
│  a  s  d  f  g  h  j  k  l        │
│                                    │
│ ⇧  z  x  c  v  b  n  m  ⌫         │
│                                    │
│ 123   ,     SPACE     .      ↵     │
└────────────────────────────────────┘
```

---

# 22. Keyboard Functional Requirements

**FR-KB-001**  
The system shall provide a functional Android IME.

**FR-KB-002**  
The IME shall be discoverable by Android.

**FR-KB-003**  
The user shall be able to manually enable the keyboard.

**FR-KB-004**  
The user shall be able to select the keyboard as the current IME.

**FR-KB-005**  
The keyboard shall provide alphabetic input.

**FR-KB-006**  
The keyboard shall provide numeric input.

**FR-KB-007**  
The keyboard shall provide common symbols.

**FR-KB-008**  
The keyboard shall provide Space.

**FR-KB-009**  
The keyboard shall provide Backspace.

**FR-KB-010**  
The keyboard shall provide Enter/action functionality.

**FR-KB-011**  
The keyboard shall support Shift.

**FR-KB-012**  
The keyboard shall support capitalization.

**FR-KB-013**  
The keyboard shall provide access to Android keyboard switching.

**FR-KB-014**  
The layout should adapt to appropriate `EditorInfo` characteristics.

---

# 23. Context-Specific Keyboard Layout

Where practical:

```text
Normal text
→ QWERTY

Email
→ QWERTY + convenient @

URL
→ URL-oriented layout

Numeric
→ Number layout

Multiline
→ Newline action

Sensitive field
→ normal input operation
   but persistent content logging disabled
```

---

# 24. Keyboard Input Flow

```text
User presses key
       │
       ▼
Keyboard UI
       │
       ▼
KeyProcessor
       │
       ├──────────► EditorInfoAnalyzer
       │
       │
       ├──────────► SensitiveInputDetector
       │
       ▼
InputConnection
       │
       ▼
Target Application
```

Application recording shall be secondary to input delivery.

---

# 25. Keyboard Reliability Requirement

A critical requirement is:

**The keyboard shall continue functioning even if event logging fails.**

Therefore:

```text
Database unavailable
        │
        ▼
Keyboard still works


Application session stopped
        │
        ▼
Keyboard still works


Event processing failure
        │
        ▼
Keyboard still works
```

---

# 26. Monitored Input Recording

During an explicitly active monitoring session, ordinary non-sensitive synthetic/monitored input may produce events.

Example:

```text
Timestamp:
2026-10-17T18:32:41

Source:
IME

Event:
TEXT_COMMIT

Package:
com.example.applicationtest

Event Data:
"hello"

Session:
android17_test_01
```

Where practical, logical commits should be preferred over unnecessary database writes for every physical key press.

---

# 27. Sensitive Input Protection

Every potentially content-bearing IME event shall be inspected before persistence.

The IME shall classify the active editor early, before generating an interaction-event candidate where practical. This early classification, the central privacy filter, repository validation, and export validation are complementary defense-in-depth safeguards.

```text
Input
  │
  ▼
EditorInfoAnalyzer
  │
  ▼
SensitiveInputDetector
  │
  ├───────────────┐
  │               │
NORMAL         SENSITIVE
  │               │
  ▼               ▼
PrivacyFilter   DROP CONTENT
  │               │
  └───────┬───────┘
          ▼
       Repository
```

---

# 28. Sensitive Fields

The system shall classify known credential-oriented input fields as sensitive.

Examples include:

- passwords;
- web passwords;
- visible-password variants when used for authentication;
- numeric passwords;
- PIN fields;
- other fields positively identified as credential/sensitive inputs.

Where classification cannot safely be determined, the implementation should favor content suppression.

---

# 29. Sensitive Event Storage

For sensitive fields, the application may store metadata such as:

```text
timestamp:
18:42:51

package:
com.example.test

event:
SENSITIVE_INPUT_DETECTED

classification:
PASSWORD

content:
NULL
```

It shall not persist the entered credential.

---

# 30. Mandatory Sensitive-Data Exclusions

The application content logger shall not intentionally persist:

```text
Passwords
PINs
OTPs
Payment-card numbers
Authentication secrets
Security codes
Authentication tokens
```

Sensitive contents shall also be excluded from:

```text
Room
Logcat
CSV exports
JSON exports
Crash reports
Debug files
Analytics
```

---

# 31. AccessibilityService

The application shall contain a dedicated `AccessibilityService` component.

The AccessibilityService shall collect only permitted interaction metadata according to the configured monitoring scope.

The AccessibilityService-specific processing path shall feed the canonical centralized event pipeline.

```text
Android
      │
      ▼
AccessibilityEvent
      │
      ▼
AppAccessibilityService
      │
      ▼
AccessibilityEventFilter
      │
      ▼
AccessibilityEventMapper
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

---

# 32. Accessibility Events

Subject to Android availability, application behavior, configured monitoring scope, and platform restrictions, the service may monitor metadata for events including:

```text
TYPE_VIEW_CLICKED
TYPE_VIEW_FOCUSED
TYPE_VIEW_SCROLLED
TYPE_WINDOW_STATE_CHANGED
TYPE_WINDOWS_CHANGED
TYPE_VIEW_SELECTED
```

The exact event set shall be configurable.

The application shall favor interaction metadata and shall avoid unnecessary extraction of arbitrary UI text, editable contents, credentials, or complete accessibility trees.

---

# 33. Accessibility Requirements

**FR-AS-001**  
Detect permitted click events.

**FR-AS-002**  
Detect permitted focus events.

**FR-AS-003**  
Detect permitted scrolling events.

**FR-AS-004**  
Detect permitted window changes.

**FR-AS-005**  
Detect application/window transitions where available.

**FR-AS-006**  
Associate timestamps with events.

**FR-AS-007**  
Associate package information when Android provides it.

**FR-AS-008**  
Avoid unnecessarily collecting UI text.

**FR-AS-009**  
Operate independently from the IME.

**FR-AS-010**  
Clearly display whether AccessibilityService is enabled.

---

# 34. Context Resolver

The system shall attempt to associate events with:

```text
timestamp
session
source
package name
application label
event type
input classification
```

Example:

```text
Application:
Application Notes

Package:
com.example.notes

Source:
IME

Event:
TEXT_COMMIT

Classification:
NORMAL
```

---

# 35. Unified Event Model

IME and Accessibility events shall use a common internal representation.

```text
InteractionEvent

id
timestamp
sessionId
source
eventType
packageName
applicationName
eventData
inputClassification
metadata
```

---

# 36. Event Sources

```text
IME
ACCESSIBILITY
SYSTEM
APPLICATION
```

---

# 37. Event Types

Initial event types may include:

```text
SESSION_STARTED
SESSION_PAUSED
SESSION_RESUMED
SESSION_STOPPED

APP_TRANSITION

VIEW_CLICKED
VIEW_FOCUSED
VIEW_SCROLLED
WINDOW_CHANGED

IME_STARTED
IME_FINISHED
IME_KEY
IME_TEXT_COMMIT
IME_DELETE
IME_ACTION

SENSITIVE_INPUT_DETECTED
```

---

# 38. Central Event Pipeline

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
Room / Local Storage
```

Services should not independently implement unrelated persistence logic.

---

# 39. Monitoring Sessions

Persistent application-content recording shall be organized into sessions.

The user shall explicitly start a monitoring session.

Example:

```text
MONITORING SESSION

Android 17 Interaction Session

● ACTIVE

Duration
00:24:17

Events
1,429

[PAUSE]

[STOP SESSION]
```

---

# 40. Session Lifecycle

```text
CREATE
   │
   ▼
START
   │
   ├─────────────┐
   ▼             │
ACTIVE           │
   │             │
   ▼             │
PAUSED ──────────┘
   │
   ▼
STOPPED
```

---

# 41. No Active Session

When no monitoring session is active:

- the keyboard shall continue functioning;
- accessibility configuration shall remain intact;
- ordinary application-input content shall not be persistently recorded by default.

---

# 42. Room Database

The system shall use Room for structured local persistence.

All permitted interaction/event data and other application-generated monitoring data persisted locally by the application shall be encrypted at rest using Android-supported cryptographic and secure key-management mechanisms.

Encryption is defense in depth and does not permit storage of passwords, PINs, OTPs, authentication secrets, security codes, authentication tokens, payment-card credentials, or other recognized credential content.

If secure encrypted persistence cannot be initialized, the application shall not silently fall back to storing protected event data in plaintext. The affected persistence operation shall fail safely while the keyboard remains functional.

Recommended tables:

```text
monitoring_sessions
interaction_events
```

Derived usage statistics shall be computed with Room aggregate queries such as
`COUNT(*)` and `GROUP BY` rather than duplicated in a separate usage table.
User preferences and application configuration shall be stored in DataStore
rather than a database settings table.

---

# 43. Monitoring Session Entity

```text
MonitoringSessionEntity

id
name
startTime
endTime
androidVersion
deviceModel
appVersion
notes
```

---

# 44. Interaction Event Entity

```text
InteractionEventEntity

id: Long

sessionId: String

timestamp: Long

source: String

eventType: String

packageName: String?

applicationName: String?

eventData: String?

inputClassification: String

metadata: String?
```

The domain `InteractionEvent` model shall use strongly typed Kotlin enums
(`EventSource`, `EventType`, and `InputClassification`). The Room
`InteractionEventEntity` shall persist those values as primitive `String`
columns, or through equivalent Room `TypeConverter`s, as defined in
`Architecture.md`.

---

# 45. Local-First Architecture

Version 1 shall require no server.

```text
ANDROID PHONE

├── Application App
├── AccessibilityService
├── Custom Keyboard
├── Event Processor
├── Room Database
└── Dashboard
```

Normal application operation shall work offline.

---

# 46. Network Requirement

Version 1 should not require transmission of interaction events over the Internet.

Any future synchronization system shall require separate requirements covering:

- explicit user configuration;
- TLS;
- authentication;
- authorization;
- privacy disclosure;
- encryption;
- threat modeling;
- server security.

---

# 47. Dashboard

The main screen shall provide a concise view of current application activity.

```text
ANDROID INTERACTION MONITOR

Session
● ACTIVE

Keyboard
● ENABLED
● CURRENT IME

Accessibility
● ENABLED

─────────────────────────

Current Application

Application Notes

com.example.notes

─────────────────────────

EVENTS

Total                 1,284
Keyboard                642
Clicks                  213
Focus                   184
Scroll                  156
Window Changes           89

─────────────────────────

[EVENT LOG]

[STATISTICS]

[PAUSE]

[STOP SESSION]
```

---

# 48. Live Event Viewer

The application shall provide a chronological timeline.

```text
18:42:01
SESSION_STARTED

18:42:05
APP_TRANSITION
com.example.notes

18:42:07
VIEW_FOCUSED

18:42:10
IME_TEXT_COMMIT
"hello"

18:42:15
VIEW_CLICKED

18:42:19
APP_TRANSITION
com.example.testlogin

18:42:23
SENSITIVE_INPUT_DETECTED
PASSWORD
CONTENT NOT STORED
```

---

# 49. Filtering

Events shall be filterable by:

- monitoring session;
- application;
- package;
- source;
- event type;
- timestamp/time range;
- input classification.

---

# 50. Search

The event viewer shall support searching permitted application information including:

```text
Package
Application
Event type
Session
Permitted synthetic test input
```

---

# 51. Statistics

The system shall calculate:

```text
Total events
Events per application
Events per minute
IME events
Accessibility events
Clicks
Focus events
Scroll events
Window changes
Application transitions
Sensitive-field suppression count
```

---

# 52. Application Activity

Example:

```text
APPLICATION ACTIVITY

Application Notes
421 events

Browser Test
302 events

Settings
184 events

Application Login Test
151 events
```

---

# 53. Application Timeline

Example:

```text
18:30
Notes opened

18:31
Custom Keyboard active

18:32
Controlled test input

18:34
Browser opened

18:35
Search interaction

18:38
Controlled login test opened

18:38
Sensitive field detected

18:38
Content suppressed
```

---

# 54. Export

Version 1 shall support CSV export.

Optional future format:

```text
JSON
```

Example:

```text
timestamp,source,event,package,data

18:32:10,ACCESSIBILITY,VIEW_CLICKED,com.example.notes,
18:32:14,IME,TEXT_COMMIT,com.example.notes,hello
18:33:21,IME,SENSITIVE_INPUT,com.example.testlogin,[REDACTED]
```

Sensitive contents shall never be restored during export.

Canonical export pipeline:

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

---

# 55. Data Deletion

The user shall be able to:

```text
Delete selected session

Delete all sessions

Delete all application data
```

Destructive bulk actions shall require confirmation.

---

# 56. Pause Function

The user shall have immediate control over application collection.

```text
EVENT LOGGING

● ACTIVE

[PAUSE]
```

Pausing event logging shall not disable normal keyboard operation.

---

# 57. Privacy Filter

All potentially content-bearing events shall pass through a centralized privacy layer.

```text
Raw Event
    │
    ▼
Privacy Filter
    │
    ├── Permitted interaction event
    │          │
    │          ▼
    │      Repository
    │
    └── Sensitive
               │
               ▼
          Remove Content
               │
               ▼
        Metadata Event Only
               │
               ▼
           Repository
```

---

# 58. Security Requirements

**SEC-001**  
Application information shall remain in application-private storage unless explicitly exported.

**SEC-002**  
The Room database shall not intentionally be exposed to unrelated applications.

**SEC-003**  
Sensitive-field contents shall not be persisted.

**SEC-004**  
Sensitive contents shall not appear in debug logs.

**SEC-005**  
The application shall not silently transmit application information.

**SEC-006**  
Export shall require explicit user action.

**SEC-007**  
The application shall provide deletion controls.

**SEC-008**  
Accessibility activation shall remain controlled by Android.

**SEC-009**  
IME enablement shall remain controlled by Android.

**SEC-010**  
IME selection shall remain controlled by Android/user choice.

**SEC-011 — Encryption at Rest**  
All persisted permitted interaction/event data shall be encrypted at rest.

**SEC-012 — Secure Key Management**  
Cryptographic key material shall be protected using Android-supported secure key-management mechanisms and shall not be hardcoded or stored in plaintext alongside encrypted application data. Android Keystore is the preferred platform mechanism to evaluate during Phase 0; the final mechanism remains provisional until documented in `tech.md`.

**SEC-013 — No Plaintext Fallback**  
Failure to initialize encrypted persistence shall not result in automatic plaintext storage of protected event data.

**SEC-014 — Keyboard Independence**  
Encryption or encrypted-storage failure shall not prevent ordinary keyboard input delivery through `InputConnection`.

**SEC-015 — Export Boundary**  
Internal encryption at rest and explicit user export are separate security boundaries. All exports shall continue to pass through Export Validation.

---

# 59. Performance Requirements

**NFR-P-001**  
Keyboard input shall remain responsive.

**NFR-P-002**  
Database operations shall not block the UI thread.

**NFR-P-003**  
Accessibility callbacks shall perform minimal synchronous processing.

**NFR-P-004**  
High-frequency events should be buffered where appropriate.

**NFR-P-005**  
Application recording shall not materially interfere with keyboard rendering.

**NFR-P-006**  
Large event histories shall use paginated/lazy loading.

---

# 60. Reliability Requirements

The system shall tolerate:

```text
Activity recreation
Process recreation
Screen rotation
Database reopening
Keyboard switching
Accessibility interruption
Application pause/resume
Device reboot
```

Android-controlled permissions and settings shall never be bypassed to restore functionality.

---

# 61. Recommended Project Structure

The four-layer package structure in `Architecture.md` (§10) is authoritative.
The SRS does not define a competing feature-based root package tree.

The Android resources required by this specification are:

```text
src/main/res/xml/accessibility_service_config.xml
src/main/res/xml/method.xml
```

---

# 62. Application Startup Flow

```text
Launch
   │
   ▼
SetupStateManager
   │
   ├── Permissions?
   ├── Accessibility?
   ├── IME enabled?
   ├── Current IME?
   ├── Database ready?
   └── Session active?
          │
          ▼
   Configuration Complete?
          │
      ┌───┴────┐
      │        │
     YES       NO
      │        │
      ▼        ▼
 Dashboard   Setup UI
```

---

# 63. IME Runtime Flow

```text
Text Field Selected
       │
       ▼
Android Starts IME
       │
       ▼
onStartInput()
       │
       ▼
Analyze EditorInfo
       │
       ▼
Classify Field
       │
       ▼
Render Keyboard
       │
       ▼
User Types
       │
       ├──────────────► InputConnection
       │                       │
       │                       ▼
       │                Target Application
       │
       ▼
Monitoring Session Active?
       │
       ├── NO → No persistent application content
       │
       └── YES
              │
              ▼
        Privacy Filter
              │
        ┌─────┴─────┐
        │           │
      SAFE      SENSITIVE
        │           │
        ▼           ▼
      Event      Drop content
        │           │
        └─────┬─────┘
              ▼
          Repository
```

---

# 64. Accessibility Runtime Flow

```text
Android Event
      │
      ▼
AppAccessibilityService
      │
      ▼
Event Enabled?
      │
      ▼
Normalize
      │
      ▼
Privacy Filter
      │
      ▼
Resolve Context
      │
      ▼
Session Active?
      │
      ▼
EventProcessor
      │
      ▼
Repository
      │
      ▼
Room
```

---

# 65. Event Correlation

One important application capability shall be chronological event correlation.

Example:

```text
12:00:01 APP_TRANSITION
         com.example.notes

12:00:03 VIEW_FOCUSED

12:00:04 IME_STARTED

12:00:07 IME_TEXT_COMMIT
         "Android"

12:00:10 VIEW_CLICKED

12:00:13 APP_TRANSITION
         com.example.browser
```

---

# 66. Controlled Test Application

The project should contain a controlled application use screen/application containing:

```text
INPUT MONITORING TEST SCREEN

Normal Text
[________________]

Email
[________________]

Number
[________________]

Multiline
[________________]

Password
[________________]

PIN
[________________]

[SUBMIT]
```

This provides known ground truth for IME tests.

---

# 67. Test Case — Normal Input

### Procedure

```text
Start session

Open Application Test App

Select normal field

Enter:

HELLO ANDROID

Stop session
```

### Expected Result

The permitted synthetic application text is present in the session timeline.

---

# 68. Test Case — Password Protection

### Procedure

Select a password field and enter a synthetic test password.

### Expected Database Result

```text
event:
SENSITIVE_INPUT_DETECTED

classification:
PASSWORD

content:
NULL
```

The entered password shall not appear in persistent storage.

---

# 69. Test Case — PIN Protection

Enter a synthetic PIN into a controlled PIN/password field.

Expected:

```text
SENSITIVE_INPUT_DETECTED

classification:
PIN

content:
NULL
```

---

# 70. Test Case — Accessibility

Generate:

```text
Click
Focus
Scroll
Window change
Application transition
```

Expected:

Applicable permitted metadata events appear in the active monitoring session.

---

# 71. Test Case — Logger Failure

Artificially cause persistence failure.

Expected:

**Keyboard remains functional.**

---

# 72. Test Case — No Monitoring Session

Stop the monitoring session and continue typing.

Expected:

```text
Keyboard works normally.

Ordinary input content is not persistently
recorded by the application logger.
```

---

# 73. Test Case — Keyboard Switching

Change the current keyboard from:

```text
Custom Keyboard
       ↓
Gboard
```

Return to the application.

Expected:

```text
Custom Keyboard

Installed            ✓
Enabled              ✓
Currently Selected   ✕
```

---

# 74. Test Case — Accessibility Disabled

Disable the service through Android Settings.

Expected dashboard:

```text
Accessibility Service

✕ DISABLED

[ENABLE]
```

The dashboard shall offer the setup action, and the application shall not
silently reactivate or bypass Android's accessibility setting. Other
application functionality should remain available where possible.

---

# 75. Test Case — Event Pipeline

For a permitted event while a monitoring session is active, processing shall
follow this sequence:

```text
AccessibilityEvent or IME event
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
              Room
```

The pipeline shall preserve the following outcomes:

| Accessibility restriction | Test | Expected result |
|---|---|---|
| Service disabled | Disable the service in Android Settings | No accessibility events are collected; setup remains available |
| Service enabled, no active session | Enable the service without starting a session | No persistent application content is recorded |
| Service enabled, active session | Enable the service and start a session | Permitted, non-sensitive metadata may be persisted |
| Sensitive field detected | Use a password or PIN field during an active session | Credential content is removed before persistence |

# 76. Functional Requirement Summary

The final application shall provide:

```text
✓ Standard Android application

✓ First-run onboarding

✓ Runtime-permission workflow

✓ Accessibility setup workflow

✓ Custom Android IME

✓ Keyboard enablement workflow

✓ Android keyboard-selection workflow

✓ Current keyboard verification

✓ QWERTY keyboard

✓ Numbers and symbols

✓ Shift / capitalization

✓ Backspace

✓ Space

✓ Enter/action keys

✓ Keyboard switching

✓ AccessibilityService

✓ Click metadata

✓ Focus metadata

✓ Scroll metadata

✓ Window/application transitions

✓ Application sessions

✓ Unified event model

✓ Event processing

✓ Context resolution

✓ Privacy filter

✓ Sensitive-field detection

✓ Password/PIN content suppression

✓ Room database

✓ Dashboard

✓ Event timeline

✓ Search

✓ Filters

✓ Statistics

✓ CSV export

✓ Data deletion

✓ Offline operation
```

---

# 77. Acceptance Criteria

The system shall be considered functionally complete when:

**AC-01:** Application installs successfully.

**AC-02:** First launch displays application disclosure.

**AC-03:** Applicable Android runtime permissions use official permission mechanisms.

**AC-04:** Android recognizes the Custom Keyboard as an IME.

**AC-05:** The user can manually enable the keyboard.

**AC-06:** The application can open the appropriate keyboard configuration workflow.

**AC-07:** The application can invoke Android's keyboard-selection interface where supported.

**AC-08:** The user can select the Custom Keyboard.

**AC-09:** The application can determine whether its keyboard is currently selected.

**AC-10:** The keyboard functions in compatible applications.

**AC-11:** QWERTY input operates reliably.

**AC-12:** Shift, Backspace, Space and Enter operate correctly.

**AC-13:** The application provides an AccessibilityService configuration workflow.

**AC-14:** The user can manually enable AccessibilityService.

**AC-15:** Permitted accessibility interaction metadata is captured.

**AC-16:** Events contain appropriate timestamps.

**AC-17:** Application/package context is associated where available.

**AC-18:** Application sessions can be started.

**AC-19:** Application sessions can be paused.

**AC-20:** Application sessions can be stopped.

**AC-21:** Events are persisted locally in Room.

**AC-22:** Dashboard displays configuration status.

**AC-23:** Event viewer displays events chronologically.

**AC-24:** Events can be filtered.

**AC-25:** Events can be searched.

**AC-26:** Application statistics are generated.

**AC-27:** Application sessions can be exported.

**AC-28:** Application information can be deleted.

**AC-29:** Controlled non-sensitive synthetic input can be recorded during active monitoring sessions.

**AC-30:** Known password/PIN fields never persist entered credential contents.

**AC-31:** Sensitive content does not appear in exports or debug logs.

**AC-32:** Stopping event logging does not disable the keyboard.

**AC-33:** Failure of the event logger does not prevent normal keyboard input.

**AC-34:** Configuration changes are detected when returning from Android Settings.

**AC-35:** Permission denial does not trap the user in setup.

**AC-36:** Accessibility activation is never silently bypassed.

**AC-37:** Keyboard enablement is never silently bypassed.

**AC-38:** Keyboard selection remains under Android/user control.

---

# 78. Development Order

`IMPLEMENTATION_PLAN.md` (Phases 0–21) is the single authoritative
implementation roadmap. For the authoritative step-by-step phase sequence and
validation gates, refer to `IMPLEMENTATION_PLAN.md` (Phases 0–21).

At a high level, those phases group into:

| Milestone | Authoritative phases |
|---|---|
| Preparation and Android foundation | 0–3 |
| Keyboard and privacy-safe event capture | 4–9 |
| Setup and user-facing monitoring features | 10–17 |
| Performance, compatibility, and release validation | 18–21 |

---

# 79. Final End-to-End System

```text
                     INSTALL APK
                          │
                          ▼
                    Launch App
                          │
                          ▼
                Application Disclosure
                          │
                          ▼
                 Permission Setup
                          │
                          ▼
              Accessibility Setup
                          │
                          ▼
                Enable Application IME
                          │
                          ▼
                Select Application IME
                          │
                          ▼
                  Verify Setup
                          │
                          ▼
                     Dashboard
                          │
                          ▼
                Start Monitoring Session
                          │
             ┌────────────┴────────────┐
             │                         │
             ▼                         ▼
    AccessibilityService       Custom Keyboard
                               InputMethodService
             │                         │
      interaction                  permitted
       metadata                  monitored input
             │                         │
             └────────────┬────────────┘
                          ▼
                    EventProcessor
                          │
                          ▼
                     PrivacyFilter
                          │
                ┌─────────┴─────────┐
                │                   │
             NORMAL             SENSITIVE
                │                   │
                │                   ▼
                │             CONTENT DROPPED
                │                   │
                └─────────┬─────────┘
                          ▼
                   ContextResolver
                          │
                          ▼
                  EventRepository
                          │
                          ▼
                    Room Database
                          │
             ┌────────────┼────────────┐
             │            │            │
             ▼            ▼            ▼
         Timeline     Statistics     Export
             │            │            │
             └────────────┼────────────┘
                          ▼
                 Dashboard
```

---

# 80. Final Product Definition

The completed product shall be a **user-authorized Android interaction and IME application**.

After installation, the application shall guide the device owner through the required Android configuration. The user shall explicitly enable the AccessibilityService, enable the Custom Keyboard, and select that keyboard using Android's system-controlled interfaces.

Once selected, the Custom Keyboard shall serve as the current IME throughout compatible applications on the application device.

During explicitly started monitoring sessions, the system shall correlate permitted keyboard interaction events with interaction metadata obtained through AccessibilityService.

All events shall pass through centralized processing, context resolution and privacy filtering before being stored in the local Room database.

Known credential and sensitive fields—including passwords and PINs—shall never have their entered contents persisted.

The application shall provide the user with session management, event timelines, filtering, search, statistics, local export and deletion capabilities while preserving Android's user-controlled security boundaries.

---

**END OF SOFTWARE REQUIREMENTS SPECIFICATION**

**Document:** `SRS.md`  
**Version:** `1.0 Final`