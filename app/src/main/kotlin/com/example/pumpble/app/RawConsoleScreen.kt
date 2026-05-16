package com.example.pumpble.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.pumpble.dana.commands.DanaRsBolusSpeed
import java.util.TimeZone

@Composable
fun RawConsoleView(
    viewModel: PumpViewModel,
    onPermissionRequest: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item { Header(viewModel.connectionState) }
        item {
            if (!viewModel.permissionsGranted) {
                Button(
                    onClick = onPermissionRequest,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.AutoMirrored.Filled.BluetoothSearching, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Grant BLE permissions")
                }
            }
        }
        item { GattProfileSection(viewModel) }
        item { DeviceSection(viewModel) }
        item { SessionSection(viewModel) }
        item { ReadCommandSection(viewModel) }
        item { ControlCommandSection(viewModel) }
    }
}

@Composable
private fun Header(connectionState: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Dana-i Pump Console",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = connectionState,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}

@Composable
private fun GattProfileSection(viewModel: PumpViewModel) {
    SectionTitle("GATT Profile")
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = viewModel.txUuidText,
            onValueChange = { viewModel.txUuidText = it },
            label = { Text("TX characteristic UUID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = viewModel.rxUuidText,
            onValueChange = { viewModel.rxUuidText = it },
            label = { Text("RX characteristic UUID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DeviceSection(viewModel: PumpViewModel) {
    SectionTitle("BLE Device")
    Button(
        onClick = { if (viewModel.isScanning) viewModel.stopScan() else viewModel.startScan() },
        enabled = viewModel.permissionsGranted,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(if (viewModel.isScanning) Icons.Filled.Stop else Icons.AutoMirrored.Filled.BluetoothSearching, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(if (viewModel.isScanning) "Stop scan" else "Scan for pumps")
    }
    Spacer(Modifier.height(10.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        viewModel.devices.forEach { pump ->
            val selected = viewModel.selectedDevice?.address == pump.address
            FilledTonalButton(
                onClick = {
                    viewModel.selectedDevice = pump
                    if (pump.name.length == 10) {
                        viewModel.deviceNameText = pump.name
                    }
                    viewModel.connectSelected()
                },
                enabled = !viewModel.sessionReady && viewModel.activeCommand == null,
                colors = if (selected) {
                    ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                } else {
                    ButtonDefaults.filledTonalButtonColors()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                    Text(pump.name.ifBlank { "Unnamed device" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${pump.address}  RSSI ${pump.rssi}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun SessionSection(viewModel: PumpViewModel) {
    SectionTitle("Session")
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = viewModel.deviceNameText,
            onValueChange = { viewModel.deviceNameText = it },
            label = { Text("Device name for pump check") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = viewModel.ble5PairingKey,
            onValueChange = { viewModel.ble5PairingKey = it },
            label = { Text("BLE5 pairing key") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { viewModel.runHandshake() },
                enabled = viewModel.danaClient != null,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Handshake")
            }
            TextButton(
                onClick = { viewModel.disconnect() },
                enabled = viewModel.danaClient != null,
                modifier = Modifier.weight(1f),
            ) {
                Text("Disconnect")
            }
        }
    }
}

@Composable
private fun ReadCommandSection(viewModel: PumpViewModel) {
    SectionTitle("Read Commands")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CommandRow {
            CommandButton(viewModel, "Initial screen", Icons.Filled.Refresh, modifier = Modifier.weight(1f)) {
                viewModel.runCommand("Initial screen") { generalInitialScreenInformation() }
            }
            CommandButton(viewModel, "Pump check", Icons.Filled.Refresh, modifier = Modifier.weight(1f)) {
                viewModel.runCommand("Pump check") { generalGetPumpCheck() }
            }
        }
        CommandRow {
            CommandButton(viewModel, "Basal rates", Icons.Filled.Refresh, modifier = Modifier.weight(1f)) {
                viewModel.runCommand("Basal rates") { basalGetBasalRate() }
            }
            CommandButton(viewModel, "Profile number", Icons.Filled.Refresh, modifier = Modifier.weight(1f)) {
                viewModel.runCommand("Profile number") { basalGetProfileNumber() }
            }
        }
        CommandRow {
            CommandButton(viewModel, "Bolus options", Icons.Filled.Refresh, modifier = Modifier.weight(1f)) {
                viewModel.runCommand("Bolus options") { bolusGetBolusOption() }
            }
            CommandButton(viewModel, "Step bolus info", Icons.Filled.Refresh, modifier = Modifier.weight(1f)) {
                viewModel.runCommand("Step bolus info") { bolusGetStepBolusInformation() }
            }
        }
        CommandRow {
            CommandButton(viewModel, "Pump time", Icons.Filled.Refresh, modifier = Modifier.weight(1f)) {
                viewModel.runCommand("Pump time") {
                    optionGetPumpUtcAndTimeZone()
                }
            }
            CommandButton(viewModel, "User options", Icons.Filled.Refresh, modifier = Modifier.weight(1f)) {
                viewModel.runCommand("User options") { optionGetUserOption() }
            }
        }
        CommandRow {
            CommandButton(viewModel, "APS history", Icons.Filled.Refresh, modifier = Modifier.weight(1f)) {
                viewModel.runStreamCommand("APS history") { apsHistoryEvents(fromMillis = 0L) }
            }
            CommandButton(viewModel, "History bolus", Icons.Filled.Refresh, modifier = Modifier.weight(1f)) {
                viewModel.runStreamCommand("History bolus") { historyBolus(fromMillis = 0L) }
            }
        }
    }
}

@Composable
private fun ControlCommandSection(viewModel: PumpViewModel) {
    SectionTitle("Write And Control Commands")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Switch(checked = viewModel.controlArmed, onCheckedChange = { viewModel.controlArmed = it })
        Text("Arm test pump commands")
    }
    Spacer(Modifier.height(8.dp))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            NumberField("APS temp %", viewModel.apsTempPercent, { viewModel.apsTempPercent = it }, Modifier.weight(1f))
            CommandButton(viewModel, "Set APS temp", Icons.Filled.Warning, requiresArm = true, modifier = Modifier.weight(1f)) {
                viewModel.runCommand("Set APS temp basal", requiresArm = true) {
                    apsBasalSetTemporaryBasal(percent = viewModel.intValue(viewModel.apsTempPercent, 100))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            NumberField("Basal %", viewModel.basalRatioPercent, { viewModel.basalRatioPercent = it }, Modifier.weight(1f))
            NumberField("Hours", viewModel.basalDurationHours, { viewModel.basalDurationHours = it }, Modifier.weight(1f))
        }
        CommandButton(viewModel, "Set temporary basal", Icons.Filled.Warning, requiresArm = true) {
            viewModel.runCommand("Set temporary basal", requiresArm = true) {
                basalSetTemporaryBasal(
                    ratioPercent = viewModel.intValue(viewModel.basalRatioPercent, 100),
                    durationHours = viewModel.intValue(viewModel.basalDurationHours, 1),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            NumberField("Bolus U", viewModel.bolusUnits, { viewModel.bolusUnits = it }, Modifier.weight(1f))
            CommandButton(viewModel, "Start bolus", Icons.Filled.Warning, requiresArm = true, modifier = Modifier.weight(1f)) {
                viewModel.runCommand("Start bolus", requiresArm = true) {
                    bolusSetStepBolusStart(
                        amountUnits = viewModel.doubleValue(viewModel.bolusUnits, 0.10),
                        speed = DanaRsBolusSpeed.U12_SECONDS,
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            NumberField("Extended U", viewModel.extendedBolusUnits, { viewModel.extendedBolusUnits = it }, Modifier.weight(1f))
            NumberField("Half hours", viewModel.extendedBolusHalfHours, { viewModel.extendedBolusHalfHours = it }, Modifier.weight(1f))
        }
        CommandButton(viewModel, "Start extended bolus", Icons.Filled.Warning, requiresArm = true) {
            viewModel.runCommand("Start extended bolus", requiresArm = true) {
                bolusSetExtendedBolus(
                    amountUnits = viewModel.doubleValue(viewModel.extendedBolusUnits, 0.10),
                    durationHalfHours = viewModel.intValue(viewModel.extendedBolusHalfHours, 1),
                )
            }
        }
        CommandRow {
            CommandButton(viewModel, "Cancel temp", Icons.Filled.Stop, requiresArm = true, modifier = Modifier.weight(1f)) {
                viewModel.runCommand("Cancel temp basal", requiresArm = true) { basalSetCancelTemporaryBasal() }
            }
            CommandButton(viewModel, "Stop bolus", Icons.Filled.Stop, requiresArm = true, modifier = Modifier.weight(1f)) {
                viewModel.runCommand("Stop bolus", requiresArm = true) { bolusSetStepBolusStop() }
            }
        }
        CommandRow {
            CommandButton(viewModel, "Cancel extended", Icons.Filled.Stop, requiresArm = true, modifier = Modifier.weight(1f)) {
                viewModel.runCommand("Cancel extended bolus", requiresArm = true) { bolusSetExtendedBolusCancel() }
            }
            CommandButton(viewModel, "Set pump time", Icons.Filled.Warning, requiresArm = true, modifier = Modifier.weight(1f)) {
                viewModel.runCommand("Set pump time", requiresArm = true) {
                    val timeZone = TimeZone.getDefault()
                    val currentOffsetHours = timeZone.getOffset(System.currentTimeMillis()) / 3_600_000
                    optionSetPumpUtcAndTimeZone(System.currentTimeMillis(), currentOffsetHours)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Column {
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        HorizontalDivider()
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun CommandRow(content: @Composable RowScope.() -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        content()
    }
}

@Composable
private fun CommandButton(
    viewModel: PumpViewModel,
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier.fillMaxWidth(),
    requiresArm: Boolean = false,
    onClick: () -> Unit,
) {
    val enabled = viewModel.danaClient != null && viewModel.sessionReady && viewModel.activeCommand == null && (!requiresArm || viewModel.controlArmed)
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(48.dp),
        colors = if (requiresArm) {
            ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        } else {
            ButtonDefaults.buttonColors()
        },
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}