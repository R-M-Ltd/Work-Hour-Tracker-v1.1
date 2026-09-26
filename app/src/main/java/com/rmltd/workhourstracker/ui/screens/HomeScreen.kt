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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rmltd.workhourstracker.data.ClockInResult
import com.rmltd.workhourstracker.data.ClockOutResult
import com.rmltd.workhourstracker.data.SaveEntryResult
import com.rmltd.workhourstracker.util.HomeManualTimes
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
    onSettings: () -> Unit
) {
    val context = LocalContext.current
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

    val todayEntry = viewModel.entryFor(today, entries)
    var homeInMinutes by remember(
        todayEntry?.dateEpochDay,
        todayEntry?.clockInMinutes,
        todayEntry?.clockOutMinutes
    ) {
        mutableStateOf(todayEntry?.clockInMinutes)
    }
    var homeOutMinutes by remember(
        todayEntry?.dateEpochDay,
        todayEntry?.clockInMinutes,
        todayEntry?.clockOutMinutes
    ) {
        mutableStateOf(todayEntry?.clockOutMinutes)
    }

    fun performHomeManualSave(forceDiscard: Boolean = false) {
        val start = homeInMinutes ?: return
        val end = homeOutMinutes ?: return
        val (lunchOut, lunchIn) = HomeManualTimes.lunchToPreserve(
            todayEntry?.lunchOutMinutes,
            todayEntry?.lunchInMinutes
        )
        val comments = todayEntry?.comments.orEmpty()
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
        if (HomeManualTimes.needsOvernightConfirm(start!!, end!!)) {
            showManualOvernightConfirm = true
        } else {
            performHomeManualSave()
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
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
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

            if (todayInThisWeek) {
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
                                    viewModel.clockInNow(today) { result ->
                                        when (result) {
                                            ClockInResult.BLOCKED_OVERNIGHT -> {
                                                showOvernightDialog = true
                                            }
                                            else -> {
                                                val msg = when (result) {
                                                    ClockInResult.STARTED -> "Clocked in now"
                                                    ClockInResult.ALREADY_OPEN -> "Already clocked in"
                                                    ClockInResult.ALREADY_CLOSED ->
                                                        "Today already has hours — edit the day to change it"
                                                    ClockInResult.BLOCKED_OVERNIGHT ->
                                                        "Finish yesterday's shift first"
                                                }
                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                                enabled = homeClock.clockInEnabled && !clockBusy,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Clock in now")
                            }
                            FilledTonalButton(
                                onClick = {
                                    viewModel.clockOutNow(today) { result ->
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
                                        }
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                enabled = homeClock.clockOutEnabled && !clockBusy,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Clock out now")
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
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Forgot to clock out…")
                            }
                        }
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
                            onPick = { homePickerField = HomeClockField.IN }
                        )
                        Spacer(Modifier.height(8.dp))
                        HomeClockTimeRow(
                            label = "Clock out",
                            minutes = homeOutMinutes,
                            onPick = { homePickerField = HomeClockField.OUT }
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { tryHomeManualSave() },
                            enabled = HomeManualTimes.canSave(homeInMinutes, homeOutMinutes) && !clockBusy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Save today's times")
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

            Spacer(Modifier.height(16.dp))

            LazyColumn(modifier = Modifier.weight(1f)) {
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
            }

            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onViewLog, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.History, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("History")
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
                            showOvernightDialog = false
                            viewModel.clockOutNow(today) { result ->
                                val msg = when (result) {
                                    ClockOutResult.SUCCESS_OVERNIGHT ->
                                        "Finished open overnight shift"
                                    ClockOutResult.SUCCESS -> "Clocked out now"
                                    ClockOutResult.FAILED ->
                                        "Could not finish open shift — try editing $dayLabel"
                                    ClockOutResult.ALREADY_CLOSED ->
                                        "Today is already clocked out"
                                }
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
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
                                }
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
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
            onConfirm = { minutes ->
                when (field) {
                    HomeClockField.IN -> homeInMinutes = minutes
                    HomeClockField.OUT -> homeOutMinutes = minutes
                }
                homePickerField = null
            },
            onDismiss = { homePickerField = null }
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
                        val minutes = state.hour * 60 + state.minute
                        showForgotClockOut = false
                        viewModel.clockOutAt(today, minutes) { result ->
                            val msg = when (result) {
                                ClockOutResult.SUCCESS ->
                                    "Clocked out at ${HoursCalc.formatClock(minutes)}"
                                ClockOutResult.SUCCESS_OVERNIGHT ->
                                    "Finished overnight at ${HoursCalc.formatClock(minutes)}"
                                ClockOutResult.FAILED ->
                                    "Could not clock out — try editing the day"
                                ClockOutResult.ALREADY_CLOSED ->
                                    "Already clocked out — edit the day to change it"
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
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
    onPick: () -> Unit
) {
    OutlinedButton(
        onClick = onPick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Filled.Schedule, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(
            if (minutes == null) label else "$label  ${HoursCalc.formatClock(minutes)}"
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
            TextButton(onClick = onClick) {
                Text(
                    when {
                        hours != null && hours > 0.0 -> formatHours(hours)
                        hasEntry -> "Edit"
                        else -> "Add"
                    },
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

fun formatHours(hours: Double): String = HoursCalc.formatHours(hours)
