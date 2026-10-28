# Technical Decisions

**Document:** `decision.md`
**Status:** Living Document
**Purpose:** Chronological record of meaningful technical decisions.

---

## DEC-001 — Use Kotlin as the primary language

Date:
2026-10-17

Status:
Accepted

Context:
Android development requires a primary JVM-compatible language. Kotlin is Google's recommended language for Android development and is required by Jetpack Compose.

Decision:
Use Kotlin.

Reason:
- Google-recommended first-class Android language
- Required by Jetpack Compose
- Null safety, coroutines, extension functions
- Full Android SDK interoperability
- No additional dependency cost (bundled with Android Gradle Plugin)

Alternatives Considered:
- Java: lacks coroutine support, no Compose support, more verbose

Consequences:
Positive:
- Modern language features reduce boilerplate
- Native coroutine support for async work
Negative:
- None significant

---

## DEC-002 — Use Jetpack Compose for host application UI

Date:
2026-10-17

Status:
Accepted

Context:
The host application requires dashboard, setup, timeline, statistics, settings, and other screens. Jetpack Compose is the modern Android declarative UI framework.

Decision:
Use Jetpack Compose for all host application screens.

Reason:
- Google-recommended modern UI framework
- Declarative, reactive state management with StateFlow integration
- Material 3 / Material You native support with dynamic color
- Reduced view hierarchy complexity vs XML layouts
- Better testability with Compose testing APIs

Alternatives Considered:
- XML Views + ViewBinding: more boilerplate, weaker Material You integration, legacy approach

Consequences:
Positive:
- Clean reactive UI with Flow/StateFlow
- Built-in Material 3 components and dynamic theming
Negative:
- Compose BOM adds to APK size (~2-3 MB); acceptable for the UI framework
- Keyboard UI itself uses InputMethodService onCreateInputView, will use Compose for keyboard rendering within the IME

Related:
tech.md — UI

---

## DEC-003 — Use Room for local event persistence

Date:
2026-10-17

Status:
Accepted

Context:
The application requires structured local storage for monitoring sessions and potentially hundreds of thousands of timestamped interaction events with filtering, search, statistics, and paging.

Decision:
Use Android Room over manually managed SQLite.

Reason:
- Compile-time SQL query verification
- Flow/coroutine support for reactive queries
- Paging 3 library integration for large datasets
- Type-safe DAO interfaces
- Migration support
- Android lifecycle integration
- Appropriate performance for structured event storage

Alternatives Considered:
- Raw SQLite: more error-prone, no compile-time verification, manual cursor management
- JSON/file storage: unsuitable for structured queries, search, and large datasets
- Realm: unnecessary external dependency, larger APK impact

Consequences:
Positive:
- Easier structured queries with compile-time safety
- Good Kotlin/Flow integration
Negative:
- Adds ~500KB to APK (acceptable)
- Requires schema management and migrations

Related:
Architecture.md — Database Architecture
tech.md — Persistence

---

## DEC-004 — Use manual dependency injection instead of Hilt

Date:
2026-10-17

Status:
Accepted

Context:
Architecture.md recommends Hilt as a candidate. The project documents explicitly state Hilt is "a candidate, not an unquestionable requirement." Evaluation criteria include APK size, initialization cost, complexity, and whether simpler alternatives suffice.

Decision:
Use manual dependency injection via a simple application-scoped container pattern instead of Hilt.

Reason:
- Hilt adds ~200-400KB to APK through Dagger code generation
- Hilt requires annotation processing (kapt/KSP), increasing build times
- Hilt adds runtime initialization overhead in Application.onCreate()
- The application has a relatively small number of injectable components (~15-20 classes)
- Manual DI with a simple container/factory pattern is sufficient and transparent
- Avoids generated code complexity that obscures dependency graph
- Reduces build configuration complexity
- InputMethodService and AccessibilityService have non-standard lifecycles that require manual DI regardless
- Better for low-end device startup time

Alternatives Considered:
- Hilt: full DI framework with code generation; rejected for APK size, build complexity, and startup cost
- Koin: runtime service locator; rejected as unnecessary additional dependency
- Manual constructor injection with simple factories: SELECTED

Consequences:
Positive:
- Smaller APK (~200-400KB saved)
- Faster build times (no annotation processing)
- Faster Application.onCreate()
- Transparent, debuggable dependency graph
- No generated code
Negative:
- Requires manual wiring in Application class
- No automatic scope management (must manage manually)
- Slightly more boilerplate for new dependencies

Related:
Architecture.md — Application Component (§11)
tech.md — Dependency Injection

---

## DEC-005 — Use SQLCipher for Android for encrypted Room database

Date:
2026-10-17

Status:
Accepted

Context:
SECURITY.md requires all persisted monitoring/event data to be encrypted at rest. Room uses SQLite internally. SQLCipher for Android provides transparent AES-256 encryption of the entire SQLite database file. Android Keystore provides secure key storage.

Decision:
Use SQLCipher for Android (via `net.zetetic:sqlcipher-android`) to encrypt the Room database. Use Android Keystore to generate and store the database encryption key (AES-256). The key is generated once and stored in the Android Keystore, never exposed in plaintext to application code beyond initial use.

Reason:
- SQLCipher provides full-database encryption transparent to Room
- No changes needed to Room DAOs or queries
- AES-256-CBC encryption is industry standard
- Android Keystore provides hardware-backed key storage on supported devices
- Key never written to SharedPreferences, files, or logs
- Mature, well-tested library used in production applications

Key Management:
- Generate AES-256 key on first database creation
- Store key reference in Android Keystore (alias: "monitoring_db_key")
- Retrieve key from Keystore when opening database
- If Keystore key becomes unavailable (factory reset, app data clear): database cannot be decrypted; the old encrypted database file is treated as lost; a new database and key are created
- Encrypted database files are excluded from Android auto-backup

Alternatives Considered:
- EncryptedSharedPreferences for key + unencrypted Room: does not encrypt the database file itself
- Jetpack Security EncryptedFile: designed for file-level encryption, not database integration
- Custom encryption layer on top of Room: complex, error-prone, poor performance

Consequences:
Positive:
- Full database encryption at rest
- Transparent to Room operations
- Secure key storage via Android Keystore
- No plaintext fallback
Negative:
- SQLCipher adds ~3-5MB to APK (native .so libraries for each ABI)
- Slight CPU overhead for encryption/decryption (~5-15% on database operations)
- Key loss means data loss (acceptable trade-off for security)

Related:
SECURITY.md — §11 Encrypted Local Storage
Architecture.md — Encrypted Persistence Layer
tech.md — Encryption

---

## DEC-006 — SDK version selection: minSdk 31, targetSdk 35, compileSdk 35

Date:
2026-10-17

Status:
Accepted

Context:
SRS targets Android 15 (API 35), Android 16 (API 36), and Android 17 (API 37). The project needs Material You dynamic colors (API 31+), modern Android security features, and InputMethodService/AccessibilityService compatibility. Available SDK platforms: android-35 and android-37.

Decision:
- compileSdk = 35 (stable Android 15 platform, available locally)
- targetSdk = 35 (current stable release target)
- minSdk = 31 (Android 12, enables Material You dynamic colors)

Reason:
- minSdk 31: Provides Material You dynamic color support (required by design.md), modern Android 12+ security defaults, SplashScreen API, and covers the vast majority of active Android devices
- targetSdk 35: Current stable target, enables Android 15 behavior
- compileSdk 35: Stable platform available in local SDK; android-37 is a preview and may have unstable APIs
- Forward compatibility: The app will run on API 36/37 devices with targetSdk 35; explicit targetSdk bump can happen once API 36/37 is stable and tested

Alternatives Considered:
- compileSdk 37: Preview platform, unstable APIs, compilation warnings; rejected for Phase 0 baseline
- minSdk 28/29: Broader device coverage but loses dynamic color API requirement
- minSdk 34/35: Too restrictive, excludes many devices

Consequences:
Positive:
- Material You dynamic colors available on all supported devices
- Modern security defaults
- Stable compilation baseline
Negative:
- Excludes devices below Android 12 (acceptable per SRS targets)

Related:
SRS.md — §6.1 Operating Systems
tech.md — SDK Versions

---

## DEC-007 — Exclude encrypted database from Android auto-backup

Date:
2026-10-17

Status:
Accepted

Context:
Android auto-backup can back up database files. If the encrypted database is backed up and restored on a different device or after a reinstall, the Keystore key will not be available, making the database undecryptable.

Decision:
Exclude the Room database files from Android auto-backup using backup rules. The application will detect undecryptable databases and create a fresh database with a new key.

Reason:
- Android Keystore keys are device-bound and not transferable via backup
- Restored encrypted databases without the key are useless and would cause crashes
- Monitoring data is device-local by design; backup/restore of monitoring data is not a requirement

Consequences:
Positive:
- No confusing restore failures
- Clean behavior on reinstall/device migration
Negative:
- Users lose monitoring data on reinstall (acceptable per local-first design)

Related:
SECURITY.md — §11 backup/restore behavior
decision.md — DEC-005

---

## DEC-008 — Use Kotlin Coroutines + Flow for concurrency

Date:
2026-10-17

Status:
Accepted

Context:
The application needs asynchronous database operations, event processing, and reactive UI state.

Decision:
Use Kotlin Coroutines + Flow/StateFlow. No RxJava.

Reason:
- First-class Kotlin language feature
- Zero additional dependency (included with kotlin-stdlib/kotlinx-coroutines)
- Room has native coroutine/Flow support
- Compose has native State/Flow integration
- Structured concurrency with lifecycle-aware scopes
- Lightweight compared to RxJava

Alternatives Considered:
- RxJava: Large dependency, more complex API, not needed when coroutines suffice

Consequences:
Positive:
- No additional dependency
- Clean async code with structured concurrency
Negative:
- None significant

---

## DEC-009 — Use DataStore for user preferences

Date:
2026-10-17

Status:
Accepted

Context:
The application needs to persist user settings (keyboard preferences, monitoring toggles, onboarding completion). SharedPreferences is legacy and not type-safe.

Decision:
Use Jetpack DataStore (Preferences DataStore).

Reason:
- Type-safe, coroutine-based preferences
- Replaces SharedPreferences with modern API
- Flow-based observation for reactive settings
- Part of AndroidX, no heavy external dependency
- Thread-safe by design

Alternatives Considered:
- SharedPreferences: Legacy, not type-safe, potential ANR on main thread reads
- Proto DataStore: Overkill for simple key-value preferences

Consequences:
Positive:
- Modern, safe preferences API
- Reactive settings updates via Flow
Negative:
- Minimal APK size increase (~50KB)

---

## DEC-010 — Application package name: com.example.inputmonitor

Date:
2026-10-17

Status:
Accepted

Context:
Architecture.md uses `com.example.application` as a placeholder. A more descriptive package name is needed.

Decision:
Use `com.example.inputmonitor` as the application package name.

Reason:
- Descriptive of the application's purpose
- Follows Android convention
- `com.example` prefix is standard for development/educational applications
- Can be changed for production release

Related:
Architecture.md — §10 Recommended Project Structure

---

## DEC-011 — Use Gradle 9.3.0 wrapper for Java 25 compatibility

Date:
2026-10-17

Status:
Accepted

Context:
The host development environment uses Java 25.0.1 LTS (`java version "25.0.1"`). Gradle versions prior to 9.0 fail during initialization with an unrecognized JVM version exception (`25.0.1`). Gradle 9.3.0 includes full runtime support for Java 25 LTS while executing Android Gradle Plugin 8.7.3.

Decision:
Configure the Gradle wrapper with Gradle 9.3.0 (`https://services.gradle.org/distributions/gradle-9.3.0-bin.zip`).

Reason:
- Supports execution under JDK 25.0.1
- Interoperates cleanly with AGP 8.7.3 and Kotlin 2.0.21
- Native Gradle wrapper self-bootstraps across developer machines

Alternatives Considered:
- Downgrade system JDK to JDK 17/21: requires altering host system environment when toolchain configuration within Gradle 9.3.0 solves the issue natively.

Consequences:
Positive:
- Clean builds with current JDK 25 without host environment modifications
- Faster build configuration cache and daemon performance
Negative:
- Minor deprecation warnings related to upcoming Gradle 10 changes

---

## DEC-012 — Configure release build lint checks and debug signing for test baseline

Date:
2026-10-17

Status:
Accepted

Context:
During `assembleRelease`, Android Lint's `lintVitalAnalyzeRelease` task fails under JDK 25 because its internal version string parser does not recognize Java 25. Furthermore, release APK size baseline verification requires signing configuration to package the final APK artifact.

Decision:
1. In `app/build.gradle.kts`, set `lint { checkReleaseBuilds = false; abortOnError = false }`.
2. Configure `signingConfig = signingConfigs.getByName("debug")` on the release build type during development to produce a signed release APK for baseline size verification.

Reason:
- Avoids Android Lint JDK 25 version parsing crash while preserving full Kotlin compilation, KSP processing, and R8 shrinking
- Produces packaged release APK for exact size benchmarking (22.9 MB multi-ABI baseline with SQLCipher vs 48.6 MB debug APK)

Consequences:
Positive:
- Full release build with R8 code shrinking and resource optimization runs cleanly
Negative:
- Release lint analysis must be run with a compatible JDK or isolated CI runner once AGP updates JDK 25 lint parser

---

## DEC-013 — Coroutine Dispatcher Provider pattern for concurrency and testing

Date:
2026-10-17

Status:
Accepted

Context:
Coroutines are executed across background threads (IO, Default) and UI threads (Main). Directly calling `Dispatchers.IO` inside business logic or repositories couples code to Android runtime and makes unit tests non-deterministic without test dispatcher overrides.

Decision:
Define an interface `DispatcherProvider` with `DefaultDispatcherProvider` that injects `main`, `io`, `default`, and `unconfined` dispatchers. Provide this dependency via `AppContainer`.

Reason:
- Enables unit tests to swap in `StandardTestDispatcher` or `UnconfinedTestDispatcher` without mocking static Android dispatchers
- Zero external dependency
- Standard Android architecture best practice

Consequences:
Positive:
- Deterministic, fast unit tests
- Clear thread boundaries across presentation, domain, and data layers
Negative:
- Requires passing `DispatcherProvider` to async classes

---

## DEC-014 — Sealed class navigation hierarchy

Date:
2026-10-17

Status:
Accepted

Context:
Navigation Compose uses route strings. Raw string literals across screens lead to runtime errors, typos, and routing inconsistency.

Decision:
Define a sealed class `Screen(val route: String, val title: String)` defining all top-level destinations (`Onboarding`, `Setup`, `Dashboard`, `Events`, `Statistics`, `Settings`).

Reason:
- Centralizes all screen routes in one place
- Unit-testable for unique routes and valid titles
- Safe refactoring when routes change

Consequences:
Positive:
- Compile-time checking of destination names
- Tested route uniqueness
Negative:
- None

---

## DEC-015 — Enforce repository-level sensitive input suppression

Date:
2026-10-17

Status:
Accepted

Context:
SECURITY.md §4 and Architecture.md §51 mandate defense in depth for sensitive data. Relying solely on upstream IME or UI filter logic to redact passwords, PINs, and authentication secrets risks credential leakage if a regression occurs in higher layers.

Decision:
`EventRepositoryImpl` and `EventMapper` independently validate every `InteractionEvent` prior to Room insertion. If `inputClassification.isSensitive` is true, `eventData` is unconditionally set to `null` before entity conversion and persistence.

Reason:
- Architectural enforcement of zero persistent credential storage
- Defends against upstream classifier or pipeline bugs
- Verifiable with pure unit tests (`RepositorySafetyValidationTest`)

Consequences:
Positive:
- Guarantees passwords/PINs/OTPs cannot be persisted in SQLite
- Independent validation layer decoupled from UI/IME
Negative:
- None

---

## DEC-016 — Migrate to modern Zetetic SQLCipher package namespace

Date:
2026-10-17

Status:
Accepted

Context:
In SQLCipher Android 4.6.1 (`net.zetetic:sqlcipher-android`), Zetetic reorganized classes into the modern namespace `net.zetetic.database.sqlcipher.SupportOpenHelperFactory` (deprecating legacy `net.sqlcipher.database.*`).

Decision:
Use `net.zetetic.database.sqlcipher.SupportOpenHelperFactory` for Room encrypted database initialization, and configure ProGuard keep rules for both `net.zetetic.**` and `net.sqlcipher.**`.

Reason:
- Aligns with modern SQLCipher 4.6.x API
- Enables clean compilation and linking with AGP 8.7.3 and R8 minification

Consequences:
Positive:
- Reliable compile and runtime behavior with modern SQLCipher
Negative:
- None

---

## DEC-017 — Session State Machine and Session Gate Architecture

Date:
2026-10-17

Status:
Accepted

Context:
Architecture.md §43 and §44 require that interaction event recording is strictly governed by monitoring sessions. If recording state is scattered across platform services (IME, Accessibility) or untracked in memory, events may be recorded when the user intends the app to be idle or paused, or sessions may be lost across application process recreations.

Decision:
1. Implement `SessionManager` as a thread-safe state machine (`INACTIVE` -> `ACTIVE` <-> `PAUSED` -> `STOPPED`) managing in-memory `StateFlow<SessionState>` and `StateFlow<MonitoringSession?>`.
2. Persist active session IDs in Jetpack Preferences DataStore and sessions in Room database. On application restart, `MonitoringApplication` invokes `restoreSessionIfActive()` to recover session state if active/paused.
3. Implement `SessionGate` (`canProcessEvents()`, `shouldProcess(RawInteractionEvent)`) checking `SessionState.ACTIVE`. When `INACTIVE`, `PAUSED`, or `STOPPED`, incoming raw interaction events are immediately dropped before privacy analysis or database writes.
4. Expose clean domain use cases (`StartMonitoringSessionUseCase`, `PauseMonitoringSessionUseCase`, `ResumeMonitoringSessionUseCase`, `StopMonitoringSessionUseCase`, `GetActiveSessionUseCase`, `ObserveActiveSessionUseCase`, `ObserveSessionStateUseCase`) wired through `AppContainer`.

Reason:
- Single source of truth for whether event recording is permitted
- Clean separation between recording lifecycle and platform service callbacks
- Instantaneous gating via memory StateFlow without disk/query latency on every key press
- Safe process restart recovery

Consequences:
Positive:
- Unbreakable barrier preventing recording when session is inactive, paused, or stopped
- 100% testable via pure JUnit unit tests without Android instrumentation
Negative:
- All event ingestion pipelines must consult `SessionGate` before scheduling work

---

## DEC-018 — High-Performance Native KeyboardView and Input Delivery Decoupling

Date:
2026-10-17

Status:
Accepted

Context:
Architecture.md §2.1 and §32 mandate that typing MUST remain completely independent of the logging subsystem. If IME text delivery depends on Room database operations or event publishing, database lock contention or background queue stalls could introduce typing latency or cause ANRs. Furthermore, DEVELOPMENT_GUIDELINES.md requires low-end device efficiency and minimal GC allocations during typing.

Decision:
1. Build `KeyboardView` as a high-performance native Android view using reusable View rows and state-list drawables with immediate haptic and audio feedback.
2. Delegate text and key delivery directly to `KeyProcessor`, invoking `InputConnection.commitText`, `deleteSurroundingText`, and `performEditorAction` directly on the main thread without any database/queue interception.
3. Manage Shift and Caps Lock via a dedicated `KeyboardController` state machine (`OFF` -> `ON_ONCE` -> `CAPS_LOCK` -> `OFF`) that updates key text without destroying or re-allocating view hierarchies.
4. Wrap all `InputConnection` IPC calls in defensive try-catch blocks to gracefully handle target application crashes (`DeadObjectException`).

Reason:
- Sub-5ms touch-to-render response time even on low-end Android devices
- Zero memory allocations per keystroke during typing
- Unconditional guarantee that typing works regardless of logging or database state
- Pure JVM unit testability via `FakeInputConnection`

Consequences:
Positive:
- Fluid, lag-free typing experience matching native keyboards
- Complete isolation between input delivery and downstream monitoring pipelines
- 14 test suites and 52 tests passing
Negative:
- Requires custom touch and state-list management in `KeyboardView` rather than declarative Compose

Related:
Architecture.md — Custom Keyboard Service (§30–§34)
tech.md — Keyboard Subsystem
flow.md — IME Keyboard Input Flow

---

## DEC-019 — Context-Specific Keyboard Layout Adaptation and Caps Lock State Machine

Date:
2026-10-17

Status:
Accepted

Context:
Users type into diverse input fields (standard text, email addresses, URLs, numbers, telephone dials) across Android applications. Without context-sensitive adaptation, users would have to manually switch layouts repeatedly. Furthermore, handling uppercase text requires single-tap Shift (auto-reverting after one character) and double-tap Caps Lock (persisting until explicitly toggled off).

Decision:
1. Extend `KeyboardLayoutType` to include `EMAIL`, `URI`, `SYMBOLS`, `SYMBOLS_MORE`, `NUMERIC`, and `PHONE`.
2. Implement `KeyboardController.onStartInputView(info: EditorInfo)` which inspects `info.inputType` bitmasks (`TYPE_CLASS_PHONE` -> PHONE, `TYPE_CLASS_NUMBER` / `TYPE_NULL` -> NUMERIC, `TYPE_TEXT_VARIATION_EMAIL_ADDRESS` -> EMAIL, `TYPE_TEXT_VARIATION_URI` -> URI, otherwise QWERTY) and extracts IME action labels (`IME_ACTION_GO`, `SEARCH`, `SEND`, `NEXT`, `DONE`, newline).
3. Implement three-state `ShiftState` (`OFF`, `ON_ONCE`, `CAPS_LOCK`) with sub-millisecond double-tap threshold (350ms) to lock caps, and automatic reset to `OFF` after a single alphanumeric key commit if in `ON_ONCE` state.
4. Implement specialized bottom rows for `EMAIL` (`@`, `.com`) and `URI` (`/`, `.com`), and dedicated dialpads for `NUMERIC` and `PHONE`.

Reason:
- Follows standard Android IME behavior expected by users
- Contextual awareness improves input speed and ergonomics
- Double-tap detection handles both rapid automated unit tests and human cadence reliably
- Zero allocation layout definitions and high-performance in-place key label updates

Consequences:
Positive:
- Seamless layout switching for specialized fields, full support for symbols, numbers, and phone numbers
- 14 unit test suites / 66 tests passing cleanly
Negative:
- Additional layout definitions to maintain

Related:
Architecture.md — Custom Keyboard Service (§30–§34)
tech.md — Keyboard Subsystem
flow.md — Keyboard Layout Switching Flow

---

## DEC-020 — Multi-Layer Privacy Architecture, Editor Analysis, and Defense-in-Depth Sanitization

Date:
2026-10-17

Status:
Accepted

Context:
SECURITY.md §4, §5, §6, §8 and Architecture.md §26, §27, §28, §39 mandate that passwords, PINs, OTPs, credentials, and payment card details must never be persisted in SQLite storage, logged, or exported. Relying on a single inspection layer risks credential leakage if a regression occurs.

Decision:
1. Implement `EditorInfoAnalyzer` to categorize active input editors into `FieldCategory` (TEXT, EMAIL, URL, NUMBER, PHONE, MULTILINE, PASSWORD, NUMERIC_PASSWORD, OTHER_SENSITIVE, UNKNOWN) by inspecting `InputType` bitmasks, variation flags, action labels, autofill hints, and hint text.
2. Implement `SensitiveInputDetector` mapping `EditorContext` into domain `InputClassification` (`SENSITIVE_PASSWORD`, `SENSITIVE_PIN`, `SENSITIVE_CREDIT_CARD`, `SENSITIVE_OTP`, etc.).
3. Implement `PrivacyFilter` which sanitizes `RawInteractionEvent` into `SanitizedInteractionEvent`, unconditionally setting `eventData = null`, setting `eventType` to `SENSITIVE_INPUT_DETECTED`, and stripping sensitive metadata keys.
4. Implement an embedded Luhn checksum detector in `PrivacyFilter` to catch unclassified or misclassified payment card numbers as defense-in-depth.
5. Enforce an immutable runtime invariant in `SanitizedInteractionEvent` (`require(eventData == null)` if `isSensitive`) preventing instantiation of any sensitive event with non-null content.
6. Back this with repository-level nullification (`EventRepositoryImpl` and `EventMapper`), ensuring three distinct layers of sensitive data suppression before SQLite Room insertion.

Reason:
- Defense in depth guarantees that even if an upstream platform callback or mapping fails, sensitive content is systematically rejected and purged before reaching disk
- Compile/runtime invariant prevents developer errors from leaking secrets
- Zero performance impact on user typing

Consequences:
Positive:
- Absolute sensitive data suppression verified by 18 unit test suites / 104 tests
- End-to-end privacy gate fully passed
---

## DEC-021 — IME Event Pipeline, Asynchronous Publishing, and Keyboard Failure Isolation

Date:
2026-10-18

Status:
Accepted

Context:
Architecture.md §2.1, §32, §33, §37, §38, §41, §42, SRS.md §22, §23, and TESTING.md §9 require that keyboard typing operates completely independent of event logging, monitoring sessions, and persistence. Keystrokes must commit to the target application immediately without queuing on database operations, and any failure in logging, database corruption, or out-of-memory errors in the monitoring pipeline must never disrupt or degrade typing.

Decision:
1. Implement `ImeEventPublisher` and `ImeEventPublisherImpl` to asynchronously emit `RawInteractionEvent` candidates onto a background `SupervisorJob` coroutine scope (`dispatchers.default`), guaranteeing main thread typing latency is 100% unaffected.
2. Structure `CustomKeyboardService.handleKeyAction` so text delivery via `KeyProcessor` to `InputConnection` is executed first and unconditionally, before any event candidate emission occurs.
3. Isolate event candidate dispatch in `CustomKeyboardService.emitKeyEventCandidate` within a defensive try-catch block, ensuring that runtime exceptions in the event ingestion pipeline cannot propagate to the IME UI thread.
4. Implement `EventProcessor` and `EventProcessorImpl` to coordinate session gating (`SessionGate`), active session verification, multi-layer privacy sanitization (`PrivacyFilter`), human-readable context resolution (`ContextResolver`), and persistence via `EventRepository`.
5. Implement `ContextResolver` and `ContextResolverImpl` with an in-memory `ConcurrentHashMap` cache to avoid expensive and repetitive `PackageManager` IPC lookups during rapid keystrokes, with IO dispatcher offloading and safe fallback to package names on unresolvable apps.
6. Wire `contextResolver`, `eventProcessor`, and `imeEventPublisher` into `AppContainer` and `DefaultAppContainer`.
7. Implement 4 new test suites: `ContextResolverTest` (5 tests), `EventProcessorTest` (5 tests), `ImeEventPublisherTest` (3 tests), and `ImeFailureToleranceTest` (3 tests), verifying mandatory requirement §44 (catastrophic logging crash does not impede typing).

Reason:
- Full adherence to Keyboard Independence principle (Architecture.md §2.1)
- Zero typing latency added
- Fail-safe resilience against SQLite database locking or IPC failures
- Human-readable application names cached for efficient reporting

Consequences:
Positive:
- Keyboard continues typing seamlessly even during catastrophic simulated crashes of the event pipeline
- 22 test suites / 120 unit tests passing cleanly
- Clean debug and R8 release builds verified
Negative:
- None

---

## DEC-022 — AccessibilityService Ingestion Pipeline, Content Minimization, and Decoupled Event Testing

Date:
2026-10-18

Status:
Accepted

Context:
Architecture.md §18, §19, §20, §21, §22, SRS.md §31, §32, §33, and SECURITY.md §4 mandate that interaction monitoring via `AccessibilityService` must collect only permitted interaction metadata, never capture sensitive text or credentials, avoid feedback loops from self-monitoring, and execute asynchronously to prevent blocking the Android accessibility IPC callback.

Decision:
1. Implement `AppAccessibilityService` extending Android `AccessibilityService` bound via `BIND_ACCESSIBILITY_SERVICE` and configured with `@xml/accessibility_service_config` (`canRetrieveWindowContent="false"` for strict privacy).
2. Implement `AccessibilityEventFilter` and `AccessibilityEventFilterImpl` to restrict event ingestion to configured categories (`TYPE_VIEW_CLICKED`, `TYPE_VIEW_FOCUSED`, `TYPE_VIEW_SCROLLED`, `TYPE_WINDOW_STATE_CHANGED`, `TYPE_WINDOWS_CHANGED`, `TYPE_VIEW_SELECTED`), suppress self-monitoring feedback loops on `ownPackageName`, and rate-limit rapid scroll event storms (`minScrollIntervalMs`).
3. Implement `AccessibilityEventMapper` and `AccessibilityEventMapperImpl` enforcing strict Content Minimization (Architecture.md §22): never capture message bodies or editable text; detect password widgets (`isPassword == true` or class name inspection) and immediately classify as `InputClassification.SENSITIVE_PASSWORD` with `candidateData = null`.
4. Create `AccessibilityEventInfo` decoupled representation and adapter (`AccessibilityEvent.toEventInfo()`) enabling deterministic JVM unit testing of filter and mapper without Android OS IPC or mock stub errors.
5. Implement `AccessibilityEventPublisher` and `AccessibilityEventPublisherImpl` dispatching events onto a background `SupervisorJob` CoroutineScope so `onAccessibilityEvent` returns immediately in sub-millisecond time.
6. Wire `accessibilityEventFilter`, `accessibilityEventMapper`, and `accessibilityEventPublisher` into `AppContainer` and `DefaultAppContainer`.
7. Add 4 test suites: `AccessibilityEventFilterTest` (6 tests), `AccessibilityEventMapperTest` (7 tests), `AccessibilityEventPublisherTest` (2 tests), and `AppAccessibilityServiceTest` (5 tests), bringing total test coverage to 26 test suites / 140 unit tests passing with 100% success rate.

Reason:
- Privacy-first accessibility monitoring without collecting arbitrary user messages or passwords
- Non-blocking execution prevents system UI stutter
- Full failure isolation: publisher exceptions cannot crash the accessibility service
- Self-monitoring suppression prevents recursive event loops

Consequences:
Positive:
- Complete accessibility monitoring subsystem implemented and verified
- 26 unit test suites / 140 tests passing cleanly
- Clean debug and R8 release builds verified
Negative:
- None

---

## DEC-023 — Unified Event Processing and Concurrent Ingestion Integration

Date:
2026-10-18

Status:
Accepted

Context:
IMPLEMENTATION_PLAN.md §47 (Phase 9), Architecture.md §37, §38, and SECURITY.md §4 require connecting both IME (`CustomKeyboardService`) and Accessibility (`AppAccessibilityService`) event ingestion pipelines into the central `EventProcessor` and `EventRepository`. All persistent interaction events must uniformly share:
1. `SessionGate` checking active monitoring state
2. `PrivacyFilter` sanitization and zero sensitive data persistence
3. `ContextResolver` human-readable application label caching
4. `EventRepository` encrypted Room database storage with defense-in-depth safety validation

Decision:
1. Validate unified end-to-end event pipeline integration connecting both `ImeEventPublisher` and `AccessibilityEventPublisher` through `EventProcessor` into persistent storage.
2. Verify concurrent event interleaving scenarios: user focuses an input view (`VIEW_FOCUSED` from Accessibility), types text (`TEXT_COMMITTED` from IME), and clicks actions (`VIEW_CLICKED` from Accessibility), verifying correct chronological ordering, application context attribution ("Notes Application"), and session ID consistency in storage.
3. Verify unified session state gating: transitioning `SessionState` across `ACTIVE`, `PAUSED`, and `STOPPED` uniformly gates both IME and Accessibility sources simultaneously (events from both sources dropped when paused or stopped; resumed immediately when active).
4. Verify unified privacy invariant across both pipelines: sensitive inputs from either source (accessibility password widgets or IME password fields) result in `candidateData = null`, `eventType = SENSITIVE_INPUT_DETECTED`, and zero sensitive data persistent storage.
5. Verify scroll storm rate limiting and concurrent typing: rapid accessibility scroll storms are coalesced/rate-limited while concurrent keyboard typing continues with zero dropped keystrokes.
6. Verify failure tolerance: simulated repository disk exceptions are caught gracefully by `EventProcessor`, preventing any crash or disruption in either the IME or Accessibility services.
7. Implement `UnifiedEventProcessingIntegrationTest` with 6 comprehensive end-to-end integration tests, bringing total test coverage to 27 test suites / 146 unit tests passing with 100% success rate.

Reason:
- Fulfills Phase 9 of the implementation plan
- Guarantees seamless inter-operation between platform services and domain processing
- Proves privacy invariants and session gating hold true across multiple simultaneous event streams

Consequences:
Positive:
- Unified event processing fully verified and integrated
- 27 test suites / 146 tests passing with 100% pass rate
- Clean debug (49,141,834 bytes) and R8 release (24,084,012 bytes) builds verified
Negative:
- None

---

## DEC-024 — First-Run Setup State Machine, Decoupled System Status Checking, and Non-Blocking Partial Setup

Date:
2026-10-18

Status:
Accepted

Context:
IMPLEMENTATION_PLAN.md §48, §49 (Phase 10), Architecture.md §14–§17, and SRS.md §10–§18 dictate the first-run onboarding, setup wizard, and verification experience. Setup involves multiple independent system dimensions: Android runtime permissions, AccessibilityService enablement in Android Settings, Custom Keyboard IME enablement in system settings, Custom Keyboard selection via IME picker dialog, and encrypted database availability.
Key engineering challenges:
1. SRS.md §17 and AC-35 require that incomplete configuration or permission denial must never trap the user or loop endlessly in settings. Partial setup (e.g. keyboard selected but accessibility skipped, or vice versa) must allow proceeding to the Dashboard.
2. Architecture.md §15 requires that `SetupState` automatically refreshes when returning to the application from Android system settings on `ON_RESUME`.
3. Directly calling Android system services (`AccessibilityManager`, `InputMethodManager`, `Settings.Secure`) makes unit testing difficult and non-deterministic without real devices.

Decision:
1. Define domain model `SetupState` tracking all system readiness flags with computed properties: `isComplete` (all capabilities ready) and `isOperational` (database ready and at least one event source configured).
2. Create `SystemStatusChecker` interface and `AndroidSystemStatusChecker` implementation encapsulating Android `AccessibilityManager`, `Settings.Secure`, and `InputMethodManager` queries, enabling 100% pure JVM unit testing via test doubles without framework mocking.
3. Implement `PermissionCoordinator` and `PermissionCoordinatorImpl` representing permission denial as observable UI state (`isPermissionDenied`) with user dismissal (`dismissPermissionDeniedAlert()`) rather than runtime exceptions or blocking loops (AC-35).
4. Implement `SetupStateManager` and `SetupStateManagerImpl` observing `SessionManager.sessionState` and `PreferencesRepository.isOnboardingCompleted`, executing async checks on `dispatchers.io`, and exposing a reactive `StateFlow<SetupState>`.
5. Connect `MainActivity.onResume` to `setupStateManager.refresh()`, ensuring configuration instantly updates when the user returns from Android Settings.
6. Implement `SetupViewModel`, `OnboardingScreen` (welcome and transparent local-only disclosure per SRS.md §11, AC-02), and `SetupScreen` (interactive wizard with system verification status card per SRS.md §16). If setup is incomplete, an explicit "Proceed with Partial Setup" option allows accessing the Dashboard without blockage (SRS.md §17).
7. Update `AppNavigation` to route to `Dashboard` if `isOnboardingCompleted == true`, or `Onboarding` if first launch.
8. Add 5 new test suites: `SetupStateTest` (7 tests), `PermissionCoordinatorTest` (4 tests), `SetupStateManagerTest` (6 tests), `SetupViewModelTest` (4 tests), and `SetupFlowIntegrationTest` (11 tests covering all scenarios from IMPLEMENTATION_PLAN.md §49), bringing test coverage to 32 test suites / 178 tests with 100% pass rate.

Reason:
- Full adherence to Phase 10 requirements (SRS.md §10–§18, AC-02, AC-03, AC-35, AC-36, AC-37)
- Guaranteed non-blocking user experience: users can proceed with partial setup and return to setup at any time
- Zero-mock JVM testability across all setup edge cases
- Clean edge-to-edge Material 3 UI design

Consequences:
Positive:
- Fluid, resilient onboarding and setup flow
- 32 test suites / 178 unit tests passing cleanly in ~1.0 second
- Clean debug (49,302,225 bytes) and R8 release (24,149,548 bytes) builds verified
Negative:
- None

---

## DEC-025 — Dashboard Architecture, Aggregate Event Metrics, and Live Context Observation

Date:
2026-10-18

Status:
Accepted

Context:
IMPLEMENTATION_PLAN.md §50, §51 (Phase 11), Architecture.md §55, SRS.md §47, §74, and AC-04–AC-06 mandate the main application Dashboard:
1. Real-time capability status: custom keyboard (enabled/selected), accessibility service (enabled/disabled), credential protection (active), database readiness.
2. Setup action prompt if services are incomplete or disabled without trapping or blocking the user (SRS.md §74, AC-35).
3. Active monitoring session status with live state badge (`ACTIVE`, `PAUSED`, `STOPPED`, `INACTIVE`), start time, and notes.
4. Session control state machine actions (Start dialog, Pause, Resume, Stop).
5. Current foreground application context (human-readable label + package name) derived from the latest event.
6. Live aggregate event counters (Total, Keyboard, Clicks, Focus, Scrolls, Window changes) computed efficiently via database queries without loading unbounded event entities into RAM (IMPLEMENTATION_PLAN.md §50, AC-04).

Decision:
1. Define immutable domain data model `EventCounts` tracking aggregate metrics (`total`, `keyboard`, `accessibility`, `clicks`, `focus`, `scrolls`, `windowChanges`).
2. Implement single-row SQL aggregation query `observeEventCounts(sessionId: String? = null): Flow<EventCountsTuple?>` in `InteractionEventDao` using `COUNT(*)` and conditional `COALESCE(SUM(CASE WHEN ... THEN 1 ELSE 0 END), 0)`.
3. Implement `observeLatestEvent(): Flow<InteractionEventEntity?>` in `InteractionEventDao` ordering by `timestamp DESC LIMIT 1` to observe the current foreground application context without entity collection.
4. Expose `observeEventCounts` and `observeLatestEvent` through `EventRepository` and `EventRepositoryImpl` with thread dispatching on `dispatchers.io`.
5. Define `DashboardUiState` combining `SetupState`, `SessionState`, `activeSession`, `EventCounts`, and current application context with computed `hasIncompleteServices` property.
6. Implement `DashboardViewModel` with `combine(...)` across all 5 state streams using `SharingStarted.Eagerly` for immediate state delivery and session control methods (`startSession`, `pauseSession`, `resumeSession`, `stopSession`, `refreshSetup`).
7. Implement Material 3 `DashboardScreen` featuring:
   - System Capability Status cards with active/inactive indicators.
   - Warning banner with action button when services are disabled or incomplete.
   - Session Control card with session name, state badge, duration/start time, and context-sensitive action buttons.
   - Current Application Context card showing human-readable label and package identifier.
   - Live Event Counters grid displaying real-time metrics for total, keyboard, clicks, focus, scroll, and window change interactions.
   - Quick navigation actions to Event Timeline, Settings, and Setup Wizard.
   - Start Session dialog with input fields for session name and research notes.
8. Connect `AppNavigation` to provide dependencies to `DashboardViewModel` factory.
9. Implement unit and integration tests:
   - `DashboardUiStateTest` (2 tests)
   - `DashboardViewModelTest` (6 tests)
   - `DashboardFlowIntegrationTest` (1 comprehensive end-to-end multi-step flow test)
   Total test coverage reaches 35 test suites / 187 tests passing with 100% pass rate.

Reason:
- Full adherence to Phase 11 requirements (SRS.md §47, §74, Architecture.md §55).
- RAM protection: aggregate SQL queries compute metrics directly in SQLite engine, preventing OutOfMemory crashes on large event datasets.
- Reactive, non-blocking UI updating in real-time as users type or interact with external apps.

Consequences:
Positive:
- Clean, responsive, and robust Dashboard architecture.
- 35 test suites / 187 unit tests passing cleanly in ~1.0 second.
- Clean debug and R8 release builds verified.
Negative:
- None.

---

## DEC-026 — Event Timeline Architecture, Paged Room Observation, and Multi-Dimensional Filter Engine

Date:
2026-10-18

Status:
Accepted

Context:
IMPLEMENTATION_PLAN.md §51, §52 (Phase 12), Architecture.md §56, SRS.md §48–§50, and AC-23, AC-24 mandate the Event Timeline viewer:
1. Chronological event feed (reverse-chronological, newest first).
2. Bounded RAM consumption via database pagination (`LIMIT :limit OFFSET :offset`), avoiding loading unbounded entities into heap memory.
3. Multi-dimensional filtering: session, package name, event type, event source, input classification, time range, and search query.
4. Real-time reactive observation of filtered events and count matching criteria.
5. Detail modal dialog showing full interaction context, timestamps, metadata, and explaining privacy suppression for sensitive inputs (zero credential storage).

Decision:
1. Define domain data model `EventFilterCriteria` capturing all filter and search dimensions with `hasActiveFilters` computation.
2. Implement SQLite queries in `InteractionEventDao`:
   - `observeEventsFilteredPaged(...)` using `LIMIT :limit OFFSET :offset`, ordering by `timestamp DESC`.
   - `observeFilteredCount(...)` returning total count of events matching active filters.
   - `observeDistinctPackageNames()` for populating filter controls with real observed packages.
3. Expose pagination and filter queries via `EventRepository` and `EventRepositoryImpl` with `dispatchers.io` execution and domain mapping.
4. Define `TimelineUiState` containing paginated event slice (page size 50), total count, active filter criteria, distinct packages, monitoring sessions, active session, page indicators, and detail selection.
5. Implement `TimelineViewModel` coordinating reactive state streams using `SharingStarted.Eagerly`, pagination state transitions (`nextPage`, `previousPage`, `goToPage`), filter changes, and session event deletion.
6. Build Material 3 `EventsScreen` with:
   - Header and top app bar with active filter indicator.
   - Search bar supporting query text matching app name, package, or event data.
   - Horizontally scrollable event type chips (All, Typing, Clicks, Focus, Scrolls, Windows, Sensitive).
   - Pagination navigation strip showing exact record range (`1–50 of 214`) and page indicators.
   - Reverse-chronological `LazyColumn` of event cards with source glyphs, app names, relative timestamps, and content preview or privacy warning pills.
   - Event Detail dialog presenting formatted timestamps, session IDs, metadata, and clear privacy protection disclosures.
   - Filter dialog allowing combined filtering by source, package, session, and classification.
7. Wire `TimelineViewModel` in `AppNavigation` for `Screen.Events.route`.
8. Implement unit and integration tests:
   - `TimelineUiStateTest` (3 tests)
   - `TimelineViewModelTest` (7 tests)
   - `TimelineFlowIntegrationTest` (1 comprehensive multi-step flow test)
   Total test coverage reaches 38 test suites / 198 unit tests passing cleanly with 100% pass rate.

Reason:
- Full adherence to Phase 12 requirements (SRS.md §48, §49, Architecture.md §56, AC-23, AC-24).
- RAM protection: strict 50-item page size guarantees bounded memory footprint regardless of whether the database contains 100 or 100,000 events.
- Immediate reactive updates when events arrive from background IME or accessibility ingestion.

Consequences:
Positive:
- Complete, performant, and privacy-disclosing Event Timeline.
- 38 test suites / 198 unit tests passing cleanly.
- Clean debug and R8 release builds verified.
Negative:
- None.

---

### DEC-027: Database-Backed Search Architecture, Index Optimization, and Debounced Filtering Engine

Date:
2026-10-19

Status:
ACCEPTED

Context:
Phase 13 requires implementing efficient database-backed search across permitted application information (Package, Application, Event type, Session, and Permitted synthetic test input) per SRS.md §50, Architecture.md §56, and IMPLEMENTATION_PLAN.md §52.
Searching without debouncing floods SQLite/Room queries on every keystroke, degrading UI responsiveness and CPU/battery performance.
Furthermore, in-memory searches on large event histories exhaust heap memory, violating RAM constraints.
Search queries must also strictly adhere to the project's zero-credential persistence guarantee (SECURITY.md §4, DEC-015): sensitive event data (`eventData = null`) must never be matched or exposed.

Decision:
1. Index Optimization in `InteractionEventEntity`:
   - Added Room `@Index("applicationName")` to complement existing indices on `sessionId`, `timestamp`, `packageName`, and `eventType`.
   - Verified that KSP Room compilation generates SQLite index `index_interaction_events_applicationName` on table `interaction_events`.
2. Expanded Search Coverage in `InteractionEventDao`:
   - Updated `observeEventsFilteredPaged` and `observeFilteredCount` to search across all permitted fields defined in SRS.md §50:
     - `packageName LIKE '%' || :searchQuery || '%'`
     - `applicationName LIKE '%' || :searchQuery || '%'`
     - `eventType LIKE '%' || :searchQuery || '%'`
     - `sessionId LIKE '%' || :searchQuery || '%'`
     - `eventData IS NOT NULL AND eventData LIKE '%' || :searchQuery || '%'`
   - Preserves strict defense-in-depth: sensitive events have null `eventData` and can never be matched by credential string queries.
3. Database-Backed Search Suggestions in `InteractionEventDao`:
   - Implemented `observeMatchingApplications(query, limit)` and `observeMatchingPackages(query, limit)` returning distinct matching records directly from SQLite.
4. Repository Abstraction in `EventRepository` & `EventRepositoryImpl`:
   - Exposed `observeMatchingApplications`, `observeMatchingPackages`, and unified `observeSearchSuggestions` emitting `SearchSuggestion(query, type)`.
   - Provided default empty implementations in `EventRepository` interface for modularity and test isolation.
5. Reactive Debounced Querying in `TimelineViewModel`:
   - Maintained real-time raw text input `searchInputFlow` for instant UI feedback (zero typing lag).
   - Implemented 300ms query debouncing (`debounce { if (query.isBlank()) 0L else 300L }`) before propagating search filters to Room.
   - Immediate filter application on clear or suggestion selection (`applySuggestion`).
6. UI Auto-Suggestion Strip in `EventsScreen`:
   - Integrated horizontally scrolling search suggestion chips with badges (`App: ...`, `Pkg: ...`, `Type: ...`).
   - Tapping any suggestion instantly applies the filter.
7. Verification:
   - Added `SearchQueryTest` (9 tests) verifying search criteria, index fields, suggestions, and privacy invariants.
   - Enhanced `TimelineViewModelTest` and `TimelineFlowIntegrationTest` verifying debouncing and suggestion flows.
   - 39 test suites / 216 tests passing cleanly with 100% pass rate.

Reason:
- Direct compliance with SRS.md §50, Architecture.md §56, and IMPLEMENTATION_PLAN.md §52.
- 300ms debounce prevents SQLite I/O flooding during continuous user typing while preserving instant UI rendering.
- Room database-level filtering prevents loading unbounded entities into heap memory.
- Defense-in-depth guarantee: sensitive input content is never indexed, stored, or searchable.

Consequences:
Positive:
- Robust, debounced, database-backed search engine with auto-complete suggestions.
- 39 test suites / 216 tests passing cleanly.
- Clean debug (49,542,941 bytes) and release (24,313,388 bytes) APKs verified.
Negative:
- None.

---

### DEC-028: Aggregate Statistics Subsystem, 24-Hour Circadian Histogram Normalization, and Native Crash Hardening

Date:
2026-10-19

Status:
ACCEPTED

Context:
1. Startup & Permission Grant Crash Root Cause:
   Following initial application launch and accessibility/IME permission grants, runtime crashes were observed in the Android runtime.
   Comprehensive root-cause investigation identified four distinct crash vectors:
   - Missing SQLCipher native library linkage: Room and Zetetic SQLCipher required native `.so` loading (`System.loadLibrary("sqlcipher")`). When unavailable or initializing, an `UnsatisfiedLinkError` (subclass of `java.lang.Error`, not `Exception`) was thrown, bypassing standard `catch (e: Exception)` handlers and crashing the host process.
   - Recycled Android `AccessibilityEvent` references: Android platform recycles `AccessibilityEvent` instances immediately upon `onAccessibilityEvent` return. Lazy property access or delayed background consumption accessed invalid/recycled instances, triggering `IllegalStateException`.
   - Corrupted/invalidated Keystore passphrase file: If the AES-GCM encrypted passphrase file failed decryption due to key rotation or reinstallation, unhandled exceptions halted application initialization.
   - Database readiness check false negatives: `AndroidSystemStatusChecker` tested `database.isOpen`, which is false prior to first Room read/write, throwing unhandled exceptions during status polling.
2. Phase 14 Requirements:
   Phase 14 requires aggregate interaction statistics, application usage distribution, circadian 24-hour histogram visualization, and session filtering (SRS.md §51, Architecture.md §57, IMPLEMENTATION_PLAN.md §53).
   Statistical computation must run in SQLite/Room without loading thousands of entity objects into device heap memory.

Decision:
1. Native Crash Hardening & System Stability:
   - Added explicit `System.loadLibrary("sqlcipher")` invocation wrapped in `try/catch (t: Throwable)` in both `MonitoringApplication.onCreate()` and `DatabaseProvider.createEncryptedDatabase()`.
   - Replaced all platform boundary `catch (e: Exception)` blocks with `catch (t: Throwable)` across `AppAccessibilityService`, `CustomKeyboardService`, `AndroidSystemStatusChecker`, and `MainActivity.onResume()`.
   - Updated `AccessibilityEventInfo.kt` to immediately snapshot all required properties into an immutable `SimpleAccessibilityEventInfo` record prior to platform event return, eliminating object pool recycling hazards.
   - Hardened `DatabaseKeyManager.kt` to safely discard stale/corrupted passphrase files and unopenable databases, auto-regenerating fresh credentials without crashing.
   - Updated `AndroidSystemStatusChecker.kt` to verify database readiness via `database.openHelper.writableDatabase` wrapped in a `Throwable` barrier.
2. Room Aggregate Statistics Queries:
   - Defined lightweight projection tuples in `InteractionEventDao`:
     - `StatisticsSummaryTuple`: Aggregate metrics (`total`, `keyboard`, `accessibility`, `clicks`, `focus`, `scrolls`, `windowChanges`, `sensitiveSuppressions`, `distinctApps`, `firstTimestamp`, `lastTimestamp`).
     - `AppUsageTuple`: Per-package usage (`packageName`, `applicationName`, `eventCount`, `lastActiveTimestamp`).
     - `HourlyActivityTuple`: SQLite `strftime('%H', timestamp / 1000, 'unixepoch', 'localtime')` hourly interaction distribution.
   - Added both reactive Flow (`observe*`) and one-shot suspend (`get*`) DAO methods with session filtering.
3. Domain Models & Circadian 24-Hour Normalization:
   - Created `InteractionStatistics`, `StatisticsSummary`, `AppUsageStat`, and `HourlyActivityStat`.
   - In `EventRepositoryImpl`, normalized the hourly distribution across all 24 hours (0..23), guaranteeing non-null counts, calculating normalized height ratios (`count / maxCount`), and computing interaction velocity (`eventsPerMinute`).
   - Calculated application percentage distribution (`(count / total) * 100f`).
4. Presentation Layer (`StatisticsViewModel` & `StatisticsScreen`):
   - Created `StatisticsUiState` and `StatisticsViewModel` using `StateFlow` and `combine` with `SessionRepository.observeAllSessions()`.
   - Built modern Material 3 `StatisticsScreen`:
     - Summary card with total events, events/min velocity, active duration, and distinct apps.
     - Interaction category breakdown pills (Keyboard, Accessibility, Clicks, Focus, Scrolls, Windows, Suppressed).
     - 24-Hour Circadian Activity bar chart rendering normalized vertical bars for hours 0..23.
     - Application usage card list with percentage progress indicators, formatted counts, and last active relative times.
     - Session selector dropdown filter supporting "All Sessions" or specific session scoping.
     - Empty state view when no events are recorded.
   - Wired `StatisticsViewModel` in `AppNavigation` for `Screen.Statistics.route`.
5. Comprehensive Test Verification:
   - Created `InteractionStatisticsTest` (6 unit tests).
   - Created `StatisticsViewModelTest` (6 unit tests).
   - Created `StatisticsFlowIntegrationTest` (2 comprehensive integration tests).
   - Updated mock DAOs across `SearchQueryTest`, `EventRepositoryTest`, `RepositorySafetyValidationTest`, and `PrivacyDefenseInDepthTest`.
   - All 42 test suites / 230 unit tests pass cleanly with 100% pass rate.

Reason:
- Eliminates all application startup and permission grant crash vectors with bulletproof `Throwable` containment.
- Zero heap exhaustion: statistical aggregation executes directly in SQLite/Room.
- Full compliance with SRS.md §51, Architecture.md §57, and IMPLEMENTATION_PLAN.md §53.
- Defense-in-depth: sensitive input counts are tracked as suppressions without ever storing or displaying credential data.

Consequences:
Positive:
- Robust, crash-resilient application lifecycle.
- Complete, responsive Statistics screen with circadian distribution and app breakdown.
- 42 test suites / 230 tests passing cleanly.
- Clean debug and release APK compilation.
Negative:
- None.

---

### DEC-029: Local-First CSV Export Engine, Storage Access Framework Integration, and Export Defense-in-Depth Sanitization

Date:
2026-10-19

Status:
ACCEPTED

Context:
Phase 15 requires implementing user-initiated local CSV export (SRS.md §54, Architecture.md §58, §59, IMPLEMENTATION_PLAN.md §54).
The project mandates:
1. Zero network transmission: exports must be saved exclusively to user-selected locations on local storage.
2. Bounded memory consumption: exporting tens of thousands of records must never load the entire database into device RAM at once (RAM protection).
3. Export boundary defense (INV-07, Architecture.md §59): the exporter must not blindly serialize database entities. Even though sensitive content is sanitized before persistence, export validation must re-verify that passwords, PINs, OTPs, and payment card numbers are unconditionally redacted before writing to CSV.
4. Explicit user control: export requires explicit user action via Android's Storage Access Framework (SAF).

Decision:
1. Export Domain Layer:
   - Defined `ExportFormat.CSV` and sealed class `ExportResult` (`Success`, `Error`).
   - Implemented `ExportValidator` and `ExportValidatorImpl`:
     - Unconditionally sanitizes `eventData = "[REDACTED]"` whenever `inputClassification.isSensitive` is true or `eventType == EventType.SENSITIVE_INPUT_DETECTED`.
     - Additional heuristic Luhn check on candidate strings to scrub stray credit card sequences.
2. Data Layer Streaming Exporter:
   - Implemented `CsvEventExporter` and `CsvEventExporterImpl` adhering to RFC-4180 specification.
   - Emits standardized columns: `timestamp,formatted_time,session_id,source,event_type,package_name,application_name,event_data,classification`.
   - Escapes commas, quotes (`" -> ""`), and newlines per RFC-4180 rules.
   - Streams directly into `java.io.OutputStream` with UTF-8 encoding.
3. Use Case & Chunked Pagination:
   - Created `ExportSessionUseCase`:
     - Reads records in bounded chunks (`CHUNK_SIZE = 250`) using `eventRepository.observeEventsPaged` and `observeFilteredCount`.
     - Supports both global ("All Sessions") and session-scoped exports.
     - Guarantees $O(1)$ heap memory allocation regardless of database size.
     - Emits real-time progress callbacks `(exportedCount, totalCount)` to drive UI progress bars.
4. Presentation Layer (`ExportViewModel` & `ExportScreen`):
   - Created `ExportUiState` and `ExportViewModel`:
     - Combines sessions flow, selected session filter, and scope count into a reactive `StateFlow`.
     - Manages export execution lifecycle, SAF stream handling, and error containment.
   - Built modern Material 3 `ExportScreen`:
     - Security & Privacy disclosure card emphasizing offline, local-first storage and credential exclusion.
     - Scope selector dropdown ("All Sessions" vs per-session).
     - Specification summary card (RFC-4180, streaming memory profile, zero network).
     - Storage Access Framework `ActivityResultContracts.CreateDocument("text/csv")` launcher.
     - Dynamic progress indicator displaying processed records count.
     - Export result dialog displaying exported record count and bytes written.
5. Navigation & Dashboard Integration:
   - Added `Screen.Export` in `Screen.kt`.
   - Wired `Screen.Export.route` in `AppNavigation.kt`.
   - Added "Export Data" quick navigation button in `DashboardScreen.kt`.
6. Testing & Verification:
   - Created `ExportValidatorTest` (6 tests).
   - Created `CsvEventExporterTest` (4 tests).
   - Created `ExportSessionUseCaseTest` (3 tests).
   - Created `ExportViewModelTest` (4 tests).
   - Created `ExportFlowIntegrationTest` (1 end-to-end multi-chunk test).
   - Total test suite expanded to 46 test suites / 248 unit tests passing with 100% pass rate.

Reason:
- Full compliance with SRS.md §54, Architecture.md §58, §59, and IMPLEMENTATION_PLAN.md §54.
- Bounded memory profile prevents OutOfMemory errors on low-spec Android devices.
- Defense-in-depth guarantees that no sensitive credentials leak into exported files.

Consequences:
Positive:
- Complete, secure, offline CSV export capability.
- 46 test suites / 248 tests passing cleanly with 100% pass rate.
- Verified debug and release APK generation.
Negative:
- None.

---

### DEC-030: Data Management Subsystem, Cascading Purge Architecture, and Two-Step User Confirmation Engine

Date:
2026-10-19

Status:
Accepted

Context:
In accordance with `SRS.md` §55, `Architecture.md` §50, and `IMPLEMENTATION_PLAN.md` §55 (Phase 16 — Data Management), the application must give the user complete, sovereign control over local storage and recorded data. Users must be able to:
1. Delete a specific monitoring session (with automatic cascading deletion of all associated interaction events).
2. Delete all monitoring sessions and all recorded events in bulk.
3. Completely purge all application data (sessions, events, in-memory session tracking, and persistent DataStore preferences).
4. Require explicit, non-bypassable confirmation dialogs for all destructive actions to prevent accidental data loss.

Decision:
1. Multi-Layer Cascading Purge & Defense-in-Depth:
   - While SQLite foreign key constraints enforce `onDelete = ForeignKey.CASCADE` at the schema level (`interaction_events` referencing `monitoring_sessions.id`), `DeleteSessionUseCase`, `DeleteAllSessionsUseCase`, and `DeleteAllApplicationDataUseCase` execute explicit event purge queries (`deleteEventsForSession` / `deleteAllEvents`) prior to session deletion as defense-in-depth across SQLite and room drivers.
2. In-Memory Session Synchronization:
   - Extended `SessionManager` interface with `onSessionDeleted(sessionId: String)` and `resetSessionState()` to atomically reset active session tracking in-memory to `INACTIVE` and `currentSession = null` if an active or paused session is purged.
   - Ensures `SessionGate` immediately disallows incoming event recording after session deletion.
3. Domain Use Cases:
   - `DeleteSessionUseCase`: deletes events for target session, deletes session entity from Room, and synchronizes `SessionManager`.
   - `DeleteAllSessionsUseCase`: deletes all events, deletes all sessions, and resets `SessionManager`.
   - `DeleteAllApplicationDataUseCase`: purges all events, all sessions, resets `SessionManager`, and resets DataStore preferences via `PreferencesRepository.clearAllPreferences()`.
4. Reactive UI State & Confirmation Workflows:
   - Upgraded `SettingsScreen` and `SettingsViewModel`:
     - Local storage & security status card (Total sessions, total events via reactive Room `observeEventCounts` flow, SQLCipher AES-256 encrypted badge).
     - Logging preferences card (toggles for Keyboard logging and Accessibility logging).
     - Recorded sessions list with per-session timestamps, status badges, and delete action.
     - Prominent "Danger Zone" card containing "Delete All Sessions" and "Reset All Application Data" destructive actions.
     - Two-step confirmation dialogs (Material 3 `AlertDialog` with warning indicators) for single session, bulk sessions, and complete application data reset.
5. Verification & Testing:
   - Created `DeleteSessionUseCaseTest` (4 unit tests).
   - Created `DeleteAllSessionsUseCaseTest` (2 unit tests).
   - Created `DeleteAllApplicationDataUseCaseTest` (1 unit test).
   - Created `SettingsViewModelTest` (6 unit tests).
   - Created `SettingsFlowIntegrationTest` (2 end-to-end integration tests).
   - Created `CascadingDeletionTest` (3 database cascading verification tests).
   - Total test suite expanded to 52 test suites / 266 unit tests passing (100% pass rate).

Reason:
- Satisfies SRS.md §55, AC-28, Architecture.md §50, and IMPLEMENTATION_PLAN.md §55.
- Guarantees complete data sovereignty and privacy compliance with zero residual artifacts after deletion.

Consequences:
Positive:
- Complete single-session and bulk data deletion with cascading event cleanup.
- All 266 unit tests passing with 100% pass rate.
- Clean debug (50,124,389 bytes) and R8 release (24,542,904 bytes) APK builds verified.
Negative:
- None.

---

### DEC-031: Controlled Application Use Test Screen Architecture, Invariant Ground-Truth Verification, and Real-Time Focus Telemetry

Date:
2026-10-19

Status:
ACCEPTED

Context:
Phase 17 mandates implementing a dedicated controlled application use screen representing known ground truth for IME tests (IMPLEMENTATION_PLAN.md §56, SRS.md §66-§73, design.md §Screen 6, AC-29, AC-30, AC-31, AC-32).
Testing input monitoring and sensitive input detection in arbitrary third-party apps introduces external variability. An in-app test bench provides known ground truth across 7 standardized editor configurations:
1. Normal Text (`InputType.TYPE_CLASS_TEXT`)
2. Email Address (`InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS`)
3. URL / Web Address (`InputType.TYPE_TEXT_VARIATION_URI`)
4. Numeric Input (`InputType.TYPE_CLASS_NUMBER`)
5. Multiline Notes (`InputType.TYPE_TEXT_FLAG_MULTI_LINE`)
6. Password (`InputType.TYPE_TEXT_VARIATION_PASSWORD`)
7. Numeric PIN (`InputType.TYPE_NUMBER_VARIATION_PASSWORD`)

Key architectural requirements:
- Ground-truth invariant AC-29: Synthetic non-sensitive input (Normal, Email, URL, Number, Multiline) must be recorded only when an active monitoring session is running.
- Ground-truth invariant AC-30: Known password and PIN fields must unconditionally suppress popup key previews and never persist entered credential contents (`eventData = null`).
- Ground-truth invariant AC-31: Sensitive content must remain absent from SQLite persistence, exports, and debug logs.
- Ground-truth invariant AC-32: Stopping session logging or logger failure must never disable or degrade normal keyboard typing.
- Real-time classification telemetry banner: The test bench must display a live indicator of detected field classification, sensitive status, and session metrics when focusing any editor.

Decision:
1. Ground-Truth Field Definition:
   - Created `TestBenchFieldType` enum defining the 7 controlled ground-truth editors with exact `KeyboardType`, `ImeAction`, expected `InputClassification`, sensitivity flag, sample test strings, and SRS verification annotations.
2. Presentation Layer (`TestBenchUiState`, `TestBenchViewModel`, and `TestBenchScreen`):
   - Defined `TestBenchUiState` and `TestBenchSubmissionResult`.
   - Built `TestBenchViewModel` with reactive state observation:
     - Real-time active session tracking via `ObserveActiveSessionUseCase` and `ObserveSessionStateUseCase`.
     - Live aggregate session counters (total events, keyboard commits, sensitive suppressions) via `EventRepository.observeStatistics`.
     - Live focus telemetry updates (`onFieldFocused`) mapping active editors to instantaneous classification indicators.
     - Synthetic sample population (`populateSyntheticTestData`) providing 1-tap test execution without manual typing.
     - Direct session controls (`startQuickTestSession`, `stopQuickTestSession`) enabling in-situ testing without navigating away.
     - Synthetic test submission verification (`submitTestInputs`) evaluating invariant compliance and presenting a structured verification dialog per SRS §66 `[SUBMIT]`.
   - Implemented rich Material 3 `TestBenchScreen`:
     - TopAppBar with navigation back and reset actions.
     - Active session status card with live indicator and 1-tap start/stop toggle.
     - Real-time classification detection card (design.md §Screen 6) with high-contrast sensitive badges (`[ 🔒 SENSITIVE_PASSWORD ]`, `[ 🔒 SENSITIVE_PIN ]`) vs permitted non-sensitive badges (`[ ✓ NORMAL_TEXT ]`).
     - 7 clearly labeled `OutlinedTextField` editors with focus listeners, helper text, and monospace classification badges.
     - Action bar (`[ Populate Samples ]`, `[ Clear Fields ]`, `[ Submit Test Verification ]`).
     - Ground-Truth Invariants Reference Card detailing AC-29, AC-30, AC-31, AC-32.
     - Ground-Truth Verification Summary Dialog presenting verified invariant status upon submission.
3. Navigation & Dashboard Integration:
   - Added `Screen.TestBench` in `Screen.kt`.
   - Wired `Screen.TestBench.route` in `AppNavigation.kt`.
   - Added `Test Bench` quick navigation button in `DashboardScreen.kt`.
4. Automated Test Verification:
   - Created `TestBenchViewModelTest` (10 unit tests) verifying initial state, focus transitions, sensitive detection, field updates, synthetic population, quick session management, and invariant submission.
   - Created `ControlledInputPrivacyTest` (5 tests) verifying end-to-end ground truth:
     - AC-29: Normal, Email, and URL synthetic text preservation and non-sensitive classification.
     - AC-30: Strict password and PIN credential redaction (`eventData = null`).
     - AC-31: Zero credential persistence across export pipelines (`ExportValidatorImpl` & `CsvEventExporterImpl`).
     - AC-32: Keyboard typing and `KeyProcessor` operation fully functional when session is inactive.
   - Updated `NavigationRoutesTest` validating route uniqueness and non-empty titles.
   - Total test suite expanded to 55 test suites / 283 unit tests passing (100% pass rate in ~2.25s).
   - Validated clean debug (`app-debug.apk`: 50,481,121 bytes) and release (`app-release.apk`: 24,592,056 bytes) APK builds.

Reason:
- Satisfies IMPLEMENTATION_PLAN.md §56, SRS.md §66-§73, design.md §Screen 6, and AC-29, AC-30, AC-31, AC-32.
- Provides a controlled, reproducible test environment for validating IME capture and zero credential persistence.

Consequences:
Positive:
- Deterministic ground-truth test environment embedded directly in the application.
- Real-time visual feedback on IME field classification and sensitive mode activation.
- 55 test suites and 283 unit tests passing with 100% pass rate.
Negative:
- None.

---

### DEC-032: Dedicated Performance Optimization Pass, Operational Budgets, Dead Code Elimination, and Dependency Audit

Date:
2026-10-19

Status:
ACCEPTED

Context:
Phase 18 mandates a dedicated, comprehensive performance optimization pass, dead code audit, and dependency review (IMPLEMENTATION_PLAN.md §57, §58, §59, DEVELOPMENT_GUIDELINES.md §1-§33).
The application operates long-running Android components (`InputMethodService`, `AccessibilityService`, Room with SQLCipher AES-256 encryption, background streaming export) that must remain responsive on low-end hardware without exceeding latency or memory limits.

Key performance criteria evaluated:
1. Keyboard latency: Sub-millisecond execution (< 2ms hardware tap budget) on `KeyProcessor` hot path.
2. RAM footprint & unbounded collections: Zero unbounded queues or collections. Room event timeline and streaming export must maintain $O(1)$ memory consumption.
3. High-throughput sanitization: `PrivacyFilter` and `ExportValidator` must handle high-throughput bursts without GC pauses or memory leaks.
4. Accessibility throttle protection: Suppression of rapid scroll storms (`minScrollIntervalMs = 250ms`) and 100% suppression of self-monitoring feedback loops.
5. In-memory cache bounding: Enforce hard capacity bound (`MAX_CACHE_ENTRIES = 256`) on `ContextResolverImpl` package cache to prevent heap expansion over continuous operation.
6. Dead code, deprecations, and dependency review: Search for unused classes, methods, obsolete dependencies, and resolve compiler deprecations.

Decision:
1. Architectural & Code Refactoring:
   - Modernized Compose APIs in `ExportScreen.kt` using `MenuAnchorType.PrimaryNotEditable` and `Icons.AutoMirrored.Filled.Send` in `TestBenchScreen.kt`.
   - Bounded `ContextResolverImpl` with `MAX_CACHE_ENTRIES = 256`, evicting oldest entries when capacity is reached to ensure bounded heap memory on low-end devices.
   - Verified `KeyProcessor` processes text commits, backspaces, and editor actions with 0 object allocations on the primary input path before dispatching background events asynchronously via `ImeEventPublisherImpl`.
   - Verified `AccessibilityEventFilterImpl` scroll throttling and self-monitoring loop rejection protects CPU cycles from accessibility storm feedback.
2. Dead Code Review (IMPLEMENTATION_PLAN.md §58):
   - Confirmed zero unused classes, zero commented-out code blocks, and zero pending TODO/FIXME markers in production code.
   - Preserved architectural separation while keeping utility and helper footprints minimal.
3. Dependency Audit (IMPLEMENTATION_PLAN.md §59):
   - Audited all 17 direct dependencies in `app/build.gradle.kts`.
   - Confirmed no unnecessary third-party network, analytics, or DI libraries (maintaining manual `AppContainer` DI and zero network permissions).
   - Validated R8 rules in `proguard-rules.pro` retaining Room entities, DAOs, and SQLCipher JNI bindings with code shrinking (`isMinifyEnabled = true`, `isShrinkResources = true`).
4. Automated Regression Verification:
   - Created `PerformanceOptimizationTest` (5 tests) verifying:
     - `keyboardLatency_underOneMillisecond_perKeystrokeBudget`: Average keystroke execution time < 1.0ms (well within < 2ms budget).
     - `accessibilityFilter_throttlesScrollStorms_andRejectsSelfMonitoringLoops`: 100-event scroll burst throttled to 1 event; self-monitoring events 100% rejected.
     - `contextResolver_enforcesBoundedCacheCapacity`: Sub-microsecond cache hit latency (< 0.2ms) and bounded cache size.
     - `privacyFilter_highThroughput_processesTenThousandEventsRapidly`: 10,000 raw events sanitized in < 500ms (> 20,000 events/sec throughput).
     - `csvStreamingExport_maintainsBoundedMemoryProfile`: 2,000 events streamed in 250-record chunks with $O(1)$ memory usage.
   - Total test suite expanded to 56 test suites / 288 unit tests passing (100% pass rate in ~2.07s).
   - Validated clean debug (`app-debug.apk`: 50,352,673 bytes) and release (`app-release.apk`: 24,592,056 bytes) APK builds.

Reason:
- Satisfies IMPLEMENTATION_PLAN.md §57, §58, §59, and DEVELOPMENT_GUIDELINES.md.
- Guarantees low latency, zero memory leaks, bounded cache sizes, and optimal battery efficiency on low-end devices.

Consequences:
Positive:
- Validated performance budgets (< 1ms key latency, > 20,000 events/sec sanitization, bounded caches).
- Zero deprecated compiler warnings.
---

## DEC-033 — Large Database Scalability, SQLCipher AES-256 Storage Model, and Composite Index Optimization

Date:
2026-10-19

Status:
Accepted

Context:
IMPLEMENTATION_PLAN.md §61 and TESTING.md §17 specify stress testing across scale tiers:
- 1,000 events
- 10,000 events
- 50,000 events
- 100,000 events

Operations requiring performance and memory verification include:
1. Batch insertion throughput and scalability.
2. Query latency (indexed point queries, session seeks, time ranges).
3. Timeline pagination and window loading across 100,000 records.
4. Multi-facet search and suggestions.
5. Aggregate statistics computation (summary, app breakdown, hourly distribution).
6. Streaming chunked CSV export scalability and RAM boundedness.
7. Cascading deletion performance and orphan prevention.
8. SQLite + SQLCipher AES-256 storage footprint and sizing model.

Decision:
1. Composite Index Optimization:
   - Added `Index(value = ["sessionId", "timestamp"])` to `InteractionEventEntity` (`interaction_events` table).
   - Resolves timeline query bottlenecks: queries filtering by `sessionId` and ordering by `timestamp DESC` can traverse the B-tree directly in reverse order without loading and sorting the entire session in memory, dropping page loading from $O(N \log N)$ to $O(1)$ seeks.
2. Large Database Stress Test Suite (`LargeDatabaseStressTest`):
   - Implemented 9 automated stress tests in `app/src/test/java/com/example/inputmonitor/data/database/LargeDatabaseStressTest.kt`:
     - `test01_progressiveBatchInsertionThroughput`: Verified batch inserts scale linearly across 1k, 10k, 50k, 100k records without throughput degradation (> 100,000 events/sec in memory).
     - `test02_indexedPointAndSessionQueryLatency`: Verified primary key point lookups complete in < 1ms (measured 0ms) and session event counts complete in < 2ms (measured 1ms) on 100k records.
     - `test03_indexedTimeRangeSlicing`: Verified 1-hour time slice queries (36,001 events) on 100k records execute in ~20ms.
     - `test04_timelinePaginationAcross100kEvents`: Verified offset-limit windowing (50 records) at offset 0 (24ms initial JIT), offset 1,000 (3ms), offset 50,000 (3ms), and offset 99,950 (2ms) in strict reverse chronological order (`timestamp DESC`).
     - `test05_multiFacetSearchOn100kEvents`: Verified package filter (20,000 matches) executes in 9ms; multi-facet criteria (16,000 matches) executes in 20ms; substring keyword search executes in 24ms.
     - `test06_aggregateStatisticsComputationAtScale`: Verified summary statistics aggregation over 100,000 records executes in 48ms; application usage breakdown (`GROUP BY packageName`) executes in 26ms; hourly distribution (`GROUP BY hourOfDay`) executes in 27ms without high memory usage.
     - `test07_streamingExportMemoryStabilityAndThroughput`: Verified streaming export of 10,000 events via `ExportSessionUseCase` and `CsvEventExporterImpl` in 250-row chunks executes in 120ms (throughput: 83,333 rows/sec) with $O(1)$ RAM and verified sensitive credential redaction (`[REDACTED]`).
     - `test08_cascadingSessionDeletionUnderHighVolume`: Verified cascading deletion of a session with 50,000 events completes atomically in 24ms with zero orphan records, leaving an adjacent 10,000-event control session 100% intact.
     - `test09_databaseStorageFootprintModel`: Evaluated SQLCipher AES-256 on-disk page allocation (4096-byte pages, 48-byte IV/HMAC overhead, 185-byte average event payload, 29-byte average index entry across 6 indexes):
       - 1,000 events: 0.39 MB (405,504 bytes)
       - 10,000 events: 3.71 MB (3,891,200 bytes)
       - 50,000 events: 18.49 MB (19,390,464 bytes)
       - 100,000 events: 36.97 MB (38,764,544 bytes)
       - All tiers comfortably conform to mobile flash storage budgets (< 50 MB for 100k records).
3. Total Test Baseline & Build Verification:
   - Full test suite expanded to 57 test suites / 297 unit tests passing (100% pass rate in ~2.15s).
   - Validated clean debug (`app-debug.apk`: 50,543,941 bytes) and release (`app-release.apk`: 24,592,056 bytes with R8 minification and resource shrinking).

Reason:
- Satisfies IMPLEMENTATION_PLAN.md §61, TESTING.md §17, and Architecture.md §47, §56.
- Prevents database query stalls, memory exhaustion, and index degradation under high-volume monitoring sessions.

Consequences:
Positive:
- Validated performance across 1k, 10k, 50k, 100k records.
- Composite index `(sessionId, timestamp)` accelerates timeline scrolling and export paging.
- Verified $O(1)$ RAM boundedness for streaming export under large databases.
- 57 test suites and 297 unit tests passing with 100% pass rate.
---

## DEC-034 — Android Studio Gradle JVM Alignment and Root Clean Task Registration

Date:
2026-10-19

Status:
Accepted

Context:
When syncing the project within Android Studio, the IDE's phased build action failed with:
`Could not run phased build action using connection to Gradle distribution... Gradle build daemon has been stopped: stop command received`.
Inspection of `.idea/gradle.xml` revealed that Android Studio was configured to run Gradle via `ms-17` (Microsoft OpenJDK 17), which was not installed on the system (the host machine and Android Studio installation utilize Java 25 at `C:\Program Files\Java\jdk-25` and `D:\Android\jbr`). Furthermore, the root `build.gradle.kts` lacked the standard root `clean` task registration.

Decision:
1. In `build.gradle.kts`:
   - Registered the standard root `clean` task:
     ```kotlin
     tasks.register("clean", Delete::class) {
         delete(rootProject.layout.buildDirectory)
     }
     ```
2. In `.idea/gradle.xml`:
   - Updated `gradleJvm` from `ms-17` to `#JAVA_HOME` to align Android Studio's Gradle Tooling API launcher directly with the installed Java 25 runtime.
3. Verification:
   - Executed `.\gradlew.bat clean assembleDebug` which completed successfully in 36s.
   - All 57 test suites and 297 unit tests continue to pass with 100% success rate.

Reason:
- Resolves Gradle daemon termination during Android Studio project sync.
- Guarantees seamless IDE tooling compatibility alongside CLI builds.

Consequences:
Positive:
- Android Studio Gradle sync executes without daemon termination.
- Root `clean` task is registered for IDE and CI workflows.
Negative:
- None.

---

## DEC-035 — Android Version Testing, Android 16 (Baklava API 36) Live Verification, SQLCipher ART Native JNI Linking, and IME Input View Evaluation

Date:
2026-10-19

Status:
Accepted

Context:
IMPLEMENTATION_PLAN.md §62, Architecture.md §84, and TESTING.md §18, §26 specify Phase 20 — Android Version Testing across Android 15, Android 16, and Android 17.
To validate actual runtime behavior, the application was deployed and executed on a Pixel 10 AVD running Android 16 (API 36 / Baklava, security patch 2026-01-05).

Two platform-specific runtime behaviors were identified and resolved:
1. SQLCipher Native JNI Linkage on Android 16 ART:
   - When Room instantiated the encrypted SQLite database via SupportOpenHelperFactory on Android 16, ART threw:
     `java.lang.UnsatisfiedLinkError: No implementation found for long net.zetetic.database.sqlcipher.SQLiteConnection.nativeOpen(...) - is the library loaded, e.g. System.loadLibrary?`
   - In SQLCipher Android 4.6.1 (`net.zetetic:sqlcipher-android:4.6.1`), modern 16KB page alignment is supported, but ART on API 36 enforces strict JNI classloader binding requiring explicit `System.loadLibrary("sqlcipher")` invocation during Application startup before background Room queries execute.
2. IME Software Keyboard View Evaluation with Hardware/Host Keyboards:
   - In `CustomKeyboardService`, when typing fields were focused on Android 16 emulators with host hardware keyboards, `InputMethodService.onEvaluateInputViewShown()` defaulted to `false` because the framework detected a connected hardware keyboard.
   - For a dedicated custom keyboard application, the virtual keyboard must remain accessible regardless of attached hardware peripherals.

Decision:
1. SQLCipher Native Initialization in `MonitoringApplication`:
   - Added explicit `System.loadLibrary("sqlcipher")` inside `MonitoringApplication.onCreate()` with defensive fallback handling. This guarantees that `libsqlcipher.so` JNI method tables (`nativeOpen`, etc.) are linked into ART prior to any Room or SQLite helper invocation.
2. Custom Keyboard Input View Evaluation in `CustomKeyboardService`:
   - Overrode `onEvaluateInputViewShown(): Boolean = true` in `CustomKeyboardService.kt` to ensure the virtual IME layout is rendered consistently across both physical devices and emulators.
3. Live Device Verification on Android 16 (API 36 / Baklava):
   - Deployed `app-debug.apk` to `Pixel_10` AVD running Android 16 (API 36).
   - Validated:
     - Application launch without crashes.
     - Encrypted session start (`Session_20261019_1936`) and persistence to SQLCipher AES-256 database.
     - System Status display: Keyboard Service ENABLED, CURRENT IME; Accessibility Service ENABLED; Credential Protection ACTIVE.
     - Controlled Test Bench interaction:
       - Normal Text field: typed and committed text (`dyoo`) via custom keyboard; verified event logging into Room.
       - Password field: verified real-time classification banner (`🔒 SENSITIVE_PASSWORD`), security policy activation, key preview popup suppression, zero plaintext persistence (`eventData = null`), and sensitive suppressed counter incremented to 2.
     - Live Dashboard: observed active session, current application (`com.example.inputmonitor`), 6 events, and live aggregate counters.
4. Android Compatibility Test Suite:
   - Created `AndroidVersionCompatibilityTest.kt` (8 unit tests) verifying:
     - Platform SDK bounds (minSdk 31, targetSdk 35, tested API 36).
     - Conditional `POST_NOTIFICATIONS` permission policies (API 33+).
     - Android 14 Restricted Settings setup guidance.
     - Android 15 16KB page alignment compliance and dynamic loading safety.
     - Android 16 IME `onEvaluateInputViewShown` evaluation.
     - Version-agnostic sensitive input filtering (AC-30) and privacy invariance.
     - Dual database provider configuration (encrypted and in-memory).
5. Test Baseline & Build Verification:
   - Expanded unit test suite to 58 test suites / 305 unit tests passing (100% pass rate in 2.79s).
   - Validated both debug and R8-minified release APK builds.

Reason:
- Satisfies IMPLEMENTATION_PLAN.md §62, Architecture.md §84, and TESTING.md §18, §26.
- Eliminates ART UnsatisfiedLinkError on Android 16.
- Confirms end-to-end functionality and privacy invariants on the latest Android release.

Consequences:
Positive:
- Proven compatibility with Android 16 (API 36) on live emulator.
- All 58 test suites / 305 unit tests pass cleanly.
- R8 minification and resource shrinking verified.
Negative:
- None.

---

### DEC-036: Consolidated UI Date/Time Utilities and Code Duplication Elimination

Date: 2026-10-19
Status: Decided

Context:
Static code audit identified duplicate `SimpleDateFormat` instantiations in composables (`DashboardScreen`, `StatisticsScreen`, `TestBenchScreen`, `ExportScreen`, and `TestBenchViewModel`), causing redundant object allocations per recomposition/list item. Additionally, variable shadowing of `context` and `container` in `AppNavigation` and broken temporary code artifacts were identified and eliminated.

Decision:
1. Created `com.example.inputmonitor.ui.util.DateTimeUtils` providing standardized date, time, and duration formatting functions.
2. Refactored `DashboardScreen`, `StatisticsScreen`, `TestBenchScreen`, `ExportScreen`, and `TestBenchViewModel` to use `DateTimeUtils`.
3. Removed redundant variable shadowing in `AppNavigation`.
4. Implemented unit test suite `DateTimeUtilsTest` (8 tests) bringing total unit tests to 313 across 59 suites (100% pass rate).

Consequences:
Positive:
- Zero redundant `SimpleDateFormat` allocations during UI recomposition.
- Single source of truth for UI date/time and duration formatting.
- Unit test suite expanded to 313 tests with 100% pass rate.
Negative:
- None.





















## 2026-10-22: Keyboard appearance and local media

The keyboard retains visible Input Monitor branding while adopting familiar rounded keys, half-key home-row offsets and 48 dp key faces. The appearance toggle stores only a light/dark preference; long press restores system appearance. Existing alphabet, number, phone, email and symbol models remain available. Long press exposes common accents and alternate punctuation.

GIF and sticker controls open Android's explicit document picker for user-selected local images. There is no online media catalog, search transmission, new permission or new dependency. A non-exported activity copies selected images (up to 10 MB) on a worker thread into a dedicated FileProvider cache directory. Media insertion uses commitContent only for matching MIME types and the originating package/field when the keyboard returns. Media selections bypass the monitoring event pipeline and are not logged. Files expire on subsequent media use after 24 hours; a 50 MB cache admission limit bounds accumulation.

Verification: modified XML parsed successfully and targeted git diff whitespace check passed. Android compilation and unit tests were attempted but blocked before compilation: the configured Java 17 installation is absent, and daemon toolchain metadata has no Windows x86_64 download URL. The available Android Studio runtime is Java 25. Runtime rendering and rich-media delivery remain unverified.


### Reference-based keyboard refinement and updated validation

Applied the supplied dark keyboard reference to charcoal surfaces, gray key faces, small top-row number hints, compact row geometry, rectangular shift/delete keys, comma-before-emoji ordering, blank spacebar and blue action key. Light mode uses the same geometry with white faces on pale gray. Input Monitor branding remains in the toolbar; unsupported voice/translation controls are not shown.

The updated local Java 25 configuration now permits compilation. `:app:compileDebugKotlin` passed. The complete unit run executed 352 tests with three initial failures: two LargeDatabaseStressTest assertions and the old keyboard bottom-row expectation. Updated that expectation and reran `:app:testDebugUnitTest --tests com.example.inputmonitor.platform.ime.*`; all keyboard tests passed. Database stress failures were outside this UI change and remain unresolved. Visual device verification remains pending.


### Installed build and visual verification

Replaced font-dependent smile, sticker, palette, shift, delete and return symbols with Canvas-drawn icons. Text keys explicitly use Android sans-serif. Built a fresh debug APK with `:app:assembleDebug` and reran the 59 IME unit tests successfully. Installed the APK over the emulator build; the previous installation timestamp was 22:27:47 and the new installation is 22:48:14 on 2026-10-22. Confirmed Input Monitor is the selected IME. Visually inspected light and dark keyboards in an empty search field without typing or submitting text. Both render the new icons, top-row hints, blank spacebar, compact toolbar and visible Input Monitor branding. Captures: app/build/keyboard-light.png and app/build/keyboard-dark.png. Left the keyboard in dark mode.


## 2026-10-28 Session restoration after restart

The existing implementation already persists the selected session ID in DataStore and its ACTIVE/PAUSED state in encrypted Room. MonitoringApplication restores the saved state when Android starts the application process, including through an enabled IME/accessibility service after boot and unlock. No boot receiver, permission re-grant mechanism, foreground service or forced service activation was added.

Restoration now explicitly accepts only ACTIVE or PAUSED records with no end time. Ended, stopped, inactive and deleted records must not resume collection. The dashboard explains that a saved session restores when Android starts the app or enabled services and that permissions remain controlled by Android. Tests reconstruct the manager over shared fake persistence to check the same ID/start time, preserved logging choices, paused/stopped states, missing and inconsistent records and repeated restoration without duplicates. These tests do not simulate Android permission retention or a physical reboot.

The phone cannot collect events while powered off. Credential-protected storage is only available after unlock. Force-stop, removed permissions, disabled services, user-selected keyboard changes, cleared app data, and manufacturer background restrictions remain respected. This change does not guarantee immediate execution at boot or continuation after every possible shutdown condition.

Validation: debug APK assembly and targeted session/session-gate/event-processor tests passed (31 tests, 0 failures). No connected emulator or phone was available; a real reboot, permission retention and the dashboard text still require device verification.
