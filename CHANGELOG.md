# Changelog

Shipped versions for Work Hours Tracker (`com.rmltd.workhourstracker`).
Format: features and fixes by release, newest first.

## 1.3.42 (versionCode 44)

Manual times + More simplify on tip `2494584` (1.3.41 / vc 43). Design pack `ui-rebuild-1.3.42`.

- **A Home:** Quiet secondary Start · Stop / since chips under Idle-partial, Running, and Paused (dashed caption — not peer to Clock-in or elapsed). Pure EMPTY idle omits chips. Tap opens shared `AddChangeHoursSheet` titled **Edit today**; mid-shift Start rewrite uses `updateOpenClockIn`; Stop-while-open uses Forgot-family `clockOutAt` (clears pause prefs).
- **A Widget:** Status shows `Start {in} · Stop —` when running, keeps `Paused · since {in}` + `pauseFreezeMinutes`, CLOSED shows `Start {in} · Stop {out}`. Whole-widget tap sets `EXTRA_OPEN_EDIT_TODAY` → Home Edit today sheet. No RemoteViews punch farm.
- **B More:** Groups **Day · Pay · Settings**. Merge Add/Change + Set today's times → **Edit today**. Drop History row (tab stays) and Settings root row. Keep lunch nav-only unpaid + Forgot. Keep 1.3.41 SettingsSection deep-links.

Protected: Lunch CTA nav-only, EOD Open-app-only / `end_of_day_reminder`, CSV/PDF schema, locked hexes / splash `#5B3F9E` / Arc, D1–D4, cloud-off, onboarding 1.3.37, typed hours / equal in·out / S3 / S4, Nav A / Idle B / Running A / Paused / More-as-sheet, pause ≠ close OPEN, shade clears pause prefs, 1.3.41 deep-links + widget freeze.

## 1.3.41 (versionCode 43)

Lows + conflict sweep on tip `7f1f7e5` (1.3.40 / vc 42). Design pack `ui-rebuild-1.3.41`.

- **L1:** Remove dead `DayRow` composable and unused `LazyColumn`/`items` imports from Home (week list stays gone; browse via History).
- **L2:** More → Settings section deep-links — Export / Rates & goals / Appearance / Backup & cloud / Reminders & shade scroll to matching Settings cards; Settings row opens root.
- **L3:** Widget respects session pause freeze (`pauseFreezeMinutes` via `SessionPausePreferences` + `SessionPause.effectiveNowMinutes`) so Today/week elapsed matches Home while paused; pause still does **not** clock out.
- **E4:** Widget status shows `Paused · since {in}` when OPEN and paused.
- **E1:** Shade Clock out clears `SessionPausePreferences` on SUCCESS / SUCCESS_OVERNIGHT / ALREADY_CLOSED (no linger until Home).
- **E2/E3:** Keep TopAppBar ⋮ More alias; Home HH:MM:SS face vs HoursCalc decimal dual display intentional.

Protected: Lunch CTA nav-only, EOD Open-app-only / `end_of_day_reminder`, CSV/PDF schema, locked hexes / splash `#5B3F9E` / Arc, D1–D4, cloud-off, onboarding 1.3.37, typed hours / equal in·out, Nav A / Idle B / Running A / More groups, pause does not close OPEN.

## 1.3.40 (versionCode 42)

Timer-first Home IA (baseline `67628dc` / 1.3.39 vc 41). Design pack `ui-rebuild-1.3.40`; locked Nav A · Idle B · Running A · More groups.

- **TF-NAV:** 3-tab bottom bar Home | History | More; History reuses LogScreen as tab root; More opens grouped sheet; TopAppBar ⋮ thin alias to More.
- **TF-IDLE:** Wide pill Clock in; quiet “Not clocked in” + date; no peer CTA farm beside primary.
- **TF-RUN:** Large tabular elapsed; Pause | Stop peer secondary only; lunch caption text only.
- **TF-PAUSE:** Net-new session pause prefs — freezes elapsed, does **not** write clockOut; Resume continues same OPEN punch; Stop closes.
- **TF-MORE:** Grouped ModalBottomSheet Today / Log & pay / App (typed Add/Change, lunch nav-only, export, rates & goals, appearance, backup/cloud, reminders & shade, Settings).
- **TF-GOALS:** Goals card removed from Home; Settings Weekly goal / Rates & goals via More.
- **TF-TYPED:** Add/Change demoted to More → Today; sheet behavior (typed, S3/S4, equal in·out) unchanged.
- Home week LazyColumn decluttered (browse via History).

Protected: Lunch CTA nav-only, EOD Open-app-only / `end_of_day_reminder`, CSV/PDF schema, locked hexes / splash `#5B3F9E` / Arc, D1–D4, cloud-off, onboarding 1.3.37, typed hours / equal in·out, 1.3.38–1.3.39 polish (Appearance, widget, SessionElapsed math, backup stamp, pay-period, shade).

## 1.3.39 (versionCode 41)

Leftover cleanup on tip `dd0be56` (1.3.38 / vc 40):

- **L1:** Remove orphan `EndOfDayActionReceiver` + manifest entry (`ACTION_EOD_CLOCK_OUT` / `ACTION_EOD_EXTEND`). EOD stays Open-app-only on `end_of_day_reminder`; shade Clock out remains on `clock_session`.
- **S1:** Comments-only History/Log note save preserves dirty Home IN/OUT stash (narrow D4); full clear when clocks clean or on day-authoritative writes.
- **S3:** Skip “Reason for 0 hours?” when note already has a `0 hours:` line; midnight 12:00 AM path unchanged.
- **S4:** Add/Change sheet suspends while TimePicker is open (one modal) on Home, Entry, and Settings Today.
- **S5:** Cloud card clarity — linked + never synced shows “Not synced yet — tap Sync now”; default off unchanged; last-synced ≠ last-backed-up.
- **L2:** Document 1.3.38 polish + this cleanup.
- **S2:** Arc Clock / splash `#5B3F9E` verified untouched.
- **Backup (optional):** Stamp “Last backed up” only on share-chooser `RESULT_OK` (best-effort).

Protected: Lunch CTA, EOD completeness / Open-app-only, CSV/PDF, locked hexes, D1–D3, cloud-off, onboarding 1.3.37, typed Add/Change, Home declutter, 1.3.38 polish.

## 1.3.38 (versionCode 40)

Polish batch at tip `dd0be56`:

- **Appearance:** System / Light / Dark above Color in Settings; widget chrome follows Appearance then palette.
- **Arc Clock live preview** in Color expand; chip on `#5B3F9E`.
- **Widget:** Today line for open session + ~60s AlarmManager refresh; no punch buttons.
- **Goals:** Home secondary ring under Today; shared `SessionElapsed` with widget / Today.
- **Backup:** “Last backed up” stamp (separate from cloud Last synced).
- **Pay-period CSV:** Settings + History additive menus; exporters schema unchanged.
- **EOD:** Skip notify when today complete; Open app only; channel `end_of_day_reminder`.
- **Shade:** `clock_session` toggle default off; Clock out when on + clocked in.

## 1.3.34 (versionCode 36)

- **Home declutter:** week title + total + single ⋮; one stateful Clock in/out; Change/Add under today’s total; History/Settings/Forgot/Lunch/Set times via ⋮; bottom History removed.
- **Typed hours + Add/Change sheet:** decimal hours (incl. 0); typed wins on Save; `hoursSource` CLOCK|TYPED; Reason for 0 hours? for typed 0; equal in/out alternate path kept.
- **No lunch taken:** checkbox on Entry + sheet; skips break subtract; History muted “No lunch” chip; CSV/PDF unchanged (no new column).
- **Arc Clock re-image:** launcher/splash/mono with subtle timesheet bar ticks; splash still `#5B3F9E`.
- **Cloud sync:** Drive + Dropbox + OneDrive picker (off by default); EncryptedSharedPreferences tokens; LWW sync engine; iCloud footnote; Sign in needs `local.properties` client IDs.
- **History:** light padding/rhythm polish only; export/search/notes/filters/Add missed day unmoved.
- **Settings → Today:** Set today’s times + Log lunch/break shortcuts.

## [1.3.32] — versionCode 34

Arthur QA D4 from 1.3.31 + Ivan UI pack `ui-rebuild-1.3.32` (baseline `755f3e9`):

- **D4 comments-only clears Home draft:** After successful `updateEntryComments` upsert, call `onDayFullySaved(date)` so `HomeDraftPreferences.clearIfEpochDay` runs (same Application hook as `upsertClosedEntry`). Missing row still returns false with no clear. Keeps `UNSAVED_DRAFT_CAPTION` and Apply-only `Restored unsaved times` latch from 1.3.31. Acceptance: Home dirty stash → Log/History comments-only save → return Home → no false restore toast, no clobber.
- Protected: Lunch CTA nav-only, EOD 8pm / Extend 1h, CSV/PDF, locked hexes, cloud sync off / no OAuth, Arc Clock FG/splash/`#5B3F9E`, opaque mono (D3), D1–D3 / D6 / D7. No new chrome.
- Version 1.3.32 / versionCode 34.

## [1.3.31] — versionCode 33

Arthur QA D1/D2/D3 from 1.3.30 + Ivan UI pack `ui-rebuild-1.3.31` (baseline `0d7d4bc`):

- **D1 Room-aware Home draft:** `decideRestore` Apply + toast `Restored unsaved times` only when locals still equal Room and stash is dirty vs Room; otherwise clear stash silently (`ClearStaleDraft`). Clear `HomeDraftPreferences` on any successful closed-day Room write (`upsertClosedEntry` → saveEntry / discard-save / clock-close / drain). Acceptance: Home dirty → Entry Save → return Home must not toast or overwrite Entry times. Caption unchanged.
- **D2 comment trim:** `decideRestore` comment equality uses `trim()` like `isDirty`; whitespace-only drift no longer Applies.
- **D3 monochrome opaque silhouette:** Drop soft `#29000000` face; opaque `#FF000000` arc/ticks/hand/hub only. FG/splash/`#5B3F9E` untouched.
- Protected: Lunch CTA nav-only, EOD 8pm / Extend 1h, CSV/PDF, locked hexes, cloud sync off / no OAuth. No Punch Ring.
- Version 1.3.31 / versionCode 33.

## [1.3.30] — versionCode 32

Arthur QA Suspected from 1.3.29 + Ivan UI pack `ui-rebuild-1.3.30` (baseline `c1a7d25`):

- **S-A Arc Clock polish:** Drop-in sharper mipmap FG/BG, splash `ic_splash_icon` 432, and monochrome VectorDrawable (arc + ticks + hand) from `icon-polish/`. Splash `#5B3F9E` / adaptive wiring unchanged.
- **S-B durable Home draft:** Persist dirty IN/OUT minutes + comments to SharedPreferences keyed by `todayEpochDay`; restore on Home enter; clear on successful Save / day rollover; keep `Not saved yet — tap Save today's times`; one-shot toast `Restored unsaved times`.
- **S-C orphan same-OUT:** No production UI (`uiNeeded: false`) — silent `drainLeftoverOpens` stays.
- Protected: Lunch CTA nav-only, EOD 8pm / Extend 1h, CSV/PDF, locked hexes + Aqua, cloud sync off / no OAuth. No Punch Ring.
- Version 1.3.30 / versionCode 32.

## [1.3.29] — versionCode 31

Arthur QA residuals from 1.3.28 + Ivan UI pack `ui-rebuild-1.3.29` (baseline `2212e94`):

- **D6 empty-day OUT survives START_OPEN:** Re-key `homeOutMinutes` / `homeInMinutes` / `homeCommentsDraft` on stable `today.toEpochDay()` (not `todayEntry?.dateEpochDay`) so null→epoch on first open-punch IN does not wipe local OUT. No new chrome.
- **D7 silent drain after SUCCESS:** Share `drainLeftoverOpens` after any successful close (`SUCCESS` and `SUCCESS_OVERNIGHT`); skip today; no new toast / Home UI.
- **S1 unsaved caption:** Keep `Not saved yet — tap Save today's times` (no SavedInstanceState this release).
- **S2 Arc Clock:** No new assets — device-verify splash `#5B3F9E` + monochrome only.
- **S3 orphan OUT:** Drained orphans get same wall-clock OUT; no orphan-naming toast.
- Protected: Lunch CTA nav-only, EOD 8pm / Extend 1h, CSV/PDF, locked hexes + Aqua, cloud sync off / no OAuth.
- Version 1.3.29 / versionCode 31.

## [1.3.28] — versionCode 30

QA residuals from Arthur 1.3.27 + Ivan UI pack `ui-rebuild-1.3.28` (baseline `7b1bf5c`):

- **D1 homeCommentsDraft on open-punch IN:** Pass draft (via `HomeOpenPunch.punchCommentsForOpenIn`) into `clockInAt` / `updateOpenClockIn`; show draft on Home after IN (no flash-empty).
- **D2 ZeroTimeNote merge:** Full-line equality dedupe (`containsFullLine`) — not substring `contains`. Zero-dialog copy unchanged.
- **D3 updateOpenClockIn BUSY:** `UpdateOpenClockInResult` {UPDATED, FAILED, BUSY}; toast `Please wait…` on flight reject (same string as other clock ops).
- **D4 findOpenEntryOnce KDoc:** “Oldest open punch”.
- **D5 overnight / Forgot:** Finish overnight, Discard, Forgot confirm `enabled = !clockBusy` (Cancel / Edit stay enabled).
- **S-A local OUT:** Remember keys no longer cross-key IN/OUT — OUT survives open-punch IN.
- **S-B unsaved caption:** When draft dirty / pending Save, `Not saved yet — tap Save today's times` (`labelSmall` / `onSurfaceVariant`).
- **S-C multi-open:** Overnight finish drains leftover corrupt opens (kept from WIP; pack out-of-scope but no contradiction).
- **S-D Arc Clock:** No new assets — device-verify only; splash `#5B3F9E` + Arc Clock unchanged.
- Protected: Lunch CTA nav-only, EOD/CSV/PDF, locked hexes + Aqua, cloud sync off / no OAuth.
- Version 1.3.28 / versionCode 30.

## [1.3.27] — versionCode 29

Fixes from the 1.3.26 analysis (High/Medium/Low); no drive-by features:

- **H1 Home TimePicker IN:** Gate overnight/orphan like Clock-in-now (`HomeOpenPunch.SHOW_OVERNIGHT`); mutate `homeInMinutes` only on `STARTED` / successful update; toast on non-success.
- **H2 clockFlight:** Invoke failure callback on flight reject (`ClockInResult.BUSY` / `ClockOutResult.BUSY` / `onDone(false)`); disable Home time pickers while `clockOpInProgress`.
- **M3 ZeroTimeNote merge:** Dedupe only on full formatted line (never bare reason substring).
- **M4/M5 LOCAL_ONLY + OUT midnight:** Stash reason in Compose `homeCommentsDraft` until Save (no immediate `updateEntryComments`).
- **M6 needsZeroHoursReason:** Wired in Entry `trySave` and Home manual save with same dialog pattern as midnight.
- **M7 Atomic punch + comments:** `clockInNow` / `updateOpenClockIn` accept optional comments in the same mutex upsert.
- **L8 Entry zero-dialog isError:** Match Home (`zeroReasonText.isNotEmpty() && !canConfirm`).
- **L9 Splash/icon hygiene:** Removed unused `drawable/ic_launcher`, `ic_splash_icon_fg`, unused `ic_launcher_background` color; added Arc Clock monochrome adaptive layer. Splash purple `#5B3F9E` + Arc Clock wiring unchanged.
- **S1 findOpenEntry:** Prefer oldest open (`ORDER BY dateEpochDay ASC`) if multi-open is ever reachable.
- **S2/S3/S4:** Deferred — 0h History filter is product-intentional for empty archives; widget already updates all instance IDs; splash visual polish device-only.
- Protected: Lunch CTA nav-only, EOD/CSV/PDF, locked hexes + Aqua, cloud sync off / no OAuth.
- Version 1.3.27 / versionCode 29.

## [1.3.26] — versionCode 28

Two CoS smoke fixes on 1.3.25 plus Ivan UI pack `ui-rebuild-1.3.26` + Arc Clock icon pack (Benjamin pick):

- **Clock in now persistence:** Home TimePicker IN alone now persists an open punch via `clockInAt` / `updateOpenClockIn` (same Room shape as Clock in now). Eager `homeInMinutes` sync on `STARTED`. Repo regression with in-memory DAO.
- **Zero time → reason note:** Confirming **12:00 AM** (`minutes == 0`) on Home Clock in/out and Entry Clock in/out / Break start/end opens pack AlertDialog (`Reason for 12:00 AM?`). **Save reason** applies 0 + appends `12:00 AM ({field}): {reason}` into `DailyEntry.comments`. **Cancel** reverts (never leaves 12:00 AM). Empty reason blocks confirm.
- **Clocked-in chips:** Home Today card `Clocked in · {time}` when open; Entry `Open shift · clocked in {time}` when in set / out null.
- **Arc Clock launcher + splash:** Adaptive `@mipmap/ic_launcher` (+ round); SplashScreen API purple `#5B3F9E` + foreground mark; Manifest activity uses Splash theme; `installSplashScreen()` in MainActivity.
- Protected: Lunch CTA nav-only, EOD/CSV/locked hexes (incl. Aqua), cloud sync off-by-default / no OAuth. Punch Ring assets not used.
- Version 1.3.26 / versionCode 28.

## [1.3.25] — versionCode 27

Five deltas from Ivan UI pack `ui-rebuild-1.3.25` (baseline 1.3.24 / `2b8f1da`):

- **Widget follows in-app theme:** Reverses 1.3.24 always-purple. RemoteViews chrome (accent bar, fill, title/status/week text) maps from active `AppTheme` + system light/dark. View IDs and `WidgetContent` status strings unchanged. Settings Color helper: “Home-screen widget uses the same color palette (and light/dark) as the app.” Theme change refreshes the widget.
- **Haptics:** Light confirm haptic on successful clock in, clock out, overnight clock out, and Forgot confirm only. No new Home controls; Material ripple remains the visual press affordance.
- **Accessibility:** Punch controls prefer ~56 dp height (min 48). TalkBack `contentDescription`s per pack table (Clock in/out, Forgot, lunch CTA, Save today's times, day Add/Edit, Entry Break start/end / Save entry, Settings Export CSV/PDF / Backup / Restore / Cloud sync, History Export / Add missed day, overnight dialog button labels).
- **PDF timesheet export:** Settings Export / share — **Export PDF** beside CSV with helper “Share a printable timesheet for the selected range”; range UX mirrors CSV (This week / Date range… / All time). Real shareable `PdfDocument` (title, date range, day rows, totals) via FileProvider + `ACTION_SEND`. CSV/backup unchanged.
- **Optional cloud sync UI:** Settings section after Backup — switch **off by default**, vendor-neutral. When on: “Not linked” + “Sign in to sync” / “Manage”. Prefs persist enable + linked. No OAuth / Drive / Firebase wiring (`oauthPresentInApp: false`). Sign in/Manage opens a clear dialog that sync is prepared for a future provider — **no automatic cloud upload** until a provider is linked in a later release. Do not sync silently.
- Locked hexes (all six), Home lunch CTA (nav only), clock states, EOD 8pm/Extend 1h, paid-break Entry, widget IDs/status strings, 1.3.21–1.3.24 fixes kept.
- Version 1.3.25 / versionCode 27.

## [1.3.24] — versionCode 26

Appearance + layout polish:

- **Home leftover gap:** Replaced Column + weighted LazyColumn + trailing spacer with a **single LazyColumn** (week strip, Today card, day rows, History). Removes the empty weight filler / bottom spacer gap so content sits flush above the screen bottom.
- **Vertical scroll:** Home and History/Log now scroll the full page via LazyColumn. Entry and Settings already used `verticalScroll` (unchanged).
- **Font styles:** Added **Cursive** (`FontFamily.Cursive`). Platform Compose generics are only Default / Sans Serif / Serif / Monospace / Cursive — no invented fonts. Settings radios + ThemePreferences + BackupCodec `fromKey` fall back to Default for unknown keys. Unit tests cover the new key.
- **Color Aqua:** New `AppTheme.AQUA` (key `aqua`). Not in locked theme-mockups pack — sensible teal-aqua set:
  - Light primary `#00838F`, primaryContainer `#B2EBF2`, secondary `#4A6366` / `#CCE8EB`, tertiary `#51606F` / `#D4E4F6`, surface `#F4FAFB`
  - Dark primary `#4DD0E1`, primaryContainer `#006064`, secondary `#B0CCCF` / `#334B4E`, tertiary `#B8C8D8` / `#394857`, surface `#0E1415`
  - Settings Color radios/chips via `AppTheme.entries` + `previewPrimary()` / `colorSchemeFor()`. Widget stays default purple.
- **Button labels:** Home / Entry / History / Settings buttons and ClockTimeRow / HomeClockTimeRow use `maxLines` + `softWrap` so labels are not clipped.
- Locked Purple/Blue/Red/Green/Orange hexes unchanged. Ivan Home CTA / 1.3.21 perf / 1.3.23 Entry scroll / Phase A–D / EOD / paid-break / widget IDs kept.
- Version 1.3.24 / versionCode 26.

## [1.3.23] — versionCode 25

Layout fix for Entry Break buttons clipped at the bottom:

- **Entry scroll:** Entry screen Column now uses `verticalScroll` (same pattern as Settings) so Shift / Break / Hours / Note / Save content can scroll on short screens. Break End (and sibling Break start / Save) are fully visible.
- **Break labels:** `ClockTimeRow` button text allows 2 lines so "Break end (optional)" and siblings stay fully labeled.
- No punch/lunch behavior, theme, widget ID, or Home CTA changes. Home still navigates only via Log lunch / break….
- Version 1.3.23 / versionCode 25.

## [1.3.22] — versionCode 24

Ivan full-app UI rebuild from `ui-rebuild-1.3.22` design pack (baseline 1.3.21 / `d821a93`; visual language polish `a94ee7d`):

- **Home lunch CTA (product delta):** Today card gets **Log lunch / break…** (`FilledTonalButton`, contentDescription `Open Entry to log lunch or break`) after Clock in/out / Forgot and before the manual-times divider. Navigates to `entry/{today}` only — **does not** punch lunch/break; Start/End lunch stay on Entry. Helper: “Breaks stay on Entry — unpaid by default.”
- **Surfaces:** Home / Entry / History / Settings / widget chrome already match pack + `a94ee7d` section cards, weekly hero, day-row cards, Settings sections, and widget IDs/chrome — no ViewModel API invent from mockups; theme hexes locked.
- **Kept from 1.3.21:** `windowBackground` surface tint, single `onAppResume`, IO reminder scheduling.
- Version 1.3.22 / versionCode 24.

## [1.3.21] — versionCode 23

Perf / white-flash patch only:

- **White flash:** `Theme.WorkHoursTracker` `android:windowBackground` tinted to Compose PurpleLight surface `#F7F2FA` (`launch_background`) so the pre-Compose frame matches the app surface.
- **Single onAppResume path:** `MainActivity` keeps Lifecycle `ON_START` + date/TZ broadcast refresh; drops the duplicate `onResume()` `onAppResume()` call.
- **Application.onCreate:** Moves the three `ReminderScheduler.schedule*` calls onto `appScope` (IO) with archive catch-up; widget `requestUpdate` unchanged (already async).
- Optional widget-skip-on-resume deferred (not clearly low-risk).
- Version 1.3.21 / versionCode 23.

## [1.3.20] — versionCode 22

Findings polish after 1.3.19 review:

- **Overnight dialog Finish toast (L):** Routes `SUCCESS_OVERNIGHT` through `HomeOvernightCopy.clockOutOvernightToast` (same as Clock-out-now) so yesterday vs orphan/open overnight wording is correct.
- **Dead clock-in toast (L):** Removed unreachable `BLOCKED_OVERNIGHT → "Finish yesterday's shift first"` arm (outer branch already opens the overnight dialog).
- **ViewModel KDoc (L):** `clockOutNow` / `discardOvernightAndClockIn` now say any other-day open (yesterday or orphan), matching 1.3.18+ behavior.
- **EOD Clock out (M):** `ACTION_CLOCK_OUT` cancels `cancelEndOfDaySameDayRetry` (symmetry with `ACTION_EXTEND`) so a fail-closed retry cannot race after clock-out handling.
- **Boot widget (M):** `BootReceiver` no longer refreshes before archive; `WeeklyResetWorker` refreshes after `catchUpWeekArchives` (same pattern as `WeeklyResetReceiver`) so week-boundary boots do not briefly show pre-archive week totals.
- Version 1.3.20 / versionCode 22.

## [1.3.19] — versionCode 21

Findings polish after 1.3.18 review:

- **Home overnight copy (M):** Clock-out toast and "Clock out now" helper use the actual open date / neutral "open overnight" wording when the open punch is older than yesterday (dialog path already branched). Pure `HomeOvernightCopy` + JVM tests.
- **EOD Extend (M):** `ACTION_EXTEND` cancels `cancelEndOfDaySameDayRetry` alongside clear-fired/snooze so a fail-closed retry cannot race the snooze notify.
- **WidgetContent KDoc (L):** `overnightPending` documents any other-day open (not only yesterday).
- **isStillClockedIn (L):** Dropped unused `today` param; behavior unchanged (any open punch).
- **Weekly reset widget (L):** `WeeklyResetReceiver` no longer refreshes before archive; `WeeklyResetWorker` refreshes after `catchUpWeekArchives` (BootReceiver early refresh kept until 1.3.20).
- **Settings Color helper (L):** Expanded Color copy matches palette radios/rows ("Choose a palette…"); collapsed copy unchanged.
- Version 1.3.19 / versionCode 21.

## [1.3.18] — versionCode 20

Findings polish after 1.3.17 review:

- **Widget gate (M-A):** `onUpdate` / `updateAppWidgetIds` / `updateAllSync` share the same mutex + generation as `requestUpdate`; `isCurrent` is checked **after** Room load and **before** `updateAppWidget`. JVM tests cover coalesce/post-load semantics (not counter-only).
- **Orphan open (M-B):** Clock-in blocks (`BLOCKED_OVERNIGHT`) when `findOpenEntry` is on another day (gap days, not only yesterday). `isStillClockedIn`, widget overnight copy, Home overnight UI, EOD, discard, and clock-out finish any open punch. JVM: open + gap day + later clock-in.
- **EOD same-day retry (M-C):** Fail-closed catch schedules a short same-day one-shot retry **without** marking fired; notify stays fail-closed. Catch comments corrected.
- **CSV breakPaid (L1):** Emit blank `breakPaid` when `breakDurationMinutes` is empty (no false noise).
- **Docs (L2):** CHANGELOG clarifies `formatClock` Locale.US is app-wide (honest; no widget/UI split).
- **Widget refresh (L3):** `BootReceiver` and weekly archive/reset paths call `WorkHoursWidgetUpdater.requestUpdate`.
- **EOD action (L4):** `EndOfDayActionReceiver` handles `ClockOutResult` failures (toast + re-notify; no silent success).
- **Contradictions:** `WidgetUpdateGeneration` KDoc matches post-load gate; 1.3.17 M1 note corrected; EOD catch comment fixed.
- Version 1.3.18 / versionCode 20.

## [1.3.17] — versionCode 19

Findings polish after 1.3.16 widget review:

- **Widget race (M1):** `WorkHoursWidgetUpdater.requestUpdate` is single-flight + generation-gated (provider `onUpdate` still bypassed the gate until 1.3.18).
- **Overnight widget copy (M2/C3):** Status is "Overnight open — open app to finish" (accurate; no fake tap-to-resolve).
- **EOD catch (M3):** Fail closed — do not show "Still clocked in" when open/clocked-in state is unknown.
- **Settings CSV date range (C1):** Settings Export CSV matches History (this week / date range / all time).
- **Color helper (C4):** Collapsed Color section no longer says "pick a radio below".
- **Theme vs widget (C2):** Settings note — home-screen widget stays default purple and does not follow in-app theme/font.
- **CSV break columns (L1):** Export includes `breakDurationMinutes` and `breakPaid`.
- **Locales (L2):** `HoursCalc.formatClock` uses Locale.US app-wide (Home/Entry/Settings/widget/CSV) — not widget-only.
- **Tests (L3/M4):** Widget overnight + non-EMPTY cases, update-generation sequencing, Entry date-scoped overnight regression.
- **README (L4):** Version 1.3.17; brief Phases A–D feature mention.

## [1.3.16] — versionCode 18

Phase D — Home screen widget (small safe first version):

- **Widget:** Shows today's clock status (Not clocked in / Clocked in since / Completed / Overnight open) and week hours so far vs goal.
- **Tap:** Opens the app (Home / MainActivity). No clock-in/out actions from the widget (overnight resolve dialogs and ViewModel single-flight stay in-app).
- **Refresh:** After clock in/out/save, app resume, prefs change, EOD notification clock-out, app start; plus AppWidgetProvider ~30 min `updatePeriodMillis`.
- Material-ish colors from default purple palette (`primary` / `primaryContainer`) via RemoteViews layout XML.
- Pure `WidgetContent` builder + JVM unit tests.

## [1.3.15] — versionCode 17

Phase C — get data out / keep it safe:

- **Export / share:** History share menu and Settings **Export CSV** — this week, date range, or all time via the system share sheet (FileProvider + ClipData). Reuses `CsvExporter`; range filter is unit-tested.
- **Per-entry notes:** Entry field labeled **Note** (same `comments` column). History shows notes, **Add/Edit note** dialog without leaving History, and a simple **Search notes** filter.
- **Backup & restore:** Settings exports/imports one JSON file (`BackupCodec`) with all daily entries, week summaries, and essential prefs (week start, goal, theme, font, rate, reminders). Confirm before overwrite; validate on import. Round-trip unit tests.

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
