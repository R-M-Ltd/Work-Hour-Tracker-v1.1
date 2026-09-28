package com.rmltd.workhourstracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.rmltd.workhourstracker.util.HoursCalc
import com.rmltd.workhourstracker.util.TypedHoursParse
import com.rmltd.workhourstracker.util.ZeroTimeNote
import java.util.Locale

data class AddChangeHoursResult(
    val typedHours: Double,
    val clockInMinutes: Int?,
    val clockOutMinutes: Int?,
    val note: String,
    val noLunchTaken: Boolean
)

/** Live sheet fields when suspending for TimePicker (S4). */
data class AddChangeHoursDraft(
    val hoursText: String,
    val note: String,
    val noLunchTaken: Boolean
)

/**
 * Shared Add / Change / Set today's times sheet (1.3.34).
 * Hours required (decimal only). Optional clocks, note, No lunch taken.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddChangeHoursSheet(
    title: String,
    initialHours: String,
    initialClockIn: Int?,
    initialClockOut: Int?,
    initialNote: String,
    initialNoLunchTaken: Boolean,
    onDismiss: () -> Unit,
    onSave: (AddChangeHoursResult) -> Unit,
    onPickClockIn: ((AddChangeHoursDraft) -> Unit)? = null,
    onPickClockOut: ((AddChangeHoursDraft) -> Unit)? = null,
    /** When parent drives clock pickers externally, pass live minutes. */
    clockInMinutes: Int? = initialClockIn,
    clockOutMinutes: Int? = initialClockOut
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var hoursText by remember { mutableStateOf(initialHours) }
    var note by remember { mutableStateOf(initialNote) }
    var noLunch by remember { mutableStateOf(initialNoLunchTaken) }
    val localIn = clockInMinutes
    val localOut = clockOutMinutes

    val parsed = TypedHoursParse.parse(hoursText)
    val hoursError = when {
        hoursText.isBlank() -> null
        hoursText.contains(':') -> "Use decimals (e.g. 7.5), not 7:30"
        parsed == null -> "Enter hours as a number (0–24)"
        else -> null
    }
    val canSave = parsed != null

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            OutlinedTextField(
                value = hoursText,
                onValueChange = { hoursText = it },
                label = { Text("Hours") },
                placeholder = { Text("e.g. 7.5 or 0") },
                isError = hoursError != null,
                supportingText = {
                    when {
                        hoursError != null -> Text(hoursError)
                        parsed == 0.0 && ZeroTimeNote.hasZeroHoursReason(note) ->
                            Text(ZeroTimeNote.ZERO_HOURS_REASON_PRESENT_CAPTION)
                        parsed == 0.0 -> Text(ZeroTimeNote.ZERO_HOURS_SAVE_CAPTION)
                        else -> Text("Decimals only — tenths OK. Colon times not accepted.")
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Hours" }
            )
            Text(
                "Optional clocks (kept as entered; typed hours win on Save)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onPickClockIn?.invoke(
                            AddChangeHoursDraft(hoursText, note, noLunch)
                        )
                    },
                    enabled = onPickClockIn != null,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = "Clock in" }
                ) {
                    Icon(Icons.Filled.Schedule, contentDescription = null)
                    Spacer(Modifier.padding(4.dp))
                    Text(
                        localIn?.let { HoursCalc.formatClock(it) } ?: "Clock in",
                        maxLines = 1
                    )
                }
                OutlinedButton(
                    onClick = {
                        onPickClockOut?.invoke(
                            AddChangeHoursDraft(hoursText, note, noLunch)
                        )
                    },
                    enabled = onPickClockOut != null,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = "Clock out" }
                ) {
                    Icon(Icons.Filled.Schedule, contentDescription = null)
                    Spacer(Modifier.padding(4.dp))
                    Text(
                        localOut?.let { HoursCalc.formatClock(it) } ?: "Clock out",
                        maxLines = 1
                    )
                }
            }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note") },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Note" }
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "No lunch taken" }
            ) {
                Checkbox(
                    checked = noLunch,
                    onCheckedChange = { noLunch = it }
                )
                Text("No lunch taken", style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    val h = parsed ?: return@Button
                    onSave(
                        AddChangeHoursResult(
                            typedHours = h,
                            clockInMinutes = localIn,
                            clockOutMinutes = localOut,
                            note = note,
                            noLunchTaken = noLunch
                        )
                    )
                },
                enabled = canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = "Save hours" }
            ) {
                Text("Save")
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Text("Cancel")
            }
        }
    }
}

fun formatHoursField(hours: Double?): String =
    if (hours == null) "" else "%.2f".format(Locale.US, hours).trimEnd('0').trimEnd('.')
