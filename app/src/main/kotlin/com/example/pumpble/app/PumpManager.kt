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
import com.example.pumpble.dana.protocol.DanaRsHandshake
import com.example.pumpble.dana.protocol.DanaRsHandshakeResult
import com.example.pumpble.dana.protocol.DanaRsHandshakeState
import com.example.pumpble.dana.protocol.DanaRsPacketCodec
import com.example.pumpble.dana.protocol.DanaRsPairingSecrets
import com.example.pumpble.transport.AndroidBleTransport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

class ConnectionLostException(message: String) : Exception(message)

object PumpManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val connectionEvents = MutableSharedFlow<Throwable>(extraBufferCapacity = 1)
    private var prefs: android.content.SharedPreferences? = null

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

    const val DEFAULT_TX_UUID = "0000fff2-0000-1000-8000-00805f9b34fb"
    const val DEFAULT_RX_UUID = "0000fff1-0000-1000-8000-00805f9b34fb"

    private var _transport by mutableStateOf<AndroidBleTransport?>(null)
    private var _packetCodec by mutableStateOf<DanaRsPacketCodec?>(null)
    private var connectionJob: Job? = null

    fun initialize(context: Context) {
        prefs = context.getSharedPreferences("pump_prefs", Context.MODE_PRIVATE)
        val lastAddress = prefs?.getString("last_address", null)
        val lastName = prefs?.getString("last_name", null)
        
        // Note: We can't recreate the BluetoothDevice object here without a scan or known address
        // but we can store the strings to help the UI.
    }

    private fun saveLastDevice(pump: DiscoveredPump) {
        prefs?.edit()?.apply {
            putString("last_address", pump.address)
            putString("last_name", pump.name)
            apply()
        }
    }
    
    fun getLastStoredAddress(): String? = prefs?.getString("last_address", null)
    fun getLastStoredName(): String? = prefs?.getString("last_name", null)

    @SuppressLint("MissingPermission")
    suspend fun connect(context: Context, pump: DiscoveredPump, txUuid: String, rxUuid: String) {
        try {
            disconnect()
            selectedDevice = pump
            saveLastDevice(pump)
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
                        val message = state.cause?.message ?: "Link lost"
                        connectionEvents.tryEmit(ConnectionLostException(message))
                        handleDisconnect(message)
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

    suspend fun runHandshake(deviceName: String, ble5Key: String?): DanaRsHandshakeState {
        val currentTransport = _transport ?: throw ConnectionLostException("Not connected")
        val currentCodec = _packetCodec ?: error("Codec missing")
        
        if (deviceName.length != 10) error("Device name must be 10 characters")

        currentCodec.reset()
        val handshake = DanaRsHandshake(
            codec = currentCodec,
            secrets = DanaRsPairingSecrets(
                ble5PairingKey = ble5Key?.trim()?.ifBlank { null },
            ),
        )
        
        LogManager.log("Starting handshake for $deviceName...")
        
        return withTimeout(45.seconds) {
            val deferredResult = CompletableDeferred<DanaRsHandshakeState>()
            
            kotlinx.coroutines.coroutineScope {
                val handshakeJob = launch {
                    try {
                        currentTransport.notifications.collect { bytes ->
                            val frames = currentCodec.decodeFrames(bytes)
                            for (frame in frames) {
                                when (val res = handshake.onFrame(frame)) {
                                    is DanaRsHandshakeResult.SendNext -> {
                                        currentTransport.write(res.bytes)
                                    }
                                    is DanaRsHandshakeResult.WaitingForPairing -> {
                                        LogManager.log("WAITING FOR PUMP PAIRING - CONFIRM ON PUMP SCREEN")
                                    }
                                    is DanaRsHandshakeResult.Connected -> {
                                        deferredResult.complete(res.state)
                                    }
                                    is DanaRsHandshakeResult.Failed -> {
                                        deferredResult.completeExceptionally(Exception("Handshake failed: ${res.reason}"))
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        deferredResult.completeExceptionally(e)
                    }
                }

                val disconnectWatcher = launch {
                    val error = connectionEvents.first()
                    deferredResult.completeExceptionally(error)
                }

                try {
                    delay(500)
                    currentTransport.write(handshake.start(deviceName))
                    
                    // Wait for result or error
                    val result = deferredResult.await()
                    
                    handshakeJob.cancel()
                    disconnectWatcher.cancel()
                    result
                } catch (e: Exception) {
                    handshakeJob.cancel()
                    disconnectWatcher.cancel()
                    throw e
                }
            }
        }
    }

    fun setSessionReady(ready: Boolean, info: String? = null) {
        sessionReady = ready
        if (ready) {
            connectionState = "Session ready" + (info?.let { " $it" } ?: "")
        }
    }

    suspend fun <R : PumpResponse> execute(command: PumpCommand<R>): R {
        val client = danaClient ?: throw ConnectionLostException("Not connected")
        
        // Race between command execution and connection loss
        return kotlinx.coroutines.coroutineScope {
            val execution = launch { /* handled by withTimeout/execute below */ }
            val disconnectWatcher = launch {
                val error = connectionEvents.first()
                this@coroutineScope.cancel("Connection lost during command", error)
            }
            
            try {
                val result = client.client.execute(command, timeout = 20.seconds)
                disconnectWatcher.cancel()
                result
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e.cause ?: e
            } finally {
                disconnectWatcher.cancel()
            }
        }
    }

    suspend fun <C : PumpResponse, R> executeStream(command: PumpStreamCommand<C, R>): R {
        val client = danaClient ?: error("Not connected")
        return client.client.executeStream(command, timeout = 60.seconds)
    }

    fun getPacketCodec() = _packetCodec
    fun getTransport() = _transport
}