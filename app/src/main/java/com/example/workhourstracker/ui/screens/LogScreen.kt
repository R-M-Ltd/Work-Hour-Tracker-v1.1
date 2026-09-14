package com.example.workhourstracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.workhourstracker.data.DailyEntry
import com.example.workhourstracker.data.WeekLog
import com.example.workhourstracker.util.HoursCalc
import com.example.workhourstracker.viewmodel.WorkHoursViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(viewModel: WorkHoursViewModel, onBack: () -> Unit) {
    val weekLogs by viewModel.weekLogs.collectAsState()
    val allTimeTotal by viewModel.allTimeTotal.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Total hours to date", style = MaterialTheme.typography.titleMedium)
                    Text(
                        formatHours(allTimeTotal),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Archived weeks only — this week's running total is on the Home screen.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (weekLogs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No completed weeks yet.")
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(weekLogs) { log ->
                        WeekLogRow(log = log, viewModel = viewModel)
                        Divider()
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekLogRow(log: WeekLog, viewModel: WorkHoursViewModel) {
    var expanded by remember { mutableStateOf(false) }
    var dayEntries by remember { mutableStateOf<List<DailyEntry>>(emptyList()) }

    LaunchedEffect(expanded) {
        if (expanded && dayEntries.isEmpty()) {
            dayEntries = viewModel.loadEntriesForWeek(LocalDate.ofEpochDay(log.weekStartEpochDay))
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${LocalDate.ofEpochDay(log.weekStartEpochDay).format(DateTimeFormatter.ofPattern("MMM d"))} – " +
                    LocalDate.ofEpochDay(log.weekEndEpochDay).format(DateTimeFormatter.ofPattern("MMM d, yyyy")),
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { expanded = !expanded }) {
                Text(formatHours(log.totalHours))
            }
        }

        if (expanded) {
            Spacer(Modifier.height(8.dp))
            dayEntries.forEach { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(LocalDate.ofEpochDay(entry.dateEpochDay).format(DateTimeFormatter.ofPattern("EEE, MMM d")))
                        HoursCalc.formatDayLabel(
                            entry.clockInMinutes,
                            entry.clockOutMinutes,
                            entry.lunchOutMinutes,
                            entry.lunchInMinutes
                        )?.let { range ->
                            Text(range, style = MaterialTheme.typography.bodySmall)
                        }
                        if (entry.comments.isNotBlank()) {
                            Text(entry.comments, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Text(formatHours(entry.hoursWorked))
                }
            }
        }
    }
}
