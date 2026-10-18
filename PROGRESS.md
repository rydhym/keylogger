# ClearTrail progress and handoff
Updated: 2026-10-18 (Asia/Calcutta).

## Source-control preparation
Initialized Git on main for the user-requested https://github.com/rydhym/keylogger.git destination (remote had no refs when inspected). Source, documentation, tests, UI assets and pinned Gradle wrapper included. Local credentials/data, downloaded tools, Gradle caches, APKs/build outputs and generated artifacts excluded. Ignore rules additionally cover environment variants, private-key containers and database files. Staged source scans found no private-key/token patterns or long literal credential assignments. Syntax checks and full Node suite passed: 26 tests, zero failures/skips. No email sent during verification.

## Live keyboard labels update
Both layouts show labeled letters, digits, paired US punctuation/symbols and readable special keys. The lower/live layout updates case with Shift/Caps Lock (including their combined effect), puts the active symbol first, and rings Caps Lock while active. History labels distinguish left/right modifiers and name arrows/Space. Held highlights, focused-only capture, bounded dashboard history and explicit email drafts remain intact. Focus loss resets labels/highlights; browser modifier state resynchronizes on the next focused key event.

Validation: syntax checks and all six keyboard tests passed, including two new modifier/label tests. Browser visual checks remain unperformed. US physical layout only; non-US layouts, IMEs and AltGr can differ. Existing compact layout omits separate function/number-pad banks, whose delivered events still appear in history.

## Practice-history email draft addition
The practice history is visible in the dashboard panel. Added a recipient email field and explicit Send history · open email draft control. No configured email service exists, so mailto opens a draft containing only the displayed practice entries, excluding recipient-field keystrokes and usage/pairing data. User reviews/sends in their email app. Empty history disables the control; clear removes exportable entries; pagehide clears recipient. No automatic sending or backend email endpoint.

Validation: syntax checks and full suite passed (24 tests, zero failures/skips). Two new tests verify explicit-action draft creation, exact history-only content, safe recipient/encoding and empty-history guard. Navigation was stubbed; no email was sent or mail client opened. Browser visuals and actual mail-handler compatibility remain untested; long-draft limits depend on the client.

## Browser keyboard exercise addition
Added a standalone focused practice panel below the timeline with matching reference/live US keyboard layouts, held-key highlights, oldest-first keydown history (latest 100, non-repeat), and clear history. This checkout had no existing keyboard UI to duplicate. Key capture is restricted to the focusable practice area; focus/window blur and hidden document reset highlights. History is memory-only and clears on pagehide. No Android capture, global hooks, storage or transmission introduced.

Validation: syntax checks passed; all 22 Node tests passed with local networking enabled, including two keyboard lifecycle/focus/history regression tests. Initial sandbox API tests failed with localhost EACCES. Browser visual and physical-device validation of this addition not performed. See README/TESTING for operation and limitations.

## Current state
BUILD STATUS: PASSING.
Phase 0: complete (scope, documentation, technology evaluation, tooling and baseline builds).
Phase 1: first manual-collection foundation implemented and host/build verified.
Phase 2: physical-device validation NOT STARTED.
Project is not production-ready or fully complete.

## Approved scope
Visible personal Android app-usage monitor and encrypted browser dashboard. No custom keyboard, Accessibility text collection, message/notification capture, hidden operation or credential logging. Initial apps: WhatsApp, Instagram, Chrome. Original eight specifications preserved in docs/archive/original-keyboard-spec; root v2 docs are current.

## Implemented
- Android visible launcher, disclosure, Usage Access settings/check, selected-app controls.
- Explicit session start/pause/resume/stop, manual capture and manual sync.
- Bounded metadata collection and estimated foreground intervals.
- Android Keystore-wrapped pairing/state, journaled encrypted outbox, idempotent upload.
- Backend authenticated ciphertext API, separate upload/read credentials, bounds, retention, purge/revocation.
- Browser pairing/recovery, AES-GCM decryption, demo, filters/charts/timeline, CSV export, lock and data controls.
- Protocol/architecture/security/design/requirements and build/setup documentation.
- Portable local JDK/SDK and pinned verified Gradle wrapper.

## Verification
- Web syntax checks: PASS.
- Node tests: 20 passed, zero failures/skips (includes JDK/Web Crypto interoperability).
- assembleDebug: PASS.
- assembleRelease: PASS, unsigned optimized artifact.
- testDebugUnitTest: 6 passed, zero failures/skips.
- lintDebug: PASS; 0 errors, 1 newer-Gradle advisory.
- release lintVital: PASS.
- Packaged permissions: only PACKAGE_USAGE_STATS and INTERNET.
- Browser: synthetic encrypted API roundtrip/recovery, filters, lock, responsive view, CSV export (84 sample rows), no console errors.
- APK installed/run on real device or emulator: NOT RUN.
- Android 15/16/17 runtime compatibility, battery, memory: NOT TESTED.

## Deliverables
- artifacts/cleartrail-debug.apk — 2,538,285 bytes.
- artifacts/cleartrail-release-unsigned.apk — 63,760 bytes; not signed for installation.
- artifacts/dashboard-demo.jpg — synthetic dashboard screenshot.
- artifacts/cleartrail-SAMPLE.csv — synthetic export.
- README.md — setup, build, pairing, limitations and deployment prerequisites.
- docs/verification.md — detailed evidence and checksums.

## Runtime handoff
Temporary local QA server stopped after verification. Run npm.cmd start from the repository and open http://127.0.0.1:4310.
Local operator token remains in ignored .local/operator-token.txt; never paste it into chat or commit it. Synthetic test device and recovery file were removed. Real phone has not been paired.
Build with scripts/build-android.ps1. Tools/cache live under ignored .tools; no global PATH changes were made.

## Known limitations
Manual capture/sync only. No scheduler, background service or boot auto-start. After process death the session pauses; uncaptured interval is discarded. Capture windows max 24 hours, event scan max 20,000, max 512 intervals, local outbox max 1000 records. Five-minute seeding can undercount an already-open long-running app. Durations/session counts are estimates.
Recovery files contain the data key; there is no server key recovery or account system yet. Production TLS hosting, key rotation, account/recovery design, security review, release signing and real-device validation remain.
Non-blocking SDK XML-version and platform-deprecation warnings recorded. Compatible Gradle baseline intentionally pinned.

## Immediate next action
Install the debug APK on an explicitly authorized test phone, pair to the local backend using USB adb reverse (or a configured HTTPS server), and verify actual Usage Access/session boundaries, encryption/outbox recovery, offline retry and dashboard sync before enabling background scheduling.
