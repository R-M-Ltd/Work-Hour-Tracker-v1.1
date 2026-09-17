# Work Hours Tracker (Android / Kotlin)

A fully self-contained Android app — no backend, no API keys, no account sign-up.
Everything runs and stores data on-device.

**Application id / package:** `com.rmltd.workhourstracker`  
**Version:** 1.3.5 (versionCode 7)

## What it does
- Work week start day is **configurable** in Settings (Sunday–Saturday). **Default remains Wednesday** (Wed → Tue).
- Tap a day to set **clock in** and **clock out** (picker or spoken time) plus a comment.
- On the **Entry** screen: **Speak whole shift** fills multiple fields from one utterance
  (e.g. “clocked in at 7:30, lunch 12 to 12:30, out at 4”). Per-field mic buttons remain.
  Voice mode is captured at launch so mid-flight UI taps cannot flip the result target.
  Bare afternoon “out at 4” after a PM clock-in prefers 4 PM (same-day) unless overnight
  is the only sensible reading (e.g. in 10 PM → out 4 AM).
- On **Home**, for **today** only: **Clock in now** / **Clock out now** set the time to the current local clock (minutes since midnight). Buttons enable/disable from day state (Empty / Open / Closed / overnight-pending). **Empty** → clock-in starts an open row (hours 0.0); clock-out fails unless **yesterday is open overnight** (then finishes yesterday, including equal wall times as a 24.00h shift). **Open** → clock-in disabled; clock-out closes today. **Closed** (or legacy hours-only) → clock-in disabled / toast to edit. **Overnight pending** → clock-in opens a dialog: finish overnight, edit yesterday, or discard the open punch and clock in today. Clock ops are single-flight (rapid taps ignored). Clock-in never pairs a new in with a leftover out, and never invents lunch.
- **Entry** save refuses another day while an open overnight exists (dialog: edit open day / discard & save). Overnight clock-out earlier than clock-in asks for confirmation before save.
- Optional **lunch start** and **lunch end**. If either is left blank, lunch did not occur and is not subtracted.
- Hours are calculated from clock times (minus lunch when both lunch fields are set) and rounded to hundredths. Hours cannot be typed.
- **Weekly goal** (Settings, default **40.00** hours): Home shows a progress ring + remaining hours. Local preference only.
- The current week's running total recalculates the instant any day is saved. Week window and Home “today” refresh on Activity **ON_START** / resume and on `DATE_CHANGED` / timezone / time change broadcasts (no process kill needed after midnight).
- A local notification reminds you once a day to log your hours (default 6:00 PM). Change the time or turn reminders off in **Settings**. The reminder is **skipped** if today already has a clock-out (intentional; open overnight on yesterday does not suppress today’s reminder).
- On Android 12+, if exact alarms are denied, Settings offers **Allow exact alarms** (opens the system exact-alarm permission screen). Reminders/week archive fall back to inexact timing until granted.
- At **2:00 AM on the configured week-start day**, the just-finished week is archived into a history
  log (skipped when the week total is 0.0) and the Home screen automatically starts showing the new week.
- A History screen lists archived weeks with hours (empty 0.0 weeks are hidden), expandable to per-day detail, shows your all-time total as the **sum of all logged days (including this week)**, and can **export CSV** (share sheet) of daily rows grouped by the **configured** week-start preference.

## Opening the project
1. Install **Android Studio** (Iguana or newer recommended).
2. `File → Open` and select the `WorkHoursTracker` folder (this folder).
3. Let Gradle sync — it will download AndroidX/Jetpack Compose, Room, and
   WorkManager (all standard, no extra accounts or keys needed).
4. Run on an emulator or device with **API 26 (Android 8.0)** or higher.

Launcher icon is `@drawable/ic_launcher` (vector).

## Unit tests
Pure Kotlin / JUnit tests under `app/src/test/java/.../` cover:
- `HoursCalc` — day/overnight, lunch, `equalOutMeansFullDay` → 24.00h, rounding
- `WeekUtils` — week-start for all seven start days, 2 AM next-week, epoch-day fallback
- `VoiceShiftParser` — labeled phrases, bare AM/PM heuristics, unlabeled times
- `WorkHoursRepository.deriveHomeClockUi` — Empty / Open / Closed / overnight-pending / legacy

**Still needs instrumented (or in-memory Room) tests:** `clockInNow` / `clockOutNow` /
`saveEntry` / discard paths (DAO + mutex + upsert). The UI derive helper is covered above;
the write transitions are not.

Run from Android Studio or `./gradlew test` when an SDK is configured.

## Project layout
```
app/src/main/java/com/rmltd/workhourstracker/
├── MainActivity.kt              # Entry point; week refresh on start/resume + date broadcasts
├── WorkHoursApplication.kt      # Holds the repository singleton, arms alarms on launch
├── data/
│   ├── DailyEntry.kt            # Room entity: one row per day
│   ├── WeekLog.kt               # Room entity: archived week summaries (History list)
│   ├── WorkHoursDao.kt          # Queries
│   ├── WorkHoursDatabase.kt     # Room database singleton
│   ├── WorkHoursRepository.kt   # Week-boundary-aware data access
│   └── ReminderPreferences.kt   # Reminder, week-start day, weekly goal
├── util/WeekUtils.kt            # Configurable week-start date math + safe epochDay
├── util/HoursCalc.kt            # Clock-in/out → hours to hundredths
├── util/VoiceShiftParser.kt     # Whole-shift + single-time speech parsing
├── util/CsvExporter.kt          # CSV build + FileProvider share intent
├── viewmodel/WorkHoursViewModel.kt
├── worker/
│   ├── ReminderScheduler.kt     # Arms the daily + weekly AlarmManager alarms
│   └── WeeklyResetWorker.kt     # Does the actual archive DB write
├── receiver/
│   ├── DailyReminderReceiver.kt # Shows the notification (if needed), re-arms tomorrow
│   ├── WeeklyResetReceiver.kt   # Hands off to WeeklyResetWorker, re-arms next week
│   └── BootReceiver.kt          # Re-arms both alarms after a device reboot
└── ui/
    ├── theme/Theme.kt
    ├── navigation/AppNavigation.kt
    └── screens/HomeScreen.kt, EntryScreen.kt, LogScreen.kt, SettingsScreen.kt
```

## Design decisions worth knowing about
- **Voice input** uses Android's built-in `RecognizerIntent` speech-to-text
  (the same system dialog Google Search/Assistant use) — no third-party speech
  API or key required. Whole-shift mode parses labeled phrases (clock in / lunch /
  out) and falls back to ordered times; per-field mic still applies a single time.
  Before launching, the app checks `resolveActivity` (and catches
  `ActivityNotFoundException`) and toasts if no recognizer is installed; the
  manifest declares `<queries>` for `RECOGNIZE_SPEECH` and marks the microphone
  as optional. The voice mode/target for a request is frozen at launch so rapid
  dual mic taps cannot apply the result to the wrong field.
- **Data is never deleted.** Rather than wiping the previous week's rows at
  reset time, the Home screen always queries for whatever the *current*
  week window is (from the configured start day). This means the weekly "reset"
  the user sees is really just the natural result of the date window moving
  forward — which makes it impossible for a bug in the reset job to accidentally
  lose unarchived hours. The week-start 2 AM job only adds a summary row to the
  history log; it never has to touch or delete the detailed daily rows.
- **Week-start preference:** daily rows stay keyed by epoch day. Changing the
  start day recomputes Home / export week windows, **rewrites** each row's
  `weekStartEpochDay`, **deletes and rebuilds** `week_logs` from daily hours
  under the new week definition (avoids overlapping old/new keys double-counting),
  and re-arms the weekly 2 AM alarm. All-time total is the sum of distinct daily
  `hoursWorked` (not a raw sum of possibly overlapping week logs).
- **Empty weeks:** archives with 0.0 total hours are not inserted. History also
  filters out any existing 0.0 `WeekLog` rows. If a week is re-archived, the
  stored total is refreshed (mitigates insert-IGNORE staleness).
- **CSV export** writes to app cache via `FileProvider` and opens the system
  share sheet (`Intent.clipData` + `FLAG_GRANT_READ_URI_PERMISSION` so more OEMs
  can read the URI). Columns: week_start, date, clock_in, clock_out, lunch_out,
  lunch_in, hours, comments. `week_start` is recomputed from the configured
  week-start day for every daily row.
- **Exact alarms** (`AlarmManager.setExactAndAllowWhileIdle`) are used instead
  of `WorkManager`'s periodic work for both the daily reminder and the weekly
  reset, since periodic `WorkManager` jobs don't guarantee firing at a precise
  clock time — only "around" an interval. Each alarm re-schedules its own next
  occurrence when it fires, and `BootReceiver` re-arms both after a restart.
  When exact alarms are denied (API 31+), scheduling falls back to inexact and
  Settings can deep-link to `ACTION_REQUEST_SCHEDULE_EXACT_ALARM`.
- **Minimum SDK 26** (Android 8.0) to support notification channels cleanly;
  this covers the vast majority of active Android devices.

## Known follow-ups (not implemented, by design)
- No cloud backup/sync — purely local storage, per the "no further
  integration" requirement. A sync layer could be added later behind the same
  `WorkHoursRepository` interface without touching the UI.
- Theme follows the system (no in-app theme picker).
- Daily reminder does not skip solely because yesterday has an open overnight
  punch (product choice; still notifies if today is open with no out).
