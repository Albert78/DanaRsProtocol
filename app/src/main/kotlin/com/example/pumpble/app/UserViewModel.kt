package com.example.pumpble.app

import android.app.Application
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pumpble.dana.commands.DanaGlucoseUnits
import com.example.pumpble.dana.commands.DanaRsBolusSpeed
import com.example.pumpble.dana.commands.bolus.BolusOptionResponse
import com.example.pumpble.dana.commands.bolus.BolusRateResponse
import com.example.pumpble.dana.commands.options.DanaRsUserOptions
import kotlinx.coroutines.launch

class UserViewModel(application: Application) : AndroidViewModel(application) {
    private val bluetoothManager = application.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    // Bind to PumpManager
    val connectionState get() = PumpManager.connectionState
    var selectedDevice by PumpManager::selectedDevice
    val sessionReady get() = PumpManager.sessionReady
    val lastSyncTime get() = PumpManager.lastSyncTime
    val pumpStatus get() = PumpManager.pumpStatus
    val userOptions get() = PumpManager.userOptions
    val bolusOptions get() = PumpManager.bolusOptions
    val bolusRate get() = PumpManager.bolusRate
    val stepBolusInfo get() = PumpManager.stepBolusInfo
    val basalRateInfo get() = PumpManager.basalRateInfo
    val pumpTimeInfo get() = PumpManager.pumpTimeInfo

    var activeCommand by mutableStateOf<String?>(null)

    // Scan logic for User Screen
    val discoveredDevices = mutableStateListOf<DiscoveredPump>()
    var isScanning by mutableStateOf(false)
    var isSearchingLastDevice by mutableStateOf(false)

    // Dialog States
    var showBolusDialog by mutableStateOf(false)
    var bolusAmountInput by mutableStateOf("0.00")
    var bolusSpeed by mutableStateOf(DanaRsBolusSpeed.U12_SECONDS)
    var isProcessingBolus by mutableStateOf(false)

    // User Options Dialog State
    var showUserOptionsDialog by mutableStateOf(false)
    var editingUserOptions by mutableStateOf<DanaRsUserOptions?>(null)
    var isSavingUserOptions by mutableStateOf(false)
    var userOptionsTargetInput by mutableStateOf("0")

    // Bolus Options Dialog State
    var showBolusOptionsDialog by mutableStateOf(false)
    var editingBolusOptions by mutableStateOf<BolusOptionResponse?>(null)
    var editingBolusRate by mutableStateOf<BolusRateResponse?>(null)
    var isSavingBolusOptions by mutableStateOf(false)

    // Basal Profiles Dialog State
    var showBasalProfilesDialog by mutableStateOf(false)
    var selectedBasalProfileIndex by mutableStateOf(0) // 0:A, 1:B, 2:C, 3:D
    var editingBasalRates by mutableStateOf<List<Double>?>(null)
    var isSavingBasalProfiles by mutableStateOf(false)
    var isLoadingBasalProfile by mutableStateOf(false)

    // Bolus Profile Dialog State (CIR, CF)
    var showBolusProfileDialog by mutableStateOf(false)
    var editingCirValues by mutableStateOf<List<Double>?>(null)
    var editingCfValues by mutableStateOf<List<Double>?>(null)
    var isSavingBolusProfile by mutableStateOf(false)

    // Temp Basal Dialog State
    var showTempBasalDialog by mutableStateOf(false)
    var tempBasalPercentInput by mutableStateOf("100")
    var tempBasalDurationInput by mutableStateOf("1")
    var isProcessingTempBasal by mutableStateOf(false)

    // Extended Bolus Dialog State
    var showExtendedBolusDialog by mutableStateOf(false)
    var extendedBolusAmountInput by mutableStateOf("0.00")
    var extendedBolusDurationInput by mutableStateOf("1") // In half hours
    var isProcessingExtendedBolus by mutableStateOf(false)

    @android.annotation.SuppressLint("MissingPermission")
    fun startDiscovery() {
        val scanner = bluetoothManager.adapter?.bluetoothLeScanner ?: return
        discoveredDevices.clear()
        isScanning = true

        val lastAddress = PumpManager.getLastStoredAddress()
        isSearchingLastDevice = lastAddress != null && selectedDevice == null

        scanner.startScan(scanCallback)

        // Auto-stop scan after some time
        viewModelScope.launch {
            kotlinx.coroutines.delay(15000)
            stopDiscovery()
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    fun stopDiscovery() {
        if (!isScanning) return
        bluetoothManager.adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        isScanning = false
        isSearchingLastDevice = false
    }

    private val scanCallback = object : ScanCallback() {
        @android.annotation.SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val name = device.name.orEmpty()

            // Dana pumps always have a 10-character serial number as their name
            if (name.length != 10) return

            val item = DiscoveredPump(
                name = name,
                address = device.address,
                rssi = result.rssi,
                device = device,
            )

            val index = discoveredDevices.indexOfFirst { it.address == item.address }
            if (index >= 0) discoveredDevices[index] = item else discoveredDevices += item

            // Auto-select last device
            if (isSearchingLastDevice && item.address == PumpManager.getLastStoredAddress()) {
                selectedDevice = item
                isSearchingLastDevice = false
            }
        }
    }

    fun toggleConnection(context: Context) {
        if (sessionReady) {
            viewModelScope.launch {
                PumpManager.disconnect()
            }
        } else {
            connectAndHandshake(context)
        }
    }

    fun connectAndHandshake(context: Context) {
        val pump = selectedDevice ?: return
        viewModelScope.launch {
            try {
                activeCommand = "Connecting"
                PumpManager.connect(context, pump, PumpManager.DEFAULT_TX_UUID, PumpManager.DEFAULT_RX_UUID)

                activeCommand = "Handshake"
                val deviceName = pump.name.ifBlank { "" }
                if (deviceName.length == 10) {
                    val state = PumpManager.runHandshake(deviceName, null)
                    val modelInfo = if (state.hardwareModel != null) " (Model ${state.hardwareModel}, Prot. v${state.protocol})" else ""
                    PumpManager.setSessionReady(true, modelInfo)
                    LogManager.log("Handshake successful")
                    refreshAllStatus()
                } else {
                    LogManager.log("Handshake failed: Device name not 10 chars")
                }
            } catch (error: ConnectionLostException) {
                LogManager.log("Connection lost during setup")
            } catch (error: Throwable) {
                LogManager.log("Setup failed: ${error.message}")
            } finally {
                activeCommand = null
            }
        }
    }

    fun syncPumpTime() {
        if (!sessionReady) return
        viewModelScope.launch {
            try {
                activeCommand = "Syncing Time"
                val timeZone = java.util.TimeZone.getDefault()
                val currentTime = System.currentTimeMillis()
                val currentOffsetHours = timeZone.getOffset(currentTime) / 3_600_000

                LogManager.log("Syncing pump time (UTC with offset $currentOffsetHours)...")
                val response = PumpManager.execute(
                    PumpManager.commands.optionSetPumpUtcAndTimeZone(currentTime, currentOffsetHours)
                )
                LogManager.log("Time sync result: ${response.status}")
                refreshAllStatus()
            } catch (error: Throwable) {
                LogManager.log("Time sync failed: ${error.message}")
            } finally {
                activeCommand = null
            }
        }
    }

    fun refreshAllStatus() {
        if (!sessionReady) {
            LogManager.log("Refresh failed: Session not ready")
            return
        }

        viewModelScope.launch {
            try {
                activeCommand = "Syncing Status"
                LogManager.log("Refreshing pump status...")

                // Execute all sync commands via PumpManager
                PumpManager.pumpStatus = PumpManager.execute(PumpManager.commands.generalInitialScreenInformation())
                PumpManager.basalRateInfo = PumpManager.execute(PumpManager.commands.basalGetBasalRate())
                PumpManager.userOptions = PumpManager.execute(PumpManager.commands.optionGetUserOption())
                PumpManager.bolusOptions = PumpManager.execute(PumpManager.commands.bolusGetBolusOption())
                PumpManager.stepBolusInfo = PumpManager.execute(PumpManager.commands.bolusGetStepBolusInformation())
                PumpManager.pumpTimeInfo = PumpManager.execute(PumpManager.commands.optionGetPumpUtcAndTimeZone())

                PumpManager.lastSyncTime = System.currentTimeMillis()
                LogManager.log("Sync successful")
            } catch (error: ConnectionLostException) {
                LogManager.log("Connection lost during sync")
            } catch (error: Throwable) {
                val message = error.message ?: "Unknown error"
                if (message.contains("0x08") || message.contains("BUSY") || message.contains("ORDER_DELIVERING")) {
                    LogManager.log("Sync failed: Pump is BUSY (Delivering Bolus?)")
                } else {
                    LogManager.log("Sync failed: $message")
                }
            } finally {
                activeCommand = null
            }
        }
    }

    fun openUserOptionsDialog() {
        if (!sessionReady) {
            LogManager.log("Action failed: Handshake required")
            return
        }

        viewModelScope.launch {
            try {
                activeCommand = "Loading Options"
                val response = PumpManager.execute(PumpManager.commands.optionGetUserOption())
                PumpManager.userOptions = response

                // Convert response to editable object
                editingUserOptions = DanaRsUserOptions(
                    hwModel = response.selectableLanguages.size.let { if (it > 0) 7 else 0 }, // Simplified HW model detection
                    timeDisplayType24 = response.timeDisplayType24,
                    buttonScrollOnOff = response.buttonScrollOnOff,
                    beepAndAlarm = response.beepAndAlarm,
                    lcdOnTimeSec = response.lcdOnTimeSec,
                    backlightOnTimeSec = response.backlightOnTimeSec,
                    selectedLanguage = response.selectedLanguage,
                    units = response.units,
                    shutdownHour = response.shutdownHour,
                    lowReservoirRate = response.lowReservoirRate,
                    cannulaVolume = response.cannulaVolume,
                    refillAmount = response.refillAmount,
                    target = response.target ?: 0
                )
                userOptionsTargetInput = (response.target ?: 0).toString()
                showUserOptionsDialog = true
            } catch (e: Exception) {
                LogManager.log("Failed to fetch user options: ${e.message}")
            } finally {
                activeCommand = null
            }
        }
    }

    fun saveUserOptions() {
        var options = editingUserOptions ?: return

        // Update target from string input
        val targetValue = userOptionsTargetInput.toIntOrNull() ?: options.target
        options = options.copy(target = targetValue)

        viewModelScope.launch {
            try {
                isSavingUserOptions = true
                activeCommand = "Saving Options"
                LogManager.log("Saving User Options...")
                val response = PumpManager.execute(PumpManager.commands.optionSetUserOption(options))
                LogManager.log("Save Options result: ${response.status}")
                showUserOptionsDialog = false
                refreshAllStatus()
            } catch (error: Throwable) {
                LogManager.log("Save failed: ${error.message}")
            } finally {
                isSavingUserOptions = false
                activeCommand = null
            }
        }
    }

    fun openBolusOptionsDialog() {
        if (!sessionReady) {
            LogManager.log("Action failed: Handshake required")
            return
        }
        viewModelScope.launch {
            try {
                activeCommand = "Loading Bolus Options"
                val optionsResponse = PumpManager.execute(PumpManager.commands.bolusGetBolusOption())

                PumpManager.bolusOptions = optionsResponse
                editingBolusOptions = optionsResponse
                showBolusOptionsDialog = true
            } catch (e: Exception) {
                LogManager.log("Failed to fetch bolus options: ${e.message}")
            } finally {
                activeCommand = null
            }
        }
    }

    fun saveBolusOptions() {
        val options = editingBolusOptions ?: return

        viewModelScope.launch {
            try {
                isSavingBolusOptions = true
                activeCommand = "Saving Bolus Options"

                LogManager.log("Saving Bolus Calculation Options...")
                val response = PumpManager.execute(
                    PumpManager.commands.bolusSetBolusOption(
                        extendedBolusEnabled = options.extendedBolusEnabled,
                        bolusCalculationOption = options.bolusCalculationOption,
                        missedBolusConfig = options.missedBolusConfig,
                        missedBolusWindows = options.missedBolusWindows
                    )
                )

                LogManager.log("Save Bolus Options result: ${response.status}")
                showBolusOptionsDialog = false
                refreshAllStatus()
            } catch (error: Throwable) {
                LogManager.log("Save failed: ${error.message}")
            } finally {
                isSavingBolusOptions = false
                activeCommand = null
            }
        }
    }

    fun openBasalProfilesDialog() {
        if (!sessionReady) {
            LogManager.log("Action failed: Handshake required")
            return
        }
        viewModelScope.launch {
            try {
                activeCommand = "Loading Basal"
                isLoadingBasalProfile = true

                // Get current profile index first
                val profileInfo = PumpManager.execute(PumpManager.commands.basalGetProfileNumber())
                selectedBasalProfileIndex = profileInfo.activeProfile

                // Fetch rates for that profile
                val basalResponse = PumpManager.execute(PumpManager.commands.basalGetProfileBasalRate(selectedBasalProfileIndex))
                editingBasalRates = basalResponse.hourlyRatesUnits

                showBasalProfilesDialog = true
            } catch (e: Exception) {
                LogManager.log("Failed to fetch basal profile: ${e.message}")
            } finally {
                isLoadingBasalProfile = false
                activeCommand = null
            }
        }
    }

    fun switchBasalProfile(index: Int) {
        if (isLoadingBasalProfile || isSavingBasalProfiles) return
        selectedBasalProfileIndex = index
        viewModelScope.launch {
            try {
                activeCommand = "Loading Basal $index"
                isLoadingBasalProfile = true
                val basalResponse = PumpManager.execute(PumpManager.commands.basalGetProfileBasalRate(index))
                editingBasalRates = basalResponse.hourlyRatesUnits
            } catch (e: Exception) {
                LogManager.log("Failed to fetch basal profile $index: ${e.message}")
            } finally {
                isLoadingBasalProfile = false
                activeCommand = null
            }
        }
    }

    fun saveBasalProfiles() {
        val basalRates = editingBasalRates ?: return
        val profileIndex = selectedBasalProfileIndex

        viewModelScope.launch {
            try {
                isSavingBasalProfiles = true
                activeCommand = "Saving Basal"
                LogManager.log("Saving Basal Profile $profileIndex...")

                PumpManager.execute(
                    PumpManager.commands.basalSetProfileBasalRate(
                        profileNumber = profileIndex,
                        hourlyRatesUnits = basalRates
                    )
                )

                LogManager.log("Basal Profile $profileIndex saved")
                showBasalProfilesDialog = false
                refreshAllStatus()
            } catch (error: Throwable) {
                LogManager.log("Save failed: ${error.message}")
            } finally {
                isSavingBasalProfiles = false
                activeCommand = null
            }
        }
    }

    fun openBolusProfileDialog() {
        if (!sessionReady) {
            LogManager.log("Action failed: Handshake required")
            return
        }
        viewModelScope.launch {
            try {
                activeCommand = "Loading Bolus Profile"
                val circfResponse = PumpManager.execute(PumpManager.commands.bolusGet24CIRCFArray())
                editingCirValues = circfResponse.valuesByHour.map { it.cir }
                editingCfValues = circfResponse.valuesByHour.map { it.cf }
                showBolusProfileDialog = true
            } catch (e: Exception) {
                LogManager.log("Failed to fetch bolus profile: ${e.message}")
            } finally {
                activeCommand = null
            }
        }
    }

    fun saveBolusProfile() {
        val cirValues = editingCirValues ?: return
        val cfValues = editingCfValues ?: return

        viewModelScope.launch {
            try {
                isSavingBolusProfile = true
                activeCommand = "Saving Bolus Profile"
                LogManager.log("Saving CIR/CF Profile...")

                val units = userOptions?.units ?: DanaGlucoseUnits.MGDL
                val icArray = IntArray(24) { i -> cirValues[i].toInt() }
                val cfArray = IntArray(24) { i ->
                    if (units == DanaGlucoseUnits.MGDL) {
                        cfValues[i].toInt()
                    } else {
                        (cfValues[i] * 100.0).toInt()
                    }
                }

                PumpManager.execute(PumpManager.commands.bolusSet24CIRCFArray(icArray, cfArray))

                LogManager.log("Bolus profile saved")
                showBolusProfileDialog = false
                refreshAllStatus()
            } catch (error: Throwable) {
                LogManager.log("Save failed: ${error.message}")
            } finally {
                isSavingBolusProfile = false
                activeCommand = null
            }
        }
    }

    fun openBolusDialog() {
        if (!sessionReady) {
            LogManager.log("Action failed: Handshake required")
            return
        }

        // Use last known step bolus amount or a safe default
        bolusAmountInput = "0.00"
        showBolusDialog = true

        // Auto-refresh bolus info if it's missing or old
        if (stepBolusInfo == null) {
            viewModelScope.launch {
                try {
                    activeCommand = "Fetching Limits"
                    PumpManager.stepBolusInfo = PumpManager.execute(PumpManager.commands.bolusGetStepBolusInformation())
                } catch (e: Exception) {
                    LogManager.log("Failed to fetch bolus limits: ${e.message}")
                } finally {
                    activeCommand = null
                }
            }
        }
    }

    fun startStepBolus() {
        val amount = bolusAmountInput.toDoubleOrNull() ?: return
        if (amount <= 0) return

        viewModelScope.launch {
            try {
                isProcessingBolus = true
                activeCommand = "Sending Bolus"
                LogManager.log("Sending Bolus: $amount U...")

                val response = PumpManager.execute(
                    PumpManager.commands.bolusSetStepBolusStart(
                        amountUnits = amount,
                        speed = bolusSpeed
                    )
                )

                LogManager.log("Bolus result: ${response.status}")
                showBolusDialog = false

                // Refresh status after a short delay to see the updated reservoir/IOB
                kotlinx.coroutines.delay(2000)
                refreshAllStatus()
            } catch (error: Throwable) {
                LogManager.log("Bolus failed: ${error.message}")
            } finally {
                isProcessingBolus = false
                activeCommand = null
            }
        }
    }

    fun stopBolus() {
        viewModelScope.launch {
            try {
                activeCommand = "Stopping Bolus"
                LogManager.log("Stopping Bolus...")
                val response = PumpManager.execute(PumpManager.commands.bolusSetStepBolusStop())
                LogManager.log("Stop Bolus result: ${response.status}")
                refreshAllStatus()
            } catch (error: Throwable) {
                LogManager.log("Stop Bolus failed: ${error.message}")
            } finally {
                activeCommand = null
            }
        }
    }

    fun openTempBasalDialog() {
        if (!sessionReady) return
        tempBasalPercentInput = "100"
        tempBasalDurationInput = "1"
        showTempBasalDialog = true
    }

    fun startTempBasal() {
        val percent = tempBasalPercentInput.toIntOrNull() ?: return
        val duration = tempBasalDurationInput.toIntOrNull() ?: return

        viewModelScope.launch {
            try {
                isProcessingTempBasal = true
                activeCommand = "Setting Temp Basal"
                LogManager.log("Setting Temp Basal: $percent% for ${duration}h...")
                val response = PumpManager.execute(
                    PumpManager.commands.basalSetTemporaryBasal(percent, duration)
                )
                LogManager.log("Temp Basal result: ${response.status}")
                showTempBasalDialog = false
                refreshAllStatus()
            } catch (error: Throwable) {
                LogManager.log("Temp Basal failed: ${error.message}")
            } finally {
                isProcessingTempBasal = false
                activeCommand = null
            }
        }
    }

    fun cancelTempBasal() {
        viewModelScope.launch {
            try {
                activeCommand = "Canceling Temp Basal"
                LogManager.log("Canceling Temp Basal...")
                val response = PumpManager.execute(PumpManager.commands.basalSetCancelTemporaryBasal())
                LogManager.log("Cancel Temp Basal result: ${response.status}")
                refreshAllStatus()
            } catch (error: Throwable) {
                LogManager.log("Cancel failed: ${error.message}")
            } finally {
                activeCommand = null
            }
        }
    }

    fun openExtendedBolusDialog() {
        if (!sessionReady) return
        extendedBolusAmountInput = "0.00"
        extendedBolusDurationInput = "1"
        showExtendedBolusDialog = true
    }

    fun startExtendedBolus() {
        val amount = extendedBolusAmountInput.toDoubleOrNull() ?: return
        val halfHours = extendedBolusDurationInput.toIntOrNull() ?: return
        if (amount <= 0) return

        viewModelScope.launch {
            try {
                isProcessingExtendedBolus = true
                activeCommand = "Starting Extended Bolus"
                LogManager.log("Starting Extended Bolus: $amount U over ${halfHours * 0.5}h...")
                val response = PumpManager.execute(
                    PumpManager.commands.bolusSetExtendedBolus(amount, halfHours)
                )
                LogManager.log("Extended Bolus result: ${response.status}")
                showExtendedBolusDialog = false
                refreshAllStatus()
            } catch (error: Throwable) {
                LogManager.log("Extended Bolus failed: ${error.message}")
            } finally {
                isProcessingExtendedBolus = false
                activeCommand = null
            }
        }
    }

    fun cancelExtendedBolus() {
        viewModelScope.launch {
            try {
                activeCommand = "Canceling Extended Bolus"
                LogManager.log("Canceling Extended Bolus...")
                val response = PumpManager.execute(PumpManager.commands.bolusSetExtendedBolusCancel())
                LogManager.log("Cancel Extended Bolus result: ${response.status}")
                refreshAllStatus()
            } catch (error: Throwable) {
                LogManager.log("Cancel failed: ${error.message}")
            } finally {
                activeCommand = null
            }
        }
    }
}