# UI/UX & KEYBOARD DESIGN SPECIFICATION

## Android Interaction & Input Monitoring Application

**Document:** `design.md`

**Version:** 1.0

**Platform:** Android (Target: Android 15, 16, 17)

**Design System:** Material Design 3 (Material You) + Google Gboard Visual Parity

**Status:** Mandatory / Authoritative UI Specification

---

# 1. Purpose

This document defines the exact visual design, layout geometry, color tokens, typography, and interactive mechanics for:

1. **The Custom Keyboard (`CustomKeyboardService`)**: Engineered to look, feel, and behave identically to Google's **Gboard** (Material You edition), including its key proportions, surface hierarchy, gesture mechanics, top utility bar, and tactile feedback, while strictly respecting the privacy and performance boundaries in `SECURITY.md` and `DEVELOPMENT_GUIDELINES.md`.


2. **The Host Application (`MainActivity`)**: A clean, responsive Material 3 analytic utility interface covering Onboarding, Setup Verification, Dashboard, Live Event Timeline, Statistics, Settings, and Controlled Test Screens.



---

# 2. Core UI & Performance Principles

Per `DEVELOPMENT_GUIDELINES.md` (§4, §11, §17, §32–34), all visual design implementations must obey these constraints:

1. **Zero Typing Lag:** Keyboard rendering, touch ripples, and key-press popups must never drop frames or block `InputConnection` delivery.


2. **Zero Heavy Assets:** All icons (Shift, Backspace, Enter, Search, Send, Globe, Toolbar icons) must use lightweight Android XML Vector Drawables (`<vector>`). No heavy PNGs, JPEGs, Lottie files, or custom font binaries.


3. **Dynamic Material You Theming:** Use native Android system dynamic color tokens (`android.R.color.system_*`) on Android 12+ with fallback Material 3 tonal palettes so the keyboard and app automatically match the user's wallpaper and system Light/Dark mode.


4. **Shoulder-Surfing & Clipboard Privacy (`SECURITY.md` §5, §16):** Gboard visual conveniences (floating key-press popups, suggestion strip previews) must automatically suppress sensitive content when `EditorInfoAnalyzer` detects a password, PIN, or credential field.



---

# 3. Gboard-Identical Custom Keyboard Specification

The `CustomKeyboardService` UI shall replicate the exact layout, proportions, and tactile behavior of **Google Gboard (Material 3 Bordered Key Theme)**.

## 3.1 Overall Keyboard Dimensions & Structure

```text
┌──────────────────────────────────────────────────────────┐
│ [🌐]  [🔢]  [📋]  │  Suggestion / Action Strip    │ [🎤] [⌨️] │  ← Top Bar (40dp)
├──────────────────────────────────────────────────────────┤
│ ┌───┐ ┌───┐ ┌───┐ ┌───┐ ┌───┐ ┌───┐ ┌───┐ ┌───┐ ┌───┐ ┌───┐ │
│ │1 q│ │2 w│ │3 e│ │4 r│ │5 t│ │6 y│ │7 u│ │8 i│ │9 o│ │0 p│ │  ← Row 1 (52dp)
│ └───┘ └───┘ └───┘ └───┘ └───┘ └───┘ └───┘ └───┘ └───┘ └───┘ │
│   ┌───┐ ┌───┐ ┌───┐ ┌───┐ ┌───┐ ┌───┐ ┌───┐ ┌───┐ ┌───┐    │
│   │ a │ │ s │ │ d │ │ f │ │ g │ │ h │ │ j │ │ k │ │ l │    │  ← Row 2 (52dp, 5% indent)
│   └───┘ └───┘ └───┘ └───┘ └───┘ └───┘ └───┘ └───┘ └───┘    │
│ ┌─────┐ ┌───┐ ┌───┐ ┌───┐ ┌───┐ ┌───┐ ┌───┐ ┌───┐ ┌─────┐ │
│ │  ⇧  │ │ z │ │ x │ │ c │ │ v │ │ b │ │ n │ │ m │ │  ⌫  │ │  ← Row 3 (52dp)
│ └─────┘ └───┘ └───┘ └───┘ └───┘ └───┘ └───┘ └───┘ └─────┘ │
│ ┌──────┐ ┌───┐ ┌───┐ ┌─────────────────┐ ┌───┐ ┌────────┐ │
│ │ ?123 │ │ , │ │ 😊│ │     English     │ │ . │ │   ↵    │ │  ← Row 4 (54dp)
│ └──────┘ └───┘ └───┘ └─────────────────┘ └───┘ └────────┘ │
├──────────────────────────────────────────────────────────┤
│ [ ⌄ ]                                             [ ⌨️ ] │  ← System Nav Bar Spacer
└──────────────────────────────────────────────────────────┘

```

### Exact Key & Row Measurements

| Metric | Portrait Value | Landscape Value | Notes |
| --- | --- | --- | --- |
| **Top Suggestion/Utility Strip Height** | `40dp` | `36dp` | Houses quick tools + live word/status strip |
| **Dedicated Number Row (Optional Toggle)** | `44dp` | `38dp` | Configurable in Settings (like Gboard's "Number row" toggle) |
| **Standard Key Row Height (Rows 1–3)** | `52dp` | `42dp` | Includes vertical touch-target padding |
| **Bottom Row Height (Row 4)** | `54dp` | `44dp` | Spacebar + action row |
| **Total Keyboard Height (4 rows + strip)** | `250dp` | `206dp` | Excludes system navigation bar inset |
| **Keycap Vertical Margin** | `3dp` top / `3dp` bottom (`6dp` gap) | `2dp` top / `2dp` bottom | Touch target extends into the margin gap |
| **Keycap Horizontal Margin** | `2.5dp` left / `2.5dp` right (`5dp` gap) | `3dp` left / `3dp` right | Prevents accidental adjacent key presses |
| **Standard Keycap Corner Radius** | `8dp` | `6dp` | Rounded rectangle matching Gboard M3 |
| **Enter / Action Key Corner Radius** | `24dp` (Full Pill / Circle) | `20dp` | Iconic Gboard rounded pill accent button |
| **Keycap Bottom Shadow / Elevation** | `1dp` subtle bottom border | `1dp` | Drawn via lightweight canvas/drawable, not expensive blur shadows |

### Row-by-Row Horizontal Width Proportions

* **Row 1 (10 keys — `q` to `p`):** Each key occupies **`10%`** of total keyboard width. Top-right corner of each key displays a subtle secondary number hint (`1` through `0`) at `10sp` font size (`onSurfaceVariant` at 70% alpha).
* **Row 2 (9 keys — `a` to `l`):** Centered with **`5%` empty horizontal padding** on both the left and right edges. Each key occupies **`10%`** of total keyboard width.
* **Row 3 (9 keys — `⇧`, `z` to `m`, `⌫`):**
* `⇧` (Shift): **`15%`** width (`1.5x` standard key width), left-aligned.
* `z x c v b n m`: **`10%`** width each (`70%` total).
* `⌫` (Backspace): **`15%`** width (`1.5x` standard key width), right-aligned.


* **Row 4 (Bottom Bar):**
* `?123` (Symbol/Number Switcher): **`15%`** width.
* `,` (Comma / Secondary Action): **`10%`** width.
* `🌐` or `😊` (Language / Emoji Switcher): **`10%`** width (triggers Android IME switcher or symbol pane).
* `SPACE` (Spacebar): **`40%`** width (or **`50%`** if secondary switcher key is hidden). Displays subtle centered label `"English"` (or `"Sensitive Mode"` when in a password/PIN field).
* `.` (Period + Quick Punctuation Popup): **`10%`** width.
* `↵` (Enter / IME Action Key): **`15%`** width, rendered as a high-emphasis pill button.



---

## 3.2 Gboard Color Palette (Material You Dynamic & Default Tokens)

The keyboard must support both **Dynamic Color (Material You wallpaper extraction)** and **Default Gboard Material 3 Light/Dark themes**, plus a **"Key Borders"** toggle in Settings (default: **ON**).

| Element | Light Theme Token (Default Hex) | Dark Theme Token (Default Hex) | Notes |
| --- | --- | --- | --- |
| **Keyboard Tray Background** | `surfaceContainerHigh` (`#ECE6F0`) | `surfaceContainerLowest` (`#141218`) | Base background behind all rows |
| **Alpha/Number Keycap Surface** | `surfaceBright` (`#FFFFFF`) | `surfaceContainerHigh` (`#2B2930`) | Crisp contrast against tray |
| **Alpha/Number Keycap Pressed** | `surfaceContainerHighest` (`#E6E0E9`) | `surfaceBright` (`#3B383E`) | Instant visual press state |
| **Keycap Primary Text (`a–z`)** | `onSurface` (`#1D1B20`) | `onSurface` (`#E6E0E9`) | `20sp` lowercase, `22sp` uppercase |
| **Keycap Secondary Hint (`1–0`, symbols)** | `onSurfaceVariant` (`#49454F`) | `onSurfaceVariant` (`#CAC4D0`) | `10sp` top-right corner of keycap |
| **Modifier Keys (`⇧`, `⌫`, `?123`, `,`, `.`)** | `secondaryContainer` (`#E8DEF8`) | `surfaceContainer` (`#211F26`) | Distinct tonal surface like Gboard |
| **Shift Active / Caps Lock Key** | `primary` (`#6750A4`) | `primaryContainer` (`#4F378B`) | Icon switches to `onPrimary` / `onPrimaryContainer` |
| **Enter / IME Action Key Surface** | `primary` (`#6750A4` / System Accent) | `primaryContainer` (`#D0BCFF` / Accent) | Pill-shaped focal button |
| **Enter / IME Action Icon** | `onPrimary` (`#FFFFFF`) | `onPrimaryContainer` (`#381E72`) | High-contrast vector icon |
| **Floating Key Preview Bubble** | `surfaceBright` (`#FFFFFF`) | `surfaceContainerHighest` (`#36343B`) | Elevated pill above finger (`4dp` elevation) |
| **Top Strip Divider** | `outlineVariant` (`#CAC4D0` at 30%) | `outlineVariant` (`#49454F` at 30%) | Optional `1dp` separator |

---

## 3.3 Gboard Functional & Interactive Features

To provide the authentic Gboard user experience while complying with `SRS.md`, `Architecture.md`, and `SECURITY.md`, the keyboard shall implement the following features:

### 1. Top Utility & Suggestion Strip (`40dp` Bar)

* **Left Quick-Expand / App Status Icon:** Displays the Gboard-style chevron/grid icon and a subtle indicator dot when a Monitoring Session is active.
* **Center 3-Slot Strip:**
* In normal text fields: Displays current composing word preview and quick punctuation/completion candidates (or quick-insert chips when empty).
* In sensitive fields (`PASSWORD`, `PIN`, `CREDENTIAL`): Displays a clean, centered privacy badge: **`🔒 Sensitive input — logging paused`** so the user has 100% transparency that credentials are never recorded.




* **Right Utility Icons:**
* **One-Tap IME Switcher (`🌐` / Keyboard icon):** Invokes `InputMethodManager.showInputMethodPicker()` (`FR-KB-013`).


* **Hide Keyboard (`⌄`):** Calls `requestHideSelf(0)`.



### 2. Floating Key-Press Magnifier Popup

* Pressing any character key (`q–p`, `a–l`, `z–m`, numbers, symbols) immediately renders a Gboard-style floating pill popup `48dp × 64dp` directly above the pressed keycap showing the enlarged character (`28sp`).
* **Mandatory Security Suppression:** When `EditorContext.isSensitive == true`, floating key-press popups are **strictly disabled** (just like Gboard in password fields) to prevent shoulder-surfing.



### 3. Long-Press Secondary Characters & Numbers

* **Row 1 (`q` to `p`):** Long-pressing (`250ms` threshold) triggers a popup displaying the corresponding number (`1` through `0`) and commits it on release.
* **Period Key (`.`):** Long-pressing `.` opens Gboard's quick punctuation popup grid (`? ! @ # $ % & - + ( ) / * " ' : ;`).
* **Enter Key (`↵`):** Adapts its icon and behavior dynamically based on `EditorInfo.imeOptions`:
* `IME_ACTION_SEARCH` → Magnifying glass icon (`🔍`)
* `IME_ACTION_SEND` → Paper plane icon (`➤`)
* `IME_ACTION_GO` / `IME_ACTION_NEXT` → Right arrow icon (`→`)
* `IME_ACTION_DONE` → Checkmark icon (`✓`)
* Multiline / Normal → Return arrow icon (`↵`)



### 4. Gboard Gesture Mechanics

* **Spacebar Trackpad (Slide-to-Move Cursor):** Swiping horizontally left or right across the `SPACE` bar moves the text cursor character-by-character via `InputConnection.setSelection()` with subtle `CLOCK_TICK` haptic feedback—one of Gboard's most-used everyday features.
* **Double-Space Period Shortcut:** Tapping `SPACE` twice within `300ms` after a word automatically replaces the first space with `". "` (configurable in Settings, disabled in Email/URL/Password fields).
* **Backspace Repeat & Slide-to-Delete:**
* Holding `⌫` for `>400ms` repeats deletion every `50ms`, accelerating to whole-word deletion after `1.5s`.
* Swiping left starting from the `⌫` key highlights and deletes words proportionally to swipe distance.


* **Shift & Caps Lock State Machine:**
* Single tap on `⇧` → `SHIFT_ONE_SHOT` (upward outline arrow; reverts to lowercase after 1 character).
* Double tap on `⇧` (within `300ms`) or long-press → `CAPS_LOCK` (filled arrow with underline bar below arrow).
* Auto-capitalization after `. `, `! `, `? `, or at the start of a text field when `EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES` is present.



### 5. Complete Gboard Layout Modes (`Phase 5`)

1. **QWERTY Layout (`ALPHABETIC`):** Standard 4-row staggered layout (plus optional 5th dedicated number row `1–0`).
2. **Numbers & Punctuation Layout (`?123` — Page 1/2):**
* Row 1: `1 2 3 4 5 6 7 8 9 0`
* Row 2: `@ # $ _ & - + ( ) /`
* Row 3: `=\<` (15%), `* " ' : ; ! ?`, `⌫` (15%)
* Row 4: `ABC` (15%), `,`, `SPACE`, `.`, `↵` (15%)


3. **Extended Symbols Layout (`=\<` — Page 2/2):**
* Row 1: `~ ` ` | • √ π ÷ × § ∆`
* Row 2: `£ ¢ € ¥ ^ ° = { } \`
* Row 3: `?123` (15%), `% © ® ™ ✓ [ ]`, `⌫` (15%)
* Row 4: `ABC` (15%), `<`, `SPACE`, `>`, `↵` (15%)


4. **Dedicated Number Pad / PIN Layout (`NUMBER` / `PHONE` / `NUMERIC_PASSWORD`):**
* Gboard's 4×4 large-key numeric pad (`1 2 3` / `4 5 6` / `7 8 9` / `* 0 #` + right-side action column `-`, `,`, `⌫`, `↵`).


5. **Email & URL Variants:**
* Email (`TYPE_TEXT_VARIATION_EMAIL_ADDRESS`): Replaces the left `,` key with `@` for single-tap access.
* URL (`TYPE_TEXT_VARIATION_URI`): Replaces `,` with `/` and adds `.com` on long-press of `.`.



---

# 4. Host Application UI Design (`MainActivity`)



The main application uses **Jetpack Compose Material 3** with a clean, technical, developer-grade aesthetic.

## 4.1 Color & Typography System (App UI)

* **Theme Support:** System Light/Dark mode + Dynamic Color (`dynamicDarkColorScheme` / `dynamicLightColorScheme`).
* **Semantic Status Colors:**
* **Active / Verified / Safe (`NORMAL`):** Emerald Green (`#2E7D32` Light / `#81C784` Dark)
* **Paused / Warning (`PAUSED` / Partial Setup):** Amber (`#EF6C00` Light / `#FFB74D` Dark)
* **Stopped / Disabled / Error:** Crimson (`#C62828` Light / `#E57373` Dark)
* **Sensitive Suppressed (`SENSITIVE_*`):** Deep Purple / Indigo Badge (`#4527A0` Light / `#B39DDB` Dark)


* **Typography:**
* Screen Titles: `MaterialTheme.typography.headlineSmall` (SemiBold)
* Section Headers: `MaterialTheme.typography.titleMedium` (Medium)
* Package Names / Timestamps / Event Types: **`FontFamily.Monospace`** (`bodySmall` / `labelMedium`) for scannable log readability.



---

## 4.2 Navigation Structure

Uses a clean **Single-Activity Navigation Host** (`ui/navigation/`):

* **Onboarding Flow (Shown when setup is incomplete):**
`WelcomeScreen` → `SetupChecklistScreen` (with inline verification cards)
* **Main Workspace (Bottom Navigation Bar with 4 top-level destinations):**
1. **Dashboard (`DashboardScreen`)** — Live session control, system health badges, quick counters.


2. **Timeline (`EventTimelineScreen`)** — Paginated live event log, search bar, filter chips.


3. **Statistics (`StatisticsScreen`)** — Aggregated metrics, per-app breakdown, sensitive suppression count.


4. **Settings & Test (`SettingsScreen`)** — Event category toggles, Gboard preferences (Number Row, Key Borders, Haptics), Data Export/Deletion, and entry to the **Controlled Input Test Screen**.





---

## 4.3 Screen-by-Screen Wireframes & Specifications

### Screen 1: Welcome & Disclosure (`ui/onboarding/`)



* **Layout:** Centered vertical column with app shield/keyboard vector emblem, clear disclosure summary (`SRS.md` §11), and a highlighted **"Privacy Boundary Guarantee"** card explaining that passwords, PINs, and OTPs are stripped before storage.


* **Primary CTA:** Full-width pill button `[ Continue to Setup ]` pinned at bottom.

### Screen 2: Interactive Setup Checklist (`ui/setup/`)



* **Layout:** Vertical list of 4 numbered Material 3 `OutlinedCard` steps that automatically turn green (`✓ READY`) as `SetupStateManager` refreshes on `ON_RESUME`:
1. **Runtime Permissions** (`[ Grant ]` button)
2. **Interaction Accessibility Service** (`[ Open Accessibility Settings ]` + `[ Skip for Now ]`)
3. **Enable Gboard-Style Custom Keyboard** (`[ Open Keyboard Settings ]`)
4. **Select Custom Keyboard as Active IME** (`[ Choose Input Method ]` — triggers native picker dialog)


* **Bottom CTA:** `[ Go to Dashboard ]` (enabled even in partial setup per `SRS.md` §17, with clear status banners).



### Screen 3: Dashboard (`ui/dashboard/`)



```text
┌──────────────────────────────────────────────────────────┐
│  Interaction Monitor                        [🧪] [⚙️]    │
├──────────────────────────────────────────────────────────┤
│  ┌────────────────────────────────────────────────────┐  │
│  │ ● SESSION ACTIVE                     00:24:17      │  │
│  │ "Android 17 Test Session"            1,429 events  │  │
│  │                                                    │  │
│  │  [ ⏸ Pause ]                     [ ⏹ Stop Session ] │  │
│  └────────────────────────────────────────────────────┘  │
│                                                          │
│  SYSTEM HEALTH                                           │
│  ┌──────────────────────┐  ┌──────────────────────────┐  │
│  │ ⌨️ Custom Keyboard   │  │ ♿ Accessibility Service │  │
│  │ ● Selected & Active  │  │ ● Enabled                │  │
│  └──────────────────────┘  └──────────────────────────┘  │
│                                                          │
│  CURRENT CONTEXT                                         │
│  ┌────────────────────────────────────────────────────┐  │
│  │ 📱 Application Notes                               │  │
│  │    com.example.notes                               │  │
│  └────────────────────────────────────────────────────┘  │
│                                                          │
│  SESSION METRICS (2x3 Grid)                              │
│  ┌────────────┐  ┌────────────┐  ┌────────────────────┐  │
│  │   1,284    │  │    642     │  │        213         │  │
│  │   Total    │  │  Keyboard  │  │       Clicks       │  │
│  └────────────┘  └────────────┘  └────────────────────┘  │
│  ┌────────────┐  ┌────────────┐  ┌────────────────────┐  │
│  │    184     │  │    156     │  │        12          │  │
│  │   Focus    │  │   Scrolls  │  │ 🔒 Sensitive Suppr │  │
│  └────────────┘  └────────────┘  └────────────────────┘  │
├──────────────────────────────────────────────────────────┤
│  [ 📊 Dashboard ]  [ 📜 Timeline ]  [ 📈 Stats ]  [ ⚙️ ] │
└──────────────────────────────────────────────────────────┘

```

### Screen 4: Event Timeline & Search (`ui/events/`)



* **Top Sticky Header:**
* Search bar (`Search package, app, event type, or text...`) backed by Room indexed queries.
* Horizontal scrollable `FilterChip` row: `[Session ▾]`, ``, `[Type ▾]`, `[Classification: All | Normal | Sensitive]`.


* **Event List (`LazyColumn` + Paging 3):**
* Each event row is a compact, low-allocation card (`64dp` height) with:
* **Left:** Monospace timestamp (`18:42:10.124`) + Source badge (`IME` in Blue, `A11Y` in Teal).
* **Center:** `EventType` chip (`IME_TEXT_COMMIT`, `VIEW_CLICKED`, `APP_TRANSITION`) + Application label & `packageName` in monospace.
* **Right / Bottom:** Permitted `eventData` preview (e.g., `"hello"`) OR a high-contrast purple badge **`[🔒 REDACTED: PASSWORD]`** when `inputClassification` is sensitive (`eventData == null`).





### Screen 5: Statistics & Export (`ui/statistics/`)



* **Summary Cards:** Total Events, Events/Minute, IME vs. Accessibility ratio bar, Sensitive Suppression Counter.
* **Per-Application Activity List:** Ranked list showing App Icon/Initials, App Name, Package Name, Event Count, and a lightweight horizontal progress bar (drawn with standard Compose `LinearProgressIndicator` — no external charting libraries, per `DEVELOPMENT_GUIDELINES.md` §7).


* **Export Bar:** `[ Export Session to CSV ]` button that triggers Android's Storage Access Framework (`ActivityResultContracts.CreateDocument("text/csv")`) and streams via `ExportManager`.



### Screen 6: Controlled Input Test Screen (`ui/settings/` or `ui/test/`)



* Implements `SRS.md` (§66) and `IMPLEMENTATION_PLAN.md` (Phase 17):


* A single scrollable card containing 7 clearly labeled `OutlinedTextField` inputs with exact `EditorInfo` configurations:
1. **Normal Text** (`InputType.TYPE_CLASS_TEXT`)
2. **Email** (`InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS`)
3. **URL** (`InputType.TYPE_TEXT_VARIATION_URI`)
4. **Number** (`InputType.TYPE_CLASS_NUMBER`)
5. **Multiline** (`InputType.TYPE_TEXT_FLAG_MULTI_LINE`)
6. **Password** (`InputType.TYPE_TEXT_VARIATION_PASSWORD`)
7. **Numeric PIN** (`InputType.TYPE_NUMBER_VARIATION_PASSWORD`)


* Includes a live **"Last classification detected"** banner at the top of the screen so the tester can immediately verify that focusing the Password/PIN fields switches the keyboard into `SENSITIVE` mode and suppresses key popups and content persistence.



---

# 5. Keyboard UI Rendering Architecture (Performance Mandate)

To satisfy `DEVELOPMENT_GUIDELINES.md` (§4 Low-End Device Requirement, §17 Keyboard Performance Is Critical, §32 UI Performance):

1. **Isolated Key State:** Touching a single key on the Gboard layout must **only** redraw that specific keycap and its floating popup preview—it must never trigger a full-screen or full-keyboard layout pass.
2. **Pre-allocated Key Rectangles:** Key coordinates and hit-boxes (`RectF`) for the 4 rows are computed once during `onSizeChanged()` / layout configuration changes, ensuring **zero object allocations (`0 B`)** during `ACTION_DOWN` and `ACTION_MOVE` touch events.
3. **Hardware Haptics:** Use `View.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)` directly on the touch-down event before dispatching to `KeyProcessor` so tactile response is instantaneous (`< 2ms`).



---

# 6. UI/UX Acceptance Checklist

Before marking Phases 4, 5, 10–17 complete, verify:

```text
[ ] Custom Keyboard matches Gboard 4-row Material 3 geometry (10-9-9-bottom staggered grid).
[ ] Row 2 (a–l) has 5% horizontal indentation on left and right.
[ ] Character keys use 8dp rounded corners; Enter/Action key uses a full pill shape with primary accent color.
[ ] Material You Dynamic Color and Light/Dark modes apply automatically to both Keyboard and App UI.
[ ] Optional Dedicated Number Row and Key Borders toggles work in Settings.
[ ] Spacebar horizontal slide moves the cursor smoothly.
[ ] Backspace hold-to-repeat and slide-to-delete function smoothly.
[ ] Long-press on top row (q–p) inputs numbers (1–0); long-press on (.) opens quick punctuation.
[ ] Floating key-press preview popup appears on normal fields and is STRICTLY DISABLED on sensitive fields.
[ ] Top bar displays a clear "Sensitive input — logging paused" indicator when in a password/PIN field.
[ ] One-tap IME Switcher icon (🌐) opens Android's native keyboard selector.
[ ] Timeline screen uses Paging and Monospace typography without frame drops on 100,000+ event databases.

```

---

**END OF UI/UX & KEYBOARD DESIGN SPECIFICATION**

**Document:** `design.md`

**Version:** `1.0`