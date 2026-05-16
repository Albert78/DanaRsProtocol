package com.example.pumpble.app

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pumpble.commands.PumpCommand
import com.example.pumpble.commands.PumpResponse
import com.example.pumpble.commands.PumpStreamCommand
import com.example.pumpble.dana.DanaBleProfiles
import com.example.pumpble.dana.DanaPumpClient
import com.example.pumpble.dana.DanaPumpClientFactory
import com.example.pumpble.dana.commands.DanaRsAckResponse
import com.example.pumpble.dana.commands.DanaRsCommands
import com.example.pumpble.dana.commands.DanaRsRawResponse
import com.example.pumpble.dana.protocol.DanaRsHandshake
import com.example.pumpble.dana.protocol.DanaRsHandshakeResult
import com.example.pumpble.dana.protocol.DanaRsHandshakeState
import com.example.pumpble.dana.protocol.DanaRsPacketCodec
import com.example.pumpble.dana.protocol.DanaRsPairingSecrets
import com.example.pumpble.transport.AndroidBleTransport
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

class PumpViewModel(application: Application) : AndroidViewModel(application) {
    private val bluetoothManager = application.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val context = application.applicationContext

    val devices = mutableStateListOf<DiscoveredPump>()
    val logLines = mutableStateListOf<String>()
    val commands = DanaRsCommands()

    var permissionsGranted by mutableStateOf(false)
    var isScanning by mutableStateOf(false)
    var selectedDevice by mutableStateOf<DiscoveredPump?>(null)
    var connectionState by mutableStateOf("Disconnected")
    var sessionReady by mutableStateOf(false)
    var activeCommand by mutableStateOf<String?>(null)
    var controlArmed by mutableStateOf(false)
    var currentScreen by mutableStateOf(AppScreen.RAW_CONSOLE)

    var txUuidText by mutableStateOf(DEFAULT_TX_UUID)
    var rxUuidText by mutableStateOf(DEFAULT_RX_UUID)
    var deviceNameText by mutableStateOf("")
    var ble5PairingKey by mutableStateOf("")

    var apsTempPercent by mutableStateOf("100")
    var basalRatioPercent by mutableStateOf("100")
    var basalDurationHours by mutableStateOf("1")
    var bolusUnits by mutableStateOf("0.10")
    var extendedBolusUnits by mutableStateOf("0.10")
    var extendedBolusHalfHours by mutableStateOf("1")

    private var transport by mutableStateOf<AndroidBleTransport?>(null)
    private var packetCodec by mutableStateOf<DanaRsPacketCodec?>(null)
    var danaClient by mutableStateOf<DanaPumpClient?>(null)
        private set

    private var connectionJob: Job? = null

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (!permissionsGranted) {
            appendLog("BLE permissions missing")
            return
        }

        val scanner = bluetoothManager.adapter?.bluetoothLeScanner
        if (scanner == null) {
            appendLog("BLE scanner unavailable")
            return
        }

        disconnect()
        devices.clear()
        selectedDevice = null
        scanner.startScan(scanCallback)
        isScanning = true
        appendLog("Scanning")
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!isScanning) return
        bluetoothManager.adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        isScanning = false
        appendLog("Scan stopped")
    }

    @SuppressLint("MissingPermission")
    fun connectSelected() {
        val pump = selectedDevice ?: return
        viewModelScope.launch {
            try {
                stopScan()

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
                    context = context,
                    device = pump.device,
                    config = profile.toBlePumpConfig(),
                )
                packetCodec = codec
                transport = connectedTransport

                connectionJob = viewModelScope.launch {
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

    fun disconnect() {
        viewModelScope.launch {
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

    fun runHandshake() {
        val connectedTransport = transport ?: return appendLog("Not connected")
        val codec = packetCodec ?: return appendLog("Codec missing")
        val deviceName = deviceNameText.trim()
        if (deviceName.length != 10) {
            appendLog("Device name must contain exactly 10 characters")
            return
        }

        viewModelScope.launch {
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
                                val frames = codec.decodeFrames(bytes)
                                for (frame in frames) {
                                    when (val result = handshake.onFrame(frame)) {
                                        is DanaRsHandshakeResult.SendNext -> {
                                            connectedTransport.write(result.bytes)
                                        }
                                        is DanaRsHandshakeResult.WaitingForPairing -> {
                                            appendLog("WAITING FOR PUMP PAIRING - CONFIRM ON PUMP SCREEN")
                                        }
                                        is DanaRsHandshakeResult.Connected -> {
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
                                throw error
                            }
                        }
                    }
                    try {
                        delay(500)
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

    fun <R : PumpResponse> runCommand(
        label: String,
        requiresArm: Boolean = false,
        commandFactory: DanaRsCommands.() -> PumpCommand<R>,
    ) {
        if (requiresArm && !controlArmed) {
            appendLog("$label blocked: commands are not armed")
            return
        }
        val client = danaClient ?: return appendLog("Not connected")
        viewModelScope.launch {
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

    fun <C : PumpResponse, R> runStreamCommand(
        label: String,
        commandFactory: DanaRsCommands.() -> PumpStreamCommand<C, R>,
    ) {
        val client = danaClient ?: return appendLog("Not connected")
        viewModelScope.launch {
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

    fun appendLog(message: String) {
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

    fun intValue(value: String, fallback: Int): Int = value.trim().toIntOrNull() ?: fallback
    fun doubleValue(value: String, fallback: Double): Double = value.trim().toDoubleOrNull() ?: fallback

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
            val index = devices.indexOfFirst { it.address == item.address }
            if (index >= 0) devices[index] = item else devices += item
        }

        override fun onScanFailed(errorCode: Int) {
            isScanning = false
            appendLog("Scan failed: $errorCode")
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopScan()
        viewModelScope.launch {
            transport?.close()
        }
    }

    companion object {
        const val DEFAULT_TX_UUID = "0000fff2-0000-1000-8000-00805f9b34fb"
        const val DEFAULT_RX_UUID = "0000fff1-0000-1000-8000-00805f9b34fb"
        const val MAX_LOG_LINES = 200
        val LOG_TIME_FORMAT = SimpleDateFormat("HH:mm:ss", Locale.US)
    }
}

data class DiscoveredPump(
    val name: String,
    val address: String,
    val rssi: Int,
    val device: BluetoothDevice,
)

enum class AppScreen {
    RAW_CONSOLE,
    USER_CONTROL,
}