# ClearTrail implementation plan v2
Supersedes the original custom-keyboard roadmap.
## Phase 0 — Scope/tooling
Archive originals, record scope/stack/encryption, initialize living docs, configure build/test inputs, verify tooling.
## Phase 1 — End-to-end foundation
Protocol/schema, bounded ciphertext backend, dashboard/demo, Android manual-session Usage Access collector, encrypted outbox and manual sync. Test server/models/crypto; compile Android when tools available.
## Phase 2 — Device validation
Physical phone over HTTPS: permission denial/revocation, app/session boundaries, pause/resume/stop, process death, estimates, offline retry, deletion/revoke, key loss, queue/low-storage behavior. Android 15/16/17 compatibility only when tested.
## Phase 3 — Completeness
Visible scheduled collection/sync, local history, explicit sharing with preview, broader app selection, configurable retention.
## Phase 4 — Production
TLS deployment, durable storage, accounts/recovery, key rotation, signing, optimized APK, independent security review and release tests.
Source existence is not completion. Device tests are separate from demos. Update docs and progress with exact evidence and blockers.
