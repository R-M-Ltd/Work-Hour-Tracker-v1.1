package com.rmltd.workhourstracker.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.rmltd.workhourstracker.data.ClockInResult
import com.rmltd.workhourstracker.data.ClockOutResult
import com.rmltd.workhourstracker.data.HomeDraftPreferences
import com.rmltd.workhourstracker.data.SessionPausePreferences
import com.rmltd.workhourstracker.data.UpdateOpenClockInResult
import com.rmltd.workhourstracker.data.SaveEntryResult
import com.rmltd.workhourstracker.ui.components.AddChangeHoursDraft
import com.rmltd.workhourstracker.ui.components.AddChangeHoursResult
import com.rmltd.workhourstracker.ui.components.AddChangeHoursSheet
import com.rmltd.workhourstracker.ui.components.formatHoursField
import com.rmltd.workhourstracker.data.HoursSource
import com.rmltd.workhourstracker.util.ClockHaptics
import kotlinx.coroutines.delay
import com.rmltd.workhourstracker.util.HomeDraftRestoreDecision
import com.rmltd.workhourstracker.util.HomeDraftSnapshot
import com.rmltd.workhourstracker.util.HomeDraftStash
import com.rmltd.workhourstracker.util.HomeManualTimes
import com.rmltd.workhourstracker.util.HomeOpenPunch
import com.rmltd.workhourstracker.util.ZeroTimeNote
import com.rmltd.workhourstracker.util.HomeOvernightCopy
import com.rmltd.workhourstracker.util.PayEstimate
import com.rmltd.workhourstracker.util.HoursCalc
import com.rmltd.workhourstracker.util.SessionElapsed
import com.rmltd.workhourstracker.util.SessionPause
import com.rmltd.workhourstracker.viewmodel.WorkHoursViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Actions demoted into More → Today (1.3.40); consumed once on Home. */
enum class HomeMoreAction {
    ADD_CHANGE_HOURS,
    SET_TODAYS_TIMES,
    FORGOT_CLOCK_OUT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: WorkHoursViewModel,
    onDayClick: (LocalDate) -> Unit,
    onViewLog: () -> Unit,
    onSettings: () -> Unit,
    onLogLunch: () -> Unit,
    onOpenMore: () -> Unit = {},
    pendingMoreAction: HomeMoreAction? = null,
    onPendingMoreActionConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    // Nav callbacks still wired from AppNavHost; More sheet owns History/Settings/Lunch.
    @Suppress("UNUSED_VARIABLE", "UNUSED_PARAMETER")
    val _navKeep = Triple(onViewLog, onSettings, onLogLunch)
    val view = LocalView.current
    val entries by viewModel.currentWeekEntries.collectAsState()
    val weekStart by viewModel.weekStart.collectAsState()
    val weeklyGoal by viewModel.weeklyGoalHours.collectAsState()
    val hourlyRate by viewModel.hourlyRate.collectAsState()
    val homeClock by viewModel.homeClockUi.collectAsState()
    val clockBusy by viewModel.clockOpInProgress.collectAsState()
    val total = viewModel.runningTotal(entries)
    val today = LocalDate.now()
    // weeklyGoal / hourlyRate / weekStart still collected for future chrome; week list decluttered.
    @Suppress("UNUSED_VARIABLE")
    val _goalRateKeep = weeklyGoal to hourlyRate to weekStart

    var showOvernightDialog by remember { mutableStateOf(false) }
    var showManualOvernightConfirm by remember { mutableStateOf(false) }
    var showManualBlockedOvernight by remember { mutableStateOf(false) }
    var manualOpenOvernightDate by remember { mutableStateOf<LocalDate?>(null) }
    var homePickerField by remember { mutableStateOf<HomeClockField?>(null) }
    var showForgotClockOut by remember { mutableStateOf(false) }
    var pendingZeroField by remember { mutableStateOf<HomeClockField?>(null) }
    var pendingZeroMinutes by remember { mutableStateOf<Int?>(null) }
    var zeroReasonText by remember { mutableStateOf("") }
    var showZeroHoursDialog by remember { mutableStateOf(false) }
    var zeroHoursReasonText by remember { mutableStateOf("") }
    var showAddChangeSheet by remember { mutableStateOf(false) }
    var pauseRevision by remember { mutableStateOf(0) }
    var addChangeTitle by remember { mutableStateOf("Add hours") }
    var pendingTypedHours by remember { mutableStateOf<Double?>(null) }
    var pendingNoLunch by remember { mutableStateOf(false) }
    var sheetNoLunch by remember { mutableStateOf(false) }
    // S4: suspend Add/Change sheet while TimePicker is alone; restore after OK/Cancel
    var sheetSuspendedForPicker by remember { mutableStateOf(false) }
    var sheetDraftHoursText by remember { mutableStateOf("") }
    var sheetDraftNote by remember { mutableStateOf("") }
    // Live tick for open-punch elapsed total (1s when session open for HH:MM:SS face)
    var nowEpochMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    val todayEntryForTick = viewModel.entryFor(LocalDate.now(), entries)
    val sessionOpenForTick = todayEntryForTick?.clockInMinutes != null &&
        todayEntryForTick?.clockOutMinutes == null
    LaunchedEffect(sessionOpenForTick) {
        while (true) {
            delay(if (sessionOpenForTick) 1_000L else 30_000L)
            nowEpochMillis = System.currentTimeMillis()
        }
    }

    val todayEntry = viewModel.entryFor(today, entries)
    // D6: Key local Home state on stable today.toEpochDay() (not todayEntry?.dateEpochDay)
    // so empty-day OUT/IN/draft survive START_OPEN when Room creates today's row
    // (null→epoch wipe). Secondary keys stay on Room IN / OUT / comments.
    // Do not reintroduce clockInMinutes as an OUT key.
    var homeInMinutes by remember(
        today.toEpochDay(),
        todayEntry?.clockInMinutes
    ) {
        mutableStateOf(todayEntry?.clockInMinutes)
    }
    var homeOutMinutes by remember(
        today.toEpochDay(),
        todayEntry?.clockOutMinutes
    ) {
        mutableStateOf(todayEntry?.clockOutMinutes)
    }
    // Stash note locally until Save / atomic punch write (LOCAL_ONLY + OUT midnight).
    var homeCommentsDraft by remember(
        today.toEpochDay(),
        todayEntry?.comments
    ) {
        mutableStateOf(todayEntry?.comments.orEmpty())
    }

    // S-B: durable draft — restore once on Home enter; persist while dirty.
    var draftRestoreDone by remember(today.toEpochDay()) { mutableStateOf(false) }
    var restoreToastLatched by remember(today.toEpochDay()) { mutableStateOf(false) }

    // Also key on Room fields so a remember re-key after Flow load cannot
    // permanently wipe a just-restored stash (toast still one-shot latched).
    LaunchedEffect(
        today.toEpochDay(),
        todayEntry?.clockInMinutes,
        todayEntry?.clockOutMinutes,
        todayEntry?.comments
    ) {
        val todayEpoch = today.toEpochDay()
        val roomIn = todayEntry?.clockInMinutes
        val roomOut = todayEntry?.clockOutMinutes
        val roomComments = todayEntry?.comments.orEmpty()
        val decision = HomeDraftStash.decideRestore(
            stash = HomeDraftPreferences.load(context),
            todayEpochDay = todayEpoch,
            localIn = homeInMinutes,
            localOut = homeOutMinutes,
            localComments = homeCommentsDraft,
            roomIn = roomIn,
            roomOut = roomOut,
            roomComments = roomComments
        )
        when (decision) {
            is HomeDraftRestoreDecision.ClearStaleDay,
            is HomeDraftRestoreDecision.ClearStaleDraft -> {
                // Stale day or Room already won (Entry/other save) — silent clear.
                HomeDraftPreferences.clear(context)
            }
            is HomeDraftRestoreDecision.Apply -> {
                homeInMinutes = decision.snapshot.inMinutes
                homeOutMinutes = decision.snapshot.outMinutes
                homeCommentsDraft = decision.snapshot.comments
                if (!restoreToastLatched) {
                    Toast.makeText(
                        context,
                        ZeroTimeNote.RESTORED_UNSAVED_TIMES_TOAST,
                        Toast.LENGTH_SHORT
                    ).show()
                    restoreToastLatched = true
                }
            }
            HomeDraftRestoreDecision.None -> Unit
        }
        draftRestoreDone = true
    }

    LaunchedEffect(
        draftRestoreDone,
        homeInMinutes,
        homeOutMinutes,
        homeCommentsDraft,
        todayEntry?.clockInMinutes,
        todayEntry?.clockOutMinutes,
        todayEntry?.comments
    ) {
        if (!draftRestoreDone) return@LaunchedEffect
        val todayEpoch = today.toEpochDay()
        val roomIn = todayEntry?.clockInMinutes
        val roomOut = todayEntry?.clockOutMinutes
        val roomComments = todayEntry?.comments.orEmpty()
        if (HomeDraftStash.shouldPersist(
                homeInMinutes,
                homeOutMinutes,
                homeCommentsDraft,
                roomIn,
                roomOut,
                roomComments
            )
        ) {
            HomeDraftPreferences.save(
                context,
                HomeDraftSnapshot(
                    epochDay = todayEpoch,
                    inMinutes = homeInMinutes,
                    outMinutes = homeOutMinutes,
                    comments = homeCommentsDraft
                )
            )
        } else {
            HomeDraftPreferences.clearIfEpochDay(context, todayEpoch)
        }
    }

    fun performTypedSave(
        typed: Double,
        clockIn: Int?,
        clockOut: Int?,
        note: String,
        noLunch: Boolean,
        forceDiscard: Boolean = false
    ) {
        val lunchOut = if (noLunch) null else todayEntry?.lunchOutMinutes
        val lunchIn = if (noLunch) null else todayEntry?.lunchInMinutes
        val breakDur = if (noLunch) null else todayEntry?.breakDurationMinutes
        if (forceDiscard) {
            viewModel.discardOpenAndSaveEntry(
                date = today,
                clockInMinutes = clockIn,
                clockOutMinutes = clockOut,
                comments = note,
                lunchOutMinutes = lunchOut,
                lunchInMinutes = lunchIn,
                breakDurationMinutes = breakDur,
                breakPaid = todayEntry?.breakPaid ?: false,
                typedHours = typed,
                noLunchTaken = noLunch
            ) {
                HomeDraftPreferences.clear(context)
                homeInMinutes = clockIn
                homeOutMinutes = clockOut
                homeCommentsDraft = note
                showAddChangeSheet = false
                Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
            }
        } else {
            viewModel.saveEntry(
                date = today,
                clockInMinutes = clockIn,
                clockOutMinutes = clockOut,
                comments = note,
                lunchOutMinutes = lunchOut,
                lunchInMinutes = lunchIn,
                breakDurationMinutes = breakDur,
                breakPaid = todayEntry?.breakPaid ?: false,
                typedHours = typed,
                noLunchTaken = noLunch
            ) { result ->
                when (result) {
                    is SaveEntryResult.Saved -> {
                        HomeDraftPreferences.clear(context)
                        homeInMinutes = clockIn
                        homeOutMinutes = clockOut
                        homeCommentsDraft = note
                        showAddChangeSheet = false
                        Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                    }
                    is SaveEntryResult.BlockedOvernightOpen -> {
                        pendingTypedHours = typed
                        pendingNoLunch = noLunch
                        homeInMinutes = clockIn
                        homeOutMinutes = clockOut
                        homeCommentsDraft = note
                        manualOpenOvernightDate = result.openDate
                        showManualBlockedOvernight = true
                    }
                }
            }
        }
    }

    fun onAddChangeSave(result: AddChangeHoursResult) {
        homeInMinutes = result.clockInMinutes
        homeOutMinutes = result.clockOutMinutes
        homeCommentsDraft = result.note
        sheetNoLunch = result.noLunchTaken
        if (ZeroTimeNote.needsZeroHoursReason(
                result.clockInMinutes,
                result.clockOutMinutes,
                result.typedHours,
                typedHours = result.typedHours
            ) && !ZeroTimeNote.hasZeroHoursReason(result.note)
        ) {
            pendingTypedHours = result.typedHours
            pendingNoLunch = result.noLunchTaken
            zeroHoursReasonText = ""
            showZeroHoursDialog = true
            return
        }
        // Midnight note gate when clocks include 0
        if (!ZeroTimeNote.canSaveWithNote(
                result.note,
                clockInMinutes = result.clockInMinutes,
                clockOutMinutes = result.clockOutMinutes
            )
        ) {
            Toast.makeText(
                context,
                "Add a reason note for 12:00 AM (0) before saving",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val cin = result.clockInMinutes
        val cout = result.clockOutMinutes
        if (cin != null && cout != null && HomeManualTimes.needsOvernightConfirm(cin, cout)) {
            pendingTypedHours = result.typedHours
            pendingNoLunch = result.noLunchTaken
            showManualOvernightConfirm = true
        } else {
            performTypedSave(
                result.typedHours,
                result.clockInMinutes,
                result.clockOutMinutes,
                result.note,
                result.noLunchTaken
            )
        }
    }

    fun applyHomeClockMinutes(field: HomeClockField, minutes: Int, reasonNote: String? = null) {
        when (field) {
            HomeClockField.IN -> {
                val action = HomeOpenPunch.decide(
                    todayIn = todayEntry?.clockInMinutes,
                    todayOut = todayEntry?.clockOutMinutes,
                    todayHoursWorked = todayEntry?.hoursWorked ?: 0.0,
                    overnightOrOrphanPending = homeClock.overnightPending
                )
                // D1: pass draft (or merged midnight reason) so open-punch IN does not
                // drop a prior OUT/LOCAL_ONLY stash when Room re-emits comments.
                val punchComments = HomeOpenPunch.punchCommentsForOpenIn(
                    homeCommentsDraft,
                    reasonNote
                )
                when (action) {
                    HomeOpenPunch.Action.SHOW_OVERNIGHT -> {
                        // Same gate as Clock-in-now — do not dirty homeInMinutes.
                        showOvernightDialog = true
                    }
                    HomeOpenPunch.Action.START_OPEN -> {
                        viewModel.clockInAt(today, minutes, punchComments) { result ->
                            when (result) {
                                ClockInResult.STARTED -> {
                                    homeInMinutes = minutes
                                    if (punchComments != null) homeCommentsDraft = punchComments
                                    Toast.makeText(context, "Clock-in saved", Toast.LENGTH_SHORT).show()
                                }
                                ClockInResult.BLOCKED_OVERNIGHT -> {
                                    showOvernightDialog = true
                                }
                                ClockInResult.BUSY -> {
                                    Toast.makeText(context, "Please wait…", Toast.LENGTH_SHORT).show()
                                }
                                ClockInResult.ALREADY_OPEN -> {
                                    Toast.makeText(context, "Already clocked in", Toast.LENGTH_SHORT).show()
                                }
                                ClockInResult.ALREADY_CLOSED -> {
                                    Toast.makeText(
                                        context,
                                        "Today already has hours — edit the day to change it",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    }
                    HomeOpenPunch.Action.UPDATE_OPEN -> {
                        viewModel.updateOpenClockIn(today, minutes, punchComments) { result ->
                            when (result) {
                                UpdateOpenClockInResult.UPDATED -> {
                                    homeInMinutes = minutes
                                    if (punchComments != null) homeCommentsDraft = punchComments
                                    Toast.makeText(context, "Clock-in updated", Toast.LENGTH_SHORT).show()
                                }
                                UpdateOpenClockInResult.BUSY -> {
                                    Toast.makeText(context, "Please wait…", Toast.LENGTH_SHORT).show()
                                }
                                UpdateOpenClockInResult.FAILED -> {
                                    Toast.makeText(
                                        context,
                                        "Could not update clock-in — try again",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    }
                    HomeOpenPunch.Action.LOCAL_ONLY -> {
                        // Closed day: local minutes + stash note until Save (no immediate Room write).
                        homeInMinutes = minutes
                        if (punchComments != null) {
                            homeCommentsDraft = punchComments
                        }
                    }
                }
            }
            HomeClockField.OUT -> {
                homeOutMinutes = minutes
                if (reasonNote != null && reasonNote.isNotBlank()) {
                    // Keep reason in Compose until Save (works even with no row yet).
                    homeCommentsDraft = ZeroTimeNote.mergeReasonIntoNote(
                        homeCommentsDraft,
                        "Clock out",
                        reasonNote
                    )
                }
            }
        }
    }

    fun restoreSheetAfterPicker() {
        if (sheetSuspendedForPicker) {
            sheetSuspendedForPicker = false
            showAddChangeSheet = true
        }
    }

    fun suspendSheetForPicker(draft: AddChangeHoursDraft, field: HomeClockField) {
        sheetDraftHoursText = draft.hoursText
        sheetDraftNote = draft.note
        sheetNoLunch = draft.noLunchTaken
        homeCommentsDraft = draft.note
        showAddChangeSheet = false
        sheetSuspendedForPicker = true
        homePickerField = field
    }

    fun onHomeTimePicked(field: HomeClockField, minutes: Int) {
        if (ZeroTimeNote.needsReason(minutes)) {
            pendingZeroField = field
            pendingZeroMinutes = minutes
            zeroReasonText = ""
            homePickerField = null
            // Keep sheet suspended until midnight reason dialog finishes
        } else {
            applyHomeClockMinutes(field, minutes)
            homePickerField = null
            restoreSheetAfterPicker()
        }
    }


    // Pause: freeze elapsed display without closing OPEN punch
    @Suppress("UNUSED_EXPRESSION")
    pauseRevision
    val todayEpoch = today.toEpochDay()
    val paused = SessionPausePreferences.isPaused(context, todayEpoch)
    val freezeMillis = SessionPausePreferences.freezeEpochMillis(context, todayEpoch)
    // Freeze minutes available for SessionElapsed callers (goals moved off Home).
    @Suppress("UNUSED_VARIABLE")
    val pauseFreezeMinutes = SessionPause.effectiveNowMinutes(
        liveNowMinutes = java.time.LocalTime.now().let { it.hour * 60 + it.minute },
        freezeEpochMillis = freezeMillis,
        today = today
    ).takeIf { paused }

    val todayOpenSession = todayEntry?.clockInMinutes != null &&
        todayEntry?.clockOutMinutes == null &&
        !homeClock.overnightPending

    // Clear stale pause when day is no longer OPEN
    LaunchedEffect(todayOpenSession, todayEpoch) {
        if (!todayOpenSession && SessionPausePreferences.isPaused(context, todayEpoch)) {
            SessionPausePreferences.clear(context)
            pauseRevision++
        }
    }

    // Consume More → Today actions (Add/Change, Set times, Forgot)
    LaunchedEffect(pendingMoreAction) {
        when (pendingMoreAction) {
            HomeMoreAction.ADD_CHANGE_HOURS -> {
                val hasHours = todayEntry?.hasPersistedHours() == true
                addChangeTitle = if (hasHours) "Change hours" else "Add hours"
                sheetDraftHoursText = when {
                    todayEntry?.hasPersistedHours() == true ->
                        formatHoursField(todayEntry!!.hoursWorked)
                    else -> ""
                }
                sheetDraftNote = homeCommentsDraft.ifBlank { todayEntry?.comments.orEmpty() }
                sheetNoLunch = todayEntry?.noLunchTaken ?: false
                sheetSuspendedForPicker = false
                showAddChangeSheet = true
                onPendingMoreActionConsumed()
            }
            HomeMoreAction.SET_TODAYS_TIMES -> {
                addChangeTitle = "Set today's times"
                sheetDraftHoursText = when {
                    todayEntry?.hasPersistedHours() == true ->
                        formatHoursField(todayEntry!!.hoursWorked)
                    else -> ""
                }
                sheetDraftNote = homeCommentsDraft.ifBlank { todayEntry?.comments.orEmpty() }
                sheetNoLunch = todayEntry?.noLunchTaken == true
                sheetSuspendedForPicker = false
                showAddChangeSheet = true
                onPendingMoreActionConsumed()
            }
            HomeMoreAction.FORGOT_CLOCK_OUT -> {
                showForgotClockOut = true
                onPendingMoreActionConsumed()
            }
            null -> Unit
        }
    }

    fun doClockIn() {
        if (homeClock.overnightPending) {
            showOvernightDialog = true
            return
        }
        val nowMins = java.time.LocalTime.now().let { it.hour * 60 + it.minute }
        viewModel.clockInNow(today) { result ->
            when (result) {
                ClockInResult.BLOCKED_OVERNIGHT -> {
                    showOvernightDialog = true
                }
                ClockInResult.STARTED -> {
                    homeInMinutes = nowMins
                    SessionPausePreferences.clear(context)
                    pauseRevision++
                    ClockHaptics.performSuccess(view)
                    Toast.makeText(context, "Clocked in now", Toast.LENGTH_SHORT).show()
                }
                ClockInResult.ALREADY_OPEN -> {
                    Toast.makeText(context, "Already clocked in", Toast.LENGTH_SHORT).show()
                }
                ClockInResult.ALREADY_CLOSED -> {
                    Toast.makeText(
                        context,
                        "Today already has hours — edit the day to change it",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                ClockInResult.BUSY -> {
                    Toast.makeText(context, "Please wait…", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun doClockOut() {
        viewModel.clockOutNow(today) { result ->
            when (result) {
                ClockOutResult.SUCCESS,
                ClockOutResult.SUCCESS_OVERNIGHT -> {
                    SessionPausePreferences.clear(context)
                    pauseRevision++
                    ClockHaptics.performSuccess(view)
                }
                else -> Unit
            }
            val msg = when (result) {
                ClockOutResult.SUCCESS -> "Clocked out now"
                ClockOutResult.SUCCESS_OVERNIGHT ->
                    HomeOvernightCopy.clockOutOvernightToast(
                        homeClock.openOvernightDate,
                        today
                    )
                ClockOutResult.FAILED ->
                    "Clock in first (or use a different time)"
                ClockOutResult.ALREADY_CLOSED ->
                    "Today is already clocked out — edit the day to change it"
                ClockOutResult.BUSY -> "Please wait…"
            }
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    fun doPause() {
        if (!todayOpenSession || paused) return
        SessionPausePreferences.pause(context, todayEpoch, System.currentTimeMillis())
        pauseRevision++
    }

    fun doResume() {
        if (!paused) return
        SessionPausePreferences.resume(context)
        pauseRevision++
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Work Hours")
                        Text(
                            "Week · ${formatHours(total)}" +
                                if (todayOpenSession && !paused) " · live" else "",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                actions = {
                    // Thin alias to bottom-bar More (single entry preferred)
                    IconButton(
                        onClick = onOpenMore,
                        modifier = Modifier.semantics { contentDescription = "More options" }
                    ) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                    }
                }
            )
        }
    ) { padding ->
        @Suppress("UNUSED_EXPRESSION")
        nowEpochMillis
        val dateLabel = today.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()) +
            " · " + today.format(DateTimeFormatter.ofPattern("MMM d"))
        val lunchCaption = when {
            todayEntry?.noLunchTaken == true -> "No lunch taken · caption only"
            todayEntry?.breakDurationMinutes != null -> "Break logged · caption only"
            todayOpenSession -> "Lunch lives in More · caption only"
            else -> null
        }
        val closedTodayHours = when {
            todayEntry?.hasPersistedHours() == true -> todayEntry!!.hoursWorked
            else -> null
        }

        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.weight(0.35f))

                when {
                    homeClock.overnightPending -> {
                        Text(
                            "Open shift unfinished",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            dateLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                        ) {
                            Text(
                                "An open shift is still unfinished. Clock out finishes it, or use Clock in for options.",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                        Button(
                            onClick = { showOvernightDialog = true },
                            enabled = !clockBusy,
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .height(60.dp)
                                .semantics { contentDescription = "Clock in" },
                            shape = RoundedCornerShape(50)
                        ) {
                            Text("Clock in", style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            HomeOvernightCopy.clockOutHelper(
                                homeClock.overnightPending,
                                homeClock.openOvernightDate,
                                today
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    todayOpenSession && paused -> {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            Text(
                                "●  Paused",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                        Text(
                            "PAUSED AT",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 1.sp
                        )
                        val inM = todayEntry!!.clockInMinutes!!
                        val endMs = freezeMillis ?: nowEpochMillis
                        val hms = SessionPause.formatHms(
                            SessionPause.elapsedMillis(inM, endMs, today)
                        )
                        Text(
                            hms,
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontSize = 56.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Light
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .padding(vertical = 8.dp)
                                .semantics { contentDescription = "Elapsed time" }
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { doResume() },
                                enabled = !clockBusy,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 52.dp)
                                    .semantics { contentDescription = "Resume" },
                                shape = RoundedCornerShape(28.dp)
                            ) { Text("Resume") }
                            OutlinedButton(
                                onClick = { doClockOut() },
                                enabled = !clockBusy && homeClock.clockOutEnabled,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 52.dp)
                                    .semantics { contentDescription = "Stop" },
                                shape = RoundedCornerShape(28.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                            ) { Text("Stop") }
                        }
                    }

                    todayOpenSession -> {
                        val inLabel = HoursCalc.formatClock(todayEntry!!.clockInMinutes!!)
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            Text(
                                "●  Clocked in · $inLabel",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                        Text(
                            "ELAPSED",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 1.sp
                        )
                        val inM = todayEntry!!.clockInMinutes!!
                        val hms = SessionPause.formatHms(
                            SessionPause.elapsedMillis(inM, nowEpochMillis, today)
                        )
                        Text(
                            hms,
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontSize = 56.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Light
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .padding(vertical = 8.dp)
                                .semantics { contentDescription = "Elapsed time" }
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = { doPause() },
                                enabled = !clockBusy,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 52.dp)
                                    .semantics { contentDescription = "Pause" },
                                shape = RoundedCornerShape(28.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                                )
                            ) { Text("Pause") }
                            OutlinedButton(
                                onClick = { doClockOut() },
                                enabled = !clockBusy && homeClock.clockOutEnabled,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 52.dp)
                                    .semantics { contentDescription = "Stop" },
                                shape = RoundedCornerShape(28.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                            ) { Text("Stop") }
                        }
                        if (lunchCaption != null) {
                            Text(
                                lunchCaption,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 14.dp)
                            )
                        }
                    }

                    else -> {
                        // Idle B — wide pill Clock in; no goals; no peer CTA farm
                        Text(
                            "Not clocked in",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            dateLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 28.dp)
                        )
                        Button(
                            onClick = { doClockIn() },
                            enabled = !clockBusy && (
                                homeClock.clockInEnabled || homeClock.overnightPending
                            ),
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .height(60.dp)
                                .semantics { contentDescription = "Clock in" },
                            shape = RoundedCornerShape(50)
                        ) {
                            Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(
                                "Clock in",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (closedTodayHours != null) {
                            Text(
                                "Today · ${formatHours(closedTodayHours)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 20.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.weight(0.65f))
            }
        }
    }

    if (showOvernightDialog) {
        val openDay = homeClock.openOvernightDate ?: today.minusDays(1)
        val isYesterday = openDay == today.minusDays(1)
        val dayLabel = if (isYesterday) "yesterday" else openDay.toString()
        AlertDialog(
            onDismissRequest = { showOvernightDialog = false },
            title = {
                Text(
                    if (isYesterday) "Yesterday's shift is still open"
                    else "Open shift still unfinished"
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Finish the open shift, edit $dayLabel's times, or discard the open punch and clock in today."
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            if (clockBusy) return@TextButton
                            showOvernightDialog = false
                            viewModel.clockOutNow(today) { result ->
                                when (result) {
                                    ClockOutResult.SUCCESS,
                                    ClockOutResult.SUCCESS_OVERNIGHT -> {
                                        SessionPausePreferences.clear(context)
                                        pauseRevision++
                                        ClockHaptics.performSuccess(view)
                                    }
                                    else -> Unit
                                }
                                val msg = when (result) {
                                    ClockOutResult.SUCCESS_OVERNIGHT ->
                                        HomeOvernightCopy.clockOutOvernightToast(
                                            homeClock.openOvernightDate,
                                            today
                                        )
                                    ClockOutResult.SUCCESS -> "Clocked out now"
                                    ClockOutResult.FAILED ->
                                        "Could not finish open shift — try editing $dayLabel"
                                    ClockOutResult.ALREADY_CLOSED ->
                                        "Today is already clocked out"
                                    ClockOutResult.BUSY -> "Please wait…"
                                }
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = !clockBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Finish overnight (clock out now)") }
                    TextButton(
                        onClick = {
                            showOvernightDialog = false
                            onDayClick(openDay)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isYesterday) "Edit yesterday" else "Edit open day")
                    }
                    TextButton(
                        onClick = {
                            if (clockBusy) return@TextButton
                            showOvernightDialog = false
                            viewModel.discardOvernightAndClockIn(today) { result ->
                                val msg = when (result) {
                                    ClockInResult.STARTED ->
                                        "Discarded open punch and clocked in"
                                    ClockInResult.ALREADY_OPEN -> "Already clocked in"
                                    ClockInResult.ALREADY_CLOSED ->
                                        "Today already has hours — edit the day"
                                    ClockInResult.BLOCKED_OVERNIGHT ->
                                        "Still blocked — try again"
                                    ClockInResult.BUSY -> "Please wait…"
                                }
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = !clockBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Discard open punch & clock in today") }
                }
            },
            confirmButton = {
                TextButton(onClick = { showOvernightDialog = false }) { Text("Cancel") }
            }
        )
    }

    homePickerField?.let { field ->
        HomeClockPickerDialog(
            field = field,
            currentMinutes = when (field) {
                HomeClockField.IN -> homeInMinutes
                HomeClockField.OUT -> homeOutMinutes
            },
            onConfirm = { minutes -> onHomeTimePicked(field, minutes) },
            onDismiss = {
                homePickerField = null
                restoreSheetAfterPicker()
            }
        )
    }

    pendingZeroField?.let { field ->
        val minutes = pendingZeroMinutes ?: 0
        val fieldLabel = when (field) {
            HomeClockField.IN -> "Clock in"
            HomeClockField.OUT -> "Clock out"
        }
        val canConfirm = zeroReasonText.trim().isNotEmpty()
        AlertDialog(
            onDismissRequest = {
                // Cancel / dismiss: revert — never apply minutes == 0
                pendingZeroField = null
                pendingZeroMinutes = null
                zeroReasonText = ""
                restoreSheetAfterPicker()
            },
            title = { Text(ZeroTimeNote.DIALOG_TITLE) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(ZeroTimeNote.dialogBody(fieldLabel))
                    OutlinedTextField(
                        value = zeroReasonText,
                        onValueChange = { if (it.length <= 500) zeroReasonText = it },
                        label = { Text(ZeroTimeNote.DIALOG_LABEL) },
                        placeholder = { Text(ZeroTimeNote.DIALOG_PLACEHOLDER) },
                        supportingText = {
                            Text(
                                if (!canConfirm && zeroReasonText.isNotEmpty()) ZeroTimeNote.EMPTY_ERROR
                                else if (!canConfirm) ZeroTimeNote.EMPTY_ERROR
                                else "${zeroReasonText.length}/500"
                            )
                        },
                        isError = zeroReasonText.isNotEmpty() && !canConfirm,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = ZeroTimeNote.DIALOG_LABEL }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val reason = zeroReasonText.trim()
                        if (reason.isEmpty()) return@TextButton
                        pendingZeroField = null
                        pendingZeroMinutes = null
                        zeroReasonText = ""
                        applyHomeClockMinutes(field, minutes, reason)
                        restoreSheetAfterPicker()
                    },
                    enabled = canConfirm,
                    modifier = Modifier.semantics {
                        contentDescription = ZeroTimeNote.CONFIRM_LABEL
                    }
                ) { Text(ZeroTimeNote.CONFIRM_LABEL) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingZeroField = null
                        pendingZeroMinutes = null
                        zeroReasonText = ""
                        restoreSheetAfterPicker()
                    },
                    modifier = Modifier.semantics {
                        contentDescription = ZeroTimeNote.DISMISS_LABEL
                    }
                ) { Text(ZeroTimeNote.DISMISS_LABEL) }
            }
        )
    }

    if (showZeroHoursDialog) {
        val canConfirm = zeroHoursReasonText.trim().isNotEmpty()
        AlertDialog(
            onDismissRequest = {
                showZeroHoursDialog = false
                zeroHoursReasonText = ""
            },
            title = { Text(ZeroTimeNote.ZERO_HOURS_TITLE) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(ZeroTimeNote.ZERO_HOURS_BODY)
                    OutlinedTextField(
                        value = zeroHoursReasonText,
                        onValueChange = { if (it.length <= 500) zeroHoursReasonText = it },
                        label = { Text(ZeroTimeNote.DIALOG_LABEL) },
                        placeholder = { Text(ZeroTimeNote.DIALOG_PLACEHOLDER) },
                        supportingText = {
                            Text(
                                if (!canConfirm) ZeroTimeNote.EMPTY_ERROR
                                else "${zeroHoursReasonText.length}/500"
                            )
                        },
                        isError = zeroHoursReasonText.isNotEmpty() && !canConfirm,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = ZeroTimeNote.DIALOG_LABEL }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val reason = zeroHoursReasonText.trim()
                        if (reason.isEmpty()) return@TextButton
                        homeCommentsDraft = ZeroTimeNote.mergeZeroHoursIntoNote(
                            homeCommentsDraft,
                            reason
                        )
                        showZeroHoursDialog = false
                        zeroHoursReasonText = ""
                        val typed = pendingTypedHours
                        if (typed != null) {
                            val cin = homeInMinutes
                            val cout = homeOutMinutes
                            if (cin != null && cout != null &&
                                HomeManualTimes.needsOvernightConfirm(cin, cout)
                            ) {
                                showManualOvernightConfirm = true
                            } else {
                                performTypedSave(
                                    typed, cin, cout, homeCommentsDraft, pendingNoLunch
                                )
                                pendingTypedHours = null
                            }
                        }
                    },
                    enabled = canConfirm,
                    modifier = Modifier.semantics {
                        contentDescription = ZeroTimeNote.CONFIRM_LABEL
                    }
                ) { Text(ZeroTimeNote.CONFIRM_LABEL) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showZeroHoursDialog = false
                        zeroHoursReasonText = ""
                    },
                    modifier = Modifier.semantics {
                        contentDescription = ZeroTimeNote.DISMISS_LABEL
                    }
                ) { Text(ZeroTimeNote.DISMISS_LABEL) }
            }
        )
    }

    if (showForgotClockOut) {
        val initial = java.time.LocalTime.now()
        val state = rememberTimePickerState(
            initialHour = initial.hour,
            initialMinute = initial.minute,
            is24Hour = false
        )
        AlertDialog(
            onDismissRequest = { showForgotClockOut = false },
            title = { Text("Forgot to clock out") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Pick the time you actually left. Closes the open shift at that time.")
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        TimePicker(state = state)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (clockBusy) return@TextButton
                        val minutes = state.hour * 60 + state.minute
                        showForgotClockOut = false
                        viewModel.clockOutAt(today, minutes) { result ->
                            when (result) {
                                ClockOutResult.SUCCESS,
                                ClockOutResult.SUCCESS_OVERNIGHT -> {
                                    SessionPausePreferences.clear(context)
                                    pauseRevision++
                                    ClockHaptics.performSuccess(view)
                                }
                                else -> Unit
                            }
                            val msg = when (result) {
                                ClockOutResult.SUCCESS ->
                                    "Clocked out at ${HoursCalc.formatClock(minutes)}"
                                ClockOutResult.SUCCESS_OVERNIGHT ->
                                    "Finished overnight at ${HoursCalc.formatClock(minutes)}"
                                ClockOutResult.FAILED ->
                                    "Could not clock out — try editing the day"
                                ClockOutResult.ALREADY_CLOSED ->
                                    "Already clocked out — edit the day to change it"
                                ClockOutResult.BUSY -> "Please wait…"
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = !clockBusy
                ) { Text("Clock out") }
            },
            dismissButton = {
                TextButton(onClick = { showForgotClockOut = false }) { Text("Cancel") }
            }
        )
    }

    if (showManualOvernightConfirm) {
        val hoursLabel = if (homeInMinutes != null && homeOutMinutes != null) {
            HoursCalc.formatHours(
                HoursCalc.hoursWorked(homeInMinutes!!, homeOutMinutes!!)
            )
        } else {
            "~?"
        }
        AlertDialog(
            onDismissRequest = { showManualOvernightConfirm = false },
            title = { Text("Overnight shift?") },
            text = {
                Text("Clock out is earlier than clock in. Treat as overnight ($hoursLabel)?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showManualOvernightConfirm = false
                        val typed = pendingTypedHours
                        if (typed != null) {
                            performTypedSave(
                                typed, homeInMinutes, homeOutMinutes,
                                homeCommentsDraft, pendingNoLunch
                            )
                            pendingTypedHours = null
                        }
                    }
                ) { Text("Save as overnight") }
            },
            dismissButton = {
                TextButton(onClick = { showManualOvernightConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showManualBlockedOvernight) {
        val openDay = manualOpenOvernightDate ?: today.minusDays(1)
        AlertDialog(
            onDismissRequest = { showManualBlockedOvernight = false },
            title = { Text("Open overnight punch") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Another day still has an open clock-in with no clock-out. " +
                            "Finish that day first, or discard the open punch to save today."
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            showManualBlockedOvernight = false
                            onDayClick(openDay)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Edit open day") }
                    TextButton(
                        onClick = {
                            showManualBlockedOvernight = false
                            val typed = pendingTypedHours
                            if (typed != null) {
                                performTypedSave(
                                    typed, homeInMinutes, homeOutMinutes,
                                    homeCommentsDraft, pendingNoLunch,
                                    forceDiscard = true
                                )
                                pendingTypedHours = null
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Discard open punch & save") }
                }
            },
            confirmButton = {
                TextButton(onClick = { showManualBlockedOvernight = false }) { Text("Cancel") }
            }
        )
    }
    if (showAddChangeSheet) {
        AddChangeHoursSheet(
            title = addChangeTitle,
            initialHours = sheetDraftHoursText,
            initialClockIn = homeInMinutes ?: todayEntry?.clockInMinutes,
            initialClockOut = homeOutMinutes ?: todayEntry?.clockOutMinutes,
            initialNote = sheetDraftNote.ifBlank {
                homeCommentsDraft.ifBlank { todayEntry?.comments.orEmpty() }
            },
            initialNoLunchTaken = sheetNoLunch,
            clockInMinutes = homeInMinutes,
            clockOutMinutes = homeOutMinutes,
            onDismiss = {
                showAddChangeSheet = false
                sheetSuspendedForPicker = false
            },
            onSave = { onAddChangeSave(it) },
            onPickClockIn = { draft -> suspendSheetForPicker(draft, HomeClockField.IN) },
            onPickClockOut = { draft -> suspendSheetForPicker(draft, HomeClockField.OUT) }
        )
    }

}

private enum class HomeClockField { IN, OUT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeClockPickerDialog(
    field: HomeClockField,
    currentMinutes: Int?,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val initial = currentMinutes ?: HomeManualTimes.defaultPickerMinutes(field == HomeClockField.IN)
    val state = rememberTimePickerState(
        initialHour = initial / 60,
        initialMinute = initial % 60,
        is24Hour = false
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        title = {
            Text(if (field == HomeClockField.IN) "Clock in" else "Clock out")
        },
        text = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = state)
            }
        }
    )
}

fun formatHours(hours: Double): String = HoursCalc.formatHours(hours)
