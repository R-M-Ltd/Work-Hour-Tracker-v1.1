package com.rmltd.workhourstracker.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rmltd.workhourstracker.data.ClockOutResult
import com.rmltd.workhourstracker.util.HoursCalc
import com.rmltd.workhourstracker.viewmodel.WorkHoursViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.max

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
    val daysInWeek = viewModel.daysInWeek(weekStart)
    val total = viewModel.runningTotal(entries)
    val today = LocalDate.now()
    val todayInThisWeek = daysInWeek.contains(today)
    val progress = if (weeklyGoal > 0.0) (total / weeklyGoal).toFloat().coerceIn(0f, 1f) else 0f
    val remaining = max(0.0, weeklyGoal - total)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("This Week") },
                actions = {
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
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(88.dp)
                    ) {
                        CircularProgressIndicator(
                            progress = progress,
                            modifier = Modifier.fillMaxSize(),
                            strokeWidth = 8.dp,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Text(
                            "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "${weekStart.format(DateTimeFormatter.ofPattern("MMM d"))} – " +
                                daysInWeek.last().format(DateTimeFormatter.ofPattern("MMM d")),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Total: ${formatHours(total)}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Goal ${formatHours(weeklyGoal)} · ${formatHours(remaining)} remaining",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            if (todayInThisWeek) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.clockInNow(today)
                            Toast.makeText(context, "Clocked in now", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Clock in now")
                    }
                    Button(
                        onClick = {
                            viewModel.clockOutNow(today) { result ->
                                val msg = when (result) {
                                    ClockOutResult.SUCCESS -> "Clocked out now"
                                    ClockOutResult.SUCCESS_OVERNIGHT ->
                                        "Finished yesterday's overnight shift"
                                    ClockOutResult.FAILED ->
                                        "Clock in first (or use a different time)"
                                }
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        // Allow overnight: today may have no clock-in while yesterday is still open
                        enabled = true,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Clock out now")
                    }
                }
                Text(
                    "Sets today's time to right now (or finishes yesterday's open overnight shift). Lunch is not added — edit the day for lunch.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(daysInWeek) { date ->
                    val entry = viewModel.entryFor(date, entries)
                    val fullLabel = HoursCalc.formatDayLabel(
                        entry?.clockInMinutes,
                        entry?.clockOutMinutes,
                        entry?.lunchOutMinutes,
                        entry?.lunchInMinutes
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
                    HorizontalDivider()
                }
            }

            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onViewLog, modifier = Modifier.fillMaxWidth()) {
                Text("View History & To-Date Hours")
            }
        }
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
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()) +
                    if (isToday) "  •  Today" else "",
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
            )
            if (clockLabel != null) {
                Text(clockLabel, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
            if (!comments.isNullOrBlank()) {
                Text(comments, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
        }
        TextButton(onClick = onClick) {
            Text(
                when {
                    hours != null && hours > 0.0 -> formatHours(hours)
                    hasEntry -> "Edit"
                    else -> "Add"
                }
            )
        }
    }
}

fun formatHours(hours: Double): String = HoursCalc.formatHours(hours)
