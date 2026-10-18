# ClearTrail

A visible Android usage monitor and private encrypted dashboard. **Foundation build: manual collection/sync; no custom keyboard or Accessibility service.**

Tracks explicitly selected WhatsApp, Instagram and Chrome foreground intervals, estimated duration and battery status during sessions you start. It does not collect typed text, messages, notifications, websites, clipboard, contacts, location, camera or microphone data.

Original keyboard-project specifications are preserved under `docs/archive/original-keyboard-spec/`. The root documentation describes the approved replacement.

## Run the dashboard

Requires Node.js 24+. No npm dependencies to install.

```powershell
npm.cmd start
```

Open <http://127.0.0.1:4310>. **Explore sample data** runs an explicitly labelled synthetic demo in the browser without uploading anything.

The **Keyboard practice** panel below the timeline shows a US reference layout and an identical live layout. Click or Tab into the live practice area to highlight physical keys and view ordered presses. Tab leaves the area. Only the focused practice area listens; pairing inputs and other applications are excluded. History keeps the latest 100 non-repeat keydowns in memory, with a clear button, and clears on page navigation/reload. Focus loss clears held highlights. Browser/system shortcuts can interrupt delivery; unsupported keys appear in history without a matching keycap. This browser exercise does not change Android collection.

To share the displayed practice history, enter one recipient email address and select **Send history · open email draft**. This opens a `mailto` draft containing only the displayed practice entries, in order. Review and send from your configured email app. The dashboard has no email service and cannot confirm delivery; it does not automatically send or save the history or recipient. Mail handlers and long-draft limits vary by browser/email app. Other dashboard data and text entered in the recipient field are excluded from practice capture and the draft body.

The live keycaps show lowercase letters by default, uppercase with Shift or Caps Lock, and lowercase with both. Number/punctuation keys show both US symbols with the active symbol first. Caps Lock has a ring indicator; modifiers/arrows have readable labels in history. Focus loss resets the display; the next focused key event resynchronizes modifier state. Physical keycaps use a US layout; history follows the character reported by your keyboard/browser, which may differ for other layouts, AltGr and IMEs. The existing compact layout does not show separate function-key or number-pad banks; their events can still appear in history.

The server creates `.local/operator-token.txt`. Read that file locally and enter its value in **Connect your phone** to authorize pairing. Do not commit or share it. Pairing produces:

- **Recovery file:** contains dashboard read/delete authority and the decryption key. Save it privately. This is currently the only recovery mechanism.
- **Phone setup bundle:** contains upload authority and the same data key. Transfer directly to your own phone; do not post it in chat or email.

The dashboard keeps secrets in memory, not localStorage. Refreshing/locking requires importing the recovery file again. Browser import is local; the recovery file/key are not uploaded. The server stores encrypted records and credential hashes.

## Build Android

JDK 17, Android platform 36, build-tools 35.0.0. Pinned Gradle wrapper is included with distribution checksum. Local tools downloaded during development are ignored under `.tools/`.

```powershell
.\scripts\build-android.ps1
```

Or open `android/` in Android Studio and configure the SDK/JDK. Debug APK output: `android/app/build/outputs/apk/debug/app-debug.apk`. Release output is unsigned unless you configure your own signing; no release key is included.

## Connect a development phone

For a USB-connected development phone with developer debugging explicitly enabled, or an emulator:

```powershell
adb install android/app/build/outputs/apk/debug/app-debug.apk
adb reverse tcp:4310 tcp:4310
```

Use the localhost phone bundle created by this dashboard. `adb reverse` makes the phone's `127.0.0.1:4310` reach the development server. Debug builds permit HTTP only for localhost/127.0.0.1/10.0.2.2. Release builds require HTTPS. No LAN plaintext exception is included.

On the phone:

1. Open ClearTrail and review the disclosure.
2. Grant Usage Access in Android settings.
3. Paste and save the phone setup bundle.
4. Select the applications to observe and start a session.
5. Use those apps, return to ClearTrail, and capture or sync.
6. Refresh the dashboard to see decrypted activity.

Pause flushes the active interval locally; resume starts from now. Stop saves the final interval. If an interval cannot be saved, it is not silently discarded; use the explicit **Stop and discard** recovery control if necessary. Uncaptured activity is conservatively discarded after process death. Capture at least once every 24 hours. A five-minute lookback seeds foreground state but never emits activity before the session boundary; longer already-open intervals can be undercounted.

The first slice does not continuously run in the background or auto-start after reboot. It queries active-session history when you explicitly capture/pause/stop/sync. Scheduled monitoring is a later phase.

## Remote deployment

The server binds `127.0.0.1`. A real remote deployment needs an HTTPS reverse proxy, a stable hostname, persistent private storage, and a securely configured operator token. Set:

```text
PORT=4310
CLEARTRAIL_PUBLIC_ORIGIN=https://your-dashboard.example
CLEARTRAIL_DATA_DIR=/private/durable/cleartrail
CLEARTRAIL_ADMIN_TOKEN=<random secret of at least 32 characters>
```

The proxy must preserve the configured Host header. Keep API and dashboard on the same origin. Register a new pairing at the final HTTPS origin; a localhost recovery file is intentionally restricted to localhost. Deployment is not performed by this foundation build. Production accounts, key rotation, monitoring, operational recovery and security review remain work items.

## Data control and limits

- Local outbox: up to 1000 encrypted records; full queue raises an error.
- Server: up to 5000 records/device, 10 devices, 30-day retention.
- Delete remote history leaves pairing active; unsent phone records can upload later.
- Revoke phone deletes server records and invalidates both device credentials.
- Clear local data/unpair removes phone outbox and wrapped pairing; it does not delete server history.
- CSV export is decrypted usage metadata. Store exported files privately.
- Logical deletion does not promise forensic erasure from disks or backups.

## Verification

```powershell
npm.cmd run check
npm.cmd test
```

Tests cover protocol authentication/tampering, strict metadata schemas, API roles, idempotency, nonce reuse, pagination, restart persistence, retention, revoke/delete, origin/host checks and limits. The interoperability test uses JDK 17 when available. Pure Kotlin interval tests run through Gradle.

See `PROGRESS.md` for actual build/test results and outstanding device gates. A functioning demo and successful APK build do not establish physical-device correctness or production readiness.
