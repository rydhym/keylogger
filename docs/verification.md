# Verification — 2026-10-17

## Optional IME update — 2026-10-19
Final-source Android assembleDebug, assembleRelease (unsigned), testDebugUnitTest (9 passing) and lintDebug passed. Lint has zero errors/five warnings: Gradle advisory and four localization findings in setup state text. Web syntax and all 27 Node tests passed. APK manifest inspection confirms a single system-bound BIND_INPUT_METHOD service in :keyboard and only the original two requested permissions. Source review/regression checks found no editor-text reads, history, persistence, transmission or usage/browser dependency in the IME path. The service uses static keys, transient modifiers, current InputConnection writes/key events, no suggestions/fullscreen extract/previews, and FLAG_SECURE. The browser practice/history/email implementation is unchanged.

Fresh debug artifact: `android/app/build/outputs/apk/debug/app-debug.apk`. Files under `artifacts/` below remain older foundation evidence. No new APK installed or runtime/device tests performed; build and static checks cannot establish host-app/password/settings behavior. No email sent, Git commit or push performed for this update. Android IME platform reference: https://developer.android.com/develop/ui/views/touch-and-input/creating-input-method.

## Automated
`npm.cmd run check` and `npm.cmd test`: 20 passing Node tests, no skips. This includes AES-GCM authentication/AAD binding, strict metadata schema, API roles/isolation, ciphertext-only storage, retry/nonce handling, body limits, retention, purge/revoke, host/Origin checks, pagination, persistent restart, rate/device/storage bounds and Java interoperability.

`scripts/build-android.ps1 -Tasks assembleDebug,testDebugUnitTest,lintDebug,assembleRelease`: BUILD SUCCESSFUL. Six UsageIntervalsTest tests passed. Debug lint: zero errors, one newer-Gradle advisory. Release lintVital passed. Source uses two deprecated platform calls for minSdk compatibility; future modernization can be evaluated independently.

Both packaged manifests inspected with aapt: only PACKAGE_USAGE_STATS and INTERNET permissions. No IME, Accessibility service, notification listener, boot receiver or foreground service.

## Artifacts
| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| artifacts/cleartrail-debug.apk | 2538285 | 26051b5e946d381845daa299e0c234634b3ae70dfa7fb0c7d4491db76a788ccc |
| artifacts/cleartrail-release-unsigned.apk | 63760 | 9937168615048b33650547130c1872dda76908f1ea588e8d04059d5282d05b3c |

The release APK is unsigned and has not been executed. These sizes are measured artifact sizes, not installed footprint or runtime memory.

## Browser
Verified locked/empty state, clearly labelled demo, application/date filters, recovery-file import, seven encrypted synthetic records downloaded from actual API and decrypted in browser, lock clearing displayed history, and 390px responsive layout without page overflow. CSV downloaded and parsed: 84 synthetic rows. No browser console errors observed. Test device revoked and test recovery removed.

Screenshot: artifacts/dashboard-demo.jpg. It contains synthetic data, not phone activity.

## Not verified
No APK installed on a physical phone or emulator. Android UsageStatsManager behavior, foreground estimates, session/Keystore recovery, outbox failure handling, networking and battery need runtime validation. No production deployment or signed release. No Android 15/16/17 compatibility certification.

## Resolved build issues
Initial Java-style multi-variable Kotlin declaration corrected. Debug network-security domains now explicitly exclude subdomains as lint requires. No security lint baseline or ignore was added. Initial sandbox loopback restrictions required running API tests with local-network access; application assertions then passed.
