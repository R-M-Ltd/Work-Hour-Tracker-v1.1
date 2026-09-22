package com.rmltd.workhourstracker.ui.screens

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import com.rmltd.workhourstracker.data.SaveEntryResult
import com.rmltd.workhourstracker.util.EntryFormSeed
import com.rmltd.workhourstracker.util.HoursCalc
import com.rmltd.workhourstracker.util.VoiceShiftParser
import com.rmltd.workhourstracker.util.extractClockMinutes
import com.rmltd.workhourstracker.viewmodel.WorkHoursViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class ClockField { IN, LUNCH_OUT, LUNCH_IN, OUT }

/** null = whole-shift voice mode; otherwise per-field mic. */
private sealed class VoiceMode {
    data object WholeShift : VoiceMode()
    data class Field(val field: ClockField) : VoiceMode()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryScreen(
    date: LocalDate,
    viewModel: WorkHoursViewModel,
    onDone: () -> Unit,
    onEditOpenDay: (LocalDate) -> Unit = {}
) {
    val context = LocalContext.current
    val clockBusy by viewModel.clockOpInProgress.collectAsState()

    // Date-scoped once-load (not currentWeekEntries) so overnight "Edit yesterday"
    // across a week boundary still shows Room clocks instead of a blank form.
    var clockInMinutes by remember(date) { mutableStateOf<Int?>(null) }
    var clockOutMinutes by remember(date) { mutableStateOf<Int?>(null) }
    var lunchOutMinutes by remember(date) { mutableStateOf<Int?>(null) }
    var lunchInMinutes by remember(date) { mutableStateOf<Int?>(null) }
    var comments by remember(date) { mutableStateOf("") }
    var entryLoadDone by remember(date) { mutableStateOf(false) }
    var pickerField by remember { mutableStateOf<ClockField?>(null) }

    LaunchedEffect(date) {
        entryLoadDone = false
        val loaded = viewModel.loadEntryForDate(date)
        val seed = if (loaded != null) {
            EntryFormSeed.fromLoaded(
                loaded.clockInMinutes,
                loaded.clockOutMinutes,
                loaded.lunchOutMinutes,
                loaded.lunchInMinutes,
                loaded.comments
            )
        } else {
            EntryFormSeed.empty()
        }
        clockInMinutes = seed.clockInMinutes
        clockOutMinutes = seed.clockOutMinutes
        lunchOutMinutes = seed.lunchOutMinutes
        lunchInMinutes = seed.lunchInMinutes
        comments = seed.comments
        entryLoadDone = true
    }
    /** Mode for the in-flight speech request; set at launch, read in the result callback (L3). */
    var pendingVoiceMode by remember { mutableStateOf<VoiceMode?>(null) }
    var showOvernightConfirm by remember { mutableStateOf(false) }
    var showBlockedOvernight by remember { mutableStateOf(false) }
    var openOvernightDate by remember { mutableStateOf<LocalDate?>(null) }

    val worked = remember(clockInMinutes, clockOutMinutes, lunchOutMinutes, lunchInMinutes) {
        if (clockInMinutes != null && clockOutMinutes != null && clockInMinutes != clockOutMinutes) {
            HoursCalc.worked(clockInMinutes!!, clockOutMinutes!!, lunchOutMinutes, lunchInMinutes)
        } else {
            null
        }
    }

    fun performSave(forceDiscard: Boolean = false) {
        val start = clockInMinutes ?: return
        val end = clockOutMinutes ?: return
        if (forceDiscard) {
            viewModel.discardOpenAndSaveEntry(
                date = date,
                clockInMinutes = start,
                clockOutMinutes = end,
                comments = comments,
                lunchOutMinutes = lunchOutMinutes,
                lunchInMinutes = lunchInMinutes,
                onDone = onDone
            )
        } else {
            viewModel.saveEntry(
                date = date,
                clockInMinutes = start,
                clockOutMinutes = end,
                comments = comments,
                lunchOutMinutes = lunchOutMinutes,
                lunchInMinutes = lunchInMinutes
            ) { result ->
                when (result) {
                    is SaveEntryResult.Saved -> onDone()
                    is SaveEntryResult.BlockedOvernightOpen -> {
                        openOvernightDate = result.openDate
                        showBlockedOvernight = true
                    }
                }
            }
        }
    }

    fun trySave() {
        val start = clockInMinutes
        val end = clockOutMinutes
        if (start == null || end == null) {
            Toast.makeText(context, "Set clock in and clock out", Toast.LENGTH_SHORT).show()
        } else if (start == end) {
            Toast.makeText(context, "Clock out must be a different time than clock in", Toast.LENGTH_SHORT).show()
        } else if (HoursCalc.isOvernight(start, end)) {
            showOvernightConfirm = true
        } else {
            performSave()
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        // Capture mode from the request that started recognition, not mid-flight UI flips (L3).
        val mode = pendingVoiceMode
        pendingVoiceMode = null
        if (spoken == null || mode == null) return@rememberLauncherForActivityResult

        when (mode) {
            is VoiceMode.WholeShift -> {
                val parsed = VoiceShiftParser.parse(spoken)
                if (!parsed.hasAny) {
                    Toast.makeText(
                        context,
                        "Didn't catch shift times — heard: \"$spoken\"",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    if (parsed.clockIn != null) clockInMinutes = parsed.clockIn
                    if (parsed.clockOut != null) clockOutMinutes = parsed.clockOut
                    if (parsed.lunchOut != null) lunchOutMinutes = parsed.lunchOut
                    if (parsed.lunchIn != null) lunchInMinutes = parsed.lunchIn
                    val missing = buildList {
                        if (parsed.clockIn == null) add("clock in")
                        if (parsed.clockOut == null) add("clock out")
                    }
                    val lunchPartial = (parsed.lunchOut != null) xor (parsed.lunchIn != null)
                    val msg = when {
                        missing.isEmpty() && !lunchPartial ->
                            "Filled from voice: ${HoursCalc.formatClock(parsed.clockIn!!)}" +
                                (if (parsed.lunchOut != null && parsed.lunchIn != null)
                                    ", lunch ${HoursCalc.formatClock(parsed.lunchOut)}–${HoursCalc.formatClock(parsed.lunchIn)}"
                                else "") +
                                " → ${HoursCalc.formatClock(parsed.clockOut!!)}"
                        else -> {
                            val bits = mutableListOf<String>()
                            if (missing.isNotEmpty()) bits.add("missing ${missing.joinToString(" & ")}")
                            if (lunchPartial) bits.add("lunch incomplete")
                            "Partial parse (${bits.joinToString("; ")}). Heard: \"$spoken\""
                        }
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            }
            is VoiceMode.Field -> {
                val parsed = extractClockMinutes(spoken)
                if (parsed != null) {
                    when (mode.field) {
                        ClockField.IN -> clockInMinutes = parsed
                        ClockField.LUNCH_OUT -> lunchOutMinutes = parsed
                        ClockField.LUNCH_IN -> lunchInMinutes = parsed
                        ClockField.OUT -> {
                            val cin = clockInMinutes
                            clockOutMinutes = if (cin != null) {
                                VoiceShiftParser.resolveOutAgainstIn(cin, parsed)
                            } else {
                                parsed
                            }
                        }
                    }
                } else {
                    Toast.makeText(context, "Didn't catch a time — heard: \"$spoken\"", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val mode = pendingVoiceMode ?: VoiceMode.WholeShift
            val prompt = when (mode) {
                is VoiceMode.WholeShift ->
                    "Describe your shift, like clocked in at 7:30, lunch 12 to 12:30, out at 4"
                is VoiceMode.Field -> "Say a time, like 7:30 AM"
            }
            startSpeechRecognition(context, speechLauncher, prompt)
        } else {
            pendingVoiceMode = null
            Toast.makeText(context, "Microphone permission denied — pick the time instead.", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchVoice(mode: VoiceMode) {
        // Freeze mode for this request so a second mic tap cannot flip the callback target (L3).
        pendingVoiceMode = mode
        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
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
            OutlinedButton(
                onClick = { launchVoice(VoiceMode.WholeShift) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Mic, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Speak whole shift")
            }
            Text(
                "One utterance can fill clock in, lunch, and clock out. Per-field mics still work below.",
                style = MaterialTheme.typography.bodySmall
            )

            ClockTimeRow(
                label = "Clock in",
                minutes = clockInMinutes,
                onPick = { pickerField = ClockField.IN },
                onSpeak = { launchVoice(VoiceMode.Field(ClockField.IN)) }
            )
            ClockTimeRow(
                label = "Lunch start",
                minutes = lunchOutMinutes,
                optional = true,
                onPick = { pickerField = ClockField.LUNCH_OUT },
                onClear = { lunchOutMinutes = null },
                onSpeak = { launchVoice(VoiceMode.Field(ClockField.LUNCH_OUT)) }
            )
            ClockTimeRow(
                label = "Lunch end",
                minutes = lunchInMinutes,
                optional = true,
                onPick = { pickerField = ClockField.LUNCH_IN },
                onClear = { lunchInMinutes = null },
                onSpeak = { launchVoice(VoiceMode.Field(ClockField.LUNCH_IN)) }
            )
            ClockTimeRow(
                label = "Clock out",
                minutes = clockOutMinutes,
                onPick = { pickerField = ClockField.OUT },
                onSpeak = { launchVoice(VoiceMode.Field(ClockField.OUT)) }
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
                onClick = { trySave() },
                enabled = entryLoadDone &&
                    !clockBusy &&
                    clockInMinutes != null &&
                    clockOutMinutes != null &&
                    clockInMinutes != clockOutMinutes,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (clockBusy) "Saving…" else "Save")
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

    if (showOvernightConfirm) {
        val hoursLabel = worked?.let { HoursCalc.formatHours(it.hours) } ?: "~?"
        AlertDialog(
            onDismissRequest = { showOvernightConfirm = false },
            title = { Text("Overnight shift?") },
            text = {
                Text("Clock out is earlier than clock in. Treat as overnight ($hoursLabel)?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showOvernightConfirm = false
                        performSave()
                    }
                ) { Text("Save as overnight") }
            },
            dismissButton = {
                TextButton(onClick = { showOvernightConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showBlockedOvernight) {
        val openDay = openOvernightDate ?: date.minusDays(1)
        AlertDialog(
            onDismissRequest = { showBlockedOvernight = false },
            title = { Text("Open overnight punch") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Another day still has an open clock-in with no clock-out. " +
                            "Finish that day first, or discard the open punch to save this day."
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            showBlockedOvernight = false
                            onEditOpenDay(openDay)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Edit open day") }
                    TextButton(
                        onClick = {
                            showBlockedOvernight = false
                            performSave(forceDiscard = true)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Discard open punch & save") }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBlockedOvernight = false }) { Text("Cancel") }
            }
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

private fun startSpeechRecognition(
    context: Context,
    launcher: ManagedActivityResultLauncher<Intent, ActivityResult>,
    prompt: String
) {
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
    }
    val resolve = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
    if (resolve == null) {
        Toast.makeText(context, "Speech recognition not available on this device", Toast.LENGTH_LONG).show()
        return
    }
    try {
        launcher.launch(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Speech recognition not available on this device", Toast.LENGTH_LONG).show()
    }
}
