package com.example.pumpble.app

import android.annotation.SuppressLint
import android.app.Application
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
import com.example.pumpble.dana.commands.DanaRsAckResponse
import com.example.pumpble.dana.commands.DanaRsRawResponse
import com.example.pumpble.dana.protocol.DanaRsHandshake
import com.example.pumpble.dana.protocol.DanaRsHandshakeResult
import com.example.pumpble.dana.protocol.DanaRsHandshakeState
import com.example.pumpble.dana.protocol.DanaRsPairingSecrets
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.TimeZone
import kotlin.time.Duration.Companion.seconds

class RawViewModel(application: Application) : AndroidViewModel(application) {
    private val bluetoothManager = application.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    val devices = mutableStateListOf<DiscoveredPump>()
    var isScanning by mutableStateOf(false)
    var selectedDevice by mutableStateOf<DiscoveredPump?>(null)
    
    var permissionsGranted by mutableStateOf(false)
    var activeCommand by mutableStateOf<String?>(null)
    var controlArmed by mutableStateOf(false)

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

    // Bind to PumpManager
    val connectionState get() = PumpManager.connectionState
    val sessionReady get() = PumpManager.sessionReady
    val danaClient get() = PumpManager.danaClient

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (!permissionsGranted) {
            LogManager.log("BLE permissions missing")
            return
        }

        val scanner = bluetoothManager.adapter?.bluetoothLeScanner
        if (scanner == null) {
            LogManager.log("BLE scanner unavailable")
            return
        }

        viewModelScope.launch {
            PumpManager.disconnect()
        }
        devices.clear()
        selectedDevice = null
        scanner.startScan(scanCallback)
        isScanning = true
        LogManager.log("Scanning")
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!isScanning) return
        bluetoothManager.adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        isScanning = false
        LogManager.log("Scan stopped")
    }

    fun connectSelected() {
        val pump = selectedDevice ?: return
        viewModelScope.launch {
            try {
                stopScan()
                PumpManager.connect(getApplication(), pump, txUuidText, rxUuidText)
            } catch (error: Throwable) {
                // Handled in PumpManager
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            PumpManager.disconnect()
            deviceNameText = ""
            activeCommand = null
        }
    }

    fun runHandshake() {
        val transport = PumpManager.getTransport() ?: return LogManager.log("Not connected")
        val codec = PumpManager.getPacketCodec() ?: return LogManager.log("Codec missing")
        val deviceName = deviceNameText.trim()
        
        if (deviceName.length != 10) {
            LogManager.log("Device name must contain exactly 10 characters")
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
                LogManager.log("Starting handshake for $deviceName...")
                
                val state = withTimeout(45.seconds) {
                    var handshakeState: DanaRsHandshakeState? = null
                    val flowJob = launch {
                        try {
                            transport.notifications.collect { bytes ->
                                val frames = codec.decodeFrames(bytes)
                                for (frame in frames) {
                                    when (val result = handshake.onFrame(frame)) {
                                        is DanaRsHandshakeResult.SendNext -> {
                                            transport.write(result.bytes)
                                        }
                                        is DanaRsHandshakeResult.WaitingForPairing -> {
                                            LogManager.log("WAITING FOR PUMP PAIRING - CONFIRM ON PUMP SCREEN")
                                        }
                                        is DanaRsHandshakeResult.Connected -> {
                                            handshakeState = result.state
                                            cancel("Handshake complete")
                                        }
                                        is DanaRsHandshakeResult.Failed -> {
                                            LogManager.log("Handshake step failed: ${result.reason}")
                                            cancel("Handshake failed: ${result.reason}")
                                        }
                                    }
                                }
                            }
                        } catch (error: Exception) {
                            if (error.message?.startsWith("Handshake complete") != true) throw error
                        }
                    }
                    try {
                        delay(500)
                        transport.write(handshake.start(deviceName))
                        flowJob.join()
                    } catch (error: Exception) {
                        if (error.message?.startsWith("Handshake complete") != true) throw error
                    }
                    handshakeState ?: error("Handshake did not complete")
                }
                
                val modelInfo = if (state.hardwareModel != null) " (Model ${state.hardwareModel}, Prot. v${state.protocol})" else ""
                PumpManager.setSessionReady(true, modelInfo)
                LogManager.log("Handshake successful: ${state.encryptionType}$modelInfo")
                
                state.ble5PairingKeyFromPump?.let {
                    if (ble5PairingKey != it) {
                        ble5PairingKey = it
                        LogManager.log("Updated BLE5 pairing key: $it")
                    }
                }
            } catch (error: Throwable) {
                LogManager.log("Handshake failed: ${error.message}")
            } finally {
                activeCommand = null
            }
        }
    }

    fun <R : PumpResponse> runCommand(
        label: String,
        requiresArm: Boolean = false,
        commandFactory: com.example.pumpble.dana.commands.DanaRsCommands.() -> PumpCommand<R>,
    ) {
        if (requiresArm && !controlArmed) {
            LogManager.log("$label blocked: commands are not armed")
            return
        }
        viewModelScope.launch {
            try {
                activeCommand = label
                val response = PumpManager.execute(PumpManager.commands.commandFactory())
                LogManager.log("$label -> ${response.toLogLine()}")
            } catch (error: Throwable) {
                LogManager.log("$label failed: ${error.message}")
            } finally {
                activeCommand = null
            }
        }
    }

    fun <C : PumpResponse, R> runStreamCommand(
        label: String,
        commandFactory: com.example.pumpble.dana.commands.DanaRsCommands.() -> PumpStreamCommand<C, R>,
    ) {
        viewModelScope.launch {
            try {
                activeCommand = label
                val result = PumpManager.executeStream(PumpManager.commands.commandFactory())
                LogManager.log("$label -> $result")
            } catch (error: Throwable) {
                LogManager.log("$label failed: ${error.message}")
            } finally {
                activeCommand = null
            }
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

    private fun ByteArray.toHex(): String = joinToString(separator = " ") { "%02X".format(it.toInt() and 0xff) }

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
            LogManager.log("Scan failed: $errorCode")
        }
    }

    companion object {
        const val DEFAULT_TX_UUID = "0000fff2-0000-1000-8000-00805f9b34fb"
        const val DEFAULT_RX_UUID = "0000fff1-0000-1000-8000-00805f9b34fb"
    }
}