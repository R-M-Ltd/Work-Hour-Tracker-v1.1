package com.rmltd.workhourstracker.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rmltd.workhourstracker.data.DailyEntry
import com.rmltd.workhourstracker.data.WeekLog
import com.rmltd.workhourstracker.util.CsvExporter
import com.rmltd.workhourstracker.util.HoursCalc
import com.rmltd.workhourstracker.viewmodel.WorkHoursViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(
    viewModel: WorkHoursViewModel,
    onBack: () -> Unit,
    onEditDay: (LocalDate) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val weekLogs by viewModel.weekLogs.collectAsState()
    val allTimeTotal by viewModel.allTimeTotal.collectAsState()
    var exporting by remember { mutableStateOf(false) }
    var showAddMissed by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddMissed = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add missed punch")
                    }
                    IconButton(
                        onClick = {
                            if (exporting) return@IconButton
                            exporting = true
                            scope.launch {
                                try {
                                    val weeks = viewModel.loadExportWeeks()
                                    val rowCount = weeks.sumOf { it.second.size }
                                    if (rowCount == 0) {
                                        Toast.makeText(
                                            context,
                                            "Nothing to export yet",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        val csv = CsvExporter.buildCsv(weeks)
                                        val intent = CsvExporter.shareCsv(context, csv)
                                        context.startActivity(
                                            Intent.createChooser(intent, "Export work hours CSV")
                                        )
                                        Toast.makeText(
                                            context,
                                            "Export includes archived weeks + current week ($rowCount days)",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(
                                        context,
                                        "Export failed: ${e.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } finally {
                                    exporting = false
                                }
                            }
                        },
                        enabled = !exporting
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = "Export CSV")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        "Total hours to date",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        formatHours(allTimeTotal),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        "Sum of all logged days (including this week). " +
                            "Archived weeks with hours are listed below; empty weeks are hidden. " +
                            "Tap a day to edit times or add a missed punch (+).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                }
            }

            OutlinedButton(
                onClick = { showAddMissed = true },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add missed punch")
            }
            Spacer(Modifier.height(8.dp))

            if (weekLogs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No completed weeks with hours yet.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(weekLogs) { log ->
                        WeekLogRow(
                            log = log,
                            viewModel = viewModel,
                            onEditDay = onEditDay
                        )
                    }
                }
            }
        }
    }

    if (showAddMissed) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showAddMissed = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val millis = datePickerState.selectedDateMillis
                        if (millis != null) {
                            // DatePicker uses UTC millis; convert via epoch day of local midnight UTC.
                            val epochDay = millis / (24L * 60L * 60L * 1000L)
                            onEditDay(LocalDate.ofEpochDay(epochDay))
                        }
                        showAddMissed = false
                    }
                ) { Text("Edit day") }
            },
            dismissButton = {
                TextButton(onClick = { showAddMissed = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun WeekLogRow(
    log: WeekLog,
    viewModel: WorkHoursViewModel,
    onEditDay: (LocalDate) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var dayEntries by remember { mutableStateOf<List<DailyEntry>>(emptyList()) }

    LaunchedEffect(expanded) {
        if (expanded) {
            dayEntries = viewModel.loadEntriesForWeek(LocalDate.ofEpochDay(log.weekStartEpochDay))
        }
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onEditDay(LocalDate.ofEpochDay(entry.dateEpochDay))
                            }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                LocalDate.ofEpochDay(entry.dateEpochDay)
                                    .format(DateTimeFormatter.ofPattern("EEE, MMM d"))
                            )
                            HoursCalc.formatDayLabel(
                                entry.clockInMinutes,
                                entry.clockOutMinutes,
                                entry.lunchOutMinutes,
                                entry.lunchInMinutes,
                                entry.breakDurationMinutes
                            )?.let { range ->
                                Text(range, style = MaterialTheme.typography.bodySmall)
                            }
                            if (entry.comments.isNotBlank()) {
                                Text(entry.comments, style = MaterialTheme.typography.bodySmall)
                            }
                            Text(
                                "Tap to edit times",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(formatHours(entry.hoursWorked))
                    }
                }
            }
        }
    }
}
