package com.rmltd.workhourstracker.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.rmltd.workhourstracker.data.ReminderPreferences
import com.rmltd.workhourstracker.util.HoursCalc
import com.rmltd.workhourstracker.util.WeekUtils
import com.rmltd.workhourstracker.viewmodel.WorkHoursViewModel
import com.rmltd.workhourstracker.worker.ReminderScheduler
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: WorkHoursViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(ReminderPreferences.isReminderEnabled(context)) }
    var reminderTime by remember { mutableStateOf(ReminderPreferences.getReminderTime(context)) }
    var showPicker by remember { mutableStateOf(false) }
    var weekStartDay by remember { mutableStateOf(ReminderPreferences.getWeekStartDay(context)) }
    var weekStartExpanded by remember { mutableStateOf(false) }
    var goalText by remember {
        mutableStateOf(
            "%.2f".format(Locale.US, ReminderPreferences.getWeeklyGoalHours(context))
        )
    }

    val weekEndDay = WeekUtils.weekEndDayName(weekStartDay)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Work week", style = MaterialTheme.typography.titleMedium)

            ExposedDropdownMenuBox(
                expanded = weekStartExpanded,
                onExpandedChange = { weekStartExpanded = !weekStartExpanded }
            ) {
                OutlinedTextField(
                    value = weekStartDay.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Week starts on") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = weekStartExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = weekStartExpanded,
                    onDismissRequest = { weekStartExpanded = false }
                ) {
                    DayOfWeek.entries.forEach { day ->
                        DropdownMenuItem(
                            text = {
                                Text(day.getDisplayName(TextStyle.FULL, Locale.getDefault()))
                            },
                            onClick = {
                                weekStartDay = day
                                weekStartExpanded = false
                                ReminderPreferences.setWeekStartDay(context, day)
                                ReminderScheduler.scheduleWeeklyReset(context)
                                viewModel.notifyPrefsChanged()
                                Toast.makeText(
                                    context,
                                    "Week starts on ${day.getDisplayName(TextStyle.FULL, Locale.getDefault())}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                    }
                }
            }

            Text(
                "Week runs ${weekStartDay.getDisplayName(TextStyle.FULL, Locale.getDefault())} → " +
                    "${weekEndDay.getDisplayName(TextStyle.FULL, Locale.getDefault())}. " +
                    "Archive/reset fires at 2:00 AM on the week-start day.",
                style = MaterialTheme.typography.bodySmall
            )

            HorizontalDivider()

            Text("Weekly goal", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = goalText,
                onValueChange = { goalText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                label = { Text("Hours per week") },
                supportingText = { Text("Shown as a progress ring on Home. Default 40.00.") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    val parsed = goalText.toDoubleOrNull()
                    if (parsed == null || parsed <= 0.0) {
                        Toast.makeText(context, "Enter a goal greater than 0", Toast.LENGTH_SHORT).show()
                    } else {
                        ReminderPreferences.setWeeklyGoalHours(context, parsed)
                        goalText = "%.2f".format(Locale.US, ReminderPreferences.getWeeklyGoalHours(context))
                        viewModel.notifyPrefsChanged()
                        Toast.makeText(context, "Weekly goal saved", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save weekly goal")
            }

            HorizontalDivider()

            Text("Daily reminder", style = MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Notification")
                    Text(
                        "Remind me once a day to log hours (skipped if today already has clock-out)",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { on ->
                        enabled = on
                        ReminderPreferences.setReminderEnabled(context, on)
                        ReminderScheduler.scheduleDailyReminder(context)
                        Toast.makeText(
                            context,
                            if (on) "Daily reminder on" else "Daily reminder off",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }

            OutlinedButton(
                onClick = { showPicker = true },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                val (hour, minute) = reminderTime
                Text("Reminder time: ${HoursCalc.formatClock(hour * 60 + minute)}")
            }

            Text(
                "Theme follows the system setting.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    if (showPicker) {
        val (hour, minute) = reminderTime
        val state = rememberTimePickerState(
            initialHour = hour,
            initialMinute = minute,
            is24Hour = false
        )
        AlertDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        ReminderPreferences.setReminderTime(context, state.hour, state.minute)
                        reminderTime = state.hour to state.minute
                        ReminderScheduler.scheduleDailyReminder(context)
                        showPicker = false
                        Toast.makeText(context, "Reminder time updated", Toast.LENGTH_SHORT).show()
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancel") }
            },
            title = { Text("Reminder time") },
            text = {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = state)
                }
            }
        )
    }
}
