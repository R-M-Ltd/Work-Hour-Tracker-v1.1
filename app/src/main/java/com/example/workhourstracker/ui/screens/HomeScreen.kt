package com.example.workhourstracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.workhourstracker.util.HoursCalc
import com.example.workhourstracker.viewmodel.WorkHoursViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: WorkHoursViewModel,
    onDayClick: (LocalDate) -> Unit,
    onViewLog: () -> Unit
) {
    val entries by viewModel.currentWeekEntries.collectAsState()
    val weekStart by viewModel.weekStart.collectAsState()
    val daysInWeek = viewModel.daysInWeek(weekStart)
    val total = viewModel.runningTotal(entries)
    val today = LocalDate.now()

    Scaffold(
        topBar = { TopAppBar(title = { Text("This Week") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "${weekStart.format(DateTimeFormatter.ofPattern("MMM d"))} – " +
                            daysInWeek.last().format(DateTimeFormatter.ofPattern("MMM d")),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Total so far: ${formatHours(total)}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(daysInWeek) { date ->
                    val entry = viewModel.entryFor(date, entries)
                    DayRow(
                        date = date,
                        hours = entry?.hoursWorked,
                        clockLabel = HoursCalc.formatDayLabel(
                            entry?.clockInMinutes,
                            entry?.clockOutMinutes,
                            entry?.lunchOutMinutes,
                            entry?.lunchInMinutes
                        ),
                        comments = entry?.comments,
                        isToday = date == today,
                        onClick = { onDayClick(date) }
                    )
                    Divider()
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
            Text(if (hours != null) formatHours(hours) else "Add")
        }
    }
}

fun formatHours(hours: Double): String = HoursCalc.formatHours(hours)
