# Decisions
## DEC-009 — Explicit practice-history email draft (2026-10-18)
No email backend is configured. The dashboard exposes an explicit recipient field and Send history control that requests a mailto draft of only the currently displayed practice entries. Sending/review happens in the user's email client. No automatic sending, delivery claim, credentials, server endpoint or unrelated dashboard data. Recipient is outside the practice focus boundary and cleared on pagehide.

## DEC-008 — Focused browser keyboard exercise (2026-10-18)
College assignment adds matching reference/live US keyboard layouts and an ordered, bounded in-memory keydown history. No prior keyboard UI exists in this checkout. Key listeners attach only to a dedicated focusable practice area; no global hooks, background capture, persistence or transmission. Blur/visibility loss resets held keys. Android and usage-monitoring scope remain unchanged.

## DEC-001 — Scope replacement
User approved visible usage monitoring + encrypted dashboard, no keyboard/Accessibility capture. Originals archived verbatim.
## DEC-002 — Small platform stack
Kotlin/platform widgets for setup; Node 24 built-in HTTP/SQLite; plain browser JS/Web Crypto. No npm runtime dependencies. Room/Compose/Hilt not needed for initial bounded outbox; reconsider on measured need.
## DEC-003 — End-to-end pairing
Browser-generated AES key; separate server-generated upload/read credentials stored hashed. Phone bundle and dashboard recovery transfer secrets explicitly. Android Keystore wrapping. QR/key rotation deferred.
## DEC-004 — Manual first slice
Explicit capture/pause/stop/sync. No background service, scheduler or auto-start. Session boundaries, outbox caps and errors visible.
## DEC-005 — Android baseline
minSdk 29, compileSdk/targetSdk 36, AGP 8.13.2, Gradle 8.13, Kotlin 2.2.21, JDK 17. Pinned compatible baseline, not newest-version claim. Android 17 needs verification. No JDK/SDK found initially.
## DEC-006 — Local foundation before deployment
Build/test locally before hosting device data. Production requires TLS proxy, durable storage, account/recovery design and device/security tests. Do not publish unfinished backend.

## DEC-007 — Build tooling recovered
Prepared portable JDK/SDK after discovering no existing installation. Verified Gradle wrapper and JDK downloads against vendor checksums. Used official SDK tooling. Fixed compilation and lint findings without suppressing security checks; both APK variants build. Live-device claims remain deferred until tested.
