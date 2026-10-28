# IMPLEMENTATION PLAN

## Android Interaction & Input Monitoring Application

**Document:** `IMPLEMENTATION_PLAN.md`  
**Version:** 1.0  
**Platform:** Android  
**Primary Language:** Kotlin  
**Purpose:** Define the implementation sequence, development workflow, documentation requirements, validation gates, optimization process, and project-memory rules.

---

# 1. Purpose

This document defines how the Android Interaction & Input Monitoring Application shall be implemented.

The project begins with these seven pre-development documents:

```text
1. SRS.md
2. Architecture.md
3. DEVELOPMENT_GUIDELINES.md
4. IMPLEMENTATION_PLAN.md
5. SECURITY.md
6. TESTING.md
7. PROGRESS.md
```

During Phase 0, create these three living project documents:

```text
8. decision.md
9. tech.md
10. flow.md
```

Before Phase 0 begins, the absence of `decision.md`, `tech.md`, and `flow.md` is intentional, not missing documentation. After Phase 0, all 10 documents form the maintained engineering documentation set. The three Phase-0 documents must evolve with the implementation.

---

# 2. Core Development Rule

The coding agent must NOT simply read the requirements once and begin coding until completion.

Development shall follow a continuous loop:

```text
READ PROJECT DOCUMENTS
        ↓
UNDERSTAND CURRENT STATE
        ↓
PLAN NEXT TASK
        ↓
IMPLEMENT
        ↓
TEST
        ↓
REVIEW
        ↓
OPTIMIZE
        ↓
VERIFY AGAINST SRS
        ↓
VERIFY AGAINST ARCHITECTURE
        ↓
VERIFY AGAINST DEVELOPMENT GUIDELINES
        ↓
UPDATE DOCUMENTATION
        ↓
UPDATE decision.md
        ↓
UPDATE tech.md IF NEEDED
        ↓
UPDATE flow.md IF NEEDED
        ↓
CONTINUE
```

---

# 3. Documentation Lifecycle and Source of Truth

Before development, the project shall contain seven supplied Markdown documents:

```text
PRE-DEVELOPMENT DOCUMENTS

1. SRS.md
2. Architecture.md
3. DEVELOPMENT_GUIDELINES.md
4. IMPLEMENTATION_PLAN.md
5. SECURITY.md
6. TESTING.md
7. PROGRESS.md
```

During Phase 0, create:

```text
CREATED DURING PHASE 0

8. decision.md
9. tech.md
10. flow.md
```

Each document has a different responsibility.

---

# 4. SRS.md

`SRS.md` defines:

> WHAT THE APPLICATION MUST DO.

It contains:

- functional requirements;
- non-functional requirements;
- keyboard requirements;
- accessibility requirements;
- sensitive-input and data-protection requirements;
- setup requirements;
- monitoring-session requirements;
- database requirements;
- UI requirements;
- acceptance criteria.

The coding agent shall repeatedly check implementation against `SRS.md`.

---

# 5. Architecture.md

`Architecture.md` defines:

> HOW THE SYSTEM IS STRUCTURED.

It contains:

- layers;
- modules;
- components;
- services;
- event pipeline;
- database architecture;
- sensitive-input protection architecture;
- dependency relationships;
- lifecycle behavior;
- implementation boundaries.

Implementation should follow this architecture unless a justified architectural change is documented.

---

# 6. DEVELOPMENT_GUIDELINES.md

`DEVELOPMENT_GUIDELINES.md` defines:

> HOW CODE MUST BE WRITTEN, REVIEWED AND OPTIMIZED.

It contains rules covering:

- low-end devices;
- memory;
- CPU;
- battery;
- APK size;
- database efficiency;
- event processing;
- dependency management;
- code cleanup;
- performance testing;
- optimization.

The coding agent must consult it before marking any phase complete.

---

# 7. IMPLEMENTATION_PLAN.md

This document defines:

> IN WHAT ORDER DEVELOPMENT SHALL OCCUR AND HOW DEVELOPMENT SHALL BE MANAGED.

It is the roadmap for implementation.

---

# 8. decision.md

`decision.md` shall answer:

> WHAT DID WE DECIDE, AND WHY?

It shall maintain a chronological record of meaningful technical decisions.

It exists so future agents do not unknowingly reverse or duplicate earlier decisions.

---

# 9. tech.md

`tech.md` shall answer:

> WHAT TECHNOLOGY ARE WE USING, AND WHY?

It shall maintain the actual current technology stack.

It must reflect implementation reality, not merely the originally proposed stack.

---

# 10. flow.md

`flow.md` shall answer:

> HOW DOES THE APPLICATION CURRENTLY FLOW?

It shall document:

- user flow;
- setup flow;
- keyboard flow;
- accessibility flow;
- event flow;
- sensitive-input handling flow;
- database flow;
- monitoring-session flow;
- export flow.

It must remain synchronized with the actual implementation.

---

# 11. Mandatory Agent Startup Procedure

Once Phase 0 begins, whenever a coding agent begins or resumes work, it shall first inspect:

```text
1. PROGRESS.md
2. decision.md
3. tech.md
4. flow.md
5. relevant SRS.md sections
6. relevant Architecture.md sections
7. relevant SECURITY.md sections
8. relevant TESTING.md sections
9. relevant DEVELOPMENT_GUIDELINES.md sections
10. relevant IMPLEMENTATION_PLAN.md sections
```

During the initial documentation-only state, the three Phase-0 files do not yet exist and must not be treated as an error. Before Phase 0, read the seven supplied documents as applicable.

Then inspect the existing source tree and current implementation.

The agent must understand:

```text
What has already been implemented?

What is partially implemented?

What remains?

What decisions were previously made?

What technology is currently installed?

What is the current application flow?

Are the documents consistent with the code?
```

Only then should new implementation begin.

---

# 12. Documentation Consistency Check

Before beginning a new phase:

```text
SOURCE CODE
     │
     ▼
Compare with SRS.md
     │
     ▼
Compare with Architecture.md
     │
     ▼
Compare with decision.md
     │
     ▼
Compare with tech.md
     │
     ▼
Compare with flow.md
```

If documentation is stale, update it.

Do not knowingly continue development with misleading project documentation.

---

# 13. Documentation Update Rule

Documentation shall be updated when the implementation changes.

Do not wait until the entire project is complete.

Examples:

```text
Added Room
→ update tech.md

Changed event architecture
→ update Architecture.md
→ update decision.md
→ update flow.md

Added new requirement
→ update SRS.md

Changed implementation order
→ update IMPLEMENTATION_PLAN.md

Added dependency
→ update tech.md
→ possibly decision.md

Changed onboarding
→ update flow.md

Changed performance strategy
→ update DEVELOPMENT_GUIDELINES.md
→ possibly decision.md
```

---

# 14. decision.md Format

Use a consistent structure.

Example:

```markdown
# Technical Decisions

## DEC-001 — Use Room for Local Event Storage

Date:
2026-XX-XX

Status:
Accepted

Context:
The application requires structured local storage for
monitoring sessions and large numbers of timestamped events.

Decision:
Use Android Room over manually managed SQLite.

Reason:
- compile-time query verification
- Flow support
- Android lifecycle integration
- migrations
- maintainability
- appropriate performance for structured event storage

Alternatives Considered:
- raw SQLite
- JSON files
- Realm

Consequences:
Positive:
- easier structured queries
- good integration with Kotlin

Negative:
- adds some application size
- requires schema management

Related:
Architecture.md — Database Architecture
tech.md — Persistence
```

---

# 15. Decision IDs

Use sequential identifiers:

```text
DEC-001
DEC-002
DEC-003
DEC-004
...
```

Never reuse IDs.

---

# 16. What Belongs in decision.md

Record meaningful decisions such as:

```text
Why Room was selected

Why Compose was selected

Why Hilt was or was not selected

Why a dependency was added

Why a dependency was removed

Why an event buffer has a particular size

Why event coalescing was introduced

Why a schema changed

Why an architecture changed

Why minSdk changed

Why targetSdk changed

Why a privacy rule was strengthened

Why an alternative approach was rejected

Why a performance optimization was introduced
```

---

# 17. What Does NOT Need a Decision Entry

Do not create excessive entries for trivial actions such as:

```text
renamed local variable
fixed typo
changed button padding
removed unused import
formatted file
```

`decision.md` is for decisions future developers need to understand.

---

# 18. Decision Reversal

Do not delete old decisions when they change.

Instead:

```markdown
## DEC-014 — Replace Library X with Native Android API

Status:
Accepted

Supersedes:
DEC-006

Reason:
...
```

The historical reasoning must remain available.

---

# 19. tech.md Structure

Recommended format:

```markdown
# Technology Stack

## Platform

Android

## Language

Kotlin

Reason:
...

## UI

Jetpack Compose

Reason:
...

## Architecture

MVVM + Repository

Reason:
...

## Database

Room

Reason:
...

## Concurrency

Kotlin Coroutines + Flow

Reason:
...

## Configuration

DataStore

Reason:
...

## Dependency Injection

Hilt

Reason:
...

## Keyboard

InputMethodService

Reason:
Native Android IME framework.

## Interaction Monitoring

AccessibilityService

Reason:
Official Android framework for permitted
accessibility-event observation.

## Build

Gradle Kotlin DSL

Reason:
...

## Testing

...

## Export

CSV

## Dependencies

| Dependency | Purpose | Reason | Required? |
|------------|---------|--------|-----------|
| Room | Database | Structured local persistence | Yes |
| ... | ... | ... | ... |
```

---

# 20. tech.md Accuracy Rule

Before `tech.md` exists, technology choices listed in `SRS.md` and `Architecture.md` are recommended defaults unless they are fundamental Android platform requirements. During Phase 0, evaluate the proposed stack for correctness, maintainability, APK size, memory, CPU, battery, dependency overhead, Android platform support, and testing requirements.

Once created, `tech.md` must describe what the project ACTUALLY uses and is authoritative for the implemented stack.

For example:

If Architecture originally recommends Hilt but implementation determines manual dependency injection is substantially smaller and sufficient:

```text
Do not leave:

tech.md:
Hilt

while source code:
manual DI
```

Instead:

1. document the decision in `decision.md`;
2. update `tech.md`;
3. update `Architecture.md` if architectural implications exist.

---

# 21. Dependency Tracking

Every meaningful external dependency shall be documented in `tech.md`.

Include:

```text
Name
Version
Purpose
Why required
Possible native alternative
Size/performance implications if significant
```

This is particularly important because application size must remain small.

---

# 22. flow.md Structure

`flow.md` shall primarily use readable diagrams.

It should include at minimum:

```text
1. Overall Application Flow

2. First Installation Flow

3. Permission Flow

4. Accessibility Setup Flow

5. Keyboard Setup Flow

6. Keyboard Runtime Flow

7. Monitoring Session Flow

8. Accessibility Event Flow

9. IME Event Flow

10. Sensitive Input Flow

11. Database Flow

12. Dashboard Flow

13. Export Flow

14. Error/Failure Flow
```

---

# 23. Overall Application Flow

`flow.md` should maintain something equivalent to:

```text
INSTALL
   ↓
FIRST LAUNCH
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

Update this whenever actual behavior changes.

---

# 24. Keyboard Runtime Flow

Maintain:

```text
TEXT FIELD
    ↓
ANDROID STARTS IME
    ↓
EditorInfoAnalyzer
    ↓
SensitiveInputDetector
    ↓
KeyboardController
    ↓
USER PRESSES KEY
    ↓
KeyProcessor
    │
    ├───────────────→ InputConnection
    │                       ↓
    │                  TARGET APP
    │
    └───────────────→ Interaction Event Candidate
                            ↓
                      Session Gate
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
                         Room / Local Storage
```

---

# 25. Sensitive Input Flow

This flow must always remain documented.

```text
ACTIVE EDITOR
     ↓
EditorInfo
     ↓
SensitiveInputDetector
     ↓
┌────┴──────────┐
│               │
NORMAL       SENSITIVE
│               │
▼               ▼
Interaction Event    CONTENT
Candidate             DISCARDED
│               │
└──────┬────────┘
       ↓
Event Processor
       ↓
Repository
```

Any architectural change affecting this flow requires documentation review.

---

# 26. Documentation Recheck Frequency

At minimum, recheck project documents:

```text
At project startup

Before every phase

After every major feature

After architectural changes

After dependency changes

After database changes

After flow changes

After optimization passes

Before commits/milestones where practical

Before release builds

At final completion
```

---

# 27. Phase 0 — Repository Preparation

Phase 0 shall follow this sequence:

```text
1. Verify the seven supplied pre-development documents.

2. Initialize decision.md.

3. Evaluate the recommended technology stack.

4. Record meaningful technology/architecture decisions in decision.md.

5. Initialize tech.md with the selected actual technology stack.

6. Initialize flow.md with the initial planned application/system flows.

7. Create the Android project using the recorded Phase-0 decisions.

8. Establish the initial package/project structure.

9. Verify .gitignore.

10. Verify the build environment.

11. Produce a clean baseline debug build.

12. Produce/verify the initial release-build baseline where practical.

13. Record baseline APK/application size.

14. Update decision.md, tech.md, and flow.md with discoveries from project initialization.

15. Update PROGRESS.md LAST.
```

The seven supplied documents are:

```text
SRS.md
Architecture.md
DEVELOPMENT_GUIDELINES.md
IMPLEMENTATION_PLAN.md
SECURITY.md
TESTING.md
PROGRESS.md
```

Then create the three Phase-0 living documents:

```text
decision.md
tech.md
flow.md
```

During Phase 0, explicitly evaluate the encrypted-storage strategy:

```text
1. Encryption-at-rest mechanism
2. Cryptographic key generation
3. Key protection and Android Keystore use
4. Supported Android versions
5. External dependency requirements
6. APK-size, CPU, RAM, battery, and low-end-device impact
7. Behavior when key material becomes unavailable
8. Application reinstall behavior
9. Backup/restore strategy
10. Database migrations
11. Encryption testing
```

Record the resulting decisions in `decision.md` and `tech.md`, and update `Architecture.md` if the selected mechanism changes the planned architecture.

Phase 0 shall also verify the Android platform constraints that affect the
architecture and build baseline:

```text
1. Select and record minSdk, targetSdk, and compileSdk, including the supported
   Android-version test matrix.
2. Confirm manifest declarations, service metadata, exported flags, and intent
   filters for MainActivity, InputMethodService, and AccessibilityService.
3. Confirm that keyboard enablement, keyboard selection, and accessibility
   activation remain user-mediated Android Settings flows; the application
   must not assume it can grant or silently restore them.
4. Verify AccessibilityService configuration, event types, feedback type,
   package scope, and platform restrictions against the intended use case.
5. Verify InputMethodService lifecycle behavior, InputConnection delivery, and
   keyboard operation when Room, repositories, or event processing fail.
6. Decide whether protected Room files and temporary export data participate in
   Android backup, and document restore, reinstall, and key-unavailable behavior.
7. Confirm background-execution, process-recreation, reboot, and battery
   constraints without introducing an unrequested foreground service.
8. Validate release-manifest shrinking, signing configuration, and clean
   debug/release builds before treating the baseline as reproducible.
```

These checks are evaluation gates for Phase 0, not permission to bypass Android
platform controls. Unresolved platform behavior shall be recorded as an
explicit decision or follow-up risk in the Phase-0 living documents.

---

# 28. Phase 0 Deliverables

The Phase 0 deliverables are:

```text
Seven supplied documents verified

decision.md initialized

Technology stack evaluated

Meaningful initial decisions recorded

Encrypted-storage strategy evaluated

tech.md initialized

flow.md initialized

Android project created

Project/package structure established

.gitignore verified

Build environment verified

Clean baseline build produced

Initial artifact-size baseline recorded

PROGRESS.md updated
```

---

# 29. Phase 1 — Android Foundation

Implement:

```text
Android application
Kotlin
Compose
Navigation
Theme
Base package structure
Dependency configuration
```

Do NOT implement every feature immediately.

First establish a clean build.

---

# 30. Phase 1 Validation

Verify:

```text
App installs

App launches

No startup crash

Navigation works

Release build can be produced
```

Then:

```text
Review dependencies

Check APK size baseline

Remove unused template code

Remove unused resources

Update tech.md

Update decision.md

Update flow.md
```

Record the initial APK size as a baseline.

---

# 31. Phase 2 — Data Foundation

Implement:

```text
Domain event models

Room database

MonitoringSessionEntity

InteractionEventEntity

DAOs

Repository

Entity/domain mappers
```

---

# 32. Phase 2 Validation

Test:

```text
Create session

Insert event

Read event

Filter event

Delete event

Delete session

Database migration strategy
```

Review indexes and query patterns.

Update:

```text
Architecture.md
decision.md
tech.md
flow.md
```

where required.

---

# 33. Phase 3 — Session Management

Implement:

```text
SessionManager

Start session

Pause session

Resume session

Stop session

Current-session state
```

The session manager shall become the authority for whether persistent application collection is active.

---

# 34. Phase 3 Validation

Test lifecycle:

```text
NONE
 ↓
ACTIVE
 ↓
PAUSED
 ↓
ACTIVE
 ↓
STOPPED
```

Verify events cannot incorrectly attach to the wrong session.

---

# 35. Phase 4 — Basic Custom Keyboard

Implement the real Android IME.

Start with minimum functionality:

```text
InputMethodService

IME metadata

Basic keyboard UI

QWERTY

Space

Backspace

Enter
```

Do not add application recording yet.

First make the keyboard reliable.

---

# 36. Phase 4 Success Gate

The keyboard must work in multiple compatible applications.

Test:

```text
Application test app

Notes/text editor

Browser text field
```

Verify:

```text
typing
deletion
space
enter
keyboard activation
keyboard switching
```

---

# 37. Phase 5 — Complete Keyboard

Implement:

```text
Shift

Caps Lock

Numbers

Symbols

Editor-specific action key

Email-oriented layout where appropriate

URL-oriented behavior where appropriate

Numeric layout

Multiline behavior
```

Optimize before proceeding.

---

# 38. Keyboard Performance Gate

Before event logging is connected, verify:

```text
No noticeable typing lag

No main-thread database operations

No unnecessary allocations

No unnecessary recomposition

No large dependencies added only for keyboard UI
```

Record relevant decisions.

---

# 39. Phase 6 — Editor Analysis and Privacy

Implement:

```text
EditorInfoAnalyzer

FieldCategory

InputClassification

SensitiveInputDetector

PrivacyFilter
```

This phase must be completed BEFORE persistent IME application content is enabled.

---

# 40. Phase 6 Mandatory Tests

Test:

```text
Normal text

Email

URL

Number

Multiline

Password

Numeric password/PIN

Unknown editor
```

Password/PIN test contents must not reach:

```text
Room

Logcat

Export

Debug files
```

---

# 41. Privacy Gate

Development shall NOT proceed to persistent IME monitored input until privacy tests pass.

This is a hard implementation gate.

---

# 42. Phase 7 — IME Event Pipeline

Implement:

```text
ImeEventPublisher

RawInteractionEvent

EventProcessor

Session Gate

ContextResolver

Repository integration
```

The keyboard input path must remain independent.

---

# 43. Phase 7 Required Architecture

```text
KEY
 │
 ├─────────────→ INPUT CONNECTION → APP
 │
 └─────────────→ EVENT PIPELINE
```

Never:

```text
KEY
 ↓
APPLICATION DATABASE
 ↓
APP
```

---

# 44. Phase 7 Failure Test

Artificially break event persistence.

Expected:

```text
Keyboard continues typing.
```

This test is mandatory.

---

# 45. Phase 8 — AccessibilityService

Implement:

```text
AppAccessibilityService

Accessibility configuration

AccessibilityEventFilter

AccessibilityEventMapper
```

Begin with minimal required event categories.

---

# 46. Accessibility Optimization

The callback shall:

```text
Receive event
    ↓
Extract minimum required metadata
    ↓
Create lightweight event
    ↓
Queue asynchronously
    ↓
Return
```

Avoid expensive processing inside Android callbacks.

---

# 47. Phase 9 — Unified Event Processing

Connect:

```text
IME
 │
 └──────┐
        ▼
  EventProcessor
        ▲
        │
Accessibility
```

All persistent interaction events shall share:

```text
Session Gate

Privacy Filter

Context Resolver

Repository
```

---

# 48. Phase 10 — First-Run Setup

Implement:

```text
Welcome screen

Application disclosure

Permission coordinator

Accessibility setup

Keyboard enablement

Keyboard selection

Configuration verification

SetupStateManager
```

---

# 49. Setup Flow Validation

Test:

```text
Fresh installation

Permission accepted

Permission denied

Accessibility enabled

Accessibility skipped

Keyboard enabled

Keyboard not enabled

Custom Keyboard selected

Different keyboard selected

Return from Settings

App restart
```

Update `flow.md` to reflect actual behavior.

---

# 50. Phase 11 — Dashboard

Implement:

```text
System status

Keyboard status

Accessibility status

Session status

Current application

Event counters

Start session

Pause session

Stop session
```

The dashboard should use aggregate state rather than loading entire event histories.

---

# 51. Phase 12 — Event Timeline

Implement:

```text
Chronological event viewer

Paging

Session filter

Package filter

Event-type filter

Source filter

Classification filter

Time filter
```

Do not load unlimited events into RAM.

---

# 52. Phase 13 — Search

Implement efficient database-backed search for permitted application fields.

Review query plans and indexes.

Do not perform large in-memory searches when Room can perform the filtering.

---

# 53. Phase 14 — Statistics

Implement:

```text
Total events

IME events

Accessibility events

Events per application

Clicks

Focus events

Scroll events

Window events

Sensitive suppression count
```

Prefer database aggregate queries.

---

# 54. Phase 15 — Export

Implement:

```text
CSV export

Streaming/chunked writing

Explicit user action

Final privacy validation
```

Never assume database contents alone are sufficient protection.

Validate sensitive classification again during export.

---

# 55. Phase 16 — Data Management

Implement:

```text
Delete session

Delete all sessions

Delete application data

Confirmation dialogs
```

Verify associated event cleanup.

---

# 56. Phase 17 — Controlled Application Use Test Screen

Implement a controlled test environment:

```text
Normal Text

Email

URL

Number

Multiline

Password

PIN
```

This becomes the known ground truth for IME tests.

---

# 57. Phase 18 — Performance Optimization

Perform a dedicated optimization pass.

Review:

```text
CPU

RAM

Battery

APK size

Database size

Keyboard latency

Accessibility processing

Startup time

Event processing

UI performance

Export performance
```

---

# 58. Dead Code Review

Search for:

```text
Unused classes

Unused functions

Unused resources

Unused dependencies

Duplicate utilities

Temporary code

Commented code

Old architecture remnants
```

Remove them.

---

# 59. Dependency Review

For every dependency:

```text
Still used?

Necessary?

Can Android SDK replace it?

Can Kotlin replace it?

Does it materially increase APK size?

Does it initialize background functionality?
```

Update `tech.md` after cleanup.

---

# 60. Low-End Device Optimization

Specifically test:

```text
Typing while event collection is active

Heavy scrolling

Large event database

Dashboard

Timeline

Search

Statistics

Export
```

The application should remain usable under constrained resources.

---

# 61. Phase 19 — Large Database Testing

Test progressively:

```text
1,000 events

10,000 events

50,000 events

100,000 events
```

Measure:

```text
Insert latency

Query latency

Timeline loading

Search

Statistics

Export

Database size

Memory
```

Optimize measured bottlenecks.

---

# 62. Phase 20 — Android Version Testing

Where devices/emulators are available, test:

```text
Android 15

Android 16

Android 17
```

Record:

```text
Android version

Device/OEM

Security patch

IME behavior

Accessibility behavior

Setup differences

Restrictions

Performance
```

Update documentation where behavior differs.

---

# 63. Phase 21 — Release Optimization

Create release configuration.

Review:

```text
R8

Resource shrinking

Unused resources

Release logging

Debug flags

APK size

Signing configuration

Crash behavior
```

Do not blindly apply shrinker rules without testing the IME and AccessibilityService in the release build.

---

# 64. Release Build Test

The actual optimized release build must be tested.

Do not assume:

```text
Debug works
=
Release works
```

Verify:

```text
IME discovery

IME selection

Keyboard

AccessibilityService

Room

Privacy filter

Dashboard

Export
```

---

# 65. Continuous Review Cycle

Every implementation phase shall finish with:

```text
IMPLEMENT
   ↓
BUILD
   ↓
TEST
   ↓
REVIEW CODE
   ↓
REMOVE DEAD CODE
   ↓
OPTIMIZE
   ↓
RETEST
   ↓
CHECK SRS
   ↓
CHECK ARCHITECTURE
   ↓
CHECK DEVELOPMENT GUIDELINES
   ↓
UPDATE decision.md
   ↓
UPDATE tech.md
   ↓
UPDATE flow.md
   ↓
MARK PHASE COMPLETE
```

---

# 66. Do Not Blindly Follow Old Documentation

Documentation must guide development, but implementation discoveries may justify changes.

If the agent discovers a better solution:

```text
Identify issue
     ↓
Evaluate alternatives
     ↓
Choose solution
     ↓
Document WHY in decision.md
     ↓
Update Architecture.md if needed
     ↓
Update tech.md if needed
     ↓
Update flow.md if needed
     ↓
Update SRS.md if requirement genuinely changed
     ↓
Implement
```

Do not silently diverge from documentation.

---

# 67. SRS Modification Rule

Do not casually change requirements merely because implementation is difficult.

If an SRS requirement cannot reasonably be implemented:

1. identify the requirement;
2. investigate Android limitations;
3. document the limitation;
4. record the technical decision;
5. modify the SRS only when justified.

---

# 68. Architecture Modification Rule

Architecture may evolve.

However:

```text
ARCHITECTURE CHANGE
        ↓
decision.md
        ↓
Architecture.md
        ↓
flow.md
        ↓
Implementation
```

Do not leave obsolete diagrams describing architecture that no longer exists.

---

# 69. Tech Stack Modification Rule

When technology changes:

```text
Technology change
      ↓
decision.md
      ↓
tech.md
      ↓
Dependency cleanup
      ↓
Architecture.md if relevant
      ↓
Build/test
```

---

# 70. Flow Modification Rule

Whenever user or system behavior changes, update `flow.md`.

Examples:

```text
Setup order changed

New screen added

Permission behavior changed

Keyboard setup changed

Session lifecycle changed

Event pipeline changed

Export process changed
```

---

# 71. Code Must Be the Reality Check

If documentation says:

```text
EventProcessor → PrivacyFilter → Repository
```

but source code actually does:

```text
Service → DAO
```

this is a serious architecture/documentation mismatch.

Either fix the implementation or formally update the architecture with justification.

Never ignore the mismatch.

---

# 72. Progress Tracking

Maintain a project checklist.

Example:

```text
[✓] Foundation
[✓] Database
[✓] Sessions
[✓] Basic IME
[ ] Advanced keyboard
[ ] Privacy classification
[ ] IME event pipeline
[ ] Accessibility
[ ] Setup
[ ] Dashboard
[ ] Timeline
[ ] Search
[ ] Statistics
[ ] Export
[ ] Optimization
[ ] Android version tests
[ ] Release validation
```

This may live inside `IMPLEMENTATION_PLAN.md` or a separate project tracker if necessary.

---

# 73. Definition of Phase Complete

A phase is complete only when:

```text
Implementation exists

Build succeeds

Relevant tests pass

Code reviewed

No obvious dead code

Performance reviewed

Privacy impact reviewed

Documentation synchronized

decision.md updated if required

tech.md updated if required

flow.md updated if required
```

Writing code alone does not complete a phase.

---

# 74. Final Documentation Audit

Before final release, compare all seven documents:

```text
SRS.md
Architecture.md
DEVELOPMENT_GUIDELINES.md
IMPLEMENTATION_PLAN.md
decision.md
tech.md
flow.md
```

against the actual source code.

Check:

```text
Are requirements accurate?

Is architecture accurate?

Is the tech stack accurate?

Are flows accurate?

Are decisions recorded?

Are optimization rules satisfied?

Does the implementation plan reflect completed work?
```

---

# 75. Final Source Audit

Review the entire project for:

```text
Dead code

Duplicate code

Unused dependencies

Unused resources

Debug code

Temporary files

Hardcoded test values

Memory leaks

Unbounded queues

Unbounded caches

Blocking calls

Inefficient Room queries

Excessive logging

Sensitive-data exposure

Oversized resources
```

---

# 76. Final Privacy Audit

Run controlled synthetic tests against:

```text
Normal field

Email field

URL field

Numeric field

Password field

PIN field
```

Inspect:

```text
Room database

Logcat

Export

Debug output

Temporary files
```

Credential-oriented test content must not be persistently present.

---

# 77. Final Performance Audit

Measure:

```text
APK size

Startup time

Idle RAM

Active-session RAM

Long-session RAM

Keyboard responsiveness

Event throughput

Database size

Timeline performance

Search performance

Statistics performance

Export performance

Battery behavior
```

Record important findings in `decision.md` where they lead to engineering decisions.

---

# 78. Final Low-End Device Audit

The final application should be tested on at least one constrained Android environment where practical.

The following must remain usable:

```text
Keyboard

Application session

Accessibility processing

Dashboard

Timeline

Search

Statistics

Export
```

---

# 79. Final Release Gate

Do NOT declare the project finished until:

```text
[ ] SRS acceptance criteria reviewed

[ ] Architecture matches implementation

[ ] DEVELOPMENT_GUIDELINES reviewed

[ ] IMPLEMENTATION_PLAN completed

[ ] decision.md current

[ ] tech.md current

[ ] flow.md current

[ ] Unit tests pass

[ ] Integration tests pass

[ ] Privacy tests pass

[ ] Keyboard reliability tests pass

[ ] Accessibility tests pass

[ ] Database stress tests pass

[ ] Release build tested

[ ] APK size reviewed

[ ] Memory reviewed

[ ] Battery behavior reviewed

[ ] Dead code removed

[ ] Dependencies reviewed

[ ] Sensitive-data audit passed
```

---

# 80. Coding Agent Master Instruction

The following instruction applies throughout the entire project:

> Before implementing a feature, inspect the relevant requirements, architecture, previous decisions, current technology stack, and current application flow.

> Do not blindly add code. Understand the existing implementation first.

> After implementing a feature, build and test it. Review the changed code for unnecessary complexity, duplication, dead code, excessive allocations, inefficient database operations, unnecessary dependencies, main-thread work, memory growth, battery impact, and application-size impact.

> Continuously optimize for lower-configuration Android devices without compromising correctness, application validity, privacy controls, or keyboard responsiveness.

> Keep `SRS.md`, `Architecture.md`, `DEVELOPMENT_GUIDELINES.md`, and `IMPLEMENTATION_PLAN.md` synchronized with significant implementation changes.

> Continuously maintain `decision.md` as the historical record of important technical decisions and their reasoning.

> Continuously maintain `tech.md` as the authoritative description of the technology stack actually used by the project and why each major technology or dependency was chosen.

> Continuously maintain `flow.md` as the authoritative representation of the application's actual user flows, system flows, keyboard flows, accessibility flows, sensitive-input handling flows, monitoring-session flows, data flows, and failure flows.

> Re-read these documents regularly during development rather than only at project initialization.

> If the implementation and documentation disagree, investigate the mismatch immediately. Either correct the implementation or update the relevant documentation with a justified decision. Never knowingly allow documentation and source code to diverge.

---

# 81. Agent Context Recovery Procedure

If development is resumed in a new coding-agent session, the agent shall NOT immediately begin changing code.

First:

```text
READ
 │
 ├── SRS.md
 ├── Architecture.md
 ├── DEVELOPMENT_GUIDELINES.md
 ├── IMPLEMENTATION_PLAN.md
 ├── decision.md
 ├── tech.md
 └── flow.md
       │
       ▼
INSPECT PROJECT TREE
       │
       ▼
INSPECT BUILD FILES
       │
       ▼
INSPECT CURRENT IMPLEMENTATION
       │
       ▼
RUN BUILD / RELEVANT TESTS
       │
       ▼
IDENTIFY CURRENT PHASE
       │
       ▼
CONTINUE DEVELOPMENT
```

This procedure exists so a new agent/session can reconstruct the project's technical reasoning instead of making contradictory decisions.

---

# 82. Documentation Ownership

The coding agent is responsible for maintaining documentation as part of development.

Documentation updates are not something the user must manually request after every change.

If code changes materially affect:

```text
Requirements
Architecture
Technology
Application flow
Performance strategy
Privacy behavior
Implementation sequence
```

the corresponding Markdown document shall be updated during the same development task.

---

# 83. Expected Final Repository

A completed project should resemble:

```text
project/
│
├── SRS.md
├── Architecture.md
├── DEVELOPMENT_GUIDELINES.md
├── IMPLEMENTATION_PLAN.md
├── decision.md
├── tech.md
├── flow.md
│
├── README.md
│
├── app/
│   └── src/
│       ├── main/
│       ├── test/
│       └── androidTest/
│
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── .gitignore
```

---

# 84. Document Relationship

The project's documentation hierarchy is:

```text
                    SRS.md
                 WHAT TO BUILD
                      │
                      ▼
                Architecture.md
                HOW TO BUILD IT
                      │
                      ▼
           IMPLEMENTATION_PLAN.md
                WHEN / IN WHAT
                 ORDER TO BUILD
                      │
                      ▼
       DEVELOPMENT_GUIDELINES.md
             HOW TO KEEP IT
          CLEAN AND EFFICIENT


        LIVING PROJECT KNOWLEDGE

      ┌────────────┬────────────┐
      │            │            │
      ▼            ▼            ▼
 decision.md     tech.md      flow.md

 WHAT/WHY       WHAT TECH     HOW IT
 WE DECIDED     WE USE        FLOWS
```

All seven documents together constitute the project's persistent engineering context.

---

# 85. Final Principle

The objective is not simply to generate a large amount of Android code.

The objective is to produce a system that is:

```text
CORRECT
   +
DOCUMENTED
   +
TRACEABLE
   +
PRIVACY-AWARE
   +
TESTABLE
   +
MAINTAINABLE
   +
LIGHTWEIGHT
   +
RESPONSIVE
   +
RESOURCE-EFFICIENT
```

Every major implementation decision should therefore be understandable later by reading:

```text
SRS.md
Architecture.md
DEVELOPMENT_GUIDELINES.md
IMPLEMENTATION_PLAN.md
decision.md
tech.md
flow.md
```

A future coding agent should be able to open these documents, inspect the repository, understand what has already been built, understand why major decisions were made, and safely continue development without starting the architectural reasoning again.

---

**END OF IMPLEMENTATION PLAN**

**Document:** `IMPLEMENTATION_PLAN.md`  
**Version:** `1.0`