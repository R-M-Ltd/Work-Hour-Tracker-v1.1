# Work Hours Tracker (Android / Kotlin)

A fully self-contained Android app — no backend, no API keys, no account sign-up.
Everything runs and stores data on-device.

**Application id / package:** `com.rmltd.workhourstracker`  
**Version:** 1.3.2 (versionCode 4)

## What it does
- Work week start day is **configurable** in Settings (Sunday–Saturday). **Default remains Wednesday** (Wed → Tue).
- Tap a day to set **clock in** and **clock out** (picker or spoken time) plus a comment.
- On the **Entry** screen: **Speak whole shift** fills multiple fields from one utterance
  (e.g. “clocked in at 7:30, lunch 12 to 12:30, out at 4”). Per-field mic buttons remain.
- On **Home**, for **today** only: **Clock in now** / **Clock out now** set the time to the current local clock (minutes since midnight). Day state guards apply: **Empty** → clock-in starts an open row (hours 0.0); clock-out fails unless **yesterday is open overnight** (then finishes yesterday). **Open** → clock-in no-ops; clock-out closes today. **Closed** → neither button writes (edit the day instead). Clock-in never pairs a new in with a leftover out, and never invents lunch.
- Optional **lunch start** and **lunch end**. If either is left blank, lunch did not occur and is not subtracted.
- Hours are calculated from clock times (minus lunch when both lunch fields are set) and rounded to hundredths. Hours cannot be typed.
- **Weekly goal** (Settings, default **40.00** hours): Home shows a progress ring + remaining hours. Local preference only.
- The current week's running total recalculates the instant any day is saved.
- A local notification reminds you once a day to log your hours (default 6:00 PM). Change the time or turn reminders off in **Settings**. The reminder is **skipped** if today already has a clock-out.
- At **2:00 AM on the configured week-start day**, the just-finished week is archived into a history
  log (skipped when the week total is 0.0) and the Home screen automatically starts showing the new week.
- A History screen lists archived weeks with hours (empty 0.0 weeks are hidden), expandable to per-day detail, shows your all-time total, and can **export CSV** (share sheet) of daily rows grouped by the **configured** week-start preference.

## Opening the project
1. Install **Android Studio** (Iguana or newer recommended).
2. `File → Open` and select the `WorkHoursTracker` folder (this folder).
3. Let Gradle sync — it will download AndroidX/Jetpack Compose, Room, and
   WorkManager (all standard, no extra accounts or keys needed).
4. Run on an emulator or device with **API 26 (Android 8.0)** or higher.

Launcher uses a vector `@drawable/ic_launcher` (no mipmap adaptive icons yet).
Android Studio's "Image Asset" tool can generate adaptive mipmaps if desired.

## Project layout
```
app/src/main/java/com/rmltd/workhourstracker/
├── MainActivity.kt              # Entry point, requests notification permission
├── WorkHoursApplication.kt      # Holds the repository singleton, arms alarms on launch
├── data/
│   ├── DailyEntry.kt            # Room entity: one row per day
│   ├── WeekLog.kt               # Room entity: one row per archived week
│   ├── WorkHoursDao.kt          # Queries
│   ├── WorkHoursDatabase.kt     # Room database singleton
│   ├── WorkHoursRepository.kt   # Week-boundary-aware data access
│   └── ReminderPreferences.kt   # Reminder, week-start day, weekly goal
├── util/WeekUtils.kt            # Configurable week-start date math
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
  as optional.
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
- **Minimum SDK 26** (Android 8.0) to support notification channels cleanly;
  this covers the vast majority of active Android devices.

## Known follow-ups (not implemented, by design)
- No cloud backup/sync — purely local storage, per the "no further
  integration" requirement. A sync layer could be added later behind the same
  `WorkHoursRepository` interface without touching the UI.
- Theme follows the system (no in-app theme picker).
