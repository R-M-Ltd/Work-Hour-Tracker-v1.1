package com.rmltd.workhourstracker.ui.screens

import android.app.Activity
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import com.rmltd.workhourstracker.ui.navigation.SettingsSection
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.rmltd.workhourstracker.data.CloudSyncPreferences
import com.rmltd.workhourstracker.data.sync.CloudOAuthLauncher
import com.rmltd.workhourstracker.data.sync.CloudSyncEngine
import com.rmltd.workhourstracker.WorkHoursApplication
import java.text.DateFormat
import java.util.Date
import com.rmltd.workhourstracker.data.AppearanceMode
import com.rmltd.workhourstracker.data.BackupPreferences
import com.rmltd.workhourstracker.data.ReminderPreferences
import com.rmltd.workhourstracker.data.ShadePreferences
import com.rmltd.workhourstracker.data.ThemePreferences
import com.rmltd.workhourstracker.ui.theme.AppFontStyle
import com.rmltd.workhourstracker.ui.theme.AppTheme
import com.rmltd.workhourstracker.ui.theme.previewPrimary
import com.rmltd.workhourstracker.util.BackupCodec
import com.rmltd.workhourstracker.util.BackupShare
import com.rmltd.workhourstracker.util.CsvExporter
import com.rmltd.workhourstracker.util.PdfExporter
import com.rmltd.workhourstracker.util.HoursCalc
import com.rmltd.workhourstracker.receiver.ClockSessionNotifier
import com.rmltd.workhourstracker.widget.WidgetThemeColors
import com.rmltd.workhourstracker.util.WeekUtils
import com.rmltd.workhourstracker.worker.ReminderScheduler
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import com.rmltd.workhourstracker.data.SaveEntryResult
import com.rmltd.workhourstracker.ui.components.AddChangeHoursDraft
import com.rmltd.workhourstracker.ui.components.AddChangeHoursResult
import com.rmltd.workhourstracker.ui.components.AddChangeHoursSheet
import com.rmltd.workhourstracker.ui.components.formatHoursField
import com.rmltd.workhourstracker.util.HomeManualTimes
import com.rmltd.workhourstracker.util.ZeroTimeNote
import com.rmltd.workhourstracker.viewmodel.WorkHoursViewModel
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: WorkHoursViewModel,
    onBack: () -> Unit,
    onSetTodaysTimes: () -> Unit = {},
    onLogLunch: () -> Unit = {},
    initialSection: SettingsSection? = null
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
    var appearanceMode by remember { mutableStateOf(ThemePreferences.getAppearanceMode(context)) }
    var fontStyle by remember { mutableStateOf(ThemePreferences.getFontStyle(context)) }
    var shadeClockControls by remember {
        mutableStateOf(ShadePreferences.isClockControlsEnabled(context))
    }
    var lastBackupEpoch by remember {
        mutableStateOf(BackupPreferences.getLastBackupEpochMillis(context))
    }
    var showPayPeriodCustom by remember { mutableStateOf(false) }
    var colorSectionExpanded by remember { mutableStateOf(true) }
    var backupBusy by remember { mutableStateOf(false) }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var showSettingsExportMenu by remember { mutableStateOf(false) }
    var showSettingsRangeExport by remember { mutableStateOf(false) }
    var showSettingsPdfMenu by remember { mutableStateOf(false) }
    var showSettingsPdfRangeExport by remember { mutableStateOf(false) }
    var cloudSyncEnabled by remember { mutableStateOf(CloudSyncPreferences.isEnabled(context)) }
    var cloudSyncLinked by remember { mutableStateOf(CloudSyncPreferences.isLinked(context)) }
    var cloudProvider by remember { mutableStateOf(CloudSyncPreferences.getProvider(context)) }
    var cloudAccount by remember { mutableStateOf(CloudSyncPreferences.getAccountName(context)) }
    var cloudLastSync by remember { mutableStateOf(CloudSyncPreferences.getLastSyncEpochMillis(context)) }
    var cloudError by remember { mutableStateOf(CloudSyncPreferences.getLastError(context)) }
    var cloudBusy by remember { mutableStateOf(false) }
    var cloudSigningIn by remember { mutableStateOf(false) }
    var cloudSessionExpired by remember { mutableStateOf(CloudSyncPreferences.isSessionExpired(context)) }
    var showCloudSyncInfo by remember { mutableStateOf(false) }

    // L2: Set today's times → AddChangeHoursSheet hosted on Settings
    var showSetTodaysSheet by remember { mutableStateOf(false) }
    var setTodaysTitle by remember { mutableStateOf("Set today's times") }
    var sheetClockIn by remember { mutableStateOf<Int?>(null) }
    var sheetClockOut by remember { mutableStateOf<Int?>(null) }
    var sheetNote by remember { mutableStateOf("") }
    var sheetNoLunch by remember { mutableStateOf(false) }
    var sheetPickerField by remember { mutableStateOf<SettingsTodayClockField?>(null) }
    // S4: suspend Set-today sheet while TimePicker alone
    var sheetSuspendedForPicker by remember { mutableStateOf(false) }
    var sheetDraftHoursText by remember { mutableStateOf("") }
    var showSheetZeroDialog by remember { mutableStateOf(false) }
    var sheetZeroReason by remember { mutableStateOf("") }
    var pendingSheetTyped by remember { mutableStateOf<Double?>(null) }
    var pendingSheetNoLunch by remember { mutableStateOf(false) }
    var showSheetOvernightConfirm by remember { mutableStateOf(false) }
    var showSheetBlockedOvernight by remember { mutableStateOf(false) }
    var sheetOpenOvernightDate by remember { mutableStateOf<LocalDate?>(null) }

    fun refreshCloudState() {
        cloudSyncEnabled = CloudSyncPreferences.isEnabled(context)
        cloudSyncLinked = CloudSyncPreferences.isLinked(context)
        cloudProvider = CloudSyncPreferences.getProvider(context)
        cloudAccount = CloudSyncPreferences.getAccountName(context)
        cloudLastSync = CloudSyncPreferences.getLastSyncEpochMillis(context)
        cloudError = CloudSyncPreferences.getLastError(context)
        cloudSessionExpired = CloudSyncPreferences.isSessionExpired(context)
        cloudSigningIn = false
    }
    val scope = rememberCoroutineScope()


    fun exportPayPeriodCsv(start: LocalDate, end: LocalDate, fileName: String) {
        backupBusy = true
        scope.launch {
            try {
                val weeks = viewModel.loadExportWeeks()
                val filtered = CsvExporter.filterByDateRange(weeks, start, end)
                val n = filtered.sumOf { it.second.size }
                if (n == 0) {
                    Toast.makeText(context, "Nothing to export", Toast.LENGTH_SHORT).show()
                } else {
                    val intent = CsvExporter.shareCsv(
                        context,
                        CsvExporter.buildCsv(filtered),
                        fileName
                    )
                    context.startActivity(
                        Intent.createChooser(intent, "Export work hours CSV")
                    )
                    Toast.makeText(
                        context,
                        "Exported $start → $end ($n days)",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                backupBusy = false
            }
        }
    }

    val weekEntries by viewModel.currentWeekEntries.collectAsState()
    val today = LocalDate.now()
    val todayEntry = viewModel.entryFor(today, weekEntries)
    val weekStart by viewModel.weekStart.collectAsState()
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
    // Optional 1.3.39: stamp Last backed up only when share chooser returns RESULT_OK
    val backupShareLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val now = System.currentTimeMillis()
            BackupPreferences.setLastBackupEpochMillis(context, now)
            lastBackupEpoch = now
            Toast.makeText(context, "Backup ready to share", Toast.LENGTH_SHORT).show()
        }
        backupBusy = false
    }
    val restorePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingRestoreUri = uri
            showRestoreConfirm = true
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                exactAlarmsAllowed = ReminderScheduler.canScheduleExactAlarms(context)
                notificationsAllowed = ReminderScheduler.areNotificationsEnabled(context)
                refreshCloudState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val weekEndDay = WeekUtils.weekEndDayName(weekStartDay)
    val sectionShape = RoundedCornerShape(18.dp)
    val scrollState = rememberScrollState()
    val sectionOffsets = remember { mutableStateMapOf<SettingsSection, Int>() }

    LaunchedEffect(initialSection, sectionOffsets.toMap()) {
        val section = initialSection ?: return@LaunchedEffect
        val offset = sectionOffsets[section] ?: return@LaunchedEffect
        scrollState.animateScrollTo(offset.coerceAtLeast(0))
    }


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
                .verticalScroll(scrollState)
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
                    Text("Work week", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

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
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coords ->
                        sectionOffsets[SettingsSection.RATES_GOALS] =
                            coords.positionInParent().y.toInt()
                    },
                shape = sectionShape,
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Weekly goal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                    OutlinedTextField(
                        value = goalText,
                        onValueChange = { goalText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("Hours per week") },
                        supportingText = { Text("Week target for Home / widget totals. Default 40.00.") },
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
                        Text("Save weekly goal", maxLines = 2, softWrap = true)
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
                    Text("Hourly rate (optional)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

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
                        Text("Save hourly rate", maxLines = 2, softWrap = true)
                    }
                }
            }

            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coords ->
                        sectionOffsets[SettingsSection.REMINDERS_SHADE] =
                            coords.positionInParent().y.toInt()
                    },
                shape = sectionShape,
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Daily reminder", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

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
                        Text("Reminder time: ${HoursCalc.formatClock(hour * 60 + minute)}", maxLines = 2, softWrap = true)
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
                    Text(
                        "Notification clock controls",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Shade Clock out")
                            Text(
                                "When on and clocked in, show an ongoing notification with elapsed time and Clock out. Off by default.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = shadeClockControls,
                            onCheckedChange = { on ->
                                if (on && !ReminderScheduler.areNotificationsEnabled(context)) {
                                    Toast.makeText(
                                        context,
                                        "Allow notifications to use clock controls",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    ReminderScheduler.openAppNotificationSettings(context)
                                    return@Switch
                                }
                                shadeClockControls = on
                                ShadePreferences.setClockControlsEnabled(context, on)
                                if (on) {
                                    ClockSessionNotifier.syncFromApp(context)
                                    Toast.makeText(context, "Clock controls on", Toast.LENGTH_SHORT).show()
                                } else {
                                    ClockSessionNotifier.cancel(context)
                                    Toast.makeText(context, "Clock controls off", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
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
                    Text("End-of-day reminder", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("End of day")
                            Text(
                                "Gentle wrap-up if today is empty or unfinished. " +
                                    "Skips the ping if today is already complete.",
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
                        Text("Cutoff time: ${HoursCalc.formatClock(hour * 60 + minute)}", maxLines = 2, softWrap = true)
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
                        Text("Notifications blocked", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
                                Text("Allow notifications", maxLines = 2, softWrap = true)
                            }
                        }
                        OutlinedButton(
                            onClick = { ReminderScheduler.openAppNotificationSettings(context) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Open notification settings", maxLines = 2, softWrap = true)
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
                        Text("Exact alarms", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
                            Text("Allow exact alarms", maxLines = 2, softWrap = true)
                        }
                    }
                }
            }

            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coords ->
                        sectionOffsets[SettingsSection.APPEARANCE] =
                            coords.positionInParent().y.toInt()
                    },
                shape = sectionShape,
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Appearance",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Choose System, Light, or Dark. Applies immediately to the app and home-screen widget.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppearanceMode.entries.forEach { mode ->
                            val selected = appearanceMode == mode
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    appearanceMode = mode
                                    viewModel.setAppearanceMode(mode)
                                    Toast.makeText(
                                        context,
                                        "${mode.displayName} appearance",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                label = { Text(mode.displayName) },
                                modifier = Modifier.weight(1f)
                            )
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
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                colorSectionExpanded = !colorSectionExpanded
                            }
                    )
                    Text(
                        if (colorSectionExpanded) {
                            "Light/dark is controlled by Appearance above. Choose a palette below, or tap Color to hide."
                        } else {
                            "Light/dark is controlled by Appearance above. Tap Color to expand and choose a palette."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Home-screen widget uses the same color palette (and light/dark) as the app. Widget updates about every minute while clocked in.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (colorSectionExpanded) {
                        Spacer(Modifier.height(4.dp))
                        // Arc Clock / widget live preview (sample chrome; no Room loop)
                        val systemDark = (context.resources.configuration.uiMode and
                            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                            android.content.res.Configuration.UI_MODE_NIGHT_YES
                        val previewDark = appearanceMode.resolveDark(systemDark)
                        val previewColors = WidgetThemeColors.resolve(colorTheme, previewDark)
                        ElevatedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics { contentDescription = "Home-screen widget preview" },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = Color(previewColors.primaryContainer)
                            ),
                            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF5B3F9E))
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        "Work Hours",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(previewColors.primary)
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Clocked in since 8:02 AM",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(previewColors.onSurface)
                                )
                                Text(
                                    "Today: 3.50h",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(previewColors.onSurface)
                                )
                                Text(
                                    "Week 12.50h / 40.00h",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(previewColors.onSurfaceVariant)
                                )
                            }
                        }
                        Text(
                            "Home-screen widget preview",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                        )
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
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coords ->
                        sectionOffsets[SettingsSection.EXPORT] =
                            coords.positionInParent().y.toInt()
                    },
                shape = sectionShape,
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Export / share", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Share a CSV or a printable PDF timesheet via the system share sheet (same ranges as History).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedButton(
                                onClick = { showSettingsExportMenu = true },
                                enabled = !backupBusy,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .semantics { contentDescription = "Export CSV" }
                            ) {
                                Text("Export CSV…", maxLines = 2, softWrap = true)
                            }
                            DropdownMenu(
                                expanded = showSettingsExportMenu,
                                onDismissRequest = { showSettingsExportMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("This week") },
                                    onClick = {
                                        showSettingsExportMenu = false
                                        backupBusy = true
                                        scope.launch {
                                            try {
                                                val weeks = viewModel.loadExportWeeks()
                                                val end = WeekUtils.weekEndFor(weekStart)
                                                val filtered = CsvExporter.filterByDateRange(
                                                    weeks, weekStart, end
                                                )
                                                val n = filtered.sumOf { it.second.size }
                                                if (n == 0) {
                                                    Toast.makeText(context, "Nothing to export", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    val intent = CsvExporter.shareCsv(
                                                        context,
                                                        CsvExporter.buildCsv(filtered),
                                                        "work_hours_${weekStart}_${end}.csv"
                                                    )
                                                    context.startActivity(
                                                        Intent.createChooser(intent, "Export work hours CSV")
                                                    )
                                                }
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                                            } finally {
                                                backupBusy = false
                                            }
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Date range…") },
                                    onClick = {
                                        showSettingsExportMenu = false
                                        showSettingsRangeExport = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("This month (pay period)") },
                                    onClick = {
                                        showSettingsExportMenu = false
                                        val ym = YearMonth.now()
                                        val start = ym.atDay(1)
                                        val end = ym.atEndOfMonth()
                                        exportPayPeriodCsv(start, end, "work_hours_${ym}.csv")
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Last month (pay period)") },
                                    onClick = {
                                        showSettingsExportMenu = false
                                        val ym = YearMonth.now().minusMonths(1)
                                        val start = ym.atDay(1)
                                        val end = ym.atEndOfMonth()
                                        exportPayPeriodCsv(start, end, "work_hours_${ym}.csv")
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Custom pay period…") },
                                    onClick = {
                                        showSettingsExportMenu = false
                                        showPayPeriodCustom = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("All time") },
                                    onClick = {
                                        showSettingsExportMenu = false
                                        backupBusy = true
                                        scope.launch {
                                            try {
                                                val weeks = viewModel.loadExportWeeks()
                                                val n = weeks.sumOf { it.second.size }
                                                if (n == 0) {
                                                    Toast.makeText(context, "Nothing to export", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    val intent = CsvExporter.shareCsv(
                                                        context,
                                                        CsvExporter.buildCsv(weeks)
                                                    )
                                                    context.startActivity(
                                                        Intent.createChooser(intent, "Export work hours CSV")
                                                    )
                                                }
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                                            } finally {
                                                backupBusy = false
                                            }
                                        }
                                    }
                                )
                            }
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedButton(
                                onClick = { showSettingsPdfMenu = true },
                                enabled = !backupBusy,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .semantics { contentDescription = "Export PDF" }
                            ) {
                                Text("Export PDF", maxLines = 2, softWrap = true)
                            }
                            DropdownMenu(
                                expanded = showSettingsPdfMenu,
                                onDismissRequest = { showSettingsPdfMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("This week") },
                                    onClick = {
                                        showSettingsPdfMenu = false
                                        backupBusy = true
                                        scope.launch {
                                            try {
                                                val weeks = viewModel.loadExportWeeks()
                                                val end = WeekUtils.weekEndFor(weekStart)
                                                val filtered = CsvExporter.filterByDateRange(
                                                    weeks, weekStart, end
                                                )
                                                val n = filtered.sumOf { it.second.size }
                                                if (n == 0) {
                                                    Toast.makeText(context, "Nothing to export", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    val intent = PdfExporter.sharePdf(
                                                        context,
                                                        weeks,
                                                        startInclusive = weekStart,
                                                        endInclusive = end,
                                                        fileName = "work_hours_${weekStart}_${end}.pdf"
                                                    )
                                                    context.startActivity(
                                                        Intent.createChooser(intent, "Export work hours PDF")
                                                    )
                                                    Toast.makeText(
                                                        context,
                                                        "Exported $weekStart → $end ($n days)",
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                }
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                                            } finally {
                                                backupBusy = false
                                            }
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Date range…") },
                                    onClick = {
                                        showSettingsPdfMenu = false
                                        showSettingsPdfRangeExport = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("All time") },
                                    onClick = {
                                        showSettingsPdfMenu = false
                                        backupBusy = true
                                        scope.launch {
                                            try {
                                                val weeks = viewModel.loadExportWeeks()
                                                val n = weeks.sumOf { it.second.size }
                                                if (n == 0) {
                                                    Toast.makeText(context, "Nothing to export", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    val intent = PdfExporter.sharePdf(
                                                        context,
                                                        weeks,
                                                        fileName = "work_hours_all.pdf"
                                                    )
                                                    context.startActivity(
                                                        Intent.createChooser(intent, "Export work hours PDF")
                                                    )
                                                }
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                                            } finally {
                                                backupBusy = false
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                    Text(
                        "Share a printable timesheet for the selected range",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coords ->
                        sectionOffsets[SettingsSection.BACKUP_CLOUD] =
                            coords.positionInParent().y.toInt()
                    },
                shape = sectionShape,
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Backup & restore", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "One JSON file for a phone swap. Includes all daily entries, week " +
                            "summaries, and settings (week start, goal, theme, font, rate, reminders). " +
                            "Does not include notification permission or transient EOD snooze stamps. " +
                            "Restore replaces everything on this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val backupLabel = if (lastBackupEpoch <= 0L) {
                        "Last backed up: Never"
                    } else {
                        "Last backed up: " + DateFormat.getDateTimeInstance(
                            DateFormat.MEDIUM, DateFormat.SHORT
                        ).format(Date(lastBackupEpoch))
                    }
                    Text(
                        backupLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.semantics { contentDescription = "Last backed up" }
                    )
                    if (BackupPreferences.isBackupStale(context)) {
                        Text(
                            if (lastBackupEpoch <= 0L) "Backup recommended"
                            else "Backup is over a week old",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = {
                            if (backupBusy) return@Button
                            backupBusy = true
                            scope.launch {
                                try {
                                    val version = try {
                                        context.packageManager
                                            .getPackageInfo(context.packageName, 0)
                                            .versionName ?: "unknown"
                                    } catch (_: Exception) {
                                        "unknown"
                                    }
                                    val json = viewModel.buildBackupJson(version)
                                    val intent = BackupShare.shareBackup(context, json)
                                    backupShareLauncher.launch(
                                        Intent.createChooser(intent, "Share work hours backup")
                                    )
                                    // Stamp deferred to RESULT_OK (best-effort; some OEMs OK on cancel).
                                    backupBusy = false
                                } catch (e: Exception) {
                                    Toast.makeText(
                                        context,
                                        "Backup failed: ${e.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    backupBusy = false
                                }
                            }
                        },
                        enabled = !backupBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "Backup and share file" }
                    ) {
                        Text(if (backupBusy) "Working…" else "Backup / share file", maxLines = 2, softWrap = true)
                    }
                    OutlinedButton(
                        onClick = {
                            restorePicker.launch(arrayOf("application/json", "text/*", "*/*"))
                        },
                        enabled = !backupBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "Restore from file" }
                    ) {
                        Text("Restore from file…", maxLines = 2, softWrap = true)
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
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Today", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Shortcuts also available from Home ⋮",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = {
                            val hasHours = todayEntry?.hasPersistedHours() == true
                            setTodaysTitle =
                                if (hasHours) "Change hours" else "Set today's times"
                            sheetClockIn = todayEntry?.clockInMinutes
                            sheetClockOut = todayEntry?.clockOutMinutes
                            sheetNote = todayEntry?.comments.orEmpty()
                            sheetNoLunch = todayEntry?.noLunchTaken == true
                            sheetDraftHoursText = when {
                                hasHours -> formatHoursField(todayEntry!!.hoursWorked)
                                else -> ""
                            }
                            sheetSuspendedForPicker = false
                            showSetTodaysSheet = true
                            onSetTodaysTimes() // no-op from nav (sheet hosted here)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .semantics { contentDescription = "Set today's times" }
                    ) { Text("Set today's times…", maxLines = 2, softWrap = true) }
                    OutlinedButton(
                        onClick = onLogLunch,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .semantics { contentDescription = "Log lunch or break" }
                    ) { Text("Log lunch / break…", maxLines = 2, softWrap = true) }
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Cloud sync", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Optional. Your hours stay on this device until you turn this on.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = cloudSyncEnabled,
                            onCheckedChange = { on ->
                                cloudSyncEnabled = on
                                CloudSyncPreferences.setEnabled(context, on)
                                Toast.makeText(
                                    context,
                                    if (on) "Cloud sync on" else "Cloud sync off",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            modifier = Modifier.semantics { contentDescription = "Cloud sync" }
                        )
                    }
                    if (cloudSyncEnabled) {
                        Text(
                            CloudSyncPreferences.HELPER_CHOOSE,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        CloudSyncPreferences.Provider.entries.forEach { prov ->
                            val selected = cloudProvider == prov
                            val linkedHere = selected && cloudSyncLinked
                            val enabledPick = cloudProvider == null || selected
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .clickable(enabled = enabledPick && !cloudSyncLinked) {
                                        cloudProvider = prov
                                        CloudSyncPreferences.setProvider(context, prov)
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selected,
                                    onClick = {
                                        if (!cloudSyncLinked) {
                                            cloudProvider = prov
                                            CloudSyncPreferences.setProvider(context, prov)
                                        }
                                    },
                                    enabled = enabledPick && !cloudSyncLinked
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(prov.displayName, style = MaterialTheme.typography.bodyLarge)
                                    if (linkedHere) {
                                        Text(
                                            cloudAccount ?: "Linked",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                        Text(
                            CloudSyncPreferences.ICLOUD_FOOTNOTE,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            CloudSyncPreferences.CONFLICT_COPY,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (cloudSyncLinked) {
                            val lastLabel = if (cloudLastSync > 0L) {
                                "Last synced: " + DateFormat.getDateTimeInstance(
                                    DateFormat.SHORT, DateFormat.SHORT
                                ).format(Date(cloudLastSync))
                            } else CloudSyncPreferences.NOT_SYNCED_YET
                            Text(lastLabel, style = MaterialTheme.typography.bodyMedium)
                        } else {
                            Text(
                                "Not linked",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        val sessionExpiredUi = cloudSessionExpired ||
                            CloudSyncPreferences.isSessionExpiredSignal(cloudError, cloudSessionExpired)
                        if (sessionExpiredUi && cloudSyncLinked) {
                            Text(
                                CloudSyncPreferences.SESSION_EXPIRED,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.semantics {
                                    contentDescription = CloudSyncPreferences.SESSION_EXPIRED
                                }
                            )
                        } else if (!cloudError.isNullOrBlank()) {
                            Text(
                                cloudError!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        if (cloudSigningIn) {
                            Text(
                                CloudSyncPreferences.SIGNING_IN,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        fun launchCloudSignIn() {
                            val prov = cloudProvider
                            if (prov == null) {
                                Toast.makeText(context, "Choose a cloud first", Toast.LENGTH_SHORT).show()
                                return
                            }
                            if (!CloudOAuthLauncher.isConfigured(prov)) {
                                CloudSyncPreferences.setLastError(
                                    context, CloudSyncPreferences.NOT_CONFIGURED
                                )
                                cloudError = CloudSyncPreferences.NOT_CONFIGURED
                                Toast.makeText(
                                    context,
                                    CloudSyncPreferences.NOT_CONFIGURED,
                                    Toast.LENGTH_LONG
                                ).show()
                                return
                            }
                            val intent = CloudOAuthLauncher.authorizeIntent(context, prov)
                            if (intent == null) {
                                cloudError = CloudSyncPreferences.NOT_CONFIGURED
                                Toast.makeText(
                                    context,
                                    CloudSyncPreferences.NOT_CONFIGURED,
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                cloudSigningIn = true
                                context.startActivity(intent)
                            }
                        }
                        if (!cloudSyncLinked) {
                            Button(
                                onClick = { launchCloudSignIn() },
                                enabled = !cloudBusy && !cloudSigningIn,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .semantics { contentDescription = "Sign in to sync" }
                            ) { Text("Sign in") }
                            Text(
                                CloudSyncPreferences.AUTH_CODE_EDUCATION,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (sessionExpiredUi) {
                            Button(
                                onClick = { launchCloudSignIn() },
                                enabled = !cloudBusy && !cloudSigningIn,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .semantics { contentDescription = CloudSyncPreferences.SIGN_IN_AGAIN }
                            ) { Text(CloudSyncPreferences.SIGN_IN_AGAIN) }
                            Text(
                                CloudSyncPreferences.AUTH_CODE_EDUCATION,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = { },
                                enabled = false,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .semantics { contentDescription = "Sync now" }
                            ) { Text("Sync now") }
                            OutlinedButton(
                                onClick = {
                                    CloudSyncPreferences.unlink(context)
                                    refreshCloudState()
                                    Toast.makeText(
                                        context,
                                        "Unlinked — local hours kept",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .semantics { contentDescription = "Unlink cloud" }
                            ) { Text("Unlink (keep local)") }
                        } else {
                            Button(
                                onClick = {
                                    cloudBusy = true
                                    scope.launch {
                                        try {
                                            val app = context.applicationContext as WorkHoursApplication
                                            val engine = CloudSyncEngine(app.repository)
                                            when (val out = engine.syncNow(context)) {
                                                is CloudSyncEngine.SyncOutcome.Success ->
                                                    Toast.makeText(context, "Synced", Toast.LENGTH_SHORT).show()
                                                is CloudSyncEngine.SyncOutcome.Error ->
                                                    Toast.makeText(context, out.message, Toast.LENGTH_LONG).show()
                                                is CloudSyncEngine.SyncOutcome.SessionExpired ->
                                                    Toast.makeText(
                                                        context,
                                                        CloudSyncPreferences.SESSION_EXPIRED,
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                else ->
                                                    Toast.makeText(context, "Sync skipped", Toast.LENGTH_SHORT).show()
                                            }
                                            refreshCloudState()
                                        } finally {
                                            cloudBusy = false
                                        }
                                    }
                                },
                                enabled = !cloudBusy,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .semantics { contentDescription = "Sync now" }
                            ) { Text(if (cloudBusy) "Syncing…" else "Sync now") }
                            OutlinedButton(
                                onClick = {
                                    CloudSyncPreferences.unlink(context)
                                    refreshCloudState()
                                    Toast.makeText(
                                        context,
                                        "Unlinked — local hours kept",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .semantics { contentDescription = "Unlink cloud" }
                            ) { Text("Unlink (keep local)") }
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
                    Text("Font style", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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


    if (showPayPeriodCustom) {
        val ym = YearMonth.now()
        ExportRangeDialog(
            initialStart = ym.atDay(1),
            initialEnd = ym.atEndOfMonth(),
            onDismiss = { showPayPeriodCustom = false },
            onConfirm = { start, end ->
                showPayPeriodCustom = false
                if (end.isBefore(start)) {
                    Toast.makeText(context, "End date must be on or after start", Toast.LENGTH_SHORT).show()
                } else {
                    exportPayPeriodCsv(start, end, "work_hours_${start}_${end}.csv")
                }
            }
        )
    }

    if (showSettingsRangeExport) {
        ExportRangeDialog(
            initialStart = weekStart,
            initialEnd = WeekUtils.weekEndFor(weekStart),
            onDismiss = { showSettingsRangeExport = false },
            onConfirm = { start, end ->
                showSettingsRangeExport = false
                if (end.isBefore(start)) {
                    Toast.makeText(context, "End date must be on or after start", Toast.LENGTH_SHORT).show()
                } else if (!backupBusy) {
                    backupBusy = true
                    scope.launch {
                        try {
                            val weeks = viewModel.loadExportWeeks()
                            val filtered = CsvExporter.filterByDateRange(weeks, start, end)
                            val n = filtered.sumOf { it.second.size }
                            if (n == 0) {
                                Toast.makeText(
                                    context,
                                    "Nothing to export for $start → $end",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                val intent = CsvExporter.shareCsv(
                                    context,
                                    CsvExporter.buildCsv(filtered),
                                    "work_hours_${start}_${end}.csv"
                                )
                                context.startActivity(
                                    Intent.createChooser(intent, "Export work hours CSV")
                                )
                                Toast.makeText(
                                    context,
                                    "Exported $start → $end ($n days)",
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
                            backupBusy = false
                        }
                    }
                }
            }
        )
    }

    if (showSettingsPdfRangeExport) {
        ExportRangeDialog(
            initialStart = weekStart,
            initialEnd = WeekUtils.weekEndFor(weekStart),
            onDismiss = { showSettingsPdfRangeExport = false },
            onConfirm = { start, end ->
                showSettingsPdfRangeExport = false
                if (end.isBefore(start)) {
                    Toast.makeText(context, "End date must be on or after start", Toast.LENGTH_SHORT).show()
                } else if (!backupBusy) {
                    backupBusy = true
                    scope.launch {
                        try {
                            val weeks = viewModel.loadExportWeeks()
                            val filtered = CsvExporter.filterByDateRange(weeks, start, end)
                            val n = filtered.sumOf { it.second.size }
                            if (n == 0) {
                                Toast.makeText(
                                    context,
                                    "Nothing to export for $start → $end",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                val intent = PdfExporter.sharePdf(
                                    context,
                                    weeks,
                                    startInclusive = start,
                                    endInclusive = end,
                                    fileName = "work_hours_${start}_${end}.pdf"
                                )
                                context.startActivity(
                                    Intent.createChooser(intent, "Export work hours PDF")
                                )
                                Toast.makeText(
                                    context,
                                    "Exported $start → $end ($n days)",
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
                            backupBusy = false
                        }
                    }
                }
            }
        )
    }

    // showCloudSyncInfo kept for compatibility; real UX is inline above.

    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = {
                showRestoreConfirm = false
                pendingRestoreUri = null
            },
            title = { Text("Restore backup?") },
            text = {
                Text(
                    "This replaces all hours and settings on this device with the backup file. " +
                        "This cannot be undone unless you make a backup first."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val uri = pendingRestoreUri
                        showRestoreConfirm = false
                        pendingRestoreUri = null
                        if (uri == null) return@TextButton
                        backupBusy = true
                        scope.launch {
                            try {
                                val json = context.contentResolver.openInputStream(uri)?.use {
                                    it.readBytes().toString(Charsets.UTF_8)
                                } ?: throw IllegalStateException("Could not read file")
                                val payload = viewModel.restoreFromBackupJson(json)
                                ReminderScheduler.scheduleDailyReminder(context)
                                ReminderScheduler.scheduleEndOfDayReminder(context)
                                ReminderScheduler.scheduleWeeklyReset(context)
                                // Refresh local Settings fields from restored prefs
                                enabled = ReminderPreferences.isReminderEnabled(context)
                                reminderTime = ReminderPreferences.getReminderTime(context)
                                endOfDayEnabled = ReminderPreferences.isEndOfDayEnabled(context)
                                endOfDayTime = ReminderPreferences.getEndOfDayTime(context)
                                weekStartDay = ReminderPreferences.getWeekStartDay(context)
                                goalText = "%.2f".format(
                                    Locale.US,
                                    ReminderPreferences.getWeeklyGoalHours(context)
                                )
                                val rate = ReminderPreferences.getHourlyRate(context)
                                rateText = if (rate <= 0.0) "" else "%.2f".format(Locale.US, rate)
                                colorTheme = ThemePreferences.getColorTheme(context)
                                fontStyle = ThemePreferences.getFontStyle(context)
                                appearanceMode = ThemePreferences.getAppearanceMode(context)
                                // Restore stamps restore time only — never last backed up.
                                BackupPreferences.setLastRestoreEpochMillis(
                                    context, System.currentTimeMillis()
                                )
                                refreshCloudState()
                                Toast.makeText(
                                    context,
                                    "Restored ${payload.entries.size} days from backup",
                                    Toast.LENGTH_LONG
                                ).show()
                            } catch (e: BackupCodec.BackupValidationException) {
                                Toast.makeText(
                                    context,
                                    "Invalid backup: ${e.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    "Restore failed: ${e.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            } finally {
                                backupBusy = false
                            }
                        }
                    }
                ) { Text("Replace and restore", maxLines = 2, softWrap = true) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showRestoreConfirm = false
                        pendingRestoreUri = null
                    }
                ) { Text("Cancel") }
            }
        )
    }

    fun performSettingsTypedSave(
        typed: Double,
        clockIn: Int?,
        clockOut: Int?,
        note: String,
        noLunch: Boolean,
        forceDiscard: Boolean = false
    ) {
        val lunchOut = if (noLunch) null else todayEntry?.lunchOutMinutes
        val lunchIn = if (noLunch) null else todayEntry?.lunchInMinutes
        val breakDur = if (noLunch) null else todayEntry?.breakDurationMinutes
        if (forceDiscard) {
            viewModel.discardOpenAndSaveEntry(
                date = today,
                clockInMinutes = clockIn,
                clockOutMinutes = clockOut,
                comments = note,
                lunchOutMinutes = lunchOut,
                lunchInMinutes = lunchIn,
                breakDurationMinutes = breakDur,
                breakPaid = todayEntry?.breakPaid ?: false,
                typedHours = typed,
                noLunchTaken = noLunch
            ) {
                showSetTodaysSheet = false
                Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
            }
        } else {
            viewModel.saveEntry(
                date = today,
                clockInMinutes = clockIn,
                clockOutMinutes = clockOut,
                comments = note,
                lunchOutMinutes = lunchOut,
                lunchInMinutes = lunchIn,
                breakDurationMinutes = breakDur,
                breakPaid = todayEntry?.breakPaid ?: false,
                typedHours = typed,
                noLunchTaken = noLunch
            ) { result ->
                when (result) {
                    is SaveEntryResult.Saved -> {
                        showSetTodaysSheet = false
                        Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                    }
                    is SaveEntryResult.BlockedOvernightOpen -> {
                        pendingSheetTyped = typed
                        pendingSheetNoLunch = noLunch
                        sheetClockIn = clockIn
                        sheetClockOut = clockOut
                        sheetNote = note
                        sheetOpenOvernightDate = result.openDate
                        showSheetBlockedOvernight = true
                    }
                }
            }
        }
    }

    fun restoreSheetAfterPicker() {
        if (sheetSuspendedForPicker) {
            sheetSuspendedForPicker = false
            showSetTodaysSheet = true
        }
    }

    fun suspendSheetForPicker(draft: AddChangeHoursDraft, field: SettingsTodayClockField) {
        sheetDraftHoursText = draft.hoursText
        sheetNote = draft.note
        sheetNoLunch = draft.noLunchTaken
        showSetTodaysSheet = false
        sheetSuspendedForPicker = true
        sheetPickerField = field
    }

    fun onSettingsAddChangeSave(result: AddChangeHoursResult) {
        sheetClockIn = result.clockInMinutes
        sheetClockOut = result.clockOutMinutes
        sheetNote = result.note
        sheetNoLunch = result.noLunchTaken
        if (ZeroTimeNote.needsZeroHoursReason(
                result.clockInMinutes,
                result.clockOutMinutes,
                result.typedHours,
                typedHours = result.typedHours
            ) && !ZeroTimeNote.hasZeroHoursReason(result.note)
        ) {
            pendingSheetTyped = result.typedHours
            pendingSheetNoLunch = result.noLunchTaken
            sheetZeroReason = ""
            showSheetZeroDialog = true
            return
        }
        if (!ZeroTimeNote.canSaveWithNote(
                result.note,
                clockInMinutes = result.clockInMinutes,
                clockOutMinutes = result.clockOutMinutes
            )
        ) {
            Toast.makeText(
                context,
                "Add a reason note for 12:00 AM (0) before saving",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val cin = result.clockInMinutes
        val cout = result.clockOutMinutes
        if (cin != null && cout != null && HomeManualTimes.needsOvernightConfirm(cin, cout)) {
            pendingSheetTyped = result.typedHours
            pendingSheetNoLunch = result.noLunchTaken
            showSheetOvernightConfirm = true
        } else {
            performSettingsTypedSave(
                result.typedHours,
                result.clockInMinutes,
                result.clockOutMinutes,
                result.note,
                result.noLunchTaken
            )
        }
    }

    if (showSetTodaysSheet) {
        AddChangeHoursSheet(
            title = setTodaysTitle,
            initialHours = sheetDraftHoursText,
            initialClockIn = sheetClockIn,
            initialClockOut = sheetClockOut,
            initialNote = sheetNote,
            initialNoLunchTaken = sheetNoLunch,
            clockInMinutes = sheetClockIn,
            clockOutMinutes = sheetClockOut,
            onDismiss = {
                showSetTodaysSheet = false
                sheetSuspendedForPicker = false
            },
            onSave = { onSettingsAddChangeSave(it) },
            onPickClockIn = { draft -> suspendSheetForPicker(draft, SettingsTodayClockField.IN) },
            onPickClockOut = { draft -> suspendSheetForPicker(draft, SettingsTodayClockField.OUT) }
        )
    }

    sheetPickerField?.let { field ->
        SettingsTodayClockPickerDialog(
            field = field,
            currentMinutes = if (field == SettingsTodayClockField.IN) sheetClockIn else sheetClockOut,
            onConfirm = { mins ->
                if (field == SettingsTodayClockField.IN) sheetClockIn = mins
                else sheetClockOut = mins
                sheetPickerField = null
                restoreSheetAfterPicker()
            },
            onDismiss = {
                sheetPickerField = null
                restoreSheetAfterPicker()
            }
        )
    }

    if (showSheetZeroDialog) {
        val canConfirm = sheetZeroReason.trim().isNotEmpty()
        AlertDialog(
            onDismissRequest = {
                showSheetZeroDialog = false
                sheetZeroReason = ""
            },
            title = { Text(ZeroTimeNote.ZERO_HOURS_TITLE) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(ZeroTimeNote.ZERO_HOURS_BODY)
                    OutlinedTextField(
                        value = sheetZeroReason,
                        onValueChange = { if (it.length <= 500) sheetZeroReason = it },
                        label = { Text(ZeroTimeNote.DIALOG_LABEL) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val reason = sheetZeroReason.trim()
                        if (reason.isEmpty()) return@TextButton
                        sheetNote = ZeroTimeNote.mergeZeroHoursIntoNote(sheetNote, reason)
                        showSheetZeroDialog = false
                        sheetZeroReason = ""
                        val typed = pendingSheetTyped
                        if (typed != null) {
                            val cin = sheetClockIn
                            val cout = sheetClockOut
                            if (cin != null && cout != null &&
                                HomeManualTimes.needsOvernightConfirm(cin, cout)
                            ) {
                                showSheetOvernightConfirm = true
                            } else {
                                performSettingsTypedSave(
                                    typed, cin, cout, sheetNote, pendingSheetNoLunch
                                )
                                pendingSheetTyped = null
                            }
                        }
                    },
                    enabled = canConfirm
                ) { Text(ZeroTimeNote.CONFIRM_LABEL) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSheetZeroDialog = false
                    sheetZeroReason = ""
                }) { Text(ZeroTimeNote.DISMISS_LABEL) }
            }
        )
    }

    if (showSheetOvernightConfirm) {
        AlertDialog(
            onDismissRequest = { showSheetOvernightConfirm = false },
            title = { Text("Overnight shift?") },
            text = {
                Text("Clock out is earlier than clock in. Treat as overnight?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSheetOvernightConfirm = false
                        val typed = pendingSheetTyped
                        if (typed != null) {
                            performSettingsTypedSave(
                                typed, sheetClockIn, sheetClockOut,
                                sheetNote, pendingSheetNoLunch
                            )
                            pendingSheetTyped = null
                        }
                    }
                ) { Text("Save as overnight") }
            },
            dismissButton = {
                TextButton(onClick = { showSheetOvernightConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showSheetBlockedOvernight) {
        val openDay = sheetOpenOvernightDate ?: today.minusDays(1)
        AlertDialog(
            onDismissRequest = { showSheetBlockedOvernight = false },
            title = { Text("Open overnight punch") },
            text = {
                Text(
                    "Another day still has an open clock-in with no clock-out. " +
                        "Finish that day first, or discard the open punch to save today."
                )
            },
            confirmButton = {
                TextButton(onClick = { showSheetBlockedOvernight = false }) { Text("Cancel") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSheetBlockedOvernight = false
                        val typed = pendingSheetTyped
                        if (typed != null) {
                            performSettingsTypedSave(
                                typed, sheetClockIn, sheetClockOut,
                                sheetNote, pendingSheetNoLunch,
                                forceDiscard = true
                            )
                            pendingSheetTyped = null
                        }
                    }
                ) { Text("Discard open punch & save") }
            }
        )
    }
}

private enum class SettingsTodayClockField { IN, OUT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTodayClockPickerDialog(
    field: SettingsTodayClockField,
    currentMinutes: Int?,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val initial = currentMinutes ?: HomeManualTimes.defaultPickerMinutes(field == SettingsTodayClockField.IN)
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
            Text(if (field == SettingsTodayClockField.IN) "Clock in" else "Clock out")
        },
        text = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = state)
            }
        }
    )
}
