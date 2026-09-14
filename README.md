# Work Hours Tracker (Android / Kotlin)

A fully self-contained Android app — no backend, no API keys, no account sign-up.
Everything runs and stores data on-device.

## What it does
- Work week = **Wednesday → Tuesday**.
- Tap a day to set **clock in** and **clock out** (picker or spoken time) plus a comment.
- On **Home**, for **today** only: **Clock in now** / **Clock out now** set the time to the current local clock (minutes since midnight). Clock-out requires clock-in first; these taps never invent lunch.
- Optional **lunch start** and **lunch end**. If either is left blank, lunch did not occur and is not subtracted.
- Hours are calculated from clock times (minus lunch when both lunch fields are set) and rounded to hundredths. Hours cannot be typed.
- The current week's running total recalculates the instant any day is saved.
- A local notification reminds you once a day to log your hours (default 6:00 PM). Change the time or turn reminders off in **Settings**.
- Every **Wednesday at 2:00 AM**, the just-finished week is archived into a history
  log (skipped when the week total is 0.0) and the Home screen automatically starts showing the new week.
- A History screen lists archived weeks with hours (empty 0.0 weeks are hidden), expandable to per-day detail, shows your all-time total, and can **export CSV** (share sheet) of archived weeks + the current week’s daily rows.

## Opening the project
1. Install **Android Studio** (Iguana or newer recommended).
2. `File → Open` and select the `WorkHoursTracker` folder (this folder).
3. Let Gradle sync — it will download AndroidX/Jetpack Compose, Room, and
   WorkManager (all standard, no extra accounts or keys needed).
4. Run on an emulator or device with **API 26 (Android 8.0)** or higher.

The project doesn't include a custom app icon (`mipmap/ic_launcher`) — Android
Studio's "Image Asset" tool (right-click `res` → New → Image Asset) will
generate one in a few clicks, or you can just run it with the default icon.

## Project layout
```
app/src/main/java/com/example/workhourstracker/
├── MainActivity.kt              # Entry point, requests notification permission
├── WorkHoursApplication.kt      # Holds the repository singleton, arms alarms on launch
├── data/
│   ├── DailyEntry.kt            # Room entity: one row per day
│   ├── WeekLog.kt               # Room entity: one row per archived week
│   ├── WorkHoursDao.kt          # Queries
│   ├── WorkHoursDatabase.kt     # Room database singleton
│   ├── WorkHoursRepository.kt   # Week-boundary-aware data access
│   └── ReminderPreferences.kt   # Reminder time + enabled flag
├── util/WeekUtils.kt            # All Wed→Tue date math lives here
├── util/HoursCalc.kt            # Clock-in/out → hours to hundredths
├── util/CsvExporter.kt          # CSV build + FileProvider share intent
├── viewmodel/WorkHoursViewModel.kt
├── worker/
│   ├── ReminderScheduler.kt     # Arms the daily + weekly AlarmManager alarms
│   └── WeeklyResetWorker.kt     # Does the actual archive DB write
├── receiver/
│   ├── DailyReminderReceiver.kt # Shows the notification, re-arms tomorrow
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
  API or key required. The transcribed phrase is parsed for a spoken time and
  applied to the active clock field for the user to confirm/edit.
- **Data is never deleted.** Rather than wiping the previous week's rows at
  reset time, the Home screen always queries for whatever the *current*
  Wed–Tue window is. This means the weekly "reset" the user sees is really
  just the natural result of the date window moving forward — which makes it
  impossible for a bug in the reset job to accidentally lose unarchived hours.
  The Wednesday 2 AM job only adds a summary row to the history log; it never
  has to touch or delete the detailed daily rows.
- **Empty weeks:** archives with 0.0 total hours are not inserted. History also
  filters out any existing 0.0 `WeekLog` rows. If a week is re-archived, the
  stored total is refreshed (mitigates insert-IGNORE staleness).
- **CSV export** writes to app cache via `FileProvider` and opens the system
  share sheet. Columns: week_start, date, clock_in, clock_out, lunch_out,
  lunch_in, hours, comments. Includes all weeks that have daily rows (archived
  + current).
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
- Week start day is fixed to Wednesday; theme follows the system (no in-app
  theme picker).
