# Technology
Android: Kotlin 2.2.21, platform Activity/widgets, UsageStatsManager; javax.crypto AES/GCM/NoPadding and AndroidKeyStore. Platform HTTP client, HTTPS remote.
minSdk 29, compileSdk/targetSdk 36; AGP 8.13.2; Gradle 8.13; JDK 17.
Server: Node 24 (observed 24.18.0), built-in HTTP/SQLite/crypto.
Web: HTML/CSS/ES modules/Web Crypto; no external scripts/assets.
Storage: ciphertext SQLite server, bounded encrypted envelope files phone.
Testing: node:test, JUnit for pure Kotlin session logic. Actual verification in PROGRESS.md.
No IME, Accessibility, Room, Hilt, Compose, WorkManager or npm runtime dependencies.
Tooling initially available: Node/npm/Python. Java/Gradle/adb/SDK absent from checked locations.

## Verified toolchain, 2026-10-17
Downloaded a checksum-verified Temurin JDK 17.0.20.1+1 and official Android SDK into ignored .tools directories. Installed platform 36 revision 2, build-tools 35.0.0 and platform-tools. Gradle 8.13 wrapper JAR matches official SHA-256 81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f; distribution checksum is pinned in wrapper properties.
Debug APK, unsigned optimized release APK, six Kotlin unit tests and Android lint verified. One lint warning recommends a newer Gradle; the compatible baseline is intentionally pinned. New SDK metadata also produces a non-blocking XML-version warning with this AGP version.
