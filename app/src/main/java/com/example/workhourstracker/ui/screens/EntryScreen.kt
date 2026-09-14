package com.example.workhourstracker.ui.screens

import android.Manifest
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.workhourstracker.util.HoursCalc
import com.example.workhourstracker.viewmodel.WorkHoursViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class ClockField { IN, LUNCH_OUT, LUNCH_IN, OUT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryScreen(
    date: LocalDate,
    viewModel: WorkHoursViewModel,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val entries by viewModel.currentWeekEntries.collectAsState()
    val existing = viewModel.entryFor(date, entries)

    var clockInMinutes by remember(existing) { mutableStateOf(existing?.clockInMinutes) }
    var clockOutMinutes by remember(existing) { mutableStateOf(existing?.clockOutMinutes) }
    var lunchOutMinutes by remember(existing) { mutableStateOf(existing?.lunchOutMinutes) }
    var lunchInMinutes by remember(existing) { mutableStateOf(existing?.lunchInMinutes) }
    var comments by remember(existing) { mutableStateOf(existing?.comments ?: "") }
    var pickerField by remember { mutableStateOf<ClockField?>(null) }
    var voiceTarget by remember { mutableStateOf(ClockField.IN) }

    val worked = remember(clockInMinutes, clockOutMinutes, lunchOutMinutes, lunchInMinutes) {
        if (clockInMinutes != null && clockOutMinutes != null && clockInMinutes != clockOutMinutes) {
            HoursCalc.worked(clockInMinutes!!, clockOutMinutes!!, lunchOutMinutes, lunchInMinutes)
        } else {
            null
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (spoken != null) {
            val parsed = extractClockMinutes(spoken)
            if (parsed != null) {
                when (voiceTarget) {
                    ClockField.IN -> clockInMinutes = parsed
                    ClockField.LUNCH_OUT -> lunchOutMinutes = parsed
                    ClockField.LUNCH_IN -> lunchInMinutes = parsed
                    ClockField.OUT -> clockOutMinutes = parsed
                }
            } else {
                Toast.makeText(context, "Didn't catch a time — heard: \"$spoken\"", Toast.LENGTH_LONG).show()
            }
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            startSpeechRecognition(speechLauncher)
        } else {
            Toast.makeText(context, "Microphone permission denied — pick the time instead.", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(date.format(DateTimeFormatter.ofPattern("EEEE, MMM d"))) })
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ClockTimeRow(
                label = "Clock in",
                minutes = clockInMinutes,
                onPick = { pickerField = ClockField.IN },
                onSpeak = {
                    voiceTarget = ClockField.IN
                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            )
            ClockTimeRow(
                label = "Lunch start",
                minutes = lunchOutMinutes,
                optional = true,
                onPick = { pickerField = ClockField.LUNCH_OUT },
                onClear = { lunchOutMinutes = null },
                onSpeak = {
                    voiceTarget = ClockField.LUNCH_OUT
                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            )
            ClockTimeRow(
                label = "Lunch end",
                minutes = lunchInMinutes,
                optional = true,
                onPick = { pickerField = ClockField.LUNCH_IN },
                onClear = { lunchInMinutes = null },
                onSpeak = {
                    voiceTarget = ClockField.LUNCH_IN
                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            )
            ClockTimeRow(
                label = "Clock out",
                minutes = clockOutMinutes,
                onPick = { pickerField = ClockField.OUT },
                onSpeak = {
                    voiceTarget = ClockField.OUT
                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Hours worked", style = MaterialTheme.typography.labelLarge)
                    Text(
                        worked?.let { HoursCalc.formatHours(it.hours) } ?: "—",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        when {
                            worked == null -> "Set clock in and clock out. Lunch is optional."
                            worked.lunchApplied && worked.overnight ->
                                "Overnight shift minus lunch. Rounded to hundredths."
                            worked.lunchApplied ->
                                "Clock times minus lunch. Hours are not editable."
                            worked.overnight ->
                                "Overnight shift. Lunch left blank, so it was not counted."
                            lunchOutMinutes != null || lunchInMinutes != null ->
                                "Lunch ignored until both start and end are set, and both sit inside the shift."
                            else -> "Calculated from clock in and clock out. Lunch left blank."
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            OutlinedTextField(
                value = comments,
                onValueChange = { comments = it },
                label = { Text("Comments") },
                modifier = Modifier.fillMaxWidth().height(140.dp)
            )

            Button(
                onClick = {
                    val start = clockInMinutes
                    val end = clockOutMinutes
                    if (start == null || end == null) {
                        Toast.makeText(context, "Set clock in and clock out", Toast.LENGTH_SHORT).show()
                    } else if (start == end) {
                        Toast.makeText(context, "Clock out must be a different time than clock in", Toast.LENGTH_SHORT).show()
                    } else {
                        viewModel.saveEntry(
                            date = date,
                            clockInMinutes = start,
                            clockOutMinutes = end,
                            comments = comments,
                            lunchOutMinutes = lunchOutMinutes,
                            lunchInMinutes = lunchInMinutes
                        )
                        onDone()
                    }
                },
                enabled = clockInMinutes != null && clockOutMinutes != null && clockInMinutes != clockOutMinutes,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save")
            }
        }
    }

    pickerField?.let { field ->
        ClockPickerDialog(
            field = field,
            currentMinutes = minutesFor(field, clockInMinutes, lunchOutMinutes, lunchInMinutes, clockOutMinutes),
            onConfirm = { minutes ->
                when (field) {
                    ClockField.IN -> clockInMinutes = minutes
                    ClockField.LUNCH_OUT -> lunchOutMinutes = minutes
                    ClockField.LUNCH_IN -> lunchInMinutes = minutes
                    ClockField.OUT -> clockOutMinutes = minutes
                }
                pickerField = null
            },
            onDismiss = { pickerField = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClockPickerDialog(
    field: ClockField,
    currentMinutes: Int?,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val initial = currentMinutes ?: defaultMinutesFor(field)
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
        title = { Text(labelFor(field)) },
        text = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = state)
            }
        }
    )
}

@Composable
private fun ClockTimeRow(
    label: String,
    minutes: Int?,
    optional: Boolean = false,
    onPick: () -> Unit,
    onClear: (() -> Unit)? = null,
    onSpeak: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = onPick,
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Filled.Schedule, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            val suffix = if (optional && minutes == null) " (optional)" else ""
            Text(if (minutes == null) "$label$suffix" else "$label  ${HoursCalc.formatClock(minutes)}")
        }
        if (optional && minutes != null && onClear != null) {
            IconButton(onClick = onClear) {
                Icon(Icons.Filled.Close, contentDescription = "Clear $label")
            }
        }
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onSpeak) {
            Icon(Icons.Filled.Mic, contentDescription = "Speak $label time")
        }
    }
}

private fun labelFor(field: ClockField): String = when (field) {
    ClockField.IN -> "Clock in"
    ClockField.LUNCH_OUT -> "Lunch start"
    ClockField.LUNCH_IN -> "Lunch end"
    ClockField.OUT -> "Clock out"
}

private fun defaultMinutesFor(field: ClockField): Int = when (field) {
    ClockField.IN -> 8 * 60
    ClockField.LUNCH_OUT -> 12 * 60
    ClockField.LUNCH_IN -> 12 * 60 + 30
    ClockField.OUT -> 17 * 60
}

private fun minutesFor(
    field: ClockField,
    clockIn: Int?,
    lunchOut: Int?,
    lunchIn: Int?,
    clockOut: Int?
): Int? = when (field) {
    ClockField.IN -> clockIn
    ClockField.LUNCH_OUT -> lunchOut
    ClockField.LUNCH_IN -> lunchIn
    ClockField.OUT -> clockOut
}

private fun startSpeechRecognition(launcher: ManagedActivityResultLauncher<Intent, ActivityResult>) {
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a time, like 7:30 AM")
    }
    launcher.launch(intent)
}

/** "7:30 am", "7 30 pm", "19:15", "730am" → minutes from midnight. */
fun extractClockMinutes(spoken: String): Int? {
    val s = spoken.lowercase(Locale.getDefault())
    // Word-boundary-before-a/p fails for attached suffixes ("730pm"); allow digit/space before a/p.
    val pm = Regex("(?:^|\\s|\\d)p\\.?m\\.?\\b").containsMatchIn(s)
    val am = Regex("(?:^|\\s|\\d)a\\.?m\\.?\\b").containsMatchIn(s)
    // Compact form must allow an attached am/pm suffix: "730am" has no word
    // boundary between the digits and "am", so a digits-only \b pattern misses it.
    val hm = Regex("(\\d{1,2})[:\\s](\\d{2})").find(s)
    val compact = Regex("\\b(\\d{1,2})(\\d{2})(?=\\s*(?:[ap]\\.?m\\.?)?\\b)").find(s)
    val hourOnly = Regex("\\b(\\d{1,2})\\b").find(s)
    val hour: Int
    val minute: Int
    when {
        hm != null -> {
            hour = hm.groupValues[1].toInt()
            minute = hm.groupValues[2].toInt()
        }
        compact != null -> {
            hour = compact.groupValues[1].toInt()
            minute = compact.groupValues[2].toInt()
        }
        hourOnly != null -> {
            hour = hourOnly.groupValues[1].toInt()
            minute = 0
        }
        else -> return null
    }
    if (minute !in 0..59) return null
    var h24 = hour
    when {
        pm && hour in 1..11 -> h24 = hour + 12
        am && hour == 12 -> h24 = 0
        hour == 12 && pm -> h24 = 12
        !am && !pm && hour in 0..23 -> h24 = hour
        am && hour in 1..11 -> h24 = hour
        hour !in 0..23 -> return null
    }
    if (h24 !in 0..23) return null
    return h24 * 60 + minute
}
