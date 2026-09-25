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
import com.rmltd.workhourstracker.util.PayEstimate
import com.rmltd.workhourstracker.util.HoursCalc
import com.rmltd.workhourstracker.util.WeekUtils
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
    val hourlyRate by viewModel.hourlyRate.collectAsState()
    val currentWeekStart by viewModel.weekStart.collectAsState()
    val allTimePayEstimate = PayEstimate.roughPay(allTimeTotal, hourlyRate)
    var exporting by remember { mutableStateOf(false) }
    var showAddMissed by remember { mutableStateOf(false) }
    var showExportMenu by remember { mutableStateOf(false) }
    var showRangeExport by remember { mutableStateOf(false) }
    var noteEditEntry by remember { mutableStateOf<DailyEntry?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var searchMatches by remember { mutableStateOf<List<DailyEntry>>(emptyList()) }

    LaunchedEffect(searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) {
            searchMatches = emptyList()
        } else {
            val all = viewModel.loadAllEntries()
            val lower = q.lowercase()
            searchMatches = all.filter { entry ->
                entry.comments.lowercase().contains(lower) ||
                    LocalDate.ofEpochDay(entry.dateEpochDay).toString().contains(lower)
            }.sortedByDescending { it.dateEpochDay }
        }
    }

    fun runExport(start: LocalDate?, end: LocalDate?, label: String) {
        if (exporting) return
        exporting = true
        scope.launch {
            try {
                val weeks = viewModel.loadExportWeeks()
                val filtered = CsvExporter.filterByDateRange(weeks, start, end)
                val rowCount = filtered.sumOf { it.second.size }
                if (rowCount == 0) {
                    Toast.makeText(context, "Nothing to export for $label", Toast.LENGTH_SHORT).show()
                } else {
                    val csv = CsvExporter.buildCsv(filtered)
                    val fileName = when {
                        start != null && end != null ->
                            "work_hours_${start}_${end}.csv"
                        start != null -> "work_hours_from_${start}.csv"
                        else -> "work_hours_export.csv"
                    }
                    val intent = CsvExporter.shareCsv(context, csv, fileName)
                    context.startActivity(
                        Intent.createChooser(intent, "Export work hours CSV")
                    )
                    Toast.makeText(
                        context,
                        "Exported $label ($rowCount days)",
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
    }

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
                    Box {
                        IconButton(
                            onClick = { showExportMenu = true },
                            enabled = !exporting
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = "Export CSV")
                        }
                        DropdownMenu(
                            expanded = showExportMenu,
                            onDismissRequest = { showExportMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("This week") },
                                onClick = {
                                    showExportMenu = false
                                    val end = WeekUtils.weekEndFor(currentWeekStart)
                                    runExport(currentWeekStart, end, "this week")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Date range…") },
                                onClick = {
                                    showExportMenu = false
                                    showRangeExport = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("All time") },
                                onClick = {
                                    showExportMenu = false
                                    runExport(null, null, "all time")
                                }
                            )
                        }
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
                    allTimePayEstimate?.let { pay ->
                        Text(
                            "Est. ${PayEstimate.formatCurrencyUsd(pay)} (not payroll)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                        )
                    }
                    Text(
                        "Sum of all logged days (including this week). " +
                            "Archived weeks with hours are listed below; empty weeks are hidden. " +
                            "Tap a day to edit times, or edit its note. Share exports CSV for a week or range.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search notes") },
                placeholder = { Text("Filter by note text or date") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = { showAddMissed = true },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add missed punch")
            }
            Spacer(Modifier.height(8.dp))

            if (searchQuery.trim().isNotEmpty()) {
                if (searchMatches.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No notes match “${searchQuery.trim()}”.")
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(searchMatches, key = { it.dateEpochDay }) { entry ->
                            SearchResultRow(
                                entry = entry,
                                onEditDay = onEditDay,
                                onEditNote = { noteEditEntry = entry }
                            )
                        }
                    }
                }
            } else if (weekLogs.isEmpty()) {
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
                            onEditDay = onEditDay,
                            onEditNote = { noteEditEntry = it }
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

    if (showRangeExport) {
        ExportRangeDialog(
            initialStart = currentWeekStart,
            initialEnd = WeekUtils.weekEndFor(currentWeekStart),
            onDismiss = { showRangeExport = false },
            onConfirm = { start, end ->
                showRangeExport = false
                if (end.isBefore(start)) {
                    Toast.makeText(context, "End date must be on or after start", Toast.LENGTH_SHORT).show()
                } else {
                    runExport(start, end, "$start → $end")
                }
            }
        )
    }

    noteEditEntry?.let { entry ->
        NoteEditDialog(
            entry = entry,
            onDismiss = { noteEditEntry = null },
            onSave = { text ->
                val date = LocalDate.ofEpochDay(entry.dateEpochDay)
                viewModel.updateEntryComments(date, text) { ok ->
                    Toast.makeText(
                        context,
                        if (ok) "Note saved" else "No entry for that day",
                        Toast.LENGTH_SHORT
                    ).show()
                    noteEditEntry = null
                    // Refresh search matches if active
                    if (searchQuery.trim().isNotEmpty()) {
                        scope.launch {
                            val all = viewModel.loadAllEntries()
                            val lower = searchQuery.trim().lowercase()
                            searchMatches = all.filter {
                                it.comments.lowercase().contains(lower) ||
                                    LocalDate.ofEpochDay(it.dateEpochDay).toString().contains(lower)
                            }.sortedByDescending { it.dateEpochDay }
                        }
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportRangeDialog(
    initialStart: LocalDate,
    initialEnd: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, LocalDate) -> Unit
) {
    var start by remember { mutableStateOf(initialStart) }
    var end by remember { mutableStateOf(initialEnd) }
    var pickingStart by remember { mutableStateOf(false) }
    var pickingEnd by remember { mutableStateOf(false) }
    val fmt = DateTimeFormatter.ISO_LOCAL_DATE

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export date range") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "CSV via the system share sheet. Inclusive start and end.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedButton(
                    onClick = { pickingStart = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Start: ${start.format(fmt)}")
                }
                OutlinedButton(
                    onClick = { pickingEnd = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("End: ${end.format(fmt)}")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(start, end) }) { Text("Export") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (pickingStart) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = start.toEpochDay() * 24L * 60L * 60L * 1000L
        )
        DatePickerDialog(
            onDismissRequest = { pickingStart = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        start = LocalDate.ofEpochDay(it / (24L * 60L * 60L * 1000L))
                    }
                    pickingStart = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { pickingStart = false }) { Text("Cancel") }
            }
        ) { DatePicker(state = state) }
    }
    if (pickingEnd) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = end.toEpochDay() * 24L * 60L * 60L * 1000L
        )
        DatePickerDialog(
            onDismissRequest = { pickingEnd = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        end = LocalDate.ofEpochDay(it / (24L * 60L * 60L * 1000L))
                    }
                    pickingEnd = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { pickingEnd = false }) { Text("Cancel") }
            }
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun NoteEditDialog(
    entry: DailyEntry,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by remember(entry.dateEpochDay) { mutableStateOf(entry.comments) }
    val dateLabel = LocalDate.ofEpochDay(entry.dateEpochDay)
        .format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy"))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Note — $dateLabel") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= 500) text = it },
                label = { Text("Note") },
                supportingText = { Text("${text.length}/500") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp)
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text.trim()) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun SearchResultRow(
    entry: DailyEntry,
    onEditDay: (LocalDate) -> Unit,
    onEditNote: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onEditDay(LocalDate.ofEpochDay(entry.dateEpochDay)) }
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    LocalDate.ofEpochDay(entry.dateEpochDay)
                        .format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")),
                    fontWeight = FontWeight.Medium
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
                TextButton(onClick = onEditNote) { Text("Edit note") }
            }
            Text(formatHours(entry.hoursWorked))
        }
    }
}

@Composable
private fun WeekLogRow(
    log: WeekLog,
    viewModel: WorkHoursViewModel,
    onEditDay: (LocalDate) -> Unit,
    onEditNote: (DailyEntry) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var dayEntries by remember { mutableStateOf<List<DailyEntry>>(emptyList()) }

    LaunchedEffect(expanded, log.weekStartEpochDay) {
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
                                Text(
                                    "Note: ${entry.comments}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Row {
                                Text(
                                    "Tap to edit times",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clickable {
                                            onEditDay(LocalDate.ofEpochDay(entry.dateEpochDay))
                                        }
                                        .padding(end = 12.dp, top = 2.dp, bottom = 2.dp)
                                )
                                Text(
                                    if (entry.comments.isBlank()) "Add note" else "Edit note",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier
                                        .clickable { onEditNote(entry) }
                                        .padding(top = 2.dp, bottom = 2.dp)
                                )
                            }
                        }
                        Text(formatHours(entry.hoursWorked))
                    }
                }
            }
        }
    }
}
