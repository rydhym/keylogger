# SECURITY & PRIVACY SPECIFICATION

**Document:** `SECURITY.md`  
**Version:** 1.0  
**Status:** Mandatory / Living Document

---

# 1. Purpose

This document defines the security and privacy boundaries of the Android Interaction & Input Monitoring Application.

Because the application contains:

```text
InputMethodService
AccessibilityService
Interaction metadata
Local application storage
Export functionality
```

security controls are architectural requirements rather than optional enhancements.

---

# 2. Security Objective

The system shall support Android interaction and input monitoring while minimizing the possibility that sensitive information is accidentally collected, persisted, logged, or exported.

---

# 3. Trust Model

Primary trusted actor:

```text
User / Device Owner
```

Trusted application components include:

```text
Custom Keyboard
AccessibilityService
PrivacyFilter
Repository
Room
```

However, no single component shall be assumed infallible.

Defense in depth is required.

---

# 4. Core Security Boundary

Potential content shall follow:

```text
SOURCE
  ↓
RawInteractionEvent
  ↓
EventProcessor
       ↓
Session Gate
       ↓
Classification / SensitiveInputDetector
       ↓
PrivacyFilter
       ↓
ContextResolver
  ↓
Repository Safety Validation
  ↓
Encrypted Persistence Layer
       ↓
ROOM / LOCAL STORAGE
```

No supported persistence path shall bypass this boundary.

---

# 5. Sensitive Data

The application shall not intentionally persist:

```text
Passwords
PINs
OTPs
Authentication secrets
Security codes
Authentication tokens
Payment-card credentials
Other recognized credential content
```

---

# 6. Sensitive Field Handling

When a field is classified as sensitive:

```text
User Input
    │
    ├────────────► InputConnection
    │                   ↓
    │              Target Application
    │
    └────────────► Event Pipeline
                        │
                        ▼
                 Sensitive content?
                    ┌───┴───┐
                    │       │
                   NO      YES
                    │       │
                    ▼       ▼
                 Process   Content
                 normally  discarded
                              │
                              ▼
                         Metadata only
```

The keyboard must continue functioning normally.

---

# 7. Sensitive Metadata

Permitted metadata may include:

```text
Timestamp
Session
Package
Event type
Sensitive classification
```

Example:

```text
event = SENSITIVE_INPUT_DETECTED
classification = PASSWORD
eventData = NULL
```

---

# 8. Defense in Depth

Sensitive-data protection shall occur at multiple boundaries:

```text
EditorInfoAnalyzer
       ↓
SensitiveInputDetector
       ↓
PrivacyFilter
       ↓
Repository Validation
       ↓
Export Validation
```

A mistake in one layer should not automatically expose sensitive content.

---

# 9. Repository Security Invariant

The repository shall reject or sanitize logically invalid combinations such as:

```text
classification = SENSITIVE_PASSWORD

eventData != NULL
```

This invariant must be tested.

---

# 10. Accessibility Data Minimization

Accessibility monitoring shall favor metadata.

Prefer:

```text
Event type
Timestamp
Package
Window transition
Interaction category
```

Avoid unnecessary collection of:

```text
Messages
UI text
Editable contents
Accessibility trees
Credentials
```

unless a future approved application requirement specifically requires additional non-sensitive information.

---

# 11. Local Storage

Application data shall remain in application-private storage unless explicitly exported by the user.

The application shall not intentionally expose its Room database to unrelated applications.

## Encrypted Local Storage

All permitted interaction/event data and other application-generated monitoring data persisted locally by the application shall be encrypted at rest using Android-supported cryptographic and secure key-management mechanisms.

Android Keystore is the preferred platform mechanism to evaluate during Phase 0. The final encrypted-storage mechanism, key lifecycle, and dependency choice shall be documented in `decision.md` and `tech.md` after Phase 0 begins.

Encryption keys must not be:

```text
Hardcoded in source code
Committed to Git
Stored as plaintext constants
Stored directly inside Room alongside encrypted data
Stored in plaintext preference/configuration files
Written to Logcat
Included in normal exports
Exposed through debug output
```

Key management is separate from encrypted data storage. Encrypted persistence must not silently fall back to plaintext if secure initialization fails. The affected persistence operation shall fail safely while ordinary keyboard input remains functional.

Temporary files, caches, debug dumps, crash diagnostics, and backup artifacts must not expose unencrypted protected application data or cryptographic key material. Encryption testing shall verify the storage, key-handling, failure, restart, and backup/restore behaviors described in `TESTING.md`.

Phase 0 must determine:

```text
Whether database files participate in Android backup
How encrypted data interacts with backup
Whether keys are available after restore, reinstall, or device migration
Behavior when key material becomes unavailable
Whether backup should be disabled or restricted for protected local data
```

Encryption at rest and explicit user export are separate boundaries. CSV export remains user-initiated and must pass Export Validation.

---

# 12. Network Boundary & Automatic Cloud Backup

The application is local-first with an optional, user-consented **Automatic Daily Cloud Backup** subsystem.

### Security Boundary States:

1. **Automatic Backup OFF (Default):**
   - The application functions as a completely offline local monitor.
   - Zero background network workers or transmission routines execute.
   - No data is transmitted across the network.

2. **Automatic Backup ON (User-Consented Opt-In):**
   - Only explicitly authorized, redacted CSV backup data is transmitted to the user-authenticated cloud destination.
   - Backups are scheduled approximately once every 24 hours via Android WorkManager.
   - **Enforced Security Controls:**
     - **TLS/HTTPS Mandatory:** Cleartext HTTP is strictly prohibited (`usesCleartextTraffic="false"`).
     - **System Certificate Verification:** Standard X.509 validation is strictly enforced; insecure trust-all managers are forbidden.
     - **Hardware-Backed Credential Storage:** Cloud API tokens and passwords are encrypted at rest using Android Keystore AES-256-GCM (`KeystoreBackupTokenStorage`). Plaintext persistence is prohibited.
     - **Export Sanitization Invariant:** Network transmission uses the identical [ExportSessionUseCase] pipeline. Passwords, PINs, OTPs, and payment cards remain strictly redacted (`[REDACTED]`).
     - **Immediate Revocation:** Disabling backup immediately cancels scheduled WorkManager jobs; disconnecting immediately purges stored authentication tokens from Android Keystore.
     - **No Covert Tracking:** No analytics SDKs, advertising trackers, or telemetry endpoints exist. Network access is restricted exclusively to user-specified backup endpoints.

---

# 13. Export Security

Export requires explicit user action.

Export uses the following pipeline:

```text
Encrypted Local Database
       ↓
Authorized Application Read
       ↓
Export Validation
       ↓
CSV Writer
       ↓
User-selected destination
```

Export validation remains required even though sensitive content should already have been excluded from Room.

Before writing each exportable event:

```text
Event
 ↓
Classification Check
 ↓
Sensitive?
 ├── NO → permitted output
 └── YES → no content / redacted metadata
```

Do not simply dump raw database structures.

---

# 14. Logging Security

Never intentionally log sensitive contents.

Prohibited:

```text
Log.d("IME", "password=$value")
Log.d("PIN", pin)
```

Permitted operational logging:

```text
IME service started
Session started
Sensitive field detected
Database opened
Export completed
```

Release builds should minimize verbose logs.

---

# 15. Temporary Files

Sensitive content shall not be placed into:

```text
Temporary files
Cache files
Debug dumps
Crash diagnostics intentionally generated by the app
Clipboard
Analytics
```

---

# 16. Clipboard

The application logger shall not use the clipboard as an intermediate storage mechanism for captured input.

---

# 17. Permission Principle

Request only capabilities actually required by implemented features.

Do not request broad permissions "just in case."

Every new permission requires review of:

```text
Why required?
What data does it expose?
Can the feature work without it?
What happens when denied?
```

---

# 18. User-Controlled System Access

The application shall not bypass Android controls for:

```text
Accessibility activation
IME enablement
IME selection
Runtime permissions
Application sandboxing
```

---

# 19. No Stealth

The application shall not intentionally implement:

```text
Hidden launcher behavior
Silent installation
Silent permission activation
Silent keyboard replacement
Security-setting bypasses
Covert persistence
Credential collection
Unauthorized remote monitoring
```

---

# 20. Session Boundary

Content-bearing application collection shall require an explicitly active monitoring session.

```text
NO ACTIVE SESSION
      ↓
Ordinary persistent application-content logging OFF
```

Keyboard functionality remains available.

---

# 21. Event Provenance

Persistent events should retain enough metadata to identify their application origin:

```text
Source
Timestamp
Session
Package where available
Classification
Event type
```

This improves auditability.

---

# 22. Data Deletion

The application shall provide:

```text
Delete session
Delete selected application data where implemented
Delete all application data
```

Bulk destructive actions require confirmation.

---

# 23. Database Security Testing

Controlled synthetic sensitive values shall be entered into test fields.

Afterward, inspect the database.

Search for exact synthetic values.

Expected:

```text
0 matches
```

---

# 24. Logcat Security Testing

After controlled sensitive-field testing, search Logcat for synthetic credentials.

Expected:

```text
0 matches
```

---

# 25. Export Security Testing

Export the session containing controlled sensitive-field events.

Search exported data for the synthetic credential.

Expected:

```text
0 matches
```

Metadata indicating that a sensitive field was encountered is acceptable.

---

# 26. Crash/Failure Behavior

A component failure must not result in privacy filters being skipped.

Bad fallback:

```text
PrivacyFilter failed
      ↓
Store raw event instead
```

Required behavior:

```text
PrivacyFilter failed
      ↓
Do not persist content-bearing event
```

Fail closed for sensitive-data persistence.

---

# 27. Unknown Classification

If the system cannot confidently classify a potentially sensitive editor, it should favor suppression where appropriate.

Application completeness is secondary to credential protection.

---

# 28. Dependency Security

Before introducing a third-party dependency:

```text
Is it necessary?
Is it maintained?
What permissions/components does it introduce?
Does it perform networking?
Does it collect analytics?
Does it materially increase attack surface?
```

Record meaningful decisions in `decision.md`.

---

# 29. Exported Android Components

Android components shall not be exported unless required for their intended Android framework interaction.

Manifest configuration must be reviewed before release.

---

# 30. Release Build

Security tests must run against the actual release configuration.

R8/resource shrinking must not accidentally remove or alter required privacy functionality.

---

# 31. Threat Categories

The project shall consider at least:

```text
Accidental sensitive-data persistence
Sensitive data in logs
Sensitive data in exports
Unprotected exported components
Unnecessary permissions
Database exposure
Memory leaks retaining sensitive objects
Unintended network transmission
Dependency risk
Configuration mistakes
Privacy filter bypass
```

---

# 32. Security Regression Rule

Every confirmed security/privacy defect should receive a regression test when reasonably automatable.

---

# 33. Security Change Management

A change affecting:

```text
Input collection
Sensitive classification
Accessibility data
Permissions
Persistence
Export
Networking
Android component exposure
```

requires review of this document.

Record meaningful architectural/security decisions in `decision.md`.

---

# 34. Mandatory Security Invariants

**SEC-INV-01**

Known password content shall not be persistently stored.

**SEC-INV-02**

Known PIN content shall not be persistently stored.

**SEC-INV-03**

Sensitive content shall not intentionally appear in Logcat.

**SEC-INV-04**

Sensitive content shall not appear in normal exports.

**SEC-INV-05**

Application persistence shall not be required for keyboard functionality.

**SEC-INV-06**

Privacy-filter failure shall not fall back to raw persistence.

**SEC-INV-07**

Accessibility/IME activation remains user-controlled.

**SEC-INV-08**

Version 1 shall not silently transmit interaction events.

**SEC-INV-09**

No persistence component shall intentionally provide a bypass around the privacy pipeline.

**SEC-INV-10**

Persisted permitted interaction/event data shall be encrypted at rest.

**SEC-INV-11**

Encryption keys shall not be hardcoded or stored in plaintext alongside encrypted application data.

**SEC-INV-12**

Failure to initialize secure encrypted persistence shall not cause automatic plaintext persistence.

**SEC-INV-13**

Encryption failure shall not break ordinary keyboard input delivery.

---

# 35. Security Review Checklist

Before every release:

```text
[ ] SensitiveInputDetector tested

[ ] PrivacyFilter tested

[ ] Repository validation tested

[ ] Password test passed

[ ] PIN test passed

[ ] Database inspected

[ ] Logcat inspected

[ ] Export inspected

[ ] Permissions reviewed

[ ] Manifest reviewed

[ ] Exported components reviewed

[ ] Dependencies reviewed

[ ] Network behavior reviewed

[ ] Debug logging reviewed

[ ] Release build tested

[ ] Security regressions pass
```

---

# 36. Final Security Rule

When there is a conflict between:

```text
Collecting more application data

and

Protecting credential/sensitive content
```

the architecture shall favor protecting sensitive content.

Any deliberate change to this boundary requires explicit redesign and review rather than an incidental implementation change.

**END OF SECURITY & PRIVACY SPECIFICATION**