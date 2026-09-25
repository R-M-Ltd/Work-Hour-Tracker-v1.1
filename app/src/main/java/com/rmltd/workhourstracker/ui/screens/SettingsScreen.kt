package com.rmltd.workhourstracker.ui.screens

import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.rmltd.workhourstracker.data.ReminderPreferences
import com.rmltd.workhourstracker.data.ThemePreferences
import com.rmltd.workhourstracker.ui.theme.AppFontStyle
import com.rmltd.workhourstracker.ui.theme.AppTheme
import com.rmltd.workhourstracker.ui.theme.previewPrimary
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
    var endOfDayEnabled by remember {
        mutableStateOf(ReminderPreferences.isEndOfDayEnabled(context))
    }
    var endOfDayTime by remember {
        mutableStateOf(ReminderPreferences.getEndOfDayTime(context))
    }
    var showEndOfDayPicker by remember { mutableStateOf(false) }
    var weekStartDay by remember { mutableStateOf(ReminderPreferences.getWeekStartDay(context)) }
    var weekStartExpanded by remember { mutableStateOf(false) }
    var goalText by remember {
        mutableStateOf(
            "%.2f".format(Locale.US, ReminderPreferences.getWeeklyGoalHours(context))
        )
    }
    var rateText by remember {
        mutableStateOf(
            ReminderPreferences.getHourlyRate(context).let { rate ->
                if (rate <= 0.0) "" else "%.2f".format(Locale.US, rate)
            }
        )
    }
    var colorTheme by remember { mutableStateOf(ThemePreferences.getColorTheme(context)) }
    var fontStyle by remember { mutableStateOf(ThemePreferences.getFontStyle(context)) }
    var colorSectionExpanded by remember { mutableStateOf(true) }
    var exactAlarmsAllowed by remember {
        mutableStateOf(ReminderScheduler.canScheduleExactAlarms(context))
    }
    var notificationsAllowed by remember {
        mutableStateOf(ReminderScheduler.areNotificationsEnabled(context))
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        notificationsAllowed = ReminderScheduler.areNotificationsEnabled(context)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                exactAlarmsAllowed = ReminderScheduler.canScheduleExactAlarms(context)
                notificationsAllowed = ReminderScheduler.areNotificationsEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val weekEndDay = WeekUtils.weekEndDayName(weekStartDay)
    val sectionShape = RoundedCornerShape(16.dp)

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
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = sectionShape,
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
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
                                        viewModel.notifyPrefsChanged(weekStartChanged = true)
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
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = sectionShape,
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
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
                }
            }

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = sectionShape,
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Hourly rate (optional)", style = MaterialTheme.typography.titleMedium)

                    OutlinedTextField(
                        value = rateText,
                        onValueChange = { rateText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("Dollars per hour") },
                        supportingText = {
                            Text(
                                "Rough pay estimate on Home / History = hours × rate. " +
                                    "Estimate only — not payroll. Uses $ (USD-style). " +
                                    "Leave blank or 0 to hide."
                            )
                        },
                        prefix = { Text("$") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            val trimmed = rateText.trim()
                            if (trimmed.isEmpty()) {
                                ReminderPreferences.setHourlyRate(context, 0.0)
                                rateText = ""
                                viewModel.notifyPrefsChanged()
                                Toast.makeText(context, "Hourly rate cleared", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val parsed = trimmed.toDoubleOrNull()
                            if (parsed == null || parsed < 0.0) {
                                Toast.makeText(context, "Enter a valid rate (or blank to clear)", Toast.LENGTH_SHORT).show()
                            } else {
                                ReminderPreferences.setHourlyRate(context, parsed)
                                val saved = ReminderPreferences.getHourlyRate(context)
                                rateText = if (saved <= 0.0) "" else "%.2f".format(Locale.US, saved)
                                viewModel.notifyPrefsChanged()
                                Toast.makeText(
                                    context,
                                    if (saved <= 0.0) "Hourly rate cleared" else "Hourly rate saved",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save hourly rate")
                    }
                }
            }

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = sectionShape,
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
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
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                }
            }

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = sectionShape,
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("End-of-day reminder", style = MaterialTheme.typography.titleMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Still clocked in")
                            Text(
                                "One notification if you are still clocked in past this time. " +
                                    "Actions: clock out or extend 1 hour.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = endOfDayEnabled,
                            onCheckedChange = { on ->
                                endOfDayEnabled = on
                                ReminderPreferences.setEndOfDayEnabled(context, on)
                                ReminderScheduler.scheduleEndOfDayReminder(context)
                                Toast.makeText(
                                    context,
                                    if (on) "End-of-day reminder on" else "End-of-day reminder off",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                    }
                    OutlinedButton(
                        onClick = { showEndOfDayPicker = true },
                        enabled = endOfDayEnabled,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val (hour, minute) = endOfDayTime
                        Text("Cutoff time: ${HoursCalc.formatClock(hour * 60 + minute)}")
                    }
                }
            }

            if (!notificationsAllowed) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = sectionShape,
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Notifications blocked", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Daily reminders will not appear while notification permission is denied. " +
                                "Allow notifications for Work Hours Tracker so reminders can fire.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            OutlinedButton(
                                onClick = {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) {
                                        ReminderScheduler.openAppNotificationSettings(context)
                                    } else {
                                        notificationPermissionLauncher.launch(
                                            Manifest.permission.POST_NOTIFICATIONS
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Allow notifications")
                            }
                        }
                        OutlinedButton(
                            onClick = { ReminderScheduler.openAppNotificationSettings(context) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Open notification settings")
                        }
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !exactAlarmsAllowed) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = sectionShape,
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Exact alarms", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Reminders and week archive are more reliable with exact alarms. " +
                                "Without them the app falls back to inexact timing.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = { ReminderScheduler.openExactAlarmSettings(context) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Allow exact alarms")
                        }
                    }
                }
            }

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = sectionShape,
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // "Color" label is a second entry point: tap expands/collapses the same theme radios.
                    Text(
                        "Color",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                colorSectionExpanded = !colorSectionExpanded
                            }
                    )
                    Text(
                        "Light/dark still follows the system setting. Tap Color to show or hide palettes, or pick a radio below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (colorSectionExpanded) {
                        Spacer(Modifier.height(4.dp))
                        AppTheme.entries.forEach { option ->
                            val selected = colorTheme == option
                            val shape = RoundedCornerShape(12.dp)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(shape)
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primaryContainer
                                        else Color.Transparent
                                    )
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selected,
                                    onClick = {
                                        colorTheme = option
                                        viewModel.setColorTheme(option)
                                        Toast.makeText(
                                            context,
                                            "${option.displayName} theme",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                )
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(option.previewPrimary())
                                )
                                Text(
                                    option.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(start = 12.dp)
                                )
                            }
                        }
                    }
                }
            }

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = sectionShape,
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Font style", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Applies app-wide (independent of the phone system font). Default matches Material.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    AppFontStyle.entries.forEach { option ->
                        val selected = fontStyle == option
                        val shape = RoundedCornerShape(12.dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clip(shape)
                                .background(
                                    if (selected) MaterialTheme.colorScheme.secondaryContainer
                                    else Color.Transparent
                                )
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = {
                                    fontStyle = option
                                    viewModel.setFontStyle(option)
                                    Toast.makeText(
                                        context,
                                        "${option.displayName} font",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )
                            Text(
                                option.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }
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

    if (showEndOfDayPicker) {
        val (hour, minute) = endOfDayTime
        val state = rememberTimePickerState(
            initialHour = hour,
            initialMinute = minute,
            is24Hour = false
        )
        AlertDialog(
            onDismissRequest = { showEndOfDayPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        ReminderPreferences.setEndOfDayTime(context, state.hour, state.minute)
                        endOfDayTime = state.hour to state.minute
                        ReminderPreferences.clearEndOfDayFired(context)
                        ReminderScheduler.scheduleEndOfDayReminder(context)
                        showEndOfDayPicker = false
                        Toast.makeText(context, "End-of-day cutoff updated", Toast.LENGTH_SHORT).show()
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showEndOfDayPicker = false }) { Text("Cancel") }
            },
            title = { Text("End-of-day cutoff") },
            text = {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = state)
                }
            }
        )
    }
}
