package com.rmltd.workhourstracker.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.ui.unit.dp
import com.rmltd.workhourstracker.data.ClockInResult
import com.rmltd.workhourstracker.data.ClockOutResult
import com.rmltd.workhourstracker.data.HomeDraftPreferences
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
import com.rmltd.workhourstracker.viewmodel.WorkHoursViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: WorkHoursViewModel,
    onDayClick: (LocalDate) -> Unit,
    onViewLog: () -> Unit,
    onSettings: () -> Unit,
    onLogLunch: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val entries by viewModel.currentWeekEntries.collectAsState()
    val weekStart by viewModel.weekStart.collectAsState()
    val weeklyGoal by viewModel.weeklyGoalHours.collectAsState()
    val hourlyRate by viewModel.hourlyRate.collectAsState()
    val homeClock by viewModel.homeClockUi.collectAsState()
    val clockBusy by viewModel.clockOpInProgress.collectAsState()
    val daysInWeek = viewModel.daysInWeek(weekStart)
    val total = viewModel.runningTotal(entries)
    val today = LocalDate.now()
    val todayInThisWeek = daysInWeek.contains(today)
    val progress = PayEstimate.progressFraction(total, weeklyGoal)
    val remaining = PayEstimate.remainingHours(total, weeklyGoal)
    val overtime = PayEstimate.isOvertime(total, weeklyGoal)
    val overtimeHrs = PayEstimate.overtimeHours(total, weeklyGoal)
    val weekPayEstimate = PayEstimate.roughPay(total, hourlyRate)

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
    var showOverflow by remember { mutableStateOf(false) }
    var showAddChangeSheet by remember { mutableStateOf(false) }
    var addChangeTitle by remember { mutableStateOf("Add hours") }
    var pendingTypedHours by remember { mutableStateOf<Double?>(null) }
    var pendingNoLunch by remember { mutableStateOf(false) }
    var sheetNoLunch by remember { mutableStateOf(false) }
    // S4: suspend Add/Change sheet while TimePicker is alone; restore after OK/Cancel
    var sheetSuspendedForPicker by remember { mutableStateOf(false) }
    var sheetDraftHoursText by remember { mutableStateOf("") }
    var sheetDraftNote by remember { mutableStateOf("") }
    // Live tick for open-punch elapsed total
    var nowEpochMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("This Week")
                        Text(
                            formatHours(total),
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
                    IconButton(
                        onClick = { showOverflow = true },
                        modifier = Modifier.semantics { contentDescription = "More options" }
                    ) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(
                        expanded = showOverflow,
                        onDismissRequest = { showOverflow = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Set today's times…") },
                            onClick = {
                                showOverflow = false
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
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Log lunch / break…") },
                            onClick = {
                                showOverflow = false
                                onLogLunch()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Forgot to clock out…") },
                            onClick = {
                                showOverflow = false
                                showForgotClockOut = true
                            },
                            enabled = homeClock.clockOutEnabled && !clockBusy
                        )
                        DropdownMenuItem(
                            text = { Text("History") },
                            onClick = {
                                showOverflow = false
                                onViewLog()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Settings") },
                            onClick = {
                                showOverflow = false
                                onSettings()
                            }
                        )
                    }
                }
            )
        }
    ) { padding ->
        val stripContainer = if (overtime) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer
        }
        val stripOn = if (overtime) {
            MaterialTheme.colorScheme.onTertiaryContainer
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        }
        val stripProgressColor = if (overtime) {
            MaterialTheme.colorScheme.tertiary
        } else {
            MaterialTheme.colorScheme.primary
        }
        // Single LazyColumn: whole Home scrolls; no weight(1f) empty gap above History.
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp)
        ) {
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = stripContainer
                    ),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(20.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(96.dp)
                        ) {
                            CircularProgressIndicator(
                                progress = progress,
                                modifier = Modifier.fillMaxSize(),
                                strokeWidth = 10.dp,
                                color = stripProgressColor,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Text(
                                "${(progress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = stripOn
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "This week",
                                style = MaterialTheme.typography.labelMedium,
                                color = stripOn.copy(alpha = 0.85f)
                            )
                            Text(
                                "${weekStart.format(DateTimeFormatter.ofPattern("MMM d"))} – " +
                                    daysInWeek.last().format(DateTimeFormatter.ofPattern("MMM d")),
                                style = MaterialTheme.typography.titleSmall,
                                color = stripOn
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                formatHours(total),
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                color = stripOn
                            )
                            Text(
                                if (overtime) {
                                    "Goal ${formatHours(weeklyGoal)} · ${formatHours(overtimeHrs)} over"
                                } else {
                                    "Goal ${formatHours(weeklyGoal)} · ${formatHours(remaining)} remaining"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = stripOn.copy(alpha = 0.85f)
                            )
                            weekPayEstimate?.let { pay ->
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "Est. ${PayEstimate.formatCurrencyUsd(pay)} (not payroll)",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = stripOn.copy(alpha = 0.92f)
                                )
                            }
                        }
                    }
                }
            }

            if (todayInThisWeek) {
                item {
                    Spacer(Modifier.height(12.dp))
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Today",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            // Today's total: shared SessionElapsed (open live / closed saved)
                            val todayOpen = todayEntry?.clockInMinutes != null &&
                                todayEntry?.clockOutMinutes == null &&
                                !homeClock.overnightPending
                            @Suppress("UNUSED_EXPRESSION")
                            nowEpochMillis
                            val nowM = java.time.LocalTime.now().let { it.hour * 60 + it.minute }
                            val displayTotal: Double? = SessionElapsed.todayDisplayHours(
                                todayEntry, nowM
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                displayTotal?.let { formatHours(it) } ?: "—",
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.semantics {
                                    contentDescription = "Today's total"
                                }
                            )
                            val hasHours = todayEntry?.hasPersistedHours() == true
                            TextButton(
                                onClick = {
                                    addChangeTitle = if (hasHours) "Change hours" else "Add hours"
                                    sheetDraftHoursText = when {
                                        todayEntry?.hasPersistedHours() == true ->
                                            formatHoursField(todayEntry!!.hoursWorked)
                                        else -> ""
                                    }
                                    sheetDraftNote = homeCommentsDraft.ifBlank { todayEntry?.comments.orEmpty() }
                                    sheetNoLunch = todayEntry?.noLunchTaken == true || sheetNoLunch
                                    sheetSuspendedForPicker = false
                                    sheetNoLunch = todayEntry?.noLunchTaken ?: false
                                    showAddChangeSheet = true
                                },
                                enabled = !clockBusy,
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .semantics {
                                        contentDescription =
                                            if (hasHours) "Change" else "Add hours"
                                    }
                            ) {
                                Text(
                                    if (hasHours) "Change" else "Add hours",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            // Status line
                            val statusLine = when {
                                homeClock.overnightPending ->
                                    "Open shift unfinished — resolve to continue"
                                todayOpen -> {
                                    val inLabel = HoursCalc.formatClock(todayEntry!!.clockInMinutes!!)
                                    "Clocked in · $inLabel"
                                }
                                todayEntry?.clockOutMinutes != null -> {
                                    val range = HoursCalc.formatRange(
                                        todayEntry?.clockInMinutes,
                                        todayEntry?.clockOutMinutes
                                    )
                                    if (range != null) "Clocked out · $range" else "Day saved"
                                }
                                todayEntry?.hoursSourceEnum() == HoursSource.TYPED ->
                                    "Typed hours saved"
                                else -> "Not clocked in"
                            }
                            Text(
                                statusLine,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            if (homeClock.overnightPending) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp)
                                ) {
                                    Text(
                                        "An open shift is still unfinished. Clock out finishes it, or use Clock in for options.",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    )
                                }
                            }
                            // ONE stateful primary: Clock in ↔ Clock out
                            val primaryIsOut = homeClock.clockOutEnabled && !homeClock.overnightPending
                            Button(
                                onClick = {
                                    if (homeClock.overnightPending) {
                                        showOvernightDialog = true
                                        return@Button
                                    }
                                    if (primaryIsOut) {
                                        viewModel.clockOutNow(today) { result ->
                                            when (result) {
                                                ClockOutResult.SUCCESS,
                                                ClockOutResult.SUCCESS_OVERNIGHT ->
                                                    ClockHaptics.performSuccess(view)
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
                                    } else {
                                        val nowMins = java.time.LocalTime.now().let { it.hour * 60 + it.minute }
                                        viewModel.clockInNow(today) { result ->
                                            when (result) {
                                                ClockInResult.BLOCKED_OVERNIGHT -> {
                                                    showOvernightDialog = true
                                                }
                                                ClockInResult.STARTED -> {
                                                    homeInMinutes = nowMins
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
                                },
                                enabled = !clockBusy && (
                                    if (primaryIsOut) homeClock.clockOutEnabled
                                    else homeClock.clockInEnabled || homeClock.overnightPending
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 56.dp)
                                    .semantics {
                                        contentDescription =
                                            if (primaryIsOut) "Clock out" else "Clock in"
                                    }
                            ) {
                                Text(
                                    if (primaryIsOut) "Clock out" else "Clock in",
                                    maxLines = 1
                                )
                            }
                            Text(
                                HomeOvernightCopy.clockOutHelper(
                                    homeClock.overnightPending,
                                    homeClock.openOvernightDate,
                                    today
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }

            // Goals secondary card (under Today / above week list) — not a second primary CTA
            item {
                Spacer(Modifier.height(12.dp))
                val nowMGoal = java.time.LocalTime.now().let { it.hour * 60 + it.minute }
                @Suppress("UNUSED_EXPRESSION")
                nowEpochMillis
                val goalHours = weeklyGoal
                val actualHours = SessionElapsed.weekActualHours(
                    entries, today.toEpochDay(), nowMGoal
                )
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Weekly goals" },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "This week goal",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (goalHours <= 0.0) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Set a weekly goal in Settings",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(onClick = onSettings) {
                                Text("Open Settings")
                            }
                        } else {
                            val over = actualHours > goalHours
                            val fill = (actualHours / goalHours).toFloat().coerceIn(0f, 1f)
                            val pct = ((actualHours / goalHours) * 100.0).toInt()
                            Spacer(Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.size(72.dp)
                                ) {
                                    CircularProgressIndicator(
                                        progress = fill,
                                        modifier = Modifier.fillMaxSize(),
                                        strokeWidth = 8.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    Text(
                                        "${"%.2f".format(java.util.Locale.US, actualHours)} / ${"%.2f".format(java.util.Locale.US, goalHours)}h",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        if (over) {
                                            "$pct% · +${formatHours(actualHours - goalHours)} over"
                                        } else {
                                            "$pct%"
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    TextButton(
                                        onClick = onSettings,
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("Edit goal in Settings")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }

            items(daysInWeek) { date ->
                val entry = viewModel.entryFor(date, entries)
                val fullLabel = HoursCalc.formatDayLabel(
                    entry?.clockInMinutes,
                    entry?.clockOutMinutes,
                    entry?.lunchOutMinutes,
                    entry?.lunchInMinutes,
                    entry?.breakDurationMinutes
                )
                val partialLabel = when {
                    fullLabel != null -> fullLabel
                    entry?.clockInMinutes != null ->
                        "In ${HoursCalc.formatClock(entry.clockInMinutes!!)}"
                    else -> null
                }
                DayRow(
                    date = date,
                    // Closed punch OR typed hours (incl. 0.00) — open punches stay hidden.
                    hours = entry?.takeIf { it.hasPersistedHours() }?.hoursWorked,
                    clockLabel = partialLabel,
                    hasEntry = entry != null,
                    comments = entry?.comments,
                    isToday = date == today,
                    onClick = { onDayClick(date) }
                )
                Spacer(Modifier.height(8.dp))
            }

            // History via ⋮ only (1.3.34 declutter)
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
                                    ClockOutResult.SUCCESS_OVERNIGHT ->
                                        ClockHaptics.performSuccess(view)
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
                                ClockOutResult.SUCCESS_OVERNIGHT ->
                                    ClockHaptics.performSuccess(view)
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

@Composable
private fun DayRow(
    date: LocalDate,
    hours: Double?,
    clockLabel: String?,
    hasEntry: Boolean,
    comments: String?,
    isToday: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (isToday) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = if (isToday) 1.5.dp else 1.dp,
            color = if (isToday) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()) +
                        if (isToday) "  •  Today" else "",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                    color = if (isToday) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface
                )
                if (clockLabel != null) {
                    Text(
                        clockLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isToday) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                if (!comments.isNullOrBlank()) {
                    Text(
                        comments,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isToday) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            val dayName = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
            val actionLabel = when {
                // Include 0.00h closed days (equal in/out zero day, 1.3.33)
                hours != null -> formatHours(hours)
                hasEntry -> "Edit"
                else -> "Add"
            }
            val actionCd = when {
                hours != null ->
                    "${formatHours(hours)} hours, edit $dayName"
                hasEntry -> "Edit $dayName"
                else -> "Add hours for $dayName"
            }
            TextButton(
                onClick = onClick,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = actionCd }
            ) {
                Text(
                    actionLabel,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

fun formatHours(hours: Double): String = HoursCalc.formatHours(hours)
