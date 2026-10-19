# ClearTrail architecture v2
## Layout
android/: Kotlin platform Activity, UsageCollector, SessionController, Vault, Outbox, SyncClient.
server/: Node HTTP + SQLite ciphertext API.
web/: buildless HTML/CSS/JS + Web Crypto dashboard.
tests/: protocol/model/API integration tests.
docs/archive/original-keyboard-spec/: historical originals.

## Pipeline
Usage Access -> active session/selected apps -> bounded usage query -> metadata summary -> validation -> AES-256-GCM -> encrypted outbox -> HTTPS upload -> ciphertext database -> authorized dashboard download -> browser decryption/validation -> charts/timeline.

## Pairing protocol
Browser generates random 32-byte data key. Server provisions separate 32-byte upload/read tokens and stores only SHA-256 hashes. Phone bundle contains upload token + data key; recovery bundle contains read token + data key. Browser never sends the data key to server. AES-GCM uses fresh random 12-byte nonce, 128-bit tag and AAD cleartrail:v1:<deviceId>:<recordId>.

## Boundaries
Android Keystore wraps local pairing/session secrets; outbox holds encrypted envelopes. Single background executor keeps I/O off UI. No Accessibility components. Optional ClearTrailInputMethod is system-bound with BIND_INPUT_METHOD and runs in :keyboard. Its static key views write only to the active InputConnection; it neither reads editor text nor calls usage/storage/network/browser code. Separate process prevents shared controller memory; it is not a separate Android UID or permission sandbox. The package still has INTERNET for explicit usage sync, but the IME has no network path. Browser practice history/email stay inside the explicitly focused browser exercise.
Node 24 built-in HTTP/SQLite, loopback bind with production TLS proxy. Strict envelope schema, role authorization, host/Origin checks, no CORS, bounded request/rate/storage/pagination.
Browser secrets live in memory; recovery JSON intentionally contains secret key and requires private storage. No localStorage or third-party scripts. Decrypted values render using textContent.
Server sees device identity, envelope sizes/timing, not contents. Compromised dashboard delivery could steal unlocked keys; E2EE does not protect compromised clients.

## Deferred
Background scheduler, accounts, key rotation, multi-server hosting, production recovery and independent security review.
