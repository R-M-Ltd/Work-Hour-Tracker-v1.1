package com.rmltd.workhourstracker.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Grouped More sheet (1.3.42): Day · Pay · Settings.
 * Edit today merges Add/Change + Set today's times. History + Settings root dropped
 * (History stays on bottom bar; section deep-links remain).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreMenuSheet(
    forgotClockOutEnabled: Boolean,
    onDismiss: () -> Unit,
    onEditToday: () -> Unit,
    onLogLunch: () -> Unit,
    onForgotClockOut: () -> Unit,
    onExport: () -> Unit,
    onRatesAndGoals: () -> Unit,
    onAppearance: () -> Unit,
    onBackupAndCloud: () -> Unit,
    onRemindersAndShade: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.semantics { contentDescription = "More menu" }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                "More",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            SectionHeader("Day")
            MoreRow(
                icon = Icons.Filled.Edit,
                title = "Edit today",
                subtitle = "Add / change hours · set times",
                onClick = onEditToday
            )
            MoreRow(
                icon = Icons.Filled.Restaurant,
                title = "Log lunch / break",
                subtitle = "Nav-only · unpaid default",
                onClick = onLogLunch
            )
            MoreRow(
                icon = Icons.Filled.SwapHoriz,
                title = "Forgot to clock out…",
                subtitle = "Close open shift at a chosen time",
                enabled = forgotClockOutEnabled,
                onClick = onForgotClockOut
            )

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(8.dp))

            SectionHeader("Pay")
            MoreRow(
                icon = Icons.Filled.Share,
                title = "Export",
                subtitle = "CSV / PDF · pay period",
                onClick = onExport
            )
            MoreRow(
                icon = Icons.Filled.AttachMoney,
                title = "Rates & goals",
                subtitle = "Pay rate · weekly target · off Home",
                onClick = onRatesAndGoals
            )

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(8.dp))

            SectionHeader("Settings")
            MoreRow(
                icon = Icons.Filled.Brightness6,
                title = "Appearance",
                subtitle = "Theme · Arc Clock preview",
                onClick = onAppearance
            )
            MoreRow(
                icon = Icons.Filled.Cloud,
                title = "Backup & cloud",
                subtitle = "Off by default · one provider",
                onClick = onBackupAndCloud
            )
            MoreRow(
                icon = Icons.Filled.Notifications,
                title = "Reminders & shade",
                subtitle = "EOD · notification shade",
                onClick = onRemindersAndShade
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun MoreRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val alpha = if (enabled) 1f else 0.45f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = title }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
            modifier = Modifier.size(24.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
            )
        }
    }
}
