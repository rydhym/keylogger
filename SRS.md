# ClearTrail requirements v2
The user approved replacing the keyboard project with a visible Android usage monitor and encrypted dashboard on 2026-10-17. Originals are preserved in docs/archive/original-keyboard-spec as historical material.

## Product and first slice
Usage Access + explicit active session + selected apps. Initial selection: WhatsApp, Instagram, Chrome. Collect package identifiers, foreground/background timestamps, estimated durations, session IDs and battery percentage. No keyboard, Accessibility, UI text, messages, notifications, URLs, contacts, clipboard, camera or microphone collection.
Visible launcher, disclosure, actual permission checks, start/pause/resume/stop, manual capture/sync, bounded encrypted offline outbox.
Private browser dashboard: pairing, recovery import/export, decryption, timeline, app/date filters, summaries, CSV export, deletion and revocation.
Backend: separate upload/read tokens, ciphertext storage, pagination, idempotent upload, 30-day retention, 5000-record/device and 10-device limits.
Clearly labelled synthetic demo; never present demo as connected-phone data.

## Later increments
Scheduled collection/sync with persistent visibility; richer local history; explicitly shared text/links with preview; expanded app selection; configurable retention; production accounts, deployment and recovery. These are not part of the completed foundation.

## Acceptance
Only selected apps within active intervals. No collection while paused. Storage failure must not advance capture boundary. Upload failure retains encrypted outbox for retry. Backend never receives data key or plaintext history. Upload token cannot read/delete; dashboard token cannot upload. Revocation removes credentials and records. Device validation and release gates must be reported honestly.
