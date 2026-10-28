# DEFECT & BUG REPORT: InputMonitor

**Date:** 2026-10-22  
**Test Cycle:** Targeted Final Validation & Release-Gate Audit  
**Application Version:** 0.1.0 (Build 1)  
**Target Environment:** Android 16 (API 36 Preview) / Host JDK 17 (Microsoft Build 17.0.20)  

---

## 1. Summary of Defect Classifications

| Bug ID | Severity | Status | Feature Area | Description |
| :--- | :---: | :---: | :--- | :--- |
| **DEF-001** | **HIGH** | **FIXED & RE-TESTED PASS** | Export / Share Sheet | FileProvider async stream race condition resulting in 0-byte CSV exports and IntentResolver permission denials. |
| **BUG-002** | **MEDIUM** | **MITIGATED — VERIFIED** | Build / Tooling | Android Gradle Plugin UAST crash under JDK 25 resolved by configuring project to Microsoft OpenJDK 17. |
| **BUG-001** | **LOW** | **ACCEPTED LOW RISK** | Custom Keyboard | Deprecated `getSuggestions` API warning modernized to array-based overload without regression. |

- **CRITICAL Defects:** 0
- **HIGH Defects:** 0 remaining (1 fixed)
- **MEDIUM Defects:** 0 remaining (1 mitigated)
- **LOW Defects:** 1 (Accepted Low Risk)
- **Crashes / ANRs:** 0 across all validation runs

---

## 2. Bug Details & Remediation History

### DEF-001: FileProvider Async Race Condition Producing 0-Byte Share Sheet Exports
- **BUG-ID:** DEF-001
- **Severity:** HIGH
- **Feature Area:** Export Subsystem / Android Share Sheet (`AppNavigation.kt` & `ExportViewModel.kt`)
- **Discovery Date:** 2026-10-22 during Release-Gate validation.
- **Description:**
  When clicking "Share CSV via Android Share Sheet" on the Export screen, the opened Android Chooser handed off a 0-byte temporary file to destination apps (such as Gmail or Google Drive). Furthermore, on Android 16 (API 36), the system `IntentResolver` logged:
  ```
  SecurityException: Permission Denial: reading com.example.inputmonitor.fileprovider uri content://com.example.inputmonitor.fileprovider/exports/... from pid=1167, uid=1000 requires that you obtain access using ACTION_GRANT_READ_URI_PERMISSION
  ```
- **Root Cause Analysis:**
  1. In `AppNavigation.kt`, the export execution was invoked as:
     ```kotlin
     FileOutputStream(shareFile).use { fos ->
         exportViewModel.executeExport(fos)
     }
     ```
     Because `executeExport(fos)` dispatches its work to a background coroutine (`viewModelScope.launch(dispatchers.io)`), the Kotlin `.use { }` block exited and closed the `FileOutputStream` *immediately* before the coroutine wrote any CSV bytes to disk.
  2. Additionally, modern Android IntentResolvers require URI permissions to be explicitly attached to the chooser intent's `ClipData` in addition to `intent.data`.
- **Code Fix:**
  1. Updated `ExportViewModel.kt` to accept an `onComplete: ((Boolean) -> Unit)?` callback and explicitly call `outputStream.flush()` inside the coroutine before finishing.
  2. Updated `AppNavigation.kt` to keep the stream open until `onComplete` fires, verify `shareFile.length() > 0`, and attach `clipData = ClipData.newUri(context.contentResolver, "Exported CSV", contentUri)` with `FLAG_GRANT_READ_URI_PERMISSION`.
- **Validation & Retest:**
  Rebuilt APK and installed on Pixel 10 emulator. Executed manual Share Sheet export: generated non-empty CSV `inputmonitor_All_Sessions_1792658740277.csv` (2,949 bytes, 18 events, valid RFC-4180 formatting). IntentResolver opened system chooser without any security exceptions.
- **Status:** **FIXED & RE-TESTED PASS**.

---

### BUG-002: AGP Lint Analysis Fails on Host JDK 25 due to UAST Bytecode Incompatibility
- **BUG-ID:** BUG-002
- **Severity:** MEDIUM
- **Feature Area:** Build System / Android Lint
- **Component:** Android Gradle Plugin 8.7.0 `lintAnalyzeDebug`
- **Description:** Running `./gradlew lintDebug` on a developer machine with system default `Java 25.0.1 LTS` caused a worker crash in IntelliJ UAST parser (`java.lang.NoClassDefFoundError: Could not initialize class org.jetbrains.uast.UastFacade`).
- **Remediation:**
  1. Installed Microsoft Build of OpenJDK 17 (`C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot`).
  2. Configured `gradle.properties`:
     ```properties
     org.gradle.java.home=C:\\Program Files\\Microsoft\\jdk-17.0.20.101-hotspot
     ```
  3. Configured `gradle/gradle-daemon-jvm.properties`:
     ```properties
     toolchainVersion=17
     ```
- **Validation & Retest:**
  Executed `./gradlew lintDebug --no-configuration-cache` under Microsoft OpenJDK 17. The build completed with `BUILD SUCCESSFUL in 53s` and generated an HTML report confirming **0 errors and 0 warnings**.
- **Status:** **MITIGATED — VERIFIED**.

---

### BUG-001: Deprecated `getSuggestions` API in Android TextServicesSession
- **BUG-ID:** BUG-001
- **Severity:** LOW
- **Feature Area:** Custom Keyboard / Typing Suggestions
- **Component:** `com.example.inputmonitor.platform.ime.TypingSuggestions.kt`
- **Description:** Compiler warning in `TypingSuggestions.kt:48`: `'fun getSuggestions(p0: TextInfo!, p1: Int): Unit' is deprecated`.
- **Remediation:**
  Updated the method call to the modern array-based overload supported on Android API 31+:
  ```kotlin
  session?.getSuggestions(arrayOf(TextInfo(word, 0, generation)), 3, false)
  ```
- **Validation & Retest:**
  Recompiled cleanly without warnings. Ran all keyboard unit test suites (`KeyProcessorTest`, `LocalSuggestionsTest`, `SuggestionPolicyTest`, `TypingSuggestionsTest`): 100% passed. Live typing on Android 16 demonstrated low-latency suggestions and accurate keystroke logging.
- **Status:** **ACCEPTED LOW RISK / VERIFIED**.

---

## 3. Crash & ANR Review

No crashes, FATAL EXCEPTIONs, or Application Not Responding (ANR) events occurred across the entire validation scope:
- **10,000-Event UI Monkey Stress Test:** 0 crashes, 0 ANRs, 0 OOM errors.
- **Network Interruption & Reconnection:** 0 crashes, 0 worker failures.
- **Process Death & Relaunch (`am force-stop`):** 0 crashes, 100% data retention.
- **Device Reboot (`adb reboot`):** 0 crashes, WorkManager restored clean single periodic job.
- **Share Sheet File Sharing:** 0 crashes post DEF-001 remediation.
