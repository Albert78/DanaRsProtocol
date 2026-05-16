package com.example.pumpble.app

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.pumpble.commands.PumpCommand
import com.example.pumpble.commands.PumpResponse
import com.example.pumpble.commands.PumpStreamCommand
import com.example.pumpble.dana.DanaBleProfiles
import com.example.pumpble.dana.DanaPumpClient
import com.example.pumpble.dana.DanaPumpClientFactory
import com.example.pumpble.dana.commands.DanaRsAckResponse
import com.example.pumpble.dana.commands.DanaRsBolusSpeed
import com.example.pumpble.dana.commands.DanaRsCommands
import com.example.pumpble.dana.commands.DanaRsRawResponse
import com.example.pumpble.dana.protocol.DanaRsHandshake
import com.example.pumpble.dana.protocol.DanaRsHandshakeResult
import com.example.pumpble.dana.protocol.DanaRsHandshakeState
import com.example.pumpble.dana.protocol.DanaRsPacketCodec
import com.example.pumpble.dana.protocol.DanaRsPairingSecrets
import com.example.pumpble.transport.AndroidBleTransport
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

class MainActivity : ComponentActivity() {
    private val scope = MainScope()
    private val devices = mutableStateListOf<DiscoveredPump>()
    private val logLines = mutableStateListOf<String>()
    private val commands = DanaRsCommands()

    private var permissionsGranted by mutableStateOf(false)
    private var isScanning by mutableStateOf(false)
    private var selectedDevice by mutableStateOf<DiscoveredPump?>(null)
    private var connectionState by mutableStateOf("Disconnected")
    private var sessionReady by mutableStateOf(false)
    private var activeCommand by mutableStateOf<String?>(null)
    private var controlArmed by mutableStateOf(false)
    private var currentScreen by mutableStateOf(AppScreen.RAW_CONSOLE)

    private var txUuidText by mutableStateOf(DEFAULT_TX_UUID)
    private var rxUuidText by mutableStateOf(DEFAULT_RX_UUID)
    private var deviceNameText by mutableStateOf("")
    private var ble5PairingKey by mutableStateOf("")

    private var apsTempPercent by mutableStateOf("100")
    private var basalRatioPercent by mutableStateOf("100")
    private var basalDurationHours by mutableStateOf("1")
    private var bolusUnits by mutableStateOf("0.10")
    private var extendedBolusUnits by mutableStateOf("0.10")
    private var extendedBolusHalfHours by mutableStateOf("1")

    private var transport by mutableStateOf<AndroidBleTransport?>(null)
    private var packetCodec by mutableStateOf<DanaRsPacketCodec?>(null)
    private var danaClient by mutableStateOf<DanaPumpClient?>(null)
    private var connectionJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionsGranted = hasBlePermissions()
        setContent { PumpConsole() }
    }

    override fun onStop() {
        super.onStop()
        stopScan()
        disconnect()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopScan()
        scope.cancel()
    }

    @Composable
    private fun PumpConsole() {
        MaterialTheme(
            colorScheme = lightColorScheme(
                primary = Color(0xFF006B5C),
                secondary = Color(0xFF2B5C88),
                tertiary = Color(0xFF9A5B00),
                error = Color(0xFFB3261E),
                surface = Color(0xFFF7FAF8),
                surfaceVariant = Color(0xFFE4ECE8),
            ),
        ) {
            Scaffold(
                bottomBar = {
                    NavigationBar {
                        NavigationBarItem(
                            selected = currentScreen == AppScreen.RAW_CONSOLE,
                            onClick = { currentScreen = AppScreen.RAW_CONSOLE },
                            icon = { Icon(Icons.Default.DeveloperMode, contentDescription = null) },
                            label = { Text("Raw Console") },
                        )
                        NavigationBarItem(
                            selected = currentScreen == AppScreen.USER_CONTROL,
                            onClick = { currentScreen = AppScreen.USER_CONTROL },
                            icon = { Icon(Icons.Default.SettingsRemote, contentDescription = null) },
                            label = { Text("User Control") },
                        )
                    }
                },
            ) { paddingValues ->
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    when (currentScreen) {
                        AppScreen.RAW_CONSOLE -> RawConsoleView()
                        AppScreen.USER_CONTROL -> UserControlView()
                    }
                }
            }
        }
    }

    @Composable
    private fun RawConsoleView() {
        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) {
            permissionsGranted = hasBlePermissions()
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item { Header() }
            item {
                if (!permissionsGranted) {
                    Button(
                        onClick = { permissionLauncher.launch(BLE_PERMISSIONS) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.BluetoothSearching, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Grant BLE permissions")
                    }
                }
            }
            item { GattProfileSection() }
            item { DeviceSection() }
            item { SessionSection() }
            item { ReadCommandSection() }
            item { ControlCommandSection() }
            item { LogSection() }
        }
    }

    @Composable
    private fun UserControlView() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Default.SettingsRemote,
                contentDescription = null,
                modifier = Modifier.height(64.dp).width(64.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "User Control Screen",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Hier wird die Nutzeroberfläche entstehen.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }

    @Composable
    private fun Header() {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Dana-i Test Pump",
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
    private fun GattProfileSection() {
        SectionTitle("GATT Profile")
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = txUuidText,
                onValueChange = { txUuidText = it },
                label = { Text("TX characteristic UUID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = rxUuidText,
                onValueChange = { rxUuidText = it },
                label = { Text("RX characteristic UUID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    @Composable
    private fun DeviceSection() {
        SectionTitle("BLE Device")
        Button(
            onClick = { if (isScanning) stopScan() else startScan() },
            enabled = permissionsGranted,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(if (isScanning) Icons.Filled.Stop else Icons.AutoMirrored.Filled.BluetoothSearching, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (isScanning) "Stop scan" else "Scan for pumps")
        }
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            devices.forEach { pump ->
                val selected = selectedDevice?.address == pump.address
                FilledTonalButton(
                    onClick = {
                        selectedDevice = pump
                        // Auto-fill device name if it looks like a Dana serial (10 chars)
                        if (pump.name.length == 10) {
                            deviceNameText = pump.name
                        }
                        connectSelected()
                    },
                    enabled = !sessionReady && activeCommand == null,
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
    private fun SessionSection() {
        SectionTitle("Session")
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = deviceNameText,
                onValueChange = { deviceNameText = it },
                label = { Text("Device name for pump check") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = ble5PairingKey,
                onValueChange = { ble5PairingKey = it },
                label = { Text("BLE5 pairing key") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { runHandshake() },
                    enabled = transport != null,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Handshake")
                }
                TextButton(
                    onClick = { disconnect() },
                    enabled = transport != null,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Disconnect")
                }
            }
        }
    }

    @Composable
    private fun ReadCommandSection() {
        SectionTitle("Read Commands")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CommandRow {
                CommandButton("Initial screen", Icons.Filled.Refresh, modifier = Modifier.weight(1f)) {
                    runCommand("Initial screen") { generalInitialScreenInformation() }
                }
                CommandButton("Pump check", Icons.Filled.Refresh, modifier = Modifier.weight(1f)) {
                    runCommand("Pump check") { generalGetPumpCheck() }
                }
            }
            CommandRow {
                CommandButton("Basal rates", Icons.Filled.Refresh, modifier = Modifier.weight(1f)) {
                    runCommand("Basal rates") { basalGetBasalRate() }
                }
                CommandButton("Profile number", Icons.Filled.Refresh, modifier = Modifier.weight(1f)) {
                    runCommand("Profile number") { basalGetProfileNumber() }
                }
            }
            CommandRow {
                CommandButton("Bolus options", Icons.Filled.Refresh, modifier = Modifier.weight(1f)) {
                    runCommand("Bolus options") { bolusGetBolusOption() }
                }
                CommandButton("Step bolus info", Icons.Filled.Refresh, modifier = Modifier.weight(1f)) {
                    runCommand("Step bolus info") { bolusGetStepBolusInformation() }
                }
            }
            CommandRow {
                CommandButton("Pump time", Icons.Filled.Refresh, modifier = Modifier.weight(1f)) {
                    runCommand("Pump time") {
                        // Doesn't work on Dana-i
                        //optionGetPumpTime()

                        optionGetPumpUtcAndTimeZone()
                    }
                }
                CommandButton("User options", Icons.Filled.Refresh, modifier = Modifier.weight(1f)) {
                    runCommand("User options") { optionGetUserOption() }
                }
            }
            CommandRow {
                CommandButton("APS history", Icons.Filled.Refresh, modifier = Modifier.weight(1f)) {
                    runStreamCommand("APS history") { apsHistoryEvents(fromMillis = 0L) }
                }
                CommandButton("History bolus", Icons.Filled.Refresh, modifier = Modifier.weight(1f)) {
                    runStreamCommand("History bolus") { historyBolus(fromMillis = 0L) }
                }
            }
        }
    }

    @Composable
    private fun ControlCommandSection() {
        SectionTitle("Write And Control Commands")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Switch(checked = controlArmed, onCheckedChange = { controlArmed = it })
            Text("Arm test pump commands")
        }
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                NumberField("APS temp %", apsTempPercent, { apsTempPercent = it }, Modifier.weight(1f))
                CommandButton("Set APS temp", Icons.Filled.Warning, requiresArm = true, modifier = Modifier.weight(1f)) {
                    runCommand("Set APS temp basal", requiresArm = true) {
                        apsBasalSetTemporaryBasal(percent = intValue(apsTempPercent, 100))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                NumberField("Basal %", basalRatioPercent, { basalRatioPercent = it }, Modifier.weight(1f))
                NumberField("Hours", basalDurationHours, { basalDurationHours = it }, Modifier.weight(1f))
            }
            CommandButton("Set temporary basal", Icons.Filled.Warning, requiresArm = true) {
                runCommand("Set temporary basal", requiresArm = true) {
                    basalSetTemporaryBasal(
                        ratioPercent = intValue(basalRatioPercent, 100),
                        durationHours = intValue(basalDurationHours, 1),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                NumberField("Bolus U", bolusUnits, { bolusUnits = it }, Modifier.weight(1f))
                CommandButton("Start bolus", Icons.Filled.Warning, requiresArm = true, modifier = Modifier.weight(1f)) {
                    runCommand("Start bolus", requiresArm = true) {
                        bolusSetStepBolusStart(
                            amountUnits = doubleValue(bolusUnits, 0.10),
                            speed = DanaRsBolusSpeed.U12_SECONDS,
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                NumberField("Extended U", extendedBolusUnits, { extendedBolusUnits = it }, Modifier.weight(1f))
                NumberField("Half hours", extendedBolusHalfHours, { extendedBolusHalfHours = it }, Modifier.weight(1f))
            }
            CommandButton("Start extended bolus", Icons.Filled.Warning, requiresArm = true) {
                runCommand("Start extended bolus", requiresArm = true) {
                    bolusSetExtendedBolus(
                        amountUnits = doubleValue(extendedBolusUnits, 0.10),
                        durationHalfHours = intValue(extendedBolusHalfHours, 1),
                    )
                }
            }
            CommandRow {
                CommandButton("Cancel temp", Icons.Filled.Stop, requiresArm = true, modifier = Modifier.weight(1f)) {
                    runCommand("Cancel temp basal", requiresArm = true) { basalSetCancelTemporaryBasal() }
                }
                CommandButton("Stop bolus", Icons.Filled.Stop, requiresArm = true, modifier = Modifier.weight(1f)) {
                    runCommand("Stop bolus", requiresArm = true) { bolusSetStepBolusStop() }
                }
            }
            CommandRow {
                CommandButton("Cancel extended", Icons.Filled.Stop, requiresArm = true, modifier = Modifier.weight(1f)) {
                    runCommand("Cancel extended bolus", requiresArm = true) { bolusSetExtendedBolusCancel() }
                }
                CommandButton("Set pump time", Icons.Filled.Warning, requiresArm = true, modifier = Modifier.weight(1f)) {
                    runCommand("Set pump time", requiresArm = true) {
                        // Doesn't seem to work for Dana-i
                        //optionSetPumpTime(System.currentTimeMillis())

                        val timeZone = TimeZone.getDefault()
                        val currentOffsetHours = timeZone.getOffset(System.currentTimeMillis()) / 3_600_000

                        optionSetPumpUtcAndTimeZone(System.currentTimeMillis(), currentOffsetHours)
                    }
                }
            }
        }
    }

    @Composable
    private fun LogSection() {
        SectionTitle("Log")
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            logLines.take(80).forEach { line ->
                Text(line, style = MaterialTheme.typography.bodySmall)
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
        text: String,
        icon: ImageVector,
        modifier: Modifier = Modifier.fillMaxWidth(),
        requiresArm: Boolean = false,
        onClick: () -> Unit,
    ) {
        val enabled = danaClient != null && sessionReady && activeCommand == null && (!requiresArm || controlArmed)
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

    @SuppressLint("MissingPermission")
    private fun startScan() {
        if (!hasBlePermissions()) {
            permissionsGranted = false
            appendLog("BLE permissions missing")
            return
        }

        val scanner = bluetoothManager.adapter?.bluetoothLeScanner
        if (scanner == null) {
            appendLog("BLE scanner unavailable")
            return
        }

        // Reset previous connection states
        disconnect()

        devices.clear()
        selectedDevice = null
        scanner.startScan(scanCallback)
        isScanning = true
        appendLog("Scanning")
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        if (!isScanning) return
        bluetoothManager.adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        isScanning = false
        appendLog("Scan stopped")
    }

    @SuppressLint("MissingPermission")
    private fun connectSelected() {
        val pump = selectedDevice ?: return
        scope.launch {
            try {
                stopScan()

                // Cleanup previous connection
                connectionJob?.cancel()
                transport?.close()
                sessionReady = false
                danaClient = null
                transport = null
                packetCodec = null
                connectionJob = null

                connectionState = "Connecting to ${pump.name.ifBlank { pump.address }}"

                val codec = DanaRsPacketCodec()
                val profile = DanaBleProfiles.danaI(
                    txCharacteristicUuid = UUID.fromString(txUuidText.trim()),
                    rxCharacteristicUuid = UUID.fromString(rxUuidText.trim()),
                )
                val connectedTransport = AndroidBleTransport.connect(
                    context = this@MainActivity,
                    device = pump.device,
                    config = profile.toBlePumpConfig(),
                )
                packetCodec = codec
                transport = connectedTransport

                // Monitor connection state
                connectionJob = scope.launch {
                    connectedTransport.state.collect { state ->
                        if (state is AndroidBleTransport.TransportState.Disconnected) {
                            connectionState = if (state.cause != null) {
                                "Disconnected: ${state.cause!!.message}"
                            } else {
                                "Disconnected"
                            }
                            sessionReady = false
                            danaClient = null
                            transport = null
                            packetCodec = null
                            appendLog("Transport state: $connectionState")
                        }
                    }
                }

                sessionReady = false
                danaClient = DanaPumpClientFactory.createDanaRsCompatible(
                    transport = connectedTransport,
                    codec = codec,
                )
                connectionState = "Connected to ${pump.name.ifBlank { pump.address }}"
                appendLog("Connected")
            } catch (error: Throwable) {
                connectionState = "Connection failed"
                appendLog("Connect failed: ${error.message}")
            }
        }
    }

    private fun disconnect() {
        scope.launch {
            try {
                transport?.close()
            } catch (error: SecurityException) {
                appendLog("Disconnect failed: ${error.message}")
            }
            deviceNameText = ""
            activeCommand = null
            transport = null
            packetCodec = null
            danaClient = null
            sessionReady = false
            connectionState = "Disconnected"
            appendLog("Disconnected")
        }
    }

    private fun runHandshake() {
        val connectedTransport = transport ?: return appendLog("Not connected")
        val codec = packetCodec ?: return appendLog("Codec missing")
        val deviceName = deviceNameText.trim()
        if (deviceName.length != 10) {
            appendLog("Device name must contain exactly 10 characters")
            return
        }

        scope.launch {
            try {
                activeCommand = "Handshake"
                codec.reset()
                val handshake = DanaRsHandshake(
                    codec = codec,
                    secrets = DanaRsPairingSecrets(
                        ble5PairingKey = ble5PairingKey.trim().ifBlank { null },
                    ),
                )
                connectionState = "Handshake running"
                appendLog("Starting handshake for $deviceName...")
                val state = withTimeout(45.seconds) {
                    var handshakeState: DanaRsHandshakeState? = null
                    val flowJob = launch {
                        try {
                            connectedTransport.notifications.collect { bytes ->
                                appendLog("Received notification: ${bytes.size} bytes")
                                val frames = codec.decodeFrames(bytes)
                                appendLog("Decoded ${frames.size} frames")
                                for (frame in frames) {
                                    appendLog("Frame: cmd=${frame.commandId.value} flags=${frame.flags}")
                                    when (val result = handshake.onFrame(frame)) {
                                        is DanaRsHandshakeResult.SendNext -> {
                                            appendLog("Handshake step: SendNext (${result.bytes.size} bytes)")
                                            connectedTransport.write(result.bytes)
                                        }
                                        is DanaRsHandshakeResult.WaitingForPairing -> {
                                            appendLog("WAITING FOR PUMP PAIRING - CONFIRM ON PUMP SCREEN")
                                        }
                                        is DanaRsHandshakeResult.Connected -> {
                                            appendLog("Handshake step: Connected!")
                                            handshakeState = result.state
                                            cancel("Handshake complete")
                                        }
                                        is DanaRsHandshakeResult.Failed -> {
                                            appendLog("Handshake step failed: ${result.reason}")
                                            cancel("Handshake failed: ${result.reason}")
                                        }
                                    }
                                }
                            }
                        } catch (error: Exception) {
                            if (error.message?.startsWith("Handshake complete") != true) {
                                appendLog("Collection error: ${error.message}")
                                throw error
                            }
                        }
                    }
                    try {
                        delay(500) // Give the pump a moment after notification enable
                        appendLog("Sending PUMP_CHECK for $deviceName...")
                        connectedTransport.write(handshake.start(deviceName))
                        flowJob.join()
                    } catch (error: Exception) {
                        if (error.message?.startsWith("Handshake complete") != true) {
                            throw error
                        }
                    }
                    handshakeState ?: error("Handshake did not complete - state is null")
                }
                sessionReady = true
                val modelInfo = if (state.hardwareModel != null) " (Model ${state.hardwareModel}, Prot. v${state.protocol})" else ""
                connectionState = "Session ready$modelInfo"
                appendLog("Handshake successful: ${state.encryptionType}$modelInfo")
                state.ble5PairingKeyFromPump?.let {
                    if (ble5PairingKey != it) {
                        ble5PairingKey = it
                        appendLog("Updated BLE5 pairing key: $it")
                    }
                }
            } catch (error: Throwable) {
                connectionState = "Handshake failed"
                appendLog("Handshake failed: ${error.message}")
            } finally {
                activeCommand = null
            }
        }
    }

    private fun <R : PumpResponse> runCommand(
        label: String,
        requiresArm: Boolean = false,
        commandFactory: DanaRsCommands.() -> PumpCommand<R>,
    ) {
        if (requiresArm && !controlArmed) {
            appendLog("$label blocked: commands are not armed")
            return
        }
        val client = danaClient ?: return appendLog("Not connected")
        scope.launch {
            try {
                activeCommand = label
                val response = client.client.execute(commands.commandFactory(), timeout = 20.seconds)
                appendLog("$label -> ${response.toLogLine()}")
            } catch (error: Throwable) {
                appendLog("$label failed: ${error.message}")
            } finally {
                activeCommand = null
            }
        }
    }

    private fun <C : PumpResponse, R> runStreamCommand(
        label: String,
        commandFactory: DanaRsCommands.() -> PumpStreamCommand<C, R>,
    ) {
        val client = danaClient ?: return appendLog("Not connected")
        scope.launch {
            try {
                activeCommand = label
                val result = client.client.executeStream(commands.commandFactory(), timeout = 60.seconds)
                appendLog("$label -> $result")
            } catch (error: Throwable) {
                appendLog("$label failed: ${error.message}")
            } finally {
                activeCommand = null
            }
        }
    }

    private fun hasBlePermissions(): Boolean {
        return BLE_PERMISSIONS.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    }

    private fun appendLog(message: String) {
        val time = LOG_TIME_FORMAT.format(Date())
        logLines.add(0, "$time  $message")
        while (logLines.size > MAX_LOG_LINES) {
            logLines.removeAt(logLines.lastIndex)
        }
    }

    private fun PumpResponse.toLogLine(): String {
        return when (this) {
            is DanaRsAckResponse -> "status=$status result=$resultCode"
            is DanaRsRawResponse -> {
                val result = resultCode?.let { " result=$it" }.orEmpty()
                "status=$status$result payload=${payload.toHex()}"
            }
            else -> toString()
        }
    }

    private fun ByteArray.toHex(): String {
        if (isEmpty()) return "<empty>"
        return joinToString(separator = " ") { byte -> "%02X".format(byte.toInt() and 0xff) }
    }

    private fun intValue(value: String, fallback: Int): Int = value.trim().toIntOrNull() ?: fallback
    private fun doubleValue(value: String, fallback: Double): Double = value.trim().toDoubleOrNull() ?: fallback

    private val bluetoothManager: BluetoothManager
        get() = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val item = DiscoveredPump(
                name = device.name.orEmpty(),
                address = device.address,
                rssi = result.rssi,
                device = device,
            )
            runOnUiThread {
                val index = devices.indexOfFirst { it.address == item.address }
                if (index >= 0) devices[index] = item else devices += item
            }
        }

        override fun onScanFailed(errorCode: Int) {
            runOnUiThread {
                isScanning = false
                appendLog("Scan failed: $errorCode")
            }
        }
    }

    private enum class AppScreen {
        RAW_CONSOLE,
        USER_CONTROL,
    }

    private companion object {
        val BLE_PERMISSIONS = arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
        )

        const val DEFAULT_TX_UUID = "0000fff2-0000-1000-8000-00805f9b34fb"
        const val DEFAULT_RX_UUID = "0000fff1-0000-1000-8000-00805f9b34fb"
        const val MAX_LOG_LINES = 200

        val LOG_TIME_FORMAT = SimpleDateFormat("HH:mm:ss", Locale.US)
    }
}

private data class DiscoveredPump(
    val name: String,
    val address: String,
    val rssi: Int,
    val device: BluetoothDevice,
)