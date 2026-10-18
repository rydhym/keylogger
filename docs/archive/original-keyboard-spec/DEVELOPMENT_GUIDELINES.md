# DEVELOPMENT GUIDELINES

## Android Interaction & Input Monitoring Application

**Document:** `DEVELOPMENT_GUIDELINES.md`  
**Purpose:** Mandatory development, optimization, performance, code-quality, and application-size requirements.

---

# 1. Purpose

This document defines mandatory engineering rules that must be followed throughout development.

The application contains long-running Android components such as:

- `InputMethodService`
- `AccessibilityService`
- event processing
- Room database operations
- monitoring sessions
- dashboard/statistics

Therefore, efficiency is a primary requirement.

The application must remain usable on relatively low-configuration Android devices and must avoid unnecessary:

- CPU usage;
- RAM usage;
- storage usage;
- battery consumption;
- database operations;
- background processing;
- dependencies;
- APK size;
- UI recompositions;
- object allocations;
- logging.

Optimization must be performed continuously rather than postponed until the project is complete.

---

# 2. Core Development Rule

After implementing every significant feature, the coding agent MUST review the affected code.

The cycle shall be:

```text
IMPLEMENT
    ↓
VERIFY CORRECTNESS
    ↓
TEST
    ↓
REVIEW
    ↓
REMOVE UNNECESSARY CODE
    ↓
OPTIMIZE
    ↓
RETEST
    ↓
CHECK RESOURCE USAGE
    ↓
CONTINUE
```

A feature is not considered complete merely because it works.

It must also be reasonably efficient and maintainable.

---

# 3. Optimization Priorities

When optimizing, use the following priority:

```text
1. Correctness
2. Privacy/security requirements
3. Stability
4. Keyboard responsiveness
5. Accessibility callback responsiveness
6. Memory efficiency
7. Battery efficiency
8. Database efficiency
9. Application size
10. Code simplicity
```

Never remove required privacy or security checks merely to improve performance.

---

# 4. Low-End Device Requirement

Development decisions shall assume that the application may run on a device with:

```text
Limited RAM
Slower CPU
Slower flash storage
Limited free storage
Aggressive background-process management
Restricted battery capacity
```

Do not design the application assuming flagship hardware.

---

# 5. Mandatory Code Review

After each feature, inspect modified code for:

```text
Unused functions
Unused classes
Unused imports
Unused resources
Unused dependencies
Duplicate logic
Duplicate models
Dead code
Temporary debugging code
Commented-out code
Unnecessary abstractions
Unnecessary allocations
Repeated database queries
Repeated PackageManager calls
Blocking operations
Memory leaks
Unbounded collections
Unbounded queues
```

Remove unnecessary code before proceeding.

---

# 6. Simplicity Rule

Prefer the simplest implementation that correctly satisfies the SRS and Architecture.

Avoid:

```text
Unnecessary design patterns
Unnecessary interfaces
Unnecessary wrapper classes
Excessive inheritance
Excessive abstraction
Huge utility classes
Duplicated data models
Premature frameworks
```

Do not create five layers where two are sufficient.

Architecture should provide separation of concerns without unnecessary complexity.

---

# 7. Dependency Policy

Every external dependency increases:

- APK size;
- build complexity;
- potential memory usage;
- maintenance requirements;
- attack surface.

Before adding a dependency, determine whether Android/Kotlin already provides the required functionality.

Prefer:

```text
Android SDK
Kotlin standard library
AndroidX
Existing project dependencies
```

before adding another library.

---

# 8. Dependency Review

Periodically inspect dependencies.

Ask:

```text
Is this library still used?

Can Android SDK replace it?

Can Kotlin replace it?

Does another existing dependency already provide this?

How much does it increase APK size?

Does it initialize anything automatically?

Does it add background components?
```

Remove unnecessary dependencies.

---

# 9. APK Size

APK/application size shall be treated as a measurable engineering metric.

The development agent shall periodically check:

```text
APK size
AAB size where applicable
DEX contribution
Native library contribution
Resource contribution
Dependency contribution
```

Investigate unexpected increases.

---

# 10. Release Optimization

Release builds should use appropriate Android build optimization.

Where compatible with the application:

```text
minifyEnabled = true
shrinkResources = true
```

R8/resource shrinking should be used for release builds after testing confirms required classes/resources are retained.

Debug builds may remain unoptimized for development.

---

# 11. Resource Optimization

Avoid unnecessarily large:

```text
PNG files
JPEG files
Fonts
Animations
Audio
Raw assets
JSON files
Duplicate icons
```

Prefer Android vector drawables for simple icons where appropriate.

Do not bundle resources that are never used.

---

# 12. Memory Rules

Never keep unlimited event histories in memory.

Bad:

```text
allEvents.add(event)
```

forever.

Preferred architecture:

```text
Event
  ↓
Bounded processing queue
  ↓
Room
  ↓
Paging/lazy UI retrieval
```

Room is the durable event store.

RAM should not become a duplicate database.

---

# 13. Bounded Queues

Any in-memory event buffer shall have a defined maximum capacity.

Never create an effectively unlimited queue for:

```text
Accessibility events
IME interaction events
Database operations
Analytics events
```

If overload occurs, the system should preserve stability and keyboard responsiveness.

Redundant high-frequency events may be coalesced or dropped according to the architecture.

---

# 14. Memory Leak Prevention

Review lifecycle-sensitive references carefully.

Avoid retaining:

```text
Activity
Compose UI
View
Context
AccessibilityNodeInfo
AccessibilityEvent
InputConnection
```

longer than necessary.

Do not place Android UI objects in long-lived singleton objects.

---

# 15. Accessibility Object Lifetime

Accessibility framework objects shall be converted into lightweight internal event models as quickly as practical.

Do not store complete Android accessibility objects in:

```text
Room
long-lived lists
singleton state
ViewModels
event history caches
```

Extract only required application metadata.

---

# 16. Accessibility Callback Performance

`onAccessibilityEvent()` must remain lightweight.

Do not perform:

```text
large database queries
file export
complex statistics
large object traversal
blocking I/O
expensive formatting
```

directly inside the callback.

Preferred:

```text
Android callback
      ↓
Extract minimal metadata
      ↓
Submit lightweight event
      ↓
Return
      ↓
Background processing
```

---

# 17. Keyboard Performance Is Critical

The keyboard is latency-sensitive.

A user must not experience typing lag because the application subsystem is busy.

The primary keyboard path remains:

```text
KEY PRESS
    ↓
KeyProcessor
    ↓
InputConnection
    ↓
TARGET APP
```

Application logging is secondary.

## Encryption Performance

Encryption must not introduce noticeable keyboard latency.

```text
KEY PRESS
  │
  ▼
KeyProcessor
  │
  ▼
InputConnection
  │
  ▼
Target Application
```

This path remains independent of:

```text
Event Pipeline
  │
  ▼
Encryption
  │
  ▼
Database
```

Where practical, measure encrypted database-write latency, CPU overhead, memory overhead, startup/unlock overhead, database size, battery impact, and low-end-device impact. Do not weaken encryption to improve performance metrics; optimize the implementation instead.

---

# 18. Never Block Input for Logging

Never implement:

```text
KEY PRESS
    ↓
WAIT FOR DATABASE
    ↓
WAIT FOR EVENT PROCESSOR
    ↓
InputConnection
```

Use:

```text
              ┌──→ best-effort interaction event
KEY PROCESSOR ┤
              └──→ InputConnection
                         ↓
                    Target App
```

---

# 19. Main Thread Rules

Avoid performing:

```text
Room writes
large queries
CSV export
file operations
large transformations
statistics calculations
package scanning
```

on the main thread.

Use appropriate coroutine dispatchers/background execution.

---

# 20. Coroutine Rules

Coroutines must have controlled lifetimes.

Avoid unmanaged application-wide coroutines unless explicitly justified.

Prefer lifecycle-aware scopes.

Every long-running coroutine should have:

```text
clear owner
clear purpose
clear cancellation behavior
```

---

# 21. Avoid Excessive Coroutine Creation

Do not blindly launch a new coroutine for every high-frequency event.

For example, thousands of accessibility events should not automatically create thousands of independent coroutine jobs.

Prefer:

```text
Event Producer
      ↓
Bounded Channel / Flow
      ↓
Small number of consumers
      ↓
Event Processor
```

---

# 22. Database Optimization

Room tables shall use indexes based on actual query patterns.

Likely indexes:

```text
sessionId
timestamp
packageName
eventType
```

Do not add indexes without purpose because indexes also consume storage and increase write cost.

---

# 23. Database Queries

Prefer targeted queries.

Bad:

```text
SELECT * FROM interaction_events
```

then filtering thousands of rows in Kotlin.

Prefer:

```text
SELECT ...
FROM interaction_events
WHERE sessionId = ?
AND eventType = ?
ORDER BY timestamp DESC
LIMIT ...
```

Let the database perform appropriate filtering.

---

# 24. Paging

Large event timelines shall use paging/lazy loading.

Never load an entire long monitoring session into memory merely to display the first screen.

Architecture:

```text
Room
 ↓
Paging
 ↓
ViewModel
 ↓
LazyColumn
```

---

# 25. Statistics

Prefer database aggregate operations.

Use database operations conceptually equivalent to:

```text
COUNT
GROUP BY
MIN
MAX
```

instead of retrieving all records and calculating everything in Kotlin.

---

# 26. Database Transactions

Use transactions when multiple database operations must remain logically atomic.

Do not create unnecessary transactions for independent single-row operations.

---

# 27. Database Growth

Application event data can become large.

The application shall expose:

```text
Delete Session
Delete Selected Data
Delete All Event Data
```

Future versions may optionally provide configurable retention policies.

---

# 28. Event Size

Keep event records small.

Avoid storing large duplicated structures.

For example, do not repeatedly store:

```text
device manufacturer
device model
Android version
application version
```

on every event if that information belongs to the session.

Store session-level metadata once in `monitoring_sessions`.

---

# 29. Metadata

Do not turn `metadata` into an uncontrolled dumping ground.

Only store metadata needed for the application objective.

Avoid large serialized objects.

---

# 30. Package Name Resolution

Application-label lookup may be relatively expensive.

Do not repeatedly resolve:

```text
com.example.app
→
Example App
```

for every event.

Use a small cache:

```text
Package
   ↓
Cache
 ┌─┴─┐
Hit Miss
 │    │
 ▼    ▼
Use PackageManager
       │
       ▼
     Cache
```

The cache must remain bounded.

---

# 31. Event Deduplication

Accessibility can generate repetitive events.

Where functionally appropriate, consider deduplicating or coalescing redundant events.

Example:

```text
SCROLL
SCROLL
SCROLL
SCROLL
SCROLL
```

within a very short interval may not always require five expensive UI/database updates.

However, raw-event fidelity requirements for a particular test scenario take precedence.

---

# 32. UI Performance

Jetpack Compose screens shall avoid unnecessary recomposition.

ViewModels should expose stable UI state.

Avoid sending massive event lists through a single `StateFlow`.

Use paging for event histories.

---

# 33. Dashboard

The dashboard should consume aggregate state such as:

```text
session status
service status
total event count
IME event count
accessibility event count
current application
```

It should not require the complete event database to render.

---

# 34. UI Effects

Keep visual effects lightweight.

Avoid unnecessary:

```text
continuous animations
blur
large shadows
complex gradients
video backgrounds
large animated assets
```

The application is a monitoring utility, not a graphics benchmark.

---

# 35. Background Work

Do not continuously perform work simply because a service exists.

Use event-driven behavior wherever possible.

Bad:

```text
while (true) {
    scanEverything()
}
```

Preferred:

```text
Android Event
     ↓
Process required information
     ↓
Return idle
```

---

# 36. Battery Usage

Battery consumption shall be monitored during testing.

Potential sources include:

```text
Accessibility event frequency
Database writes
Continuous polling
Wake locks
Excessive logging
CPU-heavy processing
Frequent UI updates
```

Avoid wake locks unless an actual requirement justifies them.

---

# 37. No Unnecessary Polling

Do not repeatedly poll Android state at short intervals when callbacks/lifecycle checks can provide the information.

For example, configuration status can usually be refreshed when:

```text
App launches
App resumes
User returns from Settings
User manually requests refresh
```

rather than every second.

---

# 38. Storage Efficiency

Store structured information efficiently.

Avoid:

```text
duplicate events
large repeated JSON
screenshots
images
raw accessibility trees
unnecessary text dumps
```

unless explicitly required by a future approved use.

---

# 39. Export Performance

Large exports shall be streamed/chunked rather than building the complete CSV in RAM.

Preferred:

```text
Database page
    ↓
CSV writer
    ↓
Output stream
    ↓
Next page
```

---

# 40. Debug Code

Before considering a feature complete, remove:

```text
temporary println()
temporary Log.d()
debug Toasts
hardcoded test values
unused fake data
temporary buttons
test screens
commented blocks
```

Retain only useful controlled diagnostic logging.

---

# 41. Logging Levels

Use logging intentionally.

Conceptual levels:

```text
ERROR
WARN
INFO
DEBUG
```

Release builds should avoid verbose event-by-event logging.

Never log sensitive input contents.

---

# 42. Exception Handling

Do not use empty catch blocks.

Bad:

```kotlin
try {
    ...
} catch (e: Exception) {
}
```

Failures should be handled appropriately without crashing latency-sensitive components.

At the same time, do not generate excessive logging for recoverable high-frequency failures.

---

# 43. Code Duplication

When identical meaningful logic appears repeatedly, consolidate it.

Examples:

```text
timestamp creation
event mapping
input classification
package resolution
database mapping
```

Do not abstract trivial one-line operations merely for abstraction's sake.

---

# 44. Large Classes

Avoid classes that accumulate unrelated responsibilities.

If a class begins handling:

```text
UI
database
keyboard
export
statistics
permissions
```

it must be decomposed.

---

# 45. Functions

Functions should have one understandable purpose.

Prefer:

```text
processEvent()
sanitizeEvent()
resolvePackage()
insertEvent()
```

over giant functions containing the complete pipeline.

---

# 46. Comments

Comments should explain:

```text
WHY something exists
WHY Android requires something
WHY an optimization is safe
WHY unusual behavior is intentional
```

Do not add comments merely restating obvious code.

---

# 47. Performance Regression Rule

Whenever a major feature is added, compare performance with the previous stable build.

Check:

```text
APK size
memory usage
database size
startup time
keyboard responsiveness
event-processing latency
battery behavior
```

Unexpected regressions must be investigated.

---

# 48. Build Size Regression

Track release artifact size.

Example engineering record:

```text
Version       APK Size

0.1           8.2 MB
0.2           8.5 MB
0.3           8.6 MB
0.4          15.4 MB  ← INVESTIGATE
```

Large unexplained jumps should block release until understood.

---

# 49. Memory Regression

Test long-running monitoring sessions.

Watch for memory patterns such as:

```text
50 MB
55 MB
60 MB
80 MB
120 MB
180 MB
```

that continue growing without returning.

This may indicate:

```text
unbounded collections
unreleased Android objects
Flow collectors
Context leaks
event queues
large caches
```

---

# 50. Database Stress Test

Test at least progressively larger event counts, for example:

```text
1,000 events
10,000 events
50,000 events
100,000 events
```

Evaluate:

```text
insert performance
timeline loading
search
filters
statistics
export
deletion
database size
```

Exact limits may be adjusted based on the target application device.

---

# 51. Low-End Device Test

At least one lower-specification physical Android device or appropriately constrained test environment should be included.

Evaluate:

```text
Keyboard responsiveness
App startup
Dashboard loading
Event recording
Scrolling
Search
Statistics
Export
Memory
Battery
Storage
```

---

# 52. Release Checklist

Before every release candidate, the development agent shall review:

```text
[ ] Build succeeds

[ ] Tests pass

[ ] Keyboard works

[ ] Accessibility events work

[ ] Sensitive input protection passes

[ ] No sensitive data appears in logs

[ ] No sensitive data appears in database

[ ] No sensitive data appears in export

[ ] No unused dependencies

[ ] No obvious unused code

[ ] No temporary debug UI

[ ] No unbounded event lists

[ ] No unbounded caches

[ ] No unbounded queues

[ ] Database queries reviewed

[ ] Main-thread operations reviewed

[ ] Coroutine lifetimes reviewed

[ ] Accessibility callbacks reviewed

[ ] APK size checked

[ ] Memory usage checked

[ ] Database growth checked

[ ] Keyboard latency checked

[ ] Battery behavior checked

[ ] Release shrinking tested
```

---

# 53. Mandatory Agent Review After Every Phase

At the end of every implementation phase, the coding agent shall explicitly perform:

```text
PHASE COMPLETE
      │
      ▼
Run tests
      │
      ▼
Review changed files
      │
      ▼
Find dead/duplicate code
      │
      ▼
Review dependencies
      │
      ▼
Review allocations
      │
      ▼
Review database usage
      │
      ▼
Review coroutine usage
      │
      ▼
Review main-thread work
      │
      ▼
Review privacy boundaries
      │
      ▼
Optimize
      │
      ▼
Run tests again
```

Do not postpone all cleanup until the final phase.

---

# 54. Agent Self-Review Questions

Before marking work complete, ask:

### Code

- Is every new class necessary?
- Is every function used?
- Is there duplicated logic?
- Can this implementation be simpler?

### Performance

- Does this allocate objects unnecessarily?
- Does this run for every key press?
- Does this run for every accessibility event?
- Could this block the UI or IME?
- Could this queue grow indefinitely?

### Database

- Is this query efficient?
- Does it need an index?
- Am I loading more rows than necessary?
- Am I duplicating session-level information?

### Memory

- Is anything being retained unnecessarily?
- Is the cache bounded?
- Is the event buffer bounded?

### Size

- Did I introduce a new dependency?
- Was it actually necessary?
- Did artifact size increase significantly?

### Battery

- Did I introduce polling?
- Does anything continuously run while idle?
- Can the operation be event-driven?

### Privacy

- Could sensitive input reach this component?
- Could it reach Logcat?
- Could it reach Room?
- Could it reach export?

---

# 55. Optimization Must Preserve Application Validity

Do not optimize away information required by a test scenario without documenting the change.

For example, event coalescing may improve performance but alter application results.

Therefore:

```text
Optimization
     │
     ▼
Does it alter test data?
     │
 ┌───┴────┐
 │        │
NO       YES
 │        │
Apply    Document +
         make test-aware
```

---

# 56. No Premature Optimization

Do not introduce complicated low-level optimization without measurement.

Preferred approach:

```text
Measure
   ↓
Identify bottleneck
   ↓
Optimize
   ↓
Measure again
```

Avoid making code substantially harder to maintain for negligible gains.

---

# 57. Performance Metrics

Where practical, maintain measurements for:

```text
Cold startup time
Warm startup time
Keyboard response latency
Event-processing latency
Database insertion rate
Timeline load time
Search time
Export time
Peak memory
Idle memory
APK size
Database size
```

---

# 58. Recommended Optimization Philosophy

Use:

> **Measure → Simplify → Optimize → Verify**

rather than:

> **Guess → Overengineer → Hope**

---

# 59. Definition of Optimized Feature

A feature is ready only when:

```text
Correct
+
Tested
+
Privacy compliant
+
No obvious dead code
+
No unnecessary dependency
+
No uncontrolled memory growth
+
No avoidable main-thread I/O
+
No unbounded queue/cache
+
Reasonable storage behavior
+
Does not degrade keyboard responsiveness
```

---

# 60. Final Development Rule

Every coding agent working on this project shall treat performance and cleanup as part of implementation rather than optional post-development work.

For every feature:

```text
BUILD
  ↓
TEST
  ↓
REVIEW
  ↓
REMOVE
  ↓
SIMPLIFY
  ↓
OPTIMIZE
  ↓
PROFILE
  ↓
RETEST
  ↓
COMMIT
```

The target is not merely:

**"The application works."**

The target is:

**"The application works reliably, remains responsive, respects the input-safety boundary, uses resources efficiently, has minimal unnecessary code, keeps its application size reasonable, and remains practical on lower-configuration Android devices."**

---

# 61. Agent Instruction Priority

When this document, `SRS.md`, and `Architecture.md` are supplied to a coding agent, they shall be interpreted together:

```text
SRS.md
   │
   └── WHAT must be built

Architecture.md
   │
   └── HOW the system should be structured

DEVELOPMENT_GUIDELINES.md
   │
   └── HOW the implementation must be
       reviewed, cleaned and optimized
```

The coding agent must not satisfy the SRS by producing unnecessarily large, inefficient, duplicated, or resource-intensive code.

---

# 62. Mandatory Final Optimization Pass

Before declaring the complete application finished, perform a dedicated final pass covering:

```text
SOURCE CODE
├── dead code
├── duplication
├── unused imports
├── unused resources
└── unnecessary abstractions

DEPENDENCIES
├── unused libraries
├── oversized libraries
└── duplicate functionality

PERFORMANCE
├── keyboard latency
├── accessibility processing
├── startup
├── database
└── UI

MEMORY
├── leaks
├── caches
├── queues
└── event histories

STORAGE
├── database growth
├── exports
└── bundled resources

BATTERY
├── polling
├── background work
├── event frequency
└── unnecessary wakeups

BUILD
├── R8
├── resource shrinking
├── release artifact size
└── final APK/AAB analysis

PRIVACY
├── sensitive-field tests
├── database inspection
├── Logcat inspection
└── export inspection
```

Only after this review and regression testing should the build be considered ready.

---

**END OF DEVELOPMENT GUIDELINES**

**Document:** `DEVELOPMENT_GUIDELINES.md`  
**Version:** `1.0`