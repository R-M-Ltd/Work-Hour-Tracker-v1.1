# Changelog

Shipped versions for Work Hours Tracker (`com.rmltd.workhourstracker`).
Format: features and fixes by release, newest first.

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
