# Changelog

Shipped versions for Work Hours Tracker (`com.rmltd.workhourstracker`).
Format: features and fixes by release, newest first.

## [1.3.14] — versionCode 16

Phase B — Home clarity:

- **Week strip:** Home progress card labeled "This week"; overtime tint (`tertiaryContainer`) when hours exceed the weekly goal, with "Xh over" instead of remaining.
- **Hourly rate (optional):** Settings field (USD-style `$`, persisted like other prefs). Leave blank/0 to hide. Home week strip and History all-time card show **Est. $X.XX (not payroll)** = hours × rate when set.
- Pure `PayEstimate` helpers + JVM unit tests (overtime threshold, rough pay math, `$` formatting).

## [1.3.13] — versionCode 15

Phase A — daily reliability:

- **Forgot punch:** History day rows are tappable to edit clock times; **Add missed punch** (+ / button) opens a date picker into Entry. Home **Forgot to clock out…** closes an open shift at a user-chosen TimePicker time (`clockOutAt`), reusing overnight/single-flight save rules.
- **Break / lunch:** Unpaid by default and subtracted from worked hours while keeping one shift (no full clock-out required). Entry supports duration chips (15/30/45/60) or break start/end times; optional paid-break checkbox for duration-only. Room `breakDurationMinutes` / `breakPaid` (DB v4). Timed break pair wins over duration. Unit tests cover break subtraction.
- **End-of-day reminder:** Settings enable + cutoff time (default 8:00 PM). If still clocked in past cutoff, one notification with **Clock out** / **Extend 1h** actions. Re-armed on boot/update; respects notification permission UX from 1.3.10+.

## [1.3.12] — versionCode 14

- Home: clear **History** entry points (top-bar History icon + bottom History button) navigate to the existing History (`LogScreen`) on a separate screen.
- Settings: in-app **Font style** (Default / Sans Serif / Serif / Monospace) persisted via `ThemePreferences` and applied app-wide through `WorkHoursTheme` typography.
- Settings: **Color** section label is tappable — expands/collapses the same theme radio list (second entry point alongside the radios).

## [1.3.11] — versionCode 13

- Settings: color theme switcher (Purple / Blue / Red / Green / Orange); preference persisted like reminder/week-start/goal.
- Selected theme flows MainActivity → `WorkHoursTheme` so the UI recomposes on change; default Purple.
- ColorSchemes use design-locked hexes from theme-mockups/palettes.json (purple/blue/red/green/orange; default purple).

## [1.3.10] — versionCode 12

- Entry: load the navigated day via date-scoped Room read (`entryForDateOnce`), not only `currentWeekEntries`, so overnight **Edit yesterday** across a week boundary shows stored clocks/lunch/comments (avoids blank Save overwrite).
- Save / discard-and-save share the same ViewModel `clockFlightMutex` + `clockOpInProgress` and repository `clockMutex` as clock-in/out.
- `BootReceiver` also handles `MY_PACKAGE_REPLACED` so reminders re-arm after an app update (BOOT_COMPLETED unchanged).
- Settings: when notifications are denied, show clear guidance + Allow / Open notification settings CTAs (reminders are not silent-fail).
- JVM tests for `EntryFormSeed` (date-scoped seed / out-of-week detection).

**Device smoke:** overnight open punch → next calendar day after week-start rollover → Home **Edit yesterday** → Entry must show yesterday's clock-in (not blank) → Save must not wipe; deny POST_NOTIFICATIONS → Settings shows blocked guidance.

## [1.3.9] — versionCode 11

- Home: Material TimePicker rows for today's clock-in and clock-out, with **Save today's times** (keeps existing Clock in/out now).
- Manual save reuses Entry/Repository `saveEntry` (preserve lunch when present; overnight confirm + BlockedOvernightOpen dialogs).
- Pure `HomeManualTimes` helpers + JVM unit tests.

## [1.3.8] — versionCode 10

- Expand JVM unit coverage for pure logic edges: HoursCalc lunch boundaries / format helpers, WeekUtils `nextWeekStart2AM` / `epochMillis`, VoiceShiftParser synonyms and unlabeled sequences, CsvExporter midnight/noon/blank clocks, ClockDayState orphan-out and overnight+closed UI cells.
- No product behavior changes.

## [1.3.7] — versionCode 9

- Extract pure clock day-state helpers (`ClockDayState`: classify, `decideClockIn` / `decideClockOut`, `deriveHomeClockUi`) from the repository; Room/mutex upsert paths unchanged.
- JVM unit tests cover the Empty / Open / Closed / legacy / overnight decision matrix.

## [1.3.6] — versionCode 8

- Unit tests for `CsvExporter.buildCsv` (headers, rows, comment escaping) without `Context`.
- Locale.US clock formatting for stable CSV output; drop unused repository wrappers.
- Document Room-backed clock write paths as needing instrumented tests / a local SDK.

## [1.3.5] — versionCode 7

- Expand unit coverage for `HoursCalc`, `WeekUtils`, `VoiceShiftParser`, and Home clock UI derivation (including 24h equal wall-time and all week-start days).
- Harden overnight range checks; remove unused `nextWednesday2AM`.

## [1.3.4] — versionCode 6

- Refresh week boundary / Home “today” on Activity start/resume and `DATE_CHANGED` / timezone broadcasts.
- Settings deep-link for exact-alarm permission (API 31+); inexact fallback when denied.
- Freeze voice mode at launch; improve PM “out at 4” heuristic; clamp invalid nav `epochDay`.
- Add util unit tests (`HoursCalc` / `WeekUtils` / `VoiceShiftParser`).

## [1.3.3] — versionCode 5

- Unify Entry with Home overnight policy (`BLOCKED_OVERNIGHT` resolve dialog).
- Day-state button enablement; single-flight clock ops; overnight clock-out confirmation.
- Equal wall-time overnight finish → 24.00h; guard legacy hours-only days.
- Clarify History all-time total copy (sum of logged days, including this week).

## [1.3.2] — versionCode 4

- Guard Home clock-in/out by day state: clock-in only on Empty; never pair with leftover out or invent lunch.
- Clock-out closes Open today or finishes yesterday overnight; no-op when Closed (avoids wiping finished days / invented overnight hours).

## [1.3.1] — versionCode 3

- Fix speech crash: guard `RecognizerIntent` with `resolveActivity` / try-catch and manifest `<queries>`.
- Home clock-out finishes yesterday’s open overnight shift.
- Rebuild `week_logs` and sum daily hours after week-start preference changes (fixes double-count).
- Set CSV `ClipData` for reliable FileProvider shares.

## [1.3] — versionCode 2

- Configurable week-start day (Sun–Sat, default Wednesday) across Home, archive, alarms, and CSV.
- Entry: Speak whole shift (plus per-field mic); weekly goal progress ring (default 40h).
- Smarter daily reminder skips when today already has a clock-out.
- Package / applicationId renamed to `com.rmltd.workhourstracker`.

### Precursor (1.2, versionCode 1)

Settings (reminder time/on-off), one-tap Home clock in/out, History CSV export, hide empty week archives — shipped before the 1.3 line.
