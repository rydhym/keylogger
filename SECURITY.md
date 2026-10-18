# ClearTrail security v2
Visible usage metadata only. No IME, Accessibility, notifications, screen capture, hidden behavior, credentials or permission bypasses.
Permission alone never starts collection. Select apps and start session explicitly. Pause/stop must persist active interval before advancing state; failure remains visible. Process restart ends active collection conservatively, without retroactive downtime collection. Initial sync is user-triggered.
AES-256-GCM, fresh 12-byte nonce, 128-bit tag, identity-bound AAD. Android Keystore wraps local secrets. Disable Android backup. No plaintext fallback.
Browser secrets in memory only. Recovery file contains the data key/read authority and is intentionally sensitive; losing it can make records unrecoverable.
Remote HTTPS required; debug-only loopback/emulator HTTP allowed. Separate bearer upload/read credentials, hashed server-side. Strict schema, CSP, Origin/host checks, request/rate/storage limits. No analytics or external scripts.
Server: 30-day retention, 5000 records/device, 10 devices. Bounded local outbox errors on full; no silent eviction.
Remote purge does not stop queued phone uploads; revoke removes device credentials and all server records. Local clear/unpair removes queue and wrapped credentials after confirmation. Deletion is logical, not a forensic-erasure guarantee.
E2EE does not protect compromised phone/browser, maliciously served dashboard JavaScript, or stolen recovery files. Production deployment, physical-device and security review remain gates.
