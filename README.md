# Input Monitor

This repository contains the Input Monitor Android application and custom keyboard. The Android project is at the repository root; open this folder directly in Android Studio.

## Open and build

1. Clone this repository or update your existing `main` branch.
2. Open the folder containing `settings.gradle.kts` in Android Studio.
3. Install Android SDK platform 35 and configure the Gradle JDK to match `gradle/gradle-daemon-jvm.properties` (currently Java 25).
4. Let Gradle sync, choose an Android 12/API 31 or newer device, and run the `app` configuration.
5. Enable and select Input Monitor through Android's keyboard settings to use its keyboard. Accessibility access is a separate user-controlled setting.

Build a debug APK with `gradlew.bat :app:assembleDebug` on Windows. The output is `app/build/outputs/apk/debug/app-debug.apk`. Configure the SDK path locally; machine-specific SDK paths and Java overrides are not included.

## Session recovery

An active session is saved and restored when Android starts the application or its enabled services after restart and unlock. Paused sessions stay paused; stopped, ended or deleted sessions are not resumed. The app cannot collect anything while the phone is powered off or re-grant access revoked by Android or the user. Force-stop and device background restrictions remain respected. A live device reboot test is still pending.

## Keyboard and media

The keyboard has Input Monitor branding, light/dark themes, letter/number/symbol layouts, offline emoji search, long-press alternatives, and local GIF/sticker selection through Android's document picker. Media insertion depends on the receiving editor's support. Online GIF catalogs, voice typing and translation are not included.

## Verification and documentation

The latest source was built successfully. Earlier keyboard checks passed 59 tests; the later session/gating/processor run passed 31 tests. Two database stress-test failures from an earlier complete suite remain documented; this is not a claim that every application test currently passes.

See `SECURITY.md` and `SRS.md` for monitoring/privacy requirements, `TESTING.md` for checks and outstanding device validation, and `decision.md` for implementation decisions. Development reports are in `reports/` and `test report/`.
