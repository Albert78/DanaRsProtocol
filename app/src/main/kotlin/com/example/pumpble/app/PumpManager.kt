package com.example.pumpble.app

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.pumpble.commands.PumpCommand
import com.example.pumpble.commands.PumpResponse
import com.example.pumpble.commands.PumpStreamCommand
import com.example.pumpble.dana.DanaBleProfiles
import com.example.pumpble.dana.DanaPumpClient
import com.example.pumpble.dana.DanaPumpClientFactory
import com.example.pumpble.dana.commands.DanaRsCommands
import com.example.pumpble.dana.commands.basal.BasalRateProfileResponse
import com.example.pumpble.dana.commands.bolus.BolusOptionResponse
import com.example.pumpble.dana.commands.bolus.BolusStepBolusInformationResponse
import com.example.pumpble.dana.commands.general.GeneralInitialScreenInformationResponse
import com.example.pumpble.dana.commands.options.OptionPumpUtcAndTimeZoneResponse
import com.example.pumpble.dana.commands.options.OptionUserOptionsResponse
import com.example.pumpble.dana.protocol.DanaRsPacketCodec
import com.example.pumpble.transport.AndroidBleTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

object PumpManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val commands = DanaRsCommands()

    var connectionState by mutableStateOf("Disconnected")
    var sessionReady by mutableStateOf(false)
    var selectedDevice by mutableStateOf<DiscoveredPump?>(null)
    var danaClient by mutableStateOf<DanaPumpClient?>(null)

    // Decoded Pump State
    var lastSyncTime by mutableStateOf<Long?>(null)
    var pumpStatus by mutableStateOf<GeneralInitialScreenInformationResponse?>(null)
    var userOptions by mutableStateOf<OptionUserOptionsResponse?>(null)
    var bolusOptions by mutableStateOf<BolusOptionResponse?>(null)
    var stepBolusInfo by mutableStateOf<BolusStepBolusInformationResponse?>(null)
    var basalRateInfo by mutableStateOf<BasalRateProfileResponse?>(null)
    var pumpTimeInfo by mutableStateOf<OptionPumpUtcAndTimeZoneResponse?>(null)

    private var _transport by mutableStateOf<AndroidBleTransport?>(null)
    private var _packetCodec by mutableStateOf<DanaRsPacketCodec?>(null)
    private var connectionJob: Job? = null

    fun initialize() {
        // Global initialization if needed
    }

    @SuppressLint("MissingPermission")
    suspend fun connect(context: Context, pump: DiscoveredPump, txUuid: String, rxUuid: String) {
        try {
            disconnect()
            selectedDevice = pump
            connectionState = "Connecting to ${pump.name.ifBlank { pump.address }}"

            val codec = DanaRsPacketCodec()
            val profile = DanaBleProfiles.danaI(
                txCharacteristicUuid = UUID.fromString(txUuid.trim()),
                rxCharacteristicUuid = UUID.fromString(rxUuid.trim()),
            )
            val connectedTransport = AndroidBleTransport.connect(
                context = context,
                device = pump.device,
                config = profile.toBlePumpConfig(),
            )

            _packetCodec = codec
            _transport = connectedTransport

            connectionJob = scope.launch {
                connectedTransport.state.collect { state ->
                    if (state is AndroidBleTransport.TransportState.Disconnected) {
                        handleDisconnect(state.cause?.message)
                    }
                }
            }

            danaClient = DanaPumpClientFactory.createDanaRsCompatible(
                transport = connectedTransport,
                codec = codec,
            )
            connectionState = "Connected to ${pump.name.ifBlank { pump.address }}"
            LogManager.log("Connected")
        } catch (error: Throwable) {
            connectionState = "Connection failed"
            LogManager.log("Connect failed: ${error.message}")
            throw error
        }
    }

    private fun handleDisconnect(reason: String?) {
        connectionState = if (reason != null) "Disconnected: $reason" else "Disconnected"
        sessionReady = false
        danaClient = null
        _transport = null
        _packetCodec = null
        LogManager.log("Transport state: $connectionState")
    }

    suspend fun disconnect() {
        try {
            _transport?.close()
        } catch (error: Exception) {
            LogManager.log("Disconnect error: ${error.message}")
        }
        connectionJob?.cancel()
        handleDisconnect(null)
    }

    fun setSessionReady(ready: Boolean, info: String? = null) {
        sessionReady = ready
        if (ready) {
            connectionState = "Session ready" + (info?.let { " $it" } ?: "")
        }
    }

    suspend fun <R : PumpResponse> execute(command: PumpCommand<R>): R {
        val client = danaClient ?: error("Not connected")
        return client.client.execute(command, timeout = 20.seconds)
    }

    suspend fun <C : PumpResponse, R> executeStream(command: PumpStreamCommand<C, R>): R {
        val client = danaClient ?: error("Not connected")
        return client.client.executeStream(command, timeout = 60.seconds)
    }

    fun getPacketCodec() = _packetCodec
    fun getTransport() = _transport
}