package com.rmltd.workhourstracker.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import com.rmltd.workhourstracker.data.UpdateOpenClockInResult
import com.rmltd.workhourstracker.data.SaveEntryResult
import com.rmltd.workhourstracker.util.ClockHaptics
import com.rmltd.workhourstracker.util.HomeManualTimes
import com.rmltd.workhourstracker.util.HomeOpenPunch
import com.rmltd.workhourstracker.util.ZeroTimeNote
import com.rmltd.workhourstracker.util.HomeOvernightCopy
import com.rmltd.workhourstracker.util.PayEstimate
import com.rmltd.workhourstracker.util.HoursCalc
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

    val todayEntry = viewModel.entryFor(today, entries)
    // Key IN only on date + Room IN (not OUT) so open-punch IN persist does not
    // wipe a locally picked OUT (S-A). Key OUT only on date + Room OUT.
    var homeInMinutes by remember(
        todayEntry?.dateEpochDay,
        todayEntry?.clockInMinutes
    ) {
        mutableStateOf(todayEntry?.clockInMinutes)
    }
    var homeOutMinutes by remember(
        todayEntry?.dateEpochDay,
        todayEntry?.clockOutMinutes
    ) {
        mutableStateOf(todayEntry?.clockOutMinutes)
    }
    // Stash note locally until Save / atomic punch write (LOCAL_ONLY + OUT midnight).
    var homeCommentsDraft by remember(
        todayEntry?.dateEpochDay,
        todayEntry?.comments
    ) {
        mutableStateOf(todayEntry?.comments.orEmpty())
    }

    fun performHomeManualSave(forceDiscard: Boolean = false) {
        val start = homeInMinutes ?: return
        val end = homeOutMinutes ?: return
        val (lunchOut, lunchIn) = HomeManualTimes.lunchToPreserve(
            todayEntry?.lunchOutMinutes,
            todayEntry?.lunchInMinutes
        )
        val comments = homeCommentsDraft
        if (forceDiscard) {
            viewModel.discardOpenAndSaveEntry(
                date = today,
                clockInMinutes = start,
                clockOutMinutes = end,
                comments = comments,
                lunchOutMinutes = lunchOut,
                lunchInMinutes = lunchIn,
                breakDurationMinutes = todayEntry?.breakDurationMinutes,
                breakPaid = todayEntry?.breakPaid ?: false
            ) {
                Toast.makeText(context, "Saved today's times", Toast.LENGTH_SHORT).show()
            }
        } else {
            viewModel.saveEntry(
                date = today,
                clockInMinutes = start,
                clockOutMinutes = end,
                comments = comments,
                lunchOutMinutes = lunchOut,
                lunchInMinutes = lunchIn,
                breakDurationMinutes = todayEntry?.breakDurationMinutes,
                breakPaid = todayEntry?.breakPaid ?: false
            ) { result ->
                when (result) {
                    is SaveEntryResult.Saved -> {
                        Toast.makeText(context, "Saved today's times", Toast.LENGTH_SHORT).show()
                    }
                    is SaveEntryResult.BlockedOvernightOpen -> {
                        manualOpenOvernightDate = result.openDate
                        showManualBlockedOvernight = true
                    }
                }
            }
        }
    }

    fun tryHomeManualSave() {
        val start = homeInMinutes
        val end = homeOutMinutes
        if (!HomeManualTimes.canSave(start, end)) {
            Toast.makeText(context, "Set clock in and clock out", Toast.LENGTH_SHORT).show()
            return
        }
        if (!ZeroTimeNote.canSaveWithNote(
                homeCommentsDraft,
                clockInMinutes = start,
                clockOutMinutes = end
            )
        ) {
            Toast.makeText(
                context,
                "Add a reason note for 12:00 AM (0) before saving",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val (lunchOut, lunchIn) = HomeManualTimes.lunchToPreserve(
            todayEntry?.lunchOutMinutes,
            todayEntry?.lunchInMinutes
        )
        val previewHours = HoursCalc.hoursWorked(
            start!!,
            end!!,
            lunchOut,
            lunchIn,
            breakDurationMinutes = todayEntry?.breakDurationMinutes,
            breakPaid = todayEntry?.breakPaid ?: false
        )
        if (ZeroTimeNote.needsZeroHoursReason(start, end, previewHours)) {
            zeroHoursReasonText = ""
            showZeroHoursDialog = true
            return
        }
        if (HomeManualTimes.needsOvernightConfirm(start, end)) {
            showManualOvernightConfirm = true
        } else {
            performHomeManualSave()
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

    fun onHomeTimePicked(field: HomeClockField, minutes: Int) {
        if (ZeroTimeNote.needsReason(minutes)) {
            pendingZeroField = field
            pendingZeroMinutes = minutes
            zeroReasonText = ""
            homePickerField = null
        } else {
            applyHomeClockMinutes(field, minutes)
            homePickerField = null
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("This Week") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                actions = {
                    IconButton(onClick = onViewLog) {
                        Icon(Icons.Filled.History, contentDescription = "History")
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
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
                            val todayOpenChip = todayEntry?.clockInMinutes != null &&
                                todayEntry?.clockOutMinutes == null &&
                                !homeClock.overnightPending
                            if (todayOpenChip) {
                                val inLabel = HoursCalc.formatClock(todayEntry!!.clockInMinutes!!)
                                Spacer(Modifier.height(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .semantics {
                                            contentDescription = "Clocked in at $inLabel"
                                        }
                                ) {
                                    Text(
                                        "Clocked in · $inLabel",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
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
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if (homeClock.overnightPending) {
                                            showOvernightDialog = true
                                            return@Button
                                        }
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
                                    },
                                    enabled = homeClock.clockInEnabled && !clockBusy,
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 56.dp)
                                        .semantics { contentDescription = "Clock in now" }
                                ) {
                                    Text(
                                        "Clock in now",
                                        maxLines = 2,
                                        softWrap = true
                                    )
                                }
                                FilledTonalButton(
                                    onClick = {
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
                                    },
                                    enabled = homeClock.clockOutEnabled && !clockBusy,
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 56.dp)
                                        .semantics { contentDescription = "Clock out now" }
                                ) {
                                    Text(
                                        "Clock out now",
                                        maxLines = 2,
                                        softWrap = true
                                    )
                                }
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
                            if (homeClock.clockOutEnabled) {
                                TextButton(
                                    onClick = { showForgotClockOut = true },
                                    enabled = !clockBusy,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .semantics { contentDescription = "Forgot to clock out" }
                                ) {
                                    Text(
                                        "Forgot to clock out…",
                                        maxLines = 2,
                                        softWrap = true
                                    )
                                }
                            }
                            // 1.3.22: navigate to Entry(today) only — does not punch lunch/break.
                            FilledTonalButton(
                                onClick = onLogLunch,
                                enabled = !clockBusy,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp)
                                    .heightIn(min = 56.dp)
                                    .semantics {
                                        contentDescription = "Open Entry to log lunch or break"
                                    }
                            ) {
                                Text(
                                    "Log lunch / break…",
                                    maxLines = 2,
                                    softWrap = true
                                )
                            }
                            Text(
                                "Breaks stay on Entry — unpaid by default.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                            Text(
                                "Or set today's times",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(8.dp))
                            HomeClockTimeRow(
                                label = "Clock in",
                                minutes = homeInMinutes,
                                enabled = !clockBusy,
                                onPick = {
                                    val action = HomeOpenPunch.decide(
                                        todayIn = todayEntry?.clockInMinutes,
                                        todayOut = todayEntry?.clockOutMinutes,
                                        todayHoursWorked = todayEntry?.hoursWorked ?: 0.0,
                                        overnightOrOrphanPending = homeClock.overnightPending
                                    )
                                    if (action == HomeOpenPunch.Action.SHOW_OVERNIGHT) {
                                        showOvernightDialog = true
                                    } else {
                                        homePickerField = HomeClockField.IN
                                    }
                                }
                            )
                            Spacer(Modifier.height(8.dp))
                            HomeClockTimeRow(
                                label = "Clock out",
                                minutes = homeOutMinutes,
                                enabled = !clockBusy,
                                onPick = { homePickerField = HomeClockField.OUT }
                            )
                            // D1: keep stashed draft visible after open-punch IN (no flash-empty).
                            if (homeCommentsDraft.isNotBlank()) {
                                Spacer(Modifier.height(10.dp))
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant
                                    ),
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .semantics { contentDescription = "Today's note draft" }
                                ) {
                                    Text(
                                        homeCommentsDraft,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                                // S-B: honesty when draft dirty vs Room, or draft present with
                                // times ready to Save but day not closed yet (OUT still local).
                                val roomComments = todayEntry?.comments.orEmpty()
                                val draftDirtyVsRoom =
                                    homeCommentsDraft.trim() != roomComments.trim()
                                val pendingClosedSave =
                                    HomeManualTimes.canSave(homeInMinutes, homeOutMinutes) &&
                                        todayEntry?.clockOutMinutes == null
                                if (draftDirtyVsRoom || pendingClosedSave) {
                                    Text(
                                        ZeroTimeNote.UNSAVED_DRAFT_CAPTION,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .padding(top = 6.dp)
                                            .semantics {
                                                contentDescription = ZeroTimeNote.UNSAVED_DRAFT_CAPTION
                                            }
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = { tryHomeManualSave() },
                                enabled = HomeManualTimes.canSave(homeInMinutes, homeOutMinutes) && !clockBusy,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .semantics { contentDescription = "Save today's times" }
                            ) {
                                Text(
                                    "Save today's times",
                                    maxLines = 2,
                                    softWrap = true
                                )
                            }
                            Text(
                                "Same save rules as Edit day (overnight guards). Existing break kept; edit day to change.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
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
                    hours = entry?.hoursWorked,
                    clockLabel = partialLabel,
                    hasEntry = entry != null,
                    comments = entry?.comments,
                    isToday = date == today,
                    onClick = { onDayClick(date) }
                )
                Spacer(Modifier.height(8.dp))
            }

            item {
                // Flush bottom: History sits just under day list (no weight filler / extra gap).
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onViewLog,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = "History" }
                ) {
                    Icon(Icons.Filled.History, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "History",
                        maxLines = 1,
                        softWrap = true
                    )
                }
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
            onDismiss = { homePickerField = null }
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
                        if (HomeManualTimes.needsOvernightConfirm(homeInMinutes!!, homeOutMinutes!!)) {
                            showManualOvernightConfirm = true
                        } else {
                            performHomeManualSave()
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
                        performHomeManualSave()
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
                            performHomeManualSave(forceDiscard = true)
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
private fun HomeClockTimeRow(
    label: String,
    minutes: Int?,
    enabled: Boolean = true,
    onPick: () -> Unit
) {
    val cd = when (label) {
        "Clock in" -> if (minutes == null) "Clock in time" else "Clock in time ${HoursCalc.formatClock(minutes)}"
        "Clock out" -> if (minutes == null) "Clock out time" else "Clock out time ${HoursCalc.formatClock(minutes)}"
        else -> label
    }
    OutlinedButton(
        onClick = onPick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics { contentDescription = cd }
    ) {
        Icon(Icons.Filled.Schedule, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(
            if (minutes == null) label else "$label  ${HoursCalc.formatClock(minutes)}",
            maxLines = 2,
            softWrap = true
        )
    }
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
                hours != null && hours > 0.0 -> formatHours(hours)
                hasEntry -> "Edit"
                else -> "Add"
            }
            val actionCd = when {
                hours != null && hours > 0.0 ->
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
