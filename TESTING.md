# ClearTrail testing v2
Optional Android IME (2026-10-19): final-source assembleDebug, assembleRelease (unsigned), testDebugUnitTest and lintDebug all passed. Nine Kotlin tests (three new ImeKeys tests plus six interval tests), zero failures/skips. Web syntax and all 27 Node tests passed, including system-bound IME metadata and source isolation checks. Debug lint: zero errors, five warnings (existing Gradle update advisory plus four localization findings on setup status text); SDK XML/deprecated-inset warnings remain non-blocking. Packaged manifest inspected with aapt: IME is exported only with BIND_INPUT_METHOD, runs in :keyboard, references input-method XML; requested permissions remain only Usage Access and INTERNET. IME source contains only static layout/modifier state and current-editor writes/key events, with no editor text reads or controller/storage/network/browser dependencies. Browser practice/history/email files unchanged.

No device or emulator test performed. An attempted read-only adb device check could not start its daemon in the sandbox; this supplies no evidence about device availability. Device gates: enablement warning and manual selection, accurate enable/select status, switching back, letters/digits/symbols/Shift/Caps, delete with a selection and Unicode text, arrows, multiline Enter and Go/Search/Next/Done/Send, password/web-password/visible-password/numeric-password fields, hide/reopen and rotation, landscape/gesture navigation, host-app compatibility, and confirmation that neither keyboard typing nor password input appears in dashboard practice history or drafts. TYPE_NULL custom editors are unsupported. Mail sending was not tested or performed.

Keyboard labels update (2026-10-18): syntax checks and six keyboard tests passed, including two new tests for Shift/Caps Lock case, shifted punctuation, active Caps Lock ring, unchanged reference labels, blur reset and readable history labels. Existing focus/history/email-draft tests continue to pass. No browser visual verification performed.

Keyboard email addition (2026-10-18): syntax checks and all four keyboard tests pass. Tests stub mail navigation and verify explicit submission, exact history-only draft body, recipient input exclusion, empty-history guard, clear/disabled state, safe encoding and recipient/header-injection rejection. No email sent or email app opened. Actual mail-handler behavior and browser visual verification remain untested.

Automated: crypto round trip, wrong key/tampering/AAD swap; strict metadata allowlist; pairing/endpoint validation; session boundaries and duplicate/missing transitions; API auth/roles/device isolation; body/record bounds; nonce uniqueness/idempotency; pagination/retention/delete/revoke/restart. Inspect database/WAL for synthetic plaintext and raw credentials.
Browser: demo/empty/paired/locked, filters, recovery, errors, responsive layout.
Android: assembleDebug/testDebugUnitTest/lintDebug when JDK/SDK available; manifest excludes forbidden components. JS/Java AES-GCM interoperability fixture.
Device: permission denial/revocation, selected-app and session boundaries, process restart, HTTPS, offline retries, queue/Keystore failure, delete/revoke, low storage, battery. Mark unrun tests NOT RUN.
Release requires physical-phone validation, production TLS/storage/auth/recovery, actual release APK and security review. See PROGRESS.md for results.

## Verified 2026-10-17
- npm run check: PASS.
- npm test: 20 passed, 0 failed/skipped, including Java/Web Crypto interoperability.
- Android assembleDebug and assembleRelease: PASS (release unsigned).
- Android testDebugUnitTest: 6 passed, 0 failed/skipped.
- Android lintDebug: 0 errors, 1 version-update warning. Release lintVital also passed.
- Packaged debug/release APK permissions checked with aapt: Usage Access and Internet only.
- Browser: locked/empty/demo, filters, recovery import, authenticated download/decrypt of 7 synthetic server records, lock, 390px viewport, CSV download (84 sample rows), no console errors. Synthetic test device revoked and test recovery file removed afterward.
- Physical phone, Android Keystore runtime, real app usage accuracy, offline/restart behavior on Android and production HTTPS: NOT RUN.
# Keyboard practice verification (2026-10-18)
`npm.cmd run check` passed. `npm.cmd test` passed: 22 tests, zero failures/skips, including two keyboard regression tests for identical layouts, multiple held keys/release, repeat suppression, ordered bounded history, clearing, focus exclusion, blur/visibility reset and pagehide cleanup. Initial sandbox run blocked API loopback connections (EACCES); the full suite passed after running with local networking available.

Manual browser visual/device verification of this addition has not been performed. Check focus by Tab/click, Shift+A, Tab away while holding a key, switching windows/tabs, clear history and narrow-screen horizontal scrolling. Browser/OS shortcuts and some mobile/IME input do not reliably produce physical key events.

