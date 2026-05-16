package com.example.pumpble.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.Date

@Composable
fun UserControlView(viewModel: UserViewModel) {
    // Auto-start discovery if nothing is selected
    androidx.compose.runtime.LaunchedEffect(viewModel.selectedDevice) {
        if (viewModel.selectedDevice == null && !viewModel.isScanning) {
            viewModel.startDiscovery()
        }
    }

    if (viewModel.showBolusDialog) {
        BolusDialog(viewModel)
    }

    if (viewModel.showUserOptionsDialog) {
        UserOptionsDialog(viewModel)
    }

    if (viewModel.showBolusOptionsDialog) {
        BolusOptionsDialog(viewModel)
    }

    if (viewModel.showBasalProfileDialog) {
        BasalProfileDialog(viewModel)
    }

    if (viewModel.showTempBasalDialog) {
        TempBasalDialog(viewModel)
    }

    if (viewModel.showExtendedBolusDialog) {
        ExtendedBolusDialog(viewModel)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            StatusDashboard(viewModel)
        }

        if (viewModel.selectedDevice == null && !viewModel.sessionReady) {
            item {
                DeviceSelectionCard(viewModel)
            }
        }

        if (viewModel.sessionReady) {
            item {
                BasalCard(viewModel)
            }
            item {
                BolusCard(viewModel)
            }
            item {
                OptionsCard(viewModel)
            }
            item {
                PumpInfoCard(viewModel)
            }
        }
    }
}

@Composable
private fun StatusDashboard(viewModel: UserViewModel) {
    val context = LocalContext.current

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = viewModel.selectedDevice?.name ?: (PumpManager.getLastStoredName() ?: "No Device Selected"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    if (viewModel.selectedDevice == null && viewModel.isSearchingLastDevice) {
                        Text("Searching for last pump...", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Connect / Disconnect Toggle
                    if (viewModel.selectedDevice != null) {
                        Button(
                            onClick = { viewModel.toggleConnection(context) },
                            enabled = viewModel.activeCommand == null,
                            colors = if (viewModel.sessionReady)
                                ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                else ButtonDefaults.buttonColors()
                        ) {
                            if (viewModel.activeCommand == "Connecting" || viewModel.activeCommand == "Handshake") {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Text(if (viewModel.sessionReady) "Disconnect" else "Connect")
                            }
                        }
                    }
                }
            }

            if (viewModel.sessionReady) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusItem(
                        icon = Icons.Default.BatteryStd,
                        label = "Battery",
                        value = viewModel.pumpStatus?.batteryRemainingPercent?.let { "$it%" } ?: "--"
                    )
                    StatusItem(
                        icon = Icons.Default.WaterDrop,
                        label = "Reservoir",
                        value = viewModel.pumpStatus?.reservoirRemainingUnits?.let { "%.1f U".format(it) } ?: "--"
                    )
                    StatusItem(
                        icon = Icons.Default.BluetoothConnected,
                        label = "Last Sync",
                        value = viewModel.lastSyncTime?.let {
                            java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(Date(it))
                        } ?: "--"
                    )

                    OutlinedButton(
                        onClick = { viewModel.refreshAllStatus() },
                        enabled = viewModel.activeCommand == null,
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        if (viewModel.activeCommand == "Syncing Status") {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Bitte verbinden Sie sich zuerst mit der Pumpe, um den Status zu sehen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun DeviceSelectionCard(viewModel: UserViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Available Pumps", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                if (viewModel.isScanning) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    TextButton(onClick = { viewModel.startDiscovery() }) {
                        Text("Rescan")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            if (viewModel.discoveredDevices.isEmpty() && !viewModel.isScanning) {
                Text("No pumps found. Make sure Bluetooth is on.", style = MaterialTheme.typography.bodySmall)
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                viewModel.discoveredDevices.forEach { pump ->
                    Surface(
                        onClick = {
                            viewModel.selectedDevice = pump
                            viewModel.stopDiscovery()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(pump.name.ifBlank { "Unnamed" }, style = MaterialTheme.typography.bodyLarge)
                                Text(pump.address, style = MaterialTheme.typography.bodySmall)
                            }
                            Text("${pump.rssi} dBm", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusItem(icon: ImageVector, label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(text = label, style = MaterialTheme.typography.labelSmall)
        Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BasalCard(viewModel: UserViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Basal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            val currentBasal = viewModel.pumpStatus?.currentBasalUnitsPerHour ?: 0.0
            Text(
                text = "%.2f U/h".format(currentBasal),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )

            if (viewModel.pumpStatus?.tempBasalInProgress == true) {
                Text(
                    "Temp Basal: ${viewModel.pumpStatus?.tempBasalPercent}%",
                    color = MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { viewModel.openTempBasalDialog() },
                    modifier = Modifier.weight(1f),
                    enabled = viewModel.activeCommand == null
                ) {
                    Text("Temp Basal")
                }
                OutlinedButton(
                    onClick = { viewModel.cancelTempBasal() },
                    modifier = Modifier.weight(1f),
                    enabled = viewModel.activeCommand == null
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun BolusCard(viewModel: UserViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Bolus", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            val lastAmount = viewModel.stepBolusInfo?.lastBolusAmountUnits ?: 0.0
            val lastTimeUtc = viewModel.stepBolusInfo?.lastBolusTimeOfDayUTC
            val zoneOffset = viewModel.pumpTimeInfo?.zoneOffsetHours ?: 0

            val lastTimeLocal = lastTimeUtc?.plusHours(zoneOffset.toLong())

            Text(
                text = "Last: %.2f U".format(lastAmount) + (lastTimeLocal?.let { " at $it" } ?: ""),
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.openBolusDialog() },
                    modifier = Modifier.weight(1f),
                    enabled = viewModel.activeCommand == null
                ) {
                    Text("Start Bolus")
                }
                OutlinedButton(
                    onClick = { viewModel.stopBolus() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    enabled = viewModel.activeCommand == null
                ) {
                    Text("Stop Bolus")
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { viewModel.openExtendedBolusDialog() },
                    modifier = Modifier.weight(1f),
                    enabled = viewModel.activeCommand == null
                ) {
                    Text("Extended")
                }
                OutlinedButton(
                    onClick = { viewModel.cancelExtendedBolus() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    enabled = viewModel.activeCommand == null
                ) {
                    Text("Stop Ext.")
                }
            }
        }
    }
}

@Composable
private fun OptionsCard(viewModel: UserViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))

            SettingsRow(Icons.Default.Settings, "User Options") {
                viewModel.openUserOptionsDialog()
            }
            SettingsRow(Icons.Default.Settings, "Bolus Options") {
                viewModel.openBolusOptionsDialog()
            }
            SettingsRow(Icons.Default.Settings, "Basal Profiles") {
                viewModel.openBasalProfileDialog()
            }
        }
    }
}

@Composable
private fun PumpInfoCard(viewModel: UserViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Pump Information",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))

            val timeInfo = viewModel.pumpTimeInfo
            val pumpTimeLocal = timeInfo?.let { it.pumpUtcTime.plusHours(it.zoneOffsetHours.toLong()) }
            InfoRow("Pump Time", pumpTimeLocal?.toString()?.replace("T", " ")?.take(16) ?: "--")
            InfoRow("Time Zone Offset", timeInfo?.let { "UTC%+d".format(it.zoneOffsetHours) } ?: "--")

            // Note: HW Model is not in OptionUserOptionsResponse directly,
            // but we can show the number of selectable languages as a proxy or omit it.
            if (viewModel.userOptions != null) {
                InfoRow("Languages", viewModel.userOptions?.selectableLanguages?.size?.toString() ?: "--")
            }

            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { viewModel.syncPumpTime() },
                enabled = viewModel.sessionReady && viewModel.activeCommand == null,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (viewModel.activeCommand == "Syncing Time") {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text("Sync Pump Time with Phone")
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, title: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun BolusDialog(viewModel: UserViewModel) {
    AlertDialog(
        onDismissRequest = { if (!viewModel.isProcessingBolus) viewModel.showBolusDialog = false },
        title = { Text("Deliver Bolus") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (viewModel.activeCommand == "Fetching Limits") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Fetching pump limits...")
                    }
                }

                val maxBolus = viewModel.stepBolusInfo?.maxBolusUnits ?: 0.0
                val step = viewModel.stepBolusInfo?.bolusStepUnits ?: 0.1

                Text("Max Bolus: %.2f U".format(maxBolus), style = MaterialTheme.typography.labelMedium)

                OutlinedTextField(
                    value = viewModel.bolusAmountInput,
                    onValueChange = { viewModel.bolusAmountInput = it },
                    label = { Text("Amount (Units)") },
                    suffix = { Text("U") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !viewModel.isProcessingBolus
                )

                Text(
                    "Step size: %.2f U".format(step),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { viewModel.startStepBolus() },
                enabled = !viewModel.isProcessingBolus && viewModel.bolusAmountInput.toDoubleOrNull() != null
            ) {
                if (viewModel.isProcessingBolus) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                } else {
                    Text("Deliver")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = { viewModel.showBolusDialog = false },
                enabled = !viewModel.isProcessingBolus
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun UserOptionsDialog(viewModel: UserViewModel) {
    val options = viewModel.editingUserOptions ?: return

    AlertDialog(
        onDismissRequest = { if (!viewModel.isSavingUserOptions) viewModel.showUserOptionsDialog = false },
        title = { Text("User Options") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("24h Time Display", style = MaterialTheme.typography.bodyLarge)
                            Text("Toggle between 12h and 24h", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = options.timeDisplayType24,
                            onCheckedChange = { viewModel.editingUserOptions = options.copy(timeDisplayType24 = it) },
                            enabled = !viewModel.isSavingUserOptions
                        )
                    }
                }

                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Button Scroll", style = MaterialTheme.typography.bodyLarge)
                            Text("Enable circular scrolling", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = options.buttonScrollOnOff,
                            onCheckedChange = { viewModel.editingUserOptions = options.copy(buttonScrollOnOff = it) },
                            enabled = !viewModel.isSavingUserOptions
                        )
                    }
                }

                item {
                    Column {
                        Text("Beep & Alarm Level: ${options.beepAndAlarm}", style = MaterialTheme.typography.bodyLarge)
                        Slider(
                            value = options.beepAndAlarm.toFloat(),
                            onValueChange = { viewModel.editingUserOptions = options.copy(beepAndAlarm = it.toInt()) },
                            valueRange = 1f..5f,
                            steps = 3,
                            enabled = !viewModel.isSavingUserOptions
                        )
                    }
                }

                item {
                    OutlinedTextField(
                        value = options.lcdOnTimeSec.toString(),
                        onValueChange = {
                            val value = it.toIntOrNull() ?: options.lcdOnTimeSec
                            viewModel.editingUserOptions = options.copy(lcdOnTimeSec = value)
                        },
                        label = { Text("LCD On Time (sec)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !viewModel.isSavingUserOptions
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { viewModel.saveUserOptions() },
                enabled = !viewModel.isSavingUserOptions
            ) {
                if (viewModel.isSavingUserOptions) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                } else {
                    Text("Save")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = { viewModel.showUserOptionsDialog = false },
                enabled = !viewModel.isSavingUserOptions
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun BasalProfileDialog(viewModel: UserViewModel) {
    val rates = viewModel.editingBasalRates ?: return

    AlertDialog(
        onDismissRequest = { if (!viewModel.isSavingBasalProfile) viewModel.showBasalProfileDialog = false },
        title = { Text("Edit Basal Profile") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.height(400.dp)) {
                items(24) { hour ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("%02d:00".format(hour), modifier = Modifier.width(60.dp))
                        OutlinedTextField(
                            value = rates[hour].toString(),
                            onValueChange = {
                                val newRates = rates.toMutableList()
                                newRates[hour] = it.toDoubleOrNull() ?: rates[hour]
                                viewModel.editingBasalRates = newRates
                            },
                            label = { Text("U/h") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !viewModel.isSavingBasalProfile
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { viewModel.saveBasalProfile() },
                enabled = !viewModel.isSavingBasalProfile
            ) {
                if (viewModel.isSavingBasalProfile) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                } else {
                    Text("Save Profile")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = { viewModel.showBasalProfileDialog = false },
                enabled = !viewModel.isSavingBasalProfile
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun BolusOptionsDialog(viewModel: UserViewModel) {
    val options = viewModel.editingBolusOptions ?: return

    AlertDialog(
        onDismissRequest = { if (!viewModel.isSavingBolusOptions) viewModel.showBolusOptionsDialog = false },
        title = { Text("Bolus Options") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Extended Bolus", style = MaterialTheme.typography.bodyLarge)
                            Text("Enable extended bolus feature", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = options.extendedBolusEnabled,
                            onCheckedChange = { viewModel.editingBolusOptions = options.copy(extendedBolusEnabled = it) },
                            enabled = !viewModel.isSavingBolusOptions
                        )
                    }
                }

                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Missed Bolus Reminder", style = MaterialTheme.typography.bodyLarge)
                            Text("Notify if no bolus delivered in windows", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = options.missedBolusConfig != 0,
                            onCheckedChange = { viewModel.editingBolusOptions = options.copy(missedBolusConfig = if (it) 1 else 0) },
                            enabled = !viewModel.isSavingBolusOptions
                        )
                    }
                }

                if (options.missedBolusConfig != 0) {
                    item {
                        Text("Reminder Windows", style = MaterialTheme.typography.titleSmall)
                    }
                    items(options.missedBolusWindows.size) { index ->
                        val window = options.missedBolusWindows[index]
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Column(Modifier.padding(8.dp)) {
                                Text("Window ${index + 1}", style = MaterialTheme.typography.labelSmall)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TimeInputField(
                                        label = "Start",
                                        hour = window.startHour,
                                        minute = window.startMinute,
                                        onValueChange = { h, m ->
                                            val newWindows = options.missedBolusWindows.toMutableList()
                                            newWindows[index] = window.copy(startHour = h, startMinute = m)
                                            viewModel.editingBolusOptions = options.copy(missedBolusWindows = newWindows)
                                        },
                                        enabled = !viewModel.isSavingBolusOptions,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text("to")
                                    TimeInputField(
                                        label = "End",
                                        hour = window.endHour,
                                        minute = window.endMinute,
                                        onValueChange = { h, m ->
                                            val newWindows = options.missedBolusWindows.toMutableList()
                                            newWindows[index] = window.copy(endHour = h, endMinute = m)
                                            viewModel.editingBolusOptions = options.copy(missedBolusWindows = newWindows)
                                        },
                                        enabled = !viewModel.isSavingBolusOptions,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { viewModel.saveBolusOptions() },
                enabled = !viewModel.isSavingBolusOptions
            ) {
                if (viewModel.isSavingBolusOptions) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                } else {
                    Text("Save")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = { viewModel.showBolusOptionsDialog = false },
                enabled = !viewModel.isSavingBolusOptions
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun TimeInputField(
    label: String,
    hour: Int,
    minute: Int,
    onValueChange: (Int, Int) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = "%02d".format(hour),
            onValueChange = { 
                val h = it.toIntOrNull()?.coerceIn(0, 23) ?: hour
                onValueChange(h, minute)
            },
            label = { Text(label) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
            enabled = enabled,
            singleLine = true
        )
        Text(":", modifier = Modifier.padding(horizontal = 4.dp))
        OutlinedTextField(
            value = "%02d".format(minute),
            onValueChange = { 
                val m = it.toIntOrNull()?.coerceIn(0, 59) ?: minute
                onValueChange(hour, m)
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
            enabled = enabled,
            singleLine = true
        )
    }
}

@Composable
private fun TempBasalDialog(viewModel: UserViewModel) {
    AlertDialog(
        onDismissRequest = { if (!viewModel.isProcessingTempBasal) viewModel.showTempBasalDialog = false },
        title = { Text("Set Temporary Basal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = viewModel.tempBasalPercentInput,
                    onValueChange = { viewModel.tempBasalPercentInput = it },
                    label = { Text("Ratio (%)") },
                    suffix = { Text("%") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !viewModel.isProcessingTempBasal
                )
                OutlinedTextField(
                    value = viewModel.tempBasalDurationInput,
                    onValueChange = { viewModel.tempBasalDurationInput = it },
                    label = { Text("Duration (Hours)") },
                    suffix = { Text("h") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !viewModel.isProcessingTempBasal
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { viewModel.startTempBasal() },
                enabled = !viewModel.isProcessingTempBasal
            ) {
                if (viewModel.isProcessingTempBasal) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                } else {
                    Text("Set Rate")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = { viewModel.showTempBasalDialog = false },
                enabled = !viewModel.isProcessingTempBasal
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ExtendedBolusDialog(viewModel: UserViewModel) {
    AlertDialog(
        onDismissRequest = { if (!viewModel.isProcessingExtendedBolus) viewModel.showExtendedBolusDialog = false },
        title = { Text("Start Extended Bolus") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = viewModel.extendedBolusAmountInput,
                    onValueChange = { viewModel.extendedBolusAmountInput = it },
                    label = { Text("Amount (Units)") },
                    suffix = { Text("U") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !viewModel.isProcessingExtendedBolus
                )
                OutlinedTextField(
                    value = viewModel.extendedBolusDurationInput,
                    onValueChange = { viewModel.extendedBolusDurationInput = it },
                    label = { Text("Duration (Half Hours)") },
                    suffix = { Text("x 30m") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !viewModel.isProcessingExtendedBolus
                )

                val halfHours = viewModel.extendedBolusDurationInput.toIntOrNull() ?: 0
                Text(
                    "Total Time: ${halfHours * 30} minutes (${halfHours * 0.5} hours)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { viewModel.startExtendedBolus() },
                enabled = !viewModel.isProcessingExtendedBolus && viewModel.extendedBolusAmountInput.toDoubleOrNull() != null
            ) {
                if (viewModel.isProcessingExtendedBolus) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                } else {
                    Text("Start")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = { viewModel.showExtendedBolusDialog = false },
                enabled = !viewModel.isProcessingExtendedBolus
            ) {
                Text("Cancel")
            }
        }
    )
}